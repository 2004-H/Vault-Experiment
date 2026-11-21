package com.example.data.model

enum class PropertyType {
    TEXT,
    NUMBER,
    BOOLEAN,
    DATE,
    LIST,
    LINK,
    RATING,
    STATUS
}

enum class PropertySource {
    FRONTMATTER,
    INLINE
}

data class NoteProperty(
    val key: String,
    val value: String,
    val type: PropertyType = PropertyType.TEXT,
    val source: PropertySource = PropertySource.FRONTMATTER,
    val listItems: List<String> = emptyList()
)

sealed interface VaultBlock {
    val id: String

    data class DataviewQueryBlock(
        override val id: String,
        val query: String,
        val title: String = ""
    ) : VaultBlock

    data class DataviewJsBlock(
        override val id: String,
        val script: String,
        val title: String = ""
    ) : VaultBlock

    data class HeadingBlock(
        override val id: String,
        val level: Int,
        val text: String
    ) : VaultBlock

    data class TaskBlock(
        override val id: String,
        val text: String,
        val completed: Boolean,
        val lineIndex: Int,
        val dueDate: String? = null,
        val priority: String? = null
    ) : VaultBlock

    data class CalloutBlock(
        override val id: String,
        val calloutType: String,
        val title: String,
        val body: String
    ) : VaultBlock

    data class MarkdownParagraphBlock(
        override val id: String,
        val text: String
    ) : VaultBlock
}

data class VaultTask(
    val id: String,
    val notePath: String,
    val noteTitle: String,
    val lineIndex: Int,
    val text: String,
    val completed: Boolean,
    val dueDate: String? = null,
    val priority: String? = null,
    val tags: List<String> = emptyList(),
    val inlineFields: Map<String, String> = emptyMap()
)

data class VaultNote(
    val path: String,
    val title: String,
    val folder: String,
    val tags: List<String>,
    val outlinks: List<String>,
    val properties: List<NoteProperty>,
    val blocks: List<VaultBlock>,
    val tasks: List<VaultTask>,
    val rawMarkdown: String,
    val lastModified: Long,
    val wordCount: Int,
    val isPinned: Boolean,
    val hasDataview: Boolean,
    val hasDataviewJs: Boolean,
    val dirtySync: Boolean = false
) {
    fun propertyValue(key: String): String? {
        val lower = key.lowercase()
        return properties.firstOrNull { it.key.lowercase() == lower }?.value
    }
}

sealed interface DataviewCell {
    data class TextCell(val text: String) : DataviewCell
    data class LinkCell(val target: String, val display: String = target) : DataviewCell
    data class TagListCell(val tags: List<String>) : DataviewCell
    data class StatusCell(val status: String) : DataviewCell
    data class RatingCell(val rating: Double, val max: Int = 5) : DataviewCell
    data class BooleanCell(val value: Boolean) : DataviewCell
    data class DateCell(val dateText: String) : DataviewCell
    data class NumberCell(val value: Double, val formatted: String) : DataviewCell
}

data class DataviewTableRow(
    val notePath: String,
    val cells: List<DataviewCell>
)

data class DataviewListEntry(
    val notePath: String,
    val noteTitle: String,
    val displayText: String
)

sealed interface DataviewResult {
    data class TableResult(
        val headers: List<String>,
        val rows: List<DataviewTableRow>,
        val executionTimeMs: Long
    ) : DataviewResult

    data class ListResult(
        val items: List<DataviewListEntry>,
        val groups: Map<String, List<DataviewListEntry>> = emptyMap(),
        val executionTimeMs: Long
    ) : DataviewResult

    data class TaskResult(
        val groups: Map<String, List<VaultTask>>,
        val executionTimeMs: Long
    ) : DataviewResult

    data class CalendarResult(
        val year: Int,
        val month: Int,
        val dateField: String,
        val notesByDate: Map<String, List<VaultNote>>,
        val executionTimeMs: Long
    ) : DataviewResult

    data class ErrorResult(
        val message: String
    ) : DataviewResult
}

sealed interface DataviewJsRenderNode {
    data class JsHeader(val level: Int, val text: String) : DataviewJsRenderNode
    data class JsParagraph(val text: String) : DataviewJsRenderNode
    data class JsSpan(val text: String) : DataviewJsRenderNode
    data class JsTable(val headers: List<String>, val rows: List<List<String>>) : DataviewJsRenderNode
    data class JsList(val items: List<String>) : DataviewJsRenderNode
    data class JsTaskList(val tasks: List<VaultTask>) : DataviewJsRenderNode
    data class JsError(val message: String) : DataviewJsRenderNode
}

data class SyncStatusSummary(
    val vaultName: String,
    val isSafLinked: Boolean,
    val linkedTreeUri: String?,
    val lastSyncEpochMs: Long,
    val isSyncing: Boolean,
    val noteCount: Int,
    val pendingPushCount: Int,
    val dataviewBlockCount: Int,
    val dataviewJsBlockCount: Int
)

data class SyncLogItem(
    val id: Long,
    val timestamp: Long,
    val action: String,
    val detail: String,
    val isSuccess: Boolean
)
