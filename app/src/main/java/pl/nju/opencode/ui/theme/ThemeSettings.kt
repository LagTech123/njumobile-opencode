package pl.nju.opencode.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** User-selectable theme mode. SYSTEM follows the device setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Which palette drives the app. */
enum class ThemeStyle {
    /** nju brand colours — teal + yellow. The default. */
    BRAND,

    /** Neutral Material 3 baseline, ignoring brand hues. */
    NEUTRAL,

    /** Wallpaper-derived Material You colours (Android 12+ only). */
    DYNAMIC
}

@Immutable
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val style: ThemeStyle = ThemeStyle.BRAND
)

/**
 * Persists theme choices. Static instance — settings are read once at startup
 * and written through [ThemeController], so there is no need for observable
 * state beyond the composition that re-reads on change.
 */
class ThemePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): ThemeSettings = ThemeSettings(
        mode = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_MODE, ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM),
        style = runCatching {
            ThemeStyle.valueOf(prefs.getString(KEY_STYLE, ThemeStyle.BRAND.name)!!)
        }.getOrDefault(ThemeStyle.BRAND)
    )

    fun save(settings: ThemeSettings) {
        prefs.edit()
            .putString(KEY_MODE, settings.mode.name)
            .putString(KEY_STYLE, settings.style.name)
            .apply()
    }

    private companion object {
        const val FILE = "nju_theme"
        const val KEY_MODE = "mode"
        const val KEY_STYLE = "style"
    }
}

/** Read by composables that need the current choice without a state holder. */
val LocalThemeSettings = staticCompositionLocalOf { ThemeSettings() }

/** Convenience for screens that just want "should this be dark?". */
@Composable
fun isDarkTheme(settings: ThemeSettings = LocalThemeSettings.current): Boolean =
    when (settings.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
