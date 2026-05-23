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
                EmaniatApp()
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
                            Triple(AppScreen.TASBIH, "السبحة", Icons.Default.Adjust),
                            Triple(AppScreen.PROFILE, "ملفي والرحلة", Icons.Default.AccountCircle)
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
                                    .padding(vertical = 6.dp, horizontal = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
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
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = label,
                                    color = if (isSelected) GoldAccent else TextColorSecondary,
                                    fontSize = 10.sp,
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
