package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.ChatDay
import com.example.data.Message
import com.example.data.db.AppDatabase
import com.example.data.parser.ChatAnalyticsEngine
import com.example.data.parser.StoryGenerator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Clean BDD Production Instrumented Integration Test Suite.
 * Replaces the default Android Studio template dummy test.
 * Validates real Android runtime context, Room database transactions, and engine pipelines.
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    private lateinit var appContext: Context
    private lateinit var inMemoryDb: AppDatabase

    @Before
    fun setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        inMemoryDb = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        inMemoryDb.close()
    }

    @Test
    fun verifyApplicationContextAndPackageIdentity() {
        // Given & When: Target application context is retrieved
        val packageName = appContext.packageName

        // Then: Must match the official TalkSummary application identifier
        assertEquals("com.aistudio.talksummary.kyzaxv", packageName)
        assertNotNull(appContext.filesDir)
    }

    @Test
    fun verifyRoomDatabaseEntityPersistenceInAndroidContext() = runBlocking {
        // Given: A sample chat day record with participants
        val chatDayDao = inMemoryDb.chatDayDao()
        val messages = listOf(
            Message("철수", "10:00", "프로젝트 릴리즈 준비 완료되었습니다."),
            Message("영희", "10:05", "네, QA 검증 시작하겠습니다.")
        )
        val entity = com.example.data.db.ChatDayEntity(
            date = "2026-06-01",
            rawText = "채팅 원본",
            summary = "릴리즈 및 QA 검증 논의",
            keywords = "프로젝트, 릴리즈, QA",
            participants = "철수, 영희",
            msgCount = 2
        )

        // When: Stored into Room DB within real Android runtime
        chatDayDao.insertChatDay(entity)
        val retrieved = chatDayDao.getChatDayByDate("2026-06-01")

        // Then: Entity should be persisted and retrieved with exact fidelity
        assertNotNull(retrieved)
        assertEquals("2026-06-01", retrieved?.date)
        assertEquals(2, retrieved?.msgCount)
        assertEquals("릴리즈 및 QA 검증 논의", retrieved?.summary)
    }

    @Test
    fun verifyAnalyticsAndStoryPipelinesExecuteSafelyInAndroidRuntime() {
        // Given: A full day of communication records
        val messages = listOf(
            Message("팀장", "09:00", "오늘 배포 작업 일정 점검 부탁드립니다."),
            Message("엔지니어", "11:30", "테스트 자동화 스위트 100% 통과했습니다."),
            Message("디자이너", "14:00", "토스 스타일 UI 에셋 최종 반영 완료되었습니다."),
            Message("팀장", "18:00", "모두 수고하셨습니다!")
        )
        val chatDay = ChatDay(
            date = "2026-06-15",
            messages = messages,
            summary = "배포 점검 및 UI 에셋 완료",
            keywords = listOf("배포", "테스트", "UI"),
            participants = listOf("팀장", "엔지니어", "디자이너"),
            msgCount = 4
        )

        // When: Running Story Generator & Chat Analytics Engine on device
        val story = StoryGenerator.generate3CardStory(chatDay)
        val report = ChatAnalyticsEngine.analyzeMonth(listOf(chatDay), "2026-06")

        // Then: 3-Card story and monthly analysis report generated without runtime exceptions
        assertNotNull(story)
        assertEquals(3, story.cards.size)
        assertEquals(4, report.totalMessages)
        assertEquals(1, report.daysCount)
        assertEquals(3, report.participantShares.size)
        assertEquals("팀장", report.participantShares[0].name)
    }
}
