package com.example.model

data class WebtoonCutItem(
    val cutIndex: Int,
    val stage: String,
    val title: String,
    val speaker: String,
    val emotionEmoji: String,
    val speechBubble: String,
    val soundEffect: String,
    val situation: String,
    val panelColorStart: Long = 0xFFFFFBEB,
    val panelColorEnd: Long = 0xFFFEF3C7
)

data class WebtoonStoryResult(
    val chatRoomName: String,
    val dateString: String,
    val cuts: List<WebtoonCutItem>,
    val isAiGenerated: Boolean = false
)
