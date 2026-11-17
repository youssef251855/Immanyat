package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.AdhanPlayerService
import com.example.ui.components.ElegantBackgroundPattern
import com.example.ui.components.IslamicOrnamentDivider
import com.example.ui.components.IslamicStarStarIcon
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

@Composable
fun AdhanAlarmScreen(
    prayerArabic: String,
    isIqamah: Boolean = false,
    audioMode: AdhanPlayerService.AlarmAudioMode = if (isIqamah) {
        AdhanPlayerService.AlarmAudioMode.IQAMAH
    } else {
        AdhanPlayerService.AlarmAudioMode.ADHAN
    },
    locationName: String = "مكة المكرمة",
    onStopClick: () -> Unit,
    onAfterPrayerClick: (() -> Unit)? = null
) {
    val timeFormat = remember { SimpleDateFormat("hh:mm:ss a", Locale("ar")) }
    val dateFormat = remember { SimpleDateFormat("EEEE, d MMMM yyyy", Locale("ar")) }
    var currentTime by remember { mutableStateOf(timeFormat.format(Date())) }
    var currentDateArabic by remember { mutableStateOf(dateFormat.format(Date())) }

    // Dynamic Clock Thread
    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            currentTime = timeFormat.format(now)
            currentDateArabic = dateFormat.format(now)
            delay(1000L)
        }
    }

    // Animated Pulsing Pulse Effect
    val infiniteTransition = rememberInfiniteTransition(label = "pulseRing")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Animated Sound Equalizer Waves
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 38f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 28f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 44f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse),
        label = "b3"
    )
    val bar4 by infiniteTransition.animateFloat(
        initialValue = 32f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "b4"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF071410),
                        Color(0xFF0F261E),
                        Color(0xFF060B08)
                    )
                )
            )
            .testTag("adhan_alarm_screen"),
        contentAlignment = Alignment.Center
    ) {
        ElegantBackgroundPattern()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Top Badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .background(GoldAccent.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .border(1.dp, GoldAccent.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    IslamicStarStarIcon(modifier = Modifier.size(16.dp))
                    Text(
                        text = when (audioMode) {
                            AdhanPlayerService.AlarmAudioMode.IQAMAH -> "إقامة الصلاة المفروضة 📣"
                            AdhanPlayerService.AlarmAudioMode.POST_ADHAN_DUA -> "دعاء ما بعد الأذان 🤲"
                            AdhanPlayerService.AlarmAudioMode.EVENING_AZKAR -> "أذكار المساء المباركة 🌅"
                            else -> "نداء الحق والأذان 🕌"
                        },
                        color = GoldAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IslamicStarStarIcon(modifier = Modifier.size(16.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$locationName 📍 • $currentDateArabic",
                    color = TextColorSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Central Glowing Islamic Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(SurfaceDarkGlass.copy(alpha = 0.85f))
                    .border(
                        BorderStroke(1.5.dp, Brush.verticalGradient(listOf(GoldAccent, CardBorder))),
                        RoundedCornerShape(28.dp)
                    )
                    .padding(vertical = 28.dp, horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Pulsing Mosque / Crescent Emblem
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .scale(pulseScale)
                            .background(
                                Brush.radialGradient(
                                    listOf(GoldAccent.copy(alpha = 0.25f), Color.Transparent)
                                ),
                                CircleShape
                            )
                            .border(1.5.dp, GoldAccent.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (audioMode) {
                                AdhanPlayerService.AlarmAudioMode.IQAMAH -> Icons.Default.Campaign
                                AdhanPlayerService.AlarmAudioMode.POST_ADHAN_DUA -> Icons.Default.VolunteerActivism
                                AdhanPlayerService.AlarmAudioMode.EVENING_AZKAR -> Icons.Default.WbTwilight
                                else -> Icons.Default.Mosque
                            },
                            contentDescription = "الأذان",
                            tint = GoldAccent,
                            modifier = Modifier.size(56.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = when (audioMode) {
                            AdhanPlayerService.AlarmAudioMode.IQAMAH -> "حان الآن موعد إقامة صلاة"
                            AdhanPlayerService.AlarmAudioMode.POST_ADHAN_DUA -> "يُتلى الآن دعاء ما بعد أذان"
                            AdhanPlayerService.AlarmAudioMode.EVENING_AZKAR -> "باقي ٣٠ دقيقة على أذان المغرب"
                            else -> "حان الآن موعد أذان صلاة"
                        },
                        color = LightWhite.copy(alpha = 0.85f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Prayer Name in Highlighted Typography
                    Text(
                        text = prayerArabic,
                        color = GoldAccent,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    IslamicOrnamentDivider(modifier = Modifier.fillMaxWidth().height(16.dp))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Digital Clock
                    Text(
                        text = currentTime,
                        color = LightWhite,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Animated Equalizer Wave
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(bar1.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(EmeraldSecondary)
                        )
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(bar2.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(GoldAccent)
                        )
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(bar3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(EmeraldSecondary)
                        )
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(bar4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(GoldAccent)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = when (audioMode) {
                            AdhanPlayerService.AlarmAudioMode.IQAMAH -> "قد قامت الصلاة • أقم صلاتك بخشوع وطمأنينة 🕌"
                            AdhanPlayerService.AlarmAudioMode.POST_ADHAN_DUA -> "ردّد الدعاء بعد الأذان تنل شفاعة النبي ﷺ 🤲"
                            AdhanPlayerService.AlarmAudioMode.EVENING_AZKAR -> "حصّن مساءك بذكر الله قبل أذان المغرب 🌅"
                            else -> "صوت الأذان يصدح الآن • الصلاة خير من النوم والعمل 🌿"
                        },
                        color = TextColorSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Post Adhan Duaa / Evening Azkar Snippet Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(EmeraldMuted.copy(alpha = 0.2f))
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (audioMode == AdhanPlayerService.AlarmAudioMode.EVENING_AZKAR) {
                            "من أذكار المساء المباركة 🌅"
                        } else {
                            "دعاء ما بعد الأذان المستجاب 🤲"
                        },
                        color = GoldAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (audioMode == AdhanPlayerService.AlarmAudioMode.EVENING_AZKAR) {
                            "« أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ »"
                        } else {
                            "« اللَّهُمَّ رَبَّ هَذِهِ الدَّعْوَةِ التَّامَّةِ، وَالصَّلَاةِ الْقَائِمَةِ، آتِ مُحَمَّدًا الْوَسِيلَةَ وَالْفَضِيلَةَ، وَابْعَثْهُ مَقَامًا مَحْمُودًا الَّذِي وَعَدْتَهُ »"
                        },
                        color = TextColorPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Dismiss Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onStopClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC0392B)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp))
                        .testTag("stop_adhan_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeMute,
                            contentDescription = "إيقاف الأذان",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (audioMode) {
                                AdhanPlayerService.AlarmAudioMode.IQAMAH -> "إيقاف الإقامة والإغلاق 🔇"
                                AdhanPlayerService.AlarmAudioMode.POST_ADHAN_DUA -> "إيقاف الدعاء والإغلاق 🔇"
                                AdhanPlayerService.AlarmAudioMode.EVENING_AZKAR -> "إيقاف أذكار المساء والإغلاق 🔇"
                                else -> "إغلاق وكتم صوت الأذان 🔇"
                            },
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (onAfterPrayerClick != null) {
                    OutlinedButton(
                        onClick = {
                            onStopClick()
                            onAfterPrayerClick()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldAccent),
                        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "تمت الصلاة",
                            tint = GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تمت الصلاة والحمد لله (بدء أذكار ما بعد الصلاة) 🤲",
                            color = GoldAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
