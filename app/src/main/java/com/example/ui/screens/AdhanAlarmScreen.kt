package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.LightWhite
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

@Composable
fun AdhanAlarmScreen(
    prayerArabic: String,
    isIqamah: Boolean = false,
    onStopClick: () -> Unit
) {
    var currentTime by remember { mutableStateOf("") }
    
    // Dynamic Clock Thread
    LaunchedEffect(Unit) {
        val formatter = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        while (true) {
            currentTime = formatter.format(Date())
            delay(1000L)
        }
    }

    // Gentle Pulsing Icon Target Scale
    val infiniteTransition = rememberInfiniteTransition(label = "alarmPrice")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAnimation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F1E19), // Midnight Deep Emerald
                        Color(0xFF070B09)  // Dark Shadows
                    )
                )
            )
            .padding(24.dp)
            .testTag("adhan_alarm_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Pulsing Alarm Icon Frame
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(scale),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = "جرس المنبه",
                    tint = GoldAccent,
                    modifier = Modifier.size(90.dp)
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                text = if (isIqamah) "نداء الإقامة 📣" else "صوت الحق يرتفع 🕌",
                color = GoldAccent,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isIqamah) "حان الآن موعد إقامة صلاة" else "حان الآن موعد أذان صلاة",
                color = Color(0xFFB0BEC5),
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = prayerArabic,
                color = Color(0xFF4CAF50), // Lively modern Emerald green
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Dynamic clock representation
            Text(
                text = currentTime,
                color = LightWhite,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(60.dp))

            // Alarm dismissal panel
            Button(
                onClick = onStopClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)), // Beautiful high contrast red
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .shadow(12.dp, RoundedCornerShape(16.dp))
                    .testTag("stop_adhan_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeMute,
                        contentDescription = "إيقاف",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isIqamah) "إيقاف الإقامة 🔇" else "إيقاف الأذان 🔇",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
