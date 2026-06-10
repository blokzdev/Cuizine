package ai.cuizine.ui.state.today

import ai.cuizine.data.mock.MockProfileRepository
import ai.cuizine.data.mock.MockSuggestionRepository
import ai.cuizine.shared.fixtures.SukhiFixtures
import ai.cuizine.shared.types.RejectionReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Flows B and D against the mock seam (`ui-ux-spec.md` §7): request →
 * suggestion; reject-for-missing-ingredients → pantry-friendly follow-up;
 * the scripted third request → honest conflict; choosing an option resolves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun awaitState(
        viewModel: TodayViewModel,
        predicate: (TodayState) -> Boolean,
    ): TodayState =
        withTimeout(timeMillis = 15_000) {
            viewModel.container.stateFlow.first(predicate)
        }

    private fun viewModel() = TodayViewModel(MockSuggestionRepository(), MockProfileRepository())

    @Test
    fun requestThenReject_improvesOnTheRejectedDimension() =
        runBlocking {
            val vm = viewModel()
            vm.onRequestSuggestion()
            val first = awaitState(vm) { it.currentSuggestion != null }
            assertEquals(SukhiFixtures.masoorDal.id, first.currentSuggestion?.id)

            vm.onRejectSuggestion(RejectionReason.MissingIngredients, freeText = null)
            val second =
                awaitState(
                    vm,
                ) { it.currentSuggestion != null && it.currentSuggestion?.id != first.currentSuggestion?.id }
            // The follow-up uses what's already in the pantry (PRD §7).
            assertEquals(SukhiFixtures.alooMethi.id, second.currentSuggestion?.id)
        }

    @Test
    fun thirdRequest_surfacesHonestConflict_andResolutionWorks() =
        runBlocking {
            val vm = viewModel()
            vm.onRequestSuggestion()
            awaitState(vm) { it.currentSuggestion != null }
            vm.onRegenerate()
            awaitState(vm) { it.currentSuggestion?.id == SukhiFixtures.rajmaBrownRice.id }

            vm.onRegenerate()
            val conflictState = awaitState(vm) { it.activeConflict != null }
            assertNotNull(conflictState.activeConflict)
            assertTrue(
                "conflicting constraints resolve to renderable rows",
                conflictState.conflictConstraints.isNotEmpty(),
            )
            assertEquals(null, conflictState.currentSuggestion)

            vm.onChooseConflictResolution("fixture-conflict-opt-khichdi")
            val resolved = awaitState(vm) { it.activeConflict == null && it.currentSuggestion != null }
            assertEquals(SukhiFixtures.khichdi.id, resolved.currentSuggestion?.id)
        }
}
