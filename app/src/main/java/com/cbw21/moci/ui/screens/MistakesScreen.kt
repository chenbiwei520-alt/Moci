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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cbw21.moci.data.AnswerStatus
import com.cbw21.moci.data.StudyDirection
import com.cbw21.moci.data.VocabBook
import com.cbw21.moci.data.mistakes
import com.cbw21.moci.ui.AppViewModel
import com.cbw21.moci.ui.components.ReviewCard
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MistakesScreen(viewModel: AppViewModel, book: VocabBook, direction: StudyDirection) {
    val mistakes = viewModel.progress.mistakes(book, direction)
    BackHandler { viewModel.backToBook() }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "错题集",
                subtitle = "${direction.label} · ${mistakes.size} 词",
                navigationIcon = {
                    IconButton(onClick = viewModel::backToBook) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回词书",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (mistakes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .widthIn(max = 420.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "暂无错题",
                        style = MiuixTheme.textStyles.title3,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "默写完成后，答错和接近正确的单词会自动收集到这里。",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    )
                }
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
                        MistakesSummaryCard(
                            wrongCount = mistakes.count { it.status == AnswerStatus.Wrong },
                            closeCount = mistakes.count { it.status == AnswerStatus.Close },
                            onPractice = { viewModel.startMistakeQuiz(book.id, direction) },
                        )
                    }
                    items(mistakes, key = { it.wordIndex }) { entry ->
                        val word = book.words.getOrNull(entry.wordIndex) ?: return@items
                        ReviewCard(
                            word = word,
                            answer = entry.answer,
                            status = entry.status,
                            direction = direction,
                            label = "第 ${entry.groupIndex + 1} 组 · No. ${word.number}",
                            onClick = {
                                viewModel.openGroup(book.id, direction, entry.groupIndex)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MistakesSummaryCard(
    wrongCount: Int,
    closeCount: Int,
    onPractice: () -> Unit,
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
                    text = "共 ${wrongCount + closeCount} 词",
                    style = MiuixTheme.textStyles.title4,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "错误 $wrongCount · 接近 $closeCount",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "也可以点击单题回到所在分组；答对后会自动移出错题集。",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onPractice,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text("重练这 ${wrongCount + closeCount} 道错题")
        }
    }
}
