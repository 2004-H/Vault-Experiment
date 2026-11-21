package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DataviewJsRenderNode
import com.example.data.model.DataviewResult
import com.example.data.model.NoteProperty
import com.example.data.model.VaultBlock
import com.example.data.model.VaultNote
import com.example.data.model.VaultTask
import com.example.domain.parser.MarkdownParser
import com.example.ui.components.AddBlockBar
import com.example.ui.components.AddPropertyDialog
import com.example.ui.components.BlockCardItem
import com.example.ui.components.PropertyEditBottomSheet
import com.example.ui.components.RapidPropertyGrid
import com.example.ui.theme.JetBrainsMonoFamily
import java.util.Collections

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BlockPageScreen(
    note: VaultNote?,
    allNotes: List<VaultNote>,
    jsCache: Map<String, List<DataviewJsRenderNode>>,
    jsCacheKeyProvider: (String, String, String) -> String,
    onEvaluateDql: (String, VaultNote?) -> DataviewResult,
    onRequestJsEval: (String, String, String, VaultNote?) -> Unit,
    onRefreshJsEval: (String, String, String, VaultNote?) -> Unit,
    onUpsertProperty: (String, String, NoteProperty) -> Unit,
    onRemoveProperty: (String, String) -> Unit,
    onSaveBlocks: (String, List<NoteProperty>, List<VaultBlock>) -> Unit,
    onToggleTaskInQuery: (VaultTask, Boolean) -> Unit,
    onToggleBlockTask: (VaultNote, VaultBlock.TaskBlock, Boolean) -> Unit,
    onTogglePin: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    if (note == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Block Page") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { pad ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading block page...", style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    var editingProperty by remember { mutableStateOf<NoteProperty?>(null) }
    var showAddProperty by remember { mutableStateOf(false) }
    var showRawMarkdown by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val backlinks = remember(note.path, allNotes) {
        allNotes.filter { other ->
            other.path != note.path && other.outlinks.any { link ->
                link.equals(note.title, ignoreCase = true) || link.equals(note.path.removeSuffix(".md"), ignoreCase = true)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = note.folder.ifBlank { "Root Vault" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("block_page_back")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onTogglePin(note.path) },
                        modifier = Modifier.testTag("block_page_pin")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pin page",
                            tint = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showRawMarkdown = true },
                        modifier = Modifier.testTag("block_page_raw_md")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = "Inspect raw Markdown"
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("block_page_delete")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete block page",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 780.dp)
            ) {
                item(key = "rapid_props") {
                    RapidPropertyGrid(
                        properties = note.properties,
                        onQuickEditProperty = { prop -> editingProperty = prop },
                        onToggleBooleanProperty = { prop ->
                            val flipped = (!prop.value.equals("true", true)).toString()
                            onUpsertProperty(note.path, prop.key, prop.copy(value = flipped))
                        },
                        onAddPropertyClick = { showAddProperty = true }
                    )
                }

                itemsIndexed(note.blocks, key = { _, block -> block.id }) { index, block ->
                    val jsNodes = if (block is VaultBlock.DataviewJsBlock) {
                        jsCache[jsCacheKeyProvider(note.path, block.id, block.script)]
                    } else null

                    BlockCardItem(
                        block = block,
                        note = note,
                        canMoveUp = index > 0,
                        canMoveDown = index < note.blocks.lastIndex,
                        evaluateDql = { q -> onEvaluateDql(q, note) },
                        jsNodes = jsNodes,
                        onRequestJsEval = { blockId, script ->
                            onRequestJsEval(note.path, blockId, script, note)
                        },
                        onRefreshJsEval = { blockId, script ->
                            onRefreshJsEval(note.path, blockId, script, note)
                        },
                        onUpdateBlock = { updated ->
                            val nextBlocks = note.blocks.map { if (it.id == updated.id) updated else it }
                            onSaveBlocks(note.path, note.properties, nextBlocks)
                        },
                        onMoveUp = {
                            if (index > 0) {
                                val nextBlocks = note.blocks.toMutableList()
                                Collections.swap(nextBlocks, index, index - 1)
                                onSaveBlocks(note.path, note.properties, nextBlocks)
                            }
                        },
                        onMoveDown = {
                            if (index < note.blocks.lastIndex) {
                                val nextBlocks = note.blocks.toMutableList()
                                Collections.swap(nextBlocks, index, index + 1)
                                onSaveBlocks(note.path, note.properties, nextBlocks)
                            }
                        },
                        onDelete = {
                            val nextBlocks = note.blocks.filterNot { it.id == block.id }
                            onSaveBlocks(note.path, note.properties, nextBlocks)
                        },
                        onNoteClick = onOpenNote,
                        onToggleTaskInQuery = onToggleTaskInQuery,
                        onToggleBlockTask = { b, done -> onToggleBlockTask(note, b, done) }
                    )
                }

                item(key = "add_block_bar") {
                    AddBlockBar(
                        onAddDataview = {
                            val next = note.blocks + VaultBlock.DataviewQueryBlock(
                                id = MarkdownParser.newBlockId("dv"),
                                query = "TABLE status AS \"Status\", priority AS \"Priority\"\nFROM #project"
                            )
                            onSaveBlocks(note.path, note.properties, next)
                        },
                        onAddDataviewJs = {
                            val next = note.blocks + VaultBlock.DataviewJsBlock(
                                id = MarkdownParser.newBlockId("dvjs"),
                                script = "const cur = dv.current();\ndv.paragraph(\"Block page: \" + (cur ? cur.file.name : \"Vault\"));"
                            )
                            onSaveBlocks(note.path, note.properties, next)
                        },
                        onAddTask = {
                            val next = note.blocks + VaultBlock.TaskBlock(
                                id = MarkdownParser.newBlockId("task"),
                                text = "New action item [priority:: medium]",
                                completed = false,
                                lineIndex = note.blocks.size,
                                priority = "medium"
                            )
                            onSaveBlocks(note.path, note.properties, next)
                        },
                        onAddHeading = {
                            val next = note.blocks + VaultBlock.HeadingBlock(
                                id = MarkdownParser.newBlockId("hd"),
                                level = 2,
                                text = "Section Title"
                            )
                            onSaveBlocks(note.path, note.properties, next)
                        },
                        onAddParagraph = {
                            val next = note.blocks + VaultBlock.MarkdownParagraphBlock(
                                id = MarkdownParser.newBlockId("p"),
                                text = "Write a concise evergreen note block..."
                            )
                            onSaveBlocks(note.path, note.properties, next)
                        }
                    )
                }

                if (backlinks.isNotEmpty() || note.outlinks.isNotEmpty()) {
                    item(key = "links_footer") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Link,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "CONNECTED BLOCKS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (backlinks.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "Backlinks (${backlinks.size})",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        backlinks.forEach { linkNote ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { onOpenNote(linkNote.path) }
                                            ) {
                                                Text(
                                                    text = "[[${linkNote.title}]]",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                if (note.outlinks.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "Outlinks (${note.outlinks.size})",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        note.outlinks.forEach { out ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { onOpenNote(out) }
                                            ) {
                                                Text(
                                                    text = "[[$out]]",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingProperty?.let { prop ->
        PropertyEditBottomSheet(
            property = prop,
            onDismiss = { editingProperty = null },
            onSave = { updated ->
                onUpsertProperty(note.path, prop.key, updated)
                editingProperty = null
            },
            onDelete = {
                onRemoveProperty(note.path, prop.key)
                editingProperty = null
            }
        )
    }

    if (showAddProperty) {
        AddPropertyDialog(
            onDismiss = { showAddProperty = false },
            onAdd = { newProp ->
                onUpsertProperty(note.path, newProp.key, newProp)
                showAddProperty = false
            }
        )
    }

    if (showRawMarkdown) {
        ModalBottomSheet(onDismissRequest = { showRawMarkdown = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = note.path,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    Text(
                        text = note.rawMarkdown,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"${note.title}\"?") },
            text = { Text("This deletes the markdown file from your synced vault.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteNote(note.path)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
