package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Message(
    val sender: String,
    val time: String,
    val text: String
)

data class ChatDay(
    val date: String,
    val messages: List<Message>,
    val summary: String,
    val keywords: List<String>,
    val participants: List<String>,
    val msgCount: Int
)

enum class TimelineGroupingMode(val label: String) {
    YEAR("년도별"),
    MONTH("월별"),
    DAY("일별")
}

data class MonthGroupData(
    val yearMonthKey: String, // "2026-03"
    val displayTitle: String, // "2026년 03월"
    val year: Int,
    val month: Int,
    val chatDays: List<ChatDay>,
    val totalMessages: Int,
    val daysCount: Int,
    val topKeywords: List<String>,
    val topSenders: List<String>,
    val summarizedDaysCount: Int
)

data class YearGroupData(
    val yearKey: String, // "2026"
    val displayTitle: String, // "2026년"
    val year: Int,
    val chatDays: List<ChatDay>,
    val totalMessages: Int,
    val daysCount: Int,
    val monthsCount: Int,
    val topKeywords: List<String>,
    val topSenders: List<String>,
    val monthBreakdown: List<Pair<String, Int>> // e.g. [("3월", 15), ("2월", 28)]
)

fun groupChatDaysByMonth(chatDays: List<ChatDay>): List<MonthGroupData> {
    if (chatDays.isEmpty()) return emptyList()

    return chatDays
        .filter { it.date.length >= 7 }
        .groupBy { it.date.substring(0, 7) }
        .map { (yearMonthKey, days) ->
            val parts = yearMonthKey.split("-")
            val year = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val month = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val displayTitle = if (year > 0 && month > 0) "${year}년 %02d월".format(month) else yearMonthKey

            val sortedDays = days.sortedByDescending { it.date }
            val totalMessages = sortedDays.sumOf { it.msgCount }
            val daysCount = sortedDays.size
            val summarizedCount = sortedDays.count { it.summary.startsWith("[AI 정밀 요약]") }

            val topKeywords = sortedDays
                .flatMap { it.keywords }
                .filter { it.isNotBlank() && !TalkSummaryRepository.isStopWord(it) }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key }

            val topSenders = sortedDays
                .flatMap { it.participants }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key }

            MonthGroupData(
                yearMonthKey = yearMonthKey,
                displayTitle = displayTitle,
                year = year,
                month = month,
                chatDays = sortedDays,
                totalMessages = totalMessages,
                daysCount = daysCount,
                topKeywords = topKeywords,
                topSenders = topSenders,
                summarizedDaysCount = summarizedCount
            )
        }
        .sortedByDescending { it.yearMonthKey }
}

fun groupChatDaysByYear(chatDays: List<ChatDay>): List<YearGroupData> {
    if (chatDays.isEmpty()) return emptyList()

    return chatDays
        .filter { it.date.length >= 4 }
        .groupBy { it.date.substring(0, 4) }
        .map { (yearKey, days) ->
            val year = yearKey.toIntOrNull() ?: 0
            val displayTitle = if (year > 0) "${year}년" else yearKey

            val sortedDays = days.sortedByDescending { it.date }
            val totalMessages = sortedDays.sumOf { it.msgCount }
            val daysCount = sortedDays.size

            val monthGroups = sortedDays
                .filter { it.date.length >= 7 }
                .groupBy { it.date.substring(5, 7) }

            val monthBreakdown = monthGroups.map { (mStr, mDays) ->
                val mInt = mStr.toIntOrNull() ?: 0
                val label = if (mInt > 0) "${mInt}월" else "${mStr}월"
                label to mDays.size
            }.sortedByDescending { it.first }

            val topKeywords = sortedDays
                .flatMap { it.keywords }
                .filter { it.isNotBlank() && !TalkSummaryRepository.isStopWord(it) }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key }

            val topSenders = sortedDays
                .flatMap { it.participants }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key }

            YearGroupData(
                yearKey = yearKey,
                displayTitle = displayTitle,
                year = year,
                chatDays = sortedDays,
                totalMessages = totalMessages,
                daysCount = daysCount,
                monthsCount = monthGroups.size,
                topKeywords = topKeywords,
                topSenders = topSenders,
                monthBreakdown = monthBreakdown
            )
        }
        .sortedByDescending { it.yearKey }
}

