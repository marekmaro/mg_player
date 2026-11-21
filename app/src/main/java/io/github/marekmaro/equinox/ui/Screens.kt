package io.github.marekmaro.equinox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.marekmaro.equinox.Chime
import io.github.marekmaro.equinox.Kind
import io.github.marekmaro.equinox.PlayerViewModel
import io.github.marekmaro.equinox.RingMode
import io.github.marekmaro.equinox.fmt
import io.github.marekmaro.equinox.tr
import io.github.marekmaro.equinox.data.Facts
import androidx.compose.runtime.mutableIntStateOf

// ================================================================== Słuchaj (menu główne)

@Composable
fun HomeScreen(
    vm: PlayerViewModel,
    onMusic: () -> Unit,
    onBooks: () -> Unit,
    onRadio: () -> Unit,
    onSessions: () -> Unit,
    onPlaying: () -> Unit,
    onPlaylists: () -> Unit,
    onLibrary: () -> Unit,
    onSearch: () -> Unit,
    onStats: () -> Unit,
    onGroup: () -> Unit,
) {
    val np = vm.nowPlaying
    val s = vm.sessions
    // każdy wiersz menu razem ze swoją akcją – kolejność można dowolnie zmieniać
    val entries = buildList<Pair<ListRow, () -> Unit>> {
        add(ListRow(tr("Losuj wszystko", "Shuffle all"), if (vm.tracks.isEmpty()) "" else "${vm.tracks.size}") to { if (vm.shuffleAll()) onPlaying() })
        add(ListRow(tr("Muzyka", "Music")) to onMusic)
        // Nowości: tylko gdy w ostatnich 30 dniach pojawiło się coś nowego
        val fresh = vm.newTracks()
        if (fresh.isNotEmpty()) add(ListRow(tr("✦ Nowości", "✦ New"), "${fresh.size}") to {
            vm.openGroup = io.github.marekmaro.equinox.TrackGroup(io.github.marekmaro.equinox.GroupType.NEW, "new", tr("✦ Nowości", "✦ New"), fresh.size)
            onGroup()
        })
        add(ListRow(tr("Audiobooki", "Audiobooks"), if (vm.books.isEmpty()) "" else "${vm.books.size}") to onBooks)
        add(ListRow(tr("Playlisty", "Playlists"), if (vm.playlists.isEmpty()) "" else "${vm.playlists.size}") to onPlaylists)
        add(ListRow("Radio", tr("na żywo", "live")) to onRadio)
        add(ListRow(tr("Szukaj", "Search")) to onSearch)
        add(ListRow(tr("Sesje", "Sessions"), "${s.todayMs / 60_000}/${s.goalMinutes} min") to onSessions)
        if (vm.showStats) add(ListRow(tr("Statystyki", "Stats")) to onStats)
        add(ListRow(tr("Biblioteka", "Library"), tr("foldery", "folders")) to onLibrary)
    }
    KnobListScreen(
        onLongOk = { vm.togglePlay() },
        title = "EQUINOX",
        smallTitle = true,
        topTrailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DayNightToggle()
                CircleButton(tr("Ustawienia", "Settings"), { vm.settingsOpen = true }) {
                    Text("☼", color = Pal.Text, fontSize = 18.sp)
                }
            }
        },
        rows = entries.map { it.first },
        onBack = null,
        onOpen = { i -> entries.getOrNull(i)?.second?.invoke() },
    ) {
        // na małych ekranach przy aktywnej karcie „Teraz gra” oszczędzamy miejsce na listę
        if (np.kind == null || LocalConfiguration.current.screenHeightDp >= 720) {
            Text(greeting(), color = Pal.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        if (np.kind != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Pal.Surface)
                    .border(1.dp, Pal.Line, RoundedCornerShape(20.dp))
                    .clickable(onClick = onPlaying)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MiniRing(progress = if (np.durationMs > 0) np.positionMs.toFloat() / np.durationMs else 0f)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (np.isPlaying) tr("TERAZ GRA", "NOW PLAYING") else tr("SŁUCHAJ DALEJ", "KEEP LISTENING"),
                        color = Pal.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp,
                    )
                    Text(np.title, color = Pal.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (np.kind == Kind.RADIO) tr("na żywo", "live") else "${fmt(np.positionMs)} / ${fmt(np.durationMs)}",
                        color = Pal.Muted, fontFamily = Mono, fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniRing(progress: Float) {
    Canvas(Modifier.size(44.dp)) {
        val sw = 4.dp.toPx()
        val inset = sw / 2
        val arc = Size(size.width - sw, size.height - sw)
        drawArc(Pal.Line, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
        drawArc(Pal.Accent, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
    }
}

/** Przycisk „Zezwól” w pustej liście, gdy aplikacja nie ma dostępu do plików audio. */
@Composable
private fun permissionAction(vm: PlayerViewModel): Pair<String, () -> Unit>? {
    if (vm.permissionGranted) return null
    val ctx = LocalContext.current
    return tr("Zezwól na dostęp do audio", "Allow access to audio") to {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

// ================================================================== Muzyka

@Composable
fun MusicScreen(vm: PlayerViewModel, onBack: () -> Unit, onPlaying: () -> Unit, onGroups: (io.github.marekmaro.equinox.GroupType) -> Unit, onSearch: () -> Unit = {}) {
    val tracks = vm.tracks
    var addTrackId by remember { mutableStateOf<Long?>(null) }
    addTrackId?.let { id -> AddToPlaylistDialog(vm, id, onDismiss = { addTrackId = null }) }
    KnobListScreen(
        onLongOk = { vm.togglePlay() },
        title = tr("Muzyka", "Music"),
        emptyAction = permissionAction(vm),
        // „+” przy utworze = dodaj do playlisty
        topTrailing = { SearchButton(onSearch) },
        // podpis: znaczniki ♥★☼☾, „nowe” i czas trwania
        rows = remember(tracks, vm.marks) { tracks.map { t -> ListRow(t.title, trackHint(vm, t), trailing = "+", onTrailing = { addTrackId = t.id }) } },
        onBack = onBack,
        onOpen = { i ->
            vm.playTracks(tracks, i)
            onPlaying()
        },
        emptyText = if (!vm.libraryLoaded) tr("Wczytuję bibliotekę…", "Loading your library…")
        else if (!vm.permissionGranted) tr("Equinox potrzebuje dostępu do plików audio,\nżeby znaleźć Twoją muzykę.", "Equinox needs access to audio files\nto find your music.")
        else tr("Nie znalazłem muzyki.\nSkopiuj pliki MP3/FLAC na telefon.", "No music found.\nCopy MP3/FLAC files to your phone."),
    ) {
        if (tracks.isNotEmpty()) {
            // przeglądanie jak w klasycznym odtwarzaczu: wykonawcy, albumy, foldery
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(tr("Wykonawcy", "Artists"), modifier = Modifier.weight(1f)) { onGroups(io.github.marekmaro.equinox.GroupType.ARTIST) }
                Chip(tr("Albumy", "Albums"), modifier = Modifier.weight(1f)) { onGroups(io.github.marekmaro.equinox.GroupType.ALBUM) }
                Chip(tr("Foldery", "Folders"), modifier = Modifier.weight(1f)) { onGroups(io.github.marekmaro.equinox.GroupType.FOLDER) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(tr("Gatunki", "Genres"), modifier = Modifier.weight(1f)) { onGroups(io.github.marekmaro.equinox.GroupType.GENRE) }
                Chip(tr("Lata", "Years"), modifier = Modifier.weight(1f)) { onGroups(io.github.marekmaro.equinox.GroupType.DECADE) }
            }
        }
    }
}

// ================================================================== Audiobooki

@Composable
fun BooksScreen(vm: PlayerViewModel, onBack: () -> Unit, onPlaying: () -> Unit, onSearch: () -> Unit = {}) {
    val books = vm.books
    KnobListScreen(
        onLongOk = { vm.togglePlay() },
        title = tr("Audiobooki", "Audiobooks"),
        topTrailing = { SearchButton(onSearch) },
        emptyAction = permissionAction(vm),
        rows = books.map { ListRow(it.title, "${(vm.bookProgress(it) * 100).toInt()}%") },
        onBack = onBack,
        onOpen = { i ->
            vm.playBook(books[i])
            onPlaying()
        },
        emptyText = if (!vm.libraryLoaded) tr("Wczytuję bibliotekę…", "Loading your library…")
        else tr("Brak audiobooków.\nUmieść pliki w folderze „Audiobooks”.\nJeden folder (album) = jedna książka.", "No audiobooks.\nPut files in an “Audiobooks” folder.\nOne folder (album) = one book."),
    )
}

// ================================================================== Radio

@Composable
fun RadioScreen(vm: PlayerViewModel, onBack: () -> Unit, onPlaying: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (vm.radioResults.isEmpty()) vm.searchRadio("")
    }

    val favorites = vm.radioFavorites
    val results = vm.radioResults.filter { r -> favorites.none { it.url == r.url } }
    val all = favorites + results
    val rows = all.map { st ->
        val fav = vm.isFavorite(st)
        ListRow(st.name, trailing = if (fav) "★" else "☆", onTrailing = { vm.toggleFavorite(st) })
    }

    KnobListScreen(
        onLongOk = { vm.togglePlay() },
        title = "Radio",
        rows = rows,
        onBack = onBack,
        onOpen = { i ->
            vm.playStation(all[i])
            onPlaying()
        },
        emptyText = when {
            vm.radioLoading -> tr("Szukam stacji…", "Looking for stations…")
            vm.radioError != null -> vm.radioError!!
            else -> tr("Brak stacji. Wyszukaj lub dodaj własny link.", "No stations. Search or add your own link.")
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(tr("Szukaj stacji…", "Search stations…")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.searchRadio(query) }),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
            )
            Chip("+ link") { showAdd = true }
        }
        if (vm.radioLoading && all.isNotEmpty()) {
            Text(tr("Szukam…", "Searching…"), color = Pal.Muted, fontFamily = Mono, fontSize = 12.sp)
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = Pal.Surface,
            title = { Text(tr("Dodaj stację", "Add station")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(tr("Nazwa", "Name")) }, singleLine = true)
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; error = false },
                        label = { Text(tr("Link do strumienia", "Stream link")) },
                        placeholder = { Text("https://…") },
                        singleLine = true,
                        isError = error,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    if (error) Text(tr("Link musi zaczynać się od http:// lub https://", "The link must start with http:// or https://"), color = Pal.Accent, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (vm.addCustomStation(name, url)) showAdd = false else error = true
                }) { Text(tr("Dodaj", "Add")) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(tr("Anuluj", "Cancel")) } },
        )
    }
}

// ================================================================== Teraz gra

/**
 * Ekran odtwarzania w trzech układach:
 * - zwykły telefon: wszystko w jednej kolumnie,
 * - szeroki ekran (rozłożony składak, tablet, poziomo): pierścień po lewej, reszta po prawej,
 * - składak w trybie „namiotu” (zgięty poziomo): pierścień nad zgięciem, sterowanie pod nim.
 */
@Composable
fun PlayingScreen(vm: PlayerViewModel, onBack: () -> Unit, onSessions: () -> Unit, onEditLayout: () -> Unit, onQueue: () -> Unit) {
    val np = vm.nowPlaying
    val cfg = LocalConfiguration.current
    val posture = LocalPosture.current
    val wide = isWide()

    val title = when (np.kind) {
        Kind.BOOK -> tr("AUDIOBOOK", "AUDIOBOOK")
        Kind.RADIO -> tr("RADIO", "RADIO")
        else -> tr("TERAZ GRA", "NOW PLAYING")
    }
    var showTutorial by remember { mutableStateOf(!vm.tutorialSeen) }
    val base = Modifier
        .fillMaxSize()
        .background(Pal.Bg)
        .systemBarsPadding()
        .padding(horizontal = 20.dp, vertical = 12.dp)

    Box(Modifier.fillMaxSize()) {
        PlayingContent(vm, np, title, base, cfg, posture, wide, onBack, onSessions, onEditLayout) { showTutorial = true }
        if (showTutorial && np.kind != null) {
            DialTutorial(onDone = {
                showTutorial = false
                vm.markTutorialSeen()
            })
        }
    }
}

@Composable
private fun PlayingContent(
    vm: PlayerViewModel,
    np: io.github.marekmaro.equinox.NowPlaying,
    title: String,
    base: Modifier,
    cfg: android.content.res.Configuration,
    posture: Posture,
    wide: Boolean,
    onBack: () -> Unit,
    onSessions: () -> Unit,
    onEditLayout: () -> Unit,
    onHelp: () -> Unit,
) {
    if (np.kind == null) {
        Column(base, horizontalAlignment = Alignment.CenterHorizontally) {
            TopBar(title, onBack, small = true, trailing = { HelpButton(onHelp) })
            Spacer(Modifier.height(200.dp))
            Text(tr("Nic teraz nie gra.", "Nothing is playing."), color = Pal.Muted, fontSize = 16.sp)
        }
        return
    }

    when {
        posture == Posture.TABLETOP -> Column(base) {
            TopBar(title, onBack, small = true, trailing = { HelpButton(onHelp) })
            // górna połowa: pierścień (ekran stoi jak laptop)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val size = ((cfg.screenHeightDp / 2) * 0.8f).toInt().coerceIn(160, 300).dp
                PlayerRing(vm, size)
            }
            Spacer(Modifier.height(24.dp)) // miejsce na zgięcie
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PlayerBlocks(vm, onSessions, onEditLayout, onQueue)
            }
        }

        wide -> Row(base, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                TopBar(title, onBack, small = true, trailing = { HelpButton(onHelp) })
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val size = minOf(cfg.screenWidthDp / 2 - 40, cfg.screenHeightDp - 140).coerceIn(160, 360).dp
                    PlayerRing(vm, size)
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) {
                Spacer(Modifier.height(8.dp))
                PlayerBlocks(vm, onSessions, onEditLayout, onQueue)
            }
        }

        else -> Column(
            base.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TopBar(title, onBack, small = true, trailing = { HelpButton(onHelp) })
            // po bokach pierścienia zostaje pas, za który można przewijać ekran palcem
            val size = minOf(cfg.screenWidthDp - 100, (cfg.screenHeightDp * 0.45f).toInt()).coerceIn(200, 290).dp
            PlayerRing(vm, size)
            PlayerBlocks(vm, onSessions, onEditLayout, onQueue)
        }
    }
}

@Composable
private fun HelpButton(onClick: () -> Unit) {
    CircleButton(tr("Jak sterować kółkiem", "How to use the dial"), onClick) {
        Text("?", color = Pal.Muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Krótki samouczek gestów: pokazywany przy pierwszym otwarciu, potem pod „?”. */
@Composable
private fun DialTutorial(onDone: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .pointerInput(Unit) { detectTapGestures { } } // nie przepuszczaj dotknięć pod spód
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        // przewijany: przy dużej czcionce na małym ekranie karta bywa wyższa niż ekran
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
        Card {
            Text(tr("Jak sterować kółkiem", "How to use the dial"), color = Pal.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TutorialStep("↻", tr("Kręć po pierścieniu", "Turn the ring"), tr("Przewija utwór, zmienia utwory albo głośność, zależnie od trybu pod kółkiem. Szybciej kręcisz, dalej skacze.", "Seeks, changes songs or volume, depending on the mode below the dial. Turn faster to jump further."))
            TutorialStep("⏯", tr("Dotknij środka tarczy", "Tap the centre"), tr("Pauza i odtwarzanie.", "Pause and play."))
            TutorialStep("⏮⏭", tr("Dotknij lewej lub prawej strony", "Tap the left or right side"), tr("Poprzedni albo następny utwór.", "Previous or next song."))
            TutorialStep("✋", tr("Przytrzymaj tarczę", "Hold the dial"), tr("Zmienia tryb kręcenia: Przewiń → Utwory → Głośność.", "Changes what turning does: Seek → Songs → Volume."))
            TutorialStep("OK", tr("W listach", "In lists"), tr("Kręć, żeby wybrać, środek OK otwiera, przytrzymanie OK to pauza.", "Turn to choose, the OK centre opens, holding OK pauses."))
            Chip(tr("Rozumiem", "Got it"), modifier = Modifier.fillMaxWidth(), selected = true, onClick = onDone)
        }
        }
    }
}

@Composable
private fun TutorialStep(symbol: String, title: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Pal.Surface2), contentAlignment = Alignment.Center) {
            Text(symbol, color = Pal.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Pal.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(text, color = Pal.Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

/** Pierścień postępu = pokrętło. Środek: dotknij = pauza, 2× = następny, przytrzymaj = poprzedni. */
@Composable
private fun PlayerRing(vm: PlayerViewModel, ringSize: androidx.compose.ui.unit.Dp) {
    val np = vm.nowPlaying
    val isRadio = np.kind == Kind.RADIO
    val mode = vm.effectiveRingMode
    RotaryKnob(
        Modifier.size(ringSize),
        stepDegrees = mode.stepDegrees,
        // przy przeskakiwaniu utworów bez przyspieszania – żeby nie przeskoczyć przypadkiem kilku
        accelerate = mode != RingMode.TRACKS,
        onStep = { d -> vm.ringStep(d) },
    ) { _ ->
        val frac = if (np.durationMs > 0) np.positionMs.toFloat() / np.durationMs else 0f
        Canvas(Modifier.fillMaxSize()) {
            drawTicks(60, 5, minor = Pal.Line, major = Pal.Tick)
            val sw = 6.dp.toPx()
            val inset = 22.dp.toPx()
            val arc = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(Pal.Surface2, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
            if (!isRadio) {
                drawArc(Pal.Accent, -90f, 360f * frac.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
            } else if (np.isPlaying) {
                drawArc(Pal.Accent, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
            }
            drawCircle(Pal.Surface, radius = size.minDimension / 2f - 40.dp.toPx())
        }
        // Tarcza w środku: lewa strona = poprzedni, środek = pauza/graj, prawa = następny.
        // Przytrzymanie = zmiana trybu kręcenia. Reakcja od razu (bez czekania na podwójne dotknięcie).
        val haptic = LocalHapticFeedback.current
        val face = ringSize * 0.58f
        Box(
            Modifier
                .size(face)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { pos ->
                            val third = size.width / 3f
                            when {
                                pos.x < third -> vm.previous()
                                pos.x > third * 2 -> vm.next()
                                else -> vm.togglePlay()
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onLongPress = {
                            vm.cycleRingMode()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                    )
                }
                .semantics {
                    contentDescription = tr(
                        "Tarcza: lewa strona poprzedni, środek ${if (np.isPlaying) "pauza" else "odtwórz"}, prawa strona następny. Przytrzymaj, aby zmienić tryb kręcenia.",
                        "Dial: left side previous, centre ${if (np.isPlaying) "pause" else "play"}, right side next. Hold to change what turning does.",
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            // Okładka z pliku (jeśli jest i jest włączona) – pod przyciskami, z przyciemnieniem
            val cover = if (vm.showCovers) vm.artwork else null
            if (cover != null) {
                Image(
                    bitmap = cover,
                    contentDescription = tr("Okładka", "Cover art"),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().clip(CircleShape),
                )
                Box(Modifier.matchParentSize().clip(CircleShape).background(Color.Black.copy(alpha = 0.42f)))
            }
            val side = if (cover != null) Color.White.copy(alpha = 0.85f) else Pal.Muted
            Row(
                Modifier.fillMaxWidth().padding(horizontal = face * 0.08f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                GlyphIcon(Glyph.Prev, side, (face * 0.13f).coerceAtMost(26.dp))
                GlyphIcon(if (np.isPlaying) Glyph.Pause else Glyph.Play, Pal.Accent, (face * 0.3f).coerceAtMost(60.dp))
                GlyphIcon(Glyph.Next, side, (face * 0.13f).coerceAtMost(26.dp))
            }
            // Bez okładki: krótki opis wykonawcy / autora (ciekawostka)
            if (cover == null) vm.facts?.headline?.takeIf { it.isNotBlank() }?.let { headline ->
                Text(
                    headline,
                    color = Pal.Muted, fontSize = 11.sp, lineHeight = 14.sp,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(start = 22.dp, end = 22.dp, bottom = face * 0.14f),
                )
            }
        }
    }
}

// ================================================================== Ciekawostki

@Composable
internal fun FactsCard(facts: Facts?, enabled: Boolean, onToggle: () -> Unit) {
    if (!enabled) {
        Chip(tr("ⓘ Pokaż ciekawostki o wykonawcy", "ⓘ Show artist fun facts"), modifier = Modifier.fillMaxWidth(), onClick = onToggle)
        return
    }
    if (facts == null) return

    val uri = LocalUriHandler.current
    var index by remember(facts.pageUrl) { mutableIntStateOf(0) }
    val count = facts.sentences.size

    Card(
        Modifier
            .clickable { index = (index + 1) % count }
            .semantics { contentDescription = tr("Ciekawostka. Dotknij, aby zobaczyć następną.", "Fun fact. Tap to see the next one.") },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("CIEKAWOSTKA · ${index + 1}/$count", "FUN FACT · ${index + 1}/$count"),
                color = Pal.Accent, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp, modifier = Modifier.weight(1f),
            )
            Text(
                tr("Ukryj", "Hide"),
                color = Pal.Muted, fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        Text(facts.name, color = Pal.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(facts.sentences[index.coerceIn(0, count - 1)], color = Pal.Text, fontSize = 14.sp, lineHeight = 21.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (count > 1) tr("dotknij, by czytać dalej", "tap to read more") else "",
                color = Pal.Muted, fontSize = 11.sp, modifier = Modifier.weight(1f),
            )
            Text(
                "Wikipedia · CC BY-SA ›",
                color = Pal.Muted, fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { try { uri.openUri(facts.pageUrl) } catch (e: Exception) {} }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

// ================================================================== Sesje

@Composable
fun SessionsScreen(vm: PlayerViewModel, onBack: () -> Unit, onPlaying: () -> Unit) {
    val s = vm.sessions
    var swError by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Pal.Bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TopBar(tr("Sesje", "Sessions"), onBack)

        SectionLabel(tr("DZIEŃ · skupienie", "DAY · focus"))

        // --- Stoper muzyczny
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Stoper muzyczny", "Music timer"), color = Pal.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        s.stopwatchRemainingMs != null -> fmt(s.stopwatchRemainingMs)
                        else -> "%02d:00".format(s.stopwatchMinutes)
                    },
                    color = if (s.stopwatchDone) Pal.Accent else Pal.Text,
                    fontFamily = Mono, fontSize = 52.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (s.stopwatchRemainingMs == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("+5") { vm.changeStopwatch(5) }
                        Chip("−5") { vm.changeStopwatch(-5) }
                    }
                }
            }
            ChimePicker(selected = vm.chime, onSelect = { vm.selectChime(it) })
            Text(
                when {
                    s.stopwatchDone -> tr("Czas minął! Muzyka ucichła.", "Time's up! The music has stopped.")
                    s.stopwatchInfo != null -> tr("Gra: ${s.stopwatchInfo}. Kiedy muzyka ucichnie, czas minie.", "Playing: ${s.stopwatchInfo}. When the music stops, time's up.")
                    swError -> tr("Za mało utworów w bibliotece, żeby ułożyć ten czas.", "Not enough songs in your library to fill this time.")
                    else -> tr("Ułożę muzykę dokładnie na ten czas. Kiedy ucichnie, czas minął. Do nauki, treningu i gotowania.", "I'll line up music for exactly this long. When it stops, time's up. Great for studying, workouts and cooking.")
                },
                color = if (s.stopwatchDone) Pal.Accent else Pal.Muted, fontSize = 13.sp, lineHeight = 19.sp,
            )
            if (s.stopwatchRemainingMs == null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Pal.Accent)
                        .clickable {
                            swError = !vm.startStopwatch()
                            if (!swError) onPlaying()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Start", color = Pal.Bg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Chip(tr("Zatrzymaj stoper", "Stop the timer"), modifier = Modifier.fillMaxWidth()) { vm.stopStopwatch() }
            }
        }
        // --- Wyzwanie
        Card {
            val goalMs = s.goalMinutes * 60_000L
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RingStat(
                    progress = (s.todayMs.toFloat() / goalMs).coerceIn(0f, 1f),
                    label = "${s.todayMs / 60_000}/${s.goalMinutes}",
                    color = Pal.Text,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("Wyzwanie", "Challenge"), color = Pal.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(tr("${s.goalMinutes} min audiobooka dziennie.", "${s.goalMinutes} min of audiobooks a day."), color = Pal.Muted, fontSize = 13.sp)
                    Text(
                        if (s.streak > 0) tr("${s.streak} dni z rzędu", "${s.streak}-day streak") else tr("Zacznij serię dziś", "Start a streak today"),
                        color = Pal.Text, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("− 5 min", modifier = Modifier.weight(1f)) { vm.changeGoal(-5) }
                Chip("+ 5 min", modifier = Modifier.weight(1f)) { vm.changeGoal(5) }
            }
        }

        SectionLabel(tr("NOC · odpoczynek", "NIGHT · rest"))
        if (vm.batteryRestricted && !vm.batteryHintHidden) BatteryHint(vm)

        // --- Sen
        Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val label = when {
                    s.sleepRemainingMs != null -> fmt(s.sleepRemainingMs)
                    s.sleepAfterItem -> tr("rozdz.", "chap.")
                    else -> "—"
                }
                RingStat(progress = if (s.sleepRemainingMs != null) 1f else 0f, label = label, color = Pal.Accent)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("Sen", "Sleep"), color = Pal.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(tr("Płynnie ścisza i zatrzymuje muzykę, książkę lub radio.", "Gently fades out and stops music, a book or the radio."), color = Pal.Muted, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("15", modifier = Modifier.weight(1f)) { vm.setSleep(15) }
                Chip("30", modifier = Modifier.weight(1f)) { vm.setSleep(30) }
                Chip("60", modifier = Modifier.weight(1f)) { vm.setSleep(60) }
                Chip(tr("Rozdz.", "Chap."), selected = s.sleepAfterItem, modifier = Modifier.weight(1.3f)) { vm.setSleepAfterItem() }
            }
            if (s.sleepRemainingMs != null || s.sleepAfterItem) {
                Chip(tr("Wyłącz timer", "Turn off the timer"), modifier = Modifier.fillMaxWidth()) { vm.cancelSleep() }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

/** Subtelne rozwijane menu sygnału pod czasem stopera: 1–4, wybór od razu odtwarza próbkę. */
@Composable
private fun ChimePicker(selected: Chime, onSelect: (Chime) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val number = Chime.entries.indexOf(selected) + 1
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { open = true }
                .padding(vertical = 8.dp, horizontal = 4.dp)
                .semantics { contentDescription = tr("Sygnał końca: ${selected.label}. Dotknij, aby zmienić.", "End signal: ${selected.label}. Tap to change.") },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(tr("♪ sygnał $number · ${selected.label}", "♪ signal $number · ${selected.label}"), color = Pal.Muted, fontSize = 12.sp, fontFamily = Mono)
            Text(if (open) "▴" else "▾", color = Pal.Muted, fontSize = 12.sp)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Pal.Surface2,
        ) {
            Chime.entries.forEachIndexed { i, c ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${i + 1}  ${c.label}",
                            color = if (c == selected) Pal.Accent else Pal.Text,
                            fontFamily = Mono, fontSize = 14.sp,
                        )
                    },
                    onClick = {
                        onSelect(c) // wybiera i od razu odtwarza próbkę
                        open = false
                    },
                )
            }
        }
    }
}

/**
 * Niektóre telefony (Xiaomi, Huawei, Oppo, Samsung…) agresywnie usypiają aplikacje w tle.
 * Podpowiadamy, jak zwolnić Equinox z ograniczeń, żeby timer snu i stoper działały niezawodnie.
 */
@Composable
private fun BatteryHint(vm: PlayerViewModel) {
    val ctx = LocalContext.current
    val brand = Build.MANUFACTURER.lowercase().replace(" ", "-")
    val known = setOf("xiaomi", "huawei", "honor", "oppo", "realme", "oneplus", "vivo", "samsung", "meizu", "asus", "sony", "nokia", "google", "motorola", "lenovo", "tecno", "infinix")
    Card {
        Text(tr("Oszczędzanie baterii", "Battery saving"), color = Pal.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            tr("Telefon może usypiać Equinox przy zgaszonym ekranie, a wtedy timer snu albo stoper nie zadziała na czas. Zezwól aplikacji na działanie w tle bez ograniczeń.", "Your phone may put Equinox to sleep when the screen is off, and then the sleep timer or music timer won't fire on time. Let the app run in the background without restrictions."),
            color = Pal.Muted, fontSize = 13.sp, lineHeight = 19.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(tr("Ustawienia baterii", "Battery settings"), modifier = Modifier.weight(1f), selected = true) {
                val intents = listOf(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)),
                )
                for (i in intents) {
                    try {
                        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        break
                    } catch (e: Exception) {
                    }
                }
            }
            Chip(tr("Porady: ${Build.MANUFACTURER}", "Tips: ${Build.MANUFACTURER}"), modifier = Modifier.weight(1f)) {
                val url = if (brand in known) "https://dontkillmyapp.com/$brand" else "https://dontkillmyapp.com"
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                }
            }
        }
        Text(
            tr("Nie pokazuj więcej", "Don't show again"),
            color = Pal.Muted, fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { vm.hideBatteryHint() }
                .padding(vertical = 6.dp),
        )
    }
}

/** Nagłówek grupy w Sesjach: Dzień / Noc. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Pal.Muted, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun RingStat(progress: Float, label: String, color: Color) {
    Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 5.dp.toPx()
            val inset = sw / 2
            val arc = Size(size.width - sw, size.height - sw)
            drawArc(Pal.Line, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
            drawArc(color, -90f, 360f * progress, false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Text(label, color = Pal.Text, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
