package ai.cuizine.data.mock

import ai.cuizine.data.repository.EngineProfileRepository
import ai.cuizine.engine.ConstraintGraphEngine
import ai.cuizine.engine.ports.ConstraintStore
import ai.cuizine.engine.ports.EngineClock
import ai.cuizine.shared.fixtures.SukhiFixtures
import ai.cuizine.shared.types.Constraint
import ai.cuizine.shared.types.SuggestionValidation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Phase 3 seam, end to end: scripted Chef meals, REAL validation, REAL
 * conflict planning, REAL session relaxation — the integration the Today
 * surface runs on until the real Chef lands in Phase 5.
 */
class ValidatedMockSuggestionRepositoryTest {
    private class InMemoryStore : ConstraintStore {
        val rows = mutableMapOf<String, Constraint>()

        override suspend fun loadLive(profileId: String) = rows.values.filter { it.profileId == profileId }

        override suspend fun loadAll(profileId: String) = loadLive(profileId)

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
            rows.remove(constraintId)
        }
    }

    private fun subject(): ValidatedMockSuggestionRepository {
        val store = InMemoryStore()
        SukhiFixtures.constraints.forEach { constraint ->
            val rehomed =
                constraint.copy(
                    profileId = EngineProfileRepository.PRIMARY_PROFILE_ID,
                    scope =
                        constraint.scope.copy(
                            profile =
                                constraint.scope.profile.copy(
                                    profileId = EngineProfileRepository.PRIMARY_PROFILE_ID,
                                ),
                        ),
                )
            store.rows[rehomed.id] = rehomed
        }
        val engine =
            ConstraintGraphEngine(
                store = store,
                foodData = StaticFoodDataPort(),
                clock = EngineClock { "2026-06-11T22:00:00Z" }, // a Thursday
                profileTimezone = { "America/Toronto" },
            )
        return ValidatedMockSuggestionRepository(engine)
    }

    @Test
    fun cleanScriptedMeal_passesRealValidation() =
        runTest {
            val repo = subject()
            repo.requestSuggestion()
            val served = repo.observeCurrentSuggestion().first()
            assertNotNull(served)
            assertEquals(SukhiFixtures.masoorDal.id, served?.id)
            assertEquals(SuggestionValidation.Passed, served?.validation)
        }

    @Test
    fun scriptedBeefMeal_failsRealValidation_andRealConflictOptionsAppear() =
        runTest {
            val repo = subject()
            repo.requestSuggestion() // masoor dal
            repo.regenerate() // rajma
            repo.regenerate() // beef keema — the real validator rejects it
            val served = repo.observeCurrentSuggestion().first()
            val conflict = (served?.validation as? SuggestionValidation.Conflict)?.conflict
            assertNotNull("real validation should reject the beef meal", conflict)
            // Real planning: the religious-cultural beef avoid is offered for
            // a session relaxation; none-of-these is always present and last.
            assertTrue(conflict!!.options.isNotEmpty())
            assertEquals("none-of-these", conflict.options.last().id)
            assertTrue(conflict.options.any { it.id.startsWith("relax-") })
            assertFalse(conflict.options.any { it.label.contains("violation", ignoreCase = true) })
        }

    @Test
    fun choosingRelaxation_recordsSessionRelaxation_andServesPassingMeal() =
        runTest {
            val repo = subject()
            repo.requestSuggestion()
            repo.regenerate()
            repo.regenerate() // conflict
            val conflict =
                (repo.observeCurrentSuggestion().first()?.validation as SuggestionValidation.Conflict).conflict
            val relaxOption = conflict.options.first { it.id.startsWith("relax-") }
            repo.chooseConflictResolution(relaxOption.id)
            val resolved = repo.observeCurrentSuggestion().first()
            assertEquals(SukhiFixtures.khichdi.id, resolved?.id)
            assertEquals(SuggestionValidation.Passed, resolved?.validation)
        }
}
