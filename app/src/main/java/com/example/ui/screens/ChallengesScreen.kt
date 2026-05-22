package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ElegantBackgroundPattern
import com.example.ui.components.GlassCard
import com.example.ui.components.IslamicOrnamentDivider
import com.example.ui.components.IslamicStarStarIcon
import com.example.ui.theme.*
import com.example.ui.viewmodel.EmaniatViewModel

data class Medal(
    val title: String,
    val description: String,
    val isUnlocked: Boolean,
    val color: Color
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ChallengesScreen(
    viewModel: EmaniatViewModel,
    modifier: Modifier = Modifier
) {
    val challengesList by viewModel.challenges.collectAsState()
    val streakData by viewModel.userStreak.collectAsState()

    val level = streakData?.level ?: 1
    val points = streakData?.points ?: 250

    var selectedTabIndex by remember { mutableStateOf(0) } // 0: Challenges, 1: Badges
    
    // Custom Medals/Badges
    val medals = remember(level, points) {
        listOf(
            Medal("السائر المبتدئ 🌿", "مُنحت لك بمناسبة الانضمام لتطبيق إيمانيات والمواظبة على العبادات.", true, EmeraldSecondary),
            Medal("فارس التسبيح 📿", "تُمنح عند تكرار التسبيحات وتعدي رصيد نقاطك ٢٠٠ نقطة.", points >= 200, GoldAccent),
            Medal("حافظ الورد 📖", "تُمنح لمداومتها على قراءة صفحات من الذكر الحكيم بمثابرة مستمرة.", viewModel.khatmaProgressPages >= 50, Color(0xFF5BA4E5)),
            Medal("المصلّي الخاشع 🕌", "تُمنح عند الالتزام بجميع الصلوات المكتوبة لمدة خمسة أيام متواصلة كورد مستمر.", level >= 3, Color(0xFFE55BCE)),
            Medal("بطل الالتزام الأسبوعي 🌟", "وسام شرفي لمن أتم التحديات الكبرى بمستوى نقاط فاق ٥٠٠ نقطة في الأسبوع.", points >= 500, GoldAccent)
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        ElegantBackgroundPattern()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // HEADER SCREEN
            Text(
                text = "التحديات الإيمانية",
                color = TextColorPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "سارعوا إلى مغفرة من ربكم - شارك في الفوز بالأوسمة الإيمانية",
                color = TextColorSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            // TAB SELECTOR (CHALLENGES VS BADGES)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDarkGlass)
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            ) {
                listOf("التحديات القائمة", "الأوسمة والإنجازات 🏅").forEachIndexed { index, txt ->
                    val active = selectedTabIndex == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (active) EmeraldMuted.copy(alpha = 0.4f) else Color.Transparent)
                            .clickable {
                                selectedTabIndex = index
                                viewModel.triggerHaptic()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = txt,
                            color = if (active) GoldAccent else TextColorSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // BODY RENDERING
            AnimatedContent(
                targetState = selectedTabIndex,
                transitionSpec = {
                    fadeIn().togetherWith(fadeOut())
                },
                label = "challenges_view"
            ) { activeTab ->
                if (activeTab == 0) {
                    // TAB: CHALLENGES LIST
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 60.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(challengesList) { chal ->
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1.8f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (chal.category == "DAILY") EmeraldMuted.copy(alpha = 0.2f)
                                                        else GoldMuted.copy(alpha = 0.2f),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = if (chal.category == "DAILY") "تحدي يومي" else "تحدي أسبوعي",
                                                    color = if (chal.category == "DAILY") EmeraldSecondary else GoldAccent,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = chal.title,
                                            color = LightWhite,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = chal.description,
                                            color = TextColorSecondary,
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Check status circle interaction
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(0.7f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (chal.isCompleted) EmeraldSecondary
                                                    else Color.Transparent
                                                )
                                                .border(
                                                    2.dp,
                                                    if (chal.isCompleted) EmeraldSecondary else GoldAccent,
                                                    CircleShape
                                                )
                                                .clickable {
                                                    viewModel.toggleChallenge(chal.id, !chal.isCompleted)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (chal.isCompleted) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "مكتمل",
                                                    tint = LightWhite
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "+${chal.pointsReward} نقطة",
                                            color = GoldAccent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // TAB: UNLOCKED MEDALS/BADGES
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 60.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(medals) { medal ->
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Custom geometric star badge drawing inside a Canvas box
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (medal.isUnlocked) medal.color.copy(alpha = 0.12f)
                                                else Color.Gray.copy(alpha = 0.08f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Stars overlay inside
                                        if (medal.isUnlocked) {
                                            IslamicStarStarIcon(modifier = Modifier.size(44.dp), color = medal.color)
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = medal.color,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else {
                                            IslamicStarStarIcon(modifier = Modifier.size(44.dp), color = Color.Gray.copy(alpha = 0.4f))
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "قفل",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = medal.title,
                                            color = if (medal.isUnlocked) LightWhite else Color.Gray,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = medal.description,
                                            color = if (medal.isUnlocked) TextColorSecondary else Color.Gray.copy(alpha = 0.7f),
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                        
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (medal.isUnlocked) "تم الفوز بالوسام بنجاح 🎉" else "الوسام مغلق الآن 🔒",
                                            color = if (medal.isUnlocked) EmeraldSecondary else MutedRed,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
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
}
