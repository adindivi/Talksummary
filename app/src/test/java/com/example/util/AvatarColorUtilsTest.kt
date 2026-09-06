package com.example.util

import androidx.compose.ui.graphics.Color
import com.example.ui.util.AvatarColorUtils
import org.junit.Assert.*
import org.junit.Test

class AvatarColorUtilsTest {

    @Test
    fun getAvatarColors_returnsConsistentColorForSameName() {
        val color1 = AvatarColorUtils.getAvatarColors("홍길동")
        val color2 = AvatarColorUtils.getAvatarColors("홍길동")

        assertEquals(color1.first, color2.first)
        assertEquals(color1.second, color2.second)
    }

    @Test
    fun getAvatarColors_returnsGracefulFallbackForEmptyOrBlankName() {
        val emptyResult = AvatarColorUtils.getAvatarColors("")
        val blankResult = AvatarColorUtils.getAvatarColors("   ")

        assertEquals(Color(0xFFF1F5F9), emptyResult.first)
        assertEquals(Color(0xFF475569), emptyResult.second)
        assertEquals(Color(0xFFF1F5F9), blankResult.first)
        assertEquals(Color(0xFF475569), blankResult.second)
    }

    @Test
    fun getAvatarColors_producesDistinctColorsAcrossDifferentSenders() {
        val senders = listOf("홍길동", "이순신", "강감찬", "유관순", "세종대왕", "장영실")
        val results = senders.map { AvatarColorUtils.getAvatarColors(it) }

        // Across 6 distinct Korean names, there should be multiple distinct palette entries
        val distinctBgs = results.map { it.first }.distinct()
        assertTrue("Expected multiple palette colors across varied names", distinctBgs.size > 1)
    }
}