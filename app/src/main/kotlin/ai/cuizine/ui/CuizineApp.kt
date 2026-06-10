package ai.cuizine.ui

import ai.cuizine.shared.types.ConversationContext
import ai.cuizine.ui.components.CuizineScaffold
import ai.cuizine.ui.navigation.CuizineDestination
import ai.cuizine.ui.screens.conversation.ConversationSurface
import ai.cuizine.ui.screens.onboarding.OnboardingHost
import ai.cuizine.ui.screens.pantry.PantryScreen
import ai.cuizine.ui.screens.profile.ProfileScreen
import ai.cuizine.ui.screens.settings.SettingsScreen
import ai.cuizine.ui.screens.today.TodayScreen
import ai.cuizine.ui.state.RootPhase
import ai.cuizine.ui.state.RootViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/**
 * Root composable: first run goes to [OnboardingHost]; otherwise the four-tab
 * shell with per-tab back stacks and the conversation surface rising over the
 * current context from the FAB (`ui-ux-spec.md` section 4) — never a fifth tab.
 */
@Composable
fun CuizineApp(rootViewModel: RootViewModel = hiltViewModel()) {
    val rootState by rootViewModel.container.stateFlow.collectAsStateWithLifecycle()
    when (rootState.phase) {
        RootPhase.Loading -> Surface(color = MaterialTheme.colorScheme.surface) {}
        RootPhase.Onboarding -> OnboardingHost(onFinished = rootViewModel::onOnboardingFinished)
        RootPhase.Shell -> CuizineShell(isMockIndicatorVisible = rootState.isMockIndicatorVisible)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CuizineShell(isMockIndicatorVisible: Boolean) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination =
        CuizineDestination.entries.firstOrNull { destination ->
            backStackEntry?.destination?.route == destination.route
        }
    var conversationContext by remember { mutableStateOf<ConversationContext?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    CuizineScaffold(
        currentDestination = currentDestination,
        onDestinationSelected = { destination ->
            navController.navigate(destination.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        },
        onConversationClick = { conversationContext = ConversationContext.General },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = CuizineDestination.Today.route,
            ) {
                composable(CuizineDestination.Today.route) { TodayScreen() }
                composable(CuizineDestination.Pantry.route) { PantryScreen() }
                composable(CuizineDestination.Profile.route) { ProfileScreen() }
                composable(CuizineDestination.Settings.route) { SettingsScreen() }
            }
            if (isMockIndicatorVisible) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = "Sample data",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }

    val activeContext = conversationContext
    if (activeContext != null) {
        ModalBottomSheet(
            onDismissRequest = { conversationContext = null },
            sheetState = sheetState,
        ) {
            ConversationSurface(
                context = activeContext,
                onOnboardingConfirmed = {},
                onOpenSuggestion = { conversationContext = null },
                modifier = Modifier.fillMaxHeight(0.92f),
            )
        }
    }
}
