package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bridge.BridgeConnectionState
import com.example.bridge.BridgeStatus
import com.example.bridge.ExternalStreamType
import com.example.bridge.ExternalTerminalSession
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PhosphorEmerald

private val ExternalQuickCommands = listOf(
    "uname -a",
    "cmake --build build --parallel 4",
    "python3 -m http.server 8080",
    "pkg list-installed | head -n 20",
    "ls -la",
    "ps aux",
    "clear"
)

@Composable
fun ExternalTerminalScreen(
    bridgeStatus: BridgeStatus,
    sessions: List<ExternalTerminalSession>,
    activeSessionId: String,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onCloseSession: (String) -> Unit,
    onSendCommand: (String) -> Unit,
    onInterruptProcess: (String) -> Unit,
    onOpenServerPortInBrowser: (Int) -> Unit,
    onGoToBridgeConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSession = remember(sessions, activeSessionId) {
        sessions.firstOrNull { it.id == activeSessionId } ?: sessions.first()
    }
    var commandInput by remember(activeSession.id) { mutableStateOf("") }
    var historyCursor by remember(activeSession.id, activeSession.commandHistory.size) {
        mutableIntStateOf(-1)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(activeSession.outputLines.size, activeSession.id) {
        if (activeSession.outputLines.isNotEmpty()) {
            listState.animateScrollToItem(activeSession.outputLines.size - 1)
        }
    }

    val statusDotColor = when (bridgeStatus.state) {
        BridgeConnectionState.CONNECTED -> PhosphorEmerald
        BridgeConnectionState.CONNECTING -> AmberWarn
        BridgeConnectionState.DISCONNECTED -> AlertCoral
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header + Multi-Session Bar (18.4 & 18.5)
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.clickable(onClick = onGoToBridgeConfig)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                        Column {
                            Text("TERMINAL EXTERNO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (bridgeStatus.state == BridgeConnectionState.CONNECTED) {
                                    "${bridgeStatus.method.label} • ${bridgeStatus.host}:${bridgeStatus.port}"
                                } else {
                                    "Desconectado • Toque para configurar Bridge"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (activeSession.isRunning) {
                            Button(
                                onClick = { onInterruptProcess(activeSession.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("interrupt_external_process_btn")
                            ) {
                                Icon(Icons.Default.StopCircle, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Interromper", style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            TextButton(
                                onClick = { onOpenServerPortInBrowser(8080) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Abrir :8080", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Multi-session tabs row (18.5)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(sessions, key = { it.id }) { sess ->
                        val selected = sess.id == activeSession.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .testTag("external_session_tab_${sess.name}")
                                .clickable { onSelectSession(sess.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (sess.isRunning) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondary)
                                    )
                                }
                                Text(
                                    text = sess.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (sessions.size > 1) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Fechar sessão",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onCloseSession(sess.id) }
                                    )
                                } else {
                                    Spacer(Modifier.width(4.dp))
                                }
                            }
                        }
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .testTag("add_external_session_btn")
                                .clickable(onClick = onNewSession)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Add, "Nova sessão", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Working directory & running status bar
                Text(
                    text = "cwd: ${activeSession.workingDirectory}" +
                        (activeSession.activeCommand?.let { " • executando: $it" }
                            ?: activeSession.lastExitCode?.let { " • exit: $it" }.orEmpty()),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (activeSession.isRunning) {
            LinearProgressIndicator(
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
            )
        }

        // Output Stream (stdout / stderr / system)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("external_terminal_output")
        ) {
            items(activeSession.outputLines, key = { it.id }) { line ->
                val color = when (line.stream) {
                    ExternalStreamType.COMMAND -> MaterialTheme.colorScheme.primary
                    ExternalStreamType.STDOUT -> MaterialTheme.colorScheme.onBackground
                    ExternalStreamType.STDERR -> MaterialTheme.colorScheme.error
                    ExternalStreamType.SYSTEM -> MaterialTheme.colorScheme.secondary
                }
                Text(
                    text = line.text,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = if (line.stream == ExternalStreamType.COMMAND) FontWeight.Bold else FontWeight.Normal,
                    color = color
                )
            }
        }

        // Quick Commands Bar
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            items(ExternalQuickCommands) { q ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.clickable { onSendCommand(q) }
                ) {
                    Text(
                        text = q,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Command Input Prompt + History Navigation
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        val hist = activeSession.commandHistory
                        if (hist.isNotEmpty()) {
                            historyCursor = if (historyCursor < 0) hist.lastIndex else (historyCursor - 1).coerceAtLeast(0)
                            commandInput = hist[historyCursor]
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, "Comando anterior", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = {
                        val hist = activeSession.commandHistory
                        if (historyCursor >= 0) {
                            if (historyCursor < hist.lastIndex) {
                                historyCursor += 1
                                commandInput = hist[historyCursor]
                            } else {
                                historyCursor = -1
                                commandInput = ""
                            }
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, "Próximo comando", modifier = Modifier.size(18.dp))
                }

                Text(
                    text = "$",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Box(modifier = Modifier.weight(1f)) {
                    if (commandInput.isEmpty()) {
                        Text(
                            text = "cmake --build build --parallel 4",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (commandInput.isNotBlank()) {
                                    onSendCommand(commandInput)
                                    commandInput = ""
                                    historyCursor = -1
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("external_terminal_input")
                    )
                }

                IconButton(
                    onClick = {
                        if (commandInput.isNotBlank()) {
                            onSendCommand(commandInput)
                            commandInput = ""
                            historyCursor = -1
                        }
                    },
                    modifier = Modifier.testTag("external_terminal_send_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar comando ao Termux original",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
