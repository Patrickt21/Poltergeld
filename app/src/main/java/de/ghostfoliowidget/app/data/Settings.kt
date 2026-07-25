package de.ghostfoliowidget.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Time horizons supported by the Ghostfolio API (no rolling 7d/30d exists). */
val SUPPORTED_RANGES = listOf("1d", "wtd", "mtd", "1y")
const val DEFAULT_RANGE = "1d"

/** Persisted connection settings for the Ghostfolio instance. */
data class Settings(
    val baseUrl: String = "",
    val token: String = "",
    val range: String = DEFAULT_RANGE,
    val requireUnlock: Boolean = false,
)

object SettingsRepository {
    private val KEY_URL = stringPreferencesKey("base_url")
    private val KEY_TOKEN_PLAIN = stringPreferencesKey("token") // legacy, pre-1.3.0
    private val KEY_TOKEN_ENC = stringPreferencesKey("token_enc")
    private val KEY_RANGE = stringPreferencesKey("range")
    private val KEY_REQUIRE_UNLOCK = booleanPreferencesKey("require_unlock")

    suspend fun get(context: Context): Settings {
        migrateLegacyToken(context)
        val prefs = context.dataStore.data.first()
        return Settings(
            baseUrl = prefs[KEY_URL] ?: "",
            token = prefs[KEY_TOKEN_ENC]?.let { TokenCipher.decrypt(it) } ?: "",
            range = (prefs[KEY_RANGE] ?: DEFAULT_RANGE)
                .takeIf { it in SUPPORTED_RANGES } ?: DEFAULT_RANGE,
            requireUnlock = prefs[KEY_REQUIRE_UNLOCK] ?: false,
        )
    }

    suspend fun save(context: Context, baseUrl: String, token: String) {
        context.dataStore.edit {
            it[KEY_URL] = normalizeUrl(baseUrl)
            it[KEY_TOKEN_ENC] = TokenCipher.encrypt(token.trim())
            it.remove(KEY_TOKEN_PLAIN)
        }
    }

    suspend fun saveRange(context: Context, range: String) {
        if (range !in SUPPORTED_RANGES) return
        context.dataStore.edit { it[KEY_RANGE] = range }
    }

    suspend fun saveRequireUnlock(context: Context, value: Boolean) {
        context.dataStore.edit { it[KEY_REQUIRE_UNLOCK] = value }
    }

    /** One-time upgrade: re-store a pre-1.3.0 plain-text token encrypted. */
    private suspend fun migrateLegacyToken(context: Context) {
        val plain = context.dataStore.data.first()[KEY_TOKEN_PLAIN] ?: return
        context.dataStore.edit {
            it[KEY_TOKEN_ENC] = TokenCipher.encrypt(plain)
            it.remove(KEY_TOKEN_PLAIN)
        }
    }

    // Last successful widget snapshot (JSON) so a failed refresh can keep
    // showing data instead of replacing it with an error message.
    private val KEY_LAST_SNAPSHOT = stringPreferencesKey("last_snapshot")

    suspend fun getLastSnapshot(context: Context): String? =
        context.dataStore.data.first()[KEY_LAST_SNAPSHOT]

    suspend fun saveLastSnapshot(context: Context, json: String) {
        context.dataStore.edit { it[KEY_LAST_SNAPSHOT] = json }
    }

    /** Trim trailing slashes and whitespace so paths can be appended safely. */
    fun normalizeUrl(raw: String): String = raw.trim().trimEnd('/')
}
