package com.example.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.model.MonthlyAnalysisReport
import com.example.model.StoryCardItem
import com.example.model.TalkStoryResult
import com.example.model.WebtoonCutItem
import com.example.model.WebtoonStoryResult
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max

/**
 * High-resolution (1080px width) offscreen Canvas & Bitmap image generator for sharing.
 * Renders 3-cut webtoons, 3-card stories, and monthly analysis reports into crisp PNG posters
 * that can be shared directly as photos via KakaoTalk.
 */
object ShareImageGenerator {

    private const val POSTER_WIDTH = 1080
    private const val PADDING_X = 54f
    private const val CONTENT_WIDTH = POSTER_WIDTH - (PADDING_X * 2)

    private fun getSharedImagesDir(context: Context): File {
        val dir = File(context.cacheDir, "shared_images")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        cleanupOldImages(dir)
        return dir
    }

    private fun cleanupOldImages(dir: File) {
        try {
            val files = dir.listFiles() ?: return
            val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
            for (file in files) {
                if (file.isFile && file.lastModified() < oneDayAgo) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    private fun saveBitmapToFile(context: Context, bitmap: Bitmap, prefix: String): File {
        val dir = getSharedImagesDir(context)
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    // ---------------------------------------------------------------------------------------------
    // 1. 3컷 웹툰 스트립 이미지 생성 (generateWebtoonStripImage)
    // ---------------------------------------------------------------------------------------------
    fun generateWebtoonStripImage(context: Context, webtoon: WebtoonStoryResult): File {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            typeface = Typeface.DEFAULT_BOLD
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1st Pass: Dynamic Measurement of total height
        var currentY = 56f // Top padding

        // Header height: Badge(38) + Title(~56) + Subtitle(~36) + Spacers
        val headerHeight = 160f
        currentY += headerHeight

        // Measure each cut
        val cutHeights = mutableListOf<Float>()
        val cuts = if (webtoon.cuts.isNotEmpty()) webtoon.cuts.take(3) else emptyList()
        val panelInnerWidth = CONTENT_WIDTH - 56f
        val bubbleWidth = panelInnerWidth - 110f // Avatar(80) + gap(30)

        for (cut in cuts) {
            val speechPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 33f
                typeface = Typeface.DEFAULT_BOLD
            }
            val speechHeight = measureMultilineText(cut.speechBubble, bubbleWidth.toInt() - 40, speechPaint)

            val narrPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 27f
                typeface = Typeface.DEFAULT
            }
            val narrHeight = measureMultilineText("📖 ${cut.situation}", panelInnerWidth.toInt() - 48, narrPaint)

            // Panel height = TopBar(64) + spacing(18) + max(Avatar+Name(90), SpeechBubble(speechHeight+48)) + spacing(20) + Narration(narrHeight+40) + bottomPadding(32)
            val bubbleBoxHeight = max(90f, speechHeight + 48f)
            val panelHeight = 32f + 50f + 20f + bubbleBoxHeight + 20f + narrHeight + 36f + 28f
            cutHeights.add(panelHeight)
            currentY += panelHeight + 36f // panel + gap
        }

        val footerHeight = 110f
        currentY += footerHeight

        val totalHeight = currentY.toInt()
        val bitmap = Bitmap.createBitmap(POSTER_WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw Background (Soft slate 50)
        canvas.drawColor(Color.parseColor("#F8FAFC"))

        // Draw Header
        var drawY = 56f
        // Capsule Badge
        drawPillBadge(canvas, "🎨 3컷 웹툰 요약", PADDING_X, drawY, Color.parseColor("#0F172A"), Color.WHITE, 24f)
        drawY += 56f

        // Chat room title
        textPaint.textSize = 42f
        textPaint.color = Color.parseColor("#0F172A")
        canvas.drawText(webtoon.chatRoomName, PADDING_X, drawY + 36f, textPaint)
        drawY += 56f

        // Subtitle & AI tag
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 27f
            color = Color.parseColor("#64748B")
            typeface = Typeface.DEFAULT
        }
        val aiLabel = if (webtoon.isAiGenerated) "제미나이 AI 각색 ✨" else "스마트 만화 요약"
        val subtitleText = "${webtoon.dateString} • $aiLabel"
        canvas.drawText(subtitleText, PADDING_X, drawY + 24f, subPaint)
        drawY += 48f

        // Draw Panels
        for (i in cuts.indices) {
            val cut = cuts[i]
            val panelHeight = cutHeights[i]
            val panelRect = RectF(PADDING_X, drawY, PADDING_X + CONTENT_WIDTH, drawY + panelHeight)

            // Panel Background Fill (Pastel gradient)
            val startColor = cut.panelColorStart.toInt()
            val endColor = cut.panelColorEnd.toInt()
            val gradientShader = LinearGradient(
                panelRect.left, panelRect.top,
                panelRect.left, panelRect.bottom,
                startColor, endColor,
                Shader.TileMode.CLAMP
            )
            paint.shader = gradientShader
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(panelRect, 32f, 32f, paint)
            paint.shader = null

            // Panel Ink Border (Dark Slate 4px)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = Color.parseColor("#0F172A")
            canvas.drawRoundRect(panelRect, 32f, 32f, paint)

            var innerY = drawY + 28f
            val innerX = PADDING_X + 28f

            // Top Row of Panel: Stage Badge (Left) + PopArt Sound Effect Sticker (Right)
            drawPillBadge(canvas, cut.stage, innerX, innerY, Color.parseColor("#0F172A"), Color.WHITE, 23f)

            // PopArt Sound Effect Sticker (Right-aligned slanted badge)
            if (cut.soundEffect.isNotBlank()) {
                val soundText = "💥 ${cut.soundEffect}"
                val stickerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 25f
                    typeface = Typeface.DEFAULT_BOLD
                    color = Color.parseColor("#991B1B") // Dark Red
                }
                val stickerTextWidth = stickerPaint.measureText(soundText)
                val stickerBoxWidth = stickerTextWidth + 34f
                val stickerRight = PADDING_X + CONTENT_WIDTH - 28f
                val stickerLeft = stickerRight - stickerBoxWidth
                val stickerRect = RectF(stickerLeft, innerY - 2f, stickerRight, innerY + 44f)

                canvas.save()
                canvas.rotate(-4f, stickerRect.centerX(), stickerRect.centerY())
                // Sticker fill
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#FEF08A") // Bright Yellow
                canvas.drawRoundRect(stickerRect, 14f, 14f, paint)
                // Sticker border
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.color = Color.parseColor("#991B1B")
                canvas.drawRoundRect(stickerRect, 14f, 14f, paint)
                // Sticker text
                canvas.drawText(soundText, stickerLeft + 17f, innerY + 30f, stickerPaint)
                canvas.restore()
            }

            innerY += 66f

            // Character Avatar + Speech Bubble Row
            val avatarRadius = 38f
            val avatarCenterX = innerX + avatarRadius
            val avatarCenterY = innerY + avatarRadius

            // Avatar circle with dynamic user hash color
            drawAvatar(canvas, cut.speaker, avatarCenterX, avatarCenterY, avatarRadius)

            // Emotion Emoji badge overlapping avatar bottom-right
            if (cut.emotionEmoji.isNotBlank()) {
                val emojiPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28f }
                canvas.drawText(cut.emotionEmoji, avatarCenterX + 12f, avatarCenterY + 36f, emojiPaint)
            }

            // Speech Bubble Box
            val bubbleLeft = innerX + (avatarRadius * 2) + 24f
            val bubbleRight = PADDING_X + CONTENT_WIDTH - 28f
            val bubbleSpeechPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 32f
                color = Color.parseColor("#0F172A")
                typeface = Typeface.DEFAULT_BOLD
            }
            val bubbleTextWidth = (bubbleRight - bubbleLeft - 44f).toInt()
            val textHeight = measureMultilineText(cut.speechBubble, bubbleTextWidth, bubbleSpeechPaint)
            val bubbleHeight = max(90f, textHeight + 48f)
            val bubbleRect = RectF(bubbleLeft, innerY, bubbleRight, innerY + bubbleHeight)

            // Speech Bubble background (Crisp White)
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawRoundRect(bubbleRect, 22f, 22f, paint)

            // Speech Bubble Ink Border
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3.5f
            paint.color = Color.parseColor("#0F172A")
            canvas.drawRoundRect(bubbleRect, 22f, 22f, paint)

            // Bubble pointer tail towards avatar
            val tailPath = Path().apply {
                moveTo(bubbleLeft + 2f, innerY + 28f)
                lineTo(bubbleLeft - 16f, innerY + 38f)
                lineTo(bubbleLeft + 2f, innerY + 48f)
                close()
            }
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawPath(tailPath, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3.5f
            paint.color = Color.parseColor("#0F172A")
            val tailBorder = Path().apply {
                moveTo(bubbleLeft + 1f, innerY + 28f)
                lineTo(bubbleLeft - 16f, innerY + 38f)
                lineTo(bubbleLeft + 1f, innerY + 48f)
            }
            canvas.drawPath(tailBorder, paint)

            // Bubble Text
            drawMultilineText(canvas, cut.speechBubble, bubbleLeft + 22f, innerY + 24f, bubbleTextWidth, bubbleSpeechPaint)

            innerY += bubbleHeight + 20f

            // Narration Caption Box (Bottom banner)
            val narrRect = RectF(innerX, innerY, PADDING_X + CONTENT_WIDTH - 28f, panelRect.bottom - 24f)
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#0F172A")
            canvas.drawRoundRect(narrRect, 16f, 16f, paint)

            val narrTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 27f
                color = Color.parseColor("#F8FAFC")
                typeface = Typeface.DEFAULT
            }
            val narrText = "📖  ${cut.situation}"
            drawMultilineText(canvas, narrText, innerX + 22f, innerY + 18f, (narrRect.width() - 44f).toInt(), narrTextPaint)

            drawY += panelHeight + 36f
        }

        // Draw Footer
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#E2E8F0")
        canvas.drawLine(PADDING_X, drawY + 10f, PADDING_X + CONTENT_WIDTH, drawY + 10f, paint)

        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 25f
            color = Color.parseColor("#94A3B8")
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("TalkSummary • 카카오톡 대화 3컷 웹툰 요약", POSTER_WIDTH / 2f, drawY + 60f, footerPaint)

        return saveBitmapToFile(context, bitmap, "webtoon_strip")
    }

    // ---------------------------------------------------------------------------------------------
    // 2. 3장 스토리 요약 카드 이미지 생성 (generateStoryCardsImage)
    // ---------------------------------------------------------------------------------------------
    fun generateStoryCardsImage(context: Context, story: TalkStoryResult): File {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cards = listOf(story.card1, story.card2, story.card3)
        val cardInnerWidth = CONTENT_WIDTH - 56f

        // 1st Pass: Measure total height
        var currentY = 56f
        val headerHeight = 160f
        currentY += headerHeight

        val cardHeights = mutableListOf<Float>()
        for (card in cards) {
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 34f
                typeface = Typeface.DEFAULT_BOLD
            }
            val titleHeight = measureMultilineText(card.title, cardInnerWidth.toInt() - 100, titlePaint)

            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 28f
                typeface = Typeface.DEFAULT
            }
            val bodyHeight = measureMultilineText(card.story, cardInnerWidth.toInt() - 48, bodyPaint)

            val cardHeight = 32f + 46f + 16f + max(60f, titleHeight.toFloat()) + 24f + bodyHeight + 36f + 32f
            cardHeights.add(cardHeight)
            currentY += cardHeight + 32f
        }

        val footerHeight = 110f
        currentY += footerHeight

        val totalHeight = currentY.toInt()
        val bitmap = Bitmap.createBitmap(POSTER_WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(Color.parseColor("#F8FAFC"))

        // Header
        var drawY = 56f
        drawPillBadge(canvas, "🎬 3장 스토리 요약", PADDING_X, drawY, Color.parseColor("#4338CA"), Color.WHITE, 24f)
        drawY += 56f

        textPaint.textSize = 42f
        textPaint.color = Color.parseColor("#0F172A")
        canvas.drawText(story.chatRoomName, PADDING_X, drawY + 36f, textPaint)
        drawY += 56f

        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 27f
            color = Color.parseColor("#64748B")
            typeface = Typeface.DEFAULT
        }
        canvas.drawText("${story.dateString} • 핵심 대화 3단계 요약", PADDING_X, drawY + 24f, subPaint)
        drawY += 48f

        // Draw 3 Cards
        for (i in cards.indices) {
            val card = cards[i]
            val cardHeight = cardHeights[i]
            val cardRect = RectF(PADDING_X, drawY, PADDING_X + CONTENT_WIDTH, drawY + cardHeight)

            // Card Background (Pure White)
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawRoundRect(cardRect, 28f, 28f, paint)

            // Card Border (Soft Slate)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = Color.parseColor("#E2E8F0")
            canvas.drawRoundRect(cardRect, 28f, 28f, paint)

            var innerY = drawY + 28f
            val innerX = PADDING_X + 28f

            // Stage Label (Left)
            val stagePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 26f
                color = Color.parseColor("#64748B")
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText(card.stage, innerX, innerY + 26f, stagePaint)

            // Tag Badge (Right)
            if (card.tag.isNotBlank()) {
                val tagRight = PADDING_X + CONTENT_WIDTH - 28f
                drawPillBadgeRightAligned(canvas, card.tag, tagRight, innerY, Color.parseColor("#EEF2FF"), Color.parseColor("#4338CA"), 22f)
            }

            innerY += 56f

            // Emoji icon circle + Title Row
            val emojiRadius = 36f
            val emojiCenterX = innerX + emojiRadius
            val emojiCenterY = innerY + emojiRadius

            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#FEF3C7") // Soft Amber
            canvas.drawCircle(emojiCenterX, emojiCenterY, emojiRadius, paint)

            val emojiPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 34f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(card.iconEmoji, emojiCenterX, emojiCenterY + 12f, emojiPaint)

            // Card Title
            val titleLeft = innerX + (emojiRadius * 2) + 20f
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 33f
                color = Color.parseColor("#0F172A")
                typeface = Typeface.DEFAULT_BOLD
            }
            val titleWidth = (PADDING_X + CONTENT_WIDTH - 28f - titleLeft).toInt()
            val drawnTitleHeight = drawMultilineText(canvas, card.title, titleLeft, innerY + 8f, titleWidth, titlePaint)

            innerY += max(emojiRadius * 2, drawnTitleHeight.toFloat()) + 20f

            // Story Body Container
            val bodyRect = RectF(innerX, innerY, PADDING_X + CONTENT_WIDTH - 28f, cardRect.bottom - 24f)
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#F8FAFC")
            canvas.drawRoundRect(bodyRect, 18f, 18f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.parseColor("#E2E8F0")
            canvas.drawRoundRect(bodyRect, 18f, 18f, paint)

            val storyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 28f
                color = Color.parseColor("#334155")
                typeface = Typeface.DEFAULT
            }
            drawMultilineText(canvas, card.story, innerX + 22f, innerY + 20f, (bodyRect.width() - 44f).toInt(), storyPaint)

            drawY += cardHeight + 32f
        }

        // Draw Footer
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#E2E8F0")
        canvas.drawLine(PADDING_X, drawY + 10f, PADDING_X + CONTENT_WIDTH, drawY + 10f, paint)

        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 25f
            color = Color.parseColor("#94A3B8")
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("TalkSummary • 카카오톡 대화 스토리 요약", POSTER_WIDTH / 2f, drawY + 60f, footerPaint)

        return saveBitmapToFile(context, bitmap, "story_cards")
    }

    // ---------------------------------------------------------------------------------------------
    // 3. 대화 분석 리포트 인포그래픽 이미지 생성 (generateAnalysisReportImage)
    // ---------------------------------------------------------------------------------------------
    fun generateAnalysisReportImage(context: Context, report: MonthlyAnalysisReport): File {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
        }

        // Fixed high quality layout height
        val totalHeight = 2160
        val bitmap = Bitmap.createBitmap(POSTER_WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(Color.parseColor("#F8FAFC"))

        var drawY = 56f

        // Header Card
        drawPillBadge(canvas, "📊 카톡 대화 심층 분석 리포트", PADDING_X, drawY, Color.parseColor("#2563EB"), Color.WHITE, 24f)
        drawY += 56f

        textPaint.textSize = 44f
        textPaint.color = Color.parseColor("#0F172A")
        canvas.drawText(report.displayMonth, PADDING_X, drawY + 38f, textPaint)
        drawY += 56f

        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f
            color = Color.parseColor("#64748B")
            typeface = Typeface.DEFAULT
        }
        val subText = "총 ${report.daysCount}일간 ${report.totalMessages}건의 대화 분석 결과"
        canvas.drawText(subText, PADDING_X, drawY + 24f, subPaint)
        drawY += 52f

        // Section 1: 이 달의 발언 랭킹 (Top 3 Podium Card)
        val rankingCardHeight = 440f
        val rankingRect = RectF(PADDING_X, drawY, PADDING_X + CONTENT_WIDTH, drawY + rankingCardHeight)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawRoundRect(rankingRect, 28f, 28f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.parseColor("#E2E8F0")
        canvas.drawRoundRect(rankingRect, 28f, 28f, paint)

        // Ranking Card Title
        var rankInnerY = drawY + 28f
        val rankInnerX = PADDING_X + 28f

        textPaint.textSize = 30f
        textPaint.color = Color.parseColor("#0F172A")
        canvas.drawText("🏆 이 달의 발언 랭킹", rankInnerX, rankInnerY + 26f, textPaint)
        rankInnerY += 54f

        val topShares = report.participantShares.take(3)
        val medals = listOf("🥇", "🥈", "🥉")
        val barColors = listOf("#F59E0B", "#64748B", "#B45309")

        for (i in topShares.indices) {
            val share = topShares[i]
            val rowY = rankInnerY + (i * 110f)

            // Medal
            val medalPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f }
            val medal = if (i < medals.size) medals[i] else "⚡"
            canvas.drawText(medal, rankInnerX, rowY + 36f, medalPaint)

            // Avatar circle
            val avatarCx = rankInnerX + 80f
            val avatarCy = rowY + 26f
            drawAvatar(canvas, share.name, avatarCx, avatarCy, 30f)

            // Name
            val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 30f
                color = Color.parseColor("#0F172A")
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText(share.name, rankInnerX + 130f, rowY + 36f, namePaint)

            // Badge
            if (share.badge.isNotBlank()) {
                val badgeX = rankInnerX + 380f
                drawPillBadge(canvas, share.badge, badgeX, rowY + 8f, Color.parseColor("#FEF3C7"), Color.parseColor("#92400E"), 21f)
            }

            // Count & Percentage (Right aligned)
            val statsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 27f
                color = Color.parseColor("#334155")
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.RIGHT
            }
            val statsText = "${share.count}건 (${share.percentage}%)"
            canvas.drawText(statsText, PADDING_X + CONTENT_WIDTH - 28f, rowY + 36f, statsPaint)

            // Progress Bar
            val barLeft = rankInnerX + 130f
            val barRight = PADDING_X + CONTENT_WIDTH - 28f
            val barWidth = barRight - barLeft
            val barTop = rowY + 56f
            val barBottom = barTop + 14f

            // Progress Background
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#F1F5F9")
            canvas.drawRoundRect(RectF(barLeft, barTop, barRight, barBottom), 7f, 7f, paint)

            // Progress Fill
            val fillWidth = (barWidth * (share.percentage / 100f)).coerceIn(12f, barWidth)
            paint.color = Color.parseColor(barColors.getOrElse(i) { "#3B82F6" })
            canvas.drawRoundRect(RectF(barLeft, barTop, barLeft + fillWidth, barBottom), 7f, 7f, paint)
        }

        drawY += rankingCardHeight + 32f

        // Section 2: 핵심 지표 2x2 그리드 (Highlights Grid)
        val gridCardWidth = (CONTENT_WIDTH - 24f) / 2f
        val gridCardHeight = 220f

        val cellA = RectF(PADDING_X, drawY, PADDING_X + gridCardWidth, drawY + gridCardHeight)
        val cellB = RectF(PADDING_X + gridCardWidth + 24f, drawY, PADDING_X + CONTENT_WIDTH, drawY + gridCardHeight)
        val cellC = RectF(PADDING_X, drawY + gridCardHeight + 20f, PADDING_X + gridCardWidth, drawY + (gridCardHeight * 2) + 20f)
        val cellD = RectF(PADDING_X + gridCardWidth + 24f, drawY + gridCardHeight + 20f, PADDING_X + CONTENT_WIDTH, drawY + (gridCardHeight * 2) + 20f)

        // Cell A: 🔥 가장 뜨거웠던 날
        val peakDate = report.peakDay?.displayDate ?: "기록 없음"
        val peakStats = report.peakDay?.let { "${it.messageCount}건 (${it.percentageOfTotal}%)" } ?: "-"
        drawMetricCard(canvas, cellA, "🔥 가장 뜨거웠던 날", peakDate, peakStats)

        // Cell B: ⚡ 선톡 장인
        val pingLeader = report.firstPingStats?.leaders?.firstOrNull()
        val pingName = pingLeader?.name ?: "기록 없음"
        val pingStats = pingLeader?.let { "${it.pingCount}회 (${it.pingPercentage}%)" } ?: "-"
        drawMetricCard(canvas, cellB, "⚡ 선톡 장인", pingName, pingStats)

        // Cell C: 😂 웃음 타입
        val laughType = report.quirksReport?.dominantLaughType ?: "스마일"
        val laughCount = report.quirksReport?.let { "총 ${it.totalLaughCount}회" } ?: "0회"
        drawMetricCard(canvas, cellC, "😂 웃음 타입", laughType, laughCount)

        // Cell D: 🟩 대화 잔디
        val activeDays = report.heatmapData?.let { "${it.activeDaysCount} / ${it.totalDaysInMonth}일" } ?: "0일"
        val attendPct = report.heatmapData?.let { "출석률 ${it.activeDayPercentage}%" } ?: "0%"
        drawMetricCard(canvas, cellD, "🟩 대화 잔디", activeDays, attendPct)

        drawY += (gridCardHeight * 2) + 52f

        // Section 3: 대화 골든타임 & 케미 카드
        val insightCardHeight = 440f
        val insightRect = RectF(PADDING_X, drawY, PADDING_X + CONTENT_WIDTH, drawY + insightCardHeight)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawRoundRect(insightRect, 28f, 28f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.parseColor("#E2E8F0")
        canvas.drawRoundRect(insightRect, 28f, 28f, paint)

        var insightY = drawY + 28f
        val insightX = PADDING_X + 28f

        // Golden Time
        val goldenTitle = "⏰ ${report.timeSlotStats.personaTitle}"
        textPaint.textSize = 30f
        textPaint.color = Color.parseColor("#0F172A")
        canvas.drawText(goldenTitle, insightX, insightY + 26f, textPaint)
        insightY += 44f

        val descPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 26f
            color = Color.parseColor("#475569")
            typeface = Typeface.DEFAULT
        }
        val maxTextWidth = (CONTENT_WIDTH - 56f).toInt()
        val goldenDescHeight = drawMultilineText(canvas, report.timeSlotStats.personaDescription, insightX, insightY, maxTextWidth, descPaint)
        insightY += goldenDescHeight + 36f

        // Subtle Divider
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#F1F5F9")
        canvas.drawLine(insightX, insightY, PADDING_X + CONTENT_WIDTH - 28f, insightY, paint)
        insightY += 28f

        // Chemistry
        val chemTitle = "💫 ${report.chemistryTitle}"
        textPaint.textSize = 30f
        textPaint.color = Color.parseColor("#0F172A")
        canvas.drawText(chemTitle, insightX, insightY + 26f, textPaint)
        insightY += 44f

        drawMultilineText(canvas, report.chemistryDescription, insightX, insightY, maxTextWidth, descPaint)

        drawY += insightCardHeight + 40f

        // Footer
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 25f
            color = Color.parseColor("#94A3B8")
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("TalkSummary • 카카오톡 대화 분석 리포트", POSTER_WIDTH / 2f, drawY + 50f, footerPaint)

        return saveBitmapToFile(context, bitmap, "analysis_report")
    }

    // ---------------------------------------------------------------------------------------------
    // Helper Drawing Functions
    // ---------------------------------------------------------------------------------------------
    private fun drawMetricCard(canvas: Canvas, rect: RectF, header: String, mainVal: String, subVal: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Card Fill
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawRoundRect(rect, 24f, 24f, paint)

        // Card Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.parseColor("#E2E8F0")
        canvas.drawRoundRect(rect, 24f, 24f, paint)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
        }

        // Header Title
        textPaint.textSize = 24f
        textPaint.color = Color.parseColor("#64748B")
        canvas.drawText(header, rect.left + 22f, rect.top + 42f, textPaint)

        // Main Value
        textPaint.textSize = 32f
        textPaint.color = Color.parseColor("#0F172A")
        val safeMain = if (mainVal.length > 10) mainVal.take(9) + "…" else mainVal
        canvas.drawText(safeMain, rect.left + 22f, rect.top + 98f, textPaint)

        // Sub Value
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f
            color = Color.parseColor("#2563EB")
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(subVal, rect.left + 22f, rect.top + 148f, subPaint)
    }

    private fun drawPillBadge(canvas: Canvas, text: String, x: Float, y: Float, bgColor: Int, textColor: Int, textSize: Float) {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            this.color = textColor
            typeface = Typeface.DEFAULT_BOLD
        }
        val textWidth = textPaint.measureText(text)
        val pillWidth = textWidth + 28f
        val pillHeight = textSize * 1.8f
        val rect = RectF(x, y, x + pillWidth, y + pillHeight)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = bgColor
        }
        canvas.drawRoundRect(rect, pillHeight / 2f, pillHeight / 2f, paint)
        canvas.drawText(text, x + 14f, y + (pillHeight * 0.72f), textPaint)
    }

    private fun drawPillBadgeRightAligned(canvas: Canvas, text: String, rightX: Float, y: Float, bgColor: Int, textColor: Int, textSize: Float) {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            this.color = textColor
            typeface = Typeface.DEFAULT_BOLD
        }
        val textWidth = textPaint.measureText(text)
        val pillWidth = textWidth + 28f
        val leftX = rightX - pillWidth
        val pillHeight = textSize * 1.8f
        val rect = RectF(leftX, y, rightX, y + pillHeight)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = bgColor
        }
        canvas.drawRoundRect(rect, pillHeight / 2f, pillHeight / 2f, paint)
        canvas.drawText(text, leftX + 14f, y + (pillHeight * 0.72f), textPaint)
    }

    private fun drawAvatar(canvas: Canvas, name: String, cx: Float, cy: Float, radius: Float) {
        val (bgColor, textColor) = getAvatarColors(name)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = bgColor
        }
        canvas.drawCircle(cx, cy, radius, paint)

        // Border around avatar
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#CBD5E1")
        canvas.drawCircle(cx, cy, radius, paint)

        // Initial letter
        val initial = if (name.isNotBlank()) name.take(1) else "?"
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = radius * 1.05f
            color = textColor
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(initial, cx, cy + (radius * 0.38f), textPaint)
    }

    private fun getAvatarColors(name: String): Pair<Int, Int> {
        val palette = listOf(
            Pair(Color.parseColor("#E3F2FD"), Color.parseColor("#1565C0")),
            Pair(Color.parseColor("#E8F5E9"), Color.parseColor("#2E7D32")),
            Pair(Color.parseColor("#FFF3E0"), Color.parseColor("#E65100")),
            Pair(Color.parseColor("#F3E5F5"), Color.parseColor("#7B1FA2")),
            Pair(Color.parseColor("#FFEBEE"), Color.parseColor("#C62828")),
            Pair(Color.parseColor("#E0F7FA"), Color.parseColor("#00838F")),
            Pair(Color.parseColor("#FFF8E1"), Color.parseColor("#F57F17")),
            Pair(Color.parseColor("#F0F4C3"), Color.parseColor("#827717")),
            Pair(Color.parseColor("#E8EAF6"), Color.parseColor("#283593")),
            Pair(Color.parseColor("#FCE4EC"), Color.parseColor("#AD1457"))
        )
        if (name.isBlank()) return Pair(Color.parseColor("#F1F5F9"), Color.parseColor("#475569"))
        var hash = 0
        for (char in name) {
            hash = char.code + ((hash shl 5) - hash)
        }
        val index = abs(hash) % palette.size
        return palette[index]
    }

    private fun measureMultilineText(text: String, width: Int, textPaint: TextPaint): Int {
        if (text.isEmpty() || width <= 0) return 0
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(6f, 1.25f)
            .setIncludePad(false)
            .build()
        return layout.height
    }

    private fun drawMultilineText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Int,
        textPaint: TextPaint
    ): Int {
        if (text.isEmpty() || width <= 0) return 0
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(6f, 1.25f)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height
    }
}
