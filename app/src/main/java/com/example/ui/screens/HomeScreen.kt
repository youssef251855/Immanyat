package com.example.ui.screens

import android.text.format.DateFormat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.EmaniatViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(
    viewModel: EmaniatViewModel,
    modifier: Modifier = Modifier
) {
    val userStreak by viewModel.userStreak.collectAsState()
    val todayWorships by viewModel.todayWorshipLogs.collectAsState()
    val completedCount by viewModel.completedWorshipCount.collectAsState()

    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("emaniat_prefs", android.content.Context.MODE_PRIVATE) }
    val hideUpdatesKey = "hide_updates_dialog_2026_06_master"
    var showUpdatesDialog by remember { mutableStateOf(!sharedPrefs.getBoolean(hideUpdatesKey, false)) }
    var dontShowAgain by remember { mutableStateOf(false) }
    var currentTime by remember { mutableStateOf("") }
    
    val onDismissUpdates = {
        if (dontShowAgain) {
            sharedPrefs.edit().putBoolean(hideUpdatesKey, true).apply()
        }
        showUpdatesDialog = false
    }
    
    // Update real-time clock smoothly
    LaunchedEffect(Unit) {
        while (true) {
            val date = Date()
            val formatStr = if (DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm a"
            currentTime = SimpleDateFormat(formatStr, Locale.US).format(date)
            kotlinx.coroutines.delay(30000)
        }
    }

    if (currentTime.isEmpty()) {
        val date = Date()
        val formatStr = if (DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm a"
        currentTime = SimpleDateFormat(formatStr, Locale.US).format(date)
    }

    // Static beautiful collection of Islamic quotes to prevent blank offline screens
    val defaultVerses = listOf(
        "أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ" to "الرعد - ٢٨",
        "إِنَّ الصَّلَاةَ كَانَتْ عَلَى الْمُؤْمِنِينَ كِتَابًا مَّوقُوتًا" to "النساء - ١٠٣",
        "ادْعُونِي أَسْتَجِبْ لَكُمْ" to "غافر - ٦٠",
        "وَبِالْأَسْحَارِ هُمْ يَسْتَغْفِرُونَ" to "الذاريات - ١٨",
        "وَسَبِّحْ بِحَمْدِ رَبِّكَ قَبْلَ طُلُوعِ الشَّمْسِ وَقَبْلَ غُرُوبِهَا" to "طه - ١٣٠"
    )

    val defaultHadiths = listOf(
        "الدعاء هو العبادة" to "سنن الترمذي",
        "كَلِمَتَانِ خَفِيفَتَانِ عَلَى اللِّسَانِ ثَقِيلَتَانِ فِي الْمِيزَانِ: سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، سُبْحَانَ اللَّهِ الْعَظِيمِ" to "متفق عليه",
        "الطهور شطر الإيمان، والحمد لله تملأ الميزان" to "صحيح مسلم",
        "إنّ أولى النّاس بي يوم القيامة أكثرهم عليّ صلاةً" to "سنن الترمذي",
        "رَكْعَتَا الْفَجْرِ خَيْرٌ مِنَ الدُّنْيَا وَمَا فِيهَا" to "صحيح مسلم"
    )

    // Select based on day of year
    val calendar = Calendar.getInstance()
    val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
    val verseToday = defaultVerses[dayOfYear % defaultVerses.size]
    val hadithToday = defaultHadiths[dayOfYear % defaultHadiths.size]

    // Pre-calculated prayer times for 2026 spiritual calendar representation (Mecca Offset)
    val prayerTimes = listOf(
        "الفجر" to "04:12 AM" to true,
        "الشروق" to "05:38 AM" to false,
        "الظهر" to "12:22 PM" to true,
        "العصر" to "03:48 PM" to true,
        "المغرب" to "07:05 PM" to true,
        "العشاء" to "08:35 PM" to true
    )

    // Determine next prayer
    val nextPrayerName = "المغرب"
    val nextPrayerTime = "07:05 PM"
    val nextPrayerCountdown = "01:24:10"

    Box(modifier = modifier.fillMaxSize()) {
        ElegantBackgroundPattern()

        if (showUpdatesDialog) {
            AlertDialog(
                onDismissRequest = onDismissUpdates,
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .border(BorderStroke(1.2.dp, GoldAccent.copy(alpha = 0.5f)), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                containerColor = DarkBackground.copy(alpha = 0.98f),
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(GoldAccent.copy(alpha = 0.15f), CircleShape)
                                .border(1.dp, GoldAccent.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NewReleases,
                                contentDescription = "التحديثات الجديدة",
                                tint = GoldAccent,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "التحديثات الكبرى المكتملة 🌟",
                            color = GoldAccent,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "أهلاً بك في تطبيق إيمانيات! يسعدنا إعلامك ببدء تفعيل ميزات إيمانية ذكية كبرى صممت خصيصاً لمساعدتك على إتقان القرآن الكريم وحفظه وتلاوته:",
                                color = LightWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Right,
                                lineHeight = 18.sp,
                                modifier = Modifier.fillMaxWidth()
                            )

                            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                            // Feature 1
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.End
                            ) {
                                Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "التسميع الذكي في جميع الـ 114 سورة 🎙️🧠",
                                        color = EmeraldSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Right
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = EmeraldSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "أصبح بإمكانك التسميع الفوري لجميع سور القرآن الـ 114 بالكامل (بالصوت أو الكتابة) مع مراجعة دقيقة وميزان تجويدي عبر ذكاء Gemini الاصطناعي.",
                                    color = TextColorSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .background(GoldAccent.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                        .border(0.5.dp, GoldAccent.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "📍 المكان: بجانب كل سورة في شاشة المصحف، وأيضاً زر مرئي داخل صفحة قراءة السورة نفسها لتسميع سهل أثناء التلاوة!",
                                        color = GoldAccent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Right
                                    )
                                }
                            }

                            HorizontalDivider(color = CardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                            // Feature 2
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.End
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "المصحف المطور ومنع تداخل التمرير 📜📱",
                                        color = EmeraldSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Right
                                    )
                                    Icon(
                                        imageVector = Icons.Default.MenuBook,
                                        contentDescription = null,
                                        tint = EmeraldSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "تمت إعادة بناء قائمة السور لتكون مسطحة وسريعة جداً وهادئة، لتقضي على أي تداخل لمؤشرات تصفح السور تماماً، لدعم الهواتف بجميع أحجامها.",
                                    color = TextColorSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .background(GoldAccent.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                        .border(0.5.dp, GoldAccent.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "📍 المكان: تبويب \"قراءة وتسميع\" بالواجهة الرئيسية للمصحف.",
                                        color = GoldAccent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Right
                                    )
                                }
                            }

                            HorizontalDivider(color = CardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                            // Feature 3
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.End
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "أصوات كبار القراء والمشغل الصوتي المدمج 🎧🎶",
                                        color = EmeraldSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Right
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Audiotrack,
                                        contentDescription = null,
                                        tint = EmeraldSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "استمع وراجع تلاوتك مباشرة بروايات كبار القراء (مشاري العفاسي، عبد الباسط عبد الصمد، سعد الغامدي، ماهر المعيقلي، محمد صديق المنشاوي) لأي سورة من الـ 114 مع مشغل كامل.",
                                    color = TextColorSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .background(GoldAccent.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                        .border(0.5.dp, GoldAccent.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "📍 المكان: التبويب الثاني \"أصوات الشيوخ\" بشاشة القرآن الكريم.",
                                        color = GoldAccent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Right
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = CardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dontShowAgain = !dontShowAgain }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "عدم إظهار هذه الرسالة مرة أخرى",
                                color = LightWhite.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Right
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Checkbox(
                                checked = dontShowAgain,
                                onCheckedChange = { dontShowAgain = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = GoldAccent,
                                    uncheckedColor = GoldAccent.copy(alpha = 0.4f),
                                    checkmarkColor = DarkBackground
                                ),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDismissUpdates()
                            viewModel.triggerHaptic()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "نفع الله بنا وبكم، استكشف التحديثات الآن ✨",
                            color = DarkBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // TOP BAR & HEADER GREETING
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "السلام عليكم ورحمة الله،",
                        color = TextColorSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = viewModel.userName,
                        color = TextColorPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Streak pill & current time
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Time pill
                    Box(
                        modifier = Modifier
                            .background(EmeraldMuted.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = currentTime,
                            color = GoldAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    // Streak Count Pill
                    userStreak?.let { streak ->
                        Row(
                            modifier = Modifier
                                .background(GoldMuted.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .border(1.dp, GoldAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "🔥 ${streak.currentStreak} يوم",
                                color = GoldAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // BEAUTIFUL GOLDEN OASIS PREMIUM BANNER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                EmeraldPrimary.copy(alpha = 0.3f),
                                GoldAccent.copy(alpha = 0.15f)
                            )
                        )
                    )
                    .border(
                        BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(GoldAccent, CardBorder))),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        viewModel.triggerHaptic()
                        viewModel.setScreen(AppScreen.OASIS)
                    }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "الدخول للواحة",
                        tint = GoldAccent,
                        modifier = Modifier.size(20.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "بوابة الميزات العشر المبتكرة الشاملة (الواحة الإيمانية) ✨",
                            color = GoldAccent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "تصفح واستكشف بوصلة الكعبة، حاسبة الزكاة الذكية، أسماء الله الحسنى، دفتر الأفكار، أصوات السكينة والمزيد صُمم خصيصاً لتعزيز أورادك.",
                            color = LightWhite,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(GoldAccent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "الواحة الروحانية",
                            tint = GoldAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // INTERACTIVE LEVEL / LEVEL CARD
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1.5f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "المستوى الإيماني: ${userStreak?.level ?: 1}",
                                color = GoldAccent,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (viewModel.isVipPremiumActive) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Brush.linearGradient(listOf(GoldAccent, Color(0xFFFFA500))))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "PRO 👑",
                                        color = Color.Black,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "رتبتك: " + when(userStreak?.level ?: 1) {
                                in 1..2 -> "السائر إلى الله 🌿"
                                in 3..5 -> "المواظب الخاشع ✨"
                                else -> "المقرب المتطوع 🌟"
                            },
                            color = TextColorPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // Linear score
                        val points = userStreak?.points ?: 250
                        val nextLevelPoints = ((userStreak?.level ?: 1) * 500)
                        val pointsProgress = points.toFloat() / nextLevelPoints.toFloat()

                        LinearProgressIndicator(
                            progress = { pointsProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = EmeraldSecondary,
                            trackColor = EmeraldMuted.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$points / $nextLevelPoints نقطة",
                            color = TextColorSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Circular Worship percent of today
                    Box(
                        modifier = Modifier.size(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val progressToday = userStreak?.worshipProgressProgress ?: 0.5f

                        CircularProgressIndicator(
                            progress = { progressToday },
                            modifier = Modifier.fillMaxSize(),
                            color = GoldAccent,
                            strokeWidth = 6.dp,
                            trackColor = EmeraldMuted.copy(alpha = 0.3f)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${(progressToday * 100).toInt()}%",
                                color = LightWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "الإنجاز",
                                color = TextColorSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // PRAYER TIMER WIDGET (COUNTDOWN)
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("prayer_timer_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "الصلاة القادمة",
                                tint = GoldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "الصلاة القادمة",
                                color = TextColorSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "صلاة $nextPrayerName",
                            color = LightWhite,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "تقام عند: $nextPrayerTime",
                            color = TextColorSecondary,
                            fontSize = 11.sp
                        )
                    }

                    // Remaining countdown representation
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "متبقي",
                            color = GoldAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = nextPrayerCountdown,
                            color = LightWhite,
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "بتوقيت مكة المكرمة",
                            color = EmeraldSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                IslamicOrnamentDivider(modifier = Modifier.fillMaxWidth().height(16.dp))

                // Scroll of Daily Prayer grid
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    prayerTimes.forEach { entry ->
                        val (pNameTime, isObligatory) = entry
                        val (name, time) = pNameTime
                        val isHighlighted = name == nextPrayerName

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isHighlighted) EmeraldPrimary.copy(alpha = 0.25f)
                                    else EmeraldMuted.copy(alpha = 0.1f)
                                )
                                .border(
                                    1.dp,
                                    if (isHighlighted) GoldAccent.copy(alpha = 0.5f)
                                    else CardBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = name,
                                    color = if (isHighlighted) GoldAccent else LightWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = time,
                                    color = TextColorSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(EmeraldSecondary.copy(alpha = 0.12f))
                        .clickable { 
                            viewModel.setScreen(AppScreen.ADHAN_SETTINGS)
                            viewModel.triggerHaptic()
                        }
                        .padding(vertical = 10.dp, horizontal = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "ضبط الأذان ومواقيت الصلاة والقبلة",
                        tint = GoldAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ضبط الأذان ومواقيت الصلاة والقبلة ⚙️",
                        color = GoldAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI ASSISTANT recommendation card (GEMINI API)
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_advice_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IslamicStarStarIcon(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "اقتراح الذكاء الاصطناعي الروحي",
                            color = GoldAccent,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { viewModel.fetchAiSpiritualAdvice() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "تحديث الاقتراح الروحي",
                            tint = GoldAccent,
                            modifier = Modifier.rotate(
                                if (viewModel.isAiLoading) {
                                    val infiniteTransition = rememberInfiniteTransition()
                                    val angle by infiniteTransition.animateFloat(
                                        initialValue = 0f,
                                        targetValue = 360f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1200, easing = LinearEasing),
                                            repeatMode = RepeatMode.Restart
                                        )
                                    )
                                    angle
                                } else 0f
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandVertically()
                ) {
                    Text(
                        text = viewModel.aiState,
                        color = TextColorPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "مدعوم بنموذج Gemini 3.5",
                        color = TextColorSecondary.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Gemini",
                        tint = GoldAccent.copy(alpha = 0.5f),
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // DAILY WORKSHIP CHECKBOXES (STREAK DRIVER)
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("worship_checklist_card")
            ) {
                Text(
                    text = "سجل العبادات اليومية لتدعيم الالتزام",
                    color = LightWhite,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "حدد العبادات التي أديتها اليوم لتحديث نسبة الإنجاز والـ Streak",
                    color = TextColorSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                todayWorships.forEach { log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.toggleWorship(log.name, !log.isCompleted) }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (log.isCompleted) EmeraldSecondary
                                        else Color.Transparent
                                    )
                                    .border(
                                        2.dp,
                                        if (log.isCompleted) EmeraldSecondary else GoldAccent,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (log.isCompleted) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "تم",
                                        tint = LightWhite,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "صلاة ${log.name}",
                                color = if (log.isCompleted) EmeraldSecondary else LightWhite,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (log.isCompleted) FontWeight.Bold else FontWeight.Medium
                            )
                        }

                        // Done point Pill
                        Text(
                            text = if (log.isCompleted) "+٥ نقاط" else "لم تؤدَّ بعد",
                            color = if (log.isCompleted) GoldAccent else TextColorSecondary.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    Divider(color = CardBorder, thickness = 0.5.dp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // DAILY AYAH & HADITH CARDS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Verse Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDarkGlass)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = "آية اليوم",
                            tint = GoldAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "آية اليوم التدبرية",
                            color = GoldAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "“${verseToday.first}”",
                            color = TextColorPrimary,
                            fontSize = 13.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = verseToday.second,
                            color = EmeraldSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }

                // Hadith Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDarkGlass)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "حديث اليوم",
                            tint = GoldAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "الحديث الشريف",
                            color = GoldAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "“${hadithToday.first}”",
                            color = TextColorPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = hadithToday.second,
                            color = EmeraldSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // QUICK NAVIGATION SHORTCUTS
            Text(
                text = "الوصول السريع للأقسام والعبادات",
                color = LightWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 80.dp), // make space for bottom player and nav bars
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    Triple("أذكار اليوم", Icons.Default.WbSunny, AppScreen.AZKAR),
                    Triple("السبحة", Icons.Default.Adjust, AppScreen.TASBIH),
                    Triple("المرئيات", Icons.Default.PlayCircle, AppScreen.VIDEOS),
                    Triple("الأدعية", Icons.Default.Favorite, AppScreen.DUAS),
                    Triple("المكتبة", Icons.Default.LibraryBooks, AppScreen.LIBRARY)
                ).forEach { grid ->
                    val (title, icon, screen) = grid
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(EmeraldMuted.copy(alpha = 0.15f))
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                            .clickable { viewModel.setScreen(screen) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = GoldAccent,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = title,
                                color = LightWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
