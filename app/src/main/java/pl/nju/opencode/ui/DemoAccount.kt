package pl.nju.opencode.ui

/**
 * The single account snapshot the demo edition boots with.
 *
 * The demo build exists so the dashboard can be photographed and the repo
 * illustrated without ever putting a real account on screen: it has no
 * credentials, never opens a WebView, and never sends a packet to the site.
 * Every value below is invented — swap in different fake numbers here if you
 * want fresh screenshots rather than editing the app.
 */
internal val DemoAccount = AccountInfo(
    name = "",
    phoneNumber = "501234567",
    offer = "nju na kartę",
    balance = "50,00 zł",
    dataRemaining = "14,7 GB",
    dataTotal = "20 GB",
    renewalDate = "2026-11-12"
)
