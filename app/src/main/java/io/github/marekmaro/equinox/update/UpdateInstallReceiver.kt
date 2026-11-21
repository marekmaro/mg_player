package io.github.marekmaro.equinox.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import io.github.marekmaro.equinox.tr

/**
 * Odpowiedź systemowego instalatora. Najczęściej „potrzebne potwierdzenie” – wtedy pokazujemy
 * systemowe okno „Zaktualizować tę aplikację?”. Po udanej instalacji Android sam zamyka
 * i uruchamia na nowo Equinox, więc nie ma tu nic do zrobienia.
 * Zarejestrowany tylko w wersji „github” (src/github/AndroidManifest.xml).
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateState.failed(tr("Aktualizacja anulowana.", "Update cancelled."))
            else -> {
                UpdateState.failed(tr("Nie udało się zainstalować aktualizacji.", "Couldn't install the update."))
                Toast.makeText(context, tr("Nie udało się zainstalować aktualizacji.", "Couldn't install the update."), Toast.LENGTH_LONG).show()
            }
        }
    }
}
