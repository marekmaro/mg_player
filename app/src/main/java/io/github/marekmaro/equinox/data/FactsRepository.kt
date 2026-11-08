package io.github.marekmaro.equinox.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Ciekawostki o wykonawcy lub autorze, z Wikipedii (licencja CC BY-SA). */
data class Facts(
    val name: String,
    val headline: String,
    val sentences: List<String>,
    val pageUrl: String,
    val lang: String,
) {
    fun toJson(): String = JSONObject()
        .put("name", name)
        .put("headline", headline)
        .put("sentences", JSONArray(sentences))
        .put("url", pageUrl)
        .put("lang", lang)
        .toString()

    companion object {
        fun fromJson(s: String): Facts? = try {
            val o = JSONObject(s)
            val arr = o.getJSONArray("sentences")
            Facts(
                name = o.optString("name"),
                headline = o.optString("headline"),
                sentences = (0 until arr.length()).map { arr.getString(it) },
                pageUrl = o.optString("url"),
                lang = o.optString("lang"),
            )
        } catch (e: Exception) {
            null
        }
    }
}

object FactsRepository {

    private const val USER_AGENT = "Equinox/0.5 (https://github.com/marekmaro/mg_player)"

    // Słowa potwierdzające, że znaleziona strona dotyczy muzyka / pisarza,
    // a nie np. miasta albo filmu o tej samej nazwie.
    private val musicWords = listOf(
        "zespół", "grupa muzyczna", "muzyk", "piosenkar", "wokalist", "raper", "raperka",
        "kompozytor", "wokalistka", "duet", "band", "singer", "musician", "rapper",
        "songwriter", "composer", "duo", "dj", "producer", "producent muzyczny",
    )
    private val bookWords = listOf(
        "pisarz", "pisarka", "poeta", "poetka", "prozaik", "dramatopisarz", "powieściopisarz",
        "autor", "autorka", "writer", "novelist", "poet", "author", "playwright",
    )

    /** Pierwszy wykonawca z napisu typu „A feat. B”, „A & B”, „A, B”. */
    fun primaryName(artist: String): String =
        artist.split(Regex("""\s+(feat\.?|ft\.?|x|&|i|and)\s+|,|;|/""", RegexOption.IGNORE_CASE))
            .first()
            .trim()

    /**
     * Zwraca ciekawostki albo null, gdy nic pewnego nie znaleziono.
     * Rzuca wyjątek przy braku internetu (wtedy spróbujemy później).
     */
    suspend fun find(name: String, isBook: Boolean): Facts? = withContext(Dispatchers.IO) {
        val words = if (isBook) bookWords else musicWords
        for (lang in listOf("pl", "en")) {
            val hint = when {
                isBook && lang == "pl" -> "pisarz"
                isBook -> "writer"
                lang == "pl" -> "muzyka"
                else -> "music"
            }
            val found = tryLang(lang, name, hint, words)
            if (found != null) return@withContext found
        }
        null
    }

    private fun tryLang(lang: String, name: String, hint: String, words: List<String>): Facts? {
        val q = URLEncoder.encode("$name $hint", "UTF-8")
        val searchText = get("https://$lang.wikipedia.org/w/api.php?action=query&list=search&srsearch=$q&srlimit=4&format=json")
        val hits = JSONObject(searchText).optJSONObject("query")?.optJSONArray("search") ?: return null
        val wanted = name.lowercase()

        for (i in 0 until hits.length()) {
            val title = hits.getJSONObject(i).optString("title")
            if (!title.lowercase().contains(wanted)) continue

            val path = URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
            val summary = try {
                JSONObject(get("https://$lang.wikipedia.org/api/rest_v1/page/summary/$path"))
            } catch (e: Exception) {
                continue
            }
            if (summary.optString("type") == "disambiguation") continue

            val description = summary.optString("description")
            val extract = summary.optString("extract")
            val probe = (description + " " + extract.take(400)).lowercase()
            if (words.none { probe.contains(it) }) continue

            val sentences = splitSentences(extract)
            if (sentences.isEmpty()) continue

            val url = summary.optJSONObject("content_urls")?.optJSONObject("mobile")?.optString("page")
                ?.takeIf { it.isNotBlank() }
                ?: "https://$lang.wikipedia.org/wiki/$path"

            return Facts(
                name = title,
                headline = description.replaceFirstChar { it.uppercase() },
                sentences = sentences,
                pageUrl = url,
                lang = lang,
            )
        }
        return null
    }

    internal fun splitSentences(text: String): List<String> =
        text.split(Regex("""(?<=[.!?])\s+(?=[A-ZĄĆĘŁŃÓŚŹŻ0-9„"])"""))
            .map { it.trim() }
            .filter { it.length >= 25 }
            .take(5)

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
