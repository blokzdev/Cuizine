package ai.cuizine.ui

import ai.cuizine.ui.components.CuizineScaffold
import ai.cuizine.ui.navigation.CuizineDestination
import ai.cuizine.ui.screens.pantry.PantryScreen
import ai.cuizine.ui.screens.profile.ProfileScreen
import ai.cuizine.ui.screens.settings.SettingsScreen
import ai.cuizine.ui.screens.today.TodayScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/**
 * Root composable: the four-tab navigation graph inside [CuizineScaffold].
 * Each tab keeps its own back stack (`ui-ux-spec.md` §4 navigation behavior)
 * via save/restore on tab switches.
 */
@Composable
fun CuizineApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination =
        CuizineDestination.entries.firstOrNull { destination ->
            backStackEntry?.destination?.route == destination.route
        }

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
        onConversationClick = {
            // The conversation surface rises from here in Phase 2.
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = CuizineDestination.Today.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(CuizineDestination.Today.route) { TodayScreen() }
            composable(CuizineDestination.Pantry.route) { PantryScreen() }
            composable(CuizineDestination.Profile.route) { ProfileScreen() }
            composable(CuizineDestination.Settings.route) { SettingsScreen() }
        }
    }
}
