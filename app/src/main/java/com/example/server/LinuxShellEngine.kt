package com.example.server

class LinuxShellEngine(private val session: ShellSessionState) {

    fun executeLine(rawLine: String): CommandResult {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return CommandResult(0)

        session.commandHistory.add(trimmed)

        // Split by top-level ; && ||
        val statements = splitControlOperators(trimmed)
        var lastResult = CommandResult(0)
        var shouldRun = true

        for (stmt in statements) {
            if (shouldRun) {
                lastResult = executePipeline(stmt.command)
                session.lastExitCode = lastResult.exitCode
            }
            shouldRun = when (stmt.nextOp) {
                "&&" -> lastResult.exitCode == 0
                "||" -> lastResult.exitCode != 0
                ";" -> true
                else -> false
            }
        }
        return lastResult
    }

    private data class StatementSegment(val command: String, val nextOp: String?)

    private fun splitControlOperators(input: String): List<StatementSegment> {
        val out = mutableListOf<StatementSegment>()
        val buf = StringBuilder()
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && !inSingle && i + 1 < input.length) {
                buf.append(c).append(input[i + 1])
                i += 2
                continue
            }
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle
                buf.append(c)
                i++
                continue
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble
                buf.append(c)
                i++
                continue
            }
            if (!inSingle && !inDouble) {
                val two = if (i + 1 < input.length) input.substring(i, i + 2) else ""
                if (two == "&&" || two == "||") {
                    out.add(StatementSegment(buf.toString().trim(), two))
                    buf.clear()
                    i += 2
                    continue
                }
                if (c == ';') {
                    out.add(StatementSegment(buf.toString().trim(), ";"))
                    buf.clear()
                    i++
                    continue
                }
            }
            buf.append(c)
            i++
        }
        val rem = buf.toString().trim()
        if (rem.isNotEmpty()) out.add(StatementSegment(rem, null))
        return out
    }

    private fun splitPipes(input: String): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && !inSingle && i + 1 < input.length) {
                buf.append(c).append(input[i + 1])
                i += 2
                continue
            }
            if (c == '\'' && !inDouble) inSingle = !inSingle
            else if (c == '"' && !inSingle) inDouble = !inDouble
            else if (c == '|' && !inSingle && !inDouble) {
                out.add(buf.toString().trim())
                buf.clear()
                i++
                continue
            }
            buf.append(c)
            i++
        }
        val rem = buf.toString().trim()
        if (rem.isNotEmpty()) out.add(rem)
        return out
    }

    private fun executePipeline(segment: String): CommandResult {
        val stages = splitPipes(segment)
        if (stages.isEmpty()) return CommandResult(0)

        var currentStdin = ""
        var finalResult = CommandResult(0)
        val combinedErrors = StringBuilder()

        for (stage in stages) {
            val res = executeSingleCommand(stage, currentStdin)
            if (res.stderr.isNotEmpty()) {
                if (combinedErrors.isNotEmpty()) combinedErrors.append("\n")
                combinedErrors.append(res.stderr)
            }
            currentStdin = if (res.stdout.isEmpty()) "" else "${res.stdout}\n"
            finalResult = res
        }

        return finalResult.copy(stderr = combinedErrors.toString())
    }

    private fun executeSingleCommand(rawStage: String, initialStdin: String): CommandResult {
        val rawTokens = tokenize(rawStage)
        if (rawTokens.isEmpty()) return CommandResult(0)

        // Alias expansion for first token
        val firstAlias = session.aliases[rawTokens[0]]
        val tokens = if (firstAlias != null) {
            tokenize(firstAlias) + rawTokens.drop(1)
        } else {
            rawTokens
        }

        // Parse redirections: >, >>, <
        var redirectOutFile: String? = null
        var redirectAppend = false
        var redirectInFile: String? = null
        val cleanTokens = mutableListOf<String>()

        var idx = 0
        while (idx < tokens.size) {
            val tok = tokens[idx]
            when {
                tok == ">>" -> {
                    redirectOutFile = tokens.getOrNull(idx + 1)
                    redirectAppend = true
                    idx += 2
                }
                tok == ">" -> {
                    redirectOutFile = tokens.getOrNull(idx + 1)
                    redirectAppend = false
                    idx += 2
                }
                tok == "<" -> {
                    redirectInFile = tokens.getOrNull(idx + 1)
                    idx += 2
                }
                tok.startsWith(">>") && tok.length > 2 -> {
                    redirectOutFile = tok.drop(2)
                    redirectAppend = true
                    idx++
                }
                tok.startsWith(">") && tok.length > 1 -> {
                    redirectOutFile = tok.drop(1)
                    redirectAppend = false
                    idx++
                }
                else -> {
                    cleanTokens.add(tok)
                    idx++
                }
            }
        }

        if (cleanTokens.isEmpty()) return CommandResult(0)

        // Handle inline VAR=value assignment without command
        if (cleanTokens.size == 1 && Regex("^[A-Za-z_][A-Za-z0-9_]*=.*").matches(cleanTokens[0])) {
            val k = cleanTokens[0].substringBefore("=")
            val v = cleanTokens[0].substringAfter("=")
            session.env[k] = v
            return CommandResult(0)
        }

        // Input redirection
        var stdin = initialStdin
        if (redirectInFile != null) {
            val inFile = session.resolveSafe(redirectInFile)
                ?: return CommandResult(1, stderr = "bash: $redirectInFile: acesso negado")
            if (!inFile.isFile) {
                return CommandResult(1, stderr = "bash: $redirectInFile: arquivo inexistente")
            }
            stdin = inFile.readText()
        }

        val cmd = cleanTokens[0]
        val args = cleanTokens.drop(1)

        val result = LinuxFsAndTextCommands.executeFsOrText(cmd, args, stdin, session)
            ?: LinuxSystemAndNetCommands.executeSystemOrNet(cmd, args, stdin, session)
            ?: CommandResult(
                exitCode = 127,
                stderr = "$cmd: comando não encontrado. Digite 'help' ou 'pkg list'."
            )

        // Output redirection
        if (redirectOutFile != null && result.exitCode == 0) {
            val outFile = session.resolveSafe(redirectOutFile)
                ?: return CommandResult(1, stderr = "bash: $redirectOutFile: caminho fora do sandbox")
            outFile.parentFile?.mkdirs()
            val payload = if (result.stdout.isEmpty()) "" else "${result.stdout}\n"
            if (redirectAppend) outFile.appendText(payload) else outFile.writeText(payload)
            session.onRefreshWorkspace()
            return result.copy(stdout = "")
        }

        return result
    }

    private fun tokenize(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var hasToken = false
        var inSingle = false
        var inDouble = false
        var i = 0

        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && !inSingle && i + 1 < input.length) {
                current.append(input[i + 1])
                hasToken = true
                i += 2
                continue
            }
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle
                hasToken = true
                i++
                continue
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble
                hasToken = true
                i++
                continue
            }
            if (!inSingle && c == '$') {
                val varName = StringBuilder()
                if (i + 1 < input.length && input[i + 1] == '?') {
                    current.append(session.lastExitCode)
                    hasToken = true
                    i += 2
                    continue
                } else if (i + 1 < input.length && input[i + 1] == '{') {
                    val close = input.indexOf('}', i + 2)
                    if (close > i + 2) {
                        val key = input.substring(i + 2, close)
                        current.append(session.env[key].orEmpty())
                        hasToken = true
                        i = close + 1
                        continue
                    }
                } else {
                    var j = i + 1
                    while (j < input.length && (input[j].isLetterOrDigit() || input[j] == '_')) {
                        varName.append(input[j])
                        j++
                    }
                    if (varName.isNotEmpty()) {
                        current.append(session.env[varName.toString()].orEmpty())
                        hasToken = true
                        i = j
                        continue
                    }
                }
            }
            if (!inSingle && !inDouble && c.isWhitespace()) {
                if (hasToken) {
                    result.add(current.toString())
                    current.clear()
                    hasToken = false
                }
                i++
                continue
            }
            if (!inSingle && !inDouble && (c == '>' || c == '<')) {
                if (hasToken) {
                    result.add(current.toString())
                    current.clear()
                    hasToken = false
                }
                if (c == '>' && i + 1 < input.length && input[i + 1] == '>') {
                    result.add(">>")
                    i += 2
                } else {
                    result.add(c.toString())
                    i++
                }
                continue
            }
            current.append(c)
            hasToken = true
            i++
        }
        if (hasToken) result.add(current.toString())
        return result
    }

    fun autocomplete(currentInput: String): String {
        val trimmedStart = currentInput.trimStart()
        if (trimmedStart.isEmpty()) return currentInput
        val parts = currentInput.split(Regex("\\s+"))
        if (parts.size <= 1 && !currentInput.endsWith(" ")) {
            val prefix = parts.firstOrNull().orEmpty()
            val match = LinuxCommandCatalog.docs
                .map { it.name }
                .plus(session.aliases.keys)
                .distinct()
                .sorted()
                .firstOrNull { it.startsWith(prefix, ignoreCase = true) }
            return match?.let { "$it " } ?: currentInput
        } else {
            val lastToken = if (currentInput.endsWith(" ")) "" else parts.last()
            val fileMatch = session.currentDir.listFiles()
                ?.map { if (it.isDirectory) "${it.name}/" else it.name }
                ?.sorted()
                ?.firstOrNull { it.startsWith(lastToken, ignoreCase = true) }
            if (fileMatch != null) {
                val base = if (currentInput.endsWith(" ")) currentInput else currentInput.dropLast(lastToken.length)
                return "$base$fileMatch"
            }
        }
        return currentInput
    }
}
