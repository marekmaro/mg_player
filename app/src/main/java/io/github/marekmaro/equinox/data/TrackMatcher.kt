package io.github.marekmaro.equinox.data

import io.github.marekmaro.equinox.normalizeForSearch
import kotlin.math.abs

/**
 * Odnajduje utwór z innego telefonu albo komputera w bibliotece tego telefonu.
 * Używane przy odtwarzaniu kopii zapasowej i wczytywaniu playlist .m3u.
 *
 * Kolejność prób:
 * 1. ta sama pełna ścieżka,
 * 2. ten sam ostatni folder i plik (np. „Album/01 Utwór.mp3” – inna karta SD, ścieżka z Windowsa);
 *    gdy znamy tytuł, musi się zgadzać, bo „CD1/01.mp3” bywa w wielu albumach,
 * 3. ten sam tytuł i wykonawca (bez wielkich liter i polskich znaków).
 * Przy kilku kandydatach wygrywa ten o najbliższej długości.
 */
class TrackMatcher(library: List<TrackRef>) {

    private val byPath = library.associateBy { normalizePath(it.path) }
    private val byTail = library.groupBy { tail(it.path) }
    private val byName = library.groupBy { nameKey(it.title, it.artist) }
    private val byTitle = library.groupBy { normalizeForSearch(it.title) }

    /**
     * [title] i [artist] mogą być puste (np. .m3u bez opisu), [durationMs] ≤ 0 = nieznana.
     * Bez wykonawcy szukamy po samym tytule tylko wtedy, gdy jest jednoznaczny.
     */
    fun find(path: String, title: String, artist: String, durationMs: Long): TrackRef? {
        fun closest(list: List<TrackRef>?) =
            if (durationMs > 0) list?.minByOrNull { abs(it.durationMs - durationMs) } else list?.firstOrNull()
        val t = normalizeForSearch(title)
        if (path.isNotBlank()) {
            byPath[normalizePath(path)]?.let { return it }
            val sameFile = byTail[tail(path)]
            val hit = if (t.isEmpty()) closest(sameFile) else closest(sameFile?.filter { normalizeForSearch(it.title) == t })
            if (hit != null) return hit
        }
        if (t.isEmpty()) return null
        if (artist.isNotBlank()) closest(byName[nameKey(title, artist)])?.let { return it }
        return byTitle[t]?.takeIf { it.size == 1 }?.first()
    }

    companion object {
        /** Ukośniki jak w Androidzie, małe litery (Windows nie rozróżnia wielkości liter). */
        fun normalizePath(p: String): String = p.replace('\\', '/').lowercase()

        /** Ostatni folder + nazwa pliku. */
        fun tail(path: String): String = normalizePath(path).split('/').filter { it.isNotEmpty() }.takeLast(2).joinToString("/")

        fun nameKey(title: String, artist: String) = normalizeForSearch(title) + "|" + normalizeForSearch(artist)
    }
}
