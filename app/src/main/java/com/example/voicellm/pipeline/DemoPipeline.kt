package com.example.voicellm.pipeline

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

class DemoPipeline(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val textInput = Channel<String>(Channel.UNLIMITED)

    private val _state = MutableStateFlow(VoicePipeline.State.Idle)
    val state: StateFlow<VoicePipeline.State> = _state.asStateFlow()

    private val _partial = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partial.asStateFlow()

    private val _assistant = MutableStateFlow("")
    val assistantText: StateFlow<String> = _assistant.asStateFlow()

    private val _history = MutableStateFlow<List<ChatMessage>>(emptyList())
    val history: StateFlow<List<ChatMessage>> = _history.asStateFlow()

    private var loopJob: Job? = null
    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private val responses = listOf(
        "Hallo! Ich bin im Demo-Modus. Auf einem echten Gerät würde hier Gemma 4 E2B antworten.",
        "Demo läuft auf Waydroid. Tippe eine Nachricht und ich antworte mit Android-TextToSpeech.",
        "Das ist Antwort Nummer drei. Die KI-Modelle werden erst auf echten Geräten geladen.",
        "Sehr schön. Der Pipeline-Flow funktioniert: STT, LLM, TTS. Hier ist alles gemockt.",
    )
    private var idx = 0

    init {
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts.language = Locale.GERMAN
        }
    }

    fun start() {
        if (loopJob != null) return
        _state.value = VoicePipeline.State.Listening
        loopJob = scope.launch { runLoop() }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        tts.stop()
        tts.shutdown()
    }

    fun sendText(text: String) {
        textInput.trySend(text)
    }

    private suspend fun runLoop() {
        for (text in textInput) {
            if (text.isBlank()) continue
            handleTurn(text)
        }
    }

    private suspend fun handleTurn(userText: String) {
        _state.value = VoicePipeline.State.Thinking
        _assistant.value = ""

        val response = responses[idx % responses.size]
        idx++

        val words = response.split(" ")
        val sb = StringBuilder()
        for (word in words) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(word)
            _assistant.value = sb.toString()
            delay(70)
        }

        if (ttsReady) {
            _state.value = VoicePipeline.State.Speaking
            val uid = "demo_${System.currentTimeMillis()}"
            tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, uid)
            suspendCancellableCoroutine { cont ->
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String) {}
                    override fun onDone(id: String) { if (id == uid && cont.isActive) cont.resume(Unit) }
                    @Deprecated("Deprecated in Java")
            override fun onError(id: String) { if (id == uid && cont.isActive) cont.resume(Unit) }
                })
                cont.invokeOnCancellation { tts.stop() }
            }
        }

        _history.value = _history.value +
            ChatMessage(isUser = true,  text = userText) +
            ChatMessage(isUser = false, text = response)
        _state.value = VoicePipeline.State.Listening
    }
}
