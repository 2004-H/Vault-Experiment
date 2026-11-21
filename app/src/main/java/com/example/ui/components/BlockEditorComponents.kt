package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DataviewJsRenderNode
import com.example.data.model.DataviewResult
import com.example.data.model.VaultBlock
import com.example.data.model.VaultNote
import com.example.data.model.VaultTask
import com.example.ui.theme.AmberProperty
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TealQuery

@Composable
fun BlockCardItem(
    block: VaultBlock,
    note: VaultNote,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    evaluateDql: (String) -> DataviewResult,
    jsNodes: List<DataviewJsRenderNode>?,
    onRequestJsEval: (String, String) -> Unit,
    onRefreshJsEval: (String, String) -> Unit,
    onUpdateBlock: (VaultBlock) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onNoteClick: (String) -> Unit,
    onToggleTaskInQuery: (VaultTask, Boolean) -> Unit,
    onToggleBlockTask: (VaultBlock.TaskBlock, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    when (block) {
        is VaultBlock.DataviewQueryBlock -> DataviewQueryBlockCard(
            block = block,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            evaluateDql = evaluateDql,
            onUpdate = onUpdateBlock,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onDelete = onDelete,
            onNoteClick = onNoteClick,
            onToggleTask = onToggleTaskInQuery,
            modifier = modifier
        )

        is VaultBlock.DataviewJsBlock -> DataviewJsBlockCard(
            block = block,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            jsNodes = jsNodes,
            onRequestEval = { onRequestJsEval(block.id, block.script) },
            onRefreshEval = { onRefreshJsEval(block.id, block.script) },
            onUpdate = onUpdateBlock,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onDelete = onDelete,
            onNoteClick = onNoteClick,
            onToggleTask = onToggleTaskInQuery,
            modifier = modifier
        )

        is VaultBlock.HeadingBlock -> EditableHeadingBlockCard(
            block = block,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            onUpdate = onUpdateBlock,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onDelete = onDelete,
            modifier = modifier
        )

        is VaultBlock.TaskBlock -> EditableTaskBlockCard(
            block = block,
            note = note,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            onToggle = { onToggleBlockTask(block, !block.completed) },
            onUpdate = onUpdateBlock,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onDelete = onDelete,
            modifier = modifier
        )

        is VaultBlock.CalloutBlock -> EditableCalloutBlockCard(
            block = block,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            onUpdate = onUpdateBlock,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onDelete = onDelete,
            modifier = modifier
        )

        is VaultBlock.MarkdownParagraphBlock -> EditableParagraphBlockCard(
            block = block,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            onUpdate = onUpdateBlock,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onDelete = onDelete,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataviewQueryBlockCard(
    block: VaultBlock.DataviewQueryBlock,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    evaluateDql: (String) -> DataviewResult,
    onUpdate: (VaultBlock.DataviewQueryBlock) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onNoteClick: (String) -> Unit,
    onToggleTask: (VaultTask, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember(block.id) { mutableStateOf(false) }
    var showBuilder by remember(block.id) { mutableStateOf(false) }
    var draftQuery by remember(block.query) { mutableStateOf(block.query) }

    // Visual Builder state
    var builderType by remember { mutableStateOf("TABLE") }
    var builderFrom by remember { mutableStateOf("#project") }
    var builderWhere by remember { mutableStateOf("status = \"active\"") }
    var builderSort by remember { mutableStateOf("priority DESC") }

    val result = remember(block.query, evaluateDql) { evaluateDql(block.query) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, TealQuery.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            BlockHeaderRow(
                badge = "DATAVIEW",
                badgeColor = TealQuery,
                icon = Icons.Rounded.TableChart,
                editing = editing,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onToggleEdit = { editing = !editing },
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onDelete = onDelete
            )

            if (editing) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showBuilder) "Visual Query Builder" else "DQL Query",
                        style = MaterialTheme.typography.labelLarge
                    )
                    TextButton(onClick = { showBuilder = !showBuilder }) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (showBuilder) "Code Mode" else "Visual Builder")
                    }
                }

                if (showBuilder) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("TABLE", "LIST", "TASK", "CALENDAR").forEach { mode ->
                            FilterChip(
                                selected = builderType == mode,
                                onClick = { builderType = mode },
                                label = { Text(mode) }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = builderFrom,
                        onValueChange = { builderFrom = it },
                        label = { Text("FROM (#tag or \"Folder\")") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = builderWhere,
                        onValueChange = { builderWhere = it },
                        label = { Text("WHERE condition (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = builderSort,
                        onValueChange = { builderSort = it },
                        label = { Text("SORT field ASC/DESC") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val headerLine = when (builderType) {
                                "TABLE" -> "TABLE status AS \"Status\", priority AS \"Priority\", rating AS \"Rating\""
                                "CALENDAR" -> "CALENDAR file.mtime"
                                else -> builderType
                            }
                            val built = buildString {
                                appendLine(headerLine)
                                if (builderFrom.isNotBlank()) appendLine("FROM $builderFrom")
                                if (builderWhere.isNotBlank()) appendLine("WHERE $builderWhere")
                                if (builderSort.isNotBlank() && builderType != "CALENDAR") appendLine("SORT $builderSort")
                            }.trim()
                            draftQuery = built
                            showBuilder = false
                        }
                    ) {
                        Text("Generate DQL")
                    }
                } else {
                    OutlinedTextField(
                        value = draftQuery,
                        onValueChange = { draftQuery = it },
                        textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("FROM #project", "WHERE status = \"active\"", "SORT rating DESC", "LIMIT 5").forEach { snippet ->
                            FilterChip(
                                selected = false,
                                onClick = { draftQuery = draftQuery.trimEnd() + "\n" + snippet },
                                label = { Text(snippet, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        draftQuery = block.query
                        editing = false
                    }) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onUpdate(block.copy(query = draftQuery))
                            editing = false
                        }
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save Query")
                    }
                }
            } else {
                Spacer(Modifier.height(10.dp))
                DataviewResultView(
                    result = result,
                    onNoteClick = onNoteClick,
                    onToggleTask = onToggleTask
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataviewJsBlockCard(
    block: VaultBlock.DataviewJsBlock,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    jsNodes: List<DataviewJsRenderNode>?,
    onRequestEval: () -> Unit,
    onRefreshEval: () -> Unit,
    onUpdate: (VaultBlock.DataviewJsBlock) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onNoteClick: (String) -> Unit,
    onToggleTask: (VaultTask, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember(block.id) { mutableStateOf(false) }
    var draftScript by remember(block.script) { mutableStateOf(block.script) }

    LaunchedEffect(block.id, block.script, jsNodes == null) {
        if (jsNodes == null) {
            onRequestEval()
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            BlockHeaderRow(
                badge = "DATAVIEWJS",
                badgeColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Rounded.DataObject,
                editing = editing,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                extraAction = {
                    IconButton(onClick = onRefreshEval) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Re-run DataviewJS",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                onToggleEdit = { editing = !editing },
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onDelete = onDelete
            )

            if (editing) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = draftScript,
                    onValueChange = { draftScript = it },
                    textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("dv.pages(\"#project\")", "dv.table([\"Note\"], ...)", "dv.list(...)", "dv.header(3, \"Title\")").forEach { chip ->
                        FilterChip(
                            selected = false,
                            onClick = { draftScript = draftScript.trimEnd() + "\n" + chip },
                            label = { Text(chip, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        draftScript = block.script
                        editing = false
                    }) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onUpdate(block.copy(script = draftScript))
                            editing = false
                        }
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save & Run")
                    }
                }
            } else {
                Spacer(Modifier.height(10.dp))
                if (jsNodes == null) {
                    Text(
                        text = "Executing DataviewJS sandbox...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    DataviewJsResultView(
                        nodes = jsNodes,
                        onNoteClick = onNoteClick,
                        onToggleTask = onToggleTask
                    )
                }
            }
        }
    }
}

@Composable
private fun EditableHeadingBlockCard(
    block: VaultBlock.HeadingBlock,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUpdate: (VaultBlock.HeadingBlock) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember(block.id) { mutableStateOf(false) }
    var draft by remember(block.text) { mutableStateOf(block.text) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            BlockHeaderRow(
                badge = "H${block.level}",
                badgeColor = MaterialTheme.colorScheme.onSurfaceVariant,
                icon = Icons.Rounded.Title,
                editing = editing,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onToggleEdit = { editing = !editing },
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onDelete = onDelete
            )
            if (editing) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { editing = false }) { Text("Cancel") }
                    Button(onClick = {
                        onUpdate(block.copy(text = draft))
                        editing = false
                    }) { Text("Save") }
                }
            } else {
                Text(
                    text = block.text,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EditableTaskBlockCard(
    block: VaultBlock.TaskBlock,
    note: VaultNote,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onUpdate: (VaultBlock.TaskBlock) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember(block.id) { mutableStateOf(false) }
    var draft by remember(block.text) { mutableStateOf(block.text) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            BlockHeaderRow(
                badge = "TASK",
                badgeColor = AmberProperty,
                icon = Icons.Rounded.TaskAlt,
                editing = editing,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onToggleEdit = { editing = !editing },
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onDelete = onDelete
            )
            if (editing) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { editing = false }) { Text("Cancel") }
                    Button(onClick = {
                        onUpdate(block.copy(text = draft))
                        editing = false
                    }) { Text("Save") }
                }
            } else {
                InteractiveTaskRow(
                    task = VaultTask(
                        id = block.id,
                        notePath = note.path,
                        noteTitle = note.title,
                        lineIndex = block.lineIndex,
                        text = block.text,
                        completed = block.completed,
                        dueDate = block.dueDate,
                        priority = block.priority
                    ),
                    onToggle = onToggle,
                    onOpenNote = {}
                )
            }
        }
    }
}

@Composable
private fun EditableCalloutBlockCard(
    block: VaultBlock.CalloutBlock,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUpdate: (VaultBlock.CalloutBlock) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember(block.id) { mutableStateOf(false) }
    var draftTitle by remember(block.title) { mutableStateOf(block.title) }
    var draftBody by remember(block.body) { mutableStateOf(block.body) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            BlockHeaderRow(
                badge = block.calloutType.uppercase(),
                badgeColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Rounded.Info,
                editing = editing,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onToggleEdit = { editing = !editing },
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onDelete = onDelete
            )
            if (editing) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = draftTitle,
                    onValueChange = { draftTitle = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = draftBody,
                    onValueChange = { draftBody = it },
                    label = { Text("Body") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { editing = false }) { Text("Cancel") }
                    Button(onClick = {
                        onUpdate(block.copy(title = draftTitle, body = draftBody))
                        editing = false
                    }) { Text("Save") }
                }
            } else {
                if (block.title.isNotBlank()) {
                    Text(
                        text = block.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (block.body.isNotBlank()) {
                    Text(
                        text = block.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditableParagraphBlockCard(
    block: VaultBlock.MarkdownParagraphBlock,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUpdate: (VaultBlock.MarkdownParagraphBlock) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember(block.id) { mutableStateOf(false) }
    var draft by remember(block.text) { mutableStateOf(block.text) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            BlockHeaderRow(
                badge = "NOTE BLOCK",
                badgeColor = MaterialTheme.colorScheme.onSurfaceVariant,
                icon = Icons.Rounded.Notes,
                editing = editing,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onToggleEdit = { editing = !editing },
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onDelete = onDelete
            )
            if (editing) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { editing = false }) { Text("Cancel") }
                    Button(onClick = {
                        onUpdate(block.copy(text = draft))
                        editing = false
                    }) { Text("Save") }
                }
            } else {
                Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun BlockHeaderRow(
    badge: String,
    badgeColor: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    editing: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    extraAction: (@Composable () -> Unit)? = null,
    onToggleEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = badgeColor.copy(alpha = 0.14f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            extraAction?.invoke()
            if (canMoveUp) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move block up", modifier = Modifier.size(16.dp))
                }
            }
            if (canMoveDown) {
                IconButton(onClick = onMoveDown, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move block down", modifier = Modifier.size(16.dp))
                }
            }
            IconButton(onClick = onToggleEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (editing) Icons.Rounded.Code else Icons.Rounded.Edit,
                    contentDescription = "Edit block",
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete block",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddBlockBar(
    onAddDataview: () -> Unit,
    onAddDataviewJs: () -> Unit,
    onAddTask: () -> Unit,
    onAddHeading: () -> Unit,
    onAddParagraph: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "INSERT BLOCK",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InsertBlockChip("Dataview", Icons.Rounded.TableChart, TealQuery, onAddDataview, "insert_dataview_block")
                InsertBlockChip("DataviewJS", Icons.Rounded.DataObject, MaterialTheme.colorScheme.primary, onAddDataviewJs, "insert_dataviewjs_block")
                InsertBlockChip("Task", Icons.Rounded.TaskAlt, AmberProperty, onAddTask, "insert_task_block")
                InsertBlockChip("Heading", Icons.Rounded.Title, MaterialTheme.colorScheme.onSurface, onAddHeading, "insert_heading_block")
                InsertBlockChip("Paragraph", Icons.Rounded.Notes, MaterialTheme.colorScheme.onSurfaceVariant, onAddParagraph, "insert_paragraph_block")
            }
        }
    }
}

@Composable
private fun InsertBlockChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
