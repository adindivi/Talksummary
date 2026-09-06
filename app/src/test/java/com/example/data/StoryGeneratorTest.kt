package com.example.data

import com.example.data.parser.StoryGenerator
import org.junit.Assert.*
import org.junit.Test

class StoryGeneratorTest {

    @Test
    fun generate3CardStory_creates3DistinctCardsWithCorrectStages() {
        val messages = listOf(
            Message("김철수", "10:00", "오늘 점심 뭐 드실 건가요?"),
            Message("이영희", "10:02", "저는 파스타 먹고 싶어요. 강남역 새로 생긴 파스타집 어때요?"),
            Message("박민수", "10:05", "좋습니다! 12시 30분에 거기서 만나요."),
            Message("김철수", "10:06", "네 12시 반에 봬요!")
        )
        val chatDay = ChatDay(
            date = "2026-03-06",
            messages = messages,
            summary = "",
            keywords = listOf("점심", "파스타"),
            participants = listOf("김철수", "이영희", "박민수"),
            msgCount = 4
        )

        val story = StoryGenerator.generate3CardStory(chatDay)

        assertEquals("김철수, 이영희, 박민수 대화방", story.chatRoomName)
        assertEquals("2026-03-06", story.dateString)

        assertEquals("1장: 대화의 시작", story.card1.stage)
        assertEquals("2장: 핵심 안건", story.card2.stage)
        assertEquals("3장: 최종 결론", story.card3.stage)

        assertTrue("Card 1 should contain sender or text", story.card1.story.contains("김철수"))
        assertTrue("Card 2 should contain agenda/question", story.card2.story.contains("이영희") || story.card2.story.contains("어때"))
        assertTrue("Card 3 should conclude", story.card3.story.contains("박민수") || story.card3.story.contains("봬요") || story.card3.story.contains("만나요"))

        // Ensure distinctness
        assertNotEquals(story.card1.story, story.card2.story)
        assertNotEquals(story.card2.story, story.card3.story)
    }

    @Test
    fun generate3CardStory_handlesEmptyMessagesGracefullyWithoutCrashing() {
        val chatDay = ChatDay(
            date = "2026-03-01",
            messages = emptyList(),
            summary = "",
            keywords = emptyList(),
            participants = emptyList(),
            msgCount = 0
        )

        val story = StoryGenerator.generate3CardStory(chatDay)

        assertNotNull(story)
        assertEquals("카카오톡 대화방", story.chatRoomName)
        assertEquals("2026-03-01", story.dateString)
        assertFalse(story.card1.story.isBlank())
        assertFalse(story.card2.story.isBlank())
        assertFalse(story.card3.story.isBlank())
    }

    @Test
    fun generate3CardStory_filtersNoiseAndMediaTags() {
        val messages = listOf(
            Message("김철수", "09:00", "사진"),
            Message("이영희", "09:01", "이모티콘"),
            Message("김철수", "09:02", "동영상"),
            Message("이영희", "09:03", "ㅋㅋㅋㅋ"),
            Message("김철수", "09:05", "실제 회의 일정은 오늘 오후 3시 대회의실입니다."),
            Message("박민수", "09:06", "안건 자료는 미리 출력해 두겠습니다."),
            Message("이영희", "09:10", "네 확인했습니다. 오후 3시에 뵙겠습니다.")
        )
        val chatDay = ChatDay(
            date = "2026-03-06",
            messages = messages,
            summary = "",
            keywords = listOf("회의", "일정"),
            participants = listOf("김철수", "이영희", "박민수"),
            msgCount = 7
        )

        val story = StoryGenerator.generate3CardStory(chatDay)

        assertFalse(story.card1.story.contains("사진"))
        assertFalse(story.card1.story.contains("이모티콘"))
        assertFalse(story.card2.story.contains("ㅋㅋㅋㅋ"))
        assertTrue(story.card1.story.contains("회의 일정") || story.card2.story.contains("회의 일정"))
    }
}