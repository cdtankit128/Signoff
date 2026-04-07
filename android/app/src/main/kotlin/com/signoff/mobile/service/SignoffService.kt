package com.signoff.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.signoff.mobile.MainActivity
import com.signoff.mobile.network.SignoffWebSocketClient
import com.signoff.mobile.sensor.BleAdvertiser
import com.signoff.mobile.sensor.MotionDetector

/**
 * Foreground service that keeps SignOff running in the background.
 * Manages WebSocket connection + motion detection + heartbeat sending.
 */
class SignoffService : Service() {

    companion object {
        private const val TAG = "SignoffService"
        private const val CHANNEL_ID = "signoff_channel"
        private const val NOTIFICATION_ID = 1
        private const val HEARTBEAT_INTERVAL_MS = 2000L

        const val EXTRA_SERVER_URL = "server_url"
        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_MODE = "run_mode" // "wifi" or "ble"
    }

    private var wsClient: SignoffWebSocketClient? = null
    private var bleAdvertiser: BleAdvertiser? = null
    private var motionDetector: MotionDetector? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var heartbeatCount = 0L

    // Heartbeat runnable — sends every 2 seconds
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            val detector = motionDetector ?: return
            wsClient?.sendHeartbeat(detector.isMoving, detector.currentAcceleration)
            heartbeatCount++

            if (heartbeatCount % 30 == 0L) {
                Log.d(TAG, "💓 Heartbeat #$heartbeatCount | moving=${detector.isMoving} | " +
                        "accel=${String.format("%.2f", detector.currentAcceleration)} m/s²")
            }

            handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val serverUrl = intent?.getStringExtra(EXTRA_SERVER_URL) ?: return START_NOT_STICKY
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: "android-phone"
        val runMode = intent.getStringExtra(EXTRA_MODE) ?: "wifi"

        Log.d(TAG, "🚀 Starting SignOff service — Mode: $runMode, Server: $serverUrl")

        // Start as foreground service
        startForeground(NOTIFICATION_ID, buildNotification("Connecting..."))

        // Acquire wake lock to prevent CPU sleep
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SignOff::HeartbeatWakeLock"
        ).apply { acquire(24 * 60 * 60 * 1000L) } // 24 hours max

        // Initialize motion detector
        motionDetector = MotionDetector(this).apply {
            onMotionChanged = { moving, accel ->
                Log.d(TAG, "Motion changed: moving=$moving, accel=$accel")
                updateNotification(if (moving) "📱 Moving (${String.format("%.1f", accel)} m/s²)"
                                   else "🧘 Still — Laptop safe")
            }
            start()
        }

        if (runMode == "wifi") {
            // Initialize WebSocket
            wsClient = SignoffWebSocketClient(serverUrl, deviceId).apply {
                onConnected = {
                    Log.d(TAG, "✅ Connected to server!")
                    updateNotification("🟢 Connected (Wi-Fi) — monitoring proximity")
                    // Start heartbeat loop
                    handler.post(heartbeatRunnable)
                }
                onDisconnected = {
                    Log.d(TAG, "❌ Disconnected from server")
                    updateNotification("🔴 Disconnected — reconnecting...")
                    handler.removeCallbacks(heartbeatRunnable)
                }
                onError = { error ->
                    Log.e(TAG, "WebSocket error: $error")
                    updateNotification("⚠️ Error: $error")
                }
                connect()
            }
        } else if (runMode == "ble") {
            updateNotification("🔵 Bluetooth Beacon Active")
            bleAdvertiser = BleAdvertiser(this).apply {
                startAdvertising()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "🛑 Stopping SignOff service")
        handler.removeCallbacks(heartbeatRunnable)
        wsClient?.disconnect()
        bleAdvertiser?.stopAdvertising()
        motionDetector?.stop()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification ──

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SignOff Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps SignOff proximity monitoring active"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SignOff Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(content))
    }
}
