package com.example.parser

import com.example.data.parser.KakaoTalkParser
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

class KakaoTalkParserTest {

    @Test
    fun parse_pcFormat_returnsCorrectMessages() {
        val sample = """
            --------------- 2026년 5월 10일 일요일 ---------------
            [홍길동] [오전 10:15] 안녕하세요!
            [이순신] [오후 2:30] 네 반갑습니다.
        """.trimIndent()

        val result = KakaoTalkParser.parse(sample)

        assertEquals(1, result.size)
        assertTrue(result.containsKey("2026-05-10"))

        val messages = result["2026-05-10"]!!
        assertEquals(2, messages.size)

        assertEquals("홍길동", messages[0].sender)
        assertEquals("오전 10:15", messages[0].time)
        assertEquals("안녕하세요!", messages[0].text)

        assertEquals("이순신", messages[1].sender)
        assertEquals("오후 2:30", messages[1].time)
        assertEquals("네 반갑습니다.", messages[1].text)
    }

    @Test
    fun parse_mobileStandardFormat_returnsCorrectMessages() {
        val sample = """
            --------------- 2026년 5월 12일 화요일 ---------------
            오전 9:30, 김유신 : 좋은 아침입니다.
            오후 1:45, 강감찬 : 점심 맛있게 드셨나요?
        """.trimIndent()

        val result = KakaoTalkParser.parse(sample)

        assertEquals(1, result.size)
        assertTrue(result.containsKey("2026-05-12"))

        val messages = result["2026-05-12"]!!
        assertEquals(2, messages.size)

        assertEquals("김유신", messages[0].sender)
        assertEquals("오전 9:30", messages[0].time)
        assertEquals("좋은 아침입니다.", messages[0].text)

        assertEquals("강감찬", messages[1].sender)
        assertEquals("오후 1:45", messages[1].time)
        assertEquals("점심 맛있게 드셨나요?", messages[1].text)
    }

    @Test
    fun parse_inlineKoreanFormat_infersDateFromLine() {
        val sample = """
            2026년 5월 15일 오전 10:30, 세종대왕 : 훈민정음 반포
            2026년 5월 16일 오후 3:20, 이순신 : 출정 준비 완료
        """.trimIndent()

        val result = KakaoTalkParser.parse(sample)

        assertEquals(2, result.size)
        assertTrue(result.containsKey("2026-05-15"))
        assertTrue(result.containsKey("2026-05-16"))

        assertEquals("세종대왕", result["2026-05-15"]!![0].sender)
        assertEquals("훈민정음 반포", result["2026-05-15"]!![0].text)

        assertEquals("이순신", result["2026-05-16"]!![0].sender)
        assertEquals("출정 준비 완료", result["2026-05-16"]!![0].text)
    }

    @Test
    fun parse_dotExportInlineFormat_infersDateAndParsesMessages() {
        val sample = """
            2026. 6. 1. 오전 11:00, 홍길동 : 6월 첫 날입니다.
            2026. 6. 1. 오후 4:15, 이순신 : 회의 참석합니다.
        """.trimIndent()

        val result = KakaoTalkParser.parse(sample)

        assertEquals(1, result.size)
        assertTrue(result.containsKey("2026-06-01"))

        val messages = result["2026-06-01"]!!
        assertEquals(2, messages.size)
        assertEquals("홍길동", messages[0].sender)
        assertEquals("6월 첫 날입니다.", messages[0].text)
    }

    @Test
    fun parse_multiLineMessage_appendsToLastMessage() {
        val sample = """
            --------------- 2026년 5월 20일 수요일 ---------------
            [홍길동] [오전 10:00] 첫 번째 줄
            두 번째 줄 내용입니다.
            세 번째 줄도 이어집니다.
            [이순신] [오전 10:05] 다른 메시지
        """.trimIndent()

        val result = KakaoTalkParser.parse(sample)

        assertEquals(1, result.size)
        val messages = result["2026-05-20"]!!
        assertEquals(2, messages.size)

        val multiLineText = messages[0].text
        assertTrue(multiLineText.contains("첫 번째 줄"))
        assertTrue(multiLineText.contains("두 번째 줄 내용입니다."))
        assertTrue(multiLineText.contains("세 번째 줄도 이어집니다."))

        assertEquals("다른 메시지", messages[1].text)
    }

    @Test
    fun parse_multipleDays_groupsByDateProperly() {
        val sample = """
            --------------- 2026년 5월 1일 금요일 ---------------
            [홍길동] [오전 10:00] Day 1 msg 1
            [홍길동] [오전 10:05] Day 1 msg 2
            --------------- 2026년 5월 2일 토요일 ---------------
            [이순신] [오전 11:00] Day 2 msg 1
        """.trimIndent()

        val result = KakaoTalkParser.parse(sample)

        assertEquals(2, result.size)
        assertEquals(2, result["2026-05-01"]!!.size)
        assertEquals(1, result["2026-05-02"]!!.size)
    }

    @Test
    fun parse_englishFormat_parsesCorrectly() {
        val sample = """
            May 10, 2026
            10:15 AM, John : Hello world
            2:30 PM, Sarah : Hi John!
        """.trimIndent()

        val result = KakaoTalkParser.parse(sample)

        assertEquals(1, result.size)
        assertTrue(result.containsKey("2026-05-10"))

        val messages = result["2026-05-10"]!!
        assertEquals(2, messages.size)
        assertEquals("John", messages[0].sender)
        assertEquals("Hello world", messages[0].text)
        assertEquals("Sarah", messages[1].sender)
        assertEquals("Hi John!", messages[1].text)
    }

    @Test
    fun parse_emptyOrInvalidInput_returnsEmptyMap() {
        val emptyResult = KakaoTalkParser.parse("")
        assertTrue(emptyResult.isEmpty())

        val whitespaceResult = KakaoTalkParser.parse("   \n\n\t  ")
        assertTrue(whitespaceResult.isEmpty())

        val invalidResult = KakaoTalkParser.parse("이것은 카카오톡 대화 내용이 아닙니다.\n아무런 형식도 없습니다.")
        assertTrue(invalidResult.isEmpty())
    }

    @Test
    fun parse_messagesWithoutExplicitDate_usesFallbackDate() {
        val sample = """
            [홍길동] [오후 1:00] 날짜 헤더 없는 메시지
        """.trimIndent()

        // 1. Default fallback to today's date
        val result = KakaoTalkParser.parse(sample)
        val expectedToday = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA).format(java.util.Date())
        assertEquals(1, result.size)
        assertTrue(result.containsKey(expectedToday))
        assertEquals("홍길동", result[expectedToday]!![0].sender)
        assertEquals("날짜 헤더 없는 메시지", result[expectedToday]!![0].text)

        // 2. Custom fallback date
        val customResult = KakaoTalkParser.parse(sample, fallbackDate = "2026-06-13")
        assertTrue(customResult.containsKey("2026-06-13"))
        assertEquals("홍길동", customResult["2026-06-13"]!![0].sender)
    }

    @Test
    fun parse_messagesBeforeFirstDateHeader_attachesToFirstDate() {
        val sample = """
            [홍길동] [오전 9:30] 헤더 이전 선행 메시지
            --------------- 2026년 7월 20일 월요일 ---------------
            [이순신] [오전 10:00] 헤더 이후 메시지
        """.trimIndent()

        val result = KakaoTalkParser.parse(sample)
        assertEquals(1, result.size)
        assertTrue(result.containsKey("2026-07-20"))

        val messages = result["2026-07-20"]!!
        assertEquals(2, messages.size)
        assertEquals("헤더 이전 선행 메시지", messages[0].text)
        assertEquals("헤더 이후 메시지", messages[1].text)
    }

    @Test
    fun parse_slashFormat_parsesCorrectly() {
        val sample = """
            --------------- 2026/08/15 ---------------
            2026/08/15 오후 3:30, 김구 : 광복절 경축
            [안중근] [오후 4:00] 대한독립만세
        """.trimIndent()

        val result = KakaoTalkParser.parse(sample)
        assertEquals(1, result.size)
        assertTrue(result.containsKey("2026-08-15"))

        val messages = result["2026-08-15"]!!
        assertEquals(2, messages.size)
        assertEquals("김구", messages[0].sender)
        assertEquals("안중근", messages[1].sender)
    }

    @Test
    fun parse_pc24hAndMobile24hFormat_parsesCorrectly() {
        val sample = """
            --------------- 2026년 9월 1일 화요일 ---------------
            [홍길동] [14:30] 24시간 PC 메시지
            15:45, 이순신 : 24시간 모바일 메시지
        """.trimIndent()

        val result = KakaoTalkParser.parse(sample)
        assertEquals(1, result.size)
        assertTrue(result.containsKey("2026-09-01"))

        val messages = result["2026-09-01"]!!
        assertEquals(2, messages.size)
        assertEquals("홍길동", messages[0].sender)
        assertEquals("14:30", messages[0].time)
        assertEquals("24시간 PC 메시지", messages[0].text)

        assertEquals("이순신", messages[1].sender)
        assertEquals("15:45", messages[1].time)
        assertEquals("24시간 모바일 메시지", messages[1].text)
    }

    @Test
    fun parse_streamingSequence_parsesCorrectly() {
        val lines = sequenceOf(
            "--------------- 2026년 9월 2일 수요일 ---------------",
            "[개발자] [오전 11:00] 스트리밍 시퀀스 첫 줄",
            "스트리밍 시퀀스 멀티라인 줄바꿈",
            "[기획자] [오전 11:05] 완벽하게 처리 완료!"
        )

        val result = KakaoTalkParser.parse(lines)
        assertEquals(1, result.size)
        assertTrue(result.containsKey("2026-09-02"))

        val messages = result["2026-09-02"]!!
        assertEquals(2, messages.size)
        assertTrue(messages[0].text.contains("스트리밍 시퀀스 멀티라인 줄바꿈"))
    }

    @Test
    fun parseFromBytes_utf8WithBom_parsesSuccessfully() {
        val sample = "\uFEFF--------------- 2026년 5월 10일 일요일 ---------------\n[홍길동] [오전 10:15] 안녕하세요 BOM 테스트!"
        val bytes = sample.toByteArray(Charsets.UTF_8)
        val result = KakaoTalkParser.parseFromBytes(bytes)

        assertEquals(1, result.size)
        assertTrue(result.containsKey("2026-05-10"))
        assertEquals("홍길동", result["2026-05-10"]!![0].sender)
        assertEquals("안녕하세요 BOM 테스트!", result["2026-05-10"]!![0].text)
    }

    @Test
    fun parseFromBytes_eucKrEncoding_parsesSuccessfully() {
        val sample = "--------------- 2026년 5월 10일 일요일 ---------------\n[홍길동] [오전 10:15] EUC-KR 인코딩 테스트입니다"
        val bytes = sample.toByteArray(Charset.forName("EUC-KR"))
        val result = KakaoTalkParser.parseFromBytes(bytes)

        assertEquals(1, result.size)
        assertTrue(result.containsKey("2026-05-10"))
        assertEquals("홍길동", result["2026-05-10"]!![0].sender)
        assertEquals("EUC-KR 인코딩 테스트입니다", result["2026-05-10"]!![0].text)
    }
}
