package com.example.ttsaggregator.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
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

/** 扁平卡片（默认无点击、无描边），全站卡片外观的唯一出处 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    bordered: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardModifier = modifier.fillMaxWidth()
    val shape = MaterialTheme.shapes.medium
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val border = if (bordered) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    } else {
        null
    }
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
        Text(title, style = MaterialTheme.typography.titleSmall)
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
                Text(title, style = MaterialTheme.typography.titleSmall)
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

// ---------------------------------------------------------------- 设置分组

/** 一个分组 = 一个白色容器；组内行用 SettingDivider 分隔 */
@Composable
fun SettingGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(content = content)
    }
}

/** 组内分隔线：左侧对齐到文字起点（14 + 34 + 14） */
@Composable
fun SettingDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 62.dp, end = 14.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** 设置行骨架：图标 + 标题/副标题 + 自定义尾部，普通行与步进器行共用 */
@Composable
fun SettingRowLayout(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIconTile(icon = icon, tint = tint, size = 34)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
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
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
        )
    }
}

// ---------------------------------------------------------------- 子页头部

/**
 * 子页统一头部：圆形返回键 + 大标题 + 可选副标题 + 右侧操作。
 * 已包含状态栏 inset，直接放进 Scaffold.topBar。
 */
@Composable
fun PulseSubHeader(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable (RowScope.() -> Unit) = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            actions()
        }
    }
}

/**
 * 一级页面统一顶部：可选前置图标 + 标题/副标题 + 右侧操作。
 * 已含水平内边距，调用方只需提供 statusBarsPadding。
 */
@Composable
fun PulseMainHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
    leading: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

/** 彩色图标方块：子页卡片与设置行的视觉锚点。 */
@Composable
fun PulseIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    background: Color = tint.copy(alpha = 0.12f),
    size: Int = 42,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3).dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((size * 0.52).dp),
        )
    }
}

/** 品牌头像：渐变方章，子页与关于页复用。 */
@Composable
fun PulseBrandMark(modifier: Modifier = Modifier, size: Int = 56) {
    val waveColor = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3).dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size((size * 0.62).dp)) {
            val w = this.size.width
            val h = this.size.height
            val wave = Path().apply {
                moveTo(w * 0.06f, h * 0.5f)
                lineTo(w * 0.30f, h * 0.5f)
                lineTo(w * 0.39f, h * 0.32f)
                lineTo(w * 0.50f, h * 0.72f)
                lineTo(w * 0.61f, h * 0.22f)
                lineTo(w * 0.72f, h * 0.64f)
                lineTo(w * 0.81f, h * 0.42f)
                lineTo(w * 0.94f, h * 0.5f)
            }
            drawPath(
                path = wave,
                color = waveColor,
                style = Stroke(
                    width = h * 0.11f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

/** 设置入口行：图标方块 + 标题/副标题 + 箭头（需放在 SettingGroup 内） */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
    trailing: @Composable (() -> Unit)? = null,
) {
    SettingRowLayout(
        icon = icon,
        title = title,
        subtitle = subtitle,
        tint = tint,
        onClick = onClick,
        trailing = {
            if (trailing != null) {
                trailing()
            } else {
                Text(
                    "›",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
