package com.example.api

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object PayPalApiClient {
    private const val TAG = "PayPalApiClient"
    private const val SANDBOX_URL = "https://api-m.sandbox.paypal.com/"
    private const val LIVE_URL = "https://api-m.paypal.com/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Retrieve active credentials from BuildConfig
    val clientId: String
        get() = BuildConfig.PAYPAL_CLIENT_ID

    val clientSecret: String
        get() = BuildConfig.PAYPAL_CLIENT_SECRET

    // Check if real credentials have been configured in the Secrets panel
    val isConfigured: Boolean
        get() = clientId.isNotEmpty() && 
                clientId != "YOUR_PAYPAL_CLIENT_ID_PLACEHOLDER" && 
                clientSecret.isNotEmpty() && 
                clientSecret != "YOUR_PAYPAL_CLIENT_SECRET_PLACEHOLDER"

    // Automatically determine sandbox vs live
    private fun getBaseUrl(): String {
        return if (isConfigured && !clientId.startsWith("A") && !clientId.contains("SANDBOX", ignoreCase = true)) {
            LIVE_URL
        } else {
            SANDBOX_URL
        }
    }

    /**
     * Obtains the PayPal Access Token using client_credentials grant type.
     */
    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            Log.e(TAG, "PayPal credentials not configured")
            return@withContext null
        }

        try {
            val credentials = Credentials.basic(clientId, clientSecret)
            val requestBody = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .build()

            val request = Request.Builder()
                .url(getBaseUrl() + "v1/oauth2/token")
                .header("Authorization", credentials)
                .header("Accept", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Token request failed: Code=${response.code} Body=${response.body?.string()}")
                    return@withContext null
                }
                val bodyString = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyString)
                return@withContext json.optString("access_token", null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching access token", e)
            null
        }
    }

    /**
     * Creates a PayPal Order and returns the Order ID and approval URL.
     */
    suspend fun createOrder(accessToken: String, amountUsd: String, planName: String): PayPalOrderInfo? = withContext(Dispatchers.IO) {
        try {
            val orderJson = JSONObject().apply {
                put("intent", "CAPTURE")
                
                val amountObj = JSONObject().apply {
                    put("currency_code", "USD")
                    put("value", amountUsd)
                }
                
                val purchaseUnitObj = JSONObject().apply {
                    put("amount", amountObj)
                    put("description", "Emaniat Pro - $planName")
                }
                
                val purchaseUnitsArr = JSONArray().apply {
                    put(purchaseUnitObj)
                }
                put("purchase_units", purchaseUnitsArr)

                val appContext = JSONObject().apply {
                    put("brand_name", "Emaniat Pro")
                    put("user_action", "PAY_NOW")
                    put("return_url", "https://example.com/return")
                    put("cancel_url", "https://example.com/cancel")
                }
                put("application_context", appContext)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = orderJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(getBaseUrl() + "v2/checkout/orders")
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Create order failed: Code=${response.code} Body=${response.body?.string()}")
                    return@withContext null
                }
                val bodyString = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyString)
                val orderId = json.getString("id")
                
                val links = json.getJSONArray("links")
                var approvalUrl: String? = null
                for (i in 0 until links.length()) {
                    val link = links.getJSONObject(i)
                    if (link.getString("rel") == "approve") {
                        approvalUrl = link.getString("href")
                        break
                    }
                }

                if (approvalUrl != null) {
                    PayPalOrderInfo(orderId = orderId, approvalUrl = approvalUrl)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating order", e)
            null
        }
    }

    /**
     * Captures an authorized checkout order.
     */
    suspend fun captureOrder(accessToken: String, orderId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = "{}".toRequestBody(mediaType)

            val request = Request.Builder()
                .url(getBaseUrl() + "v2/checkout/orders/$orderId/capture")
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string()
                Log.d(TAG, "Capture response: Code=${response.code} Body=$bodyString")
                if (response.isSuccessful && bodyString != null) {
                    val json = JSONObject(bodyString)
                    val status = json.optString("status")
                    status == "COMPLETED"
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing order", e)
            false
        }
    }
}

data class PayPalOrderInfo(
    val orderId: String,
    val approvalUrl: String
)
