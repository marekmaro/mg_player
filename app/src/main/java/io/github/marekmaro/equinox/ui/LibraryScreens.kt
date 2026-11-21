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
import io.github.marekmaro.equinox.tr

// ================================================================== Lista playlist

@Composable
fun PlaylistsScreen(vm: PlayerViewModel, onBack: () -> Unit, onOpenPlaylist: () -> Unit, onOpenGroup: () -> Unit, onSearch: () -> Unit = {}) {
    var creating by remember { mutableStateOf(false) }
    val lists = vm.playlists
    // na górze trzy playlisty automatyczne: Ulubione, Ostatnio dodane, Najczęściej słuchane
    val smart = remember(vm.tracks, vm.marks) { vm.smartGroups() }

    if (creating) {
        NameDialog(
            title = tr("Nowa playlista", "New playlist"),
            initial = "",
            confirm = tr("Utwórz", "Create"),
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
        title = tr("Playlisty", "Playlists"),
        topTrailing = { SearchButton(onSearch) },
        rows = smart.map { ListRow(it.title, "${it.count}") } + lists.map { ListRow(it.name, "${vm.playlistTracks(it).size}") },
        onBack = onBack,
        onOpen = { i ->
            if (i < smart.size) {
                vm.openGroup = smart[i]
                onOpenGroup()
            } else {
                vm.openPlaylistId = lists[i - smart.size].id
                onOpenPlaylist()
            }
        },
        emptyText = tr("Nie masz jeszcze playlist.\nUtwórz pierwszą przyciskiem powyżej.", "No playlists yet.\nCreate your first one with the button above."),
    ) {
        Chip(tr("+ Nowa playlista", "+ New playlist"), modifier = Modifier.fillMaxWidth(), selected = true) { creating = true }
    }
}

// ================================================================== Jedna playlista

@Composable
fun PlaylistScreen(vm: PlayerViewModel, onBack: () -> Unit, onPlaying: () -> Unit, onAddTracks: () -> Unit) {
    val p = vm.playlist(vm.openPlaylistId)
    if (p == null) {
        // np. playlista usunięta albo aplikacja wznowiona po zamknięciu przez system
        KnobListScreen(title = tr("Playlista", "Playlist"), rows = emptyList(), onBack = onBack, onOpen = {}, emptyText = tr("Tej playlisty już nie ma.", "This playlist no longer exists."))
        return
    }
    val tracks = vm.playlistTracks(p)
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    if (renaming) {
        NameDialog(tr("Zmień nazwę", "Rename"), p.name, tr("Zapisz", "Save"), onDismiss = { renaming = false }) { name ->
            vm.renamePlaylist(p.id, name)
            renaming = false
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = Pal.Surface,
            title = { Text(tr("Usunąć „${p.name}”?", "Delete “${p.name}”?")) },
            text = { Text(tr("Usunięta zostanie tylko playlista. Pliki muzyczne zostaną na telefonie.", "Only the playlist will be deleted. Your music files stay on the phone.")) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    vm.deletePlaylist(p.id)
                    onBack()
                }) { Text(tr("Usuń", "Delete")) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(tr("Anuluj", "Cancel")) } },
        )
    }

    KnobListScreen(
        onLongOk = { vm.togglePlay() },
        title = p.name,
        // „✕” przy utworze usuwa go z playlisty
        rows = tracks.map { t -> ListRow(t.title, fmt(t.durationMs), trailing = "✕", onTrailing = { vm.removeFromPlaylist(p.id, t.id) }) },
        onBack = onBack,
        onOpen = { i -> if (vm.playPlaylist(p, start = i)) onPlaying() },
        emptyText = tr("Playlista jest pusta.\nDodaj utwory przyciskiem „+ Dodaj”.", "The playlist is empty.\nAdd songs with the “+ Add” button."),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(tr("▶ Graj", "▶ Play"), modifier = Modifier.weight(1f), selected = tracks.isNotEmpty()) {
                if (vm.playPlaylist(p)) onPlaying()
            }
            Chip(tr("⤨ Losowo", "⤨ Shuffle"), modifier = Modifier.weight(1f)) { if (vm.playPlaylist(p, shuffle = true)) onPlaying() }
            Chip(tr("+ Dodaj", "+ Add"), modifier = Modifier.weight(1f)) { onAddTracks() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(tr("Zmień nazwę", "Rename"), modifier = Modifier.weight(1f)) { renaming = true }
            Chip(tr("Usuń playlistę", "Delete playlist"), modifier = Modifier.weight(1f)) { deleting = true }
        }
    }
}

// ================================================================== Dodawanie utworów (z wyszukiwarką)

@Composable
fun PickTracksScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val p = vm.playlist(vm.openPlaylistId)
    var query by remember { mutableStateOf("") }
    if (p == null) {
        KnobListScreen(title = tr("Dodaj utwory", "Add songs"), rows = emptyList(), onBack = onBack, onOpen = {}, emptyText = tr("Tej playlisty już nie ma.", "This playlist no longer exists."))
        return
    }
    val q = query.trim().lowercase()
    val shown = vm.tracks.filter { q.isEmpty() || it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }

    KnobListScreen(
        title = tr("Dodaj do: ${p.name}", "Add to: ${p.name}"),
        // „+” dodaje, „✓” oznacza, że utwór już jest na playliście (dotknięcie go usuwa)
        rows = shown.map { t ->
            val inList = t.id in p.trackIds
            ListRow(t.title, t.artist.take(18), trailing = if (inList) "✓" else "+", onTrailing = { vm.toggleInPlaylist(p.id, t.id) })
        },
        onBack = onBack,
        onOpen = { i -> vm.toggleInPlaylist(p.id, shown[i].id) },
        emptyText = if (vm.tracks.isEmpty()) tr("Brak muzyki w bibliotece.", "No music in your library.") else tr("Nic nie pasuje do „$query”.", "Nothing matches “$query”."),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(tr("Szukaj tytułu lub wykonawcy…", "Search title or artist…")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        Text(tr("Na playliście: ${p.trackIds.size}", "On the playlist: ${p.trackIds.size}"), color = Pal.Muted, fontFamily = Mono, fontSize = 12.sp)
    }
}

// ================================================================== Dialogi

/**
 * Działania na utworze – z listy muzyki i z ekranu odtwarzania: kolejka, znaczniki,
 * udostępnianie, dzwonek i playlisty.
 */
@Composable
fun AddToPlaylistDialog(vm: PlayerViewModel, trackId: Long, onDismiss: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    // dzwonek: true = pokazujemy pytanie (albo prośbę o zgodę systemu)
    var ringtoneAsk by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Surface,
        title = { Text(vm.track(trackId)?.title ?: tr("Dodaj…", "Add…"), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // kolejka: tuż po bieżącym albo na koniec
                vm.track(trackId)?.let { t ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(tr("▶ Jako następny", "▶ Play next"), modifier = Modifier.weight(1f), selected = true) {
                            vm.playNext(t)
                            onDismiss()
                        }
                        Chip(tr("+ Do kolejki", "+ Add to queue"), modifier = Modifier.weight(1f)) {
                            vm.addToQueue(t)
                            onDismiss()
                        }
                    }
                    // znaczniki – można oznaczyć utwór bez włączania go
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        io.github.marekmaro.equinox.Mark.entries.forEach { m ->
                            Chip(m.glyph, modifier = Modifier.weight(1f), selected = m in vm.marksOf(trackId)) { vm.toggleMark(trackId, m) }
                        }
                    }
                    // udostępnij plik albo ustaw go jako dzwonek
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(tr("↗ Udostępnij", "↗ Share"), modifier = Modifier.weight(1f)) {
                            io.github.marekmaro.equinox.TrackActions.share(ctx, t)
                        }
                        Chip(tr("☏ Jako dzwonek", "☏ As ringtone"), modifier = Modifier.weight(1f), selected = ringtoneAsk) { ringtoneAsk = !ringtoneAsk }
                    }
                    if (ringtoneAsk) {
                        val allowed = io.github.marekmaro.equinox.TrackActions.canSetRingtone(ctx)
                        Text(
                            if (allowed) tr("Ustawić „${t.title}” jako dzwonek telefonu?", "Set “${t.title}” as your phone's ringtone?")
                            else tr(
                                "Android wymaga Twojej zgody: włącz „Modyfikowanie ustawień systemu” dla Equinox, wróć i dotknij jeszcze raz.",
                                "Android needs your permission: turn on “Modify system settings” for Equinox, come back and tap again.",
                            ),
                            color = Pal.Text, fontSize = 13.sp, lineHeight = 18.sp,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (allowed) {
                                Chip(tr("Ustaw", "Set"), modifier = Modifier.weight(1f), selected = true) {
                                    if (io.github.marekmaro.equinox.TrackActions.setRingtone(ctx, t)) ringtoneAsk = false
                                }
                            } else {
                                Chip(tr("Otwórz ustawienia", "Open settings"), modifier = Modifier.weight(1f), selected = true) {
                                    io.github.marekmaro.equinox.TrackActions.openRingtonePermission(ctx)
                                }
                            }
                            Chip(tr("Anuluj", "Cancel"), modifier = Modifier.weight(1f)) { ringtoneAsk = false }
                        }
                    }
                    Text(tr("Do playlisty:", "To playlist:"), color = Pal.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
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
                        placeholder = { Text(tr("Nowa playlista…", "New playlist…")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Chip(tr("Utwórz", "Create"), selected = newName.isNotBlank()) {
                        if (newName.isNotBlank()) {
                            val id = vm.createPlaylist(newName)
                            vm.addToPlaylist(id, trackId)
                            newName = ""
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Gotowe", "Done")) } },
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
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(tr("Nazwa", "Name")) }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Anuluj", "Cancel")) } },
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
        TopBar(tr("Biblioteka", "Library"), onBack)

        // --- Skanowanie
        Card {
            Text(tr("Skanuj telefon", "Scan the phone"), color = Pal.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                tr("Przeszukam pamięć telefonu i dodam pliki audio, których system jeszcze nie zauważył (np. świeżo skopiowane). Foldery z plikiem .nomedia są pomijane.", "I'll search the phone's storage and add audio files the system hasn't noticed yet (e.g. freshly copied). Folders with a .nomedia file are skipped."),
                color = Pal.Muted, fontSize = 13.sp, lineHeight = 19.sp,
            )
            val progress = vm.scanProgress
            if (progress != null) {
                Text(tr("Szukam… znaleziono $progress plików", "Searching… found $progress files"), color = Pal.Accent, fontFamily = Mono, fontSize = 13.sp)
            } else {
                vm.scanMessage?.let { Text(it, color = Pal.Text, fontSize = 13.sp, lineHeight = 19.sp) }
                Chip(tr("Skanuj teraz", "Scan now"), modifier = Modifier.fillMaxWidth(), selected = true) { vm.scanPhone() }
            }
        }

        // --- Foldery
        Card {
            Text(tr("Foldery", "Folders"), color = Pal.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                tr("Dotknij folderu, żeby zmienić, czym jest: Auto (rozpoznaj sam) → Muzyka → Audiobook → Pomiń. Ustawienie obejmuje też podfoldery.", "Tap a folder to change what it is: Auto (detect) → Music → Audiobook → Skip. The setting also covers subfolders."),
                color = Pal.Muted, fontSize = 13.sp, lineHeight = 19.sp,
            )
            if (vm.folders.isEmpty()) {
                Text(if (vm.libraryLoaded) tr("Nie znalazłem folderów z muzyką.", "No music folders found.") else tr("Wczytuję…", "Loading…"), color = Pal.Muted, fontSize = 13.sp)
            }
            vm.folders.forEach { f ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { vm.cycleFolderRule(f.path) }
                        .semantics { contentDescription = tr("Folder ${f.name}: ${f.rule.label}. Dotknij, aby zmienić.", "Folder ${f.name}: ${f.rule.label}. Tap to change.") }
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
                    Text(tr("Prywatność", "Privacy"), color = Pal.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(tr("Bez kont, reklam i śledzenia. Zobacz, z czym łączy się aplikacja.", "No accounts, ads or tracking. See what the app connects to."), color = Pal.Muted, fontSize = 13.sp, lineHeight = 19.sp)
                }
                Text("›", color = Pal.Muted, fontSize = 22.sp)
            }
        }

        // --- Formaty
        Card {
            Text(tr("Obsługiwane formaty", "Supported formats"), color = Pal.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                tr("Zawsze: MP3, AAC/M4A, M4B, FLAC, OGG, Opus, WAV, AMR.\n", "Always: MP3, AAC/M4A, M4B, FLAC, OGG, Opus, WAV, AMR.\n") +
                    tr("Zależnie od telefonu: ALAC, AC-3.\n", "Depending on the phone: ALAC, AC-3.\n") +
                    tr("Radio: strumienie MP3/AAC, HLS (.m3u8) i linki .pls / .m3u.\n", "Radio: MP3/AAC streams, HLS (.m3u8) and .pls / .m3u links.\n") +
                    tr("Nieobsługiwane: WMA, APE, DSD.", "Not supported: WMA, APE, DSD."),
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
        TopBar(tr("Prywatność", "Privacy"), onBack)
        Text(tr("Equinox nie zbiera o Tobie żadnych danych.", "Equinox doesn't collect any data about you."), color = Pal.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        PrivacySection(
            tr("Co zostaje na telefonie", "What stays on your phone"),
            tr("Twoje pliki, playlisty, ustawienia, postęp audiobooków i statystyki wyzwania. Nie wysyłam ich nikomu i nie mam do nich dostępu.", "Your files, playlists, settings, audiobook progress and stats. I don't send them anywhere and I have no access to them."),
        )
        PrivacySection(
            tr("Z czym łączy się aplikacja", "What the app connects to"),
            tr("• radio-browser.info: lista stacji radiowych (i Twoje hasło wyszukiwania)\n", "• radio-browser.info: the list of radio stations (and your search term)\n") +
                tr("• serwery stacji radiowych: sam strumień dźwięku\n", "• radio station servers: just the audio stream\n") +
                tr("• Wikipedia: tylko nazwa wykonawcy lub autora, żeby pokazać ciekawostki (wyłączysz to przyciskiem „Ukryj”)", "• Wikipedia: only the artist's or author's name, to show fun facts (turn it off with “Hide”)"),
        )
        PrivacySection(tr("Czego tu nie ma", "What's not here"), tr("Kont, reklam, analityki, śledzenia i sprzedawania danych.", "Accounts, ads, analytics, tracking or selling data."))
        PrivacySection(
            tr("Uprawnienia", "Permissions"),
            tr("Pliki audio – żeby je odtwarzać. Powiadomienia – przyciski odtwarzania. Internet – radio i ciekawostki. Wibracje – pokrętło i sygnał stopera.", "Audio files – to play them. Notifications – playback buttons. Internet – radio and fun facts. Vibration – the dial and the timer signal."),
        )
        PrivacySection(
            tr("Jak usunąć dane", "How to delete your data"),
            tr("Ustawienia telefonu → Aplikacje → Equinox → Pamięć → Wyczyść dane. Twoje pliki muzyczne zostaną nietknięte.", "Phone settings → Apps → Equinox → Storage → Clear data. Your music files stay untouched."),
        )
        Text(tr("Pełna polityka prywatności jest dostępna na stronie aplikacji w Google Play.", "The full privacy policy is available on the app's Google Play page."), color = Pal.Muted, fontSize = 12.sp, lineHeight = 17.sp)
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
