package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.VolumeUp
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
import com.example.ui.theme.*
import com.example.ui.viewmodel.EmaniatViewModel

data class Zikr(
    val id: String,
    val text: String,
    val description: String,
    val targetCount: Int,
    val audioUrl: String = ""
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AzkarScreen(
    viewModel: EmaniatViewModel,
    modifier: Modifier = Modifier
) {
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    val categories = listOf("أذكار الصباح", "أذكار المساء", "أذكار الصلاة", "أذكار النوم")

    // Azkar collections definitions
    val morningAzkar = listOf(
        Zikr("m1", "أَعُوذُ بِاللَّهِ مِنَ الشَّيْطَانِ الرَّجِيمِ: اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ...", "فضلها: لن يزال عليك من الله حافظ ولا يقربك شيطان حتى تصبح.", 1),
        Zikr("m2", "بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ: قُلْ هُوَ اللَّهُ أَحَدٌ... وقُلْ أَعُوذُ بِرَبِّ الْفَلَقِ... وقُلْ أَعُوذُ بِرَبِّ النَّاسِ...", "تقال ثلاث مرات تفيك من كل شيء.", 3),
        Zikr("m3", "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ...", "يكتب بها التوحيد وصدق الاعتماد.", 1),
        Zikr("m4", "اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ النُّشُورُ.", "حديث نبوي في أذكار الصباح والمساء.", 1),
        Zikr("m5", "يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغِيثُ أَصْلِحْ لِي شَأْنِي كُلَّهُ وَلَا تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ.", "تصلح سائر الشؤون والأنفس.", 3),
        Zikr("m6", "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ: عَدَدَ خَلْقِهِ، وَرِضَا نَفْسِهِ، وَزِنَةَ عَرْشِهِ، وَمَدَادَ كَلِمَاتِهِ.", "تزن ميزان الجبال حسنات.", 3)
    )

    val eveningAzkar = listOf(
        Zikr("e1", "أَعُوذُ بِاللَّهِ مِنَ الشَّيْطَانِ الرَّجِيمِ: اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ...", "فضلها: لن يزال عليك من الله حافظ حتى تصبح.", 1),
        Zikr("e2", "بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ: قُلْ هُوَ اللَّهُ أَحَدٌ... (المعوذات)", "تقال ثلاث مرات مساءً تفيك من كل شر.", 3),
        Zikr("e3", "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ...", "تجديد الإيمان والتوحيد لله رب العالمين.", 1),
        Zikr("e4", "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَٰهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ...", "سيد الاستغفار: من قالها يوقناً بها ومات دخل الجنة.", 1)
    )

    val salatAzkar = listOf(
        Zikr("s1", "أَسْتَغْفِرُ اللَّهَ العظيم (ثَلَاثاً)، اللَّهُمَّ أَنْتَ السَّلَامُ وَمِنْكَ السَّلَامُ، تَبَارَكْتَ يَا ذَا الْجَلَالِ وَالْإِكْرَامِ.", "الاستغفار مباشرة بعد التسليم.", 1),
        Zikr("s2", "سُبْحَانَ اللَّهِ", "التسبيح بعد الصلاة تقرأ ثلاثاً وثلاثين مرة.", 33),
        Zikr("s3", "الْحَمْدُ لِلَّهِ", "التحميد بعد الصلاة تقرأ ثلاثاً وثلاثين مرة.", 33),
        Zikr("s4", "اللَّهُ أَكْبَرُ", "التكبير بعد الصلاة تقرأ ثلاثاً وثلاثين مرة.", 33),
        Zikr("s5", "لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ.", "تمام المئة تغفر بها الخطايا وإن كانت كزبد البحر.", 1)
    )

    val sleepAzkar = listOf(
        Zikr("sl1", "بِاسْمِكَ رَبِّي وَضَعْتُ جَنْبِي، وَبِكَ أَرْفَعُهُ، فَإِنْ أَمْسَكْتَ نَفْسِي فَارْحَمْهَا، وَإِنْ أَرْسَلْتَهَا فَاحْفَظْهَا...", "حفظ النفس والبدن عند النوم.", 1),
        Zikr("sl2", "اللَّهُمَّ قِنِي عَذَابَكَ يَوْمَ تَبْعَثُ عِبَادَكَ.", "توضع اليد اليمنى تحت الخد وتقرأ ثلاث مرات.", 3),
        Zikr("sl3", "بِاسْمِكَ اللَّهُمَّ أَمُوتُ وَأَحْيَا.", "الذكر المأثور عند الإقبال على النوم.", 1)
    )

    val activeZikrList = when(selectedCategoryIndex) {
        0 -> morningAzkar
        1 -> eveningAzkar
        2 -> salatAzkar
        else -> sleepAzkar
    }

    // Dynamic Tracking of counters during current app session
    val countsTracker = remember { mutableStateMapOf<String, Int>() }
    
    // Initialize current list index counts
    val currentAzkarCountsMap = remember(selectedCategoryIndex) {
        activeZikrList.map { z ->
            val count = countsTracker[z.id] ?: z.targetCount
            z.id to count
        }.toMap()
    }

    var textToSpeechActiveId by remember { mutableStateOf<String?>(null) }

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
                text = "الأذكار اليومية",
                color = TextColorPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "ألا بذكر الله تطمئن القلوب - رطّب لسانك بذكر الله عز وجل",
                color = TextColorSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            // HORIZONTAL CATEGORIES BAR (TABS)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEachIndexed { index, title ->
                    val isSelected = selectedCategoryIndex == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) EmeraldPrimary
                                else EmeraldMuted.copy(alpha = 0.15f)
                            )
                            .border(
                                1.dp,
                                if (isSelected) GoldAccent else CardBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                selectedCategoryIndex = index
                                viewModel.triggerHaptic()
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = title,
                            color = if (isSelected) LightWhite else TextColorSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // DISPLAY ACTIVE LIST
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(activeZikrList) { idx, zikr ->
                    val currentCount = countsTracker[zikr.id] ?: zikr.targetCount
                    val progressPercent = (zikr.targetCount - currentCount).toFloat() / zikr.targetCount.toFloat()
                    val isCompleted = currentCount == 0

                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Text portion
                            Text(
                                text = zikr.text,
                                color = if (isCompleted) EmeraldSecondary else LightWhite,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Right,
                                lineHeight = 28.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = zikr.description,
                                color = TextColorSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            IslamicOrnamentDivider(modifier = Modifier.fillMaxWidth().height(10.dp))
                            Spacer(modifier = Modifier.height(8.dp))

                            // Interactive count selectors row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Sound speech controller
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            viewModel.triggerHaptic()
                                            textToSpeechActiveId = if (textToSpeechActiveId == zikr.id) null else zikr.id
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.VolumeUp,
                                            contentDescription = "استماع صوتي للذكر",
                                            tint = if (textToSpeechActiveId == zikr.id) EmeraldSecondary else GoldAccent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    if (textToSpeechActiveId == zikr.id) {
                                        Text(
                                            text = "جاري تلاوة الذكر...",
                                            color = EmeraldSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Interactive Counter Circle
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isCompleted) EmeraldPrimary.copy(alpha = 0.1f)
                                            else EmeraldMuted.copy(alpha = 0.2f)
                                        )
                                        .border(
                                            1.dp,
                                            if (isCompleted) EmeraldSecondary else GoldAccent.copy(alpha = 0.5f),
                                            CircleShape
                                        )
                                        .clickable {
                                            if (currentCount > 0) {
                                                val nextVal = currentCount - 1
                                                countsTracker[zikr.id] = nextVal
                                                viewModel.triggerHaptic()
                                                
                                                // Handle completion effect
                                                if (nextVal == 0) {
                                                    // Add progress points
                                                    viewModel.toggleWorship("الأذكار", true)
                                                }
                                            } else {
                                                // Reset
                                                countsTracker[zikr.id] = zikr.targetCount
                                                viewModel.triggerHaptic()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Circular indicator on borders
                                    CircularProgressIndicator(
                                        progress = { progressPercent },
                                        color = if (isCompleted) EmeraldSecondary else GoldAccent,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.fillMaxSize(),
                                        trackColor = Color.Transparent
                                    )

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (isCompleted) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "مكتمل",
                                                tint = EmeraldSecondary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        } else {
                                            Text(
                                                text = currentCount.toString(),
                                                color = LightWhite,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "متبقي",
                                                color = TextColorSecondary,
                                                fontSize = 8.sp
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
}
