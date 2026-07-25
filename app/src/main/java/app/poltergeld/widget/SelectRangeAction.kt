package app.poltergeld.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import app.poltergeld.data.SettingsRepository

/** Chip tap in the widget: persist the chosen time range and refetch. */
class SelectRangeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val range = parameters[RANGE] ?: return
        SettingsRepository.saveRange(context, range)
        WidgetScheduler.refreshNow(context)
    }

    companion object {
        val RANGE = ActionParameters.Key<String>("range")
    }
}
