package com.example.data.parser

import com.example.data.ChatDay
import com.example.data.TalkSummaryRepository
import com.example.model.MonthlyAnalysisReport
import com.example.model.ParticipantShare
import com.example.model.PeakDayData
import com.example.model.TimeSlotDistribution
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
 */
object ChatAnalyticsEngine {

    /**
     * 전체 ChatDay 목록에서 분석 가능한 연-월 목록을 내림차순("2026-03", "2026-02" 등)으로 추출
     */
    fun getAvailableYearMonths(chatDays: List<ChatDay>): List<String> {
        return chatDays
            .filter { it.date.length >= 7 }
            .map { it.date.substring(0, 7) }
            .filter { it.matches(Regex("""^\d{4}-\d{2}$""")) }
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
        val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
        val month = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val displayMonth = "${year}년 ${month}월"

        val monthDays = chatDays.filter { it.date.startsWith(selectedYearMonth) }

        if (monthDays.isEmpty()) {
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
                chemistryDescription = "대화를 시작하고 서로의 케미를 발견해보세요!"
            )
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
            chemistryDescription = chemistryDesc
        )
    }

    private fun calculateParticipantShares(days: List<ChatDay>): List<ParticipantShare> {
        val senderCounts = mutableMapOf<String, Int>()

        for (day in days) {
            if (day.messages.isNotEmpty()) {
                for (msg in day.messages) {
                    val s = msg.sender.trim()
                    if (s.isNotBlank() && s != "System") {
                        senderCounts[s] = (senderCounts[s] ?: 0) + 1
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

        val sortedList = senderCounts.entries
            .sortedByDescending { it.value }
            .take(10)

        return sortedList.mapIndexed { index, entry ->
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

        // Parse date to "M월 d일 (E)"
        val displayDate = formatDisplayDateWithDayOfWeek(peak.date)

        val keywords = peak.keywords
            .filter { it.isNotBlank() && !TalkSummaryRepository.isStopWord(it) }
            .take(3)
            .ifEmpty {
                // Fallback: take notable words from first few messages
                peak.messages.take(15)
                    .flatMap { it.text.split(Regex("""\s+""")) }
                    .map { it.replace(Regex("""[^\w가-힣]"""), "").trim() }
                    .filter { it.length in 2..7 && !TalkSummaryRepository.isStopWord(it) }
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .map { it.key }
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
                in 6..11 -> morningCount++
                in 12..17 -> afternoonCount++
                in 18..23 -> eveningCount++
                else -> nightCount++ // 0..5
            }
        }

        val totalParsed = morningCount + afternoonCount + eveningCount + nightCount
        if (totalParsed == 0) {
            // Balanced default fallback if timestamps are non-standard
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

        val (personaTitle, personaDesc) = when {
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

    fun parseHour(timeStr: String): Int? {
        val t = timeStr.trim()
        val isPm = t.contains("오후", ignoreCase = true) || t.contains("PM", ignoreCase = true)
        val isAm = t.contains("오전", ignoreCase = true) || t.contains("AM", ignoreCase = true)

        val match = Regex("""(\d{1,2}):\d{2}""").find(t) ?: return null
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
        return days.flatMap { it.messages }
            .flatMap { it.text.split(Regex("""\s+""")) }
            .map { it.replace(Regex("""[^\w가-힣]"""), "").trim() }
            .filter { it.length in 2..6 && !TalkSummaryRepository.isStopWord(it) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(6)
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
                "특정 인물에 치우치지 않고 모두가 고르게 대화에 참여하는 완벽한 티키타카!"
            )
            topKeywords.any { kw ->
                listOf("회의", "일정", "공유", "마감", "확인", "진행", "보고", "업무", "자료", "검토", "작업").any { kw.contains(it) }
            } -> Pair(
                "🤝 든든한 프로 협업팀",
                "일정과 업무 공유가 일사천리로 이뤄지는 신뢰도 100% 프로 협업 케미!"
            )
            topKeywords.any { kw ->
                listOf("밥", "커피", "주말", "약속", "맛집", "축하", "술", "여행", "고기", "치킨", "카페").any { kw.contains(it) }
            } -> Pair(
                "☕ 힐링 가득 아지트",
                "일상의 소소한 온기와 편안한 만남이 가득한 힐링 대화방!"
            )
            else -> Pair(
                "💫 끈끈한 케미 만점방",
                "언제 어디서든 서로에게 든든한 일상의 에너지가 되어주는 케미!"
            )
        }
    }

    private fun formatDisplayDateWithDayOfWeek(isoDate: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
            val parsed = sdf.parse(isoDate)
            if (parsed != null) {
                val cal = Calendar.getInstance(Locale.KOREA).apply { time = parsed }
                val month = cal.get(Calendar.MONTH) + 1
                val day = cal.get(Calendar.DAY_OF_MONTH)
                val dayOfWeekStr = when (cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.SUNDAY -> "일"
                    Calendar.MONDAY -> "월"
                    Calendar.TUESDAY -> "화"
                    Calendar.WEDNESDAY -> "수"
                    Calendar.THURSDAY -> "목"
                    Calendar.FRIDAY -> "금"
                    Calendar.SATURDAY -> "토"
                    else -> ""
                }
                "${month}월 ${day}일 (${dayOfWeekStr})"
            } else {
                isoDate
            }
        } catch (_: Exception) {
            isoDate
        }
    }

    private fun getCurrentYearMonth(): String {
        val cal = Calendar.getInstance()
        return "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }
}
