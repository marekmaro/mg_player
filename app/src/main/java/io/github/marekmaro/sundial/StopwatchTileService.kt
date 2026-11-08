package io.github.marekmaro.sundial

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Kafelek w szybkich ustawieniach: „Stoper muzyczny”.
 * Dotknięcie otwiera Sundial i od razu startuje stoper (ostatnio ustawiony czas).
 * Gdy stoper już działa, kafelek jest podświetlony i pokazuje, ile zostało.
 */
class StopwatchTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val remaining = stopwatchRemainingMs()
        tile.state = if (remaining != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_stopwatch)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                remaining != null -> "zostało ${fmt(remaining)}"
                Core.stopwatchDone -> "czas minął"
                else -> "${Core.stopwatchMinutes} min"
            }
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val running = stopwatchRemainingMs() != null
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, if (running) MainActivity.OPEN_SESSIONS else MainActivity.OPEN_STOPWATCH)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 200, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /** Prosi system o odświeżenie kafelka (np. po starcie lub końcu stopera). */
        fun requestRefresh(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, StopwatchTileService::class.java))
            } catch (e: Exception) {
            }
        }

        fun stopwatchRemainingMs(): Long? {
            val durations = Core.stopwatchDurations ?: return null
            val player = PlaybackService.player ?: return null
            if (durations.isEmpty()) return null
            val idx = player.currentMediaItemIndex.coerceIn(0, durations.lastIndex)
            return (durations.drop(idx).sum() - player.currentPosition).coerceAtLeast(0)
        }
    }
}
