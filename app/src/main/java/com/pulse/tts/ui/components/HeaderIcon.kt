package com.pulse.tts.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** 顶栏图标动作：激活态用主色，未激活用次要前景色。 */
@Composable
fun HeaderIcon(
    icon: ImageVector,
    description: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val haptics = LocalAppHaptics.current
    IconButton(onClick = {
        haptics.tap()
        onClick()
    }, modifier = Modifier.size(40.dp)) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(21.dp),
            tint = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
