package com.example.model

data class StoryCardItem(
    val stage: String,    // e.g. "1장: 대화의 시작", "2장: 핵심 안건", "3장: 최종 결론"
    val title: String,    // e.g. "'강남역 모임 조율' 대화의 서막"
    val story: String,    // e.g. "홍길동님: 오늘 저녁 5명이 모여 구체적인 저녁 식사 약속 장소를 정하기 위해 분주하게 대화를 나눴습니다."
    val tag: String,      // e.g. "⚡ 대화 시작", "📌 핵심 안건", "✅ 타결 완료"
    val iconEmoji: String // e.g. "💬", "📍", "🎉"
)

data class TalkStoryResult(
    val chatRoomName: String,
    val dateString: String,
    val card1: StoryCardItem,
    val card2: StoryCardItem,
    val card3: StoryCardItem
)