package io.github.marekmaro.equinox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.marekmaro.equinox.Block
import io.github.marekmaro.equinox.Kind
import io.github.marekmaro.equinox.PlayerViewModel
import io.github.marekmaro.equinox.RingMode
import io.github.marekmaro.equinox.fmt
import kotlinx.coroutines.delay
import java.time.LocalTime

// ================================================================== Klocki na ekranie odtwarzania

/** Pod pierścieniem: klocki w kolejności wybranej przez użytkownika. */
@Composable
fun PlayerBlocks(vm: PlayerViewModel, onSessions: () -> Unit, onEditLayout: () -> Unit) {
    var addTrackId by remember { mutableStateOf<Long?>(null) }
    addTrackId?.let { id -> AddToPlaylistDialog(vm, id, onDismiss = { addTrackId = null }) }
    vm.layout.forEach { block ->
        when (block) {
            Block.TITLE -> TitleBlock(vm)
            Block.MODES -> ModesBlock(vm)
            Block.CONTROLS -> ControlsBlock(vm)
            Block.BOOK -> BookBlock(vm)
            Block.FACTS -> FactsBlock(vm)
            Block.QUEUE -> QueueBlock(vm)
            Block.TODAY -> TodayBlock(vm)
            Block.CLOCK -> ClockBlock()
            Block.SESSIONS -> SessionsBlock(vm, onSessions)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (vm.nowPlaying.kind == Kind.MUSIC) {
            Text(
                "＋ Do playlisty",
                color = Pal.Muted, fontFamily = Mono, fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { addTrackId = vm.currentTrackId() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        Text(
            "▦ Ułóż ekran",
            color = Pal.Muted, fontFamily = Mono, fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onEditLayout)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun TitleBlock(vm: PlayerViewModel) {
    val np = vm.nowPlaying
    val isRadio = np.kind == Kind.RADIO
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(np.title, color = Pal.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
        Text(np.subtitle, color = Pal.Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (isRadio) "● NA ŻYWO" else "${fmt(np.positionMs)} / ${fmt(np.durationMs)}",
            color = if (isRadio) Pal.Accent else Pal.Text, fontFamily = Mono, fontSize = 13.sp,
        )
        Text(vm.effectiveRingMode.hint, color = Pal.Muted, fontSize = 11.sp)
        np.error?.let { Text(it, color = Pal.Accent, fontSize = 13.sp, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun ModesBlock(vm: PlayerViewModel) {
    val isRadio = vm.nowPlaying.kind == Kind.RADIO
    val mode = vm.effectiveRingMode
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RingMode.entries.forEach { m ->
            if (!(isRadio && m != RingMode.VOLUME)) {
                Chip(m.label, modifier = Modifier.weight(1f), selected = m == mode) { vm.ringMode = m }
            }
        }
    }
}

@Composable
private fun ControlsBlock(vm: PlayerViewModel) {
    val np = vm.nowPlaying
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        CircleButton("Poprzedni", { vm.previous() }, size = 52.dp) { GlyphIcon(Glyph.Prev, Pal.Text, 24.dp) }
        CircleButton(if (np.isPlaying) "Pauza" else "Odtwórz", { vm.togglePlay() }, size = 72.dp, filled = true) {
            GlyphIcon(if (np.isPlaying) Glyph.Pause else Glyph.Play, Pal.Bg, 28.dp)
        }
        CircleButton("Następny", { vm.next() }, size = 52.dp) { GlyphIcon(Glyph.Next, Pal.Text, 24.dp) }
    }
}

@Composable
private fun BookBlock(vm: PlayerViewModel) {
    val np = vm.nowPlaying
    if (np.kind != Kind.BOOK) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("−30 s", modifier = Modifier.weight(1f)) { vm.seekBy(-30_000) }
            Chip("+30 s", modifier = Modifier.weight(1f)) { vm.seekBy(30_000) }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(0.8f, 1f, 1.25f, 1.5f, 2f).forEach { v ->
                Chip(
                    text = "${if (v % 1f == 0f) v.toInt().toString() else v.toString()}×",
                    selected = kotlin.math.abs(np.speed - v) < 0.01f,
                ) { vm.setSpeed(v) }
            }
        }
    }
}

@Composable
private fun FactsBlock(vm: PlayerViewModel) {
    if (vm.nowPlaying.kind == Kind.RADIO) return
    FactsCard(facts = vm.facts, enabled = vm.factsEnabled, onToggle = { vm.setFactsOn(!vm.factsEnabled) })
}

@Composable
private fun QueueBlock(vm: PlayerViewModel) {
    val next = vm.upNext
    if (next.isEmpty()) return
    Card {
        Text("NASTĘPNE", color = Pal.Muted, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
        next.forEachIndexed { i, title ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = Pal.Accent, fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.width(22.dp))
                Text(title, color = Pal.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TodayBlock(vm: PlayerViewModel) {
    val s = vm.sessions
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("DZIŚ", color = Pal.Muted, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
                Text("${s.todayMs / 60_000} z ${s.goalMinutes} min książki", color = Pal.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                if (s.streak > 0) "seria ${s.streak} d" else "zacznij serię",
                color = Pal.Accent, fontFamily = Mono, fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ClockBlock() {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1_000)
        }
    }
    Text(
        "%02d:%02d".format(now.hour, now.minute),
        color = Pal.Text, fontFamily = Mono, fontSize = 56.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { contentDescription = "Godzina ${now.hour}:${now.minute}" },
    )
}

@Composable
private fun SessionsBlock(vm: PlayerViewModel, onSessions: () -> Unit) {
    val s = vm.sessions
    val text = when {
        s.sleepRemainingMs != null -> "Sen ${fmt(s.sleepRemainingMs)}"
        s.sleepAfterItem -> "Sen: po rozdziale"
        s.stopwatchRemainingMs != null -> "Stoper ${fmt(s.stopwatchRemainingMs)}"
        else -> "Sesje: sen · stoper"
    }
    val active = s.sleepRemainingMs != null || s.sleepAfterItem || s.stopwatchRemainingMs != null
    Chip(text, modifier = Modifier.fillMaxWidth(), selected = active, onClick = onSessions)
}

// ================================================================== Edytor „Ułóż ekran”

/** Klocki: włącz/wyłącz i ustaw kolejność strzałkami. Zmiany widać od razu. */
@Composable
fun LayoutEditorScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val layout = vm.layout
    val off = Block.entries.filter { it !in layout }

    Column(
        Modifier
            .fillMaxSize()
            .background(Pal.Bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TopBar("Ułóż ekran", onBack)
        Text(
            "Ekran odtwarzania składa się z klocków. Włącz te, których potrzebujesz, i ustaw je w swojej kolejności. Pierścień jest zawsze na górze.",
            color = Pal.Muted, fontSize = 13.sp, lineHeight = 19.sp,
        )

        Text("NA EKRANIE", color = Pal.Muted, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, modifier = Modifier.padding(top = 6.dp))
        layout.forEachIndexed { i, b ->
            BlockRow(
                block = b, on = true,
                canUp = i > 0, canDown = i < layout.lastIndex,
                onToggle = { vm.toggleBlock(b) },
                onUp = { vm.moveBlock(b, -1) },
                onDown = { vm.moveBlock(b, +1) },
            )
        }

        if (off.isNotEmpty()) {
            Text("SCHOWANE", color = Pal.Muted, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, modifier = Modifier.padding(top = 10.dp))
            off.forEach { b ->
                BlockRow(block = b, on = false, canUp = false, canDown = false, onToggle = { vm.toggleBlock(b) }, onUp = {}, onDown = {})
            }
        }

        Spacer(Modifier.height(6.dp))
        Chip("Przywróć domyślny układ", modifier = Modifier.fillMaxWidth()) { vm.resetLayout() }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BlockRow(
    block: Block,
    on: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onToggle: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (on) Pal.Surface else Pal.Bg)
            .border(1.dp, Pal.Line, RoundedCornerShape(18.dp))
            .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // przełącznik: kwadratowy „klocek” – pełny = włączony
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !block.required, onClick = onToggle)
                .semantics { contentDescription = if (on) "Wyłącz ${block.label}" else "Włącz ${block.label}" },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .alpha(if (block.required) 0.5f else 1f)
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (on) Pal.Accent else Pal.Bg)
                    .border(2.dp, if (on) Pal.Accent else Pal.Tick, RoundedCornerShape(6.dp)),
            )
        }
        Column(Modifier.weight(1f).padding(start = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(block.label + if (block.required) "  ·  zawsze" else "", color = if (on) Pal.Text else Pal.Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(block.description, color = Pal.Muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (on) {
            ArrowButton("▲", "Wyżej: ${block.label}", canUp, onUp)
            ArrowButton("▼", "Niżej: ${block.label}", canDown, onDown)
        }
    }
}

@Composable
private fun ArrowButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = if (enabled) Pal.Text else Pal.Line, fontSize = 14.sp)
    }
}
