package app.poltergeld.data

import app.poltergeld.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

sealed interface PortfolioResult {
    data class Success(
        val holdings: List<Holding>,
        val total: Double,
        val baseCurrency: String,
        val portfolioPerformance: Double? = null,
    ) : PortfolioResult
    data class Error(val message: String) : PortfolioResult
}

/** The request never reached Ghostfolio – an auth proxy answered instead. */
private class ProxyBlockedException : Exception(
    tr(
        "Blocked by reverse proxy (e.g. Umbrel login). " +
            "Re-add PROXY_AUTH_WHITELIST \"/api/*\" for the Ghostfolio app.",
        "Von einem Reverse-Proxy blockiert (z. B. Umbrel-Login). " +
            "PROXY_AUTH_WHITELIST \"/api/*\" für die Ghostfolio-App wieder eintragen.",
    )
)

/**
 * Minimal Ghostfolio REST client built on HttpURLConnection so the app pulls in
 * no third-party networking library (and therefore no trackers). Two calls:
 * exchange the security token for a bearer token, then fetch holdings.
 */
object GhostfolioClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun fetchPortfolio(settings: Settings): PortfolioResult = withContext(Dispatchers.IO) {
        if (settings.baseUrl.isBlank() || settings.token.isBlank()) {
            return@withContext PortfolioResult.Error(tr("Not configured", "Nicht eingerichtet"))
        }
        try {
            val bearer = authenticate(settings)
                ?: return@withContext PortfolioResult.Error(
                    tr("Authentication failed – check token", "Anmeldung fehlgeschlagen – Token prüfen")
                )

            val body = get("${settings.baseUrl}/api/v1/portfolio/holdings?range=${settings.range}", bearer)
                ?: return@withContext PortfolioResult.Error(
                    tr("Could not load holdings", "Positionen konnten nicht geladen werden")
                )

            val parsed = json.decodeFromString<HoldingsResponse>(body)
            val holdings = parsed.holdings
                .filter { it.displayValue > 0.0 }
                .sortedByDescending { it.displayValue }
            val total = holdings.sumOf { it.displayValue }

            // Values are expressed in the portfolio base currency (a user setting),
            // which is not part of the holdings payload – fetch it separately.
            val baseCurrency = runCatching {
                get("${settings.baseUrl}/api/v1/user", bearer)?.let {
                    json.decodeFromString<UserResponse>(it).settings?.baseCurrency
                }
            }.getOrNull().orEmpty()

            // Overall portfolio performance for the same range (fraction, ×100 for %).
            val portfolioPerformance = runCatching {
                get("${settings.baseUrl}/api/v1/portfolio/performance?range=${settings.range}", bearer)?.let {
                    json.decodeFromString<PerformanceResponse>(it).performance?.percent
                }
            }.getOrNull()

            PortfolioResult.Success(holdings, total, baseCurrency, portfolioPerformance)
        } catch (e: Exception) {
            PortfolioResult.Error(e.message ?: tr("Unknown error", "Unbekannter Fehler"))
        }
    }

    private fun authenticate(settings: Settings): String? {
        val payload = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject {
                put("accessToken", kotlinx.serialization.json.JsonPrimitive(settings.token))
            }
        )
        val body = post("${settings.baseUrl}/api/v1/auth/anonymous", payload) ?: return null
        return json.decodeFromString<AuthResponse>(body).authToken
    }

    private fun post(urlStr: String, jsonBody: String): String? {
        val conn = open(urlStr)
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.outputStream.use { it.write(jsonBody.toByteArray()) }
        return readBody(conn)
    }

    private fun get(urlStr: String, bearer: String): String? {
        val conn = open(urlStr)
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $bearer")
        conn.setRequestProperty("Accept", "application/json")
        return readBody(conn)
    }

    private fun open(urlStr: String): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.instanceFollowRedirects = false
        return conn
    }

    private fun readBody(conn: HttpURLConnection): String? {
        return try {
            val code = conn.responseCode
            val contentType = conn.contentType.orEmpty()
            // Ghostfolio always answers with JSON. HTML or a redirect means an
            // auth proxy in front of it (e.g. Umbrel) intercepted the request.
            if (code in 300..399 || contentType.contains("text/html", ignoreCase = true)) {
                throw ProxyBlockedException()
            }
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use(BufferedReader::readText)
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }
}
