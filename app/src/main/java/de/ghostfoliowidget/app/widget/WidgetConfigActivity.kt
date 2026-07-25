package de.ghostfoliowidget.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Per-instance widget configuration: lets each widget on the homescreen show
 * a different view. Declared via android:configure in the provider info and
 * reachable later through the launcher's "reconfigure" affordance.
 */
class WidgetConfigActivity : ComponentActivity() {

    private val modes = listOf(
        "auto" to "Automatic (adapts to widget size)",
        "summary" to "Summary only (total + performance)",
        "topflop" to "Top 5 / Flop 5",
        "all" to "All positions",
    )

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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(pad)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Widget view", style = MaterialTheme.typography.headlineSmall)
                        modes.forEach { (key, label) ->
                            Button(
                                onClick = { apply(appWidgetId, key) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun apply(appWidgetId: Int, mode: String) {
        lifecycleScope.launch {
            val manager = GlanceAppWidgetManager(this@WidgetConfigActivity)
            val glanceId = manager.getGlanceIdBy(appWidgetId)
            updateAppWidgetState(
                this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId,
            ) { prefs ->
                prefs.toMutablePreferences().apply { this[WidgetKeys.MODE] = mode }
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
