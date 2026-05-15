package com.example.voicellm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voicellm.Config
import com.example.voicellm.R
import com.example.voicellm.pipeline.VoicePipeline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(vm: VoiceViewModel) {
    val screen by vm.screen.collectAsState()
    val ready  by vm.ready.collectAsState()
    val error  by vm.error.collectAsState()
    val lang   by vm.language.collectAsState()

    val strings = when (lang) {
        Config.Language.ENGLISH      -> AppStringsEn
        Config.Language.MULTILINGUAL -> AppStringsMulti
        else                          -> AppStringsDe
    }

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        CompositionLocalProvider(LocalStrings provides strings) {
            Surface(modifier = Modifier.fillMaxSize()) {
                when (screen) {
                    VoiceViewModel.Screen.Start              -> StartScreen(vm)
                    VoiceViewModel.Screen.Settings           -> SettingsScreen(vm, ready, error)
                    VoiceViewModel.Screen.VoiceChat          -> VoiceChatScreen(vm, error)
                    VoiceViewModel.Screen.History            -> HistoryScreen(vm)
                    VoiceViewModel.Screen.VoiceSettings      -> VoiceSettingsScreen(vm)
                    VoiceViewModel.Screen.LlmSettings        -> LlmSettingsScreen(vm)
                    VoiceViewModel.Screen.SystemPromptSettings     -> SystemPromptSettingsScreen(vm)
                    VoiceViewModel.Screen.Wizard                   -> WizardScreen(vm)
                    VoiceViewModel.Screen.Info                     -> InfoScreen(vm)
                    VoiceViewModel.Screen.Language                 -> LanguageScreen(vm)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Wizard-Screen (Erststart)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WizardScreen(vm: VoiceViewModel) {
    var step by remember { mutableStateOf(0) }

    when (step) {
        0    -> WizardLanguageStep(vm = vm, onNext = { step = 1 })
        else -> WizardSetupStep(vm = vm)
    }
}

@Composable
private fun WizardLanguageStep(vm: VoiceViewModel, onNext: () -> Unit) {
    val lang by vm.language.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 32.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Spacer(Modifier.height(24.dp))
            // Title always shown in both languages since no preference set yet
            Text("Sprache wählen  ·  Choose Language",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Assistent-Sprache und Spracherkennung\nAssistant language and speech recognition",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            listOf(
                Triple(Config.Language.DEUTSCH,      "🇩🇪", "Deutsch"),
                Triple(Config.Language.ENGLISH,      "🇬🇧", "English"),
                Triple(Config.Language.MULTILINGUAL, "🌐", "Multilingual"),
            ).forEach { (language, flag, label) ->
                val selected = lang == language
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable {
                            vm.setLanguage(language)
                            onNext()
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(flag, style = MaterialTheme.typography.headlineMedium)
                        Text(label, style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f))
                        if (selected)
                            Icon(Icons.Default.Check, null,
                                tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardSetupStep(vm: VoiceViewModel) {
    val S        = LocalStrings.current
    val language by vm.language.collectAsState()
    val voiceId  = Config.defaultVoiceForLanguage(language)
    val llmId    = Config.LLM_DEFAULT_MODEL_ID
    val voiceSt  by (vm.voiceStates[voiceId]  ?: return).collectAsState()
    val llmSt    by (vm.llmModelStates[llmId] ?: return).collectAsState()

    val active = listOf(
        VoiceViewModel.ModelStatus.Downloading,
        VoiceViewModel.ModelStatus.Extracting,
    )
    val downloading = voiceSt.status in active || llmSt.status in active
    val voiceReady  = voiceSt.status == VoiceViewModel.ModelStatus.Ready
    val llmReady    = llmSt.status   == VoiceViewModel.ModelStatus.Ready
    val bothReady   = voiceReady && llmReady

    LaunchedEffect(bothReady) {
        if (bothReady) {
            vm.finishWizard()
            vm.navigateTo(VoiceViewModel.Screen.Start)
        }
    }

    fun fmtBytes(b: Long): String = when {
        b >= 1_000_000_000L -> "%.1f GB".format(b / 1_000_000_000.0)
        b >= 1_000_000L     -> "%.0f MB".format(b / 1_000_000.0)
        else                -> "$b B"
    }

    fun stateLabel(st: VoiceViewModel.ModelState, name: String): String = when (st.status) {
        VoiceViewModel.ModelStatus.Downloading ->
            if (st.total > 0) "$name: ${fmtBytes(st.bytes)} / ${fmtBytes(st.total)}"
            else "$name: ${fmtBytes(st.bytes)} …"
        VoiceViewModel.ModelStatus.Extracting  -> "$name: ${S.wizardExtracting}"
        VoiceViewModel.ModelStatus.Ready       -> "$name: ${S.wizardReady}"
        VoiceViewModel.ModelStatus.Error       -> "$name: ${S.errorLabel} – ${st.error}"
        else -> "$name: …"
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 32.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter   = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier  = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Spacer(Modifier.height(24.dp))
            Text(S.wizardWelcome, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                S.wizardSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            if (!downloading && !bothReady) {
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(S.wizardEasySetupInfo,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold)
                        Text(S.wizardEasySetupLlm,
                            style = MaterialTheme.typography.bodySmall)
                        Text(S.wizardEasySetupVoice,
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Text(S.wizardEasySetupTotal,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { vm.startEasySetup() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(S.wizardStartEasySetup) }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { vm.finishWizard(); vm.navigateTo(VoiceViewModel.Screen.Start) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(S.wizardSkip) }
            } else {
                val voicePct = if (voiceSt.total > 0) voiceSt.bytes.toFloat() / voiceSt.total else -1f
                val llmPct   = if (llmSt.total   > 0) llmSt.bytes.toFloat()   / llmSt.total   else -1f
                val combined: Float = when {
                    voiceReady && llmPct >= 0    -> (1f + llmPct) / 2f
                    voiceReady                   -> 0.5f
                    llmReady   && voicePct >= 0  -> (voicePct + 1f) / 2f
                    voicePct >= 0 && llmPct >= 0 -> (voicePct + llmPct) / 2f
                    llmPct >= 0                  -> llmPct / 2f
                    else                         -> -1f
                }

                if (combined >= 0)
                    LinearProgressIndicator(progress = { combined },
                        modifier = Modifier.fillMaxWidth())
                else
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                Spacer(Modifier.height(12.dp))
                Text(stateLabel(llmSt,   S.wizardModelLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stateLabel(voiceSt, S.wizardVoiceLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (bothReady) {
                    Spacer(Modifier.height(8.dp))
                    Text(S.wizardStarting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Start-Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartScreen(vm: VoiceViewModel) {
    val S               = LocalStrings.current
    val pipelineReady   by vm.pipelineReady.collectAsState()
    val pipelineLoading by vm.pipelineLoading.collectAsState()
    val error           by vm.error.collectAsState()
    val activeLlmId     by vm.activeLlmId.collectAsState()
    val activeVoiceId   by vm.activeVoiceId.collectAsState()
    val llmState        by vm.llmModelStates[activeLlmId]!!.collectAsState()
    val voiceState      by vm.voiceStates[activeVoiceId]!!.collectAsState()

    val modelsReady = llmState.status == VoiceViewModel.ModelStatus.Ready &&
                      voiceState.status == VoiceViewModel.ModelStatus.Ready
    val modelsChecking = llmState.status == VoiceViewModel.ModelStatus.Checking ||
                         voiceState.status == VoiceViewModel.ModelStatus.Checking ||
                         llmState.status == VoiceViewModel.ModelStatus.Unknown ||
                         voiceState.status == VoiceViewModel.ModelStatus.Unknown

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = { vm.navigateTo(VoiceViewModel.Screen.Settings) }) {
                        Icon(Icons.Default.Settings, S.settingsTitle)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0D1B2A)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "Kern",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Light,
                letterSpacing = 6.sp,
            )
            Text(
                S.startSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(56.dp))

            when {
                error != null -> {
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(error!!, modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { vm.navigateTo(VoiceViewModel.Screen.Settings) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(S.startOpenSettings) }
                }
                pipelineReady -> {
                    Button(
                        onClick  = { vm.beginConversation() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(S.startBeginConversation, style = MaterialTheme.typography.titleSmall)
                    }
                }
                pipelineLoading -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Text(
                        S.startLoadingModel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                modelsChecking -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Text(
                        S.startChecking,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                !modelsReady -> {
                    Text(
                        S.startModelNotInstalled,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick  = { vm.navigateTo(VoiceViewModel.Screen.Settings) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(S.startSetupModels, style = MaterialTheme.typography.titleSmall)
                    }
                }
                else -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Text(
                        S.startPreparing,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings-Screen (Hub)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(vm: VoiceViewModel, ready: Boolean, error: String?) {
    val S               = LocalStrings.current
    val activeVoiceId   by vm.activeVoiceId.collectAsState()
    val activeLlmId     by vm.activeLlmId.collectAsState()
    val pipelineLoading by vm.pipelineLoading.collectAsState()
    val pipelineReady   by vm.pipelineReady.collectAsState()

    BackHandler(enabled = ready) { vm.navigateTo(VoiceViewModel.Screen.VoiceChat) }

    val activeVoiceName = Config.TTS_VOICES.find { it.id == activeVoiceId }?.displayName ?: activeVoiceId
    val activeLlmName   = Config.LLM_MODELS.find { it.id == activeLlmId }?.displayName   ?: activeLlmId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(S.settingsTitle) },
                navigationIcon = {
                    if (ready) {
                        IconButton(onClick = { vm.navigateTo(VoiceViewModel.Screen.VoiceChat) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, S.settingsBackToChat)
                        }
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let {
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(it, modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            // STT
            Text(S.settingsSpeechInput, style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(S.settingsSttTitle, style = MaterialTheme.typography.titleSmall)
                        Text(S.settingsSttSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            // LLM
            Text(S.settingsLanguageModel, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp))
            NavCard(
                title    = S.settingsManageModels,
                subtitle = "${S.settingsActivePrefix}$activeLlmName",
                onClick  = { vm.navigateTo(VoiceViewModel.Screen.LlmSettings) },
            )

            // Stimmen
            Text(S.settingsOutputVoices, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp))
            NavCard(
                title    = S.settingsManageVoices,
                subtitle = "${S.settingsActivePrefix}$activeVoiceName",
                onClick  = { vm.navigateTo(VoiceViewModel.Screen.VoiceSettings) },
            )

            // App
            Text(S.settingsApp, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp))
            NavCard(
                title    = S.settingsLanguage,
                subtitle = S.settingsLanguageSubtitle,
                onClick  = { vm.navigateTo(VoiceViewModel.Screen.Language) },
            )
            NavCard(
                title    = S.settingsInfo,
                subtitle = S.settingsInfoSubtitle,
                onClick  = { vm.navigateTo(VoiceViewModel.Screen.Info) },
            )

            if (pipelineLoading) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(S.settingsLoadingModel,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            if (ready) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick  = { vm.navigateTo(VoiceViewModel.Screen.VoiceChat) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(S.settingsGoToChat) }
            } else if (pipelineReady) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick  = { vm.beginConversation() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(S.startBeginConversation) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Info-Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoScreen(vm: VoiceViewModel) {
    val S   = LocalStrings.current
    val ctx = LocalContext.current
    val versionName = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "–" }
        catch (_: Exception) { "–" }
    }

    BackHandler { vm.navigateTo(VoiceViewModel.Screen.Settings) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(S.infoTitle) },
                navigationIcon = {
                    IconButton(onClick = { vm.navigateTo(VoiceViewModel.Screen.Settings) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back)
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Kern", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
                Text("Version $versionName",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(S.infoSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()) {
                Text(S.infoUsed, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Text("• LiteRT LLM SDK (Google)",
                    style = MaterialTheme.typography.bodySmall)
                Text("• sherpa-onnx – Piper TTS",
                    style = MaterialTheme.typography.bodySmall)
                Text("• Gemma 4 (Google DeepMind)",
                    style = MaterialTheme.typography.bodySmall)
                Text("• Android SpeechRecognizer",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NavCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LLM-Settings-Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LlmSettingsScreen(vm: VoiceViewModel) {
    val S           = LocalStrings.current
    val activeLlmId by vm.activeLlmId.collectAsState()
    val systemBuffer = 2L * 1024 * 1024 * 1024
    val totalRam     = vm.deviceTotalRamBytes

    BackHandler { vm.navigateTo(VoiceViewModel.Screen.Settings) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(S.llmTitle) },
                navigationIcon = {
                    IconButton(onClick = { vm.navigateTo(VoiceViewModel.Screen.Settings) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back)
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                S.llmDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Config.LLM_MODELS.forEach { model ->
                val state         by vm.llmModelStates[model.id]!!.collectAsState()
                val savedTokens   = vm.getContextTokens(model.id)
                var contextTokens by remember(model.id) { mutableIntStateOf(savedTokens) }
                val maxByRam = ((totalRam - systemBuffer - model.modelRamBytes) /
                    model.kvCacheBytesPerToken).toInt()
                    .coerceIn(model.contextTokenStep, model.maxContextTokens)

                LlmModelCard(
                    S               = S,
                    model           = model,
                    state           = state,
                    isActive        = model.id == activeLlmId,
                    contextTokens   = contextTokens,
                    savedTokens     = savedTokens,
                    maxByRam        = maxByRam,
                    onDownload      = { vm.downloadLlmModel(model.id) },
                    onCancel        = { vm.cancelLlmDownload(model.id) },
                    onSwitch        = { vm.switchLlm(model.id) },
                    onDelete        = { vm.deleteLlm(model.id) },
                    onContextChange = { v -> contextTokens = v },
                    onContextApply  = { vm.applyContextTokens(model.id, contextTokens) },
                )
            }
        }
    }
}

@Composable
private fun LlmModelCard(
    S: AppStrings,
    model: Config.LlmModel,
    state: VoiceViewModel.ModelState,
    isActive: Boolean,
    contextTokens: Int,
    savedTokens: Int,
    maxByRam: Int,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onSwitch: () -> Unit,
    onDelete: () -> Unit,
    onContextChange: (Int) -> Unit,
    onContextApply: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(model.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    isActive ->
                        Icon(Icons.Default.CheckCircle, S.activeLabel,
                            tint = MaterialTheme.colorScheme.primary)
                    state.status == VoiceViewModel.ModelStatus.Downloading ||
                    state.status == VoiceViewModel.ModelStatus.Extracting ->
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    state.status == VoiceViewModel.ModelStatus.Error ->
                        Icon(Icons.Default.Stop, S.errorLabel,
                            tint = MaterialTheme.colorScheme.error)
                    else -> {}
                }
            }

            when (state.status) {
                VoiceViewModel.ModelStatus.Downloading -> {
                    val ratio = if (state.total > 0) state.bytes.toFloat() / state.total else null
                    if (ratio != null) {
                        LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                        Text("${(ratio * 100).toInt()} %  •  ${fmtBytes(state.bytes)} / ${fmtBytes(state.total)}",
                            style = MaterialTheme.typography.labelSmall)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("${fmtBytes(state.bytes)} ${S.llmLoaded}",
                            style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = onCancel) { Text(S.cancel) }
                }
                VoiceViewModel.ModelStatus.Ready -> {
                    if (!isActive) {
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onSwitch, modifier = Modifier.weight(1f)) {
                                Text(S.activate)
                            }
                            OutlinedButton(onClick = onDelete) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                VoiceViewModel.ModelStatus.Error -> {
                    Text("${S.errorLabel}: ${state.error}", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onDownload) { Text(S.retry) }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onDownload, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(S.download)
                        }
                        Text(model.sizeHint, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Voice-Settings-Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceSettingsScreen(vm: VoiceViewModel) {
    val S             = LocalStrings.current
    val activeVoiceId by vm.activeVoiceId.collectAsState()
    val language      by vm.language.collectAsState()
    val voices        = Config.voicesForLanguage(language)

    BackHandler { vm.navigateTo(VoiceViewModel.Screen.Settings) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(S.voiceTitle) },
                navigationIcon = {
                    IconButton(onClick = { vm.navigateTo(VoiceViewModel.Screen.Settings) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back)
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                S.voiceDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            voices.forEach { voice ->
                val state by vm.voiceStates[voice.id]!!.collectAsState()
                VoiceCard(
                    S          = S,
                    voice      = voice,
                    state      = state,
                    isActive   = voice.id == activeVoiceId,
                    onDownload = { vm.downloadVoice(voice.id) },
                    onCancel   = { vm.cancelVoiceDownload(voice.id) },
                    onSwitch   = { vm.switchVoice(voice.id) },
                    onDelete   = { vm.deleteVoice(voice.id) },
                )
            }
        }
    }
}

@Composable
private fun VoiceCard(
    S: AppStrings,
    voice: Config.Voice,
    state: VoiceViewModel.ModelState,
    isActive: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onSwitch: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(voice.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(voice.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    isActive ->
                        Icon(Icons.Default.CheckCircle, S.activeLabel,
                            tint = MaterialTheme.colorScheme.primary)
                    state.status == VoiceViewModel.ModelStatus.Downloading ||
                    state.status == VoiceViewModel.ModelStatus.Extracting ->
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    state.status == VoiceViewModel.ModelStatus.Error ->
                        Icon(Icons.Default.Stop, S.errorLabel, tint = MaterialTheme.colorScheme.error)
                    else -> {}
                }
            }

            when (state.status) {
                VoiceViewModel.ModelStatus.Downloading -> {
                    val ratio = if (state.total > 0) state.bytes.toFloat() / state.total else null
                    if (ratio != null) {
                        LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                        Text("${(ratio * 100).toInt()} %  •  ${fmtBytes(state.bytes)} / ${fmtBytes(state.total)}",
                            style = MaterialTheme.typography.labelSmall)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("${fmtBytes(state.bytes)} ${S.llmLoaded}",
                            style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = onCancel) { Text(S.cancel) }
                }
                VoiceViewModel.ModelStatus.Extracting -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(S.voiceExtracting, style = MaterialTheme.typography.labelSmall)
                }
                VoiceViewModel.ModelStatus.Ready -> {
                    if (!isActive) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onSwitch, modifier = Modifier.weight(1f)) {
                                Text(S.activate)
                            }
                            OutlinedButton(onClick = onDelete) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                VoiceViewModel.ModelStatus.Error -> {
                    Text("${S.errorLabel}: ${state.error}", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onDownload) { Text(S.retry) }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onDownload, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(S.download)
                        }
                        Text(voice.sizeHint, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Voice-Chat-Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceChatScreen(vm: VoiceViewModel, error: String?) {
    val S = LocalStrings.current
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(S.vcNewConversationTitle) },
            text  = { Text(S.vcNewConversationText) },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetConversation()
                    showResetDialog = false
                }) { Text(S.vcReset, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(S.cancel) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (Config.DEMO_MODE) S.vcTitleDemo else S.vcTitle)
                },
                actions = {
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Default.Refresh, S.vcNewConvBtn)
                    }
                    IconButton(onClick = { vm.navigateTo(VoiceViewModel.Screen.History) }) {
                        Icon(Icons.Default.History, S.vcHistoryBtn)
                    }
                    IconButton(onClick = { vm.navigateTo(VoiceViewModel.Screen.Settings) }) {
                        Icon(Icons.Default.Settings, S.vcSettingsBtn)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            error?.let {
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(it, modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            ConversationView(vm)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// History-Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(vm: VoiceViewModel) {
    val S        = LocalStrings.current
    val history  by (vm.history ?: return).collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    BackHandler { vm.navigateTo(VoiceViewModel.Screen.VoiceChat) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(S.histResetTitle) },
            text  = { Text(S.histResetText) },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetConversation()
                    showClearDialog = false
                    vm.navigateTo(VoiceViewModel.Screen.VoiceChat)
                }) { Text(S.histResetBtn, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(S.cancel) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(S.histTitle) },
                navigationIcon = {
                    IconButton(onClick = { vm.navigateTo(VoiceViewModel.Screen.VoiceChat) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back)
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.Delete, S.histDeleteHistory,
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (history.isEmpty()) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 48.dp),
                    contentAlignment = Alignment.Center) {
                    Text(S.histNoConversation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(
                    S.histCurrentSession,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    history.forEach { msg -> ChatBubble(msg.isUser, msg.text) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ChatBubble(isUser: Boolean, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Conversation view
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConversationView(vm: VoiceViewModel) {
    val S            = LocalStrings.current
    val stateFlow    = vm.state
    val partialFlow  = vm.partialTranscript
    val assistantFlow = vm.assistantText
    val mutedFlow    = vm.muted

    if (stateFlow == null || partialFlow == null || assistantFlow == null || mutedFlow == null) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
            contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val state     by stateFlow.collectAsState()
    val partial   by partialFlow.collectAsState()
    val assistant by assistantFlow.collectAsState()
    val muted     by mutedFlow.collectAsState()
    var inputText by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(state)
            Spacer(Modifier.weight(1f))
            if (state == VoicePipeline.State.Speaking) {
                IconButton(onClick = { vm.stopSpeaking() }) {
                    Icon(Icons.Default.Stop, S.convStopOutput,
                        tint = MaterialTheme.colorScheme.error)
                }
            }
            IconButton(onClick = { vm.toggleMute() }) {
                Icon(
                    imageVector = if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (muted) S.convMicOn else S.convMicOff,
                    tint = if (muted) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (partial.isNotBlank()) Text("${S.convYouPrefix}$partial", style = MaterialTheme.typography.bodyLarge)
        if (assistant.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("${S.convAssistantPrefix}$assistant", modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (Config.DEMO_MODE) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = inputText, onValueChange = { inputText = it },
                    label = { Text(S.convEnterMessage) }, singleLine = true,
                    modifier = Modifier.weight(1f))
                Button(
                    onClick = { if (inputText.isNotBlank()) { vm.sendDemoText(inputText); inputText = "" } },
                    enabled = state != VoicePipeline.State.Thinking && state != VoicePipeline.State.Speaking,
                ) { Text(S.convSend) }
            }
        }
    }
}

@Composable
private fun StatusChip(state: VoicePipeline.State) {
    val S = LocalStrings.current
    val (label, icon) = when (state) {
        VoicePipeline.State.Idle      -> S.convReady     to Icons.Default.Mic
        VoicePipeline.State.Listening -> S.convListening to Icons.Default.Mic
        VoicePipeline.State.Thinking  -> S.convThinking  to Icons.Default.Stop
        VoicePipeline.State.Speaking  -> S.convSpeaking  to Icons.Default.Stop
    }
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.wrapContentSize()) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// System-Prompt-Settings-Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SystemPromptSettingsScreen(vm: VoiceViewModel) {
    val S     = LocalStrings.current
    val lang  by vm.language.collectAsState()
    val saved by vm.customSystemPrompt.collectAsState()
    var text  by remember(saved) { mutableStateOf(saved) }
    val dirty = text != saved

    BackHandler { vm.navigateTo(VoiceViewModel.Screen.Settings) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(S.sysPromptTitle) },
                navigationIcon = {
                    IconButton(onClick = { vm.navigateTo(VoiceViewModel.Screen.Settings) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back)
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                S.sysPromptHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                label         = { Text(S.sysPromptLabel) },
                placeholder   = { Text(Config.defaultSystemPrompt(lang),
                    color = MaterialTheme.colorScheme.onSurfaceVariant) },
                minLines      = 6,
                modifier      = Modifier.fillMaxWidth(),
            )
            if (text.isNotBlank() && text != saved) {
                Text(
                    "${S.sysPromptPreview}\n${text.trim()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick  = { vm.setCustomSystemPrompt(text.trim()) },
                    enabled  = dirty,
                    modifier = Modifier.weight(1f),
                ) { Text(S.sysPromptSaveRestart) }
                if (saved.isNotBlank()) {
                    OutlinedButton(onClick = { text = ""; vm.setCustomSystemPrompt("") }) {
                        Text(S.sysPromptClearBtn)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Language-Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageScreen(vm: VoiceViewModel) {
    val S    = LocalStrings.current
    val lang by vm.language.collectAsState()

    BackHandler { vm.navigateTo(VoiceViewModel.Screen.Settings) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(S.langTitle) },
                navigationIcon = {
                    IconButton(onClick = { vm.navigateTo(VoiceViewModel.Screen.Settings) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back)
                    }
                },
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(S.langSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            listOf(
                Config.Language.DEUTSCH      to (S.langDeTitle    to S.langDeDesc),
                Config.Language.ENGLISH      to (S.langEnTitle    to S.langEnDesc),
                Config.Language.MULTILINGUAL to (S.langMultiTitle to S.langMultiDesc),
            ).forEach { (l, td) ->
                val (title, desc) = td
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { vm.setLanguage(l) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (lang == l)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            Text(desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (lang == l) {
                            Icon(Icons.Default.Check, null,
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(S.langRestartNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun fmtBytes(n: Long): String {
    if (n < 0) return "?"
    val mb = n / 1_048_576.0
    return if (mb >= 1024) "%.2f GB".format(mb / 1024) else "%.1f MB".format(mb)
}
