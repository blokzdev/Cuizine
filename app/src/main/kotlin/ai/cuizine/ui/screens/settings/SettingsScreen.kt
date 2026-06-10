package ai.cuizine.ui.screens.settings

import ai.cuizine.shared.types.AccountState
import ai.cuizine.ui.components.CuizineButton
import ai.cuizine.ui.components.CuizineButtonVariant
import ai.cuizine.ui.components.DisclosureNote
import ai.cuizine.ui.components.PassphraseReveal
import ai.cuizine.ui.state.settings.SettingsSideEffect
import ai.cuizine.ui.state.settings.SettingsViewModel
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.orbitmvi.orbit.compose.collectSideEffect

/**
 * Settings (tab 4, `ui-ux-spec.md` §5.5). Minimal in v1: account & sync,
 * export-and-walk-away (unconditional), delete account, disclaimers, and the
 * calm alpha tier line. No model pickers, no token counters, no billing UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onAccountDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.container.stateFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    viewModel.collectSideEffect { effect ->
        when (effect) {
            is SettingsSideEffect.ShareExport -> {
                val send =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_TEXT, effect.exportJson)
                    }
                context.startActivity(Intent.createChooser(send, "Export my Cuizine data"))
            }

            SettingsSideEffect.RestartToFirstRun -> {
                onAccountDeleted()
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SectionTitle("Account & sync")
        when (val account = state.accountState) {
            AccountState.SignedOut -> {
                Text(
                    text =
                        "You're using Cuizine without an account — everything lives on this " +
                            "phone, and that's a complete experience. Sign in only if you want " +
                            "an encrypted copy following you to another device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CuizineButton(
                    text = "Sign in with Google",
                    onClick = viewModel::onSignInWithGoogle,
                    isLoading = state.isSigningIn,
                    variant = CuizineButtonVariant.Secondary,
                )
            }

            is AccountState.SignedIn -> {
                Text(
                    text = account.email,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        "Sync is on. The cloud holds a sealed, encrypted copy Cuizine cannot " +
                            "read. Last sync: ${account.lastSyncAt ?: "not yet"}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CuizineButton(
                    text = "Sync now",
                    onClick = viewModel::onTriggerSync,
                    variant = CuizineButtonVariant.Secondary,
                )
                CuizineButton(
                    text = "Sign out",
                    onClick = viewModel::onSignOut,
                    variant = CuizineButtonVariant.Plain,
                )
            }
        }

        SectionTitle("Your data")
        CuizineButton(
            text = "Export my data",
            onClick = viewModel::onExport,
            isLoading = state.isExporting,
            variant = CuizineButtonVariant.Secondary,
        )
        CuizineButton(
            text = "Delete my account and data",
            onClick = viewModel::onRequestDelete,
            variant = CuizineButtonVariant.Plain,
        )
        CuizineButton(
            text = "Share feedback with the Cuizine founder",
            onClick = viewModel::onShareFeedback,
            variant = CuizineButtonVariant.Plain,
        )

        SectionTitle("Cuizine alpha")
        Text(
            text = state.tier?.tierDisplayName ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle("About")
        DisclosureNote(
            text =
                "Cuizine is a food companion, not a medical provider. It never replaces " +
                    "your doctor's or dietitian's advice — it helps you cook within it.",
        )
        DisclosureNote(
            text =
                "No analytics run on your personal data. Ever. What you tell Cuizine stays " +
                    "on this device unless you export it or turn on encrypted sync.",
        )
    }

    if (state.isDeleteConfirmVisible) {
        AlertDialog(
            onDismissRequest = viewModel::onCancelDelete,
            title = { Text("Delete everything?") },
            text = {
                Text(
                    "This removes your profile, rules, history, and pantry from this phone " +
                        "and ends your account. There is no undo — Cuizine keeps no copy.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmDelete) { Text("Delete it all") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onCancelDelete) { Text("Keep my data") }
            },
        )
    }

    val passphrase = state.revealedPassphrase
    if (passphrase != null) {
        ModalBottomSheet(onDismissRequest = viewModel::onPassphraseConfirmedSaved) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Your recovery words",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                PassphraseReveal(passphrase = passphrase)
                CuizineButton(
                    text = "I've written them down",
                    onClick = viewModel::onPassphraseConfirmedSaved,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
