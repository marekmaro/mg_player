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

    /**
     * Rozdziały z pliku MP4/M4A/M4B w jednym z dwóch formatów:
     * 1. Nero „chpl” (moov → udta → chpl) – zapisuje m.in. ffmpeg,
     * 2. Apple / QuickTime – osobna ścieżka tekstowa wskazana przez „tref/chap”
     *    (tak zapisuje iTunes, Audible i większość programów na Maca).
     */
    fun parse(ch: FileChannel): List<Chapter> {
        val size = ch.size()
        val moov = findBox(ch, 0, size, "moov") ?: return emptyList()
        findBox(ch, moov.first, moov.second, "udta")?.let { udta ->
            findBox(ch, udta.first, udta.second, "chpl")?.let { chpl ->
                val len = (chpl.second - chpl.first).coerceAtMost(1_000_000).toInt()
                if (len > 0) {
                    val buf = ByteBuffer.allocate(len)
                    ch.read(buf, chpl.first)
                    val found = parseChpl(buf.array())
                    if (found.isNotEmpty()) return found
                }
            }
        }
        return quickTimeChapters(ch, moov)
    }

    // ------------------------------------------------------------ format Apple (QuickTime)

    private class Trak(
        val id: Long,
        val handler: String,
        val chapRefs: List<Long>,
        val timescale: Long,
        val stts: List<Pair<Long, Long>>,   // (liczba próbek, czas trwania próbki)
        val sizes: List<Long>,
        val stsc: List<Triple<Long, Long, Long>>, // (pierwszy kawałek, próbek w kawałku, opis)
        val chunkOffsets: List<Long>,
    )

    private fun quickTimeChapters(ch: FileChannel, moov: Pair<Long, Long>): List<Chapter> {
        val traks = findAllBoxes(ch, moov.first, moov.second, "trak").mapNotNull { readTrak(ch, it) }
        if (traks.isEmpty()) return emptyList()
        val audio = traks.firstOrNull { it.handler == "soun" }
        fun isText(t: Trak) = t.handler == "text" || t.handler == "sbtl"
        // tref/chap często wskazuje dwie ścieżki: tytuły (text) i obrazki rozdziałów (vide) – bierzemy tekstową
        val chapId = audio?.chapRefs?.firstOrNull { ref -> traks.any { it.id == ref && isText(it) } }
            ?: traks.firstOrNull { isText(it) }?.id
            ?: return emptyList()
        val t = traks.firstOrNull { it.id == chapId && it.id != audio?.id } ?: return emptyList()
        if (t.timescale <= 0 || t.sizes.isEmpty() || t.chunkOffsets.isEmpty()) return emptyList()

        // czas początku każdej próbki (rozdziału)
        val starts = ArrayList<Long>()
        var time = 0L
        for ((count, delta) in t.stts) {
            repeat(count.coerceAtMost(5_000).toInt()) {
                starts += time
                time += delta
            }
        }
        // położenie każdej próbki w pliku: kawałki (stco) × próbki na kawałek (stsc) × rozmiary (stsz)
        val offsets = ArrayList<Long>()
        var sample = 0
        for (c in t.chunkOffsets.indices) {
            val chunkNo = c + 1L
            val perChunk = t.stsc.lastOrNull { it.first <= chunkNo }?.second ?: 1L
            var off = t.chunkOffsets[c]
            repeat(perChunk.coerceAtMost(5_000).toInt()) {
                if (sample >= t.sizes.size) return@repeat
                offsets += off
                off += t.sizes[sample]
                sample++
            }
            if (sample >= t.sizes.size || offsets.size >= 5_000) break
        }

        val n = minOf(starts.size, offsets.size, t.sizes.size)
        val out = ArrayList<Chapter>(n)
        for (i in 0 until n) {
            val title = readTextSample(ch, offsets[i], t.sizes[i])
            out += Chapter(
                startMs = starts[i] * 1000 / t.timescale,
                title = title.ifBlank { io.github.marekmaro.equinox.tr("Rozdział ${i + 1}", "Chapter ${i + 1}") },
            )
        }
        return if (out.size >= 2) out.sortedBy { it.startMs } else emptyList()
    }

    /** Próbka tekstowa: 2 bajty długości, potem tytuł (UTF-8 albo UTF-16 ze znacznikiem BOM). */
    private fun readTextSample(ch: FileChannel, offset: Long, size: Long): String {
        if (size < 2 || size > 4_096) return ""
        val b = ByteBuffer.allocate(size.toInt())
        if (ch.read(b, offset) < 2) return ""
        b.flip()
        val len = (b.short.toInt() and 0xFFFF).coerceAtMost(b.remaining())
        val bytes = ByteArray(len).also { b.get(it) }
        return when {
            len >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> String(bytes, 2, len - 2, Charsets.UTF_16BE)
            len >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> String(bytes, 2, len - 2, Charsets.UTF_16LE)
            else -> bytes.toString(Charsets.UTF_8)
        }.trim()
    }

    private fun readTrak(ch: FileChannel, trak: Pair<Long, Long>): Trak? {
        val tkhd = findBox(ch, trak.first, trak.second, "tkhd") ?: return null
        val tk = read(ch, tkhd.first, 24) ?: return null
        val version = tk.get(0).toInt()
        val id = (tk.getInt(if (version == 1) 20 else 12).toLong() and 0xFFFFFFFFL)

        val refs = mutableListOf<Long>()
        findBox(ch, trak.first, trak.second, "tref")?.let { tref ->
            findBox(ch, tref.first, tref.second, "chap")?.let { chap ->
                val n = ((chap.second - chap.first) / 4).toInt().coerceAtMost(16)
                read(ch, chap.first, n * 4)?.let { b -> repeat(n) { refs += b.getInt(it * 4).toLong() and 0xFFFFFFFFL } }
            }
        }

        val mdia = findBox(ch, trak.first, trak.second, "mdia") ?: return null
        val mdhd = findBox(ch, mdia.first, mdia.second, "mdhd")?.let { read(ch, it.first, 24) } ?: return null
        val timescale = mdhd.getInt(if (mdhd.get(0).toInt() == 1) 20 else 12).toLong() and 0xFFFFFFFFL
        val handler = findBox(ch, mdia.first, mdia.second, "hdlr")?.let { read(ch, it.first, 12) }
            ?.let { b -> ByteArray(4) { i -> b.get(8 + i) }.toString(Charsets.ISO_8859_1) } ?: ""

        val minf = findBox(ch, mdia.first, mdia.second, "minf") ?: return Trak(id, handler, refs, timescale, emptyList(), emptyList(), emptyList(), emptyList())
        val stbl = findBox(ch, minf.first, minf.second, "stbl") ?: return Trak(id, handler, refs, timescale, emptyList(), emptyList(), emptyList(), emptyList())

        val stts = table(ch, findBox(ch, stbl.first, stbl.second, "stts"), 8) { b, o -> (b.getInt(o).toLong() and 0xFFFFFFFFL) to (b.getInt(o + 4).toLong() and 0xFFFFFFFFL) }
        val stsc = table(ch, findBox(ch, stbl.first, stbl.second, "stsc"), 12) { b, o ->
            Triple(b.getInt(o).toLong() and 0xFFFFFFFFL, b.getInt(o + 4).toLong() and 0xFFFFFFFFL, b.getInt(o + 8).toLong() and 0xFFFFFFFFL)
        }
        val sizes = findBox(ch, stbl.first, stbl.second, "stsz")?.let { stsz ->
            val head = read(ch, stsz.first, 12) ?: return@let emptyList()
            val fixed = head.getInt(4).toLong() and 0xFFFFFFFFL
            val count = (head.getInt(8).toLong() and 0xFFFFFFFFL).coerceAtMost(5_000).toInt()
            if (fixed != 0L) List(count) { fixed }
            else read(ch, stsz.first + 12, count * 4)?.let { b -> List(count) { b.getInt(it * 4).toLong() and 0xFFFFFFFFL } } ?: emptyList()
        } ?: emptyList()
        val offsets = findBox(ch, stbl.first, stbl.second, "stco")?.let { box -> table(ch, box, 4) { b, o -> b.getInt(o).toLong() and 0xFFFFFFFFL } }
            ?: findBox(ch, stbl.first, stbl.second, "co64")?.let { box -> table(ch, box, 8) { b, o -> b.getLong(o) } }
            ?: emptyList()
        return Trak(id, handler, refs, timescale, stts, sizes, stsc, offsets)
    }

    /** Tabela w pudełku „pełnym”: wersja+flagi (4), liczba wpisów (4), wpisy po [entry] bajtów. */
    private fun <T> table(ch: FileChannel, box: Pair<Long, Long>?, entry: Int, parse: (ByteBuffer, Int) -> T): List<T> {
        if (box == null) return emptyList()
        val head = read(ch, box.first, 8) ?: return emptyList()
        val count = (head.getInt(4).toLong() and 0xFFFFFFFFL).coerceAtMost(5_000).toInt()
        val b = read(ch, box.first + 8, count * entry) ?: return emptyList()
        return List(count) { parse(b, it * entry) }
    }

    private fun read(ch: FileChannel, pos: Long, len: Int): ByteBuffer? {
        if (len <= 0 || len > 1_000_000 || pos < 0) return null
        val b = ByteBuffer.allocate(len)
        if (ch.read(b, pos) < len) return null
        b.flip()
        return b
    }

    /** Wszystkie pudełka [type] na danym poziomie (np. kilka „trak” w „moov”). */
    private fun findAllBoxes(ch: FileChannel, start: Long, end: Long, type: String): List<Pair<Long, Long>> {
        val out = mutableListOf<Pair<Long, Long>>()
        var from = start
        while (from < end && out.size < 64) {
            val box = findBox(ch, from, end, type) ?: break
            out += box
            from = box.second
        }
        return out
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
