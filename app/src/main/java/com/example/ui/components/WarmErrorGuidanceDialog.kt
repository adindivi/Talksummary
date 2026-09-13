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
                                text = "카톡 채팅방 > 우측 상단 메뉴(≡) > 설정(⚙️) > [대화 내용 내보내기] 후 저장된 텍스트 파일(.txt)을 선택해 주세요.",
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

