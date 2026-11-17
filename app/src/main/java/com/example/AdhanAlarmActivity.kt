package com.example

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.data.AdhanManager
import com.example.service.AdhanAlarmReceiver
import com.example.service.AdhanPlayerService
import com.example.ui.screens.AdhanAlarmScreen
import com.example.ui.theme.MyApplicationTheme

class AdhanAlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureLockScreenWindow()
        enableEdgeToEdge()

        val initialPrayerArabic = intent.getStringExtra("PRAYER_ARABIC")
            ?.takeIf { it.isNotBlank() }
            ?: AdhanPlayerService.currentAthanArabic.value.ifBlank { "الصلاة" }

        setContent {
            MyApplicationTheme {
                val activeMode by AdhanPlayerService.activeMode.collectAsState()
                val liveArabic by AdhanPlayerService.currentAthanArabic.collectAsState()

                val displayPrayer = liveArabic.ifBlank { initialPrayerArabic }
                val locationName = AdhanManager.getSavedLocationName(this)

                // Close automatically when playback finishes or is stopped from the notification bar
                LaunchedEffect(activeMode) {
                    if (activeMode == AdhanPlayerService.AlarmAudioMode.IDLE) {
                        kotlinx.coroutines.delay(450L)
                        if (AdhanPlayerService.activeMode.value == AdhanPlayerService.AlarmAudioMode.IDLE) {
                            finish()
                        }
                    }
                }

                AdhanAlarmScreen(
                    prayerArabic = displayPrayer,
                    isIqamah = activeMode == AdhanPlayerService.AlarmAudioMode.IQAMAH,
                    audioMode = activeMode,
                    locationName = locationName,
                    onStopClick = {
                        AdhanPlayerService.stopAthan(this)
                        AdhanAlarmReceiver.stopFallbackAudio()
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun configureLockScreenWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                if (km != null && km.isKeyguardLocked && !km.isDeviceLocked) {
                    km.requestDismissKeyguard(this, null)
                }
            } catch (_: Throwable) {
            }
        }
    }
}
