package app.poltergeld.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.poltergeld.data.GhostfolioClient
import app.poltergeld.data.Holding
import app.poltergeld.data.PortfolioResult
import app.poltergeld.data.SettingsRepository
import kotlinx.serialization.json.Json

/**
 * Fetches the portfolio once per distinct time range used by the widgets and
 * writes the matching snapshot into every widget instance's state.
 */
class RefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsRepository.get(applicationContext)
        // Error messages baked into the snapshot must match the chosen language.
        app.poltergeld.L10n.apply(settings.language)

        val manager = GlanceAppWidgetManager(applicationContext)
        val ids = manager.getGlanceIds(PortfolioWidget::class.java)
        // Widgets may pin their own range; unset ones follow the app setting.
        val widgetRange = ids.associateWith { id ->
            getAppWidgetState(applicationContext, PreferencesGlanceStateDefinition, id)[WidgetKeys.RANGE]
                ?: settings.range
        }
        val ranges = (widgetRange.values + settings.range).toSet()

        var anyTransientError = false
        var anyPermanentError = false
        val snapshots = ranges.associateWith { range ->
            when (val res = GhostfolioClient.fetchPortfolio(applicationContext, settings.copy(range = range))) {
                is PortfolioResult.Success -> {
                    val positions = res.holdings.map { it.toPosition() }
                    val (top, flop) = topFlop(positions)
                    WidgetSnapshot(
                        ok = true,
                        total = res.total,
                        currency = res.baseCurrency,
                        updatedAtEpochMs = System.currentTimeMillis(),
                        range = range,
                        portfolioPerformance = res.portfolioPerformance,
                        top = top,
                        flop = flop,
                        positions = positions,
                    )
                }
                is PortfolioResult.Error -> {
                    if (res.transient) anyTransientError = true else anyPermanentError = true
                    // Keep showing the last good data, marked stale, instead of
                    // replacing the whole widget with an error message.
                    val last = SettingsRepository.getLastSnapshot(applicationContext, range)
                        ?.let { runCatching { json.decodeFromString<WidgetSnapshot>(it) }.getOrNull() }
                    last?.copy(stale = true, error = res.message)
                        ?: WidgetSnapshot(ok = false, error = res.message, range = range)
                }
            }
        }

        val encoded = snapshots.mapValues { (range, snapshot) ->
            val text = json.encodeToString(WidgetSnapshot.serializer(), snapshot)
            if (snapshot.ok && !snapshot.stale) {
                SettingsRepository.saveLastSnapshot(applicationContext, range, text)
            }
            text
        }
        for (id: GlanceId in ids) {
            val text = encoded[widgetRange[id]] ?: continue
            updateAppWidgetState(applicationContext, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().apply { this[WidgetKeys.SNAPSHOT] = text }
            }
        }
        PortfolioWidget().updateAll(applicationContext)

        // Retrying only helps with transient failures (network, server 5xx);
        // configuration errors would just burn battery until the user fixes them.
        return when {
            anyTransientError -> Result.retry()
            anyPermanentError -> Result.failure()
            else -> Result.success()
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }

    private fun Holding.toPosition() = WidgetPosition(
        name = displayName,
        value = displayValue,
        allocation = allocationInPercentage,
        performance = performance,
        symbol = assetProfile?.symbol ?: symbol ?: displayName,
    )
}
