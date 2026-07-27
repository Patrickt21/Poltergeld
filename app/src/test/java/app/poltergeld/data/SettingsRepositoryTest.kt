package app.poltergeld.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun normalizeUrlTrimsWhitespaceAndTrailingSlashes() {
        assertEquals(
            "https://ghostfolio.example.com",
            SettingsRepository.normalizeUrl("  https://ghostfolio.example.com/  "),
        )
        assertEquals(
            "http://192.168.1.10:3333",
            SettingsRepository.normalizeUrl("http://192.168.1.10:3333///"),
        )
        assertEquals("", SettingsRepository.normalizeUrl("   "))
    }
}
