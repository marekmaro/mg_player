package io.github.marekmaro.equinox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.marekmaro.equinox.data.Prefs
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// ------------------------------------------------------------------ Układ: składaki i szerokie ekrany

/** Ułożenie telefonu składanego. */
enum class Posture { FLAT, TABLETOP, BOOK }

val LocalPosture = staticCompositionLocalOf { Posture.FLAT }

/** Szeroki ekran: rozłożony składak, tablet, telefon w poziomie albo składak „jak książka”. */
@Composable
fun isWide(): Boolean {
    val c = LocalConfiguration.current
    return c.screenWidthDp >= 600 || c.screenWidthDp > c.screenHeightDp || LocalPosture.current == Posture.BOOK
}

// ------------------------------------------------------------------ Ikony

enum class Glyph { Play, Pause, Prev, Next, Back }

@Composable
fun GlyphIcon(glyph: Glyph, color: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        when (glyph) {
            Glyph.Play -> drawPath(
                Path().apply {
                    moveTo(w * 0.30f, h * 0.18f); lineTo(w * 0.82f, h * 0.5f); lineTo(w * 0.30f, h * 0.82f); close()
                }, color
            )
            Glyph.Pause -> {
                drawRect(color, Offset(w * 0.26f, h * 0.2f), Size(w * 0.16f, h * 0.6f))
                drawRect(color, Offset(w * 0.58f, h * 0.2f), Size(w * 0.16f, h * 0.6f))
            }
            Glyph.Prev -> {
                drawRect(color, Offset(w * 0.2f, h * 0.22f), Size(w * 0.09f, h * 0.56f))
                drawPath(Path().apply { moveTo(w * 0.8f, h * 0.22f); lineTo(w * 0.32f, h * 0.5f); lineTo(w * 0.8f, h * 0.78f); close() }, color)
            }
            Glyph.Next -> {
                drawRect(color, Offset(w * 0.71f, h * 0.22f), Size(w * 0.09f, h * 0.56f))
                drawPath(Path().apply { moveTo(w * 0.2f, h * 0.22f); lineTo(w * 0.68f, h * 0.5f); lineTo(w * 0.2f, h * 0.78f); close() }, color)
            }
            Glyph.Back -> {
                val sw = 2.dp.toPx()
                drawLine(color, Offset(w * 0.62f, h * 0.22f), Offset(w * 0.34f, h * 0.5f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * 0.34f, h * 0.5f), Offset(w * 0.62f, h * 0.78f), sw, StrokeCap.Round)
            }
        }
    }
}

@Composable
fun CircleButton(
    description: String,
    onClick: () -> Unit,
    size: Dp = 44.dp,
    filled: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) Pal.Accent else Color.Transparent)
            .border(1.dp, if (filled) Pal.Accent else Pal.Line, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Pal.AccentDim else Color.Transparent)
            .border(1.dp, if (selected) Pal.Accent else Pal.Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Pal.Accent else Pal.Text,
            fontFamily = Mono,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, small: Boolean = false, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            CircleButton("Wróć", onBack) { GlyphIcon(Glyph.Back, Pal.Text, 20.dp) }
        }
        if (small) {
            Text(title, color = Pal.Muted, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp)
        } else {
            Text(title, color = Pal.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/** Znak Equinox: koło w połowie jasne, w połowie ciemne (dzień i noc w równowadze). */
@Composable
fun EquinoxGlyph(size: Dp = 18.dp, color: Color = Pal.Text) {
    Canvas(Modifier.size(size)) {
        val sw = 1.5.dp.toPx()
        val r = this.size.minDimension / 2f - sw
        drawArc(color, 90f, 180f, useCenter = true, topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2))
        drawCircle(color, radius = r, style = Stroke(sw))
    }
}

/** Przełącznik motywu: Auto (wg pory dnia) → Dzień → Noc. */
@Composable
fun DayNightToggle() {
    val ctx = LocalContext.current
    Row(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, Pal.Line, RoundedCornerShape(22.dp))
            .clickable {
                val next = ThemeState.next()
                ThemeState.mode = next
                Prefs(ctx).themeMode = next.name
            }
            .semantics { contentDescription = "Motyw: ${ThemeState.mode.label}. Dotknij, aby zmienić." }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EquinoxGlyph()
        Text(ThemeState.mode.label, color = Pal.Muted, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Pal.Surface)
            .border(1.dp, Pal.Line, RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

// ------------------------------------------------------------------ Pokrętło

/**
 * Pokrętło: obrót kciukiem po okręgu. Co [stepDegrees] stopni wywołuje
 * [onStep] (+1 zgodnie z ruchem wskazówek zegara, -1 przeciwnie) i daje
 * delikatną wibrację.
 */
@Composable
fun RotaryKnob(
    modifier: Modifier = Modifier,
    stepDegrees: Float = 30f,
    onStep: (Int) -> Unit,
    content: @Composable BoxScope.(rotation: Float) -> Unit,
) {
    var rotation by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val step by rememberUpdatedState(onStep)

    Box(
        modifier.pointerInput(stepDegrees) {
            var last = 0f
            var acc = 0f
            detectDragGestures(
                onDragStart = { o ->
                    last = angleOf(o, size)
                    acc = 0f
                },
                onDrag = { change, _ ->
                    change.consume()
                    val a = angleOf(change.position, size)
                    var d = a - last
                    if (d > 180f) d -= 360f
                    if (d < -180f) d += 360f
                    last = a
                    acc += d
                    rotation += d
                    while (acc >= stepDegrees) {
                        acc -= stepDegrees
                        step(1)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    while (acc <= -stepDegrees) {
                        acc += stepDegrees
                        step(-1)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        content(rotation)
    }
}

private fun angleOf(p: Offset, s: IntSize): Float =
    Math.toDegrees(atan2((p.y - s.height / 2f).toDouble(), (p.x - s.width / 2f).toDouble())).toFloat()

/** Podziałka jak na skali starego radia. */
fun DrawScope.drawTicks(count: Int, every: Int, minor: Color = Pal.Tick, major: Color = Pal.Muted) {
    val r = size.minDimension / 2f
    for (i in 0 until count) {
        val ang = Math.toRadians(i * 360.0 / count - 90.0)
        val isMajor = i % every == 0
        val len = if (isMajor) 12.dp.toPx() else 6.dp.toPx()
        val outer = r - 1.dp.toPx()
        val inner = outer - len
        val cx = cos(ang).toFloat()
        val sy = sin(ang).toFloat()
        drawLine(
            if (isMajor) major else minor,
            Offset(center.x + cx * inner, center.y + sy * inner),
            Offset(center.x + cx * outer, center.y + sy * outer),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun MenuKnob(onStep: (Int) -> Unit, onOk: () -> Unit, onLongOk: () -> Unit = {}, size: Dp = 210.dp) {
    val ok by rememberUpdatedState(onOk)
    val longOk by rememberUpdatedState(onLongOk)
    RotaryKnob(Modifier.size(size), onStep = onStep) { rot ->
        Canvas(Modifier.fillMaxSize()) {
            drawTicks(48, 6)
            val inner = this.size.minDimension / 2f - 22.dp.toPx()
            drawCircle(Pal.Surface, radius = inner)
            drawCircle(Pal.Line, radius = inner, style = Stroke(1.dp.toPx()))
            val r = inner - 14.dp.toPx()
            val a = Math.toRadians(rot - 90.0)
            drawCircle(
                Pal.Accent,
                radius = 5.dp.toPx(),
                center = Offset(center.x + cos(a).toFloat() * r, center.y + sin(a).toFloat() * r),
            )
        }
        Box(
            Modifier
                .size(size * 0.36f)
                .clip(CircleShape)
                .background(Pal.Bg)
                .border(1.dp, Pal.Tick, CircleShape)
                // dotknięcie = wybierz, przytrzymanie = pauza / odtwarzanie
                .pointerInput(Unit) { detectTapGestures(onTap = { ok() }, onLongPress = { longOk() }) }
                .semantics { contentDescription = "Wybierz. Przytrzymaj, aby wstrzymać lub wznowić" },
            contentAlignment = Alignment.Center,
        ) {
            Text("OK", color = Pal.Text, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        }
    }
}

// ------------------------------------------------------------------ Lista + pokrętło

data class ListRow(
    val title: String,
    val hint: String = "",
    val trailing: String? = null,
    val onTrailing: (() -> Unit)? = null,
)

/**
 * Wspólny ekran listy: każda lista w aplikacji działa tak samo –
 * kręcisz pokrętłem, środek wybiera, dotknięcie wiersza też działa.
 */
@Composable
fun KnobListScreen(
    title: String,
    rows: List<ListRow>,
    onBack: (() -> Unit)?,
    onOpen: (Int) -> Unit,
    emptyText: String = "",
    smallTitle: Boolean = false,
    topTrailing: (@Composable () -> Unit)? = null,
    emptyAction: Pair<String, () -> Unit>? = null,
    onLongOk: () -> Unit = {},
    header: @Composable ColumnScope.() -> Unit = {},
) {
    var sel by remember(rows.size) { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun move(d: Int) {
        if (rows.isEmpty()) return
        sel = (sel + d).coerceIn(0, rows.lastIndex)
        scope.launch {
            val first = listState.firstVisibleItemIndex
            val visible = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            if (sel < first || sel >= first + visible - 1) {
                listState.animateScrollToItem((sel - 2).coerceAtLeast(0))
            }
        }
    }

    // Pokrętło dopasowuje się do ekranu: na małych telefonach jest mniejsze,
    // a w poziomie stoi obok listy.
    val cfg = LocalConfiguration.current
    val landscape = isWide()
    val knobSize = if (landscape) minOf(cfg.screenHeightDp * 0.62f, cfg.screenWidthDp * 0.4f).coerceIn(140f, 260f).dp
    else (cfg.screenHeightDp * 0.27f).coerceIn(150f, 210f).dp

    val list: @Composable ColumnScope.() -> Unit = {
        TopBar(title, onBack, small = smallTitle, trailing = topTrailing)
        header()
        if (rows.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(emptyText, color = Pal.Muted, textAlign = TextAlign.Center, fontSize = 15.sp, lineHeight = 22.sp)
                    emptyAction?.let { (label, action) -> Chip(label, selected = true, onClick = action) }
                }
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                itemsIndexed(rows) { i, row ->
                    ListRowItem(row, selected = i == sel) {
                        sel = i
                        onOpen(i)
                    }
                }
            }
        }
    }
    val knob: @Composable () -> Unit = {
        MenuKnob(
            onStep = { move(it) },
            onOk = { if (rows.isNotEmpty()) onOpen(sel) },
            onLongOk = onLongOk,
            size = knobSize,
        )
    }

    val base = Modifier
        .fillMaxSize()
        .background(Pal.Bg)
        .systemBarsPadding()
        .padding(horizontal = 20.dp, vertical = 12.dp)

    if (landscape) {
        Row(base, horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp), content = list)
            knob()
        }
    } else {
        Column(base, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            list()
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { knob() }
        }
    }
}

@Composable
private fun ListRowItem(row: ListRow, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Pal.Surface2 else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.title,
            color = if (selected) Pal.Accent else Pal.Text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (row.hint.isNotEmpty()) {
            Text(row.hint, color = Pal.Muted, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
        }
        val action = row.onTrailing
        if (row.trailing != null && action != null) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = action),
                contentAlignment = Alignment.Center,
            ) {
                Text(row.trailing, color = Pal.Accent, fontSize = 20.sp)
            }
        } else {
            Text("›", color = Pal.Muted, fontSize = 22.sp, modifier = Modifier.padding(start = 10.dp, end = 8.dp))
        }
    }
}
