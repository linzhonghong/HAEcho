package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.SiriEdgeGlow
import com.example.ui.viewmodel.AssistantState
import com.example.ui.viewmodel.AssistantViewModel
import com.google.accompanist.permissions.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainDashboard(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
    val isWaken = assistantState == AssistantState.WAKEN_ACTIVE || assistantState == AssistantState.PROCESSING

    // Handle Microphone Permission
    val recordAudioPermissionState = rememberPermissionState(
        android.Manifest.permission.RECORD_AUDIO
    )

    SiriEdgeGlow(
        isActive = isWaken,
        modifier = modifier.fillMaxSize()
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "控制面板") },
                        label = { Text("控制面板") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "助手设置") },
                        label = { Text("助手设置") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Assignment, contentDescription = "运行日志") },
                        label = { Text("运行日志") }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (!recordAudioPermissionState.status.isGranted) {
                    PermissionBlockedScreen(permissionState = recordAudioPermissionState)
                } else {
                    when (selectedTab) {
                        0 -> ControlPanelScreen(viewModel = viewModel)
                        1 -> SettingsScreen(viewModel = viewModel)
                        2 -> LogsScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionBlockedScreen(permissionState: PermissionState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MicOff,
            contentDescription = "未授权麦克风",
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "需要麦克风权限",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "语音卫星助手必须访问您的麦克风，以便监测唤醒词并接收您的语音控制命令。",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { permissionState.launchPermissionRequest() },
            modifier = Modifier.testTag("grant_permission_button")
        ) {
            Text("授予麦克风权限")
        }
    }
}

@Composable
fun ControlPanelScreen(viewModel: AssistantViewModel) {
    val isRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    val connStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val connOk by viewModel.isConnectionOk.collectAsStateWithLifecycle()
    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
    val turnsCount by viewModel.dialogueTurnCount.collectAsStateWithLifecycle()
    val maxTurns by viewModel.maxTurns.collectAsStateWithLifecycle()
    val isListening by viewModel.isListeningNow.collectAsStateWithLifecycle()
    val listeningHint by viewModel.listeningHint.collectAsStateWithLifecycle()
    val lastUserMsg by viewModel.lastUserMessage.collectAsStateWithLifecycle()
    val lastReplyMsg by viewModel.lastReplyMessage.collectAsStateWithLifecycle()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_animation")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header Title
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "卫星语音助手",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Home Assistant Assist Client",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // Connection Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isRunning) Color(0xFF4CAF50) else Color(0xFF9E9E9E))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isRunning) "服务正在后台运行" else "助手服务已停止",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }

                        if (isRunning) {
                            Box(
                                modifier = Modifier
                                    .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (connOk) Color(0xFF00C853) else Color(0xFFFF3D00))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (connOk) Icons.Default.CloudQueue else Icons.Default.CloudOff,
                            contentDescription = "Home Assistant 连接状况",
                            tint = if (connOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Home Assistant 连接状态",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = connStatus,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (connOk) Color(0xFF00AA66) else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        // Conversation Visualizer / Logs Cards
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (assistantState == AssistantState.WAKEN_ACTIVE) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "当前交互阶段",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Badge(
                            containerColor = when (assistantState) {
                                AssistantState.IDLE -> MaterialTheme.colorScheme.secondary
                                AssistantState.LISTENING_WAKE -> MaterialTheme.colorScheme.primary
                                AssistantState.WAKEN_ACTIVE -> Color(0xFFFF1177)
                                AssistantState.PROCESSING -> MaterialTheme.colorScheme.tertiary
                            }
                        ) {
                            Text(
                                text = when (assistantState) {
                                    AssistantState.IDLE -> "IDLE"
                                    AssistantState.LISTENING_WAKE -> "等待唤醒"
                                    AssistantState.WAKEN_ACTIVE -> "唤醒激活中"
                                    AssistantState.PROCESSING -> "处理回答中"
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (assistantState == AssistantState.WAKEN_ACTIVE || assistantState == AssistantState.PROCESSING) {
                        // Interactive Speech bubbles!
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (lastUserMsg.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp, 2.dp, 12.dp, 12.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = lastUserMsg,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }

                            if (lastReplyMsg.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(2.dp, 12.dp, 12.dp, 12.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = lastReplyMsg,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                            
                            // Dialogue turns counter
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "多轮对话剩余轮次: ${maxTurns - turnsCount} / $maxTurns",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    } else {
                        // Empty states / Tips
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hearing,
                                contentDescription = "空闲",
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (isRunning) "说出您的唤醒词，或点击下方的“手动唤醒”按钮直接发起语音交互！" else "助手服务未启动，请点击下方粉色大按钮开启语音卫星服务。",
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Wave sound simulated indicator
        if (isListening) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    val scale1 by infiniteTransition.animateFloat(
                        initialValue = 0.3f, targetValue = 1.3f,
                        animationSpec = infiniteRepeatable(tween(500, easing = EaseInOutSine), RepeatMode.Reverse),
                        label = "w1"
                    )
                    val scale2 by infiniteTransition.animateFloat(
                        initialValue = 0.2f, targetValue = 1.5f,
                        animationSpec = infiniteRepeatable(tween(400, easing = EaseInOutSine), RepeatMode.Reverse),
                        label = "w2"
                    )
                    val scale3 by infiniteTransition.animateFloat(
                        initialValue = 0.4f, targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(tween(600, easing = EaseInOutSine), RepeatMode.Reverse),
                        label = "w3"
                    )

                    Box(modifier = Modifier.height(24.dp).width(3.dp).graphicsLayer(scaleY = scale1).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Box(modifier = Modifier.height(36.dp).width(3.dp).graphicsLayer(scaleY = scale2).background(Color(0xFFFF1177), CircleShape))
                    Box(modifier = Modifier.height(24.dp).width(3.dp).graphicsLayer(scaleY = scale3).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = listeningHint, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.height(24.dp).width(3.dp).graphicsLayer(scaleY = scale3).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Box(modifier = Modifier.height(36.dp).width(3.dp).graphicsLayer(scaleY = scale1).background(Color(0xFFFF1177), CircleShape))
                    Box(modifier = Modifier.height(24.dp).width(3.dp).graphicsLayer(scaleY = scale2).background(MaterialTheme.colorScheme.primary, CircleShape))
                }
            }
        }

        // Central trigger button
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Main circular mic button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = if (isRunning) {
                                    listOf(Color(0xFF8126FF), Color(0xFF00C853))
                                } else {
                                    listOf(Color(0xFFFF3CAC), Color(0xFF784BA0))
                                }
                            )
                        )
                        .clickable { viewModel.toggleAssistant() }
                        .testTag("assistant_toggle_button")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Mic else Icons.Default.PlayArrow,
                            contentDescription = "启动/停止",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isRunning) "停止服务" else "开始监听",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Sub trigger buttons
                if (isRunning) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.triggerManualWake() },
                            modifier = Modifier.testTag("manual_wake_button")
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = "手动唤醒")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("手动唤醒")
                        }

                        Button(
                            onClick = { viewModel.stopAssistant() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("force_stop_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "紧急停止")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("关闭")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: AssistantViewModel) {
    var haUrl by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var connMode by remember { mutableStateOf("") }
    var playMode by remember { mutableStateOf("") }
    var mediaId by remember { mutableStateOf("") }
    var maxTurns by remember { mutableStateOf(5) }
    var timeoutSecs by remember { mutableStateOf(15) }
    var wakeWord by remember { mutableStateOf("") }
    var wakeResps by remember { mutableStateOf("") }
    var voiceReply by remember { mutableStateOf(true) }

    // Visibility token toggle
    var isTokenVisible by remember { mutableStateOf(false) }

    // Populate data once when component mounts
    val currentUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val currentToken by viewModel.accessToken.collectAsStateWithLifecycle()
    val currentMode by viewModel.connectionMode.collectAsStateWithLifecycle()
    val currentPlayback by viewModel.playbackMode.collectAsStateWithLifecycle()
    val currentMediaId by viewModel.mediaPlayerId.collectAsStateWithLifecycle()
    val currentMaxTurns by viewModel.maxTurns.collectAsStateWithLifecycle()
    val currentTimeout by viewModel.timeoutSeconds.collectAsStateWithLifecycle()
    val currentWakeWord by viewModel.wakeWord.collectAsStateWithLifecycle()
    val currentWakeResps by viewModel.wakeResponses.collectAsStateWithLifecycle()
    val currentVoiceReply by viewModel.voiceReplyEnabled.collectAsStateWithLifecycle()

    LaunchedEffect(currentUrl) {
        haUrl = currentUrl
        token = currentToken
        connMode = currentMode
        playMode = currentPlayback
        mediaId = currentMediaId
        maxTurns = currentMaxTurns
        timeoutSecs = currentTimeout
        wakeWord = currentWakeWord
        wakeResps = currentWakeResps
        voiceReply = currentVoiceReply
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "助手参数配置",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "配置与 Home Assistant 的链接规则以及本地语音卫星参数",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Card 1: HA authentication
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Home Assistant 认证信息", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    
                    OutlinedTextField(
                        value = haUrl,
                        onValueChange = { haUrl = it },
                        label = { Text("服务器地址 (Server URL)") },
                        placeholder = { Text("http://192.168.1.100:8123") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("setting_ha_url_input"),
                        leadingIcon = { Icon(Icons.Default.Lan, contentDescription = null) }
                    )

                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("长期访问令牌 (Long-Lived Access Token)") },
                        placeholder = { Text("Long access token...") },
                        visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                                Icon(
                                    imageVector = if (isTokenVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = "切换可见性"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("setting_ha_token_input"),
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) }
                    )
                }
            }
        }

        // Card 2: Modes configs
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("连接模式 与 回放引擎", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    // Connection mode choice
                    Column {
                        Text("连接模式:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = connMode == "Assist Satellite",
                                    onClick = { connMode = "Assist Satellite" },
                                    modifier = Modifier.testTag("mode_satellite_radio")
                                )
                                Text("Assist Satellite", fontSize = 14.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = connMode == "Direct Pipeline",
                                    onClick = { connMode = "Direct Pipeline" },
                                    modifier = Modifier.testTag("mode_pipeline_radio")
                                )
                                Text("Direct Pipeline", fontSize = 14.sp)
                            }
                        }
                    }

                    // Playback mode choice
                    Column {
                        Text("语音回放媒介 (Satellite Playback Mode):", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = playMode == "App", onClick = { playMode = "App" })
                                Text("App", fontSize = 13.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = playMode == "Media player", onClick = { playMode = "Media player" })
                                Text("Media Player", fontSize = 13.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = playMode == "Automation", onClick = { playMode = "Automation" })
                                Text("Automation", fontSize = 13.sp)
                            }
                        }
                    }

                    if (playMode == "Media player") {
                        OutlinedTextField(
                            value = mediaId,
                            onValueChange = { mediaId = it },
                            label = { Text("多媒体播放器播放实体 (Media Player Entity)") },
                            placeholder = { Text("media_player.living_room_speaker") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("setting_media_player_input")
                        )
                    }
                }
            }
        }

        // Card 3: Wake and response options
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("唤醒词与交互微调", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    OutlinedTextField(
                        value = wakeWord,
                        onValueChange = { wakeWord = it },
                        label = { Text("唤醒词 (Wake Word)") },
                        placeholder = { Text("例如：想，小爱同学，Siri") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("setting_wake_word_input")
                    )

                    OutlinedTextField(
                        value = wakeResps,
                        onValueChange = { wakeResps = it },
                        label = { Text("唤醒后的回应话语列表 (每行一个)") },
                        placeholder = { Text("在的\n我在呢\n随时恭候") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("setting_wake_responses_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("语音回复开关", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("开启后将通过扬声器拟音发声回答", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = voiceReply,
                            onCheckedChange = { voiceReply = it },
                            modifier = Modifier.testTag("setting_voice_reply_switch")
                        )
                    }
                }
            }
        }

        // Card 4: Loop params
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("超时与对话轮次", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("最大连续对话轮次: $maxTurns", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Slider(
                            value = maxTurns.toFloat(),
                            onValueChange = { maxTurns = it.toInt() },
                            valueRange = 1f..10f,
                            steps = 9
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("未响应自动超时时间: $timeoutSecs 秒", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Slider(
                            value = timeoutSecs.toFloat(),
                            onValueChange = { timeoutSecs = it.toInt() },
                            valueRange = 5f..60f,
                            steps = 11
                        )
                    }
                }
            }
        }

        // Action block save settings
        item {
            Button(
                onClick = {
                    viewModel.saveConfig(
                        url = haUrl,
                        token = token,
                        mode = connMode,
                        playback = playMode,
                        mediaId = mediaId,
                        turns = maxTurns,
                        timeout = timeoutSecs,
                        word = wakeWord,
                        responses = wakeResps,
                        voiceReply = voiceReply
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_settings_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = "保存设置")
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存设置并应用", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun LogsScreen(viewModel: AssistantViewModel) {
    val logs by viewModel.logList.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "卫星助手运行日志",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "用于查看助手的实时运行分析、会话及连接状况",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            IconButton(
                onClick = { viewModel.clearLogs() },
                modifier = Modifier.testTag("clear_logs_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "清空日志",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SpeakerNotesOff,
                        contentDescription = "无日志",
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "暂无运行日志",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when (log.level) {
                                "ERROR" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                "WARN" -> Color(0xFFFFF9C4).copy(alpha = 0.35f)
                                "SUCCESS" -> Color(0xFFE8F5E9).copy(alpha = 0.35f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Badge(
                                        containerColor = when (log.level) {
                                            "ERROR" -> MaterialTheme.colorScheme.error
                                            "WARN" -> Color(0xFFFFB300)
                                            "SUCCESS" -> Color(0xFF4CAF50)
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    ) {
                                        Text(
                                            text = log.level,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = log.tag,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = dateFormat.format(Date(log.timestamp)),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = log.message,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
