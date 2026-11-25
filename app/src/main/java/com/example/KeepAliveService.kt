package com.example

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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KeepAliveService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var wakeLock: PowerManager.WakeLock? = null
  private var countdownJob: Job? = null
  private lateinit var audioDuckTtsManager: AudioDuckTtsManager
  private lateinit var notificationManager: NotificationManager

  override fun onCreate() {
    super.onCreate()
    audioDuckTtsManager = AudioDuckTtsManager.getInstance(applicationContext)
    notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    ensureServiceChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP_MONITOR -> {
        countdownJob?.cancel()
        _countdownSeconds.value = null
        _isRunning.value = false
        releaseWakeLock()
        stopForegroundCompat()
        stopSelf()
        return START_NOT_STICKY
      }
      ACTION_START_DUCK_TEST -> {
        ensureForegroundAndWakeLock()
        startDuckingCountdownTest()
      }
      else -> {
        ensureForegroundAndWakeLock()
      }
    }
    return START_STICKY
  }

  private fun ensureForegroundAndWakeLock() {
    val notification = buildServiceNotification(countdownSeconds = _countdownSeconds.value)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
    _isRunning.value = true
    acquireWakeLock()
  }

  private fun startDuckingCountdownTest() {
    countdownJob?.cancel()
    countdownJob =
      serviceScope.launch {
        for (sec in 5 downTo 1) {
          _countdownSeconds.value = sec
          notificationManager.notify(
            NOTIFICATION_ID,
            buildServiceNotification(countdownSeconds = sec),
          )
          delay(1_000L)
        }
        _countdownSeconds.value = null
        notificationManager.notify(
          NOTIFICATION_ID,
          buildServiceNotification(countdownSeconds = null),
        )
        audioDuckTtsManager.speakAndDuck(
          message =
            "Alerte test CyberBrowser. Le volume de votre vidéo a été baissé automatiquement, puis va revenir à la normale.",
          repeatCount = 1,
          playChime = true,
          title = "Test Audio Ducking CyberBrowser",
        )
      }
  }

  private fun acquireWakeLock() {
    if (wakeLock?.isHeld == true) return
    val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
    wakeLock =
      powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
        setReferenceCounted(false)
        // Durée maximale de sécurité de 8 heures (renouvelée si redémarrage).
        acquire(8 * 60 * 60 * 1000L)
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

  private fun stopForegroundCompat() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } else {
      @Suppress("DEPRECATION")
      stopForeground(true)
    }
  }

  private fun buildServiceNotification(countdownSeconds: Int?): Notification {
    val pendingFlags =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT
      }

    val openAppIntent =
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val openPendingIntent = PendingIntent.getActivity(this, 10, openAppIntent, pendingFlags)

    val stopIntent =
      Intent(this, KeepAliveService::class.java).apply { action = ACTION_STOP_MONITOR }
    val stopPendingIntent = PendingIntent.getService(this, 11, stopIntent, pendingFlags)

    val contentText =
      if (countdownSeconds != null) {
        getString(R.string.notif_service_countdown, countdownSeconds)
      } else {
        getString(R.string.notif_service_text)
      }

    return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_shield_pulse)
      .setContentTitle(getString(R.string.notif_service_title))
      .setContentText(contentText)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setContentIntent(openPendingIntent)
      .addAction(0, getString(R.string.notif_action_stop), stopPendingIntent)
      .build()
  }

  private fun ensureServiceChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val existing = notificationManager.getNotificationChannel(SERVICE_CHANNEL_ID)
      if (existing == null) {
        val channel =
          NotificationChannel(
              SERVICE_CHANNEL_ID,
              getString(R.string.notif_channel_service_name),
              NotificationManager.IMPORTANCE_LOW,
            )
            .apply {
              description = getString(R.string.notif_channel_service_desc)
              setShowBadge(false)
            }
        notificationManager.createNotificationChannel(channel)
      }
    }
  }

  override fun onDestroy() {
    countdownJob?.cancel()
    _countdownSeconds.value = null
    _isRunning.value = false
    releaseWakeLock()
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    private const val SERVICE_CHANNEL_ID = "cyber_keepalive_channel"
    private const val NOTIFICATION_ID = 1001
    private const val WAKE_LOCK_TAG = "CyberBrowser::KeepAliveWakeLock"

    const val ACTION_START_MONITOR = "com.example.action.START_MONITOR"
    const val ACTION_STOP_MONITOR = "com.example.action.STOP_MONITOR"
    const val ACTION_START_DUCK_TEST = "com.example.action.START_DUCK_TEST"

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _countdownSeconds = MutableStateFlow<Int?>(null)
    val countdownSeconds: StateFlow<Int?> = _countdownSeconds.asStateFlow()

    fun startMonitor(context: Context) {
      val intent =
        Intent(context, KeepAliveService::class.java).apply { action = ACTION_START_MONITOR }
      ContextCompat.startForegroundService(context, intent)
    }

    fun stopMonitor(context: Context) {
      val intent =
        Intent(context, KeepAliveService::class.java).apply { action = ACTION_STOP_MONITOR }
      runCatching { context.startService(intent) }
    }

    fun startDuckTestCountdown(context: Context) {
      val intent =
        Intent(context, KeepAliveService::class.java).apply { action = ACTION_START_DUCK_TEST }
      ContextCompat.startForegroundService(context, intent)
    }
  }
}
