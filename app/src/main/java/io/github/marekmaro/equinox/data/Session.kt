package io.github.marekmaro.equinox.data

import android.content.ContentUris
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import io.github.marekmaro.equinox.Kind
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ostatnia sesja odtwarzania: co grało, w jakiej kolejności i w którym miejscu.
 * Dzięki temu po zamknięciu aplikacji (albo restarcie telefonu) muzyka, audiobook
 * lub radio wznawiają się dokładnie tam, gdzie zostały zatrzymane.
 */
data class SavedSession(
    val kind: Kind,
    val ids: List<String>,
    val titles: List<String>,
    val artists: List<String>,
    val index: Int,
    val positionMs: Long,
    val bookKey: String?,
    val shuffle: Boolean,
    val repeat: Int,
)

object SessionStore {

    private const val MAX_ITEMS = 500

    fun capture(player: Player, kind: Kind?, bookKey: String?): SavedSession? {
        val n = player.mediaItemCount
        if (n == 0 || kind == null) return null
        val cur = player.currentMediaItemIndex.coerceIn(0, n - 1)
        // przy bardzo długich kolejkach zapisujemy okno wokół bieżącego utworu
        val from = if (n <= MAX_ITEMS) 0 else (cur - MAX_ITEMS / 2).coerceIn(0, n - MAX_ITEMS)
        val to = minOf(n, from + MAX_ITEMS)
        val items = (from until to).map { player.getMediaItemAt(it) }
        return SavedSession(
            kind = kind,
            ids = items.map { it.mediaId },
            titles = items.map { it.mediaMetadata.title?.toString().orEmpty() },
            artists = items.map { it.mediaMetadata.artist?.toString().orEmpty() },
            index = cur - from,
            positionMs = if (kind == Kind.RADIO) 0 else player.currentPosition.coerceAtLeast(0),
            bookKey = bookKey,
            shuffle = player.shuffleModeEnabled,
            repeat = player.repeatMode,
        )
    }

    fun encode(s: SavedSession): String = JSONObject()
        .put("kind", s.kind.name)
        .put("ids", JSONArray(s.ids))
        .put("titles", JSONArray(s.titles))
        .put("artists", JSONArray(s.artists))
        .put("index", s.index)
        .put("pos", s.positionMs)
        .put("book", s.bookKey ?: "")
        .put("shuffle", s.shuffle)
        .put("repeat", s.repeat)
        .toString()

    fun decode(text: String?): SavedSession? = try {
        if (text.isNullOrBlank()) null else {
            val o = JSONObject(text)
            fun arr(name: String): List<String> {
                val a = o.optJSONArray(name) ?: JSONArray()
                return (0 until a.length()).map { a.optString(it) }
            }
            val ids = arr("ids")
            if (ids.isEmpty()) null else SavedSession(
                kind = Kind.valueOf(o.optString("kind", "MUSIC")),
                ids = ids,
                titles = arr("titles"),
                artists = arr("artists"),
                index = o.optInt("index").coerceIn(0, ids.lastIndex),
                positionMs = o.optLong("pos"),
                bookKey = o.optString("book").ifBlank { null },
                shuffle = o.optBoolean("shuffle"),
                repeat = o.optInt("repeat"),
            )
        }
    } catch (e: Exception) {
        null
    }

    /** Odtwarza listę pozycji z zapisu (bez dostępu do biblioteki – np. dla wznowienia z systemu). */
    fun toMediaItems(s: SavedSession): List<MediaItem> = s.ids.mapIndexed { i, id ->
        val uri = if (id.startsWith("radio:")) android.net.Uri.parse(id.removePrefix("radio:"))
        else ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toLongOrNull() ?: -1)
        MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(s.titles.getOrNull(i))
                    .setArtist(s.artists.getOrNull(i))
                    .build()
            )
            .build()
    }
}
