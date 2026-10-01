package pl.nju.opencode.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import pl.nju.opencode.R

// Bundled directly from res/font/asap.ttf (variable font — weight axis handled by the OS)
val AsapFontFamily = FontFamily(
    Font(R.font.asap, FontWeight.Normal),
    Font(R.font.asap, FontWeight.Medium),
    Font(R.font.asap, FontWeight.Bold)
)

// Base size set to 20sp as specified. Other roles scaled proportionally
// around that base rather than left at Material's defaults.
val NjuTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = AsapFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AsapFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleLarge = TextStyle(
        fontFamily = AsapFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleMedium = TextStyle(
        fontFamily = AsapFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    labelLarge = TextStyle(
        fontFamily = AsapFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp
    )
)
