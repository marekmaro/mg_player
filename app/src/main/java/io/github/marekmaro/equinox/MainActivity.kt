package io.github.marekmaro.equinox

import android.Manifest
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.launch
import io.github.marekmaro.equinox.ui.LocalPosture
import io.github.marekmaro.equinox.ui.Posture
import io.github.marekmaro.equinox.ui.LayoutEditorScreen
import io.github.marekmaro.equinox.ui.LibraryScreen
import io.github.marekmaro.equinox.ui.GroupsScreen
import io.github.marekmaro.equinox.ui.GroupTracksScreen
import io.github.marekmaro.equinox.ui.SearchScreen
import io.github.marekmaro.equinox.ui.PrivacyScreen
import io.github.marekmaro.equinox.ui.PickTracksScreen
import io.github.marekmaro.equinox.ui.PlaylistScreen
import io.github.marekmaro.equinox.ui.PlaylistsScreen
import io.github.marekmaro.equinox.ui.WelcomeIntro
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.marekmaro.equinox.ui.ThemeMode
import io.github.marekmaro.equinox.ui.ThemeState
import io.github.marekmaro.equinox.data.Prefs
import io.github.marekmaro.equinox.ui.BooksScreen
import io.github.marekmaro.equinox.ui.HomeScreen
import io.github.marekmaro.equinox.ui.EquinoxTheme
import io.github.marekmaro.equinox.ui.MusicScreen
import io.github.marekmaro.equinox.ui.PlayingScreen
import io.github.marekmaro.equinox.ui.RadioScreen
import io.github.marekmaro.equinox.ui.SessionsScreen

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN = "io.github.marekmaro.equinox.OPEN"
        const val OPEN_PLAYING = "playing"
        const val OPEN_SESSIONS = "sessions"
        const val OPEN_STOPWATCH = "stopwatch"
    }

    private val vm: PlayerViewModel by viewModels()
    private val posture = mutableStateOf(Posture.FLAT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeState.mode = ThemeMode.entries.firstOrNull { it.name == Prefs(this).themeMode } ?: ThemeMode.SYSTEM
        watchFold()
        // przy odtworzeniu po obrocie nie powtarzamy akcji z widżetu/kafelka
        val openExtra = intent?.getStringExtra(EXTRA_OPEN)
        if (savedInstanceState == null) vm.handleOpen(openExtra)
        // powitanie tylko przy zwykłym uruchomieniu (nie z widżetu/kafelka, nie po obrocie)
        val playIntro = savedInstanceState == null && openExtra == null
        setContent {
            EquinoxTheme {
                CompositionLocalProvider(LocalPosture provides posture.value) {
                    var showIntro by rememberSaveable { mutableStateOf(playIntro) }
                    Box(Modifier.fillMaxSize()) {
                        PermissionRequest(onDone = { vm.loadLibrary() })
                        App(vm)
                        if (showIntro) WelcomeIntro(onDone = { showIntro = false })
                    }
                }
            }
        }
    }

    /** Śledzi zgięcie składaka: „namiot” (zgięcie poziome) albo „książka” (pionowe). */
    private fun watchFold() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity)
                    .windowLayoutInfo(this@MainActivity)
                    .collect { info ->
                        val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                        posture.value = when {
                            fold == null || fold.state != FoldingFeature.State.HALF_OPENED -> Posture.FLAT
                            fold.orientation == FoldingFeature.Orientation.HORIZONTAL -> Posture.TABLETOP
                            else -> Posture.BOOK
                        }
                    }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.handleOpen(intent.getStringExtra(EXTRA_OPEN))
    }

    override fun onStart() {
        super.onStart()
        vm.visible = true
        vm.onVisible()
    }

    override fun onStop() {
        vm.visible = false
        super.onStop()
    }
}

@Composable
private fun PermissionRequest(onDone: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onDone() }
    LaunchedEffect(Unit) {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        launcher.launch(perms)
    }
}

enum class Screen { Home, Music, Books, Radio, Sessions, Playing, Layout, Playlists, Playlist, PickTracks, Library, Privacy, Groups, GroupTracks, Search }

@Composable
private fun App(vm: PlayerViewModel) {
    // Stos ekranów przetrwa obrót i złożenie/rozłożenie telefonu
    val stack = rememberSaveable(
        saver = listSaver<SnapshotStateList<Screen>, String>(save = { s -> s.map { it.name } }, restore = { names -> names.map { Screen.valueOf(it) }.toMutableStateList() }),
    ) { mutableListOf(Screen.Home).toMutableStateList() }

    fun go(s: Screen) {
        // nie dubluj ekranu „Teraz gra” na stosie
        if (s == Screen.Playing) stack.remove(Screen.Playing)
        stack.add(s)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    BackHandler(enabled = stack.size > 1) { back() }

    // przejście zlecone z widżetu albo kafelka
    LaunchedEffect(vm.navRequest) {
        vm.navRequest?.let {
            if (stack.lastOrNull() != it) go(it)
            vm.navRequest = null
        }
    }

    when (stack.last()) {
        Screen.Home -> HomeScreen(
            vm,
            onMusic = { go(Screen.Music) },
            onBooks = { go(Screen.Books) },
            onRadio = { go(Screen.Radio) },
            onSessions = { go(Screen.Sessions) },
            onPlaying = { go(Screen.Playing) },
            onPlaylists = { go(Screen.Playlists) },
            onLibrary = { go(Screen.Library) },
            onSearch = { go(Screen.Search) },
        )
        Screen.Music -> MusicScreen(vm, onBack = ::back, onPlaying = { go(Screen.Playing) }, onGroups = { t ->
            vm.groupListType = t
            go(Screen.Groups)
        })
        Screen.Groups -> GroupsScreen(vm, onBack = ::back, onOpenGroup = { go(Screen.GroupTracks) })
        Screen.GroupTracks -> GroupTracksScreen(vm, onBack = ::back, onPlaying = { go(Screen.Playing) })
        Screen.Search -> SearchScreen(vm, onBack = ::back, onPlaying = { go(Screen.Playing) }, onOpenGroup = { go(Screen.GroupTracks) })
        Screen.Books -> BooksScreen(vm, onBack = ::back, onPlaying = { go(Screen.Playing) })
        Screen.Radio -> RadioScreen(vm, onBack = ::back, onPlaying = { go(Screen.Playing) })
        Screen.Sessions -> SessionsScreen(vm, onBack = ::back, onPlaying = { go(Screen.Playing) })
        Screen.Playing -> PlayingScreen(vm, onBack = ::back, onSessions = { go(Screen.Sessions) }, onEditLayout = { go(Screen.Layout) })
        Screen.Layout -> LayoutEditorScreen(vm, onBack = ::back)
        Screen.Playlists -> PlaylistsScreen(vm, onBack = ::back, onOpenPlaylist = { go(Screen.Playlist) }, onOpenGroup = { go(Screen.GroupTracks) })
        Screen.Playlist -> PlaylistScreen(vm, onBack = ::back, onPlaying = { go(Screen.Playing) }, onAddTracks = { go(Screen.PickTracks) })
        Screen.PickTracks -> PickTracksScreen(vm, onBack = ::back)
        Screen.Library -> LibraryScreen(vm, onBack = ::back, onPrivacy = { go(Screen.Privacy) })
        Screen.Privacy -> PrivacyScreen(onBack = ::back)
    }
}
