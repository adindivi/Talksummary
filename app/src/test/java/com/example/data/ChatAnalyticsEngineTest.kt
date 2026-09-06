package com.example.data

import com.example.data.parser.ChatAnalyticsEngine
import org.junit.Assert.*
import org.junit.Test

class ChatAnalyticsEngineTest {

    @Test
    fun testParseHour() {
        assertEquals(10, ChatAnalyticsEngine.parseHour("오전 10:25"))
        assertEquals(20, ChatAnalyticsEngine.parseHour("오후 8:10"))
        assertEquals(12, ChatAnalyticsEngine.parseHour("오후 12:30"))
        assertEquals(0, ChatAnalyticsEngine.parseHour("오전 12:05"))
        assertEquals(23, ChatAnalyticsEngine.parseHour("23:45"))
        assertEquals(9, ChatAnalyticsEngine.parseHour("09:15"))
    }

    @Test
    fun testGetAvailableYearMonths() {
        val days = listOf(
            ChatDay("2026-03-01", emptyList(), "", emptyList(), emptyList(), 10),
            ChatDay("2026-03-15", emptyList(), "", emptyList(), emptyList(), 20),
            ChatDay("2026-02-10", emptyList(), "", emptyList(), emptyList(), 15),
            ChatDay("2025-12-25", emptyList(), "", emptyList(), emptyList(), 30)
        )
        val months = ChatAnalyticsEngine.getAvailableYearMonths(days)
        assertEquals(listOf("2026-03", "2026-02", "2025-12"), months)
    }

    @Test
    fun testAnalyzeMonth_ParticipantsAndPeakDay() {
        val messagesDay1 = listOf(
            Message("철수", "오전 10:00", "오늘 회의 언제인가요?"),
            Message("영희", "오전 10:05", "오후 2시 프로젝트 회의입니다."),
            Message("철수", "오전 10:10", "네 알겠습니다 일정 공유 감사해요"),
            Message("민수", "오후 2:30", "회의 자료 공유합니다.")
        )
        val messagesDay2 = listOf(
            Message("철수", "오후 7:00", "오늘 저녁 회식 가실 분?"),
            Message("철수", "오후 7:05", "삼겹살 맛집 예약했어요"),
            Message("영희", "오후 7:10", "저 갈게요!"),
            Message("민수", "오후 7:12", "저도 참석합니다."),
            Message("철수", "오후 7:15", "좋습니다 2차도 가시죠!"),
            Message("영희", "오후 7:20", "좋아요!")
        )

        val day1 = ChatDay(
            date = "2026-03-10",
            messages = messagesDay1,
            summary = "프로젝트 회의 조율",
            keywords = listOf("프로젝트", "회의", "일정"),
            participants = listOf("철수", "영희", "민수"),
            msgCount = 4
        )
        val day2 = ChatDay(
            date = "2026-03-18",
            messages = messagesDay2,
            summary = "저녁 회식 모임",
            keywords = listOf("회식", "맛집", "삼겹살"),
            participants = listOf("철수", "영희", "민수"),
            msgCount = 6
        )

        val report = ChatAnalyticsEngine.analyzeMonth(listOf(day1, day2), "2026-03")

        // Total messages = 4 + 6 = 10
        assertEquals(10, report.totalMessages)
        assertEquals(2, report.daysCount)
        assertEquals(5, report.avgDailyMessages)

        // Participants: 철수(5), 영희(3), 민수(2) -> total 10
        assertEquals(3, report.participantShares.size)
        val firstPlace = report.participantShares[0]
        assertEquals("철수", firstPlace.name)
        assertEquals(5, firstPlace.count)
        assertEquals(50, firstPlace.percentage)
        assertEquals("이 달의 수다왕", firstPlace.badge)

        val secondPlace = report.participantShares[1]
        assertEquals("영희", secondPlace.name)
        assertEquals(3, secondPlace.count)
        assertEquals(30, secondPlace.percentage)
        assertEquals("소통 조율자", secondPlace.badge)

        val thirdPlace = report.participantShares[2]
        assertEquals("민수", thirdPlace.name)
        assertEquals(2, thirdPlace.count)
        assertEquals(20, thirdPlace.percentage)
        assertEquals("분위기 메이커", thirdPlace.badge)

        // Peak Day: Day2 (6 messages, 60%)
        assertNotNull(report.peakDay)
        assertEquals("2026-03-18", report.peakDay?.date)
        assertEquals(6, report.peakDay?.messageCount)
        assertEquals(60, report.peakDay?.percentageOfTotal)
        assertTrue(report.peakDay?.displayDate?.contains("3월 18일") == true)

        // Time slot & persona: Day1 has 3 morning, 1 afternoon. Day2 has 6 evening.
        // Total morning: 3 (30%), afternoon: 1 (10%), evening: 6 (60%)
        assertEquals(60, report.timeSlotStats.eveningPercent)
        assertTrue(report.timeSlotStats.personaTitle.contains("저녁 수다형"))
    }

    @Test
    fun testAnalyzeMonth_EmptyGracefulFallback() {
        val report = ChatAnalyticsEngine.analyzeMonth(emptyList(), "2026-04")
        assertEquals(0, report.totalMessages)
        assertEquals(0, report.daysCount)
        assertTrue(report.participantShares.isEmpty())
        assertTrue(report.timeSlotStats.personaTitle.isNotBlank())
    }

    @Test
    fun testAnalyzeMonth_SingleDayMonth_calculates100PercentPeakDay() {
        // Given: A month with only 1 day of conversations
        val singleDay = ChatDay(
            date = "2026-07-07",
            messages = listOf(
                Message("김철수", "10:00", "반갑습니다."),
                Message("이영희", "10:05", "네 안녕하세요!")
            ),
            summary = "칠석 인사",
            keywords = listOf("칠석", "인사"),
            participants = listOf("김철수", "이영희"),
            msgCount = 2
        )

        // When: Analyzing the month
        val report = ChatAnalyticsEngine.analyzeMonth(listOf(singleDay), "2026-07")

        // Then: Total messages is 2, peak day is 100% of the month
        assertEquals(2, report.totalMessages)
        assertEquals(1, report.daysCount)
        assertEquals(2, report.avgDailyMessages)
        assertNotNull(report.peakDay)
        assertEquals("2026-07-07", report.peakDay?.date)
        assertEquals(100, report.peakDay?.percentageOfTotal)
        assertEquals(2, report.peakDay?.messageCount)
    }

    @Test
    fun testAnalyzeMonth_TieBreakerParticipants_deterministicRanking() {
        // Given: Participants with exact same message count
        val day = ChatDay(
            date = "2026-08-01",
            messages = listOf(
                Message("김철수", "10:00", "메시지 1"),
                Message("이영희", "10:01", "메시지 2"),
                Message("박민수", "10:02", "메시지 3")
            ),
            summary = "동률 테스트",
            keywords = emptyList(),
            participants = listOf("김철수", "이영희", "박민수"),
            msgCount = 3
        )

        // When: Analyzing the month
        val report = ChatAnalyticsEngine.analyzeMonth(listOf(day), "2026-08")

        // Then: Exactly 3 participants, ranks are 1, 2, 3 without crash or collision
        assertEquals(3, report.participantShares.size)
        assertEquals(1, report.participantShares[0].rank)
        assertEquals(2, report.participantShares[1].rank)
        assertEquals(3, report.participantShares[2].rank)
        assertEquals(33, report.participantShares[0].percentage)
    }

    @Test
    fun testParseHour_EdgeCasesAndFallbacks() {
        // Fallback for invalid or malformed strings should return null safely without throwing
        assertNull(ChatAnalyticsEngine.parseHour(""))
        assertNull(ChatAnalyticsEngine.parseHour("   "))
        assertNull(ChatAnalyticsEngine.parseHour("알 수 없음"))
        assertNull(ChatAnalyticsEngine.parseHour("invalid:time:format"))

        // Single digit hours
        assertEquals(7, ChatAnalyticsEngine.parseHour("7:30"))
        assertEquals(7, ChatAnalyticsEngine.parseHour("오전 7:30"))
        assertEquals(19, ChatAnalyticsEngine.parseHour("오후 7:30"))
    }

    @Test
    fun testAnalyzeMonth_NightOwlPersonaTrigger() {
        // Given: Messages predominantly at midnight/early morning (00:00 ~ 06:00)
        val nightMessages = listOf(
            Message("야행성", "오전 1:15", "아직 안 주무시나요?"),
            Message("야행성", "오전 2:30", "코딩 중입니다."),
            Message("야행성", "오전 3:45", "커밋 완료!")
        )
        val nightDay = ChatDay(
            date = "2026-09-01",
            messages = nightMessages,
            summary = "새벽 코딩",
            keywords = listOf("코딩", "커밋"),
            participants = listOf("야행성"),
            msgCount = 3
        )

        // When: Analyzing the month
        val report = ChatAnalyticsEngine.analyzeMonth(listOf(nightDay), "2026-09")

        // Then: Night slot is 100%, persona reflects night owl
        assertEquals(100, report.timeSlotStats.nightPercent)
        assertTrue("Persona should be night owl", report.timeSlotStats.personaTitle.contains("올빼미") || report.timeSlotStats.personaTitle.contains("새벽"))
    }

    @Test
    fun testCalculateFirstPingStats() {
        // Given: Day with two conversation sessions (separated by > 2 hours)
        val day1 = ChatDay(
            date = "2026-03-01",
            messages = listOf(
                Message("철수", "오전 09:00", "좋은 아침입니다."), // Session 1 starter: 철수
                Message("영희", "오전 09:04", "네 안녕하세요!"), // Response time: 4m
                Message("민수", "오전 09:20", "좋은 하루 되세요"), // Response time: 16m
                Message("영희", "오후 02:00", "점심 드셨나요?"), // Session 2 starter (> 4h gap): 영희
                Message("철수", "오후 02:10", "네 먹었습니다") // Response time: 10m
            ),
            summary = "", keywords = emptyList(), participants = listOf("철수", "영희", "민수"), msgCount = 5
        )

        // When
        val stats = ChatAnalyticsEngine.calculateFirstPingStats(listOf(day1))

        // Then: 2 sessions total
        assertEquals(2, stats.totalSessions)
        assertEquals(2, stats.leaders.size)
        // 철수 and 영희 each started 1 session (50%)
        assertEquals(50, stats.leaders[0].pingPercentage)
        assertEquals(50, stats.leaders[1].pingPercentage)

        // Response speed: 영희 answered in 4m, 철수 in 10m, 민수 in 16m
        assertNotNull(stats.fastestResponder)
        assertEquals("영희", stats.fastestResponder?.name)
        assertEquals(4, stats.fastestResponder?.avgMinutes)
        assertEquals("4분", stats.fastestResponder?.displaySpeed)

        assertNotNull(stats.slowestResponder)
        assertEquals("민수", stats.slowestResponder?.name)
        assertEquals(16, stats.slowestResponder?.avgMinutes)
    }

    @Test
    fun testCalculateLinguisticQuirks() {
        // Given: Chat messages with distinct Korean linguistic quirks
        val day = ChatDay(
            date = "2026-03-05",
            messages = listOf(
                Message("철수", "10:00", "ㅋㅋㅋㅋ 대박 ㅋㅋㅋㅋ 진짜 웃기다 ㅋㅋㅋ"), // 10 'ㅋ'
                Message("영희", "10:01", "좋은 하루 보내세요~~ 항상 감사해요~^^"), // 3 '~'
                Message("민수", "10:02", "회의 언제 시작하나요? 장소가 어디죠? 몇 시죠??") // 4 '?'
            ),
            summary = "", keywords = emptyList(), participants = listOf("철수", "영희", "민수"), msgCount = 3
        )

        // When
        val report = ChatAnalyticsEngine.calculateLinguisticQuirks(listOf(day))

        // Then
        assertEquals("ㅋㅋㅋ형", report.dominantLaughType)
        assertTrue(report.totalLaughCount >= 10)
        assertTrue(report.funFact.contains("ㅋㅋㅋ"))

        val cheolsu = report.users.find { it.name == "철수" }
        assertNotNull(cheolsu)
        assertTrue(cheolsu!!.mainQuirkBadge.contains("폭소파"))

        val younghee = report.users.find { it.name == "영희" }
        assertNotNull(younghee)
        assertTrue(younghee!!.mainQuirkBadge.contains("다정러"))

        val minsu = report.users.find { it.name == "민수" }
        assertNotNull(minsu)
        assertTrue(minsu!!.mainQuirkBadge.contains("호기심"))
    }

    @Test
    fun testCalculateTalkHeatmap() {
        // Given: March 2026 (31 days) with 2 active days
        val day1 = ChatDay("2026-03-01", emptyList(), "", emptyList(), emptyList(), 5) // level 1
        val day2 = ChatDay("2026-03-15", emptyList(), "", emptyList(), emptyList(), 85) // level 3

        // When
        val heatmap = ChatAnalyticsEngine.calculateTalkHeatmap(2026, 3, listOf(day1, day2))

        // Then
        assertEquals(2026, heatmap.year)
        assertEquals(3, heatmap.month)
        assertEquals(31, heatmap.totalDaysInMonth)
        assertEquals(31, heatmap.tiles.size)
        assertEquals(2, heatmap.activeDaysCount)
        assertEquals(6, heatmap.activeDayPercentage) // 2 / 31 * 100 = 6%

        val tile1 = heatmap.tiles[0] // Day 1
        assertEquals(1, tile1.dayOfMonth)
        assertEquals(5, tile1.count)
        assertEquals(1, tile1.level)

        val tile15 = heatmap.tiles[14] // Day 15
        assertEquals(15, tile15.dayOfMonth)
        assertEquals(85, tile15.count)
        assertEquals(3, tile15.level)

        val tile2 = heatmap.tiles[1] // Day 2 (empty)
        assertEquals(0, tile2.count)
        assertEquals(0, tile2.level)
    }

    @Test
    fun testAnalyzeMonth_IncludesAllNewCreativeFeatures() {
        val messages = listOf(
            Message("철수", "09:00", "시작합니다 ㅋㅋㅋ"),
            Message("영희", "09:05", "네 반가워요~~")
        )
        val day = ChatDay("2026-05-10", messages, "요약", listOf("키워드"), listOf("철수", "영희"), 2)

        val report = ChatAnalyticsEngine.analyzeMonth(listOf(day), "2026-05")

        assertNotNull("firstPingStats should not be null", report.firstPingStats)
        assertNotNull("quirksReport should not be null", report.quirksReport)
        assertNotNull("heatmapData should not be null", report.heatmapData)
        assertEquals(31, report.heatmapData?.totalDaysInMonth)
        assertEquals(1, report.heatmapData?.activeDaysCount)
    }
}
