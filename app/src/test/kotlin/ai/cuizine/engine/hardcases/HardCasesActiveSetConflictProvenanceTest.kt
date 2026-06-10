package ai.cuizine.engine.hardcases

import ai.cuizine.engine.ConflictChoice
import ai.cuizine.engine.ConstraintChanges
import ai.cuizine.engine.ConstraintGraphEngine
import ai.cuizine.engine.conflict.ConflictAnalyzer
import ai.cuizine.engine.ports.ConstraintStore
import ai.cuizine.engine.ports.EngineClock
import ai.cuizine.engine.ports.FoodDataPort
import ai.cuizine.engine.ports.IngredientFacts
import ai.cuizine.engine.ports.IngredientResolution
import ai.cuizine.engine.types.ConstraintType
import ai.cuizine.engine.types.ContextualPayload
import ai.cuizine.engine.types.ContextualState
import ai.cuizine.engine.types.Severity
import ai.cuizine.engine.types.SuggestionIngredient
import ai.cuizine.engine.types.SuggestionInput
import ai.cuizine.engine.types.TemporalScope
import ai.cuizine.engine.types.ValidationResult
import ai.cuizine.engine.types.Violation
import ai.cuizine.shared.fixtures.SukhiFixtures
import ai.cuizine.shared.types.Constraint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hard-cases suite, categories 4, 6, and 7 (`testing-strategy.md` §5):
 * active-set computation and cache invalidation (`constraint-engine-spec.md`
 * §5/§6), the conflict resolution algorithm's mechanical pieces (§8 +
 * [ConflictAnalyzer]), and provenance integrity across every constraint
 * operation (§9). Cache behavior is observed strictly through the public
 * API — a behavior change at the same `atTime` — never through internals.
 */
class HardCasesActiveSetConflictProvenanceTest {
    // ── Test ports (canonical pattern from ConstraintGraphEngineTest) ────

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
                "beef" to IngredientFacts("beef", categories = setOf("meat", "non_halal_risk")),
            )

        override suspend fun lookup(ingredientName: String): IngredientResolution =
            known[ingredientName.lowercase()]?.let { IngredientResolution.Known(it, "high_bundle") }
                ?: IngredientResolution.Unknown

        override suspend fun categorizeWithAi(ingredientName: String): IngredientFacts? = null
    }

    private val store = InMemoryStore()
    private val foodData = FixtureFoodData()
    private val fixedClock = EngineClock { NOW } // a Tuesday evening in Toronto

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

    private fun meal(vararg names: String) =
        SuggestionInput(
            mealName = "test meal",
            ingredients = names.map { SuggestionIngredient(it, 100.0, "g") },
        )

    // ── Conflict-analyzer helpers (fixture-style builders, no new personas) ──

    private fun violationOf(constraint: Constraint) =
        Violation(
            constraintId = constraint.id,
            constraintType = constraint.type,
            severity = constraint.severity,
            reason = "test rejection",
        )

    private fun rejection(vararg blamed: Constraint) =
        ValidationResult(passed = false, violations = blamed.map(::violationOf))

    private fun analysisOf(vararg blamed: Constraint) =
        ConflictAnalyzer.FailureAnalysis(
            blamedConstraintIds = blamed.map { it.id },
            isNarrow = blamed.size == 1,
        )

    private fun medicalLimit(id: String) = SukhiFixtures.limitSodium.copy(id = id)

    private val inviolableBeef =
        SukhiFixtures.avoidBeef.copy(id = "hardcase-inviolable-beef", severity = Severity.Inviolable)

    /** A Type-5 contextual driver, shaped like the engine's own contextual records (§3 Type 5). */
    private val contextualFlare =
        SukhiFixtures.avoidBeef.copy(
            id = "hardcase-ctx-flare",
            type = ConstraintType.Contextual,
            severity = Severity.Preference,
            humanLabel = "ibs flare",
            payload = ContextualPayload(state = ContextualState(flag = "ibs_flare")),
        )

    // ════ Category 4: active-set computation (spec §5/§6) ════════════════

    @Test
    fun activeSet_repeatedIdenticalQueryReturnsCachedResult() =
        runTest {
            // §5: the active set is cached at the query level. A row inserted
            // behind the engine's back (no engine write, so no invalidation)
            // must NOT appear when the identical query repeats.
            seedSukhi()
            val subject = engine()
            val first = subject.queryActive(SukhiFixtures.PROFILE_ID, NOW)
            val sneaky = SukhiFixtures.avoidBeef.copy(id = "hardcase-sneaky-avoid")
            store.rows[sneaky.id] = sneaky
            val second = subject.queryActive(SukhiFixtures.PROFILE_ID, NOW)
            assertEquals(first, second)
            assertFalse(second.constraints.any { it.id == sneaky.id })
        }

    @Test
    fun activeSet_addConstraintInvalidatesCacheAtSameAtTime() =
        runTest {
            // §5: "invalidation on any constraint graph write" — same atTime,
            // behavior changes after addConstraint.
            seedSukhi()
            val subject = engine()
            val before = subject.queryActive(SukhiFixtures.PROFILE_ID, NOW)
            assertFalse(before.constraints.any { it.id == "hardcase-added-avoid" })
            val added =
                subject.addConstraint(
                    SukhiFixtures.PROFILE_ID,
                    SukhiFixtures.avoidBeef.copy(id = "hardcase-added-avoid"),
                )
            val after = subject.queryActive(SukhiFixtures.PROFILE_ID, NOW)
            assertTrue(after.constraints.any { it.id == added.id })
        }

    @Test
    fun activeSet_updateConstraintInvalidatesCacheAtSameAtTime() =
        runTest {
            // §5/§6: a scope edit through updateConstraint is visible at the
            // very same atTime the cache was primed for.
            seedSukhi()
            val subject = engine()
            val before = subject.queryActive(SukhiFixtures.PROFILE_ID, NOW)
            assertTrue(before.constraints.any { it.id == SukhiFixtures.tuesdayVegetarian.id })
            subject.updateConstraint(
                SukhiFixtures.PROFILE_ID,
                SukhiFixtures.tuesdayVegetarian.id,
                ConstraintChanges(
                    scope =
                        SukhiFixtures.tuesdayVegetarian.scope.copy(
                            temporal = TemporalScope(kind = "weekly", weekdays = listOf("wednesday")),
                        ),
                    reason = "moved the vegetarian day",
                ),
            )
            val after = subject.queryActive(SukhiFixtures.PROFILE_ID, NOW)
            assertFalse(after.constraints.any { it.id == SukhiFixtures.tuesdayVegetarian.id })
        }

    @Test
    fun activeSet_removeConstraintInvalidatesCacheAtSameAtTime() =
        runTest {
            // §5: removal is a graph write — the cached set for this minute
            // must not survive it.
            seedSukhi()
            val subject = engine()
            val before = subject.queryActive(SukhiFixtures.PROFILE_ID, NOW)
            assertTrue(before.constraints.any { it.id == SukhiFixtures.avoidBeef.id })
            subject.removeConstraint(SukhiFixtures.PROFILE_ID, SukhiFixtures.avoidBeef.id)
            val after = subject.queryActive(SukhiFixtures.PROFILE_ID, NOW)
            assertFalse(after.constraints.any { it.id == SukhiFixtures.avoidBeef.id })
        }

    @Test
    fun activeSet_setContextualStateInvalidatesCacheAtSameAtTime() =
        runTest {
            // §5: "invalidation on … contextual state change" — the flare flag
            // flips the flare-scoped avoid at the identical atTime.
            seedSukhi()
            val subject = engine()
            val before = subject.queryActive(SukhiFixtures.PROFILE_ID, NOW)
            assertFalse(before.constraints.any { it.id == SukhiFixtures.avoidAlliumsDuringFlare.id })
            subject.setContextualState(SukhiFixtures.PROFILE_ID, flag = "ibs_flare")
            val after = subject.queryActive(SukhiFixtures.PROFILE_ID, NOW)
            assertTrue(after.constraints.any { it.id == SukhiFixtures.avoidAlliumsDuringFlare.id })
        }

    @Test
    fun activeSet_explicitAtTimeInDifferentMinuteRecomputesPastCachedNow() =
        runTest {
            // §5: the cache is keyed to the query moment — an explicit atTime
            // one minute later is a different moment and recomputes.
            seedSukhi()
            val subject = engine()
            subject.queryActive(SukhiFixtures.PROFILE_ID) // primes the cache at now
            val sneaky = SukhiFixtures.avoidBeef.copy(id = "hardcase-sneaky-avoid")
            store.rows[sneaky.id] = sneaky
            val nextMinute = subject.queryActive(SukhiFixtures.PROFILE_ID, "2026-06-09T22:01:00Z")
            assertTrue(nextMinute.constraints.any { it.id == sneaky.id })
        }

    @Test
    fun queryByType_returnsOnlyActiveConstraintsOfRequestedType() =
        runTest {
            // §6 queryByType: a filtered active set — avoids only, no limits
            // or prefers.
            seedSukhi()
            val avoids = engine().queryByType(SukhiFixtures.PROFILE_ID, ConstraintType.Avoid, NOW)
            assertTrue(avoids.constraints.isNotEmpty())
            assertTrue(avoids.constraints.all { it.type == ConstraintType.Avoid })
            assertTrue(avoids.constraints.any { it.id == SukhiFixtures.avoidBeef.id })
            assertFalse(avoids.constraints.any { it.id == SukhiFixtures.limitSodium.id })
        }

    @Test
    fun attribution_temporalDimensionDecisiveForWeeklyScopedConstraint() =
        runTest {
            // §5 step 4: attribution names the scope dimensions that were
            // decisive — Tuesday-vegetarian activates on temporal scope.
            seedSukhi()
            val active = engine().queryActive(SukhiFixtures.PROFILE_ID, NOW)
            val entry = active.entries.single { it.constraint.id == SukhiFixtures.tuesdayVegetarian.id }
            assertEquals(listOf("temporal"), entry.decisiveDimensions)
        }

    @Test
    fun attribution_contextualDimensionDecisiveForFlareScopedConstraint() =
        runTest {
            // §5 step 4: the flare-scoped avoid owes its activation to the
            // contextual dimension.
            seedSukhi()
            val subject = engine()
            subject.setContextualState(SukhiFixtures.PROFILE_ID, flag = "ibs_flare")
            val active = subject.queryActive(SukhiFixtures.PROFILE_ID, NOW)
            val entry = active.entries.single { it.constraint.id == SukhiFixtures.avoidAlliumsDuringFlare.id }
            assertEquals(listOf("contextual"), entry.decisiveDimensions)
        }

    @Test
    fun attribution_decisiveDimensionsEmptyForAlwaysOnConstraint() =
        runTest {
            // §5: an always-on constraint (no temporal, contextual, or
            // location restriction) has no decisive dimension to attribute.
            seedSukhi()
            val active = engine().queryActive(SukhiFixtures.PROFILE_ID, NOW)
            val entry = active.entries.single { it.constraint.id == SukhiFixtures.avoidBeef.id }
            assertTrue(entry.decisiveDimensions.isEmpty())
        }

    @Test
    fun activeSet_expiredContextualConstraintIsExcluded() =
        runTest {
            // §3 Type 5 / §5: a contextual constraint past its expiry is out
            // of the active set; before expiry it is in.
            seedSukhi()
            val subject = engine()
            val ctx =
                subject.setContextualState(
                    SukhiFixtures.PROFILE_ID,
                    flag = "ibs_flare",
                    expiresAtIso = "2026-06-10T00:00:00Z",
                )
            val beforeExpiry = subject.queryActive(SukhiFixtures.PROFILE_ID, "2026-06-09T23:00:00Z")
            assertTrue(beforeExpiry.constraints.any { it.id == ctx.id })
            val afterExpiry = subject.queryActive(SukhiFixtures.PROFILE_ID, "2026-06-10T06:00:00Z")
            assertFalse(afterExpiry.constraints.any { it.id == ctx.id })
        }

    @Test
    fun activeSet_removedConstraintExcludedFromQueryActiveButPresentInQueryAll() =
        runTest {
            // §6 removeConstraint: soft delete — out of the active set, still
            // in the full graph for audit (§9).
            seedSukhi()
            val subject = engine()
            subject.removeConstraint(SukhiFixtures.PROFILE_ID, SukhiFixtures.avoidBeef.id)
            val active = subject.queryActive(SukhiFixtures.PROFILE_ID, NOW)
            assertFalse(active.constraints.any { it.id == SukhiFixtures.avoidBeef.id })
            assertTrue(subject.queryAll(SukhiFixtures.PROFILE_ID).any { it.id == SukhiFixtures.avoidBeef.id })
        }

    // ════ Category 6: conflict resolution (spec §8 + ConflictAnalyzer) ═══

    @Test
    fun analyzeFailures_allRejectionsBlamingSameConstraintIsNarrow() {
        // §8 Step 1: "if all three rejections were caused by the same
        // constraint, the conflict is clear and narrow."
        val analysis =
            ConflictAnalyzer.analyzeFailures(
                listOf(
                    rejection(SukhiFixtures.limitSodium),
                    rejection(SukhiFixtures.limitSodium),
                    rejection(SukhiFixtures.limitSodium),
                ),
            )
        assertTrue(analysis.isNarrow)
        assertEquals(listOf(SukhiFixtures.limitSodium.id), analysis.blamedConstraintIds)
    }

    @Test
    fun analyzeFailures_differentBlamedConstraintsAcrossRejectionsIsStructural() {
        // §8 Step 1: rejections caused by different constraints mean a
        // structural conflict, not a narrow one.
        val analysis =
            ConflictAnalyzer.analyzeFailures(
                listOf(
                    rejection(SukhiFixtures.limitSodium),
                    rejection(SukhiFixtures.avoidBeef),
                    rejection(SukhiFixtures.limitSodium),
                ),
            )
        assertFalse(analysis.isNarrow)
    }

    @Test
    fun analyzeFailures_blameOrderedByRejectionFrequencyDescending() {
        // §8 Step 1: blame is ranked by how often each constraint caused a
        // rejection across the failed attempts.
        val a = medicalLimit("hardcase-freq-a")
        val b = medicalLimit("hardcase-freq-b")
        val c = medicalLimit("hardcase-freq-c")
        val analysis =
            ConflictAnalyzer.analyzeFailures(
                listOf(
                    rejection(a, c, b),
                    rejection(a, c),
                    rejection(a),
                ),
            )
        assertEquals(listOf(a.id, c.id, b.id), analysis.blamedConstraintIds)
    }

    @Test
    fun planResolution_returnsNoMealFoundWhenAnyBlamedConstraintIsInviolable() {
        // §8: "if an Inviolable constraint is part of the conflict … the
        // algorithm skips Step 3 entirely" — even when it is not the most
        // frequently blamed constraint.
        val plan =
            ConflictAnalyzer.planResolution(
                blamed = listOf(SukhiFixtures.limitSodium, inviolableBeef),
                analysis = analysisOf(SukhiFixtures.limitSodium, inviolableBeef),
            )
        assertTrue(plan is ConflictAnalyzer.ResolutionPlan.NoMealFound)
    }

    @Test
    fun planResolution_noMealFoundListsExactlyTheInviolableIds() {
        // §8: the honest fallback names the Inviolable constraints — and only
        // those — so the message can explain itself.
        val plan =
            ConflictAnalyzer.planResolution(
                blamed = listOf(inviolableBeef, SukhiFixtures.limitSodium),
                analysis = analysisOf(inviolableBeef, SukhiFixtures.limitSodium),
            )
        val noMeal = plan as ConflictAnalyzer.ResolutionPlan.NoMealFound
        assertEquals(listOf(inviolableBeef.id), noMeal.inviolableConstraintIds)
    }

    @Test
    fun planResolution_capsRelaxOptionsAtThree() {
        // §8 Step 3: "present three options to the user" — a fourth relaxable
        // constraint does not produce a fourth relax option.
        val blamed = listOf(medicalLimit("m1"), medicalLimit("m2"), medicalLimit("m3"), medicalLimit("m4"))
        val plan = ConflictAnalyzer.planResolution(blamed, analysisOf(*blamed.toTypedArray()))
        val surface = plan as ConflictAnalyzer.ResolutionPlan.SurfaceToUser
        assertEquals(4, surface.options.size) // 3 relax options + none-of-these
        assertEquals(
            listOf("m1", "m2", "m3"),
            surface.options.dropLast(1).map { it.relaxesConstraintIds.single() },
        )
    }

    @Test
    fun planResolution_noneOfTheseIsAlwaysLastAndRelaxesNothing() {
        // §8 Step 3: "none of these — I'll figure it out myself" is always a
        // valid option, always present, and never relaxes anything.
        val plan =
            ConflictAnalyzer.planResolution(
                blamed = listOf(SukhiFixtures.limitSodium),
                analysis = analysisOf(SukhiFixtures.limitSodium),
            )
        val last = (plan as ConflictAnalyzer.ResolutionPlan.SurfaceToUser).options.last()
        assertEquals(ConflictAnalyzer.NONE_OF_THESE_OPTION_ID, last.id)
        assertTrue(last.relaxesConstraintIds.isEmpty())
    }

    @Test
    fun planResolution_medicalAndReligiousCulturalAreCoEqualRelaxOptions() {
        // §8 Step 3: Medical and Religious & Cultural are the surfaced tiers,
        // co-equal — both appear as relax options.
        val plan =
            ConflictAnalyzer.planResolution(
                blamed = listOf(SukhiFixtures.limitSodium, SukhiFixtures.avoidBeef),
                analysis = analysisOf(SukhiFixtures.limitSodium, SukhiFixtures.avoidBeef),
            )
        val surface = plan as ConflictAnalyzer.ResolutionPlan.SurfaceToUser
        val relaxedIds = surface.options.flatMap { it.relaxesConstraintIds }
        assertTrue(SukhiFixtures.limitSodium.id in relaxedIds)
        assertTrue(SukhiFixtures.avoidBeef.id in relaxedIds)
    }

    @Test
    fun planResolution_preferenceTierNeverAppearsAsRelaxOption() {
        // §8 Step 2 vs Step 3: preferences relax silently and never reach the
        // user-facing option list.
        val plan =
            ConflictAnalyzer.planResolution(
                blamed = listOf(SukhiFixtures.limitSodium, SukhiFixtures.preferRajma),
                analysis = analysisOf(SukhiFixtures.limitSodium, SukhiFixtures.preferRajma),
            )
        val surface = plan as ConflictAnalyzer.ResolutionPlan.SurfaceToUser
        assertTrue(surface.options.none { SukhiFixtures.preferRajma.id in it.relaxesConstraintIds })
    }

    @Test
    fun planResolution_contextualDriverAddsClarificationOptionWhenRoomRemains() {
        // §8 edge case: a contextual-driven conflict surfaces the specialized
        // "relax or clarify the contextual state" option.
        val plan =
            ConflictAnalyzer.planResolution(
                blamed = listOf(SukhiFixtures.limitSodium, contextualFlare),
                analysis = analysisOf(SukhiFixtures.limitSodium, contextualFlare),
            )
        val surface = plan as ConflictAnalyzer.ResolutionPlan.SurfaceToUser
        val clarify = surface.options.single { it.isContextualClarification }
        assertEquals(listOf(contextualFlare.id), clarify.relaxesConstraintIds)
    }

    @Test
    fun planResolution_contextualClarificationOmittedWhenThreeRelaxOptionsFill() {
        // §8 Step 3: the option list never exceeds three (plus none-of-these);
        // a full list leaves no room for the clarification option.
        val blamed = listOf(medicalLimit("m1"), medicalLimit("m2"), medicalLimit("m3"), contextualFlare)
        val plan = ConflictAnalyzer.planResolution(blamed, analysisOf(*blamed.toTypedArray()))
        val surface = plan as ConflictAnalyzer.ResolutionPlan.SurfaceToUser
        assertTrue(surface.options.none { it.isContextualClarification })
        assertEquals(4, surface.options.size) // 3 relax options + none-of-these
    }

    @Test
    fun preferenceRelaxationCandidates_selectsOnlyPreferenceTierConstraints() {
        // §8 Step 2: silent relaxation candidates are exactly the active
        // preference-tier constraints — never Medical or Religious & Cultural.
        val candidates = ConflictAnalyzer.preferenceRelaxationCandidates(SukhiFixtures.constraints)
        assertEquals(
            listOf(SukhiFixtures.preferRajma.id, SukhiFixtures.preferAloo.id),
            candidates.map { it.id },
        )
    }

    @Test
    fun resolveConflict_relaxingBlamedConstraintLetsTheSameMealPass() =
        runTest {
            // §8 Step 4: the recorded choice unsticks validation — the meal
            // that failed before the relaxation passes after it.
            seedSukhi()
            val subject = engine()
            subject.setContextualState(SukhiFixtures.PROFILE_ID, flag = "ibs_flare")
            val flareMeal = meal("onion", "spinach")
            assertFalse(subject.validateSuggestion(SukhiFixtures.PROFILE_ID, flareMeal).passed)
            subject.resolveConflict(
                SukhiFixtures.PROFILE_ID,
                conflictId = "hardcase-conflict",
                userChoice =
                    ConflictChoice(
                        relaxedConstraintIds = listOf(SukhiFixtures.avoidAlliumsDuringFlare.id),
                        optionLabel = "Onion is okay tonight",
                    ),
            )
            assertTrue(subject.validateSuggestion(SukhiFixtures.PROFILE_ID, flareMeal).passed)
        }

    @Test
    fun clearSessionRelaxations_restoresEnforcementOfRelaxedConstraint() =
        runTest {
            // §8 Step 4: the relaxation is scoped to this session only —
            // clearing the session restores the constraint.
            seedSukhi()
            val subject = engine()
            subject.setContextualState(SukhiFixtures.PROFILE_ID, flag = "ibs_flare")
            val flareMeal = meal("onion", "spinach")
            subject.resolveConflict(
                SukhiFixtures.PROFILE_ID,
                conflictId = "hardcase-conflict",
                userChoice =
                    ConflictChoice(
                        relaxedConstraintIds = listOf(SukhiFixtures.avoidAlliumsDuringFlare.id),
                        optionLabel = "Onion is okay tonight",
                    ),
            )
            assertTrue(subject.validateSuggestion(SukhiFixtures.PROFILE_ID, flareMeal).passed)
            subject.clearSessionRelaxations(SukhiFixtures.PROFILE_ID)
            assertFalse(subject.validateSuggestion(SukhiFixtures.PROFILE_ID, flareMeal).passed)
        }

    @Test
    fun repeatedIdenticalRelaxation_neverGraduatesToPermanentRemoval() =
        runTest {
            // §8 edge case: picking the same relaxation night after night does
            // not silently remove the constraint — every new session starts
            // with it enforced and needs a fresh relaxation.
            seedSukhi()
            val subject = engine()
            subject.setContextualState(SukhiFixtures.PROFILE_ID, flag = "ibs_flare")
            val flareMeal = meal("onion", "spinach")
            val sameChoice =
                ConflictChoice(
                    relaxedConstraintIds = listOf(SukhiFixtures.avoidAlliumsDuringFlare.id),
                    optionLabel = "Onion is okay tonight",
                )
            repeat(2) {
                subject.resolveConflict(SukhiFixtures.PROFILE_ID, "hardcase-conflict", sameChoice)
                assertTrue(subject.validateSuggestion(SukhiFixtures.PROFILE_ID, flareMeal).passed)
                subject.clearSessionRelaxations(SukhiFixtures.PROFILE_ID)
                assertFalse(subject.validateSuggestion(SukhiFixtures.PROFILE_ID, flareMeal).passed)
            }
        }

    @Test
    fun resolveConflict_doesNotModifyTheConstraintGraph() =
        runTest {
            // §8 Step 4 / "what the algorithm does not try to do": every
            // relaxation is session-scoped — the stored record is untouched.
            seedSukhi()
            val subject = engine()
            val storedBefore = store.rows[SukhiFixtures.avoidAlliumsDuringFlare.id]
            subject.resolveConflict(
                SukhiFixtures.PROFILE_ID,
                conflictId = "hardcase-conflict",
                userChoice =
                    ConflictChoice(
                        relaxedConstraintIds = listOf(SukhiFixtures.avoidAlliumsDuringFlare.id),
                        optionLabel = "Onion is okay tonight",
                    ),
            )
            assertEquals(storedBefore, store.rows[SukhiFixtures.avoidAlliumsDuringFlare.id])
            assertEquals(SukhiFixtures.constraints.size, subject.queryAll(SukhiFixtures.PROFILE_ID).size)
        }

    // ════ Category 7: provenance and audit (spec §9/§6) ══════════════════

    @Test
    fun provenance_addConstraintPreservesTheFullProvenanceRecord() =
        runTest {
            // §9: a constraint without provenance is not allowed to exist —
            // and the engine stores the record exactly as supplied.
            val record =
                engine().addConstraint(
                    SukhiFixtures.PROFILE_ID,
                    SukhiFixtures.avoidBeef.copy(id = ""),
                )
            assertEquals(SukhiFixtures.avoidBeef.provenance, store.rows[record.id]?.provenance)
        }

    @Test
    fun provenance_originalPhrasingPreservedVerbatimThroughAdd() =
        runTest {
            // §9: original_phrasing is the user's verbatim words — punctuation
            // and all — so disclosure can quote them honestly.
            val phrasing = "No beef — never; not even \"just a little bit\" for guests."
            val record =
                engine().addConstraint(
                    SukhiFixtures.PROFILE_ID,
                    SukhiFixtures.avoidBeef.copy(
                        id = "",
                        provenance = SukhiFixtures.avoidBeef.provenance.copy(originalPhrasing = phrasing),
                    ),
                )
            assertEquals(phrasing, store.rows[record.id]?.provenance?.originalPhrasing)
        }

    @Test
    fun provenance_updateConstraintAppendsUserEditedModificationEntry() =
        runTest {
            // §9 modification_history: every change is logged with its source
            // and reason; §6 updateConstraint is the user-edit write path.
            seedSukhi()
            val updated =
                engine().updateConstraint(
                    SukhiFixtures.PROFILE_ID,
                    SukhiFixtures.avoidBeef.id,
                    ConstraintChanges(severity = Severity.Medical, reason = "doctor asked for stricter handling"),
                )
            val entry = updated.provenance.modificationHistory.single()
            assertEquals("user_edited", entry.source)
            assertEquals("doctor asked for stricter handling", entry.reason)
        }

    @Test
    fun provenance_modificationEntryTimestampComesFromTheEngineClock() =
        runTest {
            // §9: the history entry records when the change happened — the
            // engine's clock, not a caller-supplied time.
            seedSukhi()
            val updated =
                engine().updateConstraint(
                    SukhiFixtures.PROFILE_ID,
                    SukhiFixtures.avoidBeef.id,
                    ConstraintChanges(severity = Severity.Medical, reason = "tighter"),
                )
            assertEquals(NOW, updated.provenance.modificationHistory.single().at)
        }

    @Test
    fun provenance_modificationHistoryIsAppendOnlyAcrossTwoUpdates() =
        runTest {
            // §9: modification_history is an append-only log — two updates
            // leave two entries in the order they happened.
            seedSukhi()
            val subject = engine()
            subject.updateConstraint(
                SukhiFixtures.PROFILE_ID,
                SukhiFixtures.avoidBeef.id,
                ConstraintChanges(severity = Severity.Medical, reason = "first edit"),
            )
            val updated =
                subject.updateConstraint(
                    SukhiFixtures.PROFILE_ID,
                    SukhiFixtures.avoidBeef.id,
                    ConstraintChanges(severity = Severity.ReligiousCultural, reason = "second edit"),
                )
            assertEquals(
                listOf("first edit", "second edit"),
                updated.provenance.modificationHistory.map { it.reason },
            )
        }

    @Test
    fun queryProvenance_returnsTheRecordForAKnownConstraint() =
        runTest {
            // §6 queryProvenance: "why is this on my list?" answers from the
            // stored record.
            seedSukhi()
            assertEquals(
                SukhiFixtures.avoidBeef.provenance,
                engine().queryProvenance(SukhiFixtures.avoidBeef.id),
            )
        }

    @Test
    fun queryProvenance_returnsNullForAnUnknownConstraintId() =
        runTest {
            // §6 queryProvenance: an id the graph has never seen has no record
            // to return.
            seedSukhi()
            assertNull(engine().queryProvenance("hardcase-no-such-constraint"))
        }

    @Test
    fun provenance_setContextualStateRecordsUserDirectSource() =
        runTest {
            // §9 source vocabulary: a user-reported state ("my gut is off") is
            // user-direct provenance.
            val ctx = engine().setContextualState(SukhiFixtures.PROFILE_ID, flag = "ibs_flare")
            assertEquals("user_direct", ctx.provenance.source)
        }

    @Test
    fun provenance_setContextualStateRecordsFreeTextUpdateFlow() =
        runTest {
            // §9 added_context: contextual states arrive through the free-text
            // update flow, and the record says so.
            val ctx = engine().setContextualState(SukhiFixtures.PROFILE_ID, flag = "ibs_flare")
            assertEquals("free_text_update", ctx.provenance.addedContext.flow)
        }

    @Test
    fun provenance_blankSourceIsRejectedAtAddConstraint() =
        runTest {
            // §9: "a constraint without provenance is a constraint the engine
            // cannot honestly explain, and the engine is not allowed to
            // produce those."
            val blankSource =
                SukhiFixtures.avoidBeef.copy(
                    id = "",
                    provenance = SukhiFixtures.avoidBeef.provenance.copy(source = ""),
                )
            val error =
                runCatching {
                    engine().addConstraint(SukhiFixtures.PROFILE_ID, blankSource)
                }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
            assertTrue(error!!.message!!.contains("provenance"))
        }

    @Test
    fun provenance_softRemovedConstraintRetainsItsProvenanceForAudit() =
        runTest {
            // §6 removeConstraint / §9: removal is a soft delete — the record
            // stays readable so the audit trail never loses its history.
            seedSukhi()
            val subject = engine()
            subject.removeConstraint(SukhiFixtures.PROFILE_ID, SukhiFixtures.avoidBeef.id)
            assertEquals(
                SukhiFixtures.avoidBeef.provenance,
                subject.queryProvenance(SukhiFixtures.avoidBeef.id),
            )
        }

    private companion object {
        /** The canonical fixed moment: Tuesday 2026-06-09, 18:00 in Toronto. */
        const val NOW = "2026-06-09T22:00:00Z"
    }
}
