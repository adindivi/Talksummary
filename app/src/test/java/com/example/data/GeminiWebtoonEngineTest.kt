package com.example.data

import com.example.data.parser.GeminiWebtoonEngine
import org.junit.Assert.*
import org.junit.Test

class GeminiWebtoonEngineTest {

    @Test
    fun generateOfflineWebtoonStory_creates3DistinctCuts() {
        val messages = listOf(
            Message("김철수", "10:00", "오늘 저녁에 삼겹살 어때요? 다들 시간 되시나요?"),
            Message("이영희", "10:02", "오 좋아요! 강남역 고기집 7시 어떠세요?"),
            Message("박민수", "10:05", "저도 퇴근하고 바로 가면 7시 딱 맞습니다!"),
            Message("김철수", "10:06", "그럼 7시에 강남역 11번 출구에서 봬요!")
        )
        val chatDay = ChatDay(
            date = "2026-03-06",
            messages = messages,
            summary = "",
            keywords = listOf("삼겹살", "강남역"),
            participants = listOf("김철수", "이영희", "박민수"),
            msgCount = 4
        )

        val webtoon = GeminiWebtoonEngine.generateOfflineWebtoonStory(chatDay)

        assertNotNull(webtoon)
        assertEquals(3, webtoon.cuts.size)
        assertEquals("2026-03-06", webtoon.dateString)
        assertFalse(webtoon.isAiGenerated)

        val cut1 = webtoon.cuts[0]
        val cut2 = webtoon.cuts[1]
        val cut3 = webtoon.cuts[2]

        assertEquals(1, cut1.cutIndex)
        assertEquals("1컷 [발단]", cut1.stage)
        assertEquals("띠링~", cut1.soundEffect)
        assertFalse(cut1.speechBubble.isBlank())

        assertEquals(2, cut2.cutIndex)
        assertEquals("2컷 [절정]", cut2.stage)
        assertEquals("두-둥!", cut2.soundEffect)
        assertFalse(cut2.speechBubble.isBlank())

        assertEquals(3, cut3.cutIndex)
        assertEquals("3컷 [결말]", cut3.stage)
        assertEquals("와아아-!", cut3.soundEffect)
        assertFalse(cut3.speechBubble.isBlank())

        // Ensure distinct bubbles
        assertNotEquals(cut1.speechBubble, cut2.speechBubble)
        assertNotEquals(cut2.speechBubble, cut3.speechBubble)
    }

    @Test
    fun generateOfflineWebtoonStory_handlesEmptyMessagesWithoutCrashing() {
        val chatDay = ChatDay(
            date = "2026-03-01",
            messages = emptyList(),
            summary = "",
            keywords = emptyList(),
            participants = emptyList(),
            msgCount = 0
        )

        val webtoon = GeminiWebtoonEngine.generateOfflineWebtoonStory(chatDay)
        assertNotNull(webtoon)
        assertEquals(3, webtoon.cuts.size)
        assertEquals("카카오톡 대화방", webtoon.chatRoomName)
    }

    @Test
    fun parseGeminiWebtoonJson_parsesValidMarkdownWrappedJson() {
        val jsonSample = """
            ```json
            {
              "cuts": [
                {
                  "cutIndex": 1,
                  "stage": "1컷 [발단]",
                  "title": "금요일의 번개 제안",
                  "speaker": "김철수",
                  "emotionEmoji": "🤩",
                  "speechBubble": "오늘 불금인데 삼겹살에 소주 한잔 어때요?!",
                  "soundEffect": "띠링~",
                  "situation": "평화로운 단톡방에 회식 번개가 투척되었다."
                },
                {
                  "cutIndex": 2,
                  "stage": "2컷 [절정]",
                  "title": "야근의 그림자",
                  "speaker": "이영희",
                  "emotionEmoji": "😱",
                  "speechBubble": "헐 오늘 보고서 제출이라 야근 확정인데 ㅠㅠ",
                  "soundEffect": "두-둥!",
                  "situation": "청천벽력 같은 야근 소식에 모두가 긴장한다."
                },
                {
                  "cutIndex": 3,
                  "stage": "3컷 [결말]",
                  "title": "8시 반 극적 합의",
                  "speaker": "박민수",
                  "emotionEmoji": "🍻",
                  "speechBubble": "그럼 8시 반에 회사 앞 맛집으로 집결합시다!",
                  "soundEffect": "와아아-!",
                  "situation": "완벽한 조율로 회식은 마침내 성사되었다."
                }
              ]
            }
            ```
        """.trimIndent()

        val result = GeminiWebtoonEngine.parseGeminiWebtoonJson(
            jsonText = jsonSample,
            chatRoomTitle = "개발팀 단톡방",
            dateString = "2026-03-06"
        )

        assertNotNull(result)
        assertTrue(result!!.isAiGenerated)
        assertEquals("개발팀 단톡방", result.chatRoomName)
        assertEquals(3, result.cuts.size)

        assertEquals("김철수", result.cuts[0].speaker)
        assertEquals("띠링~", result.cuts[0].soundEffect)
        assertEquals("🤩", result.cuts[0].emotionEmoji)

        assertEquals("이영희", result.cuts[1].speaker)
        assertEquals("두-둥!", result.cuts[1].soundEffect)
        assertEquals("😱", result.cuts[1].emotionEmoji)

        assertEquals("박민수", result.cuts[2].speaker)
        assertEquals("와아아-!", result.cuts[2].soundEffect)
        assertEquals("🍻", result.cuts[2].emotionEmoji)
    }

    @Test
    fun parseGeminiWebtoonJson_returnsNullForInvalidJson() {
        val invalidText = "죄송합니다, 대화 내용을 요약할 수 없습니다."
        val result = GeminiWebtoonEngine.parseGeminiWebtoonJson(
            jsonText = invalidText,
            chatRoomTitle = "테스트방",
            dateString = "2026-03-06"
        )
        assertNull(result)
    }
}
