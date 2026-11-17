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
    // 1. losowanie z wagami (Efraimidis–Spirakis): klucz ln(u)/w liczony RAZ na utwór
    val ordered = items
        .map { it to ln(random.nextDouble().coerceAtLeast(1e-12)) / it.weight.coerceAtLeast(0.05) }
        .sortedByDescending { it.second }
        .map { it.first }
        .toMutableList()

    // 2. rozkład wykonawców z patrzeniem w przód: jeśli któryś wykonawca ma tyle
    //    pozostałych utworów, że inaczej musiałby grać pod rząd – wplatamy go teraz
    val left = ordered.groupingBy { it.artist }.eachCount().toMutableMap()
    val out = ArrayList<ShuffleItem>(items.size)
    while (ordered.isNotEmpty()) {
        val prev = out.lastOrNull()
        val remaining = ordered.size
        val (busiest, busyCount) = left.maxBy { it.value }
        val mustTake = busyCount * 2 > remaining && busiest != prev?.artist
        val i = when {
            prev == null -> 0
            mustTake -> ordered.indexOfFirst { it.artist == busiest }
            else -> ordered.indexOfFirst { it.artist != prev.artist && (it.album.isBlank() || it.album != prev.album) }
                .takeIf { it >= 0 }
                ?: ordered.indexOfFirst { it.artist != prev.artist }.takeIf { it >= 0 }
                ?: 0
        }
        val pick = ordered.removeAt(i)
        out += pick
        left[pick.artist] = (left[pick.artist] ?: 1) - 1
        if (left[pick.artist] == 0) left.remove(pick.artist)
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
