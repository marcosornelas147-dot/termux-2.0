package com.example.server

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

data class RunningServerInfo(
    val port: Int,
    val label: String,
    val rootRelativePath: String,
    val startedAt: Long = System.currentTimeMillis(),
    val requestCount: Int = 0
) {
    val baseUrl: String get() = "http://127.0.0.1:$port"
}

class LocalDevServerManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val homeDir: File = File(context.filesDir, "termux_home").apply { mkdirs() }

    private val serverJobs = ConcurrentHashMap<Int, Job>()
    private val serverSockets = ConcurrentHashMap<Int, ServerSocket>()

    private val _runningServers = MutableStateFlow<List<RunningServerInfo>>(emptyList())
    val runningServers: StateFlow<List<RunningServerInfo>> = _runningServers.asStateFlow()

    private val _serverLogs = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val serverLogs: SharedFlow<String> = _serverLogs.asSharedFlow()

    init {
        seedStarterWorkspaceIfEmpty()
    }

    fun seedStarterWorkspaceIfEmpty() {
        val indexFile = File(homeDir, "index.html")
        if (!indexFile.exists()) {
            indexFile.writeText(DEFAULT_INDEX_HTML)
        }
        val cssFile = File(homeDir, "styles.css")
        if (!cssFile.exists()) {
            cssFile.writeText(DEFAULT_STYLES_CSS)
        }
        val jsFile = File(homeDir, "app.js")
        if (!jsFile.exists()) {
            jsFile.writeText(DEFAULT_APP_JS)
        }
        val adminFile = File(homeDir, "admin.html")
        if (!adminFile.exists()) {
            adminFile.writeText(DEFAULT_ADMIN_HTML)
        }
        val apiDir = File(homeDir, "api").apply { mkdirs() }
        val apiInfo = File(apiDir, "info.json")
        if (!apiInfo.exists()) {
            apiInfo.writeText(
                """
                {
                  "service": "Termux 2.0 Local Dev Server",
                  "version": "2.0.0",
                  "environment": "android-sandbox",
                  "endpoints": ["/api/status", "/api/files", "/api/info.json"]
                }
                """.trimIndent()
            )
        }
    }

    fun startServer(port: Int, label: String, rootDir: File = homeDir): Result<RunningServerInfo> {
        if (port !in 1024..65535) {
            return Result.failure(IllegalArgumentException("Porta inválida ($port). Use entre 1024 e 65535."))
        }
        if (serverSockets.containsKey(port)) {
            val existing = _runningServers.value.firstOrNull { it.port == port }
            return if (existing != null) {
                Result.success(existing)
            } else {
                Result.failure(IllegalStateException("Porta $port já está em uso."))
            }
        }

        val canonicalRoot = rootDir.canonicalFile
        if (!canonicalRoot.path.startsWith(homeDir.canonicalPath)) {
            return Result.failure(SecurityException("Diretório raiz fora do sandbox termux_home."))
        }

        return try {
            val socket = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
            serverSockets[port] = socket

            val relPath = canonicalRoot.relativeToOrNull(homeDir)?.path?.ifBlank { "~" } ?: "~"
            val info = RunningServerInfo(port = port, label = label, rootRelativePath = relPath)
            _runningServers.update { current ->
                (current.filterNot { it.port == port } + info).sortedBy { it.port }
            }

            val job = scope.launch {
                _serverLogs.tryEmit("[HTTP] $label escutando em http://127.0.0.1:$port (raiz: $relPath)")
                while (isActive && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        launch { handleClient(client, port, canonicalRoot) }
                    } catch (_: Exception) {
                        if (socket.isClosed) break
                    }
                }
            }
            serverJobs[port] = job
            Result.success(info)
        } catch (e: Exception) {
            serverSockets.remove(port)?.let { runCatching { it.close() } }
            Result.failure(e)
        }
    }

    fun stopServer(port: Int): Boolean {
        val socket = serverSockets.remove(port)
        val job = serverJobs.remove(port)
        job?.cancel()
        runCatching { socket?.close() }
        val removed = socket != null || job != null
        _runningServers.update { list -> list.filterNot { it.port == port } }
        if (removed) {
            _serverLogs.tryEmit("[HTTP] Servidor na porta $port encerrado.")
        }
        return removed
    }

    fun stopAll() {
        serverSockets.keys.toList().forEach { stopServer(it) }
    }

    private fun handleClient(client: Socket, port: Int, rootDir: File) {
        client.use { socket ->
            socket.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val rawTarget = parts[1]
            val rawPath = rawTarget.substringBefore("?")
            val decodedPath = runCatching { URLDecoder.decode(rawPath, "UTF-8") }.getOrDefault(rawPath)

            // Drain remaining request headers
            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            var requestBody = ""
            if (contentLength > 0 && contentLength <= 65536) {
                val buf = CharArray(contentLength)
                val read = reader.read(buf, 0, contentLength)
                if (read > 0) requestBody = String(buf, 0, read)
            }

            val out = BufferedOutputStream(socket.getOutputStream())
            var statusCode = 200
            var statusText = "OK"
            var contentType = "text/plain; charset=utf-8"
            var responseBytes: ByteArray

            when {
                decodedPath == "/api/status" -> {
                    contentType = "application/json; charset=utf-8"
                    val runtime = Runtime.getRuntime()
                    val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                    val json = JSONObject().apply {
                        put("status", "online")
                        put("port", port)
                        put("host", "127.0.0.1")
                        put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                        put("androidSdk", Build.VERSION.SDK_INT)
                        put("appHeapUsedMb", usedMb)
                        put("activeServers", _runningServers.value.size)
                        put("timestamp", System.currentTimeMillis())
                    }
                    responseBytes = json.toString(2).toByteArray(Charsets.UTF_8)
                }

                decodedPath == "/api/files" -> {
                    contentType = "application/json; charset=utf-8"
                    val arr = JSONArray()
                    rootDir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                        arr.put(JSONObject().apply {
                            put("name", f.name)
                            put("isDirectory", f.isDirectory)
                            put("sizeBytes", if (f.isFile) f.length() else 0)
                        })
                    }
                    responseBytes = JSONObject().put("files", arr).toString(2).toByteArray(Charsets.UTF_8)
                }

                decodedPath == "/api/echo" && method == "POST" -> {
                    contentType = "application/json; charset=utf-8"
                    responseBytes = JSONObject().apply {
                        put("echo", requestBody)
                        put("port", port)
                    }.toString(2).toByteArray(Charsets.UTF_8)
                }

                else -> {
                    val sanitizedRel = decodedPath.trimStart('/')
                    val candidate = if (sanitizedRel.isEmpty()) {
                        File(rootDir, "index.html")
                    } else {
                        File(rootDir, sanitizedRel)
                    }
                    val canonicalTarget = candidate.canonicalFile
                    if (!canonicalTarget.path.startsWith(rootDir.canonicalPath)) {
                        statusCode = 403
                        statusText = "Forbidden"
                        responseBytes = "403 Forbidden - Acesso fora do sandbox negado.".toByteArray()
                    } else if (canonicalTarget.isDirectory) {
                        val dirIndex = File(canonicalTarget, "index.html")
                        if (dirIndex.exists() && dirIndex.isFile) {
                            contentType = "text/html; charset=utf-8"
                            responseBytes = dirIndex.readBytes()
                        } else {
                            contentType = "text/html; charset=utf-8"
                            responseBytes = buildDirectoryListingHtml(decodedPath, canonicalTarget).toByteArray()
                        }
                    } else if (canonicalTarget.exists() && canonicalTarget.isFile) {
                        contentType = guessMimeType(canonicalTarget.name)
                        responseBytes = canonicalTarget.readBytes()
                    } else {
                        statusCode = 404
                        statusText = "Not Found"
                        contentType = "text/html; charset=utf-8"
                        responseBytes = """
                            <!DOCTYPE html>
                            <html lang="pt-BR">
                            <head><meta charset="utf-8"><title>404 - Termux 2.0</title>
                            <style>body{background:#0b0f14;color:#e6edf3;font-family:monospace;padding:32px}
                            h1{color:#f87171}a{color:#10e89a}</style></head>
                            <body><h1>404 Not Found</h1><p>Arquivo não encontrado: <code>$decodedPath</code></p>
                            <p><a href="/">Voltar para /</a></p></body></html>
                        """.trimIndent().toByteArray(Charsets.UTF_8)
                    }
                }
            }

            val headers = buildString {
                append("HTTP/1.1 $statusCode $statusText\r\n")
                append("Content-Type: $contentType\r\n")
                append("Content-Length: ${responseBytes.size}\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Cache-Control: no-store\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(headers.toByteArray(Charsets.US_ASCII))
            out.write(responseBytes)
            out.flush()

            _runningServers.update { list ->
                list.map { if (it.port == port) it.copy(requestCount = it.requestCount + 1) else it }
            }
            _serverLogs.tryEmit("[127.0.0.1:$port] $statusCode $method $decodedPath")
        }
    }

    private fun buildDirectoryListingHtml(path: String, dir: File): String {
        val items = dir.listFiles()?.sortedBy { it.name }.orEmpty()
        val listHtml = items.joinToString("\n") { f ->
            val slash = if (f.isDirectory) "/" else ""
            val cleanBase = path.trimEnd('/')
            val href = "$cleanBase/${f.name}$slash"
            "<li><a href=\"$href\">${f.name}$slash</a> <span>(${if (f.isDirectory) "dir" else "${f.length()} B"})</span></li>"
        }
        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Índice de $path</title>
            <style>body{background:#0b0f14;color:#e6edf3;font-family:monospace;padding:24px}
            a{color:#10e89a;text-decoration:none}li{margin:8px 0}span{color:#94a3b8;font-size:12px}</style></head>
            <body><h2>Termux 2.0 HTTP Server — $path</h2><ul>$listHtml</ul></body></html>
        """.trimIndent()
    }

    private fun guessMimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "js", "mjs" -> "application/javascript; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "txt", "md", "log" -> "text/plain; charset=utf-8"
        else -> "application/octet-stream"
    }

    companion object {
        private val DEFAULT_INDEX_HTML = """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>Termux 2.0 • Localhost Dev Server</title>
              <link rel="stylesheet" href="/styles.css" />
            </head>
            <body>
              <main class="container">
                <header class="hero">
                  <span class="badge">127.0.0.1 • SERVIDOR LOCAL ATIVO</span>
                  <h1>Termux 2.0 Web Workspace</h1>
                  <p>Página HTML, CSS e JavaScript executando diretamente no servidor local integrado e renderizada no navegador nativo.</p>
                </header>

                <section class="grid">
                  <article class="card">
                    <h2>API Local (/api/status)</h2>
                    <pre id="api-output">Carregando diagnóstico...</pre>
                    <button id="refresh-api" class="btn">Consultar API</button>
                  </article>

                  <article class="card">
                    <h2>Teste Interativo JS</h2>
                    <p>Contador local: <strong id="counter-val">0</strong></p>
                    <div class="row">
                      <button id="inc-btn" class="btn">+1 Incrementar</button>
                      <a href="/admin.html" class="btn secondary">Painel Admin</a>
                    </div>
                  </article>
                </section>
              </main>
              <script src="/app.js"></script>
            </body>
            </html>
        """.trimIndent()

        private val DEFAULT_STYLES_CSS = """
            :root {
              --bg: #0b0f14;
              --surface: #131b26;
              --accent: #10e89a;
              --cyan: #38bdf8;
              --text: #e6edf3;
              --muted: #94a3b8;
            }
            * { box-sizing: border-box; }
            body {
              margin: 0;
              font-family: system-ui, -apple-system, sans-serif;
              background: var(--bg);
              color: var(--text);
              padding: 20px;
            }
            .container { max-width: 820px; margin: 0 auto; }
            .hero {
              background: linear-gradient(135deg, #111b27, #0c2620);
              border: 1px solid #1f3344;
              border-radius: 16px;
              padding: 24px;
              margin-bottom: 20px;
            }
            .badge {
              display: inline-block;
              font-family: monospace;
              font-size: 12px;
              color: var(--accent);
              background: rgba(16, 232, 154, 0.12);
              padding: 4px 10px;
              border-radius: 999px;
            }
            h1 { margin: 12px 0 8px; font-size: 24px; }
            p { color: var(--muted); line-height: 1.5; }
            .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 16px; }
            .card {
              background: var(--surface);
              border: 1px solid #243244;
              border-radius: 14px;
              padding: 18px;
            }
            pre {
              background: #090d12;
              color: var(--cyan);
              padding: 12px;
              border-radius: 10px;
              overflow-x: auto;
              font-size: 12px;
            }
            .row { display: flex; gap: 10px; flex-wrap: wrap; }
            .btn {
              background: var(--accent);
              color: #032116;
              border: none;
              border-radius: 10px;
              padding: 10px 14px;
              font-weight: 700;
              cursor: pointer;
              text-decoration: none;
              display: inline-block;
            }
            .btn.secondary { background: #1e293b; color: var(--text); }
        """.trimIndent()

        private val DEFAULT_APP_JS = """
            let count = 0;
            const counterEl = document.getElementById('counter-val');
            const apiOutput = document.getElementById('api-output');

            async function loadStatus() {
              try {
                const res = await fetch('/api/status');
                const data = await res.json();
                apiOutput.textContent = JSON.stringify(data, null, 2);
              } catch (err) {
                apiOutput.textContent = 'Erro ao consultar API: ' + err.message;
              }
            }

            document.getElementById('inc-btn')?.addEventListener('click', () => {
              count += 1;
              counterEl.textContent = String(count);
            });

            document.getElementById('refresh-api')?.addEventListener('click', loadStatus);
            loadStatus();
        """.trimIndent()

        private val DEFAULT_ADMIN_HTML = """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>Painel Admin Local • Termux 2.0</title>
              <link rel="stylesheet" href="/styles.css" />
            </head>
            <body>
              <main class="container">
                <header class="hero">
                  <span class="badge">ADMIN LOCALHOST</span>
                  <h1>Painel de Administração Local</h1>
                  <p>Inspecione arquivos do workspace e endpoints locais em tempo real.</p>
                  <a href="/" class="btn secondary">← Voltar para Home</a>
                </header>
                <article class="card">
                  <h2>Arquivos em termux_home (/api/files)</h2>
                  <pre id="files-out">Carregando...</pre>
                </article>
              </main>
              <script>
                fetch('/api/files')
                  .then(r => r.json())
                  .then(d => document.getElementById('files-out').textContent = JSON.stringify(d, null, 2));
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
