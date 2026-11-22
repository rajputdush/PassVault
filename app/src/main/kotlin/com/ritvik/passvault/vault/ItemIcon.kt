package com.ritvik.passvault.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ritvik.passvault.items.ItemType
import com.ritvik.passvault.items.VaultItem
import com.ritvik.passvault.ui.SiteIcon

/** The picture for a list row: the site badge for a login, a type badge for a card or note. */
@Composable
fun ItemIcon(item: VaultItem, size: Dp = 42.dp) {
    if (item.type == ItemType.LOGIN) {
        SiteIcon(item.title, item.url, item.username, size, appId = item.appId)
    } else {
        TypeBadge(item.type, size)
    }
}

@Composable
fun TypeBadge(type: ItemType, size: Dp = 42.dp) {
    val color = when (type) {
        ItemType.CARD -> Color(0xFF0E7490)
        ItemType.NOTE -> Color(0xFFE08A00)
        ItemType.LOGIN -> Color(0xFF3F6BFF)
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (type == ItemType.NOTE) Icons.Filled.Create else Icons.Filled.AccountBox,
            contentDescription = type.label,
            tint = Color.White,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}
