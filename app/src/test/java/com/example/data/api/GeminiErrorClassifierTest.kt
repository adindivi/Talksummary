package com.example.data.api

import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * Clean Unit Tests for GeminiErrorClassifier.
 * Follows Claude Prompt Pack (Skill 2: Refactoring & Skill 7: Test Code Generator).
 */
class GeminiErrorClassifierTest {

    // -------------------------------------------------------------
    // HTTP Status Code Error Classification Tests
    // -------------------------------------------------------------

    @Test
    fun `classify HTTP 400 with invalid api key returns API Key Error with flag`() {
        val errorInfo = GeminiErrorClassifier.classify(400, "API_KEY_INVALID: Key not found")
        assertEquals("API 키 오류", errorInfo.title)
        assertTrue(errorInfo.isInvalidKey)
        assertFalse(errorInfo.isQuotaExceeded)
        assertTrue(errorInfo.description.contains("[설정] 메뉴"))
    }

    @Test
    fun `classify HTTP 400 with general bad request returns Request Format Error without flag`() {
        val errorInfo = GeminiErrorClassifier.classify(400, "Invalid JSON payload in contents[0]")
        assertEquals("잘못된 요청 형식", errorInfo.title)
        assertFalse(errorInfo.isInvalidKey)
        assertFalse(errorInfo.isQuotaExceeded)
    }

    @Test
    fun `classify HTTP 403 Forbidden returns Permission Denied Error with invalidKey flag`() {
        val errorInfo = GeminiErrorClassifier.classify(403, "Caller does not have required permission")
        assertEquals("API 접근 권한 제한", errorInfo.title)
        assertTrue(errorInfo.isInvalidKey)
        assertFalse(errorInfo.isQuotaExceeded)
    }

    @Test
    fun `classify HTTP 429 Quota Exceeded returns Quota Error with isQuotaExceeded flag`() {
        val errorInfo = GeminiErrorClassifier.classify(429, "Resource has been exhausted (e.g. check quota)")
        assertEquals("무료 할당량(Quota) 초과", errorInfo.title)
        assertTrue(errorInfo.isQuotaExceeded)
        assertFalse(errorInfo.isInvalidKey)
    }

    @Test
    fun `classify HTTP 500 502 503 504 server errors returns Server Overload Error`() {
        listOf(500, 502, 503, 504).forEach { code ->
            val errorInfo = GeminiErrorClassifier.classify(code, "Backend service unavailable")
            assertEquals("AI 서버 일시적 지연", errorInfo.title)
            assertFalse(errorInfo.isInvalidKey)
            assertFalse(errorInfo.isQuotaExceeded)
        }
    }

    @Test
    fun `classify unhandled HTTP status code truncates long response snippet safely`() {
        val veryLongError = "A".repeat(200)
        val errorInfo = GeminiErrorClassifier.classify(418, veryLongError)
        assertEquals("AI 통신 오류 (코드: 418)", errorInfo.title)
        assertTrue(errorInfo.description.endsWith("..."))
        assertTrue(errorInfo.description.length <= 150)
    }

    // -------------------------------------------------------------
    // Exception Classification Tests
    // -------------------------------------------------------------

    @Test
    fun `classifyException with UnknownHostException returns Network Offline Error`() {
        val ex = UnknownHostException("Unable to resolve host generativelanguage.googleapis.com")
        val errorInfo = GeminiErrorClassifier.classifyException(ex)
        assertEquals("네트워크 연결 끊김", errorInfo.title)
        assertTrue(errorInfo.description.contains("Wi-Fi"))
    }

    @Test
    fun `classifyException with SocketTimeoutException returns Timeout Error`() {
        val ex = SocketTimeoutException("Read timed out after 60000ms")
        val errorInfo = GeminiErrorClassifier.classifyException(ex)
        assertEquals("응답 시간 초과", errorInfo.title)
        assertTrue(errorInfo.description.contains("응답 시간이 초과"))
    }

    @Test
    fun `classifyException with SSLHandshakeException returns Generic Error with localized message`() {
        val ex = SSLHandshakeException("Certificate validation failure")
        val errorInfo = GeminiErrorClassifier.classifyException(ex)
        assertEquals("AI 요약 처리 오류", errorInfo.title)
        assertEquals("Certificate validation failure", errorInfo.description)
    }

    @Test
    fun `classifyException with empty exception returns Fallback Error description`() {
        val ex = RuntimeException()
        val errorInfo = GeminiErrorClassifier.classifyException(ex)
        assertEquals("AI 요약 처리 오류", errorInfo.title)
        assertEquals("알 수 없는 오류가 발생했습니다.", errorInfo.description)
    }
}
