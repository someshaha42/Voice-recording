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
import com.example.R

/**
 * Foreground service with foregroundServiceType="microphone" and PARTIAL_WAKE_LOCK
 * to guarantee uninterrupted background and screen-off voiceover recording.
 */
class RecordingForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING,
            ACTION_UPDATE_STATE -> {
                val mode = intent.getStringExtra(EXTRA_STATUS_MODE) ?: MODE_RECORDING
                acquireWakeLock()
                val notification = buildNotification(mode)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_PAUSE_FROM_NOTIFICATION -> {
                sendBroadcast(Intent(BROADCAST_ACTION).setPackage(packageName).putExtra(EXTRA_COMMAND, CMD_PAUSE))
            }
            ACTION_RESUME_FROM_NOTIFICATION -> {
                sendBroadcast(Intent(BROADCAST_ACTION).setPackage(packageName).putExtra(EXTRA_COMMAND, CMD_RESUME))
            }
            ACTION_STOP_FROM_NOTIFICATION -> {
                sendBroadcast(Intent(BROADCAST_ACTION).setPackage(packageName).putExtra(EXTRA_COMMAND, CMD_STOP))
                stopSelfCleanly()
            }
            ACTION_STOP_SERVICE -> {
                stopSelfCleanly()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun stopSelfCleanly() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoxForge::RecordingWakeLock").apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L) // max 4h safety cap
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(mode: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (mode) {
            MODE_PAUSED -> getString(R.string.notification_title_paused)
            MODE_MONITORING -> getString(R.string.notification_title_monitoring)
            else -> getString(R.string.notification_title_recording)
        }

        val subtitle = when (mode) {
            MODE_PAUSED -> "WAV 48 kHz • Recording paused"
            MODE_MONITORING -> "Real-time DSP monitoring active"
            else -> "WAV 48 kHz • 16-bit Mono • 768 kbps"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (mode == MODE_RECORDING) {
            val pauseIntent = PendingIntent.getService(
                this,
                1,
                Intent(this, RecordingForegroundService::class.java).setAction(ACTION_PAUSE_FROM_NOTIFICATION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, getString(R.string.notification_action_pause), pauseIntent)
        } else if (mode == MODE_PAUSED) {
            val resumeIntent = PendingIntent.getService(
                this,
                2,
                Intent(this, RecordingForegroundService::class.java).setAction(ACTION_RESUME_FROM_NOTIFICATION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, getString(R.string.notification_action_resume), resumeIntent)
        }

        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, RecordingForegroundService::class.java).setAction(ACTION_STOP_FROM_NOTIFICATION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(0, getString(R.string.notification_action_stop), stopIntent)

        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "voxforge_audio_engine"
        const val NOTIFICATION_ID = 4091

        const val ACTION_START_RECORDING = "com.example.voxforge.START"
        const val ACTION_UPDATE_STATE = "com.example.voxforge.UPDATE"
        const val ACTION_STOP_SERVICE = "com.example.voxforge.STOP"

        private const val ACTION_PAUSE_FROM_NOTIFICATION = "com.example.voxforge.NOTIF_PAUSE"
        private const val ACTION_RESUME_FROM_NOTIFICATION = "com.example.voxforge.NOTIF_RESUME"
        private const val ACTION_STOP_FROM_NOTIFICATION = "com.example.voxforge.NOTIF_STOP"

        const val BROADCAST_ACTION = "com.example.voxforge.SERVICE_CMD"
        const val EXTRA_COMMAND = "cmd"
        const val CMD_PAUSE = "pause"
        const val CMD_RESUME = "resume"
        const val CMD_STOP = "stop"

        const val EXTRA_STATUS_MODE = "status_mode"
        const val MODE_RECORDING = "recording"
        const val MODE_PAUSED = "paused"
        const val MODE_MONITORING = "monitoring"

        fun syncServiceState(context: Context, isRecording: Boolean, isPaused: Boolean, isMonitoring: Boolean) {
            val mode = when {
                isRecording && isPaused -> MODE_PAUSED
                isRecording -> MODE_RECORDING
                isMonitoring -> MODE_MONITORING
                else -> null
            }

            if (mode == null) {
                val stopIntent = Intent(context, RecordingForegroundService::class.java).setAction(ACTION_STOP_SERVICE)
                runCatching { context.startService(stopIntent) }
            } else {
                val intent = Intent(context, RecordingForegroundService::class.java).apply {
                    action = ACTION_START_RECORDING
                    putExtra(EXTRA_STATUS_MODE, mode)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }
}
