package io.github.marekmaro.equinox.ui

import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.provider.Settings
import android.net.Uri
import android.content.Intent
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
                    Section(tr("Płynne przejścia", "Crossfade")) {
                        ChoiceRow(
                            listOf(tr("Wył.", "Off") to 0, "3 s" to 3, "6 s" to 6, "10 s" to 10),
                            vm.crossfadeSec,
                        ) { vm.changeCrossfade(it) }
                        Text(
                            tr(
                                "Koniec utworu cichnie, a następny łagodnie się pojawia. Tylko muzyka – audiobooki i radio grają bez zmian.",
                                "The end of a song fades out and the next one fades in. Music only – audiobooks and radio are untouched.",
                            ),
                            color = Pal.Muted, fontSize = 12.sp, lineHeight = 17.sp,
                        )
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
                    DefaultPlayerSection(vm)
                }

                Staggered(progress, next()) {
                    BackupSection(vm)
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

/** Kopia zapasowa playlist, znaczników i ustawień do jednego pliku (np. na Dysk Google lub kartę SD). */
@Composable
private fun BackupSection(vm: PlayerViewModel) {
    var confirm by remember { mutableStateOf<Uri?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirm = uri
    }
    Section(tr("Kopia zapasowa", "Backup")) {
        Text(
            tr(
                "Playlisty, znaczniki ♥ ★ ☼ ☾, ulubione stacje, miejsca w audiobookach, statystyki i ustawienia – w jednym pliku. Zapisz go np. na Dysku Google i odtwórz na nowym telefonie.",
                "Playlists, ♥ ★ ☼ ☾ marks, favourite stations, audiobook positions, stats and settings – in one file. Save it e.g. to Google Drive and restore it on a new phone.",
            ),
            color = Pal.Muted, fontSize = 12.sp, lineHeight = 17.sp,
        )
        LinkRow(tr("Zapisz kopię do pliku", "Save a backup file")) {
            save.launch("equinox-${java.time.LocalDate.now()}.json")
        }
        LinkRow(tr("Odtwórz z pliku", "Restore from a file")) {
            open.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
        }
        val pending = confirm
        if (pending != null) {
            Text(
                tr("Zastąpić obecne playlisty, znaczniki i ustawienia danymi z kopii?", "Replace current playlists, marks and settings with the backup?"),
                color = Pal.Text, fontSize = 13.sp, lineHeight = 18.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(tr("Odtwórz", "Restore")) {
                    confirm = null
                    vm.importBackup(pending)
                }
                Chip(tr("Anuluj", "Cancel")) { confirm = null }
            }
        }
        vm.backupMessage?.let { Text(it, color = Pal.Accent, fontSize = 13.sp) }
    }
}

/**
 * Domyślny odtwarzacz: Android nie ma jednego przełącznika – aplikacja staje się domyślna,
 * gdy przy otwieraniu pliku audio wybierzesz „Equinox” → „Zawsze”.
 */
@Composable
private fun DefaultPlayerSection(vm: PlayerViewModel) {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.settingsOpen = false
            vm.openExternal(uri)
        }
    }
    Section(tr("Domyślny odtwarzacz", "Default player")) {
        Text(
            tr(
                "Otwórz dowolny plik muzyczny (np. w Plikach albo Pobranych) i wybierz Equinox → „Zawsze”. Od tej pory pliki audio, listy .m3u/.pls i linki do radia będą otwierać się w Equinox.",
                "Open any music file (e.g. in Files or Downloads) and choose Equinox → “Always”. From then on audio files, .m3u/.pls lists and radio links will open in Equinox.",
            ),
            color = Pal.Muted, fontSize = 13.sp, lineHeight = 19.sp,
        )
        LinkRow(tr("Otwórz plik audio…", "Open an audio file…")) {
            try { picker.launch(arrayOf("audio/*", "application/x-mpegurl", "audio/x-scpls")) } catch (e: Exception) {}
        }
        LinkRow(tr("Aplikacje domyślne w systemie", "System default apps")) {
            val intents = listOf(
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)),
            )
            for (i in intents) {
                try {
                    ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    break
                } catch (e: Exception) {
                }
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
