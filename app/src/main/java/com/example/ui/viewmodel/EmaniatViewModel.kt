package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiApiClient
import com.example.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

enum class AppScreen {
    HOME,
    QURAN,
    AZKAR,
    DUAS,
    TASBIH,
    CHALLENGES,
    PROFILE,
    JOURNEY,
    ADHAN_SETTINGS
}

data class PlaybackState(
    val isPlaying: Boolean = false,
    val surahName: String = "",
    val surahIndex: Int = 1,
    val reader: String = "الشيخ مشاري العفاسي",
    val progress: Float = 0f,
    val isMuted: Boolean = false
)

class EmaniatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = EmaniatRepository(db)

    // Screen State
    var currentScreen by mutableStateOf(AppScreen.HOME)
        private set

    // SharedPreferences for persistent settings
    private val prefs = application.getSharedPreferences("emaniat_prefs", Context.MODE_PRIVATE)

    // Login status (persistent)
    var isUserLoggedIn by mutableStateOf(prefs.getBoolean("user_logged_in", false))
        private set

    // User customized profile
    var userName by mutableStateOf(prefs.getString("user_name", "عبد الله") ?: "عبد الله")
    var userAvatarIndex by mutableStateOf(0) // indices for beautiful vectors
    var userTitle by mutableStateOf(prefs.getString("user_title", "الباحث عن الطاعة") ?: "الباحث عن الطاعة")

    // Playback State
    var playbackState by mutableStateOf(PlaybackState(
        reader = if (prefs.getString("quran_reciter", "afs") == "afs") "الشيخ مشاري العفاسي" else "الشيخ عبد الباسط عبد الصمد"
    ))
        private set

    private var quranExoPlayer: ExoPlayer? = null
    private var progressJob: kotlinx.coroutines.Job? = null

    // Saved reciter
    var selectedReciter by mutableStateOf(prefs.getString("quran_reciter", "afs") ?: "afs")
        private set

    // Khatma Progress
    var khatmaProgressPages by mutableStateOf(142) // out of 604 pages

    // AI recommendation state
    var aiState by mutableStateOf("جاري ملامسة الروحانية...")
        private set
    var isAiLoading by mutableStateOf(false)

    // Haptics configuration
    var isVibrationEnabled by mutableStateOf(true)
    var isSoundEnabled by mutableStateOf(true)

    // Temporary active Tasbih in view
    var activeTasbihItem by mutableStateOf<TasbihItem?>(null)

    // Favorite Duas
    private val _favoriteDuas = MutableStateFlow<Set<String>>(emptySet())
    val favoriteDuas = _favoriteDuas.asStateFlow()

    // Quran download management
    // Key: "selectedReciter_surahIndex", Value: Progress percentage (0 to 100) or -1 for error
    var downloadProgressMap by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    var downloadedSurahsSet by mutableStateOf<Set<String>>(emptySet())
        private set

    // Streams from Room
    val userStreak: StateFlow<StreakData?> = repository.userStreak
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tasbihItems: StateFlow<List<TasbihItem>> = repository.tasbihItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val challenges: StateFlow<List<FaithChallenge>> = repository.challenges
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentBookmark: StateFlow<QuranBookmark?> = repository.currentBookmark
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val todayStr = repository.getTodayDateString()
    
    val todayWorshipLogs: StateFlow<List<WorshipLog>> = repository.getWorshipLogs(todayStr)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedWorshipCount: StateFlow<Int> = repository.getCompletedWorshipCount(todayStr)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            repository.initializeDefaultDataIfRequired()
            // Set some default active Tasbih Item once details are ready
            repository.tasbihItems.collect { list ->
                if (list.isNotEmpty() && activeTasbihItem == null) {
                    activeTasbihItem = list.first()
                }
            }
        }
        
        // Let's load the AI advice based on time of day on launch
        fetchAiSpiritualAdvice()
        // Refresh downloaded Quran Surahs
        refreshDownloadedSurahs()
    }

    fun setLoginStatus(loggedIn: Boolean, name: String = "عبد الله") {
        isUserLoggedIn = loggedIn
        userName = name
        prefs.edit().apply {
            putBoolean("user_logged_in", loggedIn)
            putString("user_name", name)
            putString("user_title", if (loggedIn) "عابد مخلص" else "الباحث عن الطاعة")
            apply()
        }
        userTitle = if (loggedIn) "عابد مخلص" else "الباحث عن الطاعة"
        if (!loggedIn) {
            stopRecitation()
        }
        triggerHaptic()
    }

    fun setReciter(reciter: String) {
        selectedReciter = reciter
        prefs.edit().putString("quran_reciter", reciter).apply()
        triggerHaptic()
        
        val readerLabel = if (reciter == "afs") "الشيخ مشاري العفاسي" else "الشيخ عبد الباسط عبد الصمد"
        
        // If we are currently playing, restart with the new reciter stream!
        if (playbackState.isPlaying) {
            val surahName = playbackState.surahName
            val index = playbackState.surahIndex
            playRecitationWithUrlRestart(surahName, index, readerLabel)
        } else {
            playbackState = playbackState.copy(reader = readerLabel)
        }
    }

    fun setScreen(screen: AppScreen) {
        currentScreen = screen
        if (screen == AppScreen.HOME) {
            fetchAiSpiritualAdvice()
        }
    }

    fun fetchAiSpiritualAdvice() {
        val todayHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeOfDayStr = when {
            todayHour in 5..11 -> "صباح"
            todayHour in 12..16 -> "الظهر"
            todayHour in 17..20 -> "المساء"
            else -> "الليل"
        }

        viewModelScope.launch {
            isAiLoading = true
            aiState = "جاري تأمل آيات هذا الوقت الحكيم..."
            val result = GeminiApiClient.getDailySpiritualAdvice(timeOfDayStr)
            aiState = result
            isAiLoading = false
        }
    }

    // Toggle Worship Done
    fun toggleWorship(name: String, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.updateWorshipLog(name, todayStr, isCompleted)
            triggerHaptic()
        }
    }

    // Increments Tasbih Items
    fun incrementTasbih(id: String) {
        viewModelScope.launch {
            val list = tasbihItems.value
            val item = list.find { it.id == id }
            if (item != null) {
                var newCount = item.count + 1
                if (newCount > item.totalRequired) {
                    // Reset or cycle
                    newCount = 0
                }
                repository.updateTasbihCount(id, newCount)
                
                // Update currently selected item if relevant
                activeTasbihItem?.let {
                    if (it.id == id) {
                        activeTasbihItem = it.copy(count = newCount)
                    }
                }
                triggerHaptic()
            }
        }
    }

    fun resetTasbih(id: String) {
        viewModelScope.launch {
            repository.updateTasbihCount(id, 0)
            activeTasbihItem?.let {
                if (it.id == id) {
                    activeTasbihItem = it.copy(count = 0)
                }
            }
            triggerHaptic()
        }
    }

    fun selectTasbihItem(item: TasbihItem) {
        activeTasbihItem = item
    }

    fun addNewTasbih(name: String, targetCount: Int) {
        viewModelScope.launch {
            val id = "t_custom_${System.currentTimeMillis()}"
            val newItem = TasbihItem(id, name, 0, targetCount)
            repository.insertTasbih(newItem)
            activeTasbihItem = newItem
            triggerHaptic()
        }
    }

    fun removeCustomTasbih(id: String) {
        viewModelScope.launch {
            repository.deleteTasbih(id)
            if (activeTasbihItem?.id == id) {
                activeTasbihItem = tasbihItems.value.firstOrNull()
            }
            triggerHaptic()
        }
    }

    // Save bookmarks
    fun bookmarkQuran(surahName: String, surahNumber: Int, ayahNumber: Int) {
        viewModelScope.launch {
            repository.saveBookmark(surahName, surahNumber, ayahNumber, khatmaProgressPages)
            triggerHaptic()
        }
    }

    // Advance pages of Khatma progress
    fun incrementKhatmaProgress() {
        if (khatmaProgressPages < 604) {
            khatmaProgressPages += 1
            triggerHaptic()
        }
    }

    fun resetKhatma() {
        khatmaProgressPages = 1
        triggerHaptic()
    }

    // Complete challenges
    fun toggleChallenge(id: String, completed: Boolean) {
        viewModelScope.launch {
            repository.completeChallenge(id, completed)
            triggerHaptic()
        }
    }

    // Playback control with real MediaPlayer
    fun playRecitation(surahName: String, surahIndex: Int) {
        if (surahIndex == -1) {
            stopRecitation()
            return
        }

        val readerLabel = if (selectedReciter == "afs") "الشيخ مشاري العفاسي" else "الشيخ عبد الباسط عبد الصمد"

        if (playbackState.surahIndex == surahIndex && quranExoPlayer != null) {
            togglePlayback()
            return
        }

        startNewStream(surahName, surahIndex, readerLabel)
    }

    private fun startNewStream(surahName: String, surahIndex: Int, reader: String) {
        stopRecitationOnly()

        playbackState = PlaybackState(
            isPlaying = false,
            surahName = surahName,
            surahIndex = surahIndex,
            reader = reader,
            progress = 0.02f,
            isMuted = playbackState.isMuted
        )

        // 1. Prepare file names
        val fileName = String.format(java.util.Locale.US, "%03d.mp3", surahIndex)
        val assetPath = "quran/$selectedReciter/$fileName"
        
        // 2. Check if file is downloaded in internal storage
        val localFile = java.io.File(getApplication<Application>().filesDir, "quran/$selectedReciter/$fileName")
        
        // 3. Check if file exists in assets folder
        val assetExists = try {
            getApplication<Application>().assets.open(assetPath).use { true }
        } catch (e: Exception) {
            false
        }

        val url = if (localFile.exists()) {
            localFile.absolutePath
        } else if (assetExists) {
            "asset:///$assetPath"
        } else if (selectedReciter == "afs") {
            "https://server8.mp3quran.net/afs/$fileName"
        } else {
            "https://download.quranicaudio.com/quran/abdul_basit_murattal/$fileName"
        }

        viewModelScope.launch {
            try {
                val exo = ExoPlayer.Builder(getApplication()).build().apply {
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                    
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY && !isPlaying) {
                                play()
                                val vol = if (this@EmaniatViewModel.playbackState.isMuted) 0f else 1f
                                volume = vol
                                this@EmaniatViewModel.playbackState = this@EmaniatViewModel.playbackState.copy(isPlaying = true)
                                startProgressUpdater()
                            } else if (state == Player.STATE_ENDED) {
                                this@EmaniatViewModel.playbackState = this@EmaniatViewModel.playbackState.copy(isPlaying = false, progress = 1.0f)
                                stopRecitationOnly()
                            }
                        }
                        
                        override fun onPlayerError(error: PlaybackException) {
                            Log.e("EmaniatViewModel", "ExoPlayer error", error)
                            this@EmaniatViewModel.playbackState = this@EmaniatViewModel.playbackState.copy(isPlaying = false)
                        }
                    })
                }
                quranExoPlayer = exo
            } catch (e: Exception) {
                Log.e("EmaniatViewModel", "Error initiating Quran stream", e)
                playbackState = playbackState.copy(isPlaying = false)
            }
        }
    }

    private fun playRecitationWithUrlRestart(surahName: String, surahIndex: Int, readerLabel: String) {
        startNewStream(surahName, surahIndex, readerLabel)
    }

    private fun stopRecitationOnly() {
        try {
            progressJob?.cancel()
            quranExoPlayer?.stop()
            quranExoPlayer?.release()
        } catch (e: Exception) {
            // ignore
        } finally {
            quranExoPlayer = null
        }
    }

    fun stopRecitation() {
        stopRecitationOnly()
        playbackState = PlaybackState(
            isPlaying = false,
            reader = if (selectedReciter == "afs") "الشيخ مشاري العفاسي" else "الشيخ عبد الباسط عبد الصمد"
        )
        triggerHaptic()
    }

    private fun startProgressUpdater() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                val mp = quranExoPlayer
                if (mp != null && playbackState.isPlaying) {
                    try {
                        val duration = mp.duration
                        if (duration > 0) {
                            val currentPos = mp.currentPosition
                            playbackState = playbackState.copy(progress = currentPos.toFloat() / duration.toFloat())
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    fun togglePlayback() {
        val mp = quranExoPlayer
        if (mp != null) {
            try {
                if (mp.isPlaying) {
                    mp.pause()
                    playbackState = playbackState.copy(isPlaying = false)
                } else {
                    mp.play()
                    playbackState = playbackState.copy(isPlaying = true)
                }
            } catch (e: Exception) {
                Log.e("EmaniatViewModel", "Error toggling playback", e)
            }
        } else {
            // If nothing is loaded, direct start of the stream to avoid recursive loops
            val surahName = playbackState.surahName.ifEmpty { "الفاتحة" }
            val readerLabel = if (selectedReciter == "afs") "الشيخ مشاري العفاسي" else "الشيخ عبد الباسط عبد الصمد"
            startNewStream(surahName, playbackState.surahIndex, readerLabel)
        }
        triggerHaptic()
    }

    fun setPlaybackProgress(progress: Float) {
        val mp = quranExoPlayer
        if (mp != null) {
            try {
                val duration = mp.duration
                if (duration > 0) {
                    mp.seekTo((progress * duration).toLong())
                    playbackState = playbackState.copy(progress = progress)
                }
            } catch (e: Exception) {
                // ignore
            }
        } else {
            playbackState = playbackState.copy(progress = progress)
        }
    }

    fun toggleMute() {
        val nextMuted = !playbackState.isMuted
        val mp = quranExoPlayer
        if (mp != null) {
            try {
                val vol = if (nextMuted) 0f else 1f
                mp.volume = vol
            } catch (e: Exception) {
                // ignore
            }
        }
        playbackState = playbackState.copy(isMuted = nextMuted)
        triggerHaptic()
    }

    override fun onCleared() {
        super.onCleared()
        stopRecitationOnly()
        progressJob?.cancel()
    }

    // Favorite Duas toggles
    fun toggleFavoriteDua(duaId: String) {
        val current = _favoriteDuas.value
        if (current.contains(duaId)) {
            _favoriteDuas.value = current - duaId
        } else {
            _favoriteDuas.value = current + duaId
        }
        triggerHaptic()
    }

    fun refreshDownloadedSurahs() {
        viewModelScope.launch(Dispatchers.IO) {
            val quranDir = java.io.File(getApplication<Application>().filesDir, "quran")
            val downloaded = mutableSetOf<String>()
            if (quranDir.exists() && quranDir.isDirectory) {
                quranDir.listFiles()?.forEach { reciterDir ->
                    if (reciterDir.isDirectory) {
                        val reciter = reciterDir.name
                        reciterDir.listFiles()?.forEach { file ->
                            if (file.isFile && file.name.endsWith(".mp3")) {
                                val nameWithoutExt = file.name.substringBeforeLast(".")
                                val index = nameWithoutExt.toIntOrNull()
                                if (index != null) {
                                    downloaded.add("${reciter}_$index")
                                }
                            }
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                downloadedSurahsSet = downloaded
            }
        }
    }

    fun downloadSurah(reciter: String, surahIndex: Int) {
        val key = "${reciter}_$surahIndex"
        if (downloadProgressMap.containsKey(key) && downloadProgressMap[key] != -1) {
            // Already downloading
            return
        }

        val fileName = String.format(java.util.Locale.US, "%03d.mp3", surahIndex)
        val urlString = if (reciter == "afs") {
            "https://server8.mp3quran.net/afs/$fileName"
        } else {
            "https://download.quranicaudio.com/quran/abdul_basit_murattal/$fileName"
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    downloadProgressMap = downloadProgressMap + (key to 0)
                }

                val url = java.net.URL(urlString)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connect()

                if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                    throw Exception("Server returned HTTP ${connection.responseCode}")
                }

                val fileLength = connection.contentLength
                val quranDir = java.io.File(getApplication<Application>().filesDir, "quran/$reciter")
                if (!quranDir.exists()) {
                    quranDir.mkdirs()
                }

                val localFile = java.io.File(quranDir, fileName)
                val tempFile = java.io.File(quranDir, "$fileName.tmp")

                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        val data = ByteArray(4096)
                        var total: Long = 0
                        var count: Int
                        var lastProgress = 0
                        while (input.read(data).also { count = it } != -1) {
                            total += count
                            if (fileLength > 0) {
                                val progress = (total * 100 / fileLength).toInt()
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    withContext(Dispatchers.Main) {
                                        downloadProgressMap = downloadProgressMap + (key to progress)
                                    }
                                }
                            }
                            output.write(data, 0, count)
                        }
                    }
                }

                if (tempFile.renameTo(localFile)) {
                    withContext(Dispatchers.Main) {
                        downloadProgressMap = downloadProgressMap - key
                        refreshDownloadedSurahs()
                    }
                } else {
                    tempFile.delete()
                    throw java.io.IOException("Failed to rename temporary file")
                }

            } catch (e: Exception) {
                Log.e("EmaniatViewModel", "Error downloading surah", e)
                withContext(Dispatchers.Main) {
                    downloadProgressMap = downloadProgressMap + (key to -1)
                }
            }
        }
    }

    fun deleteDownloadedSurah(reciter: String, surahIndex: Int) {
        val fileName = String.format(java.util.Locale.US, "%03d.mp3", surahIndex)
        val localFile = java.io.File(getApplication<Application>().filesDir, "quran/$reciter/$fileName")
        if (localFile.exists()) {
            localFile.delete()
        }
        refreshDownloadedSurahs()
        triggerHaptic()
    }

    // Haptics controller
    fun triggerHaptic() {
        if (!isVibrationEnabled) return
        val vibrator = getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(30)
            }
        }
    }
}
