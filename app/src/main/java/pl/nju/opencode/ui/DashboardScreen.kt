package pl.nju.opencode.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import pl.nju.opencode.BuildConfig
import pl.nju.opencode.R
import pl.nju.opencode.ui.theme.NjuCyan
import pl.nju.opencode.ui.theme.NjuInk
import pl.nju.opencode.ui.theme.NjuWhite
import pl.nju.opencode.ui.theme.NjuYellow
import pl.nju.opencode.ui.theme.ThemeMode
import pl.nju.opencode.ui.theme.ThemeSettings
import pl.nju.opencode.ui.theme.ThemeStyle
import pl.nju.opencode.ui.theme.njuBackgroundBrush
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.AddCard
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Autorenew

data class AccountInfo(
    val name: String = "",
    val phoneNumber: String = "",
    val offer: String = "",
    val balance: String = "",
    /** e.g. "7.5 GB" — remaining data allowance. */
    val dataRemaining: String = "",
    /** e.g. "30 GB" — allowance total, when the site exposes it. */
    val dataTotal: String = "",
    /** Plan renewal / period end, when the site exposes it. */
    val renewalDate: String = "",
    /** Raw page text from the scrape. Debug only, never rendered. */
    val raw: String = ""
) {
    val hasData: Boolean
        get() = dataRemaining.isNotBlank()

    /** True when a scrape produced nothing usable — must not overwrite cache. */
    val isUninitialized: Boolean
        get() = name.isBlank() && phoneNumber.isBlank() && offer.isBlank() &&
                balance.isBlank() && dataRemaining.isBlank()

    /** Merge newer scrape results over older ones without losing fields. */
    fun mergeWith(newer: AccountInfo): AccountInfo = AccountInfo(
        name = newer.name.ifBlank { name },
        phoneNumber = newer.phoneNumber.ifBlank { phoneNumber },
        offer = newer.offer.ifBlank { offer },
        balance = newer.balance.ifBlank { balance },
        dataRemaining = newer.dataRemaining.ifBlank { dataRemaining },
        dataTotal = newer.dataTotal.ifBlank { dataTotal },
        renewalDate = newer.renewalDate.ifBlank { renewalDate },
        raw = newer.raw.ifBlank { raw }
    )

    /**
     * How many known fields this snapshot carries.
     *
     * The account page paints in stages — the balance row lands well before
     * the data allowance — so a single scrape is often partial. Ranking
     * results by this lets the refresher keep the most complete one instead of
     * freezing on the first thing that happened to be on screen.
     */
    val completeness: Int
        get() = listOf(name, phoneNumber, offer, balance, dataRemaining, dataTotal, renewalDate)
            .count { it.isNotBlank() }
}

data class WebPage(
    val label: String,
    val url: String
)

/** Cap on the user-chosen greeting name so it always fits the top bar. */
private const val NAME_MAX_LENGTH = 30

/**
 * How often to poke the account page while the dashboard is in front.
 *
 * The site times its session out on inactivity, so silence is what signs the
 * user out. Well under the timeout (which is on the order of ten minutes) so
 * a locked phone or a backgrounded app still can't fall out of it unnoticed.
 */
private const val SESSION_KEEP_ALIVE_MS = 4 * 60_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    account: AccountInfo,
    updatedAt: Long = 0L,
    displayName: String = "",
    onDisplayNameChange: (String) -> Unit = {},
    onRefresh: (AccountInfo) -> Unit,
    onLogout: () -> Unit,
    onNavigate: (WebPage) -> Unit,
    onThemeChange: (ThemeSettings) -> Unit,
    themeSettings: ThemeSettings,
    onSessionLost: () -> Unit = {},
    relogging: Boolean = false,
    relogToken: Int = 0
) {
    val colors = MaterialTheme.colorScheme

    var offerLoading by remember(account.offer) { mutableStateOf(account.offer.isBlank()) }
    var balanceLoading by remember(account.balance) { mutableStateOf(account.balance.isBlank()) }
    var dataLoading by remember(account.dataRemaining) { mutableStateOf(account.dataRemaining.isBlank()) }
    // Demo edition boots already satisfied: the snapshot is fixed, so there is
    // nothing to re-scrape and no reason to spin up an invisible WebView.
    var refreshing by remember { mutableStateOf(!BuildConfig.DEMO) }
    var showSettings by remember { mutableStateOf(false) }

    // Set when the refresh bounced to /logowanie — the session cookie is gone.
    // The cached numbers stay visible, but the user needs a way back in.
    var sessionExpired by remember { mutableStateOf(false) }

    // A silent re-login just succeeded upstream: drop the expiry banner and
    // put the refresher back to work against the fresh cookie.
    LaunchedEffect(relogToken) {
        if (relogToken > 0) {
            sessionExpired = false
            refreshing = true
        }
    }

    // Keep-alive. The session doesn't die because the cookie is lost — it dies
    // because the site times out an idle session after a few minutes with no
    // request at all. Touching the account page on a timer is what actually
    // stops the sign-outs; the re-login path above is the fallback for when it
    // fails anyway (e.g. the app was dead past the timeout).
    var inForeground by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            inForeground = event == Lifecycle.Event.ON_RESUME
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(inForeground) {
        if (!inForeground || BuildConfig.DEMO) return@LaunchedEffect
        while (true) {
            delay(SESSION_KEEP_ALIVE_MS)
            if (inForeground && !sessionExpired) refreshing = true
        }
    }

    // Late-scraped fields settle on their own value or time out gracefully.
    LaunchedEffect(account) {
        if (account.offer.isNotBlank()) offerLoading = false
        if (account.balance.isNotBlank()) balanceLoading = false
        if (account.dataRemaining.isNotBlank()) dataLoading = false
        if (offerLoading) { delay(2000); offerLoading = false }
        if (balanceLoading) { delay(2500); balanceLoading = false }
        if (dataLoading) { delay(3000); dataLoading = false }
    }

    Scaffold(
        // Paint the brand gradient underneath; the scaffold itself stays clear
        // so it shows through instead of being covered by a flat fill.
        modifier = Modifier
            .fillMaxSize()
            .background(njuBackgroundBrush()),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    // "Cześć, <numer telefonu>" by default; the phone number is the only
                    // identity the account page actually exposes. A name the user
                    // set in settings takes precedence.
                    val handle = displayName.trim().ifBlank { account.phoneNumber.trim() }
                    Text(
                        text = if (handle.isEmpty()) "Moje konto" else "Cześć, $handle",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { refreshing = true }) {
                        Icon(Icons.Rounded.Autorenew, contentDescription = "Odśwież")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Ustawienia")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Wyloguj się",
                            tint = colors.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    // Clear so the brand gradient carries on behind the bar
                    // rather than being cut by a flat surface strip.
                    containerColor = Color.Transparent,
                    titleContentColor = colors.onSurface,
                    actionIconContentColor = colors.onSurfaceVariant
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // -- Freshness banner ------------------------------------------
            if (updatedAt > 0) {
                Text(
                    text = "Zaktualizowano ${formatTimestamp(updatedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // -- Session expiry --------------------------------------------
            if (sessionExpired) {
                val reconnecting = relogging
                val tone =
                    if (reconnecting) colors.onTertiaryContainer else colors.onErrorContainer
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (reconnecting) {
                            colors.tertiaryContainer
                        } else {
                            colors.errorContainer
                        }
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 16.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (reconnecting) {
                                    "Ponowne logowanie…"
                                } else {
                                    "Sesja wygasła"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = tone
                            )
                            Text(
                                text = if (reconnecting) {
                                    "Sesja wygasła — wznawiamy ją automatycznie."
                                } else {
                                    "Pokazujemy ostatnie zapisane dane. " +
                                        "Zaloguj się ponownie, aby je odświeżyć."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = tone
                            )
                        }
                        if (reconnecting) {
                            Spacer(Modifier.width(12.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = tone
                            )
                        }
                    }
                    // Hidden while reconnecting — offering a manual re-login
                    // next to an automatic one just invites a double POST.
                    if (!reconnecting) {
                        TextButton(onClick = onLogout) {
                            Text("Zaloguj się ponownie")
                        }
                    }
                }
            }

            // -- Data allowance: official-style cyan hero ---------------------
            DataHero(
                remaining = account.dataRemaining,
                total = account.dataTotal,
                loading = dataLoading,
                actions = listOf(
                    HeroAction("Profil", Icons.Rounded.Person) {
                        onNavigate(
                            WebPage(
                                "Profil",
                                "https://www.njumobile.pl/mojekonto/moj-profil"
                            )
                        )
                    },
                    HeroAction("Doładowanie", Icons.Rounded.AddCard) {
                        onNavigate(WebPage("Doładowania", "https://doladowania.njumobile.pl/"))
                    },
                    HeroAction("Kontakt", Icons.Rounded.SupportAgent) {
                        onNavigate(WebPage("Kontakt", "https://www.njumobile.pl/obsluga/kontakt"))
                    }
                )
            )

            Spacer(Modifier.height(12.dp))

            // -- Balance ------------------------------------------------------
            StatCard(
                title = "Twoje środki",
                value = account.balance,
                placeholderLoading = balanceLoading,
                // The account page words this as "środki ważne do …", so the
                // date belongs to the balance — not to the data allowance
                // where it used to sit.
                supporting = if (account.renewalDate.isNotBlank()) {
                    "ważne do ${account.renewalDate}"
                } else {
                    ""
                },
                icon = Icons.Rounded.AccountBalanceWallet,
                container = NjuYellow,
                content = NjuInk
            )

            Spacer(Modifier.height(12.dp))

            // -- Number + offer ------------------------------------------------
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = colors.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    LabelledValue("Twój numer", account.phoneNumber, loading = false)
                    Spacer(Modifier.height(12.dp))
                    LabelledValue("Twoja oferta", account.offer, offerLoading)
                }
            }

            Spacer(Modifier.height(12.dp))

            // -- Secondary destinations ----------------------------------------
            // Profil / Doładowanie / Kontakt moved up into the hero's circular
            // shortcuts, so this card only carries what's left.
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = colors.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "Więcej",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    NavRow("Zmień usługę", Icons.Rounded.CompareArrows) {
                        onNavigate(WebPage("Usługi", "https://www.njumobile.pl/mobile/mojekonto/uslugi"))
                    }
                    NavRow("Punkty sprzedaży", Icons.Rounded.Storefront) {
                        onNavigate(WebPage("Sprzedaż", "https://www.njumobile.pl/punkty-sprzedazy"))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Background refresh: re-scrape the account area using the live
            // session cookie, then push the result up to be cached.
            if (refreshing) {
                AccountRefresher(
                    onResult = { fresh ->
                        onRefresh(account.mergeWith(fresh))
                        // The balance row paints before the data allowance, so
                        // a first hit that only carries the balance isn't the
                        // end of the refresh — keep the refresher alive until
                        // the GB figure shows up (or the timer gives up).
                        if (fresh.dataRemaining.isNotBlank()) {
                            refreshing = false
                        }
                    },
                    onSessionExpired = {
                        refreshing = false
                        sessionExpired = true
                        // Let MainActivity try the stored credentials before
                        // making the user retype them.
                        onSessionLost()
                    },
                    onGiveUp = { refreshing = false }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NjuLogo(logoHeight = 16.dp)

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "nju.mobile · nieoficjalna · wersja aplikacji 1.0 " +
                        "· made with OpenCode",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            displayName = displayName,
            onDisplayNameChange = onDisplayNameChange,
            current = themeSettings,
            onSelect = { onThemeChange(it) },
            onDismiss = { showSettings = false }
        )
    }
}

private fun formatTimestamp(ms: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ms))

@Composable
private fun StatCard(
    title: String,
    value: String,
    placeholderLoading: Boolean,
    supporting: String,
    icon: ImageVector,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = container),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(content.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, color = content)
                AnimatedVisibility(
                    visible = placeholderLoading,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LoadingPlaceholder()
                }
                if (!placeholderLoading) {
                    Text(
                        text = value.ifBlank { "Brak danych" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = content
                    )
                    if (supporting.isNotBlank()) {
                        Text(
                            text = supporting,
                            style = MaterialTheme.typography.bodySmall,
                            color = content.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelledValue(label: String, value: String, loading: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        if (loading) {
            LoadingPlaceholder()
        } else {
            Text(
                text = value.ifBlank { "—" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
        }
    }
}

@Composable
private fun NavRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun LoadingPlaceholder(
    tint: androidx.compose.ui.graphics.Color? = null
) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "loading")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(
        modifier = Modifier
            .width(120.dp)
            .height(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background((tint ?: colors.onSurface).copy(alpha = alpha * 0.25f))
    )
}

/** One of the yellow circular shortcuts in the hero. */
private data class HeroAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * The data allowance, laid out the way the official nju. home screen does it:
 * a flat cyan field, a small "pozostało" lead-in, an oversized figure with the
 * total trailing it, then the row of yellow circular shortcuts.
 *
 * Colours here are the fixed brand constants rather than `colorScheme`, so the
 * hero keeps the nju identity even when the palette below is switched to the
 * neutral Material 3 or dynamic-colour styles.
 */
@Composable
private fun DataHero(
    remaining: String,
    total: String,
    loading: Boolean,
    actions: List<HeroAction>
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NjuCyan)
            .padding(20.dp)
    ) {
        Column {
            Text(
                text = "pozostało",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = NjuWhite.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(2.dp))

            if (loading) {
                LoadingPlaceholder(tint = NjuWhite)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = remaining.ifBlank { "Brak danych" },
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = NjuWhite
                    )
                    if (total.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "z $total",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = NjuWhite.copy(alpha = 0.85f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = NjuWhite.copy(alpha = 0.22f))
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                actions.forEach { HeroActionItem(it) }
            }
        }
    }
}

@Composable
private fun HeroActionItem(action: HeroAction) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = action.onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(NjuYellow),
            contentAlignment = Alignment.Center
        ) {
            Icon(action.icon, contentDescription = null, tint = NjuInk, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelMedium,
            color = NjuWhite,
            textAlign = TextAlign.Center
        )
    }
}

/** Settings bottom sheet — greeting name plus Material 3 theming. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    current: ThemeSettings,
    onSelect: (ThemeSettings) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        // Scrolling matters here: greeting + theme + palette together run past
        // the bottom of the screen and the colour options were unreachable.
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Ustawienia",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            Text(
                "Powitanie",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            OutlinedTextField(
                value = displayName,
                onValueChange = { onDisplayNameChange(it.take(NAME_MAX_LENGTH)) },
                label = { Text("Twoja nazwa") },
                placeholder = { Text("np. Kuba") },
                singleLine = true,
                supportingText = { Text("Puste — pokażę numer telefonu") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(20.dp))

            Text(
                "Motyw",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = current.mode == mode,
                        onClick = { onSelect(current.copy(mode = mode)) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)
                    ) {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Jasny"
                                ThemeMode.DARK -> "Ciemny"
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Kolorystyka",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                ThemeStyle.entries.forEach { style ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(current.copy(style = style)) }
                            .padding(8.dp)
                    ) {
                        RadioButton(
                            selected = current.style == style,
                            onClick = { onSelect(current.copy(style = style)) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (style) {
                                ThemeStyle.BRAND -> "nju (brand)"
                                ThemeStyle.NEUTRAL -> "Material 3 neutralne"
                                ThemeStyle.DYNAMIC -> "Dynamiczne (kolor tapety)"
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Background refresher
// ---------------------------------------------------------------------------

/**
 * Invisible WebView that re-reads the account area using the existing session
 * cookie and reports the scraped result. Runs once per dashboard entry so the
 * cached numbers are replaced by fresh ones without a manual pull.
 *
 * The navigation is checked before any scrape is scheduled: without a session
 * the site bounces straight back to /logowanie, and scraping that page would
 * pull marketing copy into the cache. Landing there is reported as an expired
 * session instead, so the UI can offer a re-login rather than silently showing
 * numbers that will never update.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AccountRefresher(
    onResult: (AccountInfo) -> Unit,
    onSessionExpired: () -> Unit,
    onGiveUp: () -> Unit
) {
    val callback = rememberUpdatedState(onResult)
    val expired = rememberUpdatedState(onSessionExpired)
    val giveUp = rememberUpdatedState(onGiveUp)
    var settled by remember { mutableStateOf(false) }

    // Highest completeness seen so far. Settling on the *first* usable scrape
    // was the bug that made the allowance disappear: the balance row renders
    // before the data panel, so that early hit had no GB in it and oneShot
    // discarded every later, more complete result.
    var bestScore by remember { mutableStateOf(0) }

    fun stop() {
        if (!settled) {
            settled = true
            giveUp.value()
        }
    }

    LaunchedEffect(Unit) {
        delay(15_000)
        stop()
    }

    AndroidView(
        modifier = Modifier.size(1.dp),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                visibility = android.view.View.INVISIBLE

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        Log.d("NjuRefresh", "finished: $url")
                        if (NjuWebViewBridge.isAccountUrl(url)) {
                            NjuWebViewBridge.scheduleScrapes(view)
                            // Debug builds only — this is the page the selectors
                            // need to be written against.
                            if (NjuWebViewBridge.isDumpAllowed(context)) {
                                NjuWebViewBridge.requestDomDump(view)
                            }
                        } else {
                            // Bounced to the login form: cookie is gone.
                            if (!settled) {
                                settled = true
                                expired.value()
                            }
                        }
                    }
                }

                addJavascriptInterface(
                    // Not one-shot: a later, more complete scrape is allowed to
                    // supersede an earlier partial one. Session expiry and the
                    // give-up timer still close the door via [settled].
                    NjuBridge(context, oneShot = false) { info ->
                        if (!settled && info.completeness > bestScore) {
                            bestScore = info.completeness
                            callback.value(info)
                        }
                    },
                    "AndroidBridge"
                )

                // The site lives under /mobile/ for the app — the original APK's
                // server_address string is https://www.njumobile.pl/mobile/.
                // Without it the path 404s and the scrape picks up marketing text.
                loadUrl("https://www.njumobile.pl/mobile/mojekonto-v2/")
            }
        }
    )
}
