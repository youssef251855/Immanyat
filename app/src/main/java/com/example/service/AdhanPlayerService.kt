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
import com.example.MainActivity
import com.example.data.AdhanManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class AdhanPlayerService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var progressiveVolumeJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var audioFocusRequest: AudioFocusRequest? = null

    companion object {
        val isAthanPlaying = MutableStateFlow(false)
        val currentAthanPrayer = MutableStateFlow("")
        val currentAthanArabic = MutableStateFlow("")
        
        fun stopAthan(context: Context) {
            val intent = Intent(context, AdhanPlayerService::class.java).apply {
                action = "com.example.ACTION_STOP_ATHAN"
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("AdhanPlayerService", "Service onCreate")
        
        // Grab WakeLock to ensure smooth background playback
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Emaniat:AdhanWakeLock").apply {
            acquire(10 * 60 * 1000L) // 10 minutes max lock
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "com.example.ACTION_STOP_ATHAN") {
            Log.d("AdhanPlayerService", "Stop action triggered")
            stopSelf()
            return START_NOT_STICKY
        }

        val prayerName = intent?.getStringExtra("PRAYER_NAME") ?: "Fajr"
        val prayerArabic = intent?.getStringExtra("PRAYER_ARABIC") ?: "الفجر"

        currentAthanPrayer.value = prayerName
        currentAthanArabic.value = prayerArabic
        isAthanPlaying.value = true

        Log.d("AdhanPlayerService", "Starting Athan playback for $prayerName ($prayerArabic)")

        startForegroundNotification(prayerArabic)
        requestAudioFocusAndPlay(prayerName)

        return START_NOT_STICKY
    }

    private fun startForegroundNotification(prayerArabic: String) {
        val channelId = "emaniat_athan_chan"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                "الأذان ومواقيت الصلاة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(chan)
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
            .setContentTitle("حان الآن وقت صلاة $prayerArabic بمحاذاة موقعك 🕌")
            .setContentText("صوت الحق يرتفع الآن، التفكر والدعاء مستجاب.")
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
        
        // Setup focus attributes
        val playbackAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttrs)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        currentVolume = 0f
                        mediaPlayer?.setVolume(0f, 0f)
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

    private var currentVolume = 0.05f

    private fun playAthanAudio(prayerName: String) {
        val muadhin = AdhanManager.getMuadhinForPrayer(this, prayerName)
        val file = AdhanManager.getLocalAthanFile(this, muadhin)
        val useLocal = AdhanManager.isAthanDownloaded(this, muadhin)
        val streamUrl = AdhanManager.MUADHIN_URLS[muadhin] ?: AdhanManager.MUADHIN_URLS.values.first()

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                
                if (useLocal) {
                    setDataSource(file.absolutePath)
                    Log.d("AdhanPlayerService", "Playing offline adhan from file: ${file.absolutePath}")
                } else {
                    setDataSource(streamUrl)
                    Log.d("AdhanPlayerService", "Streaming online adhan from URL: $streamUrl")
                }

                setVolume(currentVolume, currentVolume)
                
                setOnPreparedListener { mp ->
                    mp.start()
                    startVibrationIfConfigured()
                    startProgressiveVolumeRise()
                }

                setOnCompletionListener {
                    Log.d("AdhanPlayerService", "Athan play completed")
                    stopSelf()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e("AdhanPlayerService", "MediaPlayer error: what=$what normal-extra=$extra")
                    // If streaming failed and was offline, fallback to play a simple built-in alarm or terminate
                    if (!useLocal) {
                        // Play emergency backup chime or exit
                        Log.d("AdhanPlayerService", "Fallback stream error, terminating service.")
                    }
                    stopSelf()
                    true
                }

                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("AdhanPlayerService", "Error setting up MediaPlayer", e)
            stopSelf()
        }
    }

    private fun startProgressiveVolumeRise() {
        if (!AdhanManager.isGradualVolume(this)) {
            val maxVol = AdhanManager.getAthanVolume(this)
            mediaPlayer?.setVolume(maxVol, maxVol)
            return
        }

        progressiveVolumeJob = serviceScope.launch {
            val maxVolume = AdhanManager.getAthanVolume(this@AdhanPlayerService)
            val stepCount = 10
            val delayDuration = 800L // rise over 8 seconds total
            val stepSize = (maxVolume - 0.05f) / stepCount

            for (i in 1..stepCount) {
                delay(delayDuration)
                if (mediaPlayer == null || !mediaPlayer!!.isPlaying) break
                currentVolume += stepSize
                if (currentVolume > maxVolume) currentVolume = maxVolume
                mediaPlayer?.setVolume(currentVolume, currentVolume)
            }
        }
    }

    private fun startVibrationIfConfigured() {
        if (!AdhanManager.isVibrateOnAdhan(this)) return

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.let { v ->
            if (v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 500, 1000, 500, 1000, 500)
                    val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)
                    v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(3000)
                }
            }
        }
    }

    override fun onDestroy() {
        Log.d("AdhanPlayerService", "Service onDestroy code")
        isAthanPlaying.value = false
        
        // Stop progressive volume jobs
        progressiveVolumeJob?.cancel()
        serviceScope.cancel()

        // Release MediaPlayer
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null

        // Stop vibrator
        vibrator?.cancel()
        vibrator = null

        // Release Audio Focus
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }

        // Release Power Lock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null

        super.onDestroy()
    }
}
