package com.pulse.tts.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ReaderStage(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            ),
                        ),
                    ),
            )
            content()
        }
    }
}

@Composable
fun ReaderTopBar(
    title: String,
    statusColor: Color,
    onCollapse: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(statusColor, CircleShape),
        )
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        IconButton(onClick = onCollapse) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "收起朗读")
        }
    }
}

@Composable
fun SoundOrb(
    isPlaying: Boolean,
    reduceMotion: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "orb")
    val animated by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPlaying && !reduceMotion) 1.035f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2300),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb-scale",
    )
    val pulse = if (reduceMotion) 1f else animated
    Box(
        modifier = Modifier
            .size(190.dp)
            .scale(pulse),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(190.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .size(150.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                        ),
                    ),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(46.dp),
            )
        }
    }
}

@Composable
fun ReaderChip(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon()
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
