package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.VaultBlock
import com.example.data.model.VaultNote
import com.example.data.model.VaultTask
import com.example.domain.parser.MarkdownParser
import com.example.ui.components.InteractiveTaskRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class TaskFilter(val label: String) {
    OPEN("Open"),
    HIGH("High Priority"),
    DUE("With Due Date"),
    COMPLETED("Completed"),
    ALL("All")
}

@Composable
fun TasksScreen(
    tasks: List<VaultTask>,
    notes: List<VaultNote>,
    onToggleTask: (VaultTask, Boolean) -> Unit,
    onOpenNote: (String) -> Unit,
    onAddTaskToNote: (VaultNote, VaultBlock.TaskBlock) -> Unit,
    modifier: Modifier = Modifier
) {
    var filter by remember { mutableStateOf(TaskFilter.OPEN) }
    var showAddTask by remember { mutableStateOf(false) }

    val filteredTasks = remember(tasks, filter) {
        when (filter) {
            TaskFilter.OPEN -> tasks.filter { !it.completed }
            TaskFilter.HIGH -> tasks.filter { !it.completed && it.priority.equals("high", true) }
            TaskFilter.DUE -> tasks.filter { !it.completed && !it.dueDate.isNullOrBlank() }
            TaskFilter.COMPLETED -> tasks.filter { it.completed }
            TaskFilter.ALL -> tasks
        }
    }

    val grouped = remember(filteredTasks) {
        filteredTasks.groupBy { it.noteTitle }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (notes.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showAddTask = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_add_task")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add task")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 780.dp)
            ) {
                item {
                    Column {
                        Text(
                            text = "Vault Tasks",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${tasks.count { !it.completed }} open · ${tasks.count { it.completed }} completed across ${notes.size} notes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TaskFilter.entries.forEach { item ->
                                FilterChip(
                                    selected = filter == item,
                                    onClick = { filter = item },
                                    label = { Text(item.label) }
                                )
                            }
                        }
                    }
                }

                if (grouped.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.TaskAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(34.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("No tasks in this filter", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Tap + to append a Dataview-tracked task directly to a vault note.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(grouped.entries.toList(), key = { it.key }) { (noteTitle, noteTasks) ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = noteTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                noteTasks.forEach { task ->
                                    InteractiveTaskRow(
                                        task = task,
                                        onToggle = { onToggleTask(task, !task.completed) },
                                        onOpenNote = { onOpenNote(task.notePath) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddTask && notes.isNotEmpty()) {
        AddTaskDialog(
            notes = notes,
            onDismiss = { showAddTask = false },
            onAdd = { note, text, priority, due ->
                val suffix = buildString {
                    if (due.isNotBlank()) append(" [due:: $due]")
                    if (priority.isNotBlank()) append(" [priority:: $priority]")
                }
                val newBlock = VaultBlock.TaskBlock(
                    id = MarkdownParser.newBlockId("task"),
                    text = "$text$suffix",
                    completed = false,
                    lineIndex = note.blocks.size,
                    dueDate = due.ifBlank { null },
                    priority = priority.ifBlank { null }
                )
                onAddTaskToNote(note, newBlock)
                showAddTask = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskDialog(
    notes: List<VaultNote>,
    onDismiss: () -> Unit,
    onAdd: (VaultNote, String, String, String) -> Unit
) {
    var selectedNote by remember { mutableStateOf(notes.first()) }
    var expanded by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("medium") }
    var due by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Vault Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedNote.title,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Note") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        notes.forEach { n ->
                            DropdownMenuItem(
                                text = { Text(n.title) },
                                onClick = {
                                    selectedNote = n
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Task description") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("low", "medium", "high").forEach { pri ->
                        FilterChip(
                            selected = priority == pri,
                            onClick = { priority = pri },
                            label = { Text(pri) }
                        )
                    }
                }
                OutlinedTextField(
                    value = due,
                    onValueChange = { due = it },
                    label = { Text("Due date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onAdd(selectedNote, text.trim(), priority, due.trim())
                    }
                }
            ) { Text("Add Task") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
