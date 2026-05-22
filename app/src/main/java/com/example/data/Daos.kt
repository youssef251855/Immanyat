package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TasbihDao {
    @Query("SELECT * FROM tasbih ORDER BY dateModified DESC")
    fun getAllTasbihItems(): Flow<List<TasbihItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasbihItem(item: TasbihItem)

    @Query("UPDATE tasbih SET count = :count, dateModified = :timestamp WHERE id = :id")
    suspend fun updateCount(id: String, count: Int, timestamp: Long)

    @Query("DELETE FROM tasbih WHERE id = :id")
    suspend fun deleteTasbihItem(id: String)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmark WHERE id = 1 LIMIT 1")
    fun getBookmark(): Flow<QuranBookmark?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBookmark(bookmark: QuranBookmark)
}

@Dao
interface StreakDao {
    @Query("SELECT * FROM streak WHERE id = 1 LIMIT 1")
    fun getStreak(): Flow<StreakData?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveStreak(streak: StreakData)
}

@Dao
interface ChallengeDao {
    @Query("SELECT * FROM challenge")
    fun getAllChallenges(): Flow<List<FaithChallenge>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChallenges(challenges: List<FaithChallenge>)

    @Query("UPDATE challenge SET isCompleted = :completed WHERE id = :id")
    suspend fun updateChallengeStatus(id: String, completed: Boolean)
}

@Dao
interface WorshipDao {
    @Query("SELECT * FROM worship WHERE date = :date")
    fun getWorshipLogsByDate(date: String): Flow<List<WorshipLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorshipLog(log: WorshipLog)

    @Query("SELECT COUNT(*) FROM worship WHERE date = :date AND isCompleted = 1")
    fun getCompletedCount(date: String): Flow<Int>
}
