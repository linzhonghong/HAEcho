package com.example.service

import android.content.Context
import com.example.data.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class HomeAssistantClient(
    private val context: Context,
    private val repository: AppRepository,
    private val onConnectionStatusChanged: (Boolean, String) -> Unit,
    private val onAssistResponse: (String, String?) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build()
    private var webSocket: WebSocket? = null
    private var requestIdCount = 1
    private var isConnected = false

    fun connect(serverUrl: String, token: String) {
        disconnect()
        val wsUrl = getWebSocketUrl(serverUrl)
        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                logInfo("WebSocket 连接已开启至 $wsUrl")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text, token)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                logInfo("WebSocket 正在关闭: $reason (代码: $code)")
                updateStatus(false, "连接关闭: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                logError("WebSocket 错误异常: ${t.message}")
                updateStatus(false, "连接失败: ${t.message}")
            }
        })
    }

    private fun handleMessage(text: String, token: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type")
            when (type) {
                "auth_required" -> {
                    logInfo("接收到服务认证需求，发送访问秘钥")
                    val authMessage = JSONObject().apply {
                        put("type", "auth")
                        put("access_token", token)
                    }
                    webSocket?.send(authMessage.toString())
                }
                "auth_ok" -> {
                    isConnected = true
                    val version = json.optString("ha_version")
                    logSuccess("成功通过 Home Assistant 认证！当前版本：$version")
                    updateStatus(true, "已连接 (HA $version)")
                }
                "auth_invalid" -> {
                    isConnected = false
                    val message = json.optString("message")
                    logError("Home Assistant 认证失败: $message")
                    updateStatus(false, "认证失败: $message")
                }
                "result" -> {
                    val success = json.optBoolean("success", false)
                    val id = json.optInt("id")
                    if (!success) {
                        val error = json.optJSONObject("error")?.optString("message") ?: "未知服务错误"
                        logError("请求(ID: $id)失败: $error")
                    }
                }
                "event" -> {
                    val id = json.optInt("id")
                    val event = json.optJSONObject("event")
                    if (event != null) {
                        val eventType = event.optString("type")
                        logInfo("接收到事件节点：$eventType")
                        when (eventType) {
                            "intent-end" -> {
                                val intentOutput = event.optJSONObject("data")?.optJSONObject("intent_output")
                                val speechText = intentOutput?.optJSONObject("response")
                                    ?.optJSONObject("speech")
                                    ?.optJSONObject("plain")
                                    ?.optString("speech") ?: ""
                                if (speechText.isNotBlank()) {
                                    logSuccess("解析到助手文字回答: \"$speechText\"")
                                    onAssistResponse(speechText, null)
                                }
                            }
                            "tts-end" -> {
                                val ttsOutput = event.optJSONObject("data")?.optJSONObject("tts_output")
                                val audioUrl = ttsOutput?.optString("url")
                                if (audioUrl != null) {
                                    logSuccess("解析到助手 TTS 音频下载路径: \"$audioUrl\"")
                                    onAssistResponse("", audioUrl)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logError("解析消息时出错: ${e.message}")
        }
    }

    fun sendAssistCommand(text: String) {
        if (!isConnected) {
            logError("无法发送指令，未成功建立 HA 链接")
            return
        }
        val id = nextId()
        val action = JSONObject().apply {
            put("id", id)
            put("type", "assist_pipeline/run")
            put("start_stage", "intent")
            put("end_stage", "tts")
            put("input", JSONObject().apply {
                put("text", text)
            })
        }
        webSocket?.send(action.toString())
        logInfo("发送指令至 HA Assist: \"$text\" (管道请求 ID: $id)")
    }

    fun playOnMediaPlayer(mediaPlayerEntityId: String, audioUrl: String) {
        if (!isConnected) return
        val id = nextId()
        val action = JSONObject().apply {
            put("id", id)
            put("type", "call_service")
            put("domain", "media_player")
            put("service", "play_media")
            put("service_data", JSONObject().apply {
                put("entity_id", mediaPlayerEntityId)
                put("media_content_id", audioUrl)
                put("media_content_type", "music")
            })
        }
        webSocket?.send(action.toString())
        logInfo("已触发远程 HA 扬声器播放 $mediaPlayerEntityId ($id)")
    }

    fun triggerHAAutomation(automationEntityId: String) {
        if (!isConnected) return
        val id = nextId()
        val action = JSONObject().apply {
            put("id", id)
            put("type", "call_service")
            put("domain", "automation")
            put("service", "trigger")
            put("service_data", JSONObject().apply {
                put("entity_id", automationEntityId)
            })
        }
        webSocket?.send(action.toString())
        logInfo("已触发远程 HA 自动化 $automationEntityId ($id)")
    }

    fun disconnect() {
        webSocket?.close(1000, "主动断开连接")
        webSocket = null
        isConnected = false
        updateStatus(false, "未连接")
    }

    private fun getWebSocketUrl(url: String): String {
        var cleanUrl = url.trim()
        if (cleanUrl.endsWith("/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length - 1)
        }
        cleanUrl = if (cleanUrl.startsWith("https://")) {
            cleanUrl.replace("https://", "wss://")
        } else if (cleanUrl.startsWith("http://")) {
            cleanUrl.replace("http://", "ws://")
        } else {
            "ws://$cleanUrl"
        }
        return "$cleanUrl/api/websocket"
    }

    private fun nextId(): Int {
        return requestIdCount++
    }

    private fun updateStatus(connected: Boolean, message: String) {
        isConnected = connected
        onConnectionStatusChanged(connected, message)
    }

    private fun logInfo(msg: String) {
        CoroutineScope(Dispatchers.IO).launch {
            repository.addLog("INFO", "HAClient", msg)
        }
    }

    private fun logSuccess(msg: String) {
        CoroutineScope(Dispatchers.IO).launch {
            repository.addLog("SUCCESS", "HAClient", msg)
        }
    }

    private fun logError(msg: String) {
        CoroutineScope(Dispatchers.IO).launch {
            repository.addLog("ERROR", "HAClient", msg)
        }
    }
}
