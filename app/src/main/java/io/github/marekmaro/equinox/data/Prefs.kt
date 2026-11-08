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

    // --- Motyw: AUTO / DAY / NIGHT ---
    var themeMode: String
        get() = sp.getString("theme_mode", "AUTO") ?: "AUTO"
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
