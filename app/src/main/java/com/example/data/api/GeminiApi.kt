package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class Part(
    @param:Json(name = "text") val text: String
)

@JsonClass(generateAdapter = true)
data class Content(
    @param:Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @param:Json(name = "temperature") val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @param:Json(name = "contents") val contents: List<Content>,
    @param:Json(name = "generationConfig") val generationConfig: GenerationConfig? = null,
    @param:Json(name = "systemInstruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @param:Json(name = "content") val content: Content
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @param:Json(name = "candidates") val candidates: List<Candidate>
)

data class GeminiErrorInfo(
    val title: String,
    val description: String,
    val isQuotaExceeded: Boolean = false,
    val isInvalidKey: Boolean = false
)

object GeminiErrorClassifier {
    fun classify(code: Int, errorBody: String): GeminiErrorInfo {
        return when (code) {
            400 -> {
                if (errorBody.contains("API_KEY_INVALID", ignoreCase = true) || errorBody.contains("key", ignoreCase = true)) {
                    GeminiErrorInfo(
                        title = "API 키 오류",
                        description = "등록된 Google Gemini API 키가 유효하지 않거나 잘못 입력되었습니다. [설정] 메뉴에서 API 키를 다시 확인해 주세요.",
                        isInvalidKey = true
                    )
                } else {
                    GeminiErrorInfo(
                        title = "잘못된 요청 형식",
                        description = "요청 본문 형식이 올바르지 않습니다. 다시 시도해 주세요."
                    )
                }
            }
            403 -> {
                GeminiErrorInfo(
                    title = "API 접근 권한 제한",
                    description = "해당 API 키에 권한이 없거나 모델 사용이 제한되었습니다. Google AI Studio 콘솔을 확인해 주세요.",
                    isInvalidKey = true
                )
            }
            429 -> {
                GeminiErrorInfo(
                    title = "무료 할당량(Quota) 초과",
                    description = "Google Gemini API의 분당 또는 일일 무료 요청 한도에 도달했습니다. 약 1~2분 후 다시 시도하시거나, Google AI Studio에서 새 키를 등록해 주세요.",
                    isQuotaExceeded = true
                )
            }
            500, 502, 503, 504 -> {
                GeminiErrorInfo(
                    title = "AI 서버 일시적 지연",
                    description = "Google AI 서버가 일시적으로 혼잡하거나 점검 중입니다. 잠시 후 다시 시도해 주세요."
                )
            }
            else -> {
                val snippet = if (errorBody.length > 100) errorBody.take(100) + "..." else errorBody
                GeminiErrorInfo(
                    title = "AI 통신 오류 (코드: $code)",
                    description = "Google AI 서버와 통신 중 문제가 발생했습니다: $snippet"
                )
            }
        }
    }

    fun classifyException(e: Throwable): GeminiErrorInfo {
        return when (e) {
            is java.net.UnknownHostException -> GeminiErrorInfo(
                title = "네트워크 연결 끊김",
                description = "인터넷에 연결되어 있지 않습니다. Wi-Fi 또는 모바일 데이터 연결을 확인해 주세요."
            )
            is java.net.SocketTimeoutException -> GeminiErrorInfo(
                title = "응답 시간 초과",
                description = "AI 서버의 응답 시간이 초과되었습니다. 네트워크 상태를 확인하거나 잠시 후 다시 시도해 주세요."
            )
            else -> GeminiErrorInfo(
                title = "AI 요약 처리 오류",
                description = e.localizedMessage ?: "알 수 없는 오류가 발생했습니다."
            )
        }
    }
}

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
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}
