package io.github.marekmaro.equinox.data

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/** Co zrobić z plikami w folderze (i jego podfolderach). */
enum class FolderRule(private val pl: String, private val en: String) {
    AUTO("Auto", "Auto"), MUSIC("Muzyka", "Music"), BOOK("Audiobook", "Audiobook"), SKIP("Pomiń", "Skip");

    val label: String get() = io.github.marekmaro.equinox.tr(pl, en)

    fun next(): FolderRule = entries[(ordinal + 1) % entries.size]
}

data class FolderInfo(val path: String, val name: String, val count: Int, val rule: FolderRule, val inherited: Boolean)

object Library {

    /** Rozszerzenia plików, których szukamy przy skanowaniu. */
    val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "m4b", "aac", "flac", "ogg", "oga", "opus", "wav", "amr", "3gp", "mka", "alac")

    /**
     * Reguła dla ścieżki: wygrywa najdłuższy pasujący folder,
     * więc reguła ustawiona na podfolderze nadpisuje regułę rodzica.
     */
    fun ruleFor(path: String, rules: Map<String, FolderRule>): FolderRule {
        var best: String? = null
        for (folder in rules.keys) {
            val matches = path == folder || path.startsWith("$folder/")
            if (matches && (best == null || folder.length > best.length)) best = folder
        }
        return best?.let { rules[it] } ?: FolderRule.AUTO
    }

    /** Stosuje reguły folderów: Pomiń usuwa, Muzyka/Audiobook nadpisuje rozpoznanie. */
    fun applyRules(tracks: List<Track>, rules: Map<String, FolderRule>): List<Track> {
        if (rules.isEmpty()) return tracks
        return tracks.mapNotNull { t ->
            when (ruleFor(t.folder, rules)) {
                FolderRule.SKIP -> null
                FolderRule.MUSIC -> if (t.isBook) t.copy(isBook = false) else t
                FolderRule.BOOK -> if (!t.isBook) t.copy(isBook = true) else t
                FolderRule.AUTO -> t
            }
        }
    }

    fun folders(all: List<Track>, rules: Map<String, FolderRule>): List<FolderInfo> =
        all.groupBy { it.folder }
            .map { (path, list) ->
                val own = rules[path]
                FolderInfo(
                    path = path,
                    name = path.substringAfterLast('/').ifBlank { path },
                    count = list.size,
                    rule = own ?: ruleFor(path, rules),
                    inherited = own == null && ruleFor(path, rules) != FolderRule.AUTO,
                )
            }
            .sortedBy { it.path.lowercase() }

    data class ScanResult(val filesFound: Int)

    /**
     * Przeszukuje typowe miejsca w pamięci telefonu i prosi system o zaindeksowanie
     * znalezionych plików audio (np. świeżo skopiowanych, których system jeszcze nie zauważył).
     * Foldery z plikiem .nomedia i ukryte foldery są pomijane, tak jak robi to system.
     */
    suspend fun scanPhone(context: Context, onProgress: (Int) -> Unit): ScanResult = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val root = Environment.getExternalStorageDirectory()
        val files = mutableListOf<String>()
        fun walk(dir: File, depth: Int) {
            if (depth > 8 || dir.name.startsWith(".") || File(dir, ".nomedia").exists()) return
            if (dir.absolutePath.contains("/Android/data") || dir.absolutePath.contains("/Android/obb")) return
            val children = try { dir.listFiles() } catch (e: Exception) { null } ?: return
            for (f in children) {
                if (f.isDirectory) walk(f, depth + 1)
                else if (f.extension.lowercase() in AUDIO_EXTENSIONS) {
                    files += f.absolutePath
                    if (files.size % 25 == 0) onProgress(files.size)
                }
            }
        }
        walk(root, 0)
        onProgress(files.size)
        if (files.isNotEmpty()) {
            // MediaScanner zgłasza każdy plik osobno – czekamy na wszystkie (maks. 60 s)
            withTimeoutOrNull(60_000) {
                suspendCancellableCoroutine<Unit> { cont ->
                    val left = AtomicInteger(files.size)
                    MediaScannerConnection.scanFile(context, files.toTypedArray(), null) { _, _ ->
                        if (left.decrementAndGet() == 0 && cont.isActive) cont.resume(Unit)
                    }
                }
            }
        }
        ScanResult(files.size)
    }
}

data class Playlist(val id: Long, val name: String, val trackIds: List<Long>)
