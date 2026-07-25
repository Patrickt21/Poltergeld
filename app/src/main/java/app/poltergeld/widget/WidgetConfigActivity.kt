package app.poltergeld.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import app.poltergeld.data.SettingsRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Per-instance widget configuration: pick one of the fixed views or a custom
 * list of individual positions. Reachable when adding the widget and later via
 * the launcher's "reconfigure" affordance (long-press the widget).
 */
class WidgetConfigActivity : ComponentActivity() {

    private val modes = listOf(
        "auto" to "Automatic (adapts to widget size)",
        "summary" to "Summary only (total + performance)",
        "topflop" to "Top 5 / Flop 5",
        "all" to "All positions",
        "custom" to "Selected positions only",
    )

    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(RESULT_CANCELED, resultIntent(appWidgetId))

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Scaffold { pad ->
                    ConfigScreen(Modifier.padding(pad), appWidgetId)
                }
            }
        }
    }

    @Composable
    private fun ConfigScreen(modifier: Modifier, appWidgetId: Int) {
        var mode by remember { mutableStateOf("auto") }
        var selected by remember { mutableStateOf(setOf<String>()) }
        var positions by remember { mutableStateOf(listOf<WidgetPosition>()) }

        LaunchedEffect(Unit) {
            // Preload current config (reconfigure case) and the known positions.
            runCatching {
                val manager = GlanceAppWidgetManager(this@WidgetConfigActivity)
                val glanceId = manager.getGlanceIdBy(appWidgetId)
                val prefs = getAppWidgetState(
                    this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId,
                )
                prefs[WidgetKeys.MODE]?.let { mode = it }
                prefs[WidgetKeys.SELECTED]?.let { selected = it }
            }
            SettingsRepository.getLastSnapshot(this@WidgetConfigActivity)?.let { raw ->
                runCatching { json.decodeFromString<WidgetSnapshot>(raw) }
                    .getOrNull()
                    ?.let { positions = it.positions }
            }
        }

        Column(
            modifier = modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Widget view", style = MaterialTheme.typography.headlineSmall)

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(modes) { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { mode = key },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == key, onClick = { mode = key })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (mode == "custom") {
                    if (positions.isEmpty()) {
                        item {
                            Text(
                                "No positions known yet. Open the app once so it can " +
                                    "load your portfolio, then reconfigure the widget.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    items(positions) { pos ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = selected.toggle(pos.symbol) }
                                .padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = pos.symbol in selected,
                                onCheckedChange = { selected = selected.toggle(pos.symbol) },
                            )
                            Text(pos.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        }
                    }
                }
            }

            Button(
                onClick = { apply(appWidgetId, mode, selected) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apply")
            }
        }
    }

    private fun apply(appWidgetId: Int, mode: String, selected: Set<String>) {
        lifecycleScope.launch {
            val manager = GlanceAppWidgetManager(this@WidgetConfigActivity)
            val glanceId = manager.getGlanceIdBy(appWidgetId)
            updateAppWidgetState(
                this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId,
            ) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[WidgetKeys.MODE] = mode
                    this[WidgetKeys.SELECTED] = selected
                }
            }
            PortfolioWidget().update(this@WidgetConfigActivity, glanceId)
            WidgetScheduler.refreshNow(this@WidgetConfigActivity)
            setResult(RESULT_OK, resultIntent(appWidgetId))
            finish()
        }
    }

    private fun resultIntent(appWidgetId: Int) =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value
