package com.example.data.parser

import com.example.data.ChatDay
import com.example.data.Message
import com.example.data.TalkSummaryRepository
import com.example.model.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 100% 온디바이스 오프라인 카카오톡 월간 대화 분석 엔진
 * - 참여자 발언 지분율 & 랭킹 (Podium)
 * - 가장 뜨거웠던 날 (피크 데이)
 * - 대화 골든타임 & 라이프스타일 페르소나
 * - 월간 핵심 키워드 & 관계 케미 판별
 * - 선톡(First Ping) 지수 & 티키타카 답장 속도
 * - 말버릇(Linguistic Quirks) & 웃음 지수 리포트
 * - 깃허브 잔디 대화 캘린더 (Talk Heatmap)
 * - 주식형 일자별 스크러빙 곡선 & 24시간 체결량
 */
object ChatAnalyticsEngine {

    // Domain Constants
    private const val FIRST_PING_SILENCE_THRESHOLD_MINUTES = 120L
    private const val DEFAULT_FALLBACK_YEAR = 2026
    private const val DEFAULT_FALLBACK_MONTH = 1
    private const val HOURS_IN_DAY = 24
    private const val SYSTEM_SENDER_NAME = "System"

    private val MORNING_HOURS = 6..11
    private val AFTERNOON_HOURS = 12..17
    private val EVENING_HOURS = 18..23
    private val WEEK_DAY_NAMES = arrayOf("일", "월", "화", "수", "목", "금", "토")

    // Precompiled Regular Expressions for High Performance
    private val YEAR_MONTH_REGEX = Regex("""^\d{4}-\d{2}$""")
    private val TIME_HOUR_MINUTE_REGEX = Regex("""(\d{1,2}):\d{2}""")
    private val TIME_MINUTE_REGEX = Regex("""\d{1,2}:(\d{2})""")
    private val WHITESPACE_REGEX = Regex("""\s+""")
    private val NON_WORD_KOREAN_REGEX = Regex("""[^\w가-힣]""")

    /**
     * 전체 ChatDay 목록에서 분석 가능한 연-월 목록을 내림차순("2026-03", "2026-02" 등)으로 추출
     */
    fun getAvailableYearMonths(chatDays: List<ChatDay>): List<String> {
        return chatDays
            .filter { it.date.length >= 7 }
            .map { it.date.substring(0, 7) }
            .filter { it.matches(YEAR_MONTH_REGEX) }
            .distinct()
            .sortedDescending()
    }

    /**
     * 특정 월(YYYY-MM)의 대화 데이터를 기반으로 종합 분석 리포트 생성
     */
    fun analyzeMonth(chatDays: List<ChatDay>, targetYearMonth: String? = null): MonthlyAnalysisReport {
        val availableMonths = getAvailableYearMonths(chatDays)
        val selectedYearMonth = targetYearMonth ?: availableMonths.firstOrNull() ?: getCurrentYearMonth()

        val parts = selectedYearMonth.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: DEFAULT_FALLBACK_YEAR
        val month = parts.getOrNull(1)?.toIntOrNull() ?: DEFAULT_FALLBACK_MONTH
        val displayMonth = "${year}년 ${month}월"

        val monthDays = chatDays.filter { it.date.startsWith(selectedYearMonth) }

        if (monthDays.isEmpty()) {
            return createEmptyMonthReport(selectedYearMonth, displayMonth, year, month)
        }

        val totalMessages = monthDays.sumOf { if (it.msgCount > 0) it.msgCount else it.messages.size }
        val daysCount = monthDays.size
        val avgDailyMessages = if (daysCount > 0) totalMessages / daysCount else 0

        // 1. 참여자 발언 지분율 & 랭킹 계산
        val participantShares = calculateParticipantShares(monthDays)

        // 2. 가장 뜨거웠던 날 (피크 데이)
        val peakDay = calculatePeakDay(monthDays, totalMessages)

        // 3. 대화 골든타임 & 페르소나
        val timeSlotStats = calculateTimeSlotStats(monthDays)

        // 4. 월간 키워드 및 관계 케미 판별
        val topKeywords = extractTopKeywords(monthDays)
        val (chemistryTitle, chemistryDesc) = determineChemistry(participantShares, topKeywords)

        // 5. 신규 창의적 기능 (선톡, 말버릇, 잔디)
        val firstPingStats = calculateFirstPingStats(monthDays)
        val quirksReport = calculateLinguisticQuirks(monthDays)
        val heatmapData = calculateTalkHeatmap(year, month, monthDays)

        // 6. 주식 차트형 인터랙션 (스크러빙 곡선 & 24시간 체결량)
        val stockScrubbingData = calculateStockChartScrubbingData(year, month, monthDays, avgDailyMessages, peakDay)

        return MonthlyAnalysisReport(
            yearMonthKey = selectedYearMonth,
            displayMonth = displayMonth,
            totalMessages = totalMessages,
            daysCount = daysCount,
            avgDailyMessages = avgDailyMessages,
            participantShares = participantShares,
            peakDay = peakDay,
            timeSlotStats = timeSlotStats,
            topKeywords = topKeywords,
            chemistryTitle = chemistryTitle,
            chemistryDescription = chemistryDesc,
            firstPingStats = firstPingStats,
            quirksReport = quirksReport,
            heatmapData = heatmapData,
            stockScrubbingData = stockScrubbingData
        )
    }

    private fun createEmptyMonthReport(
        selectedYearMonth: String,
        displayMonth: String,
        year: Int,
        month: Int
    ): MonthlyAnalysisReport {
        return MonthlyAnalysisReport(
            yearMonthKey = selectedYearMonth,
            displayMonth = displayMonth,
            totalMessages = 0,
            daysCount = 0,
            avgDailyMessages = 0,
            participantShares = emptyList(),
            peakDay = null,
            timeSlotStats = TimeSlotDistribution(
                morningCount = 0, afternoonCount = 0, eveningCount = 0, nightCount = 0,
                morningPercent = 25, afternoonPercent = 35, eveningPercent = 30, nightPercent = 10,
                personaTitle = "💬 대화 시작 대기 중",
                personaDescription = "대화 내역이 불러와지면 분석이 시작됩니다."
            ),
            topKeywords = emptyList(),
            chemistryTitle = "✨ 새로운 시작의 방",
            chemistryDescription = "대화를 시작하고 서로의 케미를 발견해보세요!",
            firstPingStats = null,
            quirksReport = null,
            heatmapData = calculateTalkHeatmap(year, month, emptyList()),
            stockScrubbingData = calculateStockChartScrubbingData(year, month, emptyList(), 0, null)
        )
    }

    private fun calculateParticipantShares(days: List<ChatDay>): List<ParticipantShare> {
        val senderCounts = mutableMapOf<String, Int>()

        for (day in days) {
            if (day.messages.isNotEmpty()) {
                for (msg in day.messages) {
                    val sender = msg.sender.trim()
                    if (sender.isNotBlank() && sender != SYSTEM_SENDER_NAME) {
                        senderCounts[sender] = (senderCounts[sender] ?: 0) + 1
                    }
                }
            } else if (day.participants.isNotEmpty()) {
                val splitCount = if (day.msgCount > 0) (day.msgCount / day.participants.size).coerceAtLeast(1) else 1
                for (p in day.participants) {
                    val trimmed = p.trim()
                    if (trimmed.isNotBlank()) {
                        senderCounts[trimmed] = (senderCounts[trimmed] ?: 0) + splitCount
                    }
                }
            }
        }

        val totalSendersMsgs = senderCounts.values.sum()
        if (totalSendersMsgs == 0) return emptyList()

        return senderCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .mapIndexed { index, entry ->
                val rank = index + 1
                val pct = ((entry.value.toFloat() / totalSendersMsgs) * 100f).roundToInt()
                val badge = when (rank) {
                    1 -> "이 달의 수다왕"
                    2 -> "소통 조율자"
                    3 -> "분위기 메이커"
                    else -> "열정 멤버"
                }
                ParticipantShare(
                    name = entry.key,
                    count = entry.value,
                    percentage = pct,
                    rank = rank,
                    badge = badge
                )
            }
    }

    private fun calculatePeakDay(days: List<ChatDay>, totalMessages: Int): PeakDayData? {
        val peak = days.maxByOrNull { if (it.msgCount > 0) it.msgCount else it.messages.size } ?: return null
        val count = if (peak.msgCount > 0) peak.msgCount else peak.messages.size
        val pct = if (totalMessages > 0) ((count.toFloat() / totalMessages) * 100f).roundToInt() else 0
        val displayDate = formatDisplayDateWithDayOfWeek(peak.date)

        val keywords = peak.keywords
            .filter { it.isNotBlank() && !TalkSummaryRepository.isStopWord(it) }
            .take(3)
            .ifEmpty {
                extractWordsFromMessages(peak.messages.take(15), limit = 3, maxWordLength = 7)
            }

        return PeakDayData(
            date = peak.date,
            displayDate = displayDate,
            messageCount = count,
            percentageOfTotal = pct,
            peakKeywords = keywords
        )
    }

    private fun calculateTimeSlotStats(days: List<ChatDay>): TimeSlotDistribution {
        var morningCount = 0
        var afternoonCount = 0
        var eveningCount = 0
        var nightCount = 0

        val allMessages = days.flatMap { it.messages }
        for (msg in allMessages) {
            val hour = parseHour(msg.time) ?: continue
            when (hour) {
                in MORNING_HOURS -> morningCount++
                in AFTERNOON_HOURS -> afternoonCount++
                in EVENING_HOURS -> eveningCount++
                else -> nightCount++ // 0..5
            }
        }

        val totalParsed = morningCount + afternoonCount + eveningCount + nightCount
        if (totalParsed == 0) {
            return TimeSlotDistribution(
                morningCount = 0,
                afternoonCount = 0,
                eveningCount = 0,
                nightCount = 0,
                morningPercent = 25,
                afternoonPercent = 40,
                eveningPercent = 25,
                nightPercent = 10,
                personaTitle = "☀️ 낮 소통형",
                personaDescription = "낮과 오후 시간대에 주로 소통하는 균형잡힌 라이프스타일이에요!"
            )
        }

        val morningPct = ((morningCount.toFloat() / totalParsed) * 100f).roundToInt()
        val afternoonPct = ((afternoonCount.toFloat() / totalParsed) * 100f).roundToInt()
        val eveningPct = ((eveningCount.toFloat() / totalParsed) * 100f).roundToInt()
        val nightPct = ((nightCount.toFloat() / totalParsed) * 100f).roundToInt()

        val (personaTitle, personaDesc) = determineLifestylePersona(morningPct, afternoonPct, eveningPct, nightPct)

        return TimeSlotDistribution(
            morningCount = morningCount,
            afternoonCount = afternoonCount,
            eveningCount = eveningCount,
            nightCount = nightCount,
            morningPercent = morningPct,
            afternoonPercent = afternoonPct,
            eveningPercent = eveningPct,
            nightPercent = nightPct,
            personaTitle = personaTitle,
            personaDescription = personaDesc
        )
    }

    private fun determineLifestylePersona(
        morningPct: Int,
        afternoonPct: Int,
        eveningPct: Int,
        nightPct: Int
    ): Pair<String, String> {
        return when {
            nightPct >= 20 -> Pair(
                "🦉 심야 올빼미형",
                "심야 및 새벽(00~06시) 대화 비중이 ${nightPct}%로 밤샘 토크가 잦았어요!"
            )
            eveningPct >= afternoonPct && eveningPct >= morningPct -> Pair(
                "🌙 저녁 수다형",
                "퇴근 및 일과 후 저녁(18~24시)에 대화의 ${eveningPct}%가 집중되었어요!"
            )
            afternoonPct >= morningPct -> Pair(
                "☀️ 낮 소통형",
                "오후 낮 시간(12~18시)에 대화의 ${afternoonPct}%가 활발히 오갔어요!"
            )
            else -> Pair(
                "🌅 아침 얼리버드형",
                "상쾌한 아침(06~12시)부터 ${morningPct}%의 활발한 대화가 시작되었어요!"
            )
        }
    }

    fun parseHour(timeStr: String): Int? {
        val t = timeStr.trim()
        val isPm = t.contains("오후", ignoreCase = true) || t.contains("PM", ignoreCase = true)
        val isAm = t.contains("오전", ignoreCase = true) || t.contains("AM", ignoreCase = true)

        val match = TIME_HOUR_MINUTE_REGEX.find(t) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null

        if (isPm) {
            if (hour < 12) hour += 12
        } else if (isAm) {
            if (hour == 12) hour = 0
        }
        return hour.coerceIn(0, 23)
    }

    private fun extractTopKeywords(days: List<ChatDay>): List<String> {
        val collected = days
            .flatMap { it.keywords }
            .filter { it.isNotBlank() && !TalkSummaryRepository.isStopWord(it) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(6)
            .map { it.key }

        if (collected.isNotEmpty()) return collected

        // Fallback: tokenize message text
        return extractWordsFromMessages(days.flatMap { it.messages }, limit = 6, maxWordLength = 6)
    }

    private fun extractWordsFromMessages(
        messages: List<Message>,
        limit: Int = 6,
        maxWordLength: Int = 7
    ): List<String> {
        return messages
            .flatMap { it.text.split(WHITESPACE_REGEX) }
            .map { it.replace(NON_WORD_KOREAN_REGEX, "").trim() }
            .filter { it.length in 2..maxWordLength && !TalkSummaryRepository.isStopWord(it) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    private fun determineChemistry(
        participantShares: List<ParticipantShare>,
        topKeywords: List<String>
    ): Pair<String, String> {
        val topShare = participantShares.firstOrNull()?.percentage ?: 0
        val isBalanced = participantShares.size >= 3 && topShare <= 45

        return when {
            isBalanced -> Pair(
                "✨ 환상의 티키타카방",
                "특정 인물에 편중되지 않고 모든 멤버가 고르게 의견을 주고받는 이상적인 대화방이에요!"
            )
            topShare >= 65 -> {
                val leader = participantShares.first().name
                Pair(
                    "👑 1인 리더십 중심방",
                    "${leader}님이 대화의 ${topShare}%를 주도하며 든든하게 방의 분위기를 이끌고 있어요."
                )
            }
            topKeywords.any { it.contains("일정") || it.contains("회의") || it.contains("공유") || it.contains("확인") } -> Pair(
                "⚡ 칼같은 갓생 프로젝트방",
                "목적 지향적인 소통이 중심이 되는 똑 부러지는 프로페셔널 대화방이에요."
            )
            topKeywords.any { it.contains("회식") || it.contains("밥") || it.contains("주말") || it.contains("축하") } -> Pair(
                "🎉 끈끈한 친목 힐링방",
                "서로의 일상과 맛있는 순간을 나누며 따뜻한 온기가 오가는 방이에요."
            )
            else -> Pair(
                "☕ 편안한 일상 쉼터방",
                "부담 없이 잔잔하게 소소한 이야기를 나눌 수 있는 힐링 라운지예요."
            )
        }
    }

    private fun formatDisplayDateWithDayOfWeek(isoDate: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val date = try {
            sdf.parse(isoDate)
        } catch (_: Exception) {
            null
        }

        return if (date != null) {
            val cal = Calendar.getInstance().apply { time = date }
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> "일"
                Calendar.MONDAY -> "월"
                Calendar.TUESDAY -> "화"
                Calendar.WEDNESDAY -> "수"
                Calendar.THURSDAY -> "목"
                Calendar.FRIDAY -> "금"
                Calendar.SATURDAY -> "토"
                else -> ""
            }
            "${month}월 ${day}일 (${dayOfWeek})"
        } else {
            isoDate
        }
    }

    private fun getCurrentYearMonth(): String {
        val cal = Calendar.getInstance()
        return "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun parseMinute(timeStr: String): Int? {
        val match = TIME_MINUTE_REGEX.find(timeStr.trim()) ?: return null
        return match.groupValues[1].toIntOrNull()?.coerceIn(0, 59)
    }

    private fun getEpochMinutes(dateStr: String, timeStr: String): Long {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val dayMillis = try {
            sdf.parse(dateStr)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
        val dayMinutes = dayMillis / (60 * 1000L)
        val hour = parseHour(timeStr) ?: 12
        val min = parseMinute(timeStr) ?: 0
        return dayMinutes + (hour * 60) + min
    }

    data class MessagePoint(val epochMinutes: Long, val sender: String)

    private fun extractMessagePoints(days: List<ChatDay>): List<MessagePoint> {
        val sortedDays = days.sortedBy { it.date }
        val points = mutableListOf<MessagePoint>()
        for (day in sortedDays) {
            for (msg in day.messages) {
                val sender = msg.sender.trim()
                if (sender.isNotBlank() && sender != SYSTEM_SENDER_NAME) {
                    val epochMin = getEpochMinutes(day.date, msg.time)
                    points.add(MessagePoint(epochMin, sender))
                }
            }
        }
        return points
    }

    private data class FirstPingRawStats(
        val totalSessions: Int,
        val firstPingCounts: Map<String, Int>,
        val responseTimesBySender: Map<String, List<Int>>
    )

    private fun processFirstPingsAndResponses(points: List<MessagePoint>): FirstPingRawStats {
        val firstPings = mutableMapOf<String, Int>()
        val responseTimes = mutableMapOf<String, MutableList<Int>>()
        var lastTime: Long? = null
        var lastSender: String? = null
        var totalSessions = 0

        for (pt in points) {
            val gap = if (lastTime != null) (pt.epochMinutes - lastTime) else 9999L
            if (gap >= FIRST_PING_SILENCE_THRESHOLD_MINUTES) {
                firstPings[pt.sender] = (firstPings[pt.sender] ?: 0) + 1
                totalSessions++
            } else if (lastSender != null && pt.sender != lastSender && gap > 0) {
                val list = responseTimes.getOrPut(pt.sender) { mutableListOf() }
                list.add(gap.toInt())
            }
            lastTime = pt.epochMinutes
            lastSender = pt.sender
        }

        if (totalSessions == 0 && points.isNotEmpty()) {
            firstPings[points.first().sender] = 1
            totalSessions = 1
        }

        return FirstPingRawStats(totalSessions, firstPings, responseTimes)
    }

    fun calculateFirstPingStats(days: List<ChatDay>): FirstPingAnalysis {
        val allPoints = extractMessagePoints(days)
        if (allPoints.isEmpty()) {
            return FirstPingAnalysis(
                totalSessions = 0,
                leaders = emptyList(),
                fastestResponder = null,
                slowestResponder = null,
                avgRoomResponseMinutes = 0
            )
        }

        val rawStats = processFirstPingsAndResponses(allPoints)

        val leaders = rawStats.firstPingCounts.entries
            .sortedByDescending { it.value }
            .take(3)
            .map {
                val pct = if (rawStats.totalSessions > 0) {
                    ((it.value.toFloat() / rawStats.totalSessions) * 100f).roundToInt()
                } else 0
                FirstPingLeader(it.key, it.value, pct)
            }

        val userAvgs = rawStats.responseTimesBySender.mapValues { it.value.average().roundToInt() }
        val fastest = userAvgs.minByOrNull { it.value }?.let {
            ResponseSpeedUser(it.key, it.value, formatDuration(it.value))
        }
        val slowest = userAvgs.filter { it.key != fastest?.name }.maxByOrNull { it.value }?.let {
            ResponseSpeedUser(it.key, it.value, formatDuration(it.value))
        }
        val allGaps = rawStats.responseTimesBySender.values.flatten()
        val roomAvg = if (allGaps.isNotEmpty()) allGaps.average().roundToInt() else 0

        return FirstPingAnalysis(
            totalSessions = rawStats.totalSessions,
            leaders = leaders,
            fastestResponder = fastest,
            slowestResponder = slowest,
            avgRoomResponseMinutes = roomAvg
        )
    }

    fun formatDuration(mins: Int): String {
        return when {
            mins < 1 -> "1분 미만"
            mins < 60 -> "${mins}분"
            else -> {
                val h = mins / 60
                val m = mins % 60
                if (m == 0) "${h}시간" else "${h}시간 ${m}분"
            }
        }
    }

    data class UserQuirkCounts(
        var kCount: Int = 0,
        var hCount: Int = 0,
        var waveCount: Int = 0,
        var questionCount: Int = 0,
        var exclamationCount: Int = 0,
        var totalMessages: Int = 0
    )

    private fun determineUserQuirkBadge(counts: UserQuirkCounts): Pair<String, String> {
        val laughSum = counts.kCount + counts.hCount
        val maxScore = maxOf(laughSum, counts.waveCount, counts.questionCount, counts.exclamationCount)

        return when {
            maxScore == 0 ->
                Pair("✨ 다재다능 토커", "메시지 ${counts.totalMessages}건")
            maxScore == laughSum -> {
                if (counts.kCount >= counts.hCount) {
                    Pair("😂 호탕한 폭소파", "ㅋㅋㅋ ${counts.kCount}회")
                } else {
                    Pair("😊 온화한 미소파", "ㅎㅎㅎ ${counts.hCount}회")
                }
            }
            maxScore == counts.waveCount ->
                Pair("🌊 부드러운 다정러", "말끝 물결~ ${counts.waveCount}회")
            maxScore == counts.questionCount ->
                Pair("❓ 호기심 요정", "물음표? ${counts.questionCount}회")
            maxScore == counts.exclamationCount ->
                Pair("🔥 열정의 에너자이저", "느낌표! ${counts.exclamationCount}회")
            else ->
                Pair("✨ 다재다능 토커", "메시지 ${counts.totalMessages}건")
        }
    }

    fun calculateLinguisticQuirks(days: List<ChatDay>): LinguisticQuirksReport {
        val userMap = mutableMapOf<String, UserQuirkCounts>()
        var roomKCount = 0
        var roomHCount = 0

        for (day in days) {
            for (msg in day.messages) {
                val sender = msg.sender.trim()
                if (sender.isBlank() || sender == SYSTEM_SENDER_NAME) continue
                val counts = userMap.getOrPut(sender) { UserQuirkCounts() }
                counts.totalMessages++

                val text = msg.text
                val k = text.count { it == 'ㅋ' }
                val h = text.count { it == 'ㅎ' }
                val wave = text.count { it == '~' }
                val q = text.count { it == '?' }
                val ex = text.count { it == '!' }

                counts.kCount += k
                counts.hCount += h
                counts.waveCount += wave
                counts.questionCount += q
                counts.exclamationCount += ex

                roomKCount += k
                roomHCount += h
            }
        }

        if (userMap.isEmpty()) {
            return LinguisticQuirksReport(
                totalLaughCount = 0,
                dominantLaughType = "ㅋㅋㅋ형",
                users = emptyList(),
                funFact = "대화 내역이 쌓이면 말버릇과 웃음 리포트가 생성됩니다."
            )
        }

        val totalLaugh = roomKCount + roomHCount
        val dominantType = if (roomKCount >= roomHCount) "ㅋㅋㅋ형" else "ㅎㅎㅎ형"

        val users = userMap.entries
            .sortedByDescending { it.value.totalMessages }
            .take(6)
            .map { (name, counts) ->
                val (badge, topExpr) = determineUserQuirkBadge(counts)
                QuirksUser(
                    name = name,
                    laughCount = counts.kCount + counts.hCount,
                    waveCount = counts.waveCount,
                    questionCount = counts.questionCount,
                    exclamationCount = counts.exclamationCount,
                    mainQuirkBadge = badge,
                    topExpression = topExpr
                )
            }

        val ratioStr = if (roomHCount > 0) {
            String.format(Locale.KOREA, "%.1f", roomKCount.toFloat() / roomHCount)
        } else {
            "${roomKCount}"
        }

        val funFact = if (totalLaugh > 0) {
            if (roomKCount >= roomHCount) {
                "이 방은 'ㅋㅋㅋ'가 'ㅎㅎㅎ'보다 ${ratioStr}배 많은 유쾌한 'ㅋㅋㅋ형' 대화방이에요!"
            } else {
                "이 방은 부드러운 'ㅎㅎㅎ' 웃음이 가득한 다정한 대화방이에요!"
            }
        } else {
            "차분하고 진중한 톤으로 깊이 있는 대화를 나누는 방이에요."
        }

        return LinguisticQuirksReport(
            totalLaughCount = totalLaugh,
            dominantLaughType = dominantType,
            users = users,
            funFact = funFact
        )
    }

    fun calculateTalkHeatmap(year: Int, month: Int, days: List<ChatDay>): TalkHeatmapData {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val totalDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val dayMap = days.associate { it.date to (if (it.msgCount > 0) it.msgCount else it.messages.size) }
        val tiles = (1..totalDaysInMonth).map { day ->
            val dateStr = "%04d-%02d-%02d".format(year, month, day)
            val count = dayMap[dateStr] ?: 0
            val level = when {
                count == 0 -> 0
                count <= 10 -> 1
                count <= 40 -> 2
                count <= 100 -> 3
                else -> 4
            }
            DayHeatmapTile(
                dayOfMonth = day,
                date = dateStr,
                count = count,
                level = level
            )
        }

        val activeDays = tiles.count { it.count > 0 }
        val activePct = if (totalDaysInMonth > 0) ((activeDays.toFloat() / totalDaysInMonth) * 100f).roundToInt() else 0
        val maxDayCount = tiles.maxOfOrNull { it.count } ?: 0

        return TalkHeatmapData(
            year = year,
            month = month,
            totalDaysInMonth = totalDaysInMonth,
            activeDaysCount = activeDays,
            activeDayPercentage = activePct,
            tiles = tiles,
            maxDayCount = maxDayCount
        )
    }

    private data class HourlyVolumeSummary(
        val hourlyCounts: List<Int>,
        val peakHour: Int?,
        val peakHourCount: Int
    )

    private fun calculateHourlyVolume(messages: List<Message>): HourlyVolumeSummary {
        val hourly = IntArray(HOURS_IN_DAY)
        for (msg in messages) {
            val h = parseHour(msg.time)
            if (h != null && h in 0 until HOURS_IN_DAY) {
                hourly[h]++
            }
        }
        var peakH: Int? = null
        var peakHCount = 0
        for (h in 0 until HOURS_IN_DAY) {
            if (hourly[h] > peakHCount) {
                peakHCount = hourly[h]
                peakH = h
            }
        }
        return HourlyVolumeSummary(hourly.toList(), peakH, peakHCount)
    }

    private fun determineDailyTrend(
        count: Int,
        avgDailyMessages: Int,
        isPeak: Boolean
    ): Pair<Int, String> {
        if (avgDailyMessages <= 0) {
            return Pair(0, if (count > 0) "대화 ${count}건" else "대화 없음")
        }
        val diff = count - avgDailyMessages
        val pct = ((diff.toFloat() / avgDailyMessages) * 100f).roundToInt()
        val label = when {
            isPeak -> "+${pct}% 피크 🔥"
            pct >= 50 -> "+${pct}% 급등 🔥"
            pct >= 10 -> "+${pct}% 활발 📈"
            pct >= -20 -> "평균 수준 📊"
            count > 0 -> "${pct}% 한산 🍃"
            else -> "대화 없음"
        }
        return Pair(pct, label)
    }

    fun calculateStockChartScrubbingData(
        year: Int,
        month: Int,
        days: List<ChatDay>,
        avgDailyMessages: Int,
        peakDay: PeakDayData?
    ): StockChartScrubbingData {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val totalDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dayByDate = days.associateBy { it.date }

        val points = (1..totalDaysInMonth).map { day ->
            cal.set(Calendar.DAY_OF_MONTH, day)
            val dayOfWeekInt = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 7=Sat
            val dayOfWeekStr = WEEK_DAY_NAMES[(dayOfWeekInt - 1).coerceIn(0, 6)]
            val dateStr = "%04d-%02d-%02d".format(Locale.KOREA, year, month, day)
            val displayDate = "${month}월 ${day}일 (${dayOfWeekStr})"

            val chatDay = dayByDate[dateStr]
            val count = chatDay?.let { if (it.msgCount > 0) it.msgCount else it.messages.size } ?: 0
            val keywords = chatDay?.keywords ?: emptyList()

            val (hourlyCounts, peakH, peakHCount) = calculateHourlyVolume(chatDay?.messages ?: emptyList())
            val isPeak = peakDay != null && peakDay.date == dateStr
            val (pctVsAvg, trendLabel) = determineDailyTrend(count, avgDailyMessages, isPeak)

            DailyScrubbingPoint(
                dayOfMonth = day,
                dateStr = dateStr,
                displayDate = displayDate,
                dayOfWeek = dayOfWeekStr,
                messageCount = count,
                percentVsAvg = pctVsAvg,
                trendLabel = trendLabel,
                isPeakDay = isPeak,
                keywords = keywords,
                hourlyCounts = hourlyCounts,
                peakHour = peakH,
                peakHourCount = peakHCount
            )
        }

        val maxCount = points.maxOfOrNull { it.messageCount } ?: 0
        val peakPoint = points.find { it.isPeakDay } ?: points.maxByOrNull { it.messageCount }

        return StockChartScrubbingData(
            points = points,
            avgDailyCount = avgDailyMessages,
            maxDayCount = maxCount,
            peakPoint = peakPoint
        )
    }
}
