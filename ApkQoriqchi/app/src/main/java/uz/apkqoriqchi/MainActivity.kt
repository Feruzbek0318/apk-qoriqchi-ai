package uz.apkqoriqchi

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import uz.apkqoriqchi.core.data.IncomingShareBus
import uz.apkqoriqchi.ui.components.IntroAnimation
import uz.apkqoriqchi.ui.navigation.AppNavHost
import uz.apkqoriqchi.ui.theme.ApkQoriqchiTheme

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var shareBus: IncomingShareBus

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val coldStart = savedInstanceState == null
        if (coldStart) shareBus.accept(intent)
        setContent {
            ApkQoriqchiTheme {
                // The intro plays once per cold start; config changes (rotation, theme switch) skip it.
                var showIntro by rememberSaveable { mutableStateOf(coldStart) }
                Box {
                    AppNavHost(shareBus = shareBus)
                    AnimatedVisibility(
                        visible = showIntro,
                        exit = fadeOut(tween(450)) + scaleOut(tween(450), targetScale = 1.08f),
                    ) {
                        IntroAnimation(onFinished = { showIntro = false })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        shareBus.accept(intent)
    }
}
