package com.example.bridge

import java.util.UUID

enum class BridgeConnectionMethod(val label: String, val defaultPort: Int) {
    HTTP_API("API Local HTTP", 8765),
    SSH_LOCAL("SSH Local (Chave)", 8022),
    SCRIPT_AUTO("Script & Auto-detecção", 8765)
}

enum class BridgeConnectionState {
    DISCONNECTED, // Vermelho
    CONNECTING,   // Amarelo
    CONNECTED     // Verde
}

enum class ExternalStreamType {
    COMMAND,
    STDOUT,
    STDERR,
    SYSTEM
}

data class ExternalOutputLine(
    val id: Long,
    val text: String,
    val stream: ExternalStreamType,
    val timestamp: Long = System.currentTimeMillis()
)

data class ExternalTerminalSession(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val workingDirectory: String = "/data/data/com.termux/files/home",
    val commandHistory: List<String> = emptyList(),
    val outputLines: List<ExternalOutputLine> = emptyList(),
    val isRunning: Boolean = false,
    val activeTaskId: String? = null,
    val activeCommand: String? = null,
    val lastExitCode: Int? = null
)

data class BridgeStatus(
    val state: BridgeConnectionState = BridgeConnectionState.DISCONNECTED,
    val method: BridgeConnectionMethod = BridgeConnectionMethod.HTTP_API,
    val host: String = "127.0.0.1",
    val port: Int = 8765,
    val latencyMs: Long? = null,
    val serviceStatusText: String = "Serviço desconectado",
    val termuxInstalled: Boolean = false,
    val termuxVersionName: String? = null,
    val deviceAbi: String = "arm64-v8a",
    val isRootedDevice: Boolean = false,
    val remoteUname: String? = null,
    val activeProcessesCount: Int = 0,
    val detectedListeningPorts: List<Int> = emptyList(),
    val lastError: String? = null
)

data class PendingDestructiveCommand(
    val sessionId: String,
    val command: String,
    val reason: String
)

data class InstallerStepStatus(
    val stepNumber: Int,
    val title: String,
    val detail: String,
    val completed: Boolean,
    val error: String? = null
)

object BridgeSecurityPolicy {
    private val destructivePatterns = listOf(
        Regex("""\brm\s+-[^\n]*r[^\n]*f""", RegexOption.IGNORE_CASE),
        Regex("""\brm\s+-[^\n]*f[^\n]*r""", RegexOption.IGNORE_CASE),
        Regex("""\bmkfs(\.[a-z0-9]+)?\b""", RegexOption.IGNORE_CASE),
        Regex("""\bdd\s+if=""", RegexOption.IGNORE_CASE),
        Regex("""\bchmod\s+-R\s+777\s+/""", RegexOption.IGNORE_CASE),
        Regex("""\b(shutdown|reboot|poweroff)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bkill\s+-9\s+-1\b"""),
        Regex(""":\(\)\s*\{\s*:\|\:&\s*\}\s*;"""),
        Regex(""">\s*/dev/sd[a-z]""")
    )

    fun isLoopbackHost(host: String): Boolean {
        val clean = host.trim().lowercase()
        return clean == "127.0.0.1" || clean == "localhost" || clean == "::1"
    }

    fun detectDestructiveReason(command: String): String? {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return null
        return when {
            destructivePatterns[0].containsMatchIn(trimmed) || destructivePatterns[1].containsMatchIn(trimmed) ->
                "Remoção recursiva forçada (rm -rf) pode apagar arquivos permanentemente."
            destructivePatterns[2].containsMatchIn(trimmed) ->
                "Comando de formatação (mkfs) detectado."
            destructivePatterns[3].containsMatchIn(trimmed) ->
                "Gravação direta em dispositivo/bloco (dd if=) detectada."
            destructivePatterns[4].containsMatchIn(trimmed) ->
                "Alteração recursiva ampla de permissões (chmod -R 777 /) detectada."
            destructivePatterns[5].containsMatchIn(trimmed) ->
                "Comando de desligamento/reinicialização detectado."
            destructivePatterns[6].containsMatchIn(trimmed) || destructivePatterns[7].containsMatchIn(trimmed) ->
                "Comando capaz de derrubar todos os processos ou travar o ambiente."
            destructivePatterns[8].containsMatchIn(trimmed) ->
                "Redirecionamento destrutivo para dispositivo /dev."
            else -> null
        }
    }

    fun isAllowedWorkingDirectory(workingDir: String, allowedRootsCsv: String): Boolean {
        val normalizedDir = workingDir.trim().trimEnd('/')
        if (normalizedDir.isEmpty()) return false
        if (normalizedDir.contains("..")) return false

        val roots = allowedRootsCsv.split(",")
            .map { it.trim().trimEnd('/') }
            .filter { it.isNotEmpty() }

        if (roots.isEmpty()) return true
        return roots.any { root ->
            normalizedDir == root || normalizedDir.startsWith("$root/")
        }
    }
}
