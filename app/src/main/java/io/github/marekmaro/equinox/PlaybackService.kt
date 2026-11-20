package io.github.marekmaro.equinox

import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.marekmaro.equinox.data.Prefs
import io.github.marekmaro.equinox.data.SessionStore
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.marekmaro.equinox.widget.WidgetUpdater
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Serwis odtwarzania w tle. Media3 sam pokazuje powiadomienie z kontrolkami
 * i obsługuje ekran blokady, słuchawki Bluetooth i Android Auto.
 *
 * Tu działają też sesje (timer snu, stoper, wyzwanie, zapis pozycji
 * audiobooka), żeby nie zależały od tego, czy aplikacja jest otwarta.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = MainScope()
    private lateinit var prefs: Prefs
    private var radioRetries = 0
    private var lastChapterPos = 0L
    private var skippedInRow = 0
    private var itemPlayedMs = 0L
    private val sound = SoundEngine()
    private var countedFor: String? = null
    /** Głośność z timera snu (0..1) i z płynnych przejść (0..1) – mnożone razem. */
    private var sleepGain = 1f
    private var fadeGain = 1f
    /** Bieżący utwór zaczął się sam po poprzednim (a nie po przeskoku) – wtedy wchodzi łagodnie. */
    private var fadeInItem = false

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            itemPlayedMs = 0
            fadeInItem = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            applySound() // audiobook ↔ muzyka mogą mieć inne brzmienie
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && Core.sleepAfterItem) {
                session?.player?.pause()
                Core.sleepAfterItem = false
            }
            pushWidget()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            sound.attach(audioSessionId)
            applySound()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            pushWidget()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) persist()
            pushWidget()
        }

        /**
         * Odporność na błędy: uszkodzony lub usunięty plik jest pomijany,
         * a zerwane radio łączy się ponownie (3 próby, coraz dłuższa przerwa).
         */
        override fun onPlayerError(error: PlaybackException) {
            val p = session?.player ?: return
            if (Core.kind == Kind.RADIO) {
                if (radioRetries < 3) {
                    radioRetries++
                    scope.launch {
                        delay(2_000L * radioRetries)
                        p.prepare()
                        p.play()
                    }
                }
            } else if (p.hasNextMediaItem() && skippedInRow < 5) {
                skippedInRow++
                p.seekToNextMediaItem()
                p.prepare()
                p.play()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                radioRetries = 0
                skippedInRow = 0
            }
            if (playbackState == Player.STATE_ENDED && Core.stopwatchDurations != null) {
                // Stoper: muzyka ucichła, więc czas minął
                Core.stopwatchDurations = null
                Core.stopwatchDone = true
                vibrateFinish(this@PlaybackService)
                scope.playChime(Core.chime)
                StopwatchTileService.requestRefresh(this@PlaybackService)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        Core.chime = Chime.entries.firstOrNull { it.name == prefs.chime } ?: Chime.CHIP
        if (Core.playCounts == null) Core.playCounts = prefs.playCounts().toMutableMap()
        Core.soundPreset = SoundPreset.entries.firstOrNull { it.name == prefs.soundPreset } ?: SoundPreset.PURE
        Core.voiceForBooks = prefs.voiceForBooks
        Core.crossfadeSec = prefs.crossfadeSec

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // pauza po wyjęciu słuchawek
            // nie pozwól telefonowi uśpić Wi-Fi i procesora w trakcie radia przy zgaszonym ekranie
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(listener)
        // własna ikona (słońce nad horyzontem) w pasku stanu zamiast domyślnej
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().also { it.setSmallIcon(R.drawable.ic_notification) }
        )
        session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                /**
                 * Wznowienie „z zewnątrz”: przycisk odtwarzania na słuchawkach Bluetooth
                 * albo karta „Wznów” w systemowych kontrolkach multimediów, gdy aplikacja
                 * była zamknięta. Zwracamy ostatnią zapisaną sesję.
                 */
                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val saved = SessionStore.decode(prefs.lastSession)
                        ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Brak zapisanej sesji"))
                    Core.kind = saved.kind
                    Core.bookKey = saved.bookKey
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(SessionStore.toMediaItems(saved), saved.index, saved.positionMs)
                    )
                }
            })
            .build()
        Companion.player = player
        instance = this
        sound.attach(player.audioSessionId)
        applySound()
        startTicker()
        startFader()
        pushWidget()
    }

    /** Odświeża widżety na ekranie głównym aktualnym stanem odtwarzacza. */
    private fun pushWidget() {
        val p = session?.player ?: return
        if (p.mediaItemCount == 0) {
            WidgetUpdater.push(this, WidgetUpdater.idle())
            return
        }
        val md = p.mediaMetadata
        val dur = p.duration
        val radio = Core.kind == Kind.RADIO
        WidgetUpdater.push(
            this,
            WidgetUpdater.State(
                title = md.title?.toString().orEmpty().ifBlank { "Equinox" },
                subtitle = when {
                    radio -> tr("● na żywo", "● live")
                    else -> md.artist?.toString().orEmpty()
                },
                playing = p.isPlaying,
                progress = when {
                    radio -> if (p.isPlaying) 1f else 0f
                    dur > 0 && dur != C.TIME_UNSET -> p.currentPosition.toFloat() / dur
                    else -> 0f
                },
                active = true,
            ),
        )
    }

    private fun startTicker() {
        scope.launch {
            var last = SystemClock.elapsedRealtime()
            var ticks = 0
            while (isActive) {
                // gdy nic nie gra i nie ma timera snu, budzimy się rzadziej (bateria)
                val busy = session?.player?.isPlaying == true || Core.sleepEndAt != null
                delay(if (busy) 500 else 2_000)
                val now = SystemClock.elapsedRealtime()
                val dt = now - last
                last = now
                val player = session?.player ?: continue

                // Wyzwanie: licz minuty audiobooka; zapis na dysk co ~5 s
                if (player.isPlaying) {
                    ticks++
                    // pierścień na widżecie co ~5 s (rzadziej = mniej pracy dla baterii)
                    if (ticks % 10 == 0) pushWidget()
                    if (Core.kind == Kind.BOOK) Core.pendingListenMs += dt
                    // statystyki: pora dnia (wszystko) i wykonawca (tylko muzyka)
                    io.github.marekmaro.equinox.data.Stats.add(
                        java.time.LocalTime.now().hour,
                        if (Core.kind == Kind.MUSIC) player.mediaMetadata.artist?.toString() else null,
                        dt,
                    )
                    // utwór liczy się jako odtworzony po 30 s słuchania
                    if (Core.kind == Kind.MUSIC) {
                        itemPlayedMs += dt
                        val id = player.currentMediaItem?.mediaId
                        if (itemPlayedMs >= 30_000 && id != null && id != countedFor) {
                            countedFor = id
                            id.toLongOrNull()?.let { tid ->
                                val counts = Core.playCounts ?: mutableMapOf<Long, Int>().also { Core.playCounts = it }
                                counts[tid] = (counts[tid] ?: 0) + 1
                                Core.playCountsDirty = true
                                val hist = Core.recentPlayed ?: ArrayDeque(prefs.recentPlayed()).also { Core.recentPlayed = it }
                                hist.remove(tid)
                                hist.addFirst(tid)
                                while (hist.size > 100) hist.removeLast()
                            }
                        }
                    }
                    // zapis miejsca odtwarzania co ~5 s (wznowienie po zamknięciu aplikacji)
                    if (ticks % 10 == 0) persist()
                }

                // „Sen po rozdziale” dla rozdziałów wewnątrz jednego pliku (M4B)
                val pos = player.currentPosition
                if (Core.sleepAfterItem && Core.kind == Kind.BOOK && Core.chapters.isNotEmpty() &&
                    Core.chaptersFor == player.currentMediaItem?.mediaId
                ) {
                    val boundary = Core.chapters.getOrNull(
                        io.github.marekmaro.equinox.data.Chapters.indexAt(Core.chapters, lastChapterPos) + 1
                    )?.startMs
                    // tylko zwykłe odtwarzanie przez granicę (nie ręczny przeskok)
                    val natural = pos >= lastChapterPos && pos - lastChapterPos < 5_000
                    if (boundary != null && natural && lastChapterPos < boundary && pos >= boundary) {
                        player.pause()
                        Core.sleepAfterItem = false
                    }
                }
                lastChapterPos = pos

                // Timer snu z płynnym ściszaniem w ostatnich 15 s
                Core.sleepEndAt?.let { end ->
                    val rem = end - System.currentTimeMillis()
                    if (rem <= 0) {
                        player.pause()
                        Core.sleepEndAt = null
                        sleepGain = 1f
                    } else {
                        sleepGain = if (rem < 15_000) rem / 15_000f else 1f
                    }
                    updateVolume(player)
                } ?: run {
                    // timer anulowany w trakcie ściszania – wróć do pełnej głośności
                    if (sleepGain != 1f) {
                        sleepGain = 1f
                        updateVolume(player)
                    }
                }
            }
        }
    }

    private fun updateVolume(player: Player) {
        val v = (sleepGain * fadeGain).coerceIn(0f, 1f)
        if (player.volume != v) player.volume = v
    }

    /**
     * Płynne przejścia: w ostatnich sekundach utworu głośność spada do zera,
     * a następny utwór (jeśli zaczął się sam) wchodzi łagodnie przez pół tego czasu.
     * Jeden odtwarzacz, więc to „przenikanie przez ciszę” – bez nakładania dwóch utworów.
     * Pętla działa często tylko wtedy, gdy gra muzyka z włączoną funkcją.
     */
    private fun startFader() {
        scope.launch {
            while (isActive) {
                val player = session?.player
                val cf = Core.crossfadeSec * 1000L
                val active = player != null && player.isPlaying && cf > 0 && Core.kind == Kind.MUSIC
                if (player != null) {
                    val dur = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                    val g = if (active) fadeGainFor(Core.kind, cf, player.currentPosition, dur, player.hasNextMediaItem(), fadeInItem) else 1f
                    // po pauzie lub przeskoku w środek utworu wracamy do pełnej głośności
                    if (g >= 1f && player.currentPosition > cf) fadeInItem = false
                    if (g != fadeGain) {
                        fadeGain = g
                        updateVolume(player)
                    }
                }
                delay(if (active) 50 else 1_000)
            }
        }
    }

    /** Zapisuje miejsce odtwarzania (sesję), pozycję audiobooka, minuty wyzwania i liczniki. */
    private fun persist() {
        val player = session?.player ?: return
        SessionStore.capture(player, Core.kind, Core.bookKey)?.let { prefs.lastSession = SessionStore.encode(it) }
        io.github.marekmaro.equinox.data.Stats.flush(prefs)
        if (Core.playCountsDirty) {
            Core.playCounts?.let { prefs.setPlayCounts(it) }
            Core.recentPlayed?.let { prefs.setRecentPlayed(it.toList()) }
            Core.playCountsDirty = false
        }
        if (Core.kind == Kind.BOOK) {
            Core.bookKey?.let { prefs.saveBook(it, player.currentMediaItemIndex, player.currentPosition) }
        }
        if (Core.pendingListenMs > 0) {
            prefs.addListened(LocalDate.now(), Core.pendingListenMs)
            Core.pendingListenMs = 0
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    /** Ustawia korektor: dla audiobooków (gdy włączone) „Czysty głos”, w innych przypadkach wybrane brzmienie. */
    fun applySound() {
        val preset = if (Core.kind == Kind.BOOK && Core.voiceForBooks) SoundPreset.VOICE else Core.soundPreset
        sound.apply(preset)
        soundAvailable = sound.available
    }

    override fun onDestroy() {
        persist()
        sound.release()
        instance = null
        val lastTitle = session?.player?.mediaMetadata?.title?.toString()
        Companion.player = null
        WidgetUpdater.push(this, WidgetUpdater.idle(lastTitle?.takeIf { it.isNotBlank() }), force = true)
        scope.cancel()
        session?.run {
            player.removeListener(listener)
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        /** Odtwarzacz serwisu (ten sam proces) – dla widżetów i kafelka. Tylko wątek główny. */
        var player: Player? = null
            private set

        private var instance: PlaybackService? = null

        /** false = telefon nie obsługuje korektora. */
        var soundAvailable = true
            private set

        /** Wywoływane po zmianie brzmienia w aplikacji. */
        fun refreshSound() {
            instance?.applySound()
        }
    }
}
