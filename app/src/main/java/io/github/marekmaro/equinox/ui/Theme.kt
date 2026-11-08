package io.github.marekmaro.equinox.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Paleta „cyfrowo-analogowa”: grafit + jeden ciepły akcent. */
object Pal {
    val Bg = Color(0xFF141517)
    val Surface = Color(0xFF1B1D20)
    val Surface2 = Color(0xFF24272B)
    val Line = Color(0xFF2E3135)
    val Tick = Color(0xFF3A3D42)
    val Text = Color(0xFFEDEAE4)
    val Muted = Color(0xFF9A968E)
    val Accent = Color(0xFFFF7A2F)
    val AccentDim = Color(0xFF2A1E16)
}

val Mono = FontFamily.Monospace

@Composable
fun EquinoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pal.Accent,
            onPrimary = Pal.Bg,
            background = Pal.Bg,
            onBackground = Pal.Text,
            surface = Pal.Surface,
            onSurface = Pal.Text,
            surfaceVariant = Pal.Surface2,
            onSurfaceVariant = Pal.Muted,
            outline = Pal.Line,
        ),
        content = content,
    )
}
