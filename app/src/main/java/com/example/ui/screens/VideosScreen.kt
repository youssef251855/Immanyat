package com.example.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
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
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.ui.components.ElegantBackgroundPattern
import com.example.ui.components.IslamicOrnamentDivider
import com.example.ui.components.IslamicStarStarIcon
import com.example.ui.theme.*
import com.example.ui.viewmodel.EmaniatViewModel
import kotlinx.coroutines.delay

data class HistoryVideo(
    val id: String,
    val title: String,
    val description: String,
    val duration: String,
    val speaker: String,
    val videoUrl: String,
    val views: String,
    val gradientColors: List<Color>,
    val category: String = "badr"
)

fun loadVideosFromAssets(context: android.content.Context): List<HistoryVideo> {
    return try {
        val jsonString = context.assets.open("videos.json").bufferedReader().use { it.readText() }
        val jsonArray = org.json.JSONArray(jsonString)
        val list = mutableListOf<HistoryVideo>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val colorsArray = obj.getJSONArray("gradientColors")
            val colors = mutableListOf<Color>()
            for (j in 0 until colorsArray.length()) {
                colors.add(Color(android.graphics.Color.parseColor(colorsArray.getString(j))))
            }
            list.add(
                HistoryVideo(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    description = obj.getString("description"),
                    duration = obj.getString("duration"),
                    speaker = obj.getString("speaker"),
                    videoUrl = obj.getString("videoUrl"),
                    views = obj.getString("views"),
                    gradientColors = colors,
                    category = obj.optString("category", "badr")
                )
            )
        }
        list
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun VideosScreen(
    viewModel: EmaniatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("emaniat_prefs", android.content.Context.MODE_PRIVATE) }
    var currentStartId by remember { mutableStateOf(sharedPrefs.getString("startio_app_id", "") ?: "") }
    var showIdDialog by remember { mutableStateOf(false) }
    
    // START.IO APP ID CONFIGURATION DIALOG
    if (showIdDialog) {
        var tempIdText by remember { mutableStateOf(currentStartId) }
        AlertDialog(
            onDismissRequest = { showIdDialog = false },
            title = {
                Text(
                    text = "إعداد معرف إعلانات Start.io",
                    color = GoldAccent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "الرجاء إدخال معرّف تطبيق Start.io الخاص بك لتفعيل الإعلانات المخصصة وحفظ الأرباح:",
                        color = Color.White,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tempIdText,
                        onValueChange = { tempIdText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        ),
                        placeholder = {
                            Text(
                                "مثال: 200676644",
                                color = TextColorSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = CardBorder,
                            cursorColor = GoldAccent
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "* يتطلب تغيير المعرّف إعادة تشغيل التطبيق لتفعيله بالكامل.",
                        color = EmeraldMuted,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        sharedPrefs.edit().putString("startio_app_id", tempIdText.trim()).apply()
                        currentStartId = tempIdText.trim()
                        showIdDialog = false
                        android.widget.Toast.makeText(context, "تم حفظ معرف Start.io بنجاح! الرجاء إعادة تشغيل التطبيق لتطبيقه.", android.widget.Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldMuted)
                ) {
                    Text("حفظ المعرّف", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showIdDialog = false }) {
                    Text("إلغاء", color = Color.White)
                }
            },
            containerColor = SurfaceDarkGlass,
            shape = RoundedCornerShape(16.dp)
        )
    }

    var selectedCategory by remember { mutableStateOf(0) } // 0: غزوة بدر الكبرى, 1: غزوة أحد العظيمة
    
    // Video datasets loaded dynamically from videos.json or fallback list
    val allVideos = remember {
        val loaded = loadVideosFromAssets(context)
        if (loaded.isNotEmpty()) loaded else {
            listOf(
                HistoryVideo(
                    id = "badr_1",
                    title = "فيلم وثائقي: غزوة بدر الكبرى (يوم الفرقان)",
                    description = "سرد متكامل وشامل لأدق تفاصيل غزوة بدر الكبرى المباركة، بدءاً من اعتراض القافلة، شورى النبي صلى الله عليه وسلم، وخطط التحام الصفوف والمدد الإلهي العظيم للمؤمنين الصابرين.",
                    duration = "18:40",
                    speaker = "أثرياء السرد والتفسير الإيماني",
                    videoUrl = "https://gyepptdcymcwzotdgyrn.supabase.co/storage/v1/object/sign/Test/Badr.mp4?token=eyJraWQiOiJzdG9yYWdlLXVybC1zaWduaW5nLWtleV8xZjRiNmQzNC1iZDExLTQ4ZjAtYjc1OC1kMTNjMzgwYjBjYWMiLCJhbGciOiJIUzI1NiJ9.eyJ1cmwiOiJUZXN0L0JhZHIubXA0IiwiaWF0IjoxNzgwNDE3NDMzLCJleHAiOjE4MTE5NTM0MzN9.7Put-8ievXrow3AX0751Ex7a0_76-CUvTYDs5C1C6JU",
                    views = "٨٥ ألف مشاهدة • عبر شبكة البث",
                    gradientColors = listOf(Color(0xFF0F5A3E), Color(0xFF03221E)),
                    category = "badr"
                ),
                HistoryVideo(
                    id = "uhud_1",
                    title = "فيلم وثائقي: غزوة أحد العظيمة (العِبر والبلاء)",
                    description = "سرد تفصيلي لمجريات غزوة أحد العظيمة، خطة جبل الرماة الحربية العبقرية، الاستدراج العسكري العنيف بعد ترك الجبل، استشهاد سيد الشهداء حمزة رضي الله عنه، والدروس النبوية الإيمانية الخالدة للصحبة الكرام.",
                    duration = "20:05",
                    speaker = "أثرياء السرد والتفسير الإيماني",
                    videoUrl = "https://gyepptdcymcwzotdgyrn.supabase.co/storage/v1/object/sign/Test/auht.mp4?token=eyJraWQiOiJzdG9yYWdlLXVybC1zaWduaW5nLWtleV8xZjRiNmQzNC1iZDExLTQ4ZjAtYjc1OC1kMTNjMzgwYjBjYWMiLCJhbGciOiJIUzI1NiJ9.eyJ1cmwiOiJUZXN0L2F1aHQubXA0IiwiaWF0IjoxNzgwNDE3MTc0LCJleHAiOjE4MTE5NTMxNzR9._8t9pMhT93vkyaFGNmrT1O6PdNcMo9FLiRvrI46XVeU",
                    views = "٧٢ ألف مشاهدة • عبر شبكة البث",
                    gradientColors = listOf(Color(0xFFAD7813), Color(0xFF472D02)),
                    category = "uhud"
                )
            )
        }
    }

    val badrVideos = remember(allVideos) { allVideos.filter { it.category == "badr" } }
    val uhudVideos = remember(allVideos) { allVideos.filter { it.category == "uhud" } }

    val currentVideoList = if (selectedCategory == 0) badrVideos else uhudVideos
    var activeVideo by remember { mutableStateOf(currentVideoList.first()) }
    
    // Automatically switch active video when category changes
    LaunchedEffect(selectedCategory) {
        activeVideo = if (selectedCategory == 0) badrVideos.first() else uhudVideos.first()
    }

    // Interactive media player setup
    var isPlaying by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableStateOf(0f) }
    var runningTime by remember { mutableStateOf("00:00") }
    var isMuted by remember { mutableStateOf(false) }

    // ExoPlayer declaration
    var exoPlayerInstance by remember { mutableStateOf<ExoPlayer?>(null) }

    // Initialize/release ExoPlayer safely
    DisposableEffect(activeVideo) {
        // Automatically pause ongoing Quran recitation to avoid sound overlapping!
        viewModel.pauseQuranRecitation()

        isPlaying = false
        currentProgress = 0f
        runningTime = "00:00"

        val player = ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(activeVideo.videoUrl)
            setMediaItem(mediaItem)
            prepare()
            volume = if (isMuted) 0f else 1f
            
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            })
        }
        
        exoPlayerInstance = player

        onDispose {
            player.release()
            exoPlayerInstance = null
        }
    }

    // Progression loop for ExoPlayer
    LaunchedEffect(isPlaying, activeVideo) {
        while (isPlaying) {
            exoPlayerInstance?.let { player ->
                val duration = player.duration
                if (duration > 0) {
                    val pos = player.currentPosition
                    currentProgress = pos.toFloat() / duration.toFloat()
                    
                    val seconds = (pos / 1000) % 60
                    val minutes = (pos / (1000 * 60)) % 60
                    runningTime = String.format("%02d:%02d", minutes, seconds)
                }
            }
            delay(500)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        ElegantBackgroundPattern()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 8.dp)
        ) {
            
            // SCREEN TITLES
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { showIdDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "معرف Start.io",
                            tint = GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IslamicStarStarIcon(modifier = Modifier.size(16.dp), color = GoldAccent)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "المحراب المرئي والغزوات التاريخية",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IslamicStarStarIcon(modifier = Modifier.size(16.dp), color = GoldAccent)
                    }
                    
                    // Simple balance spacer matching IconButton's size
                    Spacer(modifier = Modifier.size(36.dp))
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "مشاهد وسرد إيماني لغزوتي بدر وأحد التاريخيتين مع تعلّم العِبر والجهاد",
                    color = TextColorSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(EmeraldMuted.copy(alpha = 0.2f))
                        .border(0.5.dp, EmeraldMuted.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(EmeraldMuted)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "جاري تفعيل وعرض قائمة الفيديوهات من ملف videos.json المرفوع ✅",
                        color = EmeraldMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // INNER CATEGORY TABS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDarkGlass)
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            ) {
                // TAB 1: غزوة بدر الكبرى
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedCategory = 0 }
                        .background(if (selectedCategory == 0) EmeraldMuted.copy(alpha = 0.3f) else Color.Transparent)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "غزوة بدر الكبرى ⚔️",
                        color = if (selectedCategory == 0) GoldAccent else TextColorSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // TAB 2: غزوة أحد العظيمة
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedCategory = 1 }
                        .background(if (selectedCategory == 1) EmeraldMuted.copy(alpha = 0.3f) else Color.Transparent)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "غزوة أحد العظيمة 🏹",
                        color = if (selectedCategory == 1) GoldAccent else TextColorSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // THE MAIN ACTIVE VIDEO PLAYER CONTAINER
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .shadow(12.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.4f)),
                colors = CardDefaults.cardColors(containerColor = SurfaceDarkGlass)
            ) {
                Column {
                    // Actual media output or fallback styling
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .background(Color.Black)
                    ) {
                        // Standard media3 PlayerView integration via AndroidView
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayerInstance
                                    useController = false // Use custom beautiful compose controllers below
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            },
                            update = { view: PlayerView ->
                                view.player = exoPlayerInstance
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Ambient Overlay styling when not playing / preview overlay
                        if (!isPlaying && currentProgress < 0.02f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = activeVideo.gradientColors
                                        ),
                                        alpha = 0.85f
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(GoldAccent.copy(alpha = 0.9f))
                                            .clickable {
                                                exoPlayerInstance?.play()
                                                isPlaying = true
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "تشغيل",
                                            tint = DarkBackground,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "اضغط لتشغيل المشهد المرئي",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = activeVideo.speaker,
                                        color = GoldAccent,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // TIMELINE PROGRESS BAR
                    Slider(
                        value = currentProgress,
                        onValueChange = { progress ->
                            currentProgress = progress
                            exoPlayerInstance?.let { player ->
                                val targetPos = (player.duration * progress).toLong()
                                player.seekTo(targetPos)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .padding(horizontal = 12.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = GoldAccent,
                            activeTrackColor = GoldAccent,
                            inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
                        )
                    )

                    // COMPOSE CONTROLLERS
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Time readout
                        Text(
                            text = "$runningTime / ${activeVideo.duration}",
                            color = TextColorSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Center controllers
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Mute button
                            IconButton(
                                onClick = {
                                    isMuted = !isMuted
                                    exoPlayerInstance?.volume = if (isMuted) 0f else 1f
                                }
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "كتم الصوت",
                                    tint = GoldAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Play/Pause Floating effect
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldMuted)
                                    .clickable {
                                        exoPlayerInstance?.let { player ->
                                            if (player.isPlaying) {
                                                player.pause()
                                            } else {
                                                player.play()
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "تشغيل وقوف",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Fullscreen icon (simulated preview or notify feedback)
                            IconButton(
                                onClick = {
                                    // Custom user feedback toast
                                    android.widget.Toast.makeText(context, "تم تفعيل ملء الشاشة التلقائي", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "كامل الشاشة",
                                    tint = GoldAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Quality / Views indicator
                        Text(
                            text = "1080p HD",
                            color = EmeraldMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .border(0.5.dp, EmeraldMuted, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    // ACTIVE VIDEO LABELS AND INFOS
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.2f))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = activeVideo.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "المتحدث: ${activeVideo.speaker}",
                                color = GoldAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = activeVideo.views,
                                color = TextColorSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = activeVideo.description,
                            color = TextColorSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // LIST OF OTHER SUB-SECTION EPISODES
            Text(
                text = "باقي حلقات القسم المختار:",
                color = GoldAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(currentVideoList) { video ->
                    val isCurrent = video.id == activeVideo.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeVideo = video }
                            .border(
                                width = if (isCurrent) 1.dp else 0.5.dp,
                                color = if (isCurrent) GoldAccent else CardBorder,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) EmeraldMuted.copy(alpha = 0.15f) else SurfaceDarkGlass
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Thumbnail Gradient representation
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = video.gradientColors
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCurrent && isPlaying) {
                                    // Custom playing visual animation
                                    Icon(
                                        imageVector = Icons.Default.Pause,
                                        tint = GoldAccent,
                                        contentDescription = "جاري التشغيل",
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        tint = Color.White,
                                        contentDescription = "اضغط للتشغيل",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .background(Color.Black.copy(alpha = 0.8f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = video.duration,
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))

                            // Details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = video.title,
                                    color = if (isCurrent) GoldAccent else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = video.description,
                                    color = TextColorSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = video.speaker,
                                        color = EmeraldMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = video.views,
                                        color = TextColorSecondary.copy(alpha = 0.7f),
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!viewModel.isVipPremiumActive) {
                // SPONSORED START.IO AD SECTION
                IslamicOrnamentDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .padding(vertical = 1.dp)
                )

                // Start.io Banner Ad Container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDarkGlass)
                        .border(BorderStroke(0.5.dp, CardBorder))
                        .padding(vertical = 4.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(GoldAccent)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "مساهمة ومساندة للتطبيق • إعلان برعاية Start.io",
                            color = GoldAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(GoldAccent)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
