package uz.apkqoriqchi.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import uz.apkqoriqchi.core.data.IncomingShare
import uz.apkqoriqchi.core.data.IncomingShareBus
import uz.apkqoriqchi.feature.apk.ApkScanScreen
import uz.apkqoriqchi.feature.apps.AppAuditScreen
import uz.apkqoriqchi.feature.history.HistoryDetailScreen
import uz.apkqoriqchi.feature.history.HistoryScreen
import uz.apkqoriqchi.feature.home.HomeScreen
import uz.apkqoriqchi.feature.settings.SettingsScreen
import uz.apkqoriqchi.feature.url.UrlCheckScreen

object Routes {
    const val HOME = "home"
    const val APK = "apk"
    const val URL = "url"
    const val APPS = "apps"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history/{id}"
    const val SETTINGS = "settings"
    fun historyDetail(id: Long) = "history/$id"
}

@Composable
fun AppNavHost(shareBus: IncomingShareBus) {
    val nav = rememberNavController()
    val pending by shareBus.pending.collectAsState()
    // Shared APK/link: jump to the matching scanner; the screen itself consumes the payload.
    LaunchedEffect(pending) {
        when (val p = pending) {
            is IncomingShare.Apk -> nav.navigate(Routes.APK) { launchSingleTop = true }
            is IncomingShare.Url -> nav.navigate(Routes.URL) { launchSingleTop = true }
            is IncomingShare.HistoryItem -> {
                nav.navigate(Routes.historyDetail(p.id))
                shareBus.consume()
            }
            null -> Unit
        }
    }
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = { slideInHorizontally(tween(320)) { it / 5 } + fadeIn(tween(320)) },
        exitTransition = { slideOutHorizontally(tween(320)) { -it / 10 } + fadeOut(tween(220)) },
        popEnterTransition = { slideInHorizontally(tween(320)) { -it / 10 } + fadeIn(tween(320)) },
        popExitTransition = { slideOutHorizontally(tween(320)) { it / 5 } + fadeOut(tween(220)) },
    ) {
        composable(Routes.HOME) { HomeScreen(onNavigate = { nav.navigate(it) }) }
        composable(Routes.APK) { ApkScanScreen(onBack = back) }
        composable(Routes.URL) { UrlCheckScreen(onBack = back) }
        composable(Routes.APPS) { AppAuditScreen(onBack = back) }
        composable(Routes.HISTORY) {
            HistoryScreen(onBack = back, onOpen = { nav.navigate(Routes.historyDetail(it)) })
        }
        composable(Routes.HISTORY_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            HistoryDetailScreen(id = it.arguments?.getLong("id") ?: 0L, onBack = back)
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = back) }
    }
}
