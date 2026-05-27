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
        CoroutineScope(Dispatchers.Main).launch {
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
    }

    private fun initializeTextToSpeech() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINESE
                logInfo("TTS 语音引擎初始化成功")
            } else {
                logError("TTS 语音引擎初始化失败")
            }
        }
    }

    fun startListening(wakeMode: Boolean) {
        this.isWakeMode = wakeMode
        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (speechRecognizer == null) {
                    initializeSpeechRecognizer()
                }
                
                // Stop previous active audio to avoid overlapping
                stopAudioPlayback()

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE.toString())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                speechRecognizer?.startListening(intent)
                isListening = true
                val stateText = if (wakeMode) "正在监测唤醒词: \"$wakeWord\"" else "正在倾听指令..."
                onListeningStateChanged(true, stateText)
                logInfo("启动语音录制 ($stateText)")
            } catch (e: Exception) {
                logError("启动语音监听失败: ${e.message}")
                onListeningStateChanged(false, "监听出错")
            }
        }
    }

    fun stopListening() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                isListening = false
                onListeningStateChanged(false, "已停止监听")
                logInfo("语音录制已完全停止")
            } catch (e: Exception) {
                logError("停止录音异常: ${e.message}")
            }
        }
    }

    fun speakText(text: String) {
        if (!voiceReplyEnabled) return
        logInfo("本地播放语音: \"$text\"")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sat_tts_id")
    }

    fun playAudioUrl(url: String) {
        if (!voiceReplyEnabled) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                logInfo("下载并播放 HA 语音文件: $url")
                stopAudioPlayback()
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .build()
                    )
                    setDataSource(url)
                    prepareAsync()
                    setOnPreparedListener { start() }
                    setOnCompletionListener {
                        release()
                        mediaPlayer = null
                    }
                }
            } catch (e: Exception) {
                logError("播放音频文件失败: ${e.message}，将降级到本地合成")
                // fallback to local TTS on failure
                speakText(url.substringAfterLast("/"))
            }
        }
    }

    fun speakRandomWakeResponse() {
        val reply = wakeResponseList.randomOrNull() ?: "在的"
        speakText(reply)
    }

    private fun stopAudioPlayback() {
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
        CoroutineScope(Dispatchers.Main).launch {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
        tts?.shutdown()
        tts = null
        stopAudioPlayback()
    }

    // RecognitionListener Implementations
    override fun onReadyForSpeech(params: Bundle?) {
        onListeningStateChanged(true, if (isWakeMode) "请说唤醒词: \"$wakeWord\"" else "请说您的指令...")
    }

    override fun onBeginningOfSpeech() {
        onListeningStateChanged(true, if (isWakeMode) "唤醒检测中..." else "正在录音指令...")
    }

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        onListeningStateChanged(false, "正在处理声音...")
    }

    override fun onError(error: Int) {
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
        
        logInfo("录音事件/错误: $message")
        onListeningStateChanged(false, "监听空闲/超时")

        // In Wake Mode, we should auto-restart listening on timeout/no match to behave like a standard continuous satellite!
        if (isListening && isWakeMode) {
            startListening(wakeMode = true)
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val candidate = matches?.firstOrNull() ?: ""
        if (candidate.isBlank()) {
            if (isWakeMode) startListening(true)
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
                // Not the wake word, restart listening
                startListening(true)
            }
        } else {
            // Recognized Command!
            logSuccess("识别出指令: \"$candidate\"")
            onCommandRecognized(candidate)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val speech = matches?.firstOrNull() ?: ""
        if (speech.isNotBlank() && isWakeMode) {
            val cleaned = speech.trim().lowercase()
            if (cleaned.contains(wakeWord) || cleaned.contains("hey assist") || cleaned.contains("assistant")) {
                logSuccess("唤醒词部分匹配成功!")
                // Cancel active and trigger wake
                stopListening()
                onWakeWordDetected()
            }
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
