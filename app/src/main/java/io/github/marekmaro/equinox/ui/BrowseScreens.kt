package io.github.marekmaro.equinox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.marekmaro.equinox.GroupType
import io.github.marekmaro.equinox.PlayerViewModel
import io.github.marekmaro.equinox.fmt
import io.github.marekmaro.equinox.tr

// ================================================================== Wykonawcy / Albumy / Foldery

@Composable
fun GroupsScreen(vm: PlayerViewModel, onBack: () -> Unit, onOpenGroup: () -> Unit) {
    val type = vm.groupListType
    val groups = remember(vm.tracks, type) { vm.groups(type) }
    val title = when (type) {
        GroupType.ARTIST -> tr("Wykonawcy", "Artists")
        GroupType.ALBUM -> tr("Albumy", "Albums")
        else -> tr("Foldery", "Folders")
    }
    KnobListScreen(
        onLongOk = { vm.togglePlay() },
        title = title,
        rows = groups.map { ListRow(it.title, "${it.count}") },
        onBack = onBack,
        onOpen = { i ->
            vm.openGroup = groups[i]
            onOpenGroup()
        },
        emptyText = tr("Brak muzyki w bibliotece.", "No music in your library."),
    )
}

/** Utwory jednej grupy: wykonawcy, albumu, folderu albo playlisty automatycznej. */
@Composable
fun GroupTracksScreen(vm: PlayerViewModel, onBack: () -> Unit, onPlaying: () -> Unit) {
    val g = vm.openGroup
    if (g == null) {
        KnobListScreen(title = "", rows = emptyList(), onBack = onBack, onOpen = {}, emptyText = tr("Nic tu nie ma.", "Nothing here."))
        return
    }
    val tracks = remember(vm.tracks, vm.favoriteIds, g) { vm.groupTracks(g.type, g.key) }
    var addTrackId by remember { mutableStateOf<Long?>(null) }
    addTrackId?.let { id -> AddToPlaylistDialog(vm, id, onDismiss = { addTrackId = null }) }

    KnobListScreen(
        onLongOk = { vm.togglePlay() },
        title = g.title,
        rows = tracks.map { t -> ListRow(t.title, fmt(t.durationMs), trailing = "+", onTrailing = { addTrackId = t.id }) },
        onBack = onBack,
        onOpen = { i -> if (vm.playGroup(g, start = i)) onPlaying() },
        emptyText = when (g.type) {
            GroupType.FAVORITES -> tr("Nie masz jeszcze ulubionych.\nDotknij ♡ przy utworze, który gra.", "No favourites yet.\nTap ♡ on the song that's playing.")
            GroupType.TOP -> tr("Posłuchaj trochę muzyki –\ntu pojawią się Twoje ulubione kawałki.", "Listen to some music –\nyour most played songs will show up here.")
            else -> tr("Nic tu nie ma.", "Nothing here.")
        },
    ) {
        if (tracks.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(tr("▶ Graj", "▶ Play"), modifier = Modifier.weight(1f), selected = true) { if (vm.playGroup(g)) onPlaying() }
                Chip(tr("⤨ Losowo", "⤨ Shuffle"), modifier = Modifier.weight(1f)) { if (vm.playGroup(g, shuffle = true)) onPlaying() }
            }
        }
    }
}

// ================================================================== Szukaj

@Composable
fun SearchScreen(vm: PlayerViewModel, onBack: () -> Unit, onPlaying: () -> Unit, onOpenGroup: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, vm.tracks) { vm.search(query) }
    // kursor od razu w polu wyszukiwania – można pisać bez dodatkowego dotknięcia
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (e: Exception) {} }
    var addTrackId by remember { mutableStateOf<Long?>(null) }
    addTrackId?.let { id -> AddToPlaylistDialog(vm, id, onDismiss = { addTrackId = null }) }

    KnobListScreen(
        title = tr("Szukaj", "Search"),
        rows = results.map { r ->
            val t = r.track
            if (t != null) ListRow(r.title, r.hint, trailing = "+", onTrailing = { addTrackId = t.id })
            else ListRow(r.title, r.hint)
        },
        onBack = onBack,
        onOpen = { i ->
            val r = results[i]
            val t = r.track
            val g = r.group
            when {
                t != null -> {
                    // gra cały wynik wyszukiwania, zaczynając od wybranego utworu
                    val list = results.mapNotNull { it.track }
                    vm.playTracks(list, list.indexOf(t).coerceAtLeast(0))
                    onPlaying()
                }
                g != null -> {
                    vm.openGroup = g
                    onOpenGroup()
                }
            }
        },
        emptyText = if (query.trim().length < 2) tr("Wpisz tytuł, wykonawcę albo album.", "Type a title, artist or album.")
        else tr("Nic nie znalazłem.", "Nothing found."),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(tr("Tytuł, wykonawca, album…", "Title, artist, album…")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            shape = RoundedCornerShape(14.dp),
        )
    }
}
