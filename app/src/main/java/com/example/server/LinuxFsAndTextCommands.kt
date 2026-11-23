package com.example.server

import android.os.Build
import android.os.StatFs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LinuxFsAndTextCommands {

    fun executeFsOrText(
        cmd: String,
        args: List<String>,
        stdin: String,
        session: ShellSessionState
    ): CommandResult? {
        return when (cmd) {
            "pwd" -> CommandResult(0, session.virtualAbsolutePath())

            "cd" -> handleCd(args, session)

            "ls" -> handleLs(args, session)

            "tree" -> handleTree(args, session)

            "mkdir" -> handleMkdir(args, session)

            "rmdir" -> handleRmdir(args, session)

            "touch" -> handleTouch(args, session)

            "cp" -> handleCp(args, session)

            "mv" -> handleMv(args, session)

            "rm" -> handleRm(args, session)

            "cat" -> handleCat(args, stdin, session)

            "tac" -> handleTac(args, stdin, session)

            "head" -> handleHeadTail(args, stdin, session, isHead = true)

            "tail" -> handleHeadTail(args, stdin, session, isHead = false)

            "less", "more" -> handleCat(args, stdin, session)

            "stat" -> handleStat(args, session)

            "file" -> handleFileType(args, session)

            "find" -> handleFind(args, session)

            "realpath" -> handlePathPart(args, session, mode = "real")

            "basename" -> handlePathPart(args, session, mode = "base")

            "dirname" -> handlePathPart(args, session, mode = "dir")

            "chmod" -> handleChmod(args, session)

            "ln" -> handleLn(args, session)

            "du" -> handleDu(args, session)

            "df" -> handleDf(args, session)

            "echo" -> handleEcho(args)

            "printf" -> handlePrintf(args)

            "grep", "egrep" -> handleGrep(args, stdin, session)

            "sed" -> handleSed(args, stdin, session)

            "awk" -> handleAwk(args, stdin, session)

            "wc" -> handleWc(args, stdin, session)

            "sort" -> handleSort(args, stdin, session)

            "uniq" -> handleUniq(args, stdin, session)

            "cut" -> handleCut(args, stdin, session)

            "tr" -> handleTr(args, stdin)

            "rev" -> handleRev(args, stdin, session)

            "nl" -> handleNl(args, stdin, session)

            "diff" -> handleDiff(args, session)

            "tee" -> handleTee(args, stdin, session)

            "jq" -> handleJq(args, stdin, session)

            else -> null
        }
    }

    private fun handleCd(args: List<String>, session: ShellSessionState): CommandResult {
        val raw = args.firstOrNull() ?: "~"
        val target = if (raw == "-") {
            session.previousDir
        } else {
            session.resolveSafe(raw)
        }
        if (target == null || !target.exists() || !target.isDirectory) {
            return CommandResult(1, stderr = "cd: $raw: Diretório inexistente ou fora do sandbox")
        }
        session.previousDir = session.currentDir
        session.currentDir = target
        session.env["OLDPWD"] = session.virtualAbsolutePath(session.previousDir)
        session.env["PWD"] = session.virtualAbsolutePath(session.currentDir)
        return CommandResult(0, "")
    }

    private fun handleLs(args: List<String>, session: ShellSessionState): CommandResult {
        val flags = args.filter { it.startsWith("-") }.joinToString("")
        val longFmt = flags.contains('l')
        val showAll = flags.contains('a')
        val human = flags.contains('h')
        val sortSize = flags.contains('S')
        val sortTime = flags.contains('t')
        val recursive = flags.contains('R')

        val targets = args.filterNot { it.startsWith("-") }.ifEmpty { listOf(".") }
        val df = SimpleDateFormat("MMM dd HH:mm", Locale.US)
        val out = StringBuilder()

        fun formatEntry(f: File): String {
            if (!longFmt) return if (f.isDirectory) "${f.name}/" else f.name
            val mode = session.fileModes[f.canonicalPath] ?: if (f.isDirectory) "drwxr-xr-x" else "-rw-r--r--"
            val sizeStr = if (f.isDirectory) {
                "4.0K".takeIf { human } ?: "4096"
            } else {
                if (human) humanBytes(f.length()) else f.length().toString()
            }
            val dateStr = df.format(Date(f.lastModified()))
            val displayName = if (f.isDirectory) "${f.name}/" else f.name
            return "$mode  1 u0_termux2  ${sizeStr.padStart(6)}  $dateStr  $displayName"
        }

        fun listDir(dir: File, prefix: String?) {
            if (prefix != null) out.appendLine("$prefix:")
            var children = dir.listFiles()?.toList().orEmpty()
            if (!showAll) children = children.filterNot { it.name.startsWith(".") }
            children = when {
                sortSize -> children.sortedByDescending { it.length() }
                sortTime -> children.sortedByDescending { it.lastModified() }
                else -> children.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            }
            if (showAll && longFmt) {
                out.appendLine("drwxr-xr-x  1 u0_termux2    4096  ${df.format(Date(dir.lastModified()))}  .")
                out.appendLine("drwxr-xr-x  1 u0_termux2    4096  ${df.format(Date(dir.lastModified()))}  ..")
            }
            children.forEach { out.appendLine(formatEntry(it)) }
            if (recursive) {
                children.filter { it.isDirectory }.forEach { sub ->
                    out.appendLine()
                    listDir(sub, "${prefix ?: "."}/${sub.name}")
                }
            }
        }

        for (t in targets) {
            val resolved = session.resolveSafe(t)
                ?: return CommandResult(1, stderr = "ls: '$t': Acesso negado fora do sandbox")
            if (!resolved.exists()) {
                return CommandResult(2, stderr = "ls: não foi possível acessar '$t': Arquivo ou diretório inexistente")
            }
            if (resolved.isFile) {
                out.appendLine(formatEntry(resolved))
            } else {
                listDir(resolved, if (targets.size > 1 || recursive) t else null)
            }
        }
        return CommandResult(0, out.toString().trimEnd('\n'))
    }

    private fun handleTree(args: List<String>, session: ShellSessionState): CommandResult {
        var maxDepth = 4
        var pathArg = "."
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "-L" -> {
                    maxDepth = args.getOrNull(i + 1)?.toIntOrNull()?.coerceIn(1, 10) ?: 4
                    i += 2
                }
                else -> {
                    if (!args[i].startsWith("-")) pathArg = args[i]
                    i++
                }
            }
        }
        val root = session.resolveSafe(pathArg)
            ?: return CommandResult(1, stderr = "tree: caminho inválido")
        if (!root.exists()) return CommandResult(1, stderr = "tree: '$pathArg' não existe")

        var dirs = 0
        var files = 0
        val sb = StringBuilder()
        sb.appendLine(if (pathArg == ".") session.promptPath() else pathArg)

        fun walk(dir: File, indent: String, depth: Int) {
            if (depth > maxDepth) return
            val children = dir.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name }).orEmpty()
            children.forEachIndexed { idx, child ->
                val isLast = idx == children.lastIndex
                val branch = if (isLast) "└── " else "├── "
                sb.appendLine("$indent$branch${child.name}${if (child.isDirectory) "/" else ""}")
                if (child.isDirectory) {
                    dirs++
                    walk(child, indent + if (isLast) "    " else "│   ", depth + 1)
                } else {
                    files++
                }
            }
        }
        walk(root, "", 1)
        sb.append("\n$dirs diretório(s), $files arquivo(s)")
        return CommandResult(0, sb.toString())
    }

    private fun handleMkdir(args: List<String>, session: ShellSessionState): CommandResult {
        val parents = args.any { it == "-p" }
        val names = args.filterNot { it.startsWith("-") }
        if (names.isEmpty()) return CommandResult(1, stderr = "mkdir: operando faltando")
        for (name in names) {
            val target = session.resolveSafe(name)
                ?: return CommandResult(1, stderr = "mkdir: caminho fora do sandbox: $name")
            val ok = if (parents) target.mkdirs() else target.mkdir()
            if (!ok && !target.exists()) {
                return CommandResult(1, stderr = "mkdir: não foi possível criar '$name'")
            }
        }
        session.onRefreshWorkspace()
        return CommandResult(0, "")
    }

    private fun handleRmdir(args: List<String>, session: ShellSessionState): CommandResult {
        val names = args.filterNot { it.startsWith("-") }
        if (names.isEmpty()) return CommandResult(1, stderr = "rmdir: operando faltando")
        for (name in names) {
            val target = session.resolveSafe(name)
                ?: return CommandResult(1, stderr = "rmdir: caminho inválido: $name")
            if (!target.exists() || !target.isDirectory) {
                return CommandResult(1, stderr = "rmdir: '$name' não é diretório")
            }
            if (!target.listFiles().isNullOrEmpty()) {
                return CommandResult(1, stderr = "rmdir: '$name' não está vazio")
            }
            target.delete()
        }
        session.onRefreshWorkspace()
        return CommandResult(0, "")
    }

    private fun handleTouch(args: List<String>, session: ShellSessionState): CommandResult {
        val names = args.filterNot { it.startsWith("-") }
        if (names.isEmpty()) return CommandResult(1, stderr = "touch: operando faltando")
        for (name in names) {
            val target = session.resolveSafe(name)
                ?: return CommandResult(1, stderr = "touch: caminho inválido: $name")
            target.parentFile?.mkdirs()
            if (target.exists()) {
                target.setLastModified(System.currentTimeMillis())
            } else {
                target.createNewFile()
            }
        }
        session.onRefreshWorkspace()
        return CommandResult(0, "")
    }

    private fun handleCp(args: List<String>, session: ShellSessionState): CommandResult {
        val recursive = args.any { it == "-r" || it == "-R" || it == "-a" }
        val paths = args.filterNot { it.startsWith("-") }
        if (paths.size < 2) return CommandResult(1, stderr = "cp: especifique origem e destino")
        val src = session.resolveSafe(paths[0]) ?: return CommandResult(1, stderr = "cp: origem inválida")
        val dstRaw = session.resolveSafe(paths[1]) ?: return CommandResult(1, stderr = "cp: destino inválido")
        if (!src.exists()) return CommandResult(1, stderr = "cp: '${paths[0]}' não encontrado")
        val dst = if (dstRaw.exists() && dstRaw.isDirectory) File(dstRaw, src.name) else dstRaw
        if (src.isDirectory) {
            if (!recursive) return CommandResult(1, stderr = "cp: -r não especificado; omitindo diretório '${paths[0]}'")
            src.copyRecursively(dst, overwrite = true)
        } else {
            dst.parentFile?.mkdirs()
            src.copyTo(dst, overwrite = true)
        }
        session.onRefreshWorkspace()
        return CommandResult(0, "")
    }

    private fun handleMv(args: List<String>, session: ShellSessionState): CommandResult {
        val paths = args.filterNot { it.startsWith("-") }
        if (paths.size < 2) return CommandResult(1, stderr = "mv: especifique origem e destino")
        val src = session.resolveSafe(paths[0]) ?: return CommandResult(1, stderr = "mv: origem inválida")
        val dstRaw = session.resolveSafe(paths[1]) ?: return CommandResult(1, stderr = "mv: destino inválido")
        if (!src.exists()) return CommandResult(1, stderr = "mv: '${paths[0]}' não encontrado")
        if (src.canonicalPath == session.homeDir.canonicalPath) {
            return CommandResult(1, stderr = "mv: não é permitido mover o diretório raiz ~")
        }
        val dst = if (dstRaw.exists() && dstRaw.isDirectory) File(dstRaw, src.name) else dstRaw
        dst.parentFile?.mkdirs()
        if (!src.renameTo(dst)) {
            src.copyRecursively(dst, overwrite = true)
            src.deleteRecursively()
        }
        session.onRefreshWorkspace()
        return CommandResult(0, "")
    }

    private fun handleRm(args: List<String>, session: ShellSessionState): CommandResult {
        val flags = args.filter { it.startsWith("-") }.joinToString("")
        val recursive = flags.contains('r') || flags.contains('R')
        val force = flags.contains('f')
        val targets = args.filterNot { it.startsWith("-") }
        if (targets.isEmpty()) return CommandResult(1, stderr = "rm: operando faltando")

        for (t in targets) {
            val target = session.resolveSafe(t)
            if (target == null || target.canonicalPath == session.homeDir.canonicalPath) {
                return CommandResult(1, stderr = "rm: operação proibida para '$t'")
            }
            if (!target.exists()) {
                if (force) continue
                return CommandResult(1, stderr = "rm: '$t': Arquivo ou diretório inexistente")
            }
            if (target.isDirectory && !recursive) {
                return CommandResult(1, stderr = "rm: não foi possível remover '$t': É um diretório (use -r)")
            }
            target.deleteRecursively()
        }
        session.onRefreshWorkspace()
        return CommandResult(0, "")
    }

    private fun readInputsOrStdin(
        fileArgs: List<String>,
        stdin: String,
        session: ShellSessionState,
        cmdName: String
    ): Pair<String?, CommandResult?> {
        if (fileArgs.isEmpty()) return stdin to null
        val sb = StringBuilder()
        for (fName in fileArgs) {
            if (fName == "-") {
                sb.append(stdin)
                continue
            }
            val file = session.resolveSafe(fName)
                ?: return null to CommandResult(1, stderr = "$cmdName: '$fName': Acesso negado")
            if (!file.exists() || !file.isFile) {
                return null to CommandResult(1, stderr = "$cmdName: '$fName': Arquivo não encontrado")
            }
            if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
            sb.append(file.readText())
        }
        return sb.toString() to null
    }

    private fun handleCat(args: List<String>, stdin: String, session: ShellSessionState): CommandResult {
        val numberLines = args.any { it == "-n" }
        val files = args.filterNot { it.startsWith("-") }
        val (text, err) = readInputsOrStdin(files, stdin, session, "cat")
        if (err != null || text == null) return err ?: CommandResult(1)
        return if (numberLines) {
            val numbered = text.lines().mapIndexed { idx, line ->
                "${(idx + 1).toString().padStart(5)}  $line"
            }.joinToString("\n")
            CommandResult(0, numbered)
        } else {
            CommandResult(0, text.trimEnd('\n'))
        }
    }

    private fun handleTac(args: List<String>, stdin: String, session: ShellSessionState): CommandResult {
        val files = args.filterNot { it.startsWith("-") }
        val (text, err) = readInputsOrStdin(files, stdin, session, "tac")
        if (err != null || text == null) return err ?: CommandResult(1)
        return CommandResult(0, text.lines().reversed().joinToString("\n"))
    }

    private fun handleHeadTail(
        args: List<String>,
        stdin: String,
        session: ShellSessionState,
        isHead: Boolean
    ): CommandResult {
        var count = 10
        val files = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            val a = args[i]
            when {
                a == "-n" -> {
                    count = args.getOrNull(i + 1)?.toIntOrNull()?.coerceAtLeast(0) ?: 10
                    i += 2
                }
                a.startsWith("-") && a.drop(1).all { it.isDigit() } -> {
                    count = a.drop(1).toIntOrNull() ?: 10
                    i++
                }
                !a.startsWith("-") -> {
                    files.add(a)
                    i++
                }
                else -> i++
            }
        }
        val cmdName = if (isHead) "head" else "tail"
        val (text, err) = readInputsOrStdin(files, stdin, session, cmdName)
        if (err != null || text == null) return err ?: CommandResult(1)
        val lines = text.lines()
        val sliced = if (isHead) lines.take(count) else lines.takeLast(count)
        return CommandResult(0, sliced.joinToString("\n"))
    }

    private fun handleStat(args: List<String>, session: ShellSessionState): CommandResult {
        val targetArg = args.firstOrNull() ?: return CommandResult(1, stderr = "stat: operando faltando")
        val target = session.resolveSafe(targetArg) ?: return CommandResult(1, stderr = "stat: caminho inválido")
        if (!target.exists()) return CommandResult(1, stderr = "stat: '$targetArg': Não encontrado")
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.getDefault())
        val mode = session.fileModes[target.canonicalPath] ?: if (target.isDirectory) "0755/drwxr-xr-x" else "0644/-rw-r--r--"
        val kind = if (target.isDirectory) "diretório" else "arquivo regular"
        val text = """
          Arquivo: ${target.name}
          Caminho: ${session.virtualAbsolutePath(target)}
          Tamanho: ${target.length()} bytes    Tipo: $kind
           Acesso: ($mode)  Uid: (1000/u0_termux2)
          Modific: ${df.format(Date(target.lastModified()))}
        """.trimIndent()
        return CommandResult(0, text)
    }

    private fun handleFileType(args: List<String>, session: ShellSessionState): CommandResult {
        val targetArg = args.firstOrNull() ?: return CommandResult(1, stderr = "file: informe um arquivo")
        val target = session.resolveSafe(targetArg) ?: return CommandResult(1, stderr = "file: caminho inválido")
        if (!target.exists()) return CommandResult(1, stderr = "$targetArg: não encontrado")
        val desc = when {
            target.isDirectory -> "directory"
            target.extension.equals("html", true) -> "HTML document, UTF-8 Unicode text"
            target.extension.equals("css", true) -> "CSS stylesheet, UTF-8 Unicode text"
            target.extension.equals("js", true) -> "JavaScript source, UTF-8 Unicode text"
            target.extension.equals("json", true) -> "JSON data, UTF-8 Unicode text"
            target.extension.equals("zip", true) -> "Zip archive data"
            target.extension.equals("gz", true) -> "gzip compressed data"
            target.length() == 0L -> "empty"
            else -> "ASCII / UTF-8 text"
        }
        return CommandResult(0, "${target.name}: $desc")
    }

    private fun handleFind(args: List<String>, session: ShellSessionState): CommandResult {
        var rootArg = "."
        var namePattern: String? = null
        var typeFilter: String? = null
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "-name" -> {
                    namePattern = args.getOrNull(i + 1)
                    i += 2
                }
                "-type" -> {
                    typeFilter = args.getOrNull(i + 1)
                    i += 2
                }
                else -> {
                    if (!args[i].startsWith("-")) rootArg = args[i]
                    i++
                }
            }
        }
        val root = session.resolveSafe(rootArg) ?: return CommandResult(1, stderr = "find: caminho inválido")
        if (!root.exists()) return CommandResult(1, stderr = "find: '$rootArg': Não existe")

        val regex = namePattern?.let { pat ->
            val escaped = Regex.escape(pat).replace("\\*", ".*").replace("\\?", ".")
            Regex("^$escaped$", RegexOption.IGNORE_CASE)
        }

        val matches = mutableListOf<String>()
        root.walkTopDown().forEach { f ->
            val typeOk = when (typeFilter) {
                "f" -> f.isFile
                "d" -> f.isDirectory
                else -> true
            }
            val nameOk = regex == null || regex.matches(f.name)
            if (typeOk && nameOk) {
                val rel = f.relativeToOrNull(session.currentDir)?.path?.ifBlank { "." } ?: f.name
                matches.add(if (rel == ".") "." else "./$rel")
            }
        }
        return CommandResult(0, matches.joinToString("\n"))
    }

    private fun handlePathPart(args: List<String>, session: ShellSessionState, mode: String): CommandResult {
        val raw = args.firstOrNull() ?: return CommandResult(1, stderr = "operando faltando")
        return when (mode) {
            "real" -> {
                val resolved = session.resolveSafe(raw)
                    ?: return CommandResult(1, stderr = "realpath: caminho fora do sandbox")
                CommandResult(0, session.virtualAbsolutePath(resolved))
            }
            "base" -> CommandResult(0, File(raw).name)
            else -> CommandResult(0, File(raw).parent ?: ".")
        }
    }

    private fun handleChmod(args: List<String>, session: ShellSessionState): CommandResult {
        if (args.size < 2) return CommandResult(1, stderr = "chmod: uso: chmod <modo> <arquivo>")
        val mode = args[0]
        val target = session.resolveSafe(args[1]) ?: return CommandResult(1, stderr = "chmod: caminho inválido")
        if (!target.exists()) return CommandResult(1, stderr = "chmod: '${args[1]}' não encontrado")
        val prefix = if (target.isDirectory) "d" else "-"
        when {
            mode == "+x" || mode == "755" || mode == "a+x" -> {
                target.setExecutable(true)
                session.fileModes[target.canonicalPath] = "${prefix}rwxr-xr-x"
            }
            mode == "-x" || mode == "644" -> {
                target.setExecutable(false)
                session.fileModes[target.canonicalPath] = "${prefix}rw-r--r--"
            }
            mode == "777" -> {
                target.setExecutable(true)
                target.setWritable(true)
                session.fileModes[target.canonicalPath] = "${prefix}rwxrwxrwx"
            }
            mode == "444" || mode == "400" -> {
                target.setWritable(false)
                session.fileModes[target.canonicalPath] = "${prefix}r--r--r--"
            }
            else -> session.fileModes[target.canonicalPath] = "${prefix}rw-r--r--($mode)"
        }
        return CommandResult(0, "")
    }

    private fun handleLn(args: List<String>, session: ShellSessionState): CommandResult {
        val paths = args.filterNot { it.startsWith("-") }
        if (paths.size < 2) return CommandResult(1, stderr = "ln: uso: ln -s <alvo> <link>")
        val target = session.resolveSafe(paths[0]) ?: return CommandResult(1, stderr = "ln: alvo inválido")
        val link = session.resolveSafe(paths[1]) ?: return CommandResult(1, stderr = "ln: link inválido")
        return runCatching {
            android.system.Os.symlink(target.absolutePath, link.absolutePath)
            session.onRefreshWorkspace()
            CommandResult(0, "")
        }.getOrElse { e ->
            CommandResult(1, stderr = "ln: falha ao criar link (${e.message})")
        }
    }

    private fun handleDu(args: List<String>, session: ShellSessionState): CommandResult {
        val human = args.any { it.contains('h') }
        val summaryOnly = args.any { it.contains('s') }
        val targetArg = args.filterNot { it.startsWith("-") }.firstOrNull() ?: "."
        val root = session.resolveSafe(targetArg) ?: return CommandResult(1, stderr = "du: caminho inválido")
        if (!root.exists()) return CommandResult(1, stderr = "du: '$targetArg' não encontrado")

        fun dirSize(file: File): Long =
            if (file.isFile) file.length() else file.walkTopDown().filter { it.isFile }.sumOf { it.length() }

        val sb = StringBuilder()
        if (summaryOnly || root.isFile) {
            val sz = dirSize(root)
            val disp = if (human) humanBytes(sz) else "${(sz + 1023) / 1024}"
            sb.append("$disp\t$targetArg")
        } else {
            root.walkTopDown().maxDepth(2).filter { it.isDirectory }.forEach { d ->
                val sz = dirSize(d)
                val disp = if (human) humanBytes(sz) else "${(sz + 1023) / 1024}"
                val rel = d.relativeToOrNull(session.currentDir)?.path?.ifBlank { "." } ?: d.name
                sb.appendLine("$disp\t$rel")
            }
        }
        return CommandResult(0, sb.toString().trimEnd('\n'))
    }

    private fun handleDf(args: List<String>, session: ShellSessionState): CommandResult {
        val human = args.any { it.contains('h') }
        val stat = StatFs(session.homeDir.absolutePath)
        val total = stat.totalBytes
        val avail = stat.availableBytes
        val used = total - avail
        val pct = if (total > 0) ((used * 100) / total).toInt() else 0
        fun fmt(b: Long) = if (human) humanBytes(b) else "${b / 1024}"
        val header = "Filesystem        Size      Used     Avail  Use%  Mounted on"
        val row = "/dev/sandbox  ${fmt(total).padStart(8)}  ${fmt(used).padStart(8)}  ${fmt(avail).padStart(8)}  ${pct.toString().padStart(3)}%  /data/termux_home"
        return CommandResult(0, "$header\n$row")
    }

    private fun handleEcho(args: List<String>): CommandResult {
        var noNewline = false
        var interpretEscapes = false
        val rest = mutableListOf<String>()
        var parsingFlags = true
        for (a in args) {
            if (parsingFlags && a.startsWith("-") && a.length > 1 && a.drop(1).all { it == 'n' || it == 'e' }) {
                if (a.contains('n')) noNewline = true
                if (a.contains('e')) interpretEscapes = true
            } else {
                parsingFlags = false
                rest.add(a)
            }
        }
        var out = rest.joinToString(" ")
        if (interpretEscapes) {
            out = out.replace("\\n", "\n").replace("\\t", "\t").replace("\\r", "\r")
        }
        return CommandResult(0, out)
    }

    private fun handlePrintf(args: List<String>): CommandResult {
        if (args.isEmpty()) return CommandResult(0, "")
        var fmt = args[0]
            .replace("\\n", "\n")
            .replace("\\t", "\t")
        var idx = 1
        while (fmt.contains("%s") && idx < args.size) {
            fmt = fmt.replaceFirst("%s", args[idx++])
        }
        while (fmt.contains("%d") && idx < args.size) {
            fmt = fmt.replaceFirst("%d", args[idx++].toLongOrNull()?.toString() ?: "0")
        }
        return CommandResult(0, fmt.trimEnd('\n'))
    }

    private fun handleGrep(args: List<String>, stdin: String, session: ShellSessionState): CommandResult {
        var ignoreCase = false
        var lineNumbers = false
        var invert = false
        var countOnly = false
        var recursive = false
        val positional = mutableListOf<String>()

        for (a in args) {
            if (a.startsWith("-") && a.length > 1) {
                if (a.contains('i')) ignoreCase = true
                if (a.contains('n')) lineNumbers = true
                if (a.contains('v')) invert = true
                if (a.contains('c')) countOnly = true
                if (a.contains('r') || a.contains('R')) recursive = true
            } else {
                positional.add(a)
            }
        }

        val pattern = positional.firstOrNull() ?: return CommandResult(2, stderr = "grep: informe o padrão de busca")
        val regex = runCatching {
            val opts = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
            Regex(pattern, opts)
        }.getOrElse {
            Regex(Regex.escape(pattern), if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
        }

        val targets = positional.drop(1)
        val outLines = mutableListOf<String>()
        var matchCount = 0

        fun scanContent(label: String?, content: String) {
            content.lines().forEachIndexed { idx, line ->
                val hit = regex.containsMatchIn(line)
                val keep = if (invert) !hit else hit
                if (keep) {
                    matchCount++
                    if (!countOnly) {
                        val prefix = buildString {
                            if (label != null) append("$label:")
                            if (lineNumbers) append("${idx + 1}:")
                        }
                        outLines.add("$prefix$line")
                    }
                }
            }
        }

        if (targets.isEmpty()) {
            scanContent(null, stdin)
        } else {
            val showFileLabel = targets.size > 1 || recursive
            for (t in targets) {
                val f = session.resolveSafe(t) ?: return CommandResult(2, stderr = "grep: '$t': Acesso negado")
                if (!f.exists()) return CommandResult(2, stderr = "grep: '$t': Não encontrado")
                if (f.isDirectory) {
                    if (!recursive) continue
                    f.walkTopDown().filter { it.isFile }.forEach { sub ->
                        val rel = sub.relativeToOrNull(session.currentDir)?.path ?: sub.name
                        scanContent(rel, sub.readText())
                    }
                } else {
                    scanContent(if (showFileLabel) t else null, f.readText())
                }
            }
        }

        return if (countOnly) {
            CommandResult(if (matchCount > 0) 0 else 1, matchCount.toString())
        } else {
            CommandResult(if (matchCount > 0) 0 else 1, outLines.joinToString("\n"))
        }
    }

    private fun handleSed(args: List<String>, stdin: String, session: ShellSessionState): CommandResult {
        val script = args.firstOrNull { !it.startsWith("-") }
            ?: return CommandResult(1, stderr = "sed: informe a expressão (ex: 's/a/b/g')")
        val files = args.filterNot { it == script || it.startsWith("-") }
        val (text, err) = readInputsOrStdin(files, stdin, session, "sed")
        if (err != null || text == null) return err ?: CommandResult(1)

        if (script.startsWith("s") && script.length >= 4) {
            val delim = script[1]
            val parts = script.substring(2).split(delim)
            if (parts.size >= 2) {
                val search = parts[0]
                val repl = parts[1]
                val flags = parts.getOrNull(2).orEmpty()
                val regex = Regex(search, if (flags.contains('i')) setOf(RegexOption.IGNORE_CASE) else emptySet())
                val transformed = text.lines().joinToString("\n") { line ->
                    if (flags.contains('g')) line.replace(regex, repl) else regex.replaceFirst(line, repl)
                }
                return CommandResult(0, transformed)
            }
        }
        return CommandResult(1, stderr = "sed: expressão não suportada ($script)")
    }

    private fun handleAwk(args: List<String>, stdin: String, session: ShellSessionState): CommandResult {
        var delimRegex = Regex("\\s+")
        val positional = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            when {
                args[i] == "-F" -> {
                    val d = args.getOrNull(i + 1) ?: " "
                    delimRegex = Regex(Regex.escape(d))
                    i += 2
                }
                args[i].startsWith("-F") -> {
                    delimRegex = Regex(Regex.escape(args[i].drop(2)))
                    i++
                }
                else -> {
                    positional.add(args[i])
                    i++
                }
            }
        }
        val program = positional.firstOrNull() ?: return CommandResult(1, stderr = "awk: informe o programa")
        val files = positional.drop(1)
        val (text, err) = readInputsOrStdin(files, stdin, session, "awk")
        if (err != null || text == null) return err ?: CommandResult(1)

        val printBody = Regex("\\{\\s*print\\s*(.*?)\\s*}").find(program)?.groupValues?.get(1)?.trim() ?: "\$0"
        val fieldTokens = if (printBody.isEmpty()) listOf("\$0") else printBody.split(",").map { it.trim() }

        val result = text.lines().filter { it.isNotEmpty() }.joinToString("\n") { line ->
            val cols = line.trim().split(delimRegex)
            fieldTokens.joinToString(" ") { tok ->
                when {
                    tok == "\$0" -> line
                    tok == "\$NF" -> cols.lastOrNull().orEmpty()
                    tok.startsWith("\$") -> {
                        val idx = tok.drop(1).toIntOrNull() ?: 0
                        if (idx == 0) line else cols.getOrNull(idx - 1).orEmpty()
                    }
                    else -> tok.trim('"', '\'')
                }
            }
        }
        return CommandResult(0, result)
    }

    private fun handleWc(args: List<String>, stdin: String, session: ShellSessionState): CommandResult {
        val flags = args.filter { it.startsWith("-") }.joinToString("")
        val files = args.filterNot { it.startsWith("-") }
        val (text, err) = readInputsOrStdin(files, stdin, session, "wc")
        if (err != null || text == null) return err ?: CommandResult(1)

        val lineCount = if (text.isEmpty()) 0 else text.trimEnd('\n').lines().size
        val wordCount = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        val byteCount = text.toByteArray().size

        val onlyL = flags.contains('l')
        val onlyW = flags.contains('w')
        val onlyC = flags.contains('c')
        val out = when {
            onlyL && !onlyW && !onlyC -> "$lineCount"
            onlyW && !onlyL && !onlyC -> "$wordCount"
            onlyC && !onlyL && !onlyW -> "$byteCount"
            else -> "${lineCount.toString().padStart(6)} ${wordCount.toString().padStart(6)} ${byteCount.toString().padStart(6)}"
        }
        return CommandResult(0, out)
    }

    private fun handleSort(args: List<String>, stdin: String, session: ShellSessionState): CommandResult {
        val flags = args.filter { it.startsWith("-") }.joinToString("")
        val files = args.filterNot { it.startsWith("-") }
        val (text, err) = readInputsOrStdin(files, stdin, session, "sort")
        if (err != null || text == null) return err ?: CommandResult(1)

        var lines = text.lines().filter { it.isNotEmpty() }
        lines = if (flags.contains('n')) {
            lines.sortedBy { it.trim().toDoubleOrNull() ?: 0.0 }
        } else {
            lines.sorted()
        }
        if (flags.contains('r')) lines = lines.reversed()
        if (flags.contains('u')) lines = lines.distinct()
        return CommandResult(0, lines.joinToString("\n"))
    }

    private fun handleUniq(args: List<String>, stdin: String, session: ShellSessionState): CommandResult {
        val count = args.any { it.contains('c') }
        val files = args.filterNot { it.startsWith("-") }
        val (text, err) = readInputsOrStdin(files, stdin, session, "uniq")
        if (err != null || text == null) return err ?: CommandResult(1)

        val out = mutableListOf<String>()
        var prev: String? = null
        var freq = 0
        for (line in text.lines()) {
            if (prev == null) {
                prev = line
                freq = 1
            } else if (line == prev) {
                freq++
            } else {
                out.add(if (count) "${freq.toString().padStart(5)} $prev" else prev)
                prev = line
                freq = 1
            }
        }
        if (prev != null) {
            out.add(if (count) "${freq.toString().padStart(5)} $prev" else prev)
        }
        return CommandResult(0, out.joinToString("\n"))
    }

    private fun handleCut(args: List<String>, stdin: String, session: ShellSessionState): CommandResult {
        var delim = "\t"
        var fieldExpr = "1"
        val files = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            when {
                args[i] == "-d" -> {
                    delim = args.getOrNull(i + 1) ?: "\t"
                    i += 2
                }
                args[i].startsWith("-d") -> {
                    delim = args[i].drop(2)
                    i++
                }
                args[i] == "-f" -> {
                    fieldExpr = args.getOrNull(i + 1) ?: "1"
                    i += 2
                }
                args[i].startsWith("-f") -> {
                    fieldExpr = args[i].drop(2)
                    i++
                }
                !args[i].startsWith("-") -> {
                    files.add(args[i])
                    i++
                }
                else -> i++
            }
        }
        val (text, err) = readInputsOrStdin(files, stdin, session, "cut")
        if (err != null || text == null) return err ?: CommandResult(1)
        val fieldIndices = fieldExpr.split(",").mapNotNull { it.trim().toIntOrNull()?.minus(1) }
        val out = text.lines().joinToString("\n") { line ->
            val parts = line.split(delim)
            fieldIndices.mapNotNull { idx -> parts.getOrNull(idx) }.joinToString(delim)
        }
        return CommandResult(0, out)
    }

    private fun handleTr(args: List<String>, stdin: String): CommandResult {
        if (args.size < 2) return CommandResult(1, stderr = "tr: uso: tr <set1> <set2>")
        val from = expandTrRange(args[0])
        val to = expandTrRange(args[1])
        val out = buildString {
            for (ch in stdin) {
                val idx = from.indexOf(ch)
                if (idx >= 0) {
                    append(to.getOrElse(idx) { to.lastOrNull() ?: ch })
                } else {
                    append(ch)
                }
            }
        }
        return CommandResult(0, out.trimEnd('\n'))
    }

    private fun expandTrRange(spec: String): String {
        return when (spec) {
            "a-z", "[:lower:]" -> ('a'..'z').joinToString("")
            "A-Z", "[:upper:]" -> ('A'..'Z').joinToString("")
            "0-9", "[:digit:]" -> ('0'..'9').joinToString("")
            else -> spec
        }
    }

    private fun handleRev(args: List<String>, stdin: String, session: ShellSessionState): CommandResult {
        val (text, err) = readInputsOrStdin(args, stdin, session, "rev")
        if (err != null || text == null) return err ?: CommandResult(1)
        return CommandResult(0, text.lines().joinToString("\n") { it.reversed() })
    }

    private fun handleNl(args: List<String>, stdin: String, session: ShellSessionState): CommandResult {
        val (text, err) = readInputsOrStdin(args, stdin, session, "nl")
        if (err != null || text == null) return err ?: CommandResult(1)
        return CommandResult(
            0,
            text.lines().mapIndexed { idx, s -> "${(idx + 1).toString().padStart(5)}\t$s" }.joinToString("\n")
        )
    }

    private fun handleDiff(args: List<String>, session: ShellSessionState): CommandResult {
        val files = args.filterNot { it.startsWith("-") }
        if (files.size < 2) return CommandResult(2, stderr = "diff: especifique dois arquivos")
        val f1 = session.resolveSafe(files[0]) ?: return CommandResult(2, stderr = "diff: caminho inválido")
        val f2 = session.resolveSafe(files[1]) ?: return CommandResult(2, stderr = "diff: caminho inválido")
        if (!f1.isFile || !f2.isFile) return CommandResult(2, stderr = "diff: arquivo não encontrado")

        val l1 = f1.readLines()
        val l2 = f2.readLines()
        val max = maxOf(l1.size, l2.size)
        val diffs = mutableListOf<String>()
        for (idx in 0 until max) {
            val a = l1.getOrNull(idx)
            val b = l2.getOrNull(idx)
            if (a != b) {
                if (a != null) diffs.add("< [${idx + 1}] $a")
                if (b != null) diffs.add("> [${idx + 1}] $b")
            }
        }
        return if (diffs.isEmpty()) CommandResult(0, "") else CommandResult(1, diffs.joinToString("\n"))
    }

    private fun handleTee(args: List<String>, stdin: String, session: ShellSessionState): CommandResult {
        val append = args.any { it == "-a" }
        val files = args.filterNot { it.startsWith("-") }
        for (name in files) {
            val target = session.resolveSafe(name)
                ?: return CommandResult(1, stderr = "tee: caminho inválido: $name")
            target.parentFile?.mkdirs()
            if (append) target.appendText(stdin) else target.writeText(stdin)
        }
        session.onRefreshWorkspace()
        return CommandResult(0, stdin.trimEnd('\n'))
    }

    private fun handleJq(args: List<String>, stdin: String, session: ShellSessionState): CommandResult {
        var filter = "."
        val files = mutableListOf<String>()
        for (a in args) {
            if (a.startsWith(".") && files.isEmpty() && filter == ".") {
                filter = a
            } else if (!a.startsWith("-")) {
                files.add(a)
            }
        }
        val (text, err) = readInputsOrStdin(files, stdin, session, "jq")
        if (err != null || text == null) return err ?: CommandResult(1)

        return runCatching {
            val trimmed = text.trim()
            var current: Any? = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
            val steps = filter.trimStart('.').split('.').filter { it.isNotEmpty() }
            for (step in steps) {
                current = when (current) {
                    is JSONObject -> if (current.has(step)) current.get(step) else null
                    is JSONArray -> step.toIntOrNull()?.let { idx -> if (idx in 0 until current.length()) current.get(idx) else null }
                    else -> null
                }
            }
            val formatted = when (current) {
                is JSONObject -> current.toString(2)
                is JSONArray -> current.toString(2)
                null -> "null"
                else -> current.toString()
            }
            CommandResult(0, formatted)
        }.getOrElse { e ->
            CommandResult(1, stderr = "jq: JSON inválido (${e.message})")
        }
    }

    fun humanBytes(bytes: Long): String {
        if (bytes < 1024) return "${bytes}B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1fK", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1fM", mb)
        return String.format(Locale.US, "%.1fG", mb / 1024.0)
    }
}
