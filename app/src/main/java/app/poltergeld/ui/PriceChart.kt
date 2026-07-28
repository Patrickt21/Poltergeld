package app.poltergeld.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import app.poltergeld.Format
import app.poltergeld.tr

private val chartGreen = Color(0xFF34D399)
private val chartRed = Color(0xFFF87171)

/**
 * Minimal price-history chart: a Canvas polyline through min/max-normalized
 * values, colored by overall trend. No charting library – Compose Foundation
 * (already a dependency) is enough for this.
 */
@Composable
fun PriceChart(values: List<Double>, currency: String, modifier: Modifier = Modifier) {
    if (values.size < 2) {
        Text(
            tr("Not enough price history yet", "Noch nicht genug Kursverlauf"),
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }

    val min = values.min()
    val max = values.max()
    val range = (max - min).takeIf { it > 0.0 } ?: 1.0
    val color = if (values.last() >= values.first()) chartGreen else chartRed

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val stepX = if (values.size > 1) size.width / (values.size - 1) else 0f
            val points = values.mapIndexed { i, v ->
                Offset(
                    x = i * stepX,
                    y = size.height - ((v - min) / range * size.height).toFloat(),
                )
            }
            for (i in 0 until points.size - 1) {
                drawLine(
                    color = color,
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                tr("Low", "Tief") + " " + Format.money(min, currency),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                tr("High", "Hoch") + " " + Format.money(max, currency),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
