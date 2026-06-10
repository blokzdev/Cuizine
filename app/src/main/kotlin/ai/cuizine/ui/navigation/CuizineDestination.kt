package ai.cuizine.ui.navigation

import ai.cuizine.R
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The four top-level destinations (`ui-ux-spec.md` §4). This set is closed:
 * five top-level surfaces (these four + the conversation FAB) is a locked
 * decision — v2/v3 features attach inside them, never as new tabs.
 */
enum class CuizineDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Today("today", R.string.tab_today, Icons.Outlined.RestaurantMenu),
    Pantry("pantry", R.string.tab_pantry, Icons.Outlined.Kitchen),
    Profile("profile", R.string.tab_profile, Icons.Outlined.Person),
    Settings("settings", R.string.tab_settings, Icons.Outlined.Settings),
}
