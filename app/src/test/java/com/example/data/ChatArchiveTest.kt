package com.example.data

import com.example.data.db.ChatArchiveEntity
import com.example.data.db.ChatArchiveMapper
import org.junit.Assert.*
import org.junit.Test

class ChatArchiveTest {

    @Test
    fun participantsMapper_serializesAndDeserializesCorrectly() {
        val original = listOf("김철수", "이영희", "박민수")
        val json = ChatArchiveMapper.participantsToJson(original)

        assertNotNull(json)
        assertTrue(json.contains("김철수"))
        assertTrue(json.contains("이영희"))

        val restored = ChatArchiveMapper.participantsFromJson(json)
        assertEquals(3, restored.size)
        assertEquals("김철수", restored[0])
        assertEquals("이영희", restored[1])
        assertEquals("박민수", restored[2])
    }

    @Test
    fun participantsMapper_handlesEmptyAndCorruptedJsonGracefully() {
        val emptyList = emptyList<String>()
        val emptyJson = ChatArchiveMapper.participantsToJson(emptyList)
        assertEquals("[]", emptyJson)
        assertEquals(emptyList, ChatArchiveMapper.participantsFromJson(emptyJson))

        val corrupted = "not a valid json {["
        val result = ChatArchiveMapper.participantsFromJson(corrupted)
        assertTrue(result.isEmpty())
    }

    @Test
    fun archiveEntity_sortingPrioritizesFavoritesThenRecent() {
        val archive1 = ChatArchiveEntity(
            id = "1",
            fileName = "chat1.txt",
            roomTitle = "동창회",
            importedAt = 1000L,
            lastOpenedAt = 1000L,
            startDate = "2026-01-01",
            endDate = "2026-01-10",
            totalDays = 10,
            totalMessages = 100,
            topParticipantsJson = "[]",
            internalFilePath = "/path/1.txt",
            isFavorite = false
        )

        val archive2 = ChatArchiveEntity(
            id = "2",
            fileName = "chat2.txt",
            roomTitle = "회사 프로젝트",
            importedAt = 2000L,
            lastOpenedAt = 2000L,
            startDate = "2026-02-01",
            endDate = "2026-02-10",
            totalDays = 10,
            totalMessages = 500,
            topParticipantsJson = "[]",
            internalFilePath = "/path/2.txt",
            isFavorite = true // Favorite!
        )

        val archive3 = ChatArchiveEntity(
            id = "3",
            fileName = "chat3.txt",
            roomTitle = "가족방",
            importedAt = 3000L,
            lastOpenedAt = 3000L, // Most recently opened, but not favorite
            startDate = "2026-03-01",
            endDate = "2026-03-05",
            totalDays = 5,
            totalMessages = 300,
            topParticipantsJson = "[]",
            internalFilePath = "/path/3.txt",
            isFavorite = false
        )

        val list = listOf(archive1, archive3, archive2)
        val sorted = list.sortedWith(
            compareByDescending<ChatArchiveEntity> { it.isFavorite }
                .thenByDescending { it.lastOpenedAt }
        )

        // archive2 (favorite) should come first, then archive3 (more recent), then archive1
        assertEquals("2", sorted[0].id)
        assertEquals("3", sorted[1].id)
        assertEquals("1", sorted[2].id)
    }

    @Test
    fun resolveArchiveRoomTitle_heuristicsExtractAccurately() {
        fun resolve(firstLines: List<String>, fileName: String?, topParticipants: List<String>): String {
            for (line in firstLines) {
                val trimmed = line.trim()
                if (trimmed.contains("카카오톡 대화")) {
                    val cleaned = trimmed
                        .replace(Regex("님과\\s*카카오톡\\s*대화.*"), "")
                        .replace(Regex("카카오톡\\s*대화.*"), "")
                        .trim()
                    if (cleaned.isNotBlank()) return cleaned
                }
            }
            if (topParticipants.isNotEmpty()) {
                val names = topParticipants.take(3).joinToString(", ")
                return if (topParticipants.size > 3) "$names 외 대화방" else "$names 대화방"
            }
            if (!fileName.isNullOrBlank()) {
                return fileName.removeSuffix(".txt")
            }
            return "카카오톡 대화방"
        }

        // Case 1: 1-on-1 chat header
        val header1 = listOf("이영희 님과 카카오톡 대화", "저장한 날짜 : 2026. 3. 6. 오후 2:00")
        assertEquals("이영희", resolve(header1, "KakaoTalk_20260306.txt", listOf("이영희")))

        // Case 2: Group chat header
        val header2 = listOf("개발팀 회의 단톡방 카카오톡 대화", "저장한 날짜 : 2026. 3. 6.")
        assertEquals("개발팀 회의 단톡방", resolve(header2, "KakaoTalk_20260306.txt", listOf("철수", "영희")))

        // Case 3: No header, but participants present
        val header3 = listOf("첫 메시지입니다.", "반갑습니다.")
        assertEquals("철수, 영희, 민수 외 대화방", resolve(header3, "KakaoTalk_20260306.txt", listOf("철수", "영희", "민수", "길동")))

        // Case 4: No header, no participants -> file name fallback
        assertEquals("가족단톡_2026", resolve(emptyList(), "가족단톡_2026.txt", emptyList()))
    }
}
