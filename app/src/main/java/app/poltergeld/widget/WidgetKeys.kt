package app.poltergeld.widget

import androidx.datastore.preferences.core.stringPreferencesKey

object WidgetKeys {
    val SNAPSHOT = stringPreferencesKey("snapshot_json")

    /** Per-instance display mode: auto | summary | topflop | all. */
    val MODE = stringPreferencesKey("display_mode")
}
