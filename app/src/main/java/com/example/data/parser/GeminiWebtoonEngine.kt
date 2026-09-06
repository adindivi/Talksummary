package com.example.data.parser

import com.example.data.ChatDay
import com.example.data.api.Content as ApiContent
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.GeminiApiClient
import com.example.data.api.Part as ApiPart
import com.example.model.WebtoonCutItem
import com.example.model.WebtoonStoryResult
import com.example.util.AppLogger
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@JsonClass(generateAdapter = true)
data class WebtoonCutDto(
    @param:Json(name = "cutIndex") val cutIndex: Int? = null,
    @param:Json(name = "stage") val stage: String? = null,
    @param:Json(name = "title") val title: String? = null,
    @param:Json(name = "speaker") val speaker: String? = null,
    @param:Json(name = "emotionEmoji") val emotionEmoji: String? = null,
    @param:Json(name = "speechBubble") val speechBubble: String? = null,
    @param:Json(name = "soundEffect") val soundEffect: String? = null,
    @param:Json(name = "situation") val situation: String? = null
)

@JsonClass(generateAdapter = true)
data class WebtoonResponseDto(
    @param:Json(name = "cuts") val cuts: List<WebtoonCutDto>? = null
)

object GeminiWebtoonEngine {

    private const val TAG = "GeminiWebtoonEngine"

    private val moshi: Moshi by lazy {
        Moshi.Builder().build()
    }

    fun resolveRealModelName(model: String?): String {
        val trimmed = model?.trim().orEmpty()
        return when (trimmed) {
            "gemini-2.5-flash" -> "gemini-2.5-flash"
            "gemini-2.5-pro" -> "gemini-2.5-pro"
            "gemini-3.1-pro-preview" -> "gemini-3.1-pro-preview"
            else -> if (trimmed.isEmpty()) "gemini-3.5-flash" else trimmed
        }
    }

    /**
     * Gemini AI를 활용한 3컷 만화(웹툰) 스토리 자동 생성기.
     * API Key가 없거나, 네트워크 오류 또는 파싱 실패 시 100% 안전하게 온디바이스 스마트 Fallback 엔진으로 자동 대체됩니다.
     */
    suspend fun generateWebtoonStory(
        chatDay: ChatDay,
        roomName: String? = null,
        apiKey: String? = null,
        model: String? = null
    ): WebtoonStoryResult = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrBlank()) {
            AppLogger.d(TAG, "API Key is missing. Falling back to on-device offline webtoon generator.")
            return@withContext generateOfflineWebtoonStory(chatDay, roomName)
        }

        val chatRoomTitle = resolveRoomTitle(chatDay, roomName)
        val cleanMessages = extractCleanMessages(chatDay)

        if (cleanMessages.isEmpty()) {
            return@withContext generateOfflineWebtoonStory(chatDay, roomName)
        }

        val snippet = cleanMessages.take(40).joinToString("\n") { (sender, text) ->
            "$sender: $text"
        }

        val prompt = """
            당신은 재치 넘치는 인스타툰 / 4컷만화 전문 작가입니다.
            다음 카카오톡 대화를 바탕으로, 기승전결이 극적이고 유머러스한 [3컷 웹툰(만화) 콘티]를 작성해주세요.

            대화방: $chatRoomTitle
            대화 일자: ${chatDay.date}
            대화 내용:
            $snippet

            [작성 규칙]
            1. 반드시 정확히 3개의 컷(1컷: 발단, 2컷: 절정, 3컷: 결말)을 구성하세요.
            2. 각 컷마다 실제 대화에 참여한 발화자 중 1명을 'speaker'로 지정하세요.
            3. 'emotionEmoji'는 캐릭터의 감정을 생생하게 표현하는 이모지 1개(예: 🤩, 😱, 🍻, 🤔, 🔥, ⚡, 😆, 😭)를 넣으세요.
            4. 'speechBubble'은 만화 말풍선에 들어갈 핵심 대사(1~2문장, 위트있고 생생하게)를 작성하세요.
            5. 'soundEffect'는 만화 팝아트 효과음(예: 띠링~, 두-둥!, 쿵!, 와아아-!, 찰칵!, 찌릿⚡)을 넣어주세요.
            6. 'situation'은 만화 하단 나레이션 자막으로 들어갈 재치있는 상황 묘사를 1문장으로 작성하세요.
            7. 반드시 아래 JSON 형식으로만 응답하세요. 다른 설명이나 마크다운 외 문장은 포함하지 마세요.

            {
              "cuts": [
                {
                  "cutIndex": 1,
                  "stage": "1컷 [발단]",
                  "title": "컷 소제목",
                  "speaker": "발화자 이름",
                  "emotionEmoji": "🤩",
                  "speechBubble": "핵심 대사",
                  "soundEffect": "띠링~",
                  "situation": "상황 설명 자막"
                },
                {
                  "cutIndex": 2,
                  "stage": "2컷 [절정]",
                  "title": "컷 소제목",
                  "speaker": "발화자 이름",
                  "emotionEmoji": "😱",
                  "speechBubble": "핵심 대사",
                  "soundEffect": "두-둥!",
                  "situation": "상황 설명 자막"
                },
                {
                  "cutIndex": 3,
                  "stage": "3컷 [결말]",
                  "title": "컷 소제목",
                  "speaker": "발화자 이름",
                  "emotionEmoji": "🍻",
                  "speechBubble": "핵심 대사",
                  "soundEffect": "와아아-!",
                  "situation": "상황 설명 자막"
                }
              ]
            }
        """.trimIndent()

        val realModel = resolveRealModelName(model)
        val request = GenerateContentRequest(
            contents = listOf(ApiContent(parts = listOf(ApiPart(text = prompt)))),
            generationConfig = GenerationConfig(temperature = 0.5f)
        )

        try {
            AppLogger.d(TAG, "Requesting Gemini webtoon script with model $realModel...")
            val response = GeminiApiClient.service.generateContent(
                model = realModel,
                apiKey = apiKey,
                request = request
            )
            val responseText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (responseText.isNullOrBlank()) {
                AppLogger.w(TAG, "Gemini returned empty response. Falling back to offline engine.")
                return@withContext generateOfflineWebtoonStory(chatDay, roomName)
            }

            val parsedResult = parseGeminiWebtoonJson(responseText, chatRoomTitle, chatDay.date)
            if (parsedResult != null && parsedResult.cuts.size == 3) {
                AppLogger.d(TAG, "Gemini webtoon script generated and parsed successfully!")
                return@withContext parsedResult
            } else {
                AppLogger.w(TAG, "Parsed webtoon result invalid or cuts count != 3. Falling back to offline engine.")
                return@withContext generateOfflineWebtoonStory(chatDay, roomName)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Gemini webtoon generation failed (${e.message}). Gracefully falling back to offline engine.")
            return@withContext generateOfflineWebtoonStory(chatDay, roomName)
        }
    }

    /**
     * Gemini가 생성한 JSON 응답 텍스트를 파싱하여 WebtoonStoryResult로 변환
     */
    fun parseGeminiWebtoonJson(
        jsonText: String,
        chatRoomTitle: String,
        dateString: String
    ): WebtoonStoryResult? {
        try {
            var raw = jsonText.trim()
            if (raw.startsWith("```json")) {
                raw = raw.removePrefix("```json")
            } else if (raw.startsWith("```")) {
                raw = raw.removePrefix("```")
            }
            if (raw.endsWith("```")) {
                raw = raw.removeSuffix("```")
            }
            val firstBrace = raw.indexOf('{')
            val lastBrace = raw.lastIndexOf('}')
            if (firstBrace == -1 || lastBrace == -1 || lastBrace <= firstBrace) {
                return null
            }
            val cleanJson = raw.substring(firstBrace, lastBrace + 1).trim()

            val adapter = moshi.adapter(WebtoonResponseDto::class.java)
            val dto = adapter.fromJson(cleanJson) ?: return null
            val rawCuts = dto.cuts ?: return null
            if (rawCuts.size < 3) return null

            val cut1 = rawCuts[0].toWebtoonCutItem(1, "1컷 [발단]", 0xFFFFFBEB, 0xFFFEF3C7, "🤩", "띠링~")
            val cut2 = rawCuts[1].toWebtoonCutItem(2, "2컷 [절정]", 0xFFEFF6FF, 0xFFDBEAFE, "😱", "두-둥!")
            val cut3 = rawCuts[2].toWebtoonCutItem(3, "3컷 [결말]", 0xFFF0FDF4, 0xFFDCFCE7, "🍻", "와아아-!")

            return WebtoonStoryResult(
                chatRoomName = chatRoomTitle,
                dateString = dateString,
                cuts = listOf(cut1, cut2, cut3),
                isAiGenerated = true
            )
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to parse webtoon json: ${e.message}")
            return null
        }
    }

    private fun WebtoonCutDto.toWebtoonCutItem(
        index: Int,
        defaultStage: String,
        panelStart: Long,
        panelEnd: Long,
        fallbackEmoji: String,
        fallbackSound: String
    ): WebtoonCutItem {
        return WebtoonCutItem(
            cutIndex = this.cutIndex ?: index,
            stage = if (!this.stage.isNullOrBlank()) this.stage else defaultStage,
            title = if (!this.title.isNullOrBlank()) this.title else "에피소드 $index",
            speaker = if (!this.speaker.isNullOrBlank()) this.speaker else "대화 참여자",
            emotionEmoji = if (!this.emotionEmoji.isNullOrBlank()) this.emotionEmoji else fallbackEmoji,
            speechBubble = if (!this.speechBubble.isNullOrBlank()) this.speechBubble else "함께 즐겁게 소통했어요!",
            soundEffect = if (!this.soundEffect.isNullOrBlank()) this.soundEffect else fallbackSound,
            situation = if (!this.situation.isNullOrBlank()) this.situation else "대화의 맥락이 흥미롭게 이어졌습니다.",
            panelColorStart = panelStart,
            panelColorEnd = panelEnd
        )
    }

    /**
     * 100% 온디바이스 완전 오프라인 무지연 스마트 3컷 웹툰 생성기 (Fallback 및 무키 사용자용)
     */
    fun generateOfflineWebtoonStory(chatDay: ChatDay, roomName: String? = null): WebtoonStoryResult {
        val chatRoomTitle = resolveRoomTitle(chatDay, roomName)
        val rawMessagesList = mutableListOf<Pair<String, String>>()
        val senderFrequencyMap = mutableMapOf<String, Int>()

        for (msg in chatDay.messages) {
            var sender = msg.sender.trim()
            if (sender.endsWith("님님")) {
                sender = sender.substring(0, sender.length - 1)
            }
            val text = msg.text.trim()

            if (text.isNotBlank() &&
                !text.contains("사진") &&
                !text.contains("이모티콘") &&
                !text.contains("동영상") &&
                !text.contains("음성메시지") &&
                !text.contains("파일") &&
                text.length > 2
            ) {
                rawMessagesList.add(Pair(sender, text))
                senderFrequencyMap[sender] = (senderFrequencyMap[sender] ?: 0) + 1
            }
        }

        val meaningfulMessages = rawMessagesList.filter { (_, text) ->
            val clean = text.replace(Regex("@[^\\s]+"), "").trim()
            clean.length > 2 && !clean.matches(Regex("^(ㅋㅋ+|ㅎㅎ+|네+|응+|아+|오케이|알겠어|안녕|수고|감사|ㅇㅇ+|ㄷㄷ+|ㅠㅠ+|ㅜㅜ+).*"))
        }

        val rawTopSender = senderFrequencyMap.maxByOrNull { it.value }?.key
            ?: chatDay.participants.firstOrNull()
            ?: "참여자"
        val topSender = if (rawTopSender.endsWith("님님")) rawTopSender.substring(0, rawTopSender.length - 1) else rawTopSender

        fun formatBubble(pair: Pair<String, String>?, fallback: String): String {
            if (pair == null) return fallback
            val rawText = pair.second
                .replace(Regex("@[^\\s]+"), "")
                .replace(Regex("^[^:]+:\\s*"), "")
                .trim()
            return if (rawText.length > 55) rawText.substring(0, 55) + "..." else rawText
        }

        // 1번(시작), 2번(절정), 3번(결말) Distinct Selection
        val startPair = meaningfulMessages.firstOrNull() ?: rawMessagesList.firstOrNull()

        val remainingForAgenda = meaningfulMessages.filterNot { it == startPair }
        val agendaPair = remainingForAgenda.find {
            val t = it.second
            t.contains("?") || t.contains("어때") || t.contains("일정") ||
            t.contains("약속") || t.contains("시간") || t.contains("장소") ||
            t.contains("회의") || t.contains("확인")
        } ?: remainingForAgenda.maxByOrNull { it.second.length }
          ?: rawMessagesList.filterNot { it == startPair }.getOrNull(rawMessagesList.size / 2)

        val remainingForConclusion = (meaningfulMessages + rawMessagesList)
            .distinct()
            .filterNot { it == startPair || it == agendaPair }
        val conclusionPair = remainingForConclusion.lastOrNull()

        val cut1Speaker = startPair?.first ?: topSender
        val cut1Speech = formatBubble(startPair, "오늘도 단톡방에 새로운 하루가 시작되었어요!")
        val cut1 = WebtoonCutItem(
            cutIndex = 1,
            stage = "1컷 [발단]",
            title = "'$chatRoomTitle' 대화의 서막",
            speaker = cut1Speaker,
            emotionEmoji = "🤩",
            speechBubble = cut1Speech,
            soundEffect = "띠링~",
            situation = "평화로운 단톡방에 첫 소통의 신호탄이 울렸다.",
            panelColorStart = 0xFFFFFBEB,
            panelColorEnd = 0xFFFEF3C7
        )

        val cut2Speaker = agendaPair?.first ?: topSender
        val cut2Speech = formatBubble(agendaPair, "오늘 주요 안건에 대해 이야기 나눠봐요!")
        val cut2Emoji = if (cut2Speech.contains("?") || cut2Speech.contains("어때")) "🤔" else "🔥"
        val cut2 = WebtoonCutItem(
            cutIndex = 2,
            stage = "2컷 [절정]",
            title = "${topSender}님의 핵심 화두",
            speaker = cut2Speaker,
            emotionEmoji = cut2Emoji,
            speechBubble = cut2Speech,
            soundEffect = "두-둥!",
            situation = "열띤 의견 교환과 함께 대화의 몰입도가 최고조에 달했다.",
            panelColorStart = 0xFFEFF6FF,
            panelColorEnd = 0xFFDBEAFE
        )

        val cut3Speaker = conclusionPair?.first ?: (chatDay.participants.lastOrNull() ?: topSender)
        val cut3Speech = formatBubble(conclusionPair, "다들 수고 많으셨습니다! 좋은 하루 보내세요.")
        val cut3 = WebtoonCutItem(
            cutIndex = 3,
            stage = "3컷 [결말]",
            title = "대화 타결 및 훈훈한 마무리",
            speaker = cut3Speaker,
            emotionEmoji = "🍻",
            speechBubble = cut3Speech,
            soundEffect = "와아아-!",
            situation = "모든 안건이 원만하게 정리되며 오늘의 대화가 마무리되었다.",
            panelColorStart = 0xFFF0FDF4,
            panelColorEnd = 0xFFDCFCE7
        )

        return WebtoonStoryResult(
            chatRoomName = chatRoomTitle,
            dateString = chatDay.date,
            cuts = listOf(cut1, cut2, cut3),
            isAiGenerated = false
        )
    }

    private fun resolveRoomTitle(chatDay: ChatDay, roomName: String?): String {
        return if (!roomName.isNullOrBlank()) {
            roomName
        } else if (chatDay.participants.isNotEmpty()) {
            val names = chatDay.participants.take(3).joinToString(", ")
            if (chatDay.participants.size > 3) "$names 외 대화방" else "$names 대화방"
        } else {
            "카카오톡 대화방"
        }
    }

    private fun extractCleanMessages(chatDay: ChatDay): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        for (msg in chatDay.messages) {
            var sender = msg.sender.trim()
            if (sender.endsWith("님님")) {
                sender = sender.substring(0, sender.length - 1)
            }
            val text = msg.text.trim()
            if (text.isNotBlank() &&
                !text.contains("사진") &&
                !text.contains("이모티콘") &&
                !text.contains("동영상") &&
                !text.contains("음성메시지") &&
                !text.contains("파일") &&
                text.length > 2
            ) {
                val clean = text.replace(Regex("@[^\\s]+"), "").trim()
                if (!clean.matches(Regex("^(ㅋㅋ+|ㅎㅎ+|네+|응+|아+|오케이|알겠어|안녕|수고|감사|ㅇㅇ+|ㄷㄷ+|ㅠㅠ+|ㅜㅜ+).*"))) {
                    result.add(Pair(sender, clean))
                }
            }
        }
        return result
    }
}
