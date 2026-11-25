package com.pulse.tts.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 设置页通用行与托盘。
 *
 * 规范：标题在上、副标题在下；右侧只放「值 + ›」，可选值点击或长按唤出底部托盘。
 * 单个行承担多个功能时，主操作走点击，次要设置走长按（[SettingValueRow.onLongClick]）。
 */

/** 分组标题 + 白色容器 */
@Composable
fun SettingSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
    SettingGroup(content = content)
}

/**
 * 「值 + ›」尾部。
 *
 * 箭头热区至少 44dp 高，允许单独点击；不传 [onClick] 时它只是视觉提示。
 */
@Composable
fun SettingValueTrailing(
    value: String,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .heightIn(min = 44.dp)
                        .clickable(onClick = onClick)
                } else {
                    Modifier.heightIn(min = 44.dp)
                },
            )
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (value.isNotBlank()) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (highlight) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(2.dp))
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 点击进入下一步，或点击弹出托盘的可选值行 */
@Composable
fun SettingValueRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String = "",
    highlight: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    SettingRowLayout(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        onLongClick = onLongClick,
        trailing = {
            SettingValueTrailing(
                value = value,
                highlight = highlight,
                onClick = onTrailingClick,
            )
        },
    )
}

/** 开关行：整行可点，右侧开关同步 */
@Composable
fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRowLayout(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

/** 步进行：右侧 `− 值 +` */
@Composable
fun SettingStepperRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    SettingRowLayout(
        icon = icon,
        title = title,
        subtitle = subtitle,
        trailing = {
            IconButton(onClick = onDecrease, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Remove,
                    contentDescription = "减少$title",
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            IconButton(onClick = onIncrease, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "增加$title",
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    )
}

/** 托盘中的单个选项 */
data class SettingSheetOption(
    val id: String,
    val label: String,
    val description: String? = null,
    val selected: Boolean = false,
)

/** 单选托盘：渠道、调度模式、睡眠定时等共用 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingPickerSheet(
    title: String,
    options: List<SettingSheetOption>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    description: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AppBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            options.forEach { option ->
                Surface(
                    onClick = { onSelect(option.id) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (option.selected) MaterialTheme.colorScheme.surfaceVariant
                    else Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(option.label, style = MaterialTheme.typography.titleSmall)
                            if (option.description != null) {
                                Text(
                                    option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (option.selected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "已选择",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

/** 二次确认托盘：带风险说明，确认按钮可用危险色 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingConfirmSheet(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    message: String? = null,
    note: String? = null,
    confirmLabel: String = "确定",
    destructive: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AppBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (message != null) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (note != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("取消") }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = if (destructive) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) { Text(confirmLabel) }
            }
        }
    }
}
