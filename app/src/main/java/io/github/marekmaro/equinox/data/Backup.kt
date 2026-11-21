package io.github.marekmaro.equinox.data

import org.json.JSONArray
import org.json.JSONObject

/** Utwór opisany tak, żeby dało się go odnaleźć na innym telefonie (id z MediaStore się zmieniają). */
data class TrackRef(val id: Long, val path: String, val title: String, val artist: String, val durationMs: Long)

/** Wynik wczytania kopii: ustawienia gotowe do zapisu i ile utworów udało się odnaleźć. */
data class RestoreResult(val values: Map<String, Any>, val tracksFound: Int, val tracksMissing: Int)

/**
 * Kopia zapasowa do jednego pliku JSON: playlisty, znaczniki ♥ ★ ☼ ☾, liczba odtworzeń,
 * ulubione stacje, reguły folderów, pozycje audiobooków, statystyki i ustawienia.
 *
 * Utwory są zapisane z opisem (ścieżka, tytuł, wykonawca, długość), więc po przeniesieniu
 * na nowy telefon lub po ponownej instalacji odnajdujemy je przez [TrackMatcher].
 */
object Backup {

    const val FORMAT = 1

    /** Klucze, w których zapisane są id utworów (trzeba je przenumerować przy odtwarzaniu). */
    private val TRACK_KEYS = setOf("playlists", "track_marks", "fav_tracks", "play_counts", "recent_played")

    /** Nie przenosimy: chwilowej sesji odtwarzania i pobranych opisów z Wikipedii (pobiorą się same). */
    fun isBackedUp(key: String): Boolean = key != "last_session" && !key.startsWith("facts_")

    fun export(prefs: Map<String, *>, library: List<TrackRef>, createdIso: String): String {
        val values = JSONObject()
        prefs.toSortedMap().forEach { (k, v) ->
            if (!isBackedUp(k) || v == null) return@forEach
            val (t, value) = when (v) {
                is String -> "s" to v
                is Boolean -> "b" to v
                is Int -> "i" to v
                is Long -> "l" to v
                is Float -> "f" to v.toDouble()
                else -> return@forEach // np. Set<String> – nie używamy
            }
            values.put(k, JSONObject().put("t", t).put("v", value))
        }
        // opisy wszystkich utworów, do których odwołują się playlisty, znaczniki itd.
        val used = HashSet<Long>()
        TRACK_KEYS.forEach { k -> (prefs[k] as? String)?.let { used += idsIn(k, it) } }
        val tracks = JSONObject()
        library.filter { it.id in used }.forEach { t ->
            tracks.put(t.id.toString(), JSONObject().put("path", t.path).put("title", t.title).put("artist", t.artist).put("dur", t.durationMs))
        }
        return JSONObject()
            .put("app", "Equinox")
            .put("format", FORMAT)
            .put("created", createdIso)
            .put("prefs", values)
            .put("tracks", tracks)
            .toString(1)
    }

    /** Czy kopia zawiera utwory (playlisty, znaczniki…), które trzeba odnaleźć w bibliotece. */
    fun hasTracks(text: String): Boolean = try {
        (JSONObject(text).optJSONObject("tracks")?.length() ?: 0) > 0
    } catch (e: Exception) { false }

    /** Czyta kopię i dopasowuje utwory do biblioteki na tym telefonie. Rzuca wyjątek, gdy plik nie jest kopią Equinox. */
    fun restore(text: String, library: List<TrackRef>): RestoreResult {
        val root = JSONObject(text)
        require(root.optString("app") == "Equinox") { "not an Equinox backup" }
        require(root.optInt("format", 0) in 1..FORMAT) { "unsupported format" }

        // stare id -> nowe id
        val matcher = TrackMatcher(library)
        val saved = root.optJSONObject("tracks") ?: JSONObject()
        val map = HashMap<Long, Long>()
        var missing = 0
        saved.keys().forEach { k ->
            val old = k.toLongOrNull() ?: return@forEach
            val o = saved.optJSONObject(k) ?: return@forEach
            val found = matcher.find(o.optString("path"), o.optString("title"), o.optString("artist"), o.optLong("dur"))
            if (found != null) map[old] = found.id else missing++
        }

        val values = LinkedHashMap<String, Any>()
        val prefs = root.optJSONObject("prefs") ?: JSONObject()
        prefs.keys().forEach { k ->
            if (!isBackedUp(k)) return@forEach
            val o = prefs.optJSONObject(k) ?: return@forEach
            val v: Any = when (o.optString("t")) {
                "s" -> o.optString("v").let { if (k in TRACK_KEYS) remap(k, it, map) else it }
                "b" -> o.optBoolean("v")
                "i" -> o.optInt("v")
                "l" -> o.optLong("v")
                "f" -> o.optDouble("v").toFloat()
                else -> return@forEach
            }
            values[k] = v
        }
        return RestoreResult(values, map.size, missing)
    }

    // ---------------------------------------------------------------- pomocnicze

    private fun idsIn(key: String, json: String): List<Long> = try {
        when (key) {
            "playlists" -> JSONArray(json).let { a ->
                (0 until a.length()).flatMap { i ->
                    val ids = a.optJSONObject(i)?.optJSONArray("tracks") ?: JSONArray()
                    (0 until ids.length()).map { ids.optLong(it) }
                }
            }
            "track_marks", "play_counts" -> JSONObject(json).keys().asSequence().mapNotNull { it.toLongOrNull() }.toList()
            else -> JSONArray(json).let { a -> (0 until a.length()).map { a.optLong(it) } }
        }
    } catch (e: Exception) { emptyList() }

    /** Podmienia stare id na nowe; utwory, których nie ma na tym telefonie, są pomijane. */
    private fun remap(key: String, json: String, map: Map<Long, Long>): String = try {
        when (key) {
            "playlists" -> {
                val a = JSONArray(json)
                val out = JSONArray()
                for (i in 0 until a.length()) {
                    val p = a.optJSONObject(i) ?: continue
                    val ids = p.optJSONArray("tracks") ?: JSONArray()
                    val next = (0 until ids.length()).mapNotNull { map[ids.optLong(it)] }
                    out.put(JSONObject().put("id", p.optLong("id")).put("name", p.optString("name")).put("tracks", JSONArray(next)))
                }
                out.toString()
            }
            "track_marks", "play_counts" -> {
                val o = JSONObject(json)
                val out = JSONObject()
                o.keys().forEach { k -> k.toLongOrNull()?.let { map[it] }?.let { out.put(it.toString(), o.get(k)) } }
                out.toString()
            }
            else -> {
                val a = JSONArray(json)
                JSONArray((0 until a.length()).mapNotNull { map[a.optLong(it)] }.distinct()).toString()
            }
        }
    } catch (e: Exception) { if (key == "track_marks" || key == "play_counts") "{}" else "[]" }
}
