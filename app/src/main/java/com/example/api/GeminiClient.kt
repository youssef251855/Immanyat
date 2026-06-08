package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Path
import java.util.concurrent.TimeUnit
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@JsonClass(generateAdapter = true)
data class InlineData(
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "data") val data: String
)

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inlineData") val inlineData: InlineData? = null
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
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
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

    private suspend fun generateWithFallback(
        apiKey: String,
        request: GenerateContentRequest,
        preferredModel: String = "gemini-2.5-flash"
    ): GenerateContentResponse {
        val models = listOf(
            preferredModel,
            "gemini-2.5-flash",
            "gemini-1.5-flash",
            "gemini-2.0-flash",
            "gemini-3.5-flash"
        ).distinct()
        
        var lastException: Exception? = null
        for (model in models) {
            try {
                return service.generateContent(model, apiKey, request)
            } catch (e: Exception) {
                lastException = e
                android.util.Log.e("GeminiApiClient", "Model $model failed: ${e.message}. Trying next fallback...")
            }
        }
        throw lastException ?: Exception("تعذر الاتصال بجميع قنوات الذكاء الاصطناعي")
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
            val response = generateWithFallback(apiKey, request, "gemini-2.5-flash")
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: getDefaultAdvice(timeOfDay)
        } catch (e: Exception) {
            android.util.Log.e("GeminiApiClient", "spiritual advice error", e)
            getDefaultAdvice(timeOfDay)
        }
    }

    suspend fun evaluateMemorization(surahName: String, correctText: String, recitationText: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val isMock = apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY"

        val trimmed = recitationText.trim()
        if (trimmed.length < 5) {
            return@withContext """
                {"score": 0.0, "status": "تسميع قصير جداً ⚠️", "feedback": "أهلاً بك يا طيب، يبدو أن النص المكتوب قصير جداً أو فارغ. يرجى تدوين ما تيسر لك من السورة الكريمة بوضوح لكي نتمكن من تدقيق ميزان حفظك وتلاوتك."}
            """.trimIndent()
        }

        if (isMock) {
            return@withContext """
                {"score": 8.5, "status": "جيد جداً (محاكاة)", "feedback": "أهلاً بك يا طيب، مخرج الحروف وتدقيق الكلمات وتسميعك متقارب وصحيح لولا بعض الأخطاء البسيطة في أطراف الكلمات (هذا التقييم هو وضع محاكاة لعدم توفر مفتاح Gemini API في إعدادات جهازك). يرجى التثبيت واستكمال الحفظ."}
            """.trimIndent()
        }

        val prompt = """
            أنت مدقق حفظ وتسميع القرآن الكريم ومعلم تجويد خبير ومحفز في تطبيق "إيمانيات".
            السورة التي يحاول المستخدم تسميعها هي: سورة $surahName.
            النص الصحيح الكامل لهذه السورة من المصحف الشريف هو:
            $correctText
            
            ملاحظة هامة جداً: إذا كانت قيمة النص المذكور أعلاه فارغة أو تحتوي فقط على البسملة أو غير مكتملة، يرجى الاعتماد تماماً على معرفتك الموسوعية الكاملة والمحكمة بآيات وسور القرآن الكريم العظيم لسورة $surahName بالرسم العثماني الصحيح لتصحيح وتدقيق تسميع المستخدم.
            
            نص التسميع الذي قدمه المستخدم:
            "$recitationText"
            
            الرجاء إجراء تدقيق تفصيلي للنص المقدم مقابل النص الصحيح للسورة:
            1. حدد بدقة الكلمات أو الآيات المفقودة، المغلوطة، أو التي حل محلها كلمة أخرى وصححها.
            2. قيم مستوى جودة وصحة الحفظ والترتيل بوضع درجة حاسمة نهائية من 10 (مثلاً: 8.5/10).
            3. أذكر كلمات التشجيع والتوجيه والتعليم اللطيف للقلب لنيل همة للحفظ المثالي.
            4. يجب أن تكون الإجابة بتنسيق JSON حصراً ولن تقبل أي نص آخر خارج كتل الـ JSON. يجب دائمًا استخدام هذا التنسيق بالضبط:
               {
                 "score": 9.0,
                 "status": "رائع وممتاز",
                 "feedback": "أحسنت القراءة والمتابعة في هذا العمل الطيب..."
               }
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        try {
            val response = generateWithFallback(apiKey, request, "gemini-2.5-flash")
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: """{"score":0.0,"status":"حدث خطأ","feedback":"نعتذر، لم نتمكن من الحصول على رد من خادم الذكاء الاصطناعي."}"""
        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = e.localizedMessage ?: e.message ?: e.toString()
            """{"score":0.0,"status":"حدث خطأ","feedback":"نعتذر، لم نتمكن من الاتصال بالذكاء الاصطناعي حالياً. تفاصيل الخطأ الفني: $errorMsg"}"""
        }
    }

    suspend fun evaluateAudioMemorization(surahName: String, correctText: String, audioBase64: String, mimeType: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val isMock = apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY"

        // Safe safeguard: check if the audio recording is extremely small (representing a silent or near-instant click)
        if (audioBase64.length < 24000) {
            return@withContext """
                {"score": 0.0, "status": "تسجيل صامت أو قصير جداً ⚠️", "feedback": "نعتذر يا طيب، يبدو أن التسجيل الصوتي صامت تماماً أو قصير جداً ولم يتضمن صوتاً واضحاً للتسميع والتدقيق. يُرجى التحدث بوضوح وقرب الميكروفون ثم إعادة المحاولة للتسميع والتدقيق الذكي."}
            """.trimIndent()
        }

        if (isMock) {
            return@withContext """
                {"score": 9.2, "status": "رائع وممتاز 👍 (محاكاة)", "feedback": "(وضع محاكاة) تلاوة ودافع إيماني طيب في آيات سورة $surahName! مخرج الحروف وتدقيق الكلمات ممتازين جداً في هذا الملف المسجل. نوصيك بمواصلة هذا النور والعطاء الإيماني!"}
            """.trimIndent()
        }

        val prompt = """
            أنت مدقق حفظ وتسميع القرآن الكريم العظيم ومعلم تجويد خبير رصين ومحفز في تطبيق "إيمانيات".
            السورة التي يحاول المستخدم تسميعها تلاوة بالصوت هي: سورة $surahName.
            النص الصحيح المعتمد بالكامل لهذه السورة من المصحف الشريف هو:
            $correctText
            
            المطلوب منك بدقة بالغة:
            1. استمع الملف الصوتي المرفق (audio-data) وقم بتفريغه نصياً ومقارنته بالآيات الصحيحة لسورة $surahName.
            2. قيّم جودة الحفظ وصحة التلاوة بصورة دقيقة جداً وأعطِ درجة من 10.
            3. إذا كان الملف الصوتي يحتوي فقط على صمت أو تشويش أو ضوضاء عامة دون كلام مسموع يرتل القرآن، فيجب إعطاء درجة 0.0 (score = 0.0) مع وضع الحالة (status = "تسجيل غير مسموع ⚠️") وإخبار المستخدم بأدب ولطف في الملاحظات (feedback): "نعتذر لم نتمكن من سماع تلاوتك العطرة بوضوح. يرجى إعادة التسجيل بصوت مسموع وقرب الميكروفون."
            4. إذا كانت هناك أخطاء بالحفظ، بينها برفق وصحح الكلمات المفقودة أو المستبدلة.
            5. قدم نصائح تربوية وتجويدية دافئة تحفز النفس.
            6. يجب كتابة النتيجة بصيغة JSON حصرية ودون أي نصوص خارجها. استخدم هذا التنسيق بالضبط:
               {
                 "score": 8.5,
                 "status": "جيد جداً",
                 "feedback": "ملاحظات وتدقيق تلاوتك..."
               }
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(
                Part(text = prompt),
                Part(inlineData = InlineData(mimeType = mimeType, data = audioBase64))
            )))
        )

        try {
            val response = generateWithFallback(apiKey, request, "gemini-2.5-flash")
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: """{"score":0.0,"status":"حدث خطأ","feedback":"نعتذر، لم نتمكن من الحصول على رد من خادم الذكاء الاصطناعي."}"""
        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = e.localizedMessage ?: e.message ?: e.toString()
            """{"score":0.0,"status":"حدث خطأ","feedback":"نعتذر، لم نتمكن من الاتصال بالذكاء الاصطناعي حالياً. تفاصيل الخطأ الفني: $errorMsg"}"""
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
