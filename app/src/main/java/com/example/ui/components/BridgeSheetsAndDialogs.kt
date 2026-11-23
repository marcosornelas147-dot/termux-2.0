package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bridge.InstallerStepStatus
import com.example.bridge.PendingDestructiveCommand
import com.example.data.local.BridgeAuditEntity
import com.example.data.local.BrowserSettings
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PhosphorEmerald
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FirstBridgeAuthDialog(
    host: String,
    port: Int,
    onAuthorize: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDeny,
        icon = {
            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        title = { Text("Autorizar TERMUX BRIDGE") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Antes da primeira conexão, confirme que deseja autorizar o TERMUX 2.0 a enviar comandos para o serviço local em $host:$port.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Credenciais protegidas no Android Keystore\n• Tráfego restrito ao loopback local (127.0.0.1)\n• Páginas web não têm acesso à ponte",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAuthorize,
                modifier = Modifier.testTag("authorize_bridge_confirm_btn")
            ) {
                Text("Autorizar conexão")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDeny) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun DestructiveCommandDialog(
    pending: PendingDestructiveCommand,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        },
        title = { Text("Confirmar Comando Destrutivo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pending.reason, style = MaterialTheme.typography.bodyMedium)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = pending.command,
                        fontFamily = JetBrainsMonoFamily,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Executar mesmo assim")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BridgeInstallerSheet(
    steps: List<InstallerStepStatus>,
    scriptText: String,
    termuxInstalled: Boolean,
    onRegenerateSecrets: () -> Unit,
    onTestConnection: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Build, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Instalador Termux Bridge", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    text = "Configure o serviço autorizado no Termux original sem root e compatível com ARM64.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(steps, key = { it.stepNumber }) { step ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(
                        1.dp,
                        if (step.completed) PhosphorEmerald.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = if (step.completed) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (step.completed) PhosphorEmerald else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${step.stepNumber}. ${step.title}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = step.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!step.error.isNullOrBlank()) {
                                Text(
                                    text = "Ação necessária: ${step.error}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Termux Bridge Setup", scriptText))
                            Toast.makeText(
                                context,
                                "Script copiado! Cole e execute no Termux original.",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("copy_bridge_installer_script_btn")
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copiar Script")
                    }

                    if (termuxInstalled) {
                        OutlinedButton(
                            onClick = {
                                val intent = context.packageManager.getLaunchIntentForPackage("com.termux")
                                if (intent != null) {
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Abrir Termux")
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRegenerateSecrets,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Key, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Nova Chave/Token")
                    }
                    Button(
                        onClick = onTestConnection,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Testar Comunicação")
                    }
                }
            }

            item {
                Text("Prévia do script de configuração:", style = MaterialTheme.typography.labelMedium)
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = scriptText.lines().take(20).joinToString("\n") + "\n...",
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BridgeSecurityAuditSheet(
    settings: BrowserSettings,
    auditLogs: List<BridgeAuditEntity>,
    onSaveAllowedRoots: (String) -> Unit,
    onClearLogs: () -> Unit,
    onRevokeAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var rootsInput by remember(settings.bridgeAllowedRoots) { mutableStateOf(settings.bridgeAllowedRoots) }
    val dateFmt = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Segurança e Auditoria do Bridge", style = MaterialTheme.typography.titleLarge)
                }
            }

            item {
                OutlinedTextField(
                    value = rootsInput,
                    onValueChange = { rootsInput = it },
                    label = { Text("Diretórios permitidos (separados por vírgula)") },
                    supportingText = { Text("Comandos fora destes caminhos são bloqueados.") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onSaveAllowedRoots(rootsInput) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Salvar Diretórios")
                    }
                    Button(
                        onClick = {
                            onRevokeAll()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("revoke_bridge_connection_btn")
                    ) {
                        Text("Revogar Conexão")
                    }
                }
            }

            item {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Registro de Comandos (${auditLogs.size})", style = MaterialTheme.typography.titleMedium)
                    if (auditLogs.isNotEmpty()) {
                        TextButton(onClick = onClearLogs) { Text("Limpar") }
                    }
                }
            }

            if (auditLogs.isEmpty()) {
                item {
                    Text(
                        text = "Nenhum comando executado via Termux Bridge ainda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(auditLogs, key = { it.id }) { log ->
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${log.method} • ${log.status}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (log.status == "COMPLETED") {
                                        PhosphorEmerald
                                    } else if (log.status == "BLOCKED" || log.status == "FAILED") {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.secondary
                                    }
                                )
                                Text(
                                    text = dateFmt.format(Date(log.executedAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = log.command,
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "cwd: ${log.workingDirectory}" + (log.exitCode?.let { " • exit $it" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
