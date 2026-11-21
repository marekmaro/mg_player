package io.github.marekmaro.equinox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.marekmaro.equinox.PlayerViewModel
import io.github.marekmaro.equinox.data.Bookmark
import io.github.marekmaro.equinox.fmt
import io.github.marekmaro.equinox.tr

/** Nowa zakładka: miejsce jest już zapamiętane, notatka jest opcjonalna. */
@Composable
fun AddBookmarkDialog(draft: PlayerViewModel.BookmarkDraft, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Surface,
        title = { Text(tr("Zakładka", "Bookmark")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    listOf(draft.label, fmt(draft.positionMs)).filter { it.isNotBlank() }.joinToString(" · "),
                    color = Pal.Accent, fontFamily = Mono, fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 200) note = it },
                    label = { Text(tr("Notatka (nieobowiązkowa)", "Note (optional)")) },
                    singleLine = false,
                    maxLines = 3,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(note) }) { Text(tr("Zapisz", "Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Anuluj", "Cancel")) } },
    )
}

/** Zakładki bieżącej książki: dotknięcie przenosi w to miejsce, ✕ usuwa. */
@Composable
fun BookmarksDialog(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val list = vm.currentBookmarks
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Surface,
        title = { Text(tr("Zakładki", "Bookmarks")) },
        text = {
            if (list.isEmpty()) {
                Text(
                    tr(
                        "Jeszcze nic tu nie ma. Dotknij ⚑ w trakcie słuchania, a miejsce zostanie zapamiętane – z notatką, jeśli chcesz.",
                        "Nothing here yet. Tap ⚑ while listening to remember the spot – with a note if you like.",
                    ),
                    color = Pal.Muted, fontSize = 14.sp, lineHeight = 20.sp,
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.id }) { b ->
                        BookmarkRow(
                            b,
                            onOpen = {
                                vm.openBookmark(b)
                                onDismiss()
                            },
                            onRemove = { vm.removeBookmark(b.id) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Gotowe", "Done")) } },
    )
}

@Composable
private fun BookmarkRow(b: Bookmark, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Pal.Bg)
            .border(1.dp, Pal.Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⚑", color = Pal.Accent, fontSize = 18.sp, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(fmt(b.positionMs), color = Pal.Text, fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (b.label.isNotBlank()) Text(b.label, color = Pal.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (b.note.isNotBlank()) Text("„${b.note}”", color = Pal.Text, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove)
                .semantics { contentDescription = tr("Usuń zakładkę", "Delete bookmark") },
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", color = Pal.Muted, fontSize = 15.sp)
        }
    }
}
