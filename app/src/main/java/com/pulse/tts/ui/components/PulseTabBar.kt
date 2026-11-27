package com.pulse.tts.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pulse.tts.navigation.MainTab
import com.pulse.tts.ui.theme.LocalMotion

/** 贴底底部导航：朗读 / 节点 / 记录。 */
@Composable
fun PulseTabBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
    ) {
        Column {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(56.dp),
            ) {
                TabItem("朗读", Icons.Rounded.Headphones, selected == MainTab.READ, Modifier.weight(1f)) {
                    onSelect(MainTab.READ)
                }
                TabItem("节点", Icons.Rounded.Dns, selected == MainTab.NODES, Modifier.weight(1f)) {
                    onSelect(MainTab.NODES)
                }
                TabItem("记录", Icons.Rounded.History, selected == MainTab.RECORDS, Modifier.weight(1f)) {
                    onSelect(MainTab.RECORDS)
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val haptics = LocalAppHaptics.current
    val motion = LocalMotion.current
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = tween(motion.duration(160)),
        label = "tabIconScale",
    )
    val tint by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(motion.duration(160)),
        label = "tabTint",
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .semantics {
                selected = isSelected
                role = Role.Tab
            }
            .clickable {
                haptics.toggle()
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier
                .size(22.dp)
                .scale(iconScale),
            tint = tint,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}
