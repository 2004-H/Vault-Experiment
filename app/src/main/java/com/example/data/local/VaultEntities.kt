package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.NoteProperty
import com.example.data.model.PropertySource
import com.example.data.model.PropertyType
import com.example.data.model.SyncLogItem
import com.example.data.model.VaultBlock
import com.example.data.model.VaultNote
import com.example.data.model.VaultTask
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val path: String,
    val title: String,
    val folder: String,
    val tagsJson: String,
    val outlinksJson: String,
    val propertiesJson: String,
    val blocksJson: String,
    val rawMarkdown: String,
    val lastModified: Long,
    val contentHash: String,
    val wordCount: Int,
    val isPinned: Boolean,
    val hasDataview: Boolean,
    val hasDataviewJs: Boolean,
    val dirtySync: Boolean
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val notePath: String,
    val noteTitle: String,
    val lineIndex: Int,
    val text: String,
    val completed: Boolean,
    val dueDate: String?,
    val priority: String?,
    val tagsJson: String,
    val inlineFieldsJson: String
)

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val action: String,
    val detail: String,
    val isSuccess: Boolean
)

object VaultEntityMapper {

    fun toEntity(note: VaultNote, contentHash: String): NoteEntity {
        return NoteEntity(
            path = note.path,
            title = note.title,
            folder = note.folder,
            tagsJson = JSONArray(note.tags).toString(),
            outlinksJson = JSONArray(note.outlinks).toString(),
            propertiesJson = encodeProperties(note.properties),
            blocksJson = encodeBlocks(note.blocks),
            rawMarkdown = note.rawMarkdown,
            lastModified = note.lastModified,
            contentHash = contentHash,
            wordCount = note.wordCount,
            isPinned = note.isPinned,
            hasDataview = note.hasDataview,
            hasDataviewJs = note.hasDataviewJs,
            dirtySync = note.dirtySync
        )
    }

    fun toDomain(entity: NoteEntity, tasks: List<VaultTask>): VaultNote {
        return VaultNote(
            path = entity.path,
            title = entity.title,
            folder = entity.folder,
            tags = decodeStringList(entity.tagsJson),
            outlinks = decodeStringList(entity.outlinksJson),
            properties = decodeProperties(entity.propertiesJson),
            blocks = decodeBlocks(entity.blocksJson),
            tasks = tasks,
            rawMarkdown = entity.rawMarkdown,
            lastModified = entity.lastModified,
            wordCount = entity.wordCount,
            isPinned = entity.isPinned,
            hasDataview = entity.hasDataview,
            hasDataviewJs = entity.hasDataviewJs,
            dirtySync = entity.dirtySync
        )
    }

    fun toTaskEntity(task: VaultTask): TaskEntity {
        val inlineObj = JSONObject()
        task.inlineFields.forEach { (k, v) -> inlineObj.put(k, v) }
        return TaskEntity(
            id = task.id,
            notePath = task.notePath,
            noteTitle = task.noteTitle,
            lineIndex = task.lineIndex,
            text = task.text,
            completed = task.completed,
            dueDate = task.dueDate,
            priority = task.priority,
            tagsJson = JSONArray(task.tags).toString(),
            inlineFieldsJson = inlineObj.toString()
        )
    }

    fun toDomainTask(entity: TaskEntity): VaultTask {
        val inlineMap = mutableMapOf<String, String>()
        runCatching {
            val obj = JSONObject(entity.inlineFieldsJson)
            obj.keys().forEach { key -> inlineMap[key] = obj.optString(key) }
        }
        return VaultTask(
            id = entity.id,
            notePath = entity.notePath,
            noteTitle = entity.noteTitle,
            lineIndex = entity.lineIndex,
            text = entity.text,
            completed = entity.completed,
            dueDate = entity.dueDate,
            priority = entity.priority,
            tags = decodeStringList(entity.tagsJson),
            inlineFields = inlineMap
        )
    }

    fun toDomainLog(entity: SyncLogEntity): SyncLogItem =
        SyncLogItem(
            id = entity.id,
            timestamp = entity.timestamp,
            action = entity.action,
            detail = entity.detail,
            isSuccess = entity.isSuccess
        )

    private fun decodeStringList(json: String): List<String> {
        val list = mutableListOf<String>()
        runCatching {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                list.add(arr.optString(i))
            }
        }
        return list
    }

    private fun encodeProperties(props: List<NoteProperty>): String {
        val arr = JSONArray()
        props.forEach { prop ->
            val obj = JSONObject()
            obj.put("key", prop.key)
            obj.put("value", prop.value)
            obj.put("type", prop.type.name)
            obj.put("source", prop.source.name)
            obj.put("listItems", JSONArray(prop.listItems))
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeProperties(json: String): List<NoteProperty> {
        val list = mutableListOf<NoteProperty>()
        runCatching {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val itemsArr = obj.optJSONArray("listItems") ?: JSONArray()
                val items = mutableListOf<String>()
                for (j in 0 until itemsArr.length()) {
                    items.add(itemsArr.optString(j))
                }
                list.add(
                    NoteProperty(
                        key = obj.optString("key"),
                        value = obj.optString("value"),
                        type = runCatching { PropertyType.valueOf(obj.optString("type")) }
                            .getOrDefault(PropertyType.TEXT),
                        source = runCatching { PropertySource.valueOf(obj.optString("source")) }
                            .getOrDefault(PropertySource.FRONTMATTER),
                        listItems = items
                    )
                )
            }
        }
        return list
    }

    private fun encodeBlocks(blocks: List<VaultBlock>): String {
        val arr = JSONArray()
        blocks.forEach { block ->
            val obj = JSONObject()
            obj.put("id", block.id)
            when (block) {
                is VaultBlock.DataviewQueryBlock -> {
                    obj.put("kind", "dataview")
                    obj.put("query", block.query)
                    obj.put("title", block.title)
                }
                is VaultBlock.DataviewJsBlock -> {
                    obj.put("kind", "dataviewjs")
                    obj.put("script", block.script)
                    obj.put("title", block.title)
                }
                is VaultBlock.HeadingBlock -> {
                    obj.put("kind", "heading")
                    obj.put("level", block.level)
                    obj.put("text", block.text)
                }
                is VaultBlock.TaskBlock -> {
                    obj.put("kind", "task")
                    obj.put("text", block.text)
                    obj.put("completed", block.completed)
                    obj.put("lineIndex", block.lineIndex)
                    obj.put("dueDate", block.dueDate ?: "")
                    obj.put("priority", block.priority ?: "")
                }
                is VaultBlock.CalloutBlock -> {
                    obj.put("kind", "callout")
                    obj.put("calloutType", block.calloutType)
                    obj.put("title", block.title)
                    obj.put("body", block.body)
                }
                is VaultBlock.MarkdownParagraphBlock -> {
                    obj.put("kind", "paragraph")
                    obj.put("text", block.text)
                }
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeBlocks(json: String): List<VaultBlock> {
        val list = mutableListOf<VaultBlock>()
        runCatching {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id")
                when (obj.optString("kind")) {
                    "dataview" -> list.add(
                        VaultBlock.DataviewQueryBlock(
                            id = id,
                            query = obj.optString("query"),
                            title = obj.optString("title")
                        )
                    )
                    "dataviewjs" -> list.add(
                        VaultBlock.DataviewJsBlock(
                            id = id,
                            script = obj.optString("script"),
                            title = obj.optString("title")
                        )
                    )
                    "heading" -> list.add(
                        VaultBlock.HeadingBlock(
                            id = id,
                            level = obj.optInt("level", 2),
                            text = obj.optString("text")
                        )
                    )
                    "task" -> list.add(
                        VaultBlock.TaskBlock(
                            id = id,
                            text = obj.optString("text"),
                            completed = obj.optBoolean("completed", false),
                            lineIndex = obj.optInt("lineIndex", 0),
                            dueDate = obj.optString("dueDate").takeIf { it.isNotBlank() },
                            priority = obj.optString("priority").takeIf { it.isNotBlank() }
                        )
                    )
                    "callout" -> list.add(
                        VaultBlock.CalloutBlock(
                            id = id,
                            calloutType = obj.optString("calloutType", "note"),
                            title = obj.optString("title"),
                            body = obj.optString("body")
                        )
                    )
                    else -> list.add(
                        VaultBlock.MarkdownParagraphBlock(
                            id = id,
                            text = obj.optString("text")
                        )
                    )
                }
            }
        }
        return list
    }
}
