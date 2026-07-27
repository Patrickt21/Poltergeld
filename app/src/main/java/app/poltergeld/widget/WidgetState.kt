package app.poltergeld.widget

import kotlinx.serialization.Serializable

/** Snapshot rendered by the widget, persisted in Glance preferences state. */
@Serializable
data class WidgetSnapshot(
    val ok: Boolean = false,
    val error: String? = null,
    val total: Double = 0.0,
    val currency: String = "",
    val updatedAtEpochMs: Long = 0L,
    val range: String = "1d",
    val portfolioPerformance: Double? = null,
    val stale: Boolean = false,
    val top: List<WidgetPosition> = emptyList(),
    val flop: List<WidgetPosition> = emptyList(),
    val positions: List<WidgetPosition> = emptyList(),
)

@Serializable
data class WidgetPosition(
    val name: String,
    val value: Double,
    val allocation: Double?,
    val performance: Double?,
    /** Stable identifier used by the per-widget position picker. */
    val symbol: String = "",
)

/**
 * Ranks positions by performance over the selected range: the best [n] and –
 * from the remainder, so the lists never overlap – the worst [n], worst first.
 * Positions without a performance figure (e.g. cash) can't be ranked.
 */
fun topFlop(positions: List<WidgetPosition>, n: Int = 5): Pair<List<WidgetPosition>, List<WidgetPosition>> {
    val ranked = positions
        .filter { it.performance != null }
        .sortedByDescending { it.performance!! }
    val top = ranked.take(n)
    val flop = ranked.drop(top.size).takeLast(n).sortedBy { it.performance!! }
    return top to flop
}
