package io.github.marekmaro.equinox

/**
 * Klocki ekranu odtwarzania. Każdy klocek jest niezależny: można go włączyć,
 * wyłączyć i przestawić. Nowy klocek = nowa pozycja tutaj + jego wygląd
 * w ui/Blocks.kt. Pierścień jest zawsze na górze, więc nie ma go na liście.
 */
enum class Block(
    private val pl: String,
    private val en: String,
    private val descPl: String,
    private val descEn: String,
    val onByDefault: Boolean,
    /** klocka nie da się wyłączyć (bez niego ekran nie ma sensu) */
    val required: Boolean = false,
) {
    MODES("Tryby pierścienia", "Ring modes", "Przewiń / Utwory / Głośność", "Seek / Songs / Volume", true),
    TITLE("Tytuł i czas", "Title and time", "Co gra, kto to wykonuje i ile minęło", "What’s playing, who it’s by and how far in", true, required = true),
    // przyciski są teraz na tarczy w środku pierścienia; duże przyciski można włączyć
    CONTROLS("Duże przyciski", "Big buttons", "Poprzedni, pauza, następny pod pierścieniem", "Previous, pause, next below the ring", false),
    BOOK("Audiobook", "Audiobook", "±30 s i prędkość (tylko przy książkach)", "±30 s and speed (books only)", true),
    FACTS("Ciekawostki", "Fun facts", "Fakty o wykonawcy lub autorze", "Facts about the artist or author", true),
    QUEUE("Następne", "Up next", "Trzy kolejne utwory lub rozdziały", "The next three songs or chapters", false),
    TODAY("Dziś", "Today", "Ile dziś słuchałeś i seria dni", "How much you listened today and your streak", false),
    CLOCK("Zegar", "Clock", "Duży zegar, np. przy łóżku", "A big clock, e.g. on your nightstand", false),
    SESSIONS("Sesje", "Sessions", "Skrót do snu i stopera", "Shortcut to sleep and the timer", true);

    val label: String get() = tr(pl, en)
    val description: String get() = tr(descPl, descEn)

    companion object {
        val defaultLayout: List<Block> = entries.filter { it.onByDefault }

        /** Odczyt zapisanego układu; nieznane nazwy pomijamy, wymagane klocki dokładamy. */
        fun parse(saved: String?): List<Block> {
            if (saved.isNullOrBlank()) return defaultLayout
            val list = saved.split(',').mapNotNull { n -> entries.firstOrNull { it.name == n.trim() } }.distinct()
            val missing = entries.filter { it.required && it !in list }
            return (missing + list).ifEmpty { defaultLayout }
        }

        fun serialize(layout: List<Block>): String = layout.joinToString(",") { it.name }
    }
}
