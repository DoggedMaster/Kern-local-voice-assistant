package com.example.voicellm.ui

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.example.voicellm.Config
import com.example.voicellm.engine.DownloadWorker
import com.example.voicellm.engine.ModelStore
import com.example.voicellm.pipeline.DemoPipeline
import com.example.voicellm.pipeline.VoicePipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VoiceViewModel(app: Application) : AndroidViewModel(app) {

    // --- Screens ---
    enum class Screen { Start, Settings, VoiceChat, History, VoiceSettings, LlmSettings, SystemPromptSettings, Wizard, Info, Language }
    private val _screen = MutableStateFlow(
        if (prefs.getBoolean(KEY_FIRST_LAUNCH, true)) Screen.Wizard else Screen.Start
    )
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    fun navigateTo(s: Screen) { _screen.value = s }

    // --- Pipelines ---
    private var pipeline: VoicePipeline? = null
    private var demoPipeline: DemoPipeline? = null

    val state             get() = pipeline?.state             ?: demoPipeline?.state
    val partialTranscript get() = pipeline?.partialTranscript ?: demoPipeline?.partialTranscript
    val assistantText     get() = pipeline?.assistantText     ?: demoPipeline?.assistantText
    val muted             get() = pipeline?.muted
    val history           get() = pipeline?.history           ?: demoPipeline?.history

    fun sendDemoText(text: String) { demoPipeline?.sendText(text) }
    fun toggleMute()      { pipeline?.toggleMute() }
    fun stopSpeaking()    { pipeline?.stopSpeaking() }
    fun resetConversation() {
        viewModelScope.launch(Dispatchers.Default) { pipeline?.resetConversation() }
    }

    fun beginConversation() {
        pipeline?.start() ?: return
        _ready.value = true
        _screen.value = Screen.VoiceChat
    }

    // --- UI state ---
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _pipelineLoading = MutableStateFlow(false)
    val pipelineLoading: StateFlow<Boolean> = _pipelineLoading.asStateFlow()

    private val _pipelineReady = MutableStateFlow(false)
    val pipelineReady: StateFlow<Boolean> = _pipelineReady.asStateFlow()

    private var pipelineStarting = false

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // --- Model status ---
    enum class ModelStatus { Unknown, Checking, Missing, Downloading, Extracting, Ready, Error }

    data class ModelState(
        val status: ModelStatus = ModelStatus.Unknown,
        val bytes: Long = 0L,
        val total: Long = -1L,
        val error: String? = null,
    )

    // --- Voice management ---
    private val _activeVoiceId = MutableStateFlow(
        prefs.getString(KEY_ACTIVE_VOICE, Config.TTS_DEFAULT_VOICE_ID)!!
    )
    val activeVoiceId: StateFlow<String> = _activeVoiceId.asStateFlow()

    private val _voiceStates: Map<String, MutableStateFlow<ModelState>> =
        Config.TTS_VOICES.associate { it.id to MutableStateFlow(ModelState()) }
    val voiceStates: Map<String, StateFlow<ModelState>> =
        _voiceStates.mapValues { it.value.asStateFlow() }

    // --- LLM management ---
    private val _activeLlmId = MutableStateFlow(
        prefs.getString(KEY_ACTIVE_LLM, Config.LLM_DEFAULT_MODEL_ID)!!
    )
    val activeLlmId: StateFlow<String> = _activeLlmId.asStateFlow()

    private val _llmModelStates: Map<String, MutableStateFlow<ModelState>> =
        Config.LLM_MODELS.associate { it.id to MutableStateFlow(ModelState()) }
    val llmModelStates: Map<String, StateFlow<ModelState>> =
        _llmModelStates.mapValues { it.value.asStateFlow() }

    // --- Device RAM ---
    val deviceTotalRamBytes: Long = run {
        val am   = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        info.totalMem
    }

    // --- Context window (per model, persisted) ---
    fun getContextTokens(modelId: String): Int {
        val model = Config.LLM_MODELS.find { it.id == modelId } ?: return 2048
        return prefs.getInt("ctx_tokens_$modelId", model.defaultContextTokens)
    }

    fun setContextTokens(modelId: String, tokens: Int) {
        prefs.edit().putInt("ctx_tokens_$modelId", tokens).apply()
    }

    fun applyContextTokens(modelId: String, tokens: Int) {
        prefs.edit().putInt("ctx_tokens_$modelId", tokens).apply()
        if (modelId == _activeLlmId.value) restartPipeline()
    }

    // --- Custom system prompt ---
    private val _customSystemPrompt = MutableStateFlow(
        prefs.getString(KEY_CUSTOM_SYSTEM_PROMPT, "") ?: "")
    val customSystemPrompt: StateFlow<String> = _customSystemPrompt.asStateFlow()

    fun setCustomSystemPrompt(text: String) {
        prefs.edit().putString(KEY_CUSTOM_SYSTEM_PROMPT, text).apply()
        _customSystemPrompt.value = text
        refreshPipelineSettings()
    }

    // --- Language ---
    private val _language = MutableStateFlow(
        Config.Language.fromKey(prefs.getString(KEY_LANGUAGE, Config.Language.DEUTSCH.name) ?: Config.Language.DEUTSCH.name)
    )
    val language: StateFlow<Config.Language> = _language.asStateFlow()

    fun setLanguage(lang: Config.Language) {
        if (lang == _language.value) return
        prefs.edit().putString(KEY_LANGUAGE, lang.name).apply()
        _language.value = lang
        restartPipeline()
    }

    private val prefs get() = getApplication<Application>()
        .getSharedPreferences("voicellm", Context.MODE_PRIVATE)

    private val wm get() = WorkManager.getInstance(getApplication())

    init {
        viewModelScope.launch(Dispatchers.IO) { cleanOrphanedCaches() }
    }

    private fun cleanOrphanedCaches() {
        val ctx = getApplication<Application>()
        val cacheRoot = File(ctx.filesDir, "litert_cache")
        if (!cacheRoot.isDirectory) return
        val knownNames = Config.LLM_MODELS.map { it.fileName }.toSet()
        cacheRoot.listFiles()?.forEach { entry ->
            if (entry.name !in knownNames) entry.deleteRecursively()
        }
    }

    private fun refreshPipelineSettings() {
        val p = pipeline ?: return
        val sysPrompt = Config.buildSystemPrompt(_customSystemPrompt.value, _language.value)
        viewModelScope.launch(Dispatchers.Default) {
            p.updateSettings(sysPrompt, false)
        }
    }

    fun onMicPermissionResult(granted: Boolean) {
        if (!granted) { _error.value = "Mikrofon-Zugriff wurde verweigert."; return }
        checkAndInit()
    }

    private fun checkAndInit() {
        val ctx = getApplication<Application>()

        if (Config.DEMO_MODE) {
            viewModelScope.launch {
                try {
                    val p = DemoPipeline(ctx).also { it.start() }
                    demoPipeline = p
                    _ready.value = true
                    _screen.value = Screen.VoiceChat
                } catch (t: Throwable) {
                    _error.value = "Demo-Init fehlgeschlagen: ${t.message}"
                }
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val modelsDir = ModelStore.modelsDir(ctx)
            val oldModel  = File(modelsDir, "tts/model.onnx")
            val newDir    = File(modelsDir, "tts/${Config.TTS_DEFAULT_VOICE_ID}")
            if (oldModel.exists() && !newDir.exists()) {
                newDir.mkdirs()
                File(modelsDir, "tts").listFiles()?.forEach { f ->
                    if (f.name != Config.TTS_DEFAULT_VOICE_ID) f.renameTo(File(newDir, f.name))
                }
            }
        }

        Config.TTS_VOICES.forEach { voice ->
            observeWork(voiceWorkName(voice.id), _voiceStates[voice.id]!!, ctx,
                checkPath = voiceCheckPath(voice.id))
        }
        Config.LLM_MODELS.forEach { model ->
            observeWork(llmWorkName(model.id), _llmModelStates[model.id]!!, ctx,
                checkPath = model.fileName)
        }

        viewModelScope.launch {
            Config.TTS_VOICES.forEach { voice ->
                val s = _voiceStates[voice.id]!!
                if (s.value.status == ModelStatus.Unknown) s.value = ModelState(ModelStatus.Checking)
            }
            Config.TTS_VOICES.forEach { voice ->
                val s = _voiceStates[voice.id]!!
                if (s.value.status == ModelStatus.Checking) {
                    val exists = withContext(Dispatchers.IO) {
                        ModelStore.exists(ctx, voiceCheckPath(voice.id))
                    }
                    s.value = ModelState(if (exists) ModelStatus.Ready else ModelStatus.Missing)
                }
            }

            Config.LLM_MODELS.forEach { model ->
                val s = _llmModelStates[model.id]!!
                if (s.value.status == ModelStatus.Unknown) s.value = ModelState(ModelStatus.Checking)
            }
            Config.LLM_MODELS.forEach { model ->
                val s = _llmModelStates[model.id]!!
                if (s.value.status == ModelStatus.Checking) {
                    val exists = withContext(Dispatchers.IO) {
                        ModelStore.exists(ctx, model.fileName)
                    }
                    s.value = ModelState(if (exists) ModelStatus.Ready else ModelStatus.Missing)
                }
            }

            checkBothReady(ctx)
        }
    }

    private fun observeWork(
        workName: String,
        state: MutableStateFlow<ModelState>,
        ctx: Context,
        checkPath: String,
    ) {
        viewModelScope.launch {
            wm.getWorkInfosForUniqueWorkFlow(workName).collect { infos ->
                val info  = infos.firstOrNull() ?: return@collect
                val bytes = info.progress.getLong(DownloadWorker.KEY_BYTES, 0)
                val total = info.progress.getLong(DownloadWorker.KEY_TOTAL, -1)
                val phase = info.progress.getString(DownloadWorker.KEY_PHASE) ?: "download"

                when (info.state) {
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.RUNNING -> state.value = when {
                        phase == "extract" -> ModelState(ModelStatus.Extracting)
                        else -> ModelState(ModelStatus.Downloading, bytes, total)
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val exists = withContext(Dispatchers.IO) { ModelStore.exists(ctx, checkPath) }
                        if (exists) {
                            state.value = ModelState(ModelStatus.Ready)
                            checkBothReady(ctx)
                        } else {
                            state.value = ModelState(ModelStatus.Missing)
                        }
                    }
                    WorkInfo.State.FAILED -> state.value = ModelState(
                        ModelStatus.Error,
                        error = info.outputData.getString(DownloadWorker.KEY_ERROR) ?: "Unbekannter Fehler",
                    )
                    WorkInfo.State.CANCELLED -> state.value = ModelState(ModelStatus.Missing)
                    else -> {}
                }
            }
        }
    }

    private suspend fun startPipeline(ctx: Context) {
        _error.value = null
        _pipelineLoading.value = true
        try {
            val voiceDir = File(ModelStore.modelsDir(ctx), "${Config.TTS_BASE_DIR}/${_activeVoiceId.value}")
                .absolutePath
            val llmFileName = Config.LLM_MODELS.find { it.id == _activeLlmId.value }?.fileName
                ?: Config.LLM_MODELS.first().fileName
            val ctxTokens = getContextTokens(_activeLlmId.value)
            val sysPrompt = Config.buildSystemPrompt(_customSystemPrompt.value, _language.value)
            val p = withContext(Dispatchers.Default) {
                VoicePipeline(ctx, voiceDir, llmFileName,
                    systemPrompt  = sysPrompt,
                    contextTokens = ctxTokens,
                    sttLanguage   = _language.value.sttLocale)
            }
            pipeline = p
            _pipelineReady.value = true
            _pipelineLoading.value = false
            p.startWarmup()
        } catch (t: Throwable) {
            _pipelineLoading.value = false
            pipelineStarting = false
            _error.value = "Start fehlgeschlagen: ${t.message}"
        }
    }

    private fun restartPipeline() {
        if (!_ready.value && !_pipelineReady.value && !pipelineStarting) return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            withContext(Dispatchers.Default) { pipeline?.stop() }
            pipeline = null
            _ready.value = false
            _pipelineReady.value = false
            pipelineStarting = false
            _screen.value = Screen.Start
            checkBothReady(ctx)
        }
    }

    // --- Voice actions ---
    fun downloadVoice(id: String) {
        val voice = Config.TTS_VOICES.find { it.id == id } ?: return
        val ctx   = getApplication<Application>()
        val dest  = File(ModelStore.modelsDir(ctx), "${Config.TTS_BASE_DIR}/$id")
        wm.enqueueUniqueWork(voiceWorkName(id), ExistingWorkPolicy.KEEP,
            buildExtractWork(voice.url, dest.absolutePath))
    }

    fun cancelVoiceDownload(id: String) {
        wm.cancelUniqueWork(voiceWorkName(id))
        _voiceStates[id]?.value = ModelState(ModelStatus.Missing)
    }

    fun switchVoice(id: String) {
        if (id == _activeVoiceId.value) return
        val ctx      = getApplication<Application>()
        val voiceDir = File(ModelStore.modelsDir(ctx), "${Config.TTS_BASE_DIR}/$id").absolutePath
        prefs.edit().putString(KEY_ACTIVE_VOICE, id).apply()
        _activeVoiceId.value = id
        pipeline?.switchTtsVoice(voiceDir)
    }

    fun deleteVoice(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            File(ModelStore.modelsDir(ctx), "${Config.TTS_BASE_DIR}/$id").deleteRecursively()
            _voiceStates[id]?.value = ModelState(ModelStatus.Missing)
        }
    }

    // --- LLM actions ---
    fun downloadLlmModel(id: String) {
        val model = Config.LLM_MODELS.find { it.id == id } ?: return
        val ctx   = getApplication<Application>()
        val dest  = File(ModelStore.modelsDir(ctx), model.fileName)
        wm.enqueueUniqueWork(llmWorkName(id), ExistingWorkPolicy.KEEP,
            buildDirectWork(model.url, dest.absolutePath))
    }

    fun cancelLlmDownload(id: String) {
        wm.cancelUniqueWork(llmWorkName(id))
        _llmModelStates[id]?.value = ModelState(ModelStatus.Missing)
    }

    fun switchLlm(id: String) {
        if (id == _activeLlmId.value) return
        prefs.edit().putString(KEY_ACTIVE_LLM, id).apply()
        _activeLlmId.value = id
        restartPipeline()
    }

    fun deleteLlm(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val model = Config.LLM_MODELS.find { it.id == id } ?: return@launch
            File(ModelStore.modelsDir(ctx), model.fileName).delete()
            File(ctx.filesDir, "litert_cache/${model.fileName}").deleteRecursively()
            _llmModelStates[id]?.value = ModelState(ModelStatus.Missing)
        }
    }

    private fun checkBothReady(ctx: Context) {
        val voiceReady = _voiceStates[_activeVoiceId.value]?.value?.status == ModelStatus.Ready
        val llmReady   = _llmModelStates[_activeLlmId.value]?.value?.status == ModelStatus.Ready
        if (voiceReady && llmReady && !_ready.value && !pipelineStarting) {
            pipelineStarting = true
            viewModelScope.launch { startPipeline(ctx) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pipeline?.stop(); pipeline = null
        demoPipeline?.stop(); demoPipeline = null
    }

    // --- Helpers ---
    private fun voiceWorkName(id: String) = "download_tts_$id"
    private fun llmWorkName(id: String)   = "download_llm_$id"
    private fun voiceCheckPath(id: String) = "${Config.TTS_BASE_DIR}/$id/${Config.TTS_MODEL}"

    private fun buildExtractWork(url: String, dest: String) =
        OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(
                DownloadWorker.KEY_URL  to url,
                DownloadWorker.KEY_DEST to dest,
                DownloadWorker.KEY_TYPE to DownloadWorker.TYPE_EXTRACT,
            ))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

    private fun buildDirectWork(url: String, dest: String) =
        OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(
                DownloadWorker.KEY_URL  to url,
                DownloadWorker.KEY_DEST to dest,
                DownloadWorker.KEY_TYPE to DownloadWorker.TYPE_DIRECT,
            ))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

    // --- Wizard ---
    fun startEasySetup() {
        val voiceId = Config.defaultVoiceForLanguage(_language.value)
        if (_activeVoiceId.value != voiceId) {
            prefs.edit().putString(KEY_ACTIVE_VOICE, voiceId).apply()
            _activeVoiceId.value = voiceId
        }
        downloadVoice(voiceId)
        downloadLlmModel(Config.LLM_DEFAULT_MODEL_ID)
    }

    fun finishWizard() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    companion object {
        private const val KEY_ACTIVE_VOICE          = "active_voice"
        private const val KEY_ACTIVE_LLM            = "active_llm"
        private const val KEY_CUSTOM_SYSTEM_PROMPT  = "custom_system_prompt"
        private const val KEY_FIRST_LAUNCH          = "first_launch"
        private const val KEY_LANGUAGE              = "language"
    }
}
