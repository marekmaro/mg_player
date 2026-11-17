package io.github.marekmaro.equinox.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.marekmaro.equinox.Lang
import io.github.marekmaro.equinox.LangMode
import io.github.marekmaro.equinox.PlayerViewModel
import io.github.marekmaro.equinox.tr
import kotlin.math.hypot

/**
 * Kształt „wschodzącego słońca”: koło o środku na dolnej krawędzi ekranu,
 * które rośnie i wyłania się zza „horyzontu”, aż zakryje cały ekran.
 */
private class SunriseShape(private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = hypot(size.width / 2f, size.height) * 1.05f * progress
        val c = Offset(size.width / 2f, size.height)
        return Outline.Generic(Path().apply { addOval(Rect(c, r)) })
    }
}

/**
 * Ustawienia wysuwane jak wschód słońca: panel wynurza się zza dolnej krawędzi
 * po łuku z pomarańczową obwódką, a sekcje wsuwają się po kolei.
 * Zamknięcie odtwarza animację wstecz – zachód.
 */
@Composable
fun SettingsPanel(vm: PlayerViewModel, onPrivacy: () -> Unit) {
    val open = vm.settingsOpen
    val p = remember { Animatable(0f) }
    LaunchedEffect(open) {
        p.animateTo(
            if (open) 1f else 0f,
            tween(if (open) 700 else 450, easing = FastOutSlowInEasing),
        )
    }
    if (!open && p.value <= 0.001f) return
    BackHandler(enabled = open) { vm.settingsOpen = false }
    var soundOpen by remember { mutableStateOf(false) }
    if (soundOpen) SoundSheet(vm, onDismiss = { soundOpen = false })

    val progress = p.value
    val accent = Pal.Accent
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(SunriseShape(progress))
                .background(Pal.Surface)
                .clickable(enabled = false) {}, // nie przepuszczaj dotknięć pod spód
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                var i = 0
                fun next() = i++
                Staggered(progress, next()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Ustawienia", "Settings"), color = Pal.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        CircleButton(tr("Zamknij", "Close"), { vm.settingsOpen = false }) {
                            Text("✕", color = Pal.Text, fontSize = 16.sp)
                        }
                    }
                }

                Staggered(progress, next()) {
                    Section(tr("Język", "Language")) {
                        ChoiceRow(
                            listOf(tr("Telefon", "Phone") to LangMode.SYSTEM, "Polski" to LangMode.PL, "English" to LangMode.EN),
                            Lang.mode,
                        ) { vm.changeLanguage(it) }
                    }
                }

                Staggered(progress, next()) {
                    Section(tr("Wygląd", "Look")) {
                        ChoiceRow(
                            listOf(
                                tr("Telefon", "Phone") to ThemeMode.SYSTEM,
                                tr("Pora dnia", "Time of day") to ThemeMode.AUTO,
                                tr("Dzień", "Day") to ThemeMode.DAY,
                                tr("Noc", "Night") to ThemeMode.NIGHT,
                            ),
                            ThemeState.mode,
                        ) { vm.changeTheme(it) }
                        Text(tr("Środek pierścienia", "Centre of the ring"), color = Pal.Muted, fontSize = 12.sp)
                        ChoiceRow(
                            listOf(tr("Okładka", "Cover") to true, tr("Ciekawostka", "Fun fact") to false),
                            vm.showCovers,
                        ) { vm.changeShowCovers(it) }
                    }
                }

                Staggered(progress, next()) {
                    Section(tr("Brzmienie", "Sound")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("♪ " + vm.soundPreset.label, color = Pal.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Chip(tr("Zmień", "Change")) { soundOpen = true }
                        }
                    }
                }

                Staggered(progress, next()) {
                    Section(tr("Losowanie", "Shuffle")) {
                        ChoiceRow(
                            listOf(tr("Mądre", "Smart") to true, tr("Zwykłe", "Plain") to false),
                            vm.smartShuffleOn,
                        ) { vm.changeSmartShuffle(it) }
                        Text(
                            if (vm.smartShuffleOn) tr(
                                "Bez tego samego wykonawcy pod rząd, niedawno słuchane na koniec, ♥ i ★ częściej.",
                                "No same artist back to back, recently played go last, ♥ and ★ come up more often.",
                            ) else tr("Czysty przypadek.", "Pure chance."),
                            color = Pal.Muted, fontSize = 12.sp, lineHeight = 17.sp,
                        )
                    }
                }

                Staggered(progress, next()) {
                    Section(tr("Pokrętło", "Dial")) {
                        ChoiceRow(
                            listOf(tr("Spokojne", "Calm") to 0, tr("Zwykłe", "Normal") to 1, tr("Szybkie", "Fast") to 2),
                            KnobSettings.speed,
                        ) { vm.changeKnobSpeed(it) }
                        SwitchRow(tr("Wibracje przy kręceniu", "Haptics while turning"), KnobSettings.haptics) { vm.changeKnobHaptics(!KnobSettings.haptics) }
                    }
                }

                Staggered(progress, next()) {
                    Section(tr("Dodatki", "Extras")) {
                        SwitchRow(tr("Ciekawostki z Wikipedii", "Fun facts from Wikipedia"), vm.factsEnabled) { vm.setFactsOn(!vm.factsEnabled) }
                        SwitchRow(tr("Statystyki w menu", "Stats in the menu"), vm.showStats) { vm.changeShowStats(!vm.showStats) }
                    }
                }

                Staggered(progress, next()) {
                    Section(tr("Pomoc", "Help")) {
                        LinkRow(tr("Pokaż samouczek pokrętła", "Show the dial tutorial")) {
                            vm.showTutorialAgain()
                            vm.settingsOpen = false
                        }
                        LinkRow(tr("Prywatność", "Privacy")) {
                            vm.settingsOpen = false
                            onPrivacy()
                        }
                    }
                }

                Staggered(progress, next()) {
                    val ctx = LocalContext.current
                    val version = remember {
                        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "" }
                    }
                    Text("Equinox $version · ${tr("zrobione z pasji przez", "made with love by")} Mar0", color = Pal.Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        // pomarańczowa obwódka „słońca” widoczna w trakcie wschodu i zachodu
        if (progress < 0.999f) {
            Canvas(Modifier.fillMaxSize()) {
                val r = hypot(size.width / 2f, size.height) * 1.05f * progress
                drawCircle(accent, radius = r, center = Offset(size.width / 2f, size.height), style = Stroke(3.dp.toPx()), alpha = 1f - progress * 0.6f)
            }
        }
    }
}

/** Sekcja pojawia się z opóźnieniem zależnym od pozycji: wsuwa się od dołu i nabiera wyrazistości. */
@Composable
private fun Staggered(progress: Float, index: Int, content: @Composable () -> Unit) {
    val q = ((progress - 0.25f - index * 0.07f) / 0.45f).coerceIn(0f, 1f)
    Box(
        Modifier.graphicsLayer {
            alpha = q
            translationY = (1f - q) * 60f
        },
    ) { content() }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Pal.Bg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title.uppercase(), color = Pal.Muted, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        content()
    }
}

@Composable
private fun <T> ChoiceRow(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    // więcej niż 3 opcje = dwa rzędy, żeby napisy mieściły się także na małych telefonach
    val rows = if (options.size > 3) options.chunked(2) else listOf(options)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (label, value) ->
                    Chip(label, modifier = Modifier.weight(1f), selected = value == selected) { onSelect(value) }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Pal.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switchy(on)
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Pal.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("›", color = Pal.Muted, fontSize = 20.sp)
    }
}
