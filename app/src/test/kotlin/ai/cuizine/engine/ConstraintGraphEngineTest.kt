package ai.cuizine.engine

import ai.cuizine.engine.ports.ConstraintStore
import ai.cuizine.engine.ports.EngineClock
import ai.cuizine.engine.ports.FoodDataPort
import ai.cuizine.engine.ports.IngredientFacts
import ai.cuizine.engine.ports.IngredientResolution
import ai.cuizine.engine.types.ConstraintType
import ai.cuizine.engine.types.LimitPayload
import ai.cuizine.engine.types.LimitTarget
import ai.cuizine.engine.types.PreferPayload
import ai.cuizine.engine.types.PreferTarget
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Engine-core slice of the hard-cases suite (`testing-strategy.md` §5;
 * `constraint-engine-spec.md` §4–§8): write-time tier enforcement, active-set
 * scope evaluation (incl. Aisha's composite Ramadan scope), the validator's
 * severity-scoped mechanics, and the never-relax-Inviolable rule.
 */
class ConstraintGraphEngineTest {
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
                "beef" to IngredientFacts("beef", categories = setOf("meat", "non_halal_risk")),
                "bacon" to
                    IngredientFacts(
                        "bacon",
                        categories = setOf("meat", "pork", "non_halal"),
                        nutritionPer100g = mapOf("sodium_mg" to 1700.0),
                    ),
                "watermelon" to IngredientFacts("watermelon", categories = setOf("fruit", "hydrating")),
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
    private val fixedClock = EngineClock { "2026-06-09T22:00:00Z" } // a Tuesday evening in Toronto

    private fun engine(timezone: String = "America/Toronto") =
        ConstraintGraphEngine(
            store = store,
            foodData = foodData,
            clock = fixedClock,
            profileTimezone = { timezone },
        )

    private fun seedSukhi() {
        SukhiFixtures.constraints.forEach { store.rows[it.id] = it }
    }

    // ── §4 write-time tier enforcement (structured errors, never coercion) ──

    @Test
    fun write_rejectsPreferAboveMedicalTier() =
        runTest {
            val bad =
                SukhiFixtures.preferRajma.copy(
                    id = "bad-prefer",
                    severity = Severity.Medical,
                )
            val error = runCatching { engine().addConstraint(SukhiFixtures.PROFILE_ID, bad) }.exceptionOrNull()
            assertEquals("prefer_must_be_preference", (error as ConstraintWriteException).code)
        }

    @Test
    fun write_rejectsInviolableContextual() =
        runTest {
            val bad = AishaFixtures.ramadanFast.copy(id = "bad-ctx", severity = Severity.Inviolable)
            val error = runCatching { engine().addConstraint(AishaFixtures.PROFILE_ID, bad) }.exceptionOrNull()
            assertEquals("contextual_never_inviolable", (error as ConstraintWriteException).code)
        }

    @Test
    fun write_rejectsSoftLimitInviolable() =
        runTest {
            val bad =
                SukhiFixtures.limitSodium.copy(id = "bad-limit", severity = Severity.Inviolable)
            val error = runCatching { engine().addConstraint(SukhiFixtures.PROFILE_ID, bad) }.exceptionOrNull()
            assertEquals("soft_limit_never_inviolable", (error as ConstraintWriteException).code)
        }

    @Test
    fun write_acceptsValidConstraint_andAssignsId() =
        runTest {
            val record =
                engine().addConstraint(
                    SukhiFixtures.PROFILE_ID,
                    SukhiFixtures.avoidBeef.copy(id = ""),
                )
            assertTrue(record.id.startsWith("constraint-"))
            assertNotNull(store.rows[record.id])
        }

    // ── §5 active-set computation ────────────────────────────────────────

    @Test
    fun activeSet_tuesdayVegetarianIsActiveOnTuesdayOnly() =
        runTest {
            seedSukhi()
            // 2026-06-09 is a Tuesday; 22:00Z = 18:00 Toronto.
            val tuesday = engine().queryActive(SukhiFixtures.PROFILE_ID, "2026-06-09T22:00:00Z")
            assertTrue(tuesday.constraints.any { it.id == SukhiFixtures.tuesdayVegetarian.id })
            val wednesday = engine().queryActive(SukhiFixtures.PROFILE_ID, "2026-06-10T22:00:00Z")
            assertFalse(wednesday.constraints.any { it.id == SukhiFixtures.tuesdayVegetarian.id })
        }

    @Test
    fun activeSet_flareScopedAvoidActivatesWithContextualFlag() =
        runTest {
            seedSukhi()
            val before = engine().queryActive(SukhiFixtures.PROFILE_ID)
            assertFalse(before.constraints.any { it.id == SukhiFixtures.avoidAlliumsDuringFlare.id })

            val withFlare = engine()
            withFlare.setContextualState(SukhiFixtures.PROFILE_ID, flag = "ibs_flare")
            val after = withFlare.queryActive(SukhiFixtures.PROFILE_ID)
            assertTrue(after.constraints.any { it.id == SukhiFixtures.avoidAlliumsDuringFlare.id })
        }

    @Test
    fun activeSet_contextualStateExpiresEndOfDayByDefault() =
        runTest {
            seedSukhi()
            val subject = engine()
            subject.setContextualState(SukhiFixtures.PROFILE_ID, flag = "ibs_flare")
            // Two days later the default (1-day) expiry has passed.
            val later = subject.queryActive(SukhiFixtures.PROFILE_ID, "2026-06-11T23:00:00Z")
            assertFalse(later.constraints.any { it.id == SukhiFixtures.avoidAlliumsDuringFlare.id })
        }

    @Test
    fun activeSet_aishaCompositeRamadanScope() =
        runTest {
            AishaFixtures.constraints.forEach { store.rows[it.id] = it }
            val subject = engine(timezone = AishaFixtures.TIMEZONE)
            // Mid-Ramadan, midday Toronto (17:00Z = 12:00 EST): fast active.
            val fastingNoon = subject.queryActive(AishaFixtures.PROFILE_ID, "2026-03-02T17:00:00Z")
            assertTrue(fastingNoon.constraints.any { it.id == AishaFixtures.ramadanFast.id })
            // Mid-Ramadan, 22:30 local (after sunset): fast inactive, iftar require active.
            val afterSunset = subject.queryActive(AishaFixtures.PROFILE_ID, "2026-03-03T03:30:00Z")
            assertFalse(afterSunset.constraints.any { it.id == AishaFixtures.ramadanFast.id })
            assertTrue(afterSunset.constraints.any { it.id == AishaFixtures.iftarHydration.id })
            // After Ramadan ends: neither fast nor iftar require.
            val april = subject.queryActive(AishaFixtures.PROFILE_ID, "2026-04-10T17:00:00Z")
            assertFalse(april.constraints.any { it.id == AishaFixtures.ramadanFast.id })
            assertFalse(april.constraints.any { it.id == AishaFixtures.iftarHydration.id })
            // Halal is active in every one of those moments.
            listOf(fastingNoon, afterSunset, april).forEach { set ->
                assertTrue(set.constraints.any { it.id == AishaFixtures.halalObservance.id })
            }
        }

    @Test
    fun activeSet_removedConstraintLeavesActiveSetButStaysInQueryAll() =
        runTest {
            seedSukhi()
            val subject = engine()
            subject.removeConstraint(SukhiFixtures.PROFILE_ID, SukhiFixtures.avoidBeef.id)
            val active = subject.queryActive(SukhiFixtures.PROFILE_ID)
            assertFalse(active.constraints.any { it.id == SukhiFixtures.avoidBeef.id })
            assertTrue(subject.queryAll(SukhiFixtures.PROFILE_ID).any { it.id == SukhiFixtures.avoidBeef.id })
        }

    // ── §7 validator mechanics ───────────────────────────────────────────

    private fun meal(vararg names: String) =
        SuggestionInput(
            mealName = "test meal",
            ingredients = names.map { SuggestionIngredient(it, 100.0, "g") },
        )

    @Test
    fun validator_passesCleanMeal() =
        runTest {
            seedSukhi()
            val result =
                engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("masoor dal", "spinach"))
            assertTrue(result.passed)
            assertTrue(result.violations.isEmpty())
        }

    @Test
    fun validator_rejectsCategoricalAvoidDuringFlare() =
        runTest {
            seedSukhi()
            val subject = engine()
            subject.setContextualState(SukhiFixtures.PROFILE_ID, flag = "ibs_flare")
            val result = subject.validateSuggestion(SukhiFixtures.PROFILE_ID, meal("onion", "spinach"))
            assertFalse(result.passed)
            assertEquals(SukhiFixtures.avoidAlliumsDuringFlare.id, result.violations.single().constraintId)
            // Once the flare expires the same meal passes — scope, not luck.
            val calm =
                subject.validateSuggestion(
                    SukhiFixtures.PROFILE_ID,
                    meal("onion", "spinach"),
                    atTimeIso = "2026-06-12T22:00:00Z",
                )
            assertTrue(calm.passed)
        }

    @Test
    fun validator_hardLimitViolation_andSoftLimitDisclosure() =
        runTest {
            val hardSodium =
                SukhiFixtures.limitSodium.copy(
                    id = "hard-sodium",
                    payload =
                        LimitPayload(
                            target = LimitTarget(kind = "nutritional_property", value = "sodium_mg"),
                            ceiling = Threshold(value = 2000.0, unit = "mg"),
                            window = "per_day",
                            hardOrSoft = "hard",
                        ),
                )
            store.rows[hardSodium.id] = hardSodium
            val result =
                engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("soy sauce"))
            assertFalse(result.passed)
            assertTrue(
                result.violations
                    .single()
                    .reason
                    .contains("sodium"),
            )

            store.rows.clear()
            val softSodium =
                hardSodium.copy(
                    id = "soft-sodium",
                    payload =
                        (hardSodium.payload as LimitPayload).copy(hardOrSoft = "soft"),
                    severity = Severity.Medical,
                )
            store.rows[softSodium.id] = softSodium
            val soft = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("soy sauce"))
            assertTrue(soft.passed)
            assertTrue(soft.disclosureNotes.isNotEmpty())
        }

    @Test
    fun validator_unknownIngredient_inviolableRejectsWithoutAiCall() =
        runTest {
            val inviolablePeanut =
                SukhiFixtures.avoidBeef.copy(
                    id = "inviolable-peanut",
                    severity = Severity.Inviolable,
                    payload =
                        ai.cuizine.engine.types.AvoidPayload(
                            target =
                                ai.cuizine.engine.types
                                    .AvoidTarget(kind = "category", value = "peanut"),
                        ),
                )
            store.rows[inviolablePeanut.id] = inviolablePeanut
            foodData.aiAnswer = IngredientFacts("mystery", categories = setOf("snack"))
            val result =
                engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("mystery snack mix"))
            assertFalse(result.passed)
            // The safety floor: no AI categorization for Inviolable checks.
            assertEquals(0, foodData.aiCalls)
        }

    @Test
    fun validator_unknownIngredient_medicalGetsAiFallbackWithDisclosure() =
        runTest {
            seedSukhi() // medical sodium/carb limits + flare avoid (inactive)
            foodData.aiAnswer = IngredientFacts("ramps", categories = setOf("allium", "vegetable"))
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("ramps"))
            assertTrue(foodData.aiCalls > 0)
            assertTrue(result.disclosureNotes.any { it.contains("not 100% sure") })
        }

    @Test
    fun validator_fastingStateRejectsAnyMealInWindow() =
        runTest {
            AishaFixtures.constraints.forEach { store.rows[it.id] = it }
            val subject = engine(timezone = AishaFixtures.TIMEZONE)
            val result =
                subject.validateSuggestion(
                    AishaFixtures.PROFILE_ID,
                    meal("watermelon"),
                    atTimeIso = "2026-03-02T17:00:00Z", // midday during Ramadan
                )
            assertFalse(result.passed)
            assertTrue(result.violations.any { it.constraintType == ConstraintType.Contextual })
            // The same meal after sunset passes (iftar hydration satisfied).
            val iftar =
                subject.validateSuggestion(
                    AishaFixtures.PROFILE_ID,
                    meal("watermelon"),
                    atTimeIso = "2026-03-03T03:30:00Z",
                )
            assertTrue(iftar.passed)
        }

    @Test
    fun validator_preferMisalignmentIsDisclosureNeverRejection() =
        runTest {
            store.rows[SukhiFixtures.preferRajma.id] = SukhiFixtures.preferRajma
            val result = engine().validateSuggestion(SukhiFixtures.PROFILE_ID, meal("spinach"))
            assertTrue(result.passed)
            assertTrue(result.disclosureNotes.isNotEmpty())
        }

    // ── §8 relaxation rules ──────────────────────────────────────────────

    @Test
    fun resolveConflict_sessionRelaxationSkipsConstraint_untilCleared() =
        runTest {
            seedSukhi()
            val subject = engine()
            subject.setContextualState(SukhiFixtures.PROFILE_ID, flag = "ibs_flare")
            val flareMeal = meal("onion", "spinach")
            assertFalse(subject.validateSuggestion(SukhiFixtures.PROFILE_ID, flareMeal).passed)

            subject.resolveConflict(
                SukhiFixtures.PROFILE_ID,
                conflictId = "test-conflict",
                userChoice =
                    ConflictChoice(
                        relaxedConstraintIds = listOf(SukhiFixtures.avoidAlliumsDuringFlare.id),
                        optionLabel = "Onion is okay tonight",
                    ),
            )
            assertTrue(subject.validateSuggestion(SukhiFixtures.PROFILE_ID, flareMeal).passed)

            // Session-scoped only: clearing restores the constraint (§8 Step 4).
            subject.clearSessionRelaxations(SukhiFixtures.PROFILE_ID)
            assertFalse(subject.validateSuggestion(SukhiFixtures.PROFILE_ID, flareMeal).passed)
        }

    @Test
    fun resolveConflict_neverAcceptsInviolableRelaxation() =
        runTest {
            val inviolable = SukhiFixtures.avoidBeef.copy(id = "inviolable-beef", severity = Severity.Inviolable)
            store.rows[inviolable.id] = inviolable
            val error =
                runCatching {
                    engine().resolveConflict(
                        SukhiFixtures.PROFILE_ID,
                        conflictId = "c",
                        userChoice =
                            ConflictChoice(
                                relaxedConstraintIds = listOf(inviolable.id),
                                optionLabel = "never",
                            ),
                    )
                }.exceptionOrNull()
            assertEquals("inviolable_relaxation", (error as ConstraintWriteException).code)
        }

    // ── queryConflicts structural detection ──────────────────────────────

    @Test
    fun queryConflicts_detectsRequireAvoidOverlap() =
        runTest {
            val avoidDairy =
                SukhiFixtures.avoidBeef.copy(
                    id = "avoid-dairy",
                    payload =
                        ai.cuizine.engine.types.AvoidPayload(
                            target =
                                ai.cuizine.engine.types
                                    .AvoidTarget(kind = "category", value = "dairy"),
                        ),
                )
            val requireDairy =
                Constraint(
                    id = "require-dairy",
                    profileId = SukhiFixtures.PROFILE_ID,
                    type = ConstraintType.Require,
                    severity = Severity.Medical,
                    humanLabel = "Dairy at every meal",
                    scope = avoidDairy.scope,
                    payload =
                        ai.cuizine.engine.types.RequirePayload(
                            target =
                                ai.cuizine.engine.types
                                    .RequireTarget(kind = "category", value = "dairy"),
                            window = "single_meal",
                        ),
                    provenance = avoidDairy.provenance,
                )
            store.rows[avoidDairy.id] = avoidDairy
            store.rows[requireDairy.id] = requireDairy
            val conflicts = engine().queryConflicts(SukhiFixtures.PROFILE_ID)
            assertEquals(1, conflicts.size)
            assertFalse(conflicts.single().involvesInviolable)
        }
}
