package uz.apkqoriqchi

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import uz.apkqoriqchi.ui.theme.ThemePrefs

@HiltAndroidApp
class ApkQoriqchiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemePrefs.applySaved(this)
    }
}
