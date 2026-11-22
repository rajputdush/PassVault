package com.ritvik.passvault.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritvik.passvault.autofill.AppIds
import com.ritvik.passvault.search.FuzzySearch
import com.ritvik.passvault.ui.AppTextField

/** An app's real launcher icon, or a coloured letter when the app is not installed on this phone. */
@Composable
fun AppIcon(packageName: String, label: String, size: Dp = 40.dp) {
    val context = LocalContext.current
    var icon by remember(packageName) { mutableStateOf(InstalledApps.peekIcon(packageName)) }
    LaunchedEffect(packageName) {
        if (icon == null) icon = InstalledApps.icon(context, packageName)
    }
    val shape = RoundedCornerShape(size * 0.22f)
    val image = icon
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = Modifier.size(size).clip(shape))
    } else {
        Box(
            modifier = Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.secondary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                color = MaterialTheme.colorScheme.onSecondary,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Pick one of the apps on this phone by searching its name. A package name typed in the search box can be used as it is. */
@Composable
fun AppPickerDialog(onPick: (AppEntry) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = InstalledApps.list(context) }

    val shown = remember(apps, query) {
        apps.orEmpty().filter { FuzzySearch.matches(query, listOf(it.label, it.packageName)) }
    }
    val typedPackage = query.trim().takeIf { AppIds.linkable(it) != null && shown.none { app -> app.packageName == it } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an app") },
        text = {
            Column {
                AppTextField(value = query, onValueChange = { query = it }, label = "Search your apps", modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    apps == null -> Text("Loading…", style = MaterialTheme.typography.bodyMedium)
                    shown.isEmpty() && typedPackage == null -> Text(
                        "No app matches. You can also type an app's package name, such as com.netflix.mediaclient.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        if (typedPackage != null) {
                            item {
                                AppRow(AppEntry("Use \"$typedPackage\"", typedPackage), onClick = { onPick(AppEntry(AppIds.readableName(typedPackage), typedPackage)) })
                            }
                        }
                        items(shown, key = { it.packageName }) { app ->
                            AppRow(app, onClick = { onPick(app) })
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AppRow(app: AppEntry, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(app.packageName, app.label, 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider()
    }
}
