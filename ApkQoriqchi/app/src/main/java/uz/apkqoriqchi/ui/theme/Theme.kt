package uz.apkqoriqchi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Verdict colours shared by every feature (see RiskLevel.color). */
object RiskColors {
    val Clean = Color(0xFF22C55E)
    val Low = Color(0xFF84CC16)
    val Medium = Color(0xFFF59E0B)
    val High = Color(0xFFF97316)
    val Critical = Color(0xFFEF4444)
    val Unknown = Color(0xFF94A3B8)
}

/** Brand blues from the design storyboard (deep navy background, electric-blue accents). */
object Brand {
    val Blue = Color(0xFF1E88FF)
    val BlueLight = Color(0xFF4FA3FF)
    val Navy = Color(0xFF070E1C)
    val NavySurface = Color(0xFF0E1A2F)
    val NavyCard = Color(0xFF13223B)
    val NavyOutline = Color(0xFF213453)
}

private val Dark = darkColorScheme(
    primary = Brand.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0F3566),
    onPrimaryContainer = Color(0xFFD6E7FF),
    secondary = Brand.BlueLight,
    onSecondary = Color(0xFF00213F),
    secondaryContainer = Color(0xFF1A2C4A),
    onSecondaryContainer = Color(0xFFD3E2FA),
    tertiary = Color(0xFF38BDF8),
    background = Brand.Navy,
    onBackground = Color(0xFFE6EDF7),
    surface = Brand.NavySurface,
    onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Brand.NavyCard,
    onSurfaceVariant = Color(0xFF93A4BF),
    surfaceContainerLowest = Brand.Navy,
    surfaceContainerLow = Color(0xFF0C1729),
    surfaceContainer = Brand.NavySurface,
    surfaceContainerHigh = Brand.NavyCard,
    surfaceContainerHighest = Color(0xFF1A2B47),
    outline = Color(0xFF2C4266),
    outlineVariant = Brand.NavyOutline,
    error = RiskColors.Critical,
    onError = Color.White,
    errorContainer = Color(0xFF3A1218),
    onErrorContainer = Color(0xFFFFD9DC),
)

private val Light = lightColorScheme(
    primary = Color(0xFF1570E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEAFF),
    onPrimaryContainer = Color(0xFF001C3B),
    secondary = Color(0xFF3B82F6),
    secondaryContainer = Color(0xFFE3ECFA),
    onSecondaryContainer = Color(0xFF0B2545),
    tertiary = Color(0xFF0284C7),
    background = Color(0xFFF3F6FB),
    onBackground = Color(0xFF0B1628),
    surface = Color.White,
    onSurface = Color(0xFF0B1628),
    surfaceVariant = Color(0xFFE9EFF8),
    onSurfaceVariant = Color(0xFF52627A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F9FD),
    surfaceContainer = Color(0xFFF0F4FA),
    surfaceContainerHigh = Color(0xFFE9EFF8),
    surfaceContainerHighest = Color(0xFFE1E8F3),
    outline = Color(0xFFB7C4D8),
    outlineVariant = Color(0xFFDCE4F0),
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFDE2E4),
    onErrorContainer = Color(0xFF5F0B12),
)

private val AppTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** Monospace style for hashes, JSON and other technical values. */
val MonoStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)

@Composable
fun ApkQoriqchiTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
