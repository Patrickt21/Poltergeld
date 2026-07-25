package app.poltergeld.widget

import android.content.Context
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

/** Fetches the portfolio and writes a snapshot into every widget instance's state. */
class RefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsRepository.get(applicationContext)
        // Error messages baked into the snapshot must match the chosen language.
        app.poltergeld.L10n.apply(settings.language)
        val snapshot = when (val res = GhostfolioClient.fetchPortfolio(settings)) {
            is PortfolioResult.Success -> {
                // Rank by performance over the selected range; holdings without a
                // performance figure (e.g. cash) can't be ranked.
                val ranked = res.holdings
                    .filter { it.performance != null }
                    .sortedByDescending { it.performance!! }
                val top = ranked.take(5)
                val flop = ranked.drop(top.size).takeLast(5).sortedBy { it.performance!! }
                WidgetSnapshot(
                    ok = true,
                    total = res.total,
                    currency = res.baseCurrency,
                    updatedAtEpochMs = System.currentTimeMillis(),
                    range = settings.range,
                    portfolioPerformance = res.portfolioPerformance,
                    top = top.map { it.toPosition() },
                    flop = flop.map { it.toPosition() },
                    positions = res.holdings.map { it.toPosition() },
                )
            }
            is PortfolioResult.Error -> {
                // Keep showing the last good data, marked stale, instead of
                // replacing the whole widget with an error message.
                val last = SettingsRepository.getLastSnapshot(applicationContext)
                    ?.let { runCatching { json.decodeFromString<WidgetSnapshot>(it) }.getOrNull() }
                last?.copy(stale = true, error = res.message)
                    ?: WidgetSnapshot(ok = false, error = res.message)
            }
        }

        val encoded = json.encodeToString(WidgetSnapshot.serializer(), snapshot)
        if (snapshot.ok && !snapshot.stale) {
            SettingsRepository.saveLastSnapshot(applicationContext, encoded)
        }
        val manager = androidx.glance.appwidget.GlanceAppWidgetManager(applicationContext)
        val ids = manager.getGlanceIds(PortfolioWidget::class.java)
        for (id in ids) {
            updateAppWidgetState(applicationContext, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().apply { this[WidgetKeys.SNAPSHOT] = encoded }
            }
        }
        PortfolioWidget().updateAll(applicationContext)
        return if (snapshot.ok && !snapshot.stale) Result.success() else Result.retry()
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
