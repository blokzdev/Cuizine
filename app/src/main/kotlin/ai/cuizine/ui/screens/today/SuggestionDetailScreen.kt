package ai.cuizine.ui.screens.today

import ai.cuizine.ui.components.CuizineButton
import ai.cuizine.ui.components.CuizineButtonVariant
import ai.cuizine.ui.components.DisclosureNote
import ai.cuizine.ui.components.EmptyState
import ai.cuizine.ui.state.today.TodayViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Suggestion detail (`ui-ux-spec.md` §5.2): the full picture — how the meal
 * fits each constraint, the ingredient list, quiet disclosures — plus the
 * same accept / not-quite-right / regenerate intents and "why this?".
 * Shares [TodayViewModel] with the Today home (one feature area, one container).
 */
@Composable
fun SuggestionDetailScreen(
    viewModel: TodayViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.container.stateFlow.collectAsStateWithLifecycle()
    val suggestion = state.currentSuggestion

    if (suggestion == null) {
        EmptyState(
            title = "Nothing here right now",
            body = "Head back to Today and ask for a suggestion.",
            modifier = modifier,
            action = { CuizineButton(text = "Back to Today", onClick = onBack) },
        )
        return
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = suggestion.mealName,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = suggestion.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "About ${suggestion.approximateMinutes} minutes",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (suggestion.constraintFit.isNotEmpty()) {
            Text(
                text = "How it fits you",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            suggestion.constraintFit.forEach { fit ->
                Text(
                    text = "· ${fit.note}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = "Ingredients",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        suggestion.ingredients.forEach { ingredient ->
            Text(
                text = "· $ingredient",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        suggestion.disclosureNotes.forEach { note -> DisclosureNote(text = note) }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            CuizineButton(text = "Sounds good", onClick = {
                viewModel.onAcceptSuggestion()
                onBack()
            })
            CuizineButton(
                text = "Not quite right",
                onClick = {
                    onBack()
                    viewModel.onOpenRejectionSheet()
                },
                variant = CuizineButtonVariant.Secondary,
            )
        }
        CuizineButton(
            text = "Why this suggestion?",
            onClick = viewModel::onAskWhy,
            variant = CuizineButtonVariant.Plain,
        )
    }
}
