package ai.cuizine.ui.screens.onboarding

import ai.cuizine.shared.types.ConversationContext
import ai.cuizine.ui.components.CuizineButton
import ai.cuizine.ui.components.CuizineButtonVariant
import ai.cuizine.ui.components.PassphraseReveal
import ai.cuizine.ui.screens.conversation.ConversationSurface
import ai.cuizine.ui.state.onboarding.OnboardingSideEffect
import ai.cuizine.ui.state.onboarding.OnboardingStep
import ai.cuizine.ui.state.onboarding.OnboardingViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.orbitmvi.orbit.compose.collectSideEffect

/**
 * Hosts the day-0 steps (`ui-ux-spec.md` section 7 Flow A) outside the tab
 * scaffold — there is no app chrome until Cuizine has earned the first
 * conversation.
 */
@Composable
fun OnboardingHost(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.container.stateFlow.collectAsStateWithLifecycle()
    viewModel.collectSideEffect { effect ->
        when (effect) {
            OnboardingSideEffect.EnterApp -> onFinished()
        }
    }

    // Full-screen host outside the scaffold: edge-to-edge is on, so system
    // bar insets are handled here.
    Surface(
        modifier = modifier.fillMaxSize().systemBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        when (state.step) {
            OnboardingStep.Welcome -> {
                WelcomeStep(
                    onBegin = viewModel::onBegin,
                    onSkip = viewModel::onSkipToApp,
                )
            }

            OnboardingStep.Conversation -> {
                ConversationSurface(
                    context = ConversationContext.Onboarding,
                    onOnboardingConfirmed = viewModel::onConversationConfirmed,
                    onOpenSuggestion = {},
                )
            }

            OnboardingStep.SignInDecision -> {
                SignInDecisionStep(
                    isSigningIn = state.isSigningIn,
                    onSignIn = viewModel::onSignInWithGoogle,
                    onContinueWithout = viewModel::onContinueWithoutAccount,
                )
            }

            OnboardingStep.PassphraseReveal -> {
                PassphraseRevealStep(
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    onBegin: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Cuizine helps you cook food you love, even when your body has new rules.",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        CuizineButton(
            text = "Let's start with what you cook",
            onClick = onBegin,
            modifier = Modifier.padding(top = 28.dp),
        )
        CuizineButton(
            text = "Skip for now",
            onClick = onSkip,
            variant = CuizineButtonVariant.Plain,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SignInDecisionStep(
    isSigningIn: Boolean,
    onSignIn: () -> Unit,
    onContinueWithout: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "One optional thing",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text =
                "Sign in if you'd like your kitchen to follow you to another phone some day. " +
                    "Everything is encrypted before it leaves this device — the cloud holds a " +
                    "sealed copy Cuizine cannot read. Or skip this entirely; Cuizine works " +
                    "fully without an account.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        CuizineButton(
            text = "Sign in with Google",
            onClick = onSignIn,
            isLoading = isSigningIn,
            modifier = Modifier.padding(top = 28.dp),
        )
        CuizineButton(
            text = "Continue without an account",
            onClick = onContinueWithout,
            variant = CuizineButtonVariant.Plain,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun PassphraseRevealStep(viewModel: OnboardingViewModel) {
    val state by viewModel.container.stateFlow.collectAsStateWithLifecycle()
    val passphrase = state.revealedPassphrase ?: return
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Your recovery words",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        PassphraseReveal(
            passphrase = passphrase,
            modifier = Modifier.padding(top = 20.dp),
        )
        CuizineButton(
            text = "I've written them down",
            onClick = viewModel::onPassphraseConfirmedSaved,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}
