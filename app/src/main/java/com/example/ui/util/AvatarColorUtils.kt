package com.example.ui.util

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * 참여자별 고유 파스텔 아바타 컬러 시스템
 * 발언자의 이름 해시값을 기반으로 부드러운 10종 파스텔 배경색과 시인성 높은 텍스트 색상의 듀오 페어를 반환합니다.
 */
object AvatarColorUtils {

    private val avatarPalette = listOf(
        Pair(Color(0xFFE3F2FD), Color(0xFF1565C0)), // Soft Blue
        Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32)), // Soft Green
        Pair(Color(0xFFFFF3E0), Color(0xFFE65100)), // Soft Orange
        Pair(Color(0xFFF3E5F5), Color(0xFF7B1FA2)), // Soft Purple
        Pair(Color(0xFFFFEBEE), Color(0xFFC62828)), // Soft Red
        Pair(Color(0xFFE0F7FA), Color(0xFF00838F)), // Soft Cyan
        Pair(Color(0xFFFFF8E1), Color(0xFFF57F17)), // Soft Amber
        Pair(Color(0xFFF0F4C3), Color(0xFF827717)), // Soft Lime
        Pair(Color(0xFFE8EAF6), Color(0xFF283593)), // Soft Indigo
        Pair(Color(0xFFFCE4EC), Color(0xFFAD1457))  // Soft Pink
    )

    /**
     * 발언자 이름을 기반으로 일관된 (배경색, 텍스트색) 쌍을 생성합니다.
     */
    fun getAvatarColors(name: String): Pair<Color, Color> {
        if (name.isBlank()) return Pair(Color(0xFFF1F5F9), Color(0xFF475569))
        var hash = 0
        for (char in name) {
            hash = char.code + ((hash shl 5) - hash)
        }
        val index = abs(hash) % avatarPalette.size
        return avatarPalette[index]
    }
}