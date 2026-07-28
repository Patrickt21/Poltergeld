package app.poltergeld

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Number and time formatting shared by the app UI and the Glance widget.
 * Defaults follow the chosen UI language; the explicit parameters exist so
 * unit tests can pin locale, clock and zone.
 */
object Format {

    /** Stand-in for a monetary amount while privacy mode hides it. */
    const val MASK = "••••"

    fun money(value: Double, currency: String, locale: Locale = L10n.numberLocale()): String {
        val n = String.format(locale, "%,.2f", value)
        return if (currency.isBlank()) n else "$n $currency"
    }

    fun percent(value: Double, locale: Locale = L10n.numberLocale()): String =
        String.format(locale, "%.1f%%", value)

    /** "+3,2%" / "-1.0%" from a fraction (0.032 → +3,2%). */
    fun signedPercent(fraction: Double, locale: Locale = L10n.numberLocale()): String =
        (if (fraction >= 0) "+" else "") + percent(fraction * 100, locale)

    /**
     * Timestamp label for the widget header. Same day → "14:30"; older data is
     * marked with "yesterday"/"gestern" or the date so a stale snapshot can't
     * pass as fresh.
     */
    fun timeLabel(
        epochMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        yesterdayWord: String = tr("yday", "gestern"),
    ): String {
        if (epochMs <= 0) return ""
        val then = Instant.ofEpochMilli(epochMs).atZone(zone)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val time = then.format(DateTimeFormatter.ofPattern("HH:mm"))
        return when (then.toLocalDate()) {
            today -> time
            today.minusDays(1) -> "$yesterdayWord $time"
            else -> then.format(DateTimeFormatter.ofPattern("d.M.")) + " " + time
        }
    }
}
