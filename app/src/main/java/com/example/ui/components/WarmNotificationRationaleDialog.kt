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
