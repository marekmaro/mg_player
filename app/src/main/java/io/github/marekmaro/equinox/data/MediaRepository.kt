package io.github.marekmaro.equinox.data

import io.github.marekmaro.equinox.tr
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val trackNo: Int,
    val durationMs: Long,
    val uri: Uri,
    val isBook: Boolean,
    /** folder, w którym leży plik (do reguł Muzyka / Audiobook / Pomiń) */
    val folder: String = "",
    /** kiedy plik trafił na telefon (sekundy), do „Ostatnio dodane” */
    val dateAdded: Long = 0,
    /** pełna ścieżka pliku – do odnajdywania utworów przy odtwarzaniu kopii zapasowej */
    val path: String = "",
    /** gatunki ze znaczników (jeden plik może mieć kilka, np. „Rock; Blues”) */
    val genres: List<String> = emptyList(),
    /** rok nagrania, 0 = nieznany */
    val year: Int = 0,
)

/** Gatunki i lata: porządkowanie tego, co zapisano w znacznikach plików. */
object TrackTags {

    /**
     * „Rock; Blues” → [Rock, Blues]. Pomijamy puste wpisy i stare numery ID3v1 bez nazwy
     * („(17)”), a pisownię ujednolicamy, żeby „rock” i „Rock” były jednym gatunkiem.
     */
    fun splitGenres(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';', '\u0000', '|')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.matches(Regex("\\(?\\d+\\)?")) && !it.equals("<unknown>", true) }
            .map { g -> g.split(' ').joinToString(" ") { w -> w.replaceFirstChar { c -> c.titlecase() } } }
            .distinctBy { it.lowercase() }
    }

    /** Rok ze znacznika: dopuszczamy 1900–2100 (MediaStore potrafi zwrócić np. 0 albo 20240101). */
    fun cleanYear(raw: Int): Int = when {
        raw in 1900..2100 -> raw
        raw in 19000101..21001231 -> raw / 10000
        else -> 0
    }

    /** Początek dekady: 1987 → 1980. */
    fun decadeOf(year: Int): Int = if (year <= 0) 0 else year / 10 * 10

    /** „Lata 80.” / „1980s”, dla starszych „Lata 1950.” brzmiałoby źle, więc pełny rok. */
    fun decadeLabel(decade: Int): String = when {
        decade >= 2000 -> io.github.marekmaro.equinox.tr("Lata ${decade}", "${decade}s")
        decade >= 1920 -> io.github.marekmaro.equinox.tr("Lata ${(decade % 100).toString().padStart(2, '0')}.", "${decade}s")
        else -> io.github.marekmaro.equinox.tr("Przed 1920", "Before 1920")
    }
}

/** Audiobook = jeden album / folder. Rozdziały to kolejne pliki. */
data class Book(
    val key: String,
    val title: String,
    val author: String,
    val chapters: List<Track>,
) {
    val totalMs: Long get() = chapters.sumOf { it.durationMs }
}

fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build()
        )
        .build()

object MediaRepository {

    /** Fragmenty ścieżki, po których rozpoznajemy audiobooki. */
    private val bookHints = listOf(
        "audiobook", "audiobooki", "audiobooks", "/audio books/",
        "ksiazk", "książk", "/books/", "/lektury/",
    )

    fun load(context: Context): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val hasBookFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val columns = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DURATION,
            @Suppress("DEPRECATION") MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.YEAR,
        )
        if (hasBookFlag) columns += MediaStore.Audio.Media.IS_AUDIOBOOK
        // gatunek jest kolumną utworu od Androida 11; wcześniej trzeba go złożyć z osobnej tabeli
        val hasGenreColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        if (hasGenreColumn) columns += MediaStore.Audio.Media.GENRE
        val legacyGenres = if (hasGenreColumn) emptyMap() else legacyGenres(context)

        val result = mutableListOf<Track>()
        try {
            context.contentResolver.query(
                collection,
                columns.toTypedArray(),
                "${MediaStore.Audio.Media.DURATION} >= ?",
                arrayOf("30000"), // pomijamy dźwięki krótsze niż 30 s (dzwonki, powiadomienia)
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val iDuration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                @Suppress("DEPRECATION")
                val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val iBook = if (hasBookFlag) c.getColumnIndex(MediaStore.Audio.Media.IS_AUDIOBOOK) else -1
                val iYear = c.getColumnIndex(MediaStore.Audio.Media.YEAR)
                val iGenre = if (hasGenreColumn) c.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1

                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val rawPath = c.getString(iData) ?: ""
                    val path = rawPath.lowercase()
                    val flagged = iBook >= 0 && c.getInt(iBook) != 0
                    val artist = c.getString(iArtist)
                    result += Track(
                        id = id,
                        title = c.getString(iTitle) ?: tr("Bez tytułu", "Untitled"),
                        artist = if (artist.isNullOrBlank() || artist == "<unknown>") tr("Nieznany wykonawca", "Unknown artist") else artist,
                        album = c.getString(iAlbum) ?: "",
                        trackNo = c.getInt(iTrack),
                        durationMs = c.getLong(iDuration),
                        uri = ContentUris.withAppendedId(collection, id),
                        isBook = flagged || bookHints.any { path.contains(it) },
                        folder = rawPath.substringBeforeLast('/', ""),
                        dateAdded = c.getLong(iAdded),
                        path = rawPath,
                        genres = if (iGenre >= 0) TrackTags.splitGenres(c.getString(iGenre)) else legacyGenres[id].orEmpty(),
                        year = if (iYear >= 0) TrackTags.cleanYear(c.getInt(iYear)) else 0,
                    )
                }
            }
        } catch (e: SecurityException) {
            // brak uprawnień: zwracamy pustą listę
        }
        return result
    }

    /** Android 8–10: id utworu → gatunki, z tabeli gatunków (jedno zapytanie na gatunek). */
    @Suppress("DEPRECATION")
    private fun legacyGenres(context: Context): Map<Long, List<String>> {
        val out = HashMap<Long, MutableList<String>>()
        try {
            context.contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null, null, null,
            )?.use { g ->
                while (g.moveToNext()) {
                    val genreId = g.getLong(0)
                    val names = TrackTags.splitGenres(g.getString(1))
                    if (names.isEmpty()) continue
                    context.contentResolver.query(
                        MediaStore.Audio.Genres.Members.getContentUri("external", genreId),
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                        null, null, null,
                    )?.use { m ->
                        while (m.moveToNext()) out.getOrPut(m.getLong(0)) { mutableListOf() }.addAll(names)
                    }
                }
            }
        } catch (e: Exception) {
            // brak tabeli albo uprawnień – po prostu bez gatunków
        }
        return out.mapValues { (_, v) -> v.distinctBy { it.lowercase() } }
    }

    fun groupBooks(tracks: List<Track>): List<Book> =
        tracks
            .groupBy { it.album.ifBlank { it.title } }
            .map { (album, chapters) ->
                val sorted = chapters.sortedWith(compareBy<Track>({ it.trackNo }, { it.title.lowercase() }))
                Book(key = album, title = album, author = sorted.first().artist, chapters = sorted)
            }
            .sortedBy { it.title.lowercase() }
}
