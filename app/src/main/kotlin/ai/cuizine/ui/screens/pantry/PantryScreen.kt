package ai.cuizine.ui.screens.pantry

import ai.cuizine.shared.types.PantryEntry
import ai.cuizine.ui.components.CuizineButton
import ai.cuizine.ui.components.CuizineButtonVariant
import ai.cuizine.ui.components.EmptyState
import ai.cuizine.ui.state.pantry.PantryViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Pantry (tab 2, `ui-ux-spec.md` §5.3): a calm grouped list with simple
 * manual add/edit. Optional by design — the empty state is an invitation,
 * never a nag.
 */
@Composable
fun PantryScreen(
    modifier: Modifier = Modifier,
    viewModel: PantryViewModel = hiltViewModel(),
) {
    val state by viewModel.container.stateFlow.collectAsStateWithLifecycle()

    if (state.entriesByGroup.isEmpty()) {
        EmptyState(
            title = "Your pantry is empty",
            body = "Add what you have, or skip this for now — Cuizine can work either way.",
            modifier = modifier,
            action = { CuizineButton(text = "Add an item", onClick = viewModel::onOpenAddSheet) },
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            state.entriesByGroup.forEach { (group, entries) ->
                item(key = "group-$group") {
                    Text(
                        text = group,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                items(entries, key = { it.id }) { entry ->
                    PantryRow(
                        entry = entry,
                        onClick = { viewModel.onEditEntry(entry) },
                        onRemove = { viewModel.onRemoveEntry(entry.id) },
                    )
                }
            }
            item(key = "add") {
                CuizineButton(
                    text = "Add an item",
                    onClick = viewModel::onOpenAddSheet,
                    variant = CuizineButtonVariant.Plain,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (state.isAddSheetVisible) {
        PantryEntrySheet(
            entry = state.editingEntry,
            onDismiss = viewModel::onDismissSheet,
            onSave = viewModel::onSaveEntry,
        )
    }
}

@Composable
private fun PantryRow(
    entry: PantryEntry,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val quantity =
                    entry.quantityValue?.let { value ->
                        val unit = entry.quantityUnit.orEmpty()
                        "${if (value % 1.0 == 0.0) value.toInt() else value} $unit".trim()
                    }
                if (quantity != null) {
                    Text(
                        text = quantity,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove ${entry.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PantryEntrySheet(
    entry: PantryEntry?,
    onDismiss: () -> Unit,
    onSave: (String, Double?, String?) -> Unit,
) {
    var name by remember { mutableStateOf(entry?.name.orEmpty()) }
    var quantity by remember { mutableStateOf(entry?.quantityValue?.toString().orEmpty()) }
    var unit by remember { mutableStateOf(entry?.quantityUnit.orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (entry == null) "Add to your pantry" else "Edit item",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("What is it?") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("How much? (optional)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            CuizineButton(
                text = "Save",
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), quantity.toDoubleOrNull(), unit.trim().ifBlank { null })
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
        }
    }
}
