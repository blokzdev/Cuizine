package ai.cuizine.ui.components

import ai.cuizine.R
import ai.cuizine.ui.navigation.CuizineDestination
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * The app shell (`ui-ux-spec.md` §4, §6): Material 3 bottom navigation with
 * exactly four destinations plus the centered conversation FAB. The FAB opens
 * the unified Cuizine conversation — the user never picks an agent.
 *
 * Phase 1 ships the shell with the FAB inert; the conversation surface rises
 * from it in Phase 2.
 */
@Composable
fun CuizineScaffold(
    currentDestination: CuizineDestination?,
    onDestinationSelected: (CuizineDestination) -> Unit,
    onConversationClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onConversationClick,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = stringResource(R.string.fab_conversation),
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        bottomBar = {
            NavigationBar {
                CuizineDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == currentDestination,
                        onClick = { onDestinationSelected(destination) },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
        content = content,
    )
}
