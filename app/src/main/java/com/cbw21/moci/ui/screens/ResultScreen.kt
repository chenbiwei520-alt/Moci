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
import com.cbw21.moci.data.GroupResult
import com.cbw21.moci.data.StudyDirection
import com.cbw21.moci.data.VocabBook
import com.cbw21.moci.ui.AppViewModel
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
fun ResultScreen(
    viewModel: AppViewModel,
    book: VocabBook,
    direction: StudyDirection,
    groupIndex: Int,
) {
    BackHandler { viewModel.backToBook() }
    val record = viewModel.progress.groupResult(book.id, direction, groupIndex)

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "批改结果",
                subtitle = "${direction.label} · 第 ${groupIndex + 1} 组",
                navigationIcon = {
                    IconButton(onClick = viewModel::backToBook) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回分组",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (record == null) {
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
                        SummaryCard(
                            record = record,
                            direction = direction,
                            groupIndex = groupIndex,
                            onRetry = viewModel::retryCurrentGroup,
                            onBack = viewModel::backToBook,
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
                    items(record.items, key = { it.wordIndex }) { item ->
                        val word = book.words.getOrNull(item.wordIndex) ?: return@items
                        ReviewCard(
                            word = word,
                            answer = item.answer,
                            status = item.status,
                            overridden = item.overridden,
                            direction = direction,
                            label = "No. ${word.number}",
                            onOverride = { status ->
                                viewModel.overrideStatus(item.wordIndex, status)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    record: GroupResult,
    direction: StudyDirection,
    groupIndex: Int,
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
                        text = "${record.correctCount}",
                        style = MiuixTheme.textStyles.title1,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    Text(
                        text = " / ${record.total}",
                        style = MiuixTheme.textStyles.title4,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Text(
                    text = "历史最佳 ${record.bestCorrect} / ${record.total}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "第 ${groupIndex + 1} 组",
                    style = MiuixTheme.textStyles.title4,
                )
                Text(
                    text = direction.label,
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
                    value = "${record.correctCount}",
                    label = "正确",
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    value = "${record.closeCount}",
                    label = "接近",
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    value = "${record.wrongCount}",
                    label = "错误",
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    value = "${record.blankCount}",
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
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("再练一次")
            }
            TextButton(
                text = "返回分组",
                onClick = onBack,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
