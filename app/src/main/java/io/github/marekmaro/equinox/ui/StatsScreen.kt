package io.github.marekmaro.equinox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.marekmaro.equinox.PlayerViewModel
import io.github.marekmaro.equinox.tr
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun StatsScreen(vm: PlayerViewModel, onBack: () -> Unit, onPlaying: () -> Unit) {
    val st = remember(vm.tracks) { vm.stats() }
    Column(
        Modifier
            .fillMaxSize()
            .background(Pal.Bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TopBar(tr("Statystyki", "Stats"), onBack)

        // --- 1. Zegar słuchania
        Card {
            Text(tr("Twój zegar słuchania", "Your listening clock"), color = Pal.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            val peak = st.peakHour
            Text(
                if (peak < 0) tr("Posłuchaj trochę, a tarcza pokaże, kiedy słuchasz najchętniej.", "Listen a bit and the dial will show when you listen most.")
                else tr("Najczęściej słuchasz około %02d:00".format(peak), "You listen most around %02d:00".format(peak)) + " – " + persona(peak),
                color = Pal.Muted, fontSize = 13.sp, lineHeight = 19.sp,
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { DayClock(st.hours, peak) }
        }

        // --- 2. Wykonawca tygodnia
        Card {
            Text(tr("Wykonawca tygodnia", "Artist of the week"), color = Pal.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            val a = st.topArtist
            if (a == null) {
                Text(tr("W tym tygodniu jeszcze nikt nie prowadzi.", "Nobody's leading this week yet."), color = Pal.Muted, fontSize = 13.sp)
            } else {
                Text(a, color = Pal.Accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    tr("${st.topArtistMs / 60_000} min · ${(st.topArtistShare * 100).toInt()}% Twojego słuchania w tym tygodniu",
                        "${st.topArtistMs / 60_000} min · ${(st.topArtistShare * 100).toInt()}% of your listening this week"),
                    color = Pal.Muted, fontSize = 13.sp,
                )
                ShareBar(st.topArtistShare)
            }
        }

        // --- 3. Odkrycia
        Card {
            Text(tr("Odkrycia", "Discoveries"), color = Pal.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            val pct = if (st.total == 0) 0 else st.heard * 100 / st.total
            Text(
                tr("Zagrało już $pct% Twojej muzyki (${st.heard} z ${st.total}).", "You've played $pct% of your music (${st.heard} of ${st.total})."),
                color = Pal.Muted, fontSize = 13.sp, lineHeight = 19.sp,
            )
            ShareBar(if (st.total == 0) 0f else st.heard.toFloat() / st.total)
            if (st.heard < st.total) {
                Chip(tr("⤨ Zagraj coś nieodkrytego", "⤨ Play something undiscovered"), modifier = Modifier.fillMaxWidth(), selected = true) {
                    if (vm.playUndiscovered()) onPlaying()
                }
            }
        }

        Text(
            tr("Statystyki zostają na Twoim telefonie. Ukryjesz je w ustawieniach (☼).", "Stats stay on your phone. Hide them in settings (☼)."),
            color = Pal.Muted, fontSize = 12.sp,
        )
        Text(
            tr("Ukryj statystyki", "Hide stats"),
            color = Pal.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    vm.changeShowStats(false)
                    onBack()
                }
                .padding(vertical = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

/** Krótki „typ słuchacza” wg godziny, o której słuchasz najwięcej. */
private fun persona(hour: Int): String = when (hour) {
    in 5..9 -> tr("ranny ptaszek", "early bird")
    in 10..16 -> tr("dzienny słuchacz", "daytime listener")
    in 17..21 -> tr("wieczorny marzyciel", "evening dreamer")
    else -> tr("nocny marek", "night owl")
}

@Composable
private fun ShareBar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Pal.Surface2)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Pal.Accent))
    }
}

/**
 * Doba jako tarcza: 24 promienie (północ u góry, południe na dole).
 * Długość promienia = ile słuchasz o tej godzinie; godzina szczytu na pomarańczowo.
 * Dolna połowa tarczy (dzień, 6–18) jest lekko rozjaśniona – jak w logo Equinox.
 */
@Composable
private fun DayClock(hours: LongArray, peak: Int) {
    val max = (hours.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val day = Pal.Surface2
    val bar = Pal.Muted
    val accent = Pal.Accent
    val line = Pal.Line
    val label = Pal.Muted
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Canvas(
        Modifier
            .size(220.dp)
            .padding(8.dp)
            .semantics { contentDescription = tr("Zegar słuchania w ciągu doby", "Listening clock over 24 hours") },
    ) {
        val r = size.minDimension / 2f
        val inner = r * 0.32f
        // dzień (6–18) – dolna połowa tarczy rozjaśniona
        drawArc(day, -90f + 90f, 180f, useCenter = true, topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2), alpha = 0.6f)
        drawCircle(line, radius = r, style = Stroke(1.dp.toPx()))
        drawCircle(line, radius = inner, style = Stroke(1.dp.toPx()))
        for (h in 0 until 24) {
            val a = Math.toRadians(h * 15.0 - 90.0)
            val len = (r - inner - 6.dp.toPx()) * (hours[h].toFloat() / max)
            val c = cos(a).toFloat()
            val s = sin(a).toFloat()
            if (len > 0.5f) {
                drawLine(
                    if (h == peak) accent else bar,
                    Offset(center.x + c * inner, center.y + s * inner),
                    Offset(center.x + c * (inner + len), center.y + s * (inner + len)),
                    strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round,
                )
            }
        }
        // znaczniki 0, 6, 12, 18
        listOf(0, 6, 12, 18).forEach { h ->
            val a = Math.toRadians(h * 15.0 - 90.0)
            drawCircle(label, radius = 2.dp.toPx(), center = Offset(center.x + cos(a).toFloat() * (inner - 8.dp.toPx()), center.y + sin(a).toFloat() * (inner - 8.dp.toPx())))
        }
    }
    Text(tr("☾ noc u góry · dzień na dole ☼", "☾ night on top · day below ☼"), color = Pal.Muted, fontSize = 11.sp)
    }
}
