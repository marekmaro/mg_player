package pl.mgplayer

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.mgplayer.data.Book
import pl.mgplayer.data.MediaRepository
import pl.mgplayer.data.Prefs
import pl.mgplayer.data.RadioRepository
import pl.mgplayer.data.Station
import pl.mgplayer.data.Track
import pl.mgplayer.data.toMediaItem
import java.time.LocalDate

enum class Kind { MUSIC, BOOK, RADIO }

/** Co robi obrót pierścienia na ekranie „Teraz gra”. */
enum class RingMode(val label: String, val stepDegrees: Float, val hint: String) {
    SEEK("Przewijanie", 15f, "kręć: ±10 s"),
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

    var radioResults by mutableStateOf<List<Station>>(emptyList()); private set
    var radioFavorites by mutableStateOf<List<Station>>(emptyList()); private set
    var radioLoading by mutableStateOf(false); private set
    var radioError by mutableStateOf<String?>(null); private set

    private var currentKind: Kind? = null
    private var currentBookKey: String? = null

    private var sleepEndAt: Long? = null
    private var sleepAfterItem = false

    private var stopwatchDurations: List<Long>? = null
    private var stopwatchMinutes = 25

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && sleepAfterItem) {
                controller?.pause()
                sleepAfterItem = false
            }
        }
    }

    init {
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
        stopwatchDurations = null
        currentKind = Kind.MUSIC
        currentBookKey = null
        c.setMediaItems(list.map { it.toMediaItem() }, start, 0L)
        c.setPlaybackSpeed(1f)
        c.prepare()
        c.play()
    }

    fun playBook(book: Book) {
        val c = controller ?: return
        stopwatchDurations = null
        currentKind = Kind.BOOK
        currentBookKey = book.key
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
        stopwatchDurations = null
        currentKind = Kind.RADIO
        currentBookKey = null
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
        if (currentKind == Kind.RADIO) return
        val dur = c.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        c.seekTo((c.currentPosition + deltaMs).coerceIn(0L, dur))
    }

    /** Jeden krok pierścienia, zależnie od wybranego trybu. */
    fun ringStep(d: Int) {
        val mode = if (currentKind == Kind.RADIO && ringMode == RingMode.SEEK) RingMode.VOLUME else ringMode
        when (mode) {
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
        if (currentKind == Kind.BOOK) prefs.bookSpeed = speed
    }

    // ---------------------------------------------------------------- Sesje

    fun setSleep(minutes: Int) {
        sleepAfterItem = false
        sleepEndAt = System.currentTimeMillis() + minutes * 60_000L
        tickNow()
    }

    fun setSleepAfterItem() {
        sleepEndAt = null
        sleepAfterItem = true
        tickNow()
    }

    fun cancelSleep() {
        sleepEndAt = null
        sleepAfterItem = false
        controller?.volume = 1f
        tickNow()
    }

    fun changeStopwatch(deltaMinutes: Int) {
        stopwatchMinutes = (stopwatchMinutes + deltaMinutes).coerceIn(5, 180)
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
        val target = stopwatchMinutes * 60_000L
        val pool = tracks.filter { it.durationMs in 60_000L..target }.shuffled()
        val picked = mutableListOf<Track>()
        var total = 0L
        for (t in pool) {
            if (total + t.durationMs <= target + 20_000L) {
                picked += t
                total += t.durationMs
            }
            if (target - total < 60_000L) break
        }
        if (picked.isEmpty()) return false
        playTracks(picked, 0)
        stopwatchDurations = picked.map { it.durationMs }
        sessions = sessions.copy(
            stopwatchDone = false,
            stopwatchInfo = "${picked.size} utw. · ${fmt(total)}",
        )
        return true
    }

    fun stopStopwatch() {
        stopwatchDurations = null
        sessions = sessions.copy(stopwatchRemainingMs = null, stopwatchDone = false, stopwatchInfo = null)
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

    // ---------------------------------------------------------------- Zegar

    private fun startTicker() {
        viewModelScope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(500)
                val now = SystemClock.elapsedRealtime()
                tick(now - last)
                last = now
            }
        }
    }

    private fun tickNow() = tick(0)

    private fun tick(dtMs: Long) {
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
                kind = if (item == null) null else (currentKind ?: Kind.MUSIC),
                speed = c.playbackParameters.speed,
            )

            // Audiobook: zapisuj pozycję i licz minuty do wyzwania
            if (c.isPlaying && currentKind == Kind.BOOK) {
                prefs.addListened(LocalDate.now(), dtMs)
                currentBookKey?.let { prefs.saveBook(it, c.currentMediaItemIndex, c.currentPosition) }
            }

            // Timer snu z płynnym ściszaniem w ostatnich 15 s
            sleepEndAt?.let { end ->
                val rem = end - System.currentTimeMillis()
                if (rem <= 0) {
                    c.pause()
                    c.volume = 1f
                    sleepEndAt = null
                } else if (rem < 15_000) {
                    c.volume = rem / 15_000f
                }
            }
        }

        // Stoper muzyczny
        var swRemaining: Long? = null
        var swDone = sessions.stopwatchDone
        val durations = stopwatchDurations
        if (durations != null && c != null) {
            val idx = c.currentMediaItemIndex.coerceIn(0, durations.lastIndex)
            swRemaining = (durations.drop(idx).sum() - c.currentPosition).coerceAtLeast(0)
            if (c.playbackState == Player.STATE_ENDED) {
                stopwatchDurations = null
                swRemaining = null
                swDone = true
                vibrate()
                playEndSignal()
            }
        }

        val sleepRem = sleepEndAt?.let { (it - System.currentTimeMillis()).coerceAtLeast(0) }
        sessions = sessions.copy(
            sleepRemainingMs = sleepRem,
            sleepAfterItem = sleepAfterItem,
            todayMs = prefs.listened(LocalDate.now()),
            goalMinutes = prefs.goalMinutes,
            streak = prefs.streak(),
            stopwatchMinutes = stopwatchMinutes,
            stopwatchRemainingMs = swRemaining,
            stopwatchDone = swDone,
            stopwatchInfo = if (swRemaining == null && !swDone) null else sessions.stopwatchInfo,
        )
    }

    /**
     * Sygnał końca stopera: 3 krótkie piknięcia na kanale alarmu,
     * więc słychać je nawet przy ściszonej muzyce.
     */
    private fun playEndSignal() {
        viewModelScope.launch {
            var tone: ToneGenerator? = null
            try {
                tone = ToneGenerator(AudioManager.STREAM_ALARM, 90)
                repeat(3) {
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
                    delay(650)
                }
                delay(400)
            } catch (e: Exception) {
            } finally {
                tone?.release()
            }
        }
    }

    private fun vibrate() {
        try {
            val v = getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
        } catch (e: Exception) {
        }
    }
}

fun fmt(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
