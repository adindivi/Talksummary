package com.example.data.llm

import android.content.Context
import com.example.engine.LlamaMobileEngine
import com.example.engine.SandboxModelManager
import com.example.engine.StreamTokenEvent
import com.example.model.ChatMessage
import com.example.model.GgufMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages the lifecycle of loading a local GGUF model into memory
 * and interfaces with the underlying llama.cpp C++ JNI engine for inference.
 */
class ModelLoader(private val context: Context) {
    val sandboxManager = SandboxModelManager(context)
    val engine = LlamaMobileEngine(sandboxManager, context)
    private var currentModelPath: String = ""

    /**
     * Loads the GGUF model into memory via the C++ JNI llama.cpp engine.
     * If the requested model is already loaded, it does nothing.
     */
    suspend fun loadModel(modelPath: String): Result<GgufMetadata> = withContext(Dispatchers.IO) {
        if (!engine.isModelLoaded() || currentModelPath != modelPath) {
            val file = File(modelPath)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("모델 파일이 존재하지 않습니다: $modelPath"))
            }
            val result = engine.loadModel(file)
            if (result.isSuccess) {
                currentModelPath = modelPath
            }
            result
        } else {
            val meta = engine.getLoadedMetadata()
            if (meta != null) Result.success(meta) else Result.failure(Exception("메타데이터를 찾을 수 없습니다"))
        }
    }

    /**
     * Stream inference using the loaded GGUF model.
     */
    fun streamResponse(prompt: String, history: List<ChatMessage> = emptyList()): Flow<StreamTokenEvent> {
        return engine.streamChatCompletion(prompt, history)
    }

    /**
     * Performs inference using the loaded model and waits for the full text.
     */
    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        if (!engine.isModelLoaded()) {
            throw IllegalStateException("GGUF 모델이 로드되지 않았습니다. 먼저 모델을 로드해 주세요.")
        }

        var completedText = ""
        var errorMsg: String? = null

        engine.streamChatCompletion(prompt, emptyList()).collect { event ->
            when (event) {
                is StreamTokenEvent.Completed -> {
                    completedText = event.fullText
                }
                is StreamTokenEvent.Error -> {
                    errorMsg = event.message
                }
                is StreamTokenEvent.Token -> {
                    completedText = event.accumulatedText
                }
            }
        }

        if (errorMsg != null && completedText.isEmpty()) {
            throw Exception(errorMsg)
        }

        completedText
    }

    fun isModelLoaded(): Boolean = engine.isModelLoaded()
    fun getLoadedMetadata(): GgufMetadata? = engine.getLoadedMetadata()
    fun stopInference() = engine.stopInference()

    /**
     * Closes the engine and frees native memory.
     */
    fun close() {
        engine.release()
        currentModelPath = ""
    }
}
