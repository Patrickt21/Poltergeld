package app.poltergeld.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.poltergeld.Format
import app.poltergeld.L10n
import app.poltergeld.data.GhostfolioClient
import app.poltergeld.data.SUPPORTED_PRIVACY_REVEAL_SECONDS
import app.poltergeld.data.SUPPORTED_REFRESH_MINUTES
import app.poltergeld.data.Holding
import app.poltergeld.data.PortfolioResult
import app.poltergeld.data.Settings
import app.poltergeld.data.SettingsRepository
import app.poltergeld.tr
import app.poltergeld.widget.PortfolioWidgetReceiver
import app.poltergeld.widget.WidgetConfigActivity
import app.poltergeld.widget.WidgetKeys
import app.poltergeld.widget.WidgetScheduler
import app.poltergeld.widget.widgetModeLabel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val green = Color(0xFF34D399)
private val red = Color(0xFFF87171)

private fun rangeChips() = listOf("1d" to "24h", "wtd" to "1W", "mtd" to "1M", "1y" to tr("1Y", "1J"))

// FragmentActivity (not ComponentActivity) so BiometricPrompt can attach.
// singleTask (see manifest) so a second widget tap while the app is already
// open reuses this instance via onNewIntent instead of stacking a duplicate.
class SettingsActivity : FragmentActivity() {
    private val openSymbol = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openSymbol.value = intent?.getStringExtra(WidgetKeys.SYMBOL_EXTRA_KEY)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Scaffold { pad ->
                    AppRoot(Modifier.padding(pad), activity = this, initialSymbol = openSymbol)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openSymbol.value = intent.getStringExtra(WidgetKeys.SYMBOL_EXTRA_KEY)
    }
}

@Composable
private fun AppRoot(
    modifier: Modifier = Modifier,
    activity: FragmentActivity,
    initialSymbol: androidx.compose.runtime.State<String?>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<Settings?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var onboarding by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var promptActive by remember { mutableStateOf(false) }
    // Privacy mode's reveal state: in-memory only (no timer, unlike the
    // widget) – leaving the app re-masks it via the lifecycle observer below.
    var privacyRevealed by remember { mutableStateOf(false) }

    fun unlock() {
        if (promptActive) return
        promptActive = true
        AppLock.prompt(activity) { ok ->
            promptActive = false
            if (ok) locked = false
        }
    }

    fun revealPrivacy() {
        if (promptActive) return
        promptActive = true
        AppLock.prompt(activity) { ok ->
            promptActive = false
            if (ok) privacyRevealed = true
        }
    }

    // Hiding again needs no authentication – only revealing does.
    fun hidePrivacy() {
        privacyRevealed = false
    }

    LaunchedEffect(Unit) {
        val s = SettingsRepository.get(context)
        L10n.apply(s.language)
        settings = s
        onboarding = s.baseUrl.isBlank() || s.token.isBlank()
        if (s.requireUnlock) {
            locked = true
            unlock()
        }
    }

    // Blank the recents preview and block screenshots so portfolio numbers
    // never leak off this screen — while the app lock is enabled, but also
    // while Privacy Mode (app) is on: once revealed, the very amounts it
    // hides would otherwise be screenshot- or recents-thumbnail-able.
    val screenNeedsSecureFlag = settings?.requireUnlock == true || settings?.privacyModeApp == true
    LaunchedEffect(screenNeedsSecureFlag) {
        if (screenNeedsSecureFlag) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Re-lock whenever the app leaves the foreground; prompt again on return.
    // promptActive guards against the credential screen itself triggering
    // ON_STOP and looping the lock.
    val appLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(appLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !promptActive) privacyRevealed = false
            if (settings?.requireUnlock != true || promptActive) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_STOP -> locked = true
                Lifecycle.Event.ON_START -> if (locked) unlock()
                else -> {}
            }
        }
        appLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { appLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val s = settings ?: return
    if (locked) {
        LockScreen(modifier, onUnlock = ::unlock)
        return
    }
    if (onboarding) {
        OnboardingScreen(
            modifier,
            activity = activity,
            onLockChanged = { enabled -> settings = s.copy(requireUnlock = enabled) },
            onFinish = {
                scope.launch {
                    settings = SettingsRepository.get(context)
                    onboarding = false
                }
            },
        )
        return
    }
    if (showSettings) {
        SettingsScreen(
            modifier,
            activity = activity,
            initial = s,
            onLockChanged = { enabled -> settings = s.copy(requireUnlock = enabled) },
            onClose = { updated ->
                settings = updated
                if (updated.baseUrl.isNotBlank() && updated.token.isNotBlank()) {
                    showSettings = false
                }
            },
        )
    } else {
        OverviewScreen(
            modifier,
            settings = s,
            onOpenSettings = { showSettings = true },
            initialSymbol = initialSymbol.value,
            masked = s.privacyModeApp && !privacyRevealed,
            onRevealPrivacy = ::revealPrivacy,
            onHidePrivacy = ::hidePrivacy,
        )
    }
}

@Composable
private fun LockScreen(modifier: Modifier = Modifier, onUnlock: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("👻", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(8.dp))
        Text("Poltergeld", style = MaterialTheme.typography.headlineMedium)
        Text(tr("Locked", "Gesperrt"), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onUnlock) { Text(tr("Unlock", "Entsperren")) }
    }
}

/** Full portfolio overview: search, range, sorting, asset-class filter, details. */
@Composable
private fun OverviewScreen(
    modifier: Modifier = Modifier,
    settings: Settings,
    onOpenSettings: () -> Unit,
    initialSymbol: String? = null,
    masked: Boolean = false,
    onRevealPrivacy: () -> Unit = {},
    onHidePrivacy: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var range by remember { mutableStateOf(settings.range) }
    var sortKey by remember { mutableStateOf("value") }
    var ascending by remember { mutableStateOf(false) }
    var classFilter by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Holding?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<PortfolioResult?>(null) }

    LaunchedEffect(reload, range) {
        result = null
        result = GhostfolioClient.fetchPortfolio(context, settings.copy(range = range))
    }

    // Widget deep link: jump straight to the tapped position once its data
    // has loaded. initialSymbol itself never clears (it lives in the hosting
    // Activity so a second widget tap can reach it via onNewIntent), so this
    // tracks which symbol was already handled – otherwise every later
    // Refresh tap or range change (which produces a new `result` instance)
    // would re-fire the effect and yank the user back into the detail screen
    // even after they'd pressed Back.
    var handledSymbol by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(result, initialSymbol) {
        val symbol = initialSymbol ?: return@LaunchedEffect
        if (symbol == handledSymbol) return@LaunchedEffect
        val r = result as? PortfolioResult.Success ?: return@LaunchedEffect
        handledSymbol = symbol
        r.holdings.find { it.assetProfile?.symbol == symbol || it.symbol == symbol }
            ?.let { selected = it }
    }

    val current = selected
    if (current != null) {
        BackHandler { selected = null }
        DetailScreen(
            modifier,
            settings = settings,
            holding = current,
            baseCurrency = (result as? PortfolioResult.Success)?.baseCurrency.orEmpty(),
            masked = masked,
            onBack = { selected = null },
        )
        return
    }

    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Poltergeld", style = MaterialTheme.typography.headlineSmall)
                Text(tr("Widget for Ghostfolio", "Widget für Ghostfolio"), style = MaterialTheme.typography.bodySmall)
                (result as? PortfolioResult.Success)?.let { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (masked) Format.MASK else formatMoney(r.total, r.baseCurrency),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        r.portfolioPerformance?.let { p ->
                            Text(
                                "  " + signedPercent(p),
                                color = if (p >= 0) green else red,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }
            if (masked) {
                TextButton(onClick = onRevealPrivacy) {
                    Text("🔒", style = MaterialTheme.typography.headlineSmall)
                }
            } else if (settings.privacyModeApp) {
                // Revealed while privacy mode stays on: offer to hide again
                // immediately, no authentication needed for that direction.
                TextButton(onClick = onHidePrivacy) {
                    Text("🔓", style = MaterialTheme.typography.headlineSmall)
                }
            }
            TextButton(onClick = { reload++ }) { Text(tr("Refresh", "Aktualisieren")) }
            TextButton(onClick = onOpenSettings) { Text(tr("Settings", "Einstellungen")) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            rangeChips().forEach { (key, label) ->
                FilterChip(
                    selected = key == range,
                    onClick = {
                        range = key
                        scope.launch {
                            SettingsRepository.saveRange(context, key)
                            WidgetScheduler.refreshNow(context)
                        }
                    },
                    label = { Text(label) },
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(tr("Search", "Suche")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        when (val r = result) {
            null -> Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            is PortfolioResult.Error -> Text(tr("Error: ", "Fehler: ") + r.message)
            is PortfolioResult.Success -> {
                val classes = r.holdings
                    .mapNotNull { it.assetProfile?.assetClass }
                    .distinct()
                    .sorted()
                if (classes.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        classes.forEach { c ->
                            FilterChip(
                                selected = c == classFilter,
                                onClick = { classFilter = if (classFilter == c) null else c },
                                label = { Text(prettyClass(c)) },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tr("Sort:", "Sortierung:"), style = MaterialTheme.typography.bodySmall)
                    listOf(
                        "value" to tr("Value", "Wert"),
                        "perf" to "Performance",
                        "name" to "Name",
                    )
                        .forEach { (key, label) ->
                            FilterChip(
                                selected = key == sortKey,
                                onClick = {
                                    if (sortKey == key) ascending = !ascending else sortKey = key
                                },
                                label = { Text(label + if (key == sortKey) (if (ascending) " ↑" else " ↓") else "") },
                            )
                        }
                }

                val filtered = r.holdings
                    .filter { it.matches(query) }
                    .filter { classFilter == null || it.assetProfile?.assetClass == classFilter }
                    .let { list ->
                        val sorted = when (sortKey) {
                            "perf" -> list.sortedBy { it.performance ?: Double.NEGATIVE_INFINITY }
                            "name" -> list.sortedBy { it.displayName.lowercase() }
                            else -> list.sortedBy { it.displayValue }
                        }
                        if (ascending) sorted else sorted.reversed()
                    }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered) { h ->
                        Box(Modifier.clickable { selected = h }) {
                            HoldingRow(h, r.baseCurrency, masked)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(
    modifier: Modifier = Modifier,
    settings: Settings,
    holding: Holding,
    baseCurrency: String,
    masked: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val dataSource = holding.assetProfile?.dataSource
    val symbol = holding.assetProfile?.symbol ?: holding.symbol

    var loadingDetail by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<app.poltergeld.data.HoldingDetailResponse?>(null) }
    var activities by remember { mutableStateOf<List<app.poltergeld.data.Activity>>(emptyList()) }
    var chartError by remember { mutableStateOf<String?>(null) }
    var activitiesError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(holding) {
        loadingDetail = true
        detail = null
        activities = emptyList()
        chartError = null
        activitiesError = null
        if (dataSource != null && symbol != null) {
            coroutineScope {
                val detailDeferred = async {
                    GhostfolioClient.fetchHoldingDetail(context, settings, dataSource, symbol)
                }
                val activitiesDeferred = async {
                    GhostfolioClient.fetchActivities(context, settings, dataSource, symbol)
                }
                when (val d = detailDeferred.await()) {
                    is app.poltergeld.data.HoldingDetailResult.Success -> detail = d.detail
                    is app.poltergeld.data.HoldingDetailResult.Error -> chartError = d.message
                }
                when (val a = activitiesDeferred.await()) {
                    is app.poltergeld.data.ActivitiesResult.Success -> activities = a.activities
                    is app.poltergeld.data.ActivitiesResult.Error -> activitiesError = a.message
                }
            }
        } else {
            val noDataSource = tr(
                "No data source for this position.",
                "Keine Datenquelle für diese Position.",
            )
            chartError = noDataSource
            activitiesError = noDataSource
        }
        loadingDetail = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(tr("← Back", "← Zurück")) }
        }
        Text(holding.displayName, style = MaterialTheme.typography.headlineSmall)
        holding.assetProfile?.symbol?.let { DetailRow("Symbol", it) }
        holding.assetProfile?.assetClass?.let { DetailRow(tr("Asset class", "Anlageklasse"), prettyClass(it)) }
        DetailRow(tr("Value", "Wert"), if (masked) Format.MASK else formatMoney(holding.displayValue, baseCurrency))
        holding.performance?.let { p ->
            DetailRow("Performance", signedPercent(p), if (p >= 0) green else red)
        }
        holding.allocationInPercentage?.let {
            DetailRow(tr("Allocation", "Gewichtung"), Format.percent(it * 100))
        }
        holding.quantity?.let {
            DetailRow(tr("Quantity", "Stückzahl"), String.format(L10n.numberLocale(), "%,.4f", it))
        }
        holding.marketPrice?.let {
            DetailRow(
                tr("Market price", "Marktpreis"),
                if (masked) Format.MASK else formatMoney(it, holding.assetProfile?.currency.orEmpty()),
            )
        }
        holding.investment?.let {
            DetailRow(tr("Investment", "Investiert"), if (masked) Format.MASK else formatMoney(it, baseCurrency))
        }
        holding.dividend?.takeIf { it != 0.0 }?.let {
            DetailRow(tr("Dividend", "Dividende"), if (masked) Format.MASK else formatMoney(it, baseCurrency))
        }
        detail?.averagePrice?.let {
            DetailRow(
                tr("Average price", "Durchschnittspreis"),
                if (masked) Format.MASK else formatMoney(it, holding.assetProfile?.currency.orEmpty()),
            )
        }

        if (loadingDetail) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val chartValues = detail?.historicalData?.mapNotNull { it.chartValue }.orEmpty()
            if (!masked) {
                Spacer(Modifier.height(4.dp))
                Text(tr("Price history", "Kursverlauf"), style = MaterialTheme.typography.titleMedium)
                if (chartError != null) {
                    Text(tr("Error: ", "Fehler: ") + chartError, color = red, style = MaterialTheme.typography.bodySmall)
                } else {
                    // PriceChart shows its own "not enough history yet" message
                    // for < 2 points – gating on that here too would silently
                    // render nothing instead, hiding whether data ever arrived.
                    PriceChart(chartValues, holding.assetProfile?.currency ?: baseCurrency)
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(tr("Activity history", "Kauf-/Verkaufshistorie"), style = MaterialTheme.typography.titleMedium)
            if (activitiesError != null) {
                Text(
                    tr("Error: ", "Fehler: ") + activitiesError,
                    color = red,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (activities.isEmpty()) {
                Text(
                    tr("No activities found.", "Keine Aktivitäten gefunden."),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                activities.sortedByDescending { it.date }.forEach { a ->
                    ActivityRow(a, holding.assetProfile?.currency ?: baseCurrency, masked)
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(activity: app.poltergeld.data.Activity, currency: String, masked: Boolean = false) {
    val typeColor = when (activity.type) {
        "BUY" -> green
        "SELL" -> red
        else -> Color.Unspecified
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(activityTypeLabel(activity.type), color = typeColor, style = MaterialTheme.typography.bodyMedium)
            Text(formatActivityDate(activity.date), style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            val qty = activity.quantity
            val price = activity.unitPrice
            if (qty != null && price != null) {
                Text(
                    if (masked) {
                        Format.MASK
                    } else {
                        String.format(L10n.numberLocale(), "%,.4f", qty) + " × " + formatMoney(price, currency)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!masked) {
                activity.fee?.takeIf { it > 0.0 }?.let {
                    Text(
                        tr("Fee ", "Gebühr ") + formatMoney(it, currency),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun activityTypeLabel(type: String?): String = when (type) {
    "BUY" -> tr("Buy", "Kauf")
    "SELL" -> tr("Sell", "Verkauf")
    "DIVIDEND" -> tr("Dividend", "Dividende")
    "FEE" -> tr("Fee", "Gebühr")
    "INTEREST" -> tr("Interest", "Zinsen")
    "LIABILITY" -> tr("Liability", "Verbindlichkeit")
    else -> type ?: "?"
}

/** Ghostfolio sends activity dates as ISO-8601; a plain "d.M.yyyy" is all this screen needs. */
private fun formatActivityDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val pattern = java.time.format.DateTimeFormatter.ofPattern("d.M.yyyy")
    return runCatching { java.time.OffsetDateTime.parse(iso).toLocalDate().format(pattern) }
        .recoverCatching { java.time.LocalDate.parse(iso.take(10)).format(pattern) }
        .getOrDefault(iso.take(10))
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color? = null) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (valueColor != null) {
            Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun Holding.matches(query: String): Boolean {
    if (query.isBlank()) return true
    return displayName.contains(query, ignoreCase = true) ||
        (assetProfile?.symbol ?: symbol ?: "").contains(query, ignoreCase = true)
}

private fun prettyClass(raw: String): String =
    raw.lowercase().replace('_', ' ')
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }

@Composable
private fun HoldingRow(h: Holding, currency: String, masked: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(h.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            h.allocationInPercentage?.let {
                Text(
                    Format.percent(it * 100) + tr(" of portfolio", " des Portfolios"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (masked) Format.MASK else formatMoney(h.displayValue, currency),
                style = MaterialTheme.typography.bodyLarge,
            )
            h.performance?.let { p ->
                Text(
                    signedPercent(p),
                    color = if (p >= 0) green else red,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier = Modifier,
    activity: FragmentActivity,
    initial: Settings,
    onLockChanged: (Boolean) -> Unit,
    onClose: (Settings) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(initial.baseUrl) }
    var token by remember { mutableStateOf(initial.token) }
    var refreshMinutes by remember { mutableIntStateOf(initial.refreshMinutes) }
    var requireUnlock by remember { mutableStateOf(initial.requireUnlock) }
    var privacyModeWidget by remember { mutableStateOf(initial.privacyModeWidget) }
    var privacyModeApp by remember { mutableStateOf(initial.privacyModeApp) }
    var privacyRevealSeconds by remember { mutableIntStateOf(initial.privacyRevealSeconds) }
    var connectionStatus by remember { mutableStateOf("") }
    var lockStatus by remember { mutableStateOf("") }
    var privacyStatus by remember { mutableStateOf("") }
    var saveConfirmation by remember { mutableStateOf(false) }

    // Every switch/chip on this screen already persists itself the moment it
    // changes; this button re-confirms all of them at once (deliberately
    // excluding the URL/token fields, which stay gated behind "Test & save"
    // so a broken server address can never get saved silently) and shows a
    // brief checkmark so the user has an explicit "it's saved" moment.
    fun saveAll() {
        scope.launch {
            SettingsRepository.saveRefreshMinutes(context, refreshMinutes)
            SettingsRepository.saveRequireUnlock(context, requireUnlock)
            SettingsRepository.savePrivacyModeWidget(context, privacyModeWidget)
            SettingsRepository.savePrivacyModeApp(context, privacyModeApp)
            SettingsRepository.savePrivacyRevealSeconds(context, privacyRevealSeconds)
            saveConfirmation = true
            delay(2000)
            saveConfirmation = false
        }
    }

    // Homescreen widget instances (id, mode, custom count), reloaded whenever
    // the screen resumes so the list reflects config changes made in
    // WidgetConfigActivity.
    var widgets by remember { mutableStateOf(listOf<Triple<Int, String, Int>>()) }
    var lifecycleTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) lifecycleTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(lifecycleTick) {
        val ids = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, PortfolioWidgetReceiver::class.java))
        val manager = GlanceAppWidgetManager(context)
        widgets = ids.map { id ->
            runCatching {
                val prefs = getAppWidgetState(
                    context, PreferencesGlanceStateDefinition, manager.getGlanceIdBy(id),
                )
                val mode = prefs[WidgetKeys.MODE] ?: "auto"
                Triple(id, mode, prefs[WidgetKeys.SELECTED]?.size ?: 0)
            }.getOrDefault(Triple(id, "auto", 0))
        }
    }

    fun close() {
        scope.launch { onClose(SettingsRepository.get(context)) }
    }
    BackHandler { close() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tr("Settings", "Einstellungen"), style = MaterialTheme.typography.headlineSmall)
                Text(
                    tr("Poltergeld – Widget for Ghostfolio", "Poltergeld – Widget für Ghostfolio"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                if (saveConfirmation) tr("✓ Saved", "✓ Gespeichert") else "",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { saveAll() }) { Text(tr("Save", "Speichern")) }
            TextButton(onClick = { close() }) { Text(tr("Done", "Fertig")) }
        }

        Section(tr("Language", "Sprache")) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("en" to "English", "de" to "Deutsch").forEach { (key, label) ->
                    FilterChip(
                        selected = L10n.lang == key,
                        onClick = {
                            L10n.set(key)
                            scope.launch {
                                SettingsRepository.saveLanguage(context, key)
                                // Re-render widgets so their labels switch too.
                                WidgetScheduler.refreshNow(context)
                            }
                        },
                        label = { Text(label) },
                    )
                }
            }
        }

        Section(tr("Connection", "Verbindung")) {
            Text(
                tr(
                    "Your Ghostfolio instance and Security Token " +
                        "(Ghostfolio → My Ghostfolio → Security Token).",
                    "Deine Ghostfolio-Instanz und dein Security Token " +
                        "(Ghostfolio → Mein Ghostfolio → Security Token).",
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(tr("Base URL (e.g. https://ghostfolio.example.com)", "Basis-URL (z. B. https://ghostfolio.example.com)")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Security Token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    scope.launch {
                        connectionStatus = tr("Testing connection…", "Verbindung wird getestet…")
                        // Test the candidate credentials first; only a working
                        // configuration may replace the stored one.
                        val candidate = initial.copy(
                            baseUrl = SettingsRepository.normalizeUrl(url),
                            token = token.trim(),
                        )
                        when (val r = GhostfolioClient.fetchPortfolio(context, candidate, useCache = false)) {
                            is PortfolioResult.Success -> {
                                SettingsRepository.save(context, url, token)
                                connectionStatus = tr(
                                    "OK – ${r.holdings.size} positions loaded and saved.",
                                    "OK – ${r.holdings.size} Positionen geladen und gespeichert.",
                                )
                                WidgetScheduler.schedulePeriodic(context)
                                WidgetScheduler.refreshNow(context)
                            }
                            is PortfolioResult.Error ->
                                connectionStatus = tr("Error: ", "Fehler: ") + r.message + tr(
                                    " – previous settings kept.",
                                    " – bisherige Einstellungen bleiben erhalten.",
                                )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(tr("Test & save", "Testen & speichern"))
            }
            if (connectionStatus.isNotBlank()) {
                Text(connectionStatus, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Section(tr("Refresh interval", "Aktualisierungsintervall")) {
            Text(
                tr(
                    "How often the widgets fetch fresh data in the background. " +
                        "Shorter intervals use more battery.",
                    "Wie oft die Widgets im Hintergrund neue Daten laden. " +
                        "Kürzere Intervalle verbrauchen mehr Akku.",
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SUPPORTED_REFRESH_MINUTES.forEach { minutes ->
                    FilterChip(
                        selected = minutes == refreshMinutes,
                        onClick = {
                            refreshMinutes = minutes
                            scope.launch {
                                SettingsRepository.saveRefreshMinutes(context, minutes)
                                WidgetScheduler.schedulePeriodic(context)
                            }
                        },
                        label = { Text(intervalLabel(minutes)) },
                    )
                }
            }
        }

        Section(tr("Security", "Sicherheit")) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr("App lock", "App-Sperre"), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        tr(
                            "Ask for fingerprint or device PIN when opening or " +
                                "returning to the app. Also hides the app in recents " +
                                "and blocks screenshots.",
                            "Beim Öffnen oder Zurückkehren zur App Fingerabdruck oder " +
                                "Geräte-PIN verlangen. Blendet die App auch in der " +
                                "App-Übersicht aus und sperrt Screenshots.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = requireUnlock,
                    onCheckedChange = { checked ->
                        if (checked) {
                            // Confirm the user can actually pass the lock before
                            // turning it on, so nobody locks themselves out.
                            if (!AppLock.available(context)) {
                                lockStatus = tr(
                                    "No screen lock set up on this device – " +
                                        "add a PIN or fingerprint in the system settings first.",
                                    "Auf diesem Gerät ist keine Displaysperre eingerichtet – " +
                                        "lege zuerst PIN oder Fingerabdruck in den " +
                                        "Systemeinstellungen an.",
                                )
                            } else {
                                AppLock.prompt(activity) { ok ->
                                    if (ok) {
                                        requireUnlock = true
                                        scope.launch {
                                            SettingsRepository.saveRequireUnlock(context, true)
                                        }
                                        onLockChanged(true)
                                        lockStatus = ""
                                    }
                                }
                            }
                        } else {
                            requireUnlock = false
                            scope.launch { SettingsRepository.saveRequireUnlock(context, false) }
                            onLockChanged(false)
                            lockStatus = ""
                        }
                    },
                )
            }
            if (lockStatus.isNotBlank()) {
                Text(lockStatus, style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr("Privacy mode (widget)", "Privacy Mode (Widget)"), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        tr(
                            "Hide amounts in the homescreen widget by default; reveal " +
                                "them briefly with fingerprint or device PIN.",
                            "Beträge im Homescreen-Widget standardmäßig ausblenden; " +
                                "kurzzeitig mit Fingerabdruck oder Geräte-PIN einblenden.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = privacyModeWidget,
                    onCheckedChange = { checked ->
                        if (checked && !AppLock.available(context)) {
                            privacyStatus = tr(
                                "No screen lock set up on this device – " +
                                    "add a PIN or fingerprint in the system settings first.",
                                "Auf diesem Gerät ist keine Displaysperre eingerichtet – " +
                                    "lege zuerst PIN oder Fingerabdruck in den " +
                                    "Systemeinstellungen an.",
                            )
                        } else {
                            privacyModeWidget = checked
                            privacyStatus = ""
                            scope.launch {
                                SettingsRepository.savePrivacyModeWidget(context, checked)
                                if (!checked) SettingsRepository.savePrivacyUnlockedUntil(context, 0L)
                                WidgetScheduler.refreshNow(context)
                            }
                        }
                    },
                )
            }

            if (privacyModeWidget) {
                Text(
                    tr(
                        "How long revealed amounts stay visible in the widget " +
                            "before auto-hiding again.",
                        "Wie lange eingeblendete Beträge im Widget sichtbar " +
                            "bleiben, bevor sie automatisch wieder ausgeblendet werden.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SUPPORTED_PRIVACY_REVEAL_SECONDS.forEach { seconds ->
                        FilterChip(
                            selected = seconds == privacyRevealSeconds,
                            onClick = {
                                privacyRevealSeconds = seconds
                                scope.launch {
                                    SettingsRepository.savePrivacyRevealSeconds(context, seconds)
                                }
                            },
                            label = { Text(revealDurationLabel(seconds)) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr("Privacy mode (app)", "Privacy Mode (App)"), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        tr(
                            "Hide amounts in the app by default; reveal them with " +
                                "fingerprint or device PIN until the app leaves the foreground.",
                            "Beträge in der App standardmäßig ausblenden; mit " +
                                "Fingerabdruck oder Geräte-PIN einblenden, bis die App in " +
                                "den Hintergrund geht.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = privacyModeApp,
                    onCheckedChange = { checked ->
                        if (checked && !AppLock.available(context)) {
                            privacyStatus = tr(
                                "No screen lock set up on this device – " +
                                    "add a PIN or fingerprint in the system settings first.",
                                "Auf diesem Gerät ist keine Displaysperre eingerichtet – " +
                                    "lege zuerst PIN oder Fingerabdruck in den " +
                                    "Systemeinstellungen an.",
                            )
                        } else {
                            privacyModeApp = checked
                            privacyStatus = ""
                            scope.launch { SettingsRepository.savePrivacyModeApp(context, checked) }
                        }
                    },
                )
            }
            if (privacyStatus.isNotBlank()) {
                Text(privacyStatus, style = MaterialTheme.typography.bodySmall)
            }
        }

        Section(tr("Homescreen widgets", "Homescreen-Widgets")) {
            if (widgets.isEmpty()) {
                Text(
                    tr(
                        "No Poltergeld widgets on the homescreen yet.",
                        "Noch keine Poltergeld-Widgets auf dem Homescreen.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            widgets.forEachIndexed { index, (id, mode, customCount) ->
                if (index > 0) HorizontalDivider()
                val label = widgetModeLabel(mode) +
                    if (mode == "custom") " ($customCount)" else ""
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(
                                Intent(context, WidgetConfigActivity::class.java)
                                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Widget ${index + 1}", style = MaterialTheme.typography.bodyLarge)
                        Text(label, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("›", style = MaterialTheme.typography.headlineSmall)
                }
            }
            val widgetManager = AppWidgetManager.getInstance(context)
            if (widgetManager.isRequestPinAppWidgetSupported) {
                OutlinedButton(
                    onClick = {
                        widgetManager.requestPinAppWidget(
                            ComponentName(context, PortfolioWidgetReceiver::class.java),
                            null,
                            null,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(tr("+ Add widget to homescreen", "+ Widget zum Homescreen hinzufügen"))
                }
            } else {
                Text(
                    tr(
                        "Add one via long-press on the homescreen → Widgets → " +
                            "Poltergeld Portfolio.",
                        "Hinzufügen per langem Druck auf den Homescreen → Widgets → " +
                            "Poltergeld Portfolio.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                tr(
                    "Each widget can show its own view – summary, top & flop, all " +
                        "positions or a custom watchlist – and pin its own time " +
                        "range via the chips in the widget. Tap a widget above to " +
                        "configure it.",
                    "Jedes Widget kann seine eigene Ansicht zeigen – Zusammenfassung, " +
                        "Top & Flop, alle Positionen oder eine eigene Watchlist – und " +
                        "über die Chips im Widget seinen eigenen Zeitraum festlegen. " +
                        "Tippe oben auf ein Widget, um es zu konfigurieren.",
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        AboutSection()
    }
}

private const val GITHUB_URL = "https://github.com/Patrickt21/Poltergeld"
private const val LIGHTNING_ADDRESS = "tip@muota.li"

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var tipStatus by remember { mutableStateOf("") }
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Section(tr("About", "Über")) {
        Text(
            "Poltergeld $version – " + tr(
                "open source, no trackers.",
                "Open Source, keine Tracker.",
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tr("Source code & issues", "Quellcode & Fehlermeldungen"),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text("github.com/Patrickt21/Poltergeld", style = MaterialTheme.typography.bodySmall)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    // Open a Lightning wallet if one is installed; otherwise put
                    // the address on the clipboard.
                    val opened = runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("lightning:$LIGHTNING_ADDRESS"))
                        )
                    }.isSuccess
                    tipStatus = if (opened) {
                        ""
                    } else {
                        clipboard.setText(AnnotatedString(LIGHTNING_ADDRESS))
                        tr(
                            "No Lightning wallet found – address copied to clipboard.",
                            "Keine Lightning-Wallet gefunden – Adresse in die Zwischenablage kopiert.",
                        )
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tr("Enjoying the app? Leave a tip", "Gefällt dir die App? Gib ein Trinkgeld"),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text("⚡ $LIGHTNING_ADDRESS", style = MaterialTheme.typography.bodySmall)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
        if (tipStatus.isNotBlank()) {
            Text(tipStatus, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Titled card grouping related settings. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

private fun intervalLabel(minutes: Int): String =
    if (minutes < 60) "$minutes min" else "${minutes / 60} h"

private fun revealDurationLabel(seconds: Int): String =
    if (seconds < 60) "$seconds s" else "${seconds / 60} min"

private fun signedPercent(fraction: Double): String = Format.signedPercent(fraction)

private fun formatMoney(value: Double, currency: String): String = Format.money(value, currency)

