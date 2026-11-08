package io.github.marekmaro.equinox.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

private const val INTRO_MS = 1_900

/**
 * Powitanie przy starcie (ok. 1,9 s):
 * 1. podziałka „zapala się” kreska po kresce, jak tarcza zegara,
 * 2. pomarańczowy pierścień zatacza pełne koło,
 * 3. w środku słońce wschodzi zza horyzontu (jak w logo),
 * 4. pojawia się nazwa i powitanie dopasowane do pory dnia, potem całość gaśnie.
 * Dotknięcie pomija animację. Przy wyłączonych animacjach w systemie nie ma jej wcale.
 */
@Composable
fun WelcomeIntro(onDone: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val done by rememberUpdatedState(onDone)
    val t = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val scale = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (e: Exception) {
            1f
        }
        if (scale == 0f) {
            done()
            return@LaunchedEffect
        }
        t.animateTo(0.62f, tween((INTRO_MS * 0.62f).toInt(), easing = LinearEasing))
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) // „klik” domknięcia pierścienia
        t.animateTo(1f, tween((INTRO_MS * 0.38f).toInt(), easing = LinearEasing))
        done()
    }

    val p = t.value
    fun phase(start: Float, length: Float) = ((p - start) / length).coerceIn(0f, 1f)
    val ticks = phase(0f, 0.35f)
    val ring = FastOutSlowInEasing.transform(phase(0.18f, 0.44f))
    val turn = FastOutSlowInEasing.transform(phase(0.30f, 0.40f))
    val text = phase(0.50f, 0.22f)
    val fadeOut = phase(0.84f, 0.16f)

    val bg = Pal.Bg
    val tick = Pal.Tick
    val major = Pal.Muted
    val track = Pal.Surface2
    val accent = Pal.Accent
    val face = Pal.Text

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - fadeOut }
            .background(bg)
            .pointerInput(Unit) { detectTapGestures { done() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Canvas(
                Modifier
                    .size(190.dp)
                    .graphicsLayer {
                        // delikatne „wejście” całej tarczy
                        val s = 0.92f + 0.08f * ring
                        scaleX = s
                        scaleY = s
                    },
            ) {
                val r = size.minDimension / 2f
                // 1. podziałka
                val shown = (60 * ticks).toInt()
                for (i in 0 until shown) {
                    val isMajor = i % 5 == 0
                    val a = Math.toRadians(i * 6.0 - 90.0)
                    val outer = r - 1.dp.toPx()
                    val inner = outer - if (isMajor) 12.dp.toPx() else 6.dp.toPx()
                    drawLine(
                        if (isMajor) major else tick,
                        Offset(center.x + cos(a).toFloat() * inner, center.y + sin(a).toFloat() * inner),
                        Offset(center.x + cos(a).toFloat() * outer, center.y + sin(a).toFloat() * outer),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                // 2. pierścień
                val inset = 24.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val sw = 5.dp.toPx()
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
                if (ring > 0f) {
                    drawArc(accent, -90f, 360f * ring, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
                }
                // 3. wschód słońca: słońce wynurza się zza horyzontu (jak w logo)
                val sunR = r * 0.30f
                val horizonY = center.y + sunR * 0.15f
                val sunY = horizonY + sunR * 1.1f * (1f - turn)
                clipRect(top = 0f, bottom = horizonY) {
                    drawArc(
                        accent, 180f, 180f, useCenter = true,
                        topLeft = Offset(center.x - sunR, sunY - sunR), size = Size(sunR * 2, sunR * 2),
                    )
                    drawCircle(accent, radius = sunR, center = Offset(center.x, sunY))
                }
                val half = sunR * 1.45f
                drawLine(face, Offset(center.x - half, horizonY), Offset(center.x + half, horizonY), 2.5.dp.toPx(), StrokeCap.Round)
                // odbicie w wodzie pojawia się razem ze słońcem
                drawLine(major.copy(alpha = turn), Offset(center.x - sunR * 0.8f, horizonY + 7.dp.toPx()), Offset(center.x + sunR * 0.8f, horizonY + 7.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                drawLine(tick.copy(alpha = turn), Offset(center.x - sunR * 0.45f, horizonY + 13.dp.toPx()), Offset(center.x + sunR * 0.45f, horizonY + 13.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            }
            Text(
                "EQUINOX",
                color = Pal.Text, fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = (3f + 5f * text).sp,
                modifier = Modifier.alpha(text),
            )
            Text(greeting(), color = Pal.Muted, fontSize = 16.sp, modifier = Modifier.alpha(text))
        }
    }
}
