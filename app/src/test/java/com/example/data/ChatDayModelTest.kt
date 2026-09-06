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

    @Test
    fun messageNoiseFiltering_removesAttachmentPlaceholdersAndSystemNoise() {
        val noiseRegex = Regex("^((\\[?(사진|이모티콘|동영상|음성메시지|파일|보이스톡|페이스톡|샵검색)\\]?(\\s*\\d+장)?)|(삭제된 메시지입니다\\.?))(\\s*)$")
        val messages = listOf(
            Message("김요한", "10:00", "사진"),
            Message("김건수", "10:01", "전주 한옥마을 다녀왔는데 옛날 생각나네요."),
            Message("심윤수", "10:02", "[이모티콘]"),
            Message("김요한", "10:03", "사진 3장"),
            Message("심윤수", "10:04", "내일 화상회의에서 뵙겠습니다!"),
            Message("시스템", "10:05", "삭제된 메시지입니다.")
        )

        val filtered = messages.filter { msg ->
            val text = msg.text.trim()
            text.isNotEmpty() && !noiseRegex.matches(text)
        }

        assertEquals(2, filtered.size)
        assertEquals("김건수", filtered[0].sender)
        assertEquals("전주 한옥마을 다녀왔는데 옛날 생각나네요.", filtered[0].text)
        assertEquals("심윤수", filtered[1].sender)
        assertEquals("내일 화상회의에서 뵙겠습니다!", filtered[1].text)
    }

    @Test
    fun timelineGroupingMode_enum_hasCorrectLabels() {
        assertEquals("일별", TimelineGroupingMode.DAY.label)
        assertEquals("월별", TimelineGroupingMode.MONTH.label)
        assertEquals("년도별", TimelineGroupingMode.YEAR.label)
    }

    @Test
    fun groupChatDaysByMonth_groupsCorrectlyAndAggregatesStats() {
        val days = listOf(
            ChatDay("2026-03-06", emptyList(), "[AI 정밀 요약]\n3월 6일 요약", listOf("약속", "홍대"), listOf("철수", "영희"), 50),
            ChatDay("2026-03-01", emptyList(), "3월 1일 기본 요약", listOf("삼일절", "휴일"), listOf("철수"), 20),
            ChatDay("2026-02-28", emptyList(), "[AI 정밀 요약]\n2월 28일 요약", listOf("마감", "개발"), listOf("민수", "철수"), 30),
            ChatDay("2025-12-25", emptyList(), "크리스마스 요약", listOf("성탄절", "선물"), listOf("영희"), 40)
        )

        val monthGroups = groupChatDaysByMonth(days)

        assertEquals(3, monthGroups.size)
        
        // 1st: 2026-03
        val march = monthGroups[0]
        assertEquals("2026-03", march.yearMonthKey)
        assertEquals("2026년 03월", march.displayTitle)
        assertEquals(2026, march.year)
        assertEquals(3, march.month)
        assertEquals(2, march.daysCount)
        assertEquals(70, march.totalMessages)
        assertEquals(1, march.summarizedDaysCount)
        assertTrue(march.topKeywords.contains("약속"))
        assertTrue(march.topSenders.contains("철수"))

        // 2nd: 2026-02
        val feb = monthGroups[1]
        assertEquals("2026-02", feb.yearMonthKey)
        assertEquals(1, feb.daysCount)
        assertEquals(30, feb.totalMessages)
        assertEquals(1, feb.summarizedDaysCount)

        // 3rd: 2025-12
        val dec = monthGroups[2]
        assertEquals("2025-12", dec.yearMonthKey)
        assertEquals(1, dec.daysCount)
        assertEquals(40, dec.totalMessages)
    }

    @Test
    fun groupChatDaysByYear_groupsCorrectlyAndAggregatesMonths() {
        val days = listOf(
            ChatDay("2026-03-06", emptyList(), "", listOf("회의"), listOf("철수"), 10),
            ChatDay("2026-02-28", emptyList(), "", listOf("점심"), listOf("영희"), 20),
            ChatDay("2025-12-25", emptyList(), "", listOf("연말"), listOf("민수"), 30),
            ChatDay("2025-08-15", emptyList(), "", listOf("광복절"), listOf("철수"), 40)
        )

        val yearGroups = groupChatDaysByYear(days)

        assertEquals(2, yearGroups.size)

        // 2026
        val y2026 = yearGroups[0]
        assertEquals("2026", y2026.yearKey)
        assertEquals("2026년", y2026.displayTitle)
        assertEquals(2026, y2026.year)
        assertEquals(2, y2026.daysCount)
        assertEquals(30, y2026.totalMessages)
        assertEquals(2, y2026.monthsCount)

        // 2025
        val y2025 = yearGroups[1]
        assertEquals("2025", y2025.yearKey)
        assertEquals("2025년", y2025.displayTitle)
        assertEquals(2025, y2025.year)
        assertEquals(2, y2025.daysCount)
        assertEquals(70, y2025.totalMessages)
        assertEquals(2, y2025.monthsCount)
    }
}
