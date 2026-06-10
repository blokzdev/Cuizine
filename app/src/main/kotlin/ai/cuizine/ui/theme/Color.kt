package ai.cuizine.ui.theme

import androidx.compose.ui.graphics.Color

// Cuizine color tokens (`ui-ux-spec.md` §3): a warm, neutral, kitchen-like
// Material 3 role-based scheme. Color carries meaning, not decoration. Exact
// hex values are deliberately tunable in-build (§11); the roles and the
// severity semantics below are the contract.

// Light scheme — warm clay primary over warm off-white surfaces.
internal val ClayPrimaryLight = Color(0xFF8A5138)
internal val OnClayPrimaryLight = Color(0xFFFFFFFF)
internal val ClayContainerLight = Color(0xFFFFDBCC)
internal val OnClayContainerLight = Color(0xFF361507)
internal val SageSecondaryLight = Color(0xFF5C6146)
internal val OnSageSecondaryLight = Color(0xFFFFFFFF)
internal val SageContainerLight = Color(0xFFE1E6C3)
internal val OnSageContainerLight = Color(0xFF191E08)
internal val HoneyTertiaryLight = Color(0xFF7A5817)
internal val OnHoneyTertiaryLight = Color(0xFFFFFFFF)
internal val HoneyContainerLight = Color(0xFFFFDEA9)
internal val OnHoneyContainerLight = Color(0xFF271900)
internal val SurfaceLight = Color(0xFFF8F5F1)
internal val OnSurfaceLight = Color(0xFF211A16)
internal val SurfaceVariantLight = Color(0xFFF0E4DC)
internal val OnSurfaceVariantLight = Color(0xFF52443C)
internal val OutlineLight = Color(0xFF85736B)
internal val ErrorLight = Color(0xFFA63B2A)
internal val OnErrorLight = Color(0xFFFFFFFF)
internal val ErrorContainerLight = Color(0xFFFFDAD2)
internal val OnErrorContainerLight = Color(0xFF3E0900)

// Dark scheme — the same warmth, dimmed; never cold grey-blue.
internal val ClayPrimaryDark = Color(0xFFFFB59A)
internal val OnClayPrimaryDark = Color(0xFF512411)
internal val ClayContainerDark = Color(0xFF6D3A24)
internal val OnClayContainerDark = Color(0xFFFFDBCC)
internal val SageSecondaryDark = Color(0xFFC5CAA8)
internal val OnSageSecondaryDark = Color(0xFF2E331B)
internal val SageContainerDark = Color(0xFF444930)
internal val OnSageContainerDark = Color(0xFFE1E6C3)
internal val HoneyTertiaryDark = Color(0xFFECBF6D)
internal val OnHoneyTertiaryDark = Color(0xFF422C00)
internal val HoneyContainerDark = Color(0xFF5E4100)
internal val OnHoneyContainerDark = Color(0xFFFFDEA9)
internal val SurfaceDark = Color(0xFF1A1614)
internal val OnSurfaceDark = Color(0xFFEFE0D9)
internal val SurfaceVariantDark = Color(0xFF52443C)
internal val OnSurfaceVariantDark = Color(0xFFD7C2B8)
internal val OutlineDark = Color(0xFFA08D84)
internal val ErrorDark = Color(0xFFFFB4A4)
internal val OnErrorDark = Color(0xFF601407)
internal val ErrorContainerDark = Color(0xFF842F1D)
internal val OnErrorContainerDark = Color(0xFFFFDAD2)

/**
 * Semantic colors for the four severity tiers (`constraint-engine-spec.md` §4,
 * `ui-ux-spec.md` §3). These are deliberately FIXED — they never follow the
 * dynamic-color wallpaper palette, so severity stays legible everywhere.
 * Severity is never communicated by color alone (§9 accessibility floor);
 * these colors always travel with an icon and/or label.
 */
data class SeverityColors(
    val inviolable: Color,
    val onInviolable: Color,
    val medical: Color,
    val onMedical: Color,
    val religiousCultural: Color,
    val onReligiousCultural: Color,
    val preference: Color,
    val onPreference: Color,
)

internal val SeverityColorsLight =
    SeverityColors(
        inviolable = Color(0xFF7E1F1F),
        onInviolable = Color(0xFFFFFFFF),
        medical = Color(0xFF22577A),
        onMedical = Color(0xFFFFFFFF),
        religiousCultural = Color(0xFF53387A),
        onReligiousCultural = Color(0xFFFFFFFF),
        preference = Color(0xFF3F6B3F),
        onPreference = Color(0xFFFFFFFF),
    )

internal val SeverityColorsDark =
    SeverityColors(
        inviolable = Color(0xFFE9A1A1),
        onInviolable = Color(0xFF4C0E0E),
        medical = Color(0xFFA3C9E5),
        onMedical = Color(0xFF0E304A),
        religiousCultural = Color(0xFFCBB8E8),
        onReligiousCultural = Color(0xFF2E1C4A),
        preference = Color(0xFFA9CCA9),
        onPreference = Color(0xFF1E3A1E),
    )
