package io.github.marekmaro.equinox.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/*
 * Styl „Podziałka”: każdy przycisk wygląda jak kawałek dużej tarczy.
 * – RingToggle: okrągły przełącznik z drobną podziałką; włączony ma pomarańczowy łuk,
 *   tak jak postęp utworu na tarczy.
 * – ScaleSelector: tarcza „rozwinięta” w prostą – kreski i pomarańczowy znacznik,
 *   który przesuwa się do wybranej wartości jak wskazówka.
 * – RingAction: większy okrągły przycisk akcji z podpisem (np. „Zapisz”, „Odtwórz”).
 */

/** Okrągły przełącznik (♥, ⤨, ↻…): podziałka dookoła, pomarańczowy łuk, gdy włączony. */
@Composable
fun RingToggle(
    glyph: String,
    active: Boolean,
    description: String,
    size: Dp = 46.dp,
    onClick: () -> Unit,
) {
    val sweep by animateFloatAsState(if (active) 120f else 0f, tween(260), label = "ring-toggle")
    val tick = Pal.Tick
    val line = Pal.Line
    val accent = Pal.Accent
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Pal.Bg)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Switch
                selected = active
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 1.dp.toPx()
            val r = this.size.minDimension / 2f - stroke
            drawCircle(line, radius = r, style = Stroke(stroke))
            // 12 kresek jak godziny na tarczy
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0 - 90.0)
                val outer = r - 1.dp.toPx()
                val inner = outer - 3.dp.toPx()
                drawLine(
                    tick,
                    Offset(center.x + cos(a).toFloat() * inner, center.y + sin(a).toFloat() * inner),
                    Offset(center.x + cos(a).toFloat() * outer, center.y + sin(a).toFloat() * outer),
                    strokeWidth = stroke,
                )
            }
            if (sweep > 0f) {
                val w = 2.5.dp.toPx()
                drawArc(
                    accent, startAngle = -90f - sweep / 2f, sweepAngle = sweep, useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
                    style = Stroke(w, cap = StrokeCap.Round),
                )
            }
        }
        Text(glyph, color = if (active) Pal.Accent else Pal.Muted, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/**
 * Wybór jednej z kilku wartości jako skala: pozioma linia z kreskami, podpisy pod spodem
 * i pomarańczowy znacznik nad wybraną wartością. Dotknięcie podpisu przesuwa znacznik.
 */
@Composable
fun <T> ScaleSelector(
    options: List<Pair<String, T>>,
    selected: T,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    if (options.isEmpty()) return
    val index = options.indexOfFirst { it.second == selected }.coerceAtLeast(0)
    val n = options.size
    // pozycja znacznika jako ułamek szerokości (środek wybranej kolumny)
    val target = (index + 0.5f) / n
    val pos by animateFloatAsState(target, tween(320), label = "scale-marker")
    val tick = Pal.Tick
    val major = Pal.Muted
    val accent = Pal.Accent
    // długie podpisy (np. „Time of day” przy czterech opcjach) – mniejsza czcionka, żeby się zmieściły
    val longest = options.maxOf { it.first.length }
    val fontSize = if (longest * n > 34) 10.sp else 12.sp
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val y = this.size.height - 2.dp.toPx()
            val stroke = 1.dp.toPx()
            drawLine(tick, Offset(0f, y), Offset(this.size.width, y), stroke)
            // drobne kreski co 1/(8n) szerokości, duże nad każdą wartością
            val minorCount = n * 8
            for (i in 0..minorCount) {
                val x = this.size.width * i / minorCount
                drawLine(tick, Offset(x, y), Offset(x, y - 4.dp.toPx()), stroke)
            }
            for (i in 0 until n) {
                val x = this.size.width * (i + 0.5f) / n
                drawLine(major, Offset(x, y), Offset(x, y - 8.dp.toPx()), stroke * 1.5f)
            }
            // znacznik: trójkąt + kreska, jak wskazówka na skali radia
            val x = this.size.width * pos
            val half = 5.dp.toPx()
            val tri = Path().apply {
                moveTo(x - half, 0f)
                lineTo(x + half, 0f)
                lineTo(x, half * 1.6f)
                close()
            }
            drawPath(tri, accent)
            drawLine(accent, Offset(x, half * 1.4f), Offset(x, y + 1.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
        }
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (label, value) ->
                val on = i == index
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(value) }
                        .semantics {
                            role = Role.RadioButton
                            this.selected = on
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (on) Pal.Accent else Pal.Muted,
                        fontFamily = Mono,
                        fontSize = fontSize,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Okrągły przycisk akcji z podpisem pod spodem; kropkowany pierścień nawiązuje do tarczy. */
@Composable
fun RingAction(
    glyph: String,
    label: String,
    modifier: Modifier = Modifier.width(96.dp),
    size: Dp = 60.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tick = Pal.Tick
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Pal.Bg)
                .border(1.dp, Pal.Line, CircleShape)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics { contentDescription = label; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = this.size.minDimension / 2f - 6.dp.toPx()
                for (i in 0 until 36) {
                    val a = Math.toRadians(i * 10.0)
                    drawCircle(tick, radius = 0.8.dp.toPx(), center = Offset(center.x + cos(a).toFloat() * r, center.y + sin(a).toFloat() * r))
                }
            }
            Text(glyph, color = if (enabled) Pal.Text else Pal.Muted, fontSize = 20.sp)
        }
        Text(label, color = if (enabled) Pal.Text else Pal.Muted, fontFamily = Mono, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}
