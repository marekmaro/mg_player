package io.github.marekmaro.equinox

/**
 * Klocki ekranu odtwarzania. Każdy klocek jest niezależny: można go włączyć,
 * wyłączyć i przestawić. Nowy klocek = nowa pozycja tutaj + jego wygląd
 * w ui/Blocks.kt. Pierścień jest zawsze na górze, więc nie ma go na liście.
 */
enum class Block(
    val label: String,
    val description: String,
    val onByDefault: Boolean,
    /** klocka nie da się wyłączyć (bez niego ekran nie ma sensu) */
    val required: Boolean = false,
) {
    TITLE("Tytuł i czas", "Co gra, kto to wykonuje i ile minęło", true, required = true),
    MODES("Tryby pierścienia", "Przewiń / Utwory / Głośność", true),
    CONTROLS("Przyciski", "Poprzedni, pauza, następny", true),
    BOOK("Audiobook", "±30 s i prędkość (tylko przy książkach)", true),
    FACTS("Ciekawostki", "Fakty o wykonawcy lub autorze", true),
    QUEUE("Następne", "Trzy kolejne utwory lub rozdziały", false),
    TODAY("Dziś", "Ile dziś słuchałeś i seria dni", false),
    CLOCK("Zegar", "Duży zegar, np. przy łóżku", false),
    SESSIONS("Sesje", "Skrót do snu i stopera", true);

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
