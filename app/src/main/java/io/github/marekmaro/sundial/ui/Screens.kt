package io.github.marekmaro.sundial.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import io.github.marekmaro.sundial.Chime
import io.github.marekmaro.sundial.Kind
import io.github.marekmaro.sundial.PlayerViewModel
import io.github.marekmaro.sundial.RingMode
import io.github.marekmaro.sundial.fmt
import io.github.marekmaro.sundial.data.Facts
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
) {
    val np = vm.nowPlaying
    val s = vm.sessions
    val rows = listOf(
        ListRow("Losuj wszystko", if (vm.tracks.isEmpty()) "" else "${vm.tracks.size}"),
        ListRow("Muzyka"),
        ListRow("Audiobooki", if (vm.books.isEmpty()) "" else "${vm.books.size}"),
        ListRow("Radio", "na żywo"),
        ListRow("Sesje", "${s.todayMs / 60_000}/${s.goalMinutes} min"),
    )
    KnobListScreen(
        onLongOk = { vm.togglePlay() },
        title = "SUNDIAL",
        smallTitle = true,
        rows = rows,
        onBack = null,
        onOpen = { i ->
            when (i) {
                0 -> if (vm.shuffleAll()) onPlaying()
                1 -> onMusic()
                2 -> onBooks()
                3 -> onRadio()
                4 -> onSessions()
            }
        },
    ) {
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
                        if (np.isPlaying) "TERAZ GRA" else "SŁUCHAJ DALEJ",
                        color = Pal.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp,
                    )
                    Text(np.title, color = Pal.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (np.kind == Kind.RADIO) "na żywo" else "${fmt(np.positionMs)} / ${fmt(np.durationMs)}",
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

// ================================================================== Muzyka

@Composable
fun MusicScreen(vm: PlayerViewModel, onBack: () -> Unit, onPlaying: () -> Unit) {
    val tracks = vm.tracks
    KnobListScreen(
        onLongOk = { vm.togglePlay() },
        title = "Muzyka",
        rows = tracks.map { ListRow(it.title, fmt(it.durationMs)) },
        onBack = onBack,
        onOpen = { i ->
            vm.playTracks(tracks, i)
            onPlaying()
        },
        emptyText = if (!vm.libraryLoaded) "Wczytuję bibliotekę…"
        else "Nie znalazłem muzyki.\nSkopiuj pliki MP3/FLAC na telefon\ni zezwól aplikacji na dostęp do audio.",
    )
}

// ================================================================== Audiobooki

@Composable
fun BooksScreen(vm: PlayerViewModel, onBack: () -> Unit, onPlaying: () -> Unit) {
    val books = vm.books
    KnobListScreen(
        onLongOk = { vm.togglePlay() },
        title = "Audiobooki",
        rows = books.map { ListRow(it.title, "${(vm.bookProgress(it) * 100).toInt()}%") },
        onBack = onBack,
        onOpen = { i ->
            vm.playBook(books[i])
            onPlaying()
        },
        emptyText = if (!vm.libraryLoaded) "Wczytuję bibliotekę…"
        else "Brak audiobooków.\nUmieść pliki w folderze „Audiobooks”.\nJeden folder (album) = jedna książka.",
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
            vm.radioLoading -> "Szukam stacji…"
            vm.radioError != null -> vm.radioError!!
            else -> "Brak stacji. Wyszukaj lub dodaj własny link."
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Szukaj stacji…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.searchRadio(query) }),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
            )
            Chip("+ link") { showAdd = true }
        }
        if (vm.radioLoading && all.isNotEmpty()) {
            Text("Szukam…", color = Pal.Muted, fontFamily = Mono, fontSize = 12.sp)
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = Pal.Surface,
            title = { Text("Dodaj stację") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nazwa") }, singleLine = true)
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; error = false },
                        label = { Text("Link do strumienia") },
                        placeholder = { Text("https://…") },
                        singleLine = true,
                        isError = error,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    if (error) Text("Link musi zaczynać się od http:// lub https://", color = Pal.Accent, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (vm.addCustomStation(name, url)) showAdd = false else error = true
                }) { Text("Dodaj") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Anuluj") } },
        )
    }
}

// ================================================================== Teraz gra

@Composable
fun PlayingScreen(vm: PlayerViewModel, onBack: () -> Unit, onSessions: () -> Unit) {
    val np = vm.nowPlaying
    val s = vm.sessions
    val isRadio = np.kind == Kind.RADIO
    val isBook = np.kind == Kind.BOOK

    Column(
        Modifier
            .fillMaxSize()
            .background(Pal.Bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TopBar(
            when (np.kind) {
                Kind.BOOK -> "AUDIOBOOK"
                Kind.RADIO -> "RADIO"
                else -> "TERAZ GRA"
            },
            onBack, small = true,
        )

        if (np.kind == null) {
            Spacer(Modifier.height(200.dp))
            Text("Nic teraz nie gra.", color = Pal.Muted, fontSize = 16.sp)
            return@Column
        }

        // Pierścień postępu = pokrętło. Tryb wybierasz pod spodem.
        val mode = if (isRadio && vm.ringMode == RingMode.SEEK) RingMode.VOLUME else vm.ringMode
        RotaryKnob(
            Modifier.size(290.dp),
            stepDegrees = mode.stepDegrees,
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
            // Środek: dotknij = pauza/odtwarzanie, 2× = następny, przytrzymaj = poprzedni
            Box(
                Modifier
                    .size(170.dp)
                    .clip(CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { vm.togglePlay() },
                            onDoubleTap = { vm.next() },
                            onLongPress = { vm.previous() },
                        )
                    }
                    .semantics { contentDescription = if (np.isPlaying) "Pauza" else "Odtwórz" },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlyphIcon(if (np.isPlaying) Glyph.Pause else Glyph.Play, Pal.Accent, 56.dp)
                    // W miejscu okładki: krótki opis wykonawcy / autora
                    Text(
                        vm.facts?.headline?.takeIf { it.isNotBlank() } ?: "2× dalej · przytrzymaj wstecz",
                        color = Pal.Muted, fontSize = 11.sp, lineHeight = 14.sp,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RingMode.entries.forEach { m ->
                if (!(isRadio && m != RingMode.VOLUME)) {
                    Chip(m.label, selected = m == mode) { vm.ringMode = m }
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(np.title, color = Pal.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
            Text(np.subtitle, color = Pal.Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (isRadio) "● NA ŻYWO" else "${fmt(np.positionMs)} / ${fmt(np.durationMs)}",
                color = if (isRadio) Pal.Accent else Pal.Text, fontFamily = Mono, fontSize = 13.sp,
            )
            Text(mode.hint, color = Pal.Muted, fontSize = 11.sp)
            np.error?.let { Text(it, color = Pal.Accent, fontSize = 13.sp, textAlign = TextAlign.Center) }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (isBook) 10.dp else 20.dp)) {
            if (isBook) {
                Chip("−30") { vm.seekBy(-30_000) }
            }
            CircleButton("Poprzedni", { vm.previous() }, size = 52.dp) { GlyphIcon(Glyph.Prev, Pal.Text, 24.dp) }
            CircleButton(if (np.isPlaying) "Pauza" else "Odtwórz", { vm.togglePlay() }, size = 76.dp, filled = true) {
                GlyphIcon(if (np.isPlaying) Glyph.Pause else Glyph.Play, Pal.Bg, 30.dp)
            }
            CircleButton("Następny", { vm.next() }, size = 52.dp) { GlyphIcon(Glyph.Next, Pal.Text, 24.dp) }
            if (isBook) {
                Chip("+30") { vm.seekBy(30_000) }
            }
        }

        if (isBook) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.8f, 1f, 1.25f, 1.5f, 2f).forEach { v ->
                    Chip(
                        text = "${if (v % 1f == 0f) v.toInt().toString() else v.toString()}×",
                        selected = kotlin.math.abs(np.speed - v) < 0.01f,
                    ) { vm.setSpeed(v) }
                }
            }
        }

        if (!isRadio) {
            FactsCard(
                facts = vm.facts,
                enabled = vm.factsEnabled,
                onToggle = { vm.setFactsOn(!vm.factsEnabled) },
            )
        }

        val sleepText = when {
            s.sleepRemainingMs != null -> "Sen ${fmt(s.sleepRemainingMs)}"
            s.sleepAfterItem -> "Sen: po rozdziale"
            s.stopwatchRemainingMs != null -> "Stoper ${fmt(s.stopwatchRemainingMs)}"
            else -> "Sesje: sen · stoper"
        }
        val active = s.sleepRemainingMs != null || s.sleepAfterItem || s.stopwatchRemainingMs != null
        Chip(sleepText, selected = active, modifier = Modifier.fillMaxWidth(), onClick = onSessions)
    }
}

// ================================================================== Ciekawostki

@Composable
private fun FactsCard(facts: Facts?, enabled: Boolean, onToggle: () -> Unit) {
    if (!enabled) {
        Chip("ⓘ Pokaż ciekawostki o wykonawcy", modifier = Modifier.fillMaxWidth(), onClick = onToggle)
        return
    }
    if (facts == null) return

    val uri = LocalUriHandler.current
    var index by remember(facts.pageUrl) { mutableIntStateOf(0) }
    val count = facts.sentences.size

    Card(
        Modifier
            .clickable { index = (index + 1) % count }
            .semantics { contentDescription = "Ciekawostka. Dotknij, aby zobaczyć następną." },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "CIEKAWOSTKA · ${index + 1}/$count",
                color = Pal.Accent, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp, modifier = Modifier.weight(1f),
            )
            Text(
                "Ukryj",
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
                if (count > 1) "dotknij, by czytać dalej" else "",
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
        TopBar("Sesje", onBack)

        // --- Sen
        Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val label = when {
                    s.sleepRemainingMs != null -> fmt(s.sleepRemainingMs)
                    s.sleepAfterItem -> "rozdz."
                    else -> "—"
                }
                RingStat(progress = if (s.sleepRemainingMs != null) 1f else 0f, label = label, color = Pal.Accent)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Sen", color = Pal.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("Płynnie ścisza i zatrzymuje muzykę, książkę lub radio.", color = Pal.Muted, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("15", modifier = Modifier.weight(1f)) { vm.setSleep(15) }
                Chip("30", modifier = Modifier.weight(1f)) { vm.setSleep(30) }
                Chip("60", modifier = Modifier.weight(1f)) { vm.setSleep(60) }
                Chip("Rozdz.", selected = s.sleepAfterItem, modifier = Modifier.weight(1.3f)) { vm.setSleepAfterItem() }
            }
            if (s.sleepRemainingMs != null || s.sleepAfterItem) {
                Chip("Wyłącz timer", modifier = Modifier.fillMaxWidth()) { vm.cancelSleep() }
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
                    Text("Wyzwanie", color = Pal.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("${s.goalMinutes} min audiobooka dziennie.", color = Pal.Muted, fontSize = 13.sp)
                    Text(
                        if (s.streak > 0) "${s.streak} dni z rzędu" else "Zacznij serię dziś",
                        color = Pal.Text, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("− 5 min", modifier = Modifier.weight(1f)) { vm.changeGoal(-5) }
                Chip("+ 5 min", modifier = Modifier.weight(1f)) { vm.changeGoal(5) }
            }
        }

        // --- Stoper muzyczny
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Stoper muzyczny", color = Pal.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                    s.stopwatchDone -> "Czas minął! Muzyka ucichła."
                    s.stopwatchInfo != null -> "Gra: ${s.stopwatchInfo}. Kiedy muzyka ucichnie, czas minie."
                    swError -> "Za mało utworów w bibliotece, żeby ułożyć ten czas."
                    else -> "Ułożę muzykę dokładnie na ten czas. Kiedy ucichnie, czas minął. Do nauki, treningu i gotowania."
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
                Chip("Zatrzymaj stoper", modifier = Modifier.fillMaxWidth()) { vm.stopStopwatch() }
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
                .semantics { contentDescription = "Sygnał końca: ${selected.label}. Dotknij, aby zmienić." },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("♪ sygnał $number · ${selected.label}", color = Pal.Muted, fontSize = 12.sp, fontFamily = Mono)
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
