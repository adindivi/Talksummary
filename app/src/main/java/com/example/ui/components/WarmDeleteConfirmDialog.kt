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

