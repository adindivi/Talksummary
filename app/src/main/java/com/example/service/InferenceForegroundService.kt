package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.util.AppLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Foreground Service that keeps CPU awake and maintains OS priority
 * during On-Device AI inference, bulk summaries, and large text parsing.
 */
class InferenceForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "talksummary_background_channel"
        const val NOTIFICATION_ID = 9527

        const val ACTION_START_TASK = "com.example.action.START_TASK"
        const val ACTION_START_INFERENCE = "com.example.action.START_INFERENCE"
        const val ACTION_UPDATE_PROGRESS = "com.example.action.UPDATE_PROGRESS"
        const val ACTION_STOP_TASK = "com.example.action.STOP_TASK"
        const val ACTION_STOP_INFERENCE = "com.example.action.STOP_INFERENCE"
        const val ACTION_ABORT_CLICKED = "com.example.action.ABORT_CLICKED"
        const val ACTION_CANCEL_TASK = "com.example.intent.ABORT_INFERENCE"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_STATUS_TEXT = "extra_status_text"
        const val EXTRA_CURRENT = "extra_current"
        const val EXTRA_TOTAL = "extra_total"
        const val EXTRA_TPS = "extra_tps"

        // In-process shared event flow for immediate cooperative cancellation
        private val _abortEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val abortEvents: SharedFlow<Unit> = _abortEvents.asSharedFlow()

        /**
         * Starts the foreground service for a long-running background task.
         */
        fun start(
            context: Context,
            title: String = "⚡ AI 작업 진행 중",
            statusText: String = "작업을 준비하고 있습니다...",
            current: Int = 0,
            total: Int = 0
        ) {
            val intent = Intent(context, InferenceForegroundService::class.java).apply {
                action = ACTION_START_TASK
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_STATUS_TEXT, statusText)
                putExtra(EXTRA_CURRENT, current)
                putExtra(EXTRA_TOTAL, total)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                AppLogger.e("InferenceForegroundService", "Failed to start service: ${e.message}", e)
            }
        }

        /**
         * Updates the task progress and notification content.
         */
        fun updateProgress(
            context: Context,
            statusText: String,
            current: Int = 0,
            total: Int = 0,
            title: String? = null,
            tps: Double = 0.0
        ) {
            val intent = Intent(context, InferenceForegroundService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra(EXTRA_STATUS_TEXT, statusText)
                putExtra(EXTRA_CURRENT, current)
                putExtra(EXTRA_TOTAL, total)
                putExtra(EXTRA_TPS, tps)
                if (title != null) putExtra(EXTRA_TITLE, title)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                AppLogger.e("InferenceForegroundService", "Failed to update progress: ${e.message}", e)
            }
        }

        /**
         * Stops the foreground service and releases resources.
         */
        fun stop(context: Context) {
            val intent = Intent(context, InferenceForegroundService::class.java).apply {
                action = ACTION_STOP_TASK
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                AppLogger.e("InferenceForegroundService", "Failed to stop service: ${e.message}", e)
            }
        }
    }

    private var currentTitle: String = "⚡ AI 작업 진행 중"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TASK, ACTION_START_INFERENCE -> {
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: "⚡ AI 작업 진행 중"
                val statusText = intent.getStringExtra(EXTRA_STATUS_TEXT) ?: "작업을 준비하고 있습니다..."
                val current = intent.getIntExtra(EXTRA_CURRENT, 0)
                val total = intent.getIntExtra(EXTRA_TOTAL, 0)
                val notification = buildNotification(currentTitle, statusText, current, total)
                try {
                    acquireWakeLock()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        } else {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        }
                        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (e: Exception) {
                    AppLogger.e("InferenceForegroundService", "startForeground failed: ${e.message}", e)
                }
            }
            ACTION_UPDATE_PROGRESS -> {
                acquireWakeLock() // Refresh wakelock safety window during ongoing tasks
                intent.getStringExtra(EXTRA_TITLE)?.let { currentTitle = it }
                val statusText = intent.getStringExtra(EXTRA_STATUS_TEXT) ?: "작업 진행 중..."
                val current = intent.getIntExtra(EXTRA_CURRENT, 0)
                val total = intent.getIntExtra(EXTRA_TOTAL, 0)
                val tps = intent.getDoubleExtra(EXTRA_TPS, 0.0)

                val content = if (tps > 0.0) {
                    "$statusText (${String.format(java.util.Locale.US, "%.1f", tps)} tok/s)"
                } else {
                    statusText
                }

                val notification = buildNotification(currentTitle, content, current, total)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, notification)
            }
            ACTION_STOP_TASK, ACTION_STOP_INFERENCE -> {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_ABORT_CLICKED -> {
                AppLogger.i("InferenceForegroundService", "User requested task abort from notification")
                _abortEvents.tryEmit(Unit)
                val abortIntent = Intent(ACTION_CANCEL_TASK).apply {
                    setPackage(packageName)
                }
                sendBroadcast(abortIntent)
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(
        title: String,
        contentText: String,
        current: Int = 0,
        total: Int = 0
    ): Notification {
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

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "작업 중단", abortPendingIntent)

        if (total > 0) {
            builder.setProgress(total, current, false)
            builder.setSubText("$current / $total")
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TalkSummary 백그라운드 작업",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AI 대화 요약, 대용량 파일 분석 및 온디바이스 모델 실행 유지"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "TalkSummary:TaskWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.let {
                if (!it.isHeld) {
                    it.acquire(15 * 60 * 1000L) // 15 minutes max safety limit
                    AppLogger.d("InferenceForegroundService", "WakeLock acquired")
                }
            }
        } catch (e: Exception) {
            AppLogger.w("InferenceForegroundService", "Failed to acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    AppLogger.d("InferenceForegroundService", "WakeLock released")
                }
            }
        } catch (e: Exception) {
            AppLogger.w("InferenceForegroundService", "Failed to release WakeLock: ${e.message}")
        } finally {
            wakeLock = null
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
