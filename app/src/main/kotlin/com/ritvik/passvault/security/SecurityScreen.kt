package com.ritvik.passvault.security

import androidx.compose.foundation.BorderStroke
import com.ritvik.passvault.ui.ScreenTitle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.ritvik.passvault.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.GppMaybe
import com.ritvik.passvault.ui.InfoButton
import com.ritvik.passvault.ui.IconBadge
import com.ritvik.passvault.crypto.AuditLabels
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ritvik.passvault.crypto.AuditEntry
import com.ritvik.passvault.crypto.PasswordAudit
import com.ritvik.passvault.cards.CardInfo
import com.ritvik.passvault.export.DuplicateCandidate
import com.ritvik.passvault.export.DuplicateFinder
import com.ritvik.passvault.items.ItemType
import com.ritvik.passvault.items.VaultItem
import com.ritvik.passvault.items.logins
import com.ritvik.passvault.ui.success
import com.ritvik.passvault.ui.warning
import com.ritvik.passvault.ui.ErrorText
import com.ritvik.passvault.ui.PrimaryButton
import com.ritvik.passvault.ui.ScreenColumn
import com.ritvik.passvault.ui.SectionCard

/**
 * The small pill in the vault header that opens the security check. With problems it is red with a shield, an
 * exclamation mark and how many passwords need attention; with none it is a green shield with a tick.
 */
@Composable
fun SecurityPill(attentionCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val problem = attentionCount > 0
    val accent = if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.success
    val description = when {
        attentionCount == 0 -> "Security check: no problems found"
        attentionCount == 1 -> "Security check: 1 password needs attention"
        else -> "Security check: $attentionCount passwords need attention"
    }
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            if (problem) Icons.Filled.GppMaybe else Icons.Filled.VerifiedUser,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
        if (problem) {
            Text(
                if (attentionCount > 99) "99+" else attentionCount.toString(),
                color = accent,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** The vault score as a ring with the number inside. */
@Composable
private fun ScoreRing(score: Int, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val arc = MaterialTheme.colorScheme.primary
    Box(modifier = modifier.size(76.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            val size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, topLeft, size, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawArc(
                arc, -90f, 360f * score / 100f, false, topLeft, size,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
        Text(score.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecurityHost(
    items: List<VaultItem>,
    onOpenItem: (VaultItem) -> Unit,
    onTrashItems: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: SecurityViewModel = viewModel()
    val breach by viewModel.state.collectAsState()

    val logins = remember(items) { items.logins() }
    val entries = remember(logins) { logins.map { AuditEntry(it.id, it.displayTitle, it.password, it.passwordAge) } }
    val audit = remember(entries) { PasswordAudit.analyze(entries, System.currentTimeMillis()) }
    val byId = remember(items) { items.associateBy { it.id } }
    // Cards that have expired or will within two months, soonest first. Needs a readable expiry date on the card.
    val cardsDue = remember(items) {
        val now = java.util.Calendar.getInstance()
        val year = now.get(java.util.Calendar.YEAR)
        val month = now.get(java.util.Calendar.MONTH) + 1
        items.filter { it.type == ItemType.CARD }
            .mapNotNull { card ->
                CardInfo.parseExpiry(card.cardExpiry)?.let { (m, y) ->
                    val monthsLeft = (y - year) * 12 + (m - month)
                    if (monthsLeft <= 2) card to monthsLeft else null
                }
            }
            .sortedBy { it.second }
    }

    val duplicates = remember(items) {
        DuplicateFinder.find(
            items.map {
                DuplicateCandidate(
                    id = it.id, isLogin = it.type == ItemType.LOGIN, isCard = it.type == ItemType.CARD, url = it.url,
                    username = it.username, password = it.password, cardNumber = it.cardNumber, updatedAt = it.updatedAt,
                )
            },
        )
    }

    // Breach results are forgotten as soon as this screen is left (and so also when the vault locks).
    DisposableEffect(Unit) { onDispose { viewModel.reset() } }

    ScreenColumn {
        ScreenTitle("Health", "Security check")

        val attention = audit.attentionIds.size
        val score = if (logins.isEmpty()) 100 else (100 * (logins.size - minOf(attention, logins.size)) / logins.size)
        val scoreShape = RoundedCornerShape(24.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(scoreShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            (if (attention > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.success).copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                )
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, scoreShape)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScoreRing(score)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Vault score", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        logins.isEmpty() -> "Save a login to see how healthy your passwords are."
                        attention == 0 -> "Nothing weak, reused or missing."
                        attention == 1 -> "1 password needs attention. Fix it to reach 100."
                        else -> "$attention passwords need attention. Fix them to reach 100."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val error = MaterialTheme.colorScheme.error
        val amber = MaterialTheme.colorScheme.warning
        val muted = MaterialTheme.colorScheme.onSurfaceVariant

        if (audit.weak.isNotEmpty()) {
            IssueCard(Icons.Filled.Warning, error, "Weak passwords", audit.weak.size) {
                IssueList(audit.weak) { f ->
                    IssueRow(
                        title = f.title.ifBlank { "(no title)" },
                        note = AuditLabels.weakReason(f.reasons),
                        onClick = { byId[f.id]?.let(onOpenItem) },
                    )
                }
            }
        }

        if (audit.reused.isNotEmpty()) {
            // One row per account; "x3" says how many accounts share that password.
            val reusedRows = audit.reused.flatMap { group -> group.ids.mapIndexed { i, id -> Triple(id, group.titles[i], group.ids.size) } }
            IssueCard(Icons.Filled.ContentCopy, amber, "Reused passwords", reusedRows.size) {
                IssueList(reusedRows) { (id, title, shared) ->
                    IssueRow(
                        title = title.ifBlank { "(no title)" },
                        note = "x$shared",
                        onClick = { byId[id]?.let(onOpenItem) },
                    )
                }
            }
        }

        if (audit.old.isNotEmpty()) {
            IssueCard(Icons.Filled.History, muted, "Older than 1 year", audit.old.size) {
                IssueList(audit.old) { f ->
                    IssueRow(title = f.title.ifBlank { "(no title)" }, onClick = { byId[f.id]?.let(onOpenItem) })
                }
            }
        }

        if (audit.empty.isNotEmpty()) {
            IssueCard(Icons.Filled.Lock, muted, "No password saved", audit.empty.size) {
                IssueList(audit.empty) { f ->
                    IssueRow(title = f.title.ifBlank { "(no title)" }, action = "Add", onClick = { byId[f.id]?.let(onOpenItem) })
                }
            }
        }

        if (duplicates.isNotEmpty()) {
            val duplicateRows = duplicates.flatMap { group -> group.ids.mapNotNull { id -> byId[id]?.let { Triple(it, group, id) } } }
            IssueCard(
                Icons.Filled.ContentCopy, muted, "Duplicates", duplicateRows.size,
                info = "The same login (same website and username) or the same card saved more than once. \"Move extras to Trash\" keeps the most recently changed copy and puts the others in the Trash, where you can restore them for 30 days. Copies with different passwords are only shown, so you can pick the right one yourself.",
            ) {
                IssueList(duplicateRows) { (item, group, id) ->
                    IssueRow(
                        title = item.displayTitle.ifBlank { "(no title)" },
                        note = when {
                            id == group.keepId -> "Newest"
                            group.isCard || group.identical -> "Same"
                            else -> "Differs"
                        },
                        onClick = { onOpenItem(item) },
                    )
                }
            }
            val extras = duplicates.filter { it.identical }.flatMap { g -> g.ids.filter { it != g.keepId } }
            if (extras.isNotEmpty()) {
                PrimaryButton(text = "Move ${extras.size} extra copy(ies) to Trash", onClick = { onTrashItems(extras) })
            }
        }

        if (cardsDue.isNotEmpty()) {
            IssueCard(Icons.Filled.DateRange, amber, "Cards expiring soon", cardsDue.size) {
                IssueList(cardsDue) { (card, monthsLeft) ->
                    IssueRow(
                        title = card.displayTitle,
                        note = if (monthsLeft < 0) "Expired ${card.cardExpiry}" else card.cardExpiry,
                        noteColor = if (monthsLeft < 0) error else Color.Unspecified,
                        action = null,
                        onClick = { onOpenItem(card) },
                    )
                }
            }
        }

        SectionCard(
            "Data breaches",
            info = "Checks whether your passwords appear in known data leaks, using the Have I Been Pwned service. Your passwords never leave this phone: for each one, only the first 5 characters of a one-way fingerprint (SHA-1 hash) are sent, and the comparison is done here. This needs an internet connection.",
        ) {
            when (breach.status) {
                BreachStatus.IDLE, BreachStatus.ERROR -> {
                    ErrorText(breach.error)
                    PrimaryButton(
                        text = if (breach.status == BreachStatus.ERROR) "Try again" else "Check for data breaches",
                        onClick = { viewModel.check(entries) },
                        enabled = entries.any { it.password.isNotEmpty() },
                    )
                }
                BreachStatus.RUNNING -> {
                    val progress = if (breach.total == 0) 0f else breach.checked.toFloat() / breach.total
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                    Text(
                        "Checked ${breach.checked} of ${breach.total}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BreachStatus.DONE -> {
                    if (breach.breached.isEmpty()) {
                        Text(
                            "None of your passwords were found in known data breaches.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.success,
                        )
                    } else {
                        Text(
                            "${breach.breached.size} password${if (breach.breached.size == 1) " was" else "s were"} found in data breaches. Change them now.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = error,
                        )
                        val hits = breach.breached.entries.sortedByDescending { it.value }.mapNotNull { (id, count) -> byId[id]?.let { it to count } }
                        Column(modifier = Modifier.heightIn(max = 208.dp).verticalScroll(rememberScrollState())) {
                            IssueList(hits) { (item, count) ->
                                IssueRow(
                                    title = item.displayTitle,
                                    note = "seen $count x",
                                    noteColor = error,
                                    onClick = { onOpenItem(item) },
                                )
                            }
                        }
                    }
                    PrimaryButton(text = "Check again", onClick = { viewModel.check(entries) })
                }
            }
        }
    }
}

/**
 * A card for one kind of problem: a round icon, the title and how many, then a short list of plain rows. The list
 * scrolls inside the card once it is longer than about five rows, so one long list never stretches the whole page.
 */
@Composable
private fun IssueCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    count: Int,
    info: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconBadge(icon, accent, 32.dp)
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (info != null) InfoButton(title, info)
            Text(count.toString(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val scroll = rememberScrollState()
        Box {
            Column(modifier = Modifier.heightIn(max = 208.dp).verticalScroll(scroll), content = content)
            // A soft fade at the bottom shows there is more to scroll to.
            if (scroll.canScrollForward) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface))),
                )
            }
        }
    }
}

/** Lays out [items] as rows with a thin line between them. */
@Composable
private fun <T> IssueList(items: List<T>, row: @Composable (T) -> Unit) {
    items.forEachIndexed { index, item ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 14.dp))
        row(item)
    }
}

/** One plain row: the name, an optional short note, and "Fix" with an arrow. No sentences. */
@Composable
private fun IssueRow(
    title: String,
    onClick: () -> Unit,
    note: String? = null,
    noteColor: Color = Color.Unspecified,
    action: String? = "Fix",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = if (noteColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else noteColor,
                maxLines = 1,
            )
        }
        if (action != null) Text(action, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}
