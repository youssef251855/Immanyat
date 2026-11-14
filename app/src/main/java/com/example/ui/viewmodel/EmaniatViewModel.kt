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
    ADHAN_SETTINGS,
    ADHAN_ALARM,
    LIBRARY,
    VIDEOS,
    DONATE,
    OASIS,
    QIBLA,
    ZAKAT
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
    var userEmail by mutableStateOf(prefs.getString("user_email", "") ?: "")
    var userId by mutableStateOf(prefs.getString("user_id", "") ?: "")
    var userAvatarIndex by mutableStateOf(0) // indices for beautiful vectors
    var userTitle by mutableStateOf(prefs.getString("user_title", "الباحث عن الطاعة") ?: "الباحث عن الطاعة")

    // Cloud sync state
    var isCloudSyncing by mutableStateOf(false)
        private set
    var cloudSyncStatusMessage by mutableStateOf("")
        private set
    var lastSyncTimeString by mutableStateOf(prefs.getString("last_sync_time", "غير متزامن") ?: "غير متزامن")

    // Playback State
    var playbackState by mutableStateOf(PlaybackState(
        reader = when (prefs.getString("quran_reciter", "afs")) {
            "afs" -> "الشيخ مشاري العفاسي"
            "basit" -> "الشيخ عبد الباسط عبد الصمد"
            "ghamdi" -> "الشيخ سعد الغامدي"
            "maher" -> "الشيخ ماهر المعيقلي"
            "minsh" -> "الشيخ محمد صديق المنشاوي"
            else -> "الشيخ مشاري العفاسي"
        }
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

    fun setLoginStatus(loggedIn: Boolean, name: String = "عبد الله", email: String = "", uid: String = "") {
        isUserLoggedIn = loggedIn
        userName = name
        userEmail = email
        userId = uid
        val formattedTime = SimpleDateFormat("HH:mm yyyy-MM-dd", Locale.getDefault()).format(Date())
        prefs.edit().apply {
            putBoolean("user_logged_in", loggedIn)
            putString("user_name", name)
            putString("user_email", email)
            putString("user_id", uid)
            putString("user_title", if (loggedIn) "عابد مخلص" else "الباحث عن الطاعة")
            apply()
        }
        userTitle = if (loggedIn) "عابد مخلص" else "الباحث عن الطاعة"
        if (!loggedIn) {
            stopRecitation()
            viewModelScope.launch {
                FirebaseSyncManager.signOut(getApplication())
            }
        } else if (FirebaseSyncManager.isUserAuthenticated()) {
            // Immediately sync or fetch achievements from cloud
            syncOrFetchAchievements()
        } else {
            cloudSyncStatusMessage = "تم الحفظ محلياً على الجهاز 💾"
        }
        triggerHaptic()
    }

    fun updateUserName(newName: String) {
        val cleaned = newName.trim().take(60)
        if (cleaned.isNotEmpty()) {
            userName = cleaned
            prefs.edit().putString("user_name", cleaned).apply()
            triggerHaptic()
        }
    }

    var isDeletingAccount by mutableStateOf(false)
        private set

    fun deleteAccountAndAllData(context: Context, onComplete: (Boolean, String) -> Unit) {
        if (isDeletingAccount) return
        viewModelScope.launch {
            isDeletingAccount = true
            try {
                if (FirebaseSyncManager.isUserAuthenticated()) {
                    val cloudDeleteResult = FirebaseSyncManager.deleteUserCloudDataAndAccount(context)
                    if (cloudDeleteResult.isFailure) {
                        val err = cloudDeleteResult.exceptionOrNull()?.message
                            ?: "تعذر حذف الحساب السحابي. تأكد من اتصالك بالإنترنت ثم حاول مجدداً."
                        onComplete(false, err)
                        return@launch
                    }
                } else {
                    FirebaseSyncManager.signOut(context)
                }

                stopRecitationOnly()
                com.example.service.AdhanPlayerService.stopAthan(context)
                com.example.service.AdhanAlarmReceiver.stopFallbackAudio()

                withContext(Dispatchers.IO) {
                    repository.clearAllLocalData()
                    val appCtx = getApplication<Application>()
                    appCtx.getSharedPreferences("emaniat_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                    appCtx.getSharedPreferences("emaniat_prayers", Context.MODE_PRIVATE).edit().clear().apply()
                    appCtx.getSharedPreferences("emaniat_khatma_plan_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                    appCtx.getSharedPreferences("emaniat_adhan_prefs", Context.MODE_PRIVATE).edit().clear().apply()

                    appCtx.cacheDir.listFiles()?.forEach { file ->
                        if (file.name.startsWith("recitation_")) {
                            file.delete()
                        }
                    }
                    java.io.File(appCtx.filesDir, "quran").deleteRecursively()
                    repository.initializeDefaultDataIfRequired()
                }

                isUserLoggedIn = false
                userName = "عبد الله"
                userEmail = ""
                userId = ""
                userTitle = "الباحث عن الطاعة"
                khatmaProgressPages = 142
                lastSyncTimeString = "غير متزامن"
                cloudSyncStatusMessage = ""
                downloadedSurahsSet = emptySet()
                downloadProgressMap = emptyMap()
                playbackState = PlaybackState(reader = getReaderLabelFor("afs"))
                currentScreen = AppScreen.HOME

                onComplete(true, "تم حذف حسابك وجميع بياناتك نهائياً ✅")
            } catch (e: Exception) {
                Log.e("EmaniatViewModel", "Error deleting account and local data", e)
                onComplete(false, "حدث خطأ أثناء حذف البيانات. يرجى المحاولة لاحقاً.")
            } finally {
                isDeletingAccount = false
            }
        }
    }

    fun syncOrFetchAchievements() {
        if (!FirebaseSyncManager.isUserAuthenticated()) {
            cloudSyncStatusMessage = "سجّل بحساب Google للمزامنة السحابية ☁️"
            return
        }
        viewModelScope.launch {
            isCloudSyncing = true
            cloudSyncStatusMessage = "جاري مزامنة الإنجازات مع السحابة ☁️..."
            try {
                // Try fetching from Firestore first to see if user has existing cloud data
                val cloudProfile = FirebaseSyncManager.fetchAchievementsFromCloud(getApplication())
                if (cloudProfile != null) {
                    // Restore achievements into Room
                    val currentStreakObj = userStreak.value ?: StreakData()
                    repository.saveCompleteStreakData(
                        currentStreakObj.copy(
                            points = cloudProfile.points,
                            level = cloudProfile.level,
                            currentStreak = cloudProfile.currentStreak,
                            highestStreak = cloudProfile.highestStreak
                        )
                    )
                    khatmaProgressPages = cloudProfile.khatmaProgressPages
                    if (cloudProfile.displayName.isNotEmpty()) {
                        userName = cloudProfile.displayName
                        prefs.edit().putString("user_name", userName).apply()
                    }
                    if (cloudProfile.userTitle.isNotEmpty()) {
                        userTitle = cloudProfile.userTitle
                        prefs.edit().putString("user_title", userTitle).apply()
                    }
                    repository.restoreChallengesFromCloud(cloudProfile.completedChallenges)
                    repository.restoreTasbihCountsFromCloud(cloudProfile.tasbihCounts)
                    
                    val timeStr = SimpleDateFormat("hh:mm a - yyyy/MM/dd", Locale("ar")).format(Date(cloudProfile.lastSyncTime))
                    lastSyncTimeString = timeStr
                    prefs.edit().putString("last_sync_time", timeStr).apply()
                    cloudSyncStatusMessage = "تمت استعادة ومزامنة إنجازاتك بنجاح ✅"
                } else {
                    // Upload current Room achievements to Firestore
                    uploadCurrentAchievementsToCloud()
                }
            } catch (e: Exception) {
                Log.e("EmaniatViewModel", "Sync error", e)
                cloudSyncStatusMessage = "تم الحفظ محلياً في قاعدة البيانات 💾"
            } finally {
                isCloudSyncing = false
            }
        }
    }

    fun manualSyncToCloud() {
        if (!FirebaseSyncManager.isUserAuthenticated()) {
            cloudSyncStatusMessage = "سجّل الدخول بحساب Google أولاً للمزامنة السحابية ☁️"
            triggerHaptic()
            return
        }
        viewModelScope.launch {
            isCloudSyncing = true
            cloudSyncStatusMessage = "جاري حفظ الإنجازات سحابياً..."
            val success = uploadCurrentAchievementsToCloud()
            isCloudSyncing = false
            if (success) {
                val timeStr = SimpleDateFormat("hh:mm a - yyyy/MM/dd", Locale("ar")).format(Date())
                lastSyncTimeString = timeStr
                prefs.edit().putString("last_sync_time", timeStr).apply()
                cloudSyncStatusMessage = "تم حفظ جميع الإنجازات في السحابة بنجاح ☁️✨"
            } else {
                cloudSyncStatusMessage = "تم الحفظ محلياً في قاعدة البيانات بنجاح 💾"
            }
            triggerHaptic()
        }
    }

    private suspend fun uploadCurrentAchievementsToCloud(): Boolean {
        val currentUser = FirebaseSyncManager.getCurrentUser() ?: return false
        val uid = currentUser.uid
        val streak = userStreak.value ?: StreakData()
        val challengesList = challenges.value
        val completedChallenges = challengesList.filter { it.isCompleted }.map { it.id }
        val tasbihList = tasbihItems.value
        val tasbihCountsMap = tasbihList.associate { it.id to it.count }

        val profile = UserCloudProfile(
            uid = uid,
            displayName = userName,
            email = userEmail.ifEmpty { currentUser.email ?: "" },
            userTitle = userTitle,
            points = streak.points,
            level = streak.level,
            currentStreak = streak.currentStreak,
            highestStreak = streak.highestStreak,
            khatmaProgressPages = khatmaProgressPages,
            completedChallenges = completedChallenges,
            tasbihCounts = tasbihCountsMap,
            lastSyncTime = System.currentTimeMillis()
        )
        return FirebaseSyncManager.syncAchievementsToCloud(getApplication(), profile)
    }

    fun logout() {
        setLoginStatus(false, "عبد الله", "", "")
    }

    fun getReaderLabelFor(reciter: String): String {
        return when (reciter) {
            "afs" -> "الشيخ مشاري العفاسي"
            "basit" -> "الشيخ عبد الباسط عبد الصمد"
            "ghamdi" -> "الشيخ سعد الغامدي"
            "maher" -> "الشيخ ماهر المعيقلي"
            "minsh" -> "الشيخ محمد صديق المنشاوي"
            else -> "الشيخ مشاري العفاسي"
        }
    }

    fun setReciter(reciter: String) {
        selectedReciter = reciter
        prefs.edit().putString("quran_reciter", reciter).apply()
        triggerHaptic()
        
        val readerLabel = getReaderLabelFor(reciter)
        
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
        if (screen == AppScreen.HOME && aiState == "جاري ملامسة الروحانية...") {
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

        val readerLabel = getReaderLabelFor(selectedReciter)

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
        } else if (selectedReciter == "basit") {
            "https://download.quranicaudio.com/quran/abdul_basit_murattal/$fileName"
        } else if (selectedReciter == "ghamdi") {
            "https://server11.mp3quran.net/s_gmd/$fileName"
        } else if (selectedReciter == "maher") {
            "https://server12.mp3quran.net/maher/$fileName"
        } else if (selectedReciter == "minsh") {
            "https://server10.mp3quran.net/minsh/$fileName"
        } else {
            "https://server8.mp3quran.net/afs/$fileName"
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
            reader = getReaderLabelFor(selectedReciter)
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
            val readerLabel = getReaderLabelFor(selectedReciter)
            startNewStream(surahName, playbackState.surahIndex, readerLabel)
        }
        triggerHaptic()
    }

    fun pauseQuranRecitation() {
        val mp = quranExoPlayer
        if (mp != null) {
            try {
                if (mp.isPlaying) {
                    mp.pause()
                    playbackState = playbackState.copy(isPlaying = false)
                }
            } catch (e: Exception) {
                Log.e("EmaniatViewModel", "Error pausing Quran recitation", e)
            }
        }
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
        val urlString = when (reciter) {
            "afs" -> "https://server8.mp3quran.net/afs/$fileName"
            "basit" -> "https://download.quranicaudio.com/quran/abdul_basit_murattal/$fileName"
            "ghamdi" -> "https://server11.mp3quran.net/s_gmd/$fileName"
            "maher" -> "https://server12.mp3quran.net/maher/$fileName"
            "minsh" -> "https://server10.mp3quran.net/minsh/$fileName"
            else -> "https://server8.mp3quran.net/afs/$fileName"
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
