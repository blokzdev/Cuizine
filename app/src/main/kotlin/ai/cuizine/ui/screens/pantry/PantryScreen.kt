package ai.cuizine.ui.screens.pantry

import ai.cuizine.R
import ai.cuizine.ui.components.PlaceholderScreen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** Pantry (tab 2). Real surface arrives in Phase 2 (`ui-ux-spec.md` §5.3). */
@Composable
fun PantryScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(text = stringResource(R.string.placeholder_pantry), modifier = modifier)
}
