package com.example.data

import com.example.data.db.AppDatabase
import com.example.data.db.ChatDayMapper
import com.example.data.db.SettingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TalkSummaryRepository(private val db: AppDatabase) {

    val chatDaysFlow: Flow<List<ChatDay>> = db.chatDayDao().getAllChatDaysFlow().map { list ->
        list.map { ChatDayMapper.fromEntity(it) }
    }

    suspend fun getAllChatDays(): List<ChatDay> = withContext(Dispatchers.IO) {
        db.chatDayDao().getAllChatDays().map { ChatDayMapper.fromEntity(it) }
    }

    suspend fun saveChatDays(parsedData: Map<String, List<Message>>) = withContext(Dispatchers.IO) {
        parsedData.forEach { (date, messages) ->
            if (messages.isEmpty()) return@forEach

            // Check if there's already an existing record for this date to preserve AI summaries!
            val existing = db.chatDayDao().getChatDayByDate(date)
            // If it exists, keep the existing summary if it contains "[AI 정밀 요약]" or "[AI"
            var summary = ""
            var keywords = emptyList<String>()
            var participants = emptyList<String>()

            if (existing != null) {
                summary = existing.summary
                // Parse existing keywords and participants
                val mapped = ChatDayMapper.fromEntity(existing)
                keywords = mapped.keywords
                participants = mapped.participants
            }

            if (summary.isEmpty() || (!summary.contains("AI") && !summary.contains("요약"))) {
                // Generate a heuristic summary if no existing AI summary
                val localSummary = generateHeuristicSummary(messages)
                summary = localSummary.text
                keywords = localSummary.keywords
                participants = localSummary.participants
            }

            val entity = ChatDayMapper.toEntity(
                date = date,
                messages = messages,
                summary = summary,
                keywords = keywords,
                participants = participants,
                msgCount = messages.size
            )
            db.chatDayDao().insertChatDay(entity)
        }
    }

    suspend fun updateSummary(date: String, summary: String) = withContext(Dispatchers.IO) {
        db.chatDayDao().updateSummary(date, summary)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        db.chatDayDao().clearAll()
    }

    suspend fun saveSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        db.settingsDao().insertSetting(SettingEntity(key, value))
    }

    suspend fun getSetting(key: String): String? = withContext(Dispatchers.IO) {
        db.settingsDao().getSetting(key)?.value
    }

    suspend fun clearAllSettings() = withContext(Dispatchers.IO) {
        db.settingsDao().clearAll()
    }

    private fun generateHeuristicSummary(messages: List<Message>): LocalHeuristic {
        val wordCounts = mutableMapOf<String, Int>()
        val participantCounts = mutableMapOf<String, Int>()

        messages.forEach { m ->
            participantCounts[m.sender] = (participantCounts[m.sender] ?: 0) + 1
            val words = m.text.split(Regex("\\s+"))
            words.forEach { w ->
                val cleanWord = w.trim().replace(Regex("[^a-zA-Zㄱ-ㅎㅏ-ㅣ가-힣0-9]"), "")
                if (cleanWord.length > 1 && !isStopWord(cleanWord)) {
                    wordCounts[cleanWord] = (wordCounts[cleanWord] ?: 0) + 1
                }
            }
        }

        val topParticipants = participantCounts.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }

        val topKeywords = wordCounts.entries
            .sortedByDescending { it.value }
            .take(4)
            .map { it.key }

        val summaryText = "👥 핵심 참여자: ${topParticipants.joinToString(", ")} | 하루 총 ${messages.size}회 상호작용 진행됨."
        return LocalHeuristic(summaryText, topKeywords, topParticipants)
    }

    private fun isStopWord(word: String): Boolean {
        val stopWords = setOf(
            "그리고", "근데", "진짜", "오늘", "내가", "네가", "그냥", "너무",
            "아니", "해서", "하고", "했다", "같아", "진짜로", "우리", "나도", "너도"
        )
        return stopWords.contains(word)
    }

    private data class LocalHeuristic(
        val text: String,
        val keywords: List<String>,
        val participants: List<String>
    )
}
