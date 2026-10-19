package uz.apkqoriqchi.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.apkqoriqchi.R

private val Navy = Color(0xFF000B24)
private val NavyLight = Color(0xFF06204A)
private val Glow = Color(0xFF2F8BFF)

/**
 * Brand intro shown on cold start right after the system splash (same navy background and shield,
 * so the hand-off is seamless): the shield springs in with a pulsing glow ring and a scanning sweep,
 * then the name and tagline rise in. Calls [onFinished] after ~1.6 s.
 */
@Composable
fun IntroAnimation(onFinished: () -> Unit) {
    val logoScale = remember { Animatable(0.72f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textShift = remember { Animatable(24f) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        listOf(
            async { logoAlpha.animateTo(1f, tween(380)) },
            async { logoScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) },
            async {
                delay(320)
                launch { textAlpha.animateTo(1f, tween(420)) }
                textShift.animateTo(0f, tween(480, easing = FastOutSlowInEasing))
            },
            async { progress.animateTo(1f, tween(1450, easing = LinearEasing)) },
        ).awaitAll()
        onFinished()
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        initialValue = 0.85f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "ring",
    )
    val sweep by pulse.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "sweep",
    )

    Box(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(NavyLight, Navy), radius = 1400f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(220.dp).alpha(logoAlpha.value)) {
                    val r = size.minDimension / 2
                    // expanding, fading glow ring
                    drawCircle(Glow.copy(alpha = (1.25f - ring) * 0.9f), radius = r * 0.62f * ring, style = Stroke(3.dp.toPx()))
                    // rotating "scan" arc
                    drawArc(
                        brush = Brush.sweepGradient(listOf(Color.Transparent, Glow.copy(alpha = 0.85f)), center = center),
                        startAngle = sweep, sweepAngle = 110f, useCenter = false,
                        topLeft = Offset(center.x - r * 0.78f, center.y - r * 0.78f),
                        size = androidx.compose.ui.geometry.Size(r * 1.56f, r * 1.56f),
                        style = Stroke(4.dp.toPx()),
                    )
                }
                Image(
                    painterResource(R.drawable.logo_shield), contentDescription = null,
                    modifier = Modifier.size(150.dp).scale(logoScale.value).alpha(logoAlpha.value),
                )
            }
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.graphicsLayer { translationY = textShift.value * density; alpha = textAlpha.value },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "APK Qo‘riqchi AI", color = Color.White,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.intro_tagline), color = Color(0xFF9DB7E3),
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(22.dp))
                Box(Modifier.width(140.dp).height(3.dp).background(Color.White.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxWidth(progress.value).height(3.dp).background(Glow))
                }
            }
        }
    }
}
