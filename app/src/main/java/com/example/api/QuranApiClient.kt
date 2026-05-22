package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@JsonClass(generateAdapter = true)
data class AyahResponse(
    @Json(name = "number") val number: Int,
    @Json(name = "text") val text: String,
    @Json(name = "numberInSurah") val numberInSurah: Int
)

@JsonClass(generateAdapter = true)
data class SurahData(
    @Json(name = "number") val number: Int,
    @Json(name = "name") val name: String,
    @Json(name = "englishName") val englishName: String,
    @Json(name = "numberOfAyahs") val numberOfAyahs: Int,
    @Json(name = "ayahs") val ayahs: List<AyahResponse>
)

@JsonClass(generateAdapter = true)
data class SurahApiResponse(
    @Json(name = "code") val code: Int,
    @Json(name = "status") val status: String,
    @Json(name = "data") val data: SurahData
)

interface QuranApiService {
    @GET("v1/surah/{number}")
    suspend fun getSurah(
        @Path("number") surahNumber: Int
    ): SurahApiResponse
}

object QuranApiClient {
    private const val BASE_URL = "https://api.alquran.cloud/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val service: QuranApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(QuranApiService::class.java)
    }

    suspend fun fetchSurahVerses(surahId: Int): List<String>? = withContext(Dispatchers.IO) {
        try {
            val response = service.getSurah(surahId)
            if (response.code == 200) {
                response.data.ayahs.map { it.text }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
