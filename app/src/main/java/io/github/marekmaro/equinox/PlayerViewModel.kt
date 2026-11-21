package io.github.marekmaro.equinox

import android.app.Application
import android.content.ComponentName
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.media.AudioManager
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.marekmaro.equinox.data.Book
import io.github.marekmaro.equinox.data.SessionStore
import io.github.marekmaro.equinox.data.Chapters
import io.github.marekmaro.equinox.data.FolderInfo
import io.github.marekmaro.equinox.data.FolderRule
import io.github.marekmaro.equinox.data.Library
import io.github.marekmaro.equinox.data.Playlist
import io.github.marekmaro.equinox.data.Facts
import io.github.marekmaro.equinox.data.FactsRepository
import io.github.marekmaro.equinox.data.MediaRepository
import io.github.marekmaro.equinox.data.Prefs
import io.github.marekmaro.equinox.data.RadioRepository
import io.github.marekmaro.equinox.data.Station
import io.github.marekmaro.equinox.data.Track
import io.github.marekmaro.equinox.data.toMediaItem
import java.time.LocalDate

/** Co robi obrót pierścienia na ekranie „Teraz gra”. */
enum class RingMode(private val pl: String, private val en: String, val stepDegrees: Float, private val hintPl: String, private val hintEn: String) {
    SEEK("Przewiń", "Seek", 15f, "kręć: ±10 s", "turn: ±10 s"),
    TRACKS("Utwory", "Songs", 60f, "kręć: następny / poprzedni", "turn: next / previous"),
    VOLUME("Głośność", "Volume", 20f, "kręć: głośniej / ciszej", "turn: louder / quieter");

    val label: String get() = tr(pl, en)
    val hint: String get() = tr(hintPl, hintEn)
}

data class NowPlaying(
    val title: String = "",
    val subtitle: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val kind: Kind? = null,
    val speed: Float = 1f,
    val error: String? = null,
    /** rozdział wewnątrz pliku (M4B): np. „3/12 · Pan Tadeusz”, null gdy brak */
    val chapter: String? = null,
    val shuffle: Boolean = false,
    /** Player.REPEAT_MODE_OFF / ALL / ONE */
    val repeat: Int = 0,
    /** id utworu z biblioteki (do serduszka i „Do playlisty”), null dla radia */
    val trackId: Long? = null,
)

/** Rodzaj grupy utworów do przeglądania. */
enum class GroupType { ARTIST, ALBUM, FOLDER, MARK, NEW, TOP }

data class TrackGroup(val type: GroupType, val key: String, val title: String, val count: Int)

data class SearchResult(
    val title: String,
    val hint: String,
    val track: Track? = null,
    val group: TrackGroup? = null,
    val book: Book? = null,
    val playlist: Playlist? = null,
    val station: Station? = null,
)

data class SessionsUi(
    val sleepRemainingMs: Long? = null,
    val sleepAfterItem: Boolean = false,
    val todayMs: Long = 0,
    val goalMinutes: Int = 20,
    val streak: Int = 0,
    val stopwatchMinutes: Int = 25,
    val stopwatchRemainingMs: Long? = null,
    val stopwatchDone: Boolean = false,
    val stopwatchInfo: String? = null,
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    var tracks by mutableStateOf<List<Track>>(emptyList()); private set
    var books by mutableStateOf<List<Book>>(emptyList()); private set
    var libraryLoaded by mutableStateOf(false); private set
    var nowPlaying by mutableStateOf(NowPlaying()); private set
    var sessions by mutableStateOf(SessionsUi()); private set
    var ringMode by mutableStateOf(RingMode.SEEK)
    var chime by mutableStateOf(Chime.entries.firstOrNull { it.name == prefs.chime } ?: Chime.CHIP); private set

    var facts by mutableStateOf<Facts?>(null); private set
    var factsEnabled by mutableStateOf(prefs.factsEnabled); private set
    private var factsKey: String? = null
    private var factsJob: Job? = null
    private val factsMisses = mutableSetOf<String>()

    var radioResults by mutableStateOf<List<Station>>(emptyList()); private set
    var radioFavorites by mutableStateOf<List<Station>>(emptyList()); private set
    var radioLoading by mutableStateOf(false); private set
    var radioError by mutableStateOf<String?>(null); private set

    private var playerError: String? = null
    private var connectAttempts = 0
    private var tickCount = 0
    private var cachedStreak = 0

    /** Czy aplikacja ma dostęp do plików audio (do pokazania przycisku „Zezwól”). */
    var permissionGranted by mutableStateOf(hasAudioPermission()); private set

    // Biblioteka odświeża się sama, gdy na telefonie pojawią się lub znikną pliki
    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = scheduleReload()
    }
    private var reloadJob: Job? = null

    // ---------------------------------------------------------------- Klocki ekranu odtwarzania

    var layout by mutableStateOf(Block.parse(prefs.playerLayout)); private set

    /** Tytuły trzech następnych pozycji w kolejce (klocek „Następne”). */
    var upNext by mutableStateOf<List<String>>(emptyList()); private set

    fun toggleBlock(b: Block) {
        if (b.required) return
        saveLayout(if (b in layout) layout - b else layout + b)
    }

    fun moveBlock(b: Block, delta: Int) {
        val i = layout.indexOf(b)
        val j = i + delta
        if (i < 0 || j !in layout.indices) return
        saveLayout(layout.toMutableList().apply { add(j, removeAt(i)) })
    }

    fun resetLayout() = saveLayout(Block.defaultLayout)

    private fun saveLayout(l: List<Block>) {
        layout = l
        prefs.playerLayout = Block.serialize(l)
    }

    /** Prośba o przejście do ekranu (z widżetu, kafelka). Obsługuje ją nawigacja w MainActivity. */
    var navRequest by mutableStateOf<Screen?>(null)
    private var stopwatchRequested = false

    /** Wywoływane, gdy aplikację otwiera widżet albo kafelek. */
    fun handleOpen(open: String?) {
        when (open) {
            MainActivity.OPEN_PLAYING -> navRequest = Screen.Playing
            MainActivity.OPEN_SESSIONS -> navRequest = Screen.Sessions
            MainActivity.OPEN_STOPWATCH -> {
                // stoper wystartuje, gdy biblioteka i odtwarzacz będą gotowe (patrz tick)
                stopwatchRequested = true
                navRequest = Screen.Sessions
            }
        }
    }

    // ---------------------------------------------------------------- Domyślny odtwarzacz: otwieranie z zewnątrz

    private var pendingExternal: Pair<android.net.Uri, String?>? = null
    private var pendingVoice: String? = null

    /**
     * Plik audio, lista .m3u/.pls, link do strumienia albo polecenie głosowe z innej aplikacji.
     * Zwraca true, jeśli intencja została rozpoznana.
     */
    fun handleIntent(intent: android.content.Intent?): Boolean {
        when (intent?.action) {
            android.content.Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return false
                pendingExternal = uri to intent.type
            }
            MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH -> {
                pendingVoice = intent.getStringExtra(android.app.SearchManager.QUERY).orEmpty()
            }
            else -> return false
        }
        navRequest = Screen.Playing
        processPendingExternal()
        return true
    }

    /** „Otwórz plik audio…” z ustawień (wybór pliku systemowym oknem). */
    fun openExternal(uri: android.net.Uri) {
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
        }
        pendingExternal = uri to null
        navRequest = Screen.Playing
        processPendingExternal()
    }

    /** Wykonuje zaległe otwarcie, gdy odtwarzacz (i dla poleceń głosowych – biblioteka) jest gotowy. */
    private fun processPendingExternal() {
        val c = controller ?: return
        pendingExternal?.let { (uri, mime) ->
            pendingExternal = null
            openExternalNow(c, uri, mime)
        }
        val voice = pendingVoice
        if (voice != null && libraryLoaded) {
            pendingVoice = null
            playFromVoice(voice)
        }
    }

    private fun isPlaylistFile(uri: android.net.Uri, mime: String?): Boolean {
        val m = mime.orEmpty().lowercase()
        val p = (uri.lastPathSegment ?: uri.toString()).lowercase().substringBefore('?')
        return m.contains("mpegurl") || m.contains("scpls") || p.endsWith(".m3u") || p.endsWith(".pls") || p.endsWith(".m3u8")
    }

    private fun displayName(uri: android.net.Uri): String {
        try {
            getApplication<Application>().contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
                if (cur.moveToFirst()) cur.getString(0)?.let { return it.substringBeforeLast('.') }
            }
        } catch (e: Exception) {
        }
        return (uri.lastPathSegment ?: tr("Plik audio", "Audio file")).substringAfterLast('/').substringBeforeLast('.')
    }

    private fun openExternalNow(c: MediaController, uri: android.net.Uri, mime: String?) {
        val scheme = uri.scheme.orEmpty().lowercase()
        // link do strumienia albo do listy radiowej – jak stacja radiowa
        if (scheme == "http" || scheme == "https") {
            playStation(Station(displayName(uri), uri.toString(), ""))
            return
        }
        // lokalna lista .m3u/.pls – bierzemy pierwszy adres strumienia
        if (isPlaylistFile(uri, mime)) {
            viewModelScope.launch {
                val text = withContext(Dispatchers.IO) {
                    try {
                        getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().take(64_000).toByteArray().decodeToString() }
                    } catch (e: Exception) { null }
                }
                val url = text?.let { RadioRepository.parsePlaylist(it) }
                if (url != null) playStation(Station(displayName(uri), url, ""))
                else {
                    playerError = tr("W tej liście nie ma adresu do odtworzenia.", "This playlist has no address to play.")
                    tickNow()
                }
            }
            return
        }
        // plik z biblioteki telefonu – używamy pełnych danych z biblioteki (znaczniki, okładka, wykonawca)
        val libTrack = if (uri.authority == MediaStore.AUTHORITY) {
            try { android.content.ContentUris.parseId(uri) } catch (e: Exception) { -1L }.let { trackById[it] }
        } else null
        if (libTrack != null && !libTrack.isBook) {
            playTracks(listOf(libTrack), 0)
            return
        }
        val name = displayName(uri)
        val isBook = libTrack?.isBook == true || name.lowercase().endsWith(".m4b") || mime == "audio/x-m4b"
        Core.stopwatchDurations = null
        Core.kind = if (isBook) Kind.BOOK else Kind.MUSIC
        Core.bookKey = null
        playerError = null
        queueShuffled = false
        unshuffledIds = null
        val item = MediaItem.Builder()
            .setMediaId("ext:$uri")
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(libTrack?.title ?: name).setArtist(libTrack?.artist).build())
            .build()
        c.setMediaItem(item)
        c.setPlaybackSpeed(if (isBook) prefs.bookSpeed else 1f)
        c.prepare()
        c.play()
        tickNow()
    }

    /** „Odtwórz Kult w Equinox”: najlepsze dopasowanie albo, bez zapytania, wszystko losowo. */
    private fun playFromVoice(query: String) {
        if (query.isBlank()) {
            shuffleAll()
            return
        }
        val results = search(query)
        val first = results.firstOrNull()
        when {
            first == null -> {
                playerError = tr("Nie znalazłem: „$query”.", "Couldn't find “$query”.")
                tickNow()
            }
            first.group != null -> playGroup(first.group)
            first.book != null -> playBook(first.book)
            first.station != null -> playStation(first.station)
            first.playlist != null -> playPlaylist(first.playlist)
            else -> {
                val list = results.mapNotNull { it.track }
                if (list.isNotEmpty()) playTracks(list, 0)
            }
        }
    }

    /** Ekran odświeżamy tylko, gdy aplikacja jest widoczna (oszczędza baterię). */
    var visible = true

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            playerError = when (Core.kind) {
                Kind.RADIO -> tr("Nie mogę połączyć się ze stacją. Sprawdź internet albo wybierz inną.", "Can't connect to the station. Check your internet or pick another one.")
                else -> tr("Nie mogę odtworzyć tego pliku.", "Can't play this file.")
            }
            tickNow()
        }
    }

    init {
        Core.chime = chime
        try {
            app.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
        } catch (e: Exception) {
        }
        cachedStreak = prefs.streak()
        connect()
        radioFavorites = prefs.favorites()
        startTicker()
    }

    private fun connect() {
        val ctx = getApplication<Application>()
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                controller = future.get().also { it.addListener(listener) }
                tickNow()
            } catch (e: Exception) {
                // serwis chwilowo niedostępny – spróbuj ponownie (maks. 5 razy)
                if (connectAttempts++ < 5) viewModelScope.launch { delay(1_500L * connectAttempts); connect() }
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    override fun onCleared() {
        try { getApplication<Application>().contentResolver.unregisterContentObserver(mediaObserver) } catch (e: Exception) {}
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }

    // ---------------------------------------------------------------- Biblioteka

    private fun hasAudioPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(getApplication(), perm) == PackageManager.PERMISSION_GRANTED
    }

    /** Po powrocie do aplikacji: jeśli użytkownik właśnie nadał uprawnienia, wczytaj bibliotekę. */
    // ---------------------------------------------------------------- Oszczędzanie baterii

    /** true = system może ograniczać aplikację w tle (np. zatrzymać timer snu przy zgaszonym ekranie). */
    var batteryRestricted by mutableStateOf(false); private set
    var batteryHintHidden by mutableStateOf(prefs.batteryHintHidden); private set

    private fun checkBattery() {
        val pm = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        batteryRestricted = pm?.isIgnoringBatteryOptimizations(getApplication<Application>().packageName) == false
    }

    fun hideBatteryHint() {
        batteryHintHidden = true
        prefs.batteryHintHidden = true
    }

    fun onVisible() {
        checkBattery()
        val granted = hasAudioPermission()
        if (granted && (!permissionGranted || !libraryLoaded)) loadLibrary()
        permissionGranted = granted
    }

    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(2_000) // kopiowanie wielu plików = wiele zdarzeń; czekamy, aż się uspokoi
            loadLibrary()
        }
    }

    // ---------------------------------------------------------------- Biblioteka: foldery i skanowanie

    /** Wszystkie pliki z telefonu, przed zastosowaniem reguł folderów. */
    private var allTracks: List<Track> = emptyList()
    private var folderRules: Map<String, FolderRule> = prefs.folderRules()
    var folders by mutableStateOf<List<FolderInfo>>(emptyList()); private set

    /** Stan skanowania: null = nie trwa; liczba = ile plików znaleziono dotąd. */
    var scanProgress by mutableStateOf<Int?>(null); private set
    var scanMessage by mutableStateOf<String?>(null); private set

    fun loadLibrary() {
        permissionGranted = hasAudioPermission()
        viewModelScope.launch {
            allTracks = withContext(Dispatchers.IO) { MediaRepository.load(getApplication()) }
            applyLibrary()
            libraryLoaded = true
        }
    }

    /** Szybkie wyszukiwanie utworu po id (playlisty, rozdziały) – liczone raz przy wczytaniu. */
    private var trackById: Map<Long, Track> = emptyMap()

    private fun applyLibrary() {
        trackById = allTracks.associateBy { it.id }
        val visible = Library.applyRules(allTracks, folderRules)
        tracks = visible.filter { !it.isBook }
        books = MediaRepository.groupBooks(visible.filter { it.isBook })
        folders = Library.folders(allTracks, folderRules)
    }

    /** Dotknięcie folderu: Auto → Muzyka → Audiobook → Pomiń → Auto. */
    fun cycleFolderRule(path: String) {
        val current = folderRules[path] ?: FolderRule.AUTO
        val next = current.next()
        folderRules = if (next == FolderRule.AUTO) folderRules - path else folderRules + (path to next)
        prefs.setFolderRules(folderRules)
        applyLibrary()
    }

    fun scanPhone() {
        if (scanProgress != null) return
        val before = allTracks.size
        scanProgress = 0
        scanMessage = null
        viewModelScope.launch {
            val result = try {
                Library.scanPhone(getApplication()) { n -> viewModelScope.launch { scanProgress = n } }
            } catch (e: Exception) {
                null
            }
            allTracks = withContext(Dispatchers.IO) { MediaRepository.load(getApplication()) }
            applyLibrary()
            libraryLoaded = true
            val added = (allTracks.size - before).coerceAtLeast(0)
            scanMessage = when {
                result == null -> tr("Nie udało się przeszukać pamięci telefonu.", "Couldn't search the phone's storage.")
                added > 0 -> tr("Znaleziono ${result.filesFound} plików audio, w tym $added nowych w bibliotece.", "Found ${result.filesFound} audio files, $added of them new in your library.")
                else -> tr("Znaleziono ${result.filesFound} plików audio. Biblioteka jest aktualna.", "Found ${result.filesFound} audio files. Your library is up to date.")
            }
            scanProgress = null
        }
    }

    // ---------------------------------------------------------------- Playlisty

    var playlists by mutableStateOf(prefs.playlists()); private set

    /** Otwarta playlista (ekran szczegółów). */
    var openPlaylistId by mutableStateOf<Long?>(null)

    fun playlist(id: Long?): Playlist? = playlists.firstOrNull { it.id == id }

    /** Utwory playlisty; pliki usunięte z telefonu są pomijane. */
    fun playlistTracks(p: Playlist): List<Track> = p.trackIds.mapNotNull { trackById[it] }

    fun createPlaylist(name: String): Long {
        val id = System.currentTimeMillis()
        savePlaylists(playlists + Playlist(id, name.trim().ifBlank { tr("Nowa playlista", "New playlist") }, emptyList()))
        return id
    }

    fun renamePlaylist(id: Long, name: String) =
        savePlaylists(playlists.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it })

    fun deletePlaylist(id: Long) = savePlaylists(playlists.filter { it.id != id })

    fun isInPlaylist(id: Long, trackId: Long) = playlist(id)?.trackIds?.contains(trackId) == true

    fun addToPlaylist(id: Long, trackId: Long) =
        savePlaylists(playlists.map { if (it.id == id && trackId !in it.trackIds) it.copy(trackIds = it.trackIds + trackId) else it })

    fun removeFromPlaylist(id: Long, trackId: Long) =
        savePlaylists(playlists.map { if (it.id == id) it.copy(trackIds = it.trackIds - trackId) else it })

    fun toggleInPlaylist(id: Long, trackId: Long) =
        if (isInPlaylist(id, trackId)) removeFromPlaylist(id, trackId) else addToPlaylist(id, trackId)

    fun playPlaylist(p: Playlist, start: Int = 0, shuffle: Boolean = false): Boolean {
        val list = playlistTracks(p)
        if (list.isEmpty()) return false
        if (shuffle) playShuffled(list) else playTracks(list, start.coerceIn(0, list.lastIndex))
        return true
    }

    /** Identyfikator utworu, który teraz gra (do „Dodaj do playlisty” z ekranu odtwarzania). */
    fun currentTrackId(): Long? =
        if (Core.kind == Kind.MUSIC) controller?.currentMediaItem?.mediaId?.toLongOrNull() else null

    private fun savePlaylists(list: List<Playlist>) {
        playlists = list
        prefs.setPlaylists(list)
    }

    fun bookProgress(book: Book): Float {
        val total = book.totalMs
        if (total <= 0) return 0f
        val idx = prefs.bookChapter(book.key).coerceIn(0, book.chapters.lastIndex)
        val done = book.chapters.take(idx).sumOf { it.durationMs } + prefs.bookPosition(book.key)
        return (done.toFloat() / total).coerceIn(0f, 1f)
    }

    // ---------------------------------------------------------------- Odtwarzanie

    // ---------------------------------------------------------------- Statystyki

    var showStats by mutableStateOf(prefs.showStats); private set

    fun changeShowStats(on: Boolean) {
        showStats = on
        prefs.showStats = on
    }

    data class StatsUi(
        val hours: LongArray,
        val peakHour: Int,
        val topArtist: String?,
        val topArtistMs: Long,
        val topArtistShare: Float,
        val heard: Int,
        val total: Int,
    )

    fun stats(): StatsUi {
        val hours = io.github.marekmaro.equinox.data.Stats.hours(prefs)
        val artists = io.github.marekmaro.equinox.data.Stats.artistsThisWeek(prefs)
        val top = artists.maxByOrNull { it.value }
        val sum = artists.values.sum().coerceAtLeast(1)
        val counts = Core.playCounts ?: prefs.playCounts().toMutableMap().also { Core.playCounts = it }
        val heard = tracks.count { (counts[it.id] ?: 0) > 0 }
        return StatsUi(
            hours = hours,
            peakHour = io.github.marekmaro.equinox.data.Stats.peakHour(hours),
            topArtist = top?.key,
            topArtistMs = top?.value ?: 0,
            topArtistShare = (top?.value ?: 0).toFloat() / sum,
            heard = heard,
            total = tracks.size,
        )
    }

    /** „Zagraj coś nieodkrytego”: losowo utwory, które jeszcze nigdy nie zagrały. */
    fun playUndiscovered(): Boolean {
        val counts = Core.playCounts ?: emptyMap<Long, Int>()
        val fresh = tracks.filter { (counts[it.id] ?: 0) == 0 }.shuffled()
        if (fresh.isEmpty()) return false
        playTracks(fresh, 0)
        return true
    }

    // ---------------------------------------------------------------- Ustawienia

    var settingsOpen by mutableStateOf(false)

    fun changeLanguage(m: LangMode) {
        Lang.mode = m
        prefs.language = m.name
    }

    fun changeTheme(m: io.github.marekmaro.equinox.ui.ThemeMode) {
        io.github.marekmaro.equinox.ui.ThemeState.mode = m
        prefs.themeMode = m.name
    }

    fun changeKnobSpeed(v: Int) {
        io.github.marekmaro.equinox.ui.KnobSettings.speed = v
        prefs.knobSpeed = v
    }

    fun changeKnobHaptics(on: Boolean) {
        io.github.marekmaro.equinox.ui.KnobSettings.haptics = on
        prefs.knobHaptics = on
    }

    fun showTutorialAgain() {
        tutorialSeen = false
        prefs.tutorialSeen = false
    }

    // ---------------------------------------------------------------- Okładki

    /** Okładka bieżącego utworu (z metadanych pliku), pomniejszona do ~600 px. */
    var artwork by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null); private set
    var showCovers by mutableStateOf(prefs.showCovers); private set
    private var artworkFor: String? = null

    fun changeShowCovers(on: Boolean) {
        showCovers = on
        prefs.showCovers = on
    }

    private fun updateArtwork(c: MediaController) {
        val data = c.mediaMetadata.artworkData
        val key = "${c.currentMediaItem?.mediaId}:${data?.size}"
        if (key == artworkFor) return
        artworkFor = key
        if (data == null) {
            artwork = null
            return
        }
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.Default) { decodeArtwork(data) }
            if (artworkFor == key) artwork = bmp
        }
    }

    private fun decodeArtwork(data: ByteArray): androidx.compose.ui.graphics.ImageBitmap? = try {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 600 && bounds.outHeight / (sample * 2) >= 600) sample *= 2
        android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            ?.asImageBitmap()
    } catch (e: Exception) {
        null
    }

    // ---------------------------------------------------------------- Brzmienie (korektor)

    var soundPreset by mutableStateOf(SoundPreset.entries.firstOrNull { it.name == prefs.soundPreset } ?: SoundPreset.PURE); private set
    var voiceForBooks by mutableStateOf(prefs.voiceForBooks); private set
    val soundAvailable: Boolean get() = PlaybackService.soundAvailable

    fun setSound(p: SoundPreset) {
        soundPreset = p
        Core.soundPreset = p
        prefs.soundPreset = p.name
        PlaybackService.refreshSound()
    }

    fun changeVoiceForBooks(on: Boolean) {
        voiceForBooks = on
        Core.voiceForBooks = on
        prefs.voiceForBooks = on
        PlaybackService.refreshSound()
    }

    // ---------------------------------------------------------------- Ulubione, powtarzanie, losowo

    // ---------------------------------------------------------------- Znaczniki ♥ ★ ☼ ☾

    var marks by mutableStateOf(prefs.marks()); private set

    /** Utwory z sercem (dla zgodności z dotychczasowym kodem). */
    val favoriteIds: Set<Long> get() = marks.filterValues { Mark.HEART in it }.keys

    fun marksOf(id: Long): Set<Mark> = marks[id].orEmpty()

    fun toggleMark(id: Long, mark: Mark) {
        val cur = marksOf(id)
        val next = if (mark in cur) cur - mark else cur + mark
        marks = if (next.isEmpty()) marks - id else marks + (id to next)
        prefs.setMarks(marks)
    }

    fun toggleFavorite(id: Long) = toggleMark(id, Mark.HEART)

    // ---------------------------------------------------------------- Kopia zapasowa

    /** Komunikat po zapisie lub odtworzeniu kopii (pokazywany w ustawieniach). */
    var backupMessage by mutableStateOf<String?>(null)

    private suspend fun libraryRefs(): List<io.github.marekmaro.equinox.data.TrackRef> {
        if (allTracks.isEmpty() && hasAudioPermission()) {
            allTracks = withContext(Dispatchers.IO) { MediaRepository.load(getApplication()) }
            applyLibrary()
            libraryLoaded = true
        }
        return allTracks.map { io.github.marekmaro.equinox.data.TrackRef(it.id, it.path, it.title, it.artist, it.durationMs) }
    }

    fun exportBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            backupMessage = try {
                val refs = libraryRefs()
                if (refs.isEmpty() && (playlists.isNotEmpty() || marks.isNotEmpty())) {
                    backupMessage = tr(
                        "Najpierw zezwól na dostęp do muzyki – bez niego kopia nie zapamięta utworów z playlist.",
                        "Allow access to music first – without it the backup can't remember the songs in your playlists.",
                    )
                    return@launch
                }
                val text = io.github.marekmaro.equinox.data.Backup.export(prefs.all(), refs, java.time.LocalDateTime.now().toString())
                withContext(Dispatchers.IO) {
                    // „w”, nie „wt”: plik jest świeżo utworzony, a np. Dysk Google nie obsługuje trybu „wt”
                    getApplication<Application>().contentResolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray()) }
                        ?: error("no stream")
                }
                tr("Kopia zapisana ✓", "Backup saved ✓")
            } catch (e: Exception) {
                tr("Nie udało się zapisać kopii.", "Couldn't save the backup.")
            }
        }
    }

    fun importBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            backupMessage = try {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { s ->
                        // kopia to zwykle kilkadziesiąt–kilkaset KB
                        val bytes = s.readBytes()
                        String(bytes, Charsets.UTF_8)
                    } ?: error("no stream")
                }
                val refs = libraryRefs()
                if (refs.isEmpty() && io.github.marekmaro.equinox.data.Backup.hasTracks(text)) {
                    backupMessage = tr(
                        "Najpierw zezwól na dostęp do muzyki – inaczej playlisty i znaczniki wróciłyby puste.",
                        "Allow access to music first – otherwise playlists and marks would come back empty.",
                    )
                    return@launch
                }
                val r = withContext(Dispatchers.Default) { io.github.marekmaro.equinox.data.Backup.restore(text, refs) }
                prefs.restore(r.values)
                reloadFromPrefs()
                if (r.tracksMissing == 0) tr("Kopia odtworzona ✓", "Backup restored ✓")
                else tr(
                    "Kopia odtworzona ✓ – nie znaleziono ${r.tracksMissing} utw. na tym telefonie.",
                    "Backup restored ✓ – ${r.tracksMissing} songs weren't found on this phone.",
                )
            } catch (e: Exception) {
                tr("To nie jest kopia Equinox albo plik jest uszkodzony.", "This isn't an Equinox backup or the file is damaged.")
            }
        }
    }

    /** Po odtworzeniu kopii: wczytaj od nowa wszystko, co trzymamy w pamięci. */
    private fun reloadFromPrefs() {
        io.github.marekmaro.equinox.ui.ThemeState.mode =
            io.github.marekmaro.equinox.ui.ThemeMode.entries.firstOrNull { it.name == prefs.themeMode } ?: io.github.marekmaro.equinox.ui.ThemeMode.SYSTEM
        Lang.mode = LangMode.entries.firstOrNull { it.name == prefs.language } ?: LangMode.SYSTEM
        io.github.marekmaro.equinox.ui.KnobSettings.speed = prefs.knobSpeed
        io.github.marekmaro.equinox.ui.KnobSettings.haptics = prefs.knobHaptics
        chime = Chime.entries.firstOrNull { it.name == prefs.chime } ?: Chime.CHIP
        Core.chime = chime
        factsEnabled = prefs.factsEnabled
        layout = Block.parse(prefs.playerLayout)
        radioFavorites = prefs.favorites()
        batteryHintHidden = prefs.batteryHintHidden
        playlists = prefs.playlists()
        showStats = prefs.showStats
        showCovers = prefs.showCovers
        setSound(SoundPreset.entries.firstOrNull { it.name == prefs.soundPreset } ?: SoundPreset.PURE)
        changeVoiceForBooks(prefs.voiceForBooks)
        marks = prefs.marks()
        bookmarks = prefs.bookmarks()
        changeSkipSilence(prefs.skipSilence)
        changeReplayGain(ReplayGainMode.entries.firstOrNull { it.name == prefs.replayGain } ?: ReplayGainMode.AUTO)
        changeCrossfade(prefs.crossfadeSec)
        smartShuffleOn = prefs.smartShuffle
        recentSearches = prefs.recentSearches()
        tutorialSeen = prefs.tutorialSeen
        Core.playCounts = prefs.playCounts().toMutableMap()
        Core.recentPlayed = ArrayDeque(prefs.recentPlayed())
        Core.playCountsDirty = false
        cachedStreak = prefs.streak()
        folderRules = prefs.folderRules()
        applyLibrary()
        tickNow()
    }

    // ---------------------------------------------------------------- Zakładki w audiobookach

    var bookmarks by mutableStateOf(prefs.bookmarks()); private set

    /** Zakładki książki, która teraz gra (w kolejności czytania). */
    val currentBookmarks: List<io.github.marekmaro.equinox.data.Bookmark>
        get() = if (nowPlaying.kind == Kind.BOOK) io.github.marekmaro.equinox.data.Bookmarks.forBook(bookmarks, Core.bookKey) else emptyList()

    /** Miejsce do zapisania w zakładce – pobierane w chwili dotknięcia przycisku, zanim wpiszesz notatkę. */
    data class BookmarkDraft(val bookKey: String, val itemIndex: Int, val positionMs: Long, val label: String)

    fun bookmarkDraft(): BookmarkDraft? {
        val c = controller ?: return null
        val key = Core.bookKey ?: return null
        if (Core.kind != Kind.BOOK) return null
        val pos = c.currentPosition.coerceAtLeast(0)
        val label = nowPlaying.chapter ?: c.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
        return BookmarkDraft(key, c.currentMediaItemIndex, pos, label)
    }

    fun addBookmark(d: BookmarkDraft, note: String) {
        val now = System.currentTimeMillis()
        val b = io.github.marekmaro.equinox.data.Bookmark(now, d.bookKey, d.itemIndex, d.positionMs, d.label, note.trim().take(200), now)
        bookmarks = io.github.marekmaro.equinox.data.Bookmarks.add(bookmarks, b)
        prefs.setBookmarks(bookmarks)
    }

    fun removeBookmark(id: Long) {
        bookmarks = bookmarks.filterNot { it.id == id }
        prefs.setBookmarks(bookmarks)
    }

    /** Skok do zakładki: w grającej książce przewija, inną książkę włącza od tego miejsca. */
    fun openBookmark(b: io.github.marekmaro.equinox.data.Bookmark) {
        val c = controller ?: return
        if (Core.kind == Kind.BOOK && Core.bookKey == b.bookKey && b.itemIndex < c.mediaItemCount) {
            c.seekTo(b.itemIndex, b.positionMs)
            if (!c.isPlaying) c.play()
        } else {
            books.firstOrNull { it.key == b.bookKey }?.let { playBook(it, b.itemIndex, b.positionMs) }
        }
        tickNow()
    }

    // ---------------------------------------------------------------- Wyrównanie głośności

    var replayGain by mutableStateOf(ReplayGainMode.entries.firstOrNull { it.name == prefs.replayGain } ?: ReplayGainMode.AUTO); private set

    fun changeReplayGain(m: ReplayGainMode) {
        replayGain = m
        Core.replayGain = m
        prefs.replayGain = m.name
        PlaybackService.refreshSound()
    }

    // ---------------------------------------------------------------- Audiobooki: pomijanie ciszy

    var skipSilence by mutableStateOf(prefs.skipSilence); private set

    fun changeSkipSilence(on: Boolean) {
        skipSilence = on
        Core.skipSilence = on
        prefs.skipSilence = on
        PlaybackService.refreshSound()
    }

    // ---------------------------------------------------------------- Płynne przejścia

    var crossfadeSec by mutableStateOf(prefs.crossfadeSec); private set

    fun changeCrossfade(sec: Int) {
        crossfadeSec = sec
        Core.crossfadeSec = sec
        prefs.crossfadeSec = sec
    }

    // ---------------------------------------------------------------- Mądre losowanie

    /** true = mądre losowanie (domyślnie), false = zwykłe. */
    var smartShuffleOn by mutableStateOf(prefs.smartShuffle); private set

    fun changeSmartShuffle(on: Boolean) {
        smartShuffleOn = on
        prefs.smartShuffle = on
    }

    /** Czy kolejka jest teraz przemieszana (przez nas – bez przerywania utworu). */
    private var queueShuffled: Boolean
        get() = Core.queueShuffled
        set(v) {
            if (Core.queueShuffled == v) return
            Core.queueShuffled = v
            PlaybackService.refreshSound() // wyrównanie głośności w trybie Auto zależy od losowania
        }
    /** Kolejność przed przemieszaniem (id pozycji) – do przywrócenia po wyłączeniu. */
    private var unshuffledIds: List<String>? = null

    /** Kolejność losowa: mądra (wykonawcy rozłożeni, niedawne na koniec, ♥★ wcześniej) albo zwykła. */
    fun orderShuffled(list: List<Track>): List<Track> {
        if (!smartShuffleOn) return list.shuffled()
        val recent = (Core.recentPlayed ?: ArrayDeque(prefs.recentPlayed()).also { Core.recentPlayed = it })
            .withIndex().associate { (i, id) -> id to i }
        val byId = list.associateBy { it.id }
        return smartShuffle(list.map { ShuffleItem(it.id, it.artist, it.album, shuffleWeight(marks[it.id], recent[it.id])) })
            .mapNotNull { byId[it.id] }
    }

    /**
     * Losowo włącz/wyłącz w trakcie słuchania: bieżący utwór gra dalej,
     * przestawiamy tylko resztę kolejki; wyłączenie przywraca pierwotną kolejność.
     */
    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = false // kolejnością zarządzamy sami
        val n = c.mediaItemCount
        if (n < 2) { queueShuffled = !queueShuffled; tickNow(); return }
        val cur = c.currentMediaItemIndex
        val items = (0 until n).map { c.getMediaItemAt(it) }
        val current = items[cur]
        val others = items.filterIndexed { i, _ -> i != cur }
        if (!queueShuffled) {
            unshuffledIds = items.map { it.mediaId }
            val tracksOf = others.mapNotNull { it.mediaId.toLongOrNull()?.let { id -> trackById[id] } }
            val ordered = orderShuffled(tracksOf).map { it.toMediaItem() }
            val known = ordered.map { it.mediaId }.toSet()
            val rest = ordered + others.filter { it.mediaId !in known }.shuffled() // np. pozycje spoza biblioteki
            replaceAround(c, cur, n, emptyList(), rest)
            queueShuffled = true
        } else {
            val original = unshuffledIds
            if (original != null) {
                val byId = items.associateBy { it.mediaId }
                val pos = original.indexOf(current.mediaId)
                if (pos >= 0) {
                    val before = original.take(pos).mapNotNull { byId[it] }
                    val after = original.drop(pos + 1).mapNotNull { byId[it] }
                    replaceAround(c, cur, n, before, after)
                }
            }
            queueShuffled = false
            unshuffledIds = null
        }
        tickNow()
    }

    /** Zostawia bieżącą pozycję i zastępuje wszystko przed nią i po niej (bez przerwy w dźwięku). */
    private fun replaceAround(c: MediaController, cur: Int, n: Int, before: List<MediaItem>, after: List<MediaItem>) {
        if (cur + 1 < n) c.removeMediaItems(cur + 1, n)
        if (cur > 0) c.removeMediaItems(0, cur)
        if (before.isNotEmpty()) c.addMediaItems(0, before)
        if (after.isNotEmpty()) c.addMediaItems(after)
    }

    /** Wyłączone → całość → jeden utwór → wyłączone. */
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
        tickNow()
    }

    // ---------------------------------------------------------------- Kolejka

    fun track(id: Long): Track? = trackById[id]

    /** „Zagraj jako następny” – wstawia utwór tuż po bieżącym (albo gra od razu, gdy nic nie gra). */
    fun playNext(t: Track) {
        val c = controller ?: return
        if (Core.kind == Kind.MUSIC && c.mediaItemCount > 0) c.addMediaItem(c.currentMediaItemIndex + 1, t.toMediaItem())
        else playTracks(listOf(t), 0)
    }

    /** „Dodaj do kolejki” – na koniec bieżącej kolejki. */
    fun addToQueue(t: Track) {
        val c = controller ?: return
        if (Core.kind == Kind.MUSIC && c.mediaItemCount > 0) c.addMediaItem(t.toMediaItem())
        else playTracks(listOf(t), 0)
    }

    // ---------------------------------------------------------------- Przeglądanie: wykonawcy, albumy, foldery, automatyczne

    var openGroup by mutableStateOf<TrackGroup?>(null)
    var groupListType by mutableStateOf(GroupType.ARTIST)

    fun groups(type: GroupType): List<TrackGroup> = when (type) {
        GroupType.ARTIST -> tracks.groupBy { it.artist }.map { (a, l) -> TrackGroup(type, a, a, l.size) }
        GroupType.ALBUM -> tracks.filter { it.album.isNotBlank() }.groupBy { it.album }
            .map { (a, l) -> TrackGroup(type, a, a, l.size) }
        GroupType.FOLDER -> tracks.groupBy { it.folder }
            .map { (f, l) -> TrackGroup(type, f, f.substringAfterLast('/').ifBlank { f }, l.size) }
        else -> emptyList()
    }.sortedBy { it.title.lowercase() }

    /** Playlisty automatyczne: Ulubione, Ostatnio dodane, Najczęściej słuchane. */
    /** Playlisty automatyczne: po jednej na znacznik, Nowości i Najczęściej słuchane. */
    fun smartGroups(): List<TrackGroup> =
        Mark.entries.map { m -> TrackGroup(GroupType.MARK, m.name, "${m.glyph} ${m.label}", groupTracks(GroupType.MARK, m.name).size) } +
            listOf(
                TrackGroup(GroupType.NEW, "new", tr("✦ Nowości", "✦ New"), groupTracks(GroupType.NEW, "").size),
                TrackGroup(GroupType.TOP, "top", tr("Najczęściej słuchane", "Most played"), groupTracks(GroupType.TOP, "").size),
            )

    /** Utwór „nowy” = trafił na telefon w ostatnich 14 dniach. */
    fun isNew(t: Track): Boolean = t.dateAdded > System.currentTimeMillis() / 1000 - 14 * 24 * 3600

    /** Nowości: dodane w ostatnich 30 dniach (najnowsze najpierw). */
    fun newTracks(): List<Track> {
        val since = System.currentTimeMillis() / 1000 - 30 * 24 * 3600
        return tracks.filter { it.dateAdded > since }.sortedByDescending { it.dateAdded }
    }

    fun groupTracks(type: GroupType, key: String): List<Track> = when (type) {
        GroupType.ARTIST -> tracks.filter { it.artist == key }.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNo }, { it.title.lowercase() }))
        GroupType.ALBUM -> tracks.filter { it.album == key }.sortedWith(compareBy({ it.trackNo }, { it.title.lowercase() }))
        GroupType.FOLDER -> tracks.filter { it.folder == key }.sortedBy { it.title.lowercase() }
        GroupType.MARK -> {
            val m = Mark.entries.firstOrNull { it.name == key }
            tracks.filter { m != null && m in marksOf(it.id) }
        }
        GroupType.NEW -> newTracks()
        GroupType.TOP -> {
            val counts = Core.playCounts ?: prefs.playCounts().toMutableMap().also { Core.playCounts = it }
            tracks.filter { (counts[it.id] ?: 0) > 0 }.sortedByDescending { counts[it.id] ?: 0 }.take(50)
        }
    }

    fun playGroup(g: TrackGroup, start: Int = 0, shuffle: Boolean = false): Boolean {
        val list = groupTracks(g.type, g.key)
        if (list.isEmpty()) return false
        if (shuffle) playShuffled(list) else playTracks(list, start.coerceIn(0, list.lastIndex))
        return true
    }

    /** Gra listę losowo; wyłączenie losowania przywróci jej pierwotną kolejność. */
    fun playShuffled(list: List<Track>) {
        val ordered = orderShuffled(list)
        playTracks(ordered, 0)
        unshuffledIds = list.map { it.id.toString() }
        queueShuffled = true
    }

    // ---------------------------------------------------------------- Wyszukiwanie

    /**
     * Szuka wszędzie: wykonawcy, albumy, utwory, audiobooki, playlisty i ulubione stacje.
     * Bez polskich znaków („burza” znajdzie „burzą”), każde słowo zapytania musi pasować.
     */
    fun search(query: String): List<SearchResult> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val artistHits = groups(GroupType.ARTIST).filter { matchesQuery(it.title, q) }.take(10)
            .map { SearchResult(it.title, tr("wykonawca", "artist"), group = it) }
        val albumHits = groups(GroupType.ALBUM).filter { matchesQuery(it.title, q) }.take(10)
            .map { SearchResult(it.title, tr("album", "album"), group = it) }
        val bookHits = books.filter { matchesQuery(it.title + " " + it.author, q) }.take(10)
            .map { SearchResult(it.title, tr("audiobook", "audiobook"), book = it) }
        val playlistHits = playlists.filter { matchesQuery(it.name, q) }.take(10)
            .map { SearchResult(it.name, tr("playlista", "playlist"), playlist = it) }
        val stationHits = (radioFavorites + radioResults).distinctBy { it.url }.filter { matchesQuery(it.name, q) }.take(10)
            .map { SearchResult(it.name, "radio", station = it) }
        val trackHits = tracks.filter { matchesQuery("${it.title} ${it.artist} ${it.album}", q) }
            .take(100).map { SearchResult(it.title, (marksGlyphs(marks[it.id]) + " " + it.artist).trim().take(22), track = it) }
        return artistHits + albumHits + bookHits + playlistHits + stationHits + trackHits
    }

    var recentSearches by mutableStateOf(prefs.recentSearches()); private set

    /** Zapamiętuje zapytanie, po którym coś wybrano (najnowsze na górze). */
    fun rememberSearch(q: String) {
        val t = q.trim()
        if (t.length < 2) return
        recentSearches = (listOf(t) + recentSearches.filter { !it.equals(t, ignoreCase = true) }).take(8)
        prefs.setRecentSearches(recentSearches)
    }

    fun clearRecentSearches() {
        recentSearches = emptyList()
        prefs.setRecentSearches(emptyList())
    }

    // ---------------------------------------------------------------- Wznawianie ostatniej sesji

    private var restoreChecked = false

    /**
     * Po uruchomieniu aplikacji: jeśli odtwarzacz jest pusty, wczytujemy ostatnią sesję
     * (bez włączania dźwięku) – „Słuchaj dalej” od razu pokazuje, gdzie skończyłeś.
     */
    private fun restoreSessionIfNeeded(c: MediaController) {
        if (restoreChecked || !libraryLoaded) return
        restoreChecked = true
        if (c.mediaItemCount > 0) return
        val saved = SessionStore.decode(prefs.lastSession) ?: return
        val items = mutableListOf<MediaItem>()
        var index = 0
        saved.ids.forEachIndexed { i, id ->
            val item = when {
                id.startsWith("ext:") -> MediaItem.Builder().setMediaId(id).setUri(id.removePrefix("ext:"))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(saved.titles.getOrNull(i)).setArtist(saved.artists.getOrNull(i)).build())
                    .build()
                id.startsWith("radio:") -> MediaItem.Builder().setMediaId(id).setUri(id.removePrefix("radio:"))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(saved.titles.getOrNull(i)).setArtist(saved.artists.getOrNull(i)).build())
                    .build()
                else -> id.toLongOrNull()?.let { trackById[it] }?.toMediaItem()
            }
            if (item != null) {
                if (i == saved.index) index = items.size
                items += item
            }
        }
        if (items.isEmpty()) return
        Core.kind = saved.kind
        Core.bookKey = saved.bookKey
        c.setMediaItems(items, index, saved.positionMs)
        c.shuffleModeEnabled = saved.shuffle
        c.repeatMode = saved.repeat
        if (saved.kind == Kind.BOOK) c.setPlaybackSpeed(prefs.bookSpeed)
        c.prepare()
        tickNow()
    }

    fun shuffleAll(): Boolean {
        if (tracks.isEmpty()) return false
        playShuffled(tracks)
        return true
    }

    fun playTracks(list: List<Track>, start: Int) {
        val c = controller ?: return
        Core.stopwatchDurations = null
        Core.kind = Kind.MUSIC
        Core.bookKey = null
        playerError = null
        queueShuffled = false
        unshuffledIds = null
        c.shuffleModeEnabled = false
        c.setMediaItems(list.map { it.toMediaItem() }, start, 0L)
        c.setPlaybackSpeed(1f)
        c.prepare()
        c.play()
    }

    /**
     * Gra audiobooka od zapamiętanego miejsca (cofniętego o 5 s, żeby łatwiej złapać wątek)
     * albo od wskazanego – np. z zakładki.
     */
    fun playBook(book: Book, startIndex: Int? = null, startMs: Long? = null) {
        val c = controller ?: return
        Core.stopwatchDurations = null
        Core.kind = Kind.BOOK
        Core.bookKey = book.key
        playerError = null
        val idx = (startIndex ?: prefs.bookChapter(book.key)).coerceIn(0, book.chapters.lastIndex)
        val pos = startMs ?: (prefs.bookPosition(book.key) - 5_000).coerceAtLeast(0)
        c.setMediaItems(book.chapters.map { it.toMediaItem() }, idx, pos)
        c.setPlaybackSpeed(prefs.bookSpeed)
        c.prepare()
        c.play()
    }

    fun playStation(s: Station) {
        // linki .pls / .m3u to listy – najpierw wyciągamy z nich adres strumienia
        if (RadioRepository.isPlaylistLink(s.url)) {
            viewModelScope.launch {
                val resolved = try { RadioRepository.resolvePlaylist(s.url) } catch (e: Exception) { null }
                if (resolved != null) playStationNow(s.copy(url = resolved))
                else {
                    playerError = tr("Ten link nie zawiera adresu stacji.", "This link doesn't contain a station address.")
                    tickNow()
                }
            }
            return
        }
        playStationNow(s)
    }

    private fun playStationNow(s: Station) {
        val c = controller ?: return
        Core.stopwatchDurations = null
        Core.kind = Kind.RADIO
        Core.bookKey = null
        playerError = null
        val item = MediaItem.Builder()
            .setMediaId("radio:${s.url}")
            .setUri(s.url)
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(s.name).setArtist(s.country.ifBlank { "Radio" }).build()
            )
            .build()
        c.setMediaItem(item)
        c.setPlaybackSpeed(1f)
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0L)
            c.play()
        }
    }

    /** Następny: w audiobooku z rozdziałami w jednym pliku – następny rozdział. */
    fun next() {
        val c = controller ?: return
        val ch = currentChapters()
        if (ch.isNotEmpty()) {
            val i = Chapters.indexAt(ch, c.currentPosition)
            if (i < ch.lastIndex) { c.seekTo(ch[i + 1].startMs); return }
        }
        c.seekToNext()
    }

    /** Poprzedni: na początek rozdziału, a gdy już tam jesteśmy (do 3 s) – rozdział wcześniej. */
    fun previous() {
        val c = controller ?: return
        val ch = currentChapters()
        if (ch.isNotEmpty()) {
            val i = Chapters.indexAt(ch, c.currentPosition)
            when {
                c.currentPosition - ch[i].startMs > 3_000 -> { c.seekTo(ch[i].startMs); return }
                i > 0 -> { c.seekTo(ch[i - 1].startMs); return }
            }
        }
        c.seekToPrevious()
    }

    private fun currentChapters(): List<io.github.marekmaro.equinox.data.Chapter> {
        val id = controller?.currentMediaItem?.mediaId
        return if (Core.kind == Kind.BOOK && id != null && id == Core.chaptersFor) Core.chapters else emptyList()
    }

    /** Wczytuje rozdziały pliku audiobooka w tle (raz na plik). */
    private fun loadChaptersIfNeeded(mediaId: String?) {
        if (Core.kind != Kind.BOOK || mediaId == null || mediaId == Core.chaptersFor) return
        Core.chaptersFor = mediaId
        Core.chapters = emptyList()
        val uri = mediaId.toLongOrNull()?.let { trackById[it]?.uri } ?: return
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { Chapters.read(getApplication(), uri) }
            if (Core.chaptersFor == mediaId) Core.chapters = found
        }
    }

    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        if (Core.kind == Kind.RADIO) return
        val dur = c.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        c.seekTo((c.currentPosition + deltaMs).coerceIn(0L, dur))
    }

    /** Jeden krok pierścienia, zależnie od wybranego trybu. */
    /** Przy radiu nie da się przewijać, więc pierścień działa wtedy jako głośność. */
    val effectiveRingMode: RingMode
        get() = if (nowPlaying.kind == Kind.RADIO && ringMode != RingMode.VOLUME) RingMode.VOLUME else ringMode

    fun ringStep(d: Int) {
        when (effectiveRingMode) {
            RingMode.SEEK -> seekBy(d * 10_000L)
            RingMode.TRACKS -> if (d > 0) controller?.seekToNextMediaItem() else controller?.seekToPreviousMediaItem()
            RingMode.VOLUME -> repeat(kotlin.math.abs(d)) { adjustVolume(d) }
        }
    }

    /** Przytrzymanie środka tarczy: Przewiń → Utwory → Głośność. */
    fun cycleRingMode() {
        val modes = if (nowPlaying.kind == Kind.RADIO) listOf(RingMode.VOLUME) else RingMode.entries
        ringMode = modes[(modes.indexOf(effectiveRingMode) + 1) % modes.size]
    }

    // Samouczek sterowania kółkiem: pokazywany raz, potem pod przyciskiem „?”
    var tutorialSeen by mutableStateOf(prefs.tutorialSeen); private set

    fun markTutorialSeen() {
        tutorialSeen = true
        prefs.tutorialSeen = true
    }

    fun adjustVolume(d: Int) {
        val am = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (d > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI,
        )
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        if (Core.kind == Kind.BOOK) prefs.bookSpeed = speed
    }

    // ---------------------------------------------------------------- Sesje

    fun setSleep(minutes: Int) {
        Core.sleepAfterItem = false
        Core.sleepEndAt = System.currentTimeMillis() + minutes * 60_000L
        controller?.volume = 1f
        tickNow()
    }

    fun setSleepAfterItem() {
        Core.sleepEndAt = null
        Core.sleepAfterItem = true
        controller?.volume = 1f
        tickNow()
    }

    fun cancelSleep() {
        Core.sleepEndAt = null
        Core.sleepAfterItem = false
        controller?.volume = 1f
        tickNow()
    }

    fun changeStopwatch(deltaMinutes: Int) {
        Core.stopwatchMinutes = (Core.stopwatchMinutes + deltaMinutes).coerceIn(5, 180)
        tickNow()
    }

    fun changeGoal(deltaMinutes: Int) {
        prefs.goalMinutes = (prefs.goalMinutes + deltaMinutes).coerceIn(5, 180)
        tickNow()
    }

    /**
     * Stoper muzyczny: układa utwory tak, by razem trwały możliwie dokładnie
     * zadany czas. Kiedy muzyka ucichnie, czas minął.
     */
    fun startStopwatch(): Boolean {
        val target = Core.stopwatchMinutes * 60_000L
        val idx = pickForDuration(tracks.map { it.durationMs }, target)
        if (idx.isEmpty()) return false
        val picked = idx.map { tracks[it] }
        val total = picked.sumOf { it.durationMs }
        playTracks(picked, 0)
        Core.stopwatchDurations = picked.map { it.durationMs }
        Core.stopwatchDone = false
        Core.stopwatchInfo = tr("${picked.size} utw. · ${fmt(total)}", "${picked.size} songs · ${fmt(total)}")
        StopwatchTileService.requestRefresh(getApplication())
        tickNow()
        return true
    }

    fun stopStopwatch() {
        Core.stopwatchDurations = null
        Core.stopwatchDone = false
        Core.stopwatchInfo = null
        StopwatchTileService.requestRefresh(getApplication())
        tickNow()
    }

    // ---------------------------------------------------------------- Radio

    fun searchRadio(query: String) {
        viewModelScope.launch {
            radioLoading = true
            radioError = null
            try {
                radioResults = RadioRepository.search(query)
            } catch (e: Exception) {
                radioError = tr("Nie udało się pobrać stacji. Sprawdź internet.", "Couldn't load stations. Check your internet.")
            }
            radioLoading = false
        }
    }

    fun isFavorite(s: Station) = radioFavorites.any { it.url == s.url }

    fun toggleFavorite(s: Station) {
        radioFavorites = if (isFavorite(s)) radioFavorites.filter { it.url != s.url } else radioFavorites + s
        prefs.setFavorites(radioFavorites)
    }

    fun addCustomStation(name: String, url: String): Boolean {
        val u = url.trim()
        if (!(u.startsWith("http://") || u.startsWith("https://"))) return false
        val s = Station(name.trim().ifBlank { tr("Moja stacja", "My station") }, u, "")
        if (!isFavorite(s)) {
            radioFavorites = radioFavorites + s
            prefs.setFavorites(radioFavorites)
        }
        return true
    }

    // ---------------------------------------------------------------- Ciekawostki

    fun setFactsOn(on: Boolean) {
        prefs.factsEnabled = on
        factsEnabled = on
        factsKey = null
        factsJob?.cancel()
        facts = null
        if (on) updateFacts(nowPlaying)
    }

    /** Wywoływane co tick: gdy zmienił się wykonawca/autor, szukamy ciekawostek. */
    private fun updateFacts(np: NowPlaying) {
        if (!factsEnabled) return
        val kind = np.kind
        val isBook = kind == Kind.BOOK
        val name = if (kind == Kind.MUSIC || isBook) FactsRepository.primaryName(np.subtitle) else ""
        val key = if (name.isBlank() || name == tr("Nieznany wykonawca", "Unknown artist")) "" else "${io.github.marekmaro.equinox.Lang.code}:${if (isBook) "b" else "m"}:${name.lowercase()}"
        if (key == factsKey) return

        factsKey = key
        factsJob?.cancel()
        facts = null
        if (key.isEmpty() || key in factsMisses) return

        prefs.cachedFacts(key)?.let {
            facts = it
            return
        }
        factsJob = viewModelScope.launch {
            try {
                val found = FactsRepository.find(name, isBook)
                if (factsKey != key) return@launch
                if (found != null) {
                    facts = found
                    prefs.cacheFacts(key, found)
                } else {
                    factsMisses += key // nie znaleziono – nie pytamy ponownie w tej sesji
                }
            } catch (e: Exception) {
                // brak internetu: spróbuj ponownie za 30 s (jeśli wciąż gra ten sam wykonawca)
                delay(30_000)
                if (factsKey == key) factsKey = null
            }
        }
    }

    // ---------------------------------------------------------------- Zegar

    private fun startTicker() {
        viewModelScope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(500)
                val now = SystemClock.elapsedRealtime()
                if (visible) tick(now - last)
                last = now
            }
        }
    }

    private fun tickNow() = tick(0)

    private fun tick(@Suppress("UNUSED_PARAMETER") dtMs: Long) {
        if (stopwatchRequested && controller != null && libraryLoaded) {
            stopwatchRequested = false
            if (startStopwatch()) navRequest = Screen.Playing
        }
        val c = controller
        if (c != null) {
            val item = c.currentMediaItem
            val md = item?.mediaMetadata
            val dur = c.duration
            loadChaptersIfNeeded(item?.mediaId)
            val ch = currentChapters()
            val chapterLabel = if (ch.isEmpty()) null else {
                val i = Chapters.indexAt(ch, c.currentPosition)
                tr("Rozdział ${i + 1}/${ch.size} · ${ch[i].title}", "Chapter ${i + 1}/${ch.size} · ${ch[i].title}")
            }
            nowPlaying = NowPlaying(
                title = md?.title?.toString().orEmpty(),
                subtitle = md?.artist?.toString().orEmpty(),
                isPlaying = c.isPlaying,
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = if (dur == C.TIME_UNSET || dur < 0) 0 else dur,
                kind = if (item == null) null else (Core.kind ?: Kind.MUSIC),
                speed = c.playbackParameters.speed,
                error = playerError,
                chapter = chapterLabel,
                shuffle = queueShuffled || c.shuffleModeEnabled,
                repeat = c.repeatMode,
                trackId = if (Core.kind == Kind.MUSIC || Core.kind == null) item?.mediaId?.toLongOrNull() else null,
            )
            processPendingExternal()
            restoreSessionIfNeeded(c)
            updateArtwork(c)
            if (c.isPlaying) playerError = null
            updateFacts(nowPlaying)
            upNext = if (Block.QUEUE in layout) nextTitles(c) else emptyList()
        }
        // seria dni zmienia się rzadko – liczymy ją co ~10 s zamiast co pół sekundy
        if (tickCount++ % 20 == 0) cachedStreak = prefs.streak()

        // Stoper muzyczny: ile zostało do końca ułożonej muzyki
        var swRemaining: Long? = null
        val durations = Core.stopwatchDurations
        if (durations != null && c != null && durations.isNotEmpty()) {
            val idx = c.currentMediaItemIndex.coerceIn(0, durations.lastIndex)
            swRemaining = (durations.drop(idx).sum() - c.currentPosition).coerceAtLeast(0)
        }

        val today = LocalDate.now()
        sessions = sessions.copy(
            sleepRemainingMs = Core.sleepEndAt?.let { (it - System.currentTimeMillis()).coerceAtLeast(0) },
            sleepAfterItem = Core.sleepAfterItem,
            todayMs = prefs.listened(today) + Core.pendingListenMs,
            goalMinutes = prefs.goalMinutes,
            streak = cachedStreak,
            stopwatchMinutes = Core.stopwatchMinutes,
            stopwatchRemainingMs = swRemaining,
            stopwatchDone = Core.stopwatchDone,
            stopwatchInfo = Core.stopwatchInfo,
        )
    }

    private fun nextTitles(c: MediaController): List<String> {
        val timeline = c.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val out = mutableListOf<String>()
        var i = c.currentMediaItemIndex
        while (out.size < 3) {
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
            if (i == C.INDEX_UNSET) break
            out += c.getMediaItemAt(i).mediaMetadata.title?.toString().orEmpty().ifBlank { tr("Bez tytułu", "Untitled") }
        }
        return out
    }

    /** Sygnał końca stopera: wybrana melodia (zob. [Chime]). */
    private fun playEndSignal() = viewModelScope.playChime(chime)

    /** Odsłuchanie sygnału z ekranu Sesje. */
    fun previewEndSignal() = playEndSignal()

    fun selectChime(c: Chime) {
        chime = c
        Core.chime = c
        prefs.chime = c.name
        playEndSignal()
    }
}
