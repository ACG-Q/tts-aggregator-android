package com.example.ttsaggregator.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ttsaggregator.ui.theme.StatusColors
import com.example.ttsaggregator.ui.util.NodeState

@Composable
internal fun HealthTile(
    state: NodeState,
    size: Int = 38,
    corner: Int = 11,
    iconSize: Int = 20,
) {
    val (tint, icon) = when (state) {
        NodeState.AVAILABLE -> StatusColors.Ok to Icons.Rounded.Check
        NodeState.UNTESTED -> StatusColors.Muted to Icons.AutoMirrored.Rounded.HelpOutline
        NodeState.COOLING -> StatusColors.Warn to Icons.Rounded.Schedule
        NodeState.FAILED -> StatusColors.Error to Icons.Rounded.Close
        NodeState.DISABLED -> StatusColors.Muted to Icons.Rounded.Block
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(corner.dp))
            .background(tint.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize.dp))
    }
}

@Composable
internal fun NodeBadge(text: String) {
    val color = when (text) {
        "主用" -> MaterialTheme.colorScheme.primary
        "冷却", "异常" -> StatusColors.Warn
        "停用", "未测试" -> StatusColors.Muted
        else -> StatusColors.Ok
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.5.sp,
                lineHeight = 12.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            ),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun NodeSelectBox(
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        modifier = modifier.size(24.dp),
        shape = RoundedCornerShape(7.dp),
        color = if (checked) tint else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = BorderStroke(
            1.5.dp,
            if (checked) tint else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (checked) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "已选择",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
