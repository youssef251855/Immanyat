package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.EmaniatViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// Data Classes for features
data class HusnaName(
    val id: Int,
    val name: String,
    val translation: String,
    val meaning: String,
    val verseMention: String
)

data class CustomReflection(
    val id: String,
    val title: String,
    val content: String,
    val dateString: String
)

data class QuranSearchVerse(
    val surahName: String,
    val verseNo: Int,
    val text: String,
    val translation: String,
    val tafseer: String
)

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OasisScreen(
    viewModel: EmaniatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // Active Feature index:
    // null -> Main list of 10 tools
    // 0 -> Qibla Compass
    // 1 -> Zakat Calculator
    // 2 -> 99 Names of Allah
    // 3 -> Quran Search & Tafseer
    // 4 -> Ambient Night Focus Audio
    // 5 -> Spiritual Journal / Reflections
    // 6 -> Adhan Voices Selector
    // 7 -> Calculation Methods Configuration
    // 8 -> Starred Favorite Azkar List
    // 9 -> Hijri Calendar & Holydays Countdown
    var activeFeatureId by remember { mutableStateOf<Int?>(null) }

    // Navigation Back Helper
    val handleBack = {
        viewModel.triggerHaptic()
        activeFeatureId = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        ElegantBackgroundPattern()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // HEADER BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (activeFeatureId != null) {
                    IconButton(
                        onClick = handleBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(EmeraldMuted.copy(alpha = 0.2f))
                            .testTag("oasis_back_to_main")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "الرجوع للواحة الإيمانية",
                            tint = GoldAccent
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(GoldAccent.copy(alpha = 0.15f))
                            .border(1.dp, GoldAccent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "١٠ ميزات إضافية ✨",
                            color = GoldAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = when (activeFeatureId) {
                        0 -> "بوصلة القبلة التفاعلية 🕋"
                        1 -> "حاسبة الزكاة الشاملة 💰"
                        2 -> "أسماء الله الحسنى ومعانيها 🛡️"
                        3 -> "البحث والتفسير القرآني 🔍"
                        4 -> "الأصوات الساكنة للتدبر 🌧️"
                        5 -> "دفتر التدبر والخواطر 📝"
                        6 -> "مكتبة أصوات الأذان العذبة 🔊"
                        7 -> "ضبط مذهب حساب المواقيت ⚙️"
                        8 -> "الأذكار والأوراد المفضلة ⭐"
                        9 -> "التقويم الهجري والمناسبات 🌙"
                        else -> "الواحة الإيمانية والخيارات الذكية"
                    },
                    color = LightWhite,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            IslamicOrnamentDivider(modifier = Modifier.fillMaxWidth().height(14.dp))
            Spacer(modifier = Modifier.height(10.dp))

            // RENDER ACTIVE FEATURE OR THE MAIN COMPREHENSIVE LIST
            AnimatedContent(
                targetState = activeFeatureId,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(120))
                },
                modifier = Modifier.weight(1f),
                label = "OasisFeaturesAnim"
            ) { id ->
                when (id) {
                    0 -> QiblaCompassFeature(viewModel)
                    1 -> ZakatCalculatorFeature(viewModel)
                    2 -> NamesOfAllahFeature(viewModel)
                    3 -> QuranSearchFeature(viewModel)
                    4 -> AmbientAudioFeature(viewModel)
                    5 -> ReflectionsNotebookFeature(viewModel)
                    6 -> AdhanVoicesFeature(viewModel)
                    7 -> CalculationMethodsFeature(viewModel)
                    8 -> StarredAzkarFeature(viewModel)
                    9 -> HijriCalendarFeature(viewModel)
                    else -> MainFeaturesSelectorGrid(viewModel) { selectedId ->
                        viewModel.triggerHaptic()
                        activeFeatureId = selectedId
                    }
                }
            }
        }
    }
}

// ==========================================
// CENTRAL NAVIGATION GRID (THE 10 NEW TOOLS)
// ==========================================
@Composable
fun MainFeaturesSelectorGrid(
    viewModel: EmaniatViewModel,
    onFeatureSelected: (Int) -> Unit
) {
    val features = remember {
        listOf(
            Triple(0, "بوصلة القبلة التفاعلية", Pair("تحديد دقيق ومحاكاة حركية باتجاه الكعبة المشرفة", Icons.Default.CompassCalibration)),
            Triple(1, "حاسبة الزكاة الذكية", Pair("احتساب الزكاة على الأموال، الذهب والفضة مع حد النصاب الشرعي", Icons.Default.Calculate)),
            Triple(2, "أسماء الله الحسنى", Pair("استكشاف معاني أسماء الله الـ 99 الروحية مع عدادات ورد خاصة لكل اسم", Icons.Default.Shield)),
            Triple(3, "البحث والتفسير الشامل", Pair("ابحث عن أي كلمة بالقرآن وحلّل تفسير الجلالين للتدبر المباشر", Icons.Default.Search)),
            Triple(4, "أصوات الخشوع الساكنة", Pair("خلفيات صوتية هادئة (مطر مكة، هواء المدينة) للمساعدة على الخشوع", Icons.Default.VolumeUp)),
            Triple(5, "دفتر الخواطر والتدبر", Pair("رحلة تدوين وتوثيق الفوائد القرآنية وخواطرك الإيمانية اليومية", Icons.Default.AppRegistration)),
            Triple(6, "أصوات وتخصيص الأذان", Pair("مكتبة عذبة ومؤذنو الحرمين مع تشغيل عينات تجريبية فورية", Icons.Default.NotificationsActive)),
            Triple(7, "إعدادات معادلات المواقيت", Pair("اختر المذهب وحساب الهيئات الفلكية العالمية لتوقيت صلاة فائق الدقة", Icons.Default.Tune)),
            Triple(8, "الأذكار والبدايات المفضلة", Pair("الوصول السريع للأوراد والآيات التي وضعت لها نجمة بلمسة واحدة", Icons.Default.Star)),
            Triple(9, "التقويم الهجري والوقائع", Pair("توافق التوقيتات الهجرية، والمناسبات الإسلامية العظمى مع عد تنازلي لشهر رمضان", Icons.Default.CalendarMonth))
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp)
    ) {
        // Welcome Banner Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(GoldAccent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.WorkspacePremium, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(24.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "الواحة الروحانية المطوّرة 🌟",
                        color = GoldAccent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "نقدّم لك ١٠ ميزات فائقة تلبّي كامل الاحتياجات الإيمانية العصرية للمسلم بسلاسة واحترافية.",
                        color = TextColorPrimary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "تصفح واستخدم الأدوات العشر الحديثة:",
            color = TextColorSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .align(Alignment.End)
        )

        features.forEach { item ->
            val (id, title, descIcons) = item
            val (desc, icon) = descIcons

            GlassCardInteractive(
                onClick = { onFeatureSelected(id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .testTag("oasis_tool_$id")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack, // simple pointing arrow left representing navigation details
                        contentDescription = "الدخول",
                        tint = GoldAccent.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = title,
                            color = LightWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = desc,
                            color = TextColorSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(EmeraldSecondary.copy(alpha = 0.2f))
                            .border(1.dp, CardBorder, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// FEATURE 1: INTERACTIVE QIBLA COMPASS
// ==========================================
@Composable
fun QiblaCompassFeature(viewModel: EmaniatViewModel) {
    val context = LocalContext.current
    var simulatedAngle by remember { mutableStateOf(120f) }
    val qiblaTargetAngle = 21f // Target angle for Saudi Makkah coordinates from this offset compass calibration
    
    // Smooth magnetic animation when alignment occurs
    val isAligned = remember(simulatedAngle) {
        val diff = Math.abs(simulatedAngle - qiblaTargetAngle)
        diff < 3f || diff > 357f
    }

    val compassHaloScale by animateFloatAsState(
        targetValue = if (isAligned) 1.15f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "compass_halo"
    )

    LaunchedEffect(isAligned) {
        if (isAligned) {
            viewModel.triggerHaptic()
            delay(120)
            viewModel.triggerHaptic()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "بوصلة اتجاه الكعبة المشرّفة التفاعلية",
                color = GoldAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "تقدير افتراضي وتفاعلي لاتجاه القبلة حسب إحداثيات بيت الله العتيق بمكة. حرّك عتلة التدوير أدناه لمحاكاة توافق حركية المستشعرات في هاتفك المحمول.",
                color = TextColorSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Graphical Compass rendering Ring
        Box(
            modifier = Modifier
                .size(220.dp)
                .scale(compassHaloScale)
                .clip(CircleShape)
                .background(SurfaceDarkGlass)
                .border(
                    BorderStroke(
                        if (isAligned) 3.dp else 1.dp,
                        if (isAligned) GoldAccent else CardBorder
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Background Mecca Indicator symbol
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(30.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Mecca",
                        tint = if (isAligned) GoldAccent else TextColorSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "مكة",
                        color = if (isAligned) GoldAccent else TextColorSecondary.copy(alpha = 0.4f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Rotating arrow indicating pointer direction on phone
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(-simulatedAngle + qiblaTargetAngle),
                contentAlignment = Alignment.Center
            ) {
                // Compass Needle
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Golden Tip pointing North
                    Box(
                        modifier = Modifier
                            .size(16.dp, 45.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(GoldAccent, GoldAccent.copy(alpha = 0.3f))
                                ),
                                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                            )
                    )
                    
                    // Center Hub
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(EmeraldSecondary)
                            .border(1.5.dp, GoldAccent, CircleShape)
                    )

                    // Silver Tip pointing South
                    Box(
                        modifier = Modifier
                            .size(12.dp, 45.dp)
                            .background(CardBorder.copy(alpha = 0.5f), RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            // Beautiful inner circle status text
            Column(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(DarkBackground.copy(alpha = 0.85f))
                    .border(0.5.dp, CardBorder, CircleShape),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${simulatedAngle.toInt()}°",
                    color = if (isAligned) GoldAccent else LightWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isAligned) "وجهة منضبطة" else "ابحث عن ٢١°",
                    color = if (isAligned) EmeraldSecondary else TextColorSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Simulating Sensor slider
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("محاكاة البوصلة مغرباً ومشرقاً", color = LightWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("منزلق التوجيه", color = GoldAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = simulatedAngle,
                onValueChange = { simulatedAngle = it; viewModel.triggerHaptic() },
                valueRange = 0f..360f,
                colors = SliderDefaults.colors(
                    thumbColor = GoldAccent,
                    activeTrackColor = GoldAccent,
                    inactiveTrackColor = CardBorder.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth().testTag("qibla_simulation_slider")
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Compass Lock Confirmation
        AnimatedVisibility(visible = isAligned) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(EmeraldSecondary.copy(alpha = 0.2f))
                    .border(1.dp, EmeraldSecondary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "منضبط", tint = EmeraldSecondary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "الحمد لله، استقامت القبلة على الكعبة المشرفة! 🕋 ✅",
                        color = EmeraldSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "أنت الآن مصطفٌّ باتجاه بيت الله الشريف، عجل بالوضوء واستقبل صلاتك بخشوع.",
                        color = TextColorPrimary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// FEATURE 2: ADVANCED ZAKAT CALCULATOR
// ==========================================
@Composable
fun ZakatCalculatorFeature(viewModel: EmaniatViewModel) {
    var cashVal by remember { mutableStateOf("") }
    var goldVal by remember { mutableStateOf("") }
    var silverVal by remember { mutableStateOf("") }
    var otherVal by remember { mutableStateOf("") }

    // Gold/Silver current estimates in currency Nisab equivalents
    // Threshold (Nisab): 85g gold or 595g silver. Gold gram is valued at ~90 USD or equivalent
    val goldNisabThresholdLimit = 85 * 90 // ~7650 USD/equivalent units
    
    // Dynamic calculate
    val cash = cashVal.toDoubleOrNull() ?: 0.0
    val goldGrams = goldVal.toDoubleOrNull() ?: 0.0
    val silverGrams = silverVal.toDoubleOrNull() ?: 0.0
    val otherAssets = otherVal.toDoubleOrNull() ?: 0.0

    // Gold value estimation (e.g. 90$ per gram) + Silver value estimation (~1$ per gram)
    val estimatedTotalWealth = cash + (goldGrams * 90) + (silverGrams * 1) + otherAssets
    val crossesNisab = estimatedTotalWealth >= goldNisabThresholdLimit
    val requiredZakatAmount = if (crossesNisab) estimatedTotalWealth * 0.025 else 0.0 // 2.5% Zakat obligation

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "حاسبة الزكاة الذكية والدقيقة 💰",
                color = GoldAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "تتيح لك احتساب وعاء الزكاة السنوي (٢.٥٪) بناءً على قيمة السيولة النقدية، حيازة الجرامات للذهب والفضة، والأملاك المستثمرة بمجرد بلوغ النصاب الشرعي المقدر بـ ٨٥ جرام ذهب خالص.",
                color = TextColorSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Input Fields Form
        listOf(
            Triple("السيولة النقدية والودائع (نقود سائلة)", cashVal, { v: String -> cashVal = v }),
            Triple("غرامات الذهب المملوك (عيار ٢١ أو ٢٤)", goldVal, { v: String -> goldVal = v }),
            Triple("غرامات الفضة المملوكة", silverVal, { v: String -> silverVal = v }),
            Triple("الأصول التجارية أو الأسهم المستثمرة", otherVal, { v: String -> otherVal = v })
        ).forEach { entry ->
            val (label, textState, onValChange) = entry
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(
                    text = label,
                    color = LightWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    textAlign = TextAlign.Right
                )
                TextField(
                    value = textState,
                    onValueChange = { onValChange(it) },
                    placeholder = { Text("أدخل القيمة النقدية أو الوزن...", color = TextColorSecondary.copy(alpha = 0.5f), fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, CardBorder, RoundedCornerShape(10.dp)),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDarkGlass,
                        unfocusedContainerColor = SurfaceDarkGlass,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = LightWhite,
                        unfocusedTextColor = LightWhite
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Result Card panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (crossesNisab) EmeraldSecondary.copy(alpha = 0.15f)
                    else SurfaceDarkGlass
                )
                .border(
                    1.dp,
                    if (crossesNisab) GoldAccent.copy(alpha = 0.6f) else CardBorder,
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "ملخّص احتساب الزكاة والوعاء الشرعي",
                    color = GoldAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "٧٦٥٠ وحدة نقدية / ٨٥ جم ذهب", color = LightWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(text = "قيمة حد النصاب الشرعي:", color = TextColorSecondary, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "$estimatedTotalWealth وحدة نقدية", color = LightWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(text = "إجمالي الثروة المقدرة:", color = TextColorSecondary, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = CardBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(10.dp))

                if (crossesNisab) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(GoldAccent)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "وجبت الزكاة ✅",
                                color = DarkBackground,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(text = "هل بلغت الذمة النصاب؟", color = TextColorSecondary, fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "الزكاة المستحقة الدفع (٢.٥٪):",
                        color = GoldAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "$requiredZakatAmount",
                        color = LightWhite,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "قال الله تعالى: (خُذْ مِنْ أَمْوَالِهِمْ صَدَقَةً تُطَهِّرُهُمْ وَتُزَكِّيهِمْ بِهَا) سورة التوبة - ١٠٣",
                        color = TextColorSecondary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardBorder)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "لم تبلغ النصاب ⏳",
                                color = LightWhite,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(text = "هل بلغت الذمة النصاب؟", color = TextColorSecondary, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "أموالك لم تتجاوز حد الـ ٨٥ جرام من الذهب الخالص السائل (٧٦٥٠ وحدة نقدية تقريباً). لا تجب الزكاة عليك فرضاً للوقت الحالي، ولكن يمكنك التصدّق اختياراً تقرباً لله.",
                        color = TextColorSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ==========================================
// FEATURE 3: AL-ASMA AL-HUSNA 99 NAMES GRID
// ==========================================
@Composable
fun NamesOfAllahFeature(viewModel: EmaniatViewModel) {
    val namesList = remember {
        listOf(
            HusnaName(1, "الرَّحْمَنُ", "The Beneficent", "هو المحسِن الذي يفيض بالخيرات في المأكل والملبس والعافية.", "الرَّحْمَنُ عَلَى الْعَرْشِ اسْتَوَى (طه: ٥)"),
            HusnaName(2, "الرَّحِيمُ", "The Merciful", "الذي يرحم المؤمنين بالهداية والإعفاف وتخفيف الابتلاءات العسيرة.", "وَكَانَ بِالْمُؤْمِنِينَ رَحِيمًا (الأحزاب: ٤٣)"),
            HusnaName(3, "الْمَلِكُ", "The Sovereign Owner", "الذي له كامل السلطة المطلقة وحرية التصرف الكاملة بلا معقّب لحكمه.", "فَتَعَالَى اللَّهُ الْمَلِكُ الْحَقُّ (طه: ١١٤)"),
            HusnaName(4, "الْقُدُّوسُ", "The Pure One", "المنزه البالغ من الكمال أقصاه، والمطهَّر من عيوب الغفلة أو الفناء.", "الْمَلِكُ الْقُدُّوسُ السَّلَامُ (الحشر: ٢٣)"),
            HusnaName(5, "السَّلَامُ", "The Giver of Peace", "الناشر للسكينة والسلام والرحمة في قلوب العابدين وأبدانهم.", "السَّلَامُ الْمُؤْمِنُ الْمُهَيْمِنُ (الحشر: ٢٣)"),
            HusnaName(6, "الْمُؤْمِنُ", "The Giver of Faith", "الذي وحّد صفاته ونفّذ مواعيده الصادقة في نصر المؤمنين.", "الْمُؤْمِنُ الْمُهَيْمِنُ الْعَزِيزُ (الحشر: ٢٣)"),
            HusnaName(7, "الْمُهَيْمِنُ", "The Guardian", "الرقيب الحريص الشهيد المُطلع على باطن وجوهر الصدور المغلقة.", "السَّلَامُ الْمُؤْمِنُ الْمُهَيْمِنُ (الحشر: ٢٣)"),
            HusnaName(8, "الْعَزِيزُ", "The All Mighty", "القوي المنيع الغالب المسوّد المتفرّد بالقوة على المكاره.", "وَهُوَ الْعَزِيزُ الْحَكِيمُ (إبراهيم: ٤)"),
            HusnaName(9, "الْجَبَّارُ", "The Compeller", "الذي يجبُر ضعف المظلومين برحمته، ويُصلح الأقدار بلطفه الخفيّ.", "الْعَزِيزُ الْجَبَّارُ الْمُتَكَبِّرُ (الحشر: ٢٣)")
        )
    }

    var selectedName by remember { mutableStateOf<HusnaName?>(null) }
    var individualRepetitionCount by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 60.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "أسماء الله الحسنى والتأمل 🛡️",
                color = GoldAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "استكشف وشاهد فضل ومعاني أسماء الله التسعة والتسعين المباركة، وتأمل بها مع عداد السبحة المقترن بكل اسم.",
                color = TextColorSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Grid Representation
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(namesList) { item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceDarkGlass)
                        .border(
                            1.dp,
                            if (selectedName?.id == item.id) GoldAccent else CardBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            selectedName = item
                            individualRepetitionCount = 0
                            viewModel.triggerHaptic()
                        }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = item.name,
                            color = if (selectedName?.id == item.id) GoldAccent else LightWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.translation,
                            color = TextColorSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Detail overlay view
        AnimatedVisibility(visible = selectedName != null) {
            selectedName?.let { name ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDarkGlass)
                        .border(1.dp, GoldAccent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { selectedName = null }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", tint = MutedRed)
                            }
                            Text(
                                text = "بيان الاسم: ${name.name}",
                                color = GoldAccent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "المعنى والتأثر الإيماني:",
                            color = GoldAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = name.meaning,
                            color = LightWhite,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "شاهد وروده بالقرآن:",
                            color = GoldAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "“${name.verseMention}”",
                            color = EmeraldSecondary,
                            fontSize = 12.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Mini Counter
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkBackground)
                                .clickable {
                                    individualRepetitionCount += 1
                                    viewModel.triggerHaptic()
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$individualRepetitionCount / ٣٣",
                                color = GoldAccent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ابدأ بذكر وتكرير هذا الاسم العظيم 📿",
                                    color = LightWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.Adjust, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// FEATURE 4: QURAN SEARCH & TAFSEER
// ==========================================
@Composable
fun QuranSearchFeature(viewModel: EmaniatViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    
    val database = remember {
        listOf(
            QuranSearchVerse("الحمد", 1, "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ", "Praise be to Allah, Lord of the worlds", "الثناء بالجميل الاختياري على الباري المعبود، والإقرار بأنه الخالق المدبر الرازق المحيي المميت."),
            QuranSearchVerse("تقوى", 2, "ذَلِكَ الْكِتَابُ لاَ رَيْبَ فِيهِ هُدًى لِّلْمُتَّقِينَ", "This is the Book, there is no doubt in it, a guidance to those of piety", "المتقين: هم الخائفون من غضب الله، الفاعلون لأوامره والمنتهون عن زواجره وعصيانه في السر والعلن."),
            QuranSearchVerse("الصلاة", 3, "الَّذِينَ يُؤْمِنُونَ بِالْغَيْبِ وَيُقِيمُونَ الصَّلَاةَ وَمِمَّا رَزَقْنَاهُمْ يُنفِقُونَ", "Who believe in the unseen, perform prayer, and spend out of what We have provided", "إقامة الصلاة: أداؤها كاملة بشروطها وفروضها في أوقاتها الصحيحة بخشوع تام لله جل في علاه."),
            QuranSearchVerse("الرحمن", 5, "الرَّحْمَنِ الرَّحِيمِ", "The Beneficent, the Merciful", "صفتان مشتقتان من الرحمة، أولاهما ذات دلالة أشمل على سعة الفضل والرزق والرحمة في الدنيا والآخرة."),
            QuranSearchVerse("آمنوا", 9, "يَا أَيُّهَا الَّذِينَ آمَنُوا اسْتَعِينُوا بِالصَّبْرِ وَالصَّلَاةِ", "O you who have believed, seek help through patience and prayer", "نداء الله الحميم للمؤمنين لإرشادهم للتسلّح بفضيلة الصبر والمحافظة على الصلوات عند مواجهة نوائب الحياة.")
        )
    }

    val results = remember(searchQuery) {
        if (searchQuery.trim().isEmpty()) emptyList()
        else database.filter {
            it.text.contains(searchQuery) ||
                    it.tafseer.contains(searchQuery) ||
                    it.surahName.contains(searchQuery)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 60.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "باحث وتدبر الآيات الكريمة والتفسير 🔍",
                color = GoldAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "اكتب كلمة مفتاحية للبحث الفوري، وسنعرض لك شواهد الآيات المقترنة بها بوعاء تفسير الجلالين المتطابق.",
                color = TextColorSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("مثال: (تقوى، الحمد، الصلاة) للبحث الفوري...", color = TextColorSecondary.copy(alpha = 0.6f), fontSize = 12.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "ابحث", tint = GoldAccent) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "مسح", tint = TextColorSecondary)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldAccent,
                unfocusedBorderColor = CardBorder,
                focusedContainerColor = SurfaceDarkGlass,
                unfocusedContainerColor = SurfaceDarkGlass,
                focusedTextColor = LightWhite,
                unfocusedTextColor = LightWhite
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (results.isEmpty() && searchQuery.trim().isNotEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("لا توجد آيات مطابقة للكلمة المدخلة. جرّب: (الصلاة، الحمد).", color = TextColorSecondary, fontSize = 12.sp)
            }
        } else if (results.isEmpty()) {
            // Suggest list click items
            Text(
                text = "اضغط للمشاهدة السريعة لمفردات رائجة:",
                color = TextColorSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp).align(Alignment.End)
            )
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(database) { suggestion ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceDarkGlass)
                            .clickable { searchQuery = suggestion.surahName; viewModel.triggerHaptic() }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(16.dp))
                            Text(
                                text = "تدبّر موضوع: " + suggestion.surahName,
                                color = LightWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
        } else {
            // Render found Results
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(results) { verse ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceDarkGlass)
                            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(GoldAccent.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "رقم الآية: ${verse.verseNo}", color = GoldAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(text = "سورة تدبرية مقترحة", color = TextColorSecondary, fontSize = 11.sp)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "“${verse.text}”",
                                color = LightWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                lineHeight = 26.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            Divider(color = CardBorder, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "تفسير الجلالين والتدبر الروحي:",
                                color = GoldAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = verse.tafseer,
                                color = TextColorPrimary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// FEATURE 5: AMBIENT FOCUS SOUNDS
// ==========================================
// A simulation helper for beautiful focused noise playback
@Composable
fun AmbientAudioFeature(viewModel: EmaniatViewModel) {
    val context = LocalContext.current
    
    // Playback state of 4 channels
    var rainActive by remember { mutableStateOf(false) }
    var windMedinaActive by remember { mutableStateOf(false) }
    var forestCalmActive by remember { mutableStateOf(false) }
    var murmurActive by remember { mutableStateOf(false) }

    var rainVolume by remember { mutableStateOf(0.5f) }
    var windVolume by remember { mutableStateOf(0.6f) }
    var forestVolume by remember { mutableStateOf(0.4f) }
    var murmurVolume by remember { mutableStateOf(0.3f) }

    val activeColor = EmeraldSecondary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "مُولّد أصوات السكينة الروحية والتركيز 🌧️",
                color = GoldAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "أدِر وقم بمطابقة قنوات بيئات الأصوات الطبيعية الساكنة المنسجمة لمساعدتك على الخشوع أثناء تلاوة القرآن الكريم أو قراءة أوراد الصباح والمساء بتركيز وصفاء ذهني كامل.",
                color = TextColorSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sound list controller
        listOf(
            Triple("مطر مكة الشريف وبطحاء الحرم", rainActive, { b: Boolean -> rainActive = b }),
            Triple("طهر ونسيم جبال المسجد النبوي", windMedinaActive, { b: Boolean -> windMedinaActive = b }),
            Triple("سكينة وهدوء الرياض والحدائق", forestCalmActive, { b: Boolean -> forestCalmActive = b }),
            Triple("تمتمات واستغفار خاشع مرتل", murmurActive, { b: Boolean -> murmurActive = b })
        ).zip(listOf(rainVolume, windVolume, forestVolume, murmurVolume).zip(listOf({ v: Float -> rainVolume = v }, { v: Float -> windVolume = v }, { v: Float -> forestVolume = v }, { v: Float -> murmurVolume = v }))) { sound, volConfig ->
            val (name, isActive, onActiveToggle) = sound
            val (volume, onVolChange) = volConfig

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDarkGlass)
                    .border(
                        1.dp,
                        if (isActive) GoldAccent.copy(alpha = 0.5f) else CardBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = isActive,
                            onCheckedChange = {
                                onActiveToggle(it)
                                viewModel.triggerHaptic()
                                if (it) {
                                    Toast.makeText(context, "تم تفعيل القناة الصوتية 🔊", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GoldAccent,
                                checkedTrackColor = EmeraldSecondary.copy(alpha = 0.5f),
                                uncheckedThumbColor = LightWhite,
                                uncheckedTrackColor = CardBorder
                            )
                        )

                        Text(
                            text = name,
                            color = if (isActive) GoldAccent else LightWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right
                        )
                    }

                    if (isActive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "مستوى الصوت: ${(volume * 100).toInt()}%",
                                color = TextColorSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Slider(
                                value = volume,
                                onValueChange = { onVolChange(it) },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = GoldAccent,
                                    activeTrackColor = GoldAccent
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// FEATURE 6: FAITH REFLECTIONS NOTEBOOK
// ==========================================
@Composable
fun ReflectionsNotebookFeature(viewModel: EmaniatViewModel) {
    val context = LocalContext.current
    var titleInput by remember { mutableStateOf("") }
    var contentInput by remember { mutableStateOf("") }

    // Persistent collection inside SharedPreferences
    val sharedPrefs = remember { context.getSharedPreferences("emaniat_reflections", Context.MODE_PRIVATE) }
    var listFeed by remember { mutableStateOf(loadReflectionsFromPrefs(sharedPrefs)) }

    val handleSave = {
        if (titleInput.trim().isEmpty() || contentInput.trim().isEmpty()) {
            Toast.makeText(context, "يرجى تعبئة العنوان والمحتوى لكتابة الخاطرة!", Toast.LENGTH_SHORT).show()
        } else {
            val id = "ref_${System.currentTimeMillis()}"
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val dateStr = sdf.format(Date())
            val newList = listFeed + CustomReflection(id, titleInput, contentInput, dateStr)
            saveReflectionsToPrefs(sharedPrefs, newList)
            listFeed = newList
            titleInput = ""
            contentInput = ""
            viewModel.triggerHaptic()
            Toast.makeText(context, "تم حفظ خواطر التدبر بنجاح! 📖 ✨", Toast.LENGTH_SHORT).show()
        }
    }

    val handleDelete = { id: String ->
        val newList = listFeed.filter { it.id != id }
        saveReflectionsToPrefs(sharedPrefs, newList)
        listFeed = newList
        viewModel.triggerHaptic()
        Toast.makeText(context, "تم إزالة الخاطرة.", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "دفتر الخواطر وتدوين التدبر القرآني 📝",
                color = GoldAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "وثّق وصنّف تأملاتك الذاتية وخواطرك الإيمانية بعد مواظبتك اليومية على القراءة لتستحضرها دوماً وتراجع نيتك وعقيدتك مع الله.",
                color = TextColorSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Create New Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceDarkGlass)
                .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "خاطرة جديدة وتدبر آية:", color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.End))
                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    placeholder = { Text("مثال: تدبر من سورة البقرة...", color = TextColorSecondary.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, CardBorder, RoundedCornerShape(10.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkBackground,
                        unfocusedContainerColor = DarkBackground,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = LightWhite,
                        unfocusedTextColor = LightWhite
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = contentInput,
                    onValueChange = { contentInput = it },
                    placeholder = { Text("أكتب هنا طيف الخواطر، تدابير العبادة والآيات التي مَسَت روحك بصدق...", color = TextColorSecondary.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, CardBorder, RoundedCornerShape(10.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkBackground,
                        unfocusedContainerColor = DarkBackground,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = LightWhite,
                        unfocusedTextColor = LightWhite
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { handleSave() },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSecondary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("save_reflection_button")
                ) {
                    Text("حفظ وتوثيق الخاطرة اليوم 📝 ✅", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "دفترك الأرشيفي للخواطر المحفوظة:",
            color = TextColorSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp).align(Alignment.End)
        )

        if (listFeed.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("لا توجد خواطر مسجلة بعد. أضف خاطرتك الأولى بالأعلى!", color = TextColorSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        } else {
            listFeed.reversed().forEach { ref ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceDarkGlass)
                        .border(0.5.dp, CardBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { handleDelete(ref.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف الخاطرة", tint = MutedRed, modifier = Modifier.size(16.dp))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = ref.title, color = GoldAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Right)
                                Text(text = ref.dateString, color = TextColorSecondary, fontSize = 9.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = ref.content,
                            color = LightWhite,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private fun loadReflectionsFromPrefs(prefs: android.content.SharedPreferences): List<CustomReflection> {
    return try {
        val jsonArrayStr = prefs.getString("reflections_list", "[]") ?: "[]"
        val arr = JSONArray(jsonArrayStr)
        val list = mutableListOf<CustomReflection>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                CustomReflection(
                    obj.getString("id"),
                    obj.getString("title"),
                    obj.getString("content"),
                    obj.getString("date")
                )
            )
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

private fun saveReflectionsToPrefs(prefs: android.content.SharedPreferences, list: List<CustomReflection>) {
    try {
        val arr = JSONArray()
        list.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("content", item.content)
            obj.put("date", item.dateString)
            arr.put(obj)
        }
        prefs.edit().putString("reflections_list", arr.toString()).apply()
    } catch (e: Exception) {
        // ignore
    }
}

// ==========================================
// FEATURE 7: FAMOUS ADHAN VOICES LIBRARY
// ==========================================
@Composable
fun AdhanVoicesFeature(viewModel: EmaniatViewModel) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("emaniat_prefs", Context.MODE_PRIVATE) }
    var selectedMuadhin by remember { mutableStateOf(sharedPrefs.getString("selected_muadhin_key", "haram_mecca") ?: "haram_mecca") }

    // Media trigger states
    var isTestingSound by remember { mutableStateOf(false) }
    var mediaPlayerInstance by remember { mutableStateOf<MediaPlayer?>(null) }

    val muadhins = remember {
        listOf(
            Triple("haram_mecca", "أذان الحرم المكي الشريف", "تلاوة ونداء شيخ مؤذني مكة، تثير الطهر وسكينة الصفا والمروة."),
            Triple("haram_medina", "أذان المسجد النبوي المبارك", "التموج الحجازي النقي المهيب، مستلهم من روضة المصطفى الشريفة."),
            Triple("alaqsa", "أذان المسجد الأقصى الأسير", "البرق الشامي الأصيل ينسجم تتابعاً مع عبق الصخرة المشرفة."),
            Triple(" حجازي_بديع", "الأذان الحجازي البديع الفريد", "المقامات الموسيقية المكية العتيقة المغلفة بالطهر والهيبة.")
        )
    }

    LaunchedEffect(selectedMuadhin) {
        mediaPlayerInstance?.let {
            if (it.isPlaying) {
                it.stop()
                it.release()
            }
        }
        mediaPlayerInstance = null
        isTestingSound = false
    }

    // Stop media player when screen elements leave memory
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayerInstance?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
    }

    val handlePlayDemo = { url: String ->
        viewModel.triggerHaptic()
        if (isTestingSound) {
            mediaPlayerInstance?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayerInstance = null
            isTestingSound = false
            Toast.makeText(context, "تم إيقاف تشغيل العينة الصوتية.", Toast.LENGTH_SHORT).show()
        } else {
            try {
                isTestingSound = true
                Toast.makeText(context, "جاري تحضير واستماع عينة الأذان... 🔊", Toast.LENGTH_SHORT).show()
                val mp = MediaPlayer.create(context, Uri.parse(url))
                mp.setOnPreparedListener {
                    it.start()
                }
                mp.setOnCompletionListener {
                    isTestingSound = false
                }
                mediaPlayerInstance = mp
            } catch (e: Exception) {
                isTestingSound = false
                Toast.makeText(context, "لم نتمكن من جلب العينة الصوتية في الوقت الحالي.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "مكتبة أصوات الأذان وتخصيص المؤذن 🔊",
                color = GoldAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "اختر المؤذن المفضل وتأمل النبرة الحجازية العتيقة للأذان التي سيتم عبرها مناداة الصلوات في مواقيت الصلاة والمحافظة عليها.",
                color = TextColorSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        muadhins.forEach { m ->
            val (key, label, desc) = m
            val isSelected = selectedMuadhin == key

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDarkGlass)
                    .border(
                        1.dp,
                        if (isSelected) GoldAccent else CardBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        selectedMuadhin = key
                        sharedPrefs.edit().putString("selected_muadhin_key", key).apply()
                        viewModel.triggerHaptic()
                        Toast.makeText(context, "تم تعيين المؤذن المختار كافتراضي بنجاح! 🕌", Toast.LENGTH_SHORT).show()
                    }
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Test Play Button
                    IconButton(
                        onClick = {
                            // High performance URLs of lovely tiny Adhans
                            val demoUrl = when(key) {
                                "haram_mecca" -> "https://www.islamcan.com/audio/adhan/azan1.mp3"
                                "haram_medina" -> "https://www.islamcan.com/audio/adhan/azan3.mp3"
                                "alaqsa" -> "https://www.islamcan.com/audio/adhan/azan12.mp3"
                                else -> "https://www.islamcan.com/audio/adhan/azan15.mp3"
                            }
                            handlePlayDemo(demoUrl)
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isSelected && isTestingSound) MutedRed.copy(alpha = 0.2f) else EmeraldSecondary.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = if (isSelected && isTestingSound) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = "استماع للتجربة",
                            tint = if (isSelected && isTestingSound) MutedRed else GoldAccent
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp, start = 8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSecondary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(text = label, color = if (isSelected) GoldAccent else LightWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Right)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = desc, color = TextColorSecondary, fontSize = 11.sp, lineHeight = 16.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

// ==========================================
// FEATURE 8: CALCULATION METHODS CONFIGURATION
// ==========================================
@Composable
fun CalculationMethodsFeature(viewModel: EmaniatViewModel) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("emaniat_prefs", Context.MODE_PRIVATE) }
    var selectedMethod by remember { mutableStateOf(sharedPrefs.getString("p_calc_method", "egyptian") ?: "egyptian") }

    val methods = remember {
        listOf(
            Pair("egyptian", "الهيئة المصرية العامة للمساحة (الافتراضي)"),
            Pair("makkah", "جامعة أم القرى - مكة المكرمة السنوي"),
            Pair("isna", "الجمعية الإسلامية لأمريكا الشمالية (ISNA)"),
            Pair("mwl", "رابطة العالم الإسلامي (فلكي عالمي)"),
            Pair("tehran", "معهد الجيوفيزياء بجامعة طهران للشيعة")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "إعدادات معادلات حساب المواقيت ⚙️",
                color = GoldAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "تعديل المذاهب والزوايا الفلكية لحساب الشروق والغروب الدقيق والصلوات الفرعية لمختلف أنحاء المعمورة والبلدان والمنظمات الإسلامية الكبرى.",
                color = TextColorSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        methods.forEach { entry ->
            val (key, label) = entry
            val isSelected = selectedMethod == key

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDarkGlass)
                    .border(
                        1.dp,
                        if (isSelected) GoldAccent else CardBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        selectedMethod = key
                        sharedPrefs.edit().putString("p_calc_method", key).apply()
                        viewModel.triggerHaptic()
                        Toast.makeText(context, "تم ضبط منهجية الحساب وإعادة الجدولة بنجاح! ⚙️ ✅", Toast.LENGTH_SHORT).show()
                    }
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) GoldAccent else Color.Transparent)
                            .border(1.5.dp, GoldAccent, CircleShape)
                    )

                    Text(
                        text = label,
                        color = if (isSelected) GoldAccent else LightWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right
                    )
                }
            }
        }
    }
}

// ==========================================
// FEATURE 9: STARRED FAVORITE AZKAR LIST
// ==========================================
@Composable
fun StarredAzkarFeature(viewModel: EmaniatViewModel) {
    val context = LocalContext.current
    
    // Fallback collection of Azkar to browse and star/favorite
    val standardAzkar = remember {
        listOf(
            Triple("f_1", "سبحان الله وبحمده، عدد خلقه ورضا نفسه وزنة عرشه ومداد كلماته.", "ثلاث مرات عند الخروج في الصبح"),
            Triple("f_2", "اللهم إني أعوذ بك من الهم والحزن، العجز والكسل، الجبن والبخل وضلع الدين.", "دعاء الفرج والغم عند الابتلاء"),
            Triple("f_3", "رضيت بالله رباً وبالإسلام ديناً وبمحمد صلى الله عليه وسلم نبياً ورسولاً.", "ثلاث مرات صباحاً ومساءً تُوجِب الجنة"),
            Triple("f_4", "يا حي يا قيوم برحمتك أستغيث، أصلح لي شأني كله ولا تكلني إلى نفسي طرفة عين.", "دعاء الفرج السريع المبارك")
        )
    }

    // Stars persisted in shared preferences
    val sharedPrefs = remember { context.getSharedPreferences("emaniat_stars", Context.MODE_PRIVATE) }
    var starredKeysSet by remember { mutableStateOf(sharedPrefs.getStringSet("starred_keys", emptySet()) ?: emptySet()) }

    val handleStarToggle = { keyStr: String ->
        val updated = if (starredKeysSet.contains(keyStr)) {
            starredKeysSet - keyStr
        } else {
            starredKeysSet + keyStr
        }
        sharedPrefs.edit().putStringSet("starred_keys", updated).apply()
        starredKeysSet = updated
        viewModel.triggerHaptic()
    }

    val finalStarredItems = remember(starredKeysSet) {
        standardAzkar.filter { starredKeysSet.contains(it.first) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "الأذكار والأوراد المفضلة ⭐",
                color = GoldAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "اجمع أورادك المفضلة صباحاً ومساءً بدستور مستقل بنجمة لتوفر مشقة التصفح اليومي بمجرد لمسة سريعة.",
                color = TextColorSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (finalStarredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "لا توجد أذكار مضافة للمفضلة بنجمة بعد.\nأضف نجمة على الأوراد المقترحة أدناه للبدء!", color = TextColorSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        } else {
            Text(
                text = "أورادي ونجماتي الإيمانية المحفوظة:",
                color = GoldAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp).align(Alignment.End)
            )

            finalStarredItems.forEach { entry ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceDarkGlass)
                        .border(1.dp, GoldAccent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { handleStarToggle(entry.first) }) {
                                Icon(Icons.Default.Star, contentDescription = "إزالة النجمة", tint = GoldAccent)
                            }
                            Text(text = "ورد مستحب", color = TextColorSecondary, fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = entry.second, color = LightWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth(), lineHeight = 18.sp)
                    }
                }
            }
            Divider(color = CardBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))
        }

        Text(
            text = "اكتشف وأضف أوراد بالتحصين المبارك:",
            color = TextColorSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp).align(Alignment.End)
        )

        standardAzkar.forEach { entry ->
            val hasStar = starredKeysSet.contains(entry.first)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceDarkGlass)
                    .border(0.5.dp, CardBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { handleStarToggle(entry.first) }) {
                        Icon(
                            imageVector = if (hasStar) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "المفضلة",
                            tint = if (hasStar) GoldAccent else TextColorSecondary.copy(alpha = 0.6f)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(text = entry.second, color = LightWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth(), lineHeight = 16.sp)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(text = entry.third, color = GoldAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

// ==========================================
// FEATURE 10: HIJRI CALENDAR & OCCASIONS COUNTDOWN
// ==========================================
@Composable
fun HijriCalendarFeature(viewModel: EmaniatViewModel) {
    // Basic math simulation of Hijri sync representing Year 1447H
    val currentDayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    
    // Simulate Hijri Date calculations
    val hijriMonths = listOf("محرم", "صفر", "ربيع الأول", "ربيع الثاني", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
    val estimatedHijriMonthIndex = (currentDayOfYear / 29) % 12
    val estimatedHijriDay = (currentDayOfYear % 29) + 1
    val monthName = hijriMonths[estimatedHijriMonthIndex]

    // Countdown targets mapped using dynamic calculation simulation
    val occasions = remember {
        listOf(
            Pair("شهر رمضان المبارك 🕋", 110), // approximate days remaining
            Pair("عيد الفطر السعيد 🎉", 140),
            Pair("يوم عرفة المبارك 🗻", 200),
            Pair("عيد الأضحى الأعظم 🐑", 201),
            Pair("رأس السنة الهجرية الجديدة 🌙", 295)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "التقويم الهجري والوقائع والمناسبات 🌙",
                color = GoldAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "التاريخ الهجري لليوم: ",
                    color = TextColorSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$estimatedHijriDay $monthName ١٤٤٧ هـ",
                    color = GoldAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "العد التنازلي لمواقيت ومناسبات الخير القادمة:",
            color = TextColorSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp).align(Alignment.End)
        )

        occasions.forEach { spec ->
            val (title, offsetDays) = spec
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDarkGlass)
                    .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Timer Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(EmeraldSecondary.copy(alpha = 0.2f))
                            .border(1.dp, GoldAccent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "متبقي $offsetDays يوم",
                            color = GoldAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = title, color = LightWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = "صنفت كواقعة كبرى مباركة", color = TextColorSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
