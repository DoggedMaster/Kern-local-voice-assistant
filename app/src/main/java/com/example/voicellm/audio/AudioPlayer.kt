package com.example.voicellm.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

class AudioPlayer(private val sampleRate: Int = 24000) {

    private val queue   = ArrayBlockingQueue<FloatArray>(64)
    private val aborted = AtomicBoolean(false)
    private val playing = AtomicBoolean(false)
    private val stopping = AtomicBoolean(false)
    private var track: AudioTrack? = null
    private var worker: Thread? = null

    fun start() {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { it.play() }

        stopping.set(false)
        worker = Thread({
            while (!stopping.get()) {
                val chunk = try {
                    queue.poll(200, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    break
                } ?: continue
                if (aborted.get()) continue
                playing.set(true)
                track?.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                playing.set(false)
            }
        }, "audio-player").also { it.start() }
    }

    fun enqueue(pcm: FloatArray) {
        if (aborted.get()) return
        queue.put(pcm)
    }

    fun abort() {
        aborted.set(true)
        queue.clear()
        track?.pause()
        track?.flush()
        track?.play()
    }

    fun clearAbort() {
        aborted.set(false)
    }

    val isDone: Boolean get() = queue.isEmpty() && !playing.get()

    suspend fun awaitDone() { while (!isDone) delay(30) }

    fun stop() {
        // Abort first so any blocking write returns quickly
        abort()
        stopping.set(true)
        worker?.interrupt()
        worker?.join(500)
        worker = null
        track?.run { stop(); release() }
        track = null
    }
}
