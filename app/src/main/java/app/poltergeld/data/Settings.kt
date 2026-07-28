package app.poltergeld.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Time horizons supported by the Ghostfolio API (no rolling 7d/30d exists). */
val SUPPORTED_RANGES = listOf("1d", "wtd", "mtd", "1y")
const val DEFAULT_RANGE = "1d"

/** Selectable widget refresh intervals in minutes (15 is WorkManager's floor). */
val SUPPORTED_REFRESH_MINUTES = listOf(15, 30, 60, 360)
const val DEFAULT_REFRESH_MINUTES = 60

/** Selectable durations (seconds) a widget privacy reveal stays visible before auto re-masking. */
val SUPPORTED_PRIVACY_REVEAL_SECONDS = listOf(15, 30, 60, 120, 300)
const val DEFAULT_PRIVACY_REVEAL_SECONDS = 60

/** Persisted connection settings for the Ghostfolio instance. */
data class Settings(
    val baseUrl: String = "",
    val token: String = "",
    val range: String = DEFAULT_RANGE,
    val requireUnlock: Boolean = false,
    /** UI language: "en" | "de"; blank = follow the system language. */
    val language: String = "",
    val refreshMinutes: Int = DEFAULT_REFRESH_MINUTES,
    /** Mask monetary amounts in the homescreen widget until unlocked. */
    val privacyModeWidget: Boolean = false,
    /** Mask monetary amounts in the app (overview + detail) until unlocked. */
    val privacyModeApp: Boolean = false,
    /** How long a widget privacy reveal stays visible before auto re-masking. */
    val privacyRevealSeconds: Int = DEFAULT_PRIVACY_REVEAL_SECONDS,
)

/** Widget privacy state emitted by [SettingsRepository.widgetPrivacyFlow]. */
data class WidgetPrivacy(
    /** Whether the widget masks amounts at all ([Settings.privacyModeWidget]). */
    val enabled: Boolean,
    /** Epoch ms until which a reveal is in effect; 0 / past = masked. */
    val unlockedUntil: Long,
)

object SettingsRepository {
    private val KEY_URL = stringPreferencesKey("base_url")
    private val KEY_TOKEN_PLAIN = stringPreferencesKey("token") // legacy, pre-1.3.0
    private val KEY_TOKEN_ENC = stringPreferencesKey("token_enc")
    private val KEY_RANGE = stringPreferencesKey("range")
    private val KEY_REQUIRE_UNLOCK = booleanPreferencesKey("require_unlock")
    private val KEY_LANGUAGE = stringPreferencesKey("language")
    private val KEY_REFRESH_MINUTES = intPreferencesKey("refresh_minutes")
    private val KEY_PRIVACY_WIDGET = booleanPreferencesKey("privacy_mode_widget")
    private val KEY_PRIVACY_APP = booleanPreferencesKey("privacy_mode_app")
    private val KEY_PRIVACY_REVEAL_SECONDS = intPreferencesKey("privacy_reveal_seconds")

    // Widget-only: how long a privacy reveal (via UnlockPrivacyActivity) stays
    // in effect. The app doesn't need this – it just re-masks on ON_STOP.
    private val KEY_PRIVACY_UNLOCKED_UNTIL = longPreferencesKey("privacy_unlocked_until")

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
            privacyModeWidget = prefs[KEY_PRIVACY_WIDGET] ?: false,
            privacyModeApp = prefs[KEY_PRIVACY_APP] ?: false,
            privacyRevealSeconds = (prefs[KEY_PRIVACY_REVEAL_SECONDS] ?: DEFAULT_PRIVACY_REVEAL_SECONDS)
                .takeIf { it in SUPPORTED_PRIVACY_REVEAL_SECONDS } ?: DEFAULT_PRIVACY_REVEAL_SECONDS,
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

    suspend fun savePrivacyModeWidget(context: Context, value: Boolean) {
        context.dataStore.edit { it[KEY_PRIVACY_WIDGET] = value }
    }

    suspend fun savePrivacyModeApp(context: Context, value: Boolean) {
        context.dataStore.edit { it[KEY_PRIVACY_APP] = value }
    }

    suspend fun savePrivacyRevealSeconds(context: Context, value: Int) {
        if (value !in SUPPORTED_PRIVACY_REVEAL_SECONDS) return
        context.dataStore.edit { it[KEY_PRIVACY_REVEAL_SECONDS] = value }
    }

    /** Epoch ms until which the widget shows real amounts; 0 / past = masked. */
    suspend fun getPrivacyUnlockedUntil(context: Context): Long =
        context.dataStore.data.first()[KEY_PRIVACY_UNLOCKED_UNTIL] ?: 0L

    /**
     * The widget privacy state as a flow, for observation from inside the
     * widget's composition – see PortfolioWidget for why a one-shot read
     * is not enough there.
     */
    fun widgetPrivacyFlow(context: Context): Flow<WidgetPrivacy> =
        context.dataStore.data.map {
            WidgetPrivacy(
                enabled = it[KEY_PRIVACY_WIDGET] ?: false,
                unlockedUntil = it[KEY_PRIVACY_UNLOCKED_UNTIL] ?: 0L,
            )
        }

    suspend fun savePrivacyUnlockedUntil(context: Context, epochMs: Long) {
        context.dataStore.edit { it[KEY_PRIVACY_UNLOCKED_UNTIL] = epochMs }
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
