package pl.nju.opencode.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import pl.nju.opencode.ui.theme.njuBackgroundBrush
import kotlinx.coroutines.delay
import androidx.compose.material.icons.rounded.Visibility

private const val TAG = "NjuLogin"
private const val LOGIN_URL = "https://www.njumobile.pl/logowanie"

/** Hard cap so the spinner can never outlive the attempt. */
private const val LOGIN_TIMEOUT_MS = 30_000L

private sealed interface AuthState {
    data object Idle : AuthState
    data object Working : AuthState
    data class Failed(val reason: String) : AuthState
    data object Succeeded : AuthState
}

/** [internal] so MainActivity can match on it for the silent re-login path. */
internal sealed interface DriverResult {
    data class Success(val info: AccountInfo) : DriverResult
    data class Failure(val reason: String) : DriverResult
    /** Page rejected the credentials — the site shows this as a form error. */
    data object Rejected : DriverResult
}

/**
 * Native Material 3 login form.
 *
 * The previous version rendered the site's own login page inside a WebView and
 * restyled it with injected CSS, which is why it read as scraped. This form is
 * real Compose UI. An invisible WebView still exists behind it: it owns the
 * session cookie and performs the POST, because the site exposes no API.
 *
 * Credentials are never logged or persisted by the app.
 */
@Composable
fun LoginScreen(
    cachedPhone: String = "",
    onRememberChange: (Boolean) -> Unit = {},
    onLoginSuccess: (AccountInfo) -> Unit
) {
    var phone by remember { mutableStateOf(cachedPhone) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(cachedPhone.isNotBlank()) }

    // Whether to keep the password for silent re-login. Separate from
    // "Zapamiętaj mnie": that one only prefills the number, this one is what
    // lets the app get back in on its own after the session drops.
    val context = LocalContext.current
    var keepPassword by remember { mutableStateOf(SecureCredentials.has(context)) }
    var state by remember { mutableStateOf<AuthState>(AuthState.Idle) }

    // What the invisible driver should do next.
    var attempt by remember { mutableStateOf(0) }
    var credentials by remember { mutableStateOf<Pair<String, String>?>(null) }

    val focusManager = LocalFocusManager.current
    val busy = state == AuthState.Working

    fun submit() {
        if (busy) return
        focusManager.clearFocus()
        val p = phone.filter(Char::isDigit)
        when {
            p.length != 9 -> state = AuthState.Failed("Numer telefonu musi mieć 9 cyfr")
            password.isBlank() -> state = AuthState.Failed("Wprowadź hasło")
            else -> {
                state = AuthState.Working
                credentials = p to password
                attempt++
            }
        }
    }

    // Never strand the user on a spinner: if the driver neither succeeds nor
    // rejects within the window, surface a failure instead of spinning forever.
    LaunchedEffect(state) {
        if (state != AuthState.Working) return@LaunchedEffect
        delay(LOGIN_TIMEOUT_MS)
        if (state == AuthState.Working) {
            state = AuthState.Failed("Nie udało się połączyć z serwerem. Spróbuj ponownie.")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Painted before the inset padding so the wash reaches the very top
            // and bottom of the window; only the content is inset.
            .background(njuBackgroundBrush())
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NjuLogo(logoHeight = 44.dp)

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Zaloguj się",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Sprawdź swoje konto, dane i faktury",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { input ->
                // Site validates ^[0-9]{9}$, so keep digits only.
                phone = input.filter(Char::isDigit).take(9)
                if (state is AuthState.Failed) state = AuthState.Idle
            },
            label = { Text("Numer telefonu") },
            singleLine = true,
            enabled = !busy,
            isError = phone.isNotEmpty() && phone.length != 9,
            supportingText = {
                if (phone.isNotEmpty() && phone.length != 9) Text("${phone.length}/9")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                if (state is AuthState.Failed) state = AuthState.Idle
            },
            label = { Text("Hasło") },
            singleLine = true,
            enabled = !busy,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) {
                            Icons.Rounded.VisibilityOff
                        } else {
                            Icons.Rounded.Visibility
                        },
                        contentDescription = if (passwordVisible) "Ukryj hasło" else "Pokaż hasło"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = rememberMe,
                onCheckedChange = {
                    rememberMe = it
                    onRememberChange(it)
                },
                enabled = !busy
            )
            Text(
                text = "Zapamiętaj mnie",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = keepPassword,
                onCheckedChange = {
                    keepPassword = it
                    // Unticking is the only way to make the password leave
                    // the device — see SecureCredentials for what storage
                    // does and does not protect against.
                    if (!it) SecureCredentials.clear(context)
                },
                enabled = !busy
            )
            Text(
                text = "Zapamiętaj hasło i loguj automatycznie",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = state is AuthState.Failed,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val message = (state as? AuthState.Failed)?.reason ?: ""
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { submit() },
            enabled = !busy,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Zaloguj się", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = { /* TODO: forgot-password URL */ }) {
            Text("Nie pamiętam hasła")
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Korzystając z aplikacji akceptujesz politykę prywatności",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    LoginDriver(
        attempt = attempt,
        credentials = credentials,
        onResult = { result ->
            credentials = null
            when (result) {
                is DriverResult.Success -> {
                    // Persist before the field is cleared; a second success
                    // signal (URL, then scrape) must not erase what the first
                    // one stored, hence the blank check rather than else-if.
                    if (keepPassword && password.isNotBlank()) {
                        SecureCredentials.save(context, password)
                    } else if (!keepPassword) {
                        SecureCredentials.clear(context)
                    }
                    password = ""
                    state = AuthState.Succeeded
                    onLoginSuccess(result.info)
                }
                is DriverResult.Failure -> state = AuthState.Failed(result.reason)
                DriverResult.Rejected -> {
                    password = ""
                    state = AuthState.Failed("Nieprawidłowy numer lub hasło")
                }
            }
        },
        modifier = Modifier.size(1.dp)
    )
}

/**
 * Invisible WebView that owns the session and drives the site's form.
 *
 * INVISIBLE rather than alpha 0: an alpha-0 WebView still consumes touches.
 * INVISIBLE keeps layout and loading alive but removes it from hit-testing.
 *
 * [internal] rather than private: MainActivity hosts its own instance for the
 * silent re-login path, so a dropped session can be recovered without
 * bouncing the user back to the login form.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun LoginDriver(
    attempt: Int,
    credentials: Pair<String, String>?,
    onResult: (DriverResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val resultCallback = rememberUpdatedState(onResult)
    var pageReady by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Fill + submit when the user taps the button and the page can take it.
    LaunchedEffect(attempt) {
        if (attempt == 0 || credentials == null) return@LaunchedEffect
        // Wait for the form to parse.
        var waited = 0
        while (!pageReady && waited < 4000) {
            delay(100)
            waited += 100
        }
        val view = webView ?: return@LaunchedEffect
        val outcome = view.evaluateJavascript(
            fillAndSubmitJs(credentials.first, credentials.second),
            null
        )
        Log.d(TAG, "submit attempt=$attempt outcome=$outcome")
        // The site bounces to /mojekonto on success, or re-renders the form
        // with an error on failure. Give it room to do either.
        delay(6000)
        if (pageReady && attempt > 0) {
            // Still on the login form after the POST -> credentials rejected.
            resultCallback.value(DriverResult.Rejected)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptCookie(true)
                visibility = View.INVISIBLE

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        injectMinimalStyles(view)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        injectMinimalStyles(view)
                        Log.d(TAG, "driver finished: $url")

                        val onAccount = NjuWebViewBridge.isAccountUrl(url)
                        pageReady = !onAccount

                        if (onAccount) {
                            // Authentication is decided by the URL, exactly as the
                            // original APK did with loginRedirect = "mojekonto".
                            // Waiting for a scrape to find content first would
                            // strand the user on a spinner when the selectors miss.
                            if (!reported) {
                                reported = true
                                resultCallback.value(DriverResult.Success(AccountInfo()))
                            }
                            NjuWebViewBridge.scheduleScrapes(view)
                            if (NjuWebViewBridge.isDumpAllowed(context)) {
                                NjuWebViewBridge.requestDomDump(view)
                            }
                        }
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            Log.w(TAG, "driver error: ${error?.description}")
                            resultCallback.value(
                                DriverResult.Failure("Brak połączenia z serwerem")
                            )
                        }
                    }
                }

                // Bridge forwards scraped account data once we are authenticated.
                addJavascriptInterface(
                    NjuBridge(context, oneShot = true) { info ->
                        resultCallback.value(DriverResult.Success(info))
                    },
                    "AndroidBridge"
                )

                loadUrl(LOGIN_URL)
                webView = this
            }
        },
        update = { view -> webView = view }
    )
}

/**
 * Fills the site's real form fields and submits it.
 * Selectors verified against the live page on 2026-10-01:
 *   #phone-input (name=phone-input), #password-input (name=password-form),
 *   #login-submit, form #portal-login-form
 */
private fun fillAndSubmitJs(phone: String, password: String): String {
    fun esc(s: String) = s.replace("\\", "\\\\").replace("'", "\\'")
    return """
        (function() {
            var phoneEl = document.getElementById('phone-input');
            var passEl  = document.getElementById('password-input');
            if (!phoneEl || !passEl) return 'missing';
            phoneEl.value = '${esc(phone)}';
            passEl.value  = '${esc(password)}';
            passEl.dispatchEvent(new Event('input', {bubbles:true}));
            var btn = document.getElementById('login-submit');
            if (btn) { btn.click(); return 'clicked'; }
            var form = document.getElementById('portal-login-form');
            if (form) { form.submit(); return 'submitted'; }
            return 'noaction';
        })();
    """.trimIndent()
}

/**
 * The form is invisible, so only neutralise things that could affect layout or
 * fire network calls — no cosmetic work is needed on a hidden page.
 */
private fun injectMinimalStyles(webView: WebView) {
    val js = """
        (function() {
            if (document.getElementById('nju-driver-style')) return;
            var s = document.createElement('style');
            s.id = 'nju-driver-style';
            s.innerHTML = 'body{background:transparent !important;margin:0 !important;}';
            document.head.appendChild(s);
        })();
    """.trimIndent()
    webView.evaluateJavascript(js, null)
}
