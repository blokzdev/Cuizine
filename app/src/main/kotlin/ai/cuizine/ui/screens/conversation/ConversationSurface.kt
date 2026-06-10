package ai.cuizine.ui.screens.conversation

import ai.cuizine.shared.types.ConversationContext
import ai.cuizine.ui.components.ConversationMessage
import ai.cuizine.ui.components.CuizineButton
import ai.cuizine.ui.components.ThinkingIndicator
import ai.cuizine.ui.state.conversation.ConversationSideEffect
import ai.cuizine.ui.state.conversation.ConversationViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.orbitmvi.orbit.compose.collectSideEffect

/**
 * The conversation surface (`ui-ux-spec.md` section 5.6): one place to talk
 * to Cuizine, whatever the context. Used as the FAB overlay (General /
 * AboutSuggestion) and as the day-0 constraint conversation (Onboarding).
 */
@Composable
fun ConversationSurface(
    context: ConversationContext,
    onOnboardingConfirmed: () -> Unit,
    onOpenSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationViewModel = hiltViewModel(),
) {
    val state by viewModel.container.stateFlow.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(context) { viewModel.onSessionStarted(context) }
    LaunchedEffect(state.turns.size) {
        if (state.turns.isNotEmpty()) listState.animateScrollToItem(state.turns.lastIndex)
    }
    viewModel.collectSideEffect { effect ->
        when (effect) {
            ConversationSideEffect.OnboardingConfirmed -> onOnboardingConfirmed()
            is ConversationSideEffect.OpenSuggestion -> onOpenSuggestion(effect.suggestionId)
        }
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.turns, key = { it.id }) { turn ->
                ConversationMessage(
                    turn = turn,
                    onInlineSuggestionClick = { viewModel.onInlineSuggestionClicked(it) },
                )
            }
            if (state.thinking.isThinking) {
                item(key = "thinking") {
                    ThinkingIndicator(revealedLine = state.thinking.revealedLine)
                }
            }
        }

        if (state.canConfirmOnboarding) {
            CuizineButton(
                text = "That's everything — let's cook",
                onClick = viewModel::onConfirmOnboarding,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.inputText,
                onValueChange = viewModel::onInputChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Tell Cuizine anything…") },
                maxLines = 4,
                shape = MaterialTheme.shapes.large,
            )
            IconButton(onClick = viewModel::onSendMessage) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
