package app.poltergeld.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import app.poltergeld.ui.SettingsActivity

/**
 * Position row tap: opens SettingsActivity straight to that position's detail
 * screen. Routed through an ActionCallback with an explicit startActivity
 * call instead of Glance's `actionStartActivity` modifier – nesting a second
 * (or same-class, different-parameters) actionStartActivity target inside a
 * widget whose whole body is *also* an actionStartActivity click has proven
 * unreliable in practice (taps landing on the wrong target). actionRunCallback
 * is the mechanism already used reliably elsewhere in this widget (refresh,
 * range chips), so every inner click now goes through it.
 */
class OpenPositionAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val symbol = parameters[WidgetKeys.SYMBOL_EXTRA] ?: return
        context.startActivity(
            Intent(context, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(WidgetKeys.SYMBOL_EXTRA_KEY, symbol)
        )
    }
}
