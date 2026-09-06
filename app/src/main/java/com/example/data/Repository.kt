package com.example.data

import androidx.room.withTransaction
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

    suspend fun saveChatDays(
        parsedData: Map<String, List<Message>>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ) {
        // Step 1: Pre-compute CPU intensive heuristic analysis on Dispatchers.Default
        val precomputed = withContext(Dispatchers.Default) {
            val total = parsedData.size
            var count = 0
            parsedData.mapNotNull { (date, messages) ->
                if (messages.isEmpty()) return@mapNotNull null
                val heuristic = generateHeuristicSummary(messages)
                count++
                onProgress?.invoke(count, total)
                Triple(date, messages, heuristic)
            }
        }

        // Step 2: Perform DB transaction and insertions in chunks on Dispatchers.IO
        withContext(Dispatchers.IO) {
            precomputed.chunked(500).forEach { batch ->
                db.withTransaction {
                    batch.forEach { (date, messages, heuristic) ->
                        // Check if there's already an existing record for this date to preserve AI summaries!
                        val existing = db.chatDayDao().getChatDayByDate(date)
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
                            // Use precomputed heuristic summary if no existing AI summary
                            summary = heuristic.text
                            keywords = heuristic.keywords
                            participants = heuristic.participants
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
            }
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
        }
        val participantNames = participantCounts.keys.toSet()

        messages.forEach { m ->
            val words = m.text.split(Regex("\\s+"))
            words.forEach { w ->
                val cleanWord = w.trim().replace(Regex("[^a-zA-Zㄱ-ㅎㅏ-ㅣ가-힣0-9]"), "")
                if (cleanWord.length > 1 && !isStopWord(cleanWord, participantNames)) {
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

        val summaryText = "👥 ${topParticipants.joinToString(", ")}님이 총 ${messages.size}개의 이야기를 나눴어요."
        return LocalHeuristic(summaryText, topKeywords, topParticipants)
    }

    companion object {
        private val STOP_WORDS = setOf(
            // 1. 카카오톡 시스템/미디어 메타 노이즈
            "사진", "이모티콘", "동영상", "음성메시지", "파일", "보이스톡", "페이스톡", "삭제된", "메시지입니다", "샵검색",
            // 2. 업무 격식/인사말/종결어미
            "부탁드립니다", "부탁드려요", "부탁해요", "감사합니다", "고맙습니다", "안녕하세요", "안녕하십니까",
            "확인했습니다", "확인부탁드립니다", "알겠습니다", "수고하셨습니다", "수고하세요",
            "있습니다", "없습니다", "드립니다", "됩니다", "안됩니다", "어떻게", "가능할까요",
            // 3. 일상 대명사/접속사/부사
            "그리고", "근데", "진짜", "오늘", "내가", "네가", "그냥", "너무", "아니", "해서", "하고", "했다", "같아",
            "진짜로", "우리", "나도", "너도", "혹시", "이거", "그거", "저거", "어떤", "다른", "다시", "먼저", "계속",
            "그럼", "그러면", "그래도", "아마", "바로", "조금", "모두", "모든", "매우", "항상", "이후", "이전",
            "많이", "지금", "내일", "어제", "관련", "대해", "통해", "위해",
            // 4. 직급/호칭
            "매니저", "매니저님", "책임", "책임님", "프로", "프로님", "팀장", "팀장님", "과장", "과장님", "부장", "부장님",
            "대리", "대리님", "사원", "선임", "수석", "대표", "대표님", "이사", "상무", "전무", "선생님", "담당자", "담당자님"
        )

        fun isStopWord(word: String, participantNames: Set<String> = emptySet()): Boolean {
            if (STOP_WORDS.contains(word)) return true
            // 발화자 실명 또는 호칭이 포함된 경우 제외
            if (participantNames.any { it.isNotBlank() && (it.contains(word) || word.contains(it)) }) {
                return true
            }
            return false
        }
    }

    private data class LocalHeuristic(
        val text: String,
        val keywords: List<String>,
        val participants: List<String>
    )
}
