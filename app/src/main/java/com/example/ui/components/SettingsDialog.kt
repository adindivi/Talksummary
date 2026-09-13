package com.example.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import com.example.model.GgufMetadata
import com.example.ui.theme.*
import com.example.ui.util.debouncedClickable

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
                            text = "AI 및 대화 설정",
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
                    // 1. AI Engine Selector (Top Priority - No text truncation, clean 2-card selector)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "어떤 AI로 요약할까요?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = BrandSlate
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isGemini = !useLocalChecked && useChecked
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isGemini) Color(0xFFFFFBEB) else Color(0xFFF8FAFC),
                                border = BorderStroke(
                                    1.2.dp,
                                    if (isGemini) KakaoYellow else Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { useLocalChecked = false; useChecked = true }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "⚡ 구글 제미나이",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isGemini) KakaoTextDark else Color(0xFF334155)
                                    )
                                    Text(
                                        text = "초고속 · 높은 정확도",
                                        fontSize = 9.5.sp,
                                        color = if (isGemini) Color(0xFF78350F) else Color(0xFF94A3B8)
                                    )
                                }
                            }

                            val isLocal = useLocalChecked
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isLocal) Color(0xFFECFDF5) else Color(0xFFF8FAFC),
                                border = BorderStroke(
                                    1.2.dp,
                                    if (isLocal) BrandGreenAccent else Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { useLocalChecked = true; useChecked = false }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "📱 내 폰 안의 AI",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLocal) Color(0xFF065F46) else Color(0xFF334155)
                                    )
                                    Text(
                                        text = "완전 무료 · 데이터 불필요",
                                        fontSize = 9.5.sp,
                                        color = if (isLocal) Color(0xFF047857) else Color(0xFF94A3B8)
                                    )
                                }
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
                                Text("Google API 키", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                val context = LocalContext.current
                                Text(
                                    "무료로 발급받기 →",
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
                                            onShowToast("Google AI 키 발급 페이지로 이동해요.", "info")
                                        } catch (e: Exception) {
                                            onShowToast("웹 브라우저를 열 수 없어요: ${e.message}", "error")
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
                                    Triple("gemini-2.5-flash", "2.5 Flash", true),
                                    Triple("gemini-2.5-pro", "2.5 Pro", false),
                                    Triple("gemini-3.5-flash", "3.5 Flash", false),
                                    Triple("gemini-3.1-pro-preview", "3.1 Pro", false),
                                    Triple("gemini-3.1-flash-lite-preview", "Lite", false)
                                )
                                models.forEach { (modelId, displayName, isRecommended) ->
                                    val isSelected = selectedModel == modelId
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (isSelected) Color(0xFFFEF3C7) else Color(0xFFF1F5F9),
                                        border = BorderStroke(
                                            width = if (isSelected) 1.2.dp else 0.8.dp,
                                            color = if (isSelected) Color(0xFFF59E0B) else Color(0xFFE2E8F0)
                                        ),
                                        shadowElevation = if (isSelected) 0.5.dp else 0.dp,
                                        modifier = Modifier.clickable { selectedModel = modelId }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = displayName,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color(0xFF78350F) else Color(0xFF334155)
                                            )
                                            if (isRecommended) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFF4F46E5), RoundedCornerShape(999.dp))
                                                        .padding(horizontal = 5.dp, vertical = 1.2.dp)
                                                ) {
                                                    Text(
                                                        text = "추천",
                                                        fontSize = 8.5.sp,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Local LLM Settings
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val context = LocalContext.current
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("내 폰 안의 오프라인 AI", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = BrandSlate)
                                Text(
                                    text = "허깅페이스 모델 받기 →",
                                    fontSize = 10.sp,
                                    color = Color(0xFF2563EB),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        try {
                                            val intent = android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse("https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF")
                                            )
                                            context.startActivity(intent)
                                            onShowToast("Hugging Face 무료 AI 모델 페이지로 이동해요.", "info")
                                        } catch (e: Exception) {
                                            onShowToast("웹 브라우저를 열 수 없어요: ${e.message}", "error")
                                        }
                                    }
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                    .padding(11.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (localPathText.isEmpty()) {
                                        Text(
                                            text = "인터넷이나 데이터 소모 없이 내 스마트폰 안에서 100% 무료로 동작해요.",
                                            fontSize = 9.5.sp,
                                            color = Color(0xFF64748B),
                                            lineHeight = 14.sp
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = Color(0xFFECFDF5),
                                            border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(36.dp)
                                                .clickable { onPickLocalModel() }
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.FolderOpen,
                                                    contentDescription = "불러오기",
                                                    tint = BrandGreenAccent,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "내 폰의 AI 불러오기",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF065F46)
                                                )
                                            }
                                        }

                                        Text(
                                            text = "💡 아직 AI 모델(.gguf 파일)이 없다면 우측 상단의 '허깅페이스 모델 받기'에서 Qwen2.5 등의 무료 모델을 스마트폰에 다운로드한 후 불러와 주세요.",
                                            fontSize = 9.sp,
                                            color = Color(0xFF94A3B8),
                                            lineHeight = 13.5.sp
                                        )
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "✅ 내 폰의 AI 준비 완료",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = BrandGreenAccent
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = Color(0xFFF1F5F9),
                                                border = BorderStroke(0.8.dp, Color(0xFFCBD5E1)),
                                                modifier = Modifier
                                                    .height(28.dp)
                                                    .clickable { onPickLocalModel() }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = "변경하기",
                                                        tint = BrandSlate,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = "변경하기",
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = BrandSlate
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = localPathText.substringAfterLast(File.separatorChar),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF334155),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        // Toss Style: 3 User Benefit Micro-Badges
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Badge 1: 데이터 0원
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = Color(0xFFECFDF5),
                                                border = BorderStroke(0.7.dp, Color(0xFFA7F3D0))
                                            ) {
                                                Text(
                                                    text = "📶 데이터 0원",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF065F46),
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                                )
                                            }

                                            // Badge 2: 완전 오프라인
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = Color(0xFFEFF6FF),
                                                border = BorderStroke(0.7.dp, Color(0xFFBFDBFE))
                                            ) {
                                                Text(
                                                    text = "🔒 완전 오프라인",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1E40AF),
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                                )
                                            }

                                            // Badge 3: 초경량 모델 사이즈 라벨
                                            val modelSizeLabel = remember(localPathText, loadedGgufMetadata) {
                                                val name = (loadedGgufMetadata?.modelName ?: localPathText).lowercase()
                                                when {
                                                    name.contains("0.5b") -> "⚡ 0.5B 초경량"
                                                    name.contains("1.5b") -> "⚡ 1.5B 초경량"
                                                    name.contains("3b") -> "⚡ 3B 고성능"
                                                    name.contains("7b") -> "⚡ 7B 고성능"
                                                    else -> "⚡ 온디바이스 AI"
                                                }
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = Color(0xFFF8FAFC),
                                                border = BorderStroke(0.7.dp, Color(0xFFCBD5E1))
                                            ) {
                                                Text(
                                                    text = modelSizeLabel,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF334155),
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Security & Privacy Notice Card (Placed contextually right below AI settings)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                            .debouncedClickable { onShowPrivacyModal() }
                            .padding(11.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔒 대화와 키는 안전하게 보호돼요",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = BrandSlate,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "자세히 >",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF4F46E5),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "대화와 API 키는 외부 서버로 전송되지 않고 오직 내 스마트폰 안에만 안전하게 암호화되어 보관돼요.",
                                fontSize = 9.5.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 14.5.sp
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    // Diagnostic Test Blocks
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "🛠️ 연결 상태 및 시스템 점검",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = BrandSlate
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xFFEEF2FF),
                                border = BorderStroke(0.8.dp, Color(0xFFC7D2FE)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clickable { onRunDiagnostic(keyText) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = "Test Connection",
                                        tint = Color(0xFF4F46E5),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "AI 연결 테스트",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4F46E5)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xFFF1F5F9),
                                border = BorderStroke(0.8.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clickable { onRunSystemCheck() }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "System check",
                                        tint = BrandSlate,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "시스템 상태 점검",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandSlate
                                    )
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
                        Text("설정 저장", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
}

