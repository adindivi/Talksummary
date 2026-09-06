package com.example.ui.util

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.example.model.FirstPingAnalysis
import com.example.model.FirstPingLeader
import com.example.model.LinguisticQuirksReport
import com.example.model.MonthlyAnalysisReport
import com.example.model.ParticipantShare
import com.example.model.PeakDayData
import com.example.model.StoryCardItem
import com.example.model.TalkHeatmapData
import com.example.model.TalkStoryResult
import com.example.model.TimeSlotDistribution
import com.example.model.WebtoonCutItem
import com.example.model.WebtoonStoryResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareImageGeneratorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun generateWebtoonStripImage_createsValidPngWith1080Width() {
        val webtoon = WebtoonStoryResult(
            chatRoomName = "모바일 개발팀 단톡방",
            dateString = "2026-09-06",
            cuts = listOf(
                WebtoonCutItem(
                    cutIndex = 1,
                    stage = "1컷 [발단]",
                    title = "금요일 회식 제안",
                    speaker = "철수",
                    emotionEmoji = "🤩",
                    speechBubble = "오늘 불금인데 맛있는 삼겹살에 맥주 어때요?!",
                    soundEffect = "띠링~",
                    situation = "평화로운 금요일 단톡방에 기습 회식 번개가 떨어졌다."
                ),
                WebtoonCutItem(
                    cutIndex = 2,
                    stage = "2컷 [절정]",
                    title = "청천벽력 야근",
                    speaker = "영희",
                    emotionEmoji = "😱",
                    speechBubble = "헐 오늘 프로젝트 배포 이슈 때문에 야근 확정인데요 ㅠㅠ",
                    soundEffect = "두-둥!",
                    situation = "모두가 긴장하며 야근 소식에 숨을 죽였다."
                ),
                WebtoonCutItem(
                    cutIndex = 3,
                    stage = "3컷 [결말]",
                    title = "극적 타결",
                    speaker = "민수",
                    emotionEmoji = "🍻",
                    speechBubble = "그럼 회사 바로 앞 맛집에서 8시 반에 만나죠!",
                    soundEffect = "와아아-!",
                    situation = "센스 넘치는 조율로 즐거운 회식이 성사되었다."
                )
            ),
            isAiGenerated = true
        )

        val imageFile = ShareImageGenerator.generateWebtoonStripImage(context, webtoon)

        assertNotNull(imageFile)
        assertTrue("Generated image file must exist", imageFile.exists())
        assertTrue("Generated image file must not be empty", imageFile.length() > 0)
        assertTrue("File should end with .png", imageFile.name.endsWith(".png"))

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageFile.absolutePath, options)
        assertEquals("Width must be 1080px", 1080, options.outWidth)
        assertTrue("Height must be greater than 1000px", options.outHeight > 1000)
    }

    @Test
    fun generateStoryCardsImage_createsValidPngWith1080Width() {
        val story = TalkStoryResult(
            chatRoomName = "가족 모임 대화방",
            dateString = "2026-09-06",
            card1 = StoryCardItem(
                stage = "1장: 대화의 시작",
                title = "추석 연휴 일정 상의",
                story = "어머니께서 추석 연휴 가족 모임 날짜를 언제로 할지 의견을 물어보셨습니다.",
                tag = "⚡ 대화 시작",
                iconEmoji = "💬"
            ),
            card2 = StoryCardItem(
                stage = "2장: 핵심 안건",
                title = "모임 장소 및 메뉴 조율",
                story = "첫째 날은 본가에서 식사하고 둘째 날은 외식을 하기로 의견이 모아졌습니다.",
                tag = "📌 핵심 안건",
                iconEmoji = "📍"
            ),
            card3 = StoryCardItem(
                stage = "3장: 최종 결론",
                title = "추석 당일 12시 확정",
                story = "모두가 만족하는 일정으로 추석 당일 낮 12시 모임이 최종 확정되었습니다.",
                tag = "✅ 타결 완료",
                iconEmoji = "🎉"
            )
        )

        val imageFile = ShareImageGenerator.generateStoryCardsImage(context, story)

        assertNotNull(imageFile)
        assertTrue("Generated image file must exist", imageFile.exists())
        assertTrue("Generated image file must not be empty", imageFile.length() > 0)

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageFile.absolutePath, options)
        assertEquals("Width must be 1080px", 1080, options.outWidth)
        assertTrue("Height must be greater than 1000px", options.outHeight > 1000)
    }

    @Test
    fun generateAnalysisReportImage_createsValidPngWith1080Width() {
        val report = MonthlyAnalysisReport(
            yearMonthKey = "2026-09",
            displayMonth = "2026년 9월",
            totalMessages = 540,
            daysCount = 7,
            avgDailyMessages = 77,
            participantShares = listOf(
                ParticipantShare("김철수", 270, 50, 1, "수다왕"),
                ParticipantShare("이영희", 162, 30, 2, "조율자"),
                ParticipantShare("박민수", 108, 20, 3, "분위기")
            ),
            peakDay = PeakDayData("2026-09-05", "2026.09.05 (토)", 210, 39, listOf("회식")),
            timeSlotStats = TimeSlotDistribution(
                morningCount = 100,
                afternoonCount = 300,
                eveningCount = 100,
                nightCount = 40,
                morningPercent = 18,
                afternoonPercent = 56,
                eveningPercent = 18,
                nightPercent = 8,
                personaTitle = "점심 직후 커피챗 러버 ☕",
                personaDescription = "오후 1시부터 3시 사이에 대화가 가장 활발했습니다."
            ),
            topKeywords = listOf("회식", "프로젝트"),
            chemistryTitle = "척하면 척, 환상의 티키타카 💫",
            chemistryDescription = "답장 속도가 매우 빠르고 서로의 제안에 적극 호응합니다.",
            firstPingStats = FirstPingAnalysis(
                totalSessions = 10,
                leaders = listOf(FirstPingLeader("김철수", 4, 57)),
                fastestResponder = null,
                slowestResponder = null,
                avgRoomResponseMinutes = 5
            ),
            quirksReport = LinguisticQuirksReport(
                totalLaughCount = 85,
                dominantLaughType = "ㅋㅋㅋㅋ",
                users = emptyList(),
                funFact = "웃음이 끊이지 않는 화기애애한 대화방입니다."
            ),
            heatmapData = TalkHeatmapData(
                year = 2026,
                month = 9,
                totalDaysInMonth = 30,
                activeDaysCount = 7,
                activeDayPercentage = 23,
                tiles = emptyList(),
                maxDayCount = 210
            )
        )

        val imageFile = ShareImageGenerator.generateAnalysisReportImage(context, report)

        assertNotNull(imageFile)
        assertTrue("Generated image file must exist", imageFile.exists())
        assertTrue("Generated image file must not be empty", imageFile.length() > 0)

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageFile.absolutePath, options)
        assertEquals("Width must be 1080px", 1080, options.outWidth)
        assertTrue("Height must be positive", options.outHeight > 1000)
    }
}
