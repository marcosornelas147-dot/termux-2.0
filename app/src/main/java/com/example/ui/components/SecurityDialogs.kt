package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.browser.PendingDownloadPrompt
import com.example.browser.PendingHttpWarning
import com.example.browser.PendingSitePermission
import com.example.browser.PendingSslError
import com.example.browser.SitePermissionKind
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun SslErrorSecurityDialog(
    error: PendingSslError,
    onResolve: (proceedUnsafe: Boolean, rememberForSession: Boolean) -> Unit
) {
    var rememberSession by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onResolve(false, false) },
        icon = {
            Icon(
                imageVector = Icons.Default.GppBad,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text("Conexão não segura (SSL/TLS)")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "O certificado de ${error.host} falhou na verificação de segurança.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = error.errorSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = rememberSession,
                        onCheckedChange = { rememberSession = it }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Confiar neste host apenas nesta sessão",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onResolve(false, false) }) {
                Text("Voltar com segurança")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onResolve(true, rememberSession) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Prosseguir mesmo assim")
            }
        }
    )
}

@Composable
fun HttpWarningDialog(
    warning: PendingHttpWarning,
    onResolve: (useHttps: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onResolve(true) },
        icon = {
            Icon(
                imageVector = Icons.Default.LockOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Conexão HTTP não criptografada") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "A proteção HTTPS está ativa. Este endereço externo usa HTTP sem criptografia:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = warning.httpUrl,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onResolve(true) }) {
                Text("Atualizar para HTTPS")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { onResolve(false) }) {
                Text("Abrir em HTTP")
            }
        }
    )
}

@Composable
fun SitePermissionDialog(
    prompt: PendingSitePermission,
    onResolve: (allow: Boolean, remember: Boolean) -> Unit
) {
    var rememberSite by remember { mutableStateOf(true) }
    val isGeo = prompt.kind == SitePermissionKind.GEOLOCATION

    AlertDialog(
        onDismissRequest = { onResolve(false, false) },
        icon = {
            Icon(
                imageVector = if (isGeo) Icons.Default.LocationOn else Icons.Default.Videocam,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
        },
        title = {
            Text(if (isGeo) "Permissão de Localização" else "Permissão de Câmera / Microfone")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${prompt.origin} está solicitando acesso a ${if (isGeo) "sua localização" else "câmera e/ou microfone"}.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberSite,
                        onCheckedChange = { rememberSite = it }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Lembrar decisão para este site", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onResolve(true, rememberSite) }) {
                Text("Permitir")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { onResolve(false, rememberSite) }) {
                Text("Bloquear")
            }
        }
    )
}

@Composable
fun DownloadConfirmDialog(
    prompt: PendingDownloadPrompt,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val sizeText = if (prompt.contentLength > 0) {
        val kb = prompt.contentLength / 1024.0
        if (kb >= 1024) String.format("%.2f MB", kb / 1024.0) else String.format("%.0f KB", kb)
    } else {
        "Tamanho desconhecido"
    }

    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Confirmar Download") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = prompt.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = JetBrainsMonoFamily
                )
                Text(
                    text = "Origem: ${prompt.origin}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tipo: ${prompt.mimeType} • $sizeText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Baixar arquivo")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) {
                Text("Cancelar")
            }
        }
    )
}
