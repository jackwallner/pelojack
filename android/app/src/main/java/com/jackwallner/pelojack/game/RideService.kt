package com.jackwallner.pelojack.game

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jackwallner.pelojack.MainActivity
import com.jackwallner.pelojack.PelojackApp
import com.jackwallner.pelojack.R
import com.jackwallner.pelojack.ride.RidePhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Keeps the application-owned ride alive while RuneScape or Nanit is in front. */
class RideService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as PelojackApp

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Ride in progress", NotificationManager.IMPORTANCE_LOW),
        )
        // Meet the foreground deadline even if a very short ride ends before service startup.
        val state = (app.ride.phase.value as? RidePhase.Riding)?.state
        startForeground(NOTIFICATION, notification(state?.title ?: "Ride in progress", state?.paused ?: true))
        scope.launch {
            app.ride.phase.map { phase -> (phase as? RidePhase.Riding)?.state?.let { it.title to it.paused } }
                .distinctUntilChanged().collect { state ->
                    if (state == null) stopSelf()
                    else getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(state.first, state.second))
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = (app.ride.phase.value as? RidePhase.Riding)?.state
        if (state == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION, notification(state.title, state.paused))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(title: String, paused: Boolean) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_ride_notification)
        .setContentTitle(title)
        .setContentText(if (paused) "Paused. Tap to return to your ride." else "Recording your ride. Tap for ride controls.")
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .setOngoing(true)
        .setSilent(true)
        .build()

    companion object {
        private const val CHANNEL = "ride"
        private const val NOTIFICATION = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, RideService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RideService::class.java))
        }
    }
}
