package app.poltergeld.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun recognisesDraftTagOfCurrentGhostfolio() {
        val a = json.decodeFromString<Activity>(
            """{"type":"BUY","tags":[{"id":"${Activity.TAG_ID_DRAFT}","name":"DRAFT"}]}"""
        )
        assertTrue(a.draft)
    }

    @Test
    fun recognisesLegacyIsDraftField() {
        val a = json.decodeFromString<Activity>("""{"type":"BUY","isDraft":true}""")
        assertTrue(a.draft)
    }

    @Test
    fun regularActivityIsNotADraft() {
        val a = json.decodeFromString<Activity>(
            """{"type":"SELL","isDraft":false,"tags":[{"id":"other","name":"Retirement"}]}"""
        )
        assertFalse(a.draft)
    }

    @Test
    fun decodesV2PerformanceResponse() {
        val p = json.decodeFromString<PerformanceResponse>(
            """{"chart":[],"dateOfFirstActivity":"2020-01-01",
               "performance":{"netPerformancePercentage":0.05,
               "netPerformancePercentageWithCurrencyEffect":0.042}}"""
        )
        assertEquals(0.042, p.performance!!.percent!!, 0.0)
    }
}
