package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Message(
    val sender: String,
    val time: String,
    val text: String
)

data class ChatDay(
    val date: String,
    val messages: List<Message>,
    val summary: String,
    val keywords: List<String>,
    val participants: List<String>,
    val msgCount: Int
)
