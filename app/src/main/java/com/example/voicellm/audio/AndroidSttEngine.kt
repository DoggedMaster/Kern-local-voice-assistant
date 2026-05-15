package com.example.voicellm.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Wraps Android SpeechRecognizer as a coroutine Flow.
 *
 * Each call to [listenOnce] starts one recognition session:
 *  - emits partial results while the user speaks
 *  - emits the final result (isFinal=true) and then closes
 *  - closes silently on timeout/no-match so the caller can restart
 *
 * SpeechRecognizer MUST be created + called on the Main thread.
 */
class AndroidSttEngine(private val context: Context) {

    data class SttResult(val text: String, val isFinal: Boolean)

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun listenOnce(languageCode: String = "de-DE"): Flow<SttResult> = callbackFlow {
        val recognizer = withContext(Dispatchers.Main) {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

        withContext(Dispatchers.Main) {
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onPartialResults(partialResults: Bundle) {
                    val text = partialResults
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: return
                    if (text.isNotBlank()) trySend(SttResult(text, false))
                }

                override fun onResults(results: Bundle) {
                    val text = results
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    if (text.isNotBlank()) trySend(SttResult(text, true))
                    close()
                }

                override fun onError(error: Int) {
                    // ERROR_SPEECH_TIMEOUT (6) and ERROR_NO_MATCH (7) are normal —
                    // the caller loops and calls listenOnce() again.
                    close()
                }
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Stop earlier after silence to reduce latency
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 600L)
            }
            recognizer.startListening(intent)
        }

        awaitClose {
            Handler(Looper.getMainLooper()).post {
                try {
                    recognizer.cancel()
                    recognizer.destroy()
                } catch (_: Throwable) {}
            }
        }
    }
}
