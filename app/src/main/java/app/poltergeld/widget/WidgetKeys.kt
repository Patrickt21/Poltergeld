package app.poltergeld.widget

import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.poltergeld.tr

object WidgetKeys {
    val SNAPSHOT = stringPreferencesKey("snapshot_json")

    /** Per-instance display mode: auto | summary | topflop | all | custom. */
    val MODE = stringPreferencesKey("display_mode")

    /** Per-instance time range; unset = follow the app-wide range setting. */
    val RANGE = stringPreferencesKey("display_range")

    /** Symbols chosen for the "custom" mode of this widget instance. */
    val SELECTED = stringSetPreferencesKey("selected_symbols")
}

/** Display mode keys, shared by config UIs; labels via [widgetModeLabel]. */
val WIDGET_MODE_KEYS = listOf("auto", "summary", "topflop", "all", "custom")

fun widgetModeLabel(key: String): String = when (key) {
    "auto" -> tr("Automatic (adapts to widget size)", "Automatisch (passt sich der Widget-Größe an)")
    "summary" -> tr("Summary only (total + performance)", "Nur Zusammenfassung (Gesamtwert + Performance)")
    "topflop" -> "Top 5 / Flop 5"
    "all" -> tr("All positions", "Alle Positionen")
    "custom" -> tr("Selected positions only", "Nur ausgewählte Positionen")
    else -> key
}
