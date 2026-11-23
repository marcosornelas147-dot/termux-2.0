package com.example.bridge

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

class TermuxBridgeManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val credentialStore = BridgeCredentialStore(context)

    private var lineIdSeq = 1L
    private val runningJobs = ConcurrentHashMap<String, Job>()
    private val activeSshChannels = ConcurrentHashMap<String, ChannelExec>()
    private var persistentSshSession: Session? = null

    private val _status = MutableStateFlow(detectEnvironmentInitialStatus())
    val status: StateFlow<BridgeStatus> = _status.asStateFlow()

    private val _sessions = MutableStateFlow(
        listOf(
            ExternalTerminalSession(
                name = "Sessão 1",
                outputLines = listOf(
                    newLine(
                        "TERMUX BRIDGE • Terminal Externo pronto. Conecte na aba Bridge para executar comandos no Termux original.",
                        ExternalStreamType.SYSTEM
                    )
                )
            )
        )
    )
    val sessions: StateFlow<List<ExternalTerminalSession>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow(_sessions.value.first().id)
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    private fun newLine(text: String, stream: ExternalStreamType): ExternalOutputLine =
        ExternalOutputLine(id = lineIdSeq++, text = text, stream = stream)

    private fun detectEnvironmentInitialStatus(): BridgeStatus {
        val (installed, version) = queryTermuxPackage()
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val suExists = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su").any { File(it).exists() }
        return BridgeStatus(
            state = BridgeConnectionState.DISCONNECTED,
            termuxInstalled = installed,
            termuxVersionName = version,
            deviceAbi = abi,
            isRootedDevice = suExists,
            serviceStatusText = if (installed) {
                "Termux detectado (${version ?: "instalado"}) • Aguardando conexão"
            } else {
                "Termux original não detectado • Use o Instalador ou porta local"
            }
        )
    }

    fun refreshEnvironmentDetection() {
        val (installed, version) = queryTermuxPackage()
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        _status.update {
            it.copy(
                termuxInstalled = installed,
                termuxVersionName = version,
                deviceAbi = abi
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun queryTermuxPackage(): Pair<Boolean, String?> {
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo("com.termux", PackageManager.PackageInfoFlags.of(0))
            } else {
                context.packageManager.getPackageInfo("com.termux", 0)
            }
            true to info.versionName
        }.getOrElse { false to null }
    }

    fun selectSession(sessionId: String) {
        if (_sessions.value.any { it.id == sessionId }) {
            _activeSessionId.value = sessionId
        }
    }

    fun createNewSession(workingDir: String = "/data/data/com.termux/files/home") {
        val nextNum = _sessions.value.size + 1
        val created = ExternalTerminalSession(
            name = "Sessão $nextNum",
            workingDirectory = workingDir,
            outputLines = listOf(
                newLine("Nova sessão #$nextNum iniciada em $workingDir", ExternalStreamType.SYSTEM)
            )
        )
        _sessions.update { it + created }
        _activeSessionId.value = created.id
    }

    fun closeSession(sessionId: String) {
        if (_sessions.value.size <= 1) return
        runningJobs.remove(sessionId)?.cancel()
        activeSshChannels.remove(sessionId)?.disconnect()
        val remaining = _sessions.value.filterNot { it.id == sessionId }
        _sessions.value = remaining
        if (_activeSessionId.value == sessionId) {
            _activeSessionId.value = remaining.last().id
        }
    }

    suspend fun connect(
        host: String,
        httpPort: Int,
        sshPort: Int,
        sshUser: String,
        method: BridgeConnectionMethod
    ): Boolean {
        if (!BridgeSecurityPolicy.isLoopbackHost(host)) {
            _status.update {
                it.copy(
                    state = BridgeConnectionState.DISCONNECTED,
                    lastError = "Política de segurança: apenas endereços locais (127.0.0.1 / localhost) são permitidos.",
                    serviceStatusText = "Host remoto bloqueado"
                )
            }
            return false
        }

        val activePort = if (method == BridgeConnectionMethod.SSH_LOCAL) sshPort else httpPort
        _status.update {
            it.copy(
                state = BridgeConnectionState.CONNECTING,
                method = method,
                host = host,
                port = activePort,
                serviceStatusText = "Conectando via ${method.label}...",
                lastError = null
            )
        }

        return when (method) {
            BridgeConnectionMethod.HTTP_API -> connectHttp(host, httpPort, method)
            BridgeConnectionMethod.SSH_LOCAL -> connectSsh(host, sshPort, sshUser)
            BridgeConnectionMethod.SCRIPT_AUTO -> {
                // Auto-detect: try HTTP API first, fallback to SSH Local
                val httpOk = connectHttp(host, httpPort, BridgeConnectionMethod.SCRIPT_AUTO)
                if (httpOk) true else connectSsh(host, sshPort, sshUser)
            }
        }
    }

    private suspend fun connectHttp(
        host: String,
        port: Int,
        method: BridgeConnectionMethod
    ): Boolean = withContext(Dispatchers.IO) {
        val secrets = credentialStore.getOrCreateSecrets()
        val startMs = System.currentTimeMillis()
        try {
            val conn = (URL("http://$host:$port/health").openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer ${secrets.apiToken}")
            }
            val code = conn.responseCode
            val body = (if (code in 200..399) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()

            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            if (code == 200) {
                val json = JSONObject(body)
                val remoteHome = json.optString("home", "")
                val arch = json.optString("arch", _status.value.deviceAbi)
                val runningTasks = json.optInt("runningTasks", 0)
                val portsJson = json.optJSONArray("listeningPorts")
                val ports = buildList {
                    if (portsJson != null) {
                        for (i in 0 until portsJson.length()) add(portsJson.getInt(i))
                    }
                }
                if (remoteHome.isNotBlank()) {
                    _sessions.update { list ->
                        list.map {
                            if (it.workingDirectory == "/data/data/com.termux/files/home") {
                                it.copy(workingDirectory = remoteHome)
                            } else it
                        }
                    }
                }
                _status.update {
                    it.copy(
                        state = BridgeConnectionState.CONNECTED,
                        method = method,
                        host = host,
                        port = port,
                        latencyMs = elapsed,
                        remoteUname = arch,
                        activeProcessesCount = runningTasks,
                        detectedListeningPorts = ports,
                        serviceStatusText = "API Local online ($arch)",
                        lastError = null
                    )
                }
                true
            } else {
                val msg = if (code == 401) {
                    "Token não autorizado (HTTP 401). Execute novamente o script do Instalador no Termux."
                } else {
                    "Falha na API Local (HTTP $code)"
                }
                _status.update {
                    it.copy(
                        state = BridgeConnectionState.DISCONNECTED,
                        latencyMs = elapsed,
                        serviceStatusText = "Erro de autenticação/serviço",
                        lastError = msg
                    )
                }
                false
            }
        } catch (e: Exception) {
            _status.update {
                it.copy(
                    state = BridgeConnectionState.DISCONNECTED,
                    latencyMs = null,
                    serviceStatusText = "Serviço indisponível em $host:$port",
                    lastError = "Sem resposta em $host:$port (${e.message}). Inicie o serviço no Termux."
                )
            }
            false
        }
    }

    private suspend fun connectSsh(host: String, port: Int, user: String): Boolean = withContext(Dispatchers.IO) {
        val secrets = credentialStore.getOrCreateSecrets()
        val startMs = System.currentTimeMillis()
        try {
            persistentSshSession?.takeIf { it.isConnected }?.disconnect()
            val jsch = JSch()
            jsch.addIdentity(
                "termux-bridge",
                secrets.sshPrivateKeyPem.toByteArray(Charsets.UTF_8),
                secrets.sshPublicKeyOpenSsh.toByteArray(Charsets.UTF_8),
                null
            )
            val session = jsch.getSession(user, host, port).apply {
                setConfig(Properties().apply {
                    put("StrictHostKeyChecking", "no")
                    put("PreferredAuthentications", "publickey")
                })
                timeout = 4500
            }
            session.connect(4500)
            persistentSshSession = session

            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            _status.update {
                it.copy(
                    state = BridgeConnectionState.CONNECTED,
                    method = BridgeConnectionMethod.SSH_LOCAL,
                    host = host,
                    port = port,
                    latencyMs = elapsed,
                    serviceStatusText = "SSH conectado como $user",
                    lastError = null
                )
            }
            true
        } catch (e: Exception) {
            persistentSshSession = null
            _status.update {
                it.copy(
                    state = BridgeConnectionState.DISCONNECTED,
                    latencyMs = null,
                    serviceStatusText = "SSH indisponível em $host:$port",
                    lastError = "Falha SSH ($host:$port): ${e.message}. Verifique 'sshd' e ~/.ssh/authorized_keys."
                )
            }
            false
        }
    }

    fun disconnect() {
        runningJobs.values.forEach { it.cancel() }
        runningJobs.clear()
        activeSshChannels.values.forEach { runCatching { it.disconnect() } }
        activeSshChannels.clear()
        runCatching { persistentSshSession?.disconnect() }
        persistentSshSession = null

        _sessions.update { list -> list.map { it.copy(isRunning = false, activeTaskId = null) } }
        _status.update {
            it.copy(
                state = BridgeConnectionState.DISCONNECTED,
                latencyMs = null,
                serviceStatusText = "Desconectado manualmente",
                activeProcessesCount = 0
            )
        }
    }

    suspend fun testConnection(
        host: String,
        httpPort: Int,
        sshPort: Int,
        sshUser: String,
        method: BridgeConnectionMethod
    ): Boolean {
        return connect(host, httpPort, sshPort, sshUser, method)
    }

    fun executeCommandInSession(
        sessionId: String,
        rawCommand: String,
        allowedRootsCsv: String,
        onAuditStart: suspend (sessionId: String, method: String, command: String, cwd: String) -> Long,
        onAuditFinish: suspend (auditId: Long, exitCode: Int?, status: String) -> Unit
    ) {
        val cmd = rawCommand.trim()
        if (cmd.isEmpty()) return

        val session = _sessions.value.firstOrNull { it.id == sessionId } ?: return

        // Handle local session cd tracking cleanly
        if (cmd == "clear") {
            updateSession(sessionId) { it.copy(outputLines = emptyList()) }
            return
        }

        if (!BridgeSecurityPolicy.isAllowedWorkingDirectory(session.workingDirectory, allowedRootsCsv)) {
            appendSessionLine(
                sessionId,
                "Bloqueado: diretório de trabalho '${session.workingDirectory}' fora dos diretórios autorizados.",
                ExternalStreamType.STDERR
            )
            return
        }

        if (cmd.startsWith("cd ")) {
            val arg = cmd.removePrefix("cd ").trim().trim('"', '\'')
            val nextDir = if (arg.startsWith("/")) arg else "${session.workingDirectory.trimEnd('/')}/$arg"
            val normalized = File(nextDir).canonicalPath
            if (!BridgeSecurityPolicy.isAllowedWorkingDirectory(normalized, allowedRootsCsv)) {
                appendSessionLine(
                    sessionId,
                    "Acesso negado pela política de diretórios: $normalized",
                    ExternalStreamType.STDERR
                )
            } else {
                updateSession(sessionId) {
                    it.copy(
                        workingDirectory = normalized,
                        commandHistory = (it.commandHistory + cmd).takeLast(50)
                    )
                }
                appendSessionLine(sessionId, "$ $cmd", ExternalStreamType.COMMAND)
                appendSessionLine(sessionId, "Diretório alterado para $normalized", ExternalStreamType.SYSTEM)
            }
            return
        }

        val currentStatus = _status.value
        if (currentStatus.state != BridgeConnectionState.CONNECTED) {
            appendSessionLine(
                sessionId,
                "Não conectado ao Termux original. Conecte-se na seção TERMUX BRIDGE primeiro.",
                ExternalStreamType.STDERR
            )
            return
        }

        appendSessionLine(sessionId, "$ $cmd", ExternalStreamType.COMMAND)
        updateSession(sessionId) {
            it.copy(
                isRunning = true,
                activeCommand = cmd,
                commandHistory = (it.commandHistory + cmd).takeLast(50)
            )
        }

        val job = scope.launch {
            val auditId = onAuditStart(sessionId, currentStatus.method.name, cmd, session.workingDirectory)
            try {
                val exitCode = when (currentStatus.method) {
                    BridgeConnectionMethod.SSH_LOCAL -> runViaSsh(sessionId, session.workingDirectory, cmd)
                    else -> runViaHttpApi(
                        sessionId = sessionId,
                        host = currentStatus.host,
                        port = currentStatus.port,
                        cwd = session.workingDirectory,
                        command = cmd
                    )
                }
                val finalStatus = if (exitCode == 0) "COMPLETED" else "FAILED"
                onAuditFinish(auditId, exitCode, finalStatus)
                updateSession(sessionId) {
                    it.copy(isRunning = false, activeTaskId = null, activeCommand = null, lastExitCode = exitCode)
                }
                appendSessionLine(
                    sessionId,
                    "[Processo finalizado com código $exitCode]",
                    if (exitCode == 0) ExternalStreamType.SYSTEM else ExternalStreamType.STDERR
                )
            } catch (_: CancellationException) {
                onAuditFinish(auditId, -1, "INTERRUPTED")
                updateSession(sessionId) {
                    it.copy(isRunning = false, activeTaskId = null, activeCommand = null, lastExitCode = -1)
                }
                appendSessionLine(sessionId, "[Processo interrompido pelo usuário]", ExternalStreamType.STDERR)
            } catch (e: Exception) {
                onAuditFinish(auditId, -1, "FAILED")
                updateSession(sessionId) {
                    it.copy(isRunning = false, activeTaskId = null, activeCommand = null, lastExitCode = -1)
                }
                appendSessionLine(sessionId, "Erro na ponte: ${e.message}", ExternalStreamType.STDERR)
            } finally {
                runningJobs.remove(sessionId)
            }
        }
        runningJobs[sessionId] = job
    }

    private suspend fun runViaHttpApi(
        sessionId: String,
        host: String,
        port: Int,
        cwd: String,
        command: String
    ): Int = withContext(Dispatchers.IO) {
        val secrets = credentialStore.getOrCreateSecrets()
        val execConn = (URL("http://$host:$port/exec").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 5000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${secrets.apiToken}")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        val reqBody = JSONObject().apply {
            put("command", command)
            put("cwd", cwd)
        }.toString()
        OutputStreamWriter(execConn.outputStream, Charsets.UTF_8).use { it.write(reqBody) }

        val code = execConn.responseCode
        val resp = (if (code in 200..399) execConn.inputStream else execConn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        execConn.disconnect()

        if (code != 200) {
            appendSessionLine(sessionId, "Erro HTTP $code: $resp", ExternalStreamType.STDERR)
            return@withContext -1
        }

        val taskId = JSONObject(resp).getString("taskId")
        updateSession(sessionId) { it.copy(activeTaskId = taskId) }

        var outOffset = 0
        var errOffset = 0
        var exitCode = 0
        var running = true

        while (running && isActive) {
            val pollConn = (URL(
                "http://$host:$port/tasks/$taskId?offset_out=$outOffset&offset_err=$errOffset"
            ).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Authorization", "Bearer ${secrets.apiToken}")
            }
            val pollCode = pollConn.responseCode
            val pollBody = (if (pollCode in 200..399) pollConn.inputStream else pollConn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            pollConn.disconnect()

            if (pollCode != 200) {
                throw IllegalStateException("Falha ao acompanhar tarefa (HTTP $pollCode)")
            }

            val json = JSONObject(pollBody)
            val outChunk = json.optString("stdoutChunk", "")
            val errChunk = json.optString("stderrChunk", "")
            outOffset = json.optInt("nextOutOffset", outOffset)
            errOffset = json.optInt("nextErrOffset", errOffset)
            running = json.optBoolean("running", false)
            if (!running) {
                exitCode = if (json.isNull("exitCode")) 0 else json.optInt("exitCode", 0)
            }

            if (outChunk.isNotEmpty()) {
                outChunk.trimEnd('\n').split('\n').forEach { line ->
                    appendSessionLine(sessionId, line, ExternalStreamType.STDOUT)
                }
            }
            if (errChunk.isNotEmpty()) {
                errChunk.trimEnd('\n').split('\n').forEach { line ->
                    appendSessionLine(sessionId, line, ExternalStreamType.STDERR)
                }
            }

            if (running) delay(120)
        }
        exitCode
    }

    private suspend fun runViaSsh(
        sessionId: String,
        cwd: String,
        command: String
    ): Int = withContext(Dispatchers.IO) {
        val sshSession = persistentSshSession?.takeIf { it.isConnected }
            ?: throw IllegalStateException("Sessão SSH desconectada")

        val channel = sshSession.openChannel("exec") as ChannelExec
        activeSshChannels[sessionId] = channel

        val escapedCwd = cwd.replace("'", "'\\''")
        channel.setCommand("cd '$escapedCwd' && $command")
        val stdoutStream = channel.inputStream
        val stderrStream = channel.errStream
        channel.connect(5000)

        val outJob = launch {
            BufferedReader(InputStreamReader(stdoutStream)).use { reader ->
                while (isActive) {
                    val line = reader.readLine() ?: break
                    appendSessionLine(sessionId, line, ExternalStreamType.STDOUT)
                }
            }
        }
        val errJob = launch {
            BufferedReader(InputStreamReader(stderrStream)).use { reader ->
                while (isActive) {
                    val line = reader.readLine() ?: break
                    appendSessionLine(sessionId, line, ExternalStreamType.STDERR)
                }
            }
        }

        while (!channel.isClosed && isActive) {
            delay(80)
        }
        outJob.join()
        errJob.join()
        val exitCode = channel.exitStatus
        activeSshChannels.remove(sessionId)
        channel.disconnect()
        exitCode
    }

    fun interruptSessionProcess(sessionId: String) {
        val session = _sessions.value.firstOrNull { it.id == sessionId } ?: return
        val currentStatus = _status.value

        scope.launch {
            if (currentStatus.method != BridgeConnectionMethod.SSH_LOCAL && session.activeTaskId != null) {
                runCatching {
                    val secrets = credentialStore.getOrCreateSecrets()
                    val conn = (URL(
                        "http://${currentStatus.host}:${currentStatus.port}/tasks/${session.activeTaskId}/kill"
                    ).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 3000
                        readTimeout = 3000
                        doOutput = true
                        setRequestProperty("Authorization", "Bearer ${secrets.apiToken}")
                    }
                    conn.outputStream.use { it.write(ByteArray(0)) }
                    conn.responseCode
                    conn.disconnect()
                }
            } else {
                activeSshChannels.remove(sessionId)?.let { ch ->
                    runCatching { ch.sendSignal("INT") }
                    runCatching { ch.disconnect() }
                }
            }
            runningJobs.remove(sessionId)?.cancel()
        }
    }

    fun revokeBridgeCredentialsAndDisconnect() {
        disconnect()
        credentialStore.revokeAll()
        _status.update {
            it.copy(
                serviceStatusText = "Credenciais e chaves revogadas",
                lastError = null
            )
        }
    }

    private fun appendSessionLine(sessionId: String, text: String, stream: ExternalStreamType) {
        updateSession(sessionId) { session ->
            session.copy(outputLines = (session.outputLines + newLine(text, stream)).takeLast(400))
        }
    }

    private fun updateSession(
        sessionId: String,
        transform: (ExternalTerminalSession) -> ExternalTerminalSession
    ) {
        _sessions.update { list ->
            list.map { if (it.id == sessionId) transform(it) else it }
        }
    }

    fun destroy() {
        disconnect()
    }
}
