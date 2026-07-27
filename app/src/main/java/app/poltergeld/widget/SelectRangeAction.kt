package app.poltergeld.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import app.poltergeld.data.SettingsRepository

/**
 * Chip tap in the widget: pin the chosen time range to this widget instance
 * only (other widgets and the app keep theirs) and refetch.
 */
class SelectRangeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val range = parameters[RANGE] ?: return
        // Show the last known data for that range immediately (marked by the
        // selected chip); the refresh below replaces it with fresh numbers.
        val cached = SettingsRepository.getLastSnapshot(context, range)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[WidgetKeys.RANGE] = range
                cached?.let { this[WidgetKeys.SNAPSHOT] = it }
            }
        }
        PortfolioWidget().update(context, glanceId)
        WidgetScheduler.refreshNow(context)
    }

    companion object {
        val RANGE = ActionParameters.Key<String>("range")
    }
}
