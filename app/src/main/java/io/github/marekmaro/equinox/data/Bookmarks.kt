package io.github.marekmaro.equinox.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Zakładka w audiobooku: książka, plik (rozdział) w kolejce, miejsce i krótka notatka.
 * [label] to opis miejsca w chwili zapisu (np. „Rozdział 3 · Przyjazd”), żeby lista
 * była czytelna nawet bez otwierania książki.
 */
data class Bookmark(
    val id: Long,
    val bookKey: String,
    val itemIndex: Int,
    val positionMs: Long,
    val label: String,
    val note: String,
    val createdAt: Long,
)

object Bookmarks {

    /** Na książkę – żeby lista w oknie pozostała krótka i szybka. */
    const val MAX_PER_BOOK = 200

    fun encode(list: List<Bookmark>): String = JSONArray().apply {
        list.forEach {
            put(
                JSONObject()
                    .put("id", it.id)
                    .put("book", it.bookKey)
                    .put("i", it.itemIndex)
                    .put("pos", it.positionMs)
                    .put("label", it.label)
                    .put("note", it.note)
                    .put("at", it.createdAt)
            )
        }
    }.toString()

    fun decode(text: String?): List<Bookmark> = try {
        val a = JSONArray(text ?: "[]")
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val book = o.optString("book")
            if (book.isEmpty()) null else Bookmark(
                id = o.optLong("id"),
                bookKey = book,
                itemIndex = o.optInt("i").coerceAtLeast(0),
                positionMs = o.optLong("pos").coerceAtLeast(0),
                label = o.optString("label"),
                note = o.optString("note"),
                createdAt = o.optLong("at"),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** Zakładki jednej książki w kolejności czytania (rozdział, potem miejsce). */
    fun forBook(all: List<Bookmark>, bookKey: String?): List<Bookmark> =
        if (bookKey == null) emptyList()
        else all.filter { it.bookKey == bookKey }.sortedWith(compareBy({ it.itemIndex }, { it.positionMs }))

    /**
     * Dodaje zakładkę. Zakładka w tym samym miejscu (±3 s) zastępuje poprzednią zamiast
     * tworzyć duplikat – np. gdy ktoś dwa razy dotknie przycisku.
     */
    fun add(all: List<Bookmark>, b: Bookmark): List<Bookmark> {
        val rest = all.filterNot {
            it.bookKey == b.bookKey && it.itemIndex == b.itemIndex && kotlin.math.abs(it.positionMs - b.positionMs) <= 3_000
        }
        val sameBook = rest.filter { it.bookKey == b.bookKey }
        // limit na książkę: najstarsze wypadają
        val drop = if (sameBook.size >= MAX_PER_BOOK) sameBook.sortedBy { it.createdAt }.take(sameBook.size - MAX_PER_BOOK + 1).map { it.id }.toSet() else emptySet()
        return rest.filterNot { it.id in drop } + b
    }
}
