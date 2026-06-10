package ai.cuizine.ui.state.conversation

import ai.cuizine.shared.types.ConversationContext
import ai.cuizine.shared.types.ConversationService
import ai.cuizine.shared.types.ConversationTurn
import ai.cuizine.shared.types.ThinkingState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

/**
 * State for the unified conversation surface (`ui-ux-spec.md` §5.6) — also
 * hosts the day-0 constraint conversation (§5.1) in [ConversationContext.Onboarding].
 * Identical contract for mock and real orchestrator (DECISION-LOG #2b).
 */
data class ConversationState(
    val turns: List<ConversationTurn> = emptyList(),
    val inputText: String = "",
    val thinking: ThinkingState = ThinkingState(),
    val isOnboarding: Boolean = false,
    /** Onboarding only: the script is complete and confirm-and-continue may show. */
    val canConfirmOnboarding: Boolean = false,
)

sealed interface ConversationSideEffect {
    /** Onboarding confirmed — move to the sign-in decision (`ui-ux-spec.md` §7 Flow A). */
    data object OnboardingConfirmed : ConversationSideEffect

    /** The user acted on an inline suggestion — show it on Today. */
    data class OpenSuggestion(
        val suggestionId: String,
    ) : ConversationSideEffect
}

@HiltViewModel
class ConversationViewModel
    @Inject
    constructor(
        private val conversationService: ConversationService,
    ) : ViewModel(),
        ContainerHost<ConversationState, ConversationSideEffect> {
        override val container: Container<ConversationState, ConversationSideEffect> =
            container(ConversationState())

        init {
            conversationService
                .observeTurns()
                .onEach { turns ->
                    intent {
                        reduce {
                            state.copy(
                                turns = turns,
                                canConfirmOnboarding =
                                    state.isOnboarding &&
                                        turns.count { it.author.name == "User" } >= ONBOARDING_QUESTION_COUNT,
                            )
                        }
                    }
                }.launchIn(viewModelScope)
            conversationService
                .observeThinking()
                .onEach { thinking -> intent { reduce { state.copy(thinking = thinking) } } }
                .launchIn(viewModelScope)
        }

        fun onSessionStarted(context: ConversationContext) =
            intent {
                reduce { state.copy(isOnboarding = context == ConversationContext.Onboarding) }
                conversationService.startSession(context)
            }

        fun onInputChanged(text: String) = intent { reduce { state.copy(inputText = text) } }

        fun onSendMessage() =
            intent {
                val text = state.inputText.trim()
                if (text.isEmpty()) return@intent
                reduce { state.copy(inputText = "") }
                conversationService.send(text)
            }

        fun onConfirmOnboarding() =
            intent {
                conversationService.confirmOnboarding()
                postSideEffect(ConversationSideEffect.OnboardingConfirmed)
            }

        fun onInlineSuggestionClicked(suggestionId: String) =
            intent {
                postSideEffect(ConversationSideEffect.OpenSuggestion(suggestionId))
            }

        private companion object {
            const val ONBOARDING_QUESTION_COUNT = 5
        }
    }
