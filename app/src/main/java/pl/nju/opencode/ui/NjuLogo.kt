package pl.nju.opencode.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.nju.opencode.R
import pl.nju.opencode.ui.theme.NjuCyan

/** Intrinsic aspect of `R.drawable.nju_logo` — viewport 1422 x 428. */
private const val LOGO_ASPECT = 1422f / 428f

/**
 * The nju wordmark on a cyan plate.
 *
 * The asset is yellow on transparency, which all but disappears against the
 * light end of the app's background wash. Showing it on cyan is not just a
 * contrast fix — it is the lockup the brand actually uses.
 *
 * Both dimensions are pinned rather than relying on height alone: `Image`
 * measures through `Spacer`, which collapses any axis the modifier leaves
 * unspecified to zero, so a height-only modifier would draw nothing.
 */
@Composable
fun NjuLogo(
    logoHeight: Dp = 28.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(logoHeight * 0.34f))
            .background(NjuCyan)
            .padding(
                horizontal = logoHeight * 0.62f,
                vertical = logoHeight * 0.44f
            )
    ) {
        Image(
            painter = painterResource(R.drawable.nju_logo),
            contentDescription = "nju.",
            modifier = Modifier
                .width(logoHeight * LOGO_ASPECT)
                .height(logoHeight)
        )
    }
}
