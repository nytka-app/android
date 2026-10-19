package io.github.nytka_app.capture

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.nytka_app.MainActivity
import io.github.nytka_app.R
import io.github.nytka_app.core.queue.FrameQueue
import io.github.nytka_app.core.queue.QueueUsage

/** What the widget reads: the same hub and queue the notification shows. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CaptureWidgetEntryPoint {
    fun hub(): CaptureHub

    fun queue(): FrameQueue
}

/**
 * The home-screen widget: the capture glyph and word, pendant battery, queue length and one Mute/Unmute action,
 * like [CaptureNotification]. It follows the launcher's light or dark and the wallpaper's colours; the glyph keeps
 * the capture green while recording, so that colour never moves.
 */
class CaptureWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, CaptureWidgetEntryPoint::class.java)
        provideContent {
            val status by entry.hub().status.collectAsState()
            val usage by entry.queue().usage.collectAsState()
            GlanceTheme { CaptureWidgetContent(status, usage) }
        }
    }
}

/** Capture's colour for the widget, fixed like the app's: it never follows the wallpaper. */
private val CaptureGreen = ColorProvider(day = Color(0xFF2C6A3B), night = Color(0xFF8ED39C))

@Composable
fun CaptureWidgetContent(
    status: CaptureStatus,
    usage: QueueUsage,
) {
    val context = LocalContext.current
    val state = CaptureState.of(status)
    val glyph = if (state == CaptureState.Recording) CaptureGreen else GlanceTheme.colors.onSurfaceVariant
    val toggle =
        actionStartService(
            Intent(context, CaptureService::class.java)
                .setAction(if (status.muted) CaptureService.ACTION_UNMUTE else CaptureService.ACTION_MUTE),
            // onStartCommand calls startForeground at once, so a stopped service must be started as a foreground one.
            isForegroundService = true,
        )
    val battery = status.battery?.let { "$it%" } ?: context.getString(R.string.unknown)
    val body = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp)
    Column(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                ImageProvider(state.iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(16.dp),
                colorFilter = ColorFilter.tint(glyph),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                context.getString(state.wordRes),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
        }
        Text(context.getString(R.string.pendant_battery_format, battery), style = body)
        Text(context.getString(R.string.queued_chunks_format, usage.chunks), style = body)
        // A padding modifier on the Button would replace its own, so the gap is a Spacer.
        Spacer(GlanceModifier.height(4.dp))
        Button(text = context.getString(if (status.muted) R.string.unmute else R.string.mute), onClick = toggle)
    }
}
