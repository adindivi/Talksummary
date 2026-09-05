package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

/**
 * Foreground Service that keeps CPU awake and maintains OS priority
 * during On-Device AI inference and audio playback.
 */
class InferenceForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "qwen_ai_inference_channel"
        const val NOTIFICATION_ID = 9527

        const val ACTION_START_INFERENCE = "com.example.action.START_INFERENCE"
        const val ACTION_UPDATE_PROGRESS = "com.example.action.UPDATE_PROGRESS"
        const val ACTION_STOP_INFERENCE = "com.example.action.STOP_INFERENCE"
        const val ACTION_ABORT_CLICKED = "com.example.action.ABORT_CLICKED"

        const val EXTRA_STATUS_TEXT = "extra_status_text"
        const val EXTRA_TPS = "extra_tps"

        fun start(context: Context, statusText: String = "AI 답변 생성 중...") {
            val intent = Intent(context, InferenceForegroundService::class.java).apply {
                action = ACTION_START_INFERENCE
                putExtra(EXTRA_STATUS_TEXT, statusText)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateProgress(context: Context, statusText: String, tps: Double = 0.0) {
            val intent = Intent(context, InferenceForegroundService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra(EXTRA_STATUS_TEXT, statusText)
                putExtra(EXTRA_TPS, tps)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, InferenceForegroundService::class.java).apply {
                action = ACTION_STOP_INFERENCE
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_INFERENCE -> {
                val statusText = intent.getStringExtra(EXTRA_STATUS_TEXT) ?: "AI 답변 생성 중..."
                val notification = buildNotification(statusText)
                startForeground(NOTIFICATION_ID, notification)
            }
            ACTION_UPDATE_PROGRESS -> {
                val statusText = intent.getStringExtra(EXTRA_STATUS_TEXT) ?: "AI 답변 생성 중..."
                val tps = intent.getDoubleExtra(EXTRA_TPS, 0.0)
                val content = if (tps > 0.0) "$statusText (${String.format(java.util.Locale.US, "%.1f", tps)} tok/s)" else statusText
                val notification = buildNotification(content)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, notification)
            }
            ACTION_STOP_INFERENCE -> {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_ABORT_CLICKED -> {
                // Broadcast abort intent to MainActivity/ViewModel
                val abortIntent = Intent("com.example.intent.ABORT_INFERENCE")
                sendBroadcast(abortIntent)
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val abortIntent = Intent(this, InferenceForegroundService::class.java).apply {
            action = ACTION_ABORT_CLICKED
        }
        val abortPendingIntent = PendingIntent.getService(
            this,
            1,
            abortIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🤖 Qwen 온디바이스 AI")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause, "생성 중단", abortPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI 추론 및 백그라운드 작업",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "온디바이스 AI 토큰 생성 및 음성 읽기 백그라운드 유지 알림"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "QwenRunner:InferenceWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10 minutes max safety limit
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
