package com.example.ttsaggregator.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ttsaggregator.data.ApiVersion
import com.example.ttsaggregator.data.TtsNode
import com.example.ttsaggregator.ui.theme.StatusColors

/**
 * 全局通用组件：卡片 / 分区标题 / 徽章 / 芯片。
 * 所有屏幕只用这里的组件 + MaterialTheme 字阶，保证视觉一致。
 */

// ---------------------------------------------------------------- 卡片

/** 扁平描边卡片（默认无点击），全站卡片外观的唯一出处 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardModifier = modifier.fillMaxWidth()
    val shape = MaterialTheme.shapes.medium
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)

    if (onClick != null) {
        // 可点击卡片：M3 clickable Card
        Card(
            modifier = cardModifier,
            shape = shape,
            colors = colors,
            border = border,
            elevation = elevation,
            onClick = onClick,
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        // 纯容器卡片：普通 Card，不带任何 clickable。
        // 不能用「禁用态的 clickable Card」包裹输入框——禁用 clickable 会合并语义，
        // 导致内部 TextField 的焦点/触摸异常（输入框失效）。
        Card(
            modifier = cardModifier,
            shape = shape,
            colors = colors,
            border = border,
            elevation = elevation,
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

/** 分区标题：左侧粗体标题 + 右侧灰色提示 */
@Composable
fun SectionHeader(
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        when {
            trailing != null -> trailing()
            hint != null -> Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 可点击的设置/入口行：标题 + 副标题 + 右箭头 */
@Composable
fun NavRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    AppCard(onClick = onClick, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke(this)
        }
    }
}

/** 空态占位 */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text,
            modifier = Modifier.padding(vertical = 28.dp, horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- 徽章

/** 通用小徽章：着色底 + 同色字 */
@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

@Composable
fun HealthBadge(health: TtsNode.Health, modifier: Modifier = Modifier) = when (health) {
    TtsNode.Health.OK -> Badge("健康", StatusColors.Ok, modifier)
    TtsNode.Health.FAIL -> Badge("异常", StatusColors.Error, modifier)
    TtsNode.Health.UNKNOWN -> Badge("未测试", StatusColors.Muted, modifier)
}

/** Key 状态标签：免Key（蓝）/ 需Key（橙） */
@Composable
fun KeyBadge(keyless: Boolean, modifier: Modifier = Modifier) =
    if (keyless) Badge("免Key", StatusColors.Info, modifier)
    else Badge("需Key", StatusColors.Warn, modifier)

/** API 世代标签：探测时识别的新老版本 */
@Composable
fun ApiBadge(version: ApiVersion, modifier: Modifier = Modifier) = when (version) {
    ApiVersion.V1 -> Badge("新版V1", StatusColors.Accent, modifier)
    ApiVersion.LEGACY -> Badge("老版", StatusColors.Warn, modifier)
    ApiVersion.UNKNOWN -> Badge("版本未知", StatusColors.Muted, modifier)
}

// ---------------------------------------------------------------- 芯片

/** 单选过滤芯片（状态筛选、日志过滤共用） */
@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) null
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
        )
    }
}
