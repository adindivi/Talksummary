package com.example.data.parser

import com.example.data.Message
import java.util.regex.Pattern

object KakaoTalkParser {

    // Inline patterns (date and time are embedded in the message line)
    private val fullKoreanInlinePattern = Pattern.compile("^(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일\\s*(오전|오후)\\s*(\\d{1,2}):(\\d{2})\\s*,?\\s*([^:]+)\\s*:\\s*(.*)")
    private val exportInlinePatternAMPM = Pattern.compile("^(\\d{4})\\.\\s*(\\d{1,2})\\.\\s*(\\d{1,2})\\.?\\s*(오전|오후)\\s*(\\d{1,2}):(\\d{2})\\s*,?\\s*([^:]+)\\s*:\\s*(.*)")
    private val exportInlinePattern = Pattern.compile("^(\\d{4})\\.\\s*(\\d{1,2})\\.\\s*(\\d{1,2})\\.?\\s*(\\d{1,2}):(\\d{2})\\s*,?\\s*([^:]+)\\s*:\\s*(.*)")

    // Date headers (stripped of hyphens `-` before matching)
    private val dateHeaderPattern1 = Pattern.compile("^(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일")
    private val dateHeaderPattern2 = Pattern.compile("^(\\d{4})\\.\\s*(\\d{1,2})\\.\\s*(\\d{1,2})")
    private val dateHeaderPattern3 = Pattern.compile("^(\\d{4})\\s*년\\s*(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일")
    private val dateHeaderPattern4 = Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})")
    private val savedDateHeaderPattern1 = Pattern.compile("저장한\\s+날짜\\s*:\\s*(\\d{4})\\.\\s*(\\d{1,2})\\.\\s*(\\d{1,2})")
    private val savedDateHeaderPattern2 = Pattern.compile("저장한\\s+날짜\\s*:\\s*(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일")

    // English date headers
    private val enDateHeader = Pattern.compile("^([a-zA-Z]+)\\s*(\\d{1,2}),\\s*(\\d{4})")
    private val enSavedDateHeader = Pattern.compile("Saved\\s+on\\s*([a-zA-Z]+)\\s*(\\d{1,2}),\\s*(\\d{4})")

    // Standalone message patterns (rely on pre-established or fallback currentDateStr)
    private val pcMsgPattern = Pattern.compile("^\\[([^\\]]+)\\]\\s*\\[(오전|오후|AM|PM)\\s*(\\d{1,2}):(\\d{2})\\]\\s*(.*)")
    private val mobileStandardPattern = Pattern.compile("^(오전|오후)\\s*(\\d{1,2}):(\\d{2})\\s*,?\\s*([^:]+)\\s*:\\s*(.*)")
    private val enMobileStandardPattern = Pattern.compile("^(\\d{1,2}):(\\d{2})\\s*(AM|PM)\\s*,?\\s*([^:]+)\\s*:\\s*(.*)")

    private fun parseEnglishMonth(mName: String?): String {
        val name = (mName ?: "").lowercase()
        return when {
            name.startsWith("jan") -> "1"
            name.startsWith("feb") -> "2"
            name.startsWith("mar") -> "3"
            name.startsWith("apr") -> "4"
            name.startsWith("may") -> "5"
            name.startsWith("jun") -> "6"
            name.startsWith("jul") -> "7"
            name.startsWith("aug") -> "8"
            name.startsWith("sep") -> "9"
            name.startsWith("oct") -> "10"
            name.startsWith("nov") -> "11"
            name.startsWith("dec") -> "12"
            else -> "1"
        }
    }

    /**
     * Decode files using smart multi-charset verification to prevent garbled text imports!
     */
    fun parseFromBytes(bytes: ByteArray): Map<String, List<Message>> {
        val encodings = listOf("UTF-8", "EUC-KR", "UTF-16", "MS949", "UTF-16LE", "UTF-16BE")
        
        // Try each encoding and return the first one that successfully parses messages.
        for (encoding in encodings) {
            try {
                val decoded = String(bytes, charset(encoding)).replace("\uFEFF", "")
                val result = parse(decoded)
                if (result.isNotEmpty()) {
                    return result
                }
            } catch (e: Exception) {
                // Ignore and continue
            }
        }
        
        // Final fallback: standard raw parsing from UTF-8
        try {
            val decodedUtf8 = String(bytes, Charsets.UTF_8).replace("\uFEFF", "")
            return parse(decodedUtf8)
        } catch (e: Exception) {
            // ignore
        }
        return emptyMap()
    }

    fun parse(content: String): Map<String, List<Message>> {
        val chatDays = mutableMapOf<String, MutableList<Message>>()
        val lines = content.split(Regex("\\r?\\n"))
        
        var currentDateStr: String? = null

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue

            // 1. Try inline message patterns first (these have date and time in the line itself)
            val fullKoreanMatch = fullKoreanInlinePattern.matcher(trimmedLine)
            if (fullKoreanMatch.find()) {
                val year = fullKoreanMatch.group(1) ?: "2026"
                val month = fullKoreanMatch.group(2) ?: "01"
                val day = fullKoreanMatch.group(3) ?: "01"
                val ampm = fullKoreanMatch.group(4) ?: "오전"
                val hour = fullKoreanMatch.group(5) ?: "00"
                val min = fullKoreanMatch.group(6) ?: "00"
                val senderName = (fullKoreanMatch.group(7) ?: "알수없음").trim()
                val msgText = fullKoreanMatch.group(8) ?: ""

                val mStr = month.padStart(2, '0')
                val dStr = day.padStart(2, '0')
                currentDateStr = "$year-$mStr-$dStr"

                if (!chatDays.containsKey(currentDateStr)) {
                    chatDays[currentDateStr!!] = mutableListOf()
                }
                chatDays[currentDateStr!!]?.add(Message(senderName, "$ampm $hour:$min", msgText))
                continue
            }

            val inlineAMPM = exportInlinePatternAMPM.matcher(trimmedLine)
            if (inlineAMPM.find()) {
                val year = inlineAMPM.group(1) ?: "2026"
                val month = inlineAMPM.group(2) ?: "01"
                val day = inlineAMPM.group(3) ?: "01"
                val ampm = inlineAMPM.group(4) ?: "오전"
                val hour = inlineAMPM.group(5) ?: "00"
                val min = inlineAMPM.group(6) ?: "00"
                val senderName = (inlineAMPM.group(7) ?: "알수없음").trim()
                val msgText = inlineAMPM.group(8) ?: ""

                val mStr = month.padStart(2, '0')
                val dStr = day.padStart(2, '0')
                currentDateStr = "$year-$mStr-$dStr"

                if (!chatDays.containsKey(currentDateStr)) {
                    chatDays[currentDateStr!!] = mutableListOf()
                }
                chatDays[currentDateStr!!]?.add(Message(senderName, "$ampm $hour:$min", msgText))
                continue
            }

            val inlineMatch = exportInlinePattern.matcher(trimmedLine)
            if (inlineMatch.find()) {
                val year = inlineMatch.group(1) ?: "2026"
                val month = inlineMatch.group(2) ?: "01"
                val day = inlineMatch.group(3) ?: "01"
                val hour = inlineMatch.group(4) ?: "00"
                val min = inlineMatch.group(5) ?: "00"
                val senderName = (inlineMatch.group(6) ?: "알수없음").trim()
                val msgText = inlineMatch.group(7) ?: ""

                val mStr = month.padStart(2, '0')
                val dStr = day.padStart(2, '0')
                currentDateStr = "$year-$mStr-$dStr"

                if (!chatDays.containsKey(currentDateStr)) {
                    chatDays[currentDateStr!!] = mutableListOf()
                }
                chatDays[currentDateStr!!]?.add(Message(senderName, "$hour:$min", msgText))
                continue
            }

            // 2. Try date headers (strip leading/trailing decoration hyphens first)
            var cleanLine = trimmedLine
            if (cleanLine.startsWith("-") || cleanLine.endsWith("-")) {
                cleanLine = cleanLine.replace(Regex("^\\s*-+|-\\s*$"), "").trim()
            }

            var matchedDate = false

            // Check Korean/Explicit date headers
            val mSaved1 = savedDateHeaderPattern1.matcher(cleanLine)
            if (mSaved1.find()) {
                val year = mSaved1.group(1) ?: "2026"
                val month = mSaved1.group(2) ?: "01"
                val day = mSaved1.group(3) ?: "01"
                currentDateStr = "${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}"
                matchedDate = true
            }

            if (!matchedDate) {
                val mSaved2 = savedDateHeaderPattern2.matcher(cleanLine)
                if (mSaved2.find()) {
                    val year = mSaved2.group(1) ?: "2026"
                    val month = mSaved2.group(2) ?: "01"
                    val day = mSaved2.group(3) ?: "01"
                    currentDateStr = "${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}"
                    matchedDate = true
                }
            }

            if (!matchedDate) {
                val m1 = dateHeaderPattern1.matcher(cleanLine)
                if (m1.find()) {
                    val year = m1.group(1) ?: "2026"
                    val month = m1.group(2) ?: "01"
                    val day = m1.group(3) ?: "01"
                    currentDateStr = "${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}"
                    matchedDate = true
                }
            }

            if (!matchedDate) {
                val m2 = dateHeaderPattern2.matcher(cleanLine)
                if (m2.find()) {
                    val year = m2.group(1) ?: "2026"
                    val month = m2.group(2) ?: "01"
                    val day = m2.group(3) ?: "01"
                    currentDateStr = "${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}"
                    matchedDate = true
                }
            }

            if (!matchedDate) {
                val m3 = dateHeaderPattern3.matcher(cleanLine)
                if (m3.find()) {
                    val year = m3.group(1) ?: "2026"
                    val month = m3.group(2) ?: "01"
                    val day = m3.group(3) ?: "01"
                    currentDateStr = "${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}"
                    matchedDate = true
                }
            }

            if (!matchedDate) {
                val m4 = dateHeaderPattern4.matcher(cleanLine)
                if (m4.find()) {
                    val year = m4.group(1) ?: "2026"
                    val month = m4.group(2) ?: "01"
                    val day = m4.group(3) ?: "01"
                    currentDateStr = "${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}"
                    matchedDate = true
                }
            }

            // Check English date headers
            if (!matchedDate) {
                val mEnSaved = enSavedDateHeader.matcher(cleanLine)
                if (mEnSaved.find()) {
                    val mName = mEnSaved.group(1)
                    val day = mEnSaved.group(2) ?: "01"
                    val year = mEnSaved.group(3) ?: "2026"
                    val month = parseEnglishMonth(mName)
                    currentDateStr = "${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}"
                    matchedDate = true
                }
            }

            if (!matchedDate) {
                val mEn = enDateHeader.matcher(cleanLine)
                if (mEn.find()) {
                    val mName = mEn.group(1)
                    val day = mEn.group(2) ?: "01"
                    val year = mEn.group(3) ?: "2026"
                    val month = parseEnglishMonth(mName)
                    currentDateStr = "${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}"
                    matchedDate = true
                }
            }

            if (matchedDate) {
                if (currentDateStr != null && !chatDays.containsKey(currentDateStr)) {
                    chatDays[currentDateStr!!] = mutableListOf()
                }
                continue
            }

            // 3. Try standard message patterns (relying on currentDateStr or fallback)
            val pcMatch = pcMsgPattern.matcher(trimmedLine)
            if (pcMatch.find()) {
                if (currentDateStr == null) {
                    currentDateStr = "2026-06-13" // Safeguard Fallback Date
                }
                val senderName = (pcMatch.group(1) ?: "알수없음").trim()
                val ampm = pcMatch.group(2) ?: "오전"
                val hour = pcMatch.group(3) ?: "00"
                val min = pcMatch.group(4) ?: "00"
                val msgText = pcMatch.group(5) ?: ""

                if (!chatDays.containsKey(currentDateStr)) {
                    chatDays[currentDateStr!!] = mutableListOf()
                }
                chatDays[currentDateStr!!]?.add(Message(senderName, "$ampm $hour:$min", msgText))
                continue
            }

            val mobileMatch = mobileStandardPattern.matcher(trimmedLine)
            if (mobileMatch.find()) {
                if (currentDateStr == null) {
                    currentDateStr = "2026-06-13" // Safeguard Fallback Date
                }
                val ampm = mobileMatch.group(1) ?: "오전"
                val hour = mobileMatch.group(2) ?: "00"
                val min = mobileMatch.group(3) ?: "00"
                val senderName = (mobileMatch.group(4) ?: "알수없음").trim()
                val msgText = mobileMatch.group(5) ?: ""

                if (!chatDays.containsKey(currentDateStr)) {
                    chatDays[currentDateStr!!] = mutableListOf()
                }
                chatDays[currentDateStr!!]?.add(Message(senderName, "$ampm $hour:$min", msgText))
                continue
            }

            val enMobileMatch = enMobileStandardPattern.matcher(trimmedLine)
            if (enMobileMatch.find()) {
                if (currentDateStr == null) {
                    currentDateStr = "2026-06-13" // Safeguard Fallback Date
                }
                val hour = enMobileMatch.group(1) ?: "00"
                val min = enMobileMatch.group(2) ?: "00"
                val ampm = enMobileMatch.group(3) ?: "AM"
                val senderName = (enMobileMatch.group(4) ?: "알수없음").trim()
                val msgText = enMobileMatch.group(5) ?: ""

                if (!chatDays.containsKey(currentDateStr)) {
                    chatDays[currentDateStr!!] = mutableListOf()
                }
                chatDays[currentDateStr!!]?.add(Message(senderName, "$ampm $hour:$min", msgText))
                continue
            }

            // 4. Append as multi-line continuing content
            if (currentDateStr != null && chatDays.containsKey(currentDateStr) && chatDays[currentDateStr!!]!!.isNotEmpty()) {
                val isTimestampPrefix = trimmedLine.startsWith("오전") || trimmedLine.startsWith("오후") || trimmedLine.matches(Regex("^\\d{4}.*"))
                if (!isTimestampPrefix) {
                    val lastList = chatDays[currentDateStr!!]!!
                    val lastMsg = lastList.last()
                    lastList[lastList.size - 1] = lastMsg.copy(text = lastMsg.text + "\n" + trimmedLine)
                }
            }
        }

        // Filter out any days that have 0 messages
        return chatDays.filterValues { it.isNotEmpty() }
    }
}
