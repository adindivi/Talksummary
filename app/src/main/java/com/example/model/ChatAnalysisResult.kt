package com.example.model

/**
 * 참여자별 발언 지분율 및 랭킹 데이터
 */
data class ParticipantShare(
    val name: String,
    val count: Int,
    val percentage: Int, // e.g. 48 -> 48%
    val rank: Int,       // 1, 2, 3...
    val badge: String    // "이 달의 수다왕", "소통 조율자", "분위기 메이커", "열정 멤버"
)

/**
 * 월간 피크 데이 (가장 뜨거웠던 하루) 데이터
 */
data class PeakDayData(
    val date: String,             // "2026-03-18"
    val displayDate: String,      // "3월 18일 (수)"
    val messageCount: Int,        // 185
    val percentageOfTotal: Int,   // 26%
    val peakKeywords: List<String> // ["회식", "프로젝트", "2차"]
)

/**
 * 대화 시간대별 분포 및 라이프스타일 페르소나
 */
data class TimeSlotDistribution(
    val morningCount: Int,     // 06:00 ~ 11:59 (오전)
    val afternoonCount: Int,   // 12:00 ~ 17:59 (오후/낮)
    val eveningCount: Int,     // 18:00 ~ 23:59 (저녁/밤)
    val nightCount: Int,       // 00:00 ~ 05:59 (심야/새벽)
    val morningPercent: Int,
    val afternoonPercent: Int,
    val eveningPercent: Int,
    val nightPercent: Int,
    val personaTitle: String,       // "☀️ 낮 소통형", "🌙 저녁 수다형", "🦉 심야 올빼미형"
    val personaDescription: String  // 설명 텍스트
)

/**
 * 월간 대화 심층 분석 종합 리포트
 */
data class MonthlyAnalysisReport(
    val yearMonthKey: String,          // "2026-03"
    val displayMonth: String,          // "2026년 3월"
    val totalMessages: Int,
    val daysCount: Int,
    val avgDailyMessages: Int,
    val participantShares: List<ParticipantShare>,
    val peakDay: PeakDayData?,
    val timeSlotStats: TimeSlotDistribution,
    val topKeywords: List<String>,
    val chemistryTitle: String,        // "✨ 환상의 티키타카방"
    val chemistryDescription: String  // 관계 케미 설명
)
