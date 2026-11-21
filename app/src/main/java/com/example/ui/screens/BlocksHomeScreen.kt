package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.VaultNote
import com.example.ui.HomeFilter
import com.example.ui.VaultUiState
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberProperty
import com.example.ui.theme.EmeraldDone
import com.example.ui.theme.TealQuery

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlocksHomeScreen(
    state: VaultUiState,
    onSearchChange: (String) -> Unit,
    onFilterChange: (HomeFilter) -> Unit,
    onTagFilterClick: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onSyncNow: () -> Unit,
    onCreateBlockPage: (title: String, folder: String, template: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    val filteredNotes = remember(
        state.notes,
        state.searchQuery,
        state.activeFilter,
        state.selectedTagFilter
    ) {
        val q = state.searchQuery.trim().lowercase()
        state.notes.filter { note ->
            val matchesFilter = when (state.activeFilter) {
                HomeFilter.ALL -> true
                HomeFilter.PINNED -> note.isPinned
                HomeFilter.DATAVIEW -> note.hasDataview
                HomeFilter.DATAVIEW_JS -> note.hasDataviewJs
                HomeFilter.PROJECTS -> note.tags.any { it.equals("project", true) } ||
                    note.folder.contains("project", ignoreCase = true)
            }
            val matchesTag = state.selectedTagFilter == null ||
                note.tags.any { it.equals(state.selectedTagFilter, ignoreCase = true) }

            val matchesSearch = if (q.isEmpty()) true
            else if (q.contains(":")) {
                val k = q.substringBefore(":").trim()
                val v = q.substringAfter(":").trim()
                note.properties.any {
                    it.key.lowercase().contains(k) && it.value.lowercase().contains(v)
                }
            } else {
                note.title.lowercase().contains(q) ||
                    note.folder.lowercase().contains(q) ||
                    note.tags.any { it.lowercase().contains(q.removePrefix("#")) } ||
                    note.properties.any {
                        it.key.lowercase().contains(q) || it.value.lowercase().contains(q)
                    }
            }
            matchesFilter && matchesTag && matchesSearch
        }
    }

    val topTags = remember(state.notes) {
        state.notes.flatMap { it.tags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(8)
            .map { it.key }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_new_block_page)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_new_block_page")
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val columns = if (maxWidth >= 680.dp) 2 else 1

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(columns) }) {
                    Column {
                        // Top Vault Status Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "VaultBlock",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = EmeraldDone.copy(alpha = 0.15f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.OfflinePin,
                                                contentDescription = null,
                                                tint = EmeraldDone,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = if (state.syncStatus.isSafLinked) "SAF Synced" else "Local Offline",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EmeraldDone,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${state.syncStatus.vaultName} · ${state.syncStatus.noteCount} block pages · ${state.syncStatus.dataviewBlockCount + state.syncStatus.dataviewJsBlockCount} live queries",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = onSyncNow,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("home_sync_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Sync,
                                    contentDescription = stringResource(R.string.action_sync_now),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (state.syncStatus.isSyncing) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        Spacer(Modifier.height(14.dp))

                        // Search Bar
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = onSearchChange,
                            placeholder = { Text("Search blocks, #tags, or status:active") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchChange("") }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_blocks_input")
                        )

                        Spacer(Modifier.height(10.dp))

                        // Primary Filter Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val filterDefs = listOf(
                                HomeFilter.ALL to "All Pages",
                                HomeFilter.PINNED to "Pinned",
                                HomeFilter.DATAVIEW to "Dataview",
                                HomeFilter.DATAVIEW_JS to "DataviewJS",
                                HomeFilter.PROJECTS to "Projects"
                            )
                            filterDefs.forEach { (filter, label) ->
                                FilterChip(
                                    selected = state.activeFilter == filter,
                                    onClick = { onFilterChange(filter) },
                                    label = { Text(label) }
                                )
                            }
                        }

                        if (topTags.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                topTags.forEach { tag ->
                                    val selected = state.selectedTagFilter == tag
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerLow,
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) TealQuery else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onTagFilterClick(tag) }
                                    ) {
                                        Text(
                                            text = "#$tag",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (filteredNotes.isEmpty()) {
                    item(span = { GridItemSpan(columns) }) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.empty_search_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.empty_search_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredNotes, key = { it.path }) { note ->
                        BlockNoteSummaryCard(
                            note = note,
                            onClick = { onOpenNote(note.path) },
                            onTogglePin = { onTogglePin(note.path) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateBlockPageDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, folder, template ->
                showCreateDialog = false
                onCreateBlockPage(title, folder, template)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockNoteSummaryCard(
    note: VaultNote,
    onClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    val dvCount = remember(note.blocks) { note.blocks.count { it is com.example.data.model.VaultBlock.DataviewQueryBlock } }
    val jsCount = remember(note.blocks) { note.blocks.count { it is com.example.data.model.VaultBlock.DataviewJsBlock } }
    val openTasks = remember(note.tasks) { note.tasks.count { !it.completed } }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (note.isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("note_card_${note.title}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = note.folder.ifBlank { "Root Vault" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("pin_note_${note.title}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = if (note.isPinned) "Unpin page" else "Pin page",
                        tint = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Text(
                text = note.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            // Rapid Metadata preview chips
            val previewProps = note.properties.filter {
                !it.key.equals("tags", true) && !it.key.equals("pinned", true)
            }.take(4)

            if (previewProps.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    previewProps.forEach { prop ->
                        if (prop.key.equals("status", true)) {
                            StatusPill(status = prop.value)
                        } else {
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Text(
                                    text = "${prop.key}: ${prop.value}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Block composition footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dvCount > 0) {
                    BlockTypeBadge(
                        icon = Icons.Rounded.TableChart,
                        label = "$dvCount DQL",
                        color = TealQuery
                    )
                }
                if (jsCount > 0) {
                    BlockTypeBadge(
                        icon = Icons.Rounded.DataObject,
                        label = "$jsCount JS",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (openTasks > 0) {
                    BlockTypeBadge(
                        icon = Icons.Rounded.TaskAlt,
                        label = "$openTasks tasks",
                        color = AmberProperty
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${note.blocks.size} blocks",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BlockTypeBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateBlockPageDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, folder: String, template: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("Projects") }
    var template by remember { mutableStateOf("dataview") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Dataview Block Page") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Page Title") },
                    placeholder = { Text("Sprint Tracker") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    label = { Text("Vault Folder") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Initial Block Template", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "dataview" to "Dataview Table",
                        "dataviewjs" to "DataviewJS Script",
                        "note" to "Metadata + Tasks"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = template == key,
                            onClick = { template = key },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(title.ifBlank { "Quick Block" }, folder, template)
                }
            ) {
                Text("Create Page")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
