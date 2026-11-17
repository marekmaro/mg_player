package io.github.marekmaro.equinox

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class Kind { MUSIC, BOOK, RADIO }

/**
 * Wspólny stan sesji w procesie aplikacji. Ekran (ViewModel) go ustawia,
 * a serwis odtwarzania go wykonuje: timer snu, stoper i zapis audiobooków
 * działają więc także po zamknięciu aplikacji, dopóki gra muzyka.
 * Dostęp tylko z wątku głównego.
 */
object Core {
    var kind: Kind? = null
    var bookKey: String? = null

    var sleepEndAt: Long? = null
    var sleepAfterItem: Boolean = false

    /** Długości utworów ułożonych przez stoper; null = stoper nieaktywny. */
    var stopwatchDurations: List<Long>? = null
    var stopwatchInfo: String? = null
    var stopwatchDone: Boolean = false

    var chime: Chime = Chime.CHIP

    /** Ostatnio ustawiony czas stopera w minutach. */
    var stopwatchMinutes: Int = 25

    /** Rozdziały wewnątrz bieżącego pliku audiobooka (np. M4B) i plik, którego dotyczą. */
    var chapters: List<io.github.marekmaro.equinox.data.Chapter> = emptyList()
    var chaptersFor: String? = null

    /** Brzmienie (korektor) i automatyczny „Czysty głos” dla audiobooków. */
    var soundPreset: SoundPreset = SoundPreset.PURE
    var voiceForBooks: Boolean = true

    /** Liczba odtworzeń utworów (id -> ile razy); wczytywana raz, zapisywana co kilka sekund. */
    var playCounts: MutableMap<Long, Int>? = null
    var playCountsDirty = false

    /** Ostatnio odtworzone utwory (najnowszy pierwszy) – do mądrego losowania. */
    var recentPlayed: ArrayDeque<Long>? = null

    /** Minuty audiobooka jeszcze niezapisane na dysk (zapis co kilka sekund). */
    var pendingListenMs: Long = 0L
}

/** Formatuje czas jako mm:ss albo h:mm:ss. */
fun fmt(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/**
 * Dobiera utwory, które razem trwają możliwie blisko [targetMs]
 * (najwyżej 20 s dłużej). Zwraca indeksy w [durations].
 * Najpierw losowe zachłanne wypełnienie, potem kilka prób zamiany
 * jednego utworu na lepiej pasujący, żeby zmniejszyć lukę.
 */
fun pickForDuration(durations: List<Long>, targetMs: Long, random: Random = Random.Default): List<Int> {
    val limit = targetMs + 20_000L
    val candidates = durations.indices.filter { durations[it] in 60_000L..limit }.shuffled(random)
    val picked = mutableListOf<Int>()
    var total = 0L
    for (i in candidates) {
        if (total + durations[i] <= limit) {
            picked += i
            total += durations[i]
        }
        if (targetMs - total < 30_000L) break
    }
    if (picked.isEmpty()) return picked

    // ulepszanie: zamień jeden wybrany utwór na niewybrany, jeśli zmniejsza to lukę
    val unused = candidates.filter { it !in picked }.toMutableList()
    repeat(3) {
        val gap = targetMs - total
        if (kotlin.math.abs(gap) < 15_000L) return picked
        var best: Pair<Int, Int>? = null
        var bestGap = kotlin.math.abs(gap)
        for (pi in picked.indices) for (u in unused) {
            val newTotal = total - durations[picked[pi]] + durations[u]
            if (newTotal > limit) continue
            val g = kotlin.math.abs(targetMs - newTotal)
            if (g < bestGap) { bestGap = g; best = pi to u }
        }
        val b = best ?: return picked
        val old = picked[b.first]
        total = total - durations[old] + durations[b.second]
        picked[b.first] = b.second
        unused.remove(b.second)
        unused += old
    }
    return picked
}

/** Gra sygnał w tle i zwalnia odtwarzacz po zakończeniu. */
fun CoroutineScope.playChime(chime: Chime) {
    launch {
        var track: android.media.AudioTrack? = null
        try {
            track = chime.play()
            delay(chime.durationMs + 300)
        } catch (e: Exception) {
        } finally {
            try { track?.release() } catch (e: Exception) {}
        }
    }
}

fun vibrateFinish(context: Context) {
    try {
        @Suppress("DEPRECATION")
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
    } catch (e: Exception) {
    }
}
