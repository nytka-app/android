package io.github.nytka_app.capture

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll

/**
 * The widget reads [CaptureHub] and the queue itself; [refresh] is called from the collection [CaptureService]
 * already runs for the notification, so the widget needs no background work of its own.
 */
class CaptureWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CaptureWidget()

    companion object {
        suspend fun refresh(context: Context) = CaptureWidget().updateAll(context)
    }
}
