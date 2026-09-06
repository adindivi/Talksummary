package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Clean Robolectric Integration Test Suite for TalkSummaryRepository & In-Memory AppDatabase.
 * Validates real Room database transactions, heuristic pre-computation, and setting persistence.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TalkSummaryRepositoryRobolectricTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: TalkSummaryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TalkSummaryRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `saveSetting and getSetting persists and retrieves user configuration accurately`() = runBlocking {
        repository.saveSetting("gemini_api_key", "AIzaSyDummyKeyForTesting_12345")
        repository.saveSetting("use_gemini", "true")

        val retrievedKey = repository.getSetting("gemini_api_key")
        val retrievedUse = repository.getSetting("use_gemini")
        val nonExistent = repository.getSetting("non_existent_key")

        assertEquals("AIzaSyDummyKeyForTesting_12345", retrievedKey)
        assertEquals("true", retrievedUse)
        assertNull(nonExistent)
    }

    @Test
    fun `saveChatDays computes heuristic summary and persists chat records`() = runBlocking {
        val sampleChatData = mapOf(
            "2026-06-01" to listOf(
                Message(sender = "이순신", time = "오전 9:00", text = "오늘 일정 공유합니다."),
                Message(sender = "홍길동", time = "오전 9:05", text = "네 확인했습니다."),
                Message(sender = "이순신", time = "오전 9:10", text = "수고하세요.")
            )
        )

        var progressReported = false
        repository.saveChatDays(sampleChatData) { current, total ->
            if (current == 1 && total == 1) {
                progressReported = true
            }
        }

        assertTrue("Progress callback should be executed", progressReported)

        val storedDays = repository.getAllChatDays()
        assertEquals(1, storedDays.size)

        val day = storedDays[0]
        assertEquals("2026-06-01", day.date)
        assertEquals(3, day.msgCount)
        assertTrue(day.participants.contains("이순신"))
        assertTrue(day.participants.contains("홍길동"))
        assertTrue("Heuristic summary should be generated", day.summary.isNotEmpty())
    }

    @Test
    fun `saveChatDays preserves existing AI summary on re-import`() = runBlocking {
        // Initial import with heuristic
        val chatData = mapOf(
            "2026-06-01" to listOf(Message("철수", "10:00", "회의 시작"))
        )
        repository.saveChatDays(chatData)

        // Simulate AI summary generated and updated
        database.chatDayDao().updateSummary("2026-06-01", "[AI 정밀 요약] 프로젝트 킥오프 회의 진행")

        // Re-importing same day
        repository.saveChatDays(chatData)

        val updatedDays = repository.getAllChatDays()
        assertEquals(1, updatedDays.size)
        assertEquals("[AI 정밀 요약] 프로젝트 킥오프 회의 진행", updatedDays[0].summary)
    }

    @Test
    fun `clearAll purges chat records while preserving user settings and clearAllSettings removes settings`() = runBlocking {
        repository.saveSetting("temp_key", "temp_value")
        repository.saveChatDays(mapOf(
            "2026-06-01" to listOf(Message("영희", "12:00", "점심 드셨어요?"))
        ))

        assertEquals(1, repository.getAllChatDays().size)
        assertEquals("temp_value", repository.getSetting("temp_key"))

        // clearAll should purge chat records only
        repository.clearAll()

        assertEquals(0, repository.getAllChatDays().size)
        assertEquals("temp_value", repository.getSetting("temp_key"))

        // clearAllSettings should purge user settings
        repository.clearAllSettings()
        assertNull(repository.getSetting("temp_key"))
    }

    @Test
    fun `chatDaysFlow emits updated list when database contents change`() = runBlocking {
        val initialList = repository.chatDaysFlow.first()
        assertTrue(initialList.isEmpty())

        repository.saveChatDays(mapOf(
            "2026-06-02" to listOf(Message("민수", "15:00", "반갑습니다"))
        ))

        val updatedList = repository.chatDaysFlow.first()
        assertEquals(1, updatedList.size)
        assertEquals("2026-06-02", updatedList[0].date)
    }
}
