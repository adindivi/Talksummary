package com.example.data.parser

import com.example.data.ChatDay
import com.example.model.StoryCardItem
import com.example.model.TalkStoryResult

object StoryGenerator {

    /**
     * 100% 실제 대화 원문 기반 스마트 맥락 자연어 3-Card 스토리 생성기
     * - 중복 픽업(Pick) 100% 원천 방지 (Distinct Filter)
     * - 발언자 중복 및 특수문자/이모티콘 찌꺼기 정제
     * - 온디바이스 완전 오프라인 무지연 즉시 실행
     */
    fun generate3CardStory(chatDay: ChatDay, roomName: String? = null): TalkStoryResult {
        val chatRoomTitle = if (!roomName.isNullOrBlank()) {
            roomName
        } else if (chatDay.participants.isNotEmpty()) {
            val names = chatDay.participants.take(3).joinToString(", ")
            if (chatDay.participants.size > 3) "$names 외 대화방" else "$names 대화방"
        } else {
            "카카오톡 대화방"
        }

        val rawMessagesList = mutableListOf<Pair<String, String>>()
        val senderFrequencyMap = mutableMapOf<String, Int>()

        for (msg in chatDay.messages) {
            var sender = msg.sender.trim()
            if (sender.endsWith("님님")) {
                sender = sender.substring(0, sender.length - 1)
            }
            val text = msg.text.trim()

            if (text.isNotBlank() &&
                !text.contains("사진") &&
                !text.contains("이모티콘") &&
                !text.contains("동영상") &&
                !text.contains("음성메시지") &&
                !text.contains("파일") &&
                text.length > 2
            ) {
                rawMessagesList.add(Pair(sender, text))
                senderFrequencyMap[sender] = (senderFrequencyMap[sender] ?: 0) + 1
            }
        }

        val meaningfulMessages = rawMessagesList.filter { (_, text) ->
            val clean = text.replace(Regex("@[^\\s]+"), "").trim()
            clean.length > 2 && !clean.matches(Regex("^(ㅋㅋ+|ㅎㅎ+|네+|응+|아+|오케이|알겠어|안녕|수고|감사|ㅇㅇ+|ㄷㄷ+|ㅠㅠ+|ㅜㅜ+).*"))
        }

        val rawTopSender = senderFrequencyMap.maxByOrNull { it.value }?.key 
            ?: chatDay.participants.firstOrNull() 
            ?: "참여자"
        val topSender = if (rawTopSender.endsWith("님님")) rawTopSender.substring(0, rawTopSender.length - 1) else rawTopSender

        fun formatStoryText(pair: Pair<String, String>?, fallback: String): String {
            if (pair == null) return fallback
            val sender = pair.first
            val rawText = pair.second
                .replace(Regex("@[^\\s]+"), "")
                .replace(Regex("^[^:]+:\\s*"), "")
                .trim()
            val cleanText = if (rawText.length > 60) rawText.substring(0, 60) + "..." else rawText
            return "${sender}님: $cleanText"
        }

        // 1번(시작), 2번(안건), 3번(결론) 중복 선택 100% 원천 방지 (Distinct Pick)
        val startPair = meaningfulMessages.firstOrNull() ?: rawMessagesList.firstOrNull()

        val remainingForAgenda = meaningfulMessages.filterNot { it == startPair }
        val agendaPair = remainingForAgenda.find {
            val t = it.second
            t.contains("?") || t.contains("어때") || t.contains("일정") || 
            t.contains("약속") || t.contains("시간") || t.contains("장소") || 
            t.contains("회의") || t.contains("확인")
        } ?: remainingForAgenda.maxByOrNull { it.second.length }
          ?: rawMessagesList.filterNot { it == startPair }.getOrNull(rawMessagesList.size / 2)

        val remainingForConclusion = (meaningfulMessages + rawMessagesList)
            .distinct()
            .filterNot { it == startPair || it == agendaPair }
        val conclusionPair = remainingForConclusion.lastOrNull()

        val startMessage = formatStoryText(startPair, "대화가 활발하게 시작되었습니다.")
        val keyHighlightMessage = formatStoryText(agendaPair, "${topSender}님을 중심으로 주요 안건을 교환했습니다.")
        val conclusionMessage = formatStoryText(conclusionPair, "오늘의 모든 대화가 원활하게 마무리되었습니다.")

        val card1 = StoryCardItem(
            stage = "1장: 대화의 시작",
            title = "'$chatRoomTitle' 대화의 서막",
            story = startMessage,
            tag = "⚡ 스마트 맥락 추출",
            iconEmoji = "💬"
        )

        val card2 = StoryCardItem(
            stage = "2장: 핵심 안건",
            title = "${topSender}님 중심 주요 소통 안건",
            story = keyHighlightMessage,
            tag = "📌 핵심 하이라이트",
            iconEmoji = "📍"
        )

        val card3 = StoryCardItem(
            stage = "3장: 최종 결론",
            title = "대화 마무리 및 최종 타결",
            story = conclusionMessage,
            tag = "🎉 소통 완료",
            iconEmoji = "🎉"
        )

        return TalkStoryResult(
            chatRoomName = chatRoomTitle,
            dateString = chatDay.date,
            card1 = card1,
            card2 = card2,
            card3 = card3
        )
    }
}