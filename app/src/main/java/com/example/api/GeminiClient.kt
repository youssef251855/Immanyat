package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>
)

@JsonClass(generateAdapter = true)
data class PartResponse(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class ContentResponse(
    @Json(name = "parts") val parts: List<PartResponse>
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: ContentResponse
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiApiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
    }

    suspend fun getDailySpiritualAdvice(timeOfDay: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext getDefaultAdvice(timeOfDay)
        }

        val prompt = """
            أنت مساعد روحي إسلامي رقيق وبليغ في تطبيق "إيمانيات". 
            الوقت الحالي من اليوم هو: $timeOfDay.
            قدم نصيحة روحانية، لفتة تدبرية، أو اقتراحاً لذكر يناسب هذا الوقت تحديداً (الصباح، الظهر، المساء، الليل).
            القوانين:
            1. يجب أن تكون بلغة عربية فصحى رفيعة، ملهمة، هادئة ودافئة جداً.
            2. لا تزيد الإجابة عن ثلاثة أسطر قصيرة ومركزة.
            3. تجنب الرموز السيئة، واجعلها تبدو كبطاقة روحانية دافئة ومريحة تسعد القلب والروح.
            4. اقترح في النهاية عبارة ذكر واحدة خفيفة.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        try {
            val response = service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: getDefaultAdvice(timeOfDay)
        } catch (e: Exception) {
            e.printStackTrace()
            getDefaultAdvice(timeOfDay)
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
