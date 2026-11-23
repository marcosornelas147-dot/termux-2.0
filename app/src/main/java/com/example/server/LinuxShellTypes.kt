package com.example.server

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class CommandResult(
    val exitCode: Int = 0,
    val stdout: String = "",
    val stderr: String = "",
    val isSuccessStyle: Boolean = false
)

data class LinuxCommandDoc(
    val name: String,
    val category: String,
    val synopsis: String,
    val summary: String,
    val example: String
)

class ShellSessionState(
    val context: Context,
    val homeDir: File,
    val serverManager: LocalDevServerManager,
    val onOpenUrlInBrowser: (String) -> Unit,
    val onClearTerminal: () -> Unit,
    val onRefreshWorkspace: () -> Unit
) {
    var currentDir: File = homeDir
    var previousDir: File = homeDir
    var lastExitCode: Int = 0
    val startTimeMs: Long = System.currentTimeMillis()

    val env: ConcurrentHashMap<String, String> = ConcurrentHashMap<String, String>().apply {
        put("HOME", "/data/termux_home")
        put("PWD", "/data/termux_home")
        put("OLDPWD", "/data/termux_home")
        put("USER", "u0_termux2")
        put("SHELL", "/bin/bash")
        put("TERM", "xterm-256color")
        put("PATH", "/usr/local/bin:/usr/bin:/bin")
        put("LANG", "pt_BR.UTF-8")
    }

    val aliases: ConcurrentHashMap<String, String> = ConcurrentHashMap<String, String>().apply {
        put("ll", "ls -la")
        put("la", "ls -a")
        put("cls", "clear")
    }

    val commandHistory: MutableList<String> = mutableListOf()
    val fileModes: ConcurrentHashMap<String, String> = ConcurrentHashMap()

    fun promptPath(): String {
        val rel = currentDir.relativeToOrNull(homeDir)?.path?.ifBlank { "" } ?: ""
        return if (rel.isEmpty()) "~" else "~/$rel"
    }

    fun virtualAbsolutePath(file: File = currentDir): String {
        val rel = file.relativeToOrNull(homeDir)?.path?.ifBlank { "" } ?: ""
        return if (rel.isEmpty()) "/data/termux_home" else "/data/termux_home/$rel"
    }

    fun resolveSafe(pathArg: String): File? {
        val trimmed = pathArg.trim()
        val homeCanonical = homeDir.canonicalPath
        val rawTarget = when {
            trimmed == "~" -> homeDir
            trimmed.startsWith("~/") -> File(homeDir, trimmed.removePrefix("~/"))
            trimmed == "/data/termux_home" || trimmed == "/home" -> homeDir
            trimmed.startsWith("/data/termux_home/") -> File(homeDir, trimmed.removePrefix("/data/termux_home/"))
            trimmed.startsWith("/home/") -> File(homeDir, trimmed.removePrefix("/home/"))
            trimmed.startsWith("/") -> File(homeDir, trimmed.trimStart('/'))
            else -> File(currentDir, trimmed)
        }.canonicalFile
        return if (rawTarget.path.startsWith(homeCanonical)) rawTarget else null
    }
}

object LinuxCommandCatalog {
    val docs: List<LinuxCommandDoc> = listOf(
        // Arquivos & Diretórios
        LinuxCommandDoc("ls", "Arquivos", "ls [-l] [-a] [-h] [-R] [caminho]", "Lista arquivos e diretórios com permissões e tamanhos.", "ls -la"),
        LinuxCommandDoc("cd", "Arquivos", "cd [diretório | ~ | - | ..]", "Altera o diretório atual de trabalho.", "cd api"),
        LinuxCommandDoc("pwd", "Arquivos", "pwd", "Exibe o caminho completo do diretório atual.", "pwd"),
        LinuxCommandDoc("tree", "Arquivos", "tree [-L nível] [caminho]", "Mostra a árvore hierárquica de pastas e arquivos.", "tree -L 2"),
        LinuxCommandDoc("mkdir", "Arquivos", "mkdir [-p] <diretório...>", "Cria novos diretórios (com -p cria pais automaticamente).", "mkdir -p src/components"),
        LinuxCommandDoc("rmdir", "Arquivos", "rmdir <diretório...>", "Remove diretórios vazios.", "rmdir temp"),
        LinuxCommandDoc("touch", "Arquivos", "touch <arquivo...>", "Cria arquivo vazio ou atualiza timestamp.", "touch app.js"),
        LinuxCommandDoc("cp", "Arquivos", "cp [-r] <origem> <destino>", "Copia arquivos ou diretórios (-r recursivo).", "cp index.html backup.html"),
        LinuxCommandDoc("mv", "Arquivos", "mv <origem> <destino>", "Move ou renomeia arquivos e diretórios.", "mv old.html new.html"),
        LinuxCommandDoc("rm", "Arquivos", "rm [-r] [-f] <alvo...>", "Remove arquivos ou diretórios recursivamente.", "rm -rf build"),
        LinuxCommandDoc("cat", "Arquivos", "cat [-n] <arquivo...>", "Exibe conteúdo de arquivos ou entrada padrão.", "cat -n index.html"),
        LinuxCommandDoc("head", "Arquivos", "head [-n N] [arquivo]", "Exibe as primeiras N linhas (padrão 10).", "head -n 15 index.html"),
        LinuxCommandDoc("tail", "Arquivos", "tail [-n N] [arquivo]", "Exibe as últimas N linhas (padrão 10).", "tail -n 10 styles.css"),
        LinuxCommandDoc("stat", "Arquivos", "stat <arquivo>", "Mostra metadados detalhados, permissões e datas.", "stat index.html"),
        LinuxCommandDoc("file", "Arquivos", "file <arquivo>", "Identifica o tipo de conteúdo do arquivo.", "file api/info.json"),
        LinuxCommandDoc("find", "Arquivos", "find [caminho] [-name padrão] [-type f|d]", "Busca arquivos e diretórios recursivamente.", "find . -name \"*.html\""),
        LinuxCommandDoc("chmod", "Arquivos", "chmod <modo> <arquivo>", "Altera permissões de acesso (ex: 755, +x).", "chmod 755 app.js"),
        LinuxCommandDoc("du", "Arquivos", "du [-h] [-s] [caminho]", "Calcula espaço em disco usado por pastas/arquivos.", "du -sh ."),
        LinuxCommandDoc("df", "Arquivos", "df [-h]", "Exibe espaço disponível e total no armazenamento.", "df -h"),

        // Texto & Pipelines
        LinuxCommandDoc("echo", "Texto & Pipes", "echo [-n] [-e] <texto>", "Imprime texto ou variáveis de ambiente.", "echo \$HOME"),
        LinuxCommandDoc("printf", "Texto & Pipes", "printf <formato> [args...]", "Formata e imprime texto com escapes.", "printf \"Porta: %s\\n\" 8080"),
        LinuxCommandDoc("grep", "Texto & Pipes", "grep [-i] [-n] [-v] [-c] [-r] <padrão> [arquivo]", "Filtra linhas que combinam com expressão/texto.", "cat index.html | grep -n api"),
        LinuxCommandDoc("sed", "Texto & Pipes", "sed 's/antigo/novo/g' [arquivo]", "Substitui texto em fluxo ou arquivo.", "cat app.js | sed 's/8080/3000/g'"),
        LinuxCommandDoc("awk", "Texto & Pipes", "awk [-F sep] '{print $N}' [arquivo]", "Extrai colunas de texto delimitado.", "ls -l | awk '{print $5, $9}'"),
        LinuxCommandDoc("wc", "Texto & Pipes", "wc [-l] [-w] [-c] [arquivo]", "Conta linhas, palavras e bytes.", "cat index.html | wc -l"),
        LinuxCommandDoc("sort", "Texto & Pipes", "sort [-r] [-n] [-u] [arquivo]", "Ordena linhas alfabética ou numericamente.", "ls | sort -r"),
        LinuxCommandDoc("uniq", "Texto & Pipes", "uniq [-c] [arquivo]", "Remove ou conta linhas duplicadas adjacentes.", "sort log.txt | uniq -c"),
        LinuxCommandDoc("cut", "Texto & Pipes", "cut -d <sep> -f <campos> [arquivo]", "Recorta colunas por delimitador.", "echo \"a:b:c\" | cut -d : -f 2"),
        LinuxCommandDoc("tr", "Texto & Pipes", "tr <set1> <set2>", "Traduz ou converte caracteres na entrada.", "echo \"termux\" | tr a-z A-Z"),
        LinuxCommandDoc("diff", "Texto & Pipes", "diff <arq1> <arq2>", "Compara diferenças linha a linha entre dois arquivos.", "diff index.html admin.html"),
        LinuxCommandDoc("jq", "Texto & Pipes", "jq [.chave] [arquivo.json]", "Formata e consulta propriedades JSON.", "cat api/info.json | jq .service"),
        LinuxCommandDoc("tee", "Texto & Pipes", "tee [-a] <arquivo>", "Grava saída em arquivo e exibe simultaneamente.", "ls | tee lista.txt"),

        // Compactação & Criptografia
        LinuxCommandDoc("zip", "Compactação & Hash", "zip <saida.zip> <arquivos...>", "Compacta arquivos e pastas em formato ZIP real.", "zip site.zip index.html styles.css"),
        LinuxCommandDoc("unzip", "Compactação & Hash", "unzip [-l] <arquivo.zip>", "Lista (-l) ou extrai arquivos ZIP.", "unzip -l site.zip"),
        LinuxCommandDoc("gzip", "Compactação & Hash", "gzip <arquivo>", "Compacta arquivo com GZIP (.gz).", "gzip app.js"),
        LinuxCommandDoc("gunzip", "Compactação & Hash", "gunzip <arquivo.gz>", "Descompacta arquivo .gz.", "gunzip app.js.gz"),
        LinuxCommandDoc("sha256sum", "Compactação & Hash", "sha256sum [arquivo]", "Calcula hash SHA-256 de arquivo ou entrada.", "sha256sum index.html"),
        LinuxCommandDoc("md5sum", "Compactação & Hash", "md5sum [arquivo]", "Calcula checksum MD5.", "md5sum styles.css"),
        LinuxCommandDoc("base64", "Compactação & Hash", "base64 [-d] [arquivo]", "Codifica ou decodifica (-d) em Base64.", "echo \"termux2\" | base64"),
        LinuxCommandDoc("xxd", "Compactação & Hash", "xxd [arquivo]", "Exibe dump hexadecimal e ASCII.", "head -n 2 index.html | xxd"),

        // Rede & Servidores Locais
        LinuxCommandDoc("python", "Rede & Servidores", "python -m http.server [porta]", "Inicia servidor HTTP local em 127.0.0.1.", "python -m http.server 8080"),
        LinuxCommandDoc("serve", "Rede & Servidores", "serve [porta]", "Inicia servidor Node/Dev local com API JSON.", "serve 3000"),
        LinuxCommandDoc("php", "Rede & Servidores", "php -S 127.0.0.1:<porta>", "Inicia servidor embutido na porta informada.", "php -S 127.0.0.1:8000"),
        LinuxCommandDoc("curl", "Rede & Servidores", "curl [-I] [-X METODO] [-d DADOS] <url>", "Executa requisição HTTP real e exibe resposta.", "curl http://127.0.0.1:8080/api/status"),
        LinuxCommandDoc("wget", "Rede & Servidores", "wget [-O nome] <url>", "Baixa arquivo HTTP/HTTPS para a pasta atual.", "wget -O status.json http://127.0.0.1:8080/api/status"),
        LinuxCommandDoc("ping", "Rede & Servidores", "ping [-c N] <host>", "Testa conectividade e latência de rede.", "ping -c 3 127.0.0.1"),
        LinuxCommandDoc("nslookup", "Rede & Servidores", "nslookup <domínio>", "Resolve registros DNS reais do domínio.", "nslookup github.com"),
        LinuxCommandDoc("ifconfig", "Rede & Servidores", "ifconfig | ip addr", "Lista interfaces de rede locais (lo, wlan0).", "ip addr"),
        LinuxCommandDoc("ss", "Rede & Servidores", "ss | netstat", "Lista portas TCP locais em escuta.", "ss"),
        LinuxCommandDoc("open", "Rede & Servidores", "open <url | arquivo.html>", "Abre URL ou arquivo local no Navegador Integrado.", "open 127.0.0.1:8080"),

        // Sistema & Shell
        LinuxCommandDoc("uname", "Sistema & Shell", "uname [-a|-s|-r|-m]", "Mostra informações do sistema, kernel e arquitetura.", "uname -a"),
        LinuxCommandDoc("neofetch", "Sistema & Shell", "neofetch", "Resumo visual completo do Termux 2.0 e hardware.", "neofetch"),
        LinuxCommandDoc("free", "Sistema & Shell", "free [-m|-h]", "Exibe memória RAM total, usada e livre do dispositivo.", "free -m"),
        LinuxCommandDoc("lscpu", "Sistema & Shell", "lscpu | nproc", "Exibe núcleos de CPU e arquitetura suportada.", "lscpu"),
        LinuxCommandDoc("ps", "Sistema & Shell", "ps | top", "Lista processos/servidores ativos e uso de memória.", "ps"),
        LinuxCommandDoc("kill", "Sistema & Shell", "kill <porta>", "Encerra servidor HTTP ativo na porta especificada.", "kill 8080"),
        LinuxCommandDoc("date", "Sistema & Shell", "date [+formato]", "Exibe data e hora atual (suporta +%Y-%m-%d %H:%M).", "date +%Y-%m-%d"),
        LinuxCommandDoc("cal", "Sistema & Shell", "cal", "Exibe o calendário do mês atual.", "cal"),
        LinuxCommandDoc("env", "Sistema & Shell", "env | export VAR=val", "Lista ou define variáveis de ambiente da sessão.", "export PORT=8080"),
        LinuxCommandDoc("alias", "Sistema & Shell", "alias nome='comando'", "Cria ou lista atalhos de comandos.", "alias ll='ls -la'"),
        LinuxCommandDoc("history", "Sistema & Shell", "history", "Exibe histórico de comandos digitados na sessão.", "history"),
        LinuxCommandDoc("bc", "Sistema & Shell", "bc | expr <expressão>", "Avalia expressões matemáticas.", "echo \"(128 * 4) + 16\" | bc"),
        LinuxCommandDoc("man", "Sistema & Shell", "man <comando>", "Abre manual detalhado do comando Linux.", "man grep")
    )

    fun findDoc(name: String): LinuxCommandDoc? =
        docs.firstOrNull { it.name.equals(name, ignoreCase = true) }
}
