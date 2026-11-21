package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DataviewCell
import com.example.data.model.DataviewJsRenderNode
import com.example.data.model.DataviewResult
import com.example.data.model.VaultTask
import com.example.ui.theme.AmberProperty
import com.example.ui.theme.EmeraldDone
import com.example.ui.theme.RosePriority
import com.example.ui.theme.TealQuery
import java.util.Calendar
import java.util.Locale

@Composable
fun DataviewResultView(
    result: DataviewResult,
    onNoteClick: (String) -> Unit,
    onToggleTask: (VaultTask, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    when (result) {
        is DataviewResult.TableResult -> DataviewTableView(result, onNoteClick, modifier)
        is DataviewResult.ListResult -> DataviewListView(result, onNoteClick, modifier)
        is DataviewResult.TaskResult -> DataviewTaskView(result, onNoteClick, onToggleTask, modifier)
        is DataviewResult.CalendarResult -> DataviewCalendarView(result, onNoteClick, modifier)
        is DataviewResult.ErrorResult -> DataviewErrorBanner(result.message, modifier)
    }
}

@Composable
private fun DataviewTableView(
    result: DataviewResult.TableResult,
    onNoteClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (result.rows.isEmpty()) {
        EmptyResultNotice("Query returned 0 matching rows.", modifier)
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .horizontalScroll(scrollState)
        ) {
            // Table Header
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                result.headers.forEachIndexed { idx, header ->
                    Text(
                        text = header.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(min = if (idx == 0) 148.dp else 96.dp, max = 210.dp)
                            .padding(end = 12.dp)
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Rows
            result.rows.forEachIndexed { rIdx, row ->
                Row(
                    modifier = Modifier
                        .clickable { onNoteClick(row.notePath) }
                        .background(
                            if (rIdx % 2 == 1) MaterialTheme.colorScheme.surfaceContainerLow
                            else MaterialTheme.colorScheme.surface
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.cells.forEachIndexed { cIdx, cell ->
                        Box(
                            modifier = Modifier
                                .widthIn(min = if (cIdx == 0) 148.dp else 96.dp, max = 210.dp)
                                .padding(end = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            DataviewCellView(cell = cell, onNoteClick = onNoteClick)
                        }
                    }
                }
                if (rIdx < result.rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${result.rows.size} rows · ${result.executionTimeMs}ms",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataviewCellView(
    cell: DataviewCell,
    onNoteClick: (String) -> Unit
) {
    when (cell) {
        is DataviewCell.LinkCell -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onNoteClick(cell.target) }
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = cell.display,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        is DataviewCell.StatusCell -> {
            StatusPill(status = cell.status)
        }
        is DataviewCell.RatingCell -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val filled = cell.rating.toInt().coerceIn(0, 5)
                repeat(5) { idx ->
                    Icon(
                        imageVector = if (idx < filled) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = null,
                        tint = if (idx < filled) AmberProperty else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        is DataviewCell.BooleanCell -> {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (cell.value) EmeraldDone.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = if (cell.value) "true" else "false",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (cell.value) EmeraldDone else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        is DataviewCell.TagListCell -> {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                cell.tags.take(3).forEach { tag ->
                    val clean = tag.removePrefix("#")
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "#$clean",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
        is DataviewCell.DateCell -> {
            Text(
                text = cell.dateText,
                style = MaterialTheme.typography.labelMedium,
                color = TealQuery
            )
        }
        is DataviewCell.NumberCell -> {
            Text(
                text = cell.formatted,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        is DataviewCell.TextCell -> {
            Text(
                text = cell.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DataviewListView(
    result: DataviewResult.ListResult,
    onNoteClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (result.items.isEmpty()) {
        EmptyResultNotice("List query matched 0 notes.", modifier)
        return
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (result.groups.isNotEmpty()) {
            result.groups.forEach { (groupName, entries) ->
                Text(
                    text = groupName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
                entries.forEach { entry ->
                    DataviewListRow(entry.displayText, onClick = { onNoteClick(entry.notePath) })
                }
            }
        } else {
            result.items.forEach { entry ->
                DataviewListRow(entry.displayText, onClick = { onNoteClick(entry.notePath) })
            }
        }
        Text(
            text = "${result.items.size} items · ${result.executionTimeMs}ms",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DataviewListRow(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = "Open block page",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun DataviewTaskView(
    result: DataviewResult.TaskResult,
    onNoteClick: (String) -> Unit,
    onToggleTask: (VaultTask, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (result.groups.isEmpty()) {
        EmptyResultNotice("No matching tasks found.", modifier)
        return
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        result.groups.forEach { (groupTitle, tasks) ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = groupTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    tasks.forEach { task ->
                        InteractiveTaskRow(
                            task = task,
                            onToggle = { onToggleTask(task, !task.completed) },
                            onOpenNote = { onNoteClick(task.notePath) }
                        )
                    }
                }
            }
        }
        val total = result.groups.values.sumOf { it.size }
        Text(
            text = "$total tasks · ${result.executionTimeMs}ms",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun InteractiveTaskRow(
    task: VaultTask,
    onToggle: () -> Unit,
    onOpenNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("task_toggle_${task.lineIndex}"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (task.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (task.completed) "Mark task incomplete" else "Mark task complete",
                tint = if (task.completed) EmeraldDone else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.text.replace(Regex("""\[[A-Za-z0-9_\-]+::[^\]]+\]"""), "").trim(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            if (task.dueDate != null || task.priority != null) {
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    task.dueDate?.let { due ->
                        Text(
                            text = "due $due",
                            style = MaterialTheme.typography.labelSmall,
                            color = TealQuery
                        )
                    }
                    task.priority?.let { pri ->
                        val color = when (pri.lowercase()) {
                            "high" -> RosePriority
                            "medium" -> AmberProperty
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = pri.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenNote),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = "Open source note ${task.noteTitle}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun DataviewCalendarView(
    result: DataviewResult.CalendarResult,
    onNoteClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, result.year)
        set(Calendar.MONTH, result.month - 1)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOffset = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY).coerceAtLeast(0)
    val monthLabel = String.format(Locale.US, "%04d-%02d", result.year, result.month)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = TealQuery,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Calendar · $monthLabel (${result.dateField})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(10.dp))
            val weekDays = listOf("S", "M", "T", "W", "T", "F", "S")
            Row(modifier = Modifier.fillMaxWidth()) {
                weekDays.forEach { d ->
                    Text(
                        text = d,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            val totalCells = firstDayOffset + daysInMonth
            val rows = (totalCells + 6) / 7
            for (r in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until 7) {
                        val cellIndex = r * 7 + c
                        val dayNum = cellIndex - firstDayOffset + 1
                        if (dayNum in 1..daysInMonth) {
                            val key = String.format(Locale.US, "%04d-%02d-%02d", result.year, result.month, dayNum)
                            val notesOnDay = result.notesByDate[key].orEmpty()
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (notesOnDay.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .clickable(enabled = notesOnDay.isNotEmpty()) {
                                        notesOnDay.firstOrNull()?.let { onNoteClick(it.path) }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = dayNum.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (notesOnDay.isNotEmpty()) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (notesOnDay.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(TealQuery)
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f).height(38.dp))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun DataviewJsResultView(
    nodes: List<DataviewJsRenderNode>,
    onNoteClick: (String) -> Unit,
    onToggleTask: (VaultTask, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        nodes.forEach { node ->
            when (node) {
                is DataviewJsRenderNode.JsHeader -> {
                    Text(
                        text = node.text,
                        style = if (node.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
                is DataviewJsRenderNode.JsParagraph -> {
                    Text(
                        text = node.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                is DataviewJsRenderNode.JsSpan -> {
                    Text(
                        text = node.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is DataviewJsRenderNode.JsList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        node.items.forEach { raw ->
                            val parsed = parseWikiLinkOrText(raw)
                            DataviewListRow(
                                text = parsed.second,
                                onClick = {
                                    if (parsed.first != null) onNoteClick(parsed.first!!)
                                }
                            )
                        }
                    }
                }
                is DataviewJsRenderNode.JsTable -> {
                    val rows = node.rows.map { row ->
                        val firstTarget = row.firstOrNull()?.let { parseWikiLinkOrText(it).first } ?: ""
                        val cells = row.map { rawCell ->
                            val (target, display) = parseWikiLinkOrText(rawCell)
                            if (target != null) DataviewCell.LinkCell(target, display)
                            else if (display.lowercase() in listOf("active", "done", "review", "backlog", "blocked")) {
                                DataviewCell.StatusCell(display)
                            } else {
                                DataviewCell.TextCell(display)
                            }
                        }
                        com.example.data.model.DataviewTableRow(firstTarget, cells)
                    }
                    DataviewTableView(
                        result = DataviewResult.TableResult(node.headers, rows, 0),
                        onNoteClick = { target -> if (target.isNotBlank()) onNoteClick(target) }
                    )
                }
                is DataviewJsRenderNode.JsTaskList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        node.tasks.forEach { task ->
                            InteractiveTaskRow(
                                task = task,
                                onToggle = { onToggleTask(task, !task.completed) },
                                onOpenNote = { onNoteClick(task.notePath) }
                            )
                        }
                    }
                }
                is DataviewJsRenderNode.JsError -> {
                    DataviewErrorBanner(node.message)
                }
            }
        }
    }
}

@Composable
fun StatusPill(status: String, modifier: Modifier = Modifier) {
    val lower = status.lowercase().trim()
    val (bg, fg) = when (lower) {
        "active", "in-progress", "doing" -> TealQuery.copy(alpha = 0.18f) to TealQuery
        "done", "complete", "completed" -> EmeraldDone.copy(alpha = 0.18f) to EmeraldDone
        "review", "waiting" -> AmberProperty.copy(alpha = 0.18f) to AmberProperty
        "blocked", "urgent", "high" -> RosePriority.copy(alpha = 0.18f) to RosePriority
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = modifier
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun DataviewErrorBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = "Query error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun EmptyResultNotice(message: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
    }
}

private fun parseWikiLinkOrText(raw: String): Pair<String?, String> {
    val trimmed = raw.trim()
    val match = Regex("""^\[\[([^\]|]+)(?:\|([^\]]+))?\]\]$""").matchEntire(trimmed)
    return if (match != null) {
        val target = match.groupValues[1].trim()
        val display = match.groupValues[2].trim().ifBlank { target.substringAfterLast("/").removeSuffix(".md") }
        target to display
    } else {
        null to trimmed
    }
}
