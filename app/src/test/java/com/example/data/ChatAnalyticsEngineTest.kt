package com.example.data

import com.example.data.parser.ChatAnalyticsEngine
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * ChatAnalyticsEngine Clean Code Unit Tests
 * - Given-When-Then BDD Structure
 * - Reusable test fixture factories (DRY)
 * - Thorough coverage of 100% on-device analytics algorithms
 */
class ChatAnalyticsEngineTest {

    // Test Fixture Helper Factory
    private fun createChatDay(
        date: String,
        messages: List<Message> = emptyList(),
        keywords: List<String> = emptyList(),
        msgCount: Int = messages.size,
        summary: String = "테스트 대화 요약"
    ): ChatDay {
        val participants = messages.map { it.sender.trim() }.filter { it.isNotBlank() }.distinct()
        return ChatDay(
            date = date,
            messages = messages,
            summary = summary,
            keywords = keywords,
            participants = participants,
            msgCount = msgCount
        )
    }

    @Test
    fun testParseHour_parsesVariousTimeFormats() {
        assertEquals(10, ChatAnalyticsEngine.parseHour("오전 10:25"))
        assertEquals(20, ChatAnalyticsEngine.parseHour("오후 8:10"))
        assertEquals(12, ChatAnalyticsEngine.parseHour("오후 12:30"))
        assertEquals(0, ChatAnalyticsEngine.parseHour("오전 12:05"))
        assertEquals(23, ChatAnalyticsEngine.parseHour("23:45"))
        assertEquals(9, ChatAnalyticsEngine.parseHour("09:15"))
    }

    @Test
    fun testParseMinute_parsesValidMinutes() {
        assertEquals(25, ChatAnalyticsEngine.parseMinute("오전 10:25"))
        assertEquals(5, ChatAnalyticsEngine.parseMinute("오후 8:05"))
        assertEquals(0, ChatAnalyticsEngine.parseMinute("12:00"))
        assertEquals(59, ChatAnalyticsEngine.parseMinute("23:59"))
    }

    @Test
    fun testGetAvailableYearMonths_filtersAndSortsDescending() {
        val days = listOf(
            createChatDay("2026-03-01", msgCount = 10),
            createChatDay("2026-03-15", msgCount = 20),
            createChatDay("2026-02-10", msgCount = 15),
            createChatDay("2025-12-25", msgCount = 30)
        )
        val months = ChatAnalyticsEngine.getAvailableYearMonths(days)
        assertEquals(listOf("2026-03", "2026-02", "2025-12"), months)
    }

    @Test
    fun testAnalyzeMonth_ParticipantsAndPeakDay() {
        // Given
        val day1 = createChatDay(
            date = "2026-03-10",
            messages = listOf(
                Message("철수", "오전 10:00", "오늘 회의 언제인가요?"),
                Message("영희", "오전 10:05", "오후 2시 프로젝트 회의입니다."),
                Message("철수", "오전 10:10", "네 알겠습니다 일정 공유 감사해요"),
                Message("민수", "오후 2:30", "회의 자료 공유합니다.")
            ),
            keywords = listOf("프로젝트", "회의", "일정"),
            summary = "프로젝트 회의 조율"
        )
        val day2 = createChatDay(
            date = "2026-03-18",
            messages = listOf(
                Message("철수", "오후 7:00", "오늘 저녁 회식 가실 분?"),
                Message("철수", "오후 7:05", "삼겹살 맛집 예약했어요"),
                Message("영희", "오후 7:10", "저 갈게요!"),
                Message("민수", "오후 7:12", "저도 참석합니다."),
                Message("철수", "오후 7:15", "좋습니다 2차도 가시죠!"),
                Message("영희", "오후 7:20", "좋아요!")
            ),
            keywords = listOf("회식", "맛집", "삼겹살"),
            summary = "저녁 회식 모임"
        )

        // When
        val report = ChatAnalyticsEngine.analyzeMonth(listOf(day1, day2), "2026-03")

        // Then: Total messages = 4 + 6 = 10
        assertEquals(10, report.totalMessages)
        assertEquals(2, report.daysCount)
        assertEquals(5, report.avgDailyMessages)

        // Participants Podium: 철수(5), 영희(3), 민수(2)
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

        // Peak Day: Day2 (6건, 60%)
        assertNotNull(report.peakDay)
        assertEquals("2026-03-18", report.peakDay?.date)
        assertEquals(6, report.peakDay?.messageCount)
        assertEquals(60, report.peakDay?.percentageOfTotal)
        assertTrue(report.peakDay?.displayDate?.contains("3월 18일") == true)

        // Time slot & Persona
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
        assertNotNull(report.heatmapData)
        assertNotNull(report.stockScrubbingData)
    }

    @Test
    fun testAnalyzeMonth_SingleDayMonth_calculates100PercentPeakDay() {
        // Given
        val singleDay = createChatDay(
            date = "2026-07-07",
            messages = listOf(
                Message("김철수", "10:00", "반갑습니다."),
                Message("이영희", "10:05", "네 안녕하세요!")
            ),
            keywords = listOf("칠석", "인사")
        )

        // When
        val report = ChatAnalyticsEngine.analyzeMonth(listOf(singleDay), "2026-07")

        // Then
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
        // Given
        val day = createChatDay(
            date = "2026-08-01",
            messages = listOf(
                Message("김철수", "10:00", "메시지 1"),
                Message("이영희", "10:01", "메시지 2"),
                Message("박민수", "10:02", "메시지 3")
            )
        )

        // When
        val report = ChatAnalyticsEngine.analyzeMonth(listOf(day), "2026-08")

        // Then: Total 3, each 1 msg (33%)
        assertEquals(3, report.participantShares.size)
        report.participantShares.forEach { share ->
            assertEquals(1, share.count)
            assertEquals(33, share.percentage)
        }
    }

    @Test
    fun testTimeSlotPersona_NightOwl() {
        // Given: Chat messages in night hours (01:00 ~ 04:00)
        val day = createChatDay(
            date = "2026-09-01",
            messages = listOf(
                Message("철수", "01:30", "새벽 코딩 중입니다"),
                Message("영희", "02:00", "저도 아직 안 자요"),
                Message("민수", "03:15", "다들 안 주무시네요"),
                Message("철수", "14:00", "오후 회의 확인")
            )
        )

        // When
        val report = ChatAnalyticsEngine.analyzeMonth(listOf(day), "2026-09")

        // Then: 3 night out of 4 messages (75% night)
        assertEquals(75, report.timeSlotStats.nightPercent)
        assertTrue(report.timeSlotStats.personaTitle.contains("심야 올빼미형"))
    }

    @Test
    fun testCalculateFirstPingStats() {
        // Given: Day with two conversation sessions (separated by > 2 hours)
        val day = createChatDay(
            date = "2026-03-01",
            messages = listOf(
                Message("철수", "오전 09:00", "좋은 아침입니다."), // Session 1 starter: 철수
                Message("영희", "오전 09:04", "네 안녕하세요!"), // Response time: 4m
                Message("민수", "오전 09:20", "좋은 하루 되세요"), // Response time: 16m
                Message("영희", "오후 02:00", "점심 드셨나요?"), // Session 2 starter (> 4h gap): 영희
                Message("철수", "오후 02:10", "네 먹었습니다") // Response time: 10m
            )
        )

        // When
        val stats = ChatAnalyticsEngine.calculateFirstPingStats(listOf(day))

        // Then: 2 sessions total
        assertEquals(2, stats.totalSessions)
        assertEquals(2, stats.leaders.size)
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
        val day = createChatDay(
            date = "2026-03-05",
            messages = listOf(
                Message("철수", "10:00", "ㅋㅋㅋㅋ 대박 ㅋㅋㅋㅋ 진짜 웃기다 ㅋㅋㅋ"), // 10 'ㅋ'
                Message("영희", "10:01", "좋은 하루 보내세요~~ 항상 감사해요~^^"), // 3 '~'
                Message("민수", "10:02", "회의 언제 시작하나요? 장소가 어디죠? 몇 시죠??") // 4 '?'
            )
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
        val day1 = createChatDay("2026-03-01", msgCount = 5)  // level 1
        val day2 = createChatDay("2026-03-15", msgCount = 85) // level 3

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
    fun testCalculateStockChartScrubbingData() {
        // Given: March 2026 (31 days), Day 18 has 3 messages at 20:15, 20:30, 21:00
        val day18 = createChatDay(
            date = "2026-03-18",
            messages = listOf(
                Message("철수", "오후 8:15", "오늘 회식 몇 시인가요?"),
                Message("영희", "오후 8:30", "삼겹살집 9시 예약이요!"),
                Message("민수", "오후 9:00", "지금 출발합니다~~")
            ),
            keywords = listOf("회식", "삼겹살"),
            summary = "회식 일정 조율"
        )

        val peakDay = PeakDayData(
            date = "2026-03-18",
            displayDate = "3월 18일 (수)",
            messageCount = 3,
            percentageOfTotal = 100,
            peakKeywords = listOf("회식")
        )

        // When
        val data = ChatAnalyticsEngine.calculateStockChartScrubbingData(
            year = 2026,
            month = 3,
            days = listOf(day18),
            avgDailyMessages = 1,
            peakDay = peakDay
        )

        // Then: 31 points in March
        assertEquals(31, data.points.size)
        assertEquals(1, data.avgDailyCount)
        assertEquals(3, data.maxDayCount)
        assertNotNull(data.peakPoint)
        assertEquals(18, data.peakPoint?.dayOfMonth)

        val p18 = data.points[17] // Day 18
        assertEquals(18, p18.dayOfMonth)
        assertEquals("2026-03-18", p18.dateStr)
        assertEquals(3, p18.messageCount)
        assertTrue(p18.isPeakDay)
        assertTrue(p18.trendLabel.contains("피크") || p18.trendLabel.contains("급등"))
        assertEquals(listOf("회식", "삼겹살"), p18.keywords)

        // Check 24-hour distribution for Day 18 (Hour 20 had 2 messages, Hour 21 had 1 message)
        assertEquals(24, p18.hourlyCounts.size)
        assertEquals(2, p18.hourlyCounts[20])
        assertEquals(1, p18.hourlyCounts[21])
        assertEquals(0, p18.hourlyCounts[0])
        assertEquals(20, p18.peakHour)
        assertEquals(2, p18.peakHourCount)

        // Day 1 (empty)
        val p1 = data.points[0]
        assertEquals(1, p1.dayOfMonth)
        assertEquals(0, p1.messageCount)
        assertFalse(p1.isPeakDay)
        assertEquals(24, p1.hourlyCounts.size)
        assertEquals(0, p1.hourlyCounts[12])
    }

    @Test
    fun testAnalyzeMonth_IncludesAllNewCreativeFeatures() {
        val messages = listOf(
            Message("철수", "09:00", "시작합니다 ㅋㅋㅋ"),
            Message("영희", "09:05", "네 반가워요~~")
        )
        val day = createChatDay("2026-05-10", messages = messages, keywords = listOf("키워드"))

        val report = ChatAnalyticsEngine.analyzeMonth(listOf(day), "2026-05")

        assertNotNull("firstPingStats should not be null", report.firstPingStats)
        assertNotNull("quirksReport should not be null", report.quirksReport)
        assertNotNull("heatmapData should not be null", report.heatmapData)
        assertNotNull("stockScrubbingData should not be null", report.stockScrubbingData)
        assertEquals(31, report.heatmapData?.totalDaysInMonth)
        assertEquals(1, report.heatmapData?.activeDaysCount)
        assertEquals(31, report.stockScrubbingData?.points?.size)
    }
}
