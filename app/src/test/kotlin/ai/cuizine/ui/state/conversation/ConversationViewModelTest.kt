package ai.cuizine.ui.state.conversation

import ai.cuizine.agents.mock.MockConversationService
import ai.cuizine.data.mock.MockProfileRepository
import ai.cuizine.shared.types.ConversationAuthor
import ai.cuizine.shared.types.ConversationContext
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
 * Flow A on the mock seam (`ui-ux-spec.md` §7): the five-question day-0
 * script runs, confirm-and-continue appears, and confirming completes the
 * profile — all with zero LLM calls. Orbit intents run on their real
 * dispatcher, so assertions await state conditions rather than virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun awaitState(
        viewModel: ConversationViewModel,
        predicate: (ConversationState) -> Boolean,
    ): ConversationState =
        withTimeout(timeMillis = 15_000) {
            viewModel.container.stateFlow.first(predicate)
        }

    @Test
    fun onboardingScript_capturesAndConfirms() =
        runBlocking {
            val profiles = MockProfileRepository()
            val service = MockConversationService(profiles)
            val viewModel = ConversationViewModel(service)

            viewModel.onSessionStarted(ConversationContext.Onboarding)
            val opening = awaitState(viewModel) { it.isOnboarding && it.turns.size == 1 }
            assertEquals(ConversationAuthor.Cuizine, opening.turns.first().author)

            val answers =
                listOf(
                    "Punjabi food, mostly — what my mother taught me",
                    "All five of us, four generations",
                    "The doctor says diabetes now, and my stomach has bad days",
                    "Rajma! And anything with aloo",
                    "Tuesdays we keep vegetarian",
                )
            answers.forEachIndexed { index, answer ->
                viewModel.onInputChanged(answer)
                viewModel.onSendMessage()
                awaitState(viewModel) { s ->
                    s.turns.count { it.author == ConversationAuthor.User } == index + 1
                }
            }

            // Two of the five acknowledgments carry captured constraints inline.
            val afterScript =
                awaitState(viewModel) { s ->
                    s.canConfirmOnboarding && s.turns.count { it.inlineResult != null } == 2
                }
            assertEquals(5, afterScript.turns.count { it.author == ConversationAuthor.User })

            viewModel.onConfirmOnboarding()
            val profile =
                withTimeout(timeMillis = 15_000) {
                    profiles.observeProfile().first { it != null }
                }
            assertNotNull(profile)
        }

    @Test
    fun generalSession_capturesContextSignal() =
        runBlocking {
            val service = MockConversationService(MockProfileRepository())
            val viewModel = ConversationViewModel(service)

            viewModel.onSessionStarted(ConversationContext.General)
            viewModel.onInputChanged("my sugar was 9.2 this morning")
            viewModel.onSendMessage()

            val state = awaitState(viewModel) { s -> s.turns.any { it.inlineResult != null } }
            assertTrue(state.turns.none { it.text.contains("didn't quite catch", ignoreCase = true) })
        }
}
