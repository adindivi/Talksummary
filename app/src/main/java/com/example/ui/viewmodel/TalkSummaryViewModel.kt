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
import com.example.data.TalkSummaryRepository
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GeminiApiClient
import com.example.data.api.Content as ApiContent
import com.example.data.api.Part as ApiPart
import com.example.data.api.GenerationConfig
import com.example.data.db.AppDatabase
import com.example.data.parser.KakaoTalkParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.data.llm.ModelLoader
import com.example.model.GgufMetadata
import com.example.service.InferenceForegroundService
import com.example.engine.StreamTokenEvent

class TalkSummaryViewModel(
    application: Application,
    private val repository: TalkSummaryRepository
) : AndroidViewModel(application) {

    private val modelLoader = ModelLoader(application)

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

    // Modals
    private val _showSettings = MutableStateFlow(false)
    val showSettings = _showSettings.asStateFlow()

    private val _showPasteModal = MutableStateFlow(false)
    val showPasteModal = _showPasteModal.asStateFlow()

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

    // Loading overlay
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _loadingTitle = MutableStateFlow("")
    val loadingTitle = _loadingTitle.asStateFlow()

    private val _loadingMessage = MutableStateFlow("")
    val loadingMessage = _loadingMessage.asStateFlow()

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
        loadAllSettings()
        setupNetworkListener()
        viewModelScope.launch {
            timelineData.collect { list ->
                _parserState.value = if (list.isNotEmpty()) "정상작동" else "빈 보관소"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        modelLoader.close()
    }

    private fun loadAllSettings() {
        viewModelScope.launch {
            val key = repository.getSetting("gemini_api_key") ?: ""
            _geminiApiKey.value = key

            val use = repository.getSetting("use_gemini") ?: "false"
            _useGemini.value = use == "true"

            val useLocal = repository.getSetting("use_local_model") ?: "false"
            _useLocalModel.value = useLocal == "true"

            val localPath = repository.getSetting("local_model_path") ?: ""
            _localModelPath.value = localPath

            if (localPath.isNotEmpty()) {
                val file = java.io.File(localPath)
                if (file.exists()) {
                    val parsed = com.example.engine.GgufParser.parse(file)
                    if (parsed.isSuccess) {
                        _loadedGgufMetadata.value = parsed.getOrNull()
                    }
                }
            }

            val model = repository.getSetting("configured_model") ?: "gemini-3.5-flash"
            _activeModel.value = model

            val user = repository.getSetting("main_user_name") ?: ""
            _mainUser.value = user

            updateAiState()
        }
    }

    private fun updateAiState() {
        _aiState.value = if (_useLocalModel.value && _localModelPath.value.isNotEmpty()) {
            "로컬모델 구동"
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
        // Whenever a new chat day is selected, set standard mainUser if empty
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
                start.isNotEmpty() && end.isNotEmpty() -> "${start} ~ ${end} 기간 필터 적용됨"
                start.isNotEmpty() -> "${start} 이후 대화 필터 적용됨"
                else -> "${end} 이전 대화 필터 적용됨"
            }
            showToast(filterDesc, "info")
        }
    }

    fun clearFilters() {
        _startDateFilter.value = ""
        _endDateFilter.value = ""
        showToast("필터가 초기화되었습니다.", "success")
    }

    fun setMainUser(name: String) {
        _mainUser.value = name
        viewModelScope.launch {
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
        showToast("정렬 기준이 변경되었습니다: ${nextUser}가 내 대화(노란색)로 설정됨.", "success")
    }

    fun showToast(message: String, type: String = "info") {
        _toastMessage.value = message
        _toastType.value = type
        viewModelScope.launch(Dispatchers.Main) {
            try {
                android.widget.Toast.makeText(getApplication(), message, android.widget.Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
            delay(2800)
            if (_toastMessage.value == message) {
                _toastMessage.value = null
            }
        }
    }

    fun saveSettings(key: String, useGemini: Boolean, model: String, useLocal: Boolean = false, localPath: String = "") {
        viewModelScope.launch {
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
            showToast("설정이 저장되었습니다.", "success")
            _showSettings.value = false
        }
    }

    fun parseAndImportText(rawText: String) {
        _showPasteModal.value = false
        showLoading("대화 대용량 텍스트 해석", "클라이언트 측 가상 샌드박스에서 대화를 분석 중...")
        viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.Default) {
                    KakaoTalkParser.parse(rawText)
                }
                if (parsed.isEmpty()) {
                    hideLoading()
                    showError("가져오기 실패", "카카오톡의 날짜 또는 대화 줄 형식을 하나도 찾을 수 없습니다.")
                    return@launch
                }
                // Overwrite old parsed documents automatically when importing new files/texts
                repository.clearAll()
                repository.saveChatDays(parsed)
                hideLoading()
                showToast("대화가 동기화 및 덮어쓰기 완료되었습니다!", "success")
                // Reset selections
                selectChatDay(null)
            } catch (e: Exception) {
                hideLoading()
                showError("가져오기 실패", "대화 해석 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    fun parseAndImportBytes(bytes: ByteArray) {
        _showPasteModal.value = false
        showLoading("대화 대용량 텍스트 해석", "클라이언트 측 가상 샌드박스에서 대화를 인코딩 자동 분석하며 해석 중...")
        viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.Default) {
                    KakaoTalkParser.parseFromBytes(bytes)
                }
                if (parsed.isEmpty()) {
                    hideLoading()
                    showError("가져오기 실패", "카카오톡의 대화 줄 형식을 하나도 찾을 수 없습니다.\n올바른 형태의 카카오톡 대화 내용 텍스트 파일 (.txt)을 다시 한 번 확인해 주세요.")
                    return@launch
                }
                // Overwrite old parsed documents automatically when importing new files/texts
                repository.clearAll()
                repository.saveChatDays(parsed)
                hideLoading()
                showToast("대화가 동기화 및 덮어쓰기 완료되었습니다!", "success")
                // Reset selections
                selectChatDay(null)
            } catch (e: Exception) {
                hideLoading()
                showError("가져오기 실패", "대화 해석 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    fun clearAllData() {
        showLoading("데이터 삭제", "폰 내부의 대화 및 요약 데이터를 정리하고 있습니다...")
        viewModelScope.launch {
            repository.clearAll()
            // Do NOT call repository.clearAllSettings() or delete _geminiApiKey, _useGemini, _mainUser
            selectChatDay(null)
            updateAiState()
            hideLoading()
            showToast("보관된 대화 업로드 자료가 성공적으로 초기화되었습니다.", "success")
        }
    }

    fun copyAndSetLocalModel(context: Context, uri: android.net.Uri) {
        showLoading("GGUF 모델 복사 중", "선택한 GGUF 모델 파일을 앱 샌드박스로 안전하게 복사하고 있습니다. 대용량 파일의 경우 수 분이 소요될 수 있습니다.")
        viewModelScope.launch(Dispatchers.IO) {
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
                    _loadingMessage.value = "모델 복사 진행률: $percent% (잠시만 기다려주세요)"
                }

                if (importResult.isFailure) {
                    throw importResult.exceptionOrNull() ?: Exception("GGUF 파일 임포트 실패")
                }

                val targetFile = importResult.getOrThrow()
                _loadingMessage.value = "GGUF 헤더 구조 및 양자화 파싱 중..."

                val loadResult = modelLoader.loadModel(targetFile.absolutePath)
                if (loadResult.isFailure) {
                    throw loadResult.exceptionOrNull() ?: Exception("GGUF 바이너리 파싱 실패")
                }

                val meta = loadResult.getOrThrow()

                withContext(Dispatchers.Main) {
                    _localModelPath.value = targetFile.absolutePath
                    _loadedGgufMetadata.value = meta
                    repository.saveSetting("local_model_path", targetFile.absolutePath)
                    updateAiState()
                    hideLoading()
                    showToast("GGUF 모델 탑재 성공: ${meta.modelName} (${meta.quantizationType.label})", "success")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoading()
                    showError("GGUF 모델 로드 실패", "GGUF 모델을 처리하는 중 오류가 발생했습니다: ${e.message}")
                }
            }
        }
    }

    private fun buildLocalGgufPrompt(messages: List<Message>): String {
        val serialized = messages.joinToString("\n") { "[${it.sender}]: ${it.text}" }
        return "다음 카카오톡 대화 내용을 한국어로 3줄로 간결하고 핵심만 요약해 주세요. 각 줄은 반드시 1., 2., 3. 번호로 시작해야 합니다.\n\n[대화 내용]\n$serialized\n\n[3줄 요약]"
    }

    fun triggerSingleSummarize(chatDay: ChatDay) {
        val useLocal = _useLocalModel.value
        val localPath = _localModelPath.value
        val key = _geminiApiKey.value

        if (!useLocal && key.isEmpty()) {
            showToast("설정에서 구글 AI API 키를 먼저 등록하거나 로컬 모델을 구동해 주세요.", "error")
            return
        }

        if (useLocal && localPath.isEmpty()) {
            showToast("로컬 모델 모드가 켜져있으나 모델 파일이 설정되지 않았습니다.", "error")
            return
        }

        val title = if (useLocal) "로컬 GGUF AI 요약" else "AI 정밀 대화 압축"
        val message = if (useLocal) "온디바이스 llama.cpp C++ 엔진으로 요약 중입니다..." else "구글 제미나이 언어 인스턴스로 요약 전송 중..."

        showLoading(title, message)

        viewModelScope.launch {
            try {
                val responseText = if (useLocal) {
                    InferenceForegroundService.start(getApplication(), "온디바이스 GGUF 대화 요약 중...")
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
                        when (event) {
                            is StreamTokenEvent.Token -> {
                                lastTps = event.tokensPerSecond
                                tokensCount = event.tokensGenerated
                                _localInferenceStats.value = "속도: ${String.format(java.util.Locale.US, "%.1f", lastTps)} tok/s | 생성: ${tokensCount}토큰"
                                InferenceForegroundService.updateProgress(getApplication(), "GGUF 토큰 생성 중...", lastTps)
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
                    _localInferenceStats.value = "완료: ${String.format(java.util.Locale.US, "%.1f", lastTps)} tok/s | ${tokensCount}토큰 (${String.format(java.util.Locale.US, "%.1f", durationSec)}s) | 메모리: ${usedMemMb}MB"
                    
                    viewModelScope.launch {
                        delay(10000)
                        _localInferenceStats.value = null
                    }

                    generatedText
                } else {
                    val prompt = buildGeminiPrompt(chatDay.messages)
                    requestGemini(prompt, key, _activeModel.value)
                }

                if (responseText != null && responseText.isNotEmpty()) {
                    val formatted = "[AI 정밀 요약]\n$responseText"
                    repository.updateSummary(chatDay.date, formatted)
                    
                    // Update active selected day if it matches
                    if (_selectedChatDay.value?.date == chatDay.date) {
                        _selectedChatDay.value = _selectedChatDay.value?.copy(summary = formatted)
                    }
                    showToast("성공적으로 인공지능 요약 갱신이 완료되었습니다.", "success")
                } else {
                    showError("요약 실패", "AI 모델로부터 부적합한 응답이 도착했습니다. 연결 혹은 모델 설정을 확인해 보시기 바랍니다.")
                }
            } catch (e: Exception) {
                showError("요약 실패", "AI 모델 처리 중 오류가 발생했습니다: ${e.message}")
            } finally {
                if (useLocal) {
                    InferenceForegroundService.stop(getApplication())
                }
                hideLoading()
            }
        }
    }

    fun triggerBulkSummarize() {
        val key = _geminiApiKey.value
        if (key.isEmpty()) {
            showToast("AI 설정에서 API Key를 먼저 입력 및 활성화해주셔야 일괄 요약이 진행됩니다.", "error")
            return
        }

        val list = timelineData.value
        val unsummarized = list.filter { !it.summary.startsWith("[AI 정밀 요약]") }
        if (unsummarized.isEmpty()) {
            showToast("이미 모든 대화 기록이 Gemini AI에 의해 정밀 요약되었습니다.", "success")
            return
        }

        showLoading("일괄 AI 요약 진행", "총 ${unsummarized.size}개의 일자를 요약 중입니다. 잠시만 기다려주세요.")
        viewModelScope.launch {
            var count = 0
            for (item in unsummarized) {
                count++
                _loadingMessage.value = "(${count}/${unsummarized.size}) ${item.date} 요약 중..."
                try {
                    val prompt = buildGeminiPrompt(item.messages)
                    val result = requestGemini(prompt, key, _activeModel.value)
                    if (result != null) {
                        val formatted = "[AI 정밀 요약]\n$result"
                        repository.updateSummary(item.date, formatted)
                        if (_selectedChatDay.value?.date == item.date) {
                            _selectedChatDay.value = _selectedChatDay.value?.copy(summary = formatted)
                        }
                    }
                    delay(1200) // Rate limiting pacing delay
                } catch (e: Exception) {
                    // Log and continue on bulk
                }
            }
            hideLoading()
            showToast("전체 날짜 일괄 요약 분석이 완료되었습니다!", "success")
        }
    }

    fun runSmartDiagnosticConnection(apiKeyArg: String) {
        val trimmedKey = apiKeyArg.trim()
        if (trimmedKey.isEmpty()) {
            showToast("테스트를 실행하려면 설정에 API Key를 적어주세요.", "error")
            return
        }

        _diagnosticLogs.value = "[연결 검사 시작] 구글 AI 서버로 테스트 통신을 보냅니다...\n\n"
        showLoading("AI 상태 점검", "연결중...")

        viewModelScope.launch {
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
                _diagnosticLogs.value += "🛰️ [테스트] $model 모델 연결중... "
                try {
                    val response = requestGemini(
                        prompt = "Hello, respond with exactly 'OK' to confirm health check.",
                        apiKey = trimmedKey,
                        model = model
                    )
                    if (response != null && response.isNotEmpty()) {
                        _diagnosticLogs.value += "➔ ✅ 연결 완료!\n"
                        success = true
                        chosenModel = model
                        break;
                    } else {
                        _diagnosticLogs.value += "➔ ❌ 빈 응답\n"
                    }
                } catch (e: Exception) {
                    _diagnosticLogs.value += "➔ 💥 에러 [${e.localizedMessage}]\n"
                }
                delay(500)
            }

            hideLoading()
            if (success) {
                _diagnosticLogs.value += "\n🎉 연결 성공 완료!\n이 기기 환경에 제일 적합한 [$chosenModel] 모델을 기본 AI 엔진으로 자동 지정 완료했습니다."
                _activeModel.value = chosenModel
                repository.saveSetting("configured_model", chosenModel)
                _useGemini.value = true
                repository.saveSetting("use_gemini", "true")
                updateAiState()
                showToast("인공지능 비서 연동 및 정상화 성공!", "success")
            } else {
                _diagnosticLogs.value += "\n🚨 연결 실패: 구글 AI Studio에서 정식으로 발급한 유효한 키인지 확인 바랍니다."
                _aiState.value = "키 오류"
                showToast("연결 실패! 로그 확인 바랍니다.", "error")
            }
        }
    }

    fun runFullSystemCheck() {
        _diagnosticLogs.value = "🏥 [앱 전체 정밀 점검 시작]\n====================================\n"
        viewModelScope.launch {
            delay(400)
            val list = timelineData.value
            _diagnosticLogs.value += "대화 저장 데이터: 총 ${list.size}일 분량의 대화방이 안전하게 내 폰 내부에 저장되어 있습니다.\n"
            _diagnosticLogs.value += "인터넷 망 점검: " + (if (_isOnline.value) "정상 연결 상태" else "오프라인 상태 (네트워크 통신 불통)") + "\n"
            _diagnosticLogs.value += "AI 연동 상태: ${_aiState.value}\n"
            _diagnosticLogs.value += "====================================\n🏥 시스템 전체 정기 점진 점검 완료! 이상 무."
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
        try {
            android.util.Log.d("TalkSummaryViewModel", "Requesting Gemini with model $realModel (original request: $model) and prompt length: ${prompt.length}")
            val response = GeminiApiClient.service.generateContent(
                model = realModel,
                apiKey = apiKey,
                request = request
            )
            val resultText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            android.util.Log.d("TalkSummaryViewModel", "Gemini response model path successfully executed. Result: $resultText")
            resultText
        } catch (e: retrofit2.HttpException) {
            val rawError = e.response()?.errorBody()?.string() ?: ""
            android.util.Log.e("TalkSummaryViewModel", "Gemini HTTP error ${e.code()}: $rawError")
            null
        } catch (e: Exception) {
            android.util.Log.e("TalkSummaryViewModel", "Gemini request failed for model $realModel (original: $model): ${e.message}", e)
            null
        }
    }

    private fun buildGeminiPrompt(messages: List<Message>): String {
        val serialized = messages.joinToString("\n") { "[${it.sender}]: ${it.text}" }
        return "요청 지시사항: 다음 메신저 대화 내용을 바탕으로 누구나(예: 어린아이, 초등학생도) 이해하기 아주 쉽고 친근한 표현으로 가독성 있게 핵심 대화 내용을 요약해 주세요.\n" +
                "인사말이나 부차적인 설명, 사족은 완벽히 생략하고 정확하게 '줄바꿈(\\n)' 표시가 들어간 딱 3줄로만 구성해 주시기 바랍니다.\n" +
                "반드시 아래의 번호 형식(1., 2., 3.)을 지켜 전체 총 3줄로 작성하셔야 합니다:\n" +
                "1. [매우 이해하기 쉬운 첫 번째 내용 요약]\n" +
                "2. [매우 이해하기 쉬운 두 번째 내용 요약]\n" +
                "3. [매우 이해하기 쉬운 세 번째 내용 요약]\n\n" +
                serialized
    }

    private fun showLoading(title: String, message: String) {
        _loadingTitle.value = title
        _loadingMessage.value = message
        _isLoading.value = true
    }

    private fun hideLoading() {
        _isLoading.value = false
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

        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = true
            }

            override fun onLost(network: Network) {
                _isOnline.value = false
                showToast("인터넷 연결이 해제되었습니다.", "error")
            }
        })
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
