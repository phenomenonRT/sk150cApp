package com.sk150c.control.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sk150c.control.MainActivity
import com.sk150c.control.SK150CApplication
import com.sk150c.control.data.ConnectionPhase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class PowerSupplyService : Service() {

    private val CHANNEL_ID = "power_supply_status"
    private val NOTIFICATION_ID = 101

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repository = (application as SK150CApplication).repository
        
        if (intent?.action == "ACTION_DISCONNECT") {
            repository.disconnect()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            repository.uiState.collectLatest { state ->
                if (state.phase == ConnectionPhase.LIVE) {
                    val voltsAmps = java.util.Locale.US.let { loc ->
                        String.format(loc, "%.2fV | %.3fA", state.reading.vOut, state.reading.iOut)
                    }
                    val status = if (state.chargingActive) {
                        "Battery Lab: ${state.activeProfile?.name ?: "Active"}"
                    } else {
                        "Monitoring"
                    }
                    updateNotification(voltsAmps, status)
                } else if (state.phase == ConnectionPhase.IDLE || state.phase == ConnectionPhase.ERROR) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    stopSelf()
                }
            }
        }

        // Start with an initial empty notification to satisfy foreground requirements
        val notification = buildNotification("Connecting...", "Waiting for module data")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        return START_STICKY
    }

    private fun updateNotification(text: String, subtext: String) {
        val notification = buildNotification(text, subtext)
        val notificationManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getSystemService(NotificationManager::class.java)
        } else {
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        }
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String, subtext: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val disconnectIntent = PendingIntent.getService(
            this, 1, Intent(this, PowerSupplyService::class.java).apply { action = "ACTION_DISCONNECT" },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ZK-SK150C Power Supply")
            .setContentText(text)
            .setSubText(subtext)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnectIntent)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Power Supply Status",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
