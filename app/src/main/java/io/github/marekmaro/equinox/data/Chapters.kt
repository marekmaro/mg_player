package io.github.marekmaro.equinox.data

import android.content.Context
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Rozdział wewnątrz jednego pliku audiobooka (np. M4B). */
data class Chapter(val startMs: Long, val title: String)

/**
 * Czyta listę rozdziałów zapisaną w pliku MP4/M4A/M4B w znaczniku „chpl”
 * (format Nero, zapisywany m.in. przez ffmpeg i popularne programy do audiobooków).
 * Nie zmienia pliku i czyta tylko nagłówki, nie cały dźwięk.
 */
object Chapters {

    fun read(context: Context, uri: Uri): List<Chapter> = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { parse(it) }
        } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    /** moov → udta → chpl */
    fun parse(ch: FileChannel): List<Chapter> {
        val size = ch.size()
        val moov = findBox(ch, 0, size, "moov") ?: return emptyList()
        val udta = findBox(ch, moov.first, moov.second, "udta") ?: return emptyList()
        val chpl = findBox(ch, udta.first, udta.second, "chpl") ?: return emptyList()
        val len = (chpl.second - chpl.first).coerceAtMost(1_000_000).toInt()
        if (len <= 0) return emptyList()
        val buf = ByteBuffer.allocate(len)
        ch.read(buf, chpl.first)
        return parseChpl(buf.array())
    }

    /** Szuka pudełka [type] między [start] a [end]; zwraca (początek treści, koniec). */
    private fun findBox(ch: FileChannel, start: Long, end: Long, type: String): Pair<Long, Long>? {
        var pos = start
        val hdr = ByteBuffer.allocate(16)
        var guard = 0
        while (pos + 8 <= end && guard++ < 10_000) {
            hdr.clear()
            hdr.limit(8)
            if (ch.read(hdr, pos) < 8) return null
            hdr.flip()
            var boxSize = hdr.int.toLong() and 0xFFFFFFFFL
            val name = ByteArray(4).also { hdr.get(it) }.toString(Charsets.ISO_8859_1)
            var headerLen = 8L
            if (boxSize == 1L) { // rozmiar 64-bitowy
                hdr.clear()
                hdr.limit(8)
                if (ch.read(hdr, pos + 8) < 8) return null
                hdr.flip()
                boxSize = hdr.long
                headerLen = 16L
            } else if (boxSize == 0L) {
                boxSize = end - pos // do końca rodzica
            }
            if (boxSize < headerLen) return null
            if (name == type) return (pos + headerLen) to minOf(pos + boxSize, end)
            pos += boxSize
        }
        return null
    }

    /** Treść „chpl”: wersja(1) flagi(3) [zarezerwowane(4) gdy wersja>0] liczba(1), potem: start(8, w 100 ns) długość(1) tytuł. */
    fun parseChpl(bytes: ByteArray): List<Chapter> {
        val b = ByteBuffer.wrap(bytes)
        if (b.remaining() < 5) return emptyList()
        val version = b.get().toInt()
        b.position(b.position() + 3)
        if (version != 0) {
            if (b.remaining() < 4) return emptyList()
            b.int
        }
        if (b.remaining() < 1) return emptyList()
        val count = b.get().toInt() and 0xFF
        val out = mutableListOf<Chapter>()
        repeat(count) {
            if (b.remaining() < 9) return@repeat
            val start = b.long
            val len = b.get().toInt() and 0xFF
            if (b.remaining() < len) return@repeat
            val title = ByteArray(len).also { b.get(it) }.toString(Charsets.UTF_8).trim()
            out += Chapter(startMs = start / 10_000, title = title.ifBlank { io.github.marekmaro.equinox.tr("Rozdział ${out.size + 1}", "Chapter ${out.size + 1}") })
        }
        // pojedynczy „rozdział” nic nie daje
        return if (out.size >= 2) out.sortedBy { it.startMs } else emptyList()
    }

    /** Indeks rozdziału, w którym jest pozycja [positionMs]. */
    fun indexAt(chapters: List<Chapter>, positionMs: Long): Int =
        chapters.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)
}
