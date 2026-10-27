package com.cbw21.moci.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cbw21.moci.data.AnswerStatus
import com.cbw21.moci.data.StudyDirection
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun statusLabel(status: AnswerStatus): String = when (status) {
    AnswerStatus.Correct -> "正确"
    AnswerStatus.Close -> "接近"
    AnswerStatus.Wrong -> "错误"
    AnswerStatus.Blank -> "未作答"
}

@Composable
fun StatusChip(status: AnswerStatus, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val background = when (status) {
        AnswerStatus.Correct -> colors.primaryContainer
        AnswerStatus.Close -> colors.tertiaryContainer
        AnswerStatus.Wrong -> colors.errorContainer
        AnswerStatus.Blank -> colors.surfaceContainerHigh
    }
    val content = when (status) {
        AnswerStatus.Correct -> colors.onPrimaryContainer
        AnswerStatus.Close -> colors.onTertiaryContainer
        AnswerStatus.Wrong -> colors.onErrorContainer
        AnswerStatus.Blank -> colors.onSurfaceContainerVariant
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text = statusLabel(status),
            style = MiuixTheme.textStyles.footnote1,
            color = content,
        )
    }
}

@Composable
fun DirectionBadge(direction: StudyDirection, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = direction.label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceContainer,
        )
    }
}

@Composable
fun DirectionProgressRow(
    direction: StudyDirection,
    completedGroups: Int,
    groupCount: Int,
    modifier: Modifier = Modifier,
) {
    val ratio = if (groupCount > 0) completedGroups.toFloat() / groupCount else 0f
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DirectionBadge(direction)
        LinearProgressIndicator(
            progress = ratio,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$completedGroups/$groupCount 组",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
fun StatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MiuixTheme.textStyles.title3,
            )
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
