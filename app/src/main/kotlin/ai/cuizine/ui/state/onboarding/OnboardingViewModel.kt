package ai.cuizine.ui.state.onboarding

import ai.cuizine.data.repository.AccountRepository
import ai.cuizine.shared.types.ConversationService
import ai.cuizine.shared.types.RecoveryPassphrase
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

/**
 * The day-0 first-run flow (`ui-ux-spec.md` §5.1, §7 Flow A): Welcome → the
 * constraint conversation → the deferred-friendly sign-in decision →
 * (if signed in) the passphrase reveal → Today. No forms, no permissions,
 * no wizard feel (PRD §4).
 */
data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val isSigningIn: Boolean = false,
    val revealedPassphrase: RecoveryPassphrase? = null,
)

enum class OnboardingStep { Welcome, Conversation, SignInDecision, PassphraseReveal }

sealed interface OnboardingSideEffect {
    /** Onboarding is done — the shell takes over. */
    data object EnterApp : OnboardingSideEffect
}

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val accountRepository: AccountRepository,
        private val conversationService: ConversationService,
    ) : ViewModel(),
        ContainerHost<OnboardingState, OnboardingSideEffect> {
        override val container: Container<OnboardingState, OnboardingSideEffect> =
            container(OnboardingState())

        fun onBegin() = intent { reduce { state.copy(step = OnboardingStep.Conversation) } }

        /** §5.1 skip-to-app: fully usable without finishing the conversation. */
        fun onSkipToApp() =
            intent {
                conversationService.confirmOnboarding()
                postSideEffect(OnboardingSideEffect.EnterApp)
            }

        /** Called when the conversation surface confirms the captured constraints. */
        fun onConversationConfirmed() = intent { reduce { state.copy(step = OnboardingStep.SignInDecision) } }

        fun onSignInWithGoogle() =
            intent {
                reduce { state.copy(isSigningIn = true) }
                val passphrase = accountRepository.signInWithGoogle()
                reduce {
                    state.copy(
                        isSigningIn = false,
                        step = OnboardingStep.PassphraseReveal,
                        revealedPassphrase = passphrase,
                    )
                }
            }

        fun onContinueWithoutAccount() = intent { postSideEffect(OnboardingSideEffect.EnterApp) }

        fun onPassphraseConfirmedSaved() = intent { postSideEffect(OnboardingSideEffect.EnterApp) }
    }
