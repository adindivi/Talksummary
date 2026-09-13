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
import com.example.model.GgufMetadata
import com.example.ui.theme.*
import com.example.ui.util.debouncedClickable

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

