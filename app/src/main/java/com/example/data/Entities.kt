package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasbih")
data class TasbihItem(
    @PrimaryKey val id: String,
    val name: String,
    val count: Int,
    val totalRequired: Int,
    val dateModified: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmark")
data class QuranBookmark(
    @PrimaryKey val id: Int = 1,
    val surahName: String,
    val surahNumber: Int,
    val ayahNumber: Int,
    val scrollPosition: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "streak")
data class StreakData(
    @PrimaryKey val id: Int = 1,
    val currentStreak: Int = 0,
    val lastActiveDate: String = "",
    val highestStreak: Int = 0,
    val points: Int = 0,
    val worshipProgressProgress: Float = 0f, // current day's progress % e.g. 0.0 to 1.0
    val level: Int = 1
)

@Entity(tableName = "challenge")
data class FaithChallenge(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val pointsReward: Int,
    val isCompleted: Boolean = false,
    val category: String = "DAILY" // DAILY, WEEKLY, SPECIAL
)

@Entity(tableName = "worship")
data class WorshipLog(
    @PrimaryKey val id: String, // e.g. "Fajr_2026-05-21", "Zuhar_2026-05-21", "Azkar_2026-05-21", "Quran_2026-05-21"
    val name: String, // Fajr, Dhuhr, Asr, Maghrib, Isha, Azkar, Quran
    val date: String, // "YYYY-MM-DD"
    val isCompleted: Boolean = false,
    val timeCompleted: Long = 0
)
