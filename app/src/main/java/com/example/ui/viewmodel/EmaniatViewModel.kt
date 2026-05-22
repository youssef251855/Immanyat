package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiApiClient
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

    // User customized profile
    var userName by mutableStateOf("عبد الله")
    var userAvatarIndex by mutableStateOf(0) // indices for beautiful vectors
    var userTitle by mutableStateOf("الباحث عن الطاعة")

    // Playback State
    var playbackState by mutableStateOf(PlaybackState())
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

    // Playback control simulation
    fun playRecitation(surahName: String, surahIndex: Int) {
        if (playbackState.surahIndex == surahIndex && playbackState.isPlaying) {
            playbackState = playbackState.copy(isPlaying = false)
        } else {
            playbackState = PlaybackState(
                isPlaying = true,
                surahName = surahName,
                surahIndex = surahIndex,
                progress = 0.2f
            )
            triggerHaptic()
        }
    }

    fun togglePlayback() {
        playbackState = playbackState.copy(isPlaying = !playbackState.isPlaying)
        triggerHaptic()
    }

    fun setPlaybackProgress(progress: Float) {
        playbackState = playbackState.copy(progress = progress)
    }

    fun toggleMute() {
        playbackState = playbackState.copy(isMuted = !playbackState.isMuted)
        triggerHaptic()
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
