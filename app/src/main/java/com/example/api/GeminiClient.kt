package com.example.api

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiApiClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private fun configuredBaseUrl(): String? {
        val raw = BuildConfig.BACKEND_BASE_URL.trim().removeSuffix("/")
        return if (raw.startsWith("https://", ignoreCase = true) && raw.length > "https://".length) {
            raw
        } else {
            null
        }
    }

    val isConfigured: Boolean
        get() = configuredBaseUrl() != null

    private fun buildPostRequest(url: String, payload: JSONObject): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))

        val clientToken = BuildConfig.BACKEND_CLIENT_TOKEN.trim()
        if (clientToken.isNotEmpty() && !clientToken.startsWith("UNCONFIGURED_")) {
            builder.header("X-App-Token", clientToken)
        }
        return builder.build()
    }

    private fun unconfiguredEvaluationJson(): String {
        return JSONObject().apply {
            put("score", 0.0)
            put("status", "الخدمة غير مهيأة ⚙️")
            put(
                "feedback",
                "يرجى ربط رابط خادم إيمانيات الآمن (BACKEND_BASE_URL) لتفعيل التسميع والتقييم بالذكاء الاصطناعي."
            )
        }.toString()
    }

    private fun errorEvaluationJson(status: String, feedback: String): String {
        return JSONObject().apply {
            put("score", 0.0)
            put("status", status)
            put("feedback", feedback)
        }.toString()
    }

    suspend fun getDailySpiritualAdvice(timeOfDay: String): String = withContext(Dispatchers.Default) {
        getDefaultAdvice(timeOfDay)
    }

    suspend fun evaluateMemorization(
        surahName: String,
        correctText: String,
        recitationText: String
    ): String = withContext(Dispatchers.IO) {
        val trimmed = recitationText.trim()
        if (trimmed.length < 5) {
            return@withContext errorEvaluationJson(
                "تسميع قصير جداً ⚠️",
                "أهلاً بك يا طيب، يبدو أن النص المكتوب قصير جداً أو فارغ. يرجى تدوين ما تيسر لك من السورة الكريمة بوضوح لكي نتمكن من تدقيق ميزان حفظك وتلاوتك."
            )
        }

        val baseUrl = configuredBaseUrl() ?: return@withContext unconfiguredEvaluationJson()

        try {
            val payload = JSONObject().apply {
                put("surahName", surahName)
                put("correctText", correctText)
                put("recitationText", trimmed)
            }
            val request = buildPostRequest("$baseUrl/api/gemini/recitation-text", payload)
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    val serverMsg = runCatching {
                        if (!body.isNullOrBlank()) JSONObject(body).optString("error", "") else ""
                    }.getOrDefault("")
                    return@withContext errorEvaluationJson(
                        "تعذر التقييم",
                        serverMsg.ifBlank { "تعذر الاتصال بخادم التقييم الآمن (HTTP ${response.code})." }
                    )
                }
                val json = JSONObject(body)
                if (json.has("score") && json.has("status") && json.has("feedback")) {
                    json.toString()
                } else {
                    errorEvaluationJson(
                        "استجابة غير مكتملة",
                        "لم يتمكن الخادم من إرجاع نتيجة تقييم صالحة. يرجى المحاولة مجدداً."
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("GeminiApiClient", "Text recitation evaluation failed: ${e.message}")
            errorEvaluationJson(
                "تعذر الاتصال",
                "تعذر الاتصال بخادم التسميع الآمن حالياً. تأكد من اتصالك بالإنترنت ثم حاول مرة أخرى."
            )
        }
    }

    suspend fun evaluateAudioMemorization(
        surahName: String,
        correctText: String,
        audioBase64: String,
        mimeType: String
    ): String = withContext(Dispatchers.IO) {
        if (audioBase64.length < 24000) {
            return@withContext errorEvaluationJson(
                "تسجيل صامت أو قصير جداً ⚠️",
                "نعتذر يا طيب، يبدو أن التسجيل الصوتي صامت تماماً أو قصير جداً ولم يتضمن صوتاً واضحاً للتسميع والتدقيق. يُرجى التحدث بوضوح وقرب الميكروفون ثم إعادة المحاولة."
            )
        }

        if (audioBase64.length > 8_000_000) {
            return@withContext errorEvaluationJson(
                "الملف الصوتي كبير جداً ⚠️",
                "مدة التسجيل الصوتي أطول من الحد المسموح به للتحليل السريع. يرجى تقسيم التسميع إلى مقاطع أقصر."
            )
        }

        val baseUrl = configuredBaseUrl() ?: return@withContext unconfiguredEvaluationJson()

        try {
            val payload = JSONObject().apply {
                put("surahName", surahName)
                put("correctText", correctText)
                put("mimeType", mimeType)
                put("audioBase64", audioBase64)
            }
            android.util.Log.i(
                "GeminiApiClient",
                "Sending audio recitation: surah=$surahName, mime=$mimeType, base64Len=${audioBase64.length}, refChars=${correctText.length}"
            )
            val request = buildPostRequest("$baseUrl/api/gemini/recitation-audio", payload)
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                android.util.Log.i(
                    "GeminiApiClient",
                    "Audio recitation response: http=${response.code}, bodyLen=${body?.length ?: 0}"
                )
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    val serverMsg = runCatching {
                        if (!body.isNullOrBlank()) JSONObject(body).optString("error", "") else ""
                    }.getOrDefault("")
                    android.util.Log.w("GeminiApiClient", "Audio evaluation error (${response.code}): $serverMsg")
                    return@withContext errorEvaluationJson(
                        "تعذر التقييم الصوتي",
                        serverMsg.ifBlank { "تعذر الاتصال بخادم تحليل التلاوة الآمن (HTTP ${response.code})." }
                    )
                }
                val json = JSONObject(body)
                if (json.has("score") && json.has("status") && json.has("feedback")) {
                    json.toString()
                } else {
                    errorEvaluationJson(
                        "استجابة غير مكتملة",
                        "لم يتمكن الخادم من إرجاع نتيجة تقييم صالحة للتلاوة الصوتية."
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("GeminiApiClient", "Audio recitation evaluation failed: ${e.javaClass.simpleName}: ${e.message}")
            errorEvaluationJson(
                "تعذر الاتصال",
                "تعذر إرسال التلاوة إلى خادم التقييم الآمن حالياً. تأكد من اتصالك بالإنترنت ثم حاول مرة أخرى."
            )
        }
    }

    private fun getDefaultAdvice(timeOfDay: String): String {
        return when (timeOfDay.lowercase()) {
            "morning", "صباح" -> "أشرقت شمس يومك بفضل ربك، فاجعل أول أنفاسك شكراً واستغفاراً. اقرأ أذكار الصباح تكن في ذمة الله ومكنونه."
            "noon", "الظهر", "afternoon" -> "مضى شطر يومك وجاء وقت السكينة والتقاط الأنفاس، صلّ الظهر بهدوء وكن مع الخالق يكن معك في عجلتك."
            "evening", "المساء" -> "دنت غيوب النهار وحلّ المساء برحمته، ردد بقلب خاشع: 'يا حي يا قيوم برحمتك أستغيث' ليرتاح قلبك وتطمئن روحك."
            "night", "الليل" -> "هدأ الكون وسكن المغْنى، اختلِ بربك ولو بركعتين في جوف الليل وسله ما تشاء، فإن سهام الليل لا تخطئ."
            else -> "في كل لحظة وحين، رطب لسانك بذكر الله وتذكر أن ربك يحبك ويرعاك في كل خفقة قلب."
        }
    }
}
