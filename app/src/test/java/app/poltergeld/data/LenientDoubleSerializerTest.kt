package app.poltergeld.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LenientDoubleSerializerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun decodesPlainNumbers() {
        val h = json.decodeFromString<Holding>("""{"symbol":"A","value":123.45}""")
        assertEquals(123.45, h.value!!, 0.0)
    }

    @Test
    fun decodesNumbersEncodedAsStrings() {
        val h = json.decodeFromString<Holding>("""{"symbol":"A","value":"123.45"}""")
        assertEquals(123.45, h.value!!, 0.0)
    }

    @Test
    fun decodesNullAndGarbageAsNull() {
        val h = json.decodeFromString<Holding>("""{"symbol":"A","value":null,"marketPrice":"n/a"}""")
        assertNull(h.value)
        assertNull(h.marketPrice)
    }

    @Test
    fun missingFieldsStayNull() {
        val h = json.decodeFromString<Holding>("""{"symbol":"A"}""")
        assertNull(h.quantity)
        assertNull(h.performance)
        assertEquals(0.0, h.displayValue, 0.0)
    }

    @Test
    fun displayValuePrefersBaseCurrencyValue() {
        val h = json.decodeFromString<Holding>("""{"symbol":"A","value":1.0,"valueInBaseCurrency":2.0}""")
        assertEquals(2.0, h.displayValue, 0.0)
    }

    @Test
    fun performancePrefersCurrencyEffectVariant() {
        val h = json.decodeFromString<Holding>(
            """{"symbol":"A","netPerformancePercentWithCurrencyEffect":0.05,"netPerformancePercent":0.01}"""
        )
        assertEquals(0.05, h.performance!!, 0.0)
    }

    @Test
    fun displayNameFallsBackThroughProfileAndSymbol() {
        val withProfile = json.decodeFromString<Holding>(
            """{"symbol":"SYM","assetProfile":{"name":"Nice Name","symbol":"AP"}}"""
        )
        assertEquals("Nice Name", withProfile.displayName)
        val symbolOnly = json.decodeFromString<Holding>("""{"symbol":"SYM"}""")
        assertEquals("SYM", symbolOnly.displayName)
    }
}
