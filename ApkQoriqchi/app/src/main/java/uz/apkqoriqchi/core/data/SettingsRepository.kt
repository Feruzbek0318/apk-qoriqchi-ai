package uz.apkqoriqchi.core.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

enum class ApiKey(val prefKey: String) {
    VIRUSTOTAL("vt_api_key"),
    SAFE_BROWSING("gsb_api_key"),
    GEMINI("gemini_api_key"),
}

data class UserPrefs(
    /** Upload unknown APKs to VirusTotal. Off by default: uploads are shared with the VT community. */
    val allowFileUpload: Boolean = false,
    val aiExplanations: Boolean = true,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("prefs")

/** API keys live in EncryptedSharedPreferences (Keystore-backed); non-secret prefs live in DataStore. */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val secure: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "secure_keys", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val keyVersion = MutableStateFlow(0)
    /** Emits whenever any API key changes, so UIs can refresh "configured" badges. */
    val keysChanged: StateFlow<Int> = keyVersion.asStateFlow()

    /** Only keys the user entered in Settings; nothing is built into the app. */
    fun apiKey(key: ApiKey): String? =
        secure.getString(key.prefKey, null)?.takeIf { it.isNotBlank() }

    fun setApiKey(key: ApiKey, value: String?) {
        secure.edit().apply {
            if (value.isNullOrBlank()) remove(key.prefKey) else putString(key.prefKey, value.trim())
        }.apply()
        keyVersion.value++
    }

    val prefs: Flow<UserPrefs> = context.dataStore.data.map {
        UserPrefs(
            allowFileUpload = it[ALLOW_UPLOAD] ?: false,
            aiExplanations = it[AI_ENABLED] ?: true,
        )
    }

    suspend fun setAllowFileUpload(v: Boolean) { context.dataStore.edit { it[ALLOW_UPLOAD] = v } }
    suspend fun setAiExplanations(v: Boolean) { context.dataStore.edit { it[AI_ENABLED] = v } }

    private companion object {
        val ALLOW_UPLOAD = booleanPreferencesKey("allow_file_upload")
        val AI_ENABLED = booleanPreferencesKey("ai_explanations")
    }
}
