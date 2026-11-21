package io.github.marekmaro.equinox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.marekmaro.equinox.PlayerViewModel
import io.github.marekmaro.equinox.tr

/**
 * Kolejka: wszystko, co zagra, po kolei. Kręcisz pokrętłem albo dotykasz utworu,
 * a w okienku przesuwasz go wyżej / niżej, grasz od razu albo usuwasz.
 */
@Composable
fun QueueScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    DisposableEffect(Unit) {
        vm.queueVisible = true
        onDispose { vm.queueVisible = false }
    }
    var picked by remember { mutableStateOf<Int?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val queue = vm.queue
    val current = queue.indexOfFirst { it.current }
    val upcoming = if (current >= 0) queue.size - current - 1 else 0

    KnobListScreen(
        title = tr("Kolejka", "Queue"),
        rows = queue.mapIndexed { i, e ->
            // numer pozycji po prawej; bieżący utwór oznaczony ▶
            ListRow(title = (if (e.current) "▶ " else "") + e.title, hint = "${i + 1}")
        },
        onBack = onBack,
        onOpen = { picked = it },
        emptyText = tr("Kolejka jest pusta. Włącz muzykę, a pojawi się tutaj.", "The queue is empty. Play some music and it will show up here."),
        topTrailing = if (upcoming > 0) ({
            Chip(tr("Wyczyść dalsze", "Clear upcoming")) { confirmClear = true }
        }) else null,
    )

    picked?.let { i ->
        val e = queue.getOrNull(i)
        if (e == null) picked = null else QueueItemDialog(
            title = e.title,
            subtitle = e.subtitle,
            position = i,
            size = queue.size,
            current = e.current,
            onUp = { vm.queueMove(i, -1)?.let { picked = it } },
            onDown = { vm.queueMove(i, +1)?.let { picked = it } },
            onPlay = {
                vm.queuePlay(i)
                picked = null
            },
            onRemove = {
                vm.queueRemove(i)
                picked = null
            },
            onDismiss = { picked = null },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Pal.Surface,
            title = { Text(tr("Wyczyścić kolejkę?", "Clear the queue?")) },
            text = {
                Text(
                    tr("Usunę $upcoming utw. po bieżącym. Ten, który gra, dogra do końca.", "I'll remove $upcoming songs after the current one. The one playing will finish."),
                    color = Pal.Muted, fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.queueClearUpcoming()
                    confirmClear = false
                }) { Text(tr("Wyczyść", "Clear")) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(tr("Anuluj", "Cancel")) } },
        )
    }
}

@Composable
private fun QueueItemDialog(
    title: String,
    subtitle: String,
    position: Int,
    size: Int,
    current: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Surface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                Text(
                    listOf(subtitle, tr("pozycja ${position + 1} z $size", "position ${position + 1} of $size")).filter { it.isNotBlank() }.joinToString(" · "),
                    color = Pal.Muted, fontSize = 13.sp, fontFamily = Mono,
                )
            }
        },
        text = {
            Row(Modifier.fillMaxWidth()) {
                val cell = Modifier.weight(1f)
                RingAction("▲", tr("Wyżej", "Up"), cell, size = 52.dp, enabled = position > 0, onClick = onUp)
                RingAction("▼", tr("Niżej", "Down"), cell, size = 52.dp, enabled = position < size - 1, onClick = onDown)
                RingAction("▶", tr("Graj teraz", "Play now"), cell, size = 52.dp, enabled = !current, onClick = onPlay)
                RingAction("✕", tr("Usuń", "Remove"), cell, size = 52.dp, onClick = onRemove)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Gotowe", "Done")) } },
    )
}
