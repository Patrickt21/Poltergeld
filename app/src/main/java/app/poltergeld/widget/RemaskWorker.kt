package app.poltergeld.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.poltergeld.data.SettingsRepository
import java.util.concurrent.TimeUnit

/**
 * One-shot job that clears a privacy reveal once its window has elapsed, so
 * the widget re-masks amounts deterministically – independent of user
 * interaction or the next periodic [RefreshWorker] run.
 */
class RemaskWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // ExistingWorkPolicy.REPLACE cancels *pending* work but not an already
        // running attempt, so an unlock happening right as the previous window
        // expires could race this worker: it would clear the freshly granted
        // reveal immediately after authentication. Only clear a window that
        // has actually elapsed.
        val until = SettingsRepository.getPrivacyUnlockedUntil(applicationContext)
        if (until != 0L && System.currentTimeMillis() >= until) {
            SettingsRepository.savePrivacyUnlockedUntil(applicationContext, 0L)
            PortfolioWidget().updateAll(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "ghostfolio_privacy_remask"

        fun scheduleAfterReveal(context: Context, delayMs: Long) {
            val req = OneTimeWorkRequestBuilder<RemaskWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
