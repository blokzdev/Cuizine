package ai.cuizine.ui.components

import ai.cuizine.engine.types.Severity
import ai.cuizine.ui.theme.LocalSeverityColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.TempleHindu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The one consistent visual language for the four severity tiers
 * (`ui-ux-spec.md` §3/§6/§9): fixed semantic color + icon + text label,
 * never color alone, identical everywhere a severity appears.
 */
@Composable
fun SeverityIndicator(
    severity: Severity,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val colors = LocalSeverityColors.current
    val (color, icon, label) = severityVisuals(severity, colors)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        if (showLabel) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

internal data class SeverityVisuals(
    val color: Color,
    val icon: ImageVector,
    val label: String,
)

internal fun severityVisuals(
    severity: Severity,
    colors: ai.cuizine.ui.theme.SeverityColors,
): SeverityVisuals =
    when (severity) {
        Severity.Inviolable -> {
            SeverityVisuals(colors.inviolable, Icons.Outlined.Shield, "Never crossed")
        }

        Severity.Medical -> {
            SeverityVisuals(colors.medical, Icons.Outlined.MedicalServices, "Medical")
        }

        Severity.ReligiousCultural -> {
            SeverityVisuals(colors.religiousCultural, Icons.Outlined.TempleHindu, "Religious & cultural")
        }

        Severity.Preference -> {
            SeverityVisuals(colors.preference, Icons.Outlined.Favorite, "Preference")
        }
    }
