package com.ritvik.passvault.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.ritvik.passvault.ui.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ritvik.passvault.items.TRASH_DAYS
import com.ritvik.passvault.items.VaultItem
import com.ritvik.passvault.ui.SectionCard
import com.ritvik.passvault.ui.ScreenColumn

/** Deleted items. They can be restored for [TRASH_DAYS] days, then they are removed for good. */
@Composable
fun TrashScreen(
    items: List<VaultItem>,
    onRestore: (String) -> Unit,
    onDeleteForever: (String) -> Unit,
    onEmpty: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmOne by remember { mutableStateOf<VaultItem?>(null) }

    ScreenColumn {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Trash", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }

        if (items.isEmpty()) {
            Text("Trash is empty.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                "Items are removed for good after $TRASH_DAYS days.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val now = System.currentTimeMillis()
            items.forEach { item ->
                val daysLeft = (TRASH_DAYS - (now - item.deletedAt) / DAY_MS).coerceIn(0, TRASH_DAYS.toLong())
                SectionCard(item.displayTitle) {
                    val line = item.subtitle.ifBlank { item.type.label }
                    Text(
                        "$line  ·  $daysLeft day${if (daysLeft == 1L) "" else "s"} left",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onRestore(item.id) }) { Text("Restore") }
                        TextButton(onClick = { confirmOne = item }) {
                            Text("Delete forever", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            TextButton(onClick = { confirmEmpty = true }) { Text("Empty Trash", color = MaterialTheme.colorScheme.error) }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty Trash?") },
            text = { Text("All ${items.size} item(s) will be removed for good. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    onEmpty()
                    onBack()
                }) { Text("Empty Trash", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
        )
    }
    confirmOne?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmOne = null },
            title = { Text("Delete forever?") },
            text = { Text("\"${item.displayTitle}\" will be removed for good. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmOne = null
                    onDeleteForever(item.id)
                }) { Text("Delete forever", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmOne = null }) { Text("Cancel") } },
        )
    }
}

private const val DAY_MS = 24L * 60 * 60 * 1000
