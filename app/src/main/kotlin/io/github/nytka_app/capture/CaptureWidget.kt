package io.github.nytka_app.capture

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.nytka_app.MainActivity
import io.github.nytka_app.R
import io.github.nytka_app.core.queue.QueueUsage

/** The home-screen widget: capture glyph and word, pendant battery and queue length, like [CaptureNotification]. */
object CaptureWidget {
    private const val REQUEST_OPEN = 10
    private const val REQUEST_MUTE = 11

    fun remoteViews(
        context: Context,
        status: CaptureStatus,
        usage: QueueUsage,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_capture)
        val state = CaptureState.of(status)
        views.setTextViewText(R.id.widget_status, context.getString(state.wordRes))
        // The same glyph as in the app; only Recording is green.
        views.setImageViewResource(R.id.widget_glyph, state.iconRes)
        val glyphColor = if (state == CaptureState.Recording) R.color.widget_capture else R.color.widget_glyph
        views.setInt(R.id.widget_glyph, "setColorFilter", context.getColor(glyphColor))
        val battery = status.battery?.let { "$it%" } ?: context.getString(R.string.unknown)
        views.setTextViewText(R.id.widget_battery, context.getString(R.string.pendant_battery_format, battery))
        views.setTextViewText(R.id.widget_queue, context.getString(R.string.queued_chunks_format, usage.chunks))
        views.setTextViewText(
            R.id.widget_mute_button,
            context.getString(if (status.muted) R.string.unmute else R.string.mute),
        )

        val open =
            PendingIntent.getActivity(
                context,
                REQUEST_OPEN,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        views.setOnClickPendingIntent(R.id.widget_root, open)

        val toggle =
            PendingIntent.getService(
                context,
                REQUEST_MUTE,
                Intent(context, CaptureService::class.java)
                    .setAction(if (status.muted) CaptureService.ACTION_UNMUTE else CaptureService.ACTION_MUTE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        views.setOnClickPendingIntent(R.id.widget_mute_button, toggle)

        return views
    }
}
