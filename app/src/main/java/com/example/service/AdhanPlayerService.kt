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

class AdhanPlayerService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var progressiveVolumeJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var audioFocusRequest: AudioFocusRequest? = null

    enum class AlarmAudioMode {
        IDLE,
        ADHAN,
        POST_ADHAN_DUA,
        IQAMAH,
        EVENING_AZKAR
    }

    companion object {
        val activeMode = MutableStateFlow(AlarmAudioMode.IDLE)
        val isAthanPlaying = MutableStateFlow(false)
        val isDuaPlaying = MutableStateFlow(false)
        val isIqamahPlaying = MutableStateFlow(false)
        val isEveningAzkarPlaying = MutableStateFlow(false)
        val currentAthanPrayer = MutableStateFlow("")
        val currentAthanArabic = MutableStateFlow("")

        fun startTrigger(context: Context, triggerMode: String, prayerName: String, prayerArabic: String) {
            val intent = Intent(context, AdhanPlayerService::class.java).apply {
                putExtra("TRIGGER_MODE", triggerMode)
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
                Log.e("AdhanPlayerService", "Failed to startForegroundService for $triggerMode", e)
            }
        }

        fun startAthan(context: Context, prayerName: String, prayerArabic: String) {
            startTrigger(context, "ADHAN", prayerName, prayerArabic)
        }

        fun startIqamah(context: Context, prayerName: String, prayerArabic: String) {
            startTrigger(context, "IQAMAH", prayerName, prayerArabic)
        }

        fun startEveningAzkar(context: Context) {
            startTrigger(context, "EVENING_AZKAR", "Maghrib", "أذكار المساء")
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

        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Emaniat:AdhanWakeLock").apply {
                setReferenceCounted(false)
                acquire(15 * 60 * 1000L) // Up to 15 minutes for Evening Azkar recitation
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

        val triggerModeStr = intent?.getStringExtra("TRIGGER_MODE") ?: "ADHAN"
        val mode = when (triggerModeStr) {
            "IQAMAH" -> AlarmAudioMode.IQAMAH
            "EVENING_AZKAR" -> AlarmAudioMode.EVENING_AZKAR
            "POST_ADHAN_DUA" -> AlarmAudioMode.POST_ADHAN_DUA
            else -> AlarmAudioMode.ADHAN
        }
        val prayerName = intent?.getStringExtra("PRAYER_NAME") ?: "Fajr"
        val prayerArabic = intent?.getStringExtra("PRAYER_ARABIC")
            ?: if (mode == AlarmAudioMode.EVENING_AZKAR) "أذكار المساء" else "الفجر"

        currentAthanPrayer.value = prayerName
        currentAthanArabic.value = prayerArabic
        updatePlayingFlags(mode)

        Log.d("AdhanPlayerService", "Starting $mode playback for $prayerName ($prayerArabic)")

        startForegroundNotification(mode, prayerArabic)
        launchAlarmScreen(prayerArabic)
        requestAudioFocusAndPlay(mode, prayerName, prayerArabic)

        AdhanWakeLockHolder.release()

        return START_NOT_STICKY
    }

    private fun updatePlayingFlags(mode: AlarmAudioMode) {
        activeMode.value = mode
        isAthanPlaying.value = mode == AlarmAudioMode.ADHAN
        isDuaPlaying.value = mode == AlarmAudioMode.POST_ADHAN_DUA
        isIqamahPlaying.value = mode == AlarmAudioMode.IQAMAH
        isEveningAzkarPlaying.value = mode == AlarmAudioMode.EVENING_AZKAR
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
            Log.w("AdhanPlayerService", "Direct startActivity deferred (high-priority alarm notification active): ${t.message}")
        }
    }

    private fun startForegroundNotification(mode: AlarmAudioMode, prayerArabic: String) {
        val channelId = "emaniat_athan_alarm_chan_v2"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                "الأذان ومواقيت الصلاة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات صوت وشاشة الأذان والإقامة وأذكار المساء"
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

        val stopIntent = Intent(this, AdhanPlayerService::class.java).apply {
            action = "com.example.ACTION_STOP_ATHAN"
        }
        val pStopIntent = PendingIntent.getService(
            this,
            502,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, text, stopLabel) = when (mode) {
            AlarmAudioMode.ADHAN -> Triple(
                "حان الآن موعد صلاة $prayerArabic 🕌",
                "صوت الحق يرتفع الآن - اضغط لفتح شاشة الأذان والدعاء",
                "إيقاف الأذان 🔇"
            )
            AlarmAudioMode.POST_ADHAN_DUA -> Triple(
                "دعاء ما بعد أذان $prayerArabic 🤲",
                "اللهم رب هذه الدعوة التامة والصلاة القائمة...",
                "إيقاف الدعاء 🔇"
            )
            AlarmAudioMode.IQAMAH -> Triple(
                "حان الآن موعد إقامة صلاة $prayerArabic 📣",
                "قد قامت الصلاة - أقم صلاتك بخشوع",
                "إيقاف الإقامة 🔇"
            )
            AlarmAudioMode.EVENING_AZKAR -> Triple(
                "أذكار المساء المباركة 🌅",
                "باقي ٣٠ دقيقة على أذان المغرب - استمع لأذكار المساء",
                "إيقاف الأذكار 🔇"
            )
            AlarmAudioMode.IDLE -> Triple(
                "تنبيه إيمانيات 🕌",
                "اضغط للفتح",
                "إيقاف 🔇"
            )
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(pIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, stopLabel, pStopIntent)

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

    private fun requestAudioFocusAndPlay(mode: AlarmAudioMode, prayerName: String, prayerArabic: String) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

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
                        } catch (_: Throwable) {}
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

        when (mode) {
            AlarmAudioMode.ADHAN -> playAthanAudio(prayerName, prayerArabic)
            AlarmAudioMode.POST_ADHAN_DUA -> playPostAdhanDuaAudio(prayerArabic)
            AlarmAudioMode.IQAMAH -> playIqamahAudio()
            AlarmAudioMode.EVENING_AZKAR -> playEveningAzkarAudio()
            AlarmAudioMode.IDLE -> stopSelf()
        }
    }

    private fun buildAlarmAttrs(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }

    private fun playAthanAudio(prayerName: String, prayerArabic: String) {
        val muadhin = AdhanManager.getMuadhinForPrayer(this, prayerName)
        val assetPath = AdhanManager.getMuadhinAssetPath(muadhin)

        serviceScope.launch(Dispatchers.Main) {
            try {
                progressiveVolumeJob?.cancel()
                mediaPlayer?.release()
                mediaPlayer = null

                val attrs = buildAlarmAttrs()
                var activePlayer: MediaPlayer? = null

                try {
                    val assetPlayer = MediaPlayer().apply {
                        setAudioAttributes(attrs)
                        setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                        assets.openFd(assetPath).use { afd ->
                            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        }
                        prepare()
                    }
                    activePlayer = assetPlayer
                    Log.d("AdhanPlayerService", "Loaded bundled offline adhan for $muadhin: $assetPath")
                } catch (e: Exception) {
                    Log.w("AdhanPlayerService", "Bundled asset $assetPath failed, falling back to R.raw.adhan", e)
                }

                val player = activePlayer ?: MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                    resources.openRawResourceFd(R.raw.adhan).use { afd ->
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        prepare()
                    }
                    Log.d("AdhanPlayerService", "Prepared bundled offline fallback R.raw.adhan")
                }

                val targetVolume = AdhanManager.getAthanVolume(this@AdhanPlayerService).coerceIn(0.35f, 1.0f)
                if (!AdhanManager.isGradualVolume(this@AdhanPlayerService)) {
                    player.setVolume(targetVolume, targetVolume)
                } else {
                    val startVol = minOf(0.5f, targetVolume)
                    player.setVolume(startVol, startVol)
                    startProgressiveVolumeRise(targetVolume)
                }

                player.setOnCompletionListener {
                    Log.d("AdhanPlayerService", "Athan audio play completed")
                    if (AdhanManager.isPostAdhanDuaEnabled(this@AdhanPlayerService)) {
                        playPostAdhanDuaAudio(prayerArabic)
                    } else {
                        stopPlayback()
                        stopSelf()
                    }
                }

                player.setOnErrorListener { _, what, extra ->
                    Log.e("AdhanPlayerService", "MediaPlayer error: what=$what, extra=$extra")
                    fallbackToBundledAdhan(prayerArabic)
                    true
                }

                player.start()
                mediaPlayer = player
                startVibrationIfConfigured()
            } catch (e: Exception) {
                Log.e("AdhanPlayerService", "Error setting up MediaPlayer, trying emergency fallback", e)
                fallbackToBundledAdhan(prayerArabic)
            }
        }
    }

    private fun fallbackToBundledAdhan(prayerArabic: String) {
        try {
            mediaPlayer?.release()
            mediaPlayer = null

            val fallback = MediaPlayer().apply {
                setAudioAttributes(buildAlarmAttrs())
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                resources.openRawResourceFd(R.raw.adhan).use { afd ->
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    prepare()
                }
                setVolume(1.0f, 1.0f)
                setOnCompletionListener {
                    if (AdhanManager.isPostAdhanDuaEnabled(this@AdhanPlayerService)) {
                        playPostAdhanDuaAudio(prayerArabic)
                    } else {
                        stopPlayback()
                        stopSelf()
                    }
                }
                start()
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
                        setOnCompletionListener {
                            stopPlayback()
                            stopSelf()
                        }
                        start()
                    }
                    mediaPlayer = sysPlayer
                } else {
                    stopPlayback()
                    stopSelf()
                }
            } catch (e2: Throwable) {
                Log.e("AdhanPlayerService", "Fatal error in system ringtone fallback", e2)
                stopPlayback()
                stopSelf()
            }
        }
    }

    private fun playPostAdhanDuaAudio(prayerArabic: String) {
        Log.d("AdhanPlayerService", "Transitioning to Post-Adhan Dua audio playback")
        updatePlayingFlags(AlarmAudioMode.POST_ADHAN_DUA)
        startForegroundNotification(AlarmAudioMode.POST_ADHAN_DUA, prayerArabic)

        serviceScope.launch(Dispatchers.Main) {
            try {
                progressiveVolumeJob?.cancel()
                mediaPlayer?.release()
                mediaPlayer = null

                val player = MediaPlayer().apply {
                    setAudioAttributes(buildAlarmAttrs())
                    setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                    assets.openFd(AdhanManager.ASSET_POST_ADHAN_DUA_AUDIO).use { afd ->
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        prepare()
                    }
                    val targetVolume = AdhanManager.getAthanVolume(this@AdhanPlayerService).coerceIn(0.35f, 1.0f)
                    setVolume(targetVolume, targetVolume)
                    setOnCompletionListener {
                        Log.d("AdhanPlayerService", "Post-Adhan Dua playback completed successfully")
                        stopPlayback()
                        stopSelf()
                    }
                    setOnErrorListener { _, _, _ ->
                        stopPlayback()
                        stopSelf()
                        true
                    }
                    start()
                }
                mediaPlayer = player
            } catch (e: Exception) {
                Log.e("AdhanPlayerService", "Failed to setup Post-Adhan Dua audio", e)
                stopPlayback()
                stopSelf()
            }
        }
    }

    private fun playIqamahAudio() {
        Log.d("AdhanPlayerService", "Starting Iqamah audio playback")
        updatePlayingFlags(AlarmAudioMode.IQAMAH)

        serviceScope.launch(Dispatchers.Main) {
            try {
                progressiveVolumeJob?.cancel()
                mediaPlayer?.release()
                mediaPlayer = null

                val player = MediaPlayer().apply {
                    setAudioAttributes(buildAlarmAttrs())
                    setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                    try {
                        assets.openFd(AdhanManager.ASSET_IQAMAH_AUDIO).use { afd ->
                            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        }
                    } catch (e: Exception) {
                        Log.w("AdhanPlayerService", "Bundled iqamah asset failed, falling back to R.raw.iqamah", e)
                        resources.openRawResourceFd(R.raw.iqamah).use { afd ->
                            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        }
                    }
                    prepare()
                    val targetVolume = AdhanManager.getAthanVolume(this@AdhanPlayerService).coerceIn(0.35f, 1.0f)
                    setVolume(targetVolume, targetVolume)
                    setOnCompletionListener {
                        Log.d("AdhanPlayerService", "Iqamah playback completed successfully")
                        stopPlayback()
                        stopSelf()
                    }
                    setOnErrorListener { _, _, _ ->
                        stopPlayback()
                        stopSelf()
                        true
                    }
                    start()
                }
                mediaPlayer = player
                startVibrationIfConfigured()
            } catch (e: Exception) {
                Log.e("AdhanPlayerService", "Failed to setup Iqamah audio", e)
                stopPlayback()
                stopSelf()
            }
        }
    }

    private fun playEveningAzkarAudio() {
        Log.d("AdhanPlayerService", "Starting Evening Azkar audio playback")
        updatePlayingFlags(AlarmAudioMode.EVENING_AZKAR)

        serviceScope.launch(Dispatchers.Main) {
            try {
                progressiveVolumeJob?.cancel()
                mediaPlayer?.release()
                mediaPlayer = null

                val player = MediaPlayer().apply {
                    setAudioAttributes(buildAlarmAttrs())
                    setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                    assets.openFd(AdhanManager.ASSET_EVENING_AZKAR_AUDIO).use { afd ->
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        prepare()
                    }
                    val targetVolume = AdhanManager.getAthanVolume(this@AdhanPlayerService).coerceIn(0.35f, 1.0f)
                    setVolume(targetVolume, targetVolume)
                    setOnCompletionListener {
                        Log.d("AdhanPlayerService", "Evening Azkar playback completed successfully")
                        stopPlayback()
                        stopSelf()
                    }
                    setOnErrorListener { _, _, _ ->
                        stopPlayback()
                        stopSelf()
                        true
                    }
                    start()
                }
                mediaPlayer = player
                startVibrationIfConfigured()
            } catch (e: Exception) {
                Log.e("AdhanPlayerService", "Failed to setup Evening Azkar audio", e)
                stopPlayback()
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
                } catch (_: Throwable) {}
            }
        }
    }

    private fun startVibrationIfConfigured() {
        if (!AdhanManager.isVibrateOnAdhan(this)) return

        try {
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator?.hasVibrator() == true) {
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
        } catch (_: Throwable) {}
        mediaPlayer = null

        try {
            vibrator?.cancel()
        } catch (_: Throwable) {}
        vibrator = null

        updatePlayingFlags(AlarmAudioMode.IDLE)
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
