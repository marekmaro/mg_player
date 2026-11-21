package io.github.marekmaro.equinox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
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

/** Podpis utworu na liście: znaczniki, „nowe” i czas trwania. */
fun trackHint(vm: PlayerViewModel, t: io.github.marekmaro.equinox.data.Track): String {
    val marks = io.github.marekmaro.equinox.marksGlyphs(vm.marks[t.id])
    val fresh = if (vm.isNew(t)) tr("nowe ", "new ") else ""
    return "$marks $fresh${fmt(t.durationMs)}".trim()
}

// ================================================================== Wykonawcy / Albumy / Foldery

@Composable
fun GroupsScreen(vm: PlayerViewModel, onBack: () -> Unit, onOpenGroup: () -> Unit) {
    val type = vm.groupListType
    val groups = remember(vm.tracks, type) { vm.groups(type) }
    val title = when (type) {
        GroupType.ARTIST -> tr("Wykonawcy", "Artists")
        GroupType.ALBUM -> tr("Albumy", "Albums")
        GroupType.GENRE -> tr("Gatunki", "Genres")
        GroupType.DECADE -> tr("Lata", "Years")
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
        emptyText = when (type) {
            GroupType.GENRE -> tr("Pliki nie mają zapisanych gatunków.", "Your files have no genre tags.")
            GroupType.DECADE -> tr("Pliki nie mają zapisanego roku.", "Your files have no year tags.")
            else -> tr("Brak muzyki w bibliotece.", "No music in your library.")
        },
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
    val tracks = remember(vm.tracks, vm.marks, g) { vm.groupTracks(g.type, g.key) }
    var addTrackId by remember { mutableStateOf<Long?>(null) }
    addTrackId?.let { id -> AddToPlaylistDialog(vm, id, onDismiss = { addTrackId = null }) }

    KnobListScreen(
        onLongOk = { vm.togglePlay() },
        title = g.title,
        rows = tracks.map { t -> ListRow(t.title, trackHint(vm, t), trailing = "+", onTrailing = { addTrackId = t.id }) },
        onBack = onBack,
        onOpen = { i -> if (vm.playGroup(g, start = i)) onPlaying() },
        emptyText = when (g.type) {
            GroupType.MARK -> tr("Nic tu jeszcze nie ma.\nOznacz utwór przyciskiem ♡ albo ✦\nna ekranie odtwarzania.", "Nothing here yet.\nMark a song with ♡ or ✦\non the player screen.")
            GroupType.NEW -> tr("Brak nowych utworów z ostatnich 30 dni.", "No new songs from the last 30 days.")
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
fun SearchScreen(
    vm: PlayerViewModel,
    onBack: () -> Unit,
    onPlaying: () -> Unit,
    onOpenGroup: () -> Unit,
    onOpenPlaylist: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, vm.tracks, vm.marks) { vm.search(query) }
    // kursor od razu w polu wyszukiwania – można pisać bez dodatkowego dotknięcia
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { focus.requestFocus() } catch (e: Exception) {} }
    var addTrackId by remember { mutableStateOf<Long?>(null) }
    addTrackId?.let { id -> AddToPlaylistDialog(vm, id, onDismiss = { addTrackId = null }) }

    // bez zapytania: ostatnie wyszukiwania (dotknij, żeby wrócić)
    val showRecent = query.trim().length < 2 && vm.recentSearches.isNotEmpty()
    val recent = vm.recentSearches

    fun openResult(i: Int) {
        val r = results[i]
            vm.rememberSearch(query)
            val t = r.track
            val g = r.group
            val b = r.book
            val p = r.playlist
            val st = r.station
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
                b != null -> {
                    vm.playBook(b)
                    onPlaying()
                }
                p != null -> {
                    vm.openPlaylistId = p.id
                    onOpenPlaylist()
                }
                st != null -> {
                    vm.playStation(st)
                    onPlaying()
                }
            }
    }

    KnobListScreen(
        title = tr("Szukaj", "Search"),
        rows = if (showRecent) recent.map { ListRow("↺ $it", tr("ostatnio", "recent")) }
        else results.map { r ->
            val t = r.track
            if (t != null) ListRow(r.title, r.hint, trailing = "+", onTrailing = { addTrackId = t.id })
            else ListRow(r.title, r.hint)
        },
        onBack = onBack,
        onOpen = { i -> if (showRecent) query = recent[i] else openResult(i) },
        emptyText = if (query.trim().length < 2) tr(
            "Szukaj utworów, wykonawców, albumów,\naudiobooków, playlist i stacji.\nPolskie znaki nie są potrzebne.",
            "Search songs, artists, albums,\naudiobooks, playlists and stations.",
        )
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
        if (showRecent) {
            Text(
                tr("Wyczyść ostatnie", "Clear recent"),
                color = Pal.Muted, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { vm.clearRecentSearches() }.padding(vertical = 6.dp),
            )
        }
    }
}

/** Przycisk 🔍 w górnym pasku list – wyszukiwarka zawsze pod ręką. */
@Composable
fun SearchButton(onClick: () -> Unit) {
    CircleButton(tr("Szukaj", "Search"), onClick) {
        Text("⌕", color = Pal.Text, fontSize = 20.sp)
    }
}
