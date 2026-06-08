package com.example.ui.screens

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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import com.example.api.QuranApiClient
import com.example.ui.components.ElegantBackgroundPattern
import com.example.ui.components.GlassCard
import com.example.ui.components.IslamicOrnamentDivider
import com.example.ui.components.IslamicStarStarIcon
import com.example.ui.theme.*
import com.example.ui.viewmodel.EmaniatViewModel

data class Surah(
    val id: Int,
    val arabicName: String,
    val englishName: String,
    val totalAyat: Int,
    val type: String // مكية / مدنية
)

// Helper to convert indices to beautiful Arabic numeral string
fun getArabicNumber(number: Int): String {
    val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    val builder = StringBuilder()
    var n = number
    if (n == 0) return "٠"
    while (n > 0) {
        val digit = n % 10
        builder.append(arabicDigits[digit])
        n /= 10
    }
    return builder.reverse().toString()
}

fun loadQuranFromAssets(context: android.content.Context): Map<Int, List<String>> {
    return try {
        val jsonString = context.assets.open("quran.json").bufferedReader().use { it.readText() }
        val obj = org.json.JSONObject(jsonString)
        val surahsArray = obj.getJSONArray("surahs")
        val map = mutableMapOf<Int, List<String>>()
        for (i in 0 until surahsArray.length()) {
            val surahObj = surahsArray.getJSONObject(i)
            val id = surahObj.getInt("id")
            val versesArray = surahObj.getJSONArray("verses")
            val verses = mutableListOf<String>()
            for (j in 0 until versesArray.length()) {
                verses.add(versesArray.getString(j))
            }
            map[id] = verses
        }
        map
    } catch (e: Exception) {
        e.printStackTrace()
        emptyMap()
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun QuranScreen(
    viewModel: EmaniatViewModel,
    modifier: Modifier = Modifier
) {
    val currentBookmark by viewModel.currentBookmark.collectAsState()
    val context = LocalContext.current
    val localQuranJson = remember { loadQuranFromAssets(context) }

    val sheikhs = remember {
        listOf(
            SheikhReciter("afs", "مشاري العفاسي", "عذب الصوت والترتيل", "ع"),
            SheikhReciter("basit", "عبد الباسط عبد الصمد", "صاحب الحنجرة الذهبية", "ب"),
            SheikhReciter("ghamdi", "سعد الغامدي", "هدوء وطمأنينة النفس", "غ"),
            SheikhReciter("maher", "ماهر المعيقلي", "إمام الحرم المكي الشريف", "م"),
            SheikhReciter("minsh", "محمد صديق المنشاوي", "الترتيل الخاشع الباكي", "ش")
        )
    }

    var searchInput by remember { mutableStateOf("") }
    var selectedSurahForReading by remember { mutableStateOf<Surah?>(null) }
    
    // Sub-tab Navigation and AI evaluation state variables
    var activeSubTab by remember { mutableStateOf(0) } // 0 = القراءة والتسميع, 1 = المستمع الصوتي والشيوخ
    var showUpdatesBanner by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    var showMemorizationDialog by remember { mutableStateOf(false) }
    var selectedSurahForMemorization by remember { mutableStateOf<Surah?>(null) }
    var recitationText by remember { mutableStateOf("") }
    var isEvaluating by remember { mutableStateOf(false) }
    var evaluationResultScore by remember { mutableStateOf<Double?>(null) }
    var evaluationResultStatus by remember { mutableStateOf<String?>(null) }
    var evaluationResultFeedback by remember { mutableStateOf<String?>(null) }
    
    // Voice Recitation (التسميع الصوتي) states
    var memorizationTab by remember { mutableStateOf(0) } // 0 = تسميع صوتي 🎙️, 1 = تسميع كتابي ✍️
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0) }
    var audioFile by remember { mutableStateOf<java.io.File?>(null) }
    var isPlayingRecord by remember { mutableStateOf(false) }
    var recorderPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var mediaRecorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    var permissionError by remember { mutableStateOf<String?>(null) }

    fun formatDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }

    val startAudioRecording = {
        try {
            permissionError = null
            val file = java.io.File(context.cacheDir, "recitation_${System.currentTimeMillis()}.m4a")
            audioFile = file
            
            val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.media.MediaRecorder(context)
            } else {
                android.media.MediaRecorder()
            }
            
            recorder.apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            isRecording = true
            recordingDuration = 0
            viewModel.triggerHaptic()
        } catch (e: Exception) {
            e.printStackTrace()
            permissionError = "فشل بدء التسجيل الميكروفون. يرجى التحقق من الأذونات وإعادة المحاولة."
        }
    }

    val stopAudioRecording = {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            audioFile = null
        } finally {
            mediaRecorder = null
            isRecording = false
            viewModel.triggerHaptic()
        }
    }

    val playRecordedVoice = {
        val file = audioFile
        if (file != null && file.exists()) {
            try {
                val player = android.media.MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        isPlayingRecord = false
                        it.release()
                        recorderPlayer = null
                    }
                }
                recorderPlayer = player
                isPlayingRecord = true
                viewModel.triggerHaptic()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val stopPlayingRecordedVoice = {
        try {
            recorderPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            recorderPlayer = null
            isPlayingRecord = false
            viewModel.triggerHaptic()
        }
    }

    val recordLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startAudioRecording()
        } else {
            permissionError = "تم رفض إذن تسجيل الصوت. يرجى منح إذن الميكروفون من إعدادات النظام للتسميع الصوتي."
        }
    }

    val handleRecordClick = {
        val permissionCheck = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        )
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            if (isRecording) {
                stopAudioRecording()
            } else {
                startAudioRecording()
            }
        } else {
            recordLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                kotlinx.coroutines.delay(1000L)
                recordingDuration++
            }
        }
    }
    
    // Dynamic text size font factor
    var textSizeFactor by remember { mutableStateOf(22f) }

    // Verses fetching state
    var activeSurahVerses by remember { mutableStateOf<List<String>?>(null) }
    var isLoadingVerses by remember { mutableStateOf(false) }
    var isErrorLoading by remember { mutableStateOf(false) }

    // Static collection of all 114 Quran Surahs
    val surahs = remember {
        listOf(
            Surah(1, "الفاتحة", "Al-Fatihah", 7, "مكية"),
            Surah(2, "البقرة", "Al-Baqarah", 286, "مدنية"),
            Surah(3, "آل عمران", "Ali 'Imran", 200, "مدنية"),
            Surah(4, "النساء", "An-Nisa", 176, "مدنية"),
            Surah(5, "المائدة", "Al-Ma'idah", 120, "مدنية"),
            Surah(6, "الأنعام", "Al-An'am", 165, "مكية"),
            Surah(7, "الأعراف", "Al-A'raf", 206, "مكية"),
            Surah(8, "الأنفال", "Al-Anfal", 75, "مدنية"),
            Surah(9, "التوبة", "At-Tawbah", 129, "مدنية"),
            Surah(10, "يونس", "Yunus", 109, "مكية"),
            Surah(11, "هود", "Hud", 123, "مكية"),
            Surah(12, "يوسف", "Yusuf", 111, "مكية"),
            Surah(13, "الرعد", "Ar-Ra'd", 43, "مدنية"),
            Surah(14, "إبراهيم", "Ibrahim", 52, "مكية"),
            Surah(15, "الحجر", "Al-Hijr", 99, "مكية"),
            Surah(16, "النحل", "An-Nahl", 128, "مكية"),
            Surah(17, "الإسراء", "Al-Isra", 111, "مكية"),
            Surah(18, "الكهف", "Al-Kahf", 110, "مكية"),
            Surah(19, "مريم", "Maryam", 98, "مكية"),
            Surah(20, "طه", "Taha", 135, "مكية"),
            Surah(21, "الأنبياء", "Al-Anbiya", 112, "مكية"),
            Surah(22, "الحج", "Al-Hajj", 78, "مدنية"),
            Surah(23, "المؤمنون", "Al-Mu'minun", 118, "مكية"),
            Surah(24, "النور", "Al-Nur", 64, "مدنية"),
            Surah(25, "الفرقان", "Al-Furqan", 77, "مكية"),
            Surah(26, "الشعراء", "Ash-Shu'ara", 227, "مكية"),
            Surah(27, "النمل", "An-Naml", 93, "مكية"),
            Surah(28, "القصص", "Al-Qasas", 88, "مكية"),
            Surah(29, "العنكبوت", "Al-Ankabut", 69, "مكية"),
            Surah(30, "الروم", "Ar-Rum", 60, "مكية"),
            Surah(31, "لقمان", "Luqman", 34, "مكية"),
            Surah(32, "السجدة", "As-Sajdah", 30, "مكية"),
            Surah(33, "الأحزاب", "Al-Ahzab", 73, "مدنية"),
            Surah(34, "سبأ", "Saba", 54, "مكية"),
            Surah(35, "فاطر", "Fatir", 45, "مكية"),
            Surah(36, "يس", "Ya-Sin", 83, "مكية"),
            Surah(37, "الصافات", "As-Saffat", 182, "مكية"),
            Surah(38, "ص", "Sad", 88, "مكية"),
            Surah(39, "الزمر", "Az-Zumar", 75, "مكية"),
            Surah(40, "غافر", "Ghafir", 85, "مكية"),
            Surah(41, "فصلت", "Fussilat", 54, "مكية"),
            Surah(42, "الشورى", "Ash-Shura", 53, "مكية"),
            Surah(43, "الزخرف", "Az-Zukhruf", 89, "مكية"),
            Surah(44, "الدخان", "Ad-Dukhan", 59, "مكية"),
            Surah(45, "الجاثية", "Al-Jathiyah", 37, "مكية"),
            Surah(46, "الأحقاف", "Al-Ahqaf", 35, "مكية"),
            Surah(47, "محمد", "Muhammad", 38, "مدنية"),
            Surah(48, "الفتح", "Al-Fath", 29, "مدنية"),
            Surah(49, "الحجرات", "Al-Hujurat", 18, "مدنية"),
            Surah(50, "ق", "Qaf", 45, "مكية"),
            Surah(51, "الذاريات", "Adh-Dhariyat", 60, "مكية"),
            Surah(52, "الطور", "At-Tur", 49, "مكية"),
            Surah(53, "النجم", "An-Najm", 62, "مكية"),
            Surah(54, "القمر", "Al-Qamar", 55, "مكية"),
            Surah(55, "الرحمن", "Ar-Rahman", 78, "مدنية"),
            Surah(56, "الواقعة", "Al-Waqi'ah", 96, "مكية"),
            Surah(57, "الحديد", "Al-Hadid", 29, "مدنية"),
            Surah(58, "المجادلة", "Al-Mujadilah", 22, "مدنية"),
            Surah(59, "الحشر", "Al-Hashr", 24, "مدنية"),
            Surah(60, "الممتحنة", "Al-Mumtahanah", 13, "مدنية"),
            Surah(61, "الصف", "As-Saff", 14, "مدنية"),
            Surah(62, "الجمعة", "Al-Jumu'ah", 11, "مدنية"),
            Surah(63, "المنافقون", "Al-Munafiqun", 11, "مدنية"),
            Surah(64, "التغابن", "At-Taghabun", 18, "مدنية"),
            Surah(65, "الطلاق", "At-Talaq", 12, "مدنية"),
            Surah(66, "التحريم", "At-Tahrim", 12, "مدنية"),
            Surah(67, "الملك", "Al-Mulk", 30, "مكية"),
            Surah(68, "القلم", "Al-Qalam", 52, "مكية"),
            Surah(69, "الحاقة", "Al-Haqqah", 52, "مكية"),
            Surah(70, "المعارج", "Al-Ma'arij", 44, "مكية"),
            Surah(71, "نوح", "Nuh", 28, "مكية"),
            Surah(72, "الجن", "Al-Jinn", 28, "مكية"),
            Surah(73, "المزمل", "Al-Muzzammil", 20, "مكية"),
            Surah(74, "المدثر", "Al-Muddaththir", 56, "مكية"),
            Surah(75, "القيامة", "Al-Qiyamah", 40, "مكية"),
            Surah(76, "الإنسان", "Al-Insan", 31, "مدنية"),
            Surah(77, "المرسلات", "Al-Mursalat", 50, "مكية"),
            Surah(78, "النبأ", "An-Naba", 40, "مكية"),
            Surah(79, "النازعات", "An-Nazi'at", 46, "مكية"),
            Surah(80, "عبس", "Abasa", 42, "مكية"),
            Surah(81, "التكوير", "At-Takwir", 29, "مكية"),
            Surah(82, "الانفطار", "Al-Infitar", 19, "مكية"),
            Surah(83, "المطففين", "Al-Mutaffifin", 36, "مكية"),
            Surah(84, "الانشقاق", "Al-Inshiqaq", 25, "مكية"),
            Surah(85, "البروج", "Al-Buruj", 22, "مكية"),
            Surah(86, "الطارق", "At-Tariq", 17, "مكية"),
            Surah(87, "الأعلى", "Al-A'la", 19, "مكية"),
            Surah(88, "الغاشية", "Al-Ghashiyah", 26, "مكية"),
            Surah(89, "الفجر", "Al-Fajr", 30, "مكية"),
            Surah(90, "البلد", "Al-Balad", 20, "مكية"),
            Surah(91, "الشمس", "Ash-Shams", 15, "مكية"),
            Surah(92, "الليل", "Al-Layl", 21, "مكية"),
            Surah(93, "الضحى", "Ad-Duha", 11, "مكية"),
            Surah(94, "الشرح", "Ash-Sharh", 8, "مكية"),
            Surah(95, "التين", "At-Tin", 8, "مكية"),
            Surah(96, "العلق", "Al-Alaq", 19, "مكية"),
            Surah(97, "القدر", "Al-Qadr", 5, "مكية"),
            Surah(98, "البينة", "Al-Bayyinah", 8, "مدنية"),
            Surah(99, "الزلزلة", "Az-Zalzalah", 8, "مدنية"),
            Surah(100, "العاديات", "Al-Adiyat", 11, "مكية"),
            Surah(101, "القارعة", "Al-Qari'ah", 11, "مكية"),
            Surah(102, "التكاثر", "At-Takatur", 8, "مكية"),
            Surah(103, "العصر", "Al-Asr", 3, "مكية"),
            Surah(104, "الهمزة", "Al-Humazah", 9, "مكية"),
            Surah(105, "الفيل", "Al-Fil", 5, "مكية"),
            Surah(106, "قريش", "Quraysh", 4, "مكية"),
            Surah(107, "الماعون", "Al-Ma'un", 7, "مكية"),
            Surah(108, "الكوثر", "Al-Kawthar", 3, "مكية"),
            Surah(109, "الكافرون", "Al-Kafirun", 6, "مكية"),
            Surah(110, "النصر", "An-Nasr", 3, "مدنية"),
            Surah(111, "المسد", "Al-Masad", 5, "مكية"),
            Surah(112, "الإخلاص", "Al-Ikhlas", 4, "مكية"),
            Surah(113, "الفلق", "Al-Falaq", 5, "مكية"),
            Surah(114, "الناس", "An-Nas", 6, "مكية")
        )
    }

    // Filter surahs based on query
    val filteredSurahs = surahs.filter {
        it.arabicName.contains(searchInput) ||
                it.englishName.lowercase().contains(searchInput.lowercase()) ||
                it.id.toString() == searchInput
    }

    // Comprehensive mock/fallback verses for popular Surahs if fully offline
    val mockVerses = remember {
        mapOf(
            1 to listOf(
                "بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ",
                "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ",
                "الرَّحْمَنِ الرَّحِيمِ",
                "مَالِكِ يَوْمِ الدِّينِ",
                "إِيَّاكُ نَعْبُدُ وَإِيَّاكُ نَسْتَعِينُ",
                "اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ",
                "صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ"
            ),
            18 to listOf(
                "الْحَمْدُ لِلَّهِ الَّذِي أَنْزَلَ عَلَى عَبْدِهِ الْكِتَابَ وَلَمْ يَجْعَلْ لَهُ عِوَجًا",
                "قَيِّمًا لِيُنْذِرَ بَأْسًا شَدِيدًا مِنْ لَدُنْهُ وَيُبَشِّرَ الْمُؤْمِنِينَ الَّذِينَ يَعْمَلُونَ الصَّالِحَاتِ أَنَّ لَهُمْ أَجْرًا حَسَنًا",
                "مَاكِثِينَ فِيهِ أَبَدًا",
                "وَيُنْذِرَ الَّذِينَ قَالُوا اتَّخَذَ اللَّهُ وَلَدًا",
                "مَا لَهُمْ بِهِ مِنْ عِلْمٍ وَلَا لِآبَائِهِمْ كَبُرَتْ كَلِمَةً تَخْرُجُ مِنْ أَفْوَاهِمْ إِنْ يَقُولُونَ إِلَّا كَذِبًا"
            ),
            108 to listOf(
                "بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ",
                "إِنَّا أَعْطَيْنَاكَ الْكَوْثَرَ",
                "فَصَلِّ لِرَبِّكَ وَانْحَرْ",
                "إِنَّ شَانِئَكَ هُوَ الْأَبْتَرُ"
            ),
            112 to listOf(
                "بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ",
                "قُل * هُوَ اللَّهُ أَحَدٌ",
                "اللَّهُ الصَّمَدُ",
                "لَمْ يَلِدْ وَلَمْ يُولَدْ",
                "وَلَمْ يَكُنْ لَهُ كُفُوًا أَحَدٌ"
            ),
            113 to listOf(
                "بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ",
                "قُل * أَعُوذُ بِرَبِّ الْفَلَقِ",
                "مِنْ شَرِّ مَا خَلَقَ",
                "وَمِنْ شَرِّ غَاسِقٍ إِذَا وَقَبَ",
                "وَمِنْ شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ",
                "وَمِنْ شَرِّ حَاسِدٍ إِذَا حَسَدَ"
            ),
            114 to listOf(
                "بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ",
                "قُل * أَعُوذُ بِرَبِّ النَّاسِ",
                "مَلِكِ النَّاسِ",
                "إِلَهِ النَّاسِ",
                "مِنْ شَرِّ الْوَسْوَاسِ الْخَنَّاسِ",
                "الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ",
                "مِنَ الْجِنَّةِ وَالنَّاسِ"
            )
        )
    }

    // Effect to download verses dynamically when a Surah is clicked
    LaunchedEffect(selectedSurahForReading) {
        val activeSurah = selectedSurahForReading
        if (activeSurah != null) {
            isLoadingVerses = true
            isErrorLoading = false
            activeSurahVerses = null
            
            // 1. Try loading from the user's uploaded local JSON file first!
            val localVerses = localQuranJson[activeSurah.id]
            if (localVerses != null && localVerses.isNotEmpty()) {
                activeSurahVerses = localVerses
                isLoadingVerses = false
                return@LaunchedEffect
            }
            
            try {
                // Try fetching online from the API
                val fetched = QuranApiClient.fetchSurahVerses(activeSurah.id)
                if (fetched != null && fetched.isNotEmpty()) {
                    activeSurahVerses = fetched
                } else {
                    // Fail over to offline mock/fallback list
                    val fallback = mockVerses[activeSurah.id]
                    if (fallback != null) {
                        activeSurahVerses = fallback
                    } else {
                        isErrorLoading = true
                    }
                }
            } catch (e: Exception) {
                // Fail over to offline mock/fallback list
                val fallback = mockVerses[activeSurah.id]
                if (fallback != null) {
                    activeSurahVerses = fallback
                } else {
                    isErrorLoading = true
                }
            } finally {
                isLoadingVerses = false
            }
        }
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
                text = "القرآن الكريم للقراءة 📖",
                color = TextColorPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "قراءة وتدبر لجميع الـ ١١٤ سورة الكريمة مع تتبع الختمة وحفظ علامات المتابعة",
                color = TextColorSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            // READ STATE CHANGER (ANIMATE READING VS MAIN)
            AnimatedContent(
                targetState = selectedSurahForReading,
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { x -> -x }).togetherWith(fadeOut() + slideOutHorizontally { x -> x })
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "quran_screen_animation"
            ) { activeSurah ->
                if (activeSurah != null) {
                    // READING MODE WITH DETAILED COSMIC CARDS
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 80.dp)
                    ) {
                        // Header back button & details
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { selectedSurahForReading = null },
                                modifier = Modifier.testTag("back_to_surahs")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "عودة",
                                        tint = GoldAccent
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("رجوع للقائمة", color = GoldAccent, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Actions Row (Bookmarking & AI Memorization)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // AI Memorization Test inside reading screen
                                Button(
                                    onClick = {
                                        selectedSurahForMemorization = activeSurah
                                        showMemorizationDialog = true
                                        viewModel.triggerHaptic()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSecondary.copy(alpha = 0.2f)),
                                    border = BorderStroke(1.dp, EmeraldSecondary.copy(alpha = 0.5f)),
                                    modifier = Modifier.height(36.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "تسميع بالذكاء الاصطناعي",
                                            tint = EmeraldSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text("تسميع وحفظ 🧠", color = EmeraldSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Bookmarking status
                                IconButton(onClick = {
                                    viewModel.bookmarkQuran(activeSurah.arabicName, activeSurah.id, 1)
                                }) {
                                    val isBookmarked = currentBookmark?.surahNumber == activeSurah.id
                                    Icon(
                                        imageVector = Icons.Default.Bookmark,
                                        contentDescription = "حفظ علامة القراءة",
                                        tint = if (isBookmarked) GoldAccent else GoldAccent.copy(alpha = 0.35f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // SURAH HEADER TITLE
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                IslamicStarStarIcon(modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "سورة ${activeSurah.arabicName}",
                                    color = GoldAccent,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "الترتيب ${activeSurah.id} • آياتها ${activeSurah.totalAyat} • نزولها ${activeSurah.type}",
                                    color = TextColorSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                IslamicOrnamentDivider(modifier = Modifier.fillMaxWidth().height(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // FONT SIZE ADJUSTMENT CONTROLLER (HIGHLY POLISHED UX)
                        GlassCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "حجم الخط: ${textSizeFactor.toInt()}",
                                    color = GoldAccent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Slider(
                                    value = textSizeFactor,
                                    onValueChange = { textSizeFactor = it },
                                    valueRange = 16f..38f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = GoldAccent,
                                        activeTrackColor = GoldAccent,
                                        inactiveTrackColor = CardBorder.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp)
                                )
                                Text(
                                    text = "الخط",
                                    color = LightWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Loader, Error, or Verse render panel
                        if (isLoadingVerses) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = GoldAccent)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "جاري تحميل الآيات الكريمة... ☁️",
                                    color = LightWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (isErrorLoading && activeSurahVerses == null) {
                            // Empty state/Offline state for non-preloaded Surahs with option to complete manually
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WifiOff,
                                        contentDescription = "غير متصل",
                                        tint = MutedRed,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "تطلب هذه السورة اتصالاً بالإنترنت 📡",
                                        color = LightWhite,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "السور المسبقة التثبيت للاستخدام بدون إنترنت هي: الفاتحة، الكهف، يس، الملك، الكوثر، الإخلاص، الفلق، والناس.\nيرجى الاتصال بالشبكة لقراءة بقية السور بحجمها الكامل.",
                                        color = TextColorSecondary,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            viewModel.incrementKhatmaProgress()
                                            viewModel.bookmarkQuran(activeSurah.arabicName, activeSurah.id, 1)
                                            selectedSurahForReading = null
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldMuted),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("احتساب السورة مقروءة في الختمة 📝", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            // RENDERING VERSES
                            val versesList = activeSurahVerses ?: listOf(
                                "بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ",
                                "تلاوة وذكر مبارك بآيات الله البينات الحكيمة."
                            )

                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    val isFromLocalJson = localQuranJson.containsKey(activeSurah.id)
                                    if (isFromLocalJson) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 12.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(EmeraldMuted.copy(alpha = 0.15f))
                                                .border(0.5.dp, EmeraldMuted.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(EmeraldMuted)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "محمّل بالكامل من ملف القرآن المرفوع: quran.json 📖 ✅",
                                                color = EmeraldMuted,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }

                                    versesList.forEachIndexed { idx, ayah ->
                                        // Render single ayah with beautiful custom text configuration
                                        val ayahNumber = idx + 1
                                        val arabicBadge = getArabicNumber(ayahNumber)
                                        
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 14.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = ayah,
                                                color = TextColorPrimary,
                                                fontSize = textSizeFactor.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                lineHeight = (textSizeFactor * 1.6f).sp,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            
                                            Spacer(modifier = Modifier.height(8.dp))
                                            
                                            // Ayah Number Badge
                                            Box(
                                                modifier = Modifier
                                                    .background(GoldAccent.copy(alpha = 0.1f), CircleShape)
                                                    .border(1.dp, GoldAccent.copy(alpha = 0.4f), CircleShape)
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "﴿ $arabicBadge ﴾",
                                                    color = GoldAccent,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        HorizontalDivider(color = CardBorder.copy(alpha = 0.3f), thickness = 0.5.dp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // MARK AS COMPLETED IN SMART KHATMA TRACKER
                            Button(
                                onClick = {
                                    viewModel.incrementKhatmaProgress()
                                    viewModel.bookmarkQuran(activeSurah.arabicName, activeSurah.id, 1)
                                    selectedSurahForReading = null
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSecondary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = LightWhite)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("أتممت قراءة هذه السورة الكريمة بنجاح ✔️", color = LightWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                } else {
                    // SINGLE FLAT LAZYCOLUMN FOR HIGHEST COMPATIBILITY AND PRECISE SCROLLING
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 60.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header TabRow
                        item {
                            TabRow(
                                selectedTabIndex = activeSubTab,
                                containerColor = Color.Transparent,
                                contentColor = GoldAccent
                            ) {
                                Tab(
                                    selected = activeSubTab == 0,
                                    onClick = { 
                                        activeSubTab = 0 
                                        viewModel.triggerHaptic()
                                    },
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Text("قراءة وتسميع 📖", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                )
                                Tab(
                                    selected = activeSubTab == 1,
                                    onClick = { 
                                        activeSubTab = 1 
                                        viewModel.triggerHaptic()
                                    },
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Text("أصوات الشيوخ 🎙️", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                )
                            }
                        }

                        if (activeSubTab == 0) {
                            // TAB 0: ORIGINAL QURAN LIST WITH AI MEMORIZATION INTEGRATION
                            
                            // DISMISSIBLE UPDATES INFO CARD
                            if (showUpdatesBanner) {
                                item {
                                    GlassCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateContentSize(),
                                        borderColor = GoldAccent.copy(alpha = 0.4f)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Info,
                                                        contentDescription = "التحديثات الجديدة",
                                                        tint = GoldAccent,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(
                                                        text = "آخر التحديثات والمميزات الجديدة 🌟",
                                                        color = GoldAccent,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { 
                                                        showUpdatesBanner = false 
                                                        viewModel.triggerHaptic()
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "إغلاق",
                                                        tint = MutedRed,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.height(10.dp))
                                            
                                            Text(
                                                text = "يسعدنا إعلامكم بإضافة ميزات إيمانية حيوية لتسهيل الحفظ والمراجعة:",
                                                color = LightWhite,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            
                                            Spacer(modifier = Modifier.height(10.dp))
                                            
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text("🎙️", fontSize = 14.sp)
                                                    Column {
                                                        Text(
                                                            text = "التسميع الذكي في جميع شاشات المصحف:",
                                                            color = EmeraldSecondary,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "تمكين التسميع الفوري لجميع السور الـ 114 بالكامل! بالإضافة للقائمة الرئيسية، أصبح بإمكانك الضغط على زر التسميع الصوتي والكتابي من داخل شاشة قراءة السورة مباشرةً لتفحص حفظك أثناء التلاوة.",
                                                            color = TextColorSecondary,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                                
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text("💫", fontSize = 14.sp)
                                                    Column {
                                                        Text(
                                                            text = "دمج كامل وحل لمشاكل التمرير:",
                                                            color = EmeraldSecondary,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "بفضل تصميم القائمة المسطحة المتفوقة (LazyColumn الفردي)، تم حل تداخل التمرير بصفة نهائية لضمان تصفح فائق السلاسة على جميع الأجهزة دون أي تعطل.",
                                                            color = TextColorSecondary,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                                
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text("🎧", fontSize = 14.sp)
                                                    Column {
                                                        Text(
                                                            text = "تبويب أصوات الشيوخ ودعم كبار القراء:",
                                                            color = EmeraldSecondary,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "استمع وراجع مباشرة تلاوات الشيوخ (العفاسي، عبد الباسط، الغامدي، المعيقلي، المنشاوي) لأي سورة تختارها عبر المشغل الصوتي الإيماني المدمج.",
                                                            color = TextColorSecondary,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }

                            // KHATMAS TRACKER Progress Card
                            item {
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "تتبع ختمة القرآن الكريم كامل 🕋",
                                        color = GoldAccent,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = "الصفحات المنجزة: ${viewModel.khatmaProgressPages} / ٦٠٤",
                                                color = LightWhite,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "المتبقي للختم: ${604 - viewModel.khatmaProgressPages} صفحة",
                                                color = TextColorSecondary,
                                                fontSize = 11.sp
                                            )
                                        }

                                        // Reset button
                                        TextButton(
                                            onClick = { viewModel.resetKhatma() },
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("إعادة الختمة 🔄", color = MutedRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    val pageProgress = viewModel.khatmaProgressPages.toFloat() / 604f
                                    LinearProgressIndicator(
                                        progress = { pageProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = EmeraldSecondary,
                                        trackColor = EmeraldMuted.copy(alpha = 0.3f)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { viewModel.incrementKhatmaProgress() },
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldMuted),
                                            modifier = Modifier.weight(1.0f).height(38.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text("سجلت قراءة صفحة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        currentBookmark?.let { b ->
                                            Button(
                                                onClick = {
                                                    val target = surahs.find { it.arabicName == b.surahName || it.id == b.surahNumber }
                                                    if (target != null) {
                                                        selectedSurahForReading = target
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = GoldMuted.copy(alpha = 0.4f)),
                                                modifier = Modifier.weight(1.0f).height(38.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("علامتي: ${b.surahName} 🔖", fontSize = 11.sp, color = GoldAccent, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // SEARCH BAR
                            item {
                                TextField(
                                    value = searchInput,
                                    onValueChange = { searchInput = it },
                                    placeholder = { Text("ابحث عن سور من الـ ١١٤ سورة باسمها...", color = TextColorSecondary) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                                        .testTag("surah_search_field"),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = SurfaceDarkGlass,
                                        unfocusedContainerColor = SurfaceDarkGlass,
                                        disabledContainerColor = SurfaceDarkGlass,
                                        cursorColor = GoldAccent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = LightWhite,
                                        unfocusedTextColor = LightWhite
                                    ),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Search,
                                            contentDescription = "بحث",
                                            tint = GoldAccent
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchInput.isNotEmpty()) {
                                            IconButton(onClick = { searchInput = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "مسح", tint = GoldAccent)
                                            }
                                        }
                                    }
                                )
                            }

                            // LIST ALL SURAHS
                            items(filteredSurahs) { surah ->
                                val isBookmarked = currentBookmark?.surahNumber == surah.id

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(SurfaceDarkGlass)
                                        .border(
                                            1.dp,
                                            if (isBookmarked) GoldAccent.copy(alpha = 0.5f)
                                            else CardBorder,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable { selectedSurahForReading = surah }
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left index and names
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            // Index ornamental item
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(
                                                        EmeraldMuted.copy(alpha = 0.25f),
                                                        CircleShape
                                                    )
                                                    .border(1.dp, GoldAccent.copy(alpha = 0.4f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = surah.id.toString(),
                                                    color = GoldAccent,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(14.dp))

                                            Column {
                                                Text(
                                                    text = "سورة ${surah.arabicName}",
                                                    color = if (isBookmarked) GoldAccent else LightWhite,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp
                                                )
                                                Text(
                                                    text = "${surah.englishName} • ${surah.totalAyat} آية • ${surah.type}",
                                                    color = TextColorSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        // Right side actions
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Recitation evaluation icon button
                                            IconButton(
                                                onClick = {
                                                    selectedSurahForMemorization = surah
                                                    showMemorizationDialog = true
                                                    viewModel.triggerHaptic()
                                                },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(EmeraldSecondary.copy(alpha = 0.15f))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Mic,
                                                    contentDescription = "تسميع بالذكاء الاصطناعي",
                                                    tint = EmeraldSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            // Original reading action indicator
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(GoldAccent.copy(alpha = 0.1f))
                                                    .clickable { selectedSurahForReading = surah }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = "قراءة",
                                                        color = GoldAccent,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Default.MenuBook,
                                                        contentDescription = "قراءة",
                                                        tint = GoldAccent,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // TAB 1: SHEIKH AUDIOS & VOICE CONTROLLER
                            item {
                                Text(
                                    text = "اختر القارئ والشيخ للاستماع المباشر 🎙️",
                                    color = GoldAccent,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }

                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val activeReciter = viewModel.selectedReciter
                                    sheikhs.forEach { sheikh ->
                                        val isSelected = activeReciter == sheikh.id
                                        Box(
                                            modifier = Modifier
                                                .width(140.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelected) GoldAccent.copy(alpha = 0.15f) else SurfaceDarkGlass)
                                                .border(
                                                    1.dp,
                                                    if (isSelected) GoldAccent else CardBorder,
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    viewModel.setReciter(sheikh.id)
                                                }
                                                .padding(10.dp)
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) GoldAccent else GoldAccent.copy(alpha = 0.2f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = sheikh.avatarChar,
                                                        color = if (isSelected) DarkBackground else GoldAccent,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 16.sp
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = sheikh.name,
                                                    color = LightWhite,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = sheikh.description,
                                                    color = TextColorSecondary,
                                                    fontSize = 8.sp,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                val playbackState = viewModel.playbackState
                                GlassCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "مشغل الصوتيات الإيماني 🎧",
                                            color = GoldAccent,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))

                                        val textLabel = if (playbackState.surahName.isNotEmpty()) {
                                            "سورة ${playbackState.surahName}"
                                        } else {
                                            "لم يتم تشغيل سورة بعد"
                                        }
                                        Text(
                                            text = textLabel,
                                            color = LightWhite,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "بصوت: ${playbackState.reader}",
                                            color = TextColorSecondary,
                                            fontSize = 11.sp
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Progress bar
                                        LinearProgressIndicator(
                                            progress = { playbackState.progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                            color = GoldAccent,
                                            trackColor = GoldMuted.copy(alpha = 0.3f)
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Audio Controls Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceAround,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Mute Button
                                            IconButton(onClick = { viewModel.toggleMute() }) {
                                                Icon(
                                                    imageVector = if (playbackState.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                                    contentDescription = "كتم الصوت",
                                                    tint = GoldAccent
                                                )
                                            }

                                            // Play / Pause Button
                                            IconButton(
                                                onClick = { viewModel.togglePlayback() },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .background(GoldAccent)
                                            ) {
                                                Icon(
                                                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = "تشغيل/إيقاف مؤقت",
                                                    tint = DarkBackground,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }

                                            // Stop Button
                                            IconButton(onClick = { viewModel.stopRecitation() }) {
                                                Icon(
                                                    imageVector = Icons.Default.Stop,
                                                    contentDescription = "إيقاف",
                                                    tint = GoldAccent
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                // SEARCH IN AUDIO LIST
                                TextField(
                                    value = searchInput,
                                    onValueChange = { searchInput = it },
                                    placeholder = { Text("بحث عن سورة للاستماع بصوت محدد...", color = TextColorSecondary) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = SurfaceDarkGlass,
                                        unfocusedContainerColor = SurfaceDarkGlass,
                                        disabledContainerColor = SurfaceDarkGlass,
                                        cursorColor = GoldAccent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = LightWhite,
                                        unfocusedTextColor = LightWhite
                                    ),
                                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = GoldAccent) }
                                )
                            }

                            // SCROLLABLE AUDIO INDEX
                            items(filteredSurahs) { surah ->
                                val isCurrentPlaying = viewModel.playbackState.surahIndex == surah.id
                                val playbackState = viewModel.playbackState

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isCurrentPlaying) GoldAccent.copy(alpha = 0.08f) else SurfaceDarkGlass)
                                        .border(
                                            1.dp,
                                            if (isCurrentPlaying) GoldAccent.copy(alpha = 0.6f) else CardBorder,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            viewModel.playRecitation(surah.arabicName, surah.id)
                                        }
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(GoldAccent.copy(alpha = 0.1f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(surah.id.toString(), color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text("سورة ${surah.arabicName}", color = LightWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                Text("${surah.englishName} • ${surah.totalAyat} آية", color = TextColorSecondary, fontSize = 10.sp)
                                            }
                                        }

                                        // Play action icon
                                        Icon(
                                            imageVector = if (isCurrentPlaying && playbackState.isPlaying) Icons.Default.VolumeUp else Icons.Default.PlayArrow,
                                            contentDescription = "استماع",
                                            tint = if (isCurrentPlaying) GoldAccent else TextColorSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // DIALOGS FOR AI MEMORIZATION EVALUATION
        if (showMemorizationDialog && selectedSurahForMemorization != null) {
            val surah = selectedSurahForMemorization!!
            
            val infiniteTransition = rememberInfiniteTransition(label = "pulse_recording")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_recording_scale"
            )
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 0.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_recording_alpha"
            )

            AlertDialog(
                onDismissRequest = { 
                    stopPlayingRecordedVoice()
                    stopAudioRecording()
                    showMemorizationDialog = false 
                    recitationText = ""
                    audioFile = null
                    evaluationResultScore = null
                    evaluationResultStatus = null
                    evaluationResultFeedback = null
                },
                containerColor = DarkBackground,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = EmeraldSecondary)
                        Text("اختبار التسميع بالذكاء الاصطناعي 🧠", color = LightWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "سورة ${surah.arabicName} • صحّح تلاوتك فوراً مع المعلم الذكي واحصل على تقييم تفصيلي.",
                            color = TextColorSecondary,
                            fontSize = 11.sp
                        )

                        // Dialog Internal Navigation Tabs
                        TabRow(
                            selectedTabIndex = memorizationTab,
                            containerColor = Color.Transparent,
                            contentColor = GoldAccent
                        ) {
                            Tab(
                                selected = memorizationTab == 0,
                                onClick = { 
                                    memorizationTab = 0 
                                    viewModel.triggerHaptic()
                                },
                                text = { Text("تسميع بالصوت 🎙️", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                            )
                            Tab(
                                selected = memorizationTab == 1,
                                onClick = { 
                                    memorizationTab = 1 
                                    viewModel.triggerHaptic()
                                },
                                text = { Text("تسميع كتابي ✍️", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (memorizationTab == 0) {
                            // VOICE RECITATION MODE (التسميع الصوتي)
                            if (audioFile == null) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (isRecording) {
                                        // Pulsing waveform circle and timer
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.size(100.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(100.dp)
                                                    .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                                                    .background(MutedRed.copy(alpha = pulseAlpha), CircleShape)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .background(MutedRed, CircleShape)
                                                    .clickable { handleRecordClick() },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Stop,
                                                    contentDescription = "إيقاف",
                                                    tint = LightWhite,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = "يجري الآن الاستماع للتلاوة... 🎧",
                                            color = MutedRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )

                                        Text(
                                            text = "الوقت المستغرق: ${formatDuration(recordingDuration)}",
                                            color = LightWhite,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        // Simulating custom animated equalizers
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val barHeights = listOf(20, 45, 30, 60, 40, 50, 25)
                                            barHeights.forEachIndexed { idx, expectedHeight ->
                                                val factor = if (idx % 2 == 0) pulseScale else (2.3f - pulseScale)
                                                Box(
                                                    modifier = Modifier
                                                        .width(4.dp)
                                                        .height((expectedHeight * factor).coerceAtMost(70f).dp)
                                                        .background(MutedRed, RoundedCornerShape(2.dp))
                                                )
                                            }
                                        }

                                        Text(
                                            text = "اقرأ ما تيسر من سورة ${surah.arabicName} بخشوع وترتيل سليم وعند الانتهاء اضغط زر التوقف لمعالجته مياشرة بالذكاء الاصطناعي.",
                                            color = TextColorSecondary,
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                    } else {
                                        // Ready to record
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(80.dp)
                                                .background(EmeraldSecondary.copy(alpha = 0.12f), CircleShape)
                                                .border(2.dp, EmeraldSecondary.copy(alpha = 0.4f), CircleShape)
                                                .clickable { handleRecordClick() }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Mic,
                                                contentDescription = "بدء التسجيل الصوتي",
                                                tint = EmeraldSecondary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }

                                        Text(
                                            text = "اضغط على زر الميكروفون للبدء بالتسميع الصوتي 🎙️",
                                            color = EmeraldSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center
                                        )

                                        Text(
                                            text = "سنستمع لصوتك ومخارج الآيات ومستوى الحفظ لتقييمك الذكي بالكامل ودعمك لتثبيته.",
                                            color = TextColorSecondary,
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                        )
                                    }

                                    permissionError?.let { err ->
                                        Text(
                                            text = err,
                                            color = MutedRed,
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp)
                                        )
                                    }
                                }
                            } else {
                                // Audio is captured, play back and review
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = EmeraldSecondary.copy(alpha = 0.08f)),
                                        border = BorderStroke(1.dp, EmeraldSecondary.copy(alpha = 0.25f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.CheckCircle, "ناجح", tint = EmeraldSecondary)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text("تم تسجيل تلاوتك بنجاح ✅", color = LightWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    Text("طول التسجيل: ${formatDuration(recordingDuration)}", color = TextColorSecondary, fontSize = 10.sp)
                                                }
                                            }

                                            IconButton(
                                                onClick = {
                                                    stopPlayingRecordedVoice()
                                                    audioFile = null
                                                },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(MutedRed.copy(alpha = 0.15f), CircleShape)
                                            ) {
                                                Icon(Icons.Default.Delete, "حذف", tint = MutedRed, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            if (isPlayingRecord) {
                                                stopPlayingRecordedVoice()
                                            } else {
                                                playRecordedVoice()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isPlayingRecord) MutedRed.copy(alpha = 0.8f) else GoldAccent
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isPlayingRecord) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                contentDescription = "تشغيل",
                                                tint = if (isPlayingRecord) LightWhite else DarkBackground,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isPlayingRecord) "إيقاف الاستماع لتسجيلك ⏹️" else "استمع لتلاوتك للتأكد من الصوت 🎧",
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPlayingRecord) LightWhite else DarkBackground,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "تسجيلك جاهز الآن للمراجعة الفورية عبر الذكاء الاصطناعي (Gemini). اضغط على زر بدء التدقيق بالذكاء الاصطناعي أدناه للحصول على التقييم.",
                                        color = TextColorSecondary,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            // WRITTEN RECITATION MODE (التسميع الكتابي)
                            Button(
                                onClick = {
                                    recitationText = if (surah.id == 1) {
                                        "بسم الله الرحمن الرحيم الحمد لله رب العلمين الرحمن الرحيم مالك يوم الدين إياك نعبد وإياك نستعين اهدنا الصراط المستقیم صراط الذين أنعمت عليهم غير المغضوب عليهم والضالين"
                                    } else {
                                        "بسم الله الرحمن الرحيم إنا أعطيناك الكوثر فصلي لربك وانحر إن شانئك هو الأبتر الشريف"
                                    }
                                    viewModel.triggerHaptic()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldMuted.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("تغذية بنص تسميع تجريبي (للتجربة السريعة) 📝", color = GoldAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedTextField(
                                value = recitationText,
                                onValueChange = { recitationText = it },
                                placeholder = { Text("اكتب هنا ما تحفظه من السورة الكريمة أو استخدم الزر أعلاه لتنزيل نص نموذج تجريبي مباشر...", color = TextColorSecondary) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp),
                                maxLines = 5,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = LightWhite),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldAccent,
                                    unfocusedBorderColor = CardBorder,
                                    cursorColor = GoldAccent
                                )
                            )
                        }

                        if (isEvaluating) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = GoldAccent)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "يجري الآن تدقيق الحفظ عبر خوادم إيمانيات الذكية...",
                                    color = GoldAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (evaluationResultScore != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = EmeraldSecondary.copy(alpha = 0.08f)),
                                border = BorderStroke(1.dp, EmeraldSecondary.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "مستوى الحفظ: ${evaluationResultStatus ?: ""}",
                                                color = EmeraldSecondary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "تقييم التسميع والترتيل الدقيق",
                                                color = TextColorSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                        
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(CircleShape)
                                                .background(EmeraldSecondary.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${evaluationResultScore!!}/10",
                                                color = EmeraldSecondary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    Text(
                                        text = evaluationResultFeedback ?: "",
                                        color = LightWhite,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { 
                                stopPlayingRecordedVoice()
                                stopAudioRecording()
                                showMemorizationDialog = false 
                                recitationText = ""
                                audioFile = null
                                evaluationResultScore = null
                                evaluationResultStatus = null
                                evaluationResultFeedback = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إغلاق", color = TextColorSecondary, fontWeight = FontWeight.Bold)
                        }

                        val canStartEvaluation = if (memorizationTab == 0) {
                            audioFile != null && !isEvaluating
                        } else {
                            recitationText.isNotBlank() && !isEvaluating
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isEvaluating = true
                                    evaluationResultScore = null
                                    evaluationResultStatus = null
                                    evaluationResultFeedback = null
                                    
                                    val correctText = getSurahFullText(surah.id, localQuranJson, mockVerses)
                                    val responseJson = if (memorizationTab == 0) {
                                        // Evaluate AUDIO
                                        val file = audioFile
                                        if (file != null && file.exists()) {
                                            val bytes = file.readBytes()
                                            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                            com.example.api.GeminiApiClient.evaluateAudioMemorization(
                                                surahName = surah.arabicName,
                                                correctText = correctText,
                                                audioBase64 = base64,
                                                mimeType = "audio/mp4"
                                            )
                                        } else {
                                            """{"score":0.0,"status":"ملف مفقود","feedback":"نعتذر، لم يتم العثور على تلاوتك الصوتية المسجلة."}"""
                                        }
                                    } else {
                                        // Evaluate TEXT
                                        com.example.api.GeminiApiClient.evaluateMemorization(
                                            surahName = surah.arabicName,
                                            correctText = correctText,
                                            recitationText = recitationText
                                        )
                                    }
                                    
                                    try {
                                        val cleanJson = responseJson.trim()
                                            .replace("```json", "")
                                            .replace("```", "")
                                            .trim()
                                        val jsonObject = org.json.JSONObject(cleanJson)
                                        evaluationResultScore = jsonObject.optDouble("score", 0.0)
                                        evaluationResultStatus = jsonObject.optString("status", "غير معروف")
                                        evaluationResultFeedback = jsonObject.optString("feedback", "")
                                    } catch (e: Exception) {
                                        evaluationResultScore = 0.0
                                        evaluationResultStatus = "تنبيه الفحص ⚠️"
                                        evaluationResultFeedback = "نعتذر يا طيب، واجهتنا مشكلة في معالجة نتيجة التسميع الحالية. يرجى إعادة المحاولة والتأكد من جودة التسجيل أو الاتصال بالإنترنت.\n\nالتفاصيل: $responseJson"
                                    } finally {
                                        isEvaluating = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(8.dp),
                            enabled = canStartEvaluation
                        ) {
                            Text("بدء التدقيق 🧠", color = DarkBackground, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    }
}

// Top level helper structures to hold Sheikh parameters and retrieve verses
data class SheikhReciter(val id: String, val name: String, val description: String, val avatarChar: String)

fun getSurahFullText(surahId: Int, localJson: Map<Int, List<String>>, mockVerses: Map<Int, List<String>>): String {
    val verses = localJson[surahId] ?: mockVerses[surahId]
    return if (verses != null && verses.isNotEmpty()) {
        verses.joinToString(separator = " \n ")
    } else {
        "بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ"
    }
}
