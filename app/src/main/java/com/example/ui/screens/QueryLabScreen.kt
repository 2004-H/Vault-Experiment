package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.DataviewJsRenderNode
import com.example.data.model.DataviewResult
import com.example.data.model.VaultTask
import com.example.ui.QueryLabMode
import com.example.ui.components.DataviewJsResultView
import com.example.ui.components.DataviewResultView
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun QueryLabScreen(
    mode: QueryLabMode,
    dqlQuery: String,
    jsScript: String,
    dqlResult: DataviewResult?,
    jsNodes: List<DataviewJsRenderNode>,
    isRunning: Boolean,
    onModeChange: (QueryLabMode) -> Unit,
    onDqlChange: (String) -> Unit,
    onJsChange: (String) -> Unit,
    onApplyPreset: (QueryLabMode, String) -> Unit,
    onRunQuery: () -> Unit,
    onSaveAsPage: (title: String, folder: String) -> Unit,
    onOpenNote: (String) -> Unit,
    onToggleTask: (VaultTask, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSaveDialog by remember { mutableStateOf(false) }

    val presets = remember {
        listOf(
            Triple(
                "Projects Table",
                QueryLabMode.DQL,
                "TABLE status AS \"Status\", priority AS \"Priority\", rating AS \"Rating\", due AS \"Due\"\nFROM #project\nSORT priority DESC"
            ),
            Triple(
                "Book Ratings",
                QueryLabMode.DQL,
                "TABLE author AS \"Author\", rating AS \"Rating\", status AS \"Status\"\nFROM #book\nSORT rating DESC"
            ),
            Triple(
                "Open Tasks",
                QueryLabMode.DQL,
                "TASK\nWHERE !completed\nSORT due ASC"
            ),
            Triple(
                "JS Velocity",
                QueryLabMode.DATAVIEW_JS,
                "const items = dv.pages(\"#project\").sort(p => p.progress || 0, \"desc\");\ndv.header(3, \"Sprint Velocity (\" + items.length + \")\");\ndv.table([\"Project\", \"Status\", \"Progress\"], items.map(p => [p.file.link, p.status || \"—\", (p.progress || 0) + \"%\"]));"
            ),
            Triple(
                "JS Tag Summary",
                QueryLabMode.DATAVIEW_JS,
                "const all = dv.pages();\ndv.header(3, \"Vault Overview\");\ndv.paragraph(\"Total indexed pages: \" + all.length);\ndv.list(all.limit(6).map(p => p.file.link));"
            ),
            Triple(
                "Calendar",
                QueryLabMode.DQL,
                "CALENDAR file.mtime"
            )
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 780.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Query Lab",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Test Dataview DQL and live DataviewJS scripts offline against your local markdown vault.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == QueryLabMode.DQL,
                    onClick = { onModeChange(QueryLabMode.DQL) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = { Icon(Icons.Rounded.TableChart, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("lab_mode_dql")
                ) {
                    Text("Dataview (DQL)")
                }
                SegmentedButton(
                    selected = mode == QueryLabMode.DATAVIEW_JS,
                    onClick = { onModeChange(QueryLabMode.DATAVIEW_JS) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { Icon(Icons.Rounded.DataObject, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("lab_mode_js")
                ) {
                    Text("DataviewJS")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { (label, presetMode, code) ->
                    FilterChip(
                        selected = false,
                        onClick = { onApplyPreset(presetMode, code) },
                        label = { Text(label) }
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = if (mode == QueryLabMode.DQL) dqlQuery else jsScript,
                        onValueChange = {
                            if (mode == QueryLabMode.DQL) onDqlChange(it) else onJsChange(it)
                        },
                        textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                        minLines = 5,
                        label = {
                            Text(if (mode == QueryLabMode.DQL) "```dataview" else "```dataviewjs")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("query_lab_input")
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("lab_save_page_button")
                        ) {
                            Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save as Page")
                        }

                        Button(
                            onClick = onRunQuery,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("lab_run_button")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Run Query")
                        }
                    }
                }
            }

            if (isRunning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "LIVE OUTPUT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    if (mode == QueryLabMode.DQL) {
                        if (dqlResult != null) {
                            DataviewResultView(
                                result = dqlResult,
                                onNoteClick = onOpenNote,
                                onToggleTask = onToggleTask
                            )
                        } else {
                            Text("Tap Run Query to execute.", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        DataviewJsResultView(
                            nodes = jsNodes,
                            onNoteClick = onOpenNote,
                            onToggleTask = onToggleTask
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showSaveDialog) {
        var pageTitle by remember { mutableStateOf("Saved Query View") }
        var folder by remember { mutableStateOf("Dashboards") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save as Dataview Block Page") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pageTitle,
                        onValueChange = { pageTitle = it },
                        label = { Text("Block Page Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = folder,
                        onValueChange = { folder = it },
                        label = { Text("Folder") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveDialog = false
                        onSaveAsPage(pageTitle.ifBlank { "Saved Query View" }, folder)
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }
}
