package io.github.marekmaro.equinox

import java.text.Normalizer
import kotlin.math.ln
import kotlin.random.Random

/**
 * Znaczniki utworów. Każdy tworzy własną playlistę automatyczną.
 * ♥ i ★ to klasyczne „ulubione”; ☼ i ☾ pasują do Equinox: muzyka na dzień (energia)
 * i na noc (spokój).
 */
enum class Mark(val glyph: String, private val pl: String, private val en: String, private val notePl: String, private val noteEn: String) {
    HEART("♥", "Ulubione", "Favourites", "to, co kochasz", "songs you love"),
    STAR("★", "Perełki", "Gems", "najlepsze z najlepszych", "the best of the best"),
    SUN("☼", "Na dzień", "For the day", "energia, ruch, trening", "energy, movement, workouts"),
    MOON("☾", "Na noc", "For the night", "spokój, wieczór, sen", "calm, evening, sleep");

    val label: String get() = tr(pl, en)
    val note: String get() = tr(notePl, noteEn)
}

/** Znaczki utworu do wyświetlenia na liście, np. „♥★”. */
fun marksGlyphs(marks: Set<Mark>?): String = marks?.sortedBy { it.ordinal }?.joinToString("") { it.glyph }.orEmpty()

// ---------------------------------------------------------------- Wyszukiwanie

/**
 * Tekst do porównań: małe litery, bez polskich znaków i akcentów,
 * więc „burza” znajdzie „Burzą”, a „zolw” – „Żółw”.
 */
fun normalizeForSearch(s: String): String =
    Normalizer.normalize(s.lowercase().replace('ł', 'l'), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

/** Czy wszystkie słowa zapytania pasują (do początku słowa albo wewnątrz tekstu). */
fun matchesQuery(text: String, query: String): Boolean {
    val t = normalizeForSearch(text)
    return normalizeForSearch(query).split(' ').filter { it.isNotBlank() }.all { t.contains(it) }
}

// ---------------------------------------------------------------- Mądre losowanie

/** Minimum potrzebne do losowania (bez zależności od Androida – łatwo testować). */
data class ShuffleItem(val id: Long, val artist: String, val album: String, val weight: Double)

/**
 * Mądre losowanie:
 * 1. losuje z wagami – utwory oznaczone ♥ ★ pojawiają się wcześniej, niedawno słuchane
 *    trafiają raczej na koniec,
 * 2. potem rozkłada wykonawców, żeby dwie piosenki tego samego artysty nie szły pod rząd
 *    (jeśli to w ogóle możliwe), a w drugiej kolejności – z tego samego albumu.
 */
fun smartShuffle(items: List<ShuffleItem>, random: Random = Random.Default): List<ShuffleItem> {
    if (items.size < 3) return items.shuffled(random)
    // losowanie z wagami (Efraimidis–Spirakis): klucz = u^(1/w), sortowanie malejąco
    val ordered = items.sortedByDescending { ln(random.nextDouble().coerceAtLeast(1e-12)) / it.weight.coerceAtLeast(0.05) }
    // rozkład wykonawców: zawsze bierz pierwszy z kolejki, który nie powtarza poprzedniego
    val pool = ordered.toMutableList()
    val out = ArrayList<ShuffleItem>(items.size)
    while (pool.isNotEmpty()) {
        val prev = out.lastOrNull()
        val i = when {
            prev == null -> 0
            else -> pool.indexOfFirst { it.artist != prev.artist && (it.album.isBlank() || it.album != prev.album) }
                .takeIf { it >= 0 }
                ?: pool.indexOfFirst { it.artist != prev.artist }.takeIf { it >= 0 }
                ?: 0
        }
        out += pool.removeAt(i)
    }
    return out
}

/** Waga utworu do mądrego losowania. */
fun shuffleWeight(marks: Set<Mark>?, recentRank: Int?): Double {
    var w = 1.0
    if (marks != null) {
        if (Mark.HEART in marks) w *= 1.8
        if (Mark.STAR in marks) w *= 2.2
    }
    // zagrany przed chwilą (pozycja 0 = ostatni) – mocno w dół, z czasem coraz mniej
    if (recentRank != null) w *= 0.15 + 0.85 * (recentRank / 100.0).coerceIn(0.0, 1.0)
    return w
}
