package app.poltergeld.ui

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import app.poltergeld.data.GhostfolioClient
import app.poltergeld.data.Holding
import app.poltergeld.data.PortfolioResult
import app.poltergeld.data.Settings
import app.poltergeld.data.SettingsRepository
import app.poltergeld.widget.WidgetScheduler
import kotlinx.coroutines.launch
import java.util.Locale

private val green = Color(0xFF34D399)
private val red = Color(0xFFF87171)

private val rangeChips = listOf("1d" to "24h", "wtd" to "1W", "mtd" to "1M", "1y" to "1J")

// FragmentActivity (not ComponentActivity) so BiometricPrompt can attach.
class SettingsActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Scaffold { pad ->
                    AppRoot(Modifier.padding(pad), activity = this)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(modifier: Modifier = Modifier, activity: FragmentActivity) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf<Settings?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val s = SettingsRepository.get(context)
        settings = s
        showSettings = s.baseUrl.isBlank() || s.token.isBlank()
        if (s.requireUnlock) {
            AppLock.prompt(activity) { unlocked = true }
        } else {
            unlocked = true
        }
    }

    val s = settings ?: return
    if (!unlocked) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TextButton(onClick = { AppLock.prompt(activity) { unlocked = true } }) {
                Text("Unlock")
            }
        }
        return
    }
    if (showSettings) {
        SettingsScreen(modifier, initial = s, onClose = { updated ->
            settings = updated
            if (updated.baseUrl.isNotBlank() && updated.token.isNotBlank()) {
                showSettings = false
            }
        })
    } else {
        OverviewScreen(modifier, settings = s, onOpenSettings = { showSettings = true })
    }
}

/** Full portfolio overview: search, range, sorting, asset-class filter, details. */
@Composable
private fun OverviewScreen(
    modifier: Modifier = Modifier,
    settings: Settings,
    onOpenSettings: () -> Unit,
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
        result = GhostfolioClient.fetchPortfolio(settings.copy(range = range))
    }

    val current = selected
    if (current != null) {
        BackHandler { selected = null }
        DetailScreen(
            modifier,
            holding = current,
            baseCurrency = (result as? PortfolioResult.Success)?.baseCurrency.orEmpty(),
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
                Text("Portfolio", style = MaterialTheme.typography.headlineSmall)
                (result as? PortfolioResult.Success)?.let { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatMoney(r.total, r.baseCurrency),
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
            TextButton(onClick = { reload++ }) { Text("Refresh") }
            TextButton(onClick = onOpenSettings) { Text("Settings") }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            rangeChips.forEach { (key, label) ->
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
            label = { Text("Search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        when (val r = result) {
            null -> Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            is PortfolioResult.Error -> Text("Error: ${r.message}")
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
                    Text("Sort:", style = MaterialTheme.typography.bodySmall)
                    listOf("value" to "Value", "perf" to "Performance", "name" to "Name")
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
                            HoldingRow(h, r.baseCurrency)
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
    holding: Holding,
    baseCurrency: String,
    onBack: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
        }
        Text(holding.displayName, style = MaterialTheme.typography.headlineSmall)
        holding.assetProfile?.symbol?.let { DetailRow("Symbol", it) }
        holding.assetProfile?.assetClass?.let { DetailRow("Asset class", prettyClass(it)) }
        DetailRow("Value", formatMoney(holding.displayValue, baseCurrency))
        holding.performance?.let { p ->
            DetailRow("Performance", signedPercent(p), if (p >= 0) green else red)
        }
        holding.allocationInPercentage?.let {
            DetailRow("Allocation", String.format(Locale.GERMANY, "%.1f%%", it * 100))
        }
        holding.quantity?.let { DetailRow("Quantity", String.format(Locale.GERMANY, "%,.4f", it)) }
        holding.marketPrice?.let {
            DetailRow("Market price", formatMoney(it, holding.assetProfile?.currency.orEmpty()))
        }
        holding.investment?.let { DetailRow("Investment", formatMoney(it, baseCurrency)) }
        holding.dividend?.takeIf { it != 0.0 }?.let {
            DetailRow("Dividend", formatMoney(it, baseCurrency))
        }
    }
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
private fun HoldingRow(h: Holding, currency: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(h.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            h.allocationInPercentage?.let {
                Text(
                    String.format(Locale.GERMANY, "%.1f%% of portfolio", it * 100),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatMoney(h.displayValue, currency), style = MaterialTheme.typography.bodyLarge)
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
    initial: Settings,
    onClose: (Settings) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(initial.baseUrl) }
    var token by remember { mutableStateOf(initial.token) }
    var requireUnlock by remember { mutableStateOf(initial.requireUnlock) }
    var status by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Poltergeld", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Enter the base URL of your Ghostfolio instance and your Security Token " +
                "(Ghostfolio → My Ghostfolio → Security Token).",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Base URL (e.g. https://ghostfolio.example.com)") },
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Require unlock", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Ask for fingerprint or device PIN when opening the app.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = requireUnlock,
                onCheckedChange = { checked ->
                    requireUnlock = checked
                    scope.launch { SettingsRepository.saveRequireUnlock(context, checked) }
                },
            )
        }

        Button(
            onClick = {
                scope.launch {
                    SettingsRepository.save(context, url, token)
                    status = "Testing connection…"
                    val saved = SettingsRepository.get(context)
                    when (val r = GhostfolioClient.fetchPortfolio(saved)) {
                        is PortfolioResult.Success ->
                            status = "OK – ${r.holdings.size} positions loaded."
                        is PortfolioResult.Error ->
                            status = "Error: ${r.message}"
                    }
                    WidgetScheduler.schedulePeriodic(context)
                    WidgetScheduler.refreshNow(context)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save & test")
        }

        TextButton(
            onClick = { scope.launch { onClose(SettingsRepository.get(context)) } },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Back to overview")
        }

        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodyMedium)
        }

        Text(
            "Add the widget to your homescreen from the launcher's widget picker. " +
                "It refreshes hourly and whenever you tap Save.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun signedPercent(fraction: Double): String =
    (if (fraction >= 0) "+" else "") + String.format(Locale.GERMANY, "%.1f%%", fraction * 100)

private fun formatMoney(value: Double, currency: String): String {
    val n = String.format(Locale.GERMANY, "%,.2f", value)
    return if (currency.isBlank()) n else "$n $currency"
}

