package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.browser.BrowserUrlUtils
import com.example.browser.HOME_TAB_URL
import com.example.browser.PageSecurityState
import com.example.server.LinuxShellEngine
import com.example.server.LocalDevServerManager
import com.example.server.ShellSessionState
import com.example.server.TerminalCommandProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Termux 2_0`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Termux 2.0", appName)
    }

    @Test
    fun `url normalization handles localhost domains search and blocks file scheme`() {
        assertEquals("http://127.0.0.1:8080", BrowserUrlUtils.normalizeInputToUrl("127.0.0.1:8080", true))
        assertEquals("http://localhost:3000", BrowserUrlUtils.normalizeInputToUrl("localhost:3000", true))
        assertEquals("https://github.com", BrowserUrlUtils.normalizeInputToUrl("github.com", true))
        assertEquals(HOME_TAB_URL, BrowserUrlUtils.normalizeInputToUrl("file:///etc/passwd", true))
        assertEquals(HOME_TAB_URL, BrowserUrlUtils.normalizeInputToUrl("content://com.android.contacts", true))
        assertTrue(BrowserUrlUtils.normalizeInputToUrl("kotlin compose", true).contains("google.com/search?q="))

        assertEquals(PageSecurityState.LOCALHOST, BrowserUrlUtils.resolveSecurityState("http://127.0.0.1:8080"))
        assertEquals(PageSecurityState.HTTPS_SECURE, BrowserUrlUtils.resolveSecurityState("https://github.com"))
        assertEquals(PageSecurityState.HTTP_INSECURE, BrowserUrlUtils.resolveSecurityState("http://example.com"))
    }

    @Test
    fun `local dev server serves real html and json over loopback tcp`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val serverManager = LocalDevServerManager(context)
        val started = serverManager.startServer(18080, "test-http")
        assertTrue(started.isSuccess)

        try {
            Thread.sleep(150)
            val conn = URL("http://127.0.0.1:18080/").openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            assertEquals(200, conn.responseCode)
            val html = conn.inputStream.bufferedReader().readText()
            assertTrue(html.contains("Termux 2.0 Web Workspace"))
            conn.disconnect()

            val apiConn = URL("http://127.0.0.1:18080/api/status").openConnection() as HttpURLConnection
            assertEquals(200, apiConn.responseCode)
            val json = apiConn.inputStream.bufferedReader().readText()
            assertTrue(json.contains("\"status\": \"online\""))
            apiConn.disconnect()
        } finally {
            serverManager.stopAll()
        }
    }

    @Test
    fun `terminal command processor creates html file and starts or stops server`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val serverManager = LocalDevServerManager(context)
        var openedUrl: String? = null
        val processor = TerminalCommandProcessor(context, serverManager) { openedUrl = it }

        try {
            processor.executeCommand("echo \"<h1>Teste</h1>\" > custom.html")
            assertEquals("<h1>Teste</h1>\n", processor.readWorkspaceFile("custom.html"))

            processor.executeCommand("python -m http.server 18081")
            assertTrue(serverManager.runningServers.value.any { it.port == 18081 })

            processor.executeCommand("open 127.0.0.1:18081/custom.html")
            assertEquals("http://127.0.0.1:18081/custom.html", openedUrl)

            processor.executeCommand("kill 18081")
            assertFalse(serverManager.runningServers.value.any { it.port == 18081 })
        } finally {
            serverManager.stopAll()
        }
    }

    @Test
    fun `linux shell engine supports pipelines redirections variables text processing archives and math`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val serverManager = LocalDevServerManager(context)
        val session = ShellSessionState(
            context = context,
            homeDir = serverManager.homeDir,
            serverManager = serverManager,
            onOpenUrlInBrowser = {},
            onClearTerminal = {},
            onRefreshWorkspace = {}
        )
        val engine = LinuxShellEngine(session)

        // Variables, chaining && / ||, redirection > / >>
        engine.executeLine("export APP=Termux2 && echo \$APP > notes.txt && echo \"linux\" >> notes.txt")
        val catRes = engine.executeLine("cat notes.txt | tr a-z A-Z | sort -r")
        assertEquals(0, catRes.exitCode)
        assertEquals("TERMUX2\nLINUX", catRes.stdout)

        // grep, sed, awk, wc
        val grepRes = engine.executeLine("cat notes.txt | grep -i linux | sed 's/linux/kernel/' | wc -w")
        assertEquals("1", grepRes.stdout.trim())

        val awkRes = engine.executeLine("echo \"alpha:beta:gamma\" | awk -F : '{print \$2}'")
        assertEquals("beta", awkRes.stdout.trim())

        // jq JSON query
        val jqRes = engine.executeLine("cat api/info.json | jq .version")
        assertEquals("2.0.0", jqRes.stdout.trim())

        // bc math & base64 & sha256sum
        val bcRes = engine.executeLine("echo \"(10 + 5) * 2\" | bc")
        assertEquals("30", bcRes.stdout.trim())

        val b64Res = engine.executeLine("echo -n \"hello\" | base64 | base64 -d")
        assertEquals("hello", b64Res.stdout.trim())

        val shaRes = engine.executeLine("sha256sum notes.txt")
        assertEquals(0, shaRes.exitCode)
        assertTrue(shaRes.stdout.contains("notes.txt"))

        // zip & unzip -l
        val zipRes = engine.executeLine("zip bundle.zip notes.txt && unzip -l bundle.zip")
        assertEquals(0, zipRes.exitCode)
        assertTrue(zipRes.stdout.contains("notes.txt"))

        // find & tree & man
        val findRes = engine.executeLine("find . -name \"*.html\"")
        assertTrue(findRes.stdout.contains("index.html"))

        val manRes = engine.executeLine("man grep")
        assertEquals(0, manRes.exitCode)
        assertTrue(manRes.stdout.contains("SINOPSE"))
    }
}
