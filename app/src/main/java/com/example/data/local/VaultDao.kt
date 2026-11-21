package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Query("SELECT * FROM notes ORDER BY isPinned DESC, lastModified DESC")
    fun observeAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE path = :path LIMIT 1")
    fun observeNoteByPath(path: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE path = :path LIMIT 1")
    suspend fun getNoteByPath(path: String): NoteEntity?

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesSnapshot(): List<NoteEntity>

    @Query("SELECT * FROM tasks ORDER BY completed ASC, dueDate ASC")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE notePath = :notePath ORDER BY lineIndex ASC")
    fun observeTasksForNote(notePath: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT 40")
    fun observeSyncLogs(): Flow<List<SyncLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotes(notes: List<NoteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE notePath = :notePath")
    suspend fun deleteTasksForNote(notePath: String)

    @Query("DELETE FROM notes WHERE path = :path")
    suspend fun deleteNoteByPath(path: String)

    @Query("DELETE FROM notes")
    suspend fun clearAllNotes()

    @Query("DELETE FROM tasks")
    suspend fun clearAllTasks()

    @Insert
    suspend fun insertSyncLog(log: SyncLogEntity)

    @Transaction
    suspend fun replaceNoteAndTasks(note: NoteEntity, tasks: List<TaskEntity>) {
        upsertNote(note)
        deleteTasksForNote(note.path)
        if (tasks.isNotEmpty()) {
            upsertTasks(tasks)
        }
    }

    @Transaction
    suspend fun replaceVaultSnapshot(notes: List<NoteEntity>, tasks: List<TaskEntity>) {
        clearAllNotes()
        clearAllTasks()
        if (notes.isNotEmpty()) upsertNotes(notes)
        if (tasks.isNotEmpty()) upsertTasks(tasks)
    }
}
