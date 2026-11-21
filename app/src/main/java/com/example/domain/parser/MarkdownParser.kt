package com.example.domain.parser

import com.example.data.model.NoteProperty
import com.example.data.model.PropertySource
import com.example.data.model.PropertyType
import com.example.data.model.VaultBlock
import com.example.data.model.VaultNote
import com.example.data.model.VaultTask
import java.security.MessageDigest
import java.util.UUID

object MarkdownParser {

    private val tagRegex = Regex("""(?:^|\s)#([A-Za-z0-9_/\-]+)""")
    private val wikiLinkRegex = Regex("""\[\[([^\]|#]+)(?:#[^\]|]+)?(?:\|([^\]]+))?\]\]""")
    private val inlineFieldBracketRegex = Regex("""[\[(]([A-Za-z0-9_\-\s]+)::\s*([^\]\)]+)[\])]""")
    private val inlineFieldLineRegex = Regex("""^([A-Za-z0-9_\-\s]+)::\s*(.+)$""")
    private val taskLineRegex = Regex("""^\s*[-*]\s+\[([ xX])\]\s+(.+)$""")
    private val headingRegex = Regex("""^(#{1,6})\s+(.+)$""")
    private val calloutHeaderRegex = Regex("""^>\s*\[!([A-Za-z0-9_\-]+)\]\s*(.*)$""")
    private val emojiDueRegex = Regex("""📅\s*(\d{4}-\d{2}-\d{2})""")
    private val dateRegex = Regex("""^\d{4}-\d{2}-\d{2}(?:T.*)?$""")

    fun hashContent(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    fun parseNote(
        path: String,
        rawMarkdown: String,
        lastModified: Long,
        isPinned: Boolean = false,
        dirtySync: Boolean = false
    ): VaultNote {
        val normalized = rawMarkdown.replace("\r\n", "\n")
        val lines = normalized.split("\n")

        val title = path.substringAfterLast("/").removeSuffix(".md")
        val folder = if (path.contains("/")) path.substringBeforeLast("/") else ""

        val properties = mutableListOf<NoteProperty>()
        val tagSet = linkedSetOf<String>()
        val outlinkSet = linkedSetOf<String>()
        val tasks = mutableListOf<VaultTask>()
        val blocks = mutableListOf<VaultBlock>()

        var bodyStartIndex = 0
        if (lines.firstOrNull()?.trim() == "---") {
            val endIdx = lines.drop(1).indexOfFirst { it.trim() == "---" }
            if (endIdx >= 0) {
                val fmLines = lines.subList(1, endIdx + 1)
                parseFrontmatter(fmLines, properties, tagSet, outlinkSet)
                bodyStartIndex = endIdx + 2
            }
        }

        // Parse body lines into blocks, inline fields, tasks, tags, and links
        var i = bodyStartIndex
        val paragraphBuffer = mutableListOf<String>()
        var blockCounter = 0

        fun flushParagraph() {
            val joined = paragraphBuffer.joinToString("\n").trim()
            if (joined.isNotEmpty()) {
                blocks.add(
                    VaultBlock.MarkdownParagraphBlock(
                        id = "blk_${blockCounter++}_${joined.hashCode()}",
                        text = joined
                    )
                )
            }
            paragraphBuffer.clear()
        }

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // Code fences (dataview / dataviewjs / generic)
            if (trimmed.startsWith("```")) {
                val lang = trimmed.removePrefix("```").trim().lowercase()
                if (lang == "dataview" || lang == "dataviewjs") {
                    flushParagraph()
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    val code = codeLines.joinToString("\n").trimEnd()
                    if (lang == "dataview") {
                        blocks.add(
                            VaultBlock.DataviewQueryBlock(
                                id = "dv_${blockCounter++}_${code.hashCode()}",
                                query = code
                            )
                        )
                    } else {
                        blocks.add(
                            VaultBlock.DataviewJsBlock(
                                id = "dvjs_${blockCounter++}_${code.hashCode()}",
                                script = code
                            )
                        )
                    }
                    i++
                    continue
                } else {
                    // Other code fence stays in paragraph buffer
                    paragraphBuffer.add(line)
                    i++
                    while (i < lines.size) {
                        paragraphBuffer.add(lines[i])
                        if (lines[i].trim().startsWith("```")) break
                        i++
                    }
                    i++
                    continue
                }
            }

            // Callout block (> [!note] Title)
            val calloutMatch = calloutHeaderRegex.matchEntire(trimmed)
            if (calloutMatch != null) {
                flushParagraph()
                val calloutType = calloutMatch.groupValues[1].lowercase()
                val calloutTitle = calloutMatch.groupValues[2].trim()
                val bodyLines = mutableListOf<String>()
                i++
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    bodyLines.add(lines[i].trim().removePrefix(">").trimStart())
                    i++
                }
                val bodyText = bodyLines.joinToString("\n")
                extractTagsAndLinks(bodyText, tagSet, outlinkSet)
                blocks.add(
                    VaultBlock.CalloutBlock(
                        id = "callout_${blockCounter++}_${calloutTitle.hashCode()}",
                        calloutType = calloutType,
                        title = calloutTitle,
                        body = bodyText
                    )
                )
                continue
            }

            // Heading
            val headingMatch = headingRegex.matchEntire(trimmed)
            if (headingMatch != null) {
                flushParagraph()
                val level = headingMatch.groupValues[1].length
                val headingText = headingMatch.groupValues[2].trim()
                extractTagsAndLinks(headingText, tagSet, outlinkSet)
                blocks.add(
                    VaultBlock.HeadingBlock(
                        id = "hd_${blockCounter++}_${headingText.hashCode()}",
                        level = level,
                        text = headingText
                    )
                )
                i++
                continue
            }

            // Task
            val taskMatch = taskLineRegex.matchEntire(line)
            if (taskMatch != null) {
                flushParagraph()
                val completed = taskMatch.groupValues[1].equals("x", ignoreCase = true)
                val rawTaskText = taskMatch.groupValues[2].trim()
                val inlineMap = mutableMapOf<String, String>()
                inlineFieldBracketRegex.findAll(rawTaskText).forEach { m ->
                    inlineMap[m.groupValues[1].trim()] = m.groupValues[2].trim()
                }
                val due = inlineMap["due"] ?: emojiDueRegex.find(rawTaskText)?.groupValues?.get(1)
                val priority = inlineMap["priority"] ?: when {
                    rawTaskText.contains("⏫") -> "high"
                    rawTaskText.contains("🔼") -> "medium"
                    rawTaskText.contains("🔽") -> "low"
                    else -> null
                }
                val taskTags = mutableListOf<String>()
                tagRegex.findAll(rawTaskText).forEach { m ->
                    val t = m.groupValues[1]
                    taskTags.add(t)
                    tagSet.add(t)
                }
                wikiLinkRegex.findAll(rawTaskText).forEach { m ->
                    outlinkSet.add(m.groupValues[1].trim())
                }
                val taskId = "${path}::line_$i"
                tasks.add(
                    VaultTask(
                        id = taskId,
                        notePath = path,
                        noteTitle = title,
                        lineIndex = i,
                        text = rawTaskText,
                        completed = completed,
                        dueDate = due,
                        priority = priority,
                        tags = taskTags,
                        inlineFields = inlineMap
                    )
                )
                blocks.add(
                    VaultBlock.TaskBlock(
                        id = "task_${blockCounter++}_$i",
                        text = rawTaskText,
                        completed = completed,
                        lineIndex = i,
                        dueDate = due,
                        priority = priority
                    )
                )
                i++
                continue
            }

            // Inline Dataview fields on standalone lines or bracketed
            val inlineLineMatch = inlineFieldLineRegex.matchEntire(trimmed)
            if (inlineLineMatch != null && !trimmed.startsWith("http")) {
                val key = inlineLineMatch.groupValues[1].trim()
                val rawVal = inlineLineMatch.groupValues[2].trim()
                if (key.isNotBlank() && properties.none { it.key.equals(key, ignoreCase = true) }) {
                    properties.add(classifyProperty(key, rawVal, PropertySource.INLINE))
                }
            }
            inlineFieldBracketRegex.findAll(line).forEach { m ->
                val key = m.groupValues[1].trim()
                val rawVal = m.groupValues[2].trim()
                if (key.isNotBlank() && properties.none { it.key.equals(key, ignoreCase = true) }) {
                    properties.add(classifyProperty(key, rawVal, PropertySource.INLINE))
                }
            }

            extractTagsAndLinks(line, tagSet, outlinkSet)

            if (trimmed.isEmpty()) {
                flushParagraph()
            } else {
                paragraphBuffer.add(line)
            }
            i++
        }
        flushParagraph()

        val pinnedProp = properties.firstOrNull { it.key.equals("pinned", ignoreCase = true) }
        val resolvedPinned = isPinned || pinnedProp?.value.equals("true", ignoreCase = true)
        val hasDv = blocks.any { it is VaultBlock.DataviewQueryBlock }
        val hasDvJs = blocks.any { it is VaultBlock.DataviewJsBlock }
        val wordCount = normalized.split(Regex("""\s+""")).count { it.isNotBlank() }

        return VaultNote(
            path = path,
            title = title,
            folder = folder,
            tags = tagSet.toList(),
            outlinks = outlinkSet.toList(),
            properties = properties,
            blocks = blocks,
            tasks = tasks,
            rawMarkdown = normalized,
            lastModified = lastModified,
            wordCount = wordCount,
            isPinned = resolvedPinned,
            hasDataview = hasDv,
            hasDataviewJs = hasDvJs,
            dirtySync = dirtySync
        )
    }

    private fun parseFrontmatter(
        fmLines: List<String>,
        outProps: MutableList<NoteProperty>,
        tagSet: MutableSet<String>,
        outlinkSet: MutableSet<String>
    ) {
        var idx = 0
        while (idx < fmLines.size) {
            val line = fmLines[idx]
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                idx++
                continue
            }
            val colonIdx = trimmed.indexOf(':')
            if (colonIdx <= 0) {
                idx++
                continue
            }
            val key = trimmed.substring(0, colonIdx).trim()
            val afterColon = trimmed.substring(colonIdx + 1).trim()

            if (afterColon.isEmpty()) {
                // Multi-line YAML list
                val listItems = mutableListOf<String>()
                var j = idx + 1
                while (j < fmLines.size) {
                    val sub = fmLines[j].trim()
                    if (sub.startsWith("- ")) {
                        val itemVal = unquote(sub.removePrefix("- ").trim())
                        listItems.add(itemVal)
                        j++
                    } else if (sub.isEmpty()) {
                        j++
                    } else {
                        break
                    }
                }
                if (key.equals("tags", ignoreCase = true) || key.equals("tag", ignoreCase = true)) {
                    listItems.forEach { t -> tagSet.add(t.removePrefix("#").trim()) }
                }
                listItems.forEach { item ->
                    wikiLinkRegex.findAll(item).forEach { outlinkSet.add(it.groupValues[1].trim()) }
                }
                outProps.add(
                    NoteProperty(
                        key = key,
                        value = listItems.joinToString(", "),
                        type = PropertyType.LIST,
                        source = PropertySource.FRONTMATTER,
                        listItems = listItems
                    )
                )
                idx = j
                continue
            } else {
                val prop = classifyProperty(key, afterColon, PropertySource.FRONTMATTER)
                if (key.equals("tags", ignoreCase = true) || key.equals("tag", ignoreCase = true)) {
                    val items = if (prop.listItems.isNotEmpty()) prop.listItems else prop.value.split(",").map { it.trim() }
                    items.filter { it.isNotBlank() }.forEach { tagSet.add(it.removePrefix("#").trim()) }
                }
                wikiLinkRegex.findAll(afterColon).forEach { outlinkSet.add(it.groupValues[1].trim()) }
                outProps.add(prop)
                idx++
            }
        }
    }

    fun classifyProperty(key: String, rawValue: String, source: PropertySource): NoteProperty {
        val cleaned = unquote(rawValue.trim())
        val lowerKey = key.lowercase()

        // Inline array [a, b, c]
        if (rawValue.trim().startsWith("[") && rawValue.trim().endsWith("]") && !rawValue.trim().startsWith("[[")) {
            val inner = rawValue.trim().removePrefix("[").removeSuffix("]")
            val items = inner.split(",").map { unquote(it.trim()) }.filter { it.isNotBlank() }
            return NoteProperty(
                key = key,
                value = items.joinToString(", "),
                type = PropertyType.LIST,
                source = source,
                listItems = items
            )
        }

        if (cleaned.equals("true", ignoreCase = true) || cleaned.equals("false", ignoreCase = true)) {
            return NoteProperty(key = key, value = cleaned.lowercase(), type = PropertyType.BOOLEAN, source = source)
        }

        if (wikiLinkRegex.containsMatchIn(cleaned)) {
            return NoteProperty(key = key, value = cleaned, type = PropertyType.LINK, source = source)
        }

        if (dateRegex.matches(cleaned) || lowerKey == "due" || lowerKey == "date" || lowerKey == "created" || lowerKey == "updated") {
            if (dateRegex.matches(cleaned)) {
                return NoteProperty(key = key, value = cleaned, type = PropertyType.DATE, source = source)
            }
        }

        if (lowerKey == "status" || lowerKey == "stage" || lowerKey == "state") {
            return NoteProperty(key = key, value = cleaned, type = PropertyType.STATUS, source = source)
        }

        if (lowerKey == "rating" || lowerKey == "score" || lowerKey == "stars") {
            val num = cleaned.toDoubleOrNull()
            if (num != null && num in 0.0..5.0) {
                return NoteProperty(key = key, value = cleaned, type = PropertyType.RATING, source = source)
            }
        }

        if (cleaned.toDoubleOrNull() != null) {
            return NoteProperty(key = key, value = cleaned, type = PropertyType.NUMBER, source = source)
        }

        if (lowerKey == "tags" || lowerKey == "aliases") {
            val items = cleaned.split(",").map { unquote(it.trim()) }.filter { it.isNotBlank() }
            return NoteProperty(key = key, value = items.joinToString(", "), type = PropertyType.LIST, source = source, listItems = items)
        }

        return NoteProperty(key = key, value = cleaned, type = PropertyType.TEXT, source = source)
    }

    private fun extractTagsAndLinks(
        line: String,
        tagSet: MutableSet<String>,
        outlinkSet: MutableSet<String>
    ) {
        tagRegex.findAll(line).forEach { match ->
            val t = match.groupValues[1]
            if (!t.matches(Regex("""^[0-9a-fA-F]{3,8}$"""))) {
                tagSet.add(t)
            }
        }
        wikiLinkRegex.findAll(line).forEach { match ->
            outlinkSet.add(match.groupValues[1].trim())
        }
    }

    private fun unquote(value: String): String {
        val t = value.trim()
        return if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
            t.substring(1, t.length - 1)
        } else {
            t
        }
    }

    fun formatYamlValue(prop: NoteProperty): String {
        return when (prop.type) {
            PropertyType.LIST -> {
                val items = if (prop.listItems.isNotEmpty()) prop.listItems else prop.value.split(",").map { it.trim() }.filter { it.isNotBlank() }
                "[${items.joinToString(", ") { formatScalarYaml(it) }}]"
            }
            PropertyType.BOOLEAN, PropertyType.NUMBER, PropertyType.RATING -> prop.value.trim()
            PropertyType.DATE -> prop.value.trim()
            PropertyType.LINK -> "\"${prop.value.replace("\"", "\\\"")}\""
            PropertyType.STATUS, PropertyType.TEXT -> formatScalarYaml(prop.value)
        }
    }

    private fun formatScalarYaml(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.contains(":") || trimmed.contains("#") || trimmed.startsWith("[") || trimmed.contains(",")) {
            "\"${trimmed.replace("\"", "\\\"")}\""
        } else {
            trimmed
        }
    }

    fun updatePropertyInMarkdown(
        rawMarkdown: String,
        key: String,
        newProperty: NoteProperty
    ): String {
        val normalized = rawMarkdown.replace("\r\n", "\n")
        val lines = normalized.split("\n").toMutableList()

        // If inline property exists outside frontmatter, update it when source == INLINE
        if (newProperty.source == PropertySource.INLINE) {
            val inlineRegex = Regex("""^${Regex.escape(key)}::.*$""", RegexOption.IGNORE_CASE)
            val idx = lines.indexOfFirst { inlineRegex.matches(it.trim()) }
            if (idx >= 0) {
                lines[idx] = "$key:: ${newProperty.value}"
                return lines.joinToString("\n")
            }
        }

        // Frontmatter handling
        if (lines.firstOrNull()?.trim() == "---") {
            val endIdx = lines.drop(1).indexOfFirst { it.trim() == "---" }
            if (endIdx >= 0) {
                val fmEnd = endIdx + 1
                var matchIdx = -1
                for (i in 1 until fmEnd) {
                    val trimmed = lines[i].trim()
                    if (trimmed.startsWith("$key:", ignoreCase = true)) {
                        matchIdx = i
                        break
                    }
                }
                val formatted = "$key: ${formatYamlValue(newProperty)}"
                if (matchIdx >= 0) {
                    // Remove any old multi-line list items below this key
                    var j = matchIdx + 1
                    while (j < fmEnd && lines[j].trim().startsWith("- ")) {
                        lines.removeAt(j)
                    }
                    lines[matchIdx] = formatted
                } else {
                    lines.add(fmEnd, formatted)
                }
                return lines.joinToString("\n")
            }
        }

        // No frontmatter yet: prepend YAML frontmatter cleanly
        val header = listOf("---", "$key: ${formatYamlValue(newProperty)}", "---", "")
        return (header + lines).joinToString("\n")
    }

    fun removePropertyInMarkdown(rawMarkdown: String, key: String): String {
        val normalized = rawMarkdown.replace("\r\n", "\n")
        val lines = normalized.split("\n").toMutableList()
        if (lines.firstOrNull()?.trim() == "---") {
            val endIdx = lines.drop(1).indexOfFirst { it.trim() == "---" }
            if (endIdx >= 0) {
                val fmEnd = endIdx + 1
                for (i in 1 until fmEnd) {
                    if (lines[i].trim().startsWith("$key:", ignoreCase = true)) {
                        lines.removeAt(i)
                        while (i < lines.size && lines[i].trim().startsWith("- ")) {
                            lines.removeAt(i)
                        }
                        return lines.joinToString("\n")
                    }
                }
            }
        }
        val inlineRegex = Regex("""^${Regex.escape(key)}::.*$""", RegexOption.IGNORE_CASE)
        lines.removeAll { inlineRegex.matches(it.trim()) }
        return lines.joinToString("\n")
    }

    fun toggleTaskInMarkdown(rawMarkdown: String, lineIndex: Int, completed: Boolean): String {
        val normalized = rawMarkdown.replace("\r\n", "\n")
        val lines = normalized.split("\n").toMutableList()
        if (lineIndex in lines.indices) {
            val line = lines[lineIndex]
            lines[lineIndex] = if (completed) {
                line.replaceFirst(Regex("""\[\s\]"""), "[x]")
            } else {
                line.replaceFirst(Regex("""\[[xX]\]"""), "[ ]")
            }
        }
        return lines.joinToString("\n")
    }

    fun rebuildMarkdownFromBlocks(
        properties: List<NoteProperty>,
        blocks: List<VaultBlock>
    ): String {
        val sb = StringBuilder()
        val fmProps = properties.filter { it.source == PropertySource.FRONTMATTER }
        if (fmProps.isNotEmpty()) {
            sb.appendLine("---")
            fmProps.forEach { prop ->
                sb.appendLine("${prop.key}: ${formatYamlValue(prop)}")
            }
            sb.appendLine("---")
            sb.appendLine()
        }

        val inlineProps = properties.filter { it.source == PropertySource.INLINE }
        if (inlineProps.isNotEmpty()) {
            inlineProps.forEach { prop ->
                sb.appendLine("${prop.key}:: ${prop.value}")
            }
            sb.appendLine()
        }

        blocks.forEachIndexed { index, block ->
            when (block) {
                is VaultBlock.DataviewQueryBlock -> {
                    sb.appendLine("```dataview")
                    sb.appendLine(block.query.trimEnd())
                    sb.appendLine("```")
                }
                is VaultBlock.DataviewJsBlock -> {
                    sb.appendLine("```dataviewjs")
                    sb.appendLine(block.script.trimEnd())
                    sb.appendLine("```")
                }
                is VaultBlock.HeadingBlock -> {
                    val hashes = "#".repeat(block.level.coerceIn(1, 6))
                    sb.appendLine("$hashes ${block.text}")
                }
                is VaultBlock.TaskBlock -> {
                    val box = if (block.completed) "[x]" else "[ ]"
                    sb.appendLine("- $box ${block.text}")
                }
                is VaultBlock.CalloutBlock -> {
                    sb.appendLine("> [!${block.calloutType}] ${block.title}".trimEnd())
                    block.body.lines().forEach { bLine ->
                        sb.appendLine("> $bLine")
                    }
                }
                is VaultBlock.MarkdownParagraphBlock -> {
                    sb.appendLine(block.text)
                }
            }
            if (index < blocks.lastIndex) {
                sb.appendLine()
            }
        }

        return sb.toString().trimEnd() + "\n"
    }

    fun newBlockId(prefix: String = "blk"): String = "${prefix}_${UUID.randomUUID().toString().take(8)}"
}
