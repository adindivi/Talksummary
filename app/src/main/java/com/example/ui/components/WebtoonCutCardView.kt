package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.WebtoonCutItem

@Composable
fun WebtoonCutCardView(
    cutItem: WebtoonCutItem,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(cutItem.panelColorStart)
        ),
        border = BorderStroke(2.dp, Color(0xFF0F172A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(cutItem.panelColorStart),
                            Color(cutItem.panelColorEnd)
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Bar: Stage Badge + Pop-Art Sound Effect Sticker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cut Stage Badge (e.g. 1컷 [발단])
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = cutItem.stage,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-0.3).sp
                    )
                }

                // Pop-art Comic Sound Effect Sticker (Tilted with shadow)
                val soundColor = when (cutItem.cutIndex) {
                    1 -> Color(0xFFFEE500) // Warm Yellow
                    2 -> Color(0xFFFF4757) // Dramatic Red
                    else -> Color(0xFF2ED573) // Lively Green
                }
                val soundTextColor = if (cutItem.cutIndex == 2) Color.White else Color(0xFF0F172A)

                Box(
                    modifier = Modifier
                        .graphicsLayer(rotationZ = -7f)
                        .shadow(4.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(soundColor)
                        .border(1.5.dp, Color(0xFF0F172A), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = cutItem.soundEffect,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = soundTextColor,
                        letterSpacing = (-0.2).sp
                    )
                }
            }

            // 2. Cut Title
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = cutItem.title,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    lineHeight = 22.sp,
                    letterSpacing = (-0.4).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 3. Comic Character Scene: Avatar + Speech Bubble
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Character Avatar with Emotion Emoji Badge
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(60.dp)
                ) {
                    Box(
                        modifier = Modifier.size(46.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        // Avatar Circle
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE2E8F0))
                                .border(1.5.dp, Color(0xFF0F172A), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            val initial = cutItem.speaker.firstOrNull()?.toString() ?: "👤"
                            Text(
                                text = initial,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF334155)
                            )
                        }

                        // Overlapping Emotion Emoji Bubble
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.dp, Color(0xFF0F172A), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cutItem.emotionEmoji,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = cutItem.speaker,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Comic Speech Bubble (Pointing towards avatar)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(3.dp, RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                        .background(Color.White)
                        .border(1.8.dp, Color(0xFF0F172A), RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "“${cutItem.speechBubble}”",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A),
                        lineHeight = 19.sp,
                        letterSpacing = (-0.2).sp
                    )
                }
            }

            // 4. Webtoon Narration Box (Bottom Subtitle / Situation)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xE6FFFFFF))
                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "📖",
                        fontSize = 13.sp
                    )
                    Text(
                        text = cutItem.situation,
                        fontSize = 11.5.sp,
                        color = Color(0xFF475569),
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.sp,
                        letterSpacing = (-0.2).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
