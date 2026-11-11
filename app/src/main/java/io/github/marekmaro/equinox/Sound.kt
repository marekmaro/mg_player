package io.github.marekmaro.equinox

import android.media.audiofx.Equalizer
import kotlin.math.abs
import kotlin.math.ln

/**
 * Brzmienia (ustawienia korektora) o ludzkich nazwach zamiast „+3 dB przy 230 Hz”.
 * Wzmocnienia podane dla pięciu typowych pasm: 60 Hz, 230 Hz, 910 Hz, 3,6 kHz, 14 kHz
 * (bas, dolny środek, środek, prezencja, „powietrze”). Na telefonach z inną liczbą
 * pasm wartości są dopasowywane do najbliższej częstotliwości.
 */
enum class SoundPreset(val pl: String, val en: String, val notePl: String, val noteEn: String, val gainsDb: List<Int>) {
    PURE("Czysty sygnał", "Pure signal", "bez zmian", "untouched", listOf(0, 0, 0, 0, 0)),
    WARM("Ciepły wieczór", "Warm evening", "miękko i blisko", "soft and close", listOf(3, 2, 0, -1, -2)),
    BASS("Bas w kościach", "Bass in your bones", "hip-hop, elektronika", "hip-hop, electronic", listOf(6, 4, 0, 0, 1)),
    CLUB("Klub o północy", "Midnight club", "taneczne, house", "dance, house", listOf(5, 2, -1, 2, 4)),
    ROCK("Gitarowy pazur", "Guitar claws", "rock, metal", "rock, metal", listOf(4, 1, -2, 2, 4)),
    ACOUSTIC("Akustyczny poranek", "Acoustic morning", "folk, unplugged", "folk, unplugged", listOf(2, 1, 1, 2, 2)),
    JAZZ("Jazzowa kawiarnia", "Jazz café", "jazz, soul", "jazz, soul", listOf(3, 1, -1, 1, 3)),
    CLASSIC("Filharmonia w salonie", "Concert hall at home", "klasyka, soundtracki", "classical, scores", listOf(4, 2, -1, 2, 3)),
    VOICE("Czysty głos", "Clear voice", "audiobooki, podcasty", "audiobooks, podcasts", listOf(-2, 0, 3, 4, 1)),
    WHISPER("Nocny szept", "Night whisper", "ciche słuchanie", "quiet listening", listOf(4, 1, 0, 1, 3));

    val label: String get() = tr(pl, en)
    val note: String get() = tr(notePl, noteEn)

    companion object {
        val referenceHz = listOf(60, 230, 910, 3_600, 14_000)

        /** Wzmocnienie dla dowolnej częstotliwości (interpolacja w skali logarytmicznej). */
        fun gainAt(preset: SoundPreset, hz: Int): Float {
            val f = ln(hz.coerceAtLeast(20).toDouble())
            val refs = referenceHz.map { ln(it.toDouble()) }
            if (f <= refs.first()) return preset.gainsDb.first().toFloat()
            if (f >= refs.last()) return preset.gainsDb.last().toFloat()
            val i = refs.indexOfLast { it <= f }
            val t = ((f - refs[i]) / (refs[i + 1] - refs[i])).toFloat()
            return preset.gainsDb[i] + (preset.gainsDb[i + 1] - preset.gainsDb[i]) * t
        }
    }
}

/** Steruje korektorem systemowym podpiętym pod sesję audio odtwarzacza. */
class SoundEngine {
    private var eq: Equalizer? = null
    var available = true
        private set

    fun attach(audioSessionId: Int) {
        release()
        if (audioSessionId == 0) return
        eq = try {
            Equalizer(0, audioSessionId)
        } catch (e: Exception) {
            available = false // np. telefon bez obsługi efektów dźwiękowych
            null
        }
    }

    fun apply(preset: SoundPreset) {
        val e = eq ?: return
        try {
            if (preset == SoundPreset.PURE) {
                e.enabled = false
                return
            }
            val range = e.bandLevelRange // milibele, np. [-1500, 1500]
            for (band in 0 until e.numberOfBands) {
                val hz = e.getCenterFreq(band.toShort()) / 1000 // centrum pasma w mHz
                val mb = (SoundPreset.gainAt(preset, hz) * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                e.setBandLevel(band.toShort(), mb.toShort())
            }
            e.enabled = true
        } catch (e2: Exception) {
            available = false
        }
    }

    fun release() {
        try { eq?.release() } catch (e: Exception) {}
        eq = null
    }
}

/** Najbliższa częstotliwość referencyjna (do testów i podglądu). */
fun nearestReference(hz: Int): Int = SoundPreset.referenceHz.minBy { abs(ln(it.toDouble()) - ln(hz.toDouble())) }
