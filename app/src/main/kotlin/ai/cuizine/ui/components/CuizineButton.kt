package ai.cuizine.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class CuizineButtonVariant { Primary, Secondary, Plain }

/**
 * The standard Cuizine button (`ui-ux-spec.md` §6): calm, 48dp+ target, with
 * a loading state instead of an aggressive disabled look. Unavailable actions
 * are hidden, not disabled (§2 Principle 5) — so there is no disabled state
 * here at all.
 */
@Composable
fun CuizineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: CuizineButtonVariant = CuizineButtonVariant.Primary,
    isLoading: Boolean = false,
) {
    val label: @Composable () -> Unit = {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text)
        }
    }
    when (variant) {
        CuizineButtonVariant.Primary -> {
            Button(onClick = onClick, modifier = modifier, enabled = !isLoading) { label() }
        }

        CuizineButtonVariant.Secondary -> {
            OutlinedButton(onClick = onClick, modifier = modifier, enabled = !isLoading) { Text(text) }
        }

        CuizineButtonVariant.Plain -> {
            TextButton(onClick = onClick, modifier = modifier, enabled = !isLoading) { Text(text) }
        }
    }
}
