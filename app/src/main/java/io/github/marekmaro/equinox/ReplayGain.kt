package io.github.marekmaro.equinox

import android.media.audiofx.LoudnessEnhancer
import kotlin.math.pow

/**
 * Wyrównanie głośności (ReplayGain): utwory z różnych płyt grają podobnie głośno.
 *
 * Programy do muzyki (foobar2000, MusicBrainz Picard, mp3gain, ffmpeg…) zapisują w pliku,
 * o ile decybeli trzeba utwór ściszyć lub pogłośnić. My tylko to odczytujemy:
 * – ściszanie: przez głośność odtwarzacza (bez żadnych zniekształceń),
 * – pogłaśnianie cichych nagrań: przez systemowy [LoudnessEnhancer], najwyżej o [MAX_BOOST_DB].
 * Pliki bez znaczników grają bez zmian.
 */
enum class ReplayGainMode(private val pl: String, private val en: String) {
    OFF("Wył.", "Off"),
    /** Losowo – każdy utwór osobno; po kolei – cały album razem (zachowuje różnice między utworami). */
    AUTO("Auto", "Auto"),
    TRACK("Utwór", "Track"),
    ALBUM("Album", "Album");

    val label: String get() = tr(pl, en)
}

/** Odczytane wzmocnienia w dB (null = brak znacznika). */
data class ReplayGainTags(val trackDb: Float?, val albumDb: Float?) {
    /** Wzmocnienie dla trybu; gdy brakuje wybranego znacznika, bierzemy drugi. */
    fun gainFor(mode: ReplayGainMode, shuffled: Boolean): Float? = when (mode) {
        ReplayGainMode.OFF -> null
        ReplayGainMode.AUTO -> if (shuffled) trackDb ?: albumDb else albumDb ?: trackDb
        ReplayGainMode.TRACK -> trackDb ?: albumDb
        ReplayGainMode.ALBUM -> albumDb ?: trackDb
    }

    companion object {
        val NONE = ReplayGainTags(null, null)
    }
}

object ReplayGain {

    const val MAX_BOOST_DB = 6f

    /**
     * Znaczniki z pliku jako pary (nazwa, wartość) – niezależnie od formatu:
     * ID3 (TXXX), Vorbis/FLAC/Opus (komentarze), MP4 (----:com.apple.iTunes:…).
     * Opus zapisuje też R128_TRACK_GAIN (liczba całkowita w 1/256 dB względem −23 LUFS),
     * który przeliczamy na skalę ReplayGain (+5 dB).
     */
    fun parse(entries: List<Pair<String, String>>): ReplayGainTags {
        var track: Float? = null
        var album: Float? = null
        var r128Track: Float? = null
        var r128Album: Float? = null
        for ((rawKey, value) in entries) {
            when (rawKey.trim().lowercase().substringAfterLast(':')) {
                "replaygain_track_gain" -> track = track ?: parseDb(value)
                "replaygain_album_gain" -> album = album ?: parseDb(value)
                "r128_track_gain" -> r128Track = r128Track ?: value.trim().toIntOrNull()?.let { it / 256f + 5f }
                "r128_album_gain" -> r128Album = r128Album ?: value.trim().toIntOrNull()?.let { it / 256f + 5f }
            }
        }
        return ReplayGainTags(track ?: r128Track, album ?: r128Album)
    }

    /** „-6.54 dB”, „+1,2 dB”, „−3.1” → liczba; bzdury i wartości spoza ±30 dB → null. */
    fun parseDb(text: String): Float? {
        val cleaned = text.trim()
            .replace('−', '-')
            .replace(',', '.')
            .removeSuffix("dB").removeSuffix("db").removeSuffix("DB")
            .trim()
        val v = cleaned.toFloatOrNull() ?: return null
        return v.takeIf { it.isFinite() && it in -30f..30f }
    }

    /** Głośność odtwarzacza (0..1) dla ściszenia – dodatnie wzmocnienie daje 1 (pogłaśnia [Booster]). */
    fun volumeFor(db: Float?): Float = if (db == null || db >= 0f) 1f else 10f.pow(db / 20f)

    /** Podbicie w milibelach dla [LoudnessEnhancer] (0, gdy nie trzeba). */
    fun boostMillibelsFor(db: Float?): Int = if (db == null || db <= 0f) 0 else (db.coerceAtMost(MAX_BOOST_DB) * 100).toInt()
}

/** Podbicie cichych nagrań na sesji audio odtwarzacza. Na telefonach bez efektu po prostu nic nie robi. */
class Booster {
    private var fx: LoudnessEnhancer? = null
    private var session = 0

    fun attach(audioSessionId: Int) {
        if (audioSessionId == session && fx != null) return
        release()
        session = audioSessionId
        if (audioSessionId == 0) return
        fx = try { LoudnessEnhancer(audioSessionId) } catch (e: Exception) { null }
    }

    fun set(millibels: Int) {
        val e = fx ?: return
        try {
            e.setTargetGain(millibels)
            e.enabled = millibels > 0
        } catch (ex: Exception) {
            // efekt mógł zostać odebrany przez system – po prostu bez podbicia
        }
    }

    fun release() {
        try { fx?.release() } catch (e: Exception) {}
        fx = null
    }
}
