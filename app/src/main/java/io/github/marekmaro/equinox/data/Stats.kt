package io.github.marekmaro.equinox.data

import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * Statystyki słuchania – liczone i przechowywane wyłącznie na telefonie.
 * 1. Zegar słuchania: ile minut słuchasz o każdej godzinie doby.
 * 2. Wykonawca tygodnia: komu poświęcasz najwięcej czasu w bieżącym tygodniu.
 * 3. Odkrycia: jaka część Twojej muzyki zagrała choć raz (liczniki odtworzeń).
 */
object Stats {
    /** Czas słuchania niezapisany jeszcze na dysk (zapis razem z resztą co kilka sekund). */
    val pendingHours = LongArray(24)
    val pendingArtists = mutableMapOf<String, Long>()

    fun add(hour: Int, artist: String?, ms: Long) {
        pendingHours[hour.coerceIn(0, 23)] += ms
        if (!artist.isNullOrBlank()) pendingArtists[artist] = (pendingArtists[artist] ?: 0) + ms
    }

    fun weekKey(date: LocalDate = LocalDate.now()): String =
        "${date.get(IsoFields.WEEK_BASED_YEAR)}-${date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"

    fun flush(prefs: Prefs) {
        if (pendingHours.any { it > 0 }) {
            val h = prefs.statsHours()
            for (i in 0 until 24) h[i] += pendingHours[i]
            prefs.setStatsHours(h)
            pendingHours.fill(0)
        }
        if (pendingArtists.isNotEmpty()) {
            val week = weekKey()
            val (savedWeek, saved) = prefs.statsArtists()
            // nowy tydzień = nowa klasyfikacja
            val merged = (if (savedWeek == week) saved.toMutableMap() else mutableMapOf())
            pendingArtists.forEach { (a, ms) -> merged[a] = (merged[a] ?: 0) + ms }
            prefs.setStatsArtists(week, merged)
            pendingArtists.clear()
        }
    }

    /** Godziny z danymi zapisanymi i jeszcze niezapisanymi. */
    fun hours(prefs: Prefs): LongArray = prefs.statsHours().also { h -> for (i in 0 until 24) h[i] += pendingHours[i] }

    fun artistsThisWeek(prefs: Prefs): Map<String, Long> {
        val (savedWeek, saved) = prefs.statsArtists()
        val base = if (savedWeek == weekKey()) saved.toMutableMap() else mutableMapOf()
        pendingArtists.forEach { (a, ms) -> base[a] = (base[a] ?: 0) + ms }
        return base
    }

    /** Godzina, o której słuchasz najwięcej, albo -1, gdy brak danych. */
    fun peakHour(hours: LongArray): Int = if (hours.all { it == 0L }) -1 else hours.indices.maxBy { hours[it] }
}
