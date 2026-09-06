package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ChatDay
import com.example.data.parser.ChatAnalyticsEngine
import com.example.model.*
import com.example.ui.util.AvatarColorUtils
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

    var selectedYearMonth by rememberSaveable(key = "selectedYearMonth") {
        mutableStateOf(initialYearMonth ?: availableMonths.firstOrNull() ?: "")
    }

    LaunchedEffect(availableMonths) {
        if (selectedYearMonth.isEmpty() || !availableMonths.contains(selectedYearMonth)) {
            availableMonths.firstOrNull()?.let { selectedYearMonth = it }
        }
    }

    val report = remember(selectedYearMonth, chatDays) {
        ChatAnalyticsEngine.analyzeMonth(chatDays, selectedYearMonth)
    }

    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val scrollState = rememberScrollState()
    var isClosing by remember { mutableStateOf(false) }

    fun dismissWithAnimation() {
        if (isClosing) return
        isClosing = true
        scope.launch {
            offsetY.animateTo(1200f, tween(200, easing = FastOutLinearInEasing))
            onDismiss()
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If user has already dragged the modal downwards and is now dragging up
                if (offsetY.value > 0f && available.y < 0) {
                    val consumed = minOf(-available.y, offsetY.value)
                    scope.launch { offsetY.snapTo(offsetY.value - consumed) }
                    return Offset(0f, -consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // When content is scrolled to the very top and user drags downwards
                if (available.y > 0 && scrollState.value == 0) {
                    scope.launch { offsetY.snapTo(offsetY.value + available.y * 0.7f) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetY.value > 120f || available.y > 800f) {
                    dismissWithAnimation()
                    return available
                } else if (offsetY.value > 0f) {
                    offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val dragModifier = Modifier.draggable(
        orientation = Orientation.Vertical,
        state = rememberDraggableState { delta ->
            if (!isClosing && (delta > 0 || offsetY.value > 0)) {
                scope.launch {
                    offsetY.snapTo(maxOf(0f, offsetY.value + delta))
                }
            }
        },
        onDragStopped = { velocity ->
            if (offsetY.value > 120f || velocity > 800f) {
                dismissWithAnimation()
            } else {
                scope.launch {
                    offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
            }
        }
    )

    Dialog(
        onDismissRequest = { dismissWithAnimation() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val scrimAlpha = (0.55f * (1f - (offsetY.value / 600f))).coerceIn(0f, 0.55f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { dismissWithAnimation() }
                .padding(horizontal = 14.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .heightIn(max = 700.dp)
                    .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    .nestedScroll(nestedScrollConnection)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Consume touches so click doesn't bubble up to scrim */ },
                shape = RoundedCornerShape(26.dp),
                color = Color.White,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top Drag Handle (Toss / iOS Bottom Sheet Style)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFEFF6FF))
                            .padding(top = 10.dp, bottom = 2.dp)
                            .then(dragModifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.5.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFCBD5E1))
                        )
                    }

                    // Top Header Section (Draggable to dismiss)
                    ReportHeader(
                        report = report,
                        availableMonths = availableMonths,
                        selectedYearMonth = selectedYearMonth,
                        onSelectMonth = { selectedYearMonth = it },
                        onDismiss = { dismissWithAnimation() },
                        dragModifier = dragModifier
                    )

                    // Scrollable Analysis Cards
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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

                        // 5. ⚡ 선톡 지수 & 티키타카 속도 카드 (기능 1)
                        report.firstPingStats?.let { pingStats ->
                            FirstPingSection(firstPing = pingStats)
                        }

                        // 6. 😂 말버릇 & 웃음 지수 리포트 카드 (기능 2)
                        report.quirksReport?.let { quirks ->
                            LinguisticQuirksSection(quirks = quirks)
                        }

                        // 7. 🟩 깃허브 잔디 스타일 대화 캘린더 카드 (기능 4)
                        report.heatmapData?.let { heatmap ->
                            TalkHeatmapSection(heatmap = heatmap)
                        }
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
    onDismiss: () -> Unit,
    dragModifier: Modifier = Modifier
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFEFF6FF), Color(0xFFF8FAFC))
                )
            )
            .then(dragModifier)
            .padding(top = 8.dp, bottom = 10.dp, start = 18.dp, end = 18.dp)
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
                        text = "📊 카톡 분석",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D4ED8),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${report.displayMonth} 대화 분석",
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0F172A)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${report.daysCount}일간 총 ${report.totalMessages}건의 대화 기록이에요",
                fontSize = 12.sp,
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
    var selectedShare by remember { mutableStateOf<ParticipantShare?>(null) }

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
                // Interactive Horizontal Stack Bar (Clickable segments!)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFFE2E8F0))
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        participantShares.forEach { share ->
                            val colorPair = AvatarColorUtils.getAvatarColors(share.name)
                            val isSelected = selectedShare?.name == share.name
                            Box(
                                modifier = Modifier
                                    .weight(share.percentage.coerceAtLeast(3).toFloat())
                                    .fillMaxHeight()
                                    .clickable {
                                        selectedShare = if (selectedShare?.name == share.name) null else share
                                    }
                                    .background(
                                        colorPair.second.copy(
                                            alpha = if (selectedShare == null || isSelected) 0.92f else 0.35f
                                        )
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 0.5.dp,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f)
                                    )
                            )
                        }
                    }
                }

                // Interactive Speaker Tooltip / Info Capsule
                Spacer(modifier = Modifier.height(8.dp))
                AnimatedContent(
                    targetState = selectedShare,
                    transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(140)) },
                    label = "speakerTooltip"
                ) { selected ->
                    if (selected != null) {
                        val selColors = AvatarColorUtils.getAvatarColors(selected.name)
                        Surface(
                            color = selColors.first,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, selColors.second.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedShare = null }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(selColors.second)
                                    )
                                    Text(
                                        text = selected.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = selColors.second
                                    )
                                    Surface(
                                        color = selColors.second.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = selected.badge,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = selColors.second,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${selected.count}건 (${selected.percentage}%)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = selColors.second
                                    )
                                    Text(
                                        text = "✕",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = selColors.second.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(start = 2.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "👆 색상 막대를 터치하면 발화자 이름을 확인할 수 있어요",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Top Talkers List — Clickable to highlight matching segment
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    participantShares.take(5).forEach { share ->
                        val colorPair = AvatarColorUtils.getAvatarColors(share.name)
                        val isSelected = selectedShare?.name == share.name
                        val rankIcon = when (share.rank) {
                            1 -> "🥇"
                            2 -> "🥈"
                            3 -> "🥉"
                            else -> "  "
                        }
                        val shortBadge = when (share.rank) {
                            1 -> "수다왕"
                            2 -> "조율자"
                            3 -> "분위기"
                            else -> ""
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) colorPair.first.copy(alpha = 0.6f) else Color.White,
                                    RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = if (isSelected) 1.2.dp else 0.8.dp,
                                    color = if (isSelected) colorPair.second.copy(alpha = 0.6f) else Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    selectedShare = if (selectedShare?.name == share.name) null else share
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left: Rank + Avatar + Name + Compact Badge
                            Row(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = rankIcon,
                                    fontSize = 14.sp,
                                    modifier = Modifier.width(20.dp)
                                )

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

                                Text(
                                    text = share.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1E293B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                if (shortBadge.isNotEmpty()) {
                                    Surface(
                                        color = colorPair.first,
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(0.5.dp, colorPair.second.copy(alpha = 0.25f))
                                    ) {
                                        Text(
                                            text = shortBadge,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colorPair.second,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Right: Count + Pill %
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${share.count}건",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Surface(
                                    color = if (share.rank == 1) Color(0xFFEFF6FF) else Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${share.percentage}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (share.rank == 1) Color(0xFF2563EB) else Color(0xFF475569),
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = peakDay.displayDate,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFC2410C)
                )
                Surface(
                    color = Color(0xFFFFEDD5),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${peakDay.percentageOfTotal}% 집중",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC2410C),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "이날 하루에만 ${peakDay.messageCount}건의 대화가 쏟아졌어요!",
                fontSize = 12.sp,
                color = Color(0xFF9A3412),
                lineHeight = 16.sp
            )

            if (peakDay.peakKeywords.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    peakDay.peakKeywords.forEach { kw ->
                        Surface(
                            color = Color(0xFFFFEDD5),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "#$kw",
                                fontSize = 10.5.sp,
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
    val maxPct = maxOf(
        timeStats.morningPercent,
        timeStats.afternoonPercent,
        timeStats.eveningPercent,
        timeStats.nightPercent
    )

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

            // 4 Time slots breakdown with max highlight
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TimeSlotCell(
                    modifier = Modifier.weight(1f),
                    label = "아침",
                    timeRange = "06~12시",
                    pct = timeStats.morningPercent,
                    isMax = timeStats.morningPercent == maxPct && maxPct > 0
                )
                TimeSlotCell(
                    modifier = Modifier.weight(1f),
                    label = "낮",
                    timeRange = "12~18시",
                    pct = timeStats.afternoonPercent,
                    isMax = timeStats.afternoonPercent == maxPct && maxPct > 0
                )
                TimeSlotCell(
                    modifier = Modifier.weight(1f),
                    label = "저녁",
                    timeRange = "18~24시",
                    pct = timeStats.eveningPercent,
                    isMax = timeStats.eveningPercent == maxPct && maxPct > 0
                )
                TimeSlotCell(
                    modifier = Modifier.weight(1f),
                    label = "심야",
                    timeRange = "00~06시",
                    pct = timeStats.nightPercent,
                    isMax = timeStats.nightPercent == maxPct && maxPct > 0
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
    pct: Int,
    isMax: Boolean = false
) {
    Surface(
        modifier = modifier,
        color = if (isMax) Color(0xFFDCFCE7) else Color.White,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isMax) Color(0xFF86EFAC) else Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isMax) Color(0xFF15803D) else Color(0xFF334155)
            )
            Text(
                timeRange,
                fontSize = 8.5.sp,
                color = if (isMax) Color(0xFF166534) else Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                "${pct}%",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isMax) Color(0xFF15803D) else Color(0xFF475569)
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
                    "우리들의 대화 분위기 & 관계 케미",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6B21A8)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = chemistryTitle,
                fontSize = 15.sp,
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
                Spacer(modifier = Modifier.height(8.dp))
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
                            border = BorderStroke(0.6.dp, Color(0xFFDDD6FE))
                        ) {
                            Text(
                                text = "#$kw",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF7E22CE),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 5. 선톡 지수 & 티키타카 속도 카드 (기능 1)
 */
@Composable
private fun FirstPingSection(firstPing: FirstPingAnalysis) {
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
                Text("⚡", fontSize = 15.sp)
                Text(
                    "선톡 지수 & 티키타카 속도",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF166534)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "이 방의 대화에 먼저 불을 지피는 사람은 누구일까?",
                fontSize = 12.sp,
                color = Color(0xFF15803D)
            )

            val leader = firstPing.leaders.firstOrNull()
            if (leader != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🥇 선톡 장인: ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                            Text(leader.name, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF15803D))
                        }
                        Text(
                            text = "${leader.pingCount}회 (${leader.pingPercentage}%)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF166534)
                        )
                    }
                }
            }

            if (firstPing.leaders.size > 1) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    firstPing.leaders.drop(1).take(2).forEachIndexed { idx, p ->
                        val medal = if (idx == 0) "🥈" else "🥉"
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.8.dp, Color(0xFFDCFCE7))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("$medal ${p.name}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${p.pingPercentage}%", fontSize = 11.sp, color = Color(0xFF15803D))
                            }
                        }
                    }
                }
            }

            // Speed comparison row
            if (firstPing.fastestResponder != null || firstPing.slowestResponder != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    firstPing.fastestResponder?.let { fast ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("⚡ 광속 칼답러", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(fast.name, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF92400E), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("평균 ${fast.displaySpeed}", fontSize = 10.5.sp, color = Color(0xFFB45309))
                            }
                        }
                    }

                    firstPing.slowestResponder?.let { slow ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("🐢 느긋한 관전자", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(slow.name, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E293B), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("평균 ${slow.displaySpeed}", fontSize = 10.5.sp, color = Color(0xFF64748B))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 6. 말버릇 & 웃음 지수 리포트 카드 (기능 2)
 */
@Composable
private fun LinguisticQuirksSection(quirks: LinguisticQuirksReport) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("😂", fontSize = 15.sp)
                Text(
                    "말버릇 & 웃음 지수 리포트",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF92400E)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "우리 방은 'ㅋㅋㅋ'형일까, 'ㅎㅎㅎ'형일까?",
                fontSize = 12.sp,
                color = Color(0xFFB45309)
            )

            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = Color(0xFFFEF3C7),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.8.dp, Color(0xFFFDE68A))
            ) {
                Text(
                    text = quirks.funFact,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF78350F),
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            if (quirks.users.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    quirks.users.forEach { u ->
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.6.dp, Color(0xFFFDE68A))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(AvatarColorUtils.getAvatarColors(u.name).first)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = u.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Surface(
                                    color = Color(0xFFFEF3C7),
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = u.mainQuirkBadge,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF92400E),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = u.topExpression,
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 7. 깃허브 잔디 스타일 대화 캘린더 카드 (기능 4)
 */
@Composable
private fun TalkHeatmapSection(heatmap: TalkHeatmapData) {
    var selectedTile by remember { mutableStateOf<DayHeatmapTile?>(null) }
    val startDayOfWeek = remember(heatmap.year, heatmap.month) {
        val cal = Calendar.getInstance(Locale.KOREA).apply {
            set(Calendar.YEAR, heatmap.year)
            set(Calendar.MONTH, heatmap.month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
        border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("🟩", fontSize = 15.sp)
                Text(
                    "대화 잔디 캘린더",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0369A1)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "이번 달 ${heatmap.totalDaysInMonth}일 중 ${heatmap.activeDaysCount}일(${heatmap.activeDayPercentage}%) 동안 대화했어요",
                fontSize = 12.sp,
                color = Color(0xFF0284C7)
            )

            // Interactive selected tile tooltip
            AnimatedVisibility(visible = selectedTile != null) {
                selectedTile?.let { tile ->
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = Color(0xFF0284C7),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "📅 ${tile.date}: ${tile.count}건의 대화 ${if (tile.count >= 80) "🔥" else ""}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                IconButton(
                                    onClick = { selectedTile = null },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "닫기",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Days of week header
            val dayLabels = listOf("월", "화", "수", "목", "금", "토", "일")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                dayLabels.forEach { d ->
                    Text(
                        text = d,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Grid of calendar tiles
            val leadingBlanks = (0 until startDayOfWeek).map { null }
            val allCells = leadingBlanks + heatmap.tiles
            val weeks = allCells.chunked(7)

            weeks.forEach { week ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (col in 0 until 7) {
                        val cell = week.getOrNull(col)
                        if (cell == null) {
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val isSelected = selectedTile?.date == cell.date
                            val tileBg = when (cell.level) {
                                0 -> Color(0xFFE2E8F0)
                                1 -> Color(0xFFBAE6FD)
                                2 -> Color(0xFF60A5FA)
                                3 -> Color(0xFF2563EB)
                                else -> Color(0xFF1D4ED8)
                            }
                            val textColor = when {
                                cell.level >= 2 -> Color.White
                                cell.level == 1 -> Color(0xFF0F172A)
                                else -> Color(0xFF94A3B8)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(tileBg)
                                    .then(
                                        if (isSelected) Modifier.border(2.dp, Color(0xFF0F172A), RoundedCornerShape(6.dp))
                                        else Modifier
                                    )
                                    .clickable { selectedTile = cell },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${cell.dayOfMonth}",
                                    fontSize = 9.sp,
                                    fontWeight = if (cell.level > 0) FontWeight.Bold else FontWeight.Normal,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }

            // Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("적음", fontSize = 9.sp, color = Color(0xFF64748B))
                Spacer(modifier = Modifier.width(4.dp))
                val legendColors = listOf(
                    Color(0xFFE2E8F0),
                    Color(0xFFBAE6FD),
                    Color(0xFF60A5FA),
                    Color(0xFF2563EB),
                    Color(0xFF1D4ED8)
                )
                legendColors.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(c)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Spacer(modifier = Modifier.width(2.dp))
                Text("많음", fontSize = 9.sp, color = Color(0xFF64748B))
            }
        }
    }
}
