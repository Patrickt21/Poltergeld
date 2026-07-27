package app.poltergeld

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class FormatTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun epoch(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun moneyUsesGermanGrouping() {
        assertEquals("1.234,56 EUR", Format.money(1234.56, "EUR", Locale.GERMANY))
    }

    @Test
    fun moneyUsesEnglishGrouping() {
        assertEquals("1,234.56 USD", Format.money(1234.56, "USD", Locale.US))
    }

    @Test
    fun moneyWithoutCurrencyOmitsSuffix() {
        assertEquals("0.00", Format.money(0.0, "", Locale.US))
    }

    @Test
    fun percentFollowsLocale() {
        assertEquals("3,2%", Format.percent(3.24, Locale.GERMANY))
        assertEquals("3.2%", Format.percent(3.24, Locale.US))
    }

    @Test
    fun signedPercentAddsPlusAndScales() {
        assertEquals("+3.2%", Format.signedPercent(0.032, Locale.US))
        assertEquals("-1,0%", Format.signedPercent(-0.0104, Locale.GERMANY))
    }

    @Test
    fun timeLabelSameDayIsTimeOnly() {
        val at = epoch(2026, 7, 27, 14, 30)
        val now = epoch(2026, 7, 27, 18, 0)
        assertEquals("14:30", Format.timeLabel(at, now, zone, "gestern"))
    }

    @Test
    fun timeLabelYesterdayIsMarked() {
        val at = epoch(2026, 7, 26, 23, 59)
        val now = epoch(2026, 7, 27, 0, 5)
        assertEquals("gestern 23:59", Format.timeLabel(at, now, zone, "gestern"))
    }

    @Test
    fun timeLabelOlderShowsDate() {
        val at = epoch(2026, 7, 20, 9, 15)
        val now = epoch(2026, 7, 27, 12, 0)
        assertEquals("20.7. 09:15", Format.timeLabel(at, now, zone, "gestern"))
    }

    @Test
    fun timeLabelZeroIsEmpty() {
        assertEquals("", Format.timeLabel(0, epoch(2026, 7, 27, 12, 0), zone, "gestern"))
    }
}
