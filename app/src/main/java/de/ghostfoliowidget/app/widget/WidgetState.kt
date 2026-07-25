package de.ghostfoliowidget.app.widget

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
)
