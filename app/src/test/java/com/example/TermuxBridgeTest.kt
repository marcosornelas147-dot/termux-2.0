package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.bridge.BridgeConnectionMethod
import com.example.bridge.BridgeConnectionState
import com.example.bridge.BridgeScriptGenerator
import com.example.bridge.BridgeSecurityPolicy
import com.example.bridge.ExternalStreamType
import com.example.bridge.TermuxBridgeManager
import com.example.server.LocalDevServerManager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TermuxBridgeTest {

    private class FakeBridgeDaemon(
        private val port: Int,
        private val expectedToken: String
    ) {
        private val serverSocket = ServerSocket(port, 20, InetAddress.getByName("127.0.0.1"))
        private val running = AtomicBoolean(true)
        private val tasks = ConcurrentHashMap<String, TaskState>()

        data class TaskState(
            var stdout: String = "",
            var stderr: String = "",
            var isRunning: Boolean = true,
            var exitCode: Int? = null,
            var killed: Boolean = false
        )

        init {
            thread(isDaemon = true) {
                while (running.get() && !serverSocket.isClosed) {
                    runCatching {
                        val sock = serverSocket.accept()
                        thread(isDaemon = true) { handle(sock) }
                    }
                }
            }
        }

        private fun handle(sock: java.net.Socket) {
            sock.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val reqLine = reader.readLine() ?: return
                val parts = reqLine.split(" ")
                val method = parts[0]
                val target = parts[1]
                var auth = ""
                var origin = ""
                var len = 0
                while (true) {
                    val h = reader.readLine() ?: break
                    if (h.isEmpty()) break
                    if (h.startsWith("Authorization:", true)) auth = h.substringAfter(":").trim()
                    if (h.startsWith("Origin:", true)) origin = h.substringAfter(":").trim()
                    if (h.startsWith("Content-Length:", true)) len = h.substringAfter(":").trim().toIntOrNull() ?: 0
                }
                val bodyBuf = CharArray(len)
                if (len > 0) reader.read(bodyBuf, 0, len)
                val bodyStr = String(bodyBuf)

                val out = BufferedOutputStream(s.getOutputStream())
                fun send(code: Int, json: String) {
                    val bytes = json.toByteArray()
                    val hdr = "HTTP/1.1 $code OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                    out.write(hdr.toByteArray())
                    out.write(bytes)
                    out.flush()
                }

                if (origin.isNotEmpty()) {
                    send(403, """{"error":"Origin rejected"}""")
                    return
                }
                if (auth != "Bearer $expectedToken") {
                    send(401, """{"error":"Unauthorized"}""")
                    return
                }

                when {
                    method == "GET" && target == "/health" -> {
                        send(200, """{"status":"online","arch":"aarch64","home":"/data/data/com.termux/files/home","runningTasks":0,"listeningPorts":[8080]}""")
                    }
                    method == "POST" && target == "/exec" -> {
                        val cmd = JSONObject(bodyStr).getString("command")
                        val taskId = "task-${System.nanoTime()}"
                        val state = TaskState()
                        tasks[taskId] = state
                        thread(isDaemon = true) {
                            when {
                                cmd.contains("long-build") -> {
                                    state.stdout += "[1/4] Compiling core...\n"
                                    Thread.sleep(250)
                                    if (!state.killed) {
                                        state.stdout += "[2/4] Linking...\n"
                                        Thread.sleep(250)
                                    }
                                    if (!state.killed) {
                                        state.exitCode = 0
                                        state.isRunning = false
                                    }
                                }
                                cmd.contains("fail-stderr") -> {
                                    state.stderr += "cmake: error: target not found\n"
                                    state.exitCode = 2
                                    state.isRunning = false
                                }
                                else -> {
                                    state.stdout += "aarch64 Linux\n"
                                    state.exitCode = 0
                                    state.isRunning = false
                                }
                            }
                        }
                        send(200, """{"taskId":"$taskId","pid":1234}""")
                    }
                    method == "GET" && target.startsWith("/tasks/") -> {
                        val pathOnly = target.substringBefore("?")
                        val id = pathOnly.removePrefix("/tasks/")
                        val st = tasks[id] ?: run { send(404, "{}"); return }
                        val qs = target.substringAfter("?", "")
                        val outOff = qs.split("&").firstOrNull { it.startsWith("offset_out=") }?.substringAfter("=")?.toIntOrNull() ?: 0
                        val errOff = qs.split("&").firstOrNull { it.startsWith("offset_err=") }?.substringAfter("=")?.toIntOrNull() ?: 0
                        val json = JSONObject().apply {
                            put("taskId", id)
                            put("running", st.isRunning)
                            put("exitCode", st.exitCode ?: JSONObject.NULL)
                            put("stdoutChunk", st.stdout.drop(outOff))
                            put("stderrChunk", st.stderr.drop(errOff))
                            put("nextOutOffset", st.stdout.length)
                            put("nextErrOffset", st.stderr.length)
                        }
                        send(200, json.toString())
                    }
                    method == "POST" && target.endsWith("/kill") -> {
                        val id = target.removePrefix("/tasks/").removeSuffix("/kill")
                        tasks[id]?.let {
                            it.killed = true
                            it.isRunning = false
                            it.exitCode = -1
                        }
                        send(200, """{"killed":true}""")
                    }
                    else -> send(404, "{}")
                }
            }
        }

        fun stop() {
            running.set(false)
            runCatching { serverSocket.close() }
        }
    }

    @Test
    fun `bridge security policy blocks remote hosts traversal and detects destructive commands`() {
        assertTrue(BridgeSecurityPolicy.isLoopbackHost("127.0.0.1"))
        assertTrue(BridgeSecurityPolicy.isLoopbackHost("localhost"))
        assertFalse(BridgeSecurityPolicy.isLoopbackHost("192.168.1.50"))
        assertFalse(BridgeSecurityPolicy.isLoopbackHost("8.8.8.8"))

        assertNotNull(BridgeSecurityPolicy.detectDestructiveReason("rm -rf /data/data/com.termux/files/home"))
        assertNotNull(BridgeSecurityPolicy.detectDestructiveReason("dd if=/dev/zero of=/dev/sda"))
        assertNull(BridgeSecurityPolicy.detectDestructiveReason("cmake --build build --parallel 4"))

        val allowed = "/data/data/com.termux/files/home"
        assertTrue(BridgeSecurityPolicy.isAllowedWorkingDirectory(allowed, allowed))
        assertTrue(BridgeSecurityPolicy.isAllowedWorkingDirectory("$allowed/projeto", allowed))
        assertFalse(BridgeSecurityPolicy.isAllowedWorkingDirectory("/system/bin", allowed))
        assertFalse(BridgeSecurityPolicy.isAllowedWorkingDirectory("$allowed/../etc", allowed))
    }

    @Test
    fun `bridge connect execute stdout stderr interrupt reconnect and service restart`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = TermuxBridgeManager(context)
        val secrets = manager.credentialStore.getOrCreateSecrets()
        val port = 18765
        var daemon = FakeBridgeDaemon(port, secrets.apiToken)

        try {
            // 1. Connect
            val connected = manager.connect("127.0.0.1", port, 18022, "u0_a", BridgeConnectionMethod.HTTP_API)
            assertTrue(connected)
            assertEquals(BridgeConnectionState.CONNECTED, manager.status.value.state)
            assertEquals("aarch64", manager.status.value.remoteUname)
            assertTrue(manager.status.value.detectedListeningPorts.contains(8080))

            // 2. Origin rejection from arbitrary web page (raw socket avoids HttpURLConnection stripping Origin)
            java.net.Socket("127.0.0.1", port).use { sock ->
                val rawReq = (
                    "GET /health HTTP/1.1\r\n" +
                        "Host: 127.0.0.1:$port\r\n" +
                        "Authorization: Bearer ${secrets.apiToken}\r\n" +
                        "Origin: https://evil.example.com\r\n" +
                        "Connection: close\r\n\r\n"
                    ).toByteArray()
                sock.getOutputStream().write(rawReq)
                sock.getOutputStream().flush()
                val statusLine = BufferedReader(InputStreamReader(sock.getInputStream())).readLine() ?: ""
                assertTrue("Expected 403 but got: $statusLine", statusLine.contains("403"))
            }

            fun waitUntil(timeoutMs: Long = 3000L, condition: () -> Boolean) {
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    if (condition()) return
                    Thread.sleep(30)
                }
                assertTrue("Timed out waiting for condition", condition())
            }

            // 3. Send command & receive stdout
            val sessId = manager.activeSessionId.value
            manager.executeCommandInSession(
                sessionId = sessId,
                rawCommand = "uname -a",
                allowedRootsCsv = "/data/data/com.termux/files/home",
                onAuditStart = { _, _, _, _ -> 1L },
                onAuditFinish = { _, _, _ -> }
            )
            waitUntil {
                manager.sessions.value.first().outputLines.any {
                    it.stream == ExternalStreamType.STDOUT && it.text.contains("aarch64")
                }
            }

            // 4. Send command returning stderr & non-zero exit
            manager.executeCommandInSession(
                sessionId = sessId,
                rawCommand = "fail-stderr",
                allowedRootsCsv = "/data/data/com.termux/files/home",
                onAuditStart = { _, _, _, _ -> 2L },
                onAuditFinish = { _, _, _ -> }
            )
            waitUntil {
                manager.sessions.value.first().outputLines.any {
                    it.stream == ExternalStreamType.STDERR && it.text.contains("target not found")
                }
            }

            // 5. Long process + interruption
            manager.executeCommandInSession(
                sessionId = sessId,
                rawCommand = "long-build",
                allowedRootsCsv = "/data/data/com.termux/files/home",
                onAuditStart = { _, _, _, _ -> 3L },
                onAuditFinish = { _, _, _ -> }
            )
            waitUntil { manager.sessions.value.first().isRunning }
            manager.interruptSessionProcess(sessId)
            waitUntil { !manager.sessions.value.first().isRunning }

            // 6. Disconnect + Service Restart + Reconnect
            manager.disconnect()
            assertEquals(BridgeConnectionState.DISCONNECTED, manager.status.value.state)
            daemon.stop()
            Thread.sleep(100)
            daemon = FakeBridgeDaemon(port, secrets.apiToken)
            val reconnected = manager.connect("127.0.0.1", port, 18022, "u0_a", BridgeConnectionMethod.SCRIPT_AUTO)
            assertTrue(reconnected)
            assertEquals(BridgeConnectionState.CONNECTED, manager.status.value.state)
        } finally {
            daemon.stop()
            manager.destroy()
        }
    }

    @Test
    fun `installer script includes loopback bind bearer token and ssh key`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = TermuxBridgeManager(context)
        val secrets = manager.credentialStore.getOrCreateSecrets()
        val script = BridgeScriptGenerator.buildSetupScript(
            secrets = secrets,
            httpPort = 8765,
            sshPort = 8022,
            allowedRootsCsv = "/data/data/com.termux/files/home"
        )
        assertTrue(script.contains("BRIDGE_HOST=127.0.0.1"))
        assertTrue(script.contains("BRIDGE_PORT=8765"))
        assertTrue(script.contains(secrets.apiToken))
        assertTrue(script.contains(secrets.sshPublicKeyOpenSsh))
        assertTrue(script.contains("pkg install -y python openssh"))

        // Verify Browser can also communicate with a local loopback server on 127.0.0.1
        val localServer = LocalDevServerManager(context)
        assertTrue(localServer.startServer(18089, "bridge-browser-test").isSuccess)
        try {
            val conn = URL("http://127.0.0.1:18089/").openConnection() as HttpURLConnection
            assertEquals(200, conn.responseCode)
            conn.disconnect()
        } finally {
            localServer.stopAll()
        }
    }
}
