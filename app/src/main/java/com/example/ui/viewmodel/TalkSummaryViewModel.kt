package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ChatDay
import com.example.data.Message
import com.example.data.TimelineGroupingMode
import com.example.data.TalkSummaryRepository
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GeminiApiClient
import com.example.data.api.Content as ApiContent
import com.example.data.api.Part as ApiPart
import com.example.data.api.GenerationConfig
import com.example.data.db.AppDatabase
import com.example.data.db.ChatArchiveEntity
import com.example.data.db.ChatArchiveMapper
import com.example.data.parser.KakaoTalkParser
import com.example.data.llm.ModelLoader
import com.example.engine.GgufParser
import com.example.engine.StreamTokenEvent
import com.example.model.GgufMetadata
import com.example.service.BackgroundTaskManager
import com.example.service.BatteryOptimizationHelper
import com.example.service.InferenceForegroundService
import com.example.service.TaskProgress
import com.example.service.TaskStatus
import com.example.service.TaskType
import com.example.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class TalkSummaryViewModel(
    application: Application,
    private val repository: TalkSummaryRepository
) : AndroidViewModel(application) {

    private val modelLoader = ModelLoader(application)

    // Background Task Manager
    val backgroundTaskManager = BackgroundTaskManager()
    val activeTask: StateFlow<TaskProgress?> = backgroundTaskManager.activeTask

    // GGUF Model State
    private val _loadedGgufMetadata = MutableStateFlow<GgufMetadata?>(null)
    val loadedGgufMetadata = _loadedGgufMetadata.asStateFlow()

    // UI States
    private val _geminiApiKey = MutableStateFlow("")
    val geminiApiKey = _geminiApiKey.asStateFlow()

    private val _useGemini = MutableStateFlow(false)
    val useGemini = _useGemini.asStateFlow()

    private val _useLocalModel = MutableStateFlow(false)
    val useLocalModel = _useLocalModel.asStateFlow()

    private val _localModelPath = MutableStateFlow("")
    val localModelPath = _localModelPath.asStateFlow()

    private val _mainUser = MutableStateFlow("")
    val mainUser = _mainUser.asStateFlow()

    private val _activeModel = MutableStateFlow("gemini-3.5-flash")
    val activeModel = _activeModel.asStateFlow()

    // Filters
    private val _startDateFilter = MutableStateFlow("")
    val startDateFilter = _startDateFilter.asStateFlow()

    private val _endDateFilter = MutableStateFlow("")
    val endDateFilter = _endDateFilter.asStateFlow()

    // Timeline Grouping Mode (Galaxy Gallery One UI Style: Day, Month, Year)
    private val _timelineGroupingMode = MutableStateFlow(TimelineGroupingMode.DAY)
    val timelineGroupingMode: StateFlow<TimelineGroupingMode> = _timelineGroupingMode.asStateFlow()

    // Modals
    private val _showSettings = MutableStateFlow(false)
    val showSettings = _showSettings.asStateFlow()

    private val _showPasteModal = MutableStateFlow(false)
    val showPasteModal = _showPasteModal.asStateFlow()

    private val _showArchiveModal = MutableStateFlow(false)
    val showArchiveModal = _showArchiveModal.asStateFlow()

    // Chat Archives (대화방 보관함)
    val chatArchives: StateFlow<List<ChatArchiveEntity>> =
        repository.chatArchivesFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _activeArchiveId = MutableStateFlow<String?>(null)
    val activeArchiveId = _activeArchiveId.asStateFlow()

    private val _showErrorDetails = MutableStateFlow(false)
    val showErrorDetails = _showErrorDetails.asStateFlow()

    private val _errorTitle = MutableStateFlow("")
    val errorTitle = _errorTitle.asStateFlow()

    private val _errorDescription = MutableStateFlow("")
    val errorDescription = _errorDescription.asStateFlow()

    // Statuses
    private val _isOnline = MutableStateFlow(true)
    val isOnline = _isOnline.asStateFlow()

    private val _dbState = MutableStateFlow("안전보관")
    val dbState = _dbState.asStateFlow()

    private val _parserState = MutableStateFlow("대기 중")
    val parserState = _parserState.asStateFlow()

    private val _aiState = MutableStateFlow("대기")
    val aiState = _aiState.asStateFlow()

    private val _diagnosticLogs = MutableStateFlow("")
    val diagnosticLogs = _diagnosticLogs.asStateFlow()
    
    private val _localInferenceStats = MutableStateFlow<String?>(null)
    val localInferenceStats = _localInferenceStats.asStateFlow()

    // Concurrency guard to prevent rapid double-trigger or overlapping AI tasks
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    // Currently summarizing date for in-card progress display
    private val _activeSummarizingDate = MutableStateFlow<String?>(null)
    val activeSummarizingDate: StateFlow<String?> = _activeSummarizingDate.asStateFlow()

    // Last detailed error info for user-friendly guidance
    private val _lastErrorInfo = MutableStateFlow<com.example.data.api.GeminiErrorInfo?>(null)
    val lastErrorInfo = _lastErrorInfo.asStateFlow()

    // Battery Optimization & Doze mode status
    private val _isBatteryOptimizationIgnored = MutableStateFlow<Boolean>(false)
    val isBatteryOptimizationIgnored: StateFlow<Boolean> = _isBatteryOptimizationIgnored.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Loading overlay (synced with backgroundTaskManager for full backward compatibility)
    val isLoading = backgroundTaskManager.activeTask.map { it != null && it.status == TaskStatus.RUNNING }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    val loadingTitle = backgroundTaskManager.activeTask.map { it?.title ?: "" }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ""
    )

    val loadingMessage = backgroundTaskManager.activeTask.map { it?.detail ?: "" }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ""
    )

    // Toast notifications
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage = _toastMessage.asStateFlow()

    private val _toastType = MutableStateFlow("info")
    val toastType = _toastType.asStateFlow()

    // Selections
    private val _selectedChatDay = MutableStateFlow<ChatDay?>(null)
    val selectedChatDay = _selectedChatDay.asStateFlow()

    // Raw timeline flow from DB
    val timelineData = repository.chatDaysFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Filtered timeline data
    val filteredTimelineData = combine(
        timelineData,
        _startDateFilter,
        _endDateFilter
    ) { list, start, end ->
        var result = list
        if (start.isNotEmpty()) {
            result = result.filter { it.date >= start }
        }
        if (end.isNotEmpty()) {
            result = result.filter { it.date <= end }
        }
        result
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        _isBatteryOptimizationIgnored.value = BatteryOptimizationHelper.isBatteryOptimizationIgnored(application)
        loadAllSettings()
        setupNetworkListener()
        setupAbortListener()
        viewModelScope.launch {
            timelineData.collect { list ->
                _parserState.value = if (list.isNotEmpty()) "정상작동" else "빈 보관소"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        modelLoader.close()
        networkCallback?.let { callback ->
            try {
                val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                AppLogger.e("TalkSummaryViewModel", "Failed to unregister network callback: ${e.message}")
            }
        }
    }

    private fun setupAbortListener() {
        viewModelScope.launch {
            InferenceForegroundService.abortEvents.collect {
                AppLogger.i("TalkSummaryViewModel", "Received abort event from Foreground Service Notification")
                cancelActiveTask("상단 알림창에서 요약을 멈췄어요.")
            }
        }
    }

    private fun loadAllSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val key = repository.getSetting("gemini_api_key") ?: ""
            _geminiApiKey.value = key

            val use = repository.getSetting("use_gemini") ?: "false"
            _useGemini.value = use == "true"

            val useLocal = repository.getSetting("use_local_model") ?: "false"
            _useLocalModel.value = useLocal == "true"

            val localPath = repository.getSetting("local_model_path") ?: ""
            _localModelPath.value = localPath

            if (localPath.isNotEmpty()) {
                val file = File(localPath)
                if (file.exists()) {
                    val parsed = GgufParser.parse(file)
                    if (parsed.isSuccess) {
                        _loadedGgufMetadata.value = parsed.getOrNull()
                    }
                }
            }

            val model = repository.getSetting("configured_model") ?: "gemini-3.5-flash"
            _activeModel.value = model

            val user = repository.getSetting("main_user_name") ?: ""
            _mainUser.value = user

            val activeArchive = repository.getSetting("active_archive_id")
            _activeArchiveId.value = activeArchive

            updateAiState()
        }
    }

    private fun updateAiState() {
        _aiState.value = if (_useLocalModel.value && _localModelPath.value.isNotEmpty()) {
            "내 폰 안의 AI"
        } else if (_useGemini.value && _geminiApiKey.value.isNotEmpty()) {
            "연결됨"
        } else if (_geminiApiKey.value.isNotEmpty()) {
            "대기"
        } else {
            "오프라인"
        }
    }

    fun setShowSettings(show: Boolean) {
        _showSettings.value = show
    }

    fun setShowPasteModal(show: Boolean) {
        _showPasteModal.value = show
    }

    fun setShowErrorDetails(show: Boolean) {
        _showErrorDetails.value = show
    }

    fun showError(title: String, description: String) {
        _errorTitle.value = title
        _errorDescription.value = description
        _showErrorDetails.value = true
    }

    fun selectChatDay(chatDay: ChatDay?) {
        _selectedChatDay.value = chatDay
        if (chatDay != null && (_mainUser.value.isEmpty() || !chatDay.participants.contains(_mainUser.value))) {
            val firstParticipant = chatDay.participants.firstOrNull() ?: ""
            setMainUser(firstParticipant)
        }
    }

    fun setDateFilters(start: String, end: String) {
        _startDateFilter.value = start
        _endDateFilter.value = end
        if (start.isNotEmpty() || end.isNotEmpty()) {
            val filterDesc = when {
                start.isNotEmpty() && end.isNotEmpty() -> "${start} ~ ${end} 기간만 모아보기"
                start.isNotEmpty() -> "${start}부터 이후 대화 모아보기"
                else -> "${end}까지의 대화 모아보기"
            }
            showToast(filterDesc, "info")
        }
    }

    fun clearFilters() {
        _startDateFilter.value = ""
        _endDateFilter.value = ""
        showToast("모든 날짜의 대화를 다시 보여드려요.", "success")
    }

    fun setTimelineGroupingMode(mode: TimelineGroupingMode) {
        _timelineGroupingMode.value = mode
    }

    fun filterByYearMonth(yearMonth: String) {
        _startDateFilter.value = "$yearMonth-01"
        _endDateFilter.value = "$yearMonth-31"
        _timelineGroupingMode.value = TimelineGroupingMode.DAY
        showToast("${yearMonth} 일별 대화를 보여드려요.", "info")
    }

    fun filterByYear(year: String) {
        _startDateFilter.value = "$year-01-01"
        _endDateFilter.value = "$year-12-31"
        _timelineGroupingMode.value = TimelineGroupingMode.MONTH
        showToast("${year}년 월별 대화를 보여드려요.", "info")
    }

    fun setMainUser(name: String) {
        _mainUser.value = name
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSetting("main_user_name", name)
        }
    }

    fun toggleSenderMode() {
        val selected = _selectedChatDay.value ?: return
        val participants = selected.participants
        if (participants.isEmpty()) return

        val currentIndex = participants.indexOf(_mainUser.value)
        val nextIndex = (currentIndex + 1) % participants.size
        val nextUser = participants[nextIndex]
        setMainUser(nextUser)
        showToast("'${nextUser}'님을 나(노란 말풍선)로 바꿨어요.", "success")
    }

    private var toastJob: Job? = null

    fun showToast(message: String, type: String = "info") {
        toastJob?.cancel()
        _toastMessage.value = message
        _toastType.value = type
        toastJob = viewModelScope.launch(Dispatchers.Main) {
            delay(2600)
            if (_toastMessage.value == message) {
                _toastMessage.value = null
            }
        }
    }

    fun saveSettings(key: String, useGemini: Boolean, model: String, useLocal: Boolean = false, localPath: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            _geminiApiKey.value = key
            _useGemini.value = useGemini
            _activeModel.value = model
            _useLocalModel.value = useLocal
            _localModelPath.value = localPath
            
            repository.saveSetting("gemini_api_key", key)
            repository.saveSetting("use_gemini", if (useGemini) "true" else "false")
            repository.saveSetting("configured_model", model)
            repository.saveSetting("use_local_model", if (useLocal) "true" else "false")
            repository.saveSetting("local_model_path", localPath)
            
            updateAiState()
            showToast("설정을 안전하게 저장했어요.", "success")
            _showSettings.value = false
        }
    }

    fun refreshBatteryOptimizationStatus() {
        _isBatteryOptimizationIgnored.value = BatteryOptimizationHelper.isBatteryOptimizationIgnored(getApplication())
    }

    fun requestIgnoreBatteryOptimization(context: Context) {
        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
        refreshBatteryOptimizationStatus()
    }

    fun cancelActiveTask(reason: String = "요약을 멈췄어요.") {
        if (backgroundTaskManager.isRunning) {
            modelLoader.stopInference()
            backgroundTaskManager.cancelTask(reason)
            InferenceForegroundService.stop(getApplication())
            _activeSummarizingDate.value = null
            _isProcessing.value = false
            showToast(reason, "info")
        }
    }

    fun dismissTaskProgress() {
        backgroundTaskManager.clearTask()
    }

    fun parseAndImportText(rawText: String) {
        if (_isProcessing.value) {
            showToast("지금 진행 중인 작업이 끝난 뒤에 다시 눌러주세요.", "info")
            return
        }
        _showPasteModal.value = false
        _isProcessing.value = true

        viewModelScope.launch {
            backgroundTaskManager.startTask(
                taskType = TaskType.PARSE_CHAT,
                title = "대화 내용 분석 중",
                detail = "대화 내용을 꼼꼼하게 읽고 있어요...",
                isIndeterminate = true,
                isCancellable = true
            )
            InferenceForegroundService.start(getApplication(), "⚡ 대화 분석 중", "대화 내용을 꼼꼼하게 읽고 정리하는 중이에요...")

            try {
                // Step 1: CPU-intensive regex parsing completely isolated on Dispatchers.Default
                val parsed = withContext(Dispatchers.Default) {
                    KakaoTalkParser.parse(rawText)
                }

                coroutineContext.ensureActive()

                if (parsed.isEmpty()) {
                    backgroundTaskManager.failTask("카카오톡 대화를 찾지 못했어요.")
                    showError("대화를 불러올 수 없어요", "카카오톡 대화 형식을 찾을 수 없어요.\n복사한 내용이 올바른 카카오톡 대화 내용인지 다시 한 번 확인해 주세요.")
                    return@launch
                }

                // Step 2: Database saving with progress updates on Dispatchers.IO
                backgroundTaskManager.updateProgress(0, parsed.size, "날짜별로 예쁘게 정리하고 있어요 (0/${parsed.size}일)...", false)
                repository.clearAll()
                repository.saveChatDays(parsed) { current, total ->
                    backgroundTaskManager.updateProgress(
                        current = current,
                        total = total,
                        detail = "날짜별로 예쁘게 정리하고 있어요 (${current}/${total}일)...",
                        isIndeterminate = false
                    )
                    InferenceForegroundService.updateProgress(
                        getApplication(),
                        "대화 정리 중 (${current}/${total}일)",
                        current,
                        total
                    )
                }

                selectChatDay(null)
                backgroundTaskManager.completeTask("총 ${parsed.size}일간의 대화를 성공적으로 불러왔어요.")
                showToast("대화를 성공적으로 불러왔어요!", "success")
            } catch (ce: CancellationException) {
                AppLogger.i("TalkSummaryViewModel", "parseAndImportText cancelled")
                backgroundTaskManager.cancelTask("대화 불러오기를 멈췄어요.")
            } catch (oom: OutOfMemoryError) {
                AppLogger.e("TalkSummaryViewModel", "Out of memory during text parsing", oom)
                backgroundTaskManager.failTask("기기 메모리가 부족하여 중단되었어요.")
                showError("메모리가 부족해요", "대화 내용이 너무 방대하여 기기 메모리에서 한 번에 처리할 수 없어요. 기간을 나누어 입력해 주세요.")
            } catch (e: Exception) {
                AppLogger.e("TalkSummaryViewModel", "Error parsing text", e)
                backgroundTaskManager.failTask("대화 읽기 오류: ${e.localizedMessage}")
                showError("대화 읽기 오류", "대화를 정리하는 중 문제가 발생했어요: ${e.localizedMessage}")
            } finally {
                InferenceForegroundService.stop(getApplication())
                _isProcessing.value = false
            }
        }
    }

    fun parseAndImportBytes(bytes: ByteArray, fileName: String? = null) {
        if (_isProcessing.value) {
            showToast("지금 진행 중인 작업이 끝난 뒤에 다시 눌러주세요.", "info")
            return
        }
        _showPasteModal.value = false
        _isProcessing.value = true

        viewModelScope.launch {
            backgroundTaskManager.startTask(
                taskType = TaskType.PARSE_CHAT,
                title = "카톡 파일 읽는 중",
                detail = "대화 파일을 꼼꼼하게 확인하고 있어요...",
                isIndeterminate = true,
                isCancellable = true
            )
            InferenceForegroundService.start(getApplication(), "⚡ 카톡 파일 읽는 중", "대화 파일을 분석하고 있어요...")

            try {
                // Step 1: CPU-intensive byte parsing on Dispatchers.Default
                val parsed = withContext(Dispatchers.Default) {
                    KakaoTalkParser.parseFromBytes(bytes)
                }

                coroutineContext.ensureActive()

                if (parsed.isEmpty()) {
                    backgroundTaskManager.failTask("카카오톡 대화 형식을 찾지 못했어요.")
                    showError("파일을 불러올 수 없어요", "카카오톡 대화 형식을 찾을 수 없어요.\n카카오톡에서 '대화 내용 내보내기'로 저장한 .txt 파일인지 확인해 주세요.")
                    return@launch
                }

                // Step 2: Database saving with progress updates on Dispatchers.IO
                backgroundTaskManager.updateProgress(0, parsed.size, "날짜별로 예쁘게 정리하고 있어요 (0/${parsed.size}일)...", false)
                repository.clearAll()
                repository.saveChatDays(parsed) { current, total ->
                    backgroundTaskManager.updateProgress(
                        current = current,
                        total = total,
                        detail = "날짜별로 예쁘게 정리하고 있어요 (${current}/${total}일)...",
                        isIndeterminate = false
                    )
                    InferenceForegroundService.updateProgress(
                        getApplication(),
                        "대화 정리 중 (${current}/${total}일)",
                        current,
                        total
                    )
                }

                // Step 3: Archive creation & internal storage caching
                val archiveId = java.util.UUID.randomUUID().toString()
                val archivesDir = File(getApplication<Application>().filesDir, "archives").apply { mkdirs() }
                val cachedFile = File(archivesDir, "${archiveId}.txt")
                withContext(Dispatchers.IO) {
                    cachedFile.writeBytes(bytes)
                }

                val firstLines = withContext(Dispatchers.Default) {
                    try {
                        bytes.inputStream().bufferedReader().useLines { lines ->
                            lines.take(6).toList()
                        }
                    } catch (_: Exception) {
                        emptyList<String>()
                    }
                }
                val allParticipants = parsed.values.flatten().map { it.sender.trim() }.distinct().filter { it.isNotBlank() }
                val roomTitle = resolveArchiveRoomTitle(firstLines, fileName, allParticipants)
                val sortedDates = parsed.keys.sorted()
                val startDate = sortedDates.firstOrNull() ?: ""
                val endDate = sortedDates.lastOrNull() ?: ""
                val now = System.currentTimeMillis()

                val archiveEntity = ChatArchiveEntity(
                    id = archiveId,
                    fileName = fileName ?: "카카오톡_대화.txt",
                    roomTitle = roomTitle,
                    importedAt = now,
                    lastOpenedAt = now,
                    startDate = startDate,
                    endDate = endDate,
                    totalDays = parsed.size,
                    totalMessages = parsed.values.sumOf { it.size },
                    topParticipantsJson = ChatArchiveMapper.participantsToJson(allParticipants.take(5)),
                    internalFilePath = cachedFile.absolutePath,
                    isFavorite = false
                )
                repository.insertArchive(archiveEntity)
                _activeArchiveId.value = archiveId
                repository.saveSetting("active_archive_id", archiveId)

                selectChatDay(null)
                backgroundTaskManager.completeTask("총 ${parsed.size}일간의 대화를 성공적으로 불러왔어요.")
                showToast("대화를 성공적으로 불러왔어요!", "success")
            } catch (ce: CancellationException) {
                AppLogger.i("TalkSummaryViewModel", "parseAndImportBytes cancelled")
                backgroundTaskManager.cancelTask("대화 파일 불러오기를 멈췄어요.")
            } catch (oom: OutOfMemoryError) {
                AppLogger.e("TalkSummaryViewModel", "Out of memory during byte parsing", oom)
                backgroundTaskManager.failTask("기기 메모리가 부족하여 중단되었어요.")
                showError("메모리가 부족해요", "대화 파일이 너무 커서 기기 메모리에서 처리하기 어려워요. 대화 기간을 나누어 불러와 주세요.")
            } catch (e: Exception) {
                AppLogger.e("TalkSummaryViewModel", "Error parsing bytes", e)
                backgroundTaskManager.failTask("대화 파일 읽기 오류: ${e.localizedMessage}")
                showError("대화 파일 읽기 오류", "대화 파일을 읽는 중 문제가 발생했어요: ${e.localizedMessage}")
            } finally {
                InferenceForegroundService.stop(getApplication())
                _isProcessing.value = false
            }
        }
    }

    fun setShowArchiveModal(show: Boolean) {
        _showArchiveModal.value = show
    }

    fun toggleArchiveFavorite(archive: ChatArchiveEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateArchiveFavorite(archive.id, !archive.isFavorite)
        }
    }

    fun updateArchiveTitle(archiveId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateArchiveTitle(archiveId, trimmed)
            }
        }
    }

    fun deleteArchive(archive: ChatArchiveEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(archive.internalFilePath)
                if (file.exists()) {
                    file.delete()
                }
                repository.deleteArchive(archive.id)
                if (_activeArchiveId.value == archive.id) {
                    _activeArchiveId.value = null
                    repository.saveSetting("active_archive_id", "")
                }
                showToast("'${archive.roomTitle}' 보관함에서 삭제되었어요.", "info")
            } catch (e: Exception) {
                AppLogger.e("TalkSummaryViewModel", "Failed to delete archive: ${e.message}")
                showToast("삭제 중 오류가 발생했어요: ${e.message}", "error")
            }
        }
    }

    fun loadArchive(archive: ChatArchiveEntity) {
        if (_isProcessing.value) {
            showToast("지금 진행 중인 작업이 끝난 뒤에 다시 눌러주세요.", "info")
            return
        }
        _showArchiveModal.value = false
        _isProcessing.value = true

        viewModelScope.launch {
            backgroundTaskManager.startTask(
                taskType = TaskType.PARSE_CHAT,
                title = "보관된 대화 불러오는 중",
                detail = "'${archive.roomTitle}' 대화를 열고 있어요...",
                isIndeterminate = true,
                isCancellable = false
            )
            InferenceForegroundService.start(getApplication(), "⚡ 대화방 전환 중", "'${archive.roomTitle}' 대화를 열고 있어요...")

            try {
                val file = File(archive.internalFilePath)
                if (!file.exists()) {
                    backgroundTaskManager.failTask("보관된 대화 파일을 찾을 수 없어요.")
                    showError("파일 없음", "보관된 대화 파일이 기기에서 삭제되었거나 찾을 수 없어요.")
                    return@launch
                }

                val bytes = withContext(Dispatchers.IO) {
                    file.readBytes()
                }

                val parsed = withContext(Dispatchers.Default) {
                    KakaoTalkParser.parseFromBytes(bytes)
                }

                if (parsed.isEmpty()) {
                    backgroundTaskManager.failTask("대화 내용을 불러오지 못했어요.")
                    showError("불러오기 오류", "대화 파일 형식을 해석하지 못했어요.")
                    return@launch
                }

                repository.clearAll()
                repository.saveChatDays(parsed)

                val now = System.currentTimeMillis()
                repository.updateArchiveLastOpened(archive.id, now)
                _activeArchiveId.value = archive.id
                repository.saveSetting("active_archive_id", archive.id)

                selectChatDay(null)
                backgroundTaskManager.completeTask("'${archive.roomTitle}' 대화방을 열었어요.")
                showToast("'${archive.roomTitle}' 대화방으로 전환되었어요!", "success")
            } catch (e: Exception) {
                AppLogger.e("TalkSummaryViewModel", "Error loading archive", e)
                backgroundTaskManager.failTask("대화 불러오기 오류: ${e.localizedMessage}")
                showError("대화 불러오기 오류", "대화를 여는 중 문제가 발생했어요: ${e.localizedMessage}")
            } finally {
                InferenceForegroundService.stop(getApplication())
                _isProcessing.value = false
            }
        }
    }

    private fun resolveArchiveRoomTitle(
        firstLines: List<String>,
        fileName: String?,
        topParticipants: List<String>
    ): String {
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

    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
            selectChatDay(null)
            updateAiState()
            showToast("대화 기록을 모두 깨끗하게 비웠어요.", "success")
        }
    }

    fun copyAndSetLocalModel(context: Context, uri: android.net.Uri) {
        if (_isProcessing.value) {
            showToast("지금 진행 중인 작업이 끝난 뒤에 다시 눌러주세요.", "info")
            return
        }
        _isProcessing.value = true

        viewModelScope.launch(Dispatchers.IO) {
            backgroundTaskManager.startTask(
                taskType = TaskType.GGUF_MODEL_IMPORT,
                title = "내 폰 안의 AI 준비 중",
                detail = "선택하신 AI 모델 파일을 안전하게 준비하고 있어요...",
                total = 100,
                isIndeterminate = false,
                isCancellable = true
            )
            InferenceForegroundService.start(getApplication(), "⚡ AI 모델 준비 중", "내 폰 안의 AI 모델 파일을 가져오고 있어요...")

            try {
                var rawName = "model.gguf"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        rawName = cursor.getString(nameIndex) ?: "model.gguf"
                    }
                }
                if (!rawName.endsWith(".gguf", ignoreCase = true)) {
                    rawName += ".gguf"
                }

                val importResult = modelLoader.sandboxManager.importGgufToSandbox(uri, rawName) { progress ->
                    val percent = (progress * 100).toInt()
                    backgroundTaskManager.updateProgress(percent, 100, "AI 모델 복사 중 ($percent%)")
                    InferenceForegroundService.updateProgress(getApplication(), "AI 모델 복사 중 ($percent%)", percent, 100)
                }

                coroutineContext.ensureActive()

                if (importResult.isFailure) {
                    throw importResult.exceptionOrNull() ?: Exception("AI 모델 파일 가져오기 실패")
                }

                val targetFile = importResult.getOrThrow()
                backgroundTaskManager.updateDetail("AI 모델 구조와 설정을 확인하고 있어요...")

                val loadResult = modelLoader.loadModel(targetFile.absolutePath)
                if (loadResult.isFailure) {
                    throw loadResult.exceptionOrNull() ?: Exception("AI 모델 파일 분석 실패")
                }

                val meta = loadResult.getOrThrow()

                _localModelPath.value = targetFile.absolutePath
                _loadedGgufMetadata.value = meta
                repository.saveSetting("local_model_path", targetFile.absolutePath)
                updateAiState()

                backgroundTaskManager.completeTask("내 폰 안의 AI 준비 완료: ${meta.modelName}")
                showToast("AI 모델 준비 완료! 이제 인터넷 없이도 요약할 수 있어요.", "success")
            } catch (ce: CancellationException) {
                AppLogger.i("TalkSummaryViewModel", "copyAndSetLocalModel cancelled")
                backgroundTaskManager.cancelTask("AI 모델 준비를 멈췄어요.")
            } catch (e: Exception) {
                AppLogger.e("TalkSummaryViewModel", "copyAndSetLocalModel error: ${e.message}", e)
                backgroundTaskManager.failTask("AI 모델 준비 실패: ${e.message}")
                showError("AI 모델 준비 실패", "AI 모델을 처리하는 중 문제가 발생했어요: ${e.message}")
            } finally {
                InferenceForegroundService.stop(getApplication())
                _isProcessing.value = false
            }
        }
    }

    /**
     * Filters out non-content system noise such as "[사진]", "[이모티콘]", file transfers, etc.
     * to prevent Small Language Models (Qwen GGUF) from hallucinating noise into the summary.
     */
    fun filterMeaningfulMessages(messages: List<Message>): List<Message> {
        val noiseRegex = Regex("^((\\[?(사진|이모티콘|동영상|음성메시지|파일|보이스톡|페이스톡|샵검색)\\]?(\\s*\\d+장)?)|(삭제된 메시지입니다\\.?))(\\s*)$")
        val filtered = messages.filter { msg ->
            val text = msg.text.trim()
            text.isNotEmpty() && !noiseRegex.matches(text)
        }
        return if (filtered.isNotEmpty()) filtered else messages
    }

    companion object {
        /**
         * Cleans AI generated summary output by trimming code blocks and removing accidental bracket headers.
         */
        fun cleanAiSummaryOutput(raw: String): String {
            var text = raw.trim()
                .removePrefix("```markdown")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            // Strip bracket headers e.g. "1. [배경 및 주제: ...]" or "1. [핵심 내용: ...]"
            val bracketLabelRegex = Regex("""(?m)^(\s*\d+\.\s*)\[(?:배경|주제|핵심|결론|상황|내용|요약)[^\]]*:\s*([^\]]+)\]""")
            text = bracketLabelRegex.replace(text) { matchResult ->
                "${matchResult.groupValues[1]}${matchResult.groupValues[2].trim()}"
            }
            // Also strip any plain bracket wrapper e.g. "1. [내용]" -> "1. 내용"
            val plainBracketRegex = Regex("""(?m)^(\s*\d+\.\s*)\[([^\]\n]+)\]\s*${'$'}""")
            text = plainBracketRegex.replace(text) { matchResult ->
                "${matchResult.groupValues[1]}${matchResult.groupValues[2].trim()}"
            }
            return text.trim()
        }
    }

    fun buildLocalGgufPrompt(messages: List<Message>): String {
        val targetMessages = filterMeaningfulMessages(messages)
        val serialized = targetMessages.joinToString("\n") { "[${it.sender}]: ${it.text}" }

        return """
다음 메신저 대화 내용을 바탕으로 누구나 이해하기 아주 쉽고 다정한 표현으로 핵심 대화 내용을 3줄로 요약해 주세요.

[요약 작성 규칙]
1. 대괄호([])나 '배경:', '주제:', '핵심:', '결론:' 같은 머리말 분류 라벨을 절대 붙이지 마세요.
2. 발화자의 이름(예: OOO님이)과 구체적으로 어떤 상황이었는지 명확히 밝혀 온전한 서술형 문장(~했어요, ~부탁했어요, ~알려줬어요)으로 작성하세요.
3. 1번 문장은 대화가 시작된 구체적 계기와 상황, 2번 문장은 대화자 간에 오고 간 주요 내용과 부탁, 3번 문장은 최종 마무리된 조치나 약속을 담으세요.
4. 단순 인사말, 이모티콘, 사진 전송은 제외하고 실제 대화 맥락만 요약하세요.
5. 앞뒤 인사말이나 사족 없이 오직 1., 2., 3. 세 줄만 번호로 출력하세요.

[모범 작성 예시]
1. 연호님이 조립 부품 태그와 배터리 공구에 문제가 생겼다고 상황을 공유하며 교체를 요청했어요.
2. 황보세웅님이 태그를 교체해 주었고, 연호님은 잦은 고장의 근본 원인을 파악해 달라고 부탁했어요.
3. 황보세웅님이 교체를 완료한 후, 배터리 공구 단선 문제도 함께 점검하여 해결하기로 약속했어요.

[대화 내용]
$serialized

[핵심 3줄 요약]
""".trimIndent()
    }

    fun triggerSingleSummarize(chatDay: ChatDay) {
        if (_isProcessing.value) {
            showToast("지금 진행 중인 작업이 끝난 뒤에 다시 눌러주세요.", "info")
            return
        }

        val useLocal = _useLocalModel.value
        val localPath = _localModelPath.value
        val key = _geminiApiKey.value

        if (!useLocal && key.isEmpty()) {
            showError("API 키가 필요해요", "Google Gemini AI 요약을 이용하시려면 [설정] 메뉴에서 API 키를 먼저 등록해 주세요.")
            return
        }

        if (useLocal && localPath.isEmpty()) {
            showError("AI 모델 파일이 필요해요", "내 폰 안의 AI 요약을 이용하시려면 [설정] 메뉴에서 AI 모델 파일(.gguf)을 먼저 선택해 주세요.")
            return
        }

        val title = if (useLocal) "내 폰 안의 AI 요약" else "구글 제미나이 AI 요약"
        val message = if (useLocal) "${chatDay.date} 핵심 대화를 오프라인으로 요약하고 있어요..." else "${chatDay.date} 핵심 대화를 똑똑하게 요약하고 있어요..."

        _isProcessing.value = true
        _activeSummarizingDate.value = chatDay.date

        viewModelScope.launch {
            backgroundTaskManager.startTask(
                taskType = TaskType.SINGLE_AI_SUMMARY,
                title = title,
                detail = message,
                isIndeterminate = true,
                isCancellable = true,
                onCancel = {
                    if (useLocal) modelLoader.stopInference()
                }
            )
            InferenceForegroundService.start(getApplication(), "⚡ $title", message)

            try {
                val responseText = if (useLocal) {
                    val localPrompt = buildLocalGgufPrompt(chatDay.messages)
                    val startTime = System.currentTimeMillis()
                    
                    val loadRes = modelLoader.loadModel(localPath)
                    if (loadRes.isFailure) {
                        throw loadRes.exceptionOrNull() ?: Exception("GGUF 모델 로드 실패")
                    }
                    _loadedGgufMetadata.value = loadRes.getOrNull()

                    var generatedText = ""
                    var lastTps = 0.0
                    var tokensCount = 0

                    modelLoader.streamResponse(localPrompt).collect { event ->
                        coroutineContext.ensureActive()
                        when (event) {
                            is StreamTokenEvent.Token -> {
                                lastTps = event.tokensPerSecond
                                tokensCount = event.tokensGenerated
                                val stats = "속도: ${String.format(Locale.US, "%.1f", lastTps)} tok/s | 생성: ${tokensCount}단어"
                                _localInferenceStats.value = stats
                                backgroundTaskManager.updateDetail("${chatDay.date} 3줄 요약 작성 중... (${tokensCount}단어, ${String.format(Locale.US, "%.1f", lastTps)} tok/s)")
                                InferenceForegroundService.updateProgress(getApplication(), "요약 작성 중...", 0, 0, title, lastTps)
                            }
                            is StreamTokenEvent.Completed -> {
                                generatedText = event.fullText
                                lastTps = event.tokensPerSecond
                                tokensCount = event.totalTokens
                            }
                            is StreamTokenEvent.Error -> {
                                throw Exception(event.message)
                            }
                        }
                    }

                    val durationSec = (System.currentTimeMillis() - startTime) / 1000.0
                    val runtime = Runtime.getRuntime()
                    val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                    _localInferenceStats.value = "완료: ${String.format(Locale.US, "%.1f", lastTps)} tok/s | ${tokensCount}단어 (${String.format(Locale.US, "%.1f", durationSec)}s) | 메모리: ${usedMemMb}MB"
                    
                    viewModelScope.launch {
                        delay(3500)
                        _localInferenceStats.value = null
                    }

                    generatedText
                } else {
                    val prompt = buildGeminiPrompt(chatDay.messages)
                    requestGemini(prompt, key, _activeModel.value)
                }

                coroutineContext.ensureActive()

                if (!responseText.isNullOrEmpty()) {
                    val cleaned = cleanAiSummaryOutput(responseText)
                    val formatted = "[AI 정밀 요약]\n$cleaned"
                    repository.updateSummary(chatDay.date, formatted)
                    
                    if (_selectedChatDay.value?.date == chatDay.date) {
                        _selectedChatDay.value = _selectedChatDay.value?.copy(summary = formatted)
                    }
                    backgroundTaskManager.completeTask("${chatDay.date} AI 3줄 요약이 완성되었어요.")
                    showToast("오늘의 3줄 요약이 완성되었어요!", "success")
                } else {
                    val error = _lastErrorInfo.value
                    if (error != null) {
                        backgroundTaskManager.failTask(error.description)
                        showError(error.title, error.description)
                    } else {
                        backgroundTaskManager.failTask("AI 응답을 받지 못했습니다.")
                        showError("요약할 수 없어요", "AI 응답을 받지 못했어요. 인터넷 연결이나 API 키 설정을 확인해 주세요.")
                    }
                }
            } catch (ce: CancellationException) {
                AppLogger.i("TalkSummaryViewModel", "triggerSingleSummarize cancelled")
                backgroundTaskManager.cancelTask("AI 요약을 멈췄어요.")
            } catch (e: Exception) {
                AppLogger.e("TalkSummaryViewModel", "AI summarization error", e)
                backgroundTaskManager.failTask("AI 요약 처리 오류: ${e.localizedMessage}")
                showError("요약 중 문제가 생겼어요", "대화를 요약하는 도중 문제가 발생했어요: ${e.localizedMessage}")
            } finally {
                InferenceForegroundService.stop(getApplication())
                _activeSummarizingDate.value = null
                _isProcessing.value = false
            }
        }
    }

    fun triggerBulkSummarize() {
        if (_isProcessing.value) {
            showToast("지금 진행 중인 작업이 끝난 뒤에 다시 눌러주세요.", "info")
            return
        }

        val key = _geminiApiKey.value
        if (key.isEmpty()) {
            showError("API 키가 필요해요", "전체 날짜를 일괄 요약하시려면 [설정] 메뉴에서 Google API 키를 먼저 등록해 주세요.")
            return
        }

        val list = timelineData.value
        val unsummarized = list.filter { !it.summary.startsWith("[AI 정밀 요약]") }
        if (unsummarized.isEmpty()) {
            showToast("이미 모든 날짜의 대화가 깔끔하게 요약되어 있어요.", "success")
            return
        }

        val total = unsummarized.size
        _isProcessing.value = true

        refreshBatteryOptimizationStatus()
        if (!_isBatteryOptimizationIgnored.value && total >= 3) {
            showToast("💡 화면이 꺼져도 중단 없이 요약하려면 [설정]에서 배터리 절전 예외를 켜두세요.", "info")
        }

        viewModelScope.launch {
            backgroundTaskManager.startTask(
                taskType = TaskType.BULK_AI_SUMMARY,
                title = "전체 날짜 일괄 3줄 요약",
                detail = "총 ${total}일치 대화 요약을 준비하고 있어요...",
                total = total,
                isIndeterminate = false,
                isCancellable = true
            )
            InferenceForegroundService.start(
                getApplication(),
                "⚡ 전체 대화 일괄 요약",
                "차근차근 대화를 요약하고 있어요 (총 ${total}일)",
                0,
                total
            )

            var count = 0
            var failCount = 0
            try {
                for (item in unsummarized) {
                    coroutineContext.ensureActive()
                    count++
                    val progressDesc = "(${count}/${total}) ${item.date} 대화 요약 중..."
                    backgroundTaskManager.updateProgress(count, total, progressDesc, false)
                    InferenceForegroundService.updateProgress(
                        getApplication(),
                        progressDesc,
                        count,
                        total,
                        "⚡ 전체 대화 일괄 요약"
                    )

                    try {
                        val prompt = buildGeminiPrompt(item.messages)
                        val result = requestGemini(prompt, key, _activeModel.value)
                        coroutineContext.ensureActive()
                        if (result != null) {
                            val cleaned = cleanAiSummaryOutput(result)
                            val formatted = "[AI 정밀 요약]\n$cleaned"
                            repository.updateSummary(item.date, formatted)
                            if (_selectedChatDay.value?.date == item.date) {
                                _selectedChatDay.value = _selectedChatDay.value?.copy(summary = formatted)
                            }
                        } else {
                            failCount++
                            val err = _lastErrorInfo.value
                            if (err != null && (err.isQuotaExceeded || err.isInvalidKey)) {
                                backgroundTaskManager.failTask("(${item.date} 요약 중 중단됨) ${err.description}")
                                showError(err.title, "(${item.date} 요약 중 중단됨)\n${err.description}")
                                break
                            }
                        }
                        delay(1200) // Rate limiting pacing delay
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        failCount++
                        AppLogger.e("TalkSummaryViewModel", "Error summarizing ${item.date}", e)
                    }
                }
                if (backgroundTaskManager.isRunning) {
                    backgroundTaskManager.completeTask("전체 ${total}일 중 ${count - failCount}일 요약 완료")
                    showToast("모든 날짜의 대화 요약을 마쳤어요!", "success")
                }
            } catch (ce: CancellationException) {
                AppLogger.i("TalkSummaryViewModel", "triggerBulkSummarize cancelled")
                backgroundTaskManager.cancelTask("일괄 요약 작업을 멈췄어요.")
            } finally {
                InferenceForegroundService.stop(getApplication())
                _isProcessing.value = false
            }
        }
    }

    fun runSmartDiagnosticConnection(apiKeyArg: String) {
        val trimmedKey = apiKeyArg.trim()
        if (trimmedKey.isEmpty()) {
            showToast("먼저 Google AI API 키를 입력해 주세요.", "error")
            return
        }

        _diagnosticLogs.value = "[연결 검사] 구글 AI 서버로 테스트 신호를 보냅니다...\n\n"

        viewModelScope.launch {
            backgroundTaskManager.startTask(
                taskType = TaskType.SYSTEM_DIAGNOSTIC,
                title = "AI 연결 테스트",
                detail = "구글 AI 서버와 정상적으로 통신할 수 있는지 확인하고 있어요...",
                isIndeterminate = true,
                isCancellable = true
            )

            val candidateModels = listOf(
                "gemini-2.5-flash",
                "gemini-2.5-pro",
                "gemini-3.5-flash",
                "gemini-3.1-pro-preview",
                "gemini-3.1-flash-lite-preview"
            )
            var success = false
            var chosenModel = ""

            for (model in candidateModels) {
                coroutineContext.ensureActive()
                _diagnosticLogs.value += "🛰️ [확인 중] $model 모델 연결... "
                backgroundTaskManager.updateDetail("$model 모델 연결 테스트 중...")
                try {
                    val response = requestGemini(
                        prompt = "Hello, respond with exactly 'OK' to confirm health check.",
                        apiKey = trimmedKey,
                        model = model
                    )
                    if (response != null && response.isNotEmpty()) {
                        _diagnosticLogs.value += "➔ ✅ 연결 성공!\n"
                        success = true
                        chosenModel = model
                        break
                    } else {
                        _diagnosticLogs.value += "➔ ❌ 응답 없음\n"
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    _diagnosticLogs.value += "➔ 💥 오류 [${e.localizedMessage}]\n"
                }
                delay(500)
            }

            if (success) {
                _diagnosticLogs.value += "\n🎉 연결 성공!\n이 기기에 가장 알맞은 [$chosenModel] 모델을 기본 AI 비서로 설정했어요."
                _activeModel.value = chosenModel
                repository.saveSetting("configured_model", chosenModel)
                _useGemini.value = true
                repository.saveSetting("use_gemini", "true")
                updateAiState()
                backgroundTaskManager.completeTask("AI 연결 성공: $chosenModel")
                showToast("구글 AI 비서와 성공적으로 연결되었어요!", "success")
            } else {
                _diagnosticLogs.value += "\n🚨 연결 실패: Google AI Studio에서 발급받은 유효한 키인지 확인해 주세요."
                _aiState.value = "키 오류"
                backgroundTaskManager.failTask("구글 AI 연결 실패: API 키를 다시 확인해 주세요.")
                showToast("연결할 수 없어요. 아래 로그를 확인해 주세요.", "error")
            }
        }
    }

    fun runFullSystemCheck() {
        _diagnosticLogs.value = "🏥 [시스템 상태 확인]\n====================================\n"
        viewModelScope.launch {
            delay(400)
            val list = timelineData.value
            _diagnosticLogs.value += "대화 보관함: 총 ${list.size}일치 대화가 폰 안에 안전하게 보관되어 있어요.\n"
            _diagnosticLogs.value += "인터넷 연결: " + (if (_isOnline.value) "인터넷에 원활하게 연결되어 있어요." else "인터넷이 끊겨 있어요 (오프라인 모드).") + "\n"
            _diagnosticLogs.value += "AI 비서 상태: ${_aiState.value}\n"
            _diagnosticLogs.value += "====================================\n🏥 모든 시스템이 안전하게 준비되어 있습니다."
        }
    }

    private fun resolveRealModelName(model: String): String {
        val trimmed = model.trim()
        return when (trimmed) {
            "gemini-3.5-flash" -> "gemini-3.5-flash"
            "gemini-2.5-flash" -> "gemini-2.5-flash"
            "gemini-3.1-flash-lite-preview" -> "gemini-3.1-flash-lite-preview"
            "gemini-2.5-pro" -> "gemini-2.5-pro"
            "gemini-3.1-pro-preview" -> "gemini-3.1-pro-preview"
            else -> {
                if (trimmed.isEmpty()) "gemini-3.5-flash" else trimmed
            }
        }
    }

    private suspend fun requestGemini(prompt: String, apiKey: String, model: String): String? = withContext(Dispatchers.IO) {
        val realModel = resolveRealModelName(model)
        val request = GenerateContentRequest(
            contents = listOf(ApiContent(parts = listOf(ApiPart(text = prompt)))),
            generationConfig = GenerationConfig(temperature = 0.5f)
        )
        val maxRetries = 3
        var attempt = 0
        while (attempt < maxRetries) {
            try {
                AppLogger.d("TalkSummaryViewModel", "Requesting Gemini (attempt $attempt) with model $realModel and prompt length: ${prompt.length}")
                val response = GeminiApiClient.service.generateContent(
                    model = realModel,
                    apiKey = apiKey,
                    request = request
                )
                val resultText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                AppLogger.d("TalkSummaryViewModel", "Gemini response executed successfully.")
                _lastErrorInfo.value = null
                return@withContext resultText
            } catch (e: retrofit2.HttpException) {
                val code = e.code()
                val rawError = e.response()?.errorBody()?.string() ?: ""
                AppLogger.e("TalkSummaryViewModel", "Gemini HTTP error $code (attempt $attempt): $rawError")
                val isRetryable = code == 429 || code in 500..504
                if (isRetryable && attempt < maxRetries - 1) {
                    val delayMs = when (attempt) {
                        0 -> 2500L
                        1 -> 5000L
                        else -> 10000L
                    }
                    AppLogger.w("TalkSummaryViewModel", "Transient HTTP error $code, retrying in ${delayMs}ms (attempt $attempt)...")
                    delay(delayMs)
                    attempt++
                    continue
                }
                val errorInfo = com.example.data.api.GeminiErrorClassifier.classify(code, rawError)
                _lastErrorInfo.value = errorInfo
                return@withContext null
            } catch (e: java.io.IOException) {
                AppLogger.w("TalkSummaryViewModel", "Gemini network I/O error (attempt $attempt): ${e.message}")
                if (attempt < maxRetries - 1) {
                    val delayMs = when (attempt) {
                        0 -> 2500L
                        1 -> 5000L
                        else -> 10000L
                    }
                    delay(delayMs)
                    attempt++
                    continue
                }
                val errorInfo = com.example.data.api.GeminiErrorClassifier.classifyException(e)
                _lastErrorInfo.value = errorInfo
                return@withContext null
            } catch (e: Exception) {
                AppLogger.e("TalkSummaryViewModel", "Gemini request failed: ${e.message}", e)
                val errorInfo = com.example.data.api.GeminiErrorClassifier.classifyException(e)
                _lastErrorInfo.value = errorInfo
                return@withContext null
            }
        }
        null
    }

    private fun buildGeminiPrompt(messages: List<Message>): String {
        val targetMessages = filterMeaningfulMessages(messages)
        val serialized = targetMessages.joinToString("\n") { "[${it.sender}]: ${it.text}" }
        return "요청 지시사항: 다음 메신저 대화 내용을 바탕으로 누구나(예: 어린아이, 초등학생도) 이해하기 아주 쉽고 다정한 표현으로 가독성 있게 핵심 대화 내용을 요약해 주세요.\n" +
                "대괄호([])나 '배경:', '주제:', '핵심:', '결론:' 같은 머리말 분류 라벨은 절대 넣지 마세요.\n" +
                "발화자의 이름(예: OOO님이)과 구체적인 행동/상황을 포함하여 부드러운 서술형 문장(~했어요, ~부탁했어요, ~알려줬어요)으로 작성하세요.\n" +
                "단순 사진/이모티콘 전송이나 단답형 인사는 요약에서 완전히 제외하고 실제 대화 맥락만 요약하세요.\n" +
                "인사말이나 부차적인 설명, 사족은 완벽히 생략하고 정확하게 1., 2., 3. 세 줄 문장만 작성해 주시기 바랍니다:\n" +
                "1. 대화가 시작된 구체적 계기와 상황\n" +
                "2. 대화자 간에 오고 간 주요 논의와 부탁\n" +
                "3. 최종 마무리된 조치 및 향후 약속/계획\n\n" +
                serialized
    }

    private fun setupNetworkListener() {
        val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        // Initial state
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        _isOnline.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        // Register listener
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = true
            }

            override fun onLost(network: Network) {
                _isOnline.value = false
                showToast("인터넷 연결이 끊겼어요. Wi-Fi나 모바일 데이터를 확인해 주세요.", "error")
            }
        }
        networkCallback = callback
        try {
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            AppLogger.e("TalkSummaryViewModel", "Failed to register network callback: ${e.message}", e)
        }
    }
}

class TalkSummaryViewModelFactory(
    private val application: Application,
    private val repository: TalkSummaryRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TalkSummaryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TalkSummaryViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
