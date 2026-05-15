package com.example.voicellm.engine

import android.content.Context
import android.util.Log
import com.example.voicellm.Config
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion

class LlmEngine(context: Context, modelFileName: String, useGpu: Boolean = false,
                systemPrompt: String = "", maxTokens: Int = 2048,
                enableWebSearch: Boolean = false) {

    val contextTokens = maxTokens
    val maxTurns = ((maxTokens - 600) / 220).coerceIn(3, 20)

    private var storedSystemPrompt = systemPrompt
    private var storedWebSearch    = enableWebSearch
    private var sessionSummary     = ""
    private var turnCount          = 0
    private var _accumulatedTokens = 0
    private var _textTokenEstimate = 0
    val accumulatedTokens: Int get() = _accumulatedTokens
    fun addTextEstimate(chars: Int) { _textTokenEstimate += chars / 4 }
    val effectiveTokens: Int
        get() = if (_accumulatedTokens > 0) _accumulatedTokens
                else (buildSystemInstruction(storedSystemPrompt).length / 4) + _textTokenEstimate

    private val engine: Engine = run {
        val modelPath = ModelStore.modelPath(context, modelFileName)
        val backend = if (useGpu) {
            Log.i("LlmEngine", "Initialisiere Engine (GPU) …")
            Backend.GPU()
        } else {
            Log.i("LlmEngine", "Initialisiere Engine (CPU) …")
            Backend.CPU()
        }
        val litertCacheDir = java.io.File(context.filesDir, "litert_cache/${modelFileName}")
            .also { it.mkdirs() }
        val cfg = EngineConfig(
            modelPath    = modelPath,
            backend      = backend,
            cacheDir     = litertCacheDir.absolutePath,
            maxNumTokens = maxTokens,
        )
        Engine(cfg).also { it.initialize(); Log.i("LlmEngine", "Engine bereit") }
    }

    private var convConfig = buildConfig(systemPrompt, enableWebSearch)
    private var conversation = engine.createConversation(convConfig)

    private fun buildConfig(prompt: String, webSearch: Boolean) =
        ConversationConfig(
            systemInstruction    = Contents.of(buildSystemInstruction(prompt)),
            tools                = buildTools(webSearch),
            automaticToolCalling = true,
            samplerConfig        = SamplerConfig(
                topK        = Config.LLM_TOP_K,
                topP        = 0.95,
                temperature = Config.LLM_TEMPERATURE.toDouble(),
            ),
        )

    private fun buildSystemInstruction(basePrompt: String): String {
        val sb = StringBuilder(basePrompt)
        if (sessionSummary.isNotBlank()) {
            sb.append("\n\n## Bisheriges Gespräch (Zusammenfassung):\n$sessionSummary")
        }
        return sb.toString()
    }

    @Synchronized
    fun updateConfig(systemPrompt: String, enableWebSearch: Boolean) {
        storedSystemPrompt = systemPrompt
        storedWebSearch    = enableWebSearch
        conversation.close()
        convConfig   = buildConfig(systemPrompt, enableWebSearch)
        conversation = engine.createConversation(convConfig)
        turnCount    = 0
        Log.i("LlmEngine", "Konversations-Config aktualisiert")
    }

    suspend fun warmup() {
        try {
            Log.i("LlmEngine", "Warmup: prefille System-Prompt …")
            conversation.sendMessageAsync("Hallo").first()
        } catch (_: Exception) {}
        try { conversation.close() } catch (_: Exception) {}
        try {
            conversation = engine.createConversation(convConfig)
            Log.i("LlmEngine", "Warmup abgeschlossen")
        } catch (e: Exception) {
            Log.w("LlmEngine", "Warmup: Conversation-Neustart fehlgeschlagen: ${e.message}")
        }
    }

    fun reset() {
        try { conversation.close() } catch (_: Exception) {}
        sessionSummary     = ""
        turnCount          = 0
        _accumulatedTokens = 0
        _textTokenEstimate = 0
        convConfig         = buildConfig(storedSystemPrompt, storedWebSearch)
        conversation       = engine.createConversation(convConfig)
    }

    fun incrementTurn() { turnCount++ }
    fun needsContextReset(): Boolean = turnCount >= maxTurns
    val currentTurn get() = turnCount

    suspend fun summarizeAndReset(recentHistory: List<Pair<String, String>>) {
        Log.i("LlmEngine", "Kontext-Reset: $turnCount Turns …")
        try { conversation.close() } catch (_: Exception) {}

        val summaryConv = try {
            engine.createConversation(ConversationConfig(
                systemInstruction = Contents.of(
                    "Du fasst kurz zusammen, was in diesem Gespräch besprochen wurde. " +
                    "Schreibe 2–4 prägnante Sätze, keine Aufzählungszeichen."
                ),
                samplerConfig = SamplerConfig(topK = 1, topP = 0.9, temperature = 0.3),
            ))
        } catch (e: Exception) {
            Log.w("LlmEngine", "Zusammenfassung: Conversation-Erstellung fehlgeschlagen: ${e.message}")
            convConfig   = buildConfig(storedSystemPrompt, storedWebSearch)
            conversation = engine.createConversation(convConfig)
            turnCount    = 0
            return
        }

        try {
            val prompt = buildString {
                append("Gesprächsauszug:\n")
                recentHistory.forEach { (u, a) ->
                    append("Nutzer: ${u.take(200)}\nAssistent: ${a.take(200)}\n\n")
                }
                append("Fasse das Gespräch kurz zusammen:")
            }
            val sb = StringBuilder()
            summaryConv.sendMessageAsync(prompt).collect { sb.append(it) }
            sessionSummary = sb.toString().trim()
            Log.i("LlmEngine", "Zusammenfassung: $sessionSummary")
        } catch (e: Exception) {
            Log.w("LlmEngine", "Zusammenfassung fehlgeschlagen: ${e.message}")
        } finally {
            try { summaryConv.close() } catch (_: Exception) {}
        }

        convConfig         = buildConfig(storedSystemPrompt, storedWebSearch)
        conversation       = engine.createConversation(convConfig)
        turnCount          = 0
        _accumulatedTokens = 0
        _textTokenEstimate = 0
    }

    @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
    fun streamReply(userText: String): Flow<String> =
        conversation.sendMessageAsync(userText)
            .map { it.toString() }
            .catch { e -> Log.e("LlmEngine", "Inference-Fehler", e); emit("[Fehler: ${e.message}]") }
            .onCompletion {
                try {
                    val info = conversation.getBenchmarkInfo()
                    Log.i("LlmEngine", "BenchmarkInfo: prefill=${info.lastPrefillTokenCount}, decode=${info.lastDecodeTokenCount}")
                    if (info.lastPrefillTokenCount > 0 || info.lastDecodeTokenCount > 0)
                        _accumulatedTokens += info.lastPrefillTokenCount + info.lastDecodeTokenCount
                } catch (e: Exception) {
                    Log.w("LlmEngine", "BenchmarkInfo fehlgeschlagen: ${e.message}")
                }
            }

    fun release() {
        try { conversation.close() } catch (_: Exception) {}
        try { engine.close() } catch (_: Exception) {}
    }
}
