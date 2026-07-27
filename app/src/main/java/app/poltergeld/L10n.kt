package app.poltergeld

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Tiny two-language layer. Strings live next to their usage as tr(en, de)
 * pairs instead of resource files so the Glance widget and WorkManager code
 * paths localize exactly like the Compose UI. The active language is Compose
 * state, so switching it recomposes every open screen immediately.
 */
object L10n {
    var lang by mutableStateOf(systemDefault())
        private set

    fun set(value: String) {
        lang = if (value == "de") "de" else "en"
    }

    /** Apply the persisted choice; blank means "follow the system language". */
    fun apply(stored: String) {
        lang = when {
            stored.isBlank() -> systemDefault()
            stored == "de" -> "de"
            else -> "en"
        }
    }

    private fun systemDefault(): String =
        if (Locale.getDefault().language == "de") "de" else "en"

    /** Locale used for number formatting, following the chosen UI language. */
    fun numberLocale(): Locale = if (lang == "de") Locale.GERMANY else Locale.US
}

fun tr(en: String, de: String): String = if (L10n.lang == "de") de else en
