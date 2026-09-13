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
 * Clean Kakao-themed 3-Step Guide Card with mathematically centered digit badges
 * and perfectly aligned Row hierarchy.
 */
@Composable
fun GuideStepCard(
    stepNum: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
            .border(0.8.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(Color(0xFFFEE500), CircleShape)
                    .border(0.8.dp, Color(0xFFE2D800), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNum,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    textAlign = TextAlign.Center,
                    lineHeight = 11.sp,
                    style = LocalTextStyle.current.copy(
                        platformStyle = @Suppress("DEPRECATION") PlatformTextStyle(
                            includeFontPadding = false
                        ),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both
                        )
                    ),
                    modifier = if (stepNum == "1") Modifier.offset(x = 0.5.dp) else Modifier
                )
            }
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = BrandSlate
            )
        }
        Text(
            text = description,
            fontSize = 10.5.sp,
            lineHeight = 14.5.sp,
            color = Color(0xFF475569),
            modifier = Modifier.padding(start = 28.dp)
        )
    }
}
