package pl.nju.opencode.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// -- nju brand palettes ------------------------------------------------------
private val BrandLight = lightColorScheme(
    primary = MdPrimaryLight,
    onPrimary = MdOnPrimaryLight,
    primaryContainer = MdPrimaryContainerLight,
    onPrimaryContainer = MdOnPrimaryContainerLight,
    secondary = MdSecondaryLight,
    onSecondary = MdOnSecondaryLight,
    secondaryContainer = MdSecondaryContainerLight,
    onSecondaryContainer = MdOnSecondaryContainerLight,
    tertiary = MdTertiaryLight,
    onTertiary = MdOnTertiaryLight,
    tertiaryContainer = MdTertiaryContainerLight,
    onTertiaryContainer = MdOnTertiaryContainerLight,
    background = MdBackgroundLight,
    onBackground = MdOnBackgroundLight,
    surface = MdSurfaceLight,
    onSurface = MdOnSurfaceLight,
    surfaceVariant = MdSurfaceVariantLight,
    onSurfaceVariant = MdOnSurfaceVariantLight,
    outline = MdOutlineLight,
    outlineVariant = MdOutlineVariantLight,
    error = MdErrorLight,
    onError = MdOnErrorLight,
    errorContainer = MdErrorContainerLight,
    onErrorContainer = MdOnErrorContainerLight
)

private val BrandDark = darkColorScheme(
    primary = MdPrimaryDark,
    onPrimary = MdOnPrimaryDark,
    primaryContainer = MdPrimaryContainerDark,
    onPrimaryContainer = MdOnPrimaryContainerDark,
    secondary = MdSecondaryDark,
    onSecondary = MdOnSecondaryDark,
    secondaryContainer = MdSecondaryContainerDark,
    onSecondaryContainer = MdOnSecondaryContainerDark,
    tertiary = MdTertiaryDark,
    onTertiary = MdOnTertiaryDark,
    tertiaryContainer = MdTertiaryContainerDark,
    onTertiaryContainer = MdOnTertiaryContainerDark,
    background = MdBackgroundDark,
    onBackground = MdOnBackgroundDark,
    surface = MdSurfaceDark,
    onSurface = MdOnSurfaceDark,
    surfaceVariant = MdSurfaceVariantDark,
    onSurfaceVariant = MdOnSurfaceVariantDark,
    outline = MdOutlineDark,
    outlineVariant = MdOutlineVariantDark,
    error = MdErrorDark,
    onError = MdOnErrorDark,
    errorContainer = MdErrorContainerDark,
    onErrorContainer = MdOnErrorContainerDark
)

// -- Neutral Material 3 baseline --------------------------------------------
private val NeutralLight = lightColorScheme()
private val NeutralDark = darkColorScheme()

/**
 * Resolves the [ColorScheme] for the current settings.
 *
 * DYNAMIC falls back to BRAND below Android 12, since the wallpaper-derived
 * schemes are unavailable there — silently degrading beats crashing or
 * rendering unthemed.
 */
@Composable
fun NjuBalanceTheme(
    settings: ThemeSettings = LocalThemeSettings.current,
    content: @Composable () -> Unit
) {
    val darkTheme = when (settings.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current

    val scheme: ColorScheme = when {
        settings.style == ThemeStyle.DYNAMIC &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        settings.style == ThemeStyle.NEUTRAL ->
            if (darkTheme) NeutralDark else NeutralLight

        else ->
            if (darkTheme) BrandDark else BrandLight
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = NjuTypography,
        content = content
    )

    // Edge-to-edge lets the brand gradient run behind the status bar, which
    // makes the system icons' contrast depend on *our* resolved theme rather
    // than the device's night mode (they differ when the user pins Light/Dark).
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
}
