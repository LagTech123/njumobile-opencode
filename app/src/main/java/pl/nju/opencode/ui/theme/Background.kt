package pl.nju.opencode.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Backdrop wash built from the nju brand hues.
 *
 * Brand teal fades in from the top and a trace of brand yellow settles at the
 * bottom, with the plain background in between. The mix amounts are kept small
 * (16%/5% light, 30%/6% dark) so this reads as a tint rather than a poster and
 * body text still meets contrast against it.
 *
 * Dark mode mixes further into the teal because lerp-ing a near-black background
 * by a small factor would be visually indistinguishable.
 */
@Composable
fun njuBackgroundBrush(): Brush {
    val bg = MaterialTheme.colorScheme.background
    val dark = bg.luminance() < 0.5f

    val teal = if (dark) 0.30f else 0.16f
    val yellow = if (dark) 0.06f else 0.05f

    return Brush.verticalGradient(
        0.0f to lerp(bg, NjuTeal, teal),
        0.50f to bg,
        1.0f to lerp(bg, NjuYellow, yellow)
    )
}
