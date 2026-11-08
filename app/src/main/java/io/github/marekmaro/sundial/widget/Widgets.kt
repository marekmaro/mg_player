package io.github.marekmaro.sundial.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews
import androidx.media3.common.Player
import io.github.marekmaro.sundial.MainActivity
import io.github.marekmaro.sundial.PlaybackService
import io.github.marekmaro.sundial.R
import kotlin.math.cos
import kotlin.math.sin

/** Widżet 4×2: pierścień postępu, tytuł i przyciski. */
class PlayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetUpdater.updateAll(context)
    }
}

/** Widżet 2×2: sam pierścień, środek wstrzymuje i wznawia. */
class RingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetUpdater.updateAll(context)
    }
}

/**
 * Przyciski widżetów. Działają bezpośrednio na odtwarzaczu w serwisie
 * (ten sam proces), więc nie trzeba niczego uruchamiać w tle.
 * Gdy nic nie gra, widżet zamiast przycisków otwiera aplikację.
 */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val player = PlaybackService.player
        if (player == null || player.mediaItemCount == 0) {
            WidgetUpdater.push(context, WidgetUpdater.idle())
            return
        }
        when (intent.action) {
            ACTION_TOGGLE -> if (player.isPlaying) player.pause() else {
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0, 0L)
                player.play()
            }
            ACTION_NEXT -> player.seekToNext()
            ACTION_PREV -> player.seekToPrevious()
        }
    }

    companion object {
        const val ACTION_TOGGLE = "io.github.marekmaro.sundial.widget.TOGGLE"
        const val ACTION_NEXT = "io.github.marekmaro.sundial.widget.NEXT"
        const val ACTION_PREV = "io.github.marekmaro.sundial.widget.PREV"
    }
}

object WidgetUpdater {

    data class State(
        val title: String,
        val subtitle: String,
        val playing: Boolean,
        val progress: Float,
        /** true = w odtwarzaczu jest coś załadowane, przyciski sterują nim bezpośrednio */
        val active: Boolean,
    )

    fun idle(lastTitle: String? = null) = State(
        title = lastTitle ?: "Sundial",
        subtitle = if (lastTitle != null) "Dotknij, by słuchać dalej" else "Dotknij, by słuchać",
        playing = false,
        progress = 0f,
        active = false,
    )

    @Volatile
    var last: State = idle()
        private set

    /** Zapamiętuje stan i odświeża widżety tylko wtedy, gdy coś widocznego się zmieniło. */
    fun push(context: Context, state: State, force: Boolean = false) {
        val changed = state.copy(progress = 0f) != last.copy(progress = 0f) ||
            kotlin.math.abs(state.progress - last.progress) > 0.005f
        last = state
        if (changed || force) updateAll(context)
    }

    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val playerIds = mgr.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
        val ringIds = mgr.getAppWidgetIds(ComponentName(context, RingWidget::class.java))
        if (playerIds.isEmpty() && ringIds.isEmpty()) return

        val s = last
        val ring = ringBitmap(s.progress, s.active && s.playing)
        if (playerIds.isNotEmpty()) mgr.updateAppWidget(playerIds, playerViews(context, s, ring))
        if (ringIds.isNotEmpty()) mgr.updateAppWidget(ringIds, ringViews(context, s, ring))
    }

    private fun playerViews(ctx: Context, s: State, ring: Bitmap) =
        RemoteViews(ctx.packageName, R.layout.widget_player).apply {
            setImageViewBitmap(R.id.widget_ring, ring)
            setTextViewText(R.id.widget_title, s.title)
            setTextViewText(R.id.widget_subtitle, s.subtitle)
            setImageViewResource(R.id.widget_play, if (s.playing) R.drawable.ic_pause else R.drawable.ic_play)
            setOnClickPendingIntent(R.id.widget_root, openApp(ctx))
            if (s.active) {
                setOnClickPendingIntent(R.id.widget_play, action(ctx, WidgetActionReceiver.ACTION_TOGGLE))
                setOnClickPendingIntent(R.id.widget_next, action(ctx, WidgetActionReceiver.ACTION_NEXT))
                setOnClickPendingIntent(R.id.widget_prev, action(ctx, WidgetActionReceiver.ACTION_PREV))
            } else {
                setOnClickPendingIntent(R.id.widget_play, openApp(ctx))
                setOnClickPendingIntent(R.id.widget_next, openApp(ctx))
                setOnClickPendingIntent(R.id.widget_prev, openApp(ctx))
            }
        }

    private fun ringViews(ctx: Context, s: State, ring: Bitmap) =
        RemoteViews(ctx.packageName, R.layout.widget_ring).apply {
            setImageViewBitmap(R.id.widget_ring, ring)
            setImageViewResource(R.id.widget_play, if (s.playing) R.drawable.ic_pause else R.drawable.ic_play)
            setInt(R.id.widget_play, "setColorFilter", ACCENT)
            setOnClickPendingIntent(R.id.widget_root, openApp(ctx))
            setOnClickPendingIntent(
                R.id.widget_play,
                if (s.active) action(ctx, WidgetActionReceiver.ACTION_TOGGLE) else openApp(ctx),
            )
        }

    private fun openApp(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_PLAYING)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(ctx, 100, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun action(ctx: Context, action: String): PendingIntent {
        val i = Intent(ctx, WidgetActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(ctx, action.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private const val ACCENT = 0xFFFF7A2F.toInt()

    /** Ten sam pierścień co w aplikacji: podziałka, tor i łuk postępu. */
    fun ringBitmap(progress: Float, @Suppress("UNUSED_PARAMETER") playing: Boolean, size: Int = 288): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val center = size / 2f
        val r = size / 2f

        p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 60) {
            val major = i % 5 == 0
            p.color = if (major) 0xFF3A3D42.toInt() else 0xFF2E3135.toInt()
            p.strokeWidth = size * 0.012f
            val a = Math.toRadians(i * 6.0 - 90.0)
            val outer = r - size * 0.01f
            val inner = outer - if (major) size * 0.05f else size * 0.025f
            c.drawLine(
                center + cos(a).toFloat() * inner, center + sin(a).toFloat() * inner,
                center + cos(a).toFloat() * outer, center + sin(a).toFloat() * outer, p,
            )
        }

        val inset = size * 0.12f
        val rect = RectF(inset, inset, size - inset, size - inset)
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.035f
        p.color = 0xFF24272B.toInt()
        c.drawArc(rect, 0f, 360f, false, p)
        val sweep = 360f * progress.coerceIn(0f, 1f)
        if (sweep > 0f) {
            p.color = ACCENT
            c.drawArc(rect, -90f, sweep, false, p)
        }

        p.style = Paint.Style.FILL
        p.color = 0xFF1B1D20.toInt()
        c.drawCircle(center, center, r - size * 0.2f, p)
        return bmp
    }
}
