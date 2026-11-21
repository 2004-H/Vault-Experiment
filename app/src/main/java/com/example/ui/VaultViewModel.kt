package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.VaultDatabase
import com.example.data.local.VaultPreferences
import com.example.data.local.VaultSettings
import com.example.data.model.DataviewJsRenderNode
import com.example.data.model.DataviewResult
import com.example.data.model.NoteProperty
import com.example.data.model.PropertySource
import com.example.data.model.PropertyType
import com.example.data.model.SyncLogItem
import com.example.data.model.SyncStatusSummary
import com.example.data.model.VaultBlock
import com.example.data.model.VaultNote
import com.example.data.model.VaultTask
import com.example.data.sync.LocalVaultProvider
import com.example.data.sync.VaultRepository
import com.example.domain.engine.DataviewJsEngine
import com.example.domain.engine.DataviewQueryEngine
import com.example.domain.parser.MarkdownParser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HomeFilter {
    ALL,
    PINNED,
    DATAVIEW,
    DATAVIEW_JS,
    PROJECTS
}

enum class QueryLabMode {
    DQL,
    DATAVIEW_JS
}

data class VaultUiState(
    val notes: List<VaultNote> = emptyList(),
    val tasks: List<VaultTask> = emptyList(),
    val syncLogs: List<SyncLogItem> = emptyList(),
    val settings: VaultSettings = VaultSettings(),
    val syncStatus: SyncStatusSummary = SyncStatusSummary(
        vaultName = "Local Vault",
        isSafLinked = false,
        linkedTreeUri = null,
        lastSyncEpochMs = 0L,
        isSyncing = false,
        noteCount = 0,
        pendingPushCount = 0,
        dataviewBlockCount = 0,
        dataviewJsBlockCount = 0
    ),
    val searchQuery: String = "",
    val activeFilter: HomeFilter = HomeFilter.ALL,
    val selectedTagFilter: String? = null
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val database = VaultDatabase.getInstance(application)
    private val preferences = VaultPreferences(application)
    private val fileProvider = LocalVaultProvider(application)
    private val repository = VaultRepository(database.vaultDao(), preferences, fileProvider)

    private val dqlEngine = DataviewQueryEngine()
    private val jsEngine = DataviewJsEngine(application)

    private val _searchQuery = MutableStateFlow("")
    private val _activeFilter = MutableStateFlow(HomeFilter.ALL)
    private val _selectedTagFilter = MutableStateFlow<String?>(null)
    private val _isSyncing = MutableStateFlow(false)

    private val _jsCache = MutableStateFlow<Map<String, List<DataviewJsRenderNode>>>(emptyMap())
    val jsCache: StateFlow<Map<String, List<DataviewJsRenderNode>>> = _jsCache.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    // Query Lab State
    private val _labMode = MutableStateFlow(QueryLabMode.DQL)
    val labMode: StateFlow<QueryLabMode> = _labMode.asStateFlow()

    private val _labDqlQuery = MutableStateFlow(
        """
        TABLE status AS "Status", priority AS "Priority", rating AS "Rating", due AS "Due"
        FROM #project OR #book
        SORT rating DESC
        """.trimIndent()
    )
    val labDqlQuery: StateFlow<String> = _labDqlQuery.asStateFlow()

    private val _labJsScript = MutableStateFlow(
        """
        const items = dv.pages("#project")
          .sort(p => p.progress || 0, "desc");
        dv.header(3, "Project Velocity (" + items.length + ")");
        dv.table(
          ["Project", "Status", "Progress"],
          items.map(p => [p.file.link, p.status || "—", (p.progress || 0) + "%"])
        );
        """.trimIndent()
    )
    val labJsScript: StateFlow<String> = _labJsScript.asStateFlow()

    private val _labDqlResult = MutableStateFlow<DataviewResult?>(null)
    val labDqlResult: StateFlow<DataviewResult?> = _labDqlResult.asStateFlow()

    private val _labJsNodes = MutableStateFlow<List<DataviewJsRenderNode>>(emptyList())
    val labJsNodes: StateFlow<List<DataviewJsRenderNode>> = _labJsNodes.asStateFlow()

    private val _labRunning = MutableStateFlow(false)
    val labRunning: StateFlow<Boolean> = _labRunning.asStateFlow()

    val uiState: StateFlow<VaultUiState> = combine(
        repository.notesFlow,
        repository.tasksFlow,
        repository.syncLogsFlow,
        repository.settingsFlow,
        combine(_searchQuery, _activeFilter, _selectedTagFilter, _isSyncing) { q, f, t, s ->
            Quad(q, f, t, s)
        }
    ) { notes, tasks, logs, settings, uiParams ->
        val dvCount = notes.sumOf { n -> n.blocks.count { it is VaultBlock.DataviewQueryBlock } }
        val dvJsCount = notes.sumOf { n -> n.blocks.count { it is VaultBlock.DataviewJsBlock } }
        val pendingPush = notes.count { it.dirtySync }

        VaultUiState(
            notes = notes,
            tasks = tasks,
            syncLogs = logs,
            settings = settings,
            syncStatus = SyncStatusSummary(
                vaultName = settings.vaultName,
                isSafLinked = !settings.linkedTreeUri.isNullOrBlank(),
                linkedTreeUri = settings.linkedTreeUri,
                lastSyncEpochMs = settings.lastSyncEpochMs,
                isSyncing = uiParams.fourth,
                noteCount = notes.size,
                pendingPushCount = pendingPush,
                dataviewBlockCount = dvCount,
                dataviewJsBlockCount = dvJsCount
            ),
            searchQuery = uiParams.first,
            activeFilter = uiParams.second,
            selectedTagFilter = uiParams.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VaultUiState()
    )

    init {
        viewModelScope.launch {
            repository.initializeVaultOnLaunch()
        }
        viewModelScope.launch {
            repository.notesFlow.collect { notes ->
                if (notes.isNotEmpty()) {
                    runQueryLab()
                }
            }
        }
    }

    fun observeNote(path: String) = repository.observeNote(path)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: HomeFilter) {
        _activeFilter.value = filter
    }

    fun setTagFilter(tag: String?) {
        _selectedTagFilter.value = if (_selectedTagFilter.value == tag) null else tag
    }

    fun evaluateDataview(query: String, currentNote: VaultNote?): DataviewResult {
        return dqlEngine.execute(query, uiState.value.notes, currentNote)
    }

    fun jsCacheKey(notePath: String, blockId: String, script: String): String {
        val vaultSig = uiState.value.notes.sumOf { it.lastModified }
        return "$notePath::$blockId::${script.hashCode()}::$vaultSig"
    }

    fun evaluateDataviewJsBlock(notePath: String, blockId: String, script: String, currentNote: VaultNote?) {
        val key = jsCacheKey(notePath, blockId, script)
        if (_jsCache.value.containsKey(key)) return
        viewModelScope.launch {
            val nodes = jsEngine.execute(script, uiState.value.notes, currentNote)
            _jsCache.value = _jsCache.value + (key to nodes)
        }
    }

    fun forceRefreshDataviewJsBlock(notePath: String, blockId: String, script: String, currentNote: VaultNote?) {
        val key = jsCacheKey(notePath, blockId, script)
        viewModelScope.launch {
            val nodes = jsEngine.execute(script, uiState.value.notes, currentNote)
            _jsCache.value = _jsCache.value + (key to nodes)
        }
    }

    fun setLabMode(mode: QueryLabMode) {
        _labMode.value = mode
        runQueryLab()
    }

    fun setLabDqlQuery(query: String) {
        _labDqlQuery.value = query
    }

    fun setLabJsScript(script: String) {
        _labJsScript.value = script
    }

    fun applyLabPreset(mode: QueryLabMode, code: String) {
        _labMode.value = mode
        if (mode == QueryLabMode.DQL) _labDqlQuery.value = code else _labJsScript.value = code
        runQueryLab()
    }

    fun runQueryLab() {
        val notes = uiState.value.notes
        if (_labMode.value == QueryLabMode.DQL) {
            _labDqlResult.value = dqlEngine.execute(_labDqlQuery.value, notes, null)
        } else {
            viewModelScope.launch {
                _labRunning.value = true
                _labJsNodes.value = jsEngine.execute(_labJsScript.value, notes, null)
                _labRunning.value = false
            }
        }
    }

    fun saveLabAsBlockPage(title: String, folder: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val props = listOf(
                NoteProperty("status", "active", PropertyType.STATUS),
                NoteProperty("pinned", "true", PropertyType.BOOLEAN),
                NoteProperty("tags", "dashboard, dataview", PropertyType.LIST, listItems = listOf("dashboard", "dataview"))
            )
            val queryBlock = if (_labMode.value == QueryLabMode.DQL) {
                VaultBlock.DataviewQueryBlock(
                    id = MarkdownParser.newBlockId("dv"),
                    query = _labDqlQuery.value
                )
            } else {
                VaultBlock.DataviewJsBlock(
                    id = MarkdownParser.newBlockId("dvjs"),
                    script = _labJsScript.value
                )
            }
            val blocks = listOf(
                VaultBlock.HeadingBlock(MarkdownParser.newBlockId("hd"), 1, title.ifBlank { "Saved Query" }),
                queryBlock
            )
            val createdPath = repository.createBlockPage(title, folder, props, blocks)
            _snackbarEvents.tryEmit("Saved block page \"$title\"")
            onCreated(createdPath)
        }
    }

    fun upsertProperty(notePath: String, key: String, property: NoteProperty) {
        viewModelScope.launch {
            repository.upsertProperty(notePath, key, property)
            _snackbarEvents.tryEmit("Updated $key")
        }
    }

    fun removeProperty(notePath: String, key: String) {
        viewModelScope.launch {
            repository.removeProperty(notePath, key)
            _snackbarEvents.tryEmit("Removed $key")
        }
    }

    fun toggleTask(task: VaultTask, completed: Boolean) {
        viewModelScope.launch {
            repository.toggleTask(task, completed)
        }
    }

    fun toggleBlockTask(note: VaultNote, block: VaultBlock.TaskBlock, completed: Boolean) {
        viewModelScope.launch {
            val matching = note.tasks.firstOrNull { it.lineIndex == block.lineIndex }
            if (matching != null) {
                repository.toggleTask(matching, completed)
            } else {
                val updatedBlocks = note.blocks.map {
                    if (it.id == block.id) block.copy(completed = completed) else it
                }
                repository.updateBlocks(note.path, note.properties, updatedBlocks)
            }
        }
    }

    fun saveNoteBlocks(notePath: String, properties: List<NoteProperty>, blocks: List<VaultBlock>) {
        viewModelScope.launch {
            repository.updateBlocks(notePath, properties, blocks)
        }
    }

    fun togglePinned(notePath: String) {
        viewModelScope.launch {
            repository.togglePinned(notePath)
        }
    }

    fun createBlockPage(
        title: String,
        folder: String,
        initialType: String,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            val props = mutableListOf(
                NoteProperty("status", "active", PropertyType.STATUS),
                NoteProperty("priority", "medium", PropertyType.TEXT),
                NoteProperty("tags", "block", PropertyType.LIST, listItems = listOf("block"))
            )
            val blocks = mutableListOf<VaultBlock>(
                VaultBlock.HeadingBlock(MarkdownParser.newBlockId("hd"), 1, title.ifBlank { "New Block Page" })
            )
            when (initialType) {
                "dataview" -> blocks.add(
                    VaultBlock.DataviewQueryBlock(
                        id = MarkdownParser.newBlockId("dv"),
                        query = "TABLE status AS \"Status\", priority AS \"Priority\"\nFROM #project\nSORT file.name ASC"
                    )
                )
                "dataviewjs" -> blocks.add(
                    VaultBlock.DataviewJsBlock(
                        id = MarkdownParser.newBlockId("dvjs"),
                        script = "const pages = dv.pages();\ndv.paragraph(\"Indexed \" + pages.length + \" vault pages.\");"
                    )
                )
                else -> {
                    blocks.add(
                        VaultBlock.MarkdownParagraphBlock(
                            id = MarkdownParser.newBlockId("p"),
                            text = "Add metadata properties above or insert Dataview & Task blocks below."
                        )
                    )
                    blocks.add(
                        VaultBlock.TaskBlock(
                            id = MarkdownParser.newBlockId("t"),
                            text = "Configure block metadata [priority:: medium]",
                            completed = false,
                            lineIndex = 0,
                            priority = "medium"
                        )
                    )
                }
            }
            val path = repository.createBlockPage(title, folder, props, blocks)
            _snackbarEvents.tryEmit("Created $path")
            onCreated(path)
        }
    }

    fun deleteNote(notePath: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteNote(notePath)
            _snackbarEvents.tryEmit("Deleted $notePath")
            onDeleted()
        }
    }

    fun syncVaultNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            val res = repository.syncVault()
            _isSyncing.value = false
            res.onSuccess { count ->
                _snackbarEvents.tryEmit("Synced $count markdown notes")
            }.onFailure { err ->
                _snackbarEvents.tryEmit("Sync error: ${err.message}")
            }
        }
    }

    fun linkSafVault(treeUri: String, folderName: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            repository.linkSafVault(treeUri, folderName)
            _isSyncing.value = false
            _snackbarEvents.tryEmit("Linked vault folder: $folderName")
        }
    }

    fun switchToInternalVault() {
        viewModelScope.launch {
            _isSyncing.value = true
            repository.switchToInternalVault()
            _isSyncing.value = false
            _snackbarEvents.tryEmit("Switched to On-Device Local Vault")
        }
    }

    fun resetStarterVault() {
        viewModelScope.launch {
            _isSyncing.value = true
            repository.resetStarterVault()
            _isSyncing.value = false
            _snackbarEvents.tryEmit("Restored starter Obsidian vault")
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { repository.setDarkMode(enabled) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColor(enabled) }
    }

    fun resolveNotePath(target: String): String? {
        val clean = target.trim().removeSuffix(".md")
        val notes = uiState.value.notes
        return notes.firstOrNull {
            it.path.equals(target, ignoreCase = true) ||
                it.path.removeSuffix(".md").equals(clean, ignoreCase = true) ||
                it.title.equals(clean, ignoreCase = true)
        }?.path
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
