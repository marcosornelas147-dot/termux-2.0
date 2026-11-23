package com.example.server

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object LinuxSystemAndNetCommands {

    fun executeSystemOrNet(
        cmd: String,
        args: List<String>,
        stdin: String,
        session: ShellSessionState
    ): CommandResult? {
        return when (cmd) {
            "help" -> handleHelp()
            "man" -> handleMan(args)
            "clear", "cls", "reset" -> {
                session.onClearTerminal()
                CommandResult(0, "")
            }
            "true" -> CommandResult(0, "")
            "false" -> CommandResult(1, "")
            "whoami" -> CommandResult(0, session.env["USER"] ?: "u0_termux2")
            "id" -> CommandResult(0, "uid=1000(u0_termux2) gid=1000(u0_termux2) groups=1000(u0_termux2),3003(inet)")
            "hostname" -> CommandResult(0, "termux-2.0")
            "uname" -> handleUname(args)
            "uptime" -> handleUptime(session)
            "date" -> handleDate(args)
            "cal" -> handleCal()
            "free" -> handleFree(args, session)
            "nproc" -> CommandResult(0, Runtime.getRuntime().availableProcessors().toString())
            "lscpu" -> handleLscpu()
            "neofetch" -> handleNeofetch(session)
            "pkg", "apt" -> handlePkg(args)
            "which", "type" -> handleWhich(args, session)
            "history" -> handleHistory(session)
            "env", "printenv" -> handleEnv(args, session)
            "export" -> handleExport(args, session)
            "unset" -> handleUnset(args, session)
            "alias" -> handleAlias(args, session)
            "unalias" -> handleUnalias(args, session)
            "sleep" -> handleSleep(args)
            "expr", "bc" -> handleMath(args, stdin)
            "test", "[" -> handleTest(args, session)

            // Compression & Crypto
            "md5sum" -> handleDigest(args, stdin, session, "MD5")
            "sha1sum" -> handleDigest(args, stdin, session, "SHA-1")
            "sha256sum" -> handleDigest(args, stdin, session, "SHA-256")
            "sha512sum" -> handleDigest(args, stdin, session, "SHA-512")
            "base64" -> handleBase64(args, stdin, session)
            "xxd", "hexdump" -> handleHexDump(args, stdin, session)
            "zip" -> handleZip(args, session)
            "unzip" -> handleUnzip(args, session)
            "gzip" -> handleGzip(args, session)
            "gunzip" -> handleGunzip(args, session)

            // Network & Local Servers
            "ps", "top", "servers" -> handlePs(session)
            "ss", "netstat" -> handleNetstat(session)
            "kill" -> handleKill(args, session)
            "python", "python3" -> handlePython(args, session)
            "serve", "node" -> handleServe(args, session)
            "php" -> handlePhp(args, session)
            "open" -> handleOpen(args, session)
            "curl" -> handleCurl(args)
            "wget" -> handleWget(args, session)
            "ping" -> handlePing(args)
            "nslookup", "dig" -> handleDns(args)
            "ifconfig", "ip" -> handleIfconfig()

            else -> null
        }
    }

    private fun handleHelp(): CommandResult {
        val grouped = LinuxCommandCatalog.docs.groupBy { it.category }
        val sb = StringBuilder()
        sb.appendLine("TERMUX 2.0 • Sistema Linux Integrado (${LinuxCommandCatalog.docs.size}+ comandos)")
        grouped.forEach { (cat, list) ->
            sb.appendLine("\n[$cat]")
            sb.appendLine("  " + list.joinToString(", ") { it.name })
        }
        sb.appendLine("\nOperadores: | (pipe), > (sobrescrever), >> (anexar), ; && ||, \$VAR")
        sb.append("Use 'man <comando>' ou toque no ícone de Guia no topo do Terminal.")
        return CommandResult(0, sb.toString(), isSuccessStyle = true)
    }

    private fun handleMan(args: List<String>): CommandResult {
        val target = args.firstOrNull()
            ?: return CommandResult(1, stderr = "Qual página de manual você deseja? Ex: man grep")
        val doc = LinuxCommandCatalog.findDoc(target)
            ?: return CommandResult(1, stderr = "Nenhuma entrada no manual para '$target'")
        val page = """
            NOME
                ${doc.name} - ${doc.summary}

            SINOPSE
                ${doc.synopsis}

            CATEGORIA
                ${doc.category}

            EXEMPLO
                ${doc.example}
        """.trimIndent()
        return CommandResult(0, page)
    }

    private fun handleUname(args: List<String>): CommandResult {
        val all = args.contains("-a")
        val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "aarch64"
        return when {
            all || args.isEmpty() && args.contains("-a") ->
                CommandResult(0, "Linux termux-2.0 ${Build.VERSION.RELEASE}-android #1 SMP PREEMPT $arch Android/GNU")
            args.contains("-s") -> CommandResult(0, "Linux")
            args.contains("-n") -> CommandResult(0, "termux-2.0")
            args.contains("-r") -> CommandResult(0, "${Build.VERSION.RELEASE}-android")
            args.contains("-m") -> CommandResult(0, arch)
            else -> CommandResult(0, "Linux")
        }
    }

    private fun handleUptime(session: ShellSessionState): CommandResult {
        val elapsedSec = (System.currentTimeMillis() - session.startTimeMs) / 1000
        val min = elapsedSec / 60
        val sec = elapsedSec % 60
        val now = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        return CommandResult(0, " $now up ${min}m ${sec}s,  1 user,  servers active: ${session.serverManager.runningServers.value.size}")
    }

    private fun handleDate(args: List<String>): CommandResult {
        val fmtArg = args.firstOrNull { it.startsWith("+") }
        val now = Date()
        if (fmtArg == null) {
            return CommandResult(0, SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.getDefault()).format(now))
        }
        val pattern = fmtArg.drop(1)
            .replace("%Y", "yyyy")
            .replace("%m", "MM")
            .replace("%d", "dd")
            .replace("%H", "HH")
            .replace("%M", "mm")
            .replace("%S", "ss")
        return runCatching {
            CommandResult(0, SimpleDateFormat(pattern, Locale.getDefault()).format(now))
        }.getOrElse {
            CommandResult(0, now.toString())
        }
    }

    private fun handleCal(): CommandResult {
        val cal = Calendar.getInstance()
        val monthName = SimpleDateFormat("MMMM yyyy", Locale("pt", "BR")).format(cal.time)
        val today = cal.get(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDow = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val sb = StringBuilder()
        sb.appendLine("    ${monthName.replaceFirstChar { it.uppercase() }}")
        sb.appendLine("Do Se Te Qu Qu Se Sá")
        repeat(firstDow) { sb.append("   ") }
        for (d in 1..maxDays) {
            val mark = if (d == today) "*${d.toString().padStart(1)}" else d.toString().padStart(2)
            sb.append(mark.padStart(2)).append(" ")
            if ((firstDow + d) % 7 == 0 || d == maxDays) sb.appendLine()
        }
        return CommandResult(0, sb.toString().trimEnd('\n'))
    }

    private fun handleFree(args: List<String>, session: ShellSessionState): CommandResult {
        val human = args.any { it.contains('h') }
        val am = session.context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val total = info.totalMem
        val avail = info.availMem
        val used = total - avail
        fun fmt(b: Long) = if (human) LinuxFsAndTextCommands.humanBytes(b) else "${b / (1024 * 1024)}M"
        return CommandResult(
            0,
            "               total        used        free\n" +
                "Mem:       ${fmt(total).padStart(9)}   ${fmt(used).padStart(9)}   ${fmt(avail).padStart(9)}"
        )
    }

    private fun handleLscpu(): CommandResult {
        val cores = Runtime.getRuntime().availableProcessors()
        val abis = Build.SUPPORTED_ABIS.joinToString(", ")
        return CommandResult(
            0,
            """
            Architecture:        ${Build.SUPPORTED_ABIS.firstOrNull() ?: "aarch64"}
            CPU(s):              $cores
            Supported ABIs:      $abis
            Hardware/Board:      ${Build.HARDWARE} (${Build.BOARD})
            """.trimIndent()
        )
    }

    private fun handleNeofetch(session: ShellSessionState): CommandResult {
        val am = session.context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val totalMb = info.totalMem / (1024 * 1024)
        val usedMb = (info.totalMem - info.availMem) / (1024 * 1024)
        val cores = Runtime.getRuntime().availableProcessors()
        val servers = session.serverManager.runningServers.value.size

        val output = """
             ____  ____  ____  __  __  _  _  _  _    u0_termux2@termux-2.0
            (_  _)( ___)(  _ \(  \/  )( )( )( \/ )   -----------------------
              )(   )__)  )   / )    (  )()(  )  (    OS: Termux 2.0 Linux (Android ${Build.VERSION.RELEASE})
             (__) (____)(_)\_)(_/\/\_)(____)(_/\_)   Host: ${Build.MANUFACTURER} ${Build.MODEL}
                                                     Kernel: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "aarch64"} (SDK ${Build.VERSION.SDK_INT})
                                                     Shell: bash-emu 2.0
                                                     CPU: $cores cores (${Build.HARDWARE})
                                                     Memory: ${usedMb}MB / ${totalMb}MB
                                                     Local HTTP Servers: $servers ativo(s)
        """.trimIndent()
        return CommandResult(0, output, isSuccessStyle = true)
    }

    private fun handlePkg(args: List<String>): CommandResult {
        val sub = args.firstOrNull()?.lowercase() ?: "list"
        return when (sub) {
            "list", "list-installed" -> {
                val list = LinuxCommandCatalog.docs.joinToString("\n") {
                    "${it.name.padEnd(12)} [${it.category}] - ${it.summary}"
                }
                CommandResult(0, "Pacotes/Ferramentas integradas no Termux 2.0:\n$list")
            }
            "update", "upgrade" -> CommandResult(0, "Todos os módulos nativos do Termux 2.0 já estão atualizados.", isSuccessStyle = true)
            else -> CommandResult(0, "Uso: pkg list | pkg update")
        }
    }

    private fun handleWhich(args: List<String>, session: ShellSessionState): CommandResult {
        val target = args.firstOrNull() ?: return CommandResult(1)
        if (session.aliases.containsKey(target)) {
            return CommandResult(0, "$target: aliased to ${session.aliases[target]}")
        }
        return if (LinuxCommandCatalog.findDoc(target) != null || target in setOf("pwd", "cd", "ls", "echo", "cat", "clear", "help")) {
            CommandResult(0, "/usr/bin/$target")
        } else {
            CommandResult(1, stderr = "$target: comando não encontrado")
        }
    }

    private fun handleHistory(session: ShellSessionState): CommandResult {
        if (session.commandHistory.isEmpty()) return CommandResult(0, "")
        val out = session.commandHistory.mapIndexed { idx, s ->
            "${(idx + 1).toString().padStart(4)}  $s"
        }.joinToString("\n")
        return CommandResult(0, out)
    }

    private fun handleEnv(args: List<String>, session: ShellSessionState): CommandResult {
        val key = args.firstOrNull()
        return if (key != null) {
            val v = session.env[key]
            if (v != null) CommandResult(0, v) else CommandResult(1)
        } else {
            CommandResult(0, session.env.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value}" })
        }
    }

    private fun handleExport(args: List<String>, session: ShellSessionState): CommandResult {
        if (args.isEmpty()) return handleEnv(emptyList(), session)
        for (a in args) {
            if (a.contains("=")) {
                val k = a.substringBefore("=")
                val v = a.substringAfter("=")
                session.env[k] = v
            }
        }
        return CommandResult(0, "")
    }

    private fun handleUnset(args: List<String>, session: ShellSessionState): CommandResult {
        args.forEach { session.env.remove(it) }
        return CommandResult(0, "")
    }

    private fun handleAlias(args: List<String>, session: ShellSessionState): CommandResult {
        if (args.isEmpty()) {
            val out = session.aliases.entries.sortedBy { it.key }.joinToString("\n") { "alias ${it.key}='${it.value}'" }
            return CommandResult(0, out)
        }
        val joined = args.joinToString(" ")
        if (joined.contains("=")) {
            val name = joined.substringBefore("=").trim()
            val value = joined.substringAfter("=").trim().trim('\'', '"')
            session.aliases[name] = value
            return CommandResult(0, "")
        }
        val found = session.aliases[joined]
        return if (found != null) CommandResult(0, "alias $joined='$found'") else CommandResult(1, stderr = "alias: $joined: não encontrado")
    }

    private fun handleUnalias(args: List<String>, session: ShellSessionState): CommandResult {
        args.forEach { session.aliases.remove(it) }
        return CommandResult(0, "")
    }

    private fun handleSleep(args: List<String>): CommandResult {
        val sec = args.firstOrNull()?.toDoubleOrNull()?.coerceIn(0.0, 15.0) ?: 1.0
        Thread.sleep((sec * 1000).toLong())
        return CommandResult(0, "")
    }

    private fun handleMath(args: List<String>, stdin: String): CommandResult {
        val exprStr = if (args.isNotEmpty()) args.joinToString(" ") else stdin.trim()
        if (exprStr.isBlank()) return CommandResult(1, stderr = "Uso: bc <<< '2 + 2' ou echo '10 * 5' | bc")
        return runCatching {
            val result = evalSimpleArithmetic(exprStr)
            val formatted = if (result % 1.0 == 0.0) result.toLong().toString() else result.toString()
            CommandResult(0, formatted)
        }.getOrElse { e ->
            CommandResult(1, stderr = "erro aritmético: ${e.message}")
        }
    }

    private fun evalSimpleArithmetic(expr: String): Double {
        val tokens = Regex("\\d+(\\.\\d+)?|[+\\-*/%()]").findAll(expr).map { it.value }.toList()
        var pos = 0

        lateinit var parseExpr: () -> Double

        fun parseFactor(): Double {
            val tok = tokens.getOrNull(pos) ?: throw IllegalArgumentException("fim inesperado")
            return when {
                tok == "(" -> {
                    pos++
                    val v = parseExpr()
                    if (tokens.getOrNull(pos) == ")") pos++
                    v
                }
                tok == "-" -> {
                    pos++
                    -parseFactor()
                }
                else -> {
                    pos++
                    tok.toDouble()
                }
            }
        }

        fun parseTerm(): Double {
            var v = parseFactor()
            while (pos < tokens.size && tokens[pos] in listOf("*", "/", "%")) {
                val op = tokens[pos++]
                val r = parseFactor()
                v = when (op) {
                    "*" -> v * r
                    "/" -> v / r
                    else -> v % r
                }
            }
            return v
        }

        parseExpr = {
            var v = parseTerm()
            while (pos < tokens.size && tokens[pos] in listOf("+", "-")) {
                val op = tokens[pos++]
                val r = parseTerm()
                v = if (op == "+") v + r else v - r
            }
            v
        }

        return parseExpr()
    }

    private fun handleTest(args: List<String>, session: ShellSessionState): CommandResult {
        val clean = args.filterNot { it == "]" }
        if (clean.size == 2) {
            val op = clean[0]
            val arg = clean[1]
            val pass = when (op) {
                "-f" -> session.resolveSafe(arg)?.isFile == true
                "-d" -> session.resolveSafe(arg)?.isDirectory == true
                "-e" -> session.resolveSafe(arg)?.exists() == true
                "-z" -> arg.isEmpty()
                "-n" -> arg.isNotEmpty()
                else -> false
            }
            return CommandResult(if (pass) 0 else 1)
        }
        if (clean.size == 3) {
            val a = clean[0]
            val op = clean[1]
            val b = clean[2]
            val pass = when (op) {
                "=", "==" -> a == b
                "!=" -> a != b
                "-eq" -> a.toLongOrNull() == b.toLongOrNull()
                "-ne" -> a.toLongOrNull() != b.toLongOrNull()
                "-gt" -> (a.toLongOrNull() ?: 0) > (b.toLongOrNull() ?: 0)
                "-lt" -> (a.toLongOrNull() ?: 0) < (b.toLongOrNull() ?: 0)
                else -> false
            }
            return CommandResult(if (pass) 0 else 1)
        }
        return CommandResult(if (clean.isNotEmpty()) 0 else 1)
    }

    private fun handleDigest(args: List<String>, stdin: String, session: ShellSessionState, algo: String): CommandResult {
        val files = args.filterNot { it.startsWith("-") }
        val md = MessageDigest.getInstance(algo)
        if (files.isEmpty()) {
            val hash = md.digest(stdin.toByteArray()).joinToString("") { "%02x".format(it) }
            return CommandResult(0, "$hash  -")
        }
        val sb = StringBuilder()
        for (name in files) {
            val f = session.resolveSafe(name) ?: return CommandResult(1, stderr = "$algo: caminho inválido")
            if (!f.isFile) return CommandResult(1, stderr = "$algo: $name: Arquivo não encontrado")
            val hash = md.digest(f.readBytes()).joinToString("") { "%02x".format(it) }
            sb.appendLine("$hash  $name")
        }
        return CommandResult(0, sb.toString().trimEnd('\n'))
    }

    private fun handleBase64(args: List<String>, stdin: String, session: ShellSessionState): CommandResult {
        val decode = args.any { it == "-d" || it == "--decode" }
        val fileArg = args.firstOrNull { !it.startsWith("-") }
        val inputBytes = if (fileArg != null) {
            val f = session.resolveSafe(fileArg) ?: return CommandResult(1, stderr = "base64: caminho inválido")
            if (!f.isFile) return CommandResult(1, stderr = "base64: $fileArg não encontrado")
            f.readBytes()
        } else {
            stdin.trimEnd('\n').toByteArray()
        }
        return runCatching {
            if (decode) {
                val decoded = Base64.decode(inputBytes, Base64.DEFAULT)
                CommandResult(0, String(decoded, Charsets.UTF_8))
            } else {
                val encoded = Base64.encodeToString(inputBytes, Base64.NO_WRAP)
                CommandResult(0, encoded)
            }
        }.getOrElse { e ->
            CommandResult(1, stderr = "base64: entrada inválida (${e.message})")
        }
    }

    private fun handleHexDump(args: List<String>, stdin: String, session: ShellSessionState): CommandResult {
        val fileArg = args.firstOrNull { !it.startsWith("-") }
        val bytes = if (fileArg != null) {
            val f = session.resolveSafe(fileArg) ?: return CommandResult(1, stderr = "xxd: caminho inválido")
            if (!f.isFile) return CommandResult(1, stderr = "xxd: $fileArg não encontrado")
            f.readBytes().take(512).toByteArray()
        } else {
            stdin.toByteArray().take(512).toByteArray()
        }
        val sb = StringBuilder()
        var offset = 0
        while (offset < bytes.size) {
            val chunk = bytes.sliceArray(offset until minOf(offset + 16, bytes.size))
            val hex = chunk.joinToString(" ") { "%02x".format(it) }.padEnd(47)
            val ascii = chunk.map { b ->
                val c = b.toInt() and 0xFF
                if (c in 32..126) c.toChar() else '.'
            }.joinToString("")
            sb.appendLine("%08x: %s  %s".format(offset, hex, ascii))
            offset += 16
        }
        return CommandResult(0, sb.toString().trimEnd('\n'))
    }

    private fun handleZip(args: List<String>, session: ShellSessionState): CommandResult {
        val positional = args.filterNot { it.startsWith("-") }
        if (positional.size < 2) return CommandResult(1, stderr = "zip: uso: zip <saida.zip> <arquivos...>")
        val outFile = session.resolveSafe(positional[0]) ?: return CommandResult(1, stderr = "zip: destino inválido")
        outFile.parentFile?.mkdirs()

        val log = StringBuilder()
        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            for (srcArg in positional.drop(1)) {
                val src = session.resolveSafe(srcArg) ?: continue
                if (!src.exists()) continue
                src.walkTopDown().filter { it.isFile }.forEach { f ->
                    val entryName = f.relativeTo(session.currentDir).path
                    zos.putNextEntry(ZipEntry(entryName))
                    FileInputStream(f).use { it.copyTo(zos) }
                    zos.closeEntry()
                    log.appendLine("  adding: $entryName (${f.length()} bytes)")
                }
            }
        }
        session.onRefreshWorkspace()
        return CommandResult(0, log.toString().trimEnd('\n'), isSuccessStyle = true)
    }

    private fun handleUnzip(args: List<String>, session: ShellSessionState): CommandResult {
        val listOnly = args.any { it == "-l" }
        val zipArg = args.firstOrNull { !it.startsWith("-") }
            ?: return CommandResult(1, stderr = "unzip: especifique um arquivo .zip")
        val zipFile = session.resolveSafe(zipArg) ?: return CommandResult(1, stderr = "unzip: caminho inválido")
        if (!zipFile.isFile) return CommandResult(1, stderr = "unzip: '$zipArg' não encontrado")

        val out = StringBuilder()
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (listOnly) {
                    out.appendLine("  ${entry.name} (${entry.size} bytes)")
                } else if (!entry.isDirectory) {
                    val dest = File(session.currentDir, entry.name).canonicalFile
                    if (dest.path.startsWith(session.homeDir.canonicalPath)) {
                        dest.parentFile?.mkdirs()
                        FileOutputStream(dest).use { fos -> zis.copyTo(fos) }
                        out.appendLine("  inflating: ${entry.name}")
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        if (!listOnly) session.onRefreshWorkspace()
        return CommandResult(0, out.toString().trimEnd('\n'), isSuccessStyle = true)
    }

    private fun handleGzip(args: List<String>, session: ShellSessionState): CommandResult {
        val name = args.firstOrNull { !it.startsWith("-") } ?: return CommandResult(1, stderr = "gzip: informe o arquivo")
        val src = session.resolveSafe(name) ?: return CommandResult(1, stderr = "gzip: caminho inválido")
        if (!src.isFile) return CommandResult(1, stderr = "gzip: '$name' não encontrado")
        val dst = File(src.parentFile, "${src.name}.gz")
        GZIPOutputStream(FileOutputStream(dst)).use { gz ->
            FileInputStream(src).use { it.copyTo(gz) }
        }
        src.delete()
        session.onRefreshWorkspace()
        return CommandResult(0, "Compactado: ${dst.name}", isSuccessStyle = true)
    }

    private fun handleGunzip(args: List<String>, session: ShellSessionState): CommandResult {
        val name = args.firstOrNull { !it.startsWith("-") } ?: return CommandResult(1, stderr = "gunzip: informe arquivo .gz")
        val src = session.resolveSafe(name) ?: return CommandResult(1, stderr = "gunzip: caminho inválido")
        if (!src.isFile) return CommandResult(1, stderr = "gunzip: '$name' não encontrado")
        val outName = if (src.name.endsWith(".gz")) src.name.removeSuffix(".gz") else "${src.name}.out"
        val dst = File(src.parentFile, outName)
        GZIPInputStream(FileInputStream(src)).use { gz ->
            FileOutputStream(dst).use { gz.copyTo(it) }
        }
        src.delete()
        session.onRefreshWorkspace()
        return CommandResult(0, "Descompactado: ${dst.name}", isSuccessStyle = true)
    }

    private fun handlePs(session: ShellSessionState): CommandResult {
        val servers = session.serverManager.runningServers.value
        val sb = StringBuilder()
        sb.appendLine("  PID  PORT    TTY     CMD")
        sb.appendLine("    1  -       pts/0   /bin/bash (Termux 2.0)")
        servers.forEach { srv ->
            sb.appendLine("${(100 + srv.port % 900).toString().padStart(5)}  :${srv.port.toString().padEnd(5)}  pts/0   ${srv.label} (${srv.requestCount} reqs)")
        }
        return CommandResult(0, sb.toString().trimEnd('\n'))
    }

    private fun handleNetstat(session: ShellSessionState): CommandResult {
        val servers = session.serverManager.runningServers.value
        val sb = StringBuilder("Proto  Local Address          State       Process\n")
        if (servers.isEmpty()) {
            sb.append("(nenhuma porta TCP em escuta)")
        } else {
            servers.forEach { s ->
                sb.appendLine("tcp    127.0.0.1:${s.port.toString().padEnd(11)} LISTEN      ${s.label}")
            }
        }
        return CommandResult(0, sb.toString().trimEnd('\n'))
    }

    private fun handleKill(args: List<String>, session: ShellSessionState): CommandResult {
        val port = args.lastOrNull()?.toIntOrNull()
            ?: return CommandResult(1, stderr = "Uso: kill <porta>")
        return if (session.serverManager.stopServer(port)) {
            CommandResult(0, "Servidor na porta $port encerrado.", isSuccessStyle = true)
        } else {
            CommandResult(1, stderr = "kill: nenhum servidor ativo na porta $port")
        }
    }

    private fun handlePython(args: List<String>, session: ShellSessionState): CommandResult {
        val httpIdx = args.indexOf("http.server")
        return if (args.contains("-m") && httpIdx >= 0) {
            val port = args.getOrNull(httpIdx + 1)?.toIntOrNull() ?: 8080
            startServer(port, "python3 -m http.server $port", session)
        } else {
            CommandResult(0, "Dica: execute 'python -m http.server 8080' para servir ${session.promptPath()}")
        }
    }

    private fun handleServe(args: List<String>, session: ShellSessionState): CommandResult {
        val port = args.lastOrNull()?.toIntOrNull() ?: 3000
        return startServer(port, "node dev-server :$port", session)
    }

    private fun handlePhp(args: List<String>, session: ShellSessionState): CommandResult {
        val sIdx = args.indexOf("-S")
        val hostPort = if (sIdx >= 0) args.getOrNull(sIdx + 1) else null
        val port = hostPort?.substringAfter(":")?.toIntOrNull() ?: 8000
        return startServer(port, "php -S 127.0.0.1:$port", session)
    }

    private fun startServer(port: Int, label: String, session: ShellSessionState): CommandResult {
        val res = session.serverManager.startServer(port, label, session.currentDir)
        return res.fold(
            onSuccess = { info ->
                CommandResult(
                    0,
                    "Servidor iniciado em ${info.baseUrl}\nUse 'open ${info.baseUrl}' ou toque em Abrir para visualizar no navegador integrado.",
                    isSuccessStyle = true
                )
            },
            onFailure = { err ->
                CommandResult(1, stderr = "Erro ao iniciar servidor na porta $port: ${err.message}")
            }
        )
    }

    private fun handleOpen(args: List<String>, session: ShellSessionState): CommandResult {
        val raw = args.firstOrNull() ?: "http://127.0.0.1:8080"
        val resolvedTarget = if (!raw.contains("://") && !raw.startsWith("127.0.0.1") && !raw.startsWith("localhost") && !raw.contains(".com") && !raw.contains(".org")) {
            val localFile = session.resolveSafe(raw)
            if (localFile != null && localFile.exists()) {
                val activePort = session.serverManager.runningServers.value.firstOrNull()?.port ?: 8080
                if (session.serverManager.runningServers.value.none { it.port == activePort }) {
                    session.serverManager.startServer(activePort, "python3 -m http.server $activePort", session.homeDir)
                }
                val rel = localFile.relativeToOrNull(session.homeDir)?.path.orEmpty()
                "http://127.0.0.1:$activePort/$rel"
            } else {
                "http://$raw"
            }
        } else if (raw.startsWith("http://") || raw.startsWith("https://")) {
            raw
        } else if (raw.startsWith("127.0.0.1") || raw.startsWith("localhost")) {
            "http://$raw"
        } else {
            "https://$raw"
        }
        session.onOpenUrlInBrowser(resolvedTarget)
        return CommandResult(0, "Abrindo $resolvedTarget no navegador integrado...", isSuccessStyle = true)
    }

    private fun handleCurl(args: List<String>): CommandResult {
        var method = "GET"
        var headOnly = false
        var bodyData: String? = null
        val headers = mutableMapOf<String, String>()
        var urlArg: String? = null

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "-I", "--head" -> {
                    headOnly = true
                    method = "HEAD"
                    i++
                }
                "-X", "--request" -> {
                    method = args.getOrNull(i + 1)?.uppercase() ?: "GET"
                    i += 2
                }
                "-d", "--data" -> {
                    bodyData = args.getOrNull(i + 1) ?: ""
                    if (method == "GET") method = "POST"
                    i += 2
                }
                "-H", "--header" -> {
                    val h = args.getOrNull(i + 1).orEmpty()
                    if (h.contains(":")) {
                        headers[h.substringBefore(":").trim()] = h.substringAfter(":").trim()
                    }
                    i += 2
                }
                else -> {
                    if (!args[i].startsWith("-")) urlArg = args[i]
                    i++
                }
            }
        }

        val rawUrl = urlArg ?: return CommandResult(1, stderr = "curl: informe uma URL")
        val normalized = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) rawUrl else "http://$rawUrl"
        return runCatching {
            val conn = (URL(normalized).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = method
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                if (bodyData != null) {
                    doOutput = true
                    outputStream.use { it.write(bodyData.toByteArray()) }
                }
            }
            val code = conn.responseCode
            val sb = StringBuilder()
            if (headOnly) {
                sb.appendLine("HTTP/1.1 $code ${conn.responseMessage}")
                conn.headerFields.forEach { (k, v) ->
                    if (k != null) sb.appendLine("$k: ${v.joinToString(", ")}")
                }
            } else {
                val stream = if (code in 200..399) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                sb.append(text.take(4000))
            }
            conn.disconnect()
            CommandResult(0, sb.toString().trimEnd('\n'))
        }.getOrElse { e ->
            CommandResult(7, stderr = "curl: falha ao conectar em $normalized (${e.message})")
        }
    }

    private fun handleWget(args: List<String>, session: ShellSessionState): CommandResult {
        var outName: String? = null
        var urlArg: String? = null
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "-O" -> {
                    outName = args.getOrNull(i + 1)
                    i += 2
                }
                else -> {
                    if (!args[i].startsWith("-")) urlArg = args[i]
                    i++
                }
            }
        }
        val rawUrl = urlArg ?: return CommandResult(1, stderr = "wget: informe a URL")
        val normalized = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) rawUrl else "http://$rawUrl"
        val fileName = outName ?: normalized.substringAfterLast("/").substringBefore("?").ifBlank { "index.html" }
        val dest = session.resolveSafe(fileName) ?: return CommandResult(1, stderr = "wget: destino inválido")

        return runCatching {
            val conn = (URL(normalized).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 10000
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            dest.parentFile?.mkdirs()
            dest.writeBytes(bytes)
            session.onRefreshWorkspace()
            CommandResult(0, "Salvo '$fileName' (${bytes.size} bytes)", isSuccessStyle = true)
        }.getOrElse { e ->
            CommandResult(1, stderr = "wget: erro (${e.message})")
        }
    }

    private fun handlePing(args: List<String>): CommandResult {
        var count = 3
        var host: String? = null
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "-c" -> {
                    count = args.getOrNull(i + 1)?.toIntOrNull()?.coerceIn(1, 6) ?: 3
                    i += 2
                }
                else -> {
                    if (!args[i].startsWith("-")) host = args[i]
                    i++
                }
            }
        }
        val targetHost = host ?: return CommandResult(1, stderr = "ping: informe o host")
        return runCatching {
            val addr = InetAddress.getByName(targetHost)
            val sb = StringBuilder("PING $targetHost (${addr.hostAddress}):\n")
            var ok = 0
            for (seq in 1..count) {
                val start = System.nanoTime()
                val reachable = if (addr.isLoopbackAddress) {
                    true
                } else {
                    runCatching {
                        Socket().use { s ->
                            s.connect(InetSocketAddress(addr, 443), 2000)
                        }
                        true
                    }.getOrElse {
                        addr.isReachable(1500)
                    }
                }
                val ms = (System.nanoTime() - start) / 1_000_000.0
                if (reachable) {
                    ok++
                    sb.appendLine("64 bytes from ${addr.hostAddress}: icmp_seq=$seq time=${String.format(Locale.US, "%.2f", ms)} ms")
                } else {
                    sb.appendLine("Request timeout for icmp_seq $seq")
                }
            }
            sb.append("--- $targetHost ping statistics ---\n$count packets transmitted, $ok received")
            CommandResult(if (ok > 0) 0 else 1, sb.toString())
        }.getOrElse { e ->
            CommandResult(2, stderr = "ping: host desconhecido '$targetHost' (${e.message})")
        }
    }

    private fun handleDns(args: List<String>): CommandResult {
        val host = args.firstOrNull { !it.startsWith("-") }
            ?: return CommandResult(1, stderr = "nslookup: informe um domínio")
        return runCatching {
            val addrs = InetAddress.getAllByName(host)
            val lines = addrs.joinToString("\n") { "Name:    $host\nAddress: ${it.hostAddress}" }
            CommandResult(0, lines)
        }.getOrElse { e ->
            CommandResult(1, stderr = "nslookup: falha ao resolver '$host' (${e.message})")
        }
    }

    private fun handleIfconfig(): CommandResult {
        return runCatching {
            val sb = StringBuilder()
            val ifaces = NetworkInterface.getNetworkInterfaces()
            while (ifaces.hasMoreElements()) {
                val ni = ifaces.nextElement()
                val addrs = ni.inetAddresses.toList()
                if (addrs.isEmpty()) continue
                sb.appendLine("${ni.name}: flags=${if (ni.isUp) "UP" else "DOWN"} mtu ${ni.mtu}")
                addrs.forEach { a ->
                    sb.appendLine("        inet ${a.hostAddress}")
                }
            }
            CommandResult(0, sb.toString().trimEnd('\n'))
        }.getOrElse { e ->
            CommandResult(1, stderr = "ifconfig: ${e.message}")
        }
    }
}
