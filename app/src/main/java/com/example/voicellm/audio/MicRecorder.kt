package com.example.voicellm.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Streamt 16 kHz Mono PCM Int16 Frames (~ 20 ms pro Frame) aus dem Mikrofon.
 * Kein Rauschunterdruecker, weil sherpa-onnx VAD schon robust genug ist.
 */
class MicRecorder(private val sampleRate: Int = 16000) {

    private val frameSize = (sampleRate * FRAME_MS) / 1000
    private var record: AudioRecord? = null
    @Volatile private var running = false
    private val channel = Channel<ShortArray>(capacity = Channel.UNLIMITED)

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(frameSize * 4)

        record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf,
        ).apply { startRecording() }

        running = true
        Thread({
            val buf = ShortArray(frameSize)
            while (running) {
                val n = record?.read(buf, 0, buf.size) ?: 0
                if (n > 0) channel.trySend(buf.copyOf(n))
            }
        }, "mic-recorder").start()
    }

    fun frames(): Flow<ShortArray> = channel.receiveAsFlow()

    fun stop() {
        running = false
        record?.run {
            try { stop() } catch (_: Throwable) {}
            release()
        }
        record = null
    }

    companion object { const val FRAME_MS = 20 }
}
