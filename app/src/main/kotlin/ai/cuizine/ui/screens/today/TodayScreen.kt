package ai.cuizine.ui.screens.today

import ai.cuizine.shared.types.ConversationContext
import ai.cuizine.shared.types.RejectionReason
import ai.cuizine.ui.components.ConstraintRow
import ai.cuizine.ui.components.CuizineButton
import ai.cuizine.ui.components.CuizineButtonVariant
import ai.cuizine.ui.components.EmptyState
import ai.cuizine.ui.components.SuggestionCard
import ai.cuizine.ui.components.SuggestionCardState
import ai.cuizine.ui.components.ThinkingIndicator
import ai.cuizine.ui.state.today.TodaySideEffect
import ai.cuizine.ui.state.today.TodayViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.orbitmvi.orbit.compose.collectSideEffect

/**
 * Today (tab 1, `ui-ux-spec.md` §5.2): one suggestion at a time, accept /
 * not-quite-right / regenerate, a quiet "tell Cuizine something" affordance,
 * and the honest conflict surface when validation can't cleanly regenerate.
 */
@Composable
fun TodayScreen(
    onOpenSuggestionDetail: () -> Unit,
    onOpenConversation: (ConversationContext) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.container.stateFlow.collectAsStateWithLifecycle()
    viewModel.collectSideEffect { effect ->
        when (effect) {
            TodaySideEffect.OpenSuggestionDetail -> {
                onOpenSuggestionDetail()
            }

            TodaySideEffect.OpenConversationGeneral -> {
                onOpenConversation(ConversationContext.General)
            }

            is TodaySideEffect.OpenConversationAboutSuggestion -> {
                onOpenConversation(ConversationContext.AboutSuggestion(effect.suggestionId))
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val conflict = state.activeConflict
        when {
            state.isGenerating -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 120.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    ThinkingIndicator(revealedLine = "Composing something that fits your day…")
                }
            }

            conflict != null -> {
                ConflictContent(
                    explanation = conflict.explanation,
                    constraints = state.conflictConstraints,
                    options =
                        conflict.options.map { option ->
                            Triple(option.id, option.label, option.severityImplication)
                        },
                    onChoose = viewModel::onChooseConflictResolution,
                    onAskForHelp = { onOpenConversation(ConversationContext.General) },
                )
            }

            state.currentSuggestion != null -> {
                val suggestion = checkNotNull(state.currentSuggestion)
                SuggestionCard(
                    suggestion = suggestion,
                    state = SuggestionCardState.Presented,
                    onClick = viewModel::onOpenSuggestionDetail,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CuizineButton(text = "Sounds good", onClick = viewModel::onAcceptSuggestion)
                    CuizineButton(
                        text = "Not quite right",
                        onClick = viewModel::onOpenRejectionSheet,
                        variant = CuizineButtonVariant.Secondary,
                    )
                }
                CuizineButton(
                    text = "Something else instead",
                    onClick = viewModel::onRegenerate,
                    variant = CuizineButtonVariant.Plain,
                )
            }

            else -> {
                EmptyState(
                    title = "What should we cook today?",
                    body = "Whenever you're ready, I'll suggest one meal that fits everything you've told me.",
                    modifier = Modifier.weight(1f, fill = false),
                    action = {
                        CuizineButton(text = "Suggest a meal", onClick = viewModel::onRequestSuggestion)
                    },
                )
            }
        }

        CuizineButton(
            text = "Tell Cuizine something",
            onClick = viewModel::onTellCuizineSomething,
            variant = CuizineButtonVariant.Plain,
        )
    }

    if (state.isRejectionSheetVisible) {
        RejectionSheet(
            onDismiss = viewModel::onDismissRejectionSheet,
            onReject = viewModel::onRejectSuggestion,
        )
    }
}

@Composable
private fun ConflictContent(
    explanation: String,
    constraints: List<ai.cuizine.shared.types.Constraint>,
    options: List<Triple<String, String, String>>,
    onChoose: (String) -> Unit,
    onAskForHelp: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "These are pulling in different directions tonight",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = explanation,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        constraints.forEach { constraint ->
            ConstraintRow(constraint = constraint, scopeSummary = null, onClick = {})
        }
        options.forEach { (id, label, implication) ->
            Card(
                onClick = { onChoose(id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = implication,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        CuizineButton(
            text = "Help me decide",
            onClick = onAskForHelp,
            variant = CuizineButtonVariant.Plain,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RejectionSheet(
    onDismiss: () -> Unit,
    onReject: (RejectionReason, String?) -> Unit,
) {
    var freeText by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "What's not quite right?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            rejectionChoices.forEach { (reason, label) ->
                CuizineButton(
                    text = label,
                    onClick = { onReject(reason, freeText.ifBlank { null }) },
                    variant = CuizineButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = freeText,
                onValueChange = { freeText = it },
                placeholder = { Text("Anything else? (optional)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                maxLines = 2,
            )
        }
    }
}

private val rejectionChoices =
    listOf(
        RejectionReason.MissingIngredients to "Don't have the ingredients",
        RejectionReason.FamilyWouldNotLikeIt to "Family wouldn't like it",
        RejectionReason.TooMuchWork to "Too much work tonight",
        RejectionReason.WrongForHowImFeeling to "Wrong for how I'm feeling",
        RejectionReason.Other to "Something else",
    )
