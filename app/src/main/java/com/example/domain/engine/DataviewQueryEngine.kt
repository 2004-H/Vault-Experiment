package com.example.domain.engine

import com.example.data.model.DataviewCell
import com.example.data.model.DataviewListEntry
import com.example.data.model.DataviewResult
import com.example.data.model.DataviewTableRow
import com.example.data.model.PropertyType
import com.example.data.model.VaultNote
import com.example.data.model.VaultTask
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DataviewQueryEngine {

    private val todayStr: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun execute(
        rawQuery: String,
        allNotes: List<VaultNote>,
        currentNote: VaultNote? = null
    ): DataviewResult {
        val startMs = System.currentTimeMillis()
        val lines = rawQuery.replace("\r\n", "\n")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") }

        if (lines.isEmpty()) {
            return DataviewResult.ErrorResult("Empty Dataview query. Try TABLE, LIST, TASK, or CALENDAR.")
        }

        return try {
            val clauses = parseClauses(lines)
            val header = clauses.header.trim()
            val headerUpper = header.uppercase()

            val mode = when {
                headerUpper.startsWith("TABLE") -> QueryMode.TABLE
                headerUpper.startsWith("LIST") -> QueryMode.LIST
                headerUpper.startsWith("TASK") -> QueryMode.TASK
                headerUpper.startsWith("CALENDAR") -> QueryMode.CALENDAR
                else -> return DataviewResult.ErrorResult("Unsupported query type. Start with TABLE, LIST, TASK, or CALENDAR.")
            }

            // 1. FROM filtering
            var candidateNotes = if (clauses.from.isNotBlank()) {
                filterByFrom(allNotes, clauses.from)
            } else {
                allNotes
            }

            // Exclude current note if query doesn't explicitly ask for self? Obsidian includes self unless excluded,
            // so we keep standard Obsidian Dataview semantics!
            if (mode == QueryMode.TASK) {
                val allTasks = candidateNotes.flatMap { note ->
                    note.tasks.map { task -> task to note }
                }
                val filteredTasks = if (clauses.where.isNotBlank()) {
                    allTasks.filter { (task, note) ->
                        evalWhereTask(clauses.where, task, note, currentNote)
                    }
                } else {
                    allTasks
                }
                val sortedTasks = if (clauses.sort.isNotBlank()) {
                    sortTasks(filteredTasks, clauses.sort)
                } else {
                    filteredTasks
                }
                val limitedTasks = if (clauses.limit != null) {
                    sortedTasks.take(clauses.limit)
                } else {
                    sortedTasks
                }

                val groups = if (clauses.groupBy.isNotBlank()) {
                    limitedTasks.groupBy { (task, note) ->
                        evalTaskExpr(clauses.groupBy, task, note, currentNote).ifBlank { "Ungrouped" }
                    }.mapValues { entry -> entry.value.map { it.first } }
                } else {
                    limitedTasks.groupBy { (_, note) -> note.title }
                        .mapValues { entry -> entry.value.map { it.first } }
                }
                val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1)
                return DataviewResult.TaskResult(groups = groups, executionTimeMs = elapsed)
            }

            // Note-level WHERE
            if (clauses.where.isNotBlank()) {
                candidateNotes = candidateNotes.filter { note ->
                    evalWhereNote(clauses.where, note, currentNote)
                }
            }

            // SORT
            if (clauses.sort.isNotBlank()) {
                candidateNotes = sortNotes(candidateNotes, clauses.sort, currentNote)
            }

            // LIMIT
            if (clauses.limit != null) {
                candidateNotes = candidateNotes.take(clauses.limit)
            }

            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1)

            when (mode) {
                QueryMode.TABLE -> buildTableResult(header, candidateNotes, currentNote, elapsed)
                QueryMode.LIST -> buildListResult(header, clauses.groupBy, candidateNotes, currentNote, elapsed)
                QueryMode.CALENDAR -> buildCalendarResult(header, candidateNotes, elapsed)
                QueryMode.TASK -> error("Handled above")
            }
        } catch (e: Exception) {
            DataviewResult.ErrorResult(e.message ?: "Query evaluation error")
        }
    }

    private enum class QueryMode { TABLE, LIST, TASK, CALENDAR }

    private data class ParsedClauses(
        val header: String,
        val from: String = "",
        val where: String = "",
        val sort: String = "",
        val groupBy: String = "",
        val limit: Int? = null
    )

    private fun parseClauses(lines: List<String>): ParsedClauses {
        val headerParts = mutableListOf<String>()
        var from = ""
        val whereParts = mutableListOf<String>()
        var sort = ""
        var groupBy = ""
        var limit: Int? = null

        var activeClause = "HEADER"
        for (line in lines) {
            val upper = line.uppercase()
            when {
                upper.startsWith("FROM ") -> {
                    activeClause = "FROM"
                    from = line.substring(5).trim()
                }
                upper == "FROM" -> activeClause = "FROM"
                upper.startsWith("WHERE ") -> {
                    activeClause = "WHERE"
                    whereParts.add(line.substring(6).trim())
                }
                upper == "WHERE" -> activeClause = "WHERE"
                upper.startsWith("SORT ") -> {
                    activeClause = "SORT"
                    sort = line.substring(5).trim()
                }
                upper.startsWith("GROUP BY ") -> {
                    activeClause = "GROUP BY"
                    groupBy = line.substring(9).trim()
                }
                upper.startsWith("LIMIT ") -> {
                    activeClause = "LIMIT"
                    limit = line.substring(6).trim().toIntOrNull()
                }
                upper.startsWith("FLATTEN ") -> {
                    activeClause = "FLATTEN"
                }
                else -> {
                    when (activeClause) {
                        "HEADER" -> headerParts.add(line)
                        "FROM" -> from = "$from $line".trim()
                        "WHERE" -> whereParts.add(line)
                        "SORT" -> sort = "$sort $line".trim()
                        "GROUP BY" -> groupBy = "$groupBy $line".trim()
                    }
                }
            }
        }
        return ParsedClauses(
            header = headerParts.joinToString(" "),
            from = from,
            where = whereParts.joinToString(" AND "),
            sort = sort,
            groupBy = groupBy,
            limit = limit
        )
    }

    private fun filterByFrom(notes: List<VaultNote>, fromExpr: String): List<VaultNote> {
        val trimmed = fromExpr.trim()
        if (trimmed == "\"\"" || trimmed == "*") return notes
        // Handle OR / AND expressions on tags and folders
        val orParts = splitLogical(trimmed, "OR")
        return notes.filter { note ->
            orParts.any { orPart ->
                val andParts = splitLogical(orPart, "AND")
                andParts.all { token -> evalFromToken(token.trim(), note) }
            }
        }
    }

    private fun evalFromToken(token: String, note: VaultNote): Boolean {
        if (token.isBlank()) return true
        val negated = token.startsWith("!") || token.startsWith("-")
        val clean = token.removePrefix("!").removePrefix("-").trim().removePrefix("(").removeSuffix(")")
        val matches = when {
            clean.startsWith("#") -> {
                val targetTag = clean.removePrefix("#").lowercase()
                note.tags.any { it.lowercase() == targetTag || it.lowercase().startsWith("$targetTag/") }
            }
            clean.startsWith("\"") && clean.endsWith("\"") -> {
                val folder = clean.removePrefix("\"").removeSuffix("\"").trim('/').lowercase()
                if (folder.isEmpty()) true
                else note.folder.lowercase() == folder || note.folder.lowercase().startsWith("$folder/") || note.path.lowercase().startsWith("$folder/")
            }
            clean.startsWith("[[") && clean.endsWith("]]") -> {
                val target = clean.removePrefix("[[").removeSuffix("]]").lowercase()
                note.outlinks.any { it.lowercase() == target }
            }
            else -> true
        }
        return if (negated) !matches else matches
    }

    private fun buildTableResult(
        header: String,
        notes: List<VaultNote>,
        currentNote: VaultNote?,
        elapsedMs: Long
    ): DataviewResult.TableResult {
        var rest = header.substring(5).trim() // after TABLE
        val withoutId = rest.uppercase().startsWith("WITHOUT ID")
        if (withoutId) {
            rest = rest.substring(10).trim()
        }

        val fieldSpecs = if (rest.isBlank()) emptyList() else splitTopLevelComma(rest)
        val headers = mutableListOf<String>()
        if (!withoutId) headers.add("File")

        val parsedFields = fieldSpecs.map { spec ->
            val asMatch = Regex("""(?i)^(.+?)\s+AS\s+"?([^"]+)"?$""").matchEntire(spec.trim())
            if (asMatch != null) {
                val expr = asMatch.groupValues[1].trim()
                val alias = asMatch.groupValues[2].trim()
                headers.add(alias)
                expr to alias
            } else {
                val expr = spec.trim()
                headers.add(expr)
                expr to expr
            }
        }

        val rows = notes.map { note ->
            val cells = mutableListOf<DataviewCell>()
            if (!withoutId) {
                cells.add(DataviewCell.LinkCell(target = note.path, display = note.title))
            }
            parsedFields.forEach { (expr, _) ->
                cells.add(evalCell(expr, note, currentNote))
            }
            DataviewTableRow(notePath = note.path, cells = cells)
        }

        return DataviewResult.TableResult(
            headers = headers,
            rows = rows,
            executionTimeMs = elapsedMs
        )
    }

    private fun buildListResult(
        header: String,
        groupByExpr: String,
        notes: List<VaultNote>,
        currentNote: VaultNote?,
        elapsedMs: Long
    ): DataviewResult.ListResult {
        var rest = header.substring(4).trim()
        val withoutId = rest.uppercase().startsWith("WITHOUT ID")
        if (withoutId) rest = rest.substring(10).trim()

        val entries = notes.map { note ->
            val extra = if (rest.isNotBlank()) evalNoteExpr(rest, note, currentNote) else ""
            val display = when {
                withoutId && extra.isNotBlank() -> extra
                extra.isNotBlank() -> "${note.title} — $extra"
                else -> note.title
            }
            DataviewListEntry(notePath = note.path, noteTitle = note.title, displayText = display)
        }

        val groups = if (groupByExpr.isNotBlank()) {
            val cleanGroup = groupByExpr.replace(Regex("""(?i)\s+AS\s+.*$"""), "").trim()
            notes.indices.groupBy { idx ->
                evalNoteExpr(cleanGroup, notes[idx], currentNote).ifBlank { "Other" }
            }.mapValues { (_, indices) -> indices.map { entries[it] } }
        } else {
            emptyMap()
        }

        return DataviewResult.ListResult(
            items = entries,
            groups = groups,
            executionTimeMs = elapsedMs
        )
    }

    private fun buildCalendarResult(
        header: String,
        notes: List<VaultNote>,
        elapsedMs: Long
    ): DataviewResult.CalendarResult {
        val dateField = header.substring(8).trim().ifBlank { "file.mtime" }
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val map = mutableMapOf<String, MutableList<VaultNote>>()
        notes.forEach { note ->
            val dateVal = when (dateField.lowercase()) {
                "file.mtime", "file.mday", "file.cday", "file.ctime" -> df.format(Date(note.lastModified))
                else -> note.propertyValue(dateField)?.take(10)
            }
            if (dateVal != null && Regex("""\d{4}-\d{2}-\d{2}""").matches(dateVal)) {
                map.getOrPut(dateVal) { mutableListOf() }.add(note)
            }
        }
        return DataviewResult.CalendarResult(
            year = year,
            month = month,
            dateField = dateField,
            notesByDate = map,
            executionTimeMs = elapsedMs
        )
    }

    private fun evalCell(expr: String, note: VaultNote, currentNote: VaultNote?): DataviewCell {
        val value = evalNoteExpr(expr, note, currentNote)
        val lower = expr.trim().lowercase()
        val matchedProp = note.properties.firstOrNull { it.key.equals(expr.trim(), ignoreCase = true) }

        if (lower == "file.tags" || lower == "tags") {
            return DataviewCell.TagListCell(note.tags)
        }
        if (lower == "file.link" || lower == "file.name") {
            return DataviewCell.LinkCell(target = note.path, display = note.title)
        }
        if (matchedProp != null) {
            when (matchedProp.type) {
                PropertyType.STATUS -> return DataviewCell.StatusCell(value)
                PropertyType.RATING -> return DataviewCell.RatingCell(value.toDoubleOrNull() ?: 0.0)
                PropertyType.BOOLEAN -> return DataviewCell.BooleanCell(value.equals("true", ignoreCase = true))
                PropertyType.DATE -> return DataviewCell.DateCell(value)
                PropertyType.NUMBER -> return DataviewCell.NumberCell(value.toDoubleOrNull() ?: 0.0, value)
                PropertyType.LIST -> return DataviewCell.TagListCell(matchedProp.listItems)
                PropertyType.LINK -> {
                    val target = value.removePrefix("[[").removeSuffix("]]").substringBefore("|")
                    val display = value.removePrefix("[[").removeSuffix("]]").substringAfter("|", target)
                    return DataviewCell.LinkCell(target = target, display = display)
                }
                PropertyType.TEXT -> Unit
            }
        }
        if (value.startsWith("[[") && value.endsWith("]]")) {
            val inner = value.removePrefix("[[").removeSuffix("]]")
            val target = inner.substringBefore("|")
            val display = inner.substringAfter("|", target)
            return DataviewCell.LinkCell(target, display)
        }
        if (value == "true" || value == "false") {
            return DataviewCell.BooleanCell(value == "true")
        }
        if (Regex("""^\d{4}-\d{2}-\d{2}$""").matches(value)) {
            return DataviewCell.DateCell(value)
        }
        return DataviewCell.TextCell(value.ifBlank { "—" })
    }

    private fun evalNoteExpr(expr: String, note: VaultNote, currentNote: VaultNote?): String {
        val t = expr.trim()
        // Quoted string literal
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
            return t.substring(1, t.length - 1)
        }

        // Functions
        val fnMatch = Regex("""^([A-Za-z_][A-Za-z0-9_]*)\((.*)\)$""").matchEntire(t)
        if (fnMatch != null) {
            val fn = fnMatch.groupValues[1].lowercase()
            val args = splitTopLevelComma(fnMatch.groupValues[2])
            return when (fn) {
                "choice" -> {
                    val cond = args.getOrNull(0)?.let { evalWhereNote(it, note, currentNote) } ?: false
                    val a = args.getOrNull(1)?.let { evalNoteExpr(it, note, currentNote) } ?: ""
                    val b = args.getOrNull(2)?.let { evalNoteExpr(it, note, currentNote) } ?: ""
                    if (cond) a else b
                }
                "default" -> {
                    val primary = args.getOrNull(0)?.let { evalNoteExpr(it, note, currentNote) } ?: ""
                    val fallback = args.getOrNull(1)?.let { evalNoteExpr(it, note, currentNote) } ?: ""
                    primary.ifBlank { fallback }
                }
                "length" -> {
                    val arg = args.firstOrNull()?.trim() ?: ""
                    when (arg.lowercase()) {
                        "file.tasks" -> note.tasks.size.toString()
                        "file.tags", "tags" -> note.tags.size.toString()
                        "file.outlinks" -> note.outlinks.size.toString()
                        else -> {
                            val prop = note.properties.firstOrNull { it.key.equals(arg, ignoreCase = true) }
                            if (prop != null && prop.listItems.isNotEmpty()) prop.listItems.size.toString()
                            else evalNoteExpr(arg, note, currentNote).length.toString()
                        }
                    }
                }
                "round" -> {
                    val num = args.getOrNull(0)?.let { evalNoteExpr(it, note, currentNote).toDoubleOrNull() } ?: 0.0
                    val digits = args.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                    "%.${digits}f".format(Locale.US, num)
                }
                "date" -> {
                    val arg = args.firstOrNull()?.trim()?.removeSurrounding("\"")?.lowercase() ?: ""
                    if (arg == "today" || arg == "now") todayStr else arg
                }
                "link" -> {
                    val target = args.getOrNull(0)?.let { evalNoteExpr(it, note, currentNote) } ?: ""
                    val display = args.getOrNull(1)?.let { evalNoteExpr(it, note, currentNote) }
                    if (display != null) "[[$target|$display]]" else "[[$target]]"
                }
                "upper" -> args.firstOrNull()?.let { evalNoteExpr(it, note, currentNote).uppercase() } ?: ""
                "lower" -> args.firstOrNull()?.let { evalNoteExpr(it, note, currentNote).lowercase() } ?: ""
                "join" -> {
                    val arg = args.getOrNull(0)?.trim() ?: ""
                    val sep = args.getOrNull(1)?.trim()?.removeSurrounding("\"") ?: ", "
                    if (arg.equals("file.tags", ignoreCase = true) || arg.equals("tags", ignoreCase = true)) {
                        note.tags.joinToString(sep) { "#$it" }
                    } else {
                        evalNoteExpr(arg, note, currentNote)
                    }
                }
                else -> evalNoteExpr(args.firstOrNull() ?: "", note, currentNote)
            }
        }

        // String concatenation `+`
        if (t.contains(" + ")) {
            val parts = t.split(" + ")
            val numericParts = parts.mapNotNull { evalNoteExpr(it, note, currentNote).toDoubleOrNull() }
            if (numericParts.size == parts.size) {
                val sum = numericParts.sum()
                return if (sum % 1.0 == 0.0) sum.toLong().toString() else sum.toString()
            }
            return parts.joinToString("") { evalNoteExpr(it, note, currentNote) }
        }

        // Built-in file.* fields
        return when (t.lowercase()) {
            "file.name" -> note.title
            "file.path" -> note.path
            "file.folder" -> note.folder.ifBlank { "/" }
            "file.link" -> "[[${note.path}|${note.title}]]"
            "file.tags" -> note.tags.joinToString(", ") { "#$it" }
            "file.mtime", "file.mday" -> SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(note.lastModified))
            "file.size", "wordcount" -> note.wordCount.toString()
            "file.tasks.length" -> note.tasks.size.toString()
            "this.file.name" -> currentNote?.title ?: ""
            "this.file.path" -> currentNote?.path ?: ""
            else -> {
                if (t.startsWith("this.", ignoreCase = true) && currentNote != null) {
                    val key = t.substring(5)
                    currentNote.propertyValue(key) ?: ""
                } else {
                    note.propertyValue(t) ?: ""
                }
            }
        }
    }

    private fun evalWhereNote(whereExpr: String, note: VaultNote, currentNote: VaultNote?): Boolean {
        val orParts = splitLogical(whereExpr, "OR")
        return orParts.any { orPart ->
            val andParts = splitLogical(orPart, "AND")
            andParts.all { clause -> evalAtomicWhereNote(clause.trim(), note, currentNote) }
        }
    }

    private fun evalAtomicWhereNote(clause: String, note: VaultNote, currentNote: VaultNote?): Boolean {
        val clean = clause.trim().removePrefix("(").removeSuffix(")").trim()
        if (clean.isEmpty()) return true
        if (clean.startsWith("!") || clean.uppercase().startsWith("NOT ")) {
            val inner = if (clean.startsWith("!")) clean.substring(1) else clean.substring(4)
            return !evalAtomicWhereNote(inner, note, currentNote)
        }

        // contains / icontains / startswith / endswith
        val fnMatch = Regex("""(?i)^(contains|icontains|startswith|endswith)\((.+),(.+)\)$""").matchEntire(clean)
        if (fnMatch != null) {
            val fn = fnMatch.groupValues[1].lowercase()
            val leftRaw = fnMatch.groupValues[2].trim()
            val rightVal = evalNoteExpr(fnMatch.groupValues[3].trim(), note, currentNote)
            val leftList = when (leftRaw.lowercase()) {
                "file.tags", "tags" -> note.tags
                "file.outlinks" -> note.outlinks
                else -> note.properties.firstOrNull { it.key.equals(leftRaw, ignoreCase = true) }?.listItems ?: emptyList()
            }
            if (leftList.isNotEmpty() && (fn == "contains" || fn == "icontains")) {
                val needle = rightVal.removePrefix("#")
                return leftList.any {
                    if (fn == "icontains") it.contains(needle, ignoreCase = true) else it == needle || it.contains(needle)
                }
            }
            val leftVal = evalNoteExpr(leftRaw, note, currentNote)
            return when (fn) {
                "contains" -> leftVal.contains(rightVal)
                "icontains" -> leftVal.contains(rightVal, ignoreCase = true)
                "startswith" -> leftVal.startsWith(rightVal, ignoreCase = true)
                "endswith" -> leftVal.endsWith(rightVal, ignoreCase = true)
                else -> false
            }
        }

        // Comparison operators
        val ops = listOf("!=", ">=", "<=", "=", ">", "<")
        for (op in ops) {
            val idx = clean.indexOf(op)
            if (idx > 0) {
                val left = evalNoteExpr(clean.substring(0, idx), note, currentNote)
                val right = evalNoteExpr(clean.substring(idx + op.length), note, currentNote)
                return compareValues(left, right, op)
            }
        }

        // Truthy property check (e.g. `WHERE rating` or `WHERE active`)
        val valStr = evalNoteExpr(clean, note, currentNote)
        return valStr.isNotBlank() && !valStr.equals("false", ignoreCase = true) && valStr != "0"
    }

    private fun evalWhereTask(whereExpr: String, task: VaultTask, note: VaultNote, currentNote: VaultNote?): Boolean {
        val orParts = splitLogical(whereExpr, "OR")
        return orParts.any { orPart ->
            val andParts = splitLogical(orPart, "AND")
            andParts.all { clause ->
                val clean = clause.trim().removePrefix("(").removeSuffix(")").trim()
                val lower = clean.lowercase()
                when {
                    lower == "!completed" || lower == "not completed" -> !task.completed
                    lower == "completed" -> task.completed
                    lower.startsWith("contains(") || lower.startsWith("icontains(") -> {
                        val fnMatch = Regex("""(?i)^(i?contains)\((.+),(.+)\)$""").matchEntire(clean)
                        if (fnMatch != null) {
                            val left = evalTaskExpr(fnMatch.groupValues[2], task, note, currentNote)
                            val right = fnMatch.groupValues[3].trim().removeSurrounding("\"")
                            left.contains(right, ignoreCase = true)
                        } else true
                    }
                    else -> {
                        val ops = listOf("!=", ">=", "<=", "=", ">", "<")
                        val matchedOp = ops.firstOrNull { clean.contains(it) }
                        if (matchedOp != null) {
                            val idx = clean.indexOf(matchedOp)
                            val left = evalTaskExpr(clean.substring(0, idx), task, note, currentNote)
                            val right = evalTaskExpr(clean.substring(idx + matchedOp.length), task, note, currentNote)
                            compareValues(left, right, matchedOp)
                        } else {
                            evalWhereNote(clean, note, currentNote)
                        }
                    }
                }
            }
        }
    }

    private fun evalTaskExpr(expr: String, task: VaultTask, note: VaultNote, currentNote: VaultNote?): String {
        return when (val t = expr.trim().lowercase()) {
            "text" -> task.text
            "completed" -> task.completed.toString()
            "due" -> task.dueDate ?: ""
            "priority" -> task.priority ?: ""
            "tags" -> task.tags.joinToString(", ") { "#$it" }
            else -> task.inlineFields[t] ?: evalNoteExpr(expr, note, currentNote)
        }
    }

    private fun compareValues(left: String, right: String, op: String): Boolean {
        val lNum = left.toDoubleOrNull()
        val rNum = right.toDoubleOrNull()
        val cmp = if (lNum != null && rNum != null) {
            lNum.compareTo(rNum)
        } else {
            left.lowercase().compareTo(right.lowercase())
        }
        return when (op) {
            "=" -> left.equals(right, ignoreCase = true)
            "!=" -> !left.equals(right, ignoreCase = true)
            ">" -> cmp > 0
            "<" -> cmp < 0
            ">=" -> cmp >= 0
            "<=" -> cmp <= 0
            else -> false
        }
    }

    private fun sortNotes(notes: List<VaultNote>, sortExpr: String, currentNote: VaultNote?): List<VaultNote> {
        val specs = splitTopLevelComma(sortExpr).map { spec ->
            val parts = spec.trim().split(Regex("""\s+"""))
            val desc = parts.lastOrNull()?.equals("DESC", ignoreCase = true) == true
            val field = if (parts.lastOrNull()?.uppercase() in listOf("ASC", "DESC")) {
                parts.dropLast(1).joinToString(" ")
            } else {
                spec.trim()
            }
            field to desc
        }
        return notes.sortedWith { a, b ->
            for ((field, desc) in specs) {
                val va = evalNoteExpr(field, a, currentNote)
                val vb = evalNoteExpr(field, b, currentNote)
                val na = va.toDoubleOrNull()
                val nb = vb.toDoubleOrNull()
                val cmp = if (na != null && nb != null) na.compareTo(nb) else va.lowercase().compareTo(vb.lowercase())
                if (cmp != 0) return@sortedWith if (desc) -cmp else cmp
            }
            0
        }
    }

    private fun sortTasks(
        tasks: List<Pair<VaultTask, VaultNote>>,
        sortExpr: String
    ): List<Pair<VaultTask, VaultNote>> {
        val parts = sortExpr.trim().split(Regex("""\s+"""))
        val desc = parts.lastOrNull()?.equals("DESC", ignoreCase = true) == true
        val field = if (parts.lastOrNull()?.uppercase() in listOf("ASC", "DESC")) {
            parts.dropLast(1).joinToString(" ")
        } else sortExpr.trim()

        return tasks.sortedWith { (ta, na), (tb, nb) ->
            val va = evalTaskExpr(field, ta, na, null)
            val vb = evalTaskExpr(field, tb, nb, null)
            val cmp = va.lowercase().compareTo(vb.lowercase())
            if (desc) -cmp else cmp
        }
    }

    private fun splitLogical(input: String, op: String): List<String> {
        val regex = Regex("""\s+${op}\s+""", RegexOption.IGNORE_CASE)
        return input.split(regex)
    }

    private fun splitTopLevelComma(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var inQuotes = false
        for (ch in input) {
            when (ch) {
                '"' -> {
                    inQuotes = !inQuotes
                    current.append(ch)
                }
                '(' -> {
                    if (!inQuotes) depth++
                    current.append(ch)
                }
                ')' -> {
                    if (!inQuotes && depth > 0) depth--
                    current.append(ch)
                }
                ',' -> {
                    if (!inQuotes && depth == 0) {
                        result.add(current.toString().trim())
                        current.setLength(0)
                    } else {
                        current.append(ch)
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotBlank()) result.add(current.toString().trim())
        return result
    }
}
