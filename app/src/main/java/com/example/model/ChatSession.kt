package com.example.model

data class ChatSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "새로운 대화",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList(),
    val personaPrompt: String? = null
)
