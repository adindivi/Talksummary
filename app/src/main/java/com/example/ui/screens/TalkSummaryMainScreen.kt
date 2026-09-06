package com.example.ui.screens

import com.example.ui.theme.MyApplicationTheme

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import com.example.ui.util.debouncedClickable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChatDay
import com.example.data.Message
import com.example.model.GgufMetadata
import com.example.service.TaskProgress
import com.example.service.TaskStatus
import com.example.ui.viewmodel.TalkSummaryViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

// Private helper to show DatePickerDialog
private fun showDatePickerDialog(
    context: android.content.Context,
    currentDateStr: String,
    onDateSelected: (String) -> Unit
) {
    val calendar = Calendar.getInstance()
    if (currentDateStr.isNotEmpty()) {
        try {
            val parts = currentDateStr.split("-")
            if (parts.size == 3) {
                val year = parts[0].toIntOrNull()
                val month = parts[1].toIntOrNull()
                val day = parts[2].toIntOrNull()
                if (year != null && month != null && day != null) {
                    calendar.set(year, month - 1, day)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    DatePickerDialog(
        context,
        { _, y, m, d ->
            val formatted = String.format("%04d-%02d-%02d", y, m + 1, d)
            onDateSelected(formatted)
        },
        year,
        month,
        day
    ).show()
}

// Brand Visual Tokens
val KakaoYellow = Color(0xFFFEE500)
val KakaoTextDark = Color(0xFF3C1E1E)
val KakaoChatBg = Color(0xFFBACDDE)
val KakaoHeaderBg = Color(0xFFA9BDCE)
val BrandSlate = Color(0xFF334155)
val BrandGreenAccent = Color(0xFF10B981)

@OptIn(ExperimentalMaterial3Api::class)
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
    val showErrorDetails by viewModel.showErrorDetails.collectAsStateWithLifecycle()
    val errorTitle by viewModel.errorTitle.collectAsStateWithLifecycle()
    val errorDescription by viewModel.errorDescription.collectAsStateWithLifecycle()

    // Background Tasks, Loading & Toasts
    val activeTask by viewModel.activeTask.collectAsStateWithLifecycle()
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
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    viewModel.parseAndImportBytes(bytes)
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
            if (selectedChatDay == null || isTabletOrFoldable) {
                var showClearConfirm by remember { mutableStateOf(false) }

                Surface(
                    color = Color.White,
                    shadowElevation = 1.5.dp,
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.setShowSettings(true) },
                                modifier = Modifier
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(10.dp))
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = "AI 비서 및 환경 설정",
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            IconButton(
                                onClick = { showClearConfirm = true },
                                modifier = Modifier
                                    .background(Color(0xFFFEF2F2), RoundedCornerShape(10.dp))
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "대화 기록 지우기",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(19.dp)
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
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
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
                        onPasteTextClick = { viewModel.setShowPasteModal(true) },
                        onOpenPrivacyModal = { showPrivacyModal = true }
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
                                onShowPrivacyModal = { showPrivacyModal = true }
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
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            onImportFileClick = { filePickerLauncher.launch("text/plain") },
                            onPasteTextClick = { viewModel.setShowPasteModal(true) },
                            onOpenPrivacyModal = { showPrivacyModal = true }
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
                            onShowPrivacyModal = { showPrivacyModal = true }
                        )
                    }
                }
            }
            
            // Diagnostics Overlay for Local LLM
            if (localInferenceStats != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xCC000000)),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = "Engine", tint = BrandGreenAccent, modifier = Modifier.size(16.dp))
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
                    .padding(bottom = 50.dp, start = 20.dp, end = 20.dp),
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
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, when (toastType) {
                        "error" -> Color(0xFFFCA5A5)
                        "success" -> Color(0xFF6EE7B7)
                        else -> Color(0xFFCBD5E1)
                    }),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
                            modifier = Modifier.size(18.dp)
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

        // Background Task Progress & Loading Overlay Dialog
        if (activeTask != null && activeTask?.status == TaskStatus.RUNNING) {
            val task = activeTask!!
            Dialog(
                onDismissRequest = { /* Prevent dismissing on outside touch */ },
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            ) {
                Card(
                    modifier = Modifier
                        .widthIn(min = 280.dp, max = 340.dp)
                        .fillMaxWidth(0.92f),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .background(Color(0xFFF8FAFC), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(34.dp),
                                strokeWidth = 3.5.dp,
                                color = BrandSlate,
                                trackColor = Color(0xFFE2E8F0)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = task.title.ifEmpty { "잠시만 기다려주세요" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = BrandSlate,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = task.detail.ifEmpty { "차근차근 정리하고 있어요..." },
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Center
                            )
                        }

                        // Progress Indicator
                        if (!task.isIndeterminate && task.total > 0) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { task.progressPercentage },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(3.5.dp)),
                                    color = BrandGreenAccent,
                                    trackColor = Color(0xFFE2E8F0)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${task.current} / ${task.total}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF64748B)
                                    )
                                    Text(
                                        text = "${task.progressPercentInt}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandGreenAccent
                                    )
                                }
                            }
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(2.5.dp)),
                                color = Color(0xFF3B82F6),
                                trackColor = Color(0xFFE2E8F0)
                            )
                        }

                        // Safe Background Notice Pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = "Safe",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "잠시 다른 앱을 보고 오셔도 작업이 계속돼요",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        // Cancel Button
                        if (task.isCancellable) {
                            OutlinedButton(
                                onClick = { viewModel.cancelActiveTask() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    modifier = Modifier.size(15.dp),
                                    tint = Color(0xFFEF4444)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "요약 멈추기",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                            }
                        }
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
    onPasteTextClick: () -> Unit = {},
    onOpenPrivacyModal: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var startText by remember(startDateFilter) { mutableStateOf(startDateFilter) }
    var endText by remember(endDateFilter) { mutableStateOf(endDateFilter) }

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Only show usage tip if database is empty - Reclaims massive space on mobile once files are uploaded!
        if (allChatDays.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Tip",
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "💡 초간단 이용 가이드",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF78350F)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1.0f)
                                .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "📂 1. 대화 파일 선택\n하단의 [카톡 .txt 열기] 버튼을 눌러 내보낸 카카오톡 대화 내용(.txt)을 불러옵니다.",
                                fontSize = 9.5.sp,
                                lineHeight = 14.sp,
                                color = Color(0xFF78350F)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1.0f)
                                .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "📱 2. 대화 시뮬레이터\n요약 타임라인 카드를 터치하면 실제 카카오톡 스타일 시뮬레이터로 대화를 확인합니다.",
                                fontSize = 9.5.sp,
                                lineHeight = 14.sp,
                                color = Color(0xFF78350F)
                            )
                        }
                    }
                }
            }
        }

        // Upload Buttons Zone
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onImportFileClick,
                colors = ButtonDefaults.buttonColors(containerColor = KakaoYellow),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = KakaoTextDark,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Button(
                onClick = onPasteTextClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentPaste,
                        contentDescription = "Paste Text",
                        tint = BrandSlate,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "대화 붙여넣기",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandSlate,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Dates Filters Block (Compact Single-Row Design)
        if (allChatDays.isNotEmpty()) {
            val isFilterActive = startText.isNotEmpty() || endText.isNotEmpty()
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isFilterActive) Color(0xFFBFDBFE) else Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
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
                            .border(0.8.dp, if (startText.isNotEmpty()) Color(0xFF93C5FD) else Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                            .clickable {
                                showDatePickerDialog(context, startText) { selected ->
                                    startText = selected
                                    viewModel.setDateFilters(selected, endText)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = startText.ifEmpty { "시작일" },
                                fontSize = 11.sp,
                                fontWeight = if (startText.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                                color = if (startText.isNotEmpty()) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "시작일 선택",
                                tint = if (startText.isNotEmpty()) Color(0xFF2563EB) else Color(0xFF94A3B8),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    Text(
                        text = "~",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                            .border(0.8.dp, if (endText.isNotEmpty()) Color(0xFF93C5FD) else Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                            .clickable {
                                showDatePickerDialog(context, endText) { selected ->
                                    endText = selected
                                    viewModel.setDateFilters(startText, selected)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = endText.ifEmpty { "종료일" },
                                fontSize = 11.sp,
                                fontWeight = if (endText.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                                color = if (endText.isNotEmpty()) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "종료일 선택",
                                tint = if (endText.isNotEmpty()) Color(0xFF2563EB) else Color(0xFF94A3B8),
                                modifier = Modifier.size(12.dp)
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
            }
        }

        // Timeline Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = "Timeline",
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "날짜별 대화 요약",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = BrandSlate
                )
            }
            val totalMessages = chatDays.sumOf { it.msgCount }
            Box(
                modifier = Modifier
                    .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                    .border(0.8.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "총 ${chatDays.size}일 (${totalMessages}건)",
                    fontSize = 10.sp,
                    color = BrandSlate,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Scrollable Lists of Days
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
                        "아직 불러온 대화가 없어요",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = BrandSlate
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "위의 [카톡 대화 파일 열기] 또는 [대화 내용 붙여넣기]를 눌러 대화를 시작해 보세요.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(chatDays, key = { it.date }) { chatDay ->
                    TimelineItemCard(
                        chatDay = chatDay,
                        onSelect = { viewModel.selectChatDay(chatDay) },
                        onAIPress = { viewModel.triggerSingleSummarize(chatDay) },
                        onCopySummary = { text ->
                            clipboardManager.setText(AnnotatedString(text))
                            viewModel.showToast("대화 요약이 클립보드에 복사되었습니다.", "success")
                        }
                    )
                }
            }
        }
    }
}

// TIMELINE CARD INDIVIDUAL COMPONENT
@Composable
fun TimelineItemCard(
    chatDay: ChatDay,
    onSelect: () -> Unit,
    onAIPress: () -> Unit,
    onCopySummary: (String) -> Unit = {}
) {
    val isAISummarized = chatDay.summary.startsWith("[AI 정밀 요약]")

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = chatDay.date,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "대화 요약",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                        .border(0.8.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "💬 ${chatDay.msgCount}건",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isAISummarized) Color(0xFFF5F3FF) else Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isAISummarized) Color(0xFFDDD6FE) else Color(0xFFE2E8F0),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isAISummarized) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = "AI 요약본",
                                    tint = Color(0xFF7C3AED),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "✨ 오늘의 3줄 요약",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF7C3AED)
                                )
                            }
                        } else {
                            Text(
                                text = "대화 둘러보기",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                        }

                        IconButton(
                            onClick = {
                                val cleanText = chatDay.summary.replace("[AI 정밀 요약]\n", "")
                                onCopySummary(cleanText)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "요약 복사",
                                tint = if (isAISummarized) Color(0xFF7C3AED) else Color.Gray,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = chatDay.summary.replace("[AI 정밀 요약]\n", ""),
                        fontSize = 12.sp,
                        color = if (isAISummarized) Color(0xFF1E1B4B) else Color(0xFF1E293B),
                        lineHeight = 19.sp,
                        letterSpacing = (-0.2).sp,
                        fontWeight = if (isAISummarized) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Scrollable horizontal Row of Keywords tags: GUARANTEES no squishing or overlap on narrow devices!
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(end = 8.dp)
                ) {
                    chatDay.keywords.take(5).forEach { keyword ->
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                .border(0.6.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "#$keyword",
                                fontSize = 9.5.sp,
                                color = Color(0xFF334155),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Button(
                    onClick = onAIPress,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAISummarized) Color(0xFFF1F5F9) else BrandSlate,
                        contentColor = if (isAISummarized) BrandSlate else Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = if (isAISummarized) BorderStroke(1.dp, Color(0xFFCBD5E1)) else null,
                    modifier = Modifier.height(30.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isAISummarized) Icons.Default.Refresh else Icons.Filled.Star,
                            contentDescription = "요약 실행",
                            tint = if (isAISummarized) BrandSlate else Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = if (isAISummarized) "다시 요약" else "AI 3줄 요약",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// CHAT ROOM SIMULATOR VIEW DETAILS
@Composable
fun ChatRoomScreen(
    chatDay: ChatDay,
    mainUser: String,
    isOnline: Boolean,
    onBackToList: () -> Unit,
    onToggleSender: () -> Unit,
    modifier: Modifier = Modifier,
    isMobile: Boolean = true,
    onShowToast: (String) -> Unit = {},
    onShowPrivacyModal: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()

    if (isMobile) {
        BackHandler(enabled = true) {
            onBackToList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KakaoChatBg)
    ) {
        // App header bar mimicking KakaoTalk Chatroom header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KakaoHeaderBg)
                .then(if (isMobile) Modifier.statusBarsPadding() else Modifier)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (isMobile) {
                    IconButton(onClick = onBackToList) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF374151))
                    }
                } else {
                    Spacer(modifier = Modifier.width(6.dp))
                }

                // Room Avatar Initials icon
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color.White, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "💬",
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = chatDay.participants.take(3).joinToString(", ") + " 외 대화방",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF111827),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${chatDay.date} • 카톡 대화방",
                        fontSize = 9.5.sp,
                        color = Color(0xFF475569),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Quick Operations Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onToggleSender,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Toggle Sender",
                        tint = Color(0xFF1F2937),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            if (chatDay.messages.isNotEmpty()) {
                                scrollState.animateScrollToItem(0)
                            }
                        }
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Scroll top",
                        tint = Color(0xFF1F2937),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            if (chatDay.messages.isNotEmpty()) {
                                scrollState.animateScrollToItem(chatDay.messages.size + 1)
                            }
                        }
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Scroll bottom",
                        tint = Color(0xFF1F2937),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Chat messages timeline lazylist
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 10.dp)
        ) {
            // Header date card element
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF9EABB8).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = chatDay.date,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Real messages bubbles
            items(chatDay.messages.size) { idx ->
                val msg = chatDay.messages[idx]
                val isMe = msg.sender == mainUser

                // Sequential messages formatting optimization
                val showHeader = !isMe && (idx == 0 || chatDay.messages[idx - 1].sender != msg.sender)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Top
                ) {
                    if (!isMe) {
                        if (showHeader) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(getRandomAvatarBg(msg.sender), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = msg.sender.take(1),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(34.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Column(
                        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                    ) {
                        if (showHeader) {
                            Text(
                                text = msg.sender,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4B5563),
                                modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                        ) {
                            if (isMe) {
                                Text(
                                    text = msg.time,
                                    fontSize = 9.sp,
                                    color = Color(0xFF64748B),
                                    modifier = Modifier.padding(end = 4.dp, bottom = 2.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isMe) KakaoYellow else Color.White,
                                        shape = RoundedCornerShape(
                                            topStart = if (!isMe && showHeader) 2.dp else 14.dp,
                                            topEnd = if (isMe) 2.dp else 14.dp,
                                            bottomStart = 14.dp,
                                            bottomEnd = 14.dp
                                        )
                                    )
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(msg.text))
                                        val snippet = if (msg.text.length > 15) msg.text.take(15) + "..." else msg.text
                                        onShowToast("메시지가 복사되었습니다: \"$snippet\"")
                                    }
                                    .padding(horizontal = 11.dp, vertical = 8.dp)
                                    .widthIn(max = if (isMobile) 280.dp else 480.dp)
                            ) {
                                Text(
                                    text = msg.text,
                                    fontSize = 12.5.sp,
                                    color = if (isMe) KakaoTextDark else Color.Black,
                                    lineHeight = 17.sp,
                                    letterSpacing = (-0.2).sp
                                )
                            }

                            if (!isMe) {
                                Text(
                                    text = msg.time,
                                    fontSize = 9.sp,
                                    color = Color(0xFF64748B),
                                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom status visual card bar
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.85f), CircleShape)
                        .border(0.5.dp, Color(0xFFCBD5E1), CircleShape)
                        .debouncedClickable { onShowPrivacyModal() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Safe",
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "대화 내용은 폰 안에만 안전하게 머물러요",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF334155),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(KakaoYellow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = KakaoTextDark,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

// Generate dynamic colors for background avatars matching sender hash
private fun getRandomAvatarBg(name: String): Color {
    val bgs = listOf(
        Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B),
        Color(0xFF8B5CF6), Color(0xFF6366F1), Color(0xFFEC4899), Color(0xFF14B8A6),
        Color(0xFF06B6D4), Color(0xFF84CC16)
    )
    val hash = kotlin.math.abs(name.hashCode())
    return bgs[hash % bgs.size]
}

// SETTINGS DIALOG WITH ADVANCED DIAGNOSTICS
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    apiKey: String,
    useGemini: Boolean,
    activeModel: String,
    useLocalModel: Boolean,
    localModelPath: String,
    loadedGgufMetadata: GgufMetadata? = null,
    diagnosticLogs: String,
    onClose: () -> Unit,
    onSave: (String, Boolean, String, Boolean, String) -> Unit,
    onRunDiagnostic: (String) -> Unit,
    onRunSystemCheck: () -> Unit,
    onPickLocalModel: () -> Unit,
    onShowToast: (String, String) -> Unit = { _, _ -> },
    onShowPrivacyModal: () -> Unit = {},
    isBatteryOptimizationIgnored: Boolean = true,
    onRequestBatteryExemption: () -> Unit = {}
) {
    var keyText by remember { mutableStateOf(apiKey) }
    var useChecked by remember { mutableStateOf(useGemini) }
    var selectedModel by remember { mutableStateOf(activeModel) }
    var useLocalChecked by remember { mutableStateOf(useLocalModel) }
    var localPathText by remember { mutableStateOf(localModelPath) }
    val scrollState = rememberScrollState()

    LaunchedEffect(localModelPath) {
        localPathText = localModelPath
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        MyApplicationTheme(darkTheme = false) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .widthIn(max = 520.dp)
                    .heightIn(max = 660.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header settings menu mimicking premium header UI
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BrandSlate)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = KakaoYellow,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "AI 비서 및 환경 설정",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                            .debouncedClickable { onShowPrivacyModal() }
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔒 API 키와 대화는 어떻게 보호되나요?",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = BrandSlate
                                )
                                Text(
                                    text = "안심 원칙 보기 >",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF4F46E5)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "입력하신 키와 대화 내용은 외부 서버로 전송되지 않고 스마트폰 기기 내부 저장소에만 안전하게 보관돼요. 탭하여 100% 안심 프라이버시 약속을 확인해 보세요.",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 15.sp
                            )
                        }
                    }

                    // Background Battery & Doze Optimization Policy Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isBatteryOptimizationIgnored) Color(0xFFECFDF5) else Color(0xFFFFFBEB),
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                1.dp,
                                if (isBatteryOptimizationIgnored) Color(0xFFA7F3D0) else Color(0xFFFDE68A),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isBatteryOptimizationIgnored) Icons.Filled.CheckCircle else Icons.Filled.Bolt,
                                        contentDescription = "Battery Status",
                                        tint = if (isBatteryOptimizationIgnored) Color(0xFF059669) else Color(0xFFD97706),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "백그라운드 절전 및 배터리 정책",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = BrandSlate
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isBatteryOptimizationIgnored) Color(0xFFD1FAE5) else Color(0xFFFEF3C7),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                                ) {
                                    Text(
                                        text = if (isBatteryOptimizationIgnored) "절전 예외 허용됨" else "절전 모드 켜짐",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBatteryOptimizationIgnored) Color(0xFF065F46) else Color(0xFF92400E)
                                    )
                                }
                            }

                            Text(
                                text = if (isBatteryOptimizationIgnored) {
                                    "화면이 꺼지거나 다른 앱을 사용하는 동안에도 긴 대화 요약이 안드로이드 Doze(절전) 모드에 방해받지 않고 안전하게 실행돼요."
                                } else {
                                    "화면이 꺼지면 안드로이드 Doze 절전 정책에 의해 AI 요약 속도가 느려지거나 일시 중단될 수 있어요. 안정적인 백그라운드 요약을 위해 절전 예외를 권장해요."
                                },
                                fontSize = 10.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 15.sp
                            )

                            if (!isBatteryOptimizationIgnored) {
                                Button(
                                    onClick = onRequestBatteryExemption,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandSlate),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("⚡", fontSize = 11.sp)
                                        Text(
                                            text = "배터리 절전 예외 설정하기",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // AI Engine Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("AI 비서 선택", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = BrandSlate)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { useLocalChecked = false; useChecked = true },
                                colors = ButtonDefaults.buttonColors(containerColor = if (!useLocalChecked && useChecked) KakaoYellow else Color(0xFFF1F5F9)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "구글 제미나이 (클라우드)",
                                    color = if (!useLocalChecked && useChecked) KakaoTextDark else Color.DarkGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Button(
                                onClick = { useLocalChecked = true; useChecked = false },
                                colors = ButtonDefaults.buttonColors(containerColor = if (useLocalChecked) BrandGreenAccent else Color(0xFFF1F5F9)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "내 폰 안의 AI (오프라인)",
                                    color = if (useLocalChecked) Color.White else Color.DarkGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    if (!useLocalChecked) {
                        // Gemini API Settings
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Google AI Studio API 키", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                val context = LocalContext.current
                                Text(
                                    "무료 키 발급받기 →",
                                    fontSize = 10.sp,
                                    color = Color(0xFF2563EB),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        try {
                                            val intent = android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse("https://aistudio.google.com/app/apikey")
                                            )
                                            context.startActivity(intent)
                                            onShowToast("Google AI Studio 키 발급 페이지로 이동합니다.", "info")
                                        } catch (e: Exception) {
                                            onShowToast("웹 브라우저를 열 수 없습니다: ${e.message}", "error")
                                        }
                                    }
                                )
                            }

                            OutlinedTextField(
                                value = keyText,
                                onValueChange = { keyText = it },
                                placeholder = { Text("AIzaSy... 로 시작하는 API 키를 입력해 주세요", fontSize = 12.sp, color = Color.Gray) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.Black,
                                    unfocusedTextColor = Color.Black,
                                    disabledTextColor = Color.Black,
                                    errorTextColor = Color.Black,
                                    focusedBorderColor = KakaoYellow,
                                    unfocusedBorderColor = Color.Gray,
                                    focusedLabelColor = Color.Black,
                                    unfocusedLabelColor = Color.Gray,
                                    focusedContainerColor = Color(0xFFF1F5F9), // Slate gray container to raise high depth and contrast
                                    unfocusedContainerColor = Color(0xFFF1F5F9)
                                ),
                                textStyle = TextStyle(fontSize = 12.sp, color = Color.Black),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Model Selector
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("제미나이 AI 모델 선택", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = BrandSlate)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val models = listOf(
                                    "gemini-2.5-flash" to "2.5 Flash (추천)",
                                    "gemini-2.5-pro" to "2.5 Pro",
                                    "gemini-3.5-flash" to "3.5 Flash",
                                    "gemini-3.1-pro-preview" to "3.1 Pro",
                                    "gemini-3.1-flash-lite-preview" to "Lite"
                                )
                                models.forEach { (modelId, displayName) ->
                                    val isSelected = selectedModel == modelId
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (isSelected) KakaoYellow else Color(0xFFF1F5F9),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) KakaoTextDark else Color(0xFFE2E8F0),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { selectedModel = modelId }
                                            .padding(horizontal = 12.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = displayName,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) KakaoTextDark else Color.DarkGray
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Local LLM Settings
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("내 폰 안의 오프라인 AI 모델 (llama.cpp)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = BrandSlate)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                                    .clickable { onPickLocalModel() }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = if (localPathText.isEmpty()) "📂 AI 모델 파일(.gguf) 선택하기" else "✅ AI 모델 준비 완료",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (localPathText.isEmpty()) Color.DarkGray else BrandGreenAccent
                                    )
                                    Text(
                                        text = if (localPathText.isEmpty()) "기기에 저장된 .gguf AI 모델 파일을 선택해 주세요." else localPathText.substringAfterLast(File.separatorChar),
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (loadedGgufMetadata != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Surface(
                                            color = Color(0xFFE0F2FE),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "${loadedGgufMetadata.modelName} • ${loadedGgufMetadata.quantizationType.label} • ${loadedGgufMetadata.architecture.uppercase()} • ctx:${loadedGgufMetadata.contextLength}",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0369A1),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    // Diagnostic Test Blocks
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "🛠️ 시스템 점검 및 연결 진단",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = BrandSlate
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onRunDiagnostic(keyText) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEF2F6)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Test Connection", tint = Color(0xFF4F46E5), modifier = Modifier.size(14.dp))
                                    Text("AI 통신 점검", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5))
                                }
                            }

                            Button(
                                onClick = onRunSystemCheck,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "System check", tint = BrandSlate, modifier = Modifier.size(14.dp))
                                    Text("시스템 전체 점검", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BrandSlate)
                                }
                            }
                        }

                        // Code-like Terminal Logs Output Widget
                        if (diagnosticLogs.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = diagnosticLogs,
                                    fontSize = 9.sp,
                                    color = Color(0xFF38BDF8),
                                    lineHeight = 13.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Bottom CTA buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onClose) {
                        Text("취소", color = Color.Gray, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = BrandSlate),
                        shape = RoundedCornerShape(12.dp),
                        onClick = { onSave(keyText, useChecked, selectedModel, useLocalChecked, localPathText) }
                    ) {
                        Text("설정 저장하기", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
}

// PASTE TEXT AREA DIALOG
@Composable
fun PasteTextDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onShowToast: (String, String) -> Unit = { _, _ -> }
) {
    var rawText by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        MyApplicationTheme(darkTheme = false) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .widthIn(max = 520.dp)
                    .heightIn(max = 580.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BrandSlate)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = "Paste Icon",
                                tint = KakaoYellow,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "대화 내용 직접 붙여넣기",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }

                    // Content Body
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "복사한 대화 내용을 여기에 붙여넣어 주세요.",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                            Button(
                                onClick = {
                                    val clipText = clipboardManager.getText()?.text
                                    if (!clipText.isNullOrBlank()) {
                                        rawText = clipText
                                        onShowToast("클립보드에서 대화를 가져왔어요 (${clipText.length}자).", "success")
                                    } else {
                                        onShowToast("클립보드에 복사된 대화 내용이 없어요.", "info")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = BrandSlate,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "클립보드 내용 붙여넣기",
                                    fontSize = 11.sp,
                                    color = BrandSlate,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Text Field Area
                        OutlinedTextField(
                            value = rawText,
                            onValueChange = { rawText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            placeholder = {
                                Text(
                                    "예시:\n2025년 5월 10일 토요일\n[홍길동] [오전 10:15] 안녕하세요!\n[김철수] [오전 10:16] 반갑습니다.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8),
                                    lineHeight = 16.sp
                                )
                            },
                            textStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandSlate,
                                unfocusedBorderColor = Color(0xFFE2E8F0)
                            )
                        )

                        // Char counter and clear action
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${rawText.length}자 입력됨",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                            if (rawText.isNotEmpty()) {
                                TextButton(
                                    onClick = { rawText = "" },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("지우기", fontSize = 11.sp, color = Color(0xFFEF4444))
                                }
                            }
                        }
                    }

                    // Bottom Action Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC))
                            .border(BorderStroke(1.dp, Color(0xFFF1F5F9)))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("취소", color = Color.Gray, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KakaoYellow,
                                disabledContainerColor = Color(0xFFE2E8F0)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            enabled = rawText.isNotBlank(),
                            onClick = { onConfirm(rawText) }
                        ) {
                            Text(
                                "대화 불러오기",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (rawText.isNotBlank()) KakaoTextDark else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// USER-FRIENDLY WARM REASSURANCE DIALOGS (Apple & Kakao Style)
// -------------------------------------------------------------------------

/**
 * 1. Warm Reassurance Delete Confirmation Dialog
 * Replaces harsh warning dialogs with a soothing, reassuring message.
 */
@Composable
fun WarmDeleteConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 400.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Friendly Soft Rose Icon Header
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFFFFF1F2), CircleShape)
                        .border(1.dp, Color(0xFFFFE4E6), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = "비우기",
                        tint = Color(0xFFE11D48),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "대화 기록을 모두 비울까요?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandSlate,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "기기에 저장된 대화 원문과 요약본만 폰에서 깔끔하게 정리돼요.",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Reassuring Info Box
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("💬", fontSize = 13.sp)
                            Text(
                                text = "카카오톡에서 '대화 내보내기'로 언제든 다시 불러와 새롭게 요약할 수 있어요.",
                                fontSize = 12.sp,
                                color = Color(0xFF334155),
                                lineHeight = 17.sp
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🔒", fontSize = 13.sp)
                            Text(
                                text = "등록하신 API 키와 맞춤 환경 설정은 안전하게 그대로 유지돼요.",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandSlate)
                    ) {
                        Text(
                            text = "그대로 둘게요",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }

                    OutlinedButton(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFFECDD3)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFFFFF1F2),
                            contentColor = Color(0xFFE11D48)
                        )
                    ) {
                        Text(
                            text = "깨끗이 비우기",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 2. Warm Guidance Trouble-Resolution Dialog
 * Replaces generic error dialogs with actionable steps and peace-of-mind assurance.
 */
@Composable
fun WarmErrorGuidanceDialog(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 410.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Friendly Soft Amber Badge
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(Color(0xFFFEF3C7), CircleShape)
                        .border(1.dp, Color(0xFFFDE68A), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.TipsAndUpdates,
                        contentDescription = "도움말",
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = title.ifEmpty { "잠시 확인해 주세요" },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandSlate,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = Color(0xFF334155),
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Reassuring Shield Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFECFDF5), RoundedCornerShape(12.dp))
                        .border(0.5.dp, Color(0xFFA7F3D0), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Safe",
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "대화 원문과 기존 저장 기록은 안전하니 걱정하지 마세요.",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF065F46)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actionable Solution Guidance Cards
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quick Action: Open Settings
                    Surface(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("⚙️", fontSize = 14.sp)
                                Column {
                                    Text(
                                        text = "AI 비서 및 API 키 설정 열기",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp,
                                        color = BrandSlate
                                    )
                                    Text(
                                        text = "Google AI Gemini 키 발급 및 모델 변경",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "이동",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // 카톡 대화 내보내기 팁
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "💡 카카오톡 대화 가져오기 팁",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BrandSlate
                            )
                            Text(
                                text = "카톡 채팅방 > 우측 상단 메뉴(≡) > 설정(⚙️) > [대화 내용 내보내기] 후 저장된 텍스트 파일(.txt)을 선택하거나 복사해서 붙여넣기 해보세요.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandSlate)
                ) {
                    Text(
                        text = "확인했어요",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * 3. 100% Privacy & Security Peace-of-Mind Dialog
 * Explains device-only sandbox, privacy shielding, and instant clean deletion rights.
 */
@Composable
fun PrivacyPeaceOfMindDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 410.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Green Shield Header
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(Color(0xFFECFDF5), CircleShape)
                        .border(1.dp, Color(0xFFA7F3D0), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "안심 보안",
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "100% 안심 프라이버시 약속",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandSlate,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "소중한 메신저 대화, 오직 회원님의 폰 안에서만 안전하게 지켜져요.",
                    fontSize = 12.5.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 3 Core Commitments
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PrivacyCommitmentCard(
                        emoji = "📱",
                        title = "내 폰 안에서만 안전하게 보관",
                        desc = "대화 원문과 데이터베이스는 외부 개발자 서버로 일체 전송되지 않으며, 스마트폰 기기 내부 샌드박스에만 안전하게 보관돼요."
                    )

                    PrivacyCommitmentCard(
                        emoji = "🛡️",
                        title = "안전한 AI 분석 & 개인정보 보호",
                        desc = "AI 요약 시에도 구글 공식 보안 채널(HTTPS)을 통해 암호화 전송되며, 저장되지 않는 일회성 통신으로 처리돼요."
                    )

                    PrivacyCommitmentCard(
                        emoji = "🧹",
                        title = "원클릭 흔적 없는 완전 삭제",
                        desc = "원하실 때 언제든 상단 휴지통 버튼으로 저장된 모든 대화와 요약 기록을 기기에서 말끔하게 지울 수 있어요."
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandSlate)
                ) {
                    Text(
                        text = "안심하고 이용할게요",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyCommitmentCard(
    emoji: String,
    title: String,
    desc: String
) {
    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.White, CircleShape)
                    .border(0.5.dp, Color(0xFFCBD5E1), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 14.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = BrandSlate
                )
                Text(
                    text = desc,
                    fontSize = 11.sp,
                    color = Color(0xFF475569),
                    lineHeight = 15.sp
                )
            }
        }
    }
}

/**
 * 4. Warm Notification Permission Rationale Dialog
 * Explains gentle background notification purpose without aggressive popups.
 */
@Composable
fun WarmNotificationRationaleDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 390.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Soft Blue Notification Bell
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFFEFF6FF), CircleShape)
                        .border(1.dp, Color(0xFFDBEAFE), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = "알림",
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "요약이 끝나면 살짝 알려드릴게요",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandSlate,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "대화 요약이 진행되는 동안 다른 앱을 편하게 보고 계셔도 괜찮아요.",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Reassuring Info Box
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🔔", fontSize = 13.sp)
                            Text(
                                text = "요약이 완료되는 즉시 상단 알림으로 조용히 알려드려요.",
                                fontSize = 12.sp,
                                color = Color(0xFF334155),
                                lineHeight = 16.sp
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🌿", fontSize = 13.sp)
                            Text(
                                text = "스팸이나 불필요한 홍보성 알림은 일체 보내지 않아요.",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandSlate)
                    ) {
                        Text(
                            text = "알림 켜기",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "나중에 할게요",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
