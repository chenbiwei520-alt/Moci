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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cbw21.moci.data.StudyDirection
import com.cbw21.moci.data.VocabBook
import com.cbw21.moci.data.VocabWord
import com.cbw21.moci.ui.AppViewModel
import com.cbw21.moci.ui.QuizSession
import com.cbw21.moci.ui.components.HandwritingPanel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class InputMode { Keyboard, Handwriting }

@Composable
fun QuizScreen(viewModel: AppViewModel, book: VocabBook, session: QuizSession) {
    val direction = session.direction
    val word = book.words[session.currentWordIndex]
    var showExitDialog by remember { mutableStateOf(false) }
    var inputMode by remember(session.bookId, direction, session.groupIndex) {
        mutableStateOf(InputMode.Handwriting)
    }

    val storedAnswer = session.answers[session.currentWordIndex].orEmpty()
    var fieldValue by remember(session.currentWordIndex) {
        mutableStateOf(TextFieldValue(storedAnswer))
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(storedAnswer) {
        if (fieldValue.text != storedAnswer) {
            fieldValue = TextFieldValue(storedAnswer, selection = TextRange(storedAnswer.length))
        }
    }

    LaunchedEffect(session.currentWordIndex, inputMode) {
        if (inputMode == InputMode.Keyboard) {
            focusRequester.requestFocus()
        }
    }

    BackHandler { showExitDialog = true }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            SmallTopAppBar(
                title = session.groupIndex?.let { "第 ${it + 1} 组" } ?: "错题重练",
                subtitle = "${direction.label} · 第 ${session.current + 1} / ${session.size} 题",
                navigationIcon = {
                    IconButton(onClick = { showExitDialog = true }) {
                        Icon(
                            imageVector = MiuixIcons.Close,
                            contentDescription = "退出默写",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MiuixTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        text = "上一题",
                        onClick = viewModel::previousQuestion,
                        enabled = session.current > 0,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "不会",
                        onClick = viewModel::skipQuestion,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = viewModel::nextQuestion,
                        modifier = Modifier.weight(1.6f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        Text(if (session.isLast) "完成并批改" else "下一题")
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 900.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "第 ${session.current + 1} / ${session.size} 题",
                            style = MiuixTheme.textStyles.subtitle,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "No. ${word.number}",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = (session.current + 1).toFloat() / session.size,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    PromptCard(direction = direction, word = word)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 360.dp)
                                .fillMaxWidth(),
                        ) {
                            TabRow(
                                tabs = listOf("键盘", "手写"),
                                selectedTabIndex = inputMode.ordinal,
                                onTabSelected = { index ->
                                    val selected = InputMode.entries[index]
                                    if (selected == InputMode.Handwriting) {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    }
                                    inputMode = selected
                                },
                            )
                        }
                    }

                    when (inputMode) {
                        InputMode.Keyboard -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                colors = CardDefaults.defaultColors(
                                    color = MiuixTheme.colorScheme.surfaceContainer,
                                ),
                            ) {
                                TextField(
                                    value = fieldValue,
                                    onValueChange = { value ->
                                        fieldValue = value
                                        viewModel.updateQuizAnswer(value.text)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester),
                                    cornerRadius = 8.dp,
                                    label = if (direction == StudyDirection.CnToEn) {
                                        "输入英文单词"
                                    } else {
                                        "输入中文释义"
                                    },
                                    singleLine = direction == StudyDirection.CnToEn,
                                    maxLines = if (direction == StudyDirection.CnToEn) 1 else 4,
                                    minLines = if (direction == StudyDirection.CnToEn) 1 else 2,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = if (direction == StudyDirection.CnToEn) {
                                            KeyboardType.Ascii
                                        } else {
                                            KeyboardType.Text
                                        },
                                        imeAction = ImeAction.Done,
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                        },
                                    ),
                                    textStyle = if (direction == StudyDirection.CnToEn) {
                                        MiuixTheme.textStyles.title4
                                    } else {
                                        MiuixTheme.textStyles.body1
                                    },
                                )
                            }
                        }

                        InputMode.Handwriting -> {
                            HandwritingPanel(
                                language = direction.targetLanguage,
                                wordKey = session.currentWordIndex,
                                answer = storedAnswer,
                                onAnswer = viewModel::updateQuizAnswer,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }

                    Text(
                        text = if (session.groupIndex == null) {
                            "本次 ${session.size} 道错题，全部完成后统一批改。"
                        } else {
                            "一组 ${session.size} 题，全部完成后统一批改。"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 12.dp),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (showExitDialog) {
                OverlayDialog(
                    show = true,
                    title = if (session.groupIndex == null) "退出错题重练？" else "退出本组默写？",
                    summary = if (session.hasAnyInput()) {
                        "已填写但尚未批改的答案不会保存。"
                    } else if (session.groupIndex == null) {
                        "本次还没有填写答案。"
                    } else {
                        "本组还没有填写答案。"
                    },
                    onDismissRequest = { showExitDialog = false },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            text = "继续默写",
                            onClick = { showExitDialog = false },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(20.dp))
                        TextButton(
                            text = "退出",
                            onClick = {
                                showExitDialog = false
                                viewModel.exitQuiz()
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
private fun PromptCard(direction: StudyDirection, word: VocabWord) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        insideMargin = PaddingValues(horizontal = 28.dp, vertical = 30.dp),
        cornerRadius = 24.dp,
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = direction.promptLabel,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(12.dp))
            when (direction) {
                StudyDirection.CnToEn -> {
                    Text(
                        text = word.meaning,
                        style = MiuixTheme.textStyles.title4,
                        textAlign = TextAlign.Center,
                    )
                }

                StudyDirection.EnToCn -> {
                    Text(
                        text = word.word,
                        style = MiuixTheme.textStyles.title1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
