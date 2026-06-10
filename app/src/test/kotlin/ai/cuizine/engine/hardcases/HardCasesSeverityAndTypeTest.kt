package ai.cuizine.engine.hardcases

import ai.cuizine.engine.ConflictChoice
import ai.cuizine.engine.ConstraintChanges
import ai.cuizine.engine.ConstraintGraphEngine
import ai.cuizine.engine.ConstraintWriteException
import ai.cuizine.engine.conflict.ConflictAnalyzer
import ai.cuizine.engine.ports.ConstraintStore
import ai.cuizine.engine.ports.EngineClock
import ai.cuizine.engine.ports.FoodDataPort
import ai.cuizine.engine.ports.IngredientFacts
import ai.cuizine.engine.ports.IngredientResolution
import ai.cuizine.engine.types.AvoidException
import ai.cuizine.engine.types.AvoidPayload
import ai.cuizine.engine.types.AvoidTarget
import ai.cuizine.engine.types.ConstraintType
import ai.cuizine.engine.types.LimitPayload
import ai.cuizine.engine.types.LimitTarget
import ai.cuizine.engine.types.PreferPayload
import ai.cuizine.engine.types.PreferTarget
import ai.cuizine.engine.types.RequirePayload
import ai.cuizine.engine.types.RequireTarget
import ai.cuizine.engine.types.Severity
import ai.cuizine.engine.types.SuggestionIngredient
import ai.cuizine.engine.types.SuggestionInput
import ai.cuizine.engine.types.Threshold
import ai.cuizine.shared.fixtures.AishaFixtures
import ai.cuizine.shared.fixtures.SukhiFixtures
import ai.cuizine.shared.types.Constraint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hard-cases suite, Categories 1 and 2 (`testing-strategy.md` §5): the four
 * severity tiers' mechanical rules (`constraint-engine-spec.md` §4) and the
 * five constraint types' semantic behavior (`constraint-engine-spec.md` §3,
 * §7 Step 3). Where the engine deviates from the spec, the test asserts the
 * SPEC behavior and the deviation is reported, not papered over.
 */
class HardCasesSeverityAndTypeTest {
    // ── Test ports ───────────────────────────────────────────────────────

    private class InMemoryStore : ConstraintStore {
        val rows = mutableMapOf<String, Constraint>()
        val removed = mutableSetOf<String>()

        override suspend fun loadLive(profileId: String) =
            rows.values.filter { it.profileId == profileId && it.id !in removed }

        override suspend fun loadAll(profileId: String) = rows.values.filter { it.profileId == profileId }

        override suspend fun loadById(constraintId: String) = rows[constraintId]

        override suspend fun insert(constraint: Constraint) {
            rows[constraint.id] = constraint
        }

        override suspend fun update(constraint: Constraint) {
            rows[constraint.id] = constraint
        }

        override suspend fun markRemoved(
            constraintId: String,
            removedAtIso: String,
        ) {
            removed += constraintId
        }
    }

    private class FixtureFoodData : FoodDataPort {
        val known =
            mapOf(
                "onion" to IngredientFacts("onion", categories = setOf("allium", "vegetable", "fodmap_high")),
                "garlic" to IngredientFacts("garlic", categories = setOf("allium", "fodmap_high")),
                "spinach" to IngredientFacts("spinach", categories = setOf("vegetable", "leafy_green")),
                "masoor dal" to IngredientFacts("masoor dal", categories = setOf("legume", "dal")),
                "rajma" to IngredientFacts("rajma", categories = setOf("legume", "dal")),
                "beef" to IngredientFacts("beef", categories = setOf("meat", "non_halal_risk")),
                "watermelon" to IngredientFacts("watermelon", categories = setOf("fruit", "hydrating")),
                "ghee" to IngredientFacts("ghee", categories = setOf("dairy", "fat")),
                "paneer" to IngredientFacts("paneer", categories = setOf("dairy")),
                "cilantro" to IngredientFacts("cilantro", categories = setOf("herb")),
                "lentils" to
                    IngredientFacts(
                        "lentils",
                        categories = setOf("legume"),
                        nutritionPer100g = mapOf("iron_mg" to 6.0),
                    ),
                "wheat noodles" to
                    IngredientFacts(
                        "wheat noodles",
                        categories = setOf("grain"),
                        allergens = setOf("gluten"),
                    ),
                "bacon" to
                    IngredientFacts(
                        "bacon",
                        categories = setOf("meat", "pork", "non_halal"),
                        nutritionPer100g = mapOf("sodium_mg" to 1700.0),
                    ),
                "soy sauce" to
                    IngredientFacts("soy sauce", nutritionPer100g = mapOf("sodium_mg" to 5500.0)),
            )
        var aiAnswer: IngredientFacts? = null
        var aiCalls = 0

        override suspend fun lookup(ingredientName: String): IngredientResolution =
            known[ingredientName.lowercase()]?.let { IngredientResolution.Known(it, "high_bundle") }
                ?: IngredientResolution.Unknown

        override suspend fun categorizeWithAi(ingredientName: String): IngredientFacts? {
            aiCalls += 1
            return aiAnswer
        }
    }

    private val store = InMemoryStore()
    private val foodData = FixtureFoodData()
    private val fixedClock = EngineClock { "2026-06-09T22:00:00Z" } // 18:00 Tuesday evening in Toronto

    private fun engine(timezone: String = "America/Toronto") =
        ConstraintGraphEngine(
            store = store,
            foodData = foodData,
            clock = fixedClock,
            profileTimezone = { timezone },
        )

    private fun seed(vararg constraints: Constraint) {
        constraints.forEach { store.rows[it.id] = it }
    }

    private fun seedSukhi() {
        SukhiFixtures.constraints.forEach { store.rows[it.id] = it }
    }

    private fun seedAisha() {
        AishaFixtures.constraints.forEach { store.rows[it.id] = it }
    }

    // ── Constraint builders (fixture helper style — never new personas) ──

    private fun avoidConstraint(
        id: String,
        target: AvoidTarget,
        severity: Severity = Severity.Medical,
        exceptions: List<AvoidException> = emptyList(),
    ) = SukhiFixtures.avoidBeef.copy(
        id = id,
        severity = severity,
        humanLabel = "avoid ${target.value}",
        payload = AvoidPayload(target = target, exceptions = exceptions),
    )

    private fun requireConstraint(
        id: String,
        target: RequireTarget,
        window: String = "single_meal",
    ) = Constraint(
        id = id,
        profileId = SukhiFixtures.PROFILE_ID,
        type = ConstraintType.Require,
        severity = Severity.Medical,
        humanLabel = "require ${target.value}",
        scope = SukhiFixtures.avoidBeef.scope,
        payload = RequirePayload(target = target, window = window),
        provenance = SukhiFixtures.avoidBeef.provenance,
    )

    private fun limitConstraint(
        id: String,
        ceiling: Double,
        hardOrSoft: String,
    ) = SukhiFixtures.limitSodium.copy(
        id = id,
        payload =
            LimitPayload(
                target = LimitTarget(kind = "nutritional_property", value = "sodium_mg"),
                ceiling = Threshold(value = ceiling, unit = "mg"),
                window = "per_meal",
                hardOrSoft = hardOrSoft,
            ),
    )

    private fun meal(vararg names: String) =
        SuggestionInput(
            mealName = "test meal",
            ingredients = names.map { SuggestionIngredient(it, 100.0, "g") },
        )

    private fun mealWeighted(vararg items: Pair<String, Double>) =
        SuggestionInput(
            mealName = "test meal",
            ingredients = items.map { (name, grams) -> SuggestionIngredient(name, grams, "g") },
        )

    // ═════════════════════════════════════════════════════════════════════
    // Category 1 — severity tiers (`constraint-engine-spec.md` §4)
    // ═════════════════════════════════════════════════════════════════════

    // ── Tier 1: Inviolable ───────────────────────────────────────────────

    @Test
    fun inviolable_avoidViolationRejectsOutright() =
        runTest {
            // §4 Tier 1: any suggestion violating an active Inviolable constraint is rejected outright.
            seed(
                avoidConstraint(
                    "inviolable-beef",
                    AvoidTarget(kind = "ingredient", value = "beef"),
                    Severity.Inviolable,
                ),
            )
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("beef"))
            assertFalse(result.passed)
            assertEquals(Severity.Inviolable, result.violations.single().severity)
        }

    @Test
    fun inviolable_unknownIngredientNeverTriggersAiFallback() =
        runTest {
            // §4 Tier 1 + §7 Step 4: AI fallback is forbidden for Inviolable checks — the safety floor.
            seed(
                avoidConstraint(
                    "inviolable-peanut",
                    AvoidTarget(kind = "category", value = "peanut"),
                    Severity.Inviolable,
                ),
            )
            foodData.aiAnswer = IngredientFacts("mystery", categories = setOf("snack"))
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("mystery snack mix"))
            assertFalse(result.passed)
            assertEquals(0, foodData.aiCalls)
        }

    @Test
    fun inviolable_conflictPlannerReturnsNoMealFoundNeverOptions() {
        // §4 Tier 1 + §8: Inviolable constraints are never in the pickable set — the honest
        // no-meal-found fallback is the only path.
        val inviolableHalal =
            AishaFixtures.halalObservance.copy(id = "inviolable-halal", severity = Severity.Inviolable)
        val medicalAllium = avoidConstraint("medical-allium", AvoidTarget(kind = "category", value = "allium"))
        val plan =
            ConflictAnalyzer.planResolution(
                blamed = listOf(inviolableHalal, medicalAllium),
                analysis =
                    ConflictAnalyzer.FailureAnalysis(
                        blamedConstraintIds = listOf(medicalAllium.id, inviolableHalal.id),
                        isNarrow = false,
                    ),
            )
        val noMeal = plan as ConflictAnalyzer.ResolutionPlan.NoMealFound
        assertEquals(listOf(inviolableHalal.id), noMeal.inviolableConstraintIds)
    }

    @Test
    fun inviolable_resolveConflictRejectsRelaxationAttempt() =
        runTest {
            // §4 Tier 1: user-initiated relaxation of an Inviolable constraint is forbidden in v1.
            val inviolableHalal =
                AishaFixtures.halalObservance.copy(id = "inviolable-halal", severity = Severity.Inviolable)
            seed(inviolableHalal)
            val error =
                runCatching {
                    engine().resolveConflict(
                        AishaFixtures.PROFILE_ID,
                        conflictId = "c",
                        userChoice =
                            ConflictChoice(
                                relaxedConstraintIds = listOf(inviolableHalal.id),
                                optionLabel = "never",
                            ),
                    )
                }.exceptionOrNull()
            assertEquals("inviolable_relaxation", (error as ConstraintWriteException).code)
        }

    @Test
    fun inviolable_writeTimeRejectsEscalatingSoftLimitToInviolable() =
        runTest {
            // §4 disallowed combinations: a soft limit can never carry the Inviolable tier — the
            // write path rejects with a structured error rather than coercing.
            seed(SukhiFixtures.limitSodium) // hard_or_soft = "soft"
            val error =
                runCatching {
                    engine().updateConstraint(
                        SukhiFixtures.PROFILE_ID,
                        SukhiFixtures.limitSodium.id,
                        ConstraintChanges(severity = Severity.Inviolable),
                    )
                }.exceptionOrNull()
            assertEquals("soft_limit_never_inviolable", (error as ConstraintWriteException).code)
        }

    @Test
    fun inviolable_staleSessionRelaxationCannotBypassInviolable() =
        runTest {
            // §8: "never relax Inviolable" is enforced again at validation time as the safety
            // floor — a relaxation granted while the constraint was Medical must stop applying
            // once the constraint is Inviolable.
            val medicalBeef = avoidConstraint("escalating-beef", AvoidTarget(kind = "ingredient", value = "beef"))
            seed(medicalBeef)
            val subject = engine()
            subject.resolveConflict(
                SukhiFixtures.PROFILE_ID,
                conflictId = "c",
                userChoice = ConflictChoice(relaxedConstraintIds = listOf(medicalBeef.id), optionLabel = "tonight"),
            )
            assertTrue(subject.validateSuggestion(SukhiFixtures.PROFILE_ID, meal("beef")).passed)
            store.rows[medicalBeef.id] = medicalBeef.copy(severity = Severity.Inviolable)
            // Later minute → active-set cache recomputes against the escalated severity.
            val after =
                subject.validateSuggestion(SukhiFixtures.PROFILE_ID, meal("beef"), atTimeIso = "2026-06-09T22:05:00Z")
            assertFalse(after.passed)
        }

    // ── Tier 2: Medical ──────────────────────────────────────────────────

    @Test
    fun medical_violationRejectsAndCarriesRegenerationReason() =
        runTest {
            // §4 Tier 2: a Medical violation rejects the suggestion; the violation's mechanical
            // reason is the Chef's regeneration signal (§7 "The regeneration loop").
            seed(avoidConstraint("medical-allium", AvoidTarget(kind = "category", value = "allium")))
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("onion"))
            assertFalse(result.passed)
            val violation = result.violations.single()
            assertEquals(Severity.Medical, violation.severity)
            assertTrue(violation.reason.isNotBlank())
        }

    @Test
    fun medical_unknownIngredientAiFallbackPassesWithDisclosure() =
        runTest {
            // §4 Tier 2 + §7 Step 4: unknown ingredients under a Medical constraint get the AI
            // sub-call; a safe categorization passes WITH an inline disclosure note.
            seed(SukhiFixtures.avoidAlliumsDuringFlare)
            val subject = engine()
            subject.setContextualState(SukhiFixtures.PROFILE_ID, flag = "ibs_flare")
            foodData.aiAnswer = IngredientFacts("ramps", categories = setOf("vegetable"))
            val result = subject.validateSuggestion(SukhiFixtures.PROFILE_ID, meal("ramps"))
            assertTrue(result.passed)
            assertTrue(foodData.aiCalls > 0)
            assertTrue(result.disclosureNotes.any { it.contains("not 100% sure") })
        }

    @Test
    fun medical_unknownIngredientAiCategorizedUnsafeIsViolation() =
        runTest {
            // §7 Step 4: when the AI categorization fails the Medical rule check, the outcome
            // is a violation — never a quiet pass.
            seed(SukhiFixtures.avoidAlliumsDuringFlare)
            val subject = engine()
            subject.setContextualState(SukhiFixtures.PROFILE_ID, flag = "ibs_flare")
            foodData.aiAnswer = IngredientFacts("ramps", categories = setOf("allium", "vegetable"))
            val result = subject.validateSuggestion(SukhiFixtures.PROFILE_ID, meal("ramps"))
            assertFalse(result.passed)
            assertTrue(result.violations.any { it.constraintId == SukhiFixtures.avoidAlliumsDuringFlare.id })
        }

    @Test
    fun medical_relaxablePerSessionByExplicitUserChoice() =
        runTest {
            // §4 Tier 2: Medical constraints can be temporarily relaxed by explicit user choice
            // ("I know this isn't ideal but I want it anyway tonight").
            val medicalBeef = avoidConstraint("medical-beef", AvoidTarget(kind = "ingredient", value = "beef"))
            seed(medicalBeef)
            val subject = engine()
            assertFalse(subject.validateSuggestion(SukhiFixtures.PROFILE_ID, meal("beef")).passed)
            subject.resolveConflict(
                SukhiFixtures.PROFILE_ID,
                conflictId = "c",
                userChoice = ConflictChoice(relaxedConstraintIds = listOf(medicalBeef.id), optionLabel = "tonight"),
            )
            assertTrue(subject.validateSuggestion(SukhiFixtures.PROFILE_ID, meal("beef")).passed)
        }

    // ── Tier 3: Religious & Cultural (mirrors Medical — §4 "structurally identical") ──

    @Test
    fun religiousCultural_violationRejectsLikeMedical() =
        runTest {
            // §4 Tier 3: validator behavior is identical to Medical — violation rejects.
            seed(SukhiFixtures.avoidBeef)
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("beef"))
            assertFalse(result.passed)
            assertEquals(Severity.ReligiousCultural, result.violations.single().severity)
        }

    @Test
    fun religiousCultural_unknownIngredientAiFallbackPassesWithDisclosure() =
        runTest {
            // §4 Tier 3 + §7 Step 4: same AI-fallback-with-disclosure path as Medical.
            seed(AishaFixtures.halalObservance)
            foodData.aiAnswer = IngredientFacts("jackfruit", categories = setOf("fruit"))
            val result = engine().validateSuggestion(AishaFixtures.PROFILE_ID, meal("jackfruit"))
            assertTrue(result.passed)
            assertTrue(foodData.aiCalls > 0)
            assertTrue(result.disclosureNotes.any { it.contains("not 100% sure") })
        }

    @Test
    fun religiousCultural_relaxablePerSessionByExplicitUserChoice() =
        runTest {
            // §4 Tier 3: temporarily relaxable by explicit user choice, exactly like Medical.
            seed(SukhiFixtures.avoidBeef)
            val subject = engine()
            assertFalse(subject.validateSuggestion(SukhiFixtures.PROFILE_ID, meal("beef")).passed)
            subject.resolveConflict(
                SukhiFixtures.PROFILE_ID,
                conflictId = "c",
                userChoice =
                    ConflictChoice(
                        relaxedConstraintIds = listOf(SukhiFixtures.avoidBeef.id),
                        optionLabel = "Beef is okay tonight",
                    ),
            )
            assertTrue(subject.validateSuggestion(SukhiFixtures.PROFILE_ID, meal("beef")).passed)
        }

    @Test
    fun religiousCultural_coEqualWithMedicalInResolutionOptions() {
        // §4 Tiers 2/3 (ADR 0009): Medical and Religious & Cultural are co-equal — when both
        // block, BOTH are surfaced as options and neither takes precedence.
        val medicalAllium = avoidConstraint("medical-allium", AvoidTarget(kind = "category", value = "allium"))
        val plan =
            ConflictAnalyzer.planResolution(
                blamed = listOf(medicalAllium, SukhiFixtures.avoidBeef),
                analysis =
                    ConflictAnalyzer.FailureAnalysis(
                        blamedConstraintIds = listOf(medicalAllium.id, SukhiFixtures.avoidBeef.id),
                        isNarrow = false,
                    ),
            )
        val surfaced = plan as ConflictAnalyzer.ResolutionPlan.SurfaceToUser
        assertTrue(surfaced.options.any { it.relaxesConstraintIds == listOf(medicalAllium.id) })
        assertTrue(surfaced.options.any { it.relaxesConstraintIds == listOf(SukhiFixtures.avoidBeef.id) })
    }

    // ── Tier 4: Preference ───────────────────────────────────────────────

    @Test
    fun preference_avoidViolationNeverRejectsAlone() =
        runTest {
            // §4 Tier 4: "a preference violation does not cause suggestion rejection on its
            // own" — a disliked ingredient (cilantro) alone must not fail the meal.
            seed(
                avoidConstraint(
                    "preference-cilantro",
                    AvoidTarget(kind = "ingredient", value = "cilantro"),
                    Severity.Preference,
                ),
            )
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("cilantro", "spinach"))
            assertTrue(result.passed)
        }

    @Test
    fun preference_unknownIngredientAiFallbackWithoutDisclosure() =
        runTest {
            // §4 Tier 4 + §7 Step 4: AI categorization runs at the Preference tier WITHOUT a
            // user-facing disclosure note — lower stakes don't justify interrupting the user.
            seed(
                avoidConstraint(
                    "preference-cilantro",
                    AvoidTarget(kind = "ingredient", value = "cilantro"),
                    Severity.Preference,
                ),
            )
            foodData.aiAnswer = IngredientFacts("culantro", categories = setOf("herb"))
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("culantro"))
            assertTrue(result.passed)
            assertTrue(foodData.aiCalls > 0)
            assertTrue(result.disclosureNotes.isEmpty())
        }

    @Test
    fun preference_silentRelaxationCandidatesAreOnlyPreferenceTier() {
        // §4 Tier 4 + §8 Step 2: preferences are the only tier the conflict resolver may relax
        // silently — the candidate set never contains a higher tier.
        val candidates =
            ConflictAnalyzer.preferenceRelaxationCandidates(
                listOf(
                    SukhiFixtures.preferRajma,
                    SukhiFixtures.avoidBeef,
                    SukhiFixtures.limitSodium,
                    SukhiFixtures.preferAloo,
                ),
            )
        assertEquals(listOf(SukhiFixtures.preferRajma, SukhiFixtures.preferAloo), candidates)
    }

    @Test
    fun preference_neverSurfacedAsUserFacingRelaxationOption() {
        // §8 Steps 2–3: preferences are relaxed silently, never offered as a user-facing
        // conflict option — only Medical/Religious & Cultural reach the option list.
        val medicalAllium = avoidConstraint("medical-allium", AvoidTarget(kind = "category", value = "allium"))
        val plan =
            ConflictAnalyzer.planResolution(
                blamed = listOf(SukhiFixtures.preferRajma, medicalAllium),
                analysis =
                    ConflictAnalyzer.FailureAnalysis(
                        blamedConstraintIds = listOf(SukhiFixtures.preferRajma.id, medicalAllium.id),
                        isNarrow = false,
                    ),
            )
        val surfaced = plan as ConflictAnalyzer.ResolutionPlan.SurfaceToUser
        assertTrue(surfaced.options.none { SukhiFixtures.preferRajma.id in it.relaxesConstraintIds })
    }

    // ═════════════════════════════════════════════════════════════════════
    // Category 2 — constraint type semantics (`constraint-engine-spec.md` §3, §7 Step 3)
    // ═════════════════════════════════════════════════════════════════════

    // ── Type 1: avoid ────────────────────────────────────────────────────

    @Test
    fun avoid_literalIngredientMatchViolates() =
        runTest {
            // §3 Type 1 + §7 Step 3: a literal target match that is not excepted is a violation.
            seed(SukhiFixtures.avoidBeef)
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("beef", "spinach"))
            val violation = result.violations.single()
            assertEquals(SukhiFixtures.avoidBeef.id, violation.constraintId)
            assertEquals("beef", violation.ingredientName)
        }

    @Test
    fun avoid_categoricalMatchViolatesThroughCategoryGraph() =
        runTest {
            // §3 Type 1: categorical targets expand to all member ingredients at validation
            // time (garlic → allium).
            seed(avoidConstraint("avoid-allium-always", AvoidTarget(kind = "category", value = "allium")))
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("garlic", "spinach"))
            assertFalse(result.passed)
            assertEquals("garlic", result.violations.single().ingredientName)
        }

    @Test
    fun avoid_exceptionExcusesMatchingIngredient() =
        runTest {
            // §3 Type 1 exceptions: "no dairy, but ghee is fine for me" — the documented
            // tolerance excuses the match.
            seed(
                avoidConstraint(
                    "avoid-dairy",
                    AvoidTarget(kind = "category", value = "dairy"),
                    exceptions = listOf(AvoidException(kind = "ingredient", value = "ghee", reason = "tolerated")),
                ),
            )
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("ghee", "spinach"))
            assertTrue(result.passed)
        }

    @Test
    fun avoid_exceptionOnlyExcusesTheNamedTarget() =
        runTest {
            // §3 Type 1 exceptions: the ghee carve-out does not extend to other dairy (paneer).
            seed(
                avoidConstraint(
                    "avoid-dairy",
                    AvoidTarget(kind = "category", value = "dairy"),
                    exceptions = listOf(AvoidException(kind = "ingredient", value = "ghee", reason = "tolerated")),
                ),
            )
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("paneer"))
            assertFalse(result.passed)
            assertEquals("paneer", result.violations.single().ingredientName)
        }

    @Test
    fun avoid_allergenSetMatchesCategoricalTarget() =
        runTest {
            // §7 Step 3: categorical matching runs through the resolved entry's allergen set
            // too (wheat noodles carry the gluten allergen).
            seed(avoidConstraint("avoid-gluten", AvoidTarget(kind = "category", value = "gluten")))
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("wheat noodles"))
            assertFalse(result.passed)
            assertEquals("wheat noodles", result.violations.single().ingredientName)
        }

    // ── Type 2: prefer ───────────────────────────────────────────────────

    @Test
    fun prefer_alignmentInMealNameProducesNoDisclosure() =
        runTest {
            // §7 Step 3: a suggestion aligned with the preference (dish name) produces no note.
            seed(SukhiFixtures.preferRajma)
            val suggestion =
                SuggestionInput(
                    mealName = "Rajma with brown basmati",
                    ingredients = listOf(SuggestionIngredient("spinach", 100.0, "g")),
                )
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, suggestion)
            assertTrue(result.disclosureNotes.isEmpty())
        }

    @Test
    fun prefer_alignmentInIngredientProducesNoDisclosure() =
        runTest {
            // §7 Step 3: alignment via an included ingredient also counts — no note.
            seed(SukhiFixtures.preferRajma)
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("rajma"))
            assertTrue(result.disclosureNotes.isEmpty())
        }

    @Test
    fun prefer_highStrengthMisalignmentAddsDisclosureNote() =
        runTest {
            // §3 Type 2 + §7 Step 3: misalignment with a high-strength preference is a quiet
            // disclosure note.
            seed(SukhiFixtures.preferRajma) // strength = "high"
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("spinach"))
            assertTrue(result.disclosureNotes.any { it.contains("rajma") })
        }

    @Test
    fun prefer_mediumStrengthMisalignmentStaysSilent() =
        runTest {
            // §3 Type 2: strength biases how loudly misalignment registers — medium stays silent.
            seed(SukhiFixtures.preferAloo) // strength = "medium"
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("spinach"))
            assertTrue(result.disclosureNotes.isEmpty())
        }

    @Test
    fun prefer_lowStrengthMisalignmentStaysSilent() =
        runTest {
            // §3 Type 2: low strength misalignment never produces a note.
            seed(
                SukhiFixtures.preferRajma.copy(
                    id = "prefer-rajma-low",
                    payload = PreferPayload(target = PreferTarget(kind = "dish", value = "rajma"), strength = "low"),
                ),
            )
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("spinach"))
            assertTrue(result.disclosureNotes.isEmpty())
        }

    @Test
    fun prefer_misalignmentIsNeverAViolation() =
        runTest {
            // §7 Step 3: "Misalignment is a disclosure note, never a rejection."
            seed(SukhiFixtures.preferRajma)
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("spinach"))
            assertTrue(result.passed)
            assertTrue(result.violations.isEmpty())
        }

    // ── Type 3: require ──────────────────────────────────────────────────

    @Test
    fun require_presentCategoryTargetPasses() =
        runTest {
            // §3 Type 3 + §7 Step 3: the required category present in the meal satisfies it.
            seed(requireConstraint("require-greens", RequireTarget(kind = "category", value = "leafy_green")))
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("spinach", "masoor dal"))
            assertTrue(result.passed)
        }

    @Test
    fun require_absentCategoryTargetViolates() =
        runTest {
            // §7 Step 3: a missing requirement is a violation.
            seed(requireConstraint("require-greens", RequireTarget(kind = "category", value = "leafy_green")))
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("masoor dal"))
            assertFalse(result.passed)
            assertTrue(
                result.violations
                    .single()
                    .reason
                    .contains("missing required"),
            )
        }

    @Test
    fun require_nutritionalThresholdMetPasses() =
        runTest {
            // §7 Step 3: "meets the nutritional property threshold" — 100 g of lentils carries
            // 6 mg iron, over the 5 mg threshold.
            seed(
                requireConstraint(
                    "require-iron",
                    RequireTarget(
                        kind = "nutritional_property",
                        value = "iron_mg",
                        threshold = Threshold(value = 5.0, unit = "mg"),
                    ),
                ),
            )
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, mealWeighted("lentils" to 100.0))
            assertTrue(result.passed)
        }

    @Test
    fun require_nutritionalThresholdUnmetViolates() =
        runTest {
            // §7 Step 3: 50 g of lentils carries only 3 mg iron — under the 5 mg threshold.
            seed(
                requireConstraint(
                    "require-iron",
                    RequireTarget(
                        kind = "nutritional_property",
                        value = "iron_mg",
                        threshold = Threshold(value = 5.0, unit = "mg"),
                    ),
                ),
            )
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, mealWeighted("lentils" to 50.0))
            assertFalse(result.passed)
        }

    @Test
    fun require_nonSingleMealWindowSkippedInV1() =
        runTest {
            // The single-suggestion validator can only enforce single_meal windows in v1 —
            // daily accounting needs cross-meal history. A per_day require must not fail one
            // meal (see §3 Type 3 wording tension, reported as a discrepancy).
            seed(
                requireConstraint(
                    "require-greens-daily",
                    RequireTarget(kind = "category", value = "leafy_green"),
                    window = "per_day",
                ),
            )
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("masoor dal"))
            assertTrue(result.passed)
        }

    // ── Type 4: limit ────────────────────────────────────────────────────

    @Test
    fun limit_underCeilingPassesWithoutNotes() =
        runTest {
            // §3 Type 4: under the ceiling is a clean pass — no violation, no disclosure.
            seed(limitConstraint("hard-sodium", ceiling = 2000.0, hardOrSoft = "hard"))
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, mealWeighted("bacon" to 100.0))
            assertTrue(result.passed)
            assertTrue(result.disclosureNotes.isEmpty())
        }

    @Test
    fun limit_exactlyAtCeilingIsNotAViolation() =
        runTest {
            // §3 Type 4: the ceiling "must not exceed" — landing exactly on it (1700 mg sodium
            // against a 1700 mg ceiling) is NOT a violation. The named boundary case from
            // `testing-strategy.md` §5 Category 2.
            seed(limitConstraint("hard-sodium-exact", ceiling = 1700.0, hardOrSoft = "hard"))
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, mealWeighted("bacon" to 100.0))
            assertTrue(result.passed)
            assertTrue(result.violations.isEmpty())
        }

    @Test
    fun limit_hardOverCeilingViolates() =
        runTest {
            // §3 Type 4: hard limits behave like avoids above the ceiling — a violation.
            seed(limitConstraint("hard-sodium", ceiling = 2000.0, hardOrSoft = "hard"))
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, mealWeighted("soy sauce" to 100.0))
            assertFalse(result.passed)
            assertTrue(
                result.violations
                    .single()
                    .reason
                    .contains("sodium"),
            )
        }

    @Test
    fun limit_softOverCeilingIsDisclosureNotViolation() =
        runTest {
            // §3 Type 4 + §7 Step 3: soft limits over the ceiling are a disclosure note, never
            // a rejection.
            seed(limitConstraint("soft-sodium", ceiling = 2000.0, hardOrSoft = "soft"))
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, mealWeighted("soy sauce" to 100.0))
            assertTrue(result.passed)
            assertTrue(result.disclosureNotes.isNotEmpty())
        }

    @Test
    fun limit_sumsPropertyAcrossIngredientsWithGramQuantities() =
        runTest {
            // §7 Step 3: the property is summed ACROSS ingredients — bacon (1700 mg) and
            // 10 g soy sauce (550 mg) are each under the 2000 mg ceiling alone, together over.
            seed(limitConstraint("hard-sodium", ceiling = 2000.0, hardOrSoft = "hard"))
            val result =
                engine().validateSuggestion(
                    SukhiFixtures.PROFILE_ID,
                    mealWeighted("bacon" to 100.0, "soy sauce" to 10.0),
                )
            assertFalse(result.passed)
            assertEquals(ConstraintType.Limit, result.violations.single().constraintType)
        }

    // ── Type 5: contextual ───────────────────────────────────────────────

    @Test
    fun contextual_incompatibleWithAnyMealRejectsNonEmptyMeal() =
        runTest {
            // §7 Step 3: a state with incompatible_with "any_meal" directly violates on any
            // non-empty meal in its window — Aisha's fast at midday during Ramadan.
            seedAisha()
            val result =
                engine(timezone = AishaFixtures.TIMEZONE).validateSuggestion(
                    AishaFixtures.PROFILE_ID,
                    meal("watermelon"),
                    atTimeIso = "2026-03-02T17:00:00Z",
                )
            assertFalse(result.passed)
            val violation = result.violations.single()
            assertEquals(ConstraintType.Contextual, violation.constraintType)
            assertEquals(AishaFixtures.ramadanFast.id, violation.constraintId)
        }

    @Test
    fun contextual_withoutIncompatibleWithIsScopeOnlyNeverDirectViolation() =
        runTest {
            // §7 Step 3: contextual constraints "rarely produce direct violations on their own —
            // they mostly act as scope modifiers". A plain flag with no incompatible_with and no
            // dependent constraints must not reject anything.
            val subject = engine()
            subject.setContextualState(SukhiFixtures.PROFILE_ID, flag = "ibs_flare")
            val result = subject.validateSuggestion(SukhiFixtures.PROFILE_ID, meal("spinach"))
            assertTrue(result.passed)
            assertTrue(result.violations.isEmpty())
        }

    @Test
    fun contextual_explicitExpiryHonoredInActiveSet() =
        runTest {
            // §3 Type 5: contextual states always carry a short expiration; an expired state
            // leaves the active set.
            val subject = engine()
            val created =
                subject.setContextualState(
                    SukhiFixtures.PROFILE_ID,
                    flag = "ibs_flare",
                    expiresAtIso = "2026-06-09T23:00:00Z",
                )
            val before = subject.queryActive(SukhiFixtures.PROFILE_ID, "2026-06-09T22:30:00Z")
            assertTrue(before.constraints.any { it.id == created.id })
            val after = subject.queryActive(SukhiFixtures.PROFILE_ID, "2026-06-10T01:00:00Z")
            assertFalse(after.constraints.any { it.id == created.id })
        }

    @Test
    fun contextual_flagActivatesDependentConstraints() =
        runTest {
            // §3 Type 5: a contextual flag acts as a scope modifier — Sukhi's flare flag pulls
            // her flare-scoped allium avoid into the active set.
            seedSukhi()
            val subject = engine()
            val before = subject.queryByType(SukhiFixtures.PROFILE_ID, ConstraintType.Avoid)
            assertFalse(before.constraints.any { it.id == SukhiFixtures.avoidAlliumsDuringFlare.id })
            subject.setContextualState(SukhiFixtures.PROFILE_ID, flag = "ibs_flare")
            val after = subject.queryByType(SukhiFixtures.PROFILE_ID, ConstraintType.Avoid)
            assertTrue(after.constraints.any { it.id == SukhiFixtures.avoidAlliumsDuringFlare.id })
        }

    @Test
    fun contextual_travelFlagDefaultsToSevenDayExpiry() =
        runTest {
            // §3 Type 5: trip flags without an explicit end default to seven days (renewable).
            val created = engine().setContextualState(SukhiFixtures.PROFILE_ID, flag = "travel_toronto")
            assertEquals("2026-06-16T22:00:00Z", created.expiresAt)
        }

    @Test
    fun contextual_todayFlagExpiresEndOfDayInUserTimezone() =
        runTest {
            // §3 Type 5: "For 'today only' flags, this is end-of-day in the user's timezone."
            // Set at 18:00 Toronto, the flag must be gone by 09:00 the next morning
            // (2026-06-10T13:00:00Z), because local midnight has passed.
            val subject = engine()
            val created = subject.setContextualState(SukhiFixtures.PROFILE_ID, flag = "ibs_flare")
            val nextMorning = subject.queryActive(SukhiFixtures.PROFILE_ID, "2026-06-10T13:00:00Z")
            assertFalse(nextMorning.constraints.any { it.id == created.id })
        }
}
