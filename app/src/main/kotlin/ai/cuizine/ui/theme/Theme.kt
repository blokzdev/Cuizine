package ai.cuizine.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme =
    lightColorScheme(
        primary = ClayPrimaryLight,
        onPrimary = OnClayPrimaryLight,
        primaryContainer = ClayContainerLight,
        onPrimaryContainer = OnClayContainerLight,
        secondary = SageSecondaryLight,
        onSecondary = OnSageSecondaryLight,
        secondaryContainer = SageContainerLight,
        onSecondaryContainer = OnSageContainerLight,
        tertiary = HoneyTertiaryLight,
        onTertiary = OnHoneyTertiaryLight,
        tertiaryContainer = HoneyContainerLight,
        onTertiaryContainer = OnHoneyContainerLight,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
        surfaceVariant = SurfaceVariantLight,
        onSurfaceVariant = OnSurfaceVariantLight,
        background = SurfaceLight,
        onBackground = OnSurfaceLight,
        outline = OutlineLight,
        error = ErrorLight,
        onError = OnErrorLight,
        errorContainer = ErrorContainerLight,
        onErrorContainer = OnErrorContainerLight,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = ClayPrimaryDark,
        onPrimary = OnClayPrimaryDark,
        primaryContainer = ClayContainerDark,
        onPrimaryContainer = OnClayContainerDark,
        secondary = SageSecondaryDark,
        onSecondary = OnSageSecondaryDark,
        secondaryContainer = SageContainerDark,
        onSecondaryContainer = OnSageContainerDark,
        tertiary = HoneyTertiaryDark,
        onTertiary = OnHoneyTertiaryDark,
        tertiaryContainer = HoneyContainerDark,
        onTertiaryContainer = OnHoneyContainerDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        background = SurfaceDark,
        onBackground = OnSurfaceDark,
        outline = OutlineDark,
        error = ErrorDark,
        onError = OnErrorDark,
        errorContainer = ErrorContainerDark,
        onErrorContainer = OnErrorContainerDark,
    )

/**
 * Severity colors are exposed beside (not inside) the Material scheme because
 * they are semantic and must not drift with the wallpaper palette
 * (`ui-ux-spec.md` §3).
 */
val LocalSeverityColors = staticCompositionLocalOf { SeverityColorsLight }

/**
 * Cuizine's Material 3 theme: dynamic color is supported (Material You) but
 * the severity semantics stay fixed. Both light and dark are first-class
 * from v1.
 */
@Composable
fun CuizineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> {
                DarkColorScheme
            }

            else -> {
                LightColorScheme
            }
        }
    val severityColors = if (darkTheme) SeverityColorsDark else SeverityColorsLight

    CompositionLocalProvider(LocalSeverityColors provides severityColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CuizineTypography,
            shapes = CuizineShapes,
            content = content,
        )
    }
}
