package io.github.marekmaro.equinox.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.marekmaro.equinox.PlayerViewModel
import io.github.marekmaro.equinox.SoundPreset
import io.github.marekmaro.equinox.tr

/**
 * Brzmienie: zamiast suwaków – karty z nazwami jak nastroje.
 * Każda karta ma mały wykres, który pokazuje, co dzieje się z dźwiękiem.
 */
@Composable
fun SoundSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss)
                .systemBarsPadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .graphicsLayer {
                        // wynurza się od dołu, lekko „sprężynując”
                        translationY = (1f - rise.value) * 600f
                        alpha = rise.value.coerceIn(0f, 1f)
                    }
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Pal.Surface)
                    .clickable(enabled = false) {}
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(tr("Brzmienie", "Sound"), color = Pal.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (!vm.soundAvailable) {
                    Text(
                        tr("Ten telefon nie pozwala zmieniać brzmienia w aplikacjach.", "This phone doesn't allow apps to change the sound."),
                        color = Pal.Accent, fontSize = 13.sp,
                    )
                }
                SoundPreset.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { p ->
                            PresetCard(p, selected = p == vm.soundPreset, modifier = Modifier.weight(1f)) { vm.setSound(p) }
                        }
                        if (pair.size == 1) Box(Modifier.weight(1f))
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { vm.changeVoiceForBooks(!vm.voiceForBooks) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Audiobooki: zawsze „Czysty głos”", "Audiobooks: always “Clear voice”"), color = Pal.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(tr("Lektor brzmi wyraźniej, muzyka zostaje po Twojemu.", "Narrators sound clearer, music stays your way."), color = Pal.Muted, fontSize = 12.sp)
                    }
                    Switchy(vm.voiceForBooks)
                }
                Chip(tr("Gotowe", "Done"), modifier = Modifier.fillMaxWidth(), selected = true, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun PresetCard(p: SoundPreset, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Pal.AccentDim else Pal.Bg)
            .border(if (selected) 2.dp else 1.dp, if (selected) Pal.Accent else Pal.Line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${p.label}: ${p.note}" }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Curve(p, if (selected) Pal.Accent else Pal.Muted)
        Text(p.label, color = if (selected) Pal.Accent else Pal.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
        Text(p.note, color = Pal.Muted, fontSize = 11.sp, maxLines = 1)
    }
}

/** Mini wykres: od basu (lewo) do sopranu (prawo), środkowa linia = bez zmian. */
@Composable
private fun Curve(p: SoundPreset, color: Color) {
    val line = Pal.Line
    Canvas(Modifier.fillMaxWidth().height(34.dp)) {
        val mid = size.height / 2
        drawLine(line, Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
        val path = Path()
        val steps = 40
        for (i in 0..steps) {
            val hz = (30 * Math.pow(16_000.0 / 30, i / steps.toDouble())).toInt()
            val g = SoundPreset.gainAt(p, hz)
            val x = size.width * i / steps
            val y = mid - g / 7f * (size.height / 2)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Prosty przełącznik w stylu aplikacji. */
@Composable
fun Switchy(on: Boolean) {
    Box(
        Modifier
            .padding(start = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) Pal.Accent else Pal.Line)
            .padding(3.dp),
    ) {
        Row {
            if (on) Box(Modifier.padding(start = 20.dp))
            Box(Modifier.clip(RoundedCornerShape(11.dp)).background(Pal.Surface).padding(11.dp))
            if (!on) Box(Modifier.padding(start = 20.dp))
        }
    }
}
