package com.example.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.util.AppLogger

/**
 * Supported background task types.
 */
enum class TaskType(val displayName: String) {
    PARSE_CHAT("대화 분석 및 파싱"),
    SINGLE_AI_SUMMARY("AI 대화 요약"),
    BULK_AI_SUMMARY("일괄 AI 대화 요약"),
    GGUF_MODEL_IMPORT("GGUF 모델 탑재"),
    SYSTEM_DIAGNOSTIC("시스템 상태 진단")
}

/**
 * Lifecycle states of a background task.
 */
enum class TaskStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED
}

/**
 * Immutable snapshot of background task progress.
 */
data class TaskProgress(
    val taskType: TaskType = TaskType.PARSE_CHAT,
    val status: TaskStatus = TaskStatus.IDLE,
    val title: String = "",
    val detail: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val isIndeterminate: Boolean = true,
    val isCancellable: Boolean = true,
    val startTimeMillis: Long = System.currentTimeMillis()
) {
    val progressPercentage: Float
        get() = if (total > 0) (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    val progressPercentInt: Int
        get() = (progressPercentage * 100).toInt()
}

/**
 * Thread-safe Background Task Manager for controlling lifecycle, progress, and cooperative cancellation.
 */
class BackgroundTaskManager {

    private val _activeTask = MutableStateFlow<TaskProgress?>(null)
    val activeTask: StateFlow<TaskProgress?> = _activeTask.asStateFlow()

    private var activeJob: Job? = null
    private var onCancelCallback: (() -> Unit)? = null

    val isRunning: Boolean
        get() = _activeTask.value?.status == TaskStatus.RUNNING

    /**
     * Starts tracking a new background task.
     */
    fun startTask(
        taskType: TaskType,
        title: String,
        detail: String,
        total: Int = 0,
        isIndeterminate: Boolean = total <= 0,
        isCancellable: Boolean = true,
        job: Job? = null,
        onCancel: (() -> Unit)? = null
    ) {
        activeJob = job
        onCancelCallback = onCancel
        val initial = TaskProgress(
            taskType = taskType,
            status = TaskStatus.RUNNING,
            title = title,
            detail = detail,
            current = 0,
            total = total,
            isIndeterminate = isIndeterminate,
            isCancellable = isCancellable,
            startTimeMillis = System.currentTimeMillis()
        )
        _activeTask.value = initial
        AppLogger.i("BackgroundTaskManager", "Started task: $taskType - '$title'")
    }

    /**
     * Updates the progress of the currently active task.
     */
    fun updateProgress(
        current: Int,
        total: Int? = null,
        detail: String? = null,
        isIndeterminate: Boolean? = null
    ) {
        val currentTask = _activeTask.value ?: return
        if (currentTask.status != TaskStatus.RUNNING) return

        val newTotal = total ?: currentTask.total
        val newIndeterminate = isIndeterminate ?: (newTotal <= 0)

        _activeTask.value = currentTask.copy(
            current = current,
            total = newTotal,
            detail = detail ?: currentTask.detail,
            isIndeterminate = newIndeterminate
        )
    }

    /**
     * Updates the detailed progress message.
     */
    fun updateDetail(detail: String) {
        val currentTask = _activeTask.value ?: return
        if (currentTask.status != TaskStatus.RUNNING) return

        _activeTask.value = currentTask.copy(detail = detail)
    }

    /**
     * Completes the current task successfully.
     */
    fun completeTask(finalDetail: String = "작업이 성공적으로 완료되었습니다.") {
        val currentTask = _activeTask.value ?: return
        AppLogger.i("BackgroundTaskManager", "Completed task: ${currentTask.taskType}")
        _activeTask.value = currentTask.copy(
            status = TaskStatus.COMPLETED,
            detail = finalDetail,
            current = currentTask.total
        )
        cleanup()
    }

    /**
     * Marks the current task as failed with an error message.
     */
    fun failTask(errorMessage: String) {
        val currentTask = _activeTask.value ?: return
        AppLogger.e("BackgroundTaskManager", "Failed task: ${currentTask.taskType} - $errorMessage")
        _activeTask.value = currentTask.copy(
            status = TaskStatus.FAILED,
            detail = errorMessage
        )
        cleanup()
    }

    /**
     * Cancels the currently active task cooperatively.
     */
    fun cancelTask(reason: String = "사용자에 의해 작업이 취소되었습니다."): Boolean {
        val currentTask = _activeTask.value
        if (currentTask == null || currentTask.status != TaskStatus.RUNNING) {
            return false
        }
        if (!currentTask.isCancellable) {
            AppLogger.w("BackgroundTaskManager", "Attempted to cancel a non-cancellable task: ${currentTask.taskType}")
            return false
        }

        AppLogger.i("BackgroundTaskManager", "Cancelling task: ${currentTask.taskType} ($reason)")
        
        // Execute onCancel callback (e.g. stopping native engine)
        try {
            onCancelCallback?.invoke()
        } catch (e: Exception) {
            AppLogger.e("BackgroundTaskManager", "Error in onCancelCallback: ${e.message}", e)
        }

        // Cancel active coroutine job
        activeJob?.cancel(CancellationException(reason))

        _activeTask.value = currentTask.copy(
            status = TaskStatus.CANCELLED,
            detail = reason
        )
        cleanup()
        return true
    }

    /**
     * Clears the active task from tracking (e.g. after dialog is dismissed).
     */
    fun clearTask() {
        _activeTask.value = null
        cleanup()
    }

    private fun cleanup() {
        activeJob = null
        onCancelCallback = null
    }
}
