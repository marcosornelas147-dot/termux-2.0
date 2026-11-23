package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bridge.BridgeConnectionMethod
import com.example.bridge.BridgeConnectionState
import com.example.bridge.BridgeStatus
import com.example.data.local.BrowserSettings
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PhosphorEmerald

@Composable
fun TermuxBridgeScreen(
    status: BridgeStatus,
    settings: BrowserSettings,
    onConnect: (host: String, httpPort: Int, sshPort: Int, sshUser: String, method: BridgeConnectionMethod) -> Unit,
    onDisconnect: () -> Unit,
    onTestConnection: (host: String, httpPort: Int, sshPort: Int, sshUser: String, method: BridgeConnectionMethod) -> Unit,
    onOpenExternalTerminal: () -> Unit,
    onOpenInstaller: () -> Unit,
    onOpenSecurityAudit: () -> Unit,
    onOpenLocalPortInBrowser: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var hostInput by remember(settings.bridgeHost) { mutableStateOf(settings.bridgeHost) }
    var httpPortInput by remember(settings.bridgeHttpPort) { mutableStateOf(settings.bridgeHttpPort.toString()) }
    var sshPortInput by remember(settings.bridgeSshPort) { mutableStateOf(settings.bridgeSshPort.toString()) }
    var sshUserInput by remember(settings.bridgeSshUser) { mutableStateOf(settings.bridgeSshUser) }
    var selectedMethod by remember(settings.bridgeMethod) {
        mutableStateOf(
            runCatching { BridgeConnectionMethod.valueOf(settings.bridgeMethod) }
                .getOrDefault(BridgeConnectionMethod.HTTP_API)
        )
    }

    val indicatorColor = when (status.state) {
        BridgeConnectionState.CONNECTED -> PhosphorEmerald
        BridgeConnectionState.CONNECTING -> AmberWarn
        BridgeConnectionState.DISCONNECTED -> AlertCoral
    }
    val stateLabel = when (status.state) {
        BridgeConnectionState.CONNECTED -> "CONECTADO"
        BridgeConnectionState.CONNECTING -> "CONECTANDO..."
        BridgeConnectionState.DISCONNECTED -> "DESCONECTADO"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header + Status Card (18.3)
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, indicatorColor.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "TERMUX BRIDGE",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Central de controle do Termux original",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Visual Status Indicator: Verde / Amarelo / Vermelho
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = indicatorColor.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, indicatorColor),
                                modifier = Modifier.testTag("bridge_status_indicator")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(indicatorColor)
                                    )
                                    Text(
                                        text = stateLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = indicatorColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Telemetry Details Grid
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BridgeMetaRow("Endereço local", status.host)
                            BridgeMetaRow(
                                "Porta",
                                if (selectedMethod == BridgeConnectionMethod.SSH_LOCAL) {
                                    sshPortInput
                                } else {
                                    "${status.port}"
                                }
                            )
                            BridgeMetaRow("Método", status.method.label)
                            BridgeMetaRow(
                                "Tempo de resposta",
                                status.latencyMs?.let { "$it ms" } ?: "—"
                            )
                            BridgeMetaRow("Estado do serviço", status.serviceStatusText)
                            BridgeMetaRow(
                                "Ambiente",
                                "${status.deviceAbi} • ${if (status.isRootedDevice) "Root detectado" else "Sem root"}"
                            )
                            BridgeMetaRow(
                                "Termux original",
                                if (status.termuxInstalled) {
                                    "Instalado (${status.termuxVersionName ?: "com.termux"})"
                                } else {
                                    "Não detectado (com.termux)"
                                }
                            )
                        }

                        if (!status.lastError.isNullOrBlank()) {
                            Text(
                                text = status.lastError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        // Action Buttons: Conectar / Desconectar / Testar Conexão
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val httpPort = httpPortInput.toIntOrNull() ?: 8765
                            val sshPort = sshPortInput.toIntOrNull() ?: 8022
                            if (status.state == BridgeConnectionState.CONNECTED) {
                                Button(
                                    onClick = onDisconnect,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("bridge_disconnect_button")
                                ) {
                                    Icon(Icons.Default.LinkOff, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Desconectar")
                                }
                            } else {
                                Button(
                                    onClick = {
                                        onConnect(hostInput, httpPort, sshPort, sshUserInput, selectedMethod)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("bridge_connect_button")
                                ) {
                                    Icon(Icons.Default.Link, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Conectar")
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    onTestConnection(hostInput, httpPort, sshPort, sshUserInput, selectedMethod)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("bridge_test_button")
                            ) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Testar Conexão")
                            }
                        }

                        if (status.state == BridgeConnectionState.CONNECTED) {
                            Button(
                                onClick = onOpenExternalTerminal,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Terminal, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Abrir TERMINAL EXTERNO")
                            }
                        }
                    }
                }
            }

            // Detected External Termux Localhost Ports -> Browser Integration (18.6)
            if (status.detectedListeningPorts.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Servidores Locais Detectados no Termux",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(status.detectedListeningPorts) { port ->
                                    OutlinedButton(onClick = { onOpenLocalPortInBrowser(port) }) {
                                        Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("127.0.0.1:$port", fontFamily = JetBrainsMonoFamily)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Connection Method & Configuration Card (18.2)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Método e Parâmetros de Conexão", style = MaterialTheme.typography.titleMedium)

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(BridgeConnectionMethod.entries) { method ->
                                FilterChip(
                                    selected = selectedMethod == method,
                                    onClick = { selectedMethod = method },
                                    label = { Text(method.label) },
                                    leadingIcon = if (selectedMethod == method) {
                                        { Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = hostInput,
                                onValueChange = { hostInput = it },
                                label = { Text("Host Loopback") },
                                singleLine = true,
                                modifier = Modifier.weight(1.2f)
                            )
                            OutlinedTextField(
                                value = if (selectedMethod == BridgeConnectionMethod.SSH_LOCAL) sshPortInput else httpPortInput,
                                onValueChange = {
                                    if (selectedMethod == BridgeConnectionMethod.SSH_LOCAL) {
                                        sshPortInput = it.filter { ch -> ch.isDigit() }
                                    } else {
                                        httpPortInput = it.filter { ch -> ch.isDigit() }
                                    }
                                },
                                label = {
                                    Text(if (selectedMethod == BridgeConnectionMethod.SSH_LOCAL) "Porta SSH" else "Porta API")
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(0.8f)
                            )
                        }

                        if (selectedMethod == BridgeConnectionMethod.SSH_LOCAL ||
                            selectedMethod == BridgeConnectionMethod.SCRIPT_AUTO
                        ) {
                            OutlinedTextField(
                                value = sshUserInput,
                                onValueChange = { sshUserInput = it },
                                label = { Text("Usuário SSH do Termux (ex: u0_a254)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Installer & Security Actions (18.7 & 18.8)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onOpenInstaller,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("open_bridge_installer_btn")
                    ) {
                        Icon(Icons.Default.Build, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Instalador")
                    }
                    OutlinedButton(
                        onClick = onOpenSecurityAudit,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("open_bridge_security_btn")
                    ) {
                        Icon(Icons.Default.Security, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Segurança")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun BridgeMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = JetBrainsMonoFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
