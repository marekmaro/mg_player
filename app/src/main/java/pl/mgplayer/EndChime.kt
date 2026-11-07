package pl.mgplayer

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Sygnał końca stopera: klasyczne „pik-pik” jak w zegarku elektronicznym,
 * ale ułożone w melodię (akord C-dur: mi – sol – do). Dwa razy krótki motyw,
 * na końcu wznosząca kodka z dłużej wybrzmiewającym, najwyższym dźwiękiem.
 *
 * Dźwięk jest generowany w kodzie (bez plików audio) i grany kanałem
 * alarmu, więc słychać go nawet przy ściszonej muzyce.
 */
object EndChime {

    private const val RATE = 44_100

    private const val E6 = 1318.51
    private const val G6 = 1567.98
    private const val C7 = 2093.00
    private const val E7 = 2637.02
    private const val REST = 0.0

    /** (częstotliwość w Hz, czas w ms). 0 Hz = cisza. */
    private val pattern = listOf(
        E6 to 90, REST to 50, G6 to 90, REST to 50, C7 to 170, REST to 280,
        E6 to 90, REST to 50, G6 to 90, REST to 50, C7 to 170, REST to 280,
        G6 to 80, REST to 40, C7 to 80, REST to 40, E7 to 520,
    )

    val durationMs: Long = pattern.sumOf { it.second }.toLong()

    fun render(): ShortArray {
        val total = pattern.sumOf { it.second } * RATE / 1000
        val out = ShortArray(total)
        var pos = 0
        pattern.forEachIndexed { i, (freq, ms) ->
            val n = ms * RATE / 1000
            val last = i == pattern.lastIndex
            for (k in 0 until n) {
                if (freq > 0.0) {
                    val t = k.toDouble() / RATE
                    // miękki „cyfrowy” ton: sinus + nieparzyste harmoniczne (zaokrąglony prostokąt)
                    val w = 2 * PI * freq * t
                    val tone = sin(w) + 0.30 * sin(3 * w) / 3 + 0.15 * sin(5 * w) / 5
                    // szybki atak, wybrzmienie jak w małym dzwonku
                    val attack = (k / (RATE * 0.004)).coerceAtMost(1.0)
                    val decay = exp(-t * if (last) 5.0 else 14.0)
                    val release = ((n - k) / (RATE * 0.006)).coerceAtMost(1.0)
                    val v = 0.55 * tone * attack * decay * release
                    out[pos + k] = (v * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
                }
            }
            pos += n
        }
        return out
    }

    /** Odtwarza sygnał. Zwraca odtwarzacz, który trzeba zwolnić po [durationMs]. */
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
