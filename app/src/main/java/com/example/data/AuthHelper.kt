package com.example.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest

sealed class AuthResult {
    data class Success(val user: FirebaseUser?, val displayName: String, val email: String) : AuthResult()
    data object Cancelled : AuthResult()
    data class Error(val message: String) : AuthResult()
}

object AuthHelper {
    private const val TAG = "AuthHelper"
    private const val FAST_CANCEL_THRESHOLD_MS = 900L

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    @Suppress("DEPRECATION")
    fun getAppSigningSha1(context: Context): String? {
        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            val pkgInfo = pm.getPackageInfo(context.packageName, flags)
            val signatureBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
                    ?: pkgInfo.signingInfo?.signingCertificateHistory?.firstOrNull()?.toByteArray()
            } else {
                pkgInfo.signatures?.firstOrNull()?.toByteArray()
            } ?: return null

            val digest = MessageDigest.getInstance("SHA-1").digest(signatureBytes)
            digest.joinToString(":") { b -> "%02X".format(b) }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read signing SHA-1: ${e.message}")
            null
        }
    }

    /**
     * Restores an already-persisted Firebase session on screen startup without triggering
     * Credential Manager prompts silently (which can trigger Play Services cooldowns/cancellations).
     */
    suspend fun attemptAutoSignIn(context: Context): AuthResult? = withContext(Dispatchers.IO) {
        val currentUser = try {
            Firebase.auth.currentUser
        } catch (e: Exception) {
            null
        }
        if (currentUser != null) {
            return@withContext AuthResult.Success(
                user = currentUser,
                displayName = currentUser.displayName ?: "مستخدم إيمانيات",
                email = currentUser.email ?: ""
            )
        }
        null
    }

    private suspend fun signInWithCredentialOption(
        promptContext: Context,
        option: CredentialOption
    ): AuthResult {
        val credentialManager = CredentialManager.create(promptContext)
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val response = credentialManager.getCredential(context = promptContext, request = request)
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
            return AuthResult.Success(user, name, email)
        }
        return AuthResult.Error("نوع بيانات الاعتماد المستلمة من Google غير مدعوم.")
    }

    private fun isProviderOrConfigError(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val m = message.lowercase()
        return m.contains("28444") ||
            m.contains("developer") ||
            m.contains("blocked") ||
            m.contains("sha") ||
            m.contains("sync") ||
            m.contains("provider") ||
            m.contains("reauth") ||
            m.contains("10:") ||
            m.contains("16:") ||
            m.contains("configuration") ||
            m.contains("whitelisted") ||
            m.contains("cannot find a matching") ||
            m.contains("not found") ||
            m.contains("mismatch") ||
            m.contains("cert")
    }

    suspend fun signInWithGoogle(context: Context): AuthResult {
        val clientId = try {
            context.getString(R.string.default_web_client_id).trim()
        } catch (e: Exception) {
            return AuthResult.Error("إعدادات تسجيل الدخول بحساب Google غير مكتملة (default_web_client_id مفقود).")
        }
        if (clientId.isEmpty()) {
            return AuthResult.Error("معرّف عميل Google الويب غير مهيأ.")
        }

        // Pass the actual Activity context directly so Credential Manager can anchor the bottom sheet reliably.
        val activity = context.findActivity()
        val promptContext: Context = activity ?: context

        // 1) Primary Credential Manager flow: GetSignInWithGoogleOption
        val primaryOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val primaryStartMs = System.currentTimeMillis()
        try {
            return signInWithCredentialOption(promptContext, primaryOption)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            val elapsedMs = System.currentTimeMillis() - primaryStartMs
            val msg = e.errorMessage?.toString() ?: e.message
            Log.w("Auth", "Google Sign-In cancelled or dismissed after ${elapsedMs}ms: $msg (SHA1=${getAppSigningSha1(context)})", e)
            // Play Services rejects unregistered SHA-1 certificates almost instantaneously (< 900ms)
            // with GetCredentialCancellationException; fall through to secondary/web flow in that case.
            if (!isProviderOrConfigError(msg) && elapsedMs >= FAST_CANCEL_THRESHOLD_MS) {
                return AuthResult.Cancelled
            }
        } catch (e: NoCredentialException) {
            Log.w(TAG, "NoCredentialException on primary flow; trying GetGoogleIdOption fallback", e)
        } catch (e: GetCredentialProviderConfigurationException) {
            Log.w(TAG, "Provider configuration exception on primary flow; trying fallback", e)
        } catch (e: GetCredentialUnsupportedException) {
            Log.w(TAG, "Credential Manager unsupported on primary flow; trying web fallback", e)
            return signInWithGoogleWebFallback(context)
        } catch (e: GetCredentialCustomException) {
            Log.w(TAG, "Custom credential exception on primary flow (${e.type}): ${e.message}", e)
        } catch (e: GetCredentialUnknownException) {
            Log.w(TAG, "Unknown credential exception on primary flow: ${e.message}", e)
        } catch (e: GetCredentialInterruptedException) {
            return AuthResult.Error("تمت مقاطعة عملية تسجيل الدخول، يرجى المحاولة مرة أخرى.")
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed on primary flow: ${e.message}", e)
            if (!isProviderOrConfigError(e.message)) {
                return AuthResult.Error(e.localizedMessage ?: "تعذر تسجيل الدخول بواسطة Google.")
            }
        }

        // 2) Secondary Credential Manager flow: GetGoogleIdOption (unfiltered)
        val fallbackOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .build()
        val secondaryStartMs = System.currentTimeMillis()
        try {
            return signInWithCredentialOption(promptContext, fallbackOption)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            val elapsedMs = System.currentTimeMillis() - secondaryStartMs
            val msg = e.errorMessage?.toString() ?: e.message
            Log.w("Auth", "Fallback GoogleIdOption cancelled after ${elapsedMs}ms: $msg", e)
            if (!isProviderOrConfigError(msg) && elapsedMs >= FAST_CANCEL_THRESHOLD_MS) {
                return AuthResult.Cancelled
            }
        } catch (e: GetCredentialException) {
            Log.w(TAG, "CredentialManager fallback failed (${e.javaClass.simpleName}): ${e.message}; falling back to Web OAuth", e)
        } catch (e: Exception) {
            Log.e(TAG, "Fallback Google Sign-In failed: ${e.message}", e)
            if (!isProviderOrConfigError(e.message)) {
                return AuthResult.Error(e.localizedMessage ?: "تعذر تسجيل الدخول بواسطة Google.")
            }
        }

        // 3) Seamless Firebase Federated Google OAuth fallback (works even before SHA-1 is added in Firebase Console)
        return signInWithGoogleWebFallback(context)
    }

    /**
     * Opens Google Sign-In via Firebase Federated OAuth (Custom Tabs).
     * Works reliably even when Credential Manager / Play Services rejects an unregistered SHA-1
     * or when the device has no Credential Provider configured.
     */
    suspend fun signInWithGoogleWebFallback(context: Context): AuthResult {
        val activity = context.findActivity()
            ?: return AuthResult.Error("تعذر العثور على شاشة نشطة لفتح تسجيل الدخول عبر المتصفح.")

        return try {
            val provider = OAuthProvider.newBuilder("google.com")
                .addCustomParameter("prompt", "select_account")
                .build()

            val authResult = withContext(Dispatchers.Main) {
                val pending = Firebase.auth.pendingAuthResult
                val task = pending ?: Firebase.auth.startActivityForSignInWithProvider(activity, provider)
                task.await()
            }

            val user = authResult.user
            val name = user?.displayName ?: "مستخدم إيمانيات"
            val email = user?.email ?: ""
            AuthResult.Success(user, name, email)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthException) {
            Log.e(TAG, "Web OAuth FirebaseAuthException (${e.errorCode}): ${e.message}", e)
            val friendly = when (e.errorCode) {
                "ERROR_OPERATION_NOT_ALLOWED" ->
                    "يرجى تفعيل تسجيل الدخول بحساب Google داخل لوحة تحكم Firebase (Authentication ← Sign-in method ← Google)."
                "ERROR_WEB_CONTEXT_CANCELED" -> null
                "ERROR_NETWORK_REQUEST_FAILED" ->
                    "تعذر الاتصال بخادم المصادقة، تأكد من اتصالك بالإنترنت ثم أعد المحاولة."
                else -> e.localizedMessage ?: "تعذر إتمام تسجيل الدخول عبر المتصفح (${e.errorCode})."
            }
            if (friendly == null) AuthResult.Cancelled else AuthResult.Error(friendly)
        } catch (e: Exception) {
            Log.e(TAG, "Web OAuth Google Sign-In failed: ${e.message}", e)
            val msg = e.localizedMessage ?: e.message ?: "فشل تسجيل الدخول بواسطة Google."
            if (msg.contains("canceled", ignoreCase = true) || msg.contains("cancelled", ignoreCase = true)) {
                AuthResult.Cancelled
            } else {
                AuthResult.Error(msg)
            }
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
