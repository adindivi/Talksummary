package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
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
import java.util.Calendar
import com.example.data.ChatDay
import com.example.data.MonthGroupData
import com.example.data.YearGroupData
import com.example.data.TimelineGroupingMode
import com.example.service.TaskProgress
import com.example.ui.theme.*
import com.example.ui.util.AvatarColorUtils
import com.example.ui.util.debouncedClickable
import kotlinx.coroutines.delay

/**
 * Staggered entry animation wrapper (Fade-in & Slide-up)
 * Creates a cascading, smooth Apple/Toss-style entry motion when tabs change or lists load.
 */
@Composable
fun StaggeredListItem(
    index: Int,
    key: Any,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isVisible by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key) {
        val delayMs = if (index <= 5) (index * 35L) else 0L
        if (delayMs > 0L) {
            delay(delayMs)
        }
        isVisible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 280,
            easing = FastOutSlowInEasing
        ),
        label = "staggeredAlpha"
    )

    val translateY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 22f,
        animationSpec = tween(
            durationMillis = 280,
            easing = FastOutSlowInEasing
        ),
        label = "staggeredTranslateY"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                this.translationY = translateY * density
            }
    ) {
        content()
    }
}

// TIMELINE CARD INDIVIDUAL COMPONENT (Streamlined Flat Layout with Inline Live Progress & Apple-Style Pill Button)
@Composable
fun TimelineItemCard(
    chatDay: ChatDay,
    onSelect: () -> Unit,
    onAIPress: () -> Unit,
    onCopySummary: (String) -> Unit = {},
    isSummarizing: Boolean = false,
    activeTask: TaskProgress? = null,
    onCancelTask: () -> Unit = {},
    onOpenStory: () -> Unit = {}
) {
    val isAISummarized = chatDay.summary.startsWith("[AI 정밀 요약]")

    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSummarizing -> Color(0xFFF8FAFF)
                isAISummarized -> Color(0xFFFAF8FF)
                else -> Color.White
            }
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            when {
                isSummarizing -> Color(0xFF818CF8)
                isAISummarized -> Color(0xFFE9D5FF)
                else -> AppleSurfaceBorder
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSummarizing) 1.dp else 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isSummarizing, onClick = onSelect)
    ) {
        Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp)) {
            // Header Row: Date + Status Badge + Message Count
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
                    if (isSummarizing) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFEEF2FF),
                            border = BorderStroke(0.6.dp, Color(0xFFC7D2FE))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = Color(0xFF4F46E5),
                                    trackColor = Color(0xFFE0E7FF)
                                )
                                Text(
                                    text = "작성 중",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4338CA)
                                )
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                        .border(0.8.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = "💬 ${chatDay.msgCount}건",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // AI 3줄 요약 Title Bar & Copy Button (Shown when not currently in active summarizing mode)
            if (!isSummarizing) {
                if (isAISummarized) {
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
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "AI 요약본",
                                tint = Color(0xFF7C3AED),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "AI 핵심 3줄 요약",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7C3AED)
                            )
                        }

                        IconButton(
                            onClick = {
                                val cleanText = chatDay.summary.replace("[AI 정밀 요약]\n", "")
                                onCopySummary(cleanText)
                            },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "요약 복사",
                                tint = Color(0xFF7C3AED),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val cleanText = chatDay.summary.replace("[AI 정밀 요약]\n", "")
                                onCopySummary(cleanText)
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "요약 복사",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            // Summary Content or In-Card Live Progress Indicator
            if (isSummarizing && activeTask != null) {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5FD)),
                    border = BorderStroke(0.8.dp, Color(0xFFC7D2FE)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = activeTask.detail.ifEmpty { "AI 핵심 3줄 요약을 작성하고 있어요..." },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E1B4B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = 6.dp)
                            )

                            // Cancel Button
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xFFFEF2F2),
                                border = BorderStroke(0.8.dp, Color(0xFFFECACA)),
                                modifier = Modifier.clickable { onCancelTask() }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "요약 멈추기",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "멈추기",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                            }
                        }

                        // Linear progress indicator
                        if (!activeTask.isIndeterminate && activeTask.total > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { activeTask.progressPercentage },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = Color(0xFF4F46E5),
                                    trackColor = Color(0xFFE0E7FF)
                                )
                                Text(
                                    text = "${activeTask.progressPercentInt}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4F46E5)
                                )
                            }
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.5.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = Color(0xFF4F46E5),
                                trackColor = Color(0xFFE0E7FF)
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = chatDay.summary.replace("[AI 정밀 요약]\n", ""),
                    fontSize = 12.sp,
                    color = if (isAISummarized) Color(0xFF1E1B4B) else Color(0xFF334155),
                    lineHeight = 18.5.sp,
                    letterSpacing = (-0.2).sp,
                    fontWeight = if (isAISummarized) FontWeight.Medium else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Actions: Keywords + Compact Apple-Style Pill Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Scrollable horizontal Row of Keywords tags (Apple-Style Soft Tint Chips)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(end = 8.dp)
                ) {
                    chatDay.keywords.filter { !com.example.data.TalkSummaryRepository.isStopWord(it) }.take(4).forEach { keyword ->
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(999.dp))
                                .border(0.6.dp, Color(0xFFE2E8F0), RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 2.5.dp)
                        ) {
                            Text(
                                text = keyword,
                                fontSize = 10.sp,
                                color = Color(0xFF334155),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (isSummarizing) {
                    // While summarizing, show an Apple-style pulsating Pill indicating work in progress
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFEEF2FF),
                        border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.8.dp,
                                color = Color(0xFF4F46E5),
                                trackColor = Color(0xFFE0E7FF)
                            )
                            Text(
                                text = "요약 중...",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4338CA)
                            )
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 3-Card Cinematic Story Carousel Trigger Button
                        OutlinedButton(
                            onClick = onOpenStory,
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF475569)
                            ),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text("🎬", fontSize = 10.sp)
                                Text(
                                    text = "스토리",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Button(
                            onClick = onAIPress,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAISummarized) Color(0xFFF1F5F9) else BrandSlate,
                                contentColor = if (isAISummarized) BrandSlate else Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(999.dp),
                            border = if (isAISummarized) BorderStroke(1.dp, Color(0xFFCBD5E1)) else null,
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
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
    }
}
