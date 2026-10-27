package com.cbw21.moci.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cbw21.moci.data.StudyDirection
import com.cbw21.moci.data.VocabBook
import com.cbw21.moci.data.mistakes
import com.cbw21.moci.ui.AppViewModel
import com.cbw21.moci.ui.MistakePracticeResult
import com.cbw21.moci.ui.components.ReviewCard
import com.cbw21.moci.ui.components.StatCell
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MistakeResultScreen(
    viewModel: AppViewModel,
    book: VocabBook,
    direction: StudyDirection,
    result: MistakePracticeResult,
) {
    BackHandler { viewModel.backToMistakes() }
    val remaining = viewModel.progress.mistakes(book, direction).size

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "错题批改",
                subtitle = "${direction.label} · ${result.total} 题",
                navigationIcon = {
                    IconButton(onClick = viewModel::backToMistakes) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回错题集",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (result.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无批改结果",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(minSize = 260.dp),
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 1400.dp)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = padding.calculateTopPadding() + 12.dp,
                        bottom = padding.calculateBottomPadding() + 28.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp,
                ) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        MistakeSummaryCard(
                            result = result,
                            remaining = remaining,
                            onRetry = viewModel::retryMistakes,
                            onBack = viewModel::backToMistakes,
                        )
                    }
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Text(
                            text = "逐题回顾",
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(result.items, key = { it.item.wordIndex }) { entry ->
                        val word = book.words.getOrNull(entry.item.wordIndex) ?: return@items
                        ReviewCard(
                            word = word,
                            answer = entry.item.answer,
                            status = entry.item.status,
                            overridden = entry.item.overridden,
                            direction = direction,
                            label = "第 ${entry.groupIndex + 1} 组 · No. ${word.number}",
                            onOverride = { status ->
                                viewModel.overrideMistakeStatus(entry.item.wordIndex, status)
                            },
                        )
                    }
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Text(
                            text = "答对的单词已自动移出错题集。",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 8.dp),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MistakeSummaryCard(
    result: MistakePracticeResult,
    remaining: Int,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "本次得分",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${result.correctCount}",
                        style = MiuixTheme.textStyles.title1,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    Text(
                        text = " / ${result.total}",
                        style = MiuixTheme.textStyles.title4,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Text(
                    text = if (remaining == 0) "错题已全部答对" else "剩余错题 $remaining 词",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${result.total} 题",
                    style = MiuixTheme.textStyles.title4,
                )
                Text(
                    text = "错题重练",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                .padding(vertical = 12.dp, horizontal = 8.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                StatCell(
                    value = "${result.correctCount}",
                    label = "正确",
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    value = "${result.closeCount}",
                    label = "接近",
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    value = "${result.wrongCount}",
                    label = "错误",
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    value = "${result.blankCount}",
                    label = "未作答",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onRetry,
                enabled = remaining > 0,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(if (remaining > 0) "再练剩余 $remaining 题" else "错题已清空")
            }
            TextButton(
                text = "返回错题集",
                onClick = onBack,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
