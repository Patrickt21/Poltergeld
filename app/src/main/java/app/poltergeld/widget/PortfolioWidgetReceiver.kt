package app.poltergeld.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.poltergeld.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PortfolioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PortfolioWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // The stored refresh interval lives in DataStore, which must not be
        // read on the receiver's main thread; keep the receiver alive briefly.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetScheduler.schedulePeriodic(context)
                WidgetScheduler.refreshNow(context)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetScheduler.refreshNow(context)
    }
}

object WidgetScheduler {
    private const val PERIODIC = "ghostfolio_periodic_refresh"
    private const val ONESHOT = "ghostfolio_refresh_now"

    /** (Re-)schedules the periodic refresh using the stored interval. */
    suspend fun schedulePeriodic(context: Context) {
        val minutes = SettingsRepository.get(context).refreshMinutes
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(minutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun refreshNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONESHOT, ExistingWorkPolicy.REPLACE, req)
    }
}
