package io.github.marekmaro.equinox.update

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.marekmaro.equinox.data.Prefs
import io.github.marekmaro.equinox.tr
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/** Co się dzieje z aktualizacją – pokazywane w ustawieniach i jako kropka przy ☼. */
sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val info: UpdateInfo) : UpdateStatus
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateStatus
    /** Pobrane i sprawdzone – czeka na zgodę systemu albo potwierdzenie instalacji. */
    data class Ready(val info: UpdateInfo, val needsPermission: Boolean) : UpdateStatus
    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateStatus
}

/**
 * Jedno miejsce sterujące aktualizacją (sprawdź → pobierz → sprawdź podpis → zainstaluj),
 * żyje przez cały czas działania aplikacji, więc pobieranie nie przerywa się przy obrocie ekranu.
 */
object UpdateState {

    var status: UpdateStatus by mutableStateOf(UpdateStatus.Idle)
        private set

    /** Kropka przy przycisku ustawień: jest coś do zainstalowania. */
    val hasUpdate: Boolean
        get() = status is UpdateStatus.Available || status is UpdateStatus.Ready ||
            (status as? UpdateStatus.Failed)?.info != null

    private val scope = MainScope()
    private var job: Job? = null
    private var file: java.io.File? = null

    /** Automatycznie: raz na dobę, gdy włączone w ustawieniach. */
    fun checkDaily(context: Context, currentVersion: String) {
        if (!Updater.enabled) return
        val prefs = Prefs(context)
        if (!prefs.updateAutoCheck) return
        if (System.currentTimeMillis() - prefs.updateLastCheck < Updater.MIN_CHECK_INTERVAL_MS) return
        check(context, currentVersion, quiet = true)
    }

    /** Ręcznie („Sprawdź teraz”) albo automatycznie ([quiet] – bez komunikatu o błędzie sieci). */
    fun check(context: Context, currentVersion: String, quiet: Boolean = false) {
        if (!Updater.enabled || job?.isActive == true) return
        val app = context.applicationContext
        status = UpdateStatus.Checking
        job = scope.launch {
            status = try {
                val info = Updater.check(currentVersion)
                Prefs(app).updateLastCheck = System.currentTimeMillis()
                if (info != null) UpdateStatus.Available(info) else UpdateStatus.UpToDate
            } catch (e: Exception) {
                if (quiet) UpdateStatus.Idle
                else UpdateStatus.Failed(tr("Nie mogę połączyć się z GitHubem. Sprawdź internet.", "Can't reach GitHub. Check your internet connection."))
            }
        }
    }

    /** Pobierz, sprawdź i przekaż do instalacji (albo poproś o zgodę systemu). */
    fun install(context: Context, info: UpdateInfo) {
        if (!Updater.enabled || job?.isActive == true) return
        val app = context.applicationContext
        job = scope.launch {
            try {
                val apk = file?.takeIf { it.exists() && it.name.contains(info.version) }
                    ?: Updater.download(app, info) { p -> status = UpdateStatus.Downloading(info, p) }.also { file = it }
                if (!Updater.verify(app, apk)) {
                    apk.delete()
                    file = null
                    status = UpdateStatus.Failed(
                        tr("Pobrany plik nie jest podpisany kluczem Equinox – nie instaluję go.", "The downloaded file isn't signed with the Equinox key – not installing it."),
                        info,
                    )
                    return@launch
                }
                if (!Updater.canInstall(app)) {
                    status = UpdateStatus.Ready(info, needsPermission = true)
                    return@launch
                }
                status = UpdateStatus.Ready(info, needsPermission = false)
                Updater.install(app, apk)
            } catch (e: Exception) {
                status = UpdateStatus.Failed(tr("Pobieranie się nie udało. Spróbuj jeszcze raz.", "The download failed. Please try again."), info)
            }
        }
    }

    /** Wywoływane przez odbiornik instalatora (np. anulowanie w oknie systemowym). */
    fun failed(message: String) {
        val info = when (val s = status) {
            is UpdateStatus.Ready -> s.info
            is UpdateStatus.Downloading -> s.info
            is UpdateStatus.Available -> s.info
            else -> null
        }
        status = UpdateStatus.Failed(message, info)
    }
}
