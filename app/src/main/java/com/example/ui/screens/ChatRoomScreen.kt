package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.data.ChatDay
import com.example.ui.theme.KakaoChatBg
import com.example.ui.theme.KakaoHeaderBg
import com.example.ui.theme.KakaoTextDark
import com.example.ui.theme.KakaoYellow
import com.example.ui.util.AvatarColorUtils
import com.example.ui.util.debouncedClickable

/**
 * Fullscreen KakaoTalk-styled Chatroom Viewer.
 * Renders individual day's chronological chat bubbles, expandable AI 3-line summary card,
 * sequential avatar grouping, message copying, and smooth auto-scroll.
 */
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
    onShowPrivacyModal: () -> Unit = {},
    onTriggerSummarize: ((ChatDay) -> Unit)? = null,
    isSummarizing: Boolean = false,
    onOpenStory: ((ChatDay) -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()

    var isSummaryExpanded by remember { mutableStateOf(false) }
    val isAISummarized = chatDay.summary.startsWith("[AI 정밀 요약]")
    val cleanSummary = chatDay.summary.replace("[AI 정밀 요약]\n", "").trim()
    val hasSummary = cleanSummary.isNotBlank()

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
        // App header bar mimicking KakaoTalk Chatroom header (Ultra-Slim Padding)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KakaoHeaderBg)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (isMobile) {
                    IconButton(
                        onClick = onBackToList,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF374151),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Naturally combined 💬 Icon and Chat Title Column (Inline integration saves 40dp width)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "💬",
                            fontSize = 12.5.sp
                        )
                        Text(
                            text = chatDay.participants.take(3).joinToString(", ") + " 외 대화방",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF111827),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${chatDay.date} • 카톡 대화방",
                        fontSize = 8.5.sp,
                        color = Color(0xFF475569),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }

            // Quick Operations Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Header Toggle Summary Icon Button
                IconButton(
                    onClick = { isSummaryExpanded = !isSummaryExpanded },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Toggle Summary",
                        tint = if (isSummaryExpanded || isAISummarized) Color(0xFF6366F1) else Color(0xFF374151),
                        modifier = Modifier.size(15.dp)
                    )
                }

                IconButton(
                    onClick = onToggleSender,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Toggle Sender",
                        tint = Color(0xFF1F2937),
                        modifier = Modifier.size(15.dp)
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
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Scroll top",
                        tint = Color(0xFF1F2937),
                        modifier = Modifier.size(15.dp)
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
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Scroll bottom",
                        tint = Color(0xFF1F2937),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // Apple-Style Floating Pill/Capsule Summary Bar (26dp Ultra-Slim Height)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.5.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(if (isSummaryExpanded) 14.dp else 999.dp),
                color = Color.White.copy(alpha = 0.94f),
                shadowElevation = if (isSummaryExpanded) 2.dp else 0.dp,
                border = BorderStroke(0.6.dp, Color(0xFFCBD5E1).copy(alpha = 0.8f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Collapsed Bar: 26dp Ultra-Slim Apple Pill Notice
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .clickable { isSummaryExpanded = !isSummaryExpanded }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = if (isAISummarized) "✨" else "📌",
                                fontSize = 10.sp
                            )
                            if (hasSummary) {
                                val firstLine = cleanSummary.lines().firstOrNull { it.isNotBlank() } ?: cleanSummary
                                Text(
                                    text = firstLine,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF334155),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = "이 날의 대화 요약 보기 (탭하여 펼치기)",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isAISummarized) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFEDE9FE), RoundedCornerShape(999.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "AI 요약",
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF7C3AED)
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (isSummaryExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand Summary",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    // Expanded Accordion Card View
                    AnimatedVisibility(
                        visible = isSummaryExpanded,
                        enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(180))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isAISummarized) "✨ AI 3줄 정밀 요약" else "📌 대화 핵심 요약",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAISummarized) Color(0xFF6D28D9) else Color(0xFF1E293B)
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (hasSummary) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color.White, RoundedCornerShape(999.dp))
                                                .border(0.6.dp, Color(0xFFCBD5E1), RoundedCornerShape(999.dp))
                                                .clickable {
                                                    clipboardManager.setText(AnnotatedString(cleanSummary))
                                                    onShowToast("3줄 요약이 복사되었습니다.")
                                                }
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copy",
                                                    tint = Color(0xFF475569),
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Text(
                                                    text = "복사",
                                                    fontSize = 9.sp,
                                                    color = Color(0xFF475569),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    if (onOpenStory != null) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFFEF3C7), RoundedCornerShape(999.dp))
                                                .border(0.6.dp, Color(0xFFFDE68A), RoundedCornerShape(999.dp))
                                                .clickable {
                                                    onOpenStory(chatDay)
                                                }
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text("🎬", fontSize = 9.sp)
                                                Text(
                                                    text = "스토리",
                                                    fontSize = 9.sp,
                                                    color = Color(0xFF92400E),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    if (onTriggerSummarize != null && !isSummarizing) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF6366F1), RoundedCornerShape(999.dp))
                                                .clickable {
                                                    onTriggerSummarize(chatDay)
                                                }
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = "Summarize",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Text(
                                                    text = if (isAISummarized) "다시 요약" else "AI 요약",
                                                    fontSize = 9.sp,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (isSummarizing) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 1.8.dp,
                                        color = Color(0xFF6366F1)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "AI가 대화를 정밀하게 요약하고 있어요...",
                                        fontSize = 10.5.sp,
                                        color = Color(0xFF6366F1),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else if (hasSummary) {
                                Text(
                                    text = cleanSummary,
                                    fontSize = 10.5.sp,
                                    lineHeight = 15.5.sp,
                                    color = Color(0xFF334155),
                                    letterSpacing = (-0.2).sp
                                )
                            } else {
                                Text(
                                    text = "아직 생성된 대화 요약이 없습니다. 상단의 [AI 요약] 버튼을 누르면 딱 3줄로 요약해 드려요!",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF64748B),
                                    lineHeight = 14.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Chat messages timeline lazylist (Tightened Spacing & Top Padding)
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 6.dp)
        ) {
            // Header date card element (Compact Padding)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF9EABB8).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = chatDay.date,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Real messages bubbles (Robust Single-Line Timestamp & Proportional Width)
            items(chatDay.messages.size) { idx ->
                val msg = chatDay.messages[idx]
                val isMe = msg.sender == mainUser

                // Sequential messages formatting optimization
                val showHeader = !isMe && (idx == 0 || chatDay.messages[idx - 1].sender != msg.sender)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Top
                ) {
                    if (!isMe) {
                        if (showHeader) {
                            val (avatarBg, avatarText) = AvatarColorUtils.getAvatarColors(msg.sender)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(avatarBg, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = msg.sender.take(1),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = avatarText
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(34.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (isMe) {
                        Spacer(modifier = Modifier.width(if (isMobile) 36.dp else 80.dp))
                    }

                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                    ) {
                        if (showHeader) {
                            Text(
                                text = msg.sender,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4B5563),
                                modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
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
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(end = 4.dp, bottom = 2.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
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
                                    .padding(horizontal = 11.dp, vertical = 7.dp)
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
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                                )
                            }
                        }
                    }

                    if (!isMe) {
                        Spacer(modifier = Modifier.width(if (isMobile) 28.dp else 60.dp))
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
