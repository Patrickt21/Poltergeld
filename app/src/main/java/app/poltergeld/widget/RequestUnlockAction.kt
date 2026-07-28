package app.poltergeld.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/**
 * Lock glyph tap while masked: launches [UnlockPrivacyActivity] to show the
 * biometric prompt. Uses an explicit startActivity call rather than Glance's
 * `actionStartActivity` modifier – see [OpenPositionAction] for why.
 */
class RequestUnlockAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        context.startActivity(
            Intent(context, UnlockPrivacyActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
