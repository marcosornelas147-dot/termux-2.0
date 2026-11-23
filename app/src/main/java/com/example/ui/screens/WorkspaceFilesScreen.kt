package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.server.TerminalCommandProcessor
import com.example.server.WorkspaceFileItem
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun WorkspaceFilesScreen(
    files: List<WorkspaceFileItem>,
    terminalProcessor: TerminalCommandProcessor,
    onRunInBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    val editableFiles = remember(files) { files.filter { !it.isDirectory } }
    var selectedPath by remember { mutableStateOf("index.html") }
    var editorText by remember { mutableStateOf("") }
    var savedFeedback by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }

    LaunchedEffect(selectedPath, editableFiles.size) {
        if (editableFiles.isNotEmpty()) {
            val target = if (editableFiles.any { it.relativePath == selectedPath }) {
                selectedPath
            } else {
                editableFiles.first().relativePath
            }
            selectedPath = target
            editorText = terminalProcessor.readWorkspaceFile(target)
            savedFeedback = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.secondary)
                        Column {
                            Text("Projetos HTML / CSS / JS", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "~/termux_home/$selectedPath",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { showNewFileDialog = true },
                            modifier = Modifier.testTag("new_workspace_file_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Novo arquivo HTML/JS")
                        }
                        Button(
                            onClick = {
                                terminalProcessor.writeWorkspaceFile(selectedPath, editorText)
                                onRunInBrowser()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("run_html_in_browser_btn")
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Abrir :8080", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // File Tabs
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(editableFiles, key = { it.relativePath }) { file ->
                        val selected = file.relativePath == selectedPath
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.clickable {
                                selectedPath = file.relativePath
                                editorText = terminalProcessor.readWorkspaceFile(file.relativePath)
                                savedFeedback = false
                            }
                        ) {
                            Text(
                                text = file.relativePath,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Code Editor
        OutlinedTextField(
            value = editorText,
            onValueChange = {
                editorText = it
                savedFeedback = false
            },
            textStyle = TextStyle(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onBackground
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("workspace_code_editor")
        )

        // Bottom Save Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (savedFeedback) "✓ Salvo em ~/termux_home/$selectedPath" else "${editorText.length} caracteres",
                style = MaterialTheme.typography.labelSmall,
                color = if (savedFeedback) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    terminalProcessor.writeWorkspaceFile(selectedPath, editorText)
                    savedFeedback = true
                },
                modifier = Modifier.testTag("save_workspace_file_btn")
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Salvar")
            }
        }
    }

    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Novo arquivo no workspace") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    placeholder = { Text("ex: teste.html ou script.js") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newFileName.trim().trimStart('/')
                        if (clean.isNotEmpty()) {
                            val starter = if (clean.endsWith(".html")) {
                                "<!DOCTYPE html>\n<html>\n<body style='background:#0b0f14;color:#10e89a;font-family:monospace;padding:24px'>\n  <h1>$clean funcionando no Termux 2.0!</h1>\n</body>\n</html>"
                            } else {
                                "// $clean\n"
                            }
                            terminalProcessor.writeWorkspaceFile(clean, starter)
                            selectedPath = clean
                            editorText = starter
                            newFileName = ""
                            showNewFileDialog = false
                        }
                    }
                ) {
                    Text("Criar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
