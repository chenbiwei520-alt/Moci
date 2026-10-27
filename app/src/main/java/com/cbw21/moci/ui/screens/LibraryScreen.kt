package com.cbw21.moci.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cbw21.moci.data.ProgressData
import com.cbw21.moci.data.StudyDirection
import com.cbw21.moci.data.VocabBook
import com.cbw21.moci.data.groupCount
import com.cbw21.moci.ui.AppViewModel
import com.cbw21.moci.ui.BackupState
import com.cbw21.moci.ui.ImportState
import com.cbw21.moci.ui.ReimportCandidate
import com.cbw21.moci.ui.components.DirectionProgressRow
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.ContactsBook
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun LibraryScreen(viewModel: AppViewModel) {
    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importPdf(uri)
    }
    val openPicker = { pdfPicker.launch(arrayOf("application/pdf")) }

    val backupExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val backupImporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importBackup(uri)
    }
    val exportBackup = {
        if (viewModel.books.isNotEmpty()) {
            backupExporter.launch("墨词备份-${LocalDate.now()}.json")
        }
    }
    val importBackup = {
        backupImporter.launch(arrayOf("application/json", "text/plain", "*/*"))
    }
    val backupEnabled = viewModel.importState == null &&
        viewModel.reimportCandidate == null &&
        viewModel.backupState?.busy != true

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "墨词",
                actions = {
                    IconButton(
                        onClick = openPicker,
                        enabled = backupEnabled && !viewModel.isLoading,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.AddCircle,
                            contentDescription = "导入词书",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                viewModel.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                viewModel.books.isEmpty() -> {
                    EmptyLibrary(
                        onImport = openPicker,
                        onRestoreBackup = importBackup,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    BookList(
                        books = viewModel.books,
                        progress = viewModel.progress,
                        backupEnabled = backupEnabled,
                        onOpenBook = viewModel::openBook,
                        onExportBackup = exportBackup,
                        onImportBackup = importBackup,
                    )
                }
            }

            ImportDialog(
                state = viewModel.importState,
                onDismiss = viewModel::dismissImportError,
            )

            val candidate = viewModel.reimportCandidate
            val existing = candidate?.let { viewModel.findBook(it.bookId) }
            ReimportDialog(
                state = candidate,
                completedCnToEn = existing?.let {
                    viewModel.progress.completedGroups(it, StudyDirection.CnToEn)
                } ?: 0,
                completedEnToCn = existing?.let {
                    viewModel.progress.completedGroups(it, StudyDirection.EnToCn)
                } ?: 0,
                onReplace = viewModel::confirmReimport,
                onDismiss = viewModel::dismissReimport,
            )

            BackupDialog(
                state = viewModel.backupState,
                onDismiss = viewModel::dismissBackup,
            )
        }
    }
}

@Composable
private fun EmptyLibrary(
    onImport: () -> Unit,
    onRestoreBackup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 520.dp)
            .fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 28.dp, vertical = 36.dp),
        cornerRadius = 24.dp,
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = MiuixIcons.ContactsBook,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MiuixTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "还没有词书",
                style = MiuixTheme.textStyles.title3,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "导入双栏「单词 / 释义」词表 PDF，应用会自动按每 20 词分组，供中默英与英默中两种模式练习。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onImport,
                colors = ButtonDefaults.buttonColorsPrimary(),
                insideMargin = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Icon(
                    imageVector = MiuixIcons.AddCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("导入 PDF")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                text = "从备份恢复",
                onClick = onRestoreBackup,
                insideMargin = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun BookList(
    books: List<VocabBook>,
    progress: ProgressData,
    backupEnabled: Boolean,
    onOpenBook: (String) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 960.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "我的词书",
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                )
            }
            items(books, key = { it.id }) { book ->
                BookCard(
                    book = book,
                    progress = progress,
                    onClick = { onOpenBook(book.id) },
                )
            }
            item {
                BackupCard(
                    enabled = backupEnabled,
                    onExport = onExportBackup,
                    onImport = onImportBackup,
                )
            }
        }
    }
}

@Composable
private fun BackupCard(
    enabled: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = MiuixIcons.Backup,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(text = "备份与恢复", style = MiuixTheme.textStyles.title4)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "备份包含全部词书和两种模式的练习记录。更换安装包或清除数据后，用它找回进度。",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                text = "从备份导入",
                onClick = onImport,
                enabled = enabled,
                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onExport,
                enabled = enabled,
                colors = ButtonDefaults.buttonColorsPrimary(),
                insideMargin = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text("导出备份")
            }
        }
    }
}

@Composable
private fun BackupDialog(state: BackupState?, onDismiss: () -> Unit) {
    if (state == null) return
    OverlayDialog(
        show = true,
        title = state.title,
        summary = state.message,
        onDismissRequest = { if (!state.busy) onDismiss() },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.busy) {
                CircularProgressIndicator(size = 22.dp)
            } else {
                Button(onClick = onDismiss) {
                    Text("知道了")
                }
            }
        }
    }
}

@Composable
private fun BookCard(
    book: VocabBook,
    progress: ProgressData,
    onClick: () -> Unit,
) {
    val groupCount = book.groupCount()
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Text(
            text = book.title,
            style = MiuixTheme.textStyles.title4,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${book.words.size} 词 · $groupCount 组",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(14.dp))
        DirectionProgressRow(
            direction = StudyDirection.CnToEn,
            completedGroups = progress.completedGroups(book, StudyDirection.CnToEn),
            groupCount = groupCount,
        )
        Spacer(Modifier.height(10.dp))
        DirectionProgressRow(
            direction = StudyDirection.EnToCn,
            completedGroups = progress.completedGroups(book, StudyDirection.EnToCn),
            groupCount = groupCount,
        )
    }
}

@Composable
private fun ReimportDialog(
    state: ReimportCandidate?,
    completedCnToEn: Int,
    completedEnToCn: Int,
    onReplace: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state == null) return

    val newWordCount = state.imported.words.size
    val wordCountLine = if (newWordCount == state.existingWordCount) {
        "新文件解析出 $newWordCount 词，与当前词表一致。"
    } else {
        "新文件解析出 $newWordCount 词（原 ${state.existingWordCount} 词），超出新词表范围的组会被移除。"
    }
    OverlayDialog(
        show = true,
        title = "替换同名词书？",
        summary = "已存在《${state.existingTitle}》。替换只更新词表和释义，" +
            "已完成的学习进度会保留：中 → 英 $completedCnToEn 组，英 → 中 $completedEnToCn 组。\n" +
            wordCountLine,
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                text = "保留原书",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(20.dp))
            TextButton(
                text = "替换词表",
                onClick = onReplace,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun ImportDialog(state: ImportState?, onDismiss: () -> Unit) {
    if (state == null) return

    if (state.isError) {
        OverlayDialog(
            show = true,
            title = "导入失败",
            summary = state.error,
            onDismissRequest = onDismiss,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = onDismiss) {
                    Text("知道了")
                }
            }
        }
    } else {
        OverlayDialog(
            show = true,
            title = "正在导入词书",
            summary = if (state.pageCount > 0) {
                "正在读取第 ${state.page} / ${state.pageCount} 页"
            } else {
                "正在打开 PDF…"
            },
        ) {
            Column(Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = state.progress,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "需要逐页提取词条，大词表请稍候。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}
