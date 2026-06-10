package ai.cuizine.ui.screens.settings

import ai.cuizine.R
import ai.cuizine.ui.components.PlaceholderScreen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** Settings (tab 4). Real surfaces arrive in Phase 2 (`ui-ux-spec.md` §5.5). */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(text = stringResource(R.string.placeholder_settings), modifier = modifier)
}
