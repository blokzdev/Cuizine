package ai.cuizine.ui.components

import ai.cuizine.shared.types.MealSuggestion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

enum class SuggestionCardState { Presented, Accepted, BeingRegenerated }

/**
 * The meal suggestion card (`ui-ux-spec.md` §6): the warm description leads,
 * constraint-fit notes stay quiet, disclosure notes are informational.
 * States: presented, accepted, being-regenerated.
 */
@Composable
fun SuggestionCard(
    suggestion: MealSuggestion,
    state: SuggestionCardState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val contentAlpha = if (state == SuggestionCardState.BeingRegenerated) 0.5f else 1f
    Card(
        modifier = modifier.fillMaxWidth().alpha(contentAlpha),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = onClick ?: {},
        enabled = onClick != null && state != SuggestionCardState.BeingRegenerated,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = suggestion.mealName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (state == SuggestionCardState.Accepted) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Accepted",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = suggestion.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (suggestion.constraintFit.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    suggestion.constraintFit.forEach { fit ->
                        Text(
                            text = "· ${fit.note}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            suggestion.disclosureNotes.forEach { note ->
                DisclosureNote(text = note)
            }
        }
    }
}
