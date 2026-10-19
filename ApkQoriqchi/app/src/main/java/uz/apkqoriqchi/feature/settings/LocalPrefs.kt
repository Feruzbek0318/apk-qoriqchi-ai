package uz.apkqoriqchi.feature.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.localStore: DataStore<Preferences> by preferencesDataStore("app_local_prefs")

/** UI-level preferences owned by the settings/home features (onboarding flag, notification toggle). */
@Singleton
class LocalPrefs @Inject constructor(@ApplicationContext private val context: Context) {
    val onboardingDone: Flow<Boolean> = context.localStore.data.map { it[ONBOARDING_DONE] ?: false }
    val notifyOnScan: Flow<Boolean> = context.localStore.data.map { it[NOTIFY_ON_SCAN] ?: false }

    suspend fun setOnboardingDone() { context.localStore.edit { it[ONBOARDING_DONE] = true } }
    suspend fun setNotifyOnScan(v: Boolean) { context.localStore.edit { it[NOTIFY_ON_SCAN] = v } }

    private companion object {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val NOTIFY_ON_SCAN = booleanPreferencesKey("notify_on_scan")
    }
}
