package com.example.voicellm.engine

import com.example.voicellm.Config
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig

class TtsEngine(voiceDir: String) {

    private var tts: OfflineTts = createTts(voiceDir)

    private fun createTts(dir: String): OfflineTts {
        val cfg = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model   = "$dir/${Config.TTS_MODEL}",
                    tokens  = "$dir/${Config.TTS_TOKENS}",
                    dataDir = "$dir/${Config.TTS_DATA_DIR}",
                ),
                numThreads = 2,
            ),
        )
        return OfflineTts(config = cfg)
    }

    @Synchronized
    fun synth(text: String): FloatArray =
        tts.generate(text = text, sid = Config.TTS_VOICE_ID, speed = Config.TTS_SPEED).samples

    @Synchronized
    fun switchVoice(dir: String) {
        tts.release()
        tts = createTts(dir)
    }

    @Synchronized
    fun release() = tts.release()
}
