package com.example.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BackgroundTaskManagerTest {

    private lateinit var manager: BackgroundTaskManager

    @Before
    fun setUp() {
        manager = BackgroundTaskManager()
    }

    @Test
    fun testInitialStateIsIdle() {
        assertNull(manager.activeTask.value)
        assertFalse(manager.isRunning)
    }

    @Test
    fun testStartTaskTransitionsToRunning() {
        manager.startTask(
            taskType = TaskType.BULK_AI_SUMMARY,
            title = "전체 일괄 요약",
            detail = "준비 중...",
            total = 10,
            isIndeterminate = false,
            isCancellable = true
        )

        val task = manager.activeTask.value
        assertNotNull(task)
        assertEquals(TaskType.BULK_AI_SUMMARY, task?.taskType)
        assertEquals(TaskStatus.RUNNING, task?.status)
        assertEquals("전체 일괄 요약", task?.title)
        assertEquals("준비 중...", task?.detail)
        assertEquals(0, task?.current)
        assertEquals(10, task?.total)
        assertEquals(0f, task?.progressPercentage)
        assertEquals(0, task?.progressPercentInt)
        assertTrue(manager.isRunning)
    }

    @Test
    fun testUpdateProgressUpdatesCurrentAndDetail() {
        manager.startTask(
            taskType = TaskType.PARSE_CHAT,
            title = "대화 파싱",
            detail = "시작",
            total = 20,
            isIndeterminate = false
        )

        manager.updateProgress(current = 5, detail = "5일치 완료")
        var task = manager.activeTask.value
        assertEquals(5, task?.current)
        assertEquals(20, task?.total)
        assertEquals("5일치 완료", task?.detail)
        assertEquals(0.25f, task?.progressPercentage)
        assertEquals(25, task?.progressPercentInt)

        manager.updateProgress(current = 15, detail = "15일치 완료")
        task = manager.activeTask.value
        assertEquals(15, task?.current)
        assertEquals(0.75f, task?.progressPercentage)
        assertEquals(75, task?.progressPercentInt)
    }

    @Test
    fun testCompleteTaskTransitionsToCompleted() {
        manager.startTask(
            taskType = TaskType.SINGLE_AI_SUMMARY,
            title = "단일 요약",
            detail = "생성 중...",
            total = 1
        )

        manager.completeTask("요약 완료됨")
        val task = manager.activeTask.value
        assertEquals(TaskStatus.COMPLETED, task?.status)
        assertEquals("요약 완료됨", task?.detail)
        assertEquals(1, task?.current)
        assertFalse(manager.isRunning)
    }

    @Test
    fun testFailTaskTransitionsToFailed() {
        manager.startTask(
            taskType = TaskType.GGUF_MODEL_IMPORT,
            title = "GGUF 모델 탑재",
            detail = "복사 중..."
        )

        manager.failTask("저장 공간 부족")
        val task = manager.activeTask.value
        assertEquals(TaskStatus.FAILED, task?.status)
        assertEquals("저장 공간 부족", task?.detail)
        assertFalse(manager.isRunning)
    }

    @Test
    fun testCancelTaskCooperativeCancellation() {
        var callbackInvoked = false
        val job = Job()

        manager.startTask(
            taskType = TaskType.BULK_AI_SUMMARY,
            title = "일괄 요약",
            detail = "처리 중...",
            total = 10,
            isCancellable = true,
            job = job,
            onCancel = {
                callbackInvoked = true
            }
        )

        val cancelled = manager.cancelTask("사용자 취소")
        assertTrue(cancelled)
        assertTrue(callbackInvoked)
        assertTrue(job.isCancelled)

        val task = manager.activeTask.value
        assertEquals(TaskStatus.CANCELLED, task?.status)
        assertEquals("사용자 취소", task?.detail)
        assertFalse(manager.isRunning)
    }

    @Test
    fun testNonCancellableTaskCannotBeCancelled() {
        manager.startTask(
            taskType = TaskType.SYSTEM_DIAGNOSTIC,
            title = "시스템 점검",
            detail = "검사 중...",
            isCancellable = false
        )

        val cancelled = manager.cancelTask("취소 시도")
        assertFalse(cancelled)
        assertEquals(TaskStatus.RUNNING, manager.activeTask.value?.status)
        assertTrue(manager.isRunning)
    }

    @Test
    fun testTaskProgressBoundaryCalculations() {
        // Zero total
        val zeroTotal = TaskProgress(total = 0, current = 0)
        assertEquals(0f, zeroTotal.progressPercentage, 0.001f)
        assertEquals(0, zeroTotal.progressPercentInt)

        // Current exceeding total
        val overflow = TaskProgress(total = 10, current = 15)
        assertEquals(1.0f, overflow.progressPercentage, 0.001f)
        assertEquals(100, overflow.progressPercentInt)

        // Negative current
        val negative = TaskProgress(total = 10, current = -5)
        assertEquals(0f, negative.progressPercentage, 0.001f)
        assertEquals(0, negative.progressPercentInt)
    }
}
