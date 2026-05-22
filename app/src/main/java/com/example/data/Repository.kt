package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EmaniatRepository(private val db: AppDatabase) {

    val tasbihItems: Flow<List<TasbihItem>> = db.tasbihDao().getAllTasbihItems()
    val currentBookmark: Flow<QuranBookmark?> = db.bookmarkDao().getBookmark()
    val userStreak: Flow<StreakData?> = db.streakDao().getStreak()
    val challenges: Flow<List<FaithChallenge>> = db.challengeDao().getAllChallenges()

    fun getWorshipLogs(date: String): Flow<List<WorshipLog>> = db.worshipDao().getWorshipLogsByDate(date)
    fun getCompletedWorshipCount(date: String): Flow<Int> = db.worshipDao().getCompletedCount(date)

    suspend fun saveBookmark(surahName: String, surahNumber: Int, ayahNumber: Int, progress: Int) {
        db.bookmarkDao().saveBookmark(
            QuranBookmark(
                id = 1,
                surahName = surahName,
                surahNumber = surahNumber,
                ayahNumber = ayahNumber,
                scrollPosition = progress,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateTasbihCount(id: String, count: Int) {
        db.tasbihDao().updateCount(id, count, System.currentTimeMillis())
    }

    suspend fun insertTasbih(item: TasbihItem) {
        db.tasbihDao().insertTasbihItem(item)
    }

    suspend fun deleteTasbih(id: String) {
        db.tasbihDao().deleteTasbihItem(id)
    }

    suspend fun updateWorshipLog(name: String, date: String, isCompleted: Boolean) {
        val id = "${name}_$date"
        val log = WorshipLog(
            id = id,
            name = name,
            date = date,
            isCompleted = isCompleted,
            timeCompleted = if (isCompleted) System.currentTimeMillis() else 0
        )
        db.worshipDao().insertWorshipLog(log)
        
        // Recalculate streak / points when daily worship statuses are modified
        recalculateProgressAndPoints(date)
    }

    suspend fun completeChallenge(id: String, completed: Boolean) {
        db.challengeDao().updateChallengeStatus(id, completed)
        if (completed) {
            val challengeList = db.challengeDao().getAllChallenges().first()
            val target = challengeList.find { it.id == id }
            if (target != null) {
                val currentStreakInfo = db.streakDao().getStreak().firstOrNull() ?: StreakData()
                val addedPoints = target.pointsReward
                val newPoints = currentStreakInfo.points + addedPoints
                val newLevel = (newPoints / 500) + 1
                db.streakDao().saveStreak(
                    currentStreakInfo.copy(
                        points = newPoints,
                        level = if (newLevel > currentStreakInfo.level) newLevel else currentStreakInfo.level
                    )
                )
            }
        }
    }

    private suspend fun recalculateProgressAndPoints(date: String) {
        // Find logs for today
        val logs = db.worshipDao().getWorshipLogsByDate(date).first()
        val completed = logs.count { it.isCompleted }
        val total = 7.0f // Fajr, Dhuhr, Asr, Maghrib, Isha, Azkar, Quran
        val progress = if (logs.isNotEmpty()) completed.toFloat() / total else 0.0f

        val currentStreakInfo = db.streakDao().getStreak().firstOrNull() ?: StreakData()
        
        // Save today's progress
        var streakValue = currentStreakInfo.currentStreak
        var lastDate = currentStreakInfo.lastActiveDate
        val todayStr = getTodayDateString()
        
        // If progress is highly complete (>60%) and was not completed yet today, check streak increment
        if (progress >= 0.57f && lastDate != todayStr) {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            try {
                if (lastDate.isNotEmpty()) {
                    val last = format.parse(lastDate)
                    val today = format.parse(todayStr)
                    val diff = today.time - last.time
                    val diffDays = diff / (24 * 60 * 60 * 1000)
                    if (diffDays == 1L) {
                        streakValue += 1
                    } else if (diffDays > 1L) {
                        streakValue = 1 // broke streak
                    }
                } else {
                    streakValue = 1 // first day
                }
            } catch (e: Exception) {
                streakValue = 1
            }
            lastDate = todayStr
        }

        val gainedPoints = completed * 5 // 5 points per prayer/worship
        val totalPoints = currentStreakInfo.points + gainedPoints
        val finalLevel = (totalPoints / 500) + 1

        db.streakDao().saveStreak(
            currentStreakInfo.copy(
                currentStreak = streakValue,
                highestStreak = maxOf(streakValue, currentStreakInfo.highestStreak),
                lastActiveDate = lastDate,
                worshipProgressProgress = progress,
                points = totalPoints,
                level = finalLevel
            )
        )
    }

    suspend fun initializeDefaultDataIfRequired() {
        // Initialize Challenges
        val currentChallenges = db.challengeDao().getAllChallenges().first()
        if (currentChallenges.isEmpty()) {
            val defaults = listOf(
                FaithChallenge("ch_fajr", "صلاة الفجر في وقتها", "صلاة الفجر جماعة في وقتها تنير كامل يومك", 50, false, "DAILY"),
                FaithChallenge("ch_quran", "قراءة ورد من القرآن الكريم", "تلاوة صفحات من آيات الذكر الحكيم بتدبر وعناية", 30, false, "DAILY"),
                FaithChallenge("ch_azkar", "أذكار الصباح والمساء كاملة", "الحصن الحصين للمسلم، قراءة أذكار الصباح بعد الفجر والمساء بعد العصر", 40, false, "DAILY"),
                FaithChallenge("ch_tasbih", "مئة تسبيحة واستغفار", "الاستغفار رئة تريح النفس، استغفر وسبّح ربك مئة مرة", 20, false, "DAILY"),
                FaithChallenge("ch_kahf", "ختم سورة الكهف يوم الجمعة", "نور ما بين الجمعتين، تلاوة سورة الكهف كاملة في يوم الجمعة", 100, false, "WEEKLY"),
                FaithChallenge("ch_duha", "المحافظة على صلاة الضحى", "صلاة الأوابين مجلبة للرزق، ركعتين إلى ثمان ركعات في الضحى", 80, false, "WEEKLY"),
                FaithChallenge("ch_qiyam", "قيام الليل وتلاوة ودعاء", "شرف المؤمن قيام الليل، ناجِ ربك في الثلث الأخير من الليل بركعتين", 120, false, "WEEKLY")
            )
            db.challengeDao().insertChallenges(defaults)
        }

        // Initialize default Tasbih Items
        val currentTasbihList = db.tasbihDao().getAllTasbihItems().first()
        if (currentTasbihList.isEmpty()) {
            val defaults = listOf(
                TasbihItem("t_subhan", "سبحان الله وبحمده", 0, 33),
                TasbihItem("t_alhamd", "الحمد لله رب العالمين", 0, 33),
                TasbihItem("t_allahu", "الله أكبر", 0, 33),
                TasbihItem("t_stighfar", "أستغفر الله العظيم وأتوب إليه", 0, 100),
                TasbihItem("t_laillaha", "لا إله إلا الله وحده لا شريك له", 0, 100),
                TasbihItem("t_salat", "اللهم صلِّ وسلم على نبينا محمد", 0, 100)
            )
            for (item in defaults) {
                db.tasbihDao().insertTasbihItem(item)
            }
        }

        // Initialize Streak default row if not exist
        val streak = db.streakDao().getStreak().firstOrNull()
        if (streak == null) {
            db.streakDao().saveStreak(StreakData(id = 1, currentStreak = 3, lastActiveDate = getTodayDateString(), highestStreak = 5, points = 250, worshipProgressProgress = 0.5f, level = 1))
        }

        // Initialize default Worship Logs for today if they don't exist
        val todayStr = getTodayDateString()
        val logs = db.worshipDao().getWorshipLogsByDate(todayStr).first()
        if (logs.isEmpty()) {
            val defaults = listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء", "الأذكار", "القرآن")
            for (name in defaults) {
                val log = WorshipLog(id = "${name}_$todayStr", name = name, date = todayStr, isCompleted = false)
                db.worshipDao().insertWorshipLog(log)
            }
        }
    }

    fun getTodayDateString(): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return format.format(Date())
    }
}
