package io.github.marekmaro.sundial

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import io.github.marekmaro.sundial.ui.BooksScreen
import io.github.marekmaro.sundial.ui.HomeScreen
import io.github.marekmaro.sundial.ui.SundialTheme
import io.github.marekmaro.sundial.ui.MusicScreen
import io.github.marekmaro.sundial.ui.PlayingScreen
import io.github.marekmaro.sundial.ui.RadioScreen
import io.github.marekmaro.sundial.ui.SessionsScreen

class MainActivity : ComponentActivity() {

    private val vm: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SundialTheme {
                PermissionRequest(onDone = { vm.loadLibrary() })
                App(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.visible = true
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

sealed interface Screen {
    data object Home : Screen
    data object Music : Screen
    data object Books : Screen
    data object Radio : Screen
    data object Sessions : Screen
    data object Playing : Screen
}

@Composable
private fun App(vm: PlayerViewModel) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }

    fun go(s: Screen) {
        // nie dubluj ekranu „Teraz gra” na stosie
        if (s == Screen.Playing) stack.remove(Screen.Playing)
        stack.add(s)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    BackHandler(enabled = stack.size > 1) { back() }

    when (stack.last()) {
        Screen.Home -> HomeScreen(
            vm,
            onMusic = { go(Screen.Music) },
            onBooks = { go(Screen.Books) },
            onRadio = { go(Screen.Radio) },
            onSessions = { go(Screen.Sessions) },
            onPlaying = { go(Screen.Playing) },
        )
        Screen.Music -> MusicScreen(vm, onBack = ::back, onPlaying = { go(Screen.Playing) })
        Screen.Books -> BooksScreen(vm, onBack = ::back, onPlaying = { go(Screen.Playing) })
        Screen.Radio -> RadioScreen(vm, onBack = ::back, onPlaying = { go(Screen.Playing) })
        Screen.Sessions -> SessionsScreen(vm, onBack = ::back, onPlaying = { go(Screen.Playing) })
        Screen.Playing -> PlayingScreen(vm, onBack = ::back, onSessions = { go(Screen.Sessions) })
    }
}
