package io.github.marekmaro.sundial

import android.app.Application
import android.content.ComponentName
import android.content.Context
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
import io.github.marekmaro.sundial.data.Book
import io.github.marekmaro.sundial.data.Facts
import io.github.marekmaro.sundial.data.FactsRepository
import io.github.marekmaro.sundial.data.MediaRepository
import io.github.marekmaro.sundial.data.Prefs
import io.github.marekmaro.sundial.data.RadioRepository
import io.github.marekmaro.sundial.data.Station
import io.github.marekmaro.sundial.data.Track
import io.github.marekmaro.sundial.data.toMediaItem
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
                // serwis niedostępny – spróbujemy przy następnym uruchomieniu
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }

    // ---------------------------------------------------------------- Biblioteka

    fun loadLibrary() {
        viewModelScope.launch {
            val all = withContext(Dispatchers.IO) { MediaRepository.load(getApplication()) }
            tracks = all.filter { !it.isBook }
            books = MediaRepository.groupBooks(all.filter { it.isBook })
            libraryLoaded = true
        }
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
            RingMode.VOLUME -> adjustVolume(d)
        }
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
            updateFacts(nowPlaying)
        }

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
            streak = prefs.streak(),
            stopwatchMinutes = Core.stopwatchMinutes,
            stopwatchRemainingMs = swRemaining,
            stopwatchDone = Core.stopwatchDone,
            stopwatchInfo = Core.stopwatchInfo,
        )
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
