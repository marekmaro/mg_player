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

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && Core.sleepAfterItem) {
                session?.player?.pause()
                Core.sleepAfterItem = false
            }
            pushWidget()
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
        session = MediaSession.Builder(this, player).build()
        Companion.player = player
        startTicker()
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
                    radio -> "● na żywo"
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
                    if (Core.kind == Kind.BOOK) {
                        Core.pendingListenMs += dt
                        if (ticks % 10 == 0) persist()
                    }
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
                        player.volume = 1f
                        Core.sleepEndAt = null
                    } else if (rem < 15_000) {
                        player.volume = rem / 15_000f
                    }
                }
            }
        }
    }

    /** Zapisuje pozycję audiobooka i zaległe minuty wyzwania. */
    private fun persist() {
        val player = session?.player ?: return
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

    override fun onDestroy() {
        persist()
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
    }
}
