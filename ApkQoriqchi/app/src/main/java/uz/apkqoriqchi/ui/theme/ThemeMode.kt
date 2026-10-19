package uz.apkqoriqchi.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode(val nightMode: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES),
}

/**
 * Light/dark choice. Stored in plain SharedPreferences (not DataStore) so it can be applied synchronously in
 * Application.onCreate, before the first frame. AppCompat then updates the activity's uiMode, which
 * isSystemInDarkTheme() and the system bars follow.
 */
object ThemePrefs {
    private const val FILE = "ui_prefs"
    private const val KEY = "theme_mode"

    fun get(context: Context): ThemeMode =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM

    fun set(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    fun applySaved(context: Context) = AppCompatDelegate.setDefaultNightMode(get(context).nightMode)
}
