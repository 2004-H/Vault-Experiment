package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.example.data.model.NoteProperty
import com.example.data.model.PropertySource
import com.example.data.model.PropertyType
import com.example.domain.parser.MarkdownParser
import com.example.ui.theme.AmberProperty
import com.example.ui.theme.EmeraldDone
import com.example.ui.theme.TealQuery
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RapidPropertyGrid(
    properties: List<NoteProperty>,
    onQuickEditProperty: (NoteProperty) -> Unit,
    onToggleBooleanProperty: (NoteProperty) -> Unit,
    onAddPropertyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RAPID METADATA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onAddPropertyClick)
                        .testTag("add_property_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add property",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Property",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (properties.isEmpty()) {
                Text(
                    text = "No YAML frontmatter or inline:: fields yet. Tap + Property to add typed metadata.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    properties.forEach { prop ->
                        RapidPropertyChip(
                            property = prop,
                            onClick = {
                                if (prop.type == PropertyType.BOOLEAN) {
                                    onToggleBooleanProperty(prop)
                                } else {
                                    onQuickEditProperty(prop)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RapidPropertyChip(
    property: NoteProperty,
    onClick: () -> Unit
) {
    val accent = when (property.type) {
        PropertyType.STATUS -> TealQuery
        PropertyType.RATING -> AmberProperty
        PropertyType.BOOLEAN -> if (property.value.equals("true", true)) EmeraldDone else MaterialTheme.colorScheme.onSurfaceVariant
        PropertyType.DATE -> TealQuery
        PropertyType.NUMBER -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("property_chip_${property.key}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = property.key,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (property.source == PropertySource.INLINE) "::" else ":",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.width(6.dp))
            when (property.type) {
                PropertyType.RATING -> {
                    val stars = (property.value.toDoubleOrNull()?.toInt() ?: 0).coerceIn(0, 5)
                    Row {
                        repeat(stars.coerceAtLeast(1)) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = AmberProperty,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
                PropertyType.STATUS -> StatusPill(status = property.value)
                else -> {
                    Text(
                        text = property.value.ifBlank { "—" },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PropertyEditBottomSheet(
    property: NoteProperty,
    onDismiss: () -> Unit,
    onSave: (NoteProperty) -> Unit,
    onDelete: () -> Unit
) {
    var draftValue by remember(property) { mutableStateOf(property.value) }
    var draftList by remember(property) { mutableStateOf(property.listItems) }
    var newTagInput by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Edit Property",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${property.key} (${property.type.name.lowercase()})",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_property_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete property",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            when (property.type) {
                PropertyType.STATUS -> {
                    val presets = listOf("backlog", "active", "review", "done", "blocked")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { option ->
                            FilterChip(
                                selected = draftValue.equals(option, ignoreCase = true),
                                onClick = {
                                    draftValue = option
                                    onSave(property.copy(value = option))
                                },
                                label = { Text(option) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = draftValue,
                        onValueChange = { draftValue = it },
                        label = { Text("Custom status") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                PropertyType.RATING -> {
                    val current = (draftValue.toDoubleOrNull()?.toInt() ?: 0).coerceIn(0, 5)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (star in 1..5) {
                            IconButton(
                                onClick = {
                                    draftValue = star.toString()
                                    onSave(property.copy(value = star.toString()))
                                },
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(
                                    imageVector = if (star <= current) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                    contentDescription = "$star stars",
                                    tint = if (star <= current) AmberProperty else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }
                }

                PropertyType.NUMBER -> {
                    val currentNum = draftValue.toDoubleOrNull() ?: 0.0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val next = (currentNum - 5).coerceAtLeast(0.0)
                                draftValue = if (next % 1.0 == 0.0) next.toLong().toString() else next.toString()
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Decrease")
                        }
                        OutlinedTextField(
                            value = draftValue,
                            onValueChange = { draftValue = it },
                            label = { Text("Value") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                val next = currentNum + 5
                                draftValue = if (next % 1.0 == 0.0) next.toLong().toString() else next.toString()
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Increase")
                        }
                    }
                }

                PropertyType.DATE -> {
                    val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val today = df.format(Date())
                    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.let { df.format(it.time) }
                    val nextWeek = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }.let { df.format(it.time) }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Today" to today, "Tomorrow" to tomorrow, "+7 days" to nextWeek).forEach { (label, dateStr) ->
                            FilterChip(
                                selected = draftValue == dateStr,
                                onClick = {
                                    draftValue = dateStr
                                    onSave(property.copy(value = dateStr))
                                },
                                label = { Text("$label ($dateStr)") }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = draftValue,
                        onValueChange = { draftValue = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                PropertyType.LIST -> {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        draftList.forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                ) {
                                    Text(
                                        text = item,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    IconButton(
                                        onClick = { draftList = draftList - item },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove $item",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newTagInput,
                            onValueChange = { newTagInput = it },
                            label = { Text("Add item") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val cleaned = newTagInput.trim().removePrefix("#")
                                if (cleaned.isNotEmpty() && cleaned !in draftList) {
                                    draftList = draftList + cleaned
                                    newTagInput = ""
                                }
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Add")
                        }
                    }
                }

                PropertyType.BOOLEAN -> {
                    val checked = draftValue.equals("true", true)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enabled", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = checked,
                            onCheckedChange = { draftValue = it.toString() }
                        )
                    }
                }

                else -> {
                    if (property.key.equals("priority", ignoreCase = true)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("low", "medium", "high").forEach { opt ->
                                FilterChip(
                                    selected = draftValue.equals(opt, true),
                                    onClick = {
                                        draftValue = opt
                                        onSave(property.copy(value = opt))
                                    },
                                    label = { Text(opt) }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    OutlinedTextField(
                        value = draftValue,
                        onValueChange = { draftValue = it },
                        label = { Text("Value") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val updated = if (property.type == PropertyType.LIST) {
                            property.copy(value = draftList.joinToString(", "), listItems = draftList)
                        } else {
                            property.copy(value = draftValue.trim())
                        }
                        onSave(updated)
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("save_property_button")
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Apply & Sync")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddPropertyDialog(
    onDismiss: () -> Unit,
    onAdd: (NoteProperty) -> Unit
) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(PropertySource.FRONTMATTER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Metadata Property") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("status", "priority", "rating", "due", "owner", "progress").forEach { preset ->
                        FilterChip(
                            selected = key.equals(preset, true),
                            onClick = {
                                key = preset
                                if (value.isBlank()) {
                                    value = when (preset) {
                                        "status" -> "active"
                                        "priority" -> "high"
                                        "rating" -> "5"
                                        "progress" -> "50"
                                        "due" -> SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                        else -> ""
                                    }
                                }
                            },
                            label = { Text(preset) }
                        )
                    }
                }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Key (e.g. status, due, rating)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Value") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = source == PropertySource.FRONTMATTER,
                        onClick = { source = PropertySource.FRONTMATTER },
                        label = { Text("YAML Frontmatter") }
                    )
                    FilterChip(
                        selected = source == PropertySource.INLINE,
                        onClick = { source = PropertySource.INLINE },
                        label = { Text("Inline key::") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (key.isNotBlank()) {
                        onAdd(MarkdownParser.classifyProperty(key.trim(), value.trim(), source))
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
