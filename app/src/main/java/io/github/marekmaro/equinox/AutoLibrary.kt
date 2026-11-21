package io.github.marekmaro.equinox

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.marekmaro.equinox.data.Library
import io.github.marekmaro.equinox.data.MediaRepository
import io.github.marekmaro.equinox.data.Prefs
import io.github.marekmaro.equinox.data.RadioRepository
import io.github.marekmaro.equinox.data.Track
import io.github.marekmaro.equinox.data.toMediaItem

/**
 * Biblioteka widziana z samochodu (Android Auto) i z innych przeglądarek multimediów.
 *
 * Drzewo:
 *   root → ♥ Ulubione · Ostatnio grane · Playlisty → … · Wykonawcy → … · Albumy → … · Gatunki → … · Audiobooki · Radio
 *
 * Dotknięcie utworu gra całą listę, z której pochodzi (album, playlistę…), od tego utworu.
 * Dotknięcie audiobooka wznawia go w zapamiętanym miejscu.
 *
 * Identyfikatory: foldery to „fav”, „pl:<id>”, „artist:<nazwa>”…; pozycje do odtworzenia
 * to „t|<folder>|<id utworu>”, „b|<książka>” i „r|<adres stacji>”.
 */
class AutoLibrary(private val context: Context) {

    private val prefs = Prefs(context)
    private var cache: List<Track>? = null
    private var cachedAt = 0L

    /** Utwory z telefonu (po regułach folderów); odświeżane co minutę. Wołać poza wątkiem głównym. */
    fun tracks(): List<Track> {
        val now = SystemClock.elapsedRealtime()
        cache?.let { if (now - cachedAt < 60_000) return it }
        val all = Library.applyRules(MediaRepository.load(context), prefs.folderRules())
        cache = all
        cachedAt = now
        return all
    }

    private fun music() = tracks().filter { !it.isBook }

    // ------------------------------------------------------------------ drzewo

    fun root(): MediaItem = folder(ROOT, "Equinox", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

    fun children(parentId: String): List<MediaItem> = when {
        parentId == ROOT -> listOf(
            folder(FAV, "♥ " + tr("Ulubione", "Favourites"), MediaMetadata.MEDIA_TYPE_PLAYLIST),
            folder(RECENT, tr("Ostatnio grane", "Recently played"), MediaMetadata.MEDIA_TYPE_PLAYLIST),
            folder(PLAYLISTS, tr("Playlisty", "Playlists"), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
            folder(ARTISTS, tr("Wykonawcy", "Artists"), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
            folder(ALBUMS, tr("Albumy", "Albums"), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
            folder(GENRES, tr("Gatunki", "Genres"), MediaMetadata.MEDIA_TYPE_FOLDER_GENRES),
            folder(BOOKS, tr("Audiobooki", "Audiobooks"), MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS),
            folder(RADIO, tr("Radio", "Radio"), MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS),
        )
        parentId == PLAYLISTS -> prefs.playlists().map {
            folder("pl:${it.id}", it.name, MediaMetadata.MEDIA_TYPE_PLAYLIST, subtitle = tr("${it.trackIds.size} utw.", "${it.trackIds.size} songs"))
        }
        parentId == ARTISTS -> music().groupBy { it.artist }.toSortedMap(String.CASE_INSENSITIVE_ORDER).map { (name, list) ->
            folder("artist:$name", name, MediaMetadata.MEDIA_TYPE_ARTIST, subtitle = tr("${list.size} utw.", "${list.size} songs"))
        }
        parentId == ALBUMS -> music().filter { it.album.isNotBlank() }.groupBy { it.album }.toSortedMap(String.CASE_INSENSITIVE_ORDER).map { (name, list) ->
            folder("album:$name", name, MediaMetadata.MEDIA_TYPE_ALBUM, subtitle = list.first().artist)
        }
        parentId == GENRES -> music().flatMap { t -> t.genres.map { it to t } }.groupBy { it.first.lowercase() }
            .map { (_, l) -> l.first().first to l.size }
            .sortedBy { it.first.lowercase() }
            .map { (name, n) -> folder("genre:$name", name, MediaMetadata.MEDIA_TYPE_GENRE, subtitle = tr("$n utw.", "$n songs")) }
        parentId == BOOKS -> MediaRepository.groupBooks(tracks().filter { it.isBook }).map { b ->
            playable("b|${b.key}", b.title, b.author, MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
        }
        parentId == RADIO -> prefs.favorites().map { s ->
            playable("r|${s.url}", s.name, s.country.ifBlank { "Radio" }, MediaMetadata.MEDIA_TYPE_RADIO_STATION)
        }
        else -> tracksIn(parentId).map { t ->
            playable("t|$parentId|${t.id}", t.title, t.artist, MediaMetadata.MEDIA_TYPE_MUSIC)
        }
    }

    /** Utwory w folderze (ulubione, playlista, wykonawca, album…), w kolejności do grania. */
    fun tracksIn(parentId: String): List<Track> {
        val music = music()
        val byId = music.associateBy { it.id }
        return when {
            parentId == FAV -> prefs.marks().filterValues { Mark.HEART in it }.keys.mapNotNull { byId[it] }.sortedBy { it.title.lowercase() }
            parentId == RECENT -> prefs.recentPlayed().mapNotNull { byId[it] }.take(50)
            parentId.startsWith("pl:") -> {
                val id = parentId.removePrefix("pl:").toLongOrNull()
                prefs.playlists().firstOrNull { it.id == id }?.trackIds?.mapNotNull { byId[it] }.orEmpty()
            }
            parentId.startsWith("artist:") -> parentId.removePrefix("artist:").let { a ->
                music.filter { it.artist == a }.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNo }, { it.title.lowercase() }))
            }
            parentId.startsWith("genre:") -> parentId.removePrefix("genre:").let { g ->
                music.filter { t -> t.genres.any { it.equals(g, ignoreCase = true) } }
                    .sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNo }))
            }
            parentId.startsWith("album:") -> parentId.removePrefix("album:").let { a ->
                music.filter { it.album == a }.sortedWith(compareBy({ it.trackNo }, { it.title.lowercase() }))
            }
            else -> emptyList()
        }
    }

    fun item(id: String): MediaItem? = when {
        id == ROOT -> root()
        id.startsWith("t|") -> id.substringAfterLast('|').toLongOrNull()?.let { tid ->
            tracks().firstOrNull { it.id == tid }?.let { playable(id, it.title, it.artist, MediaMetadata.MEDIA_TYPE_MUSIC) }
        }
        id.startsWith("pl:") -> children(PLAYLISTS).firstOrNull { it.mediaId == id }
        id.startsWith("artist:") -> folder(id, id.removePrefix("artist:"), MediaMetadata.MEDIA_TYPE_ARTIST)
        id.startsWith("album:") -> folder(id, id.removePrefix("album:"), MediaMetadata.MEDIA_TYPE_ALBUM)
        id.startsWith("genre:") -> folder(id, id.removePrefix("genre:"), MediaMetadata.MEDIA_TYPE_GENRE)
        id.startsWith("b|") -> children(BOOKS).firstOrNull { it.mediaId == id }
        id.startsWith("r|") -> children(RADIO).firstOrNull { it.mediaId == id }
        else -> children(ROOT).firstOrNull { it.mediaId == id }
    }

    // ------------------------------------------------------------------ odtwarzanie

    /** Co zagrać po wybraniu pozycji w samochodzie: lista, od której pozycji i miejsca, i jaki to rodzaj. */
    class Plan(val items: List<MediaItem>, val index: Int, val positionMs: Long, val kind: Kind, val bookKey: String? = null)

    /** Zamienia wybraną pozycję (albo zapytanie głosowe) na listę do odtworzenia. Wołać poza wątkiem głównym. */
    suspend fun plan(item: MediaItem): Plan? {
        val id = item.mediaId
        val query = item.requestMetadata.searchQuery
        return when {
            query != null -> search(query)
            id.startsWith("t|") -> {
                val parent = id.substring(2).substringBeforeLast('|')
                val tid = id.substringAfterLast('|').toLongOrNull()
                val list = tracksIn(parent).ifEmpty { tracks().filter { it.id == tid } }
                if (list.isEmpty()) null
                else Plan(list.map { it.toMediaItem() }, list.indexOfFirst { it.id == tid }.coerceAtLeast(0), 0, Kind.MUSIC)
            }
            id.startsWith("b|") -> {
                val key = id.removePrefix("b|")
                val book = MediaRepository.groupBooks(tracks().filter { it.isBook }).firstOrNull { it.key == key } ?: return null
                val idx = prefs.bookChapter(key).coerceIn(0, book.chapters.lastIndex)
                val pos = (prefs.bookPosition(key) - 5_000).coerceAtLeast(0)
                Plan(book.chapters.map { it.toMediaItem() }, idx, pos, Kind.BOOK, key)
            }
            id.startsWith("r|") -> {
                val url = id.removePrefix("r|")
                val station = prefs.favorites().firstOrNull { it.url == url }
                val stream = if (RadioRepository.isPlaylistLink(url)) (try { RadioRepository.resolvePlaylist(url) } catch (e: Exception) { null } ?: return null) else url
                Plan(
                    listOf(
                        MediaItem.Builder()
                            .setMediaId("radio:$stream")
                            .setUri(stream)
                            .setMediaMetadata(MediaMetadata.Builder().setTitle(station?.name ?: "Radio").setArtist(station?.country?.ifBlank { null } ?: "Radio").build())
                            .build()
                    ),
                    0, 0, Kind.RADIO,
                )
            }
            else -> null
        }
    }

    /** „Odtwórz … w Equinox” w samochodzie: wykonawca, album albo tytuł; puste zapytanie = losowo wszystko. */
    private fun search(query: String): Plan? {
        val music = music()
        if (music.isEmpty()) return null
        val list = if (query.isBlank()) music.shuffled()
        else music.filter { matchesQuery("${it.title} ${it.artist} ${it.album}", query) }
            .sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNo }))
        if (list.isEmpty()) return null
        return Plan(list.map { it.toMediaItem() }, 0, 0, Kind.MUSIC)
    }

    // ------------------------------------------------------------------ pomocnicze

    private fun folder(id: String, title: String, type: Int, subtitle: String? = null) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(type)
                .build()
        )
        .build()

    private fun playable(id: String, title: String, subtitle: String, type: Int) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(subtitle)
                .setSubtitle(subtitle)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(type)
                .build()
        )
        .build()

    companion object {
        const val ROOT = "root"
        const val FAV = "fav"
        const val RECENT = "recent"
        const val PLAYLISTS = "playlists"
        const val ARTISTS = "artists"
        const val ALBUMS = "albums"
        const val GENRES = "genres"
        const val BOOKS = "books"
        const val RADIO = "radio"
    }
}
