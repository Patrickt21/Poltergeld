package app.poltergeld.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Time horizons supported by the Ghostfolio API (no rolling 7d/30d exists). */
val SUPPORTED_RANGES = listOf("1d", "wtd", "mtd", "1y")
const val DEFAULT_RANGE = "1d"

/** Selectable widget refresh intervals in minutes (15 is WorkManager's floor). */
val SUPPORTED_REFRESH_MINUTES = listOf(15, 30, 60, 360)
const val DEFAULT_REFRESH_MINUTES = 60

/** Persisted connection settings for the Ghostfolio instance. */
data class Settings(
    val baseUrl: String = "",
    val token: String = "",
    val range: String = DEFAULT_RANGE,
    val requireUnlock: Boolean = false,
    /** UI language: "en" | "de"; blank = follow the system language. */
    val language: String = "",
    val refreshMinutes: Int = DEFAULT_REFRESH_MINUTES,
)

object SettingsRepository {
    private val KEY_URL = stringPreferencesKey("base_url")
    private val KEY_TOKEN_PLAIN = stringPreferencesKey("token") // legacy, pre-1.3.0
    private val KEY_TOKEN_ENC = stringPreferencesKey("token_enc")
    private val KEY_RANGE = stringPreferencesKey("range")
    private val KEY_REQUIRE_UNLOCK = booleanPreferencesKey("require_unlock")
    private val KEY_LANGUAGE = stringPreferencesKey("language")
    private val KEY_REFRESH_MINUTES = intPreferencesKey("refresh_minutes")

    // Server-derived values that rarely change, cached across refreshes so a
    // periodic update needs two requests instead of four. Cleared whenever the
    // connection settings change.
    private val KEY_BEARER_ENC = stringPreferencesKey("bearer_enc")
    private val KEY_BASE_CURRENCY = stringPreferencesKey("base_currency_cache")

    suspend fun get(context: Context): Settings {
        migrateLegacyToken(context)
        val prefs = context.dataStore.data.first()
        return Settings(
            baseUrl = prefs[KEY_URL] ?: "",
            token = prefs[KEY_TOKEN_ENC]?.let { TokenCipher.decrypt(it) } ?: "",
            range = (prefs[KEY_RANGE] ?: DEFAULT_RANGE)
                .takeIf { it in SUPPORTED_RANGES } ?: DEFAULT_RANGE,
            requireUnlock = prefs[KEY_REQUIRE_UNLOCK] ?: false,
            language = prefs[KEY_LANGUAGE] ?: "",
            refreshMinutes = (prefs[KEY_REFRESH_MINUTES] ?: DEFAULT_REFRESH_MINUTES)
                .takeIf { it in SUPPORTED_REFRESH_MINUTES } ?: DEFAULT_REFRESH_MINUTES,
        )
    }

    suspend fun save(context: Context, baseUrl: String, token: String) {
        context.dataStore.edit {
            it[KEY_URL] = normalizeUrl(baseUrl)
            it[KEY_TOKEN_ENC] = TokenCipher.encrypt(token.trim())
            it.remove(KEY_TOKEN_PLAIN)
            // New credentials invalidate the cached JWT and base currency.
            it.remove(KEY_BEARER_ENC)
            it.remove(KEY_BASE_CURRENCY)
        }
    }

    suspend fun saveRange(context: Context, range: String) {
        if (range !in SUPPORTED_RANGES) return
        context.dataStore.edit { it[KEY_RANGE] = range }
    }

    suspend fun saveRequireUnlock(context: Context, value: Boolean) {
        context.dataStore.edit { it[KEY_REQUIRE_UNLOCK] = value }
    }

    suspend fun saveLanguage(context: Context, value: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = value }
    }

    suspend fun saveRefreshMinutes(context: Context, value: Int) {
        if (value !in SUPPORTED_REFRESH_MINUTES) return
        context.dataStore.edit { it[KEY_REFRESH_MINUTES] = value }
    }

    suspend fun getCachedBearer(context: Context): String? =
        context.dataStore.data.first()[KEY_BEARER_ENC]?.let { TokenCipher.decrypt(it) }

    suspend fun saveCachedBearer(context: Context, bearer: String) {
        context.dataStore.edit { it[KEY_BEARER_ENC] = TokenCipher.encrypt(bearer) }
    }

    suspend fun getCachedBaseCurrency(context: Context): String? =
        context.dataStore.data.first()[KEY_BASE_CURRENCY]?.takeIf { it.isNotBlank() }

    suspend fun saveCachedBaseCurrency(context: Context, currency: String) {
        context.dataStore.edit { it[KEY_BASE_CURRENCY] = currency }
    }

    /** One-time upgrade: re-store a pre-1.3.0 plain-text token encrypted. */
    private suspend fun migrateLegacyToken(context: Context) {
        val plain = context.dataStore.data.first()[KEY_TOKEN_PLAIN] ?: return
        context.dataStore.edit {
            it[KEY_TOKEN_ENC] = TokenCipher.encrypt(plain)
            it.remove(KEY_TOKEN_PLAIN)
        }
    }

    // Last successful widget snapshot per range (JSON) so a failed refresh can
    // keep showing data instead of replacing it with an error message.
    private fun snapshotKey(range: String) = stringPreferencesKey("last_snapshot_$range")

    suspend fun getLastSnapshot(context: Context, range: String): String? =
        context.dataStore.data.first()[snapshotKey(range)]

    suspend fun saveLastSnapshot(context: Context, range: String, json: String) {
        context.dataStore.edit { it[snapshotKey(range)] = json }
    }

    /** Most recent snapshot of any range – for UIs that just need the position list. */
    suspend fun getAnyLastSnapshot(context: Context): String? {
        val prefs = context.dataStore.data.first()
        val globalRange = (prefs[KEY_RANGE] ?: DEFAULT_RANGE)
        return prefs[snapshotKey(globalRange)]
            ?: SUPPORTED_RANGES.firstNotNullOfOrNull { prefs[snapshotKey(it)] }
            ?: prefs[stringPreferencesKey("last_snapshot")] // legacy, pre-1.8.0
    }

    /** Trim trailing slashes and whitespace so paths can be appended safely. */
    fun normalizeUrl(raw: String): String = raw.trim().trimEnd('/')
}
