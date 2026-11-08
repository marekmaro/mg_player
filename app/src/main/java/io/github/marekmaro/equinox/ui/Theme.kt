package io.github.marekmaro.equinox.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * Equinox = równowaga dnia i nocy. Aplikacja ma dwa oblicza:
 * Noc (grafit, ciepły pomarańcz) i Dzień (jasny papier, przygaszony pomarańcz).
 * W trybie Auto przełącza się sama o świcie i zmierzchu.
 */
/** SYSTEM = jak ustawienia telefonu (domyślnie), AUTO = wg pory dnia, DZIEŃ / NOC = na stałe. */
enum class ThemeMode(val label: String) { SYSTEM("TELEFON"), AUTO("PORA DNIA"), DAY("DZIEŃ"), NIGHT("NOC") }

private class Palette(
    val bg: Color, val surface: Color, val surface2: Color, val line: Color, val tick: Color,
    val text: Color, val muted: Color, val accent: Color, val accentDim: Color,
)

private val NightPalette = Palette(
    bg = Color(0xFF141517), surface = Color(0xFF1B1D20), surface2 = Color(0xFF24272B),
    line = Color(0xFF2E3135), tick = Color(0xFF3A3D42),
    text = Color(0xFFEDEAE4), muted = Color(0xFF9A968E),
    accent = Color(0xFFFF7A2F), accentDim = Color(0xFF2A1E16),
)

// Dzień: ciepły papier; kolory dobrane tak, by tekst miał kontrast co najmniej 4.5:1
private val DayPalette = Palette(
    bg = Color(0xFFF4F1EA), surface = Color(0xFFFBF9F4), surface2 = Color(0xFFE9E3D7),
    line = Color(0xFFD9D2C4), tick = Color(0xFFC4BBAA),
    text = Color(0xFF1E1F22), muted = Color(0xFF66615A),
    accent = Color(0xFFC14E0E), accentDim = Color(0xFFF5E2D4),
)

object ThemeState {
    var mode by mutableStateOf(ThemeMode.SYSTEM)
    private var clockNight by mutableStateOf(isNightHour(LocalTime.now().hour))

    /** Tryb ciemny telefonu – ustawiany przez EquinoxTheme. */
    var systemDark by mutableStateOf(true)

    val night: Boolean
        get() = when (mode) {
            ThemeMode.DAY -> false
            ThemeMode.NIGHT -> true
            ThemeMode.AUTO -> clockNight
            ThemeMode.SYSTEM -> systemDark
        }

    /** Noc od 19:00 do 6:59. */
    fun isNightHour(hour: Int) = hour >= 19 || hour < 7

    fun refreshClock() {
        clockNight = isNightHour(LocalTime.now().hour)
    }

    fun next(): ThemeMode = ThemeMode.entries[(mode.ordinal + 1) % ThemeMode.entries.size]
}

/** Kolory bieżącego motywu. Odczyt w Compose sam odświeża ekran po zmianie dnia/nocy. */
object Pal {
    private val p: Palette get() = if (ThemeState.night) NightPalette else DayPalette
    val Bg get() = p.bg
    val Surface get() = p.surface
    val Surface2 get() = p.surface2
    val Line get() = p.line
    val Tick get() = p.tick
    val Text get() = p.text
    val Muted get() = p.muted
    val Accent get() = p.accent
    val AccentDim get() = p.accentDim
}

val Mono = FontFamily.Monospace

/** Powitanie zależne od pory dnia. */
fun greeting(hour: Int = LocalTime.now().hour): String = when (hour) {
    in 5..11 -> "Dzień dobry"
    in 12..17 -> "Miłego popołudnia"
    in 18..21 -> "Dobry wieczór"
    else -> "Spokojnej nocy"
}

@Composable
fun EquinoxTheme(content: @Composable () -> Unit) {
    // w trybie Auto sprawdzamy porę dnia co minutę
    LaunchedEffect(Unit) {
        while (true) {
            ThemeState.refreshClock()
            delay(60_000)
        }
    }
    ThemeState.systemDark = isSystemInDarkTheme()
    val night = ThemeState.night

    // ikony paska stanu: jasne nocą, ciemne za dnia
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !night
                isAppearanceLightNavigationBars = !night
            }
        }
    }

    val scheme = if (night) {
        darkColorScheme(
            primary = Pal.Accent, onPrimary = Pal.Bg, background = Pal.Bg, onBackground = Pal.Text,
            surface = Pal.Surface, onSurface = Pal.Text, surfaceVariant = Pal.Surface2,
            onSurfaceVariant = Pal.Muted, outline = Pal.Line,
        )
    } else {
        lightColorScheme(
            primary = Pal.Accent, onPrimary = Pal.Bg, background = Pal.Bg, onBackground = Pal.Text,
            surface = Pal.Surface, onSurface = Pal.Text, surfaceVariant = Pal.Surface2,
            onSurfaceVariant = Pal.Muted, outline = Pal.Line,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
