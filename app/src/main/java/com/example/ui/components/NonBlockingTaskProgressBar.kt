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
 * Non-blocking Background Task Progress Bar
 * Replaces the full-screen blocking Dialog so the user can freely scroll, read past summaries,
 * and interact with the entire app while AI summarizes in the background.
 */
@Composable
fun NonBlockingTaskProgressBar(
    task: TaskProgress,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(0.8.dp, Color(0xFFCBD5E1))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Header Row: Title & Cancel Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF2563EB),
                        trackColor = Color(0xFFE2E8F0)
                    )
                    Text(
                        text = task.title.ifEmpty { "AI 요약 진행 중" },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandSlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (task.isCancellable) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(0.8.dp, Color(0xFFFECACA)),
                        modifier = Modifier.clickable { onCancel() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "요약 멈추기",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "요약 멈추기",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }
                }
            }

            // Detail description (date, word count, tokens/sec)
            if (task.detail.isNotEmpty()) {
                Text(
                    text = task.detail,
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Live progress bar
            if (!task.isIndeterminate && task.total > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { task.progressPercentage },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = BrandGreenAccent,
                        trackColor = Color(0xFFE2E8F0)
                    )
                    Text(
                        text = "${task.progressPercentInt}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandGreenAccent
                    )
                }
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF3B82F6),
                    trackColor = Color(0xFFE2E8F0)
                )
            }
        }
    }
}

