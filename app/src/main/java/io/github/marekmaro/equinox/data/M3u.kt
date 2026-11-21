package io.github.marekmaro.equinox.data

/** Pozycja wczytanej playlisty: ścieżka pliku i (jeśli były) tytuł, wykonawca, długość. */
data class PlaylistEntry(val path: String, val title: String = "", val artist: String = "", val durationMs: Long = 0)

/**
 * Playlisty w plikach .m3u / .m3u8 (i .pls przy wczytywaniu) – żeby przenosić je
 * między Equinox, komputerem (foobar2000, VLC, Winamp, iTunes) i innymi odtwarzaczami.
 *
 * Zapisujemy rozszerzony M3U w UTF-8: #EXTINF z długością i „Wykonawca - Tytuł”,
 * a pod spodem pełną ścieżkę pliku na telefonie.
 */
object M3u {

    fun export(name: String, tracks: List<TrackRef>): String = buildString {
        append("#EXTM3U\n")
        if (name.isNotBlank()) append("#PLAYLIST:").append(oneLine(name)).append('\n')
        tracks.forEach { t ->
            val sec = if (t.durationMs > 0) t.durationMs / 1000 else -1
            val label = if (t.artist.isNotBlank()) "${t.artist} - ${t.title}" else t.title
            append("#EXTINF:").append(sec).append(',').append(oneLine(label)).append('\n')
            append(t.path).append('\n')
        }
    }

    /** Nazwa playlisty zapisana w pliku (#PLAYLIST albo nagłówek .pls), jeśli jest. */
    fun nameIn(text: String): String? = text.lineSequence()
        .map { it.trim().removePrefix("﻿") }
        .firstOrNull { it.startsWith("#PLAYLIST:", ignoreCase = true) }
        ?.substringAfter(':')?.trim()?.ifBlank { null }

    /** Czyta .m3u / .m3u8 albo .pls; adresy internetowe (radio) pomija – to playlisty plików. */
    fun parse(text: String): List<PlaylistEntry> {
        val lines = text.lines().map { it.trim().removePrefix("﻿") }
        return if (lines.any { it.equals("[playlist]", ignoreCase = true) }) parsePls(lines) else parseM3u(lines)
    }

    private fun parseM3u(lines: List<String>): List<PlaylistEntry> {
        val out = ArrayList<PlaylistEntry>()
        var pending: PlaylistEntry? = null
        for (line in lines) {
            when {
                line.isEmpty() -> {}
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val body = line.substringAfter(':')
                    // „#EXTINF:215 tvg-id="…",Wykonawca - Tytuł” – atrybuty po długości są pomijane
                    val secs = body.substringBefore(',').trim().substringBefore(' ').toLongOrNull() ?: -1
                    val (artist, title) = splitLabel(body.substringAfter(',', ""))
                    pending = PlaylistEntry("", title, artist, if (secs > 0) secs * 1000 else 0)
                }
                line.startsWith("#") -> {}
                isUrl(line) -> pending = null
                else -> {
                    val path = decodeFileUrl(line)
                    out += (pending ?: PlaylistEntry("")).copy(path = path)
                    pending = null
                }
            }
        }
        return out
    }

    private fun parsePls(lines: List<String>): List<PlaylistEntry> {
        val files = HashMap<Int, String>()
        val titles = HashMap<Int, String>()
        val lengths = HashMap<Int, Long>()
        val re = Regex("^(File|Title|Length)(\\d+)=(.*)$", RegexOption.IGNORE_CASE)
        for (line in lines) {
            val m = re.find(line) ?: continue
            val n = m.groupValues[2].toIntOrNull() ?: continue
            val v = m.groupValues[3].trim()
            when (m.groupValues[1].lowercase()) {
                "file" -> files[n] = v
                "title" -> titles[n] = v
                "length" -> v.toLongOrNull()?.let { lengths[n] = it }
            }
        }
        return files.keys.sorted().mapNotNull { n ->
            val f = files[n] ?: return@mapNotNull null
            if (isUrl(f)) return@mapNotNull null
            val (artist, title) = splitLabel(titles[n].orEmpty())
            PlaylistEntry(decodeFileUrl(f), title, artist, (lengths[n] ?: 0).let { if (it > 0) it * 1000 else 0 })
        }
    }

    /** „Wykonawca - Tytuł” → (wykonawca, tytuł); bez myślnika całość to tytuł. */
    private fun splitLabel(label: String): Pair<String, String> {
        val l = label.trim()
        val i = l.indexOf(" - ")
        return if (i > 0) l.substring(0, i).trim() to l.substring(i + 3).trim() else "" to l
    }

    private fun isUrl(s: String) = s.startsWith("http://", true) || s.startsWith("https://", true) ||
        s.startsWith("rtsp://", true) || s.startsWith("mms://", true)

    /** „file:///sdcard/Muzyka/a%20b.mp3” → „/sdcard/Muzyka/a b.mp3”; zwykłe ścieżki bez zmian. */
    private fun decodeFileUrl(s: String): String {
        if (!s.startsWith("file:", ignoreCase = true)) return s
        val raw = s.substringAfter(':').trimStart('/')
        return "/" + try { java.net.URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") } catch (e: Exception) { raw }
    }

    private fun oneLine(s: String) = s.replace('\n', ' ').replace('\r', ' ')
}
