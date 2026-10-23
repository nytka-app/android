package io.github.nytka_app.capture

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import io.github.nytka_app.core.context.Activity

/**
 * Activity transitions from Google Play services: entering and leaving still, walking, running, cycling and in a
 * vehicle, delivered by Play services in batches. Nothing is polled; the permission is `ACTIVITY_RECOGNITION`.
 */
class TransitionWatcher(
    private val context: Context,
) : ActivityWatcher {
    private var receiver: BroadcastReceiver? = null
    private var pending: PendingIntent? = null
    private var entered: Activity? = null

    @SuppressLint("MissingPermission") // checked first
    override fun start(onActivity: (Activity?, Long) -> Unit): Boolean {
        if (receiver != null) return true
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) return false
        val events =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    val result = ActivityTransitionResult.extractResult(intent) ?: return
                    result.transitionEvents.forEach { event ->
                        val activity = ACTIVITIES[event.activityType] ?: return@forEach
                        val atMs = event.elapsedRealTimeNanos / 1_000_000
                        when {
                            event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                                entered = activity
                                onActivity(activity, atMs)
                            }

                            // Leaving an activity other than the last entered says nothing about now.
                            activity == entered -> {
                                entered = null
                                onActivity(null, atMs)
                            }
                        }
                    }
                }
            }
        ContextCompat.registerReceiver(context, events, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        // Mutable: Play services adds the result to the intent.
        val intent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        val transitions =
            ACTIVITIES.keys.flatMap { type ->
                listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT).map {
                    ActivityTransition
                        .Builder()
                        .setActivityType(type)
                        .setActivityTransition(it)
                        .build()
                }
            }
        ActivityRecognition
            .getClient(
                context,
            ).requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), intent)
        receiver = events
        pending = intent
        return true
    }

    @SuppressLint("MissingPermission")
    override fun stop() {
        pending?.let {
            try {
                ActivityRecognition.getClient(context).removeActivityTransitionUpdates(it)
            } catch (_: SecurityException) {
                // The permission was revoked, which ends the updates anyway.
            }
        }
        receiver?.let { context.unregisterReceiver(it) }
        pending = null
        receiver = null
        entered = null
    }

    companion object {
        const val AVAILABLE = true
        private const val ACTION = "io.github.nytka_app.ACTIVITY_TRANSITIONS"

        private val ACTIVITIES =
            mapOf(
                DetectedActivity.STILL to Activity.STILL,
                DetectedActivity.WALKING to Activity.WALKING,
                DetectedActivity.RUNNING to Activity.RUNNING,
                DetectedActivity.ON_BICYCLE to Activity.CYCLING,
                DetectedActivity.IN_VEHICLE to Activity.VEHICLE,
            )
    }
}
