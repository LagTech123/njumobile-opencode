package pl.nju.opencode.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import pl.nju.opencode.ui.theme.NjuCyan
import pl.nju.opencode.ui.theme.NjuTeal
import pl.nju.opencode.ui.theme.NjuYellow
import pl.nju.opencode.ui.theme.njuBackgroundBrush
import java.net.URI
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff

/**
 * Full-screen browser for the site's own pages (profile, top-up, contact, …).
 *
 * These are server-rendered and slow enough that a WebView dropped straight
 * into the layout shows a blank white rectangle for a second or two, which
 * reads as "the button did nothing". So the screen opens on a branded loading
 * page and only hands over to the WebView once the first page has actually
 * finished — and falls back to a retry state if it never does.
 *
 * The WebView is keyed on [loadToken] so a retry rebuilds it from scratch: a
 * failed load leaves a broken engine behind that reloading the URL won't clear.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FullScreenWebView(
    url: String,
    title: String,
    onBack: () -> Unit
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    // Bumped by "Spróbuj ponownie" to force a fresh WebView.
    var loadToken by remember { mutableStateOf(0) }

    // Back walks the site's own history first, then leaves for the dashboard.
    BackHandler(enabled = true) {
        val view = webView
        if (!failed && view != null && view.canGoBack()) {
            view.goBack()
        } else {
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(njuBackgroundBrush())
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Edge-to-edge: without this the title sits under the clock.
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Wstecz",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1
                )
            }
        }

        // Thin bar for navigations *inside* the page; the first load gets the
        // full loading page instead.
        AnimatedVisibility(visible = !failed && progress in 0.01f..0.99f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = NjuTeal,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            key(loadToken) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress / 100f
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView,
                                    url: String?,
                                    favicon: Bitmap?
                                ) {
                                    super.onPageStarted(view, url, favicon)
                                    progress = 0f
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    super.onPageFinished(view, url)
                                    progress = 1f
                                    ready = true
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError
                                ) {
                                    super.onReceivedError(view, request, error)
                                    // Sub-resources fail all the time on this site;
                                    // only a dead main frame is worth an error page.
                                    if (request.isForMainFrame) {
                                        failed = true
                                        ready = true
                                    }
                                }
                            }

                            loadUrl(url)
                            webView = this
                        }
                    },
                    update = { /* no updates needed */ }
                )
            }

            // The loading page / error page sits over the WebView so the blank
            // engine is never what the user sees. Fully qualified: inside Box
            // both BoxScope and ColumnScope are in scope, and the bare name
            // resolves to the scope-qualified overloads instead.
            androidx.compose.animation.AnimatedVisibility(
                visible = !ready || failed,
                modifier = Modifier.fillMaxSize()
            ) {
                if (failed) {
                    LoadFailedPage(
                        url = url,
                        onRetry = {
                            failed = false
                            ready = false
                            progress = 0f
                            loadToken++
                        },
                        onBack = onBack
                    )
                } else {
                    LoadingPage(title = title, url = url)
                }
            }
        }
    }
}

/** Branded holding screen shown until the first page finishes loading. */
@Composable
private fun LoadingPage(title: String, url: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NjuLogo(logoHeight = 36.dp)

        Spacer(Modifier.height(28.dp))

        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            strokeWidth = 3.dp,
            color = NjuTeal
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Ładowanie strony…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        hostOf(url)?.let {
            Spacer(Modifier.height(20.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Shown when the main frame never loads — offline, DNS failure, 5xx. */
@Composable
private fun LoadFailedPage(
    url: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Nie udało się wczytać strony",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        hostOf(url)?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("Wróć") }
            Button(onClick = onRetry) { Text("Spróbuj ponownie") }
        }
    }
}

/** Hostname for the small "where are we going" line, or null if unparseable. */
private fun hostOf(url: String): String? = try {
    URI(url).host?.removePrefix("www.")
} catch (_: Exception) {
    null
}
