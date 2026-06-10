package ai.cuizine.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Cuizine shape language (`ui-ux-spec.md` §3): Material 3 rounded corners,
 * soft but not playful. Elevation is used sparingly — the FAB floats, cards
 * sit nearly flat.
 */
val CuizineShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
