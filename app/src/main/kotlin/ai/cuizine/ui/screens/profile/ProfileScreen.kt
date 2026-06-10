package ai.cuizine.ui.screens.profile

import ai.cuizine.R
import ai.cuizine.ui.components.PlaceholderScreen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** Profile (tab 3). Real surface arrives in Phase 2 (`ui-ux-spec.md` §5.4). */
@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(text = stringResource(R.string.placeholder_profile), modifier = modifier)
}
