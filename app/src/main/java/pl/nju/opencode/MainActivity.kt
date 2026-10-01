package pl.nju.opencode

import android.content.Context
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pl.nju.opencode.ui.AccountCache
import pl.nju.opencode.ui.AccountInfo
import pl.nju.opencode.ui.CachedAccount
import pl.nju.opencode.ui.DashboardScreen
import pl.nju.opencode.ui.DriverResult
import pl.nju.opencode.ui.FullScreenWebView
import pl.nju.opencode.ui.LoginDriver
import pl.nju.opencode.ui.LoginScreen
import pl.nju.opencode.ui.SecureCredentials
import pl.nju.opencode.ui.WebPage
import pl.nju.opencode.ui.theme.LocalThemeSettings
import pl.nju.opencode.ui.theme.NjuBalanceTheme
import pl.nju.opencode.ui.theme.ThemePreferences
import pl.nju.opencode.ui.theme.ThemeSettings

/**
 * Single source of truth for where the user is.
 *
 * Startup is stale-while-revalidate: if a cached snapshot exists it renders
 * immediately with real numbers, and a background refresh overwrites it once
 * fresh data lands. Only a missing cache shows the login form.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the system bars so the brand gradient runs continuously
        // from under the status bar instead of being cut off by an opaque bar.
        // Insets are honoured per screen; see statusBarsPadding() usages.
        enableEdgeToEdge()

        val prefs = getSharedPreferences("nju_prefs", Context.MODE_PRIVATE)
        val themePrefs = ThemePreferences(this)

        setContent {
            val context = LocalContext.current
            var themeSettings by remember { mutableStateOf(themePrefs.load()) }

            // Read the cache exactly once per process.
            val cached: CachedAccount? = remember { AccountCache.load(context) }

            var account by remember {
                mutableStateOf(cached?.info ?: AccountInfo())
            }
            var currentPage by remember { mutableStateOf<WebPage?>(null) }
            var hasSession by remember { mutableStateOf(cached != null) }

            // Greeting shown in the top bar. Blank means "use the phone number".
            var displayName by remember {
                mutableStateOf(prefs.getString("displayName", "") ?: "")
            }

            // Last successful write time, surfaced as "updated X ago".
            var updatedAt by remember { mutableStateOf(cached?.savedAt ?: 0L) }

            // "Zapamiętaj mnie" — prefilling the number on the login form is the
            // whole point of the switch, so the flag has to outlive the screen.
            var rememberPhone by remember {
                mutableStateOf(prefs.getBoolean("remember_phone", true))
            }

            fun rememberNumber(number: String) {
                when {
                    !rememberPhone -> prefs.edit().remove("phone").apply()
                    // A bare URL login signal carries no number — keep the one
                    // we already stored rather than dropping it.
                    number.isNotBlank() -> prefs.edit().putString("phone", number).apply()
                }
            }

            fun persist(info: AccountInfo) {
                if (info.isUninitialized) return
                // A single scrape is rarely complete — the login hit carries the
                // number and balance but no allowance yet, and the account page
                // paints the balance row before the data panel. Merging into
                // what we already know means a partial snapshot can never blank
                // out fields we've already shown the user.
                val merged = account.mergeWith(info)
                account = merged
                updatedAt = System.currentTimeMillis()
                AccountCache.save(context, merged)
            }

            // --- Silent re-login -------------------------------------------
            // The site times its session out on inactivity rather than losing
            // the cookie, so the fix has two halves: DashboardScreen pokes the
            // account page on a timer to keep it alive, and if it expires
            // anyway this POSTs the stored credentials again instead of
            // bouncing the user back to the login form.
            var relogAttempt by remember { mutableStateOf(0) }
            var relogCreds by remember { mutableStateOf<Pair<String, String>?>(null) }
            var relogToken by remember { mutableStateOf(0) }
            var relogging by remember { mutableStateOf(false) }

            fun startSilentRelogin() {
                // One attempt at a time — the keep-alive can report the same
                // dead session again before the first POST finishes.
                if (relogging) return
                val stored = SecureCredentials.load(context) ?: return
                val number =
                    (prefs.getString("phone", "") ?: "").ifBlank { account.phoneNumber }
                if (number.isBlank()) return
                relogCreds = number.filter(Char::isDigit) to stored
                relogging = true
                relogAttempt++
            }

            // Watchdog: LoginDriver reports within ~10s of its POST, but a
            // server that never answers would otherwise leave the banner
            // spinning forever.
            LaunchedEffect(relogAttempt) {
                if (relogAttempt == 0) return@LaunchedEffect
                delay(45_000)
                if (relogging) {
                    relogCreds = null
                    relogging = false
                }
            }

            fun finishRelogin(result: DriverResult) {
                when (result) {
                    is DriverResult.Success -> {
                        persist(result.info)
                        CookieManager.getInstance().flush()
                        rememberNumber(result.info.phoneNumber)
                        relogToken++
                    }
                    // The stored password no longer works. Clear it so the
                    // app stops retrying — better one manual login than a
                    // retry loop against an authentication endpoint.
                    is DriverResult.Failure, DriverResult.Rejected ->
                        SecureCredentials.clear(context)
                }
                relogCreds = null
                relogging = false
            }

            CompositionLocalProvider(LocalThemeSettings provides themeSettings) {
                NjuBalanceTheme(settings = themeSettings) {
                    when {
                        currentPage != null -> FullScreenWebView(
                            url = currentPage!!.url,
                            title = currentPage!!.label,
                            onBack = { currentPage = null }
                        )

                        // No cached snapshot -> must log in first.
                        !hasSession -> LoginScreen(
                            cachedPhone = if (rememberPhone) {
                                prefs.getString("phone", "") ?: ""
                            } else "",
                            onRememberChange = { value ->
                                rememberPhone = value
                                prefs.edit()
                                    .putBoolean("remember_phone", value)
                                    .apply()
                                if (!value) rememberNumber("")
                            },
                            onLoginSuccess = { info ->
                                persist(info)
                                // The cookie lives in memory until flush() —
                                // without this the session dies with the process
                                // and the next cold start shows "Sesja wygasła".
                                CookieManager.getInstance().flush()
                                // Login success can arrive as a bare URL signal
                                // before any scrape, so don't wipe a known number.
                                rememberNumber(info.phoneNumber)
                                hasSession = true
                            }
                        )

                        else -> {
                            DashboardScreen(
                                account = account,
                                updatedAt = updatedAt,
                                displayName = displayName,
                                onDisplayNameChange = { value ->
                                    displayName = value
                                    prefs.edit().putString("displayName", value).apply()
                                },
                                onRefresh = { fresh ->
                                    persist(fresh)
                                    // Session proven alive by a successful scrape —
                                    // make sure it survives the next process death.
                                    CookieManager.getInstance().flush()
                                    rememberNumber(fresh.phoneNumber)
                                },
                                onLogout = {
                                    CookieManager.getInstance().removeAllCookies(null)
                                    CookieManager.getInstance().flush()
                                    WebStorage.getInstance().deleteAllData()
                                    AccountCache.clear(context)
                                    // An explicit logout means "don't come back
                                    // in on your own" — drop the key too.
                                    SecureCredentials.clear(context)
                                    // The remembered number is the point of the
                                    // switch — it survives a logout so the next
                                    // login form is already filled in.
                                    if (!rememberPhone) prefs.edit().remove("phone").apply()
                                    account = AccountInfo()
                                    updatedAt = 0L
                                    hasSession = false
                                },
                                onNavigate = { page -> currentPage = page },
                                onThemeChange = { themeSettings = it },
                                themeSettings = themeSettings,
                                onSessionLost = { startSilentRelogin() },
                                relogging = relogging,
                                relogToken = relogToken
                            )

                            // Invisible driver for the silent re-login. It owns
                            // its own WebView so the dashboard's refresher stays
                            // free to report the result.
                            if (relogCreds != null) {
                                LoginDriver(
                                    attempt = relogAttempt,
                                    credentials = relogCreds,
                                    onResult = { finishRelogin(it) },
                                    modifier = Modifier.size(1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
