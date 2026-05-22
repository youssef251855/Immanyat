package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ElegantBackgroundPattern
import com.example.ui.components.GlassCard
import com.example.ui.components.IslamicOrnamentDivider
import com.example.ui.components.IslamicStarStarIcon
import com.example.ui.theme.*
import com.example.ui.viewmodel.EmaniatViewModel

data class JourneyStep(
    val levelReq: Int,
    val title: String,
    val desc: String,
    val dateUnlocked: String,
    val scoreReq: Int
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ProfileScreen(
    viewModel: EmaniatViewModel,
    modifier: Modifier = Modifier
) {
    val streakData by viewModel.userStreak.collectAsState()

    var activeProfileTab by remember { mutableStateOf(0) } // 0: Profile Statistics, 1: Spiritual Journey Map

    var editingName by remember { mutableStateOf(false) }
    var nameTempInput by remember { mutableStateOf(viewModel.userName) }

    val points = streakData?.points ?: 250
    val level = streakData?.level ?: 1
    val currentStreakCount = streakData?.currentStreak ?: 3
    val highestStreakCount = streakData?.highestStreak ?: 5

    // Spiritual timeline markers matching levels
    val journeySteps = remember {
        listOf(
            JourneyStep(1, "البداية وطلب الهداية 🌿", "انطلقت رحلتك بقبول نية العبادة الصادقة وتحميل تطبيق إيمانيات لتدعيم الالتزام.", "تم الانضمام", 0),
            JourneyStep(2, "مواظب الأوراد اليومية ✨", "حافظ على صلاتك وأذكارك لخمسة أيام متتالية محققاً الالتزام.", "تم الفوز", 150),
            JourneyStep(3, "مسبّح الأوابين 📿", "إتمام أكثر من ألف تسبيحة مخصصة ودعاء الاستغفار.", "تم الفوز", 350),
            JourneyStep(4, "المصلّي الخاشع الخفي 🕌", "المحافظة على صلاة الفجر في وقتها خمسة أيام ومضاعفة رصيد الأوراد.", "مستوى ٤", 750),
            JourneyStep(5, "البطل الإيماني المقرب 🌟", "القمة الإيمانية، إتمام جميع التحديات الأسبوعية والالتزام الكامل بالعبادات.", "مستوى ٥", 1500)
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        ElegantBackgroundPattern()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // HEADER SCREEN
            Text(
                text = "ملفي والرحلة الإيمانية",
                color = TextColorPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "وفي ذلك فليتنافس المتنافسون - مراجعة إحصاء الالتزام ومراحل الارتقاء",
                color = TextColorSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            // TAB MANAGER PROFILE VS JOURNEYMAP
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDarkGlass)
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            ) {
                listOf("إحصائيات عباداتي", "رحلتي الإيمانية 🌌").forEachIndexed { index, title ->
                    val active = activeProfileTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (active) EmeraldMuted.copy(alpha = 0.4f) else Color.Transparent)
                            .clickable {
                                activeProfileTab = index
                                viewModel.triggerHaptic()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (active) GoldAccent else TextColorSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // BODY CONDITIONAL RENDERER
            AnimatedContent(
                targetState = activeProfileTab,
                transitionSpec = {
                    fadeIn().togetherWith(fadeOut())
                },
                label = "profile_tabs"
            ) { targetTab ->
                if (targetTab == 0) {
                    // TAB 0: PROFILE AND EDITORS
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // User visual avatar display & edit name row
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Avatar circle holding an Islamic star illustration
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldSecondary.copy(alpha = 0.15f))
                                        .border(1.5.dp, GoldAccent, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "الصورة الشخصية",
                                        tint = GoldAccent,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    if (editingName) {
                                        TextField(
                                            value = nameTempInput,
                                            onValueChange = { nameTempInput = it },
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                            colors = TextFieldDefaults.colors(
                                                focusedTextColor = LightWhite,
                                                unfocusedTextColor = LightWhite,
                                                focusedContainerColor = DarkBackground,
                                                unfocusedContainerColor = DarkBackground
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(
                                                onClick = {
                                                    if (nameTempInput.isNotEmpty()) {
                                                        viewModel.userName = nameTempInput
                                                        editingName = false
                                                        viewModel.triggerHaptic()
                                                    }
                                                }
                                            ) {
                                                Text("حفظ الاسم ✅", color = GoldAccent, fontWeight = FontWeight.Bold)
                                            }
                                            TextButton(onClick = { editingName = false }) {
                                                Text("إلغاء", color = MutedRed)
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = viewModel.userName,
                                            color = LightWhite,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "مستوى التقى: " + when(level) {
                                                in 1..2 -> "السائر الطيب ☘️"
                                                in 3..4 -> "المثابر المحتسب ⭐"
                                                else -> "الممتاز المقرب 🌟"
                                            },
                                            color = TextColorSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        TextButton(
                                            onClick = { editingName = true },
                                            modifier = Modifier.height(28.dp).padding(0.dp)
                                        ) {
                                            Text("تعديل اسمك الشخصي ✍️", color = GoldAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // COMMITMENT STATS GRID (STREAKS & POINTS CARDS)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Active Streak Card
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SurfaceDarkGlass)
                                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                                    .padding(14.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Waves, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("أيام الالتزام الحالية", color = TextColorSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("$currentStreakCount يوم", color = LightWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                    Text("أعلى التزام: $highestStreakCount يوم", color = EmeraldSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // Active points Card
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SurfaceDarkGlass)
                                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                                    .padding(14.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.MilitaryTech, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("مجموع الرصيد الإيماني", color = TextColorSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("$points نقطة", color = LightWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                    Text("المستوى الحالي: $level", color = GoldAccent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // WORSHIP STATS RADIAL CHARTS CARD Representation info
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "مستويات إنجاز الفروض والسنن",
                                color = GoldAccent,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "تقييم التزامك بالفروض الخمسة والأوراد المساندة",
                                color = TextColorSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // 5 Columns representing progress
                            val elements = listOf(
                                "الفجر" to 0.85f to "ممتاز",
                                "الظهر" to 0.9f to "ممتاز",
                                "العصر" to 0.7f to "جيد",
                                "المغرب" to 0.95f to "ممتاز",
                                "العشاء" to 0.6f to "صالح"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                elements.forEach { ent ->
                                    val (nameProg, labelRating) = ent
                                    val (name, valueProg) = nameProg

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(EmeraldMuted.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(name, color = LightWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            
                                            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(
                                                    progress = { valueProg },
                                                    color = EmeraldSecondary,
                                                    strokeWidth = 3.dp,
                                                    modifier = Modifier.fillMaxSize(),
                                                    trackColor = CardBorder
                                                )
                                                Text("${(valueProg * 100).toInt()}%", fontSize = 9.sp, color = GoldAccent, fontWeight = FontWeight.Bold)
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(labelRating, color = TextColorSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // TAB 1: SPIRITUAL JOURNEY TIMELINE MAP
                    Column(modifier = Modifier.fillMaxWidth()) {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "خريطة مراحل رحلتك الإيمانية",
                                color = GoldAccent,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "انطلق في رحلتك الروحية للإجابة والارتقاء في مستويات الطاعات وحصد النقاط والأوسمة الجرئية للالتزام الكامل.",
                                color = TextColorSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Timeline rendering loop
                        journeySteps.forEachIndexed { index, step ->
                            val currentUnlocked = points >= step.scoreReq
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Left timeline decoration indicators
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    // Circle icon bullet
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (currentUnlocked) EmeraldPrimary
                                                else Color.Gray.copy(alpha = 0.2f)
                                            )
                                            .border(
                                                1.5.dp,
                                                if (currentUnlocked) GoldAccent else Color.Gray,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (currentUnlocked) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "تم الفتح",
                                                tint = GoldAccent,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "مغفل",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    // Connected timeline line except the last point
                                    if (index < journeySteps.size - 1) {
                                        Box(
                                            modifier = Modifier
                                                .width(2.dp)
                                                .height(64.dp)
                                                .background(
                                                    if (currentUnlocked) EmeraldPrimary.copy(alpha = 0.5f)
                                                    else Color.Gray.copy(alpha = 0.2f)
                                                )
                                        )
                                    }
                                }

                                // Timeline content card description
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (currentUnlocked) SurfaceDarkGlass
                                            else SurfaceDarkGlass.copy(alpha = 0.4f)
                                        )
                                        .border(
                                            1.dp,
                                            if (currentUnlocked) EmeraldPrimary.copy(alpha = 0.3f)
                                            else CardBorder,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(14.dp)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = step.title,
                                                color = if (currentUnlocked) GoldAccent else Color.Gray,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Text(
                                                text = step.dateUnlocked,
                                                color = if (currentUnlocked) EmeraldSecondary else Color.Gray.copy(alpha = 0.6f),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = step.desc,
                                            color = if (currentUnlocked) TextColorPrimary else Color.Gray.copy(alpha = 0.5f),
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp)) // padding bottom
        }
    }
}
