package io.github.nytka_app.capture

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import dagger.hilt.android.AndroidEntryPoint
import io.github.nytka_app.core.queue.FrameQueue
import io.github.nytka_app.core.queue.QueueUsage
import javax.inject.Inject

/**
 * Shows what [CaptureHub] and [FrameQueue] already expose; [refresh] is called from the collection
 * [CaptureService] already runs for the notification, so the widget needs no background work of its own.
 */
@AndroidEntryPoint
class CaptureWidgetProvider : AppWidgetProvider() {
    @Inject
    lateinit var hub: CaptureHub

    @Inject
    lateinit var queue: FrameQueue

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val views = CaptureWidget.remoteViews(context, hub.status.value, queue.usage.value)
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }

    companion object {
        fun refresh(
            context: Context,
            status: CaptureStatus,
            usage: QueueUsage,
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CaptureWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = CaptureWidget.remoteViews(context, status, usage)
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }
}
