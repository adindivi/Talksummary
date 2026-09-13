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
 * Galaxy Gallery (One UI) Style 3-Tier Segmented Switcher [년도별 | 월별 | 일별]
 */
@Composable
fun GalaxySegmentedSwitcher(
    selectedMode: TimelineGroupingMode,
    onModeSelect: (TimelineGroupingMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFF1F5F9),
        border = BorderStroke(0.8.dp, Color(0xFFE2E8F0)),
        modifier = modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimelineGroupingMode.values().forEach { mode ->
                val isSelected = selectedMode == mode
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isSelected) Color.White else Color.Transparent,
                    border = if (isSelected) BorderStroke(0.7.dp, Color(0xFFCBD5E1)) else null,
                    shadowElevation = if (isSelected) 1.dp else 0.dp,
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable { onModeSelect(mode) }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(min = 52.dp)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF0F172A) else Color(0xFF64748B),
                            letterSpacing = (-0.2).sp,
                            style = LocalTextStyle.current.copy(
                                platformStyle = @Suppress("DEPRECATION") androidx.compose.ui.text.PlatformTextStyle(
                                    includeFontPadding = false
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Month Summary Card Component (Galaxy Gallery Style)
 */
@Composable
fun MonthSummaryCard(
    monthData: MonthGroupData,
    onViewDays: () -> Unit,
    onOpenAnalysis: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppleSurfaceBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onViewDays)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            // Header Row: Display Title + Message Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp))
                            .border(0.8.dp, Color(0xFFDBEAFE), RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "${monthData.month}월",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D4ED8)
                        )
                    }
                    Text(
                        text = monthData.displayTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp))
                            .border(0.8.dp, Color(0xFFBFDBFE), RoundedCornerShape(8.dp))
                            .clickable { onOpenAnalysis(monthData.yearMonthKey) }
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text("📊", fontSize = 10.sp)
                            Text(
                                text = "분석",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D4ED8)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                            .border(0.8.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "💬 ${monthData.daysCount}일 (${monthData.totalMessages}건)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-status Row: AI Summary Stats & Participants
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (monthData.summarizedDaysCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFFAF5FF),
                        border = BorderStroke(0.6.dp, Color(0xFFE9D5FF))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "AI 요약 통계",
                                tint = Color(0xFF7C3AED),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "AI 요약 ${monthData.summarizedDaysCount}/${monthData.daysCount}일",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7C3AED)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "일별 AI 요약 대기 중",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (monthData.topSenders.isNotEmpty()) {
                    Text(
                        text = "대화: ${monthData.topSenders.take(2).joinToString(", ")}",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
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
                    monthData.topKeywords.take(4).forEach { keyword ->
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

                Button(
                    onClick = onViewDays,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandSlate,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(999.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "일별 대화 보기",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "일별 보기",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Year Summary Card Component (Galaxy Gallery Style)
 */
@Composable
fun YearSummaryCard(
    yearData: YearGroupData,
    onViewMonths: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppleSurfaceBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onViewMonths)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            // Header Row: Display Title + Message Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFEF3C7), RoundedCornerShape(8.dp))
                            .border(0.8.dp, Color(0xFFFDE68A), RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "타임캡슐",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309)
                        )
                    }
                    Text(
                        text = yearData.displayTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF0F172A)
                    )
                }

                Box(
                    modifier = Modifier
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                        .border(0.8.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = "총 ${yearData.daysCount}일 (${yearData.totalMessages}건)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Month Breakdown Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                yearData.monthBreakdown.forEach { (monthName, count) ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(0.6.dp, Color(0xFFE2E8F0))
                    ) {
                        Text(
                            text = "$monthName (${count}일)",
                            fontSize = 10.sp,
                            color = Color(0xFF475569),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
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
                    yearData.topKeywords.take(4).forEach { keyword ->
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

                Button(
                    onClick = onViewMonths,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandSlate,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(999.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "월별 대화 보기",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "월별 보기",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }
    }
}

