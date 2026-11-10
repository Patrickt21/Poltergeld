package app.poltergeld.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopFlopTest {

    private fun pos(symbol: String, performance: Double?) =
        WidgetPosition(name = symbol, value = 100.0, allocation = null, performance = performance, symbol = symbol)

    @Test
    fun ranksBestFirstAndWorstFirst() {
        val positions = (1..12).map { pos("P$it", it / 100.0) } // P12 best, P1 worst
        val (top, flop) = topFlop(positions)
        assertEquals(listOf("P12", "P11", "P10", "P9", "P8"), top.map { it.symbol })
        assertEquals(listOf("P1", "P2", "P3", "P4", "P5"), flop.map { it.symbol })
    }

    @Test
    fun topAndFlopNeverOverlap() {
        val positions = (1..7).map { pos("P$it", it / 100.0) }
        val (top, flop) = topFlop(positions)
        assertEquals(5, top.size)
        assertEquals(2, flop.size)
        assertTrue(top.map { it.symbol }.intersect(flop.map { it.symbol }.toSet()).isEmpty())
    }

    @Test
    fun fewPositionsGoOnlyToTop() {
        val positions = (1..3).map { pos("P$it", it / 100.0) }
        val (top, flop) = topFlop(positions)
        assertEquals(3, top.size)
        assertTrue(flop.isEmpty())
    }

    @Test
    fun positionsWithoutPerformanceAreIgnored() {
        val positions = listOf(pos("CASH", null), pos("A", 0.05), pos("B", -0.02))
        val (top, flop) = topFlop(positions)
        assertEquals(listOf("A", "B"), top.map { it.symbol })
        assertTrue(flop.isEmpty())
        assertTrue(top.none { it.symbol == "CASH" })
    }

    @Test
    fun emptyListYieldsEmptyResults() {
        val (top, flop) = topFlop(emptyList())
        assertTrue(top.isEmpty())
        assertTrue(flop.isEmpty())
    }
}

class WidgetRangeKeysTest {

    @org.junit.Test
    fun narrowWidgetKeepsFourChips() {
        org.junit.Assert.assertEquals(listOf("1d", "wtd", "mtd", "1y"), widgetRangeKeys("1d", wide = false))
    }

    @org.junit.Test
    fun rangePickedElsewhereTakesTheLastSlot() {
        org.junit.Assert.assertEquals(listOf("1d", "wtd", "mtd", "max"), widgetRangeKeys("max", wide = false))
        org.junit.Assert.assertEquals(
            listOf("1d", "wtd", "mtd", "ytd", "1y", "5y"),
            widgetRangeKeys("5y", wide = true),
        )
    }

    @org.junit.Test
    fun everyChipIsASupportedRange() {
        (widgetRangeKeys("1d", wide = true) + widgetRangeKeys("1d", wide = false)).forEach {
            org.junit.Assert.assertTrue(it in app.poltergeld.data.SUPPORTED_RANGES)
        }
    }
}
