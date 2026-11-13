package com.example.data

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class AuthResult {
    data class Success(val user: FirebaseUser?, val displayName: String, val email: String) : AuthResult()
    data object Cancelled : AuthResult()
    data class Error(val message: String) : AuthResult()
}

object AuthHelper {
    private const val TAG = "AuthHelper"

    suspend fun attemptAutoSignIn(context: Context): AuthResult? = withContext(Dispatchers.IO) {
        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            return@withContext AuthResult.Success(
                user = currentUser,
                displayName = currentUser.displayName ?: "مستخدم إيمانيات",
                email = currentUser.email ?: ""
            )
        }

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            return@withContext null
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return@withContext try {
            val credentialManager = CredentialManager.create(context)
            val response = credentialManager.getCredential(context = context, request = request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                val user = authResult.user
                val name = user?.displayName ?: googleIdTokenCredential.displayName ?: "مستخدم إيمانيات"
                val email = user?.email ?: googleIdTokenCredential.id
                AuthResult.Success(user, name, email)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Silent auto sign-in skipped: ${e.message}")
            null
        }
    }

    suspend fun signInWithGoogle(context: Context): AuthResult {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            return AuthResult.Error("إعدادات تسجيل الدخول بحساب Google غير مكتملة")
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val credentialManager = CredentialManager.create(context)
            val activityContext = (context as? Activity) ?: context
            val response = credentialManager.getCredential(context = activityContext, request = request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = withContext(Dispatchers.IO) {
                    Firebase.auth.signInWithCredential(authCredential).await()
                }
                val user = authResult.user
                val name = user?.displayName ?: googleIdTokenCredential.displayName ?: "مستخدم إيمانيات"
                val email = user?.email ?: googleIdTokenCredential.id
                AuthResult.Success(user, name, email)
            } else {
                AuthResult.Error("نوع بيانات الاعتماد غير مدعوم")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
            AuthResult.Cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed: ${e.message}", e)
            AuthResult.Error(e.localizedMessage ?: "فشل تسجيل الدخول بواسطة جوجل")
        }
    }

    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            try {
                val credentialManager = CredentialManager.create(context)
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                try {
                    Firebase.auth.signOut()
                } catch (e: Exception) {
                    Log.e(TAG, "Error signing out of Firebase", e)
                }
            }
        }
    }
}
