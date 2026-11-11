package io.github.marekmaro.equinox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** SYSTEM = język telefonu (polski dla „pl”, w pozostałych przypadkach angielski). */
enum class LangMode { SYSTEM, PL, EN }

object Lang {
    var mode by mutableStateOf(LangMode.SYSTEM)

    val isPl: Boolean
        get() = when (mode) {
            LangMode.PL -> true
            LangMode.EN -> false
            LangMode.SYSTEM -> Locale.getDefault().language == "pl"
        }

    /** Kod języka do zapytań (Wikipedia). */
    val code: String get() = if (isPl) "pl" else "en"
}

/**
 * Tekst w bieżącym języku. Oba warianty stoją obok siebie w kodzie,
 * więc tłumaczenie nigdy się nie „rozjedzie” z oryginałem.
 * Odczyt w Compose sam odświeża ekran po zmianie języka.
 */
fun tr(pl: String, en: String): String = if (Lang.isPl) pl else en
