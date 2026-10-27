package com.cbw21.moci.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cbw21.moci.data.AnswerStatus
import com.cbw21.moci.data.StudyDirection
import com.cbw21.moci.data.VocabBook
import com.cbw21.moci.data.groupCount
import com.cbw21.moci.data.groupRange
import com.cbw21.moci.data.mistakes
import com.cbw21.moci.ui.AppViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun BookScreen(viewModel: AppViewModel, book: VocabBook, direction: StudyDirection) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val mistakes = viewModel.progress.mistakes(book, direction)

    BackHandler { viewModel.backToLibrary() }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = book.title,
                subtitle = "${book.words.size} 词 · ${book.groupCount()} 组",
                navigationIcon = {
                    IconButton(onClick = viewModel::backToLibrary) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回词书列表",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = "删除词书",
                            modifier = Modifier.size(24.dp),
                            tint = MiuixTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 168.dp),
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 1100.dp)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = padding.calculateTopPadding() + 12.dp,
                        bottom = padding.calculateBottomPadding() + 28.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        DirectionTabs(direction = direction, onSelect = viewModel::selectDirection)
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        DirectionOverview(book = book, direction = direction, viewModel = viewModel)
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        MistakesCard(
                            wrongCount = mistakes.count { it.status == AnswerStatus.Wrong },
                            closeCount = mistakes.count { it.status == AnswerStatus.Close },
                            onClick = { viewModel.openMistakes(book.id, direction) },
                        )
                    }
                    items(
                        items = (0 until book.groupCount()).toList(),
                        key = { it },
                    ) { groupIndex ->
                        val range = book.groupRange(groupIndex)
                        val record = viewModel.progress.groupResult(book.id, direction, groupIndex)
                        GroupCard(
                            groupIndex = groupIndex,
                            firstNumber = range.first + 1,
                            lastNumber = range.last + 1,
                            total = range.count(),
                            bestCorrect = record?.bestCorrect,
                            onClick = { viewModel.openGroup(book.id, direction, groupIndex) },
                        )
                    }
                }
            }

            if (showDeleteDialog) {
                OverlayDialog(
                    show = true,
                    title = "删除词书？",
                    summary = "《${book.title}》及其两种模式的学习进度都会被删除，无法恢复。",
                    onDismissRequest = { showDeleteDialog = false },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = { showDeleteDialog = false },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(20.dp))
                        TextButton(
                            text = "删除",
                            onClick = {
                                showDeleteDialog = false
                                viewModel.deleteBook(book.id)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColors(
                                color = MiuixTheme.colorScheme.error,
                                textColor = MiuixTheme.colorScheme.onError,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectionTabs(direction: StudyDirection, onSelect: (StudyDirection) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(),
        ) {
            TabRow(
                tabs = StudyDirection.entries.map { it.label },
                selectedTabIndex = direction.ordinal,
                onTabSelected = { index -> onSelect(StudyDirection.entries[index]) },
            )
        }
    }
}

@Composable
private fun DirectionOverview(
    book: VocabBook,
    direction: StudyDirection,
    viewModel: AppViewModel,
) {
    val mastered = viewModel.progress.masteredWords(book, direction)
    val completed = viewModel.progress.completedGroups(book, direction)
    val groupCount = book.groupCount()
    val ratio = if (book.words.isEmpty()) 0f else mastered.toFloat() / book.words.size

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
                    text = "学习进度",
                    style = MiuixTheme.textStyles.title4,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = direction.promptLabel,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$mastered / ${book.words.size}",
                    style = MiuixTheme.textStyles.title2,
                )
                Text(
                    text = "最佳正确词数",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = ratio,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "已完成 $completed / $groupCount 组 · 每组 20 词",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun MistakesCard(
    wrongCount: Int,
    closeCount: Int,
    onClick: () -> Unit,
) {
    val total = wrongCount + closeCount
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "错题集",
                    style = MiuixTheme.textStyles.title4,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (total == 0) "暂无错题" else "错误 $wrongCount · 接近 $closeCount",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Text(
                text = "$total 词",
                style = MiuixTheme.textStyles.title4,
                color = if (total == 0) {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                } else {
                    MiuixTheme.colorScheme.primary
                },
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = "查看错题集",
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun GroupCard(
    groupIndex: Int,
    firstNumber: Int,
    lastNumber: Int,
    total: Int,
    bestCorrect: Int?,
    onClick: () -> Unit,
) {
    val ratio = if (bestCorrect == null) 0f else bestCorrect.toFloat() / total
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "第 ${groupIndex + 1} 组",
                    style = MiuixTheme.textStyles.title4,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "第 $firstNumber–$lastNumber 词",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (bestCorrect != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$bestCorrect / $total",
                        style = MiuixTheme.textStyles.title4,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    Text(
                        text = "最佳正确",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else {
                Text(
                    text = "未开始",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = ratio,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
