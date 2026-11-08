package io.github.marekmaro.equinox.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Station(val name: String, val url: String, val country: String)

/** Wyszukiwarka stacji: darmowe, otwarte API radio-browser.info. */
object RadioRepository {

    private const val BASE = "https://all.api.radio-browser.info/json/stations/search"

    fun isPlaylistLink(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".pls") || path.endsWith(".m3u")
    }

    /** Z listy .pls / .m3u wyciąga pierwszy adres http(s) strumienia. */
    suspend fun resolvePlaylist(url: String): String? = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Equinox/0.5")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            val text = conn.inputStream.bufferedReader().use { it.readText().take(64_000) }
            parsePlaylist(text)
        } finally {
            conn.disconnect()
        }
    }

    /** Parsowanie: w .pls linie „File1=http…”, w .m3u zwykłe linie z adresem. */
    fun parsePlaylist(text: String): String? =
        text.lineSequence()
            .map { it.trim() }
            .map { if (it.contains('=') && it.lowercase().startsWith("file")) it.substringAfter('=').trim() else it }
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }

    suspend fun search(query: String): List<Station> = withContext(Dispatchers.IO) {
        val common = "order=clickcount&reverse=true&hidebroken=true&limit=60"
        val url = if (query.isBlank()) {
            "$BASE?countrycode=PL&$common"
        } else {
            "$BASE?name=${URLEncoder.encode(query.trim(), "UTF-8")}&$common"
        }
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Equinox/0.5")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val stream = o.optString("url_resolved").ifBlank { o.optString("url") }
                val name = o.optString("name").trim()
                if (stream.isBlank() || name.isBlank()) null
                else Station(name = name, url = stream, country = o.optString("country"))
            }.distinctBy { it.url }
                // ta sama stacja bywa w bazie kilka razy (różne strumienie) – zostaw najpopularniejszą
                .distinctBy { it.name.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "") }
        } finally {
            conn.disconnect()
        }
    }
}
