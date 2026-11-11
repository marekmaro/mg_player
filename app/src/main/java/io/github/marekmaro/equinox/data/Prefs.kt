package io.github.marekmaro.equinox.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Proste trwałe ustawienia: postępy audiobooków, wyzwanie, ulubione stacje. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("equinox", Context.MODE_PRIVATE)

    // --- Audiobooki: zapamiętana pozycja dla każdej książki ---
    fun saveBook(key: String, chapterIndex: Int, positionMs: Long) {
        sp.edit()
            .putInt("book_i_$key", chapterIndex)
            .putLong("book_p_$key", positionMs)
            .apply()
    }

    fun bookChapter(key: String): Int = sp.getInt("book_i_$key", 0)
    fun bookPosition(key: String): Long = sp.getLong("book_p_$key", 0L)

    var bookSpeed: Float
        get() = sp.getFloat("book_speed", 1f)
        set(v) = sp.edit().putFloat("book_speed", v).apply()

    // --- Wyzwanie: minuty słuchania książek dziennie ---
    var goalMinutes: Int
        get() = sp.getInt("goal_min", 20)
        set(v) = sp.edit().putInt("goal_min", v).apply()

    fun listened(day: LocalDate): Long = sp.getLong("listen_$day", 0L)

    fun addListened(day: LocalDate, ms: Long) {
        sp.edit().putLong("listen_$day", listened(day) + ms).apply()
    }

    /** Ile dni z rzędu cel został osiągnięty (dzisiejszy dzień liczy się, jeśli już zaliczony). */
    fun streak(): Int {
        val goal = goalMinutes * 60_000L
        var day = LocalDate.now()
        if (listened(day) < goal) day = day.minusDays(1)
        var count = 0
        while (listened(day) >= goal && count < 3650) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    // --- Statystyki ---
    var showStats: Boolean
        get() = sp.getBoolean("show_stats", true)
        set(v) = sp.edit().putBoolean("show_stats", v).apply()

    fun statsHours(): LongArray = try {
        val a = JSONArray(sp.getString("stats_hours", "[]") ?: "[]")
        LongArray(24) { if (it < a.length()) a.optLong(it) else 0L }
    } catch (e: Exception) { LongArray(24) }

    fun setStatsHours(h: LongArray) {
        sp.edit().putString("stats_hours", JSONArray(h.toList()).toString()).apply()
    }

    fun statsArtists(): Pair<String, Map<String, Long>> = try {
        val o = JSONObject(sp.getString("stats_artists", "{}") ?: "{}")
        val m = o.optJSONObject("m") ?: JSONObject()
        o.optString("week") to m.keys().asSequence().associateWith { m.optLong(it) }
    } catch (e: Exception) { "" to emptyMap() }

    fun setStatsArtists(week: String, m: Map<String, Long>) {
        val mo = JSONObject()
        m.forEach { (k, v) -> mo.put(k, v) }
        sp.edit().putString("stats_artists", JSONObject().put("week", week).put("m", mo).toString()).apply()
    }

    // --- Pokrętło: czułość (0 spokojne, 1 zwykłe, 2 szybkie) i wibracje ---
    var knobSpeed: Int
        get() = sp.getInt("knob_speed", 1)
        set(v) = sp.edit().putInt("knob_speed", v).apply()

    var knobHaptics: Boolean
        get() = sp.getBoolean("knob_haptics", true)
        set(v) = sp.edit().putBoolean("knob_haptics", v).apply()

    // --- Środek pierścienia: okładka (true) albo opis wykonawcy ---
    var showCovers: Boolean
        get() = sp.getBoolean("show_covers", true)
        set(v) = sp.edit().putBoolean("show_covers", v).apply()

    // --- Brzmienie ---
    var soundPreset: String
        get() = sp.getString("sound_preset", "PURE") ?: "PURE"
        set(v) = sp.edit().putString("sound_preset", v).apply()

    var voiceForBooks: Boolean
        get() = sp.getBoolean("voice_for_books", true)
        set(v) = sp.edit().putBoolean("voice_for_books", v).apply()

    // --- Ostatnia sesja odtwarzania (do wznowienia po zamknięciu aplikacji) ---
    var lastSession: String?
        get() = sp.getString("last_session", null)
        set(v) = sp.edit().putString("last_session", v).apply()

    // --- Ulubione utwory (id) ---
    fun favoriteTracks(): Set<Long> = try {
        val a = JSONArray(sp.getString("fav_tracks", "[]") ?: "[]")
        (0 until a.length()).map { a.getLong(it) }.toSet()
    } catch (e: Exception) { emptySet() }

    fun setFavoriteTracks(ids: Set<Long>) {
        sp.edit().putString("fav_tracks", JSONArray(ids.toList()).toString()).apply()
    }

    // --- Liczba odtworzeń (id -> ile razy) ---
    fun playCounts(): Map<Long, Int> = try {
        val o = JSONObject(sp.getString("play_counts", "{}") ?: "{}")
        o.keys().asSequence().mapNotNull { k -> k.toLongOrNull()?.let { it to o.optInt(k) } }.toMap()
    } catch (e: Exception) { emptyMap() }

    fun setPlayCounts(m: Map<Long, Int>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k.toString(), v) }
        sp.edit().putString("play_counts", o.toString()).apply()
    }

    // --- Język: SYSTEM / PL / EN ---
    var language: String
        get() = sp.getString("language", "SYSTEM") ?: "SYSTEM"
        set(v) = sp.edit().putString("language", v).apply()

    // --- Reguły folderów: ścieżka -> Muzyka / Audiobook / Pomiń ---
    fun folderRules(): Map<String, FolderRule> {
        val o = try { JSONObject(sp.getString("folder_rules", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        return o.keys().asSequence().mapNotNull { k ->
            FolderRule.entries.firstOrNull { it.name == o.optString(k) }?.let { k to it }
        }.toMap()
    }

    fun setFolderRules(rules: Map<String, FolderRule>) {
        val o = JSONObject()
        rules.forEach { (k, v) -> if (v != FolderRule.AUTO) o.put(k, v.name) }
        sp.edit().putString("folder_rules", o.toString()).apply()
    }

    // --- Playlisty ---
    fun playlists(): List<Playlist> {
        val arr = try { JSONArray(sp.getString("playlists", "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val ids = o.optJSONArray("tracks") ?: JSONArray()
            Playlist(
                id = o.optLong("id"),
                name = o.optString("name"),
                trackIds = (0 until ids.length()).map { ids.getLong(it) },
            )
        }
    }

    fun setPlaylists(list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("tracks", JSONArray(p.trackIds)))
        }
        sp.edit().putString("playlists", arr.toString()).apply()
    }

    // --- Klocki ekranu odtwarzania (kolejność, przecinki) ---
    var playerLayout: String?
        get() = sp.getString("player_layout", null)
        set(v) = sp.edit().putString("player_layout", v).apply()

    var batteryHintHidden: Boolean
        get() = sp.getBoolean("battery_hint_hidden", false)
        set(v) = sp.edit().putBoolean("battery_hint_hidden", v).apply()

    var tutorialSeen: Boolean
        get() = sp.getBoolean("tutorial_seen", false)
        set(v) = sp.edit().putBoolean("tutorial_seen", v).apply()

    // --- Motyw: AUTO / DAY / NIGHT ---
    var themeMode: String
        get() = sp.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
        set(v) = sp.edit().putString("theme_mode", v).apply()

    // --- Sygnał końca stopera ---
    var chime: String
        get() = sp.getString("chime", "CHIP") ?: "CHIP"
        set(v) = sp.edit().putString("chime", v).apply()

    // --- Ciekawostki o wykonawcach i autorach ---
    var factsEnabled: Boolean
        get() = sp.getBoolean("facts_on", true)
        set(v) = sp.edit().putBoolean("facts_on", v).apply()

    fun cachedFacts(key: String): Facts? = sp.getString("facts_$key", null)?.let { Facts.fromJson(it) }

    fun cacheFacts(key: String, facts: Facts) {
        sp.edit().putString("facts_$key", facts.toJson()).apply()
    }

    // --- Radio: ulubione stacje ---
    fun favorites(): List<Station> {
        val arr = JSONArray(sp.getString("radio_fav", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Station(o.optString("name"), o.optString("url"), o.optString("country"))
        }
    }

    fun setFavorites(list: List<Station>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("name", it.name).put("url", it.url).put("country", it.country))
        }
        sp.edit().putString("radio_fav", arr.toString()).apply()
    }
}
