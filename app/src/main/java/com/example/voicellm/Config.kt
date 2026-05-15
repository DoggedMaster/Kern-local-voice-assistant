package com.example.voicellm

object Config {

    // --- Demo-Modus ---
    const val DEMO_MODE = false

    // --- Language ---
    enum class Language(val sttLocale: String) {
        DEUTSCH("de-DE"), ENGLISH("en-US"), MULTILINGUAL("de-DE");
        companion object { fun fromKey(k: String) = entries.find { it.name == k } ?: DEUTSCH }
    }

    // --- TTS: sherpa-onnx Piper VITS Deutsch ---
    const val TTS_BASE_DIR  = "tts"
    const val TTS_MODEL     = "model.onnx"
    const val TTS_TOKENS    = "tokens.txt"
    const val TTS_DATA_DIR  = "espeak-ng-data"
    const val TTS_VOICE_ID  = 0
    const val TTS_SPEED     = 1.0f
    const val TTS_DEFAULT_VOICE_ID = "thorsten-medium"
    const val TTS_DEFAULT_VOICE_ID_EN = "en_US-amy-low"

    fun defaultVoiceForLanguage(language: Language): String = when (language) {
        Language.DEUTSCH, Language.MULTILINGUAL -> TTS_DEFAULT_VOICE_ID
        Language.ENGLISH                        -> TTS_DEFAULT_VOICE_ID_EN
    }

    private const val TTS_BASE_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"

    data class Voice(
        val id: String,
        val displayName: String,
        val description: String,
        val url: String,
        val sizeHint: String,
    )

    val TTS_VOICES = listOf(
        Voice("thorsten-medium",       "Thorsten (mittel)",    "Männlich · mittlere Qualität",
            "${TTS_BASE_URL}vits-piper-de_DE-thorsten-medium.tar.bz2",           "~75 MB"),
        Voice("thorsten-high",         "Thorsten (hoch)",      "Männlich · hohe Qualität",
            "${TTS_BASE_URL}vits-piper-de_DE-thorsten-high.tar.bz2",             "~120 MB"),
        Voice("thorsten_emotional-medium", "Thorsten (emotional)", "Männlich · emotional",
            "${TTS_BASE_URL}vits-piper-de_DE-thorsten_emotional-medium.tar.bz2", "~75 MB"),
        Voice("miro-high",             "Miro (hoch)",          "Männlich · hohe Qualität",
            "${TTS_BASE_URL}vits-piper-de_DE-miro-high.tar.bz2",                 "~120 MB"),
        Voice("karlsson-low",          "Karlsson",             "Männlich",
            "${TTS_BASE_URL}vits-piper-de_DE-karlsson-low.tar.bz2",              "~35 MB"),
        Voice("eva_k-x_low",           "Eva",                  "Weiblich",
            "${TTS_BASE_URL}vits-piper-de_DE-eva_k-x_low.tar.bz2",              "~25 MB"),
        Voice("kerstin-low",           "Kerstin",              "Weiblich",
            "${TTS_BASE_URL}vits-piper-de_DE-kerstin-low.tar.bz2",              "~35 MB"),
        Voice("ramona-low",            "Ramona",               "Weiblich",
            "${TTS_BASE_URL}vits-piper-de_DE-ramona-low.tar.bz2",               "~35 MB"),
        Voice("glados-medium",         "GLaDOS",               "Spaß-Stimme (Portal)",
            "${TTS_BASE_URL}vits-piper-de_DE-glados-medium.tar.bz2",            "~75 MB"),
        Voice("glados_turret-medium",  "GLaDOS Turret",        "Spaß-Stimme (Portal)",
            "${TTS_BASE_URL}vits-piper-de_DE-glados_turret-medium.tar.bz2",     "~75 MB"),
        // --- English voices ---
        Voice("en_US-ryan-high",     "Ryan (US)",    "Male · US English · high quality",
            "${TTS_BASE_URL}vits-piper-en_US-ryan-high.tar.bz2",     "~120 MB"),
        Voice("en_US-amy-low",       "Amy (US)",     "Female · US English",
            "${TTS_BASE_URL}vits-piper-en_US-amy-low.tar.bz2",       "~25 MB"),
        Voice("en_US-lessac-medium", "Lessac (US)",  "Female · US English · medium quality",
            "${TTS_BASE_URL}vits-piper-en_US-lessac-medium.tar.bz2", "~75 MB"),
        Voice("en_GB-alan-medium",   "Alan (GB)",    "Male · British English",
            "${TTS_BASE_URL}vits-piper-en_GB-alan-medium.tar.bz2",   "~75 MB"),
        Voice("en_GB-cori-high",     "Cori (GB)",    "Female · British English · high quality",
            "${TTS_BASE_URL}vits-piper-en_GB-cori-high.tar.bz2",     "~120 MB"),
    )

    // --- LLM: Gemma 4 LiteRT-LM ---
    const val LLM_DEFAULT_MODEL_ID = "gemma-4-e2b"

    data class LlmModel(
        val id: String,
        val displayName: String,
        val description: String,
        val url: String,
        val fileName: String,
        val sizeHint: String,
        val defaultContextTokens: Int  = 2048,
        val maxContextTokens: Int      = 8192,
        val contextTokenStep: Int      = 256,
        val modelRamBytes: Long        = 1_500_000_000L,
        val kvCacheBytesPerToken: Long = 120_000L,
    )

    val LLM_MODELS = listOf(
        LlmModel(
            id                   = "gemma-4-e2b",
            displayName          = "Gemma 4 E2B",
            description          = "2 Mrd. Parameter · schneller",
            url                  = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            fileName             = "gemma-4-E2B-it.litertlm",
            sizeHint             = "~2,6 GB",
            defaultContextTokens = 2048,
            maxContextTokens     = 8192,
            modelRamBytes        = 1_500_000_000L,
            kvCacheBytesPerToken = 120_000L,
        ),
        LlmModel(
            id                   = "gemma-4-e4b",
            displayName          = "Gemma 4 E4B",
            description          = "4 Mrd. Parameter · leistungsstärker",
            url                  = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
            fileName             = "gemma-4-E4B-it.litertlm",
            sizeHint             = "~3,7 GB",
            defaultContextTokens = 2048,
            maxContextTokens     = 8192,
            modelRamBytes        = 2_400_000_000L,
            kvCacheBytesPerToken = 200_000L,
        ),
    )
    const val LLM_TOP_K       = 40
    const val LLM_TEMPERATURE = 0.8f
    const val LLM_USE_GPU     = true

    private const val SYSTEM_PROMPT_BASE =
        "Du bist ein hilfreicher deutschsprachiger Sprachassistent. " +
        "Antworte knapp in 1-3 Saetzen in natuerlicher gesprochener Sprache, " +
        "ohne Markdown, ohne Aufzaehlungen."

    private const val SYSTEM_PROMPT_BASE_EN =
        "You are a helpful voice assistant. " +
        "Answer concisely in 1-3 sentences in natural spoken language, " +
        "without Markdown, without bullet points."

    private const val SYSTEM_PROMPT_BASE_MULTI =
        "You are a helpful voice assistant. " +
        "Always reply in the same language the user is speaking. " +
        "Answer concisely in 1-3 sentences in natural spoken language, " +
        "without Markdown, without bullet points."

    fun defaultSystemPrompt(language: Language): String = when (language) {
        Language.DEUTSCH      -> SYSTEM_PROMPT_BASE
        Language.ENGLISH      -> SYSTEM_PROMPT_BASE_EN
        Language.MULTILINGUAL -> SYSTEM_PROMPT_BASE_MULTI
    }

    fun buildSystemPrompt(customBase: String, language: Language = Language.DEUTSCH): String {
        val base = customBase.ifBlank { defaultSystemPrompt(language) }
        val toolsLine = when (language) {
            Language.ENGLISH -> "Available tools: Date/Time. No internet access."
            else             -> "Verfuegbare Tools: Datum/Uhrzeit. Du hast keinen Internetzugang."
        }
        return "$base\n$toolsLine".trimEnd()
    }

    fun voicesForLanguage(language: Language): List<Voice> = when (language) {
        Language.DEUTSCH      -> TTS_VOICES.filter { !it.id.startsWith("en_") }
        Language.ENGLISH      -> TTS_VOICES.filter { it.id.startsWith("en_") }
        Language.MULTILINGUAL -> TTS_VOICES
    }

    // --- Audio ---
    const val TTS_SAMPLE_RATE = 22050
    const val VAD_ENDPOINT_MS = 400
}
