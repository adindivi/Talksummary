package com.example

import com.example.data.api.GeminiErrorClassifier
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ExampleUnitTest {

    @Test
    fun geminiErrorClassifier_classifies400InvalidKey() {
        val errorInfo = GeminiErrorClassifier.classify(400, "API_KEY_INVALID: Key not found")
        assertEquals("API 키 오류", errorInfo.title)
        assertTrue(errorInfo.isInvalidKey)
    }

    @Test
    fun geminiErrorClassifier_classifies429QuotaExceeded() {
        val errorInfo = GeminiErrorClassifier.classify(429, "Resource has been exhausted (e.g. check quota)")
        assertEquals("무료 할당량(Quota) 초과", errorInfo.title)
        assertTrue(errorInfo.isQuotaExceeded)
    }

    @Test
    fun geminiErrorClassifier_classifies503ServerOverload() {
        val errorInfo = GeminiErrorClassifier.classify(503, "Service Unavailable")
        assertEquals("AI 서버 일시적 지연", errorInfo.title)
    }

    @Test
    fun geminiErrorClassifier_classifiesUnknownHostException() {
        val errorInfo = GeminiErrorClassifier.classifyException(UnknownHostException("Unable to resolve host"))
        assertEquals("네트워크 연결 끊김", errorInfo.title)
    }

    @Test
    fun geminiErrorClassifier_classifiesTimeoutException() {
        val errorInfo = GeminiErrorClassifier.classifyException(SocketTimeoutException("timeout"))
        assertEquals("응답 시간 초과", errorInfo.title)
    }
}
