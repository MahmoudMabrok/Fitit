package tools.mo3ta.fitit.ui.videoenhancer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tools.mo3ta.fitit.MainActivity
import tools.mo3ta.fitit.R
import tools.mo3ta.fitit.analytics.AnalyticsManager
import java.io.File

/**
 * Runs a video-enhance job as a foreground service so it keeps going after the user leaves the
 * enhancer screen or backgrounds the whole app.
 *
 * The job's progress and result are published through [VideoEnhanceManager], which any live
 * [VideoEnhancerViewModel] observes. An ongoing notification shows the percentage while running, and
 * a separate notification fires when the clip is ready (or failed) — tapping it reopens the app on the
 * enhancer, where the result card is already populated from the manager state. On success the output
 * is also auto-saved to the gallery, so the result survives even if the user never comes back.
 */
class VideoEnhancerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelCurrent()
            return START_NOT_STICKY
        }

        val uriString = intent?.getStringExtra(EXTRA_URI)
        if (uriString == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Promote to foreground immediately so the system doesn't kill us mid-decode.
        startForegroundProgress(0f)

        // One job at a time: a second request while we're busy is ignored rather than racing the
        // shared encoder/EGL resources. The UI already disables Enhance while a run is in progress.
        if (job?.isActive == true) return START_NOT_STICKY

        val uri = Uri.parse(uriString)
        val level = intent.getStringExtra(EXTRA_LEVEL)?.let { runCatching { EnhancementLevel.valueOf(it) }.getOrNull() }
            ?: EnhancementLevel.STANDARD
        val engine = intent.getStringExtra(EXTRA_ENGINE)?.let { runCatching { EnhanceEngine.valueOf(it) }.getOrNull() }
            ?: EnhanceEngine.GL
        val speedMode = intent.getStringExtra(EXTRA_SPEED)?.let { runCatching { MlSpeedMode.valueOf(it) }.getOrNull() }
            ?: MlSpeedMode.BALANCED
        val capOverride = intent.getIntExtra(EXTRA_CAP, -1).takeIf { it > 0 }

        job = scope.launch { runEnhance(uri, level, engine, speedMode, capOverride) }
        return START_NOT_STICKY
    }

    private suspend fun runEnhance(
        uri: Uri,
        level: EnhancementLevel,
        engine: EnhanceEngine,
        speedMode: MlSpeedMode,
        capOverride: Int?,
    ) {
        VideoEnhanceManager.update(EnhanceState.Running(0f))
        val outputDir = File(cacheDir, "enhanced_videos").also { it.mkdirs() }
        val outputFile = File(outputDir, "enhanced_${System.currentTimeMillis()}.mp4")
        var lastNotifiedPercent = -1
        var cancelled = false

        try {
            val usedEngine = VideoEnhancer.enhance(
                this, uri, outputFile, level, engine, speedMode, capOverride,
            ) { p ->
                VideoEnhanceManager.update(EnhanceState.Running(p))
                val percent = (p * 100).toInt()
                if (percent != lastNotifiedPercent) {
                    lastNotifiedPercent = percent
                    notifyProgress(p)
                }
            }
            val fellBackToGl = engine == EnhanceEngine.ML && usedEngine == EnhanceEngine.GL
            try { AnalyticsManager.trackVideoEnhanceCompleted(level.name) } catch (_: Exception) {}

            // Auto-save so the result is durable even if the user never returns to the app.
            val savedUri = runCatching { saveVideoToGallery(this, outputFile) }.getOrNull()
            if (savedUri != null) {
                try { AnalyticsManager.trackVideoEnhanceSaved() } catch (_: Exception) {}
            }

            VideoEnhanceManager.update(
                EnhanceState.Success(outputFile, outputFile.length(), savedUri != null, fellBackToGl),
            )
            notifyDone(savedToGallery = savedUri != null)
        } catch (_: CancellationException) {
            // User-requested cancel: discard the partial output and clear the result quietly.
            cancelled = true
            outputFile.delete()
            VideoEnhanceManager.update(EnhanceState.Idle)
            try { AnalyticsManager.trackVideoEnhanceCancelled() } catch (_: Exception) {}
        } catch (e: Exception) {
            outputFile.delete()
            VideoEnhanceManager.update(EnhanceState.Failed(e.message))
            notifyFailed()
        } finally {
            if (cancelled) {
                // Drop the ongoing notification entirely — there's nothing to show.
                NotificationManagerCompat.from(this).cancel(NOTIF_ID_PROGRESS)
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            } else {
                // Detach so the done/failed notification stays after we drop the ongoing one.
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            }
            stopSelf()
        }
    }

    /**
     * Cancels the running job (if any) and tears the service down. The job's own `finally` clears the
     * notification and stops the service; when nothing is running we clean up directly.
     */
    private fun cancelCurrent() {
        val active = job
        if (active != null && active.isActive) {
            active.cancel()
            return
        }
        VideoEnhanceManager.reset()
        NotificationManagerCompat.from(this).cancel(NOTIF_ID_PROGRESS)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    // region notifications

    private fun startForegroundProgress(progress: Float) {
        ServiceCompat.startForeground(
            this,
            NOTIF_ID_PROGRESS,
            buildProgressNotification(progress),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun notifyProgress(progress: Float) {
        if (!hasNotificationPermission()) return
        NotificationManagerCompat.from(this).notify(NOTIF_ID_PROGRESS, buildProgressNotification(progress))
    }

    private fun buildProgressNotification(progress: Float): Notification {
        val percent = (progress * 100).toInt().coerceIn(0, 100)
        return NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(getString(R.string.video_enhancer_notification_processing_title))
            .setContentText(getString(R.string.video_enhancer_notification_processing_text, percent))
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openEnhancerIntent())
            .addAction(0, getString(R.string.video_enhancer_cancel), cancelIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun notifyDone(savedToGallery: Boolean) {
        if (!hasNotificationPermission()) return
        val text = getString(
            if (savedToGallery) R.string.video_enhancer_notification_done_saved
            else R.string.video_enhancer_notification_done_text,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(getString(R.string.video_enhancer_notification_done_title))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openEnhancerIntent())
            .build()
        NotificationManagerCompat.from(this).notify(NOTIF_ID_RESULT, notification)
    }

    private fun notifyFailed() {
        if (!hasNotificationPermission()) return
        val notification = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(getString(R.string.video_enhancer_notification_failed_title))
            .setContentText(getString(R.string.video_enhancer_notification_failed_text))
            .setAutoCancel(true)
            .setContentIntent(openEnhancerIntent())
            .build()
        NotificationManagerCompat.from(this).notify(NOTIF_ID_RESULT, notification)
    }

    /** Tap target that (re)opens the app straight on the video enhancer screen. */
    private fun openEnhancerIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DEST_VIDEO_ENHANCER)
        }
        return PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelIntent(): PendingIntent {
        val intent = Intent(this, VideoEnhancerService::class.java).apply { action = ACTION_CANCEL }
        return PendingIntent.getService(
            this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun hasNotificationPermission(): Boolean =
        NotificationManagerCompat.from(this).areNotificationsEnabled()

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                getString(R.string.video_enhancer_notification_channel_progress),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DONE,
                getString(R.string.video_enhancer_notification_channel_done),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    // endregion

    companion object {
        private const val ACTION_CANCEL = "tools.mo3ta.fitit.action.CANCEL_ENHANCE"
        private const val EXTRA_URI = "extra_uri"
        private const val EXTRA_LEVEL = "extra_level"
        private const val EXTRA_ENGINE = "extra_engine"
        private const val EXTRA_SPEED = "extra_speed"
        private const val EXTRA_CAP = "extra_cap"

        private const val CHANNEL_PROGRESS = "video_enhance_progress"
        private const val CHANNEL_DONE = "video_enhance_done"
        private const val NOTIF_ID_PROGRESS = 4101
        private const val NOTIF_ID_RESULT = 4102

        /** Kicks off a background enhance run for [uri] with the chosen options. */
        fun start(
            context: Context,
            uri: Uri,
            level: EnhancementLevel,
            engine: EnhanceEngine,
            speedMode: MlSpeedMode,
            capOverride: Int?,
        ) {
            val intent = Intent(context, VideoEnhancerService::class.java).apply {
                putExtra(EXTRA_URI, uri.toString())
                putExtra(EXTRA_LEVEL, level.name)
                putExtra(EXTRA_ENGINE, engine.name)
                putExtra(EXTRA_SPEED, speedMode.name)
                capOverride?.let { putExtra(EXTRA_CAP, it) }
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** Requests cancellation of the in-progress run, if any. */
        fun cancel(context: Context) {
            val intent = Intent(context, VideoEnhancerService::class.java).apply { action = ACTION_CANCEL }
            // The service is already running in the foreground, so a plain command delivery is enough.
            runCatching { context.startService(intent) }
        }
    }
}
