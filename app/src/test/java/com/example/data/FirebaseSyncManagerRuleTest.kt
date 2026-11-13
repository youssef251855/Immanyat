package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FirebaseSyncManagerRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun syncAndFetchAchievements_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val initialProfile = UserCloudProfile(
            uid = uid,
            displayName = "أحمد",
            email = ALICE_EMAIL,
            userTitle = "عابد مخلص",
            points = 350,
            level = 3,
            currentStreak = 7,
            highestStreak = 12,
            khatmaProgressPages = 210,
            completedChallenges = listOf("c1", "c2"),
            tasbihCounts = mapOf("1" to 33, "2" to 100)
        )

        val created = withTimeout(DEFAULT_TIMEOUT_MS) {
            FirebaseSyncManager.syncAchievementsToCloud(firestore, initialProfile)
        }
        assertTrue("Expected initial profile sync to succeed", created)

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            FirebaseSyncManager.fetchAchievementsFromCloud(firestore)
        }
        assertNotNull(fetched)
        assertEquals(uid, fetched!!.uid)
        assertEquals("أحمد", fetched.displayName)
        assertEquals(350, fetched.points)
        assertEquals(210, fetched.khatmaProgressPages)
        assertEquals(listOf("c1", "c2"), fetched.completedChallenges)
        assertEquals(mapOf("1" to 33, "2" to 100), fetched.tasbihCounts)

        // Update existing profile
        val updatedProfile = initialProfile.copy(points = 500, khatmaProgressPages = 240)
        val updated = withTimeout(DEFAULT_TIMEOUT_MS) {
            FirebaseSyncManager.syncAchievementsToCloud(firestore, updatedProfile)
        }
        assertTrue("Expected profile update sync to succeed", updated)

        val refetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            FirebaseSyncManager.fetchAchievementsFromCloud(firestore)
        }
        assertEquals(500, refetched?.points)
        assertEquals(240, refetched?.khatmaProgressPages)
    }

    @Test
    fun crossUserRead_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val profile = UserCloudProfile(
            uid = aliceUid,
            displayName = "Alice",
            email = ALICE_EMAIL
        )
        assertTrue(
            withTimeout(DEFAULT_TIMEOUT_MS) {
                FirebaseSyncManager.syncAchievementsToCloud(firestore, profile)
            }
        )

        // Sign in as Bob and attempt to read Alice's document directly
        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid).get().await()
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's profile")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
        }
    }

    @Test
    fun unauthenticatedAccess_failsWithPermissionDeniedAndSyncReturnsFalse() = runBlocking {
        auth.signOut()

        val synced = withTimeout(DEFAULT_TIMEOUT_MS) {
            FirebaseSyncManager.syncAchievementsToCloud(
                firestore,
                UserCloudProfile(uid = "fake_uid", displayName = "Guest")
            )
        }
        assertFalse(synced)

        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document("some_uid").get().await()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated read")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice@emaniat.test"
        const val BOB_EMAIL = "bob@emaniat.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
    }
}
