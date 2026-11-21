package com.example.data.sync

import com.example.data.local.SyncLogEntity
import com.example.data.local.VaultDao
import com.example.data.local.VaultEntityMapper
import com.example.data.local.VaultPreferences
import com.example.data.local.VaultSettings
import com.example.data.model.NoteProperty
import com.example.data.model.SyncLogItem
import com.example.data.model.VaultBlock
import com.example.data.model.VaultNote
import com.example.data.model.VaultTask
import com.example.domain.parser.MarkdownParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class VaultRepository(
    private val dao: VaultDao,
    private val preferences: VaultPreferences,
    private val fileProvider: LocalVaultProvider
) {

    val settingsFlow: Flow<VaultSettings> = preferences.settingsFlow

    val notesFlow: Flow<List<VaultNote>> = combine(
        dao.observeAllNotes(),
        dao.observeAllTasks()
    ) { noteEntities, taskEntities ->
        val tasksByPath = taskEntities.map { VaultEntityMapper.toDomainTask(it) }.groupBy { it.notePath }
        noteEntities.map { entity ->
            VaultEntityMapper.toDomain(entity, tasksByPath[entity.path].orEmpty())
        }
    }

    val tasksFlow: Flow<List<VaultTask>> = dao.observeAllTasks().map { list ->
        list.map { VaultEntityMapper.toDomainTask(it) }
    }

    val syncLogsFlow: Flow<List<SyncLogItem>> = dao.observeSyncLogs().map { list ->
        list.map { VaultEntityMapper.toDomainLog(it) }
    }

    fun observeNote(path: String): Flow<VaultNote?> = combine(
        dao.observeNoteByPath(path),
        dao.observeTasksForNote(path)
    ) { entity, taskEntities ->
        entity?.let {
            VaultEntityMapper.toDomain(it, taskEntities.map { t -> VaultEntityMapper.toDomainTask(t) })
        }
    }

    suspend fun initializeVaultOnLaunch() = withContext(Dispatchers.IO) {
        val settings = preferences.settingsFlow.first()
        if (settings.linkedTreeUri.isNullOrBlank() && !settings.starterVaultSeeded) {
            fileProvider.seedStarterVaultIfNeeded()
            preferences.markStarterSeeded()
        }
        val existingNotes = dao.getAllNotesSnapshot()
        if (existingNotes.isEmpty()) {
            syncVault()
        }
    }

    suspend fun syncVault(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = preferences.settingsFlow.first()
            val treeUri = settings.linkedTreeUri
            val existingCached = dao.getAllNotesSnapshot().associateBy { it.path }

            // 1. Push any offline dirty edits first
            var pushedCount = 0
            existingCached.values.filter { it.dirtySync }.forEach { dirty ->
                fileProvider.writeFile(treeUri, dirty.path, dirty.rawMarkdown)
                pushedCount++
            }

            // 2. Scan disk/SAF markdown files
            val diskFiles = fileProvider.readAllFiles(treeUri)
            val parsedNotes = diskFiles.map { entry ->
                val cached = existingCached[entry.path]
                val note = MarkdownParser.parseNote(
                    path = entry.path,
                    rawMarkdown = entry.content,
                    lastModified = entry.lastModified,
                    isPinned = cached?.isPinned ?: false,
                    dirtySync = false
                )
                val hash = MarkdownParser.hashContent(entry.content)
                VaultEntityMapper.toEntity(note, hash) to note.tasks.map { VaultEntityMapper.toTaskEntity(it) }
            }

            val noteEntities = parsedNotes.map { it.first }
            val taskEntities = parsedNotes.flatMap { it.second }
            dao.replaceVaultSnapshot(noteEntities, taskEntities)

            val now = System.currentTimeMillis()
            preferences.setLastSync(now)
            dao.insertSyncLog(
                SyncLogEntity(
                    timestamp = now,
                    action = if (treeUri == null) "Local Vault Sync" else "SAF Folder Sync",
                    detail = "Indexed ${noteEntities.size} markdown notes, ${taskEntities.size} tasks" +
                        if (pushedCount > 0) " (pushed $pushedCount offline edits)" else "",
                    isSuccess = true
                )
            )
            noteEntities.size
        }.onFailure { err ->
            dao.insertSyncLog(
                SyncLogEntity(
                    timestamp = System.currentTimeMillis(),
                    action = "Sync Failed",
                    detail = err.message ?: "Unable to access vault directory",
                    isSuccess = false
                )
            )
        }
    }

    suspend fun linkSafVault(treeUri: String, folderName: String) = withContext(Dispatchers.IO) {
        preferences.setLinkedVault(treeUri, folderName)
        dao.insertSyncLog(
            SyncLogEntity(
                timestamp = System.currentTimeMillis(),
                action = "Linked Vault Folder",
                detail = "Connected SAF vault \"$folderName\"",
                isSuccess = true
            )
        )
        syncVault()
    }

    suspend fun switchToInternalVault() = withContext(Dispatchers.IO) {
        fileProvider.seedStarterVaultIfNeeded()
        preferences.setLinkedVault(null, "Local Vault")
        syncVault()
    }

    suspend fun resetStarterVault() = withContext(Dispatchers.IO) {
        fileProvider.resetStarterVault()
        preferences.setLinkedVault(null, "Local Vault")
        syncVault()
    }

    suspend fun upsertProperty(notePath: String, key: String, property: NoteProperty) = withContext(Dispatchers.IO) {
        val current = dao.getNoteByPath(notePath) ?: return@withContext
        val updatedRaw = MarkdownParser.updatePropertyInMarkdown(current.rawMarkdown, key, property)
        persistMarkdownChange(notePath, updatedRaw, current.isPinned, "Property Updated", "$notePath → $key = ${property.value}")
    }

    suspend fun removeProperty(notePath: String, key: String) = withContext(Dispatchers.IO) {
        val current = dao.getNoteByPath(notePath) ?: return@withContext
        val updatedRaw = MarkdownParser.removePropertyInMarkdown(current.rawMarkdown, key)
        persistMarkdownChange(notePath, updatedRaw, current.isPinned, "Property Removed", "$notePath → removed $key")
    }

    suspend fun toggleTask(task: VaultTask, completed: Boolean) = withContext(Dispatchers.IO) {
        val current = dao.getNoteByPath(task.notePath) ?: return@withContext
        val updatedRaw = MarkdownParser.toggleTaskInMarkdown(current.rawMarkdown, task.lineIndex, completed)
        persistMarkdownChange(
            task.notePath,
            updatedRaw,
            current.isPinned,
            "Task Toggled",
            "${task.noteTitle}: ${if (completed) "completed" else "reopened"}"
        )
    }

    suspend fun updateBlocks(
        notePath: String,
        properties: List<NoteProperty>,
        blocks: List<VaultBlock>
    ) = withContext(Dispatchers.IO) {
        val current = dao.getNoteByPath(notePath) ?: return@withContext
        val updatedRaw = MarkdownParser.rebuildMarkdownFromBlocks(properties, blocks)
        persistMarkdownChange(notePath, updatedRaw, current.isPinned, "Blocks Saved", "$notePath (${blocks.size} blocks)")
    }

    suspend fun togglePinned(notePath: String) = withContext(Dispatchers.IO) {
        val current = dao.getNoteByPath(notePath) ?: return@withContext
        val newPinned = !current.isPinned
        val parsed = MarkdownParser.parseNote(current.path, current.rawMarkdown, current.lastModified, isPinned = newPinned)
        val pinnedProp = NoteProperty(
            key = "pinned",
            value = newPinned.toString(),
            type = com.example.data.model.PropertyType.BOOLEAN
        )
        val updatedRaw = MarkdownParser.updatePropertyInMarkdown(parsed.rawMarkdown, "pinned", pinnedProp)
        persistMarkdownChange(notePath, updatedRaw, newPinned, "Pin Toggled", "$notePath → pinned=$newPinned")
    }

    suspend fun createBlockPage(
        title: String,
        folder: String,
        properties: List<NoteProperty>,
        blocks: List<VaultBlock>
    ): String = withContext(Dispatchers.IO) {
        val safeTitle = title.trim().ifBlank { "Untitled Block" }.replace("/", "-")
        val cleanFolder = folder.trim().trim('/')
        val path = if (cleanFolder.isEmpty()) "$safeTitle.md" else "$cleanFolder/$safeTitle.md"
        val raw = MarkdownParser.rebuildMarkdownFromBlocks(properties, blocks)
        persistMarkdownChange(path, raw, isPinned = false, logAction = "Created Block Page", logDetail = path)
        path
    }

    suspend fun deleteNote(notePath: String) = withContext(Dispatchers.IO) {
        val settings = preferences.settingsFlow.first()
        runCatching { fileProvider.deleteFile(settings.linkedTreeUri, notePath) }
        dao.deleteTasksForNote(notePath)
        dao.deleteNoteByPath(notePath)
        dao.insertSyncLog(
            SyncLogEntity(
                timestamp = System.currentTimeMillis(),
                action = "Deleted Note",
                detail = notePath,
                isSuccess = true
            )
        )
    }

    suspend fun setDarkMode(enabled: Boolean) = preferences.setDarkMode(enabled)
    suspend fun setDynamicColor(enabled: Boolean) = preferences.setDynamicColor(enabled)

    private suspend fun persistMarkdownChange(
        notePath: String,
        updatedRaw: String,
        isPinned: Boolean,
        logAction: String,
        logDetail: String
    ) {
        val settings = preferences.settingsFlow.first()
        var dirty = false
        val modifiedAt = try {
            fileProvider.writeFile(settings.linkedTreeUri, notePath, updatedRaw)
        } catch (_: Exception) {
            dirty = true
            System.currentTimeMillis()
        }

        val parsed = MarkdownParser.parseNote(
            path = notePath,
            rawMarkdown = updatedRaw,
            lastModified = modifiedAt,
            isPinned = isPinned,
            dirtySync = dirty
        )
        val hash = MarkdownParser.hashContent(updatedRaw)
        dao.replaceNoteAndTasks(
            VaultEntityMapper.toEntity(parsed, hash),
            parsed.tasks.map { VaultEntityMapper.toTaskEntity(it) }
        )
        dao.insertSyncLog(
            SyncLogEntity(
                timestamp = System.currentTimeMillis(),
                action = if (dirty) "$logAction (Offline Queued)" else logAction,
                detail = logDetail,
                isSuccess = true
            )
        )
    }
}
