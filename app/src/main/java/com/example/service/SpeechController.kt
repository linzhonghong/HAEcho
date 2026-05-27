package com.example.service

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.data.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class SpeechController(
    private val context: Context,
    private val repository: AppRepository,
    private val onWakeWordDetected: () -> Unit,
    private val onCommandRecognized: (String) -> Unit,
    private val onListeningStateChanged: (Boolean, String) -> Unit
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isListening = false
    private var isWakeMode = true // true = waiting for wake word, false = capturing command
    private var wakeWord = "想" // default wake word
    private var wakeResponseList = listOf("在的", "我在", "请吩咐")
    private var voiceReplyEnabled = true
    private var mediaPlayer: MediaPlayer? = null
    private var consecutiveErrorCount = 0

    init {
        initializeSpeechRecognizer()
        initializeTextToSpeech()
    }

    fun setConfig(wakeWord: String, responses: List<String>, voiceReplyEnabled: Boolean) {
        this.wakeWord = wakeWord.trim().lowercase()
        if (responses.isNotEmpty()) {
            this.wakeResponseList = responses
        }
        this.voiceReplyEnabled = voiceReplyEnabled
    }

    private fun initializeSpeechRecognizer() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            CoroutineScope(Dispatchers.Main).launch {
                initializeSpeechRecognizer()
            }
            return
        }
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(this@SpeechController)
                }
                logInfo("语音识别引擎初始化成功")
            } else {
                logError("当前设备不支持系统语音识别")
            }
        } catch (e: Exception) {
            logError("初始化语音识别失败: ${e.message}")
        }
    }

    private fun initializeTextToSpeech() {
        try {
            tts = TextToSpeech(context) { status ->
                try {
                    if (status == TextToSpeech.SUCCESS) {
                        val result = tts?.setLanguage(Locale.CHINESE)
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            logError("TTS 语言不支持中文，尝试设置默认语言")
                            tts?.language = Locale.getDefault()
                        }
                        logInfo("TTS 语音引擎初始化成功")
                    } else {
                        logError("TTS 语音引擎初始化失败")
                    }
                } catch (e: Exception) {
                    logError("TTS 初始化回调异常: ${e.message}")
                }
            }
        } catch (e: Exception) {
            logError("创建 TextToSpeech 实例失败: ${e.message}")
        }
    }

    fun startListening(wakeMode: Boolean) {
        this.isWakeMode = wakeMode
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            CoroutineScope(Dispatchers.Main).launch {
                startListening(wakeMode)
            }
            return
        }
        
        // Reset the safety latch on a fresh / manual start to allow retry
        if (consecutiveErrorCount >= 5) {
            consecutiveErrorCount = 0
        }
        
        try {
            if (speechRecognizer == null) {
                initializeSpeechRecognizer()
            }
            
            isListening = true
            stopAudioPlaybackSync()

            // Proactively cancel any previous active session to prevent ERROR_RECOGNIZER_BUSY crashes
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                // ignore
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE.toString())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.startListening(intent)
            val stateText = if (wakeMode) "正在监测唤醒词: \"$wakeWord\"" else "正在倾听指令..."
            onListeningStateChanged(true, stateText)
            logInfo("启动语音录制 ($stateText)")
        } catch (e: Exception) {
            logError("启动语音监听失败: ${e.message}")
            onListeningStateChanged(false, "监听出错")
        }
    }

    fun stopListening() {
        isListening = false
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            CoroutineScope(Dispatchers.Main).launch {
                stopListening()
            }
            return
        }
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            onListeningStateChanged(false, "已停止监听")
            logInfo("语音录制已完全停止")
        } catch (e: Exception) {
            logError("停止录音异常: ${e.message}")
        }
    }

    fun speakText(text: String) {
        if (!voiceReplyEnabled) return
        logInfo("本地播放语音: \"$text\"")
        try {
            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sat_tts_id")
            if (result == TextToSpeech.ERROR) {
                logError("TTS 播放时发生错误 (QUEUE_FLUSH 返回了 ERROR)")
            }
        } catch (e: Exception) {
            logError("TTS 语音播发异常: ${e.message}")
        }
    }

    fun playAudioUrl(url: String) {
        if (!voiceReplyEnabled) return
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            CoroutineScope(Dispatchers.Main).launch {
                playAudioUrl(url)
            }
            return
        }
        try {
            logInfo("配置并播放 HA 语音文件: $url")
            stopAudioPlaybackSync()
            
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build()
                )
                setOnPreparedListener { 
                    try {
                        start() 
                    } catch (e: Exception) {
                        logError("无法启动音频播放: ${e.message}")
                    }
                }
                setOnCompletionListener {
                    try {
                        release()
                    } catch (e: Exception) {}
                    if (mediaPlayer == this) {
                        mediaPlayer = null
                    }
                }
                setOnErrorListener { _, what, extra ->
                    logError("MediaPlayer 错误: what=$what, extra=$extra. 尝试本地降级。")
                    speakText(url.substringAfterLast("/"))
                    true
                }
                setDataSource(url)
                prepareAsync()
            }
        } catch (e: Exception) {
            logError("播放音频文件失败: ${e.message}，将降级到本地合成")
            speakText(url.substringAfterLast("/"))
        }
    }

    fun speakRandomWakeResponse() {
        val reply = wakeResponseList.randomOrNull() ?: "在的"
        speakText(reply)
    }

    private fun stopAudioPlaybackSync() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            CoroutineScope(Dispatchers.Main).launch {
                stopAudioPlaybackSync()
            }
            return
        }
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                     it.stop()
                }
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            // ignore
        }
    }

    fun destroy() {
        isListening = false
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            CoroutineScope(Dispatchers.Main).launch {
                destroy()
            }
            return
        }
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // ignore
        }
        speechRecognizer = null
        
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            // ignore
        }
        tts = null
        
        stopAudioPlaybackSync()
    }

    // RecognitionListener Implementations
    override fun onReadyForSpeech(params: Bundle?) {
        try {
            consecutiveErrorCount = 0
            onListeningStateChanged(true, if (isWakeMode) "请说唤醒词: \"$wakeWord\"" else "请说您的指令...")
        } catch (e: Exception) {
            logError("onReadyForSpeech 异常: ${e.message}")
        }
    }

    override fun onBeginningOfSpeech() {
        try {
            consecutiveErrorCount = 0
            onListeningStateChanged(true, if (isWakeMode) "唤醒检测中..." else "正在录音指令...")
        } catch (e: Exception) {
            logError("onBeginningOfSpeech 异常: ${e.message}")
        }
    }

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        try {
            onListeningStateChanged(false, "正在处理声音...")
        } catch (e: Exception) {
            logError("onEndOfSpeech 异常: ${e.message}")
        }
    }

    override fun onError(error: Int) {
        try {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "音频录制错误"
                SpeechRecognizer.ERROR_CLIENT -> "客户端连接异常"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少录音权限"
                SpeechRecognizer.ERROR_NETWORK -> "网络连接异常"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                SpeechRecognizer.ERROR_NO_MATCH -> "无匹配的话语"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音系统忙碌/忙ing"
                SpeechRecognizer.ERROR_SERVER -> "识别服务器错误"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "倾听超时"
                else -> "未知录音错误 (code: $error)"
            }
            
            consecutiveErrorCount++
            logInfo("录音事件/错误: $message (连续错误数: $consecutiveErrorCount)")
            onListeningStateChanged(false, "监听空闲/超时")

            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                try {
                    speechRecognizer?.cancel()
                } catch (e: Exception) {}
            }

            if (consecutiveErrorCount >= 5) {
                logError("检测到语音识别连续发生 5 次错误。为防止应用ANR卡死，已暂停自动轮询（系统无内置/受损/未绑定的语音语音识别引擎等常见原因）。请确认设备支持并开启了麦克风及Google语音服务，然后手动重新点击。")
                // Halts automated recursion to protect device main thread
                isListening = false
                return
            }

            // In Wake Mode, we should auto-restart listening on timeout/no match to behave like a standard continuous satellite!
            if (isListening && isWakeMode) {
                CoroutineScope(Dispatchers.Main).launch {
                    delay(1500)
                    if (isListening && isWakeMode && consecutiveErrorCount < 5) {
                        startListening(wakeMode = true)
                    }
                }
            }
        } catch (e: Exception) {
            logError("onError 异常: ${e.message}")
        }
    }

    override fun onResults(results: Bundle?) {
        try {
            consecutiveErrorCount = 0
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val candidate = matches?.firstOrNull() ?: ""
            if (candidate.isBlank()) {
                if (isListening && isWakeMode) {
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(800)
                        if (isListening && isWakeMode) {
                            startListening(true)
                        }
                    }
                }
                return
            }

            val cleaned = candidate.trim().lowercase()
            logInfo("语音识别得出内容: \"$candidate\"")

            if (isWakeMode) {
                // Wait, does it contain the wake word?
                if (cleaned.contains(wakeWord) || cleaned.contains("hey assist") || cleaned.contains("assistant")) {
                    logSuccess("检测到唤醒词 \"$wakeWord\" inside \"$candidate\"!")
                    onWakeWordDetected()
                } else {
                    // Not the wake word, restart listening with safety delay
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(800)
                        if (isListening && isWakeMode) {
                            startListening(true)
                        }
                    }
                }
            } else {
                // Recognized Command!
                logSuccess("识别出指令: \"$candidate\"")
                onCommandRecognized(candidate)
            }
        } catch (e: Exception) {
            logError("onResults 异常: ${e.message}")
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        try {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val speech = matches?.firstOrNull() ?: ""
            if (speech.isNotBlank() && isWakeMode) {
                consecutiveErrorCount = 0
                val cleaned = speech.trim().lowercase()
                if (cleaned.contains(wakeWord) || cleaned.contains("hey assist") || cleaned.contains("assistant")) {
                    logSuccess("唤醒词部分匹配成功!")
                    // Cancel active and trigger wake
                    stopListening()
                    onWakeWordDetected()
                }
            }
        } catch (e: Exception) {
            logError("onPartialResults 异常: ${e.message}")
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    // Logging helpers
    private fun logInfo(msg: String) {
        CoroutineScope(Dispatchers.IO).launch {
            repository.addLog("INFO", "SpeechController", msg)
        }
    }

    private fun logSuccess(msg: String) {
        CoroutineScope(Dispatchers.IO).launch {
            repository.addLog("SUCCESS", "SpeechController", msg)
        }
    }

    private fun logError(msg: String) {
        CoroutineScope(Dispatchers.IO).launch {
            repository.addLog("ERROR", "SpeechController", msg)
        }
    }
}
