package com.example.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.AdhanAlarmActivity
import com.example.R
import com.example.MainActivity
import com.example.data.AdhanManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class AdhanPlayerService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var iqamahPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var progressiveVolumeJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var audioFocusRequest: AudioFocusRequest? = null

    companion object {
        val isAthanPlaying = MutableStateFlow(false)
        val isIqamahPlaying = MutableStateFlow(false)
        val currentAthanPrayer = MutableStateFlow("")
        val currentAthanArabic = MutableStateFlow("")

        fun startAthan(context: Context, prayerName: String, prayerArabic: String) {
            val intent = Intent(context, AdhanPlayerService::class.java).apply {
                putExtra("PRAYER_NAME", prayerName)
                putExtra("PRAYER_ARABIC", prayerArabic)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("AdhanPlayerService", "Failed to startForegroundService", e)
            }
        }
        
        fun stopAthan(context: Context) {
            val intent = Intent(context, AdhanPlayerService::class.java).apply {
                action = "com.example.ACTION_STOP_ATHAN"
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e("AdhanPlayerService", "Failed to stop athan service", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("AdhanPlayerService", "Service onCreate")
        
        // Grab WakeLock to ensure smooth background playback
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Emaniat:AdhanWakeLock").apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10 minutes max lock
            }
        } catch (e: Exception) {
            Log.e("AdhanPlayerService", "Error acquiring WakeLock", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "com.example.ACTION_STOP_ATHAN") {
            Log.d("AdhanPlayerService", "Stop action triggered")
            AdhanAlarmReceiver.stopFallbackAudio()
            stopPlayback()
            stopSelf()
            return START_NOT_STICKY
        }

        val prayerName = intent?.getStringExtra("PRAYER_NAME") ?: "Fajr"
        val prayerArabic = intent?.getStringExtra("PRAYER_ARABIC") ?: "الفجر"

        currentAthanPrayer.value = prayerName
        currentAthanArabic.value = prayerArabic
        isAthanPlaying.value = true
        isIqamahPlaying.value = false

        Log.d("AdhanPlayerService", "Starting Athan playback for $prayerName ($prayerArabic)")

        startForegroundNotification(prayerArabic)
        launchAlarmScreen(prayerArabic)
        requestAudioFocusAndPlay(prayerName)

        // Receiver wake lock can be released now that service is active
        AdhanWakeLockHolder.release()

        return START_NOT_STICKY
    }

    private fun launchAlarmScreen(prayerArabic: String) {
        try {
            val alarmIntent = Intent(this, AdhanAlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("PRAYER_ARABIC", prayerArabic)
            }
            startActivity(alarmIntent)
        } catch (t: Throwable) {
            Log.w("AdhanPlayerService", "Direct startActivity suppressed (fullScreenIntent notification will handle): ${t.message}")
        }
    }

    private fun startForegroundNotification(prayerArabic: String) {
        val channelId = "emaniat_athan_alarm_chan_v2"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                "الأذان ومواقيت الصلاة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات صوت وشاشة الأذان عند دخول أوقات الصلاة"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(chan)
        }

        val launchIntent = Intent(this, AdhanAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_ATHAN_SCREEN", true)
            putExtra("PRAYER_ARABIC", prayerArabic)
        }
        val pIntent = PendingIntent.getActivity(
            this,
            501,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop Action Intent
        val stopIntent = Intent(this, AdhanPlayerService::class.java).apply {
            action = "com.example.ACTION_STOP_ATHAN"
        }
        val pStopIntent = PendingIntent.getService(
            this,
            502,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("حان الآن موعد صلاة $prayerArabic 🕌")
            .setContentText("صوت الحق يرتفع الآن - اضغط لفتح شاشة الأذان والدعاء")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(pIntent, true) // Show full screen on lock screen
            .setContentIntent(pIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف الأذان 🔇", pStopIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                12345,
                builder.build(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(12345, builder.build())
        }
    }

    private fun requestAudioFocusAndPlay(prayerName: String) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Ensure alarm stream volume is clearly audible
        val currentAlarmVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val minAudible = (maxAlarmVol * 0.65).toInt().coerceAtLeast(1)
        if (currentAlarmVol < minAudible && maxAlarmVol > 0) {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, minAudible, 0)
            } catch (t: Throwable) {
                Log.w("AdhanPlayerService", "Unable to adjust stream volume", t)
            }
        }

        // Setup audio focus attributes
        val playbackAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttrs)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        try {
                            mediaPlayer?.setVolume(0.1f, 0.1f)
                        } catch (t: Throwable) {}
                    }
                }
                .build()
            
            audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { },
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }

        // Start Audio Play
        playAthanAudio(prayerName)
    }

    private fun playAthanAudio(prayerName: String) {
        val muadhin = AdhanManager.getMuadhinForPrayer(this, prayerName)
        val file = AdhanManager.getLocalAthanFile(this, muadhin)
        val useLocal = AdhanManager.isAthanDownloaded(this, muadhin)

        serviceScope.launch(Dispatchers.Main) {
            try {
                mediaPlayer?.release()
                mediaPlayer = null

                val player = MediaPlayer()
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                player.setAudioAttributes(attrs)
                player.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)

                var loadedFromLocal = false
                if (useLocal && file.exists() && file.length() > 50 * 1024) {
                    try {
                        player.setDataSource(file.absolutePath)
                        player.prepare()
                        loadedFromLocal = true
                        Log.d("AdhanPlayerService", "Successfully loaded local athan file: ${file.absolutePath}")
                    } catch (e: Exception) {
                        Log.w("AdhanPlayerService", "Failed loading local athan file, falling back to bundled R.raw.adhan", e)
                    }
                }

                if (!loadedFromLocal) {
                    // Play high quality bundled raw adhan
                    resources.openRawResourceFd(R.raw.adhan).use { afd ->
                        player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        player.prepare()
                    }
                    Log.d("AdhanPlayerService", "Successfully prepared bundled R.raw.adhan")
                }

                val targetVolume = AdhanManager.getAthanVolume(this@AdhanPlayerService).coerceIn(0.35f, 1.0f)
                if (!AdhanManager.isGradualVolume(this@AdhanPlayerService)) {
                    player.setVolume(targetVolume, targetVolume)
                } else {
                    // Start clearly audible at 0.5f and smoothly ramp up
                    val startVol = minOf(0.5f, targetVolume)
                    player.setVolume(startVol, startVol)
                    startProgressiveVolumeRise(targetVolume)
                }

                player.setOnCompletionListener {
                    Log.d("AdhanPlayerService", "Athan audio play completed")
                    playIqamahAudio()
                }

                player.setOnErrorListener { _, what, extra ->
                    Log.e("AdhanPlayerService", "MediaPlayer error: what=$what, extra=$extra")
                    // If error occurs, fallback to bundled R.raw.adhan once
                    fallbackToBundledAdhan()
                    true
                }

                player.start()
                mediaPlayer = player
                startVibrationIfConfigured()

            } catch (e: Exception) {
                Log.e("AdhanPlayerService", "Error setting up MediaPlayer, trying emergency fallback", e)
                fallbackToBundledAdhan()
            }
        }
    }

    private fun fallbackToBundledAdhan() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null

            val fallback = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                resources.openRawResourceFd(R.raw.adhan).use { afd ->
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    prepare()
                }
                setVolume(1.0f, 1.0f)
                start()
                setOnCompletionListener {
                    playIqamahAudio()
                }
            }
            mediaPlayer = fallback
        } catch (t: Throwable) {
            Log.e("AdhanPlayerService", "Fallback bundled Adhan failed, trying system alarm ringtone", t)
            try {
                val alarmUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                    ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                if (alarmUri != null) {
                    val sysPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        setDataSource(this@AdhanPlayerService, alarmUri)
                        prepare()
                        setVolume(1.0f, 1.0f)
                        start()
                    }
                    mediaPlayer = sysPlayer
                } else {
                    stopSelf()
                }
            } catch (e2: Throwable) {
                Log.e("AdhanPlayerService", "Fatal error in system ringtone fallback", e2)
                stopSelf()
            }
        }
    }

    private fun playIqamahAudio() {
        Log.d("AdhanPlayerService", "Transitioning to Iqamah audio playback")
        isIqamahPlaying.value = true
        isAthanPlaying.value = false
        
        serviceScope.launch(Dispatchers.Main) {
            try {
                mediaPlayer?.release()
                mediaPlayer = null

                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                    resources.openRawResourceFd(R.raw.iqamah).use { afd ->
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        prepare()
                    }
                    val targetVolume = AdhanManager.getAthanVolume(this@AdhanPlayerService).coerceIn(0.2f, 1.0f)
                    setVolume(targetVolume, targetVolume)
                    start()
                    setOnCompletionListener {
                        Log.d("AdhanPlayerService", "Iqamah playback completed successfully")
                        stopSelf()
                    }
                    setOnErrorListener { _, _, _ ->
                        stopSelf()
                        true
                    }
                }
                iqamahPlayer = player
            } catch (e: Exception) {
                Log.e("AdhanPlayerService", "Failed to setup Iqamah audio", e)
                stopSelf()
            }
        }
    }

    private fun startProgressiveVolumeRise(maxVolume: Float) {
        progressiveVolumeJob?.cancel()
        progressiveVolumeJob = serviceScope.launch {
            var vol = 0.35f
            while (vol < maxVolume && isActive) {
                delay(1500L)
                vol = (vol + 0.15f).coerceAtMost(maxVolume)
                try {
                    mediaPlayer?.setVolume(vol, vol)
                } catch (t: Throwable) {}
            }
        }
    }

    private fun startVibrationIfConfigured() {
        if (!AdhanManager.isVibrateOnAdhan(this)) return

        try {
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator?.hasVibrator() == true) {
                // Waveform: wait 0ms, vibrate 1000ms, sleep 1000ms (repeat 3 times)
                val pattern = longArrayOf(0, 1000, 1000, 1000, 1000, 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.e("AdhanPlayerService", "Vibration error", e)
        }
    }

    private fun stopPlayback() {
        progressiveVolumeJob?.cancel()
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (t: Throwable) {}
        mediaPlayer = null

        try {
            iqamahPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (t: Throwable) {}
        iqamahPlayer = null

        try {
            vibrator?.cancel()
        } catch (t: Throwable) {}
        vibrator = null

        isAthanPlaying.value = false
        isIqamahPlaying.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("AdhanPlayerService", "Service onDestroy")
        stopPlayback()
        serviceScope.cancel()

        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (t: Throwable) {}
        wakeLock = null

        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus { }
            }
        } catch (t: Throwable) {}
    }
}
