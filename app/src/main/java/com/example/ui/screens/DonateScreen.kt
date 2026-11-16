package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AdhanManager
import com.example.data.CompassMath
import com.example.data.CompassSensorStatus
import com.example.data.HadithCollection
import com.example.data.rememberCompassState
import com.example.ui.components.ElegantBackgroundPattern
import com.example.ui.components.GlassCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.EmaniatViewModel
import java.text.SimpleDateFormat
import java.util.*

enum class DonateSubTab {
    VIP_GOLD,
    QIBLA_PRAYERS,
    NAMES_OF_ALLAH,
    ZAKAT_CALC,
    HADITH_STUDIO
}

@Composable
fun DonateScreen(
    viewModel: EmaniatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeSubTab by remember { mutableStateOf(DonateSubTab.VIP_GOLD) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Ambient Islamic back ornament 
        ElegantBackgroundPattern(alpha = 0.04f)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Elegant horizontal Scrollable Category Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDarkGlass)
                    .border(BorderStroke(0.5.dp, CardBorder))
                    .padding(vertical = 8.dp, horizontal = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(4.dp))
                DonateSubTab.values().forEach { tab ->
                    val isSelected = activeSubTab == tab
                    val label = when (tab) {
                        DonateSubTab.VIP_GOLD -> "🤝 دعم التطبيق"
                        DonateSubTab.QIBLA_PRAYERS -> "🧭 القبلة والصلوات"
                        DonateSubTab.NAMES_OF_ALLAH -> "✨ أسماء الله"
                        DonateSubTab.ZAKAT_CALC -> "💰 حاسبة الزكاة"
                        DonateSubTab.HADITH_STUDIO -> "📜 حديث وحكمة"
                    }

                    val tabBgBrush = if (isSelected) {
                        Brush.horizontalGradient(listOf(EmeraldPrimary, EmeraldSecondary))
                    } else {
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(tabBgBrush)
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (isSelected) GoldAccent else CardBorder.copy(alpha = 0.5f)
                                ),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                viewModel.triggerHaptic()
                                activeSubTab = tab
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) LightWhite else TextColorSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Sub-screen Container with crisp entry transformations
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeSubTab) {
                    DonateSubTab.VIP_GOLD -> VipGoldTab(viewModel = viewModel)
                    DonateSubTab.QIBLA_PRAYERS -> QiblaAndPrayersTab(viewModel = viewModel)
                    DonateSubTab.NAMES_OF_ALLAH -> NamesOfAllahTab(viewModel = viewModel)
                    DonateSubTab.ZAKAT_CALC -> ZakatCalculatorTab(viewModel = viewModel)
                    DonateSubTab.HADITH_STUDIO -> HadithStudioTab(viewModel = viewModel)
                }
            }
        }
    }
}

// ---------------------------------------------------------
// FEATURE 1: VOLUNTARY APP DEVELOPMENT SUPPORT
// ---------------------------------------------------------
@Composable
fun VipGoldTab(viewModel: EmaniatViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, GoldAccent.copy(alpha = 0.45f)), RoundedCornerShape(20.dp))
                .shadow(12.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDarkGlass)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                ElegantBackgroundPattern(alpha = 0.05f)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(GoldAccent.copy(alpha = 0.3f), Color.Transparent)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "دعم التطبيق",
                            tint = GoldAccent,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "نشر الخير والصدقة الجارية 🌿",
                        color = GoldAccent,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "جميع ميزات تطبيق إيمانيات — القرآن الكريم، التسميع والتصحيح الذكي، الأذكار، ومواقيت الصلاة — متاحة مجاناً للجميع دون أي اشتراكات مدفوعة.",
                        color = TextColorPrimary,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(EmeraldMuted.copy(alpha = 0.22f))
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "«مَنْ دَلَّ عَلَى خَيْرٍ فَلَهُ مِثْلُ أَجْرِ فَاعِلِهِ»",
                                color = GoldAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "أعظم دعم للتطبيق هو الدعاء للقائمين عليه بظهر الغيب ومشاركته مع الأهل والأصدقاء ليكون صدقةً جاريةً لك.",
                                color = TextColorSecondary,
                                fontSize = 11.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.triggerHaptic()
                runCatching {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "تطبيق إيمانيات — القرآن الكريم، الأذكار، مواقيت الصلاة، واتجاه القبلة:\nhttps://play.google.com/store/apps/details?id=com.immanyat.com"
                        )
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "مشاركة تطبيق إيمانيات"))
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(imageVector = Icons.Default.Share, contentDescription = "مشاركة التطبيق", tint = LightWhite)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "انشر التطبيق صدقةً جارية مع أحبابك 📤",
                color = LightWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ---------------------------------------------------------
// FEATURE 2: INTERACTIVE PRAYER CHECKER & COMPASS WIDGET
// ---------------------------------------------------------
@Composable
fun QiblaAndPrayersTab(viewModel: EmaniatViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Persistent state for checklist
    val prefs = remember { context.getSharedPreferences("emaniat_prayers", Context.MODE_PRIVATE) }
    val todayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    var fajrChecked by remember { mutableStateOf(prefs.getBoolean("${todayKey}_fajr", false)) }
    var dhuhrChecked by remember { mutableStateOf(prefs.getBoolean("${todayKey}_dhuhr", false)) }
    var asrChecked by remember { mutableStateOf(prefs.getBoolean("${todayKey}_asr", false)) }
    var maghribChecked by remember { mutableStateOf(prefs.getBoolean("${todayKey}_maghrib", false)) }
    var ishaChecked by remember { mutableStateOf(prefs.getBoolean("${todayKey}_isha", false)) }

    var prayerStreak by remember { mutableStateOf(prefs.getInt("prayer_streak", 0)) }

    // Real device rotation sensor and calculated Qibla bearing for saved location
    val savedCoords = remember { AdhanManager.getSavedCoordinates(context) }
    val qiblaBearing = remember { AdhanManager.calculateQiblaDirection(context).toFloat() }
    val compassState = rememberCompassState(savedCoords.latitude, savedCoords.longitude)

    // Manual compass fallback when hardware sensor is unavailable
    var manualOffset by remember { mutableFloatStateOf(0f) }
    val isManualMode = compassState.status == CompassSensorStatus.UNAVAILABLE

    val targetContinuous = if (isManualMode) manualOffset else compassState.continuousAzimuth
    val animatedContinuous by animateFloatAsState(
        targetValue = targetContinuous,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "compassRotation"
    )
    val displayAzimuth = CompassMath.normalizeAngle360(animatedContinuous)
    val relativeQiblaAngle = CompassMath.normalizeAngle360(qiblaBearing - displayAzimuth)
    val isAligned = (relativeQiblaAngle <= 4f || relativeQiblaAngle >= 356f) &&
        (isManualMode || compassState.status != CompassSensorStatus.DETECTING)

    fun updateStreak() {
        // Simple streak logic: if all 5 prayers checked, streak increments
        val allChecked = fajrChecked && dhuhrChecked && asrChecked && maghribChecked && ishaChecked
        if (allChecked) {
            val lastStreakDay = prefs.getString("last_streak_day", "")
            if (lastStreakDay != todayKey) {
                prayerStreak += 1
                prefs.edit().putInt("prayer_streak", prayerStreak).putString("last_streak_day", todayKey).apply()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // High-end digital compass layout
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🧭 بوصلة اتجاه القبلة الذكية",
                    color = GoldAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // The Compass Visual
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .clip(CircleShape)
                        .background(DarkBackground)
                        .border(
                            BorderStroke(
                                width = if (isAligned) 3.dp else 2.dp,
                                color = if (isAligned) GoldAccent else CardBorder
                            ),
                            CircleShape
                        )
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer compass rim rotating continuously relative to True North
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(-animatedContinuous),
                        contentAlignment = Alignment.Center
                    ) {
                        // Dial marks
                        Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                            Text("N", color = Color(0xFFE53935), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopCenter))
                            Text("S", color = TextColorSecondary, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomCenter))
                            Text("E", color = TextColorSecondary, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterEnd))
                            Text("W", color = TextColorSecondary, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterStart))
                        }

                        // Kaaba icon pinned at Qibla bearing on dial
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .rotate(qiblaBearing),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Text("🕋", fontSize = 18.sp, modifier = Modifier.padding(top = 18.dp))
                        }
                    }

                    // Active Kaaba Needle rotating directly toward Qibla relative to phone orientation
                    Column(
                        modifier = Modifier
                            .fillMaxSize(0.76f)
                            .rotate(qiblaBearing - animatedContinuous),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .weight(1f)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (isAligned) GoldAccent else EmeraldPrimary)
                        )
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(GoldAccent)
                        )
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .weight(1f)
                                .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                                .background(CardBorder)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = when {
                        isAligned -> "✨ أنت الآن في اتجاه القبلة تماماً 🕋"
                        isManualMode -> "الجهاز لا يحتوي على مستشعر بوصلة؛ استخدم المعايرة اليدوية:"
                        compassState.status == CompassSensorStatus.DETECTING -> "جاري قراءة مستشعرات البوصلة..."
                        else -> "تم ضبط البوصلة (${compassState.sensorSource}) — حرّك الهاتف على شكل 8 عند الحاجة"
                    },
                    color = if (isAligned) EmeraldSecondary else TextColorSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isAligned) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )

                if (isManualMode) {
                    Slider(
                        value = manualOffset,
                        onValueChange = { manualOffset = it },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(
                            thumbColor = GoldAccent,
                            activeTrackColor = EmeraldPrimary
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Text(
                    text = "اتجاه القبلة لموقعك: ${qiblaBearing.toInt()}° | اتجاه الهاتف: ${displayAzimuth.toInt()}°",
                    color = GoldAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.triggerHaptic()
                        viewModel.setScreen(AppScreen.QIBLA)
                    },
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = "فتح البوصلة الكاملة",
                        tint = GoldAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "فتح البوصلة التفصيلية الكاملة",
                        color = GoldAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Daily Prayers Checklist Track
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "🔥 سلسلة الإنتظام: $prayerStreak يوم",
                            color = GoldAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "🗓️ متابع صلوات اليوم",
                        color = GoldAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val prayers = listOf(
                    Triple("الفجر", fajrChecked) { checked: Boolean ->
                        fajrChecked = checked
                        prefs.edit().putBoolean("${todayKey}_fajr", checked).apply()
                        updateStreak()
                    },
                    Triple("الظهر", dhuhrChecked) { checked: Boolean ->
                        dhuhrChecked = checked
                        prefs.edit().putBoolean("${todayKey}_dhuhr", checked).apply()
                        updateStreak()
                    },
                    Triple("العصر", asrChecked) { checked: Boolean ->
                        asrChecked = checked
                        prefs.edit().putBoolean("${todayKey}_asr", checked).apply()
                        updateStreak()
                    },
                    Triple("المغرب", maghribChecked) { checked: Boolean ->
                        maghribChecked = checked
                        prefs.edit().putBoolean("${todayKey}_maghrib", checked).apply()
                        updateStreak()
                    },
                    Triple("العشاء", ishaChecked) { checked: Boolean ->
                        ishaChecked = checked
                        prefs.edit().putBoolean("${todayKey}_isha", checked).apply()
                        updateStreak()
                    }
                )

                prayers.forEach { (name, checked, onCheckedChange) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (checked) EmeraldPrimary.copy(alpha = 0.08f)
                                else Color.Transparent
                            )
                            .clickable {
                                viewModel.triggerHaptic()
                                onCheckedChange(!checked)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                viewModel.triggerHaptic()
                                onCheckedChange(it)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary)
                        )

                        Text(
                            text = name,
                            color = if (checked) GoldAccent else TextColorPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// FEATURE 3: 99 BEAUTIFUL NAMES OF ALLAH & MEDITATION
// ---------------------------------------------------------
data class AllahName(val arabic: String, val englishName: String, val meaning: String, val detail: String)

@Composable
fun NamesOfAllahTab(viewModel: EmaniatViewModel) {
    val scrollState = rememberScrollState()
    var selectedName by remember { mutableStateOf<AllahName?>(null) }

    val names = remember {
        listOf(
            AllahName("الرَّحْمَنُ", "Ar-Rahman", "الرحمن بجود كرمه وعطفه الوفير", "الذي تشمل رحمته الكائنات جميعاً ويعم خيره البرايا من غير تمييز."),
            AllahName("الرَّحِيمُ", "Ar-Rahim", "الرحيم بخصوصية عباده المؤمنين", "المنعم برحمته الخاصة على عباده الأتقياء الطائعين الهادين والمستغفرين."),
            AllahName("الْمَلِكُ", "Al-Malik", "الحاكم المطلق ذو السلطان السامي", "الذي يملك الكون كله ويتصرف في ملكوته بحكمة من غير شريك عادل مرشد."),
            AllahName("الْقُدُّوسُ", "Al-Quddus", "المنزه والمقدس عن دنس العيوب", "الطاهر والمنزه عن عيوب ومثالب النقص المترفع بالكمال المطلق الرباني."),
            AllahName("السَّلَامُ", "As-Salam", "ناشر مودة السلام والأمان بفيضه", "الذي سلمت ذاته العظيمة من العيوب وحلت ببركته الأمان والسكينة للنفوس."),
            AllahName("الْمُؤْمِنُ", "Al-Mu'min", "واهب معطيات الأمان والتكامل", "الذي صدّق نفسه ورسله ونشر بأمانه السكينة في أفئدة المرتجين لرحمته."),
            AllahName("الْمُهَيْمِنُ", "Al-Mehymin", "الرقيب والمطلع والحامي للبشر", "الحافظ المطلع الشاهد المسيطر على جميع الخلائق بدقة وإحاطة تامة."),
            AllahName("الْعَزِيزُ", "Al-Aziz", "القدر المنزه بالعز والقوة والمجد", "الغالب الذي لا يهزم الشامخ بالعزة والقوة العظيمة القاهرة التي تشد العروة."),
            AllahName("الْجَبَّارُ", "Al-Jabbar", "الباني وجابر القلوب المنكسرة", "الذي يجبر كسر القلوب ويداوي الضعيف ويصلح بمشيئته أحوال المؤمنين."),
            AllahName("الْمُتَكَبِّرُ", "Al-Mutakabbir", "ذو العلو والكبرياء والجلال المطلق", "المتفرد بصفات العلو والتعالي والجمال، المترفع عن صفات النقص البشري.")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "✨ أسماء الله الحسنى ومعانيها الروحانية",
            color = GoldAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(names) { index, name ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(12.dp))
                        .clickable {
                            viewModel.triggerHaptic()
                            selectedName = name
                        },
                    colors = CardDefaults.cardColors(containerColor = SurfaceDarkGlass),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = name.arabic,
                            color = GoldAccent,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = name.englishName,
                            color = LightWhite,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = name.meaning,
                            color = TextColorSecondary,
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    // Name detailed meditation modal
    if (selectedName != null) {
        val nameData = selectedName!!
        var isMeditating by remember { mutableStateOf(false) }
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (isMeditating) 1.25f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )

        AlertDialog(
            onDismissRequest = { selectedName = null },
            title = {
                Text(
                    text = "شرح وتأمل الاسم الكريم 🕌",
                    color = GoldAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .rotate(if (isMeditating) pulseScale * 15f else 0f)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        if (isMeditating) EmeraldPrimary else GoldAccent.copy(alpha = 0.2f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(BorderStroke(1.5.dp, GoldAccent), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = nameData.arabic,
                            color = LightWhite,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = nameData.englishName,
                        color = GoldAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = nameData.detail,
                        color = TextColorPrimary,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Pulse meditation guide
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isMeditating) EmeraldPrimary.copy(alpha = 0.15f)
                                else CardBorder.copy(alpha = 0.1f)
                            )
                            .clickable {
                                isMeditating = !isMeditating
                                viewModel.triggerHaptic()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isMeditating) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = "Meditation State",
                            tint = GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isMeditating) "جلسة تأمّل حية وغوص في الذكر... (انقر للإيقاف)" else "بدء جلسة تأمّل وقراءة الذكر المستمر",
                            color = LightWhite,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isMeditating) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "تنفس ببطء مع نبض الدائرة الربانية... ردد بقلبك: يا ${nameData.arabic.replace("الـ", "")}",
                            color = EmeraldSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedName = null },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("إغلاق", color = LightWhite)
                }
            },
            containerColor = SurfaceDarkGlass,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// ---------------------------------------------------------
// FEATURE 4: CHARITY & ZAKAT CALCULATOR
// ---------------------------------------------------------
@Composable
fun ZakatCalculatorTab(viewModel: EmaniatViewModel) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Inputs
    var cashValue by remember { mutableStateOf("") }
    var goldGrams by remember { mutableStateOf("") }
    var goldCaratPrice by remember { mutableStateOf("240") } // Average price of 1g 24K Gold in SAR/AED
    var silverGrams by remember { mutableStateOf("") }
    var merchandiseValue by remember { mutableStateOf("") }
    var debtsToUser by remember { mutableStateOf("") }
    var debtsFromUser by remember { mutableStateOf("") }

    // Calculated fields
    var totalWealth by remember { mutableStateOf(0f) }
    var goldThresholdValue by remember { mutableStateOf(0f) }
    var zakatDue by remember { mutableStateOf(0f) }
    var isZakatObligatory by remember { mutableStateOf(false) }
    var hasCalculated by remember { mutableStateOf(false) }

    fun calculateZakat() {
        val cash = cashValue.toFloatOrNull() ?: 0f
        val gold = goldGrams.toFloatOrNull() ?: 0f
        val goldPrice = goldCaratPrice.toFloatOrNull() ?: 240f
        val silver = silverGrams.toFloatOrNull() ?: 0f
        val silverPrice = 3.5f // Avg silver price per gram
        val merchandise = merchandiseValue.toFloatOrNull() ?: 0f
        val debtsTo = debtsToUser.toFloatOrNull() ?: 0f
        val debtsFrom = debtsFromUser.toFloatOrNull() ?: 0f

        // Wealth total: Cash + (Gold * goldPrice) + (Silver * silverPrice) + Merchandise + DebtsTo - DebtsFrom
        totalWealth = cash + (gold * goldPrice) + (silver * silverPrice) + merchandise + debtsTo - debtsFrom
        if (totalWealth < 0f) totalWealth = 0f

        // Gold Nisab threshold (85 grams of 24K gold equivalent)
        goldThresholdValue = 85f * goldPrice

        isZakatObligatory = totalWealth >= goldThresholdValue
        zakatDue = if (isZakatObligatory) totalWealth * 0.025f else 0f
        hasCalculated = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "💰 حاسبة الزكاة والصدقات الإسلامية",
            color = GoldAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "املأ قيم أصولك المالية:",
                    color = GoldAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = cashValue,
                    onValueChange = { cashValue = it },
                    label = { Text("السيولة النقدية والودائع البنكية") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = goldGrams,
                        onValueChange = { goldGrams = it },
                        label = { Text("الذهب بالجرامات") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = goldCaratPrice,
                        onValueChange = { goldCaratPrice = it },
                        label = { Text("سعر جرام الذهب (24)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = merchandiseValue,
                    onValueChange = { merchandiseValue = it },
                    label = { Text("أصول تجارية وبضائع مخزنة") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = debtsToUser,
                        onValueChange = { debtsToUser = it },
                        label = { Text("ديون مرجوة السداد لك") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = debtsFromUser,
                        onValueChange = { debtsFromUser = it },
                        label = { Text("ديون والتزامات عليك") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        viewModel.triggerHaptic()
                        calculateZakat()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("احسب الزكاة الواجبة 🧮", color = LightWhite, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (hasCalculated) {
            Spacer(modifier = Modifier.height(16.dp))

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        BorderStroke(
                            1.dp,
                            if (isZakatObligatory) GoldAccent else EmeraldPrimary.copy(alpha = 0.5f)
                        ),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isZakatObligatory) "وجبت عليك فريضة الزكاة 💰" else "لم تبلغ مالياتك النصاب بعد",
                        color = if (isZakatObligatory) GoldAccent else EmeraldSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "إجمالي رأس المال والأصول الخاضعة: ${totalWealth.toInt()} عملة محلية",
                        color = LightWhite,
                        fontSize = 11.sp
                    )

                    Text(
                        text = "قيمة النصاب المقترنة بـ 85ج ذهب: ${goldThresholdValue.toInt()} عملة محلية",
                        color = TextColorSecondary,
                        fontSize = 10.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isZakatObligatory) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(GoldAccent.copy(alpha = 0.15f))
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "القدر الواجب إخراجه (2.5%): ${zakatDue.toInt()} عملة محلية",
                                color = GoldAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                viewModel.triggerHaptic()
                                runCatching {
                                    val summary = "ملخص حساب الزكاة — تطبيق إيمانيات\n" +
                                        "إجمالي المال الخاضع للزكاة: ${totalWealth.toInt()}\n" +
                                        "نصاب الذهب المقدر (٨٥ جم): ${goldThresholdValue.toInt()}\n" +
                                        "الزكاة الواجبة (2.5%): ${zakatDue.toInt()}"
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, summary)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "مشاركة ملخص الزكاة"))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "مشاركة ملخص حساب الزكاة 📤",
                                color = LightWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "تُصرف الزكاة لمستحقيها في المصارف الثمانية الشرعية (سورة التوبة: ٦٠).",
                            color = TextColorSecondary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "ماليتك لم تبلغ حد النصاب، ولكن الصدقات الطوعية تقيك مصارع السوء وتضاعف الأجور.",
                            color = TextColorSecondary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// FEATURE 5: DAILY HADITH SHARE & TEMPLATE MAKER
// ---------------------------------------------------------
@Composable
fun HadithStudioTab(viewModel: EmaniatViewModel) {
    val context = LocalContext.current
    var selectedTopic by remember { mutableStateOf("الكل") }
    var currentIndex by remember { mutableStateOf(0) }
    var showExplanation by remember { mutableStateOf(false) }
    var posterBgColor by remember { mutableStateOf(EmeraldPrimary) }

    val filteredHadiths = remember(selectedTopic) {
        if (selectedTopic == "الكل") HadithCollection.ALL_50_HADITHS
        else HadithCollection.ALL_50_HADITHS.filter { it.topic == selectedTopic }
    }

    val safeIndex = currentIndex.coerceIn(0, (filteredHadiths.size - 1).coerceAtLeast(0))
    val currentHadith = filteredHadiths[safeIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "📜 موسوعة ٥٠ حديثاً نبوياً صحيحاً بالمصادر",
            color = GoldAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Topic Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HadithCollection.TOPICS.forEach { topic ->
                val isSelected = selectedTopic == topic
                val topicCount = if (topic == "الكل") {
                    HadithCollection.ALL_50_HADITHS.size
                } else {
                    HadithCollection.ALL_50_HADITHS.count { it.topic == topic }
                }
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        viewModel.triggerHaptic()
                        selectedTopic = topic
                        currentIndex = 0
                        showExplanation = false
                    },
                    label = {
                        Text(
                            text = "$topic ($topicCount)",
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldPrimary,
                        selectedLabelColor = GoldAccent,
                        containerColor = SurfaceDarkGlass,
                        labelColor = TextColorSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = CardBorder,
                        selectedBorderColor = GoldAccent
                    )
                )
            }
        }

        // The Poster Board
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(BorderStroke(1.dp, CardBorder), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceDarkGlass),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header: Card Number + Color Palette
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = GoldAccent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.5.dp, GoldAccent.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "#${currentHadith.number} • ${currentHadith.title}",
                            color = GoldAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val colors = listOf(EmeraldPrimary, EmeraldMuted, GoldAccent, Color(0xFF1E3A8A), Color(0xFF6B21A8))
                        colors.forEach { col ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(
                                        BorderStroke(
                                            2.dp,
                                            if (posterBgColor == col) LightWhite else Color.Transparent
                                        ),
                                        CircleShape
                                    )
                                    .clickable {
                                        viewModel.triggerHaptic()
                                        posterBgColor = col
                                    }
                            )
                        }
                    }
                }

                // Styled Quote Canvas Box (Scrollable for longer authentic Hadiths)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(posterBgColor.copy(alpha = 0.3f), posterBgColor.copy(alpha = 0.9f))
                            )
                        )
                        .border(BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f)), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "🕌",
                            fontSize = 22.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Text(
                            text = currentHadith.text,
                            color = LightWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 24.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "الراوي: ${currentHadith.narrator}",
                            color = GoldAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Surface(
                            color = DarkBackground.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "المصدر: ${currentHadith.source}",
                                color = LightWhite.copy(alpha = 0.9f),
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        AnimatedVisibility(visible = showExplanation) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkBackground.copy(alpha = 0.55f))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "الفائدة والشرح:",
                                    color = GoldAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentHadith.explanation,
                                    color = TextColorPrimary,
                                    fontSize = 11.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                // Swiper Actions + Explanation Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            viewModel.triggerHaptic()
                            currentIndex = if (safeIndex > 0) safeIndex - 1 else filteredHadiths.size - 1
                        },
                        modifier = Modifier.testTag("hadith_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "الحديث السابق",
                            tint = GoldAccent
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "الحديث ${safeIndex + 1} من ${filteredHadiths.size}",
                            color = TextColorPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = {
                                viewModel.triggerHaptic()
                                showExplanation = !showExplanation
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (showExplanation) "إخفاء الشرح" else "الشرح والفائدة",
                                color = EmeraldSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            viewModel.triggerHaptic()
                            currentIndex = if (safeIndex < filteredHadiths.size - 1) safeIndex + 1 else 0
                        },
                        modifier = Modifier.testTag("hadith_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "الحديث التالي",
                            tint = GoldAccent
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                viewModel.triggerHaptic()
                try {
                    val shareText = "✨ حديث شريف (${currentHadith.title}) ✨\n\n" +
                            "${currentHadith.text}\n\n" +
                            "• الراوي: ${currentHadith.narrator}\n" +
                            "• المصدر: ${currentHadith.source}\n\n" +
                            "الفائدة: ${currentHadith.explanation}\n\n" +
                            "تمت المشاركة من تطبيق إيمانيات 🕌"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "نشر الحديث الشريف"))
                    Toast.makeText(context, "تم تنسيق الحديث بالمصدر للمشاركة! 🌟", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("hadith_share_button")
        ) {
            Icon(imageVector = Icons.Default.Share, contentDescription = "مشاركة الحديث", tint = LightWhite)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "مشاركة الحديث مع الراوي والمصدر 🚀",
                color = LightWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
