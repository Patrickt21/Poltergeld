package app.poltergeld.widget

import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

object WidgetKeys {
    val SNAPSHOT = stringPreferencesKey("snapshot_json")

    /** Per-instance display mode: auto | summary | topflop | all | custom. */
    val MODE = stringPreferencesKey("display_mode")

    /** Symbols chosen for the "custom" mode of this widget instance. */
    val SELECTED = stringSetPreferencesKey("selected_symbols")
}

/** Display modes with their user-facing labels, shared by config UIs. */
val WIDGET_MODES = listOf(
    "auto" to "Automatic (adapts to widget size)",
    "summary" to "Summary only (total + performance)",
    "topflop" to "Top 5 / Flop 5",
    "all" to "All positions",
    "custom" to "Selected positions only",
)
