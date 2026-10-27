package com.cbw21.moci.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cbw21.moci.data.AnswerStatus
import com.cbw21.moci.data.StudyDirection
import com.cbw21.moci.data.VocabWord
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun ReviewCard(
    word: VocabWord,
    answer: String,
    status: AnswerStatus,
    direction: StudyDirection,
    label: String,
    modifier: Modifier = Modifier,
    overridden: Boolean = false,
    onClick: (() -> Unit)? = null,
    onOverride: ((AnswerStatus) -> Unit)? = null,
) {
    val insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    val colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)
    if (onClick == null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            insideMargin = insideMargin,
            colors = colors,
        ) {
            ReviewCardContent(
                word = word,
                answer = answer,
                status = status,
                direction = direction,
                label = label,
                overridden = overridden,
                onOverride = onOverride,
            )
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            onClick = onClick,
            insideMargin = insideMargin,
            colors = colors,
            pressFeedbackType = PressFeedbackType.Sink,
        ) {
            ReviewCardContent(
                word = word,
                answer = answer,
                status = status,
                direction = direction,
                label = label,
                overridden = overridden,
                onOverride = onOverride,
            )
        }
    }
}

@Composable
private fun ReviewCardContent(
    word: VocabWord,
    answer: String,
    status: AnswerStatus,
    direction: StudyDirection,
    label: String,
    overridden: Boolean,
    onOverride: ((AnswerStatus) -> Unit)?,
) {
    val correctAnswer = when (direction) {
        StudyDirection.CnToEn -> word.word
        StudyDirection.EnToCn -> word.meaning
    }
    val prompt = when (direction) {
        StudyDirection.CnToEn -> word.meaning
        StudyDirection.EnToCn -> word.word
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusChip(status = status)
        if (overridden) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "已修正",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = prompt,
        style = MiuixTheme.textStyles.body1,
        maxLines = if (direction == StudyDirection.CnToEn) 3 else 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = "正确答案",
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
    Text(
        text = correctAnswer,
        style = MiuixTheme.textStyles.title4,
        maxLines = if (direction == StudyDirection.CnToEn) 1 else 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = if (answer.isBlank()) "未作答" else "你的答案：$answer",
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (onOverride != null) {
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            if (status == AnswerStatus.Correct) {
                TextButton(
                    text = "改为错误",
                    onClick = { onOverride(AnswerStatus.Wrong) },
                )
            } else {
                TextButton(
                    text = "改为正确",
                    onClick = { onOverride(AnswerStatus.Correct) },
                )
            }
        }
    }
}
