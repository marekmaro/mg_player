package io.github.marekmaro.equinox.data

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
)

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
        )
        if (hasBookFlag) columns += MediaStore.Audio.Media.IS_AUDIOBOOK

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
                val iBook = if (hasBookFlag) c.getColumnIndex(MediaStore.Audio.Media.IS_AUDIOBOOK) else -1

                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val rawPath = c.getString(iData) ?: ""
                    val path = rawPath.lowercase()
                    val flagged = iBook >= 0 && c.getInt(iBook) != 0
                    val artist = c.getString(iArtist)
                    result += Track(
                        id = id,
                        title = c.getString(iTitle) ?: "Bez tytułu",
                        artist = if (artist.isNullOrBlank() || artist == "<unknown>") "Nieznany wykonawca" else artist,
                        album = c.getString(iAlbum) ?: "",
                        trackNo = c.getInt(iTrack),
                        durationMs = c.getLong(iDuration),
                        uri = ContentUris.withAppendedId(collection, id),
                        isBook = flagged || bookHints.any { path.contains(it) },
                        folder = rawPath.substringBeforeLast('/', ""),
                    )
                }
            }
        } catch (e: SecurityException) {
            // brak uprawnień: zwracamy pustą listę
        }
        return result
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
