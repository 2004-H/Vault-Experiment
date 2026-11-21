package com.example

import com.example.data.model.DataviewResult
import com.example.data.model.NoteProperty
import com.example.data.model.PropertySource
import com.example.data.model.PropertyType
import com.example.data.sync.StarterVaultTemplates
import com.example.domain.engine.DataviewQueryEngine
import com.example.domain.parser.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataviewEngineTest {

    @Test
    fun parseNote_extractsFrontmatterInlineFieldsTasksAndBlocks() {
        val sample = StarterVaultTemplates.starterNotes().getValue("Dashboards/Command Center.md")
        val note = MarkdownParser.parseNote("Dashboards/Command Center.md", sample, 1700000000L)

        assertEquals("Command Center", note.title)
        assertEquals("Dashboards", note.folder)
        assertTrue(note.isPinned)
        assertTrue(note.hasDataview)
        assertTrue(note.hasDataviewJs)
        assertEquals("active", note.propertyValue("status"))
        assertEquals(2, note.tasks.size)
        assertTrue(note.tags.contains("dashboard"))
    }

    @Test
    fun updatePropertyAndToggleTask_preservesMarkdownAndUpdatesInPlace() {
        val raw = """
            ---
            status: active
            rating: 4
            ---

            # Project Note

            - [ ] Ship update [priority:: high]
        """.trimIndent() + "\n"

        val updatedPropRaw = MarkdownParser.updatePropertyInMarkdown(
            raw,
            "status",
            NoteProperty("status", "done", PropertyType.STATUS, PropertySource.FRONTMATTER)
        )
        val parsedAfterProp = MarkdownParser.parseNote("Projects/Test.md", updatedPropRaw, 100L)
        assertEquals("done", parsedAfterProp.propertyValue("status"))
        assertEquals("4", parsedAfterProp.propertyValue("rating"))

        val task = parsedAfterProp.tasks.first()
        val toggledRaw = MarkdownParser.toggleTaskInMarkdown(updatedPropRaw, task.lineIndex, true)
        val parsedAfterToggle = MarkdownParser.parseNote("Projects/Test.md", toggledRaw, 200L)
        assertTrue(parsedAfterToggle.tasks.first().completed)
    }

    @Test
    fun dataviewQueryEngine_executesTableListAndTaskQueries() {
        val notes = StarterVaultTemplates.starterNotes().map { (path, content) ->
            MarkdownParser.parseNote(path, content, 1732180000000L)
        }
        val engine = DataviewQueryEngine()

        val tableRes = engine.execute(
            """
            TABLE status AS "Status", rating AS "Rating"
            FROM #project
            WHERE status = "active"
            SORT rating DESC
            """.trimIndent(),
            notes
        )
        assertTrue(tableRes is DataviewResult.TableResult)
        val rows = (tableRes as DataviewResult.TableResult).rows
        assertEquals(2, rows.size)

        val taskRes = engine.execute(
            """
            TASK
            WHERE !completed
            """.trimIndent(),
            notes
        )
        assertTrue(taskRes is DataviewResult.TaskResult)
        val totalOpen = (taskRes as DataviewResult.TaskResult).groups.values.sumOf { it.size }
        assertTrue(totalOpen >= 5)
    }
}
