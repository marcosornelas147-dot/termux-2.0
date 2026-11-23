package com.example.server

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class TerminalLineType {
    COMMAND,
    OUTPUT,
    SUCCESS,
    ERROR,
    SERVER_LOG
}

data class TerminalLine(
    val id: Long,
    val text: String,
    val type: TerminalLineType = TerminalLineType.OUTPUT
)

data class WorkspaceFileItem(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long
)

class TerminalCommandProcessor(
    context: Context,
    private val serverManager: LocalDevServerManager,
    onOpenUrlInBrowser: (String) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lineIdSeq = 1L

    private val _currentPromptPath = MutableStateFlow("~")
    val currentPromptPath: StateFlow<String> = _currentPromptPath.asStateFlow()

    private val _lines = MutableStateFlow<List<TerminalLine>>(
        listOf(
            nextLine("TERMUX 2.0 • Shell Linux & Servidor Local Integrado", TerminalLineType.SUCCESS),
            nextLine("Digite 'help', 'neofetch', 'man <cmd>' ou 'python -m http.server 8080'.", TerminalLineType.OUTPUT)
        )
    )
    val lines: StateFlow<List<TerminalLine>> = _lines.asStateFlow()

    private val _workspaceFiles = MutableStateFlow<List<WorkspaceFileItem>>(emptyList())
    val workspaceFiles: StateFlow<List<WorkspaceFileItem>> = _workspaceFiles.asStateFlow()

    private val session = ShellSessionState(
        context = context,
        homeDir = serverManager.homeDir,
        serverManager = serverManager,
        onOpenUrlInBrowser = onOpenUrlInBrowser,
        onClearTerminal = { _lines.value = emptyList() },
        onRefreshWorkspace = { refreshWorkspaceFiles() }
    )

    private val shellEngine = LinuxShellEngine(session)

    init {
        refreshWorkspaceFiles()
        scope.launch {
            serverManager.serverLogs.collect { log ->
                appendLine(log, TerminalLineType.SERVER_LOG)
            }
        }
    }

    private fun nextLine(text: String, type: TerminalLineType): TerminalLine =
        TerminalLine(id = lineIdSeq++, text = text, type = type)

    private fun appendLine(text: String, type: TerminalLineType = TerminalLineType.OUTPUT) {
        if (text.isEmpty()) return
        _lines.update { current ->
            (current + nextLine(text, type)).takeLast(400)
        }
    }

    fun refreshWorkspaceFiles() {
        val home = serverManager.homeDir
        val collected = mutableListOf<WorkspaceFileItem>()
        home.walkTopDown().maxDepth(4).forEach { file ->
            if (file != home) {
                val rel = file.relativeTo(home).path
                collected.add(
                    WorkspaceFileItem(
                        name = file.name,
                        relativePath = rel,
                        isDirectory = file.isDirectory,
                        sizeBytes = if (file.isFile) file.length() else 0L
                    )
                )
            }
        }
        _workspaceFiles.value = collected.sortedWith(
            compareBy<WorkspaceFileItem> { !it.isDirectory }.thenBy { it.relativePath }
        )
    }

    fun readWorkspaceFile(relativePath: String): String {
        val target = session.resolveSafe(relativePath) ?: return ""
        return if (target.exists() && target.isFile) target.readText() else ""
    }

    fun writeWorkspaceFile(relativePath: String, content: String): Boolean {
        val target = session.resolveSafe(relativePath) ?: return false
        target.parentFile?.mkdirs()
        target.writeText(content)
        refreshWorkspaceFiles()
        return true
    }

    fun deleteWorkspaceFile(relativePath: String): Boolean {
        val target = session.resolveSafe(relativePath) ?: return false
        val ok = target.deleteRecursively()
        refreshWorkspaceFiles()
        return ok
    }

    fun autocomplete(currentInput: String): String = shellEngine.autocomplete(currentInput)

    fun getHistoryCommands(): List<String> = session.commandHistory.toList()

    fun executeCommand(rawInput: String) {
        val cmd = rawInput.trim()
        if (cmd.isEmpty()) return

        appendLine("${session.promptPath()} $ $cmd", TerminalLineType.COMMAND)

        val isNetworkCommand = cmd.contains("curl") || cmd.contains("wget") || cmd.contains("ping") ||
            cmd.contains("nslookup") || cmd.contains("dig") || cmd.contains("sleep")

        if (isNetworkCommand) {
            scope.launch {
                val result = shellEngine.executeLine(cmd)
                _currentPromptPath.value = session.promptPath()
                if (result.stdout.isNotEmpty()) {
                    appendLine(
                        result.stdout,
                        if (result.isSuccessStyle) TerminalLineType.SUCCESS else TerminalLineType.OUTPUT
                    )
                }
                if (result.stderr.isNotEmpty()) {
                    appendLine(result.stderr, TerminalLineType.ERROR)
                }
            }
        } else {
            val result = shellEngine.executeLine(cmd)
            _currentPromptPath.value = session.promptPath()
            if (result.stdout.isNotEmpty()) {
                appendLine(
                    result.stdout,
                    if (result.isSuccessStyle) TerminalLineType.SUCCESS else TerminalLineType.OUTPUT
                )
            }
            if (result.stderr.isNotEmpty()) {
                appendLine(result.stderr, TerminalLineType.ERROR)
            }
        }
    }
}
