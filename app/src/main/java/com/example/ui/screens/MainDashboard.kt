package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
                NavigationBar(
                    containerColor = Color(0xFF1C1B1F),
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "控制面板") },
                        label = { Text("控制面板") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF1C1B1F),
                            selectedTextColor = Color(0xFFD0BCFF),
                            unselectedIconColor = Color(0xFFCAC4D0),
                            unselectedTextColor = Color(0xFFCAC4D0),
                            indicatorColor = Color(0xFFD0BCFF)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "助手设置") },
                        label = { Text("助手设置") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF1C1B1F),
                            selectedTextColor = Color(0xFFD0BCFF),
                            unselectedIconColor = Color(0xFFCAC4D0),
                            unselectedTextColor = Color(0xFFCAC4D0),
                            indicatorColor = Color(0xFFD0BCFF)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Assignment, contentDescription = "运行日志") },
                        label = { Text("运行日志") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF1C1B1F),
                            selectedTextColor = Color(0xFFD0BCFF),
                            unselectedIconColor = Color(0xFFCAC4D0),
                            unselectedTextColor = Color(0xFFCAC4D0),
                            indicatorColor = Color(0xFFD0BCFF)
                        )
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF1C1B1F))
            ) {
                if (!recordAudioPermissionState.status.isGranted) {
                    PermissionBlockedScreen(permissionState = recordAudioPermissionState)
                } else {
                    when (selectedTab) {
                        0 -> ControlPanelScreen(
                            viewModel = viewModel,
                            onSettingsClick = { selectedTab = 1 }
                        )
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
fun ControlPanelScreen(viewModel: AssistantViewModel, onSettingsClick: () -> Unit = {}) {
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
    val logs by viewModel.logList.collectAsStateWithLifecycle()
    val wakeWord by viewModel.wakeWord.collectAsStateWithLifecycle()
    val voiceReplyEnabled by viewModel.voiceReplyEnabled.collectAsStateWithLifecycle()
    val timeoutSeconds by viewModel.timeoutSeconds.collectAsStateWithLifecycle()

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
        // Sophisticated Dark Top App Bar matching HTML
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Assist Satellite",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (connOk && isRunning) Color(0xFF4ADE80) else Color(0xFF9E9E9E))
                        )
                        Text(
                            text = if (connOk && isRunning) "CONNECTED TO HOME ASSISTANT" else "CONNECTED STATE: $connStatus",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFCAC4D0),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF49454F))
                ) {
                    Text(text = "🛰️", fontSize = 18.sp)
                }
            }
        }

        // Connection Summary Card in Grid format
        item {
            val connMode by viewModel.connectionMode.collectAsStateWithLifecycle()
            val playMode by viewModel.playbackMode.collectAsStateWithLifecycle()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF2B2930), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF49454F), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "CONNECTION MODE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD0BCFF),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = connMode.ifBlank { "Assist Satellite" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF2B2930), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF49454F), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "PLAYBACK MODE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD0BCFF),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (playMode == "App") "App Speaker" else if (playMode == "Media player") "Media Player" else "Automation",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Configuration Settings dashboard box (Rich design layout)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2B2930), RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0xFF49454F).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                               text = "Wake Word",
                               fontSize = 12.sp,
                               color = Color(0xFFCAC4D0)
                            )
                            Text(
                               text = if (wakeWord.isNotBlank()) "\"$wakeWord\"" else "\"Hey Assist\"",
                               fontSize = 18.sp,
                               fontWeight = FontWeight.Medium,
                               color = Color.White
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                               text = "Voice Response",
                               fontSize = 12.sp,
                               color = Color(0xFFCAC4D0)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                               modifier = Modifier
                                   .size(width = 44.dp, height = 22.dp)
                                   .clip(CircleShape)
                                   .background(if (voiceReplyEnabled) Color(0xFFD0BCFF) else Color(0xFF49454F))
                                   .padding(2.dp),
                               contentAlignment = if (voiceReplyEnabled) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                               Box(
                                   modifier = Modifier
                                       .size(18.dp)
                                       .clip(CircleShape)
                                       .background(if (voiceReplyEnabled) Color(0xFF381E72) else Color(0xFFCAC4D0))
                               )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF1C1B1F), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "MAX TURNS",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCAC4D0)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = String.format("%02d", maxTurns),
                                    fontSize = 18.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF1C1B1F), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "TIMEOUT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCAC4D0)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${timeoutSeconds}s",
                                    fontSize = 18.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF1C1B1F), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "SENSITIVITY",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCAC4D0)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "HIGH",
                                    fontSize = 18.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Dialog & Mini Terminal Logs Combination Box
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2B2930), RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0xFF49454F).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE INTERACTION",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD0BCFF),
                            letterSpacing = 0.5.sp
                        )
                        Badge(
                            containerColor = when (assistantState) {
                                AssistantState.IDLE -> Color(0xFF49454F)
                                AssistantState.LISTENING_WAKE -> Color(0xFF381E72)
                                AssistantState.WAKEN_ACTIVE -> Color(0xFFB3261E)
                                AssistantState.PROCESSING -> Color(0xFF8126FF)
                            }
                        ) {
                            Text(
                                text = when (assistantState) {
                                    AssistantState.IDLE -> "IDLE"
                                    AssistantState.LISTENING_WAKE -> "等待唤醒"
                                    AssistantState.WAKEN_ACTIVE -> "唤醒激活"
                                    AssistantState.PROCESSING -> "处理回答"
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    if (assistantState == AssistantState.WAKEN_ACTIVE || assistantState == AssistantState.PROCESSING) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (lastUserMsg.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                                            .background(Color(0xFF381E72))
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = lastUserMsg,
                                            color = Color.White,
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
                                            .clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                                            .background(Color(0xFF1C1B1F))
                                            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = lastReplyMsg,
                                            color = Color(0xFFE6E1E5),
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = " dialogue step remaining: ${maxTurns - turnsCount} / $maxTurns ",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFD0BCFF)
                                )
                            }
                        }
                    } else {
                        // Sophisticated mini log terminal
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp)
                                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            val activeLogs = logs.takeLast(4).reversed()
                            if (activeLogs.isEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "⚡ System Idle - Standing By",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color(0xFFD0BCFF).copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Say \"$wakeWord\" to activate voice control",
                                        fontSize = 11.sp,
                                        color = Color(0xFFCAC4D0),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    activeLogs.forEach { log ->
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = "[${log.level}]",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = when (log.level) {
                                                    "ERROR" -> Color(0xFFF2B8B5)
                                                    "WARN" -> Color(0xFFFFD54F)
                                                    "SUCCESS" -> Color(0xFF81C784)
                                                    else -> Color(0xFFD0BCFF)
                                                },
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = log.message,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = Color(0xFFD0BCFF).copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                }
                            }
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

                    Box(modifier = Modifier.height(24.dp).width(3.dp).graphicsLayer(scaleY = scale1).background(Color(0xFFD0BCFF), CircleShape))
                    Box(modifier = Modifier.height(36.dp).width(3.dp).graphicsLayer(scaleY = scale2).background(Color(0xFFB3261E), CircleShape))
                    Box(modifier = Modifier.height(24.dp).width(3.dp).graphicsLayer(scaleY = scale3).background(Color(0xFFD0BCFF), CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = listeningHint, fontSize = 12.sp, color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.height(24.dp).width(3.dp).graphicsLayer(scaleY = scale3).background(Color(0xFFD0BCFF), CircleShape))
                    Box(modifier = Modifier.height(36.dp).width(3.dp).graphicsLayer(scaleY = scale1).background(Color(0xFFB3261E), CircleShape))
                    Box(modifier = Modifier.height(24.dp).width(3.dp).graphicsLayer(scaleY = scale2).background(Color(0xFFD0BCFF), CircleShape))
                }
            }
        }

        // Bottom control action buttons
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Glorious Mic Button (M3 Lavender Gold aura)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = if (isRunning) {
                                    listOf(Color(0xFFD0BCFF), Color(0xFF8126FF))
                                } else {
                                    listOf(Color(0xFF49454F), Color(0xFF38353F))
                                }
                            )
                        )
                        .clickable { viewModel.toggleAssistant() }
                        .testTag("assistant_toggle_button")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1C1B1F))
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Mic else Icons.Default.PlayArrow,
                            contentDescription = "Power Mode Toggle",
                            tint = Color(0xFFD0BCFF),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action buttons representing Stop and Config Gear
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.stopAssistant() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB3261E),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("force_stop_button")
                    ) {
                        Text(
                            text = "STOP ENGINE",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF49454F))
                            .clickable { onSettingsClick() }
                            .testTag("on_screen_gear_nav")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings Panel Link",
                            tint = Color(0xFFE6E1E5)
                        )
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
