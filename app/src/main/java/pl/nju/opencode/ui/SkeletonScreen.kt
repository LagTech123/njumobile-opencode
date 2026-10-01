package pl.nju.opencode.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pl.nju.opencode.ui.theme.NjuTeal

@Composable
fun SkeletonScreen(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF1A9BB5),
            Color(0xFF2AB5CF),
            Color(0xFF1A9BB5),
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    val brushDark = Brush.linearGradient(
        colors = listOf(
            Color(0xFF1589A0),
            Color(0xFF1FA3BC),
            Color(0xFF1589A0),
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    val brushLight = Brush.linearGradient(
        colors = listOf(
            Color(0xFF25A8C2),
            Color(0xFF35C0DA),
            Color(0xFF25A8C2),
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    // Pulsing alpha for the card background
    val pulseAnim = transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NjuTeal)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        // Logo skeleton
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush)
        )

        Spacer(Modifier.height(20.dp))

        // Title skeleton
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush)
        )

        Spacer(Modifier.height(16.dp))

        // Subtitle skeleton
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brushLight)
        )

        Spacer(Modifier.height(24.dp))

        // Card skeleton with pulsing background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0D8AA5).copy(alpha = pulseAnim.value))
                .padding(20.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(3) {
                    Column {
                        Box(
                            modifier = Modifier
                                .width(100.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(brushLight)
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(brushDark)
                        )
                    }
                }

                // Button skeleton
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(brush)
                )
            }
        }
    }
}
