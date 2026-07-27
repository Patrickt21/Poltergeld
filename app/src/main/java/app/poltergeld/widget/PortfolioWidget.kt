package app.poltergeld.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.poltergeld.Format
import app.poltergeld.L10n
import app.poltergeld.data.SettingsRepository
import app.poltergeld.tr
import app.poltergeld.ui.SettingsActivity
import kotlinx.serialization.json.Json

// Day/night pairs: the widget follows the system theme.
private val bg = ColorProvider(day = Color(0xFFF4F5F7), night = Color(0xFF14161B))
private val card = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1E2129))
private val fg = ColorProvider(day = Color(0xFF191C21), night = Color(0xFFECEDEE))
private val muted = ColorProvider(day = Color(0xFF667080), night = Color(0xFF9AA0AA))
private val green = ColorProvider(day = Color(0xFF047857), night = Color(0xFF34D399))
private val red = ColorProvider(day = Color(0xFFB91C1C), night = Color(0xFFF87171))
// Text on the selected (green) chip: inverse of the normal foreground.
private val onAccent = ColorProvider(day = Color(0xFFF4F5F7), night = Color(0xFF14161B))

private val SIZE_SMALL = DpSize(110.dp, 40.dp)
private val SIZE_MEDIUM = DpSize(180.dp, 180.dp)
private val SIZE_LARGE = DpSize(250.dp, 330.dp)

class PortfolioWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Responsive(setOf(SIZE_SMALL, SIZE_MEDIUM, SIZE_LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Widgets render outside any activity, so apply the stored language here.
        L10n.apply(SettingsRepository.get(context).language)
        provideContent {
            val raw = currentState(WidgetKeys.SNAPSHOT)
            val snapshot = raw?.let {
                runCatching { snapshotJson.decodeFromString<WidgetSnapshot>(it) }.getOrNull()
            }
            val mode = currentState(WidgetKeys.MODE) ?: "auto"
            val range = currentState(WidgetKeys.RANGE)
            val selected = currentState(WidgetKeys.SELECTED) ?: emptySet()
            WidgetBody(snapshot, mode, range, selected)
        }
    }

    private companion object {
        // Tolerates snapshots persisted by older app versions.
        val snapshotJson = Json { ignoreUnknownKeys = true }
    }
}

/** Ghostfolio API range keys and their chip labels. */
private fun rangeChips() = listOf("1d" to "24h", "wtd" to "1W", "mtd" to "1M", "1y" to tr("1Y", "1J"))

@Composable
private fun WidgetBody(snapshot: WidgetSnapshot?, mode: String, range: String?, selected: Set<String>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bg)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<SettingsActivity>())
    ) {
        when {
            snapshot == null -> CenterMessage(
                tr("Tap to set up Ghostfolio", "Tippen, um Ghostfolio einzurichten")
            )
            !snapshot.ok -> CenterMessage(
                snapshot.error ?: tr("Could not load data", "Daten konnten nicht geladen werden")
            )
            else -> {
                // "auto" picks the richest layout that fits the current size;
                // an explicit per-widget mode (from the config screen) wins.
                val size = LocalSize.current
                val effective = if (mode == "auto") {
                    when {
                        size.height < SIZE_MEDIUM.height -> "summary"
                        size.height < SIZE_LARGE.height -> "topflop"
                        else -> "full"
                    }
                } else {
                    mode
                }
                val showTopFlop = effective == "topflop" || effective == "full"
                val showAll = effective == "all" || effective == "full"
                val custom = if (effective == "custom") {
                    snapshot.positions.filter { it.symbol in selected }
                } else {
                    emptyList()
                }

                Header(snapshot)
                if (showTopFlop || showAll || effective == "custom") {
                    Spacer(GlanceModifier.height(8.dp))
                    RangeChipRow(range ?: snapshot.range)
                    Spacer(GlanceModifier.height(6.dp))
                    LazyColumn {
                        if (showTopFlop) {
                            item { SectionLabel("Top 5") }
                            items(snapshot.top) { pos -> PositionRow(pos, snapshot.currency) }
                            item { SectionLabel("Flop 5") }
                            items(snapshot.flop) { pos -> PositionRow(pos, snapshot.currency) }
                        }
                        if (showAll) {
                            item { SectionLabel(tr("All positions", "Alle Positionen")) }
                            items(snapshot.positions) { pos -> PositionRow(pos, snapshot.currency) }
                        }
                        if (effective == "custom") {
                            if (custom.isEmpty()) {
                                item {
                                    SectionLabel(tr(
                                        "No positions selected – long-press the widget and reconfigure",
                                        "Keine Positionen ausgewählt – Widget lange drücken und neu konfigurieren",
                                    ))
                                }
                            } else {
                                items(custom) { pos -> PositionRow(pos, snapshot.currency) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(s: WidgetSnapshot) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text("Portfolio", style = TextStyle(color = muted, fontSize = 12.sp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Format.money(s.total, s.currency),
                    style = TextStyle(color = fg, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                )
                s.portfolioPerformance?.let { p ->
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        Format.signedPercent(p),
                        style = TextStyle(
                            color = if (p >= 0) green else red,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (s.stale) "⚠ " else "") + Format.timeLabel(s.updatedAtEpochMs),
                style = TextStyle(
                    color = if (s.stale) red else muted,
                    fontSize = 10.sp,
                ),
            )
            Text(
                "⟳",
                modifier = GlanceModifier
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clickable(actionRunCallback<RefreshAction>()),
                style = TextStyle(
                    color = fg,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@Composable
private fun RangeChipRow(selected: String) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        rangeChips().forEach { (key, label) ->
            val isSelected = key == selected
            Text(
                label,
                modifier = GlanceModifier
                    .background(if (isSelected) green else card)
                    .cornerRadius(8.dp)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clickable(
                        actionRunCallback<SelectRangeAction>(
                            actionParametersOf(SelectRangeAction.RANGE to key)
                        )
                    ),
                style = TextStyle(
                    color = if (isSelected) onAccent else muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(GlanceModifier.width(6.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = GlanceModifier.padding(top = 4.dp, bottom = 2.dp),
        style = TextStyle(
            color = muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}

@Composable
private fun PositionRow(pos: WidgetPosition, currency: String) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(card)
            .cornerRadius(10.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                pos.name,
                maxLines = 1,
                style = TextStyle(color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
            pos.allocation?.let {
                Text(
                    Format.percent(it * 100) + tr(" of portfolio", " des Portfolios"),
                    style = TextStyle(color = muted, fontSize = 10.sp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Format.money(pos.value, currency),
                style = TextStyle(color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
            pos.performance?.let { p ->
                Text(
                    Format.signedPercent(p),
                    style = TextStyle(color = if (p >= 0) green else red, fontSize = 11.sp),
                )
            }
        }
    }
}

@Composable
private fun CenterMessage(text: String) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = TextStyle(color = muted, fontSize = 13.sp))
    }
}
