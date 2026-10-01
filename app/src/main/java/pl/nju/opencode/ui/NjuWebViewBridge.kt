package pl.nju.opencode.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject
import java.io.File

private val mainHandler = Handler(Looper.getMainLooper())

/**
 * Bridge between the nju.mobile website and the native dashboard.
 *
 * Login detection is URL based, not text-scrape based. This mirrors the original
 * APK, which used `loginRedirect = "mojekonto"` inside shouldOverrideUrlLoading.
 * The site redirects to /mojekonto... only after a successful authentication, so
 * the URL is a far more reliable signal than pattern matching on innerText.
 */
object NjuWebViewBridge {

    private const val TAG = "NjuBridge"

    /** Path segment the site lands on after a successful login. */
    private const val ACCOUNT_MARKER = "mojekonto"

    /** Must not count as "logged in" — the login form lives here. */
    private const val LOGIN_MARKER = "logowanie"

    private const val SCRAPE_ATTEMPTS = 10
    private const val SCRAPE_DELAY_MS = 700L

    /** Success is reported this long after reaching the account page regardless, so the user is never stuck. */
    const val SCRAPE_TIMEOUT_MS = 8_000L

    fun isAccountUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        return lower.contains(ACCOUNT_MARKER) && !lower.contains(LOGIN_MARKER)
    }

    /**
     * Extracts account data and pushes JSON through [NjuBridge.onAccountData].
     * Returns '0' and calls nothing when the page holds no usable data, so the
     * caller can keep retrying while the dashboard finishes rendering.
     *
     * Field lookups are exact DOM queries taken from the live
     * `/mobile/mojekonto-v2/` dump rather than text patterns:
     *
     * ```
     * #your-account-home-infoservices .row
     *   span                     -> label  ("mój numer", "moja oferta", ...)
     *   .title-dashboard-summary -> value
     * ```
     *
     * Regexes over `innerText` were tried first and picked up marketing copy —
     * they returned "abonament" from the 404 page and the name "Czy" from a
     * chat widget's "Cześć, Czy potrzebujesz pomocy?". Both fall back to the
     * legacy text scan only when the expected block is absent.
     */
    val scrapeJs: String = """
        (function() {
            try {
                var body = document.body;
                var text = body ? (body.innerText || '') : '';
                var out = {};

                function clean(s) {
                    return (s || '').replace(/\s+/g, ' ').trim();
                }

                // Label -> value inside the summary rows. Exact label match wins;
                // a substring match is only used if the exact one never appears.
                function rowValue(exact, partial) {
                    var root = document.getElementById('your-account-home-infoservices');
                    if (!root) return '';
                    var rows = root.querySelectorAll('.row');
                    var loose = '';
                    for (var i = 0; i < rows.length; i++) {
                        var labelEl = rows[i].querySelector('span');
                        var valueEl = rows[i].querySelector('.title-dashboard-summary');
                        if (!labelEl || !valueEl) continue;
                        var label = clean(labelEl.textContent).toLowerCase();
                        var value = clean(valueEl.textContent);
                        if (!value) continue;
                        if (label === exact) return value;
                        if (!loose && label.indexOf(partial) !== -1) loose = value;
                    }
                    return loose;
                }

                out.phone = rowValue('mój numer', 'numer');
                out.offer = rowValue('moja oferta', 'oferta');
                out.balance = rowValue('dostępne środki', 'środk');

                // "pozostało 20 GB z 20 GB" — a <strong> in the data panel.
                //
                // The word part must not use \w: JS's \w is ASCII-only, so
                // `pozosta\w*` dies on the ł in "pozostało" and the pattern
                // silently never matches (Python's Unicode \w hid this while
                // the selector was being developed). [^\d] covers any letters,
                // diacritics and whitespace up to the first number.
                var strongs = document.querySelectorAll('strong');
                for (var k = 0; k < strongs.length; k++) {
                    var s = clean(strongs[k].textContent);
                    var m = s.match(/pozosta[^\d]*?([0-9]+(?:[,.][0-9]+)?)\s*(GB|MB)\s+z\s+([0-9]+(?:[,.][0-9]+)?)\s*(GB|MB)/i);
                    if (m) {
                        out.dataRemaining = m[1] + ' ' + m[2].toUpperCase();
                        out.dataTotal = m[3] + ' ' + m[4].toUpperCase();
                        break;
                    }
                }

                // (środki ważne do 2026-10-22)
                var renew = text.match(/środki ważne do\s*[:\s]*([0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{1,2}[./-][0-9]{1,2}[./-][0-9]{2,4})/i);
                out.renewalDate = renew ? renew[1] : '';

                out.raw = text.substring(0, 4000);

                // Phone / balance / data are the only trustworthy signals. Offer
                // alone is not: the login page and 404 page both mention offers.
                var usable = out.phone || out.balance || out.dataRemaining;
                if (usable && window.AndroidBridge) {
                    window.AndroidBridge.onAccountData(JSON.stringify(out));
                    return '1';
                }
                return '0';
            } catch (e) {
                return 'E:' + e;
            }
        })();
    """.trimIndent()

    private val dumpJs: String =
        "AndroidBridge.onDomDump(document.documentElement.outerHTML); '1';"

    /** True only in debug builds — dumps must never ship. */
    fun isDumpAllowed(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    fun parse(json: String): AccountInfo? = try {
        val o = JSONObject(json)
        AccountInfo(
            name = o.optString("name"),
            phoneNumber = o.optString("phone"),
            offer = o.optString("offer"),
            balance = o.optString("balance"),
            dataRemaining = o.optString("dataRemaining"),
            dataTotal = o.optString("dataTotal"),
            renewalDate = o.optString("renewalDate"),
            raw = o.optString("raw")
        )
    } catch (e: Exception) {
        Log.w(TAG, "parse failed: ${e.message}")
        null
    }

    fun scrape(webView: WebView) {
        webView.evaluateJavascript(scrapeJs, null)
    }

    fun scheduleScrapes(webView: WebView) {
        for (i in 1..SCRAPE_ATTEMPTS) {
            webView.postDelayed({ scrape(webView) }, SCRAPE_DELAY_MS * i)
        }
    }

    fun requestDomDump(webView: WebView) {
        webView.evaluateJavascript(dumpJs, null)
    }
}

/**
 * Single JS interface. Registered as "AndroidBridge" — only one object can hold
 * that name at a time, so scrape results and debug dumps share this class.
 *
 * [oneShot] suppresses repeat deliveries: `scheduleScrapes` runs up to ten
 * attempts, and every one of them would otherwise re-fire the callback.
 *
 * Results are posted to the main looper — `@JavascriptInterface` methods are
 * invoked on WebView's own background thread, which is not safe for writing
 * Compose state.
 */
class NjuBridge(
    private val context: Context,
    private val oneShot: Boolean = false,
    private val onData: (AccountInfo) -> Unit
) {
    @Volatile
    private var delivered = false

    @JavascriptInterface
    fun onAccountData(json: String) {
        if (oneShot && delivered) return
        val info = NjuWebViewBridge.parse(json) ?: return
        if (info.isUninitialized) return
        if (oneShot) delivered = true
        mainHandler.post { onData(info) }
    }

    /** Debug builds only: persists authenticated HTML for adb pull. */
    @JavascriptInterface
    fun onDomDump(html: String) {
        if (!NjuWebViewBridge.isDumpAllowed(context)) return
        try {
            val dir = context.getExternalFilesDir("debug") ?: context.filesDir
            val file = File(dir, "nju_dom_dump.html")
            file.writeText(html)
            Log.i(TAG, "DOM dumped -> ${file.absolutePath} (${html.length} chars)")
        } catch (e: Exception) {
            Log.w(TAG, "dump failed: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "NjuBridge"
    }
}
