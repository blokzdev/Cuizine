package ai.cuizine.ui.components

import ai.cuizine.shared.types.ConversationAuthor
import ai.cuizine.shared.types.ConversationTurn
import ai.cuizine.shared.types.InlineResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One conversation turn (`ui-ux-spec.md` §6): user messages right-aligned in
 * primary container, Cuizine messages left-aligned on surface — and Cuizine
 * messages can carry inline results (a captured constraint, a suggested meal).
 */
@Composable
fun ConversationMessage(
    turn: ConversationTurn,
    modifier: Modifier = Modifier,
    onInlineSuggestionClick: ((String) -> Unit)? = null,
) {
    val isUser = turn.author == ConversationAuthor.User
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color =
                    if (isUser) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ) {
                Text(
                    text = turn.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color =
                        if (isUser) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            when (val inline = turn.inlineResult) {
                is InlineResult.CapturedConstraint -> {
                    ConstraintChip(constraint = inline.constraint)
                }

                is InlineResult.CapturedContext -> {
                    DisclosureNote(text = inline.acknowledgement)
                }

                is InlineResult.SuggestedMeal -> {
                    SuggestionCard(
                        suggestion = inline.suggestion,
                        state = SuggestionCardState.Presented,
                        onClick =
                            onInlineSuggestionClick?.let { handler ->
                                { handler(inline.suggestion.id) }
                            },
                    )
                }

                null -> {
                    Unit
                }
            }
        }
    }
}
