package com.example.data

import org.junit.Assert.*
import org.junit.Test

class ChatDayModelTest {

    @Test
    fun chatDay_properties_initializedProperly() {
        val messages = listOf(
            Message(sender = "홍길동", time = "오전 10:00", text = "안녕하세요"),
            Message(sender = "이순신", time = "오전 10:05", text = "반갑습니다")
        )
        val chatDay = ChatDay(
            date = "2026-05-10",
            messages = messages,
            summary = "5월 10일 대화 요약입니다.",
            keywords = listOf("인사", "대화"),
            participants = listOf("홍길동", "이순신"),
            msgCount = 2
        )

        assertEquals("2026-05-10", chatDay.date)
        assertEquals(2, chatDay.messages.size)
        assertEquals("5월 10일 대화 요약입니다.", chatDay.summary)
        assertEquals(2, chatDay.keywords.size)
        assertEquals(listOf("홍길동", "이순신"), chatDay.participants)
        assertEquals(2, chatDay.msgCount)
    }

    @Test
    fun timelineFiltering_byStartDate_filtersCorrectly() {
        val days = listOf(
            ChatDay("2026-05-01", emptyList(), "", emptyList(), emptyList(), 0),
            ChatDay("2026-05-10", emptyList(), "", emptyList(), emptyList(), 0),
            ChatDay("2026-05-20", emptyList(), "", emptyList(), emptyList(), 0)
        )

        val startDate = "2026-05-10"
        val filtered = days.filter { it.date >= startDate }

        assertEquals(2, filtered.size)
        assertEquals("2026-05-10", filtered[0].date)
        assertEquals("2026-05-20", filtered[1].date)
    }

    @Test
    fun timelineFiltering_byEndDate_filtersCorrectly() {
        val days = listOf(
            ChatDay("2026-05-01", emptyList(), "", emptyList(), emptyList(), 0),
            ChatDay("2026-05-10", emptyList(), "", emptyList(), emptyList(), 0),
            ChatDay("2026-05-20", emptyList(), "", emptyList(), emptyList(), 0)
        )

        val endDate = "2026-05-10"
        val filtered = days.filter { it.date <= endDate }

        assertEquals(2, filtered.size)
        assertEquals("2026-05-01", filtered[0].date)
        assertEquals("2026-05-10", filtered[1].date)
    }

    @Test
    fun timelineFiltering_byDateRange_filtersCorrectly() {
        val days = listOf(
            ChatDay("2026-05-01", emptyList(), "", emptyList(), emptyList(), 0),
            ChatDay("2026-05-05", emptyList(), "", emptyList(), emptyList(), 0),
            ChatDay("2026-05-10", emptyList(), "", emptyList(), emptyList(), 0),
            ChatDay("2026-05-15", emptyList(), "", emptyList(), emptyList(), 0),
            ChatDay("2026-05-20", emptyList(), "", emptyList(), emptyList(), 0)
        )

        val start = "2026-05-05"
        val end = "2026-05-15"
        val filtered = days.filter { it.date >= start && it.date <= end }

        assertEquals(3, filtered.size)
        assertEquals("2026-05-05", filtered[0].date)
        assertEquals("2026-05-10", filtered[1].date)
        assertEquals("2026-05-15", filtered[2].date)
    }

    @Test
    fun participantStatistics_calculatesCorrectCountsAndRanking() {
        val messages = listOf(
            Message("홍길동", "10:00", "메시지 1"),
            Message("이순신", "10:01", "메시지 2"),
            Message("홍길동", "10:02", "메시지 3"),
            Message("강감찬", "10:03", "메시지 4"),
            Message("홍길동", "10:04", "메시지 5"),
            Message("이순신", "10:05", "메시지 6")
        )

        val counts = messages.groupBy { it.sender }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }

        assertEquals(3, counts.size)
        assertEquals("홍길동" to 3, counts[0])
        assertEquals("이순신" to 2, counts[1])
        assertEquals("강감찬" to 1, counts[2])

        val topSpeakers = counts.take(2).map { it.first }
        assertEquals(listOf("홍길동", "이순신"), topSpeakers)
    }
}
