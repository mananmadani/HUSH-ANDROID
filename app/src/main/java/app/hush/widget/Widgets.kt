package app.hush.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import app.hush.HushApp
import app.hush.MainActivity
import app.hush.Prefs
import app.hush.R
import app.hush.player.PlayerState
import app.hush.ui.hiRes
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.RoundedCornersTransformation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 4x2 widget: cover, title, artist and previous / play / next. */
class NowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetUpdater.update(context)
    }
}

/** 2x2 widget: cover, title and a play button. */
class MiniWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetUpdater.update(context)
    }
}

/** Handles the buttons on the widgets. Not exported, so only Hush's own widgets can use it. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val player = (context.applicationContext as HushApp).player
        when (intent.action) {
            WidgetUpdater.ACTION_TOGGLE -> player.toggle()
            WidgetUpdater.ACTION_NEXT -> player.next()
            WidgetUpdater.ACTION_PREV -> player.previous()
        }
    }
}

object WidgetUpdater {
    const val ACTION_TOGGLE = "app.hush.widget.TOGGLE"
    const val ACTION_NEXT = "app.hush.widget.NEXT"
    const val ACTION_PREV = "app.hush.widget.PREV"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cachedUrl: String? = null
    private var cachedBitmap: Bitmap? = null

    /** Redraw every Hush widget on the home screen with what is playing now. */
    fun update(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val wide = manager.getAppWidgetIds(ComponentName(app, NowPlayingWidget::class.java))
        val mini = manager.getAppWidgetIds(ComponentName(app, MiniWidget::class.java))
        if (wide.isEmpty() && mini.isEmpty()) return

        val state = (app as HushApp).player.state.value
        scope.launch {
            val cover = loadCover(app, state.art)
            if (wide.isNotEmpty()) {
                manager.updateAppWidget(wide, build(app, R.layout.widget_now_playing, state, cover, true))
            }
            if (mini.isNotEmpty()) {
                manager.updateAppWidget(mini, build(app, R.layout.widget_mini, state, cover, false))
            }
        }
    }

    private suspend fun loadCover(context: Context, art: String?): Bitmap? {
        if (art == null) return null
        if (art == cachedUrl) return cachedBitmap
        val request = ImageRequest.Builder(context)
            .data(hiRes(art))
            .allowHardware(false)
            .size(360)
            .transformations(RoundedCornersTransformation(48f))
            .build()
        val bitmap = ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        if (bitmap != null) {
            cachedUrl = art
            cachedBitmap = bitmap
        }
        return bitmap
    }

    private fun build(
        context: Context,
        layout: Int,
        s: PlayerState,
        cover: Bitmap?,
        withSkip: Boolean
    ): RemoteViews {
        val hasMedia = s.mediaId != null
        val views = RemoteViews(context.packageName, layout)

        views.setTextViewText(R.id.w_title, if (hasMedia) s.title else "Hush")
        views.setTextViewText(R.id.w_artist, if (hasMedia) s.artist else "Tap to play something")
        if (hasMedia && cover != null) {
            views.setImageViewBitmap(R.id.w_art, cover)
        } else {
            views.setImageViewResource(R.id.w_art, R.drawable.w_art_placeholder)
        }
        views.setImageViewResource(R.id.w_play, if (s.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
        views.setInt(R.id.w_play_bg, "setColorFilter", Prefs.accentDark.toArgb())

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.w_art, open)
        views.setOnClickPendingIntent(R.id.w_title, open)
        views.setOnClickPendingIntent(R.id.w_artist, open)
        views.setOnClickPendingIntent(R.id.w_play_wrap, if (hasMedia) action(context, ACTION_TOGGLE, 1) else open)
        if (withSkip) {
            views.setOnClickPendingIntent(R.id.w_prev, if (hasMedia) action(context, ACTION_PREV, 2) else open)
            views.setOnClickPendingIntent(R.id.w_next, if (hasMedia) action(context, ACTION_NEXT, 3) else open)
        }
        return views
    }

    private fun action(context: Context, name: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, WidgetActionReceiver::class.java).setAction(name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
