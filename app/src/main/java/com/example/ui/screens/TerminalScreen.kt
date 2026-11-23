package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.server.LinuxCommandCatalog
import com.example.server.LinuxCommandDoc
import com.example.server.RunningServerInfo
import com.example.server.TerminalLine
import com.example.server.TerminalLineType
import com.example.ui.theme.JetBrainsMonoFamily

private val QuickTerminalCommands = listOf(
    "neofetch",
    "python -m http.server 8080",
    "serve 3000",
    "ls -la",
    "tree -L 2",
    "cat index.html | grep -n api",
    "curl 127.0.0.1:8080/api/status | jq",
    "free -h",
    "df -h",
    "help"
)

private val ExtraKeys = listOf("TAB", "↑", "↓", "|", ">", ">>", "&&", "/", "~", "-", "CLR")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    lines: List<TerminalLine>,
    promptPath: String,
    runningServers: List<RunningServerInfo>,
    onExecuteCommand: (String) -> Unit,
    onAutocomplete: (String) -> String,
    getHistory: () -> List<String>,
    onOpenUrlInBrowser: (String) -> Unit,
    onStopServer: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var commandField by remember { mutableStateOf(TextFieldValue("")) }
    var historyIndex by remember { mutableIntStateOf(-1) }
    var showGuideSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    fun submitCurrentCommand() {
        val raw = commandField.text
        if (raw.isNotBlank()) {
            onExecuteCommand(raw)
            commandField = TextFieldValue("")
            historyIndex = -1
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header + Active Servers Strip
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text("Terminal Linux • Termux 2.0", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Shell POSIX: pipes (|), redirecionamento (>), ${LinuxCommandCatalog.docs.size}+ comandos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { showGuideSheet = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("terminal_linux_guide_btn")
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Comandos", style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (runningServers.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(runningServers, key = { it.port }) { srv ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = srv.baseUrl,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${srv.requestCount} reqs",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        onClick = { onOpenUrlInBrowser(srv.baseUrl) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .testTag("open_server_${srv.port}_btn")
                                    ) {
                                        Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Abrir", style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(
                                        onClick = { onStopServer(srv.port) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = "Parar servidor ${srv.port}",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Terminal Console Output
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("terminal_output_list")
        ) {
            items(lines, key = { it.id }) { line ->
                val color = when (line.type) {
                    TerminalLineType.COMMAND -> MaterialTheme.colorScheme.primary
                    TerminalLineType.SUCCESS -> MaterialTheme.colorScheme.primary
                    TerminalLineType.ERROR -> MaterialTheme.colorScheme.error
                    TerminalLineType.SERVER_LOG -> MaterialTheme.colorScheme.secondary
                    TerminalLineType.OUTPUT -> MaterialTheme.colorScheme.onBackground
                }
                Text(
                    text = line.text,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontWeight = if (line.type == TerminalLineType.COMMAND) FontWeight.Bold else FontWeight.Normal,
                    color = color
                )
            }
        }

        // Quick Command Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            items(QuickTerminalCommands) { quickCmd ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .testTag("quick_cmd_${quickCmd.substringBefore(" ")}")
                        .clickable { onExecuteCommand(quickCmd) }
                ) {
                    Text(
                        text = quickCmd,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Termux Extra Keys Bar (TAB, arrows, pipes, redirections)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f))
        ) {
            items(ExtraKeys) { keyLabel ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                    modifier = Modifier.clickable {
                        when (keyLabel) {
                            "TAB" -> {
                                val completed = onAutocomplete(commandField.text)
                                commandField = TextFieldValue(completed, TextRange(completed.length))
                            }
                            "↑" -> {
                                val hist = getHistory()
                                if (hist.isNotEmpty()) {
                                    val nextIdx = if (historyIndex == -1) hist.lastIndex else (historyIndex - 1).coerceAtLeast(0)
                                    historyIndex = nextIdx
                                    val cmdText = hist[nextIdx]
                                    commandField = TextFieldValue(cmdText, TextRange(cmdText.length))
                                }
                            }
                            "↓" -> {
                                val hist = getHistory()
                                if (historyIndex >= 0) {
                                    if (historyIndex < hist.lastIndex) {
                                        historyIndex++
                                        val cmdText = hist[historyIndex]
                                        commandField = TextFieldValue(cmdText, TextRange(cmdText.length))
                                    } else {
                                        historyIndex = -1
                                        commandField = TextFieldValue("")
                                    }
                                }
                            }
                            "CLR" -> onExecuteCommand("clear")
                            else -> {
                                val insert = if (keyLabel in listOf("|", ">", ">>", "&&")) " $keyLabel " else keyLabel
                                val next = commandField.text + insert
                                commandField = TextFieldValue(next, TextRange(next.length))
                            }
                        }
                    }
                ) {
                    Text(
                        text = keyLabel,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Command Input Prompt
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$promptPath $",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (commandField.text.isEmpty()) {
                        Text(
                            text = "ls -la | grep html",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    BasicTextField(
                        value = commandField,
                        onValueChange = { commandField = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submitCurrentCommand() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("terminal_command_input")
                    )
                }
                IconButton(
                    onClick = { submitCurrentCommand() },
                    modifier = Modifier.testTag("terminal_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Executar comando",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (showGuideSheet) {
        LinuxCommandGuideSheet(
            onSelectExample = { example ->
                commandField = TextFieldValue(example, TextRange(example.length))
                showGuideSheet = false
            },
            onDismiss = { showGuideSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinuxCommandGuideSheet(
    onSelectExample: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Todos") }

    val categories = remember {
        listOf("Todos") + LinuxCommandCatalog.docs.map { it.category }.distinct()
    }

    val filtered = remember(query, selectedCategory) {
        LinuxCommandCatalog.docs.filter { doc ->
            val catOk = selectedCategory == "Todos" || doc.category == selectedCategory
            val qOk = query.isBlank() ||
                doc.name.contains(query, ignoreCase = true) ||
                doc.summary.contains(query, ignoreCase = true)
            catOk && qOk
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Guia de Comandos Linux", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar comando (ex: grep, zip, curl, find)...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(categories) { cat ->
                    val active = cat == selectedCategory
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            1.dp,
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.clickable { selectedCategory = cat }
                    ) {
                        Text(
                            text = cat,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.name }) { doc: LinuxCommandDoc ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectExample(doc.example) }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = doc.synopsis,
                                    fontFamily = JetBrainsMonoFamily,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = doc.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Text(
                                text = doc.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Ex: ${doc.example} (toque para inserir)",
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
