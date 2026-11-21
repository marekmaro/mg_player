package io.github.marekmaro.equinox

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import io.github.marekmaro.equinox.data.Track

/** Udostępnianie utworu i ustawianie go jako dzwonka – działania na pliku z poziomu systemu. */
object TrackActions {

    /** Systemowe „Udostępnij”: plik trafia do wybranej aplikacji (komunikator, poczta, Bluetooth…). */
    fun share(context: Context, t: Track) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, t.uri)
            putExtra(Intent.EXTRA_TITLE, t.title)
            putExtra(Intent.EXTRA_SUBJECT, "${t.artist} – ${t.title}")
            // podgląd w okienku wyboru i dostęp do pliku tylko dla aplikacji, którą wskażesz
            clipData = ClipData.newRawUri(t.title, t.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, tr("Udostępnij utwór", "Share song")).apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            toast(context, tr("Nie ma aplikacji, która przyjmie plik audio.", "No app can receive an audio file."))
        }
    }

    /** Czy aplikacja może zmienić dzwonek (zgoda „Modyfikowanie ustawień systemu”). */
    fun canSetRingtone(context: Context): Boolean = Settings.System.canWrite(context)

    /** Systemowy ekran zgody na zmianę ustawień – Android wymaga, by użytkownik włączył ją sam. */
    fun openRingtonePermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")).apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            toast(context, tr("Otwórz Ustawienia → Aplikacje → Equinox → Modyfikowanie ustawień systemu.", "Open Settings → Apps → Equinox → Modify system settings."))
        }
    }

    /** Ustawia utwór jako dzwonek telefonu; zwraca true, gdy się udało. */
    fun setRingtone(context: Context, t: Track): Boolean = try {
        RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, t.uri)
        toast(context, tr("Dzwonek: ${t.title}", "Ringtone: ${t.title}"))
        true
    } catch (e: Exception) {
        toast(context, tr("Nie udało się ustawić dzwonka.", "Couldn't set the ringtone."))
        false
    }

    private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}
