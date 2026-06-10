package ai.cuizine.ui.screens.profile

import ai.cuizine.engine.types.Severity
import ai.cuizine.ui.components.ConstraintRow
import ai.cuizine.ui.components.CuizineButton
import ai.cuizine.ui.components.CuizineButtonVariant
import ai.cuizine.ui.components.EmptyState
import ai.cuizine.ui.components.SeverityIndicator
import ai.cuizine.ui.state.profile.ProfileSideEffect
import ai.cuizine.ui.state.profile.ProfileViewModel
import ai.cuizine.ui.state.profile.scopeSummary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.orbitmvi.orbit.compose.collectSideEffect

/**
 * Profile home (`ui-ux-spec.md` §5.4): who Cuizine is cooking for and every
 * rule it holds, grouped by severity, in the user's own words. Adding a
 * constraint opens the conversation — never a form.
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onOpenConstraintDetail: (String) -> Unit,
    onOpenConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.container.stateFlow.collectAsStateWithLifecycle()
    viewModel.collectSideEffect { effect ->
        when (effect) {
            is ProfileSideEffect.OpenConstraintDetail -> onOpenConstraintDetail(effect.constraintId)
            ProfileSideEffect.OpenConversation -> onOpenConversation()
        }
    }

    val profile = state.profile
    if (profile == null) {
        EmptyState(
            title = "No profile yet",
            body = "Finish the first conversation and your kitchen profile will live here.",
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "header") {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = profile.culturalContext.cuisineOrigins.joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = "Cooking for ${profile.cookingFor.householdSize}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Severity.entries.forEach { severity ->
            val group = state.constraints.filter { it.severity == severity }
            if (group.isNotEmpty()) {
                item(key = "header-${severity.name}") {
                    SeverityIndicator(
                        severity = severity,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                items(group, key = { it.id }) { constraint ->
                    ConstraintRow(
                        constraint = constraint,
                        scopeSummary = constraint.scopeSummary(),
                        onClick = { viewModel.onConstraintClicked(constraint.id) },
                    )
                }
            }
        }

        item(key = "add") {
            CuizineButton(
                text = "Tell Cuizine about a new rule or favourite",
                onClick = viewModel::onAddConstraint,
                variant = CuizineButtonVariant.Plain,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}
