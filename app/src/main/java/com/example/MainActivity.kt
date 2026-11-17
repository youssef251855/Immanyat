package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.VolumeMute
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.ui.components.IslamicOrnamentDivider
import com.example.ui.components.IslamicStarStarIcon
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.EmaniatViewModel
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Immediate schedule of the background Adhan Alarm in background thread
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.example.data.AdhanManager.scheduleNextAlarm(this@MainActivity)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val activeAlarmMode by com.example.service.AdhanPlayerService.activeMode.collectAsState()
                val currentAthanArabic by com.example.service.AdhanPlayerService.currentAthanArabic.collectAsState()

                // Request Notification Permission on Android 13+ (API 33+)
                val notifPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        com.example.data.AdhanManager.scheduleNextAlarm(context)
                    }
                }

                LaunchedEffect(Unit) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                // Foreground exact-second watcher for Adhan, Iqamah, and Evening Azkar alongside AlarmManager
                LaunchedEffect(Unit) {
                    while (true) {
                        try {
                            val now = System.currentTimeMillis()
                            val prayers = com.example.data.AdhanManager.calculatePrayerTimesForDate(context, java.util.Date(now))
                            val serviceIdle = com.example.service.AdhanPlayerService.activeMode.value ==
                                com.example.service.AdhanPlayerService.AlarmAudioMode.IDLE

                            if (serviceIdle) {
                                val cal = java.util.Calendar.getInstance()
                                val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                                val quietSleep = com.example.data.AdhanManager.isSilentDuringSleep(context) && (hour >= 23 || hour < 5)

                                val matchingPrayer = prayers.firstOrNull { p ->
                                    p.name != "Sunrise" &&
                                        p.isEnabled &&
                                        now >= p.timeMillis &&
                                        (now - p.timeMillis) <= 45_000L &&
                                        !com.example.data.AdhanManager.wasPrayerTriggeredToday(context, p.name)
                                }
                                if (matchingPrayer != null) {
                                    com.example.data.AdhanManager.markPrayerTriggeredToday(context, matchingPrayer.name)
                                    if (!quietSleep) {
                                        com.example.service.AdhanPlayerService.startAthan(
                                            context,
                                            matchingPrayer.name,
                                            matchingPrayer.arabicName
                                        )
                                    }
                                    com.example.data.AdhanManager.scheduleNextAlarm(context)
                                } else {
                                    val matchingIqamah = if (com.example.data.AdhanManager.isIqamahAlarmEnabled(context)) {
                                        prayers.firstOrNull { p ->
                                            if (p.name == "Sunrise" || !p.isEnabled) return@firstOrNull false
                                            val iqamahTime = p.timeMillis +
                                                com.example.data.AdhanManager.getIqamahDelayMinutes(context, p.name) * 60_000L
                                            now >= iqamahTime &&
                                                (now - iqamahTime) <= 45_000L &&
                                                !com.example.data.AdhanManager.wasIqamahTriggeredToday(context, p.name)
                                        }
                                    } else null
                                    if (matchingIqamah != null) {
                                        com.example.data.AdhanManager.markIqamahTriggeredToday(context, matchingIqamah.name)
                                        if (!quietSleep) {
                                            com.example.service.AdhanPlayerService.startIqamah(
                                                context,
                                                matchingIqamah.name,
                                                matchingIqamah.arabicName
                                            )
                                        }
                                        com.example.data.AdhanManager.scheduleNextAlarm(context)
                                    } else if (com.example.data.AdhanManager.isEveningAzkarAlarmEnabled(context)) {
                                        val maghrib = prayers.firstOrNull { it.name == "Maghrib" }
                                        val azkarTime = maghrib?.let {
                                            it.timeMillis - com.example.data.AdhanManager.EVENING_AZKAR_BEFORE_MAGHRIB_MINUTES * 60_000L
                                        }
                                        if (azkarTime != null &&
                                            now >= azkarTime &&
                                            (now - azkarTime) <= 45_000L &&
                                            !com.example.data.AdhanManager.wasEveningAzkarTriggeredToday(context)
                                        ) {
                                            com.example.data.AdhanManager.markEveningAzkarTriggeredToday(context)
                                            com.example.service.AdhanPlayerService.startEveningAzkar(context)
                                            com.example.data.AdhanManager.scheduleNextAlarm(context)
                                        }
                                    }
                                }
                            }
                        } catch (_: Throwable) {}
                        delay(1000L)
                    }
                }

                var isSplashFinished by remember { mutableStateOf(false) }

                if (activeAlarmMode != com.example.service.AdhanPlayerService.AlarmAudioMode.IDLE) {
                    com.example.ui.screens.AdhanAlarmScreen(
                        prayerArabic = currentAthanArabic.ifBlank { "الصلاة" },
                        isIqamah = activeAlarmMode == com.example.service.AdhanPlayerService.AlarmAudioMode.IQAMAH,
                        audioMode = activeAlarmMode,
                        locationName = com.example.data.AdhanManager.getSavedLocationName(context),
                        onStopClick = {
                            com.example.service.AdhanPlayerService.stopAthan(context)
                            com.example.service.AdhanAlarmReceiver.stopFallbackAudio()
                        }
                    )
                } else if (!isSplashFinished) {
                    SplashScreen(
                        onSplashFinished = { isSplashFinished = true }
                    )
                } else {
                    EmaniatApp()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.example.data.AdhanManager.scheduleNextAlarm(this@MainActivity)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
fun EmaniatApp(
    viewModel: EmaniatViewModel = viewModel()
) {
    val currentScreen = viewModel.currentScreen
    val playingState = viewModel.playbackState
    val context = LocalContext.current

    // Tab selects inside Azkar tab (Azkar vs Duas)
    var azkarSubTabIsDuas by remember { mutableStateOf(false) }

    // Tab selects inside Profile tab (Profile Stats, Challenges, Badges)
    var profileSubTab by remember { mutableStateOf(0) } // 0: Stats, 1: Challenges, 2: Journey

    if (!viewModel.isUserLoggedIn) {
        LoginScreen(
            viewModel = viewModel,
            onLoginSuccess = {}
        )
    } else {
        BackHandler(enabled = currentScreen != AppScreen.HOME) {
            viewModel.setScreen(AppScreen.HOME)
            viewModel.triggerHaptic()
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .statusBarsPadding()
                    .padding(vertical = 4.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentScreen == AppScreen.ADHAN_SETTINGS) {
                        IconButton(
                            onClick = { 
                                viewModel.setScreen(AppScreen.HOME)
                                viewModel.triggerHaptic()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "الرجوع للرئيسية",
                                tint = GoldAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { 
                                viewModel.setScreen(AppScreen.ADHAN_SETTINGS)
                                viewModel.triggerHaptic()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "الأوقات والأذان والقبلة",
                                tint = GoldAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    
                    Text(
                        text = "إيمانيات",
                        color = GoldAccent,
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("app_bar_title")
                    )

                    IconButton(
                        onClick = { 
                            viewModel.isVibrationEnabled = !viewModel.isVibrationEnabled
                            viewModel.triggerHaptic()
                        }
                    ) {
                        Icon(
                            imageVector = if (viewModel.isVibrationEnabled) Icons.Default.Vibration 
                            else Icons.Default.NotificationsOff,
                            contentDescription = "الاهتزاز",
                            tint = if (viewModel.isVibrationEnabled) GoldAccent else TextColorSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                IslamicOrnamentDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .padding(top = 2.dp)
                )
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .navigationBarsPadding()
            ) {
                // BOTTOM NAVIGATION BAR TABS
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp)
                        .background(SurfaceDarkGlass)
                        .border(BorderStroke(0.5.dp, CardBorder))
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // TAB LIST
                        val tabs = listOf(
                            Triple(AppScreen.HOME, "الرئيسية", Icons.Default.Home),
                            Triple(AppScreen.QURAN, "القرآن", Icons.Default.MenuBook),
                            Triple(AppScreen.AZKAR, "الأذكار", Icons.Default.WbSunny),
                            Triple(AppScreen.LIBRARY, "المكتبة", Icons.Default.LibraryBooks),
                            Triple(AppScreen.VIDEOS, "فيديو", Icons.Default.PlayCircle),
                            Triple(AppScreen.TASBIH, "السبحة", Icons.Default.Adjust),
                            Triple(AppScreen.PROFILE, "ملفي", Icons.Default.AccountCircle),
                            Triple(AppScreen.DONATE, "تبرع", Icons.Default.Favorite)
                        )

                        tabs.forEach { item ->
                            val (screen, label, icon) = item
                            val isSelected = currentScreen == screen

                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.setScreen(screen)
                                        viewModel.triggerHaptic()
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) EmeraldMuted.copy(alpha = 0.25f)
                                            else Color.Transparent
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = if (isSelected) GoldAccent else TextColorSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = label,
                                    color = if (isSelected) GoldAccent else TextColorSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBackground)
        ) {
            when (currentScreen) {
                AppScreen.HOME -> HomeScreen(viewModel = viewModel)
                AppScreen.QURAN -> QuranScreen(viewModel = viewModel)
                
                AppScreen.AZKAR -> {
                    // Bundle Azkar and Duas top sub-tabs selection in Azkar Screen
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillOuterSubTabRowModifier()
                        ) {
                            TextButton(
                                onClick = { azkarSubTabIsDuas = false },
                                modifier = Modifier.weight(1f).background(if (!azkarSubTabIsDuas) EmeraldMuted.copy(alpha = 0.15f) else Color.Transparent)
                            ) {
                                Text(
                                    text = "الأذكار المأثورة 📿",
                                    color = if (!azkarSubTabIsDuas) GoldAccent else TextColorSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TextButton(
                                onClick = { azkarSubTabIsDuas = true },
                                modifier = Modifier.weight(1f).background(if (azkarSubTabIsDuas) EmeraldMuted.copy(alpha = 0.15f) else Color.Transparent)
                            ) {
                                Text(
                                    text = "الأدعية المباركة 🤲",
                                    color = if (azkarSubTabIsDuas) GoldAccent else TextColorSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        if (azkarSubTabIsDuas) {
                            DuasScreen(viewModel = viewModel)
                        } else {
                            AzkarScreen(viewModel = viewModel)
                        }
                    }
                }
                
                AppScreen.TASBIH -> TasbihScreen(viewModel = viewModel)
                
                AppScreen.PROFILE -> {
                    // Sub-tab selectors inside Account/Profile tab
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillOuterSubTabRowModifier()
                        ) {
                            listOf("إحصاءاتي 📊", "التحديات 🏆", "رحلتي 🚀", "الأوسمة 🏅").forEachIndexed { index, title ->
                                TextButton(
                                    onClick = { 
                                        profileSubTab = index
                                        viewModel.triggerHaptic()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (profileSubTab == index) EmeraldMuted.copy(alpha = 0.2f) 
                                            else Color.Transparent
                                        )
                                ) {
                                    Text(
                                        text = title,
                                        color = if (profileSubTab == index) GoldAccent else TextColorSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        when (profileSubTab) {
                            0 -> ProfileScreen(viewModel = viewModel, initialTab = 0)
                            1 -> ChallengesScreen(viewModel = viewModel, initialTab = 0)
                            2 -> ProfileScreen(viewModel = viewModel, initialTab = 1)
                            else -> ChallengesScreen(viewModel = viewModel, initialTab = 1)
                        }
                    }
                }
                AppScreen.ADHAN_SETTINGS -> {
                    AdhanSettingsScreen(
                        viewModel = viewModel,
                        onClose = { viewModel.setScreen(AppScreen.HOME) }
                    )
                }
                AppScreen.ADHAN_ALARM -> {
                    val currentArabic by com.example.service.AdhanPlayerService.currentAthanArabic.collectAsState()
                    val mode by com.example.service.AdhanPlayerService.activeMode.collectAsState()
                    com.example.ui.screens.AdhanAlarmScreen(
                        prayerArabic = currentArabic.ifBlank { "العصر" },
                        isIqamah = mode == com.example.service.AdhanPlayerService.AlarmAudioMode.IQAMAH,
                        audioMode = mode,
                        locationName = com.example.data.AdhanManager.getSavedLocationName(context),
                        onStopClick = {
                            com.example.service.AdhanPlayerService.stopAthan(context)
                            com.example.service.AdhanAlarmReceiver.stopFallbackAudio()
                            viewModel.setScreen(AppScreen.HOME)
                        }
                    )
                }
                AppScreen.OASIS -> OasisScreen(viewModel = viewModel)
                AppScreen.LIBRARY -> LibraryScreen(viewModel = viewModel)
                AppScreen.VIDEOS -> VideosScreen(viewModel = viewModel)
                AppScreen.DONATE -> DonateScreen(viewModel = viewModel)
                AppScreen.QIBLA -> QiblaCompassScreen(viewModel = viewModel)
                AppScreen.ZAKAT -> ZakatCalculatorScreen(viewModel = viewModel)
                else -> HomeScreen(viewModel = viewModel)
            }
        }
    }
}
}

private fun Modifier.fillOuterSubTabRowModifier(): Modifier = this
    .fillMaxWidth()
    .padding(horizontal = 16.dp, vertical = 6.dp)
    .clip(RoundedCornerShape(10.dp))
    .background(SurfaceDarkGlass)
    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))

