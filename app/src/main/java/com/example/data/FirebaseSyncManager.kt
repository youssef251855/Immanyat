package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = try {
        Firebase.auth.currentUser
    } catch (e: Exception) {
        null
    }

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

@IgnoreExtraProperties
data class UserCloudProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val userTitle: String = "عابد مخلص",
    val points: Int = 250,
    val level: Int = 1,
    val currentStreak: Int = 3,
    val highestStreak: Int = 5,
    val khatmaProgressPages: Int = 142,
    val completedChallenges: List<String> = emptyList(),
    val tasbihCounts: Map<String, Int> = emptyMap(),
    val lastSyncTime: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

object FirebaseSyncManager {
    private const val TAG = "FirebaseSyncManager"
    private const val USERS_COLLECTION = "users"

    fun getFirestore(context: Context): FirebaseFirestore {
        val databaseId = context.applicationContext.getString(R.string.firestore_database_id)
        return FirebaseFirestore.getInstance(databaseId)
    }

    private fun requireUserId(): String {
        return Firebase.auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun getCurrentUser(): FirebaseUser? {
        return try {
            Firebase.auth.currentUser
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing FirebaseAuth", e)
            null
        }
    }

    fun isUserAuthenticated(): Boolean {
        return getCurrentUser() != null
    }

    suspend fun syncAchievementsToCloud(
        context: Context,
        profile: UserCloudProfile
    ): Boolean {
        val db = try {
            getFirestore(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to obtain FirebaseFirestore instance", e)
            return false
        }
        return syncAchievementsToCloud(db, profile)
    }

    suspend fun syncAchievementsToCloud(
        db: FirebaseFirestore,
        profile: UserCloudProfile
    ): Boolean = withContext(Dispatchers.IO) {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            Log.w(TAG, "Skipping cloud sync: user not authenticated")
            return@withContext false
        }

        val userRef = db.collection(USERS_COLLECTION).document(uid)
        try {
            val existingDoc = userRef.get().await()
            val sanitizedPages = profile.khatmaProgressPages.coerceIn(0, 604)
            val sanitizedLevel = profile.level.coerceAtLeast(1)
            val sanitizedPoints = profile.points.coerceAtLeast(0)
            val sanitizedStreak = profile.currentStreak.coerceAtLeast(0)
            val sanitizedHighStreak = profile.highestStreak.coerceAtLeast(0)
            val sanitizedChallenges = profile.completedChallenges.take(100)
            val sanitizedTasbih = profile.tasbihCounts.entries.take(50).associate { it.key to it.value }

            if (!existingDoc.exists()) {
                val createData = hashMapOf<String, Any>(
                    "uid" to uid,
                    "displayName" to profile.displayName.take(120),
                    "email" to profile.email.take(254),
                    "userTitle" to profile.userTitle.take(100),
                    "points" to sanitizedPoints,
                    "level" to sanitizedLevel,
                    "currentStreak" to sanitizedStreak,
                    "highestStreak" to sanitizedHighStreak,
                    "khatmaProgressPages" to sanitizedPages,
                    "completedChallenges" to sanitizedChallenges,
                    "tasbihCounts" to sanitizedTasbih,
                    "lastSyncTime" to System.currentTimeMillis(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                try {
                    userRef.set(createData).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.CREATE, userRef.path)
                    return@withContext false
                }
            } else {
                val updateData = hashMapOf<String, Any>(
                    "displayName" to profile.displayName.take(120),
                    "email" to profile.email.take(254),
                    "userTitle" to profile.userTitle.take(100),
                    "points" to sanitizedPoints,
                    "level" to sanitizedLevel,
                    "currentStreak" to sanitizedStreak,
                    "highestStreak" to sanitizedHighStreak,
                    "khatmaProgressPages" to sanitizedPages,
                    "completedChallenges" to sanitizedChallenges,
                    "tasbihCounts" to sanitizedTasbih,
                    "lastSyncTime" to System.currentTimeMillis(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                try {
                    userRef.update(updateData).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.UPDATE, userRef.path)
                    return@withContext false
                }
            }

            Log.d(TAG, "Successfully synced achievements to cloud for $uid")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, userRef.path)
            false
        }
    }

    suspend fun fetchAchievementsFromCloud(context: Context): UserCloudProfile? {
        val db = try {
            getFirestore(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to obtain FirebaseFirestore instance", e)
            return null
        }
        return fetchAchievementsFromCloud(db)
    }

    suspend fun fetchAchievementsFromCloud(db: FirebaseFirestore): UserCloudProfile? = withContext(Dispatchers.IO) {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            Log.w(TAG, "Skipping cloud fetch: user not authenticated")
            return@withContext null
        }

        val userRef = db.collection(USERS_COLLECTION).document(uid)
        try {
            val doc = userRef.get().await()
            if (doc.exists()) {
                val displayName = doc.getString("displayName") ?: ""
                val email = doc.getString("email") ?: ""
                val userTitle = doc.getString("userTitle") ?: "عابد مخلص"
                val points = (doc.getLong("points") ?: 250L).toInt()
                val level = (doc.getLong("level") ?: 1L).toInt()
                val currentStreak = (doc.getLong("currentStreak") ?: 3L).toInt()
                val highestStreak = (doc.getLong("highestStreak") ?: 5L).toInt()
                val khatmaPages = (doc.getLong("khatmaProgressPages") ?: 142L).toInt()
                @Suppress("UNCHECKED_CAST")
                val completedChallenges = (doc.get("completedChallenges") as? List<*>)
                    ?.filterIsInstance<String>()
                    ?: emptyList()
                val rawTasbih = doc.get("tasbihCounts") as? Map<*, *>
                val tasbihCounts = rawTasbih?.entries
                    ?.mapNotNull { (k, v) ->
                        val key = k as? String
                        val count = (v as? Number)?.toInt()
                        if (key != null && count != null) key to count else null
                    }?.toMap() ?: emptyMap()
                val lastSyncTime = doc.getLong("lastSyncTime") ?: System.currentTimeMillis()
                val createdAt = doc.getTimestamp("createdAt")
                val updatedAt = doc.getTimestamp("updatedAt")

                return@withContext UserCloudProfile(
                    uid = uid,
                    displayName = displayName,
                    email = email,
                    userTitle = userTitle,
                    points = points,
                    level = level,
                    currentStreak = currentStreak,
                    highestStreak = highestStreak,
                    khatmaProgressPages = khatmaPages,
                    completedChallenges = completedChallenges,
                    tasbihCounts = tasbihCounts,
                    lastSyncTime = lastSyncTime,
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, userRef.path)
            null
        }
    }

    suspend fun signOut(context: Context) {
        AuthHelper.signOut(context)
    }
}
