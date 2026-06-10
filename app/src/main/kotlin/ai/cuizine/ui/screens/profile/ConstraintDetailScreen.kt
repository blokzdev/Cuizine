package ai.cuizine.ui.screens.profile

import ai.cuizine.engine.types.Severity
import ai.cuizine.ui.components.CuizineButton
import ai.cuizine.ui.components.CuizineButtonVariant
import ai.cuizine.ui.components.EmptyState
import ai.cuizine.ui.components.SeverityIndicator
import ai.cuizine.ui.state.profile.ProfileViewModel
import ai.cuizine.ui.state.profile.scopeSummary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Constraint detail (`ui-ux-spec.md` §5.4): the rule in the user's words,
 * its full scope, its provenance trail, severity editing with a warning
 * before lowering a medical/inviolable rule, and gentle removal.
 */
@Composable
fun ConstraintDetailScreen(
    constraintId: String,
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Side effects are collected by the Profile home only — one collector per
    // container, or deliveries would compete.
    val state by viewModel.container.stateFlow.collectAsStateWithLifecycle()
    val constraint = state.constraints.firstOrNull { it.id == constraintId }
    var pendingSeverity by remember { mutableStateOf<Severity?>(null) }

    if (constraint == null) {
        EmptyState(
            title = "This rule is gone",
            body = "It may have been removed.",
            modifier = modifier,
            action = { CuizineButton(text = "Back", onClick = onBack) },
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
            text = constraint.humanLabel,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SeverityIndicator(severity = constraint.severity)
        Text(
            text = "Applies: ${constraint.scopeSummary()}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = "Where this came from",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val provenance = constraint.provenance
        Text(
            text = "Added ${provenance.addedAt.take(10)} during ${provenance.addedContext.flow.replace('_', ' ')}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        provenance.originalPhrasing?.let { phrasing ->
            Text(
                text = "You said: “$phrasing”",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        provenance.modificationHistory.forEach { change ->
            Text(
                text = "Changed ${change.at.take(10)}${change.reason?.let { ": $it" } ?: ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = "How firmly should I hold this?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Severity.entries.forEach { severity ->
                if (severity != constraint.severity) {
                    CuizineButton(
                        text = severityLabel(severity),
                        onClick = {
                            val isLowering =
                                constraint.severity in setOf(Severity.Inviolable, Severity.Medical) &&
                                    severity.ordinal > constraint.severity.ordinal
                            if (isLowering) {
                                pendingSeverity = severity
                            } else {
                                viewModel.onSeverityChanged(constraint.id, severity)
                            }
                        },
                        variant = CuizineButtonVariant.Secondary,
                    )
                }
            }
        }

        CuizineButton(
            text = "Remove this rule",
            onClick = {
                viewModel.onRemoveConstraint(constraint.id)
                onBack()
            },
            variant = CuizineButtonVariant.Plain,
            modifier = Modifier.padding(top = 8.dp),
        )
        CuizineButton(text = "Back", onClick = onBack, variant = CuizineButtonVariant.Plain)
    }

    val lowering = pendingSeverity
    if (lowering != null) {
        AlertDialog(
            onDismissRequest = { pendingSeverity = null },
            title = { Text("Loosen a ${severityLabel(constraint.severity).lowercase()} rule?") },
            text = {
                Text(
                    "This rule currently protects something important. If you loosen it, " +
                        "Cuizine may suggest meals it would have kept away from you. " +
                        "You can change it back any time.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onSeverityChanged(constraint.id, lowering)
                    pendingSeverity = null
                }) { Text("Loosen it") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSeverity = null }) { Text("Keep as is") }
            },
        )
    }
}

private fun severityLabel(severity: Severity): String =
    when (severity) {
        Severity.Inviolable -> "Never crossed"
        Severity.Medical -> "Medical"
        Severity.ReligiousCultural -> "Religious & cultural"
        Severity.Preference -> "Preference"
    }
