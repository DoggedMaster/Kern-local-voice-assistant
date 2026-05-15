package com.example.voicellm.pipeline

import android.content.Context
import com.example.voicellm.Config
import com.example.voicellm.audio.AndroidSttEngine
import com.example.voicellm.audio.AudioPlayer
import com.example.voicellm.engine.LlmEngine
import com.example.voicellm.engine.TtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class ChatMessage(val isUser: Boolean, val text: String)

class VoicePipeline(
    context: Context,
    initialVoiceDir: String,
    llmModelFileName: String,
    useGpu: Boolean = false,
    systemPrompt: String = "",
    enableWebSearch: Boolean = false,
    contextTokens: Int = 2048,
    private var sttLanguage: String = "de-DE",
) {
    private val stt    = AndroidSttEngine(context)
    private val player = AudioPlayer(Config.TTS_SAMPLE_RATE)
    private val llm    = LlmEngine(context, llmModelFileName, useGpu,
        systemPrompt, contextTokens, enableWebSearch)
    private val tts    = TtsEngine(initialVoiceDir)

    fun switchTtsVoice(dir: String) = tts.switchVoice(dir)

    private var warmupJob: Job? = null

    fun startWarmup() {
        warmupJob = scope.launch {
            try { llm.warmup() }
            catch (e: Exception) { android.util.Log.w("VoicePipeline", "LLM-Warmup fehlgeschlagen: ${e.message}") }
            try { withContext(Dispatchers.Default) { tts.synth(".") } }
            catch (e: Exception) { android.util.Log.w("VoicePipeline", "TTS-Warmup fehlgeschlagen: ${e.message}") }
            finally { warmupJob = null }
        }
    }

    suspend fun warmup() { warmupJob?.join() }

    fun updateSettings(systemPrompt: String, enableWebSearch: Boolean) {
        llm.updateConfig(systemPrompt, enableWebSearch)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null

    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _partial = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partial.asStateFlow()

    private val _assistant = MutableStateFlow("")
    val assistantText: StateFlow<String> = _assistant.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _history = MutableStateFlow<List<ChatMessage>>(emptyList())
    val history: StateFlow<List<ChatMessage>> = _history.asStateFlow()

    fun toggleMute() { _muted.value = !_muted.value }

    suspend fun resetConversation() {
        loopJob?.cancelAndJoin()
        loopJob = null
        player.abort()
        player.clearAbort()
        llm.reset()
        _history.value = emptyList()
        _assistant.value = ""
        _state.value = State.Idle
        loopJob = scope.launch { runLoop() }
    }

    fun stopSpeaking() {
        player.abort()
        player.clearAbort()
        _state.value = State.Idle
    }

    enum class State { Idle, Listening, Thinking, Speaking }

    fun start() {
        if (loopJob != null) return
        player.start()
        loopJob = scope.launch { runLoop() }
    }

    fun stop() {
        runBlocking {
            loopJob?.cancelAndJoin()
            loopJob = null
        }
        player.stop()
        tts.release()
        llm.release()
        _state.value = State.Idle
    }

    private suspend fun runLoop() {
        while (true) {
            if (_muted.value) {
                _state.value = State.Idle
                _muted.first { !it }
            }
            _state.value = State.Listening

            var finalText = ""
            stt.listenOnce(sttLanguage).collect { result ->
                _partial.value = result.text
                if (result.isFinal) {
                    finalText = result.text
                } else if (_state.value == State.Speaking && result.text.isNotBlank()) {
                    player.abort()
                    player.clearAbort()
                    _state.value = State.Listening
                }
            }
            _partial.value = ""
            if (finalText.isNotBlank()) handleTurn(finalText)
        }
    }

    private fun stripMarkdownForTts(text: String): String {
        var s = text
        s = s.replace(Regex("""^#{1,6}\s+""", RegexOption.MULTILINE), "")
        s = s.replace(Regex("""^\s*[-*•]\s+""", RegexOption.MULTILINE), "")
        s = s.replace(Regex("""\*\*(.+?)\*\*"""), "$1")
        s = s.replace(Regex("""\*(.+?)\*"""), "$1")
        s = s.replace(Regex("""_(.+?)_"""), "$1")
        s = s.replace(Regex("""`+"""), "")
        s = s.replace(Regex("""\[([^\]]+)]\([^)]+\)"""), "$1")
        return s.trim()
    }

    private suspend fun handleTurn(text: String) {
        warmupJob?.join(); warmupJob = null

        _state.value = State.Thinking
        _assistant.value = ""

        val ttsChannel = Channel<String>(Channel.UNLIMITED)
        val ttsJob = scope.launch {
            for (sentence in ttsChannel) {
                _state.value = State.Speaking
                val pcm = withContext(Dispatchers.Default) { tts.synth(sentence) }
                player.enqueue(pcm)
            }
        }

        try {
            llm.streamReply(text).asSentences().collect { sentence ->
                _assistant.value = _assistant.value + sentence + " "
                val forTts = stripMarkdownForTts(sentence)
                if (forTts.isNotBlank()) ttsChannel.send(forTts)
            }
        } finally {
            ttsChannel.close()
            ttsJob.join()
            player.awaitDone()
            _state.value = State.Listening
        }

        val assistantFinal = _assistant.value.trim()
        if (assistantFinal.isNotBlank()) {
            _history.value = _history.value +
                ChatMessage(isUser = true,  text = text) +
                ChatMessage(isUser = false, text = assistantFinal)

            llm.incrementTurn()
            llm.addTextEstimate(text.length + assistantFinal.length)
            if (llm.needsContextReset()) {
                val recentPairs = _history.value
                    .windowed(2, 2, partialWindows = false)
                    .filter { it[0].isUser && !it[1].isUser }
                    .takeLast(8)
                    .map { it[0].text to it[1].text }
                llm.summarizeAndReset(recentPairs)
            }
        }
    }
}
