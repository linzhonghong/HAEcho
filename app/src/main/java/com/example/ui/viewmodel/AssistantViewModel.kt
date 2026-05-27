package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.service.HomeAssistantClient
import com.example.service.SpeechController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AssistantState {
    IDLE,
    LISTENING_WAKE,
    WAKEN_ACTIVE,
    PROCESSING
}

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val repository = AppRepository(db)

    // Configuration states
    private val _serverUrl = MutableStateFlow("http://192.168.1.100:8123")
    val serverUrl = _serverUrl.asStateFlow()

    private val _accessToken = MutableStateFlow("")
    val accessToken = _accessToken.asStateFlow()

    private val _connectionMode = MutableStateFlow("Assist Satellite") // Assist Satellite or Direct Pipeline
    val connectionMode = _connectionMode.asStateFlow()

    private val _playbackMode = MutableStateFlow("App") // App, Media player, Automation
    val playbackMode = _playbackMode.asStateFlow()

    private val _mediaPlayerId = MutableStateFlow("media_player.living_room_speaker")
    val mediaPlayerId = _mediaPlayerId.asStateFlow()

    private val _maxTurns = MutableStateFlow(5)
    val maxTurns = _maxTurns.asStateFlow()

    private val _timeoutSeconds = MutableStateFlow(15)
    val timeoutSeconds = _timeoutSeconds.asStateFlow()

    private val _wakeWord = MutableStateFlow("想")
    val wakeWord = _wakeWord.asStateFlow()

    private val _wakeResponses = MutableStateFlow("在的\n我在\n随时听候您的吩咐")
    val wakeResponses = _wakeResponses.asStateFlow()

    private val _voiceReplyEnabled = MutableStateFlow(true)
    val voiceReplyEnabled = _voiceReplyEnabled.asStateFlow()

    // Runtime states
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning = _isServiceRunning.asStateFlow()

    private val _connectionStatus = MutableStateFlow("未连接")
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _isConnectionOk = MutableStateFlow(false)
    val isConnectionOk = _isConnectionOk.asStateFlow()

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState = _assistantState.asStateFlow()

    private val _dialogueTurnCount = MutableStateFlow(0)
    val dialogueTurnCount = _dialogueTurnCount.asStateFlow()

    private val _isListeningNow = MutableStateFlow(false)
    val isListeningNow = _isListeningNow.asStateFlow()

    private val _listeningHint = MutableStateFlow("未启动")
    val listeningHint = _listeningHint.asStateFlow()

    private val _lastUserMessage = MutableStateFlow("")
    val lastUserMessage = _lastUserMessage.asStateFlow()

    private val _lastReplyMessage = MutableStateFlow("")
    val lastReplyMessage = _lastReplyMessage.asStateFlow()

    // Live logs list
    val logList = repository.allLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private var haClient: HomeAssistantClient? = null
    private var speechController: SpeechController? = null
    private var timeoutJob: Job? = null

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _serverUrl.value = repository.getSetting("server_url", "http://192.168.1.100:8123")
            _accessToken.value = repository.getSetting("access_token", "")
            _connectionMode.value = repository.getSetting("connection_mode", "Assist Satellite")
            _playbackMode.value = repository.getSetting("playback_mode", "App")
            _mediaPlayerId.value = repository.getSetting("media_player_id", "media_player.living_room_speaker")
            _maxTurns.value = repository.getSetting("max_turns", "5").toIntOrNull() ?: 5
            _timeoutSeconds.value = repository.getSetting("timeout_seconds", "15").toIntOrNull() ?: 15
            _wakeWord.value = repository.getSetting("wake_word", "想")
            _wakeResponses.value = repository.getSetting("wake_responses", "在的\n我在\n随时听候您的吩咐")
            _voiceReplyEnabled.value = repository.getSetting("voice_reply_enabled", "true") == "true"
        }
    }

    fun saveConfig(
        url: String,
        token: String,
        mode: String,
        playback: String,
        mediaId: String,
        turns: Int,
        timeout: Int,
        word: String,
        responses: String,
        voiceReply: Boolean
    ) {
        viewModelScope.launch {
            _serverUrl.value = url
            _accessToken.value = token
            _connectionMode.value = mode
            _playbackMode.value = playback
            _mediaPlayerId.value = mediaId
            _maxTurns.value = turns
            _timeoutSeconds.value = timeout
            _wakeWord.value = word
            _wakeResponses.value = responses
            _voiceReplyEnabled.value = voiceReply

            repository.saveSetting("server_url", url)
            repository.saveSetting("access_token", token)
            repository.saveSetting("connection_mode", mode)
            repository.saveSetting("playback_mode", playback)
            repository.saveSetting("media_player_id", mediaId)
            repository.saveSetting("max_turns", turns.toString())
            repository.saveSetting("timeout_seconds", timeout.toString())
            repository.saveSetting("wake_word", word)
            repository.saveSetting("wake_responses", responses)
            repository.saveSetting("voice_reply_enabled", voiceReply.toString())

            repository.addLog("INFO", "Settings", "配置保存成功")

            // Restart service if it was running to apply config
            if (_isServiceRunning.value) {
                stopAssistant()
                startAssistant()
            }
        }
    }

    fun toggleAssistant() {
        if (_isServiceRunning.value) {
            stopAssistant()
        } else {
            startAssistant()
        }
    }

    private fun startAssistant() {
        _isServiceRunning.value = true
        _assistantState.value = AssistantState.LISTENING_WAKE
        _dialogueTurnCount.value = 0

        // 1. Initialize HA connection client
        haClient = HomeAssistantClient(
            context = getApplication(),
            repository = repository,
            onConnectionStatusChanged = { ok, msg ->
                viewModelScope.launch {
                    _isConnectionOk.value = ok
                    _connectionStatus.value = msg
                }
            },
            onAssistResponse = { reply, audioUrl ->
                viewModelScope.launch {
                    if (reply.isNotBlank() || audioUrl != null) {
                        _assistantState.value = AssistantState.WAKEN_ACTIVE
                        if (reply.isNotBlank()) {
                            _lastReplyMessage.value = reply
                        }
                        handlePlayback(reply, audioUrl)
                        resetTimeoutTimer()
                    }
                }
            }
        )

        haClient?.connect(_serverUrl.value, _accessToken.value)

        // 2. Initialize and configure local voice controller
        val respList = _wakeResponses.value.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        speechController = SpeechController(
            context = getApplication(),
            repository = repository,
            onWakeWordDetected = {
                viewModelScope.launch {
                    handleScreenWakeUp()
                }
            },
            onCommandRecognized = { text ->
                viewModelScope.launch {
                    handleCommandText(text)
                }
            },
            onListeningStateChanged = { listening, hint ->
                viewModelScope.launch {
                    _isListeningNow.value = listening
                    _listeningHint.value = hint
                }
            }
        ).apply {
            setConfig(
                wakeWord = _wakeWord.value,
                responses = respList,
                voiceReplyEnabled = _voiceReplyEnabled.value
            )
            startListening(wakeMode = true)
        }
    }

    fun stopAssistant() {
        _isServiceRunning.value = false
        _assistantState.value = AssistantState.IDLE
        _dialogueTurnCount.value = 0
        _isListeningNow.value = false
        _listeningHint.value = "未开启助手机制"

        haClient?.disconnect()
        haClient = null

        speechController?.stopListening()
        speechController?.destroy()
        speechController = null

        timeoutJob?.cancel()
        timeoutJob = null
    }

    private fun handleScreenWakeUp() {
        viewModelScope.launch {
            _assistantState.value = AssistantState.WAKEN_ACTIVE
            _dialogueTurnCount.value = 0
            _lastUserMessage.value = "（唤醒卫星助手中...）"
            _lastReplyMessage.value = "我正在听，请讲..."

            repository.addLog("SUCCESS", "System", "助手已唤醒！触发全屏 iPhone Siri 光晕边缘视觉")

            // Play voice reaction
            speechController?.speakRandomWakeResponse()

            // Delay slightly for speech to finish, then capture the user command!
            delay(1200)

            // Start command capture
            speechController?.startListening(wakeMode = false)
            resetTimeoutTimer()
        }
    }

    private fun handleCommandText(text: String) {
        viewModelScope.launch {
            if (text.isBlank()) return@launch
            _lastUserMessage.value = text
            _assistantState.value = AssistantState.PROCESSING

            // Send command text to HA Assist pipeline over WebSocket connection
            haClient?.sendAssistCommand(text)
            resetTimeoutTimer()
        }
    }

    private fun handlePlayback(responseText: String, audioUrlPath: String?) {
        viewModelScope.launch {
            val fullAudioUrl = if (audioUrlPath != null) {
                val cleanServer = _serverUrl.value.trim().removeSuffix("/")
                "$cleanServer$audioUrlPath"
            } else null

            repository.addLog("INFO", "Playback", "处理音频：播放模式=${_playbackMode.value}, URL=$fullAudioUrl")

            when (_playbackMode.value) {
                "App" -> {
                    // App playing mode
                    if (fullAudioUrl != null) {
                        speechController?.playAudioUrl(fullAudioUrl)
                    } else if (responseText.isNotBlank()) {
                        speechController?.speakText(responseText)
                    }
                }
                "Media player" -> {
                    // Speak on remote HA media player entity!
                    if (fullAudioUrl != null) {
                        haClient?.playOnMediaPlayer(_mediaPlayerId.value, fullAudioUrl)
                    } else if (responseText.isNotBlank()) {
                        // fallback to localized speaking if no HA TTS url pathway can be established
                        speechController?.speakText(responseText)
                    }
                }
                "Automation" -> {
                    // Trigger custom or default Automation entity in Home Assistant
                    haClient?.triggerHAAutomation("automation.satellite_wake_event")
                    speechController?.speakText("已触发自动化")
                }
            }

            // Dialogue round increments
            _dialogueTurnCount.value += 1
            if (_dialogueTurnCount.value >= _maxTurns.value) {
                repository.addLog("INFO", "System", "达到最大对话轮次(${_maxTurns.value})，休眠进入监测唤醒词阶段")
                returnToWakeMonitoring()
            } else {
                // Not reached max turns, wait 3 seconds and listen again for multi-round convenience!
                delay(4000)
                if (_assistantState.value == AssistantState.WAKEN_ACTIVE) {
                    speechController?.startListening(wakeMode = false)
                }
            }
        }
    }

    private fun returnToWakeMonitoring() {
        _assistantState.value = AssistantState.LISTENING_WAKE
        _dialogueTurnCount.value = 0
        speechController?.startListening(wakeMode = true)
        timeoutJob?.cancel()
    }

    private fun resetTimeoutTimer() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(_timeoutSeconds.value * 1000L)
            repository.addLog("WARN", "System", "对话超时未响应(${_timeoutSeconds.value}秒)，自动退出唤醒状态")
            returnToWakeMonitoring()
        }
    }

    // Manual quick-trigger button click so users can test immediately
    fun triggerManualWake() {
        if (!_isServiceRunning.value) {
            viewModelScope.launch {
                repository.addLog("WARN", "System", "请先启动卫星助手服务")
            }
            return
        }
        speechController?.stopListening()
        handleScreenWakeUp()
    }

    fun submitCommandDirectly(text: String) {
        if (!_isServiceRunning.value) {
            viewModelScope.launch {
                repository.addLog("WARN", "System", "请先启动卫星助手服务以发送指令")
            }
            return
        }
        speechController?.stopListening()
        // Force state transition to processing
        viewModelScope.launch {
            _dialogueTurnCount.value = 0
            handleCommandText(text)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAssistant()
    }
}
