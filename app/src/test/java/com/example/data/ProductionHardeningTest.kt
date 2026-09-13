package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.ChatDayMapper
import com.example.data.parser.GeminiWebtoonEngine
import com.example.data.parser.KakaoTalkParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.Charset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProductionHardeningTest {

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
    fun `Phase 1 - Room summary projection excludes rawMessagesJson preventing 2MB CursorWindow crashes`() = runBlocking {
        val messages = (1..50).map { i ->
            Message("사용자$i", "10:0$i", "이것은 데이터베이스 커서 윈도우 한도를 방어하기 위한 대용량 메시지 내용 $i 입니다.")
        }
        val sampleMap = mapOf("2026-07-01" to messages)

        repository.saveChatDays(sampleMap, archiveId = "test_archive")

        // 1. Direct DAO projection check
        val projections = database.chatDayDao().getSummaryChatDaysFlow("test_archive").first()
        assertEquals(1, projections.size)
        val proj = projections[0]
        assertEquals("2026-07-01", proj.date)
        assertEquals("test_archive", proj.archiveId)
        assertEquals(50, proj.msgCount)

        // 2. ChatDayMapper.fromSummary check (messages should be empty in lightweight view)
        val lightweightDay = ChatDayMapper.fromSummary(proj)
        assertTrue("Lightweight day must have empty messages list", lightweightDay.messages.isEmpty())
        assertEquals(50, lightweightDay.msgCount)

        // 3. On-demand message loading check
        val fullDay = repository.getChatDayWithMessages("2026-07-01", archiveId = "test_archive")
        assertNotNull(fullDay)
        assertEquals(50, fullDay!!.messages.size)
        assertEquals("사용자1", fullDay.messages[0].sender)
    }

    @Test
    fun `Phase 2 - Multi-archive isolation preserves AI summaries during room switching`() = runBlocking {
        // Room A import
        val roomAMessages = mapOf(
            "2026-07-01" to listOf(Message("팀장", "09:00", "프로젝트 A 시작")),
            "2026-07-02" to listOf(Message("팀장", "09:00", "프로젝트 A 진행"))
        )
        repository.saveChatDays(roomAMessages, archiveId = "room_a")
        database.chatDayDao().updateSummary("room_a", "2026-07-01", "[AI 정밀 요약] 프로젝트 A 킥오프")

        // Room B import
        val roomBMessages = mapOf(
            "2026-07-01" to listOf(Message("친구", "18:00", "저녁 뭐 먹을까?"))
        )
        repository.saveChatDays(roomBMessages, archiveId = "room_b")
        database.chatDayDao().updateSummary("room_b", "2026-07-01", "[AI 정밀 요약] 저녁 메뉴 상의")

        // Switch to Room A: flow emits only Room A days with its AI summary
        repository.setActiveArchiveId("room_a")
        val roomADays = repository.chatDaysFlow.first()
        assertEquals(2, roomADays.size)
        val dayA1 = roomADays.find { it.date == "2026-07-01" }
        assertNotNull(dayA1)
        assertEquals("[AI 정밀 요약] 프로젝트 A 킥오프", dayA1!!.summary)

        // Switch to Room B: flow emits only Room B days with its AI summary
        repository.setActiveArchiveId("room_b")
        val roomBDays = repository.chatDaysFlow.first()
        assertEquals(1, roomBDays.size)
        val dayB1 = roomBDays.find { it.date == "2026-07-01" }
        assertNotNull(dayB1)
        assertEquals("[AI 정밀 요약] 저녁 메뉴 상의", dayB1!!.summary)

        // Delete Room B: Room A days and summaries remain 100% untouched
        repository.deleteChatDaysForArchive("room_b")
        assertEquals(0, database.chatDayDao().getChatDaysCountByArchive("room_b"))
        assertEquals(2, database.chatDayDao().getChatDaysCountByArchive("room_a"))

        val fullDayA = repository.getChatDayWithMessages("2026-07-01", "room_a")
        assertEquals("[AI 정밀 요약] 프로젝트 A 킥오프", fullDayA?.summary)
    }

    @Test
    fun `Phase 3 - KakaoTalkParser detectCharset identifies encodings without full decode`() {
        val sampleKorean = "2026년 5월 10일 일요일\n[홍길동] [오전 10:15] 안녕하세요!"

        // UTF-8 with BOM
        val utf8BomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + sampleKorean.toByteArray(Charsets.UTF_8)
        assertEquals(Charsets.UTF_8, KakaoTalkParser.detectCharset(utf8BomBytes))

        // UTF-16LE with BOM
        val utf16LeBomBytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + sampleKorean.toByteArray(Charsets.UTF_16LE)
        assertEquals(Charsets.UTF_16LE, KakaoTalkParser.detectCharset(utf16LeBomBytes))

        // EUC-KR
        val eucKrBytes = sampleKorean.toByteArray(Charset.forName("EUC-KR"))
        assertEquals(Charset.forName("EUC-KR"), KakaoTalkParser.detectCharset(eucKrBytes))
    }

    @Test
    fun `Phase 3 - KakaoTalkParser filters system announcements and handles bracketed date headers`() {
        // System announcements detection
        assertTrue(KakaoTalkParser.isSystemAnnouncement("홍길동님이 들어왔습니다."))
        assertTrue(KakaoTalkParser.isSystemAnnouncement("이순신님이 나갔습니다."))
        assertTrue(KakaoTalkParser.isSystemAnnouncement("채팅방 관리자가 메시지를 가렸습니다."))
        assertTrue(KakaoTalkParser.isSystemAnnouncement("운영정책을 위반하여 가려진 메시지입니다."))
        assertFalse(KakaoTalkParser.isSystemAnnouncement("오늘 회의는 2시에 시작합니다."))

        // System message should NOT append to preceding chat message
        val chatWithSystemNoise = """
            [2026년 8월 10일 월요일]
            [철수] [오전 11:00] 안녕하세요 여러분!
            영희님이 들어왔습니다.
            [영희] [오전 11:02] 반갑습니다~
        """.trimIndent()

        val parsed = KakaoTalkParser.parse(chatWithSystemNoise)
        assertEquals(1, parsed.size)
        val messages = parsed["2026-08-10"]!!
        assertEquals(2, messages.size)

        // 철수's message text must NOT have "영희님이 들어왔습니다." appended!
        assertEquals("안녕하세요 여러분!", messages[0].text)
        assertEquals("반갑습니다~", messages[1].text)
    }

    @Test
    fun `Phase 5 - Stratified time sampling samples early, mid, and late day messages`() {
        val allMessages = (1..90).map { i ->
            Pair("화자$i", "메시지 $i 내용 (시간대별 발언)")
        }

        val sampled = GeminiWebtoonEngine.sampleStratifiedMessages(allMessages, maxCount = 45)

        assertEquals(45, sampled.size)
        // Earliest message from morning segment
        assertTrue(sampled.any { it.second == "메시지 1 내용 (시간대별 발언)" })
        // Midday message from afternoon segment
        assertTrue(sampled.any { it.second.contains("메시지 4") || it.second.contains("메시지 3") })
        // Latest message from evening segment
        assertTrue(sampled.any { it.second == "메시지 90 내용 (시간대별 발언)" })
    }
}
