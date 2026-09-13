package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.data.parser.ChatAnalyticsEngine
import com.example.data.parser.StoryGenerator
import com.example.model.ChatSession
import com.example.model.GgufMetadata
import com.example.model.TalkStoryResult
import com.example.model.WebtoonStoryResult
import com.example.service.TaskProgress
import com.example.service.TaskStatus
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.util.*
import com.example.ui.viewmodel.TalkSummaryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

@Composable
fun TalkSummaryMainScreen(
    viewModel: TalkSummaryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Collect States
    val chatDays by viewModel.filteredTimelineData.collectAsStateWithLifecycle()
    val allChatDays by viewModel.timelineData.collectAsStateWithLifecycle()
    val selectedChatDay by viewModel.selectedChatDay.collectAsStateWithLifecycle()
    val startDateFilter by viewModel.startDateFilter.collectAsStateWithLifecycle()
    val endDateFilter by viewModel.endDateFilter.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val dbState by viewModel.dbState.collectAsStateWithLifecycle()
    val parserState by viewModel.parserState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val mainUser by viewModel.mainUser.collectAsStateWithLifecycle()
    val activeModel by viewModel.activeModel.collectAsStateWithLifecycle()
    val geminiApiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
    val useGemini by viewModel.useGemini.collectAsStateWithLifecycle()

    // Modal visibilities
    val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()
    val showPasteModal by viewModel.showPasteModal.collectAsStateWithLifecycle()
    val showArchiveModal by viewModel.showArchiveModal.collectAsStateWithLifecycle()
    val chatArchives by viewModel.chatArchives.collectAsStateWithLifecycle()
    val activeArchiveId by viewModel.activeArchiveId.collectAsStateWithLifecycle()
    val showErrorDetails by viewModel.showErrorDetails.collectAsStateWithLifecycle()
    val errorTitle by viewModel.errorTitle.collectAsStateWithLifecycle()
    val errorDescription by viewModel.errorDescription.collectAsStateWithLifecycle()

    // Background Tasks, Loading & Toasts
    val activeTask by viewModel.activeTask.collectAsStateWithLifecycle()
    val activeSummarizingDate by viewModel.activeSummarizingDate.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val loadingTitle by viewModel.loadingTitle.collectAsStateWithLifecycle()
    val loadingMessage by viewModel.loadingMessage.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val toastType by viewModel.toastType.collectAsStateWithLifecycle()
    val isBatteryOptimizationIgnored by viewModel.isBatteryOptimizationIgnored.collectAsStateWithLifecycle()

    // File launcher for selected logs imports (.txt)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val fileName = getFileNameFromUri(context, uri) ?: "카카오톡_대화.txt"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    viewModel.parseAndImportBytes(bytes, fileName)
                } else {
                    viewModel.showToast("파일 내용이 비어있거나 읽을 수 없습니다.", "error")
                }
            } catch (e: Exception) {
                viewModel.showToast("파일 가져오기 오류: ${e.message}", "error")
            }
        }
    }

    // Local Model picker launcher
    val localModelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.copyAndSetLocalModel(context, uri)
        }
    }

    val useLocalModel by viewModel.useLocalModel.collectAsStateWithLifecycle()
    val localModelPath by viewModel.localModelPath.collectAsStateWithLifecycle()
    val localInferenceStats by viewModel.localInferenceStats.collectAsStateWithLifecycle()
    val loadedGgufMetadata by viewModel.loadedGgufMetadata.collectAsStateWithLifecycle()

    // Runtime Permission (Android 13+ Notification for AI Background Service)
    var showPermissionRationale by remember { mutableStateOf(false) }
    var showPrivacyModal by remember { mutableStateOf(false) }
    var activeStoryDate by rememberSaveable { mutableStateOf<String?>(null) }
    val activeStoryChatDay = remember(activeStoryDate, allChatDays, selectedChatDay) {
        if (activeStoryDate == null) null
        else allChatDays.find { it.date == activeStoryDate } ?: (if (selectedChatDay?.date == activeStoryDate) selectedChatDay else null)
    }
    var showAnalysisReportModal by rememberSaveable { mutableStateOf(false) }
    var analysisInitialYearMonth by rememberSaveable { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.showToast("알림 권한이 허용되었습니다.", "success")
        } else {
            viewModel.showToast("알림 권한이 비활성화되었습니다.", "info")
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                showPermissionRationale = true
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isTabletOrFoldable = configuration.screenWidthDp >= 720

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (selectedChatDay == null || isTabletOrFoldable) {
                    var showClearConfirm by remember { mutableStateOf(false) }

                    Surface(
                        color = Color.White,
                        shadowElevation = 0.dp,
                        border = BorderStroke(0.8.dp, AppleSurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(KakaoYellow, RoundedCornerShape(10.dp))
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = "Logo",
                                        tint = KakaoTextDark,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "TalkSummary",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "복잡한 대화도 딱 3줄로 깔끔하게",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B),
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.setShowArchiveModal(true) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = "대화방 보관함",
                                        tint = Color(0xFF1E293B),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.setShowSettings(true) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Settings,
                                        contentDescription = "AI 비서 및 환경 설정",
                                        tint = Color(0xFF1E293B),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { showClearConfirm = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "대화 기록 지우기",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        if (showClearConfirm) {
                            WarmDeleteConfirmDialog(
                                onDismiss = { showClearConfirm = false },
                                onConfirm = {
                                    showClearConfirm = false
                                    viewModel.clearAllData()
                                }
                            )
                        }
                    }
                }

                // Non-blocking Background Task Progress Bar (Slides in below Header for global tasks, suppressed when summarizing within a card)
                AnimatedVisibility(
                    visible = activeTask != null && activeTask?.status == TaskStatus.RUNNING && activeSummarizingDate == null,
                    enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
                ) {
                    if (activeTask != null) {
                        NonBlockingTaskProgressBar(
                            task = activeTask!!,
                            onCancel = { viewModel.cancelActiveTask() }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(AppleFogCanvas)
        ) {
            val isDualPane = (maxWidth >= 720.dp && maxHeight >= 480.dp) || maxWidth >= 960.dp
            val timelineWeight = if (maxWidth > 1000.dp) 0.38f else 0.45f
            val chatWeight = 1f - timelineWeight

            if (isDualPane) {
                // Side-by-Side Dual Column View for Tablets, Foldables & Large Screens
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TimelineColumn(
                        viewModel = viewModel,
                        chatDays = chatDays,
                        allChatDays = allChatDays,
                        startDateFilter = startDateFilter,
                        endDateFilter = endDateFilter,
                        isOnline = isOnline,
                        dbState = dbState,
                        parserState = parserState,
                        aiState = aiState,
                        modifier = Modifier.weight(timelineWeight),
                        onImportFileClick = { filePickerLauncher.launch("text/plain") },
                        onOpenAnalysisClick = { targetYm ->
                            analysisInitialYearMonth = targetYm
                            showAnalysisReportModal = true
                        },
                        onPasteTextClick = { viewModel.setShowPasteModal(true) },
                        onOpenPrivacyModal = { showPrivacyModal = true },
                        onOpenStory = { cd -> activeStoryDate = cd.date }
                    )

                    Box(
                        modifier = Modifier
                            .weight(chatWeight)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
                    ) {
                        if (selectedChatDay != null) {
                            ChatRoomScreen(
                                chatDay = selectedChatDay!!,
                                mainUser = mainUser,
                                isOnline = isOnline,
                                onBackToList = { viewModel.selectChatDay(null) },
                                onToggleSender = { viewModel.toggleSenderMode() },
                                modifier = Modifier.fillMaxSize(),
                                isMobile = false,
                                onShowToast = { msg -> viewModel.showToast(msg, "info") },
                                onShowPrivacyModal = { showPrivacyModal = true },
                                onTriggerSummarize = { cd -> viewModel.triggerSingleSummarize(cd) },
                                isSummarizing = activeSummarizingDate == selectedChatDay?.date,
                                onOpenStory = { cd -> activeStoryDate = cd.date }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(KakaoChatBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.85f)),
                                    modifier = Modifier.widthIn(max = 320.dp),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .background(Color.White, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Forum,
                                                contentDescription = "Empty",
                                                tint = BrandSlate,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = "대화를 선택해 주세요",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = BrandSlate,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "왼쪽 목록에서 날짜를 선택하면 그날 나눈 카카오톡 대화를 자세히 볼 수 있어요.",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Adaptive Single Pane View for Mobile Phones
                Box(modifier = Modifier.fillMaxSize()) {
                    if (selectedChatDay == null) {
                        TimelineColumn(
                            viewModel = viewModel,
                            chatDays = chatDays,
                            allChatDays = allChatDays,
                            startDateFilter = startDateFilter,
                            endDateFilter = endDateFilter,
                            isOnline = isOnline,
                            dbState = dbState,
                            parserState = parserState,
                            aiState = aiState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
                            onImportFileClick = { filePickerLauncher.launch("text/plain") },
                            onOpenAnalysisClick = { targetYm ->
                                analysisInitialYearMonth = targetYm
                                showAnalysisReportModal = true
                            },
                            onPasteTextClick = { viewModel.setShowPasteModal(true) },
                            onOpenPrivacyModal = { showPrivacyModal = true },
                            onOpenStory = { cd -> activeStoryDate = cd.date }
                        )
                    } else {
                        ChatRoomScreen(
                            chatDay = selectedChatDay!!,
                            mainUser = mainUser,
                            isOnline = isOnline,
                            onBackToList = { viewModel.selectChatDay(null) },
                            onToggleSender = { viewModel.toggleSenderMode() },
                            modifier = Modifier.fillMaxSize(),
                            isMobile = true,
                            onShowToast = { msg -> viewModel.showToast(msg, "info") },
                            onShowPrivacyModal = { showPrivacyModal = true },
                            onTriggerSummarize = { cd -> viewModel.triggerSingleSummarize(cd) },
                            isSummarizing = activeSummarizingDate == selectedChatDay?.date,
                            onOpenStory = { cd -> activeStoryDate = cd.date }
                        )
                    }
                }
            }
            
            // Diagnostics Overlay for Local LLM (Top Floating Capsule, never overlaps with bottom toasts)
            if (localInferenceStats != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 10.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Card(
                        shape = RoundedCornerShape(999.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xEE1E293B)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = "Engine", tint = BrandGreenAccent, modifier = Modifier.size(15.dp))
                            Text(
                                text = localInferenceStats ?: "",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // --- Custom Dialogs & Modals ---

        // Settings Modal
        if (showSettings) {
            SettingsDialog(
                apiKey = geminiApiKey,
                useGemini = useGemini,
                activeModel = activeModel,
                useLocalModel = useLocalModel,
                localModelPath = localModelPath,
                loadedGgufMetadata = loadedGgufMetadata,
                diagnosticLogs = viewModel.diagnosticLogs.collectAsStateWithLifecycle().value,
                onClose = { viewModel.setShowSettings(false) },
                onSave = { key, use, model, useLocal, localPath -> viewModel.saveSettings(key, use, model, useLocal, localPath) },
                onRunDiagnostic = { key -> viewModel.runSmartDiagnosticConnection(key) },
                onRunSystemCheck = { viewModel.runFullSystemCheck() },
                onPickLocalModel = { localModelPickerLauncher.launch("*/*") },
                onShowToast = { msg, type -> viewModel.showToast(msg, type) },
                onShowPrivacyModal = { showPrivacyModal = true },
                isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
                onRequestBatteryExemption = { viewModel.requestIgnoreBatteryOptimization(context) }
            )
        }

        // Permission Rationale Modal (Warm Apple & Kakao Style)
        if (showPermissionRationale) {
            WarmNotificationRationaleDialog(
                onDismiss = { showPermissionRationale = false },
                onConfirm = {
                    showPermissionRationale = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        }

        // Paste Text Dialog
        if (showPasteModal) {
            PasteTextDialog(
                onDismiss = { viewModel.setShowPasteModal(false) },
                onConfirm = { text -> viewModel.parseAndImportText(text) },
                onShowToast = { msg, type -> viewModel.showToast(msg, type) }
            )
        }

        // Alert Detail Modal (Warm Error Guidance Dialog)
        if (showErrorDetails) {
            WarmErrorGuidanceDialog(
                title = errorTitle,
                description = errorDescription,
                onDismiss = { viewModel.setShowErrorDetails(false) },
                onOpenSettings = {
                    viewModel.setShowErrorDetails(false)
                    viewModel.setShowSettings(true)
                }
            )
        }

        // 100% Privacy & Security Peace-of-Mind Modal
        if (showPrivacyModal) {
            PrivacyPeaceOfMindDialog(
                onDismiss = { showPrivacyModal = false }
            )
        }

        // Multi-Chatroom Storage & 1-Touch Switching Modal
        if (showArchiveModal) {
            ChatArchiveListDialog(
                archives = chatArchives,
                activeArchiveId = activeArchiveId,
                onDismiss = { viewModel.setShowArchiveModal(false) },
                onSelectArchive = { archive ->
                    viewModel.loadArchive(archive)
                },
                onToggleFavorite = { archive ->
                    viewModel.toggleArchiveFavorite(archive)
                },
                onDeleteArchive = { archive ->
                    viewModel.deleteArchive(archive)
                },
                onImportNewFile = {
                    viewModel.setShowArchiveModal(false)
                    filePickerLauncher.launch("text/*")
                }
            )
        }

        // 3-Card Instagram-style Cinematic Story Dialog & 3-Cut AI Webtoon
        activeStoryChatDay?.let { chatDay ->
            val storyResult = remember(chatDay) { StoryGenerator.generate3CardStory(chatDay) }
            TalkStoryCarouselDialog(
                storyResult = storyResult,
                chatDay = chatDay,
                geminiApiKey = geminiApiKey,
                activeModel = activeModel,
                onDismiss = { activeStoryDate = null },
                onShareStory = { story ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "🎨 고화질 스토리 카드를 생성하고 있어요...", Toast.LENGTH_SHORT).show()
                            }
                            val imageFile = ShareImageGenerator.generateStoryCardsImage(context, story)
                            val captionText = "🎴 [${story.chatRoomName}] 3장 스토리 요약\n#카카오톡대화요약 #스토리카드"
                            withContext(Dispatchers.Main) {
                                shareImageDirectlyToKakaoTalk(context, imageFile, captionText, "3장 스토리 이미지 카톡 공유")
                            }
                        } catch (_: Exception) {
                            withContext(Dispatchers.Main) {
                                val shareText = "🎴 [${story.chatRoomName}] 3장 스토리 요약\n\n" +
                                    "1장: ${story.card1.title}\n${story.card1.story}\n\n" +
                                    "2장: ${story.card2.title}\n${story.card2.story}\n\n" +
                                    "3장: ${story.card3.title}\n${story.card3.story}\n\n" +
                                    "#카카오톡대화요약 #스토리카드"
                                shareDirectlyToKakaoTalk(context, shareText, "3장 스토리 카톡 공유")
                            }
                        }
                    }
                },
                onShareWebtoon = { webtoon ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "🎨 3컷 웹툰 고화질 이미지를 생성하고 있어요...", Toast.LENGTH_SHORT).show()
                            }
                            val imageFile = ShareImageGenerator.generateWebtoonStripImage(context, webtoon)
                            val aiTag = if (webtoon.isAiGenerated) "제미나이 AI 각색 ✨" else "스마트 만화 요약"
                            val captionText = "🎨 [${webtoon.chatRoomName}] 3컷 웹툰 요약툰! ($aiTag)\n#카카오톡대화요약 #3컷웹툰 #인스타툰"
                            withContext(Dispatchers.Main) {
                                shareImageDirectlyToKakaoTalk(context, imageFile, captionText, "3컷 웹툰 이미지 카톡 공유")
                            }
                        } catch (_: Exception) {
                            withContext(Dispatchers.Main) {
                                val cutsText = webtoon.cuts.joinToString("\n\n") { cut ->
                                    "${cut.stage}: \"${cut.speechBubble}\" (${cut.speaker} ${cut.emotionEmoji})\n" +
                                    "💥 효과음: ${cut.soundEffect}\n" +
                                    "📖 ${cut.situation}"
                                }
                                val aiTag = if (webtoon.isAiGenerated) "제미나이 AI 각색 ✨" else "스마트 만화 요약"
                                val shareText = "🎨 [${webtoon.chatRoomName}] 3컷 웹툰 요약툰! ($aiTag)\n\n" +
                                    "$cutsText\n\n" +
                                    "#카카오톡대화요약 #3컷웹툰 #인스타툰"
                                shareDirectlyToKakaoTalk(context, shareText, "3컷 웹툰 카톡 공유")
                            }
                        }
                    }
                }
            )
        }

        // Toss-Style Monthly KakaoTalk Deep Analysis Report Dialog
        if (showAnalysisReportModal) {
            TalkAnalysisReportDialog(
                chatDays = allChatDays,
                initialYearMonth = analysisInitialYearMonth,
                onDismiss = {
                    showAnalysisReportModal = false
                    analysisInitialYearMonth = null
                },
                onShareReport = { report ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "📊 심층 분석 리포트 이미지를 생성하고 있어요...", Toast.LENGTH_SHORT).show()
                            }
                            val imageFile = ShareImageGenerator.generateAnalysisReportImage(context, report)
                            val captionText = "📊 [카톡 대화 분석 리포트 - ${report.displayMonth}]\n#카카오톡대화분석 #토크서머리"
                            withContext(Dispatchers.Main) {
                                shareImageDirectlyToKakaoTalk(context, imageFile, captionText, "카톡 대화 분석 리포트 이미지 공유")
                            }
                        } catch (_: Exception) {
                            withContext(Dispatchers.Main) {
                                val podiumText = if (report.participantShares.isNotEmpty()) {
                                    report.participantShares.take(3).joinToString("\n") { share ->
                                        val icon = when (share.rank) {
                                            1 -> "🥇"
                                            2 -> "🥈"
                                            3 -> "🥉"
                                            else -> "⚡"
                                        }
                                        "$icon ${share.name} (${share.count}건, ${share.percentage}%) - ${share.badge}"
                                    }
                                } else "참여자 데이터 없음"

                                val peakText = report.peakDay?.let {
                                    "🔥 가장 뜨거웠던 날: ${it.displayDate} (${it.messageCount}건, ${it.percentageOfTotal}%)\n"
                                } ?: ""

                        val pingText = report.firstPingStats?.leaders?.firstOrNull()?.let { leader ->
                            "⚡ 선톡 장인: ${leader.name} (${leader.pingCount}회, ${leader.pingPercentage}%)\n"
                        } ?: ""

                        val quirksText = report.quirksReport?.let { q ->
                            "😂 웃음 타입: ${q.dominantLaughType} (총 ${q.totalLaughCount}회)\n"
                        } ?: ""

                        val heatmapText = report.heatmapData?.let { h ->
                            "🟩 대화 잔디: ${h.totalDaysInMonth}일 중 ${h.activeDaysCount}일 대화 (${h.activeDayPercentage}% 출석)\n"
                        } ?: ""

                        val shareText = "📊 [카톡 대화 분석 리포트 - ${report.displayMonth}]\n\n" +
                            "총 ${report.daysCount}일간 ${report.totalMessages}건의 대화 분석 결과\n\n" +
                            "🏆 이 달의 발언 랭킹:\n$podiumText\n\n" +
                            peakText + "\n" +
                            pingText +
                            quirksText +
                            heatmapText + "\n" +
                            "⏰ 대화 골든타임:\n${report.timeSlotStats.personaTitle}\n${report.timeSlotStats.personaDescription}\n\n" +
                            "💫 우리들의 케미:\n${report.chemistryTitle}\n${report.chemistryDescription}\n\n" +
                            "#카카오톡대화분석 #토크서머리"

                                shareDirectlyToKakaoTalk(context, shareText, "카톡 대화 분석 리포트 공유")
                            }
                        }
                    }
                }
            )
        }

        // Toast Popup (Animated)
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 36.dp, start = 20.dp, end = 20.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (toastType) {
                            "error" -> Color(0xFFFEF2F2)
                            "success" -> Color(0xFFECFDF5)
                            else -> Color(0xFFF8FAFC)
                        }
                    ),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, when (toastType) {
                        "error" -> Color(0xFFFCA5A5)
                        "success" -> Color(0xFF6EE7B7)
                        else -> Color(0xFFCBD5E1)
                    }),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = when (toastType) {
                                "error" -> Icons.Default.Close
                                "success" -> Icons.Default.Check
                                else -> Icons.Default.Info
                            },
                            contentDescription = "Toast Icon",
                            tint = when (toastType) {
                                "error" -> Color(0xFFEF4444)
                                "success" -> Color(0xFF10B981)
                                else -> Color(0xFF3B82F6)
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = toastMessage ?: "",
                            fontSize = 12.sp,
                            color = when (toastType) {
                                "error" -> Color(0xFF991B1B)
                                "success" -> Color(0xFF065F46)
                                else -> BrandSlate
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

    }
}


// TIMELINE VIEW COLUMN COMPONENT
@Composable
fun TimelineColumn(
    viewModel: TalkSummaryViewModel,
    chatDays: List<ChatDay>,
    allChatDays: List<ChatDay>,
    startDateFilter: String,
    endDateFilter: String,
    isOnline: Boolean,
    dbState: String,
    parserState: String,
    aiState: String,
    modifier: Modifier = Modifier,
    onImportFileClick: () -> Unit,
    onOpenAnalysisClick: (String?) -> Unit = {},
    onPasteTextClick: () -> Unit = {},
    onOpenPrivacyModal: () -> Unit = {},
    onOpenStory: (ChatDay) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var startText by remember(startDateFilter) { mutableStateOf(startDateFilter) }
    var endText by remember(endDateFilter) { mutableStateOf(endDateFilter) }
    var isCalendarExpanded by remember { mutableStateOf(false) }

    val activeSummarizingDate by viewModel.activeSummarizingDate.collectAsStateWithLifecycle()
    val activeTask by viewModel.activeTask.collectAsStateWithLifecycle()
    val timelineGroupingMode by viewModel.timelineGroupingMode.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Only show usage tip if database is empty - Reclaims massive space on mobile once files are uploaded!
        if (allChatDays.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    // Header: Harmonious Badge + Subtitle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFEF9C3), RoundedCornerShape(999.dp))
                                .border(0.8.dp, Color(0xFFFEF08A), RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "가이드",
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "초간단 이용 가이드",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF854D0E)
                                )
                            }
                        }
                        Text(
                            text = "3단계로 끝내기 ✨",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "카카오톡 대화, 딱 3단계로 끝내요",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandSlate
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3-Step Practical Flow (Kakao Yellow Badges: Mathematically Centered Digits & Row Alignment)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Step 1: 카톡 대화 내보내기
                        GuideStepCard(
                            stepNum = "1",
                            title = "카카오톡 대화 내보내기",
                            description = "채팅방 설정(≡) > [대화 내용 내보내기]에서 텍스트로 저장해요."
                        )

                        // Step 2: 대화 파일 불러오기
                        GuideStepCard(
                            stepNum = "2",
                            title = "대화 파일 불러오기",
                            description = "아래 [대화 파일 열기] 버튼을 눌러 저장한 파일을 선택해요."
                        )

                        // Step 3: AI 요약 & 웹툰 공유
                        GuideStepCard(
                            stepNum = "3",
                            title = "AI 3줄 요약 & 웹툰 공유",
                            description = "하루 대화 요약과 3컷 만화를 이미지로 카톡에 바로 공유해요."
                        )
                    }
                }
            }
        }

        // Upload & Analysis Buttons Zone (Apple Signature 44dp Pill Buttons: Filled KakaoYellow + Toss Indigo)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onImportFileClick,
                colors = ButtonDefaults.buttonColors(containerColor = KakaoYellow),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, Color(0xFFE2D800).copy(alpha = 0.7f)),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Upload,
                        contentDescription = "Upload File",
                        tint = KakaoTextDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "대화 파일 열기",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = KakaoTextDark,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Button(
                onClick = {
                    if (allChatDays.isEmpty()) {
                        viewModel.showToast("먼저 대화 파일을 불러와 주세요", "info")
                    } else {
                        onOpenAnalysisClick(null)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, Color(0xFF1D4ED8)),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.BarChart,
                        contentDescription = "분석 리포트",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "나의 카톡 분석하기",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        if (allChatDays.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "보안 안심",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "대화는 서버 전송 없이 내 폰에서만 안전하게 분석돼요",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Dates Filters Block (Flat Surface on Fog Canvas, 0dp Elevation, Apple Inline Accordion)
        if (allChatDays.isNotEmpty()) {
            val isFilterActive = startText.isNotEmpty() || endText.isNotEmpty()
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (isFilterActive) Color(0xFFBFDBFE) else AppleSurfaceBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    // Slim Filter Bar Header (Clickable to toggle accordion)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isCalendarExpanded = !isCalendarExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FilterList,
                                contentDescription = "기간 필터",
                                tint = if (isFilterActive) Color(0xFF2563EB) else Color(0xFF64748B),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "기간",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFilterActive) Color(0xFF1D4ED8) else BrandSlate
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                .border(0.8.dp, if (isFilterActive) Color(0xFF93C5FD) else Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val labelText = if (isFilterActive) {
                                    val s = startText.ifEmpty { "시작" }
                                    val e = endText.ifEmpty { "종료" }
                                    "$s ~ $e"
                                } else {
                                    "전체 대화 (날짜 선택하기)"
                                }
                                Text(
                                    text = labelText,
                                    fontSize = 11.sp,
                                    fontWeight = if (isFilterActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isFilterActive) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                    maxLines = 1
                                )
                                Icon(
                                    imageVector = if (isCalendarExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "달력 열기/접기",
                                    tint = if (isFilterActive) Color(0xFF2563EB) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        if (isFilterActive) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp))
                                    .border(0.8.dp, Color(0xFFFECACA), RoundedCornerShape(8.dp))
                                    .clickable {
                                        startText = ""
                                        endText = ""
                                        viewModel.clearFilters()
                                    }
                                    .padding(horizontal = 7.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "초기화",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    // Inline Expandable Date Range Picker (Accordion, No-Modal)
                    AnimatedVisibility(
                        visible = isCalendarExpanded,
                        enter = expandVertically(
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                            expandFrom = Alignment.Top
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 180)
                        ),
                        exit = shrinkVertically(
                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                            shrinkTowards = Alignment.Top
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 140)
                        )
                    ) {
                        InlineDateRangePicker(
                            chatDays = allChatDays,
                            selectedStartDate = startText,
                            selectedEndDate = endText,
                            onRangeSelected = { start, end ->
                                startText = start
                                endText = end
                                if (start.isNotEmpty() || end.isNotEmpty()) {
                                    viewModel.setDateFilters(start, end)
                                }
                            },
                            onClear = {
                                startText = ""
                                endText = ""
                                viewModel.clearFilters()
                            },
                            onClose = {
                                isCalendarExpanded = false
                            }
                        )
                    }
                }
            }
        }

        if (allChatDays.isNotEmpty()) {
            // Galaxy Gallery One UI Style: 3-Tier Segmented Switcher [년도별 | 월별 | 일별] + Count Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GalaxySegmentedSwitcher(
                    selectedMode = timelineGroupingMode,
                    onModeSelect = { viewModel.setTimelineGroupingMode(it) }
                )

                val totalMessages = chatDays.sumOf { it.msgCount }
                val countLabel = when (timelineGroupingMode) {
                    TimelineGroupingMode.DAY -> "총 ${chatDays.size}일 (${totalMessages}건)"
                    TimelineGroupingMode.MONTH -> {
                        val monthsCount = chatDays.map { it.date.take(7) }.distinct().size
                        "총 ${monthsCount}개월 (${totalMessages}건)"
                    }
                    TimelineGroupingMode.YEAR -> {
                        val yearsCount = chatDays.map { it.date.take(4) }.distinct().size
                        "총 ${yearsCount}개년 (${totalMessages}건)"
                    }
                }

                Box(
                    modifier = Modifier
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(999.dp))
                        .border(0.8.dp, Color(0xFFE2E8F0), RoundedCornerShape(999.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = countLabel,
                        fontSize = 10.5.sp,
                        color = BrandSlate,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Scrollable Lists according to selected grouping mode
            if (chatDays.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "Empty list",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "선택한 기간에 일치하는 대화가 없어요",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = BrandSlate
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "상단 기간 필터를 재설정하거나 초기화 버튼(🔄)을 눌러보세요.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
            Crossfade(
                targetState = timelineGroupingMode,
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                label = "TimelineGroupingCrossfade",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { currentMode ->
                when (currentMode) {
                    TimelineGroupingMode.DAY -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(chatDays, key = { _, it -> it.date }) { index, chatDay ->
                                val isSummarizing = (activeSummarizingDate == chatDay.date && activeTask?.status == TaskStatus.RUNNING)
                                StaggeredListItem(
                                    index = index,
                                    key = "${currentMode}_${chatDay.date}"
                                ) {
                                    TimelineItemCard(
                                        chatDay = chatDay,
                                        isSummarizing = isSummarizing,
                                        activeTask = if (isSummarizing) activeTask else null,
                                        onCancelTask = { viewModel.cancelActiveTask() },
                                        onSelect = { viewModel.selectChatDay(chatDay) },
                                        onAIPress = { viewModel.triggerSingleSummarize(chatDay) },
                                        onCopySummary = { text ->
                                            clipboardManager.setText(AnnotatedString(text))
                                            viewModel.showToast("대화 요약이 클립보드에 복사되었습니다.", "success")
                                        },
                                        onOpenStory = { onOpenStory(chatDay) }
                                    )
                                }
                            }
                        }
                    }
                    TimelineGroupingMode.MONTH -> {
                        val monthGroups = remember(chatDays) { groupChatDaysByMonth(chatDays) }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(monthGroups, key = { _, it -> it.yearMonthKey }) { index, monthData ->
                                StaggeredListItem(
                                    index = index,
                                    key = "${currentMode}_${monthData.yearMonthKey}"
                                ) {
                                    MonthSummaryCard(
                                        monthData = monthData,
                                        onViewDays = {
                                            viewModel.filterByYearMonth(monthData.yearMonthKey)
                                        },
                                        onOpenAnalysis = { ym ->
                                            onOpenAnalysisClick(ym)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    TimelineGroupingMode.YEAR -> {
                        val yearGroups = remember(chatDays) { groupChatDaysByYear(chatDays) }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(yearGroups, key = { _, it -> it.yearKey }) { index, yearData ->
                                StaggeredListItem(
                                    index = index,
                                    key = "${currentMode}_${yearData.yearKey}"
                                ) {
                                    YearSummaryCard(
                                        yearData = yearData,
                                        onViewMonths = {
                                            viewModel.filterByYear(yearData.yearKey)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

