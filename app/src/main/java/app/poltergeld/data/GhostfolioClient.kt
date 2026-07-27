package app.poltergeld.data

import android.content.Context
import app.poltergeld.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface PortfolioResult {
    data class Success(
        val holdings: List<Holding>,
        val total: Double,
        val baseCurrency: String,
        val portfolioPerformance: Double? = null,
    ) : PortfolioResult

    /**
     * [transient] distinguishes "try again later" failures (network hiccup,
     * server 5xx) from configuration errors that retrying cannot fix.
     */
    data class Error(val message: String, val transient: Boolean = false) : PortfolioResult
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

/** Non-2xx answer from the server itself (JSON, so not a proxy). */
private class HttpStatusException(val code: Int) : Exception(
    tr("Server error (HTTP $code)", "Serverfehler (HTTP $code)")
)

/**
 * Minimal Ghostfolio REST client built on HttpURLConnection so the app pulls in
 * no third-party networking library (and therefore no trackers). The bearer JWT
 * and the base currency barely ever change, so both are cached (the JWT
 * encrypted) and only refreshed when the server answers 401/403.
 */
object GhostfolioClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * [useCache] is disabled by the "test connection" flows so candidate
     * credentials are always verified with a fresh authentication and never
     * persist anything.
     */
    suspend fun fetchPortfolio(
        context: Context,
        settings: Settings,
        useCache: Boolean = true,
    ): PortfolioResult = withContext(Dispatchers.IO) {
        if (settings.baseUrl.isBlank() || settings.token.isBlank()) {
            return@withContext PortfolioResult.Error(tr("Not configured", "Nicht eingerichtet"))
        }
        if (!UrlPolicy.isCleartextAllowed(settings.baseUrl)) {
            return@withContext PortfolioResult.Error(
                tr(
                    "Insecure http:// is only allowed for local network addresses – " +
                        "use https:// for this server.",
                    "Unsicheres http:// ist nur für Adressen im Heimnetz erlaubt – " +
                        "nutze https:// für diesen Server.",
                )
            )
        }
        try {
            var freshAuth = false
            var bearer = if (useCache) SettingsRepository.getCachedBearer(context) else null
            if (bearer == null) {
                bearer = authenticate(settings)
                    ?: return@withContext PortfolioResult.Error(
                        tr("Authentication failed – check token", "Anmeldung fehlgeschlagen – Token prüfen")
                    )
                freshAuth = true
                if (useCache) SettingsRepository.saveCachedBearer(context, bearer)
            }

            var holdingsBody = try {
                get("${settings.baseUrl}/api/v1/portfolio/holdings?range=${settings.range}", bearer)
            } catch (e: HttpStatusException) {
                if (e.code !in setOf(401, 403) || freshAuth) throw e
                null
            }
            if (holdingsBody == null) {
                // Cached JWT expired or was revoked – authenticate once more.
                bearer = authenticate(settings)
                    ?: return@withContext PortfolioResult.Error(
                        tr("Authentication failed – check token", "Anmeldung fehlgeschlagen – Token prüfen")
                    )
                if (useCache) SettingsRepository.saveCachedBearer(context, bearer)
                holdingsBody = get("${settings.baseUrl}/api/v1/portfolio/holdings?range=${settings.range}", bearer)
            }

            val parsed = json.decodeFromString<HoldingsResponse>(holdingsBody)
            val holdings = parsed.holdings
                .filter { it.displayValue > 0.0 }
                .sortedByDescending { it.displayValue }
            val total = holdings.sumOf { it.displayValue }

            // Values are expressed in the portfolio base currency (a user setting),
            // which is not part of the holdings payload – cached because it
            // practically never changes.
            val baseCurrency = (if (useCache) SettingsRepository.getCachedBaseCurrency(context) else null)
                ?: runCatching {
                    json.decodeFromString<UserResponse>(
                        get("${settings.baseUrl}/api/v1/user", bearer)
                    ).settings?.baseCurrency
                }.getOrNull().orEmpty().also {
                    if (useCache && it.isNotBlank()) SettingsRepository.saveCachedBaseCurrency(context, it)
                }

            // Overall portfolio performance for the same range (fraction, ×100 for %).
            val portfolioPerformance = runCatching {
                json.decodeFromString<PerformanceResponse>(
                    get("${settings.baseUrl}/api/v1/portfolio/performance?range=${settings.range}", bearer)
                ).performance?.percent
            }.getOrNull()

            PortfolioResult.Success(holdings, total, baseCurrency, portfolioPerformance)
        } catch (e: HttpStatusException) {
            PortfolioResult.Error(e.message!!, transient = e.code in 500..599)
        } catch (e: ProxyBlockedException) {
            PortfolioResult.Error(e.message!!)
        } catch (e: IOException) {
            // Timeouts, DNS failures, unreachable hosts: worth retrying later.
            PortfolioResult.Error(
                e.message ?: tr("Network error", "Netzwerkfehler"),
                transient = true,
            )
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
        val body = try {
            post("${settings.baseUrl}/api/v1/auth/anonymous", payload)
        } catch (e: HttpStatusException) {
            // 4xx here means the token is wrong, which the caller reports.
            if (e.code in 400..499) return null else throw e
        }
        return json.decodeFromString<AuthResponse>(body).authToken
    }

    private fun post(urlStr: String, jsonBody: String): String =
        exchange(urlStr) { conn ->
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.outputStream.use { it.write(jsonBody.toByteArray()) }
        }

    private fun get(urlStr: String, bearer: String): String =
        exchange(urlStr) { conn ->
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $bearer")
            conn.setRequestProperty("Accept", "application/json")
        }

    /**
     * Runs one request, following at most [MAX_REDIRECTS] redirects – but only
     * same-host http→https upgrades (e.g. the user typed http:// and the server
     * redirects to https). Any other redirect, or an HTML answer, means an auth
     * proxy in front of Ghostfolio (e.g. Umbrel) intercepted the request.
     */
    private fun exchange(urlStr: String, configure: (HttpURLConnection) -> Unit): String {
        var url = URL(urlStr)
        repeat(MAX_REDIRECTS + 1) {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = false
            }
            try {
                configure(conn)
                val code = conn.responseCode
                val contentType = conn.contentType.orEmpty()
                if (code in 300..399) {
                    val target = conn.getHeaderField("Location")
                        ?.let { runCatching { URL(url, it) }.getOrNull() }
                        ?: throw ProxyBlockedException()
                    val isHttpsUpgrade = url.protocol == "http" &&
                        target.protocol == "https" && target.host == url.host
                    if (!isHttpsUpgrade) throw ProxyBlockedException()
                    url = target
                    return@repeat
                }
                if (contentType.contains("text/html", ignoreCase = true)) {
                    throw ProxyBlockedException()
                }
                if (code !in 200..299) throw HttpStatusException(code)
                return conn.inputStream.bufferedReader().use(BufferedReader::readText)
            } finally {
                conn.disconnect()
            }
        }
        throw ProxyBlockedException()
    }

    private const val MAX_REDIRECTS = 2
}
