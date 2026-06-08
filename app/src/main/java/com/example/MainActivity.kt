package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        
        // Initialize Start.io Ads SDK
        try {
            val sharedPrefs = getSharedPreferences("emaniat_prefs", MODE_PRIVATE)
            val customAppId = sharedPrefs.getString("startio_app_id", "") ?: ""
            val appIdToUse = if (customAppId.isNotBlank()) customAppId else {
                try {
                    val buildConfigId = BuildConfig.STARTIO_APP_ID
                    if (buildConfigId.isNotBlank() && buildConfigId != "STARTIO_APP_ID") buildConfigId else "200676644"
                } catch (e: Exception) {
                    "200676644"
                }
            }
            val initCtx = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                createAttributionContext("EmaniatLocation")
            } else {
                this
            }
            com.startapp.sdk.adsbase.StartAppSDK.init(initCtx, appIdToUse, false)
            com.startapp.sdk.adsbase.StartAppSDK.enableReturnAds(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
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
                var isSplashFinished by remember { mutableStateOf(false) }
                if (!isSplashFinished) {
                    SplashScreen(
                        onSplashFinished = { isSplashFinished = true }
                    )
                } else {
                    EmaniatApp()
                }
            }
        }
    }
}

@Composable
fun EmaniatApp(
    viewModel: EmaniatViewModel = viewModel()
) {
    val currentScreen = viewModel.currentScreen
    val playingState = viewModel.playbackState
    val context = LocalContext.current
    var showAdsterraDialog by remember { mutableStateOf(false) }
    var isFirstRun by remember { mutableStateOf(true) }

    LaunchedEffect(currentScreen) {
        if (isFirstRun) {
            isFirstRun = false
        } else {
            // Respecting "Ethical Monetization": absolutely NO pop-up ads when entering prayer pages, the Quran pages, or Home/Tasbih
            val isHolyOrPrayerPage = currentScreen == AppScreen.QURAN || 
                    currentScreen == AppScreen.ADHAN_SETTINGS || 
                    currentScreen == AppScreen.HOME || 
                    currentScreen == AppScreen.TASBIH
            if (!viewModel.isVipPremiumActive && !isHolyOrPrayerPage) {
                showAdsterraDialog = true
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5 * 60 * 1000L) // 5 minutes
            try {
                if (!viewModel.isVipPremiumActive) {
                    com.startapp.sdk.adsbase.StartAppAd.showAd(context)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(10 * 60 * 1000L) // every 10 minutes
            val activeScreen = viewModel.currentScreen
            val isHolyOrPrayerPage = activeScreen == AppScreen.QURAN || 
                    activeScreen == AppScreen.ADHAN_SETTINGS || 
                    activeScreen == AppScreen.HOME || 
                    activeScreen == AppScreen.TASBIH
            if (!viewModel.isVipPremiumActive && !isHolyOrPrayerPage) {
                showAdsterraDialog = true
            }
        }
    }

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
                if (!viewModel.isVipPremiumActive && (currentScreen == AppScreen.HOME || currentScreen == AppScreen.QURAN || currentScreen == AppScreen.LIBRARY || currentScreen == AppScreen.VIDEOS)) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        factory = { ctx ->
                            com.startapp.sdk.ads.banner.Banner(ctx)
                        }
                    )
                }

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
                            0 -> ProfileScreen(viewModel = viewModel)
                            1 -> ChallengesScreen(viewModel = viewModel)
                            2 -> {
                                // Renders ProfileScreen tab with Spiritual Journey Map
                                // We simulate opening journey sub tab in custom profile view
                                ProfileScreen(viewModel = viewModel)
                            }
                            else -> {
                                // Unlock Medal badges
                                ChallengesScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
                AppScreen.ADHAN_SETTINGS -> {
                    AdhanSettingsScreen(
                        viewModel = viewModel,
                        onClose = { viewModel.setScreen(AppScreen.HOME) }
                    )
                }
                AppScreen.OASIS -> OasisScreen(viewModel = viewModel)
                AppScreen.LIBRARY -> LibraryScreen(viewModel = viewModel)
                AppScreen.VIDEOS -> VideosScreen(viewModel = viewModel)
                AppScreen.DONATE -> DonateScreen(viewModel = viewModel)
                else -> HomeScreen(viewModel = viewModel)
            }

            // Beautiful Adsterra Sponsor Promo Dialog (forced support)
            if (showAdsterraDialog) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showAdsterraDialog = false },
                    properties = androidx.compose.ui.window.DialogProperties(
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true
                    )
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 24.dp)
                            .shadow(24.dp, shape = RoundedCornerShape(20.dp))
                            .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDarkGlass)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Top Star Icon
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldSecondary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "دعم التطبيق",
                                    tint = GoldAccent,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "دعم تطبيق إيمانيات 🕌",
                                color = GoldAccent,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "مساهمتك البسيطة تمنحنا فرصة للاستمرار ونشر التطبيق على متجر Google Play وخدمة ملايين المسلمين.",
                                color = TextColorPrimary,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "اضغط على زر عرض الإعلان أدناه للمساعدة والدعم السريع، جزاكم الله خيراً وجعلها في ميزان حسناتكم.",
                                color = TextColorSecondary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    try {
                                        val adUrl = "https://www.effectivecpmnetwork.com/wykven1z2g?key=8354f640db8eebe8bf7568da45909e36"
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(adUrl))
                                        context.startActivity(intent)
                                        showAdsterraDialog = false
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .shadow(4.dp, RoundedCornerShape(12.dp))
                            ) {
                                Text(
                                    text = "اضغط هنا لمشاهدة الإعلان ومساعدتنا 🚀",
                                    color = LightWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = { showAdsterraDialog = false },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColorSecondary),
                                border = BorderStroke(1.dp, CardBorder),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                Text(
                                    text = "إغلاق نافذة الدعم",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
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
