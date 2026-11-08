package io.github.marekmaro.equinox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import io.github.marekmaro.equinox.data.FolderRule
import io.github.marekmaro.equinox.fmt

// ================================================================== Lista playlist

@Composable
fun PlaylistsScreen(vm: PlayerViewModel, onBack: () -> Unit, onOpenPlaylist: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    val lists = vm.playlists

    if (creating) {
        NameDialog(
            title = "Nowa playlista",
            initial = "",
            confirm = "Utwórz",
            onDismiss = { creating = false },
            onConfirm = { name ->
                creating = false
                vm.openPlaylistId = vm.createPlaylist(name)
                onOpenPlaylist()
            },
        )
    }

    KnobListScreen(
        onLongOk = { vm.togglePlay() },
        title = "Playlisty",
        rows = lists.map { ListRow(it.name, "${vm.playlistTracks(it).size}") },
        onBack = onBack,
        onOpen = { i ->
            vm.openPlaylistId = lists[i].id
            onOpenPlaylist()
        },
        emptyText = "Nie masz jeszcze playlist.\nUtwórz pierwszą przyciskiem powyżej.",
    ) {
        Chip("+ Nowa playlista", modifier = Modifier.fillMaxWidth(), selected = true) { creating = true }
    }
}

// ================================================================== Jedna playlista

@Composable
fun PlaylistScreen(vm: PlayerViewModel, onBack: () -> Unit, onPlaying: () -> Unit, onAddTracks: () -> Unit) {
    val p = vm.playlist(vm.openPlaylistId)
    if (p == null) {
        // np. playlista usunięta albo aplikacja wznowiona po zamknięciu przez system
        KnobListScreen(title = "Playlista", rows = emptyList(), onBack = onBack, onOpen = {}, emptyText = "Tej playlisty już nie ma.")
        return
    }
    val tracks = vm.playlistTracks(p)
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    if (renaming) {
        NameDialog("Zmień nazwę", p.name, "Zapisz", onDismiss = { renaming = false }) { name ->
            vm.renamePlaylist(p.id, name)
            renaming = false
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = Pal.Surface,
            title = { Text("Usunąć „${p.name}”?") },
            text = { Text("Usunięta zostanie tylko playlista. Pliki muzyczne zostaną na telefonie.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    vm.deletePlaylist(p.id)
                    onBack()
                }) { Text("Usuń") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Anuluj") } },
        )
    }

    KnobListScreen(
        onLongOk = { vm.togglePlay() },
        title = p.name,
        // „✕” przy utworze usuwa go z playlisty
        rows = tracks.map { t -> ListRow(t.title, fmt(t.durationMs), trailing = "✕", onTrailing = { vm.removeFromPlaylist(p.id, t.id) }) },
        onBack = onBack,
        onOpen = { i -> if (vm.playPlaylist(p, start = i)) onPlaying() },
        emptyText = "Playlista jest pusta.\nDodaj utwory przyciskiem „+ Dodaj”.",
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("▶ Graj", modifier = Modifier.weight(1f), selected = tracks.isNotEmpty()) {
                if (vm.playPlaylist(p)) onPlaying()
            }
            Chip("⤨ Losowo", modifier = Modifier.weight(1f)) { if (vm.playPlaylist(p, shuffle = true)) onPlaying() }
            Chip("+ Dodaj", modifier = Modifier.weight(1f)) { onAddTracks() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Zmień nazwę", modifier = Modifier.weight(1f)) { renaming = true }
            Chip("Usuń playlistę", modifier = Modifier.weight(1f)) { deleting = true }
        }
    }
}

// ================================================================== Dodawanie utworów (z wyszukiwarką)

@Composable
fun PickTracksScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val p = vm.playlist(vm.openPlaylistId)
    var query by remember { mutableStateOf("") }
    if (p == null) {
        KnobListScreen(title = "Dodaj utwory", rows = emptyList(), onBack = onBack, onOpen = {}, emptyText = "Tej playlisty już nie ma.")
        return
    }
    val q = query.trim().lowercase()
    val shown = vm.tracks.filter { q.isEmpty() || it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }

    KnobListScreen(
        title = "Dodaj do: ${p.name}",
        // „+” dodaje, „✓” oznacza, że utwór już jest na playliście (dotknięcie go usuwa)
        rows = shown.map { t ->
            val inList = t.id in p.trackIds
            ListRow(t.title, t.artist.take(18), trailing = if (inList) "✓" else "+", onTrailing = { vm.toggleInPlaylist(p.id, t.id) })
        },
        onBack = onBack,
        onOpen = { i -> vm.toggleInPlaylist(p.id, shown[i].id) },
        emptyText = if (vm.tracks.isEmpty()) "Brak muzyki w bibliotece." else "Nic nie pasuje do „$query”.",
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Szukaj tytułu lub wykonawcy…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        Text("Na playliście: ${p.trackIds.size}", color = Pal.Muted, fontFamily = Mono, fontSize = 12.sp)
    }
}

// ================================================================== Dialogi

/** „Dodaj do playlisty” – z listy muzyki i z ekranu odtwarzania. */
@Composable
fun AddToPlaylistDialog(vm: PlayerViewModel, trackId: Long, onDismiss: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Surface,
        title = { Text("Dodaj do playlisty") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(vm.playlists, key = { it.id }) { p ->
                        val inList = trackId in p.trackIds
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { vm.toggleInPlaylist(p.id, trackId) }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(p.name, color = Pal.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(if (inList) "✓" else "+", color = Pal.Accent, fontSize = 18.sp)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("Nowa playlista…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Chip("Utwórz", selected = newName.isNotBlank()) {
                        if (newName.isNotBlank()) {
                            val id = vm.createPlaylist(newName)
                            vm.addToPlaylist(id, trackId)
                            newName = ""
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Gotowe") } },
    )
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Surface,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nazwa") }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

// ================================================================== Biblioteka: skanowanie, foldery, formaty

@Composable
fun LibraryScreen(vm: PlayerViewModel, onBack: () -> Unit, onPrivacy: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Pal.Bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TopBar("Biblioteka", onBack)

        // --- Skanowanie
        Card {
            Text("Skanuj telefon", color = Pal.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                "Przeszukam pamięć telefonu i dodam pliki audio, których system jeszcze nie zauważył (np. świeżo skopiowane). Foldery z plikiem .nomedia są pomijane.",
                color = Pal.Muted, fontSize = 13.sp, lineHeight = 19.sp,
            )
            val progress = vm.scanProgress
            if (progress != null) {
                Text("Szukam… znaleziono $progress plików", color = Pal.Accent, fontFamily = Mono, fontSize = 13.sp)
            } else {
                vm.scanMessage?.let { Text(it, color = Pal.Text, fontSize = 13.sp, lineHeight = 19.sp) }
                Chip("Skanuj teraz", modifier = Modifier.fillMaxWidth(), selected = true) { vm.scanPhone() }
            }
        }

        // --- Foldery
        Card {
            Text("Foldery", color = Pal.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                "Dotknij folderu, żeby zmienić, czym jest: Auto (rozpoznaj sam) → Muzyka → Audiobook → Pomiń. Ustawienie obejmuje też podfoldery.",
                color = Pal.Muted, fontSize = 13.sp, lineHeight = 19.sp,
            )
            if (vm.folders.isEmpty()) {
                Text(if (vm.libraryLoaded) "Nie znalazłem folderów z muzyką." else "Wczytuję…", color = Pal.Muted, fontSize = 13.sp)
            }
            vm.folders.forEach { f ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { vm.cycleFolderRule(f.path) }
                        .semantics { contentDescription = "Folder ${f.name}: ${f.rule.label}. Dotknij, aby zmienić." }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, color = if (f.rule == FolderRule.SKIP) Pal.Muted else Pal.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            f.path.removePrefix("/storage/emulated/0/") + " · ${f.count}",
                            color = Pal.Muted, fontSize = 11.sp, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    RuleBadge(f.rule, f.inherited)
                }
            }
        }

        // --- Prywatność
        Card(Modifier.clickable(onClick = onPrivacy)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Prywatność", color = Pal.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("Bez kont, reklam i śledzenia. Zobacz, z czym łączy się aplikacja.", color = Pal.Muted, fontSize = 13.sp, lineHeight = 19.sp)
                }
                Text("›", color = Pal.Muted, fontSize = 22.sp)
            }
        }

        // --- Formaty
        Card {
            Text("Obsługiwane formaty", color = Pal.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                "Zawsze: MP3, AAC/M4A, M4B, FLAC, OGG, Opus, WAV, AMR.\n" +
                    "Zależnie od telefonu: ALAC, AC-3.\n" +
                    "Radio: strumienie MP3/AAC, HLS (.m3u8) i linki .pls / .m3u.\n" +
                    "Nieobsługiwane: WMA, APE, DSD.",
                color = Pal.Muted, fontSize = 13.sp, lineHeight = 20.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RuleBadge(rule: FolderRule, inherited: Boolean) {
    val active = rule != FolderRule.AUTO
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active && !inherited) Pal.AccentDim else Pal.Bg)
            .border(1.dp, if (active) Pal.Accent else Pal.Line, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            rule.label + if (inherited) " ↑" else "",
            color = if (active) Pal.Accent else Pal.Muted, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

// ================================================================== Prywatność

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Pal.Bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TopBar("Prywatność", onBack)
        Text("Equinox nie zbiera o Tobie żadnych danych.", color = Pal.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        PrivacySection(
            "Co zostaje na telefonie",
            "Twoje pliki, playlisty, ustawienia, postęp audiobooków i statystyki wyzwania. Nie wysyłam ich nikomu i nie mam do nich dostępu.",
        )
        PrivacySection(
            "Z czym łączy się aplikacja",
            "• radio-browser.info: lista stacji radiowych (i Twoje hasło wyszukiwania)\n" +
                "• serwery stacji radiowych: sam strumień dźwięku\n" +
                "• Wikipedia: tylko nazwa wykonawcy lub autora, żeby pokazać ciekawostki (wyłączysz to przyciskiem „Ukryj”)",
        )
        PrivacySection("Czego tu nie ma", "Kont, reklam, analityki, śledzenia i sprzedawania danych.")
        PrivacySection(
            "Uprawnienia",
            "Pliki audio – żeby je odtwarzać. Powiadomienia – przyciski odtwarzania. Internet – radio i ciekawostki. Wibracje – pokrętło i sygnał stopera.",
        )
        PrivacySection(
            "Jak usunąć dane",
            "Ustawienia telefonu → Aplikacje → Equinox → Pamięć → Wyczyść dane. Twoje pliki muzyczne zostaną nietknięte.",
        )
        Text("Pełna polityka prywatności jest dostępna na stronie aplikacji w Google Play.", color = Pal.Muted, fontSize = 12.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PrivacySection(title: String, text: String) {
    Card {
        Text(title, color = Pal.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(text, color = Pal.Muted, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
