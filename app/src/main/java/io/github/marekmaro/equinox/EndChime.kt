package io.github.marekmaro.equinox

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Sygnały końca stopera. Generowane w kodzie (bez plików audio) i grane
 * kanałem alarmu, więc słychać je nawet przy ściszonej muzyce.
 */
enum class Timbre { DIGITAL, BELL, CHIP, SOFT }

/** Jedna nuta: częstotliwość, start i długość (z wybrzmieniem) w ms. */
data class Note(val freq: Double, val startMs: Int, val lenMs: Int)

enum class Chime(val label: String, val timbre: Timbre, val notes: List<Note>) {

    /** Mała fanfara w stylu starych konsol 8-bit. */
    CHIP("Fanfara 8-bit", Timbre.CHIP, seq(
        G5 to 90, R to 20, C6 to 90, R to 20, E6 to 90, R to 20, G6 to 180, R to 90,
        E6 to 90, R to 20, G6 to 480,
    )),

    /** Klasyczne „pik-pik” z zegarka, ułożone w akord: mi–sol–DO ×2, potem sol–do–MI. */
    CLASSIC("Klasyczny", Timbre.DIGITAL, seq(
        E6 to 90, R to 50, G6 to 90, R to 50, C7 to 170, R to 280,
        E6 to 90, R to 50, G6 to 90, R to 50, C7 to 170, R to 280,
        G6 to 80, R to 40, C7 to 80, R to 40, E7 to 520,
    )),

    /** Wznoszący się arpeggio jak dzwonki rurowe; dźwięki nakładają się i wybrzmiewają. */
    BELL("Dzwonek", Timbre.BELL, listOf(
        Note(C6, 0, 1100), Note(E6, 180, 1100), Note(G6, 360, 1100), Note(C7, 540, 1800),
    )),

    /** Spokojne „ding-dong”, jak sygnał na dworcu. Dobre po sesji nauki. */
    SOFT("Delikatny", Timbre.SOFT, listOf(
        Note(E6, 0, 1300), Note(C6, 650, 1800),
    ));

    val durationMs: Long get() = notes.maxOf { it.startMs + it.lenMs }.toLong()

    fun render(): ShortArray {
        val total = (durationMs * RATE / 1000).toInt()
        val mix = DoubleArray(total)
        notes.forEachIndexed { i, note ->
            if (note.freq <= 0.0) return@forEachIndexed
            val start = note.startMs * RATE / 1000
            val n = note.lenMs * RATE / 1000
            val last = i == notes.lastIndex
            for (k in 0 until n) {
                val idx = start + k
                if (idx >= total) break
                val t = k.toDouble() / RATE
                mix[idx] += voice(timbre, note.freq, t, k, n, last)
            }
        }
        // normalizacja do bezpiecznej głośności
        val peak = mix.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-9)
        val gain = 0.8 / peak
        return ShortArray(total) { (mix[it] * gain * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort() }
    }

    fun play(): AudioTrack {
        val pcm = render()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        track.play()
        return track
    }
}

private const val RATE = 44_100

private const val R = 0.0
private const val G5 = 783.99
private const val C6 = 1046.50
private const val E6 = 1318.51
private const val G6 = 1567.98
private const val C7 = 2093.00
private const val E7 = 2637.02

/** Nuty jedna po drugiej: (częstotliwość, czas w ms); 0 Hz = pauza. */
private fun seq(vararg steps: Pair<Double, Int>): List<Note> {
    var t = 0
    val out = mutableListOf<Note>()
    for ((f, ms) in steps) {
        if (f > 0.0) out += Note(f, t, ms)
        t += ms
    }
    return out
}

private fun voice(timbre: Timbre, f: Double, t: Double, k: Int, n: Int, last: Boolean): Double {
    val w = 2 * PI * f * t
    val attack: Double
    val body: Double
    val env: Double
    when (timbre) {
        Timbre.DIGITAL -> {
            body = sin(w) + 0.30 * sin(3 * w) / 3 + 0.15 * sin(5 * w) / 5
            attack = (k / (RATE * 0.004)).coerceAtMost(1.0)
            env = exp(-t * if (last) 5.0 else 14.0)
        }
        Timbre.BELL -> {
            // nieharmoniczne alikwoty dają brzmienie dzwonu; wyższe gasną szybciej
            body = sin(w) + 0.5 * sin(2.76 * w) * exp(-t * 6) + 0.25 * sin(5.40 * w) * exp(-t * 10)
            attack = (k / (RATE * 0.002)).coerceAtMost(1.0)
            env = exp(-t * if (last) 2.2 else 3.2)
        }
        Timbre.CHIP -> {
            // prawie prostokąt (nieparzyste harmoniczne), płaska obwiednia jak w konsoli
            body = sin(w) + sin(3 * w) / 3 + sin(5 * w) / 5 + sin(7 * w) / 7
            attack = (k / (RATE * 0.002)).coerceAtMost(1.0)
            env = if (last) exp(-t * 4.5) else 1.0
        }
        Timbre.SOFT -> {
            body = sin(w) + 0.2 * sin(2 * w) * exp(-t * 4)
            attack = (k / (RATE * 0.012)).coerceAtMost(1.0)
            env = exp(-t * 2.4)
        }
    }
    val release = ((n - k) / (RATE * 0.008)).coerceAtMost(1.0)
    return body * attack * env * release
}
