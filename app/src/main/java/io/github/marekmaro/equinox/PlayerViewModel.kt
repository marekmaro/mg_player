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
enum class RingMode(val label: String, val stepDegrees: Float, val hint: String) {
    SEEK("Przewiń", 15f, "kręć: ±10 s"),
    TRACKS("Utwory", 60f, "kręć: następny / poprzedni"),
    VOLUME("Głośność", 20f, "kręć: głośniej / ciszej"),
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

    /** Ekran odświeżamy tylko, gdy aplikacja jest widoczna (oszczędza baterię). */
    var visible = true

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            playerError = when (Core.kind) {
                Kind.RADIO -> "Nie mogę połączyć się ze stacją. Sprawdź internet albo wybierz inną."
                else -> "Nie mogę odtworzyć tego pliku."
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
    fun onVisible() {
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

    private fun applyLibrary() {
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
                result == null -> "Nie udało się przeszukać pamięci telefonu."
                added > 0 -> "Znaleziono ${result.filesFound} plików audio, w tym $added nowych w bibliotece."
                else -> "Znaleziono ${result.filesFound} plików audio. Biblioteka jest aktualna."
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
    fun playlistTracks(p: Playlist): List<Track> {
        val byId = allTracks.associateBy { it.id }
        return p.trackIds.mapNotNull { byId[it] }
    }

    fun createPlaylist(name: String): Long {
        val id = System.currentTimeMillis()
        savePlaylists(playlists + Playlist(id, name.trim().ifBlank { "Nowa playlista" }, emptyList()))
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
        if (shuffle) playTracks(list.shuffled(), 0) else playTracks(list, start.coerceIn(0, list.lastIndex))
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

    fun shuffleAll(): Boolean {
        if (tracks.isEmpty()) return false
        playTracks(tracks.shuffled(), 0)
        return true
    }

    fun playTracks(list: List<Track>, start: Int) {
        val c = controller ?: return
        Core.stopwatchDurations = null
        Core.kind = Kind.MUSIC
        Core.bookKey = null
        playerError = null
        c.setMediaItems(list.map { it.toMediaItem() }, start, 0L)
        c.setPlaybackSpeed(1f)
        c.prepare()
        c.play()
    }

    fun playBook(book: Book) {
        val c = controller ?: return
        Core.stopwatchDurations = null
        Core.kind = Kind.BOOK
        Core.bookKey = book.key
        playerError = null
        val idx = prefs.bookChapter(book.key).coerceIn(0, book.chapters.lastIndex)
        // cofamy o 5 s, żeby łatwiej złapać wątek
        val pos = (prefs.bookPosition(book.key) - 5_000).coerceAtLeast(0)
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
                    playerError = "Ten link nie zawiera adresu stacji."
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

    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }

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
        Core.stopwatchInfo = "${picked.size} utw. · ${fmt(total)}"
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
                radioError = "Nie udało się pobrać stacji. Sprawdź internet."
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
        val s = Station(name.trim().ifBlank { "Moja stacja" }, u, "")
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
        val key = if (name.isBlank() || name == "Nieznany wykonawca") "" else "${if (isBook) "b" else "m"}:${name.lowercase()}"
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
            nowPlaying = NowPlaying(
                title = md?.title?.toString().orEmpty(),
                subtitle = md?.artist?.toString().orEmpty(),
                isPlaying = c.isPlaying,
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = if (dur == C.TIME_UNSET || dur < 0) 0 else dur,
                kind = if (item == null) null else (Core.kind ?: Kind.MUSIC),
                speed = c.playbackParameters.speed,
                error = playerError,
            )
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
            out += c.getMediaItemAt(i).mediaMetadata.title?.toString().orEmpty().ifBlank { "Bez tytułu" }
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
