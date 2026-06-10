package ai.cuizine.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Cuizine typography (`ui-ux-spec.md` §3): Material 3 type roles with a lean
 * toward highly legible body text (the primary persona is 53). The specific
 * typeface is an in-build decision (§11) — system default until then.
 * Numbers that matter (limits, nutritional values) get [NumberEmphasis].
 */
val CuizineTypography =
    Typography(
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 17.sp,
                lineHeight = 26.sp,
                letterSpacing = 0.01.em,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.01.em,
            ),
    )

/**
 * Treatment for precise reading of numbers (blood-sugar figures, sodium
 * ceilings): tabular feel via medium weight and generous size.
 */
val NumberEmphasis =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    )
