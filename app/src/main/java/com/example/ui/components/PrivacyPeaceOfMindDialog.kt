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
                    text = "소중한 대화, 안심하고 맡겨주세요",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandSlate,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "외부 서버로 보내지 않고, 오직 내 스마트폰 안에서만 안전하게 머물러요.",
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
                        title = "개발자도 대화를 볼 수 없어요",
                        desc = "불러온 대화는 외부 서버로 나가지 않고, 오직 내 스마트폰 안에만 안전하게 암호화되어 보관돼요."
                    )

                    PrivacyCommitmentCard(
                        emoji = "🛡️",
                        title = "AI 요약이 끝나면 즉시 사라져요",
                        desc = "요약할 때만 강력한 보안 채널로 안전하게 처리되며, 요약이 끝나면 어디에도 남지 않고 즉시 파기돼요."
                    )

                    PrivacyCommitmentCard(
                        emoji = "🧹",
                        title = "원할 때 흔적 없이 지울 수 있어요",
                        desc = "화면 상단의 휴지통 버튼을 누르면, 저장된 모든 대화와 요약 기록이 스마트폰에서 흔적도 없이 깨끗하게 사라져요."
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(999.dp),
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

