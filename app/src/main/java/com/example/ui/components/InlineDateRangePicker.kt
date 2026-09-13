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

private data class DayCellData(
    val dayNumber: Int,
    val isoDate: String,
    val hasChat: Boolean
)

// Helpers for Date Arithmetic in Inline Date Range Picker
private fun parseYearMonth(dateStr: String): Pair<Int, Int>? {
    if (dateStr.isEmpty()) return null
    val parts = dateStr.split("-")
    if (parts.size >= 2) {
        val y = parts[0].toIntOrNull()
        val m = parts[1].toIntOrNull()
        if (y != null && m != null) return Pair(y, m)
    }
    return null
}

private fun formatIsoDate(year: Int, month: Int, day: Int): String {
    val m = if (month < 10) "0$month" else "$month"
    val d = if (day < 10) "0$day" else "$day"
    return "$year-$m-$d"
}

private fun offsetDateIso(dateStr: String, dayOffset: Int): String {
    try {
        val parts = dateStr.split("-")
        if (parts.size == 3) {
            val y = parts[0].toIntOrNull() ?: return dateStr
            val m = parts[1].toIntOrNull() ?: return dateStr
            val d = parts[2].toIntOrNull() ?: return dateStr
            val cal = Calendar.getInstance()
            cal.set(y, m - 1, d)
            cal.add(Calendar.DAY_OF_MONTH, dayOffset)
            return formatIsoDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        }
    } catch (_: Exception) {}
    return dateStr
}

/**
 * Modern Inline Date Range Picker (Apple iOS No-Modal Accordion + Material 3 Continuous Range Band)
 */
@Composable
fun InlineDateRangePicker(
    chatDays: List<ChatDay>,
    selectedStartDate: String,
    selectedEndDate: String,
    onRangeSelected: (String, String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chatDatesSet = remember(chatDays) { chatDays.mapTo(HashSet(chatDays.size)) { it.date } }
    val latestChatDate = remember(chatDays) { chatDays.maxOfOrNull { it.date } ?: "" }

    val initialYearMonth = remember(selectedStartDate, latestChatDate) {
        parseYearMonth(selectedStartDate)
            ?: parseYearMonth(latestChatDate)
            ?: run {
                val now = Calendar.getInstance()
                Pair(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1)
            }
    }

    var currentYear by remember { mutableIntStateOf(initialYearMonth.first) }
    var currentMonth by remember { mutableIntStateOf(initialYearMonth.second) }

    var tempStart by remember(selectedStartDate) { mutableStateOf(selectedStartDate) }
    var tempEnd by remember(selectedEndDate) { mutableStateOf(selectedEndDate) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Quick Presets Row (Apple Pill Style)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isAll = tempStart.isEmpty() && tempEnd.isEmpty()
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (isAll) Color(0xFF2563EB) else Color(0xFFF1F5F9),
                modifier = Modifier.clickable {
                    tempStart = ""
                    tempEnd = ""
                    onClear()
                }
            ) {
                Text(
                    text = "전체 대화",
                    fontSize = 11.sp,
                    fontWeight = if (isAll) FontWeight.Bold else FontWeight.Medium,
                    color = if (isAll) Color.White else BrandSlate,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            if (latestChatDate.isNotEmpty()) {
                val recent7Start = offsetDateIso(latestChatDate, -6)
                val isRecent7 = tempStart == recent7Start && tempEnd == latestChatDate
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isRecent7) Color(0xFF2563EB) else Color(0xFFF1F5F9),
                    modifier = Modifier.clickable {
                        tempStart = recent7Start
                        tempEnd = latestChatDate
                        parseYearMonth(recent7Start)?.let {
                            currentYear = it.first
                            currentMonth = it.second
                        }
                        onRangeSelected(recent7Start, latestChatDate)
                    }
                ) {
                    Text(
                        text = "최근 7일",
                        fontSize = 11.sp,
                        fontWeight = if (isRecent7) FontWeight.Bold else FontWeight.Medium,
                        color = if (isRecent7) Color.White else BrandSlate,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                val recent30Start = offsetDateIso(latestChatDate, -29)
                val isRecent30 = tempStart == recent30Start && tempEnd == latestChatDate
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isRecent30) Color(0xFF2563EB) else Color(0xFFF1F5F9),
                    modifier = Modifier.clickable {
                        tempStart = recent30Start
                        tempEnd = latestChatDate
                        parseYearMonth(recent30Start)?.let {
                            currentYear = it.first
                            currentMonth = it.second
                        }
                        onRangeSelected(recent30Start, latestChatDate)
                    }
                ) {
                    Text(
                        text = "최근 30일",
                        fontSize = 11.sp,
                        fontWeight = if (isRecent30) FontWeight.Bold else FontWeight.Medium,
                        color = if (isRecent30) Color.White else BrandSlate,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.8.dp)

        // Month Navigation: [<] YYYY년 M월 [>]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (currentMonth == 1) {
                        currentYear -= 1
                        currentMonth = 12
                    } else {
                        currentMonth -= 1
                    }
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "이전 달",
                    tint = BrandSlate,
                    modifier = Modifier.size(15.dp)
                )
            }

            Text(
                text = "${currentYear}년 ${currentMonth}월",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = BrandSlate
            )

            IconButton(
                onClick = {
                    if (currentMonth == 12) {
                        currentYear += 1
                        currentMonth = 1
                    } else {
                        currentMonth += 1
                    }
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "다음 달",
                    tint = BrandSlate,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // Days of Week Header
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            val daysOfWeek = listOf("일", "월", "화", "수", "목", "금", "토")
            daysOfWeek.forEachIndexed { index, day ->
                Text(
                    text = day,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (index) {
                        0 -> Color(0xFFEF4444)
                        6 -> Color(0xFF3B82F6)
                        else -> Color(0xFF64748B)
                    },
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Days Grid for currentYear & currentMonth (Precomputed single-pass memoization)
        val dayCells = remember(currentYear, currentMonth, chatDatesSet) {
            val cal = Calendar.getInstance().apply {
                set(currentYear, currentMonth - 1, 1)
            }
            val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val leadingEmptyCount = firstDayOfWeek - 1
            val maxDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val totalCells = leadingEmptyCount + maxDaysInMonth
            val numRows = (totalCells + 6) / 7

            List(numRows * 7) { index ->
                val dayNumber = index - leadingEmptyCount + 1
                if (dayNumber in 1..maxDaysInMonth) {
                    val isoDate = formatIsoDate(currentYear, currentMonth, dayNumber)
                    DayCellData(
                        dayNumber = dayNumber,
                        isoDate = isoDate,
                        hasChat = chatDatesSet.contains(isoDate)
                    )
                } else {
                    null
                }
            }
        }
        val numRows = dayCells.size / 7

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (row in 0 until numRows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        val cellData = dayCells.getOrNull(cellIndex)

                        if (cellData != null) {
                            val isoDate = cellData.isoDate
                            val dayNumber = cellData.dayNumber
                            val hasChat = cellData.hasChat
                            val isStart = tempStart.isNotEmpty() && isoDate == tempStart
                            val isEnd = tempEnd.isNotEmpty() && isoDate == tempEnd
                            val isInRange = tempStart.isNotEmpty() && tempEnd.isNotEmpty() &&
                                    isoDate > tempStart && isoDate < tempEnd

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Background Range Band (Material 3 Continuous Highlight)
                                if (tempStart.isNotEmpty() && tempEnd.isNotEmpty() && tempStart != tempEnd) {
                                    when {
                                        isInRange -> {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(28.dp)
                                                    .background(Color(0xFFE0EDFF))
                                            )
                                        }
                                        isStart -> {
                                            Row(modifier = Modifier.fillMaxSize()) {
                                                Spacer(modifier = Modifier.weight(1f))
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(28.dp)
                                                        .align(Alignment.CenterVertically)
                                                        .background(
                                                            color = Color(0xFFE0EDFF),
                                                            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp)
                                                        )
                                                )
                                            }
                                        }
                                        isEnd -> {
                                            Row(modifier = Modifier.fillMaxSize()) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(28.dp)
                                                        .align(Alignment.CenterVertically)
                                                        .background(
                                                            color = Color(0xFFE0EDFF),
                                                            shape = RoundedCornerShape(topEnd = 0.dp, bottomEnd = 0.dp)
                                                        )
                                                )
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }

                                // Day Circle Target
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(
                                            color = when {
                                                isStart || isEnd -> Color(0xFF2563EB)
                                                else -> Color.Transparent
                                            },
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            if (tempStart.isEmpty() || (tempStart.isNotEmpty() && tempEnd.isNotEmpty())) {
                                                tempStart = isoDate
                                                tempEnd = ""
                                                onRangeSelected(isoDate, "")
                                            } else {
                                                if (isoDate < tempStart) {
                                                    tempStart = isoDate
                                                    tempEnd = ""
                                                    onRangeSelected(isoDate, "")
                                                } else if (isoDate == tempStart) {
                                                    tempEnd = isoDate
                                                    onRangeSelected(tempStart, isoDate)
                                                } else {
                                                    tempEnd = isoDate
                                                    onRangeSelected(tempStart, isoDate)
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "$dayNumber",
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isStart || isEnd) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                isStart || isEnd -> Color.White
                                                col == 0 -> Color(0xFFEF4444)
                                                col == 6 -> Color(0xFF2563EB)
                                                else -> Color(0xFF1E293B)
                                            }
                                        )
                                        if (hasChat) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 1.dp)
                                                    .size(3.dp)
                                                    .background(
                                                        color = if (isStart || isEnd) Color.White else Color(0xFF10B981),
                                                        shape = CircleShape
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f).height(36.dp))
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.8.dp)

        // Bottom Action Bar: Range status & Close
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val summaryText = when {
                tempStart.isNotEmpty() && tempEnd.isNotEmpty() -> "$tempStart ~ $tempEnd"
                tempStart.isNotEmpty() -> "$tempStart ~ (종료일 선택)"
                else -> "대화 날짜를 터치해 기간을 정하세요"
            }
            Text(
                text = summaryText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (tempStart.isNotEmpty()) Color(0xFF2563EB) else Color(0xFF64748B)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tempStart.isNotEmpty() || tempEnd.isNotEmpty()) {
                    Text(
                        text = "초기화",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFEF4444),
                        modifier = Modifier
                            .clickable {
                                tempStart = ""
                                tempEnd = ""
                                onClear()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.clickable { onClose() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "접기",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandSlate
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "접기",
                            tint = BrandSlate,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}
