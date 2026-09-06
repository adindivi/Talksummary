package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ChatDay
import com.example.data.parser.ChatAnalyticsEngine
import com.example.model.MonthlyAnalysisReport
import com.example.model.ParticipantShare
import com.example.model.PeakDayData
import com.example.model.TimeSlotDistribution
import com.example.ui.util.AvatarColorUtils

private val KakaoBtnYellow = Color(0xFFFEE500)
private val KakaoBtnDark = Color(0xFF191919)

@Composable
fun TalkAnalysisReportDialog(
    chatDays: List<ChatDay>,
    initialYearMonth: String? = null,
    onDismiss: () -> Unit,
    onShareReport: (MonthlyAnalysisReport) -> Unit = {}
) {
    val availableMonths = remember(chatDays) {
        ChatAnalyticsEngine.getAvailableYearMonths(chatDays)
    }

    var selectedYearMonth by remember(initialYearMonth, availableMonths) {
        mutableStateOf(initialYearMonth ?: availableMonths.firstOrNull() ?: "")
    }

    val report = remember(selectedYearMonth, chatDays) {
        ChatAnalyticsEngine.analyzeMonth(chatDays, selectedYearMonth)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .heightIn(max = 700.dp),
                shape = RoundedCornerShape(26.dp),
                color = Color.White,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top Header Section
                    ReportHeader(
                        report = report,
                        availableMonths = availableMonths,
                        selectedYearMonth = selectedYearMonth,
                        onSelectMonth = { selectedYearMonth = it },
                        onDismiss = onDismiss
                    )

                    // Scrollable Analysis Cards
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. 발언 지분율 & 랭킹 카드
                        ParticipantRankSection(participantShares = report.participantShares)

                        // 2. 가장 뜨거웠던 날 (피크 데이) 카드
                        report.peakDay?.let { peak ->
                            PeakDaySection(peakDay = peak, avgDaily = report.avgDailyMessages)
                        }

                        // 3. 대화 골든타임 & 페르소나 카드
                        TimeSlotPersonaSection(timeStats = report.timeSlotStats)

                        // 4. 월간 키워드 & 관계 케미 카드
                        ChemistrySection(
                            chemistryTitle = report.chemistryTitle,
                            chemistryDesc = report.chemistryDescription,
                            topKeywords = report.topKeywords
                        )
                    }

                    // Bottom Action Bar: KakaoTalk Share
                    Surface(
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onShareReport(report) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = KakaoBtnYellow,
                                    contentColor = KakaoBtnDark
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Share,
                                        contentDescription = "공유",
                                        tint = KakaoBtnDark,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "카카오톡으로 리포트 공유하기",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = KakaoBtnDark
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportHeader(
    report: MonthlyAnalysisReport,
    availableMonths: List<String>,
    selectedYearMonth: String,
    onSelectMonth: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFEFF6FF), Color(0xFFF8FAFC))
                )
            )
            .padding(top = 18.dp, bottom = 12.dp, start = 18.dp, end = 18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFFDBEAFE),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "📊 월간 카톡 분석 리포트",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D4ED8),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "닫기",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${report.displayMonth} 대화 분석",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0F172A)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${report.daysCount}일간 총 ${report.totalMessages}건의 소중한 대화 기록",
                fontSize = 12.5.sp,
                color = Color(0xFF64748B)
            )

            // Month Select Chips (if multiple months exist)
            if (availableMonths.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableMonths.forEach { ym ->
                        val isSelected = ym == selectedYearMonth
                        val monthLabel = ym.split("-").let { parts ->
                            val m = parts.getOrNull(1)?.toIntOrNull() ?: 1
                            "${m}월"
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (isSelected) Color(0xFF2563EB) else Color.White,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF2563EB) else Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { onSelectMonth(ym) }
                        ) {
                            Text(
                                text = monthLabel,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFF475569),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 1. 참여자 발언 지분율 & 랭킹 (Horizontal Stack Bar + Podium Cards)
 */
@Composable
private fun ParticipantRankSection(participantShares: List<ParticipantShare>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("👥", fontSize = 15.sp)
                Text(
                    "누가 대화를 이끌었을까?",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (participantShares.isEmpty()) {
                Text(
                    "참여자 데이터가 충분하지 않습니다.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                // Horizontal Stack Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFE2E8F0))
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        participantShares.forEach { share ->
                            val colorPair = AvatarColorUtils.getAvatarColors(share.name)
                            Box(
                                modifier = Modifier
                                    .weight(share.percentage.coerceAtLeast(2).toFloat())
                                    .fillMaxHeight()
                                    .background(colorPair.second.copy(alpha = 0.85f))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Top Talkers List
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    participantShares.take(4).forEach { share ->
                        val colorPair = AvatarColorUtils.getAvatarColors(share.name)
                        val (rankIcon, medalColor) = when (share.rank) {
                            1 -> Pair("🥇", Color(0xFFF59E0B))
                            2 -> Pair("🥈", Color(0xFF94A3B8))
                            3 -> Pair("🥉", Color(0xFFD97706))
                            else -> Pair("⚡", Color(0xFF64748B))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(rankIcon, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))

                            // Avatar Circle
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(colorPair.first),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = share.name.take(1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorPair.second
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = share.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1E293B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        color = colorPair.first.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = share.badge,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colorPair.second,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "${share.count}건 (${share.percentage}%)",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (share.rank == 1) Color(0xFF2563EB) else Color(0xFF475569)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 2. 가장 뜨거웠던 날 (피크 데이) 카드
 */
@Composable
private fun PeakDaySection(peakDay: PeakDayData, avgDaily: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
        border = BorderStroke(1.dp, Color(0xFFFED7AA)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("🔥", fontSize = 15.sp)
                Text(
                    "가장 뜨거웠던 하루",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF9A3412)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = peakDay.displayDate,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFC2410C)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "이날 하루에만 ${peakDay.messageCount}건(전체의 ${peakDay.percentageOfTotal}%)의 대화가 쏟아졌어요!",
                fontSize = 12.5.sp,
                color = Color(0xFF9A3412),
                lineHeight = 17.sp
            )

            if (peakDay.peakKeywords.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    peakDay.peakKeywords.forEach { kw ->
                        Surface(
                            color = Color(0xFFFFEDD5),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "#$kw",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFC2410C),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFFED7AA).copy(alpha = 0.6f), thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "📅 하루 평균 대화량: ${avgDaily}건",
                fontSize = 11.5.sp,
                color = Color(0xFF9A3412).copy(alpha = 0.85f)
            )
        }
    }
}

/**
 * 3. 대화 골든타임 & 라이프스타일 페르소나 카드
 */
@Composable
private fun TimeSlotPersonaSection(timeStats: TimeSlotDistribution) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("⏰", fontSize = 15.sp)
                Text(
                    "대화 골든타임 & 페르소나",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF166534)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = timeStats.personaTitle,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF15803D)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = timeStats.personaDescription,
                fontSize = 12.sp,
                color = Color(0xFF166534),
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4 Time slots breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TimeSlotCell(
                    modifier = Modifier.weight(1f),
                    label = "아침",
                    timeRange = "06~12시",
                    pct = timeStats.morningPercent
                )
                TimeSlotCell(
                    modifier = Modifier.weight(1f),
                    label = "낮",
                    timeRange = "12~18시",
                    pct = timeStats.afternoonPercent
                )
                TimeSlotCell(
                    modifier = Modifier.weight(1f),
                    label = "저녁",
                    timeRange = "18~24시",
                    pct = timeStats.eveningPercent
                )
                TimeSlotCell(
                    modifier = Modifier.weight(1f),
                    label = "심야",
                    timeRange = "00~06시",
                    pct = timeStats.nightPercent
                )
            }
        }
    }
}

@Composable
private fun TimeSlotCell(
    modifier: Modifier = Modifier,
    label: String,
    timeRange: String,
    pct: Int
) {
    Surface(
        modifier = modifier,
        color = Color.White,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFDCFCE7))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
            Text(timeRange, fontSize = 9.sp, color = Color(0xFF86EFAC))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${pct}%",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (pct >= 35) Color(0xFF15803D) else Color(0xFF475569)
            )
        }
    }
}

/**
 * 4. 월간 키워드 & 관계 케미 카드
 */
@Composable
private fun ChemistrySection(
    chemistryTitle: String,
    chemistryDesc: String,
    topKeywords: List<String>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF5FF)),
        border = BorderStroke(1.dp, Color(0xFFE9D5FF)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("💫", fontSize = 15.sp)
                Text(
                    "이 달의 대화 분위기 & 관계 케미",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6B21A8)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = chemistryTitle,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF7E22CE)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = chemistryDesc,
                fontSize = 12.sp,
                color = Color(0xFF6B21A8),
                lineHeight = 16.sp
            )

            if (topKeywords.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    topKeywords.forEach { kw ->
                        Surface(
                            color = Color(0xFFF3E8FF),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, Color(0xFFDDD6FE))
                        ) {
                            Text(
                                text = "#$kw",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF7E22CE),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
