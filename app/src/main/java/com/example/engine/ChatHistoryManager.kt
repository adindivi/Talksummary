package com.example.engine

import android.content.Context
import androidx.core.util.AtomicFile
import com.example.model.ChatMessage
import com.example.model.ChatSession
import com.example.model.MessageSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class ChatHistoryManager(private val context: Context) {
    private val baseFile = File(context.filesDir, "chat_sessions.json")
    private val atomicSessionsFile = AtomicFile(baseFile)

    suspend fun loadSessions(): List<ChatSession> = withContext(Dispatchers.IO) {
        if (!baseFile.exists()) return@withContext emptyList()
        try {
            val jsonBytes = atomicSessionsFile.readFully()
            val jsonStr = String(jsonBytes, Charsets.UTF_8)
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<ChatSession>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val msgArray = obj.getJSONArray("messages")
                val messages = mutableListOf<ChatMessage>()
                for (j in 0 until msgArray.length()) {
                    val mObj = msgArray.getJSONObject(j)
                    messages.add(
                        ChatMessage(
                            id = mObj.optString("id", java.util.UUID.randomUUID().toString()),
                            sender = MessageSender.valueOf(mObj.getString("sender")),
                            content = mObj.getString("content"),
                            timestamp = mObj.optLong("timestamp", System.currentTimeMillis()),
                            isStreaming = false,
                            inputTokens = mObj.optInt("inputTokens", 0),
                            tokensGenerated = mObj.optInt("tokensGenerated", 0),
                            tokensPerSecond = mObj.optDouble("tokensPerSecond", 0.0),
                            isFailure = mObj.optBoolean("isFailure", false),
                            errorMessage = if (mObj.isNull("errorMessage")) null else mObj.optString("errorMessage")
                        )
                    )
                }
                list.add(
                    ChatSession(
                        id = obj.getString("id"),
                        title = obj.optString("title", "새로운 대화"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        messages = messages,
                        personaPrompt = if (obj.isNull("personaPrompt")) null else obj.optString("personaPrompt")
                    )
                )
            }
            list.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            android.util.Log.e("ChatHistoryManager", "Failed to load sessions: ${e.message}")
            emptyList()
        }
    }

    suspend fun saveSessions(sessions: List<ChatSession>) = withContext(Dispatchers.IO) {
        var fos: FileOutputStream? = null
        try {
            val jsonArray = JSONArray()
            for (session in sessions) {
                val obj = JSONObject()
                obj.put("id", session.id)
                obj.put("title", session.title)
                obj.put("createdAt", session.createdAt)
                obj.put("updatedAt", session.updatedAt)
                obj.put("personaPrompt", session.personaPrompt ?: JSONObject.NULL)

                val msgArray = JSONArray()
                for (msg in session.messages) {
                    val mObj = JSONObject()
                    mObj.put("id", msg.id)
                    mObj.put("sender", msg.sender.name)
                    mObj.put("content", msg.content)
                    mObj.put("timestamp", msg.timestamp)
                    mObj.put("inputTokens", msg.inputTokens)
                    mObj.put("tokensGenerated", msg.tokensGenerated)
                    mObj.put("tokensPerSecond", msg.tokensPerSecond)
                    mObj.put("isFailure", msg.isFailure)
                    mObj.put("errorMessage", msg.errorMessage ?: JSONObject.NULL)
                    msgArray.put(mObj)
                }
                obj.put("messages", msgArray)
                jsonArray.put(obj)
            }
            val bytes = jsonArray.toString().toByteArray(Charsets.UTF_8)
            fos = atomicSessionsFile.startWrite()
            fos.write(bytes)
            atomicSessionsFile.finishWrite(fos)
            android.util.Log.d("ChatHistoryManager", "Atomic-saved ${sessions.size} sessions (${sessions.sumOf { it.messages.size }} total messages).")
        } catch (e: Exception) {
            if (fos != null) {
                atomicSessionsFile.failWrite(fos)
            }
            android.util.Log.e("ChatHistoryManager", "Failed to atomic-save sessions: ${e.message}")
        }
    }
}
