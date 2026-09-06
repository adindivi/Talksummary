package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ChatDay
import com.example.data.parser.GeminiWebtoonEngine
import com.example.model.StoryCardItem
import com.example.model.TalkStoryResult
import com.example.model.WebtoonCutItem
import com.example.model.WebtoonStoryResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class StoryCarouselMode {
    WEBTOON,
    CLASSIC
}

@Composable
fun TalkStoryCarouselDialog(
    storyResult: TalkStoryResult,
    chatDay: ChatDay? = null,
    geminiApiKey: String? = null,
    activeModel: String? = null,
    onDismiss: () -> Unit,
    onShareStory: (TalkStoryResult) -> Unit = {},
    onShareWebtoon: (WebtoonStoryResult) -> Unit = {}
) {
    var currentMode by remember { mutableStateOf(StoryCarouselMode.WEBTOON) }

    // Instant offline webtoon fallback generation (0ms lag)
    var webtoonResult by remember(chatDay, storyResult) {
        mutableStateOf(
            chatDay?.let { GeminiWebtoonEngine.generateOfflineWebtoonStory(it, storyResult.chatRoomName) }
        )
    }
    var isAiGenerating by remember { mutableStateOf(false) }

    // Asynchronous Gemini AI script enhancement if API key is provided
    LaunchedEffect(chatDay, geminiApiKey, activeModel) {
        if (chatDay != null && !geminiApiKey.isNullOrBlank()) {
            isAiGenerating = true
            try {
                val aiScript = GeminiWebtoonEngine.generateWebtoonStory(
                    chatDay = chatDay,
                    roomName = storyResult.chatRoomName,
                    apiKey = geminiApiKey,
                    model = activeModel
                )
                webtoonResult = aiScript
            } catch (_: Exception) {
            } finally {
                isAiGenerating = false
            }
        }
    }

    val cards = remember(storyResult) {
        listOf(storyResult.card1, storyResult.card2, storyResult.card3)
    }
    val webtoonCuts = webtoonResult?.cuts ?: emptyList()

    val totalPages = 3
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { totalPages })
    val scope = rememberCoroutineScope()
    var isAutoPlayActive by remember { mutableStateOf(true) }

    // Auto-advance timer (3.8 seconds per card)
    LaunchedEffect(pagerState.currentPage, isAutoPlayActive) {
        if (isAutoPlayActive) {
            delay(3800)
            val nextPage = (pagerState.currentPage + 1) % totalPages
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (currentMode == StoryCarouselMode.WEBTOON) Color(0xFFFEF3C7) else Color(0xFFEEF2FF)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (currentMode == StoryCarouselMode.WEBTOON) "🎨" else "🎬",
                                fontSize = 16.sp
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (currentMode == StoryCarouselMode.WEBTOON) "3컷 만화 스토리" else "3장 스토리 요약",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                if (currentMode == StoryCarouselMode.WEBTOON) {
                                    if (isAiGenerating) {
                                        Text(
                                            text = "AI 각색 중...",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4F46E5)
                                        )
                                    } else if (webtoonResult?.isAiGenerated == true) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFEEF2FF))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "AI ✨",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF4F46E5)
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                text = "${storyResult.chatRoomName} • ${storyResult.dateString}",
                                fontSize = 10.5.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mode Segmented Switcher (3-Cut Webtoon vs Classic Card)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 3-Cut Webtoon Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (currentMode == StoryCarouselMode.WEBTOON) Color.White else Color.Transparent)
                            .border(
                                width = if (currentMode == StoryCarouselMode.WEBTOON) 1.dp else 0.dp,
                                color = if (currentMode == StoryCarouselMode.WEBTOON) Color(0xFFE2E8F0) else Color.Transparent,
                                shape = RoundedCornerShape(9.dp)
                            )
                            .clickable {
                                isAutoPlayActive = false
                                currentMode = StoryCarouselMode.WEBTOON
                            }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "🎨 3컷 만화",
                                fontSize = 12.sp,
                                fontWeight = if (currentMode == StoryCarouselMode.WEBTOON) FontWeight.Bold else FontWeight.Medium,
                                color = if (currentMode == StoryCarouselMode.WEBTOON) Color(0xFF0F172A) else Color(0xFF64748B)
                            )
                            if (webtoonResult?.isAiGenerated == true) {
                                Text(
                                    text = "AI",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF4F46E5)
                                )
                            }
                        }
                    }

                    // Classic Card Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (currentMode == StoryCarouselMode.CLASSIC) Color.White else Color.Transparent)
                            .border(
                                width = if (currentMode == StoryCarouselMode.CLASSIC) 1.dp else 0.dp,
                                color = if (currentMode == StoryCarouselMode.CLASSIC) Color(0xFFE2E8F0) else Color.Transparent,
                                shape = RoundedCornerShape(9.dp)
                            )
                            .clickable {
                                isAutoPlayActive = false
                                currentMode = StoryCarouselMode.CLASSIC
                            }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🎬 클래식 카드",
                            fontSize = 12.sp,
                            fontWeight = if (currentMode == StoryCarouselMode.CLASSIC) FontWeight.Bold else FontWeight.Medium,
                            color = if (currentMode == StoryCarouselMode.CLASSIC) Color(0xFF0F172A) else Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Page Progress Indicator Bars (3 Bars like Instagram / Toss Story)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (i in 0 until totalPages) {
                        val isActive = i == pagerState.currentPage
                        val isPassed = i < pagerState.currentPage
                        val barActiveColor = if (currentMode == StoryCarouselMode.WEBTOON) Color(0xFF0F172A) else Color(0xFF4F46E5)
                        val barPassedColor = if (currentMode == StoryCarouselMode.WEBTOON) Color(0xFF64748B) else Color(0xFF818CF8)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(
                                    when {
                                        isActive -> barActiveColor
                                        isPassed -> barPassedColor
                                        else -> Color(0xFFE2E8F0)
                                    }
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Carousel Pager (3 Cards Slide)
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) { page ->
                    when (currentMode) {
                        StoryCarouselMode.WEBTOON -> {
                            val cutItem = webtoonCuts.getOrNull(page)
                            if (cutItem != null) {
                                WebtoonCutCardView(cutItem = cutItem)
                            } else if (cards.indices.contains(page)) {
                                StoryCardView(cardItem = cards[page])
                            }
                        }
                        StoryCarouselMode.CLASSIC -> {
                            if (cards.indices.contains(page)) {
                                StoryCardView(cardItem = cards[page])
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Controls: Prev/Next Navigation & Share Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Navigation controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                isAutoPlayActive = false
                                scope.launch {
                                    val prev = if (pagerState.currentPage > 0) pagerState.currentPage - 1 else totalPages - 1
                                    pagerState.animateScrollToPage(prev)
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "이전",
                                tint = Color(0xFF334155),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = "${pagerState.currentPage + 1} / $totalPages",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        IconButton(
                            onClick = {
                                isAutoPlayActive = false
                                scope.launch {
                                    val next = (pagerState.currentPage + 1) % totalPages
                                    pagerState.animateScrollToPage(next)
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "다음",
                                tint = Color(0xFF334155),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Share button
                    Button(
                        onClick = {
                            if (currentMode == StoryCarouselMode.WEBTOON && webtoonResult != null) {
                                onShareWebtoon(webtoonResult!!)
                            } else {
                                onShareStory(storyResult)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentMode == StoryCarouselMode.WEBTOON) Color(0xFF0F172A) else Color(0xFF334155),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "공유",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (currentMode == StoryCarouselMode.WEBTOON) "만화 공유" else "스토리 공유",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StoryCardView(cardItem: StoryCardItem) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Stage & Tag Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cardItem.stage,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFEEF2FF))
                        .border(1.dp, Color(0xFFC7D2FE), RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = cardItem.tag,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4338CA)
                    )
                }
            }

            // Big Emoji & Card Title
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFFEE500), Color(0xFFFFD54F))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = cardItem.iconEmoji, fontSize = 22.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = cardItem.title,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    lineHeight = 22.sp,
                    letterSpacing = (-0.3).sp
                )
            }

            // Story Body Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = cardItem.story,
                    fontSize = 12.5.sp,
                    color = Color(0xFF334155),
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.2).sp
                )
            }
        }
    }
}