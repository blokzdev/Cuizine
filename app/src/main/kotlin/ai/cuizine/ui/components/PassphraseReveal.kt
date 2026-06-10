package ai.cuizine.ui.components

import ai.cuizine.shared.types.RecoveryPassphrase
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The six-word recovery passphrase reveal (`ui-ux-spec.md` §6;
 * `local-first-sync.md` §4). Weighty and precise: the words are large and
 * unambiguous, and the no-recovery truth is stated honestly — no master key,
 * no admin override, nobody at Cuizine can restore this.
 */
@Composable
fun PassphraseReveal(
    passphrase: RecoveryPassphrase,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                passphrase.words.forEachIndexed { index, word ->
                    Text(
                        text = "${index + 1}.  $word",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Text(
            text =
                "These six words are the only way to read your synced data on a new " +
                    "device. Cuizine cannot recover them for you — there is no master key " +
                    "and no override. Write them somewhere safe, on paper.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
