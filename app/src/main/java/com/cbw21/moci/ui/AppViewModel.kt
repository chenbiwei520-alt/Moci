package com.cbw21.moci.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cbw21.moci.data.AnswerRecord
import com.cbw21.moci.data.AnswerStatus
import com.cbw21.moci.data.BackupRepository
import com.cbw21.moci.data.GroupResult
import com.cbw21.moci.data.ImportedVocab
import com.cbw21.moci.data.LibraryRepository
import com.cbw21.moci.data.MistakePracticeItem
import com.cbw21.moci.data.PdfVocabImporter
import com.cbw21.moci.data.ProgressData
import com.cbw21.moci.data.ProgressRepository
import com.cbw21.moci.data.StudyDirection
import com.cbw21.moci.data.VocabBook
import com.cbw21.moci.data.VocabParseException
import com.cbw21.moci.data.applyMistakePractice
import com.cbw21.moci.data.groupCount
import com.cbw21.moci.data.groupRange
import com.cbw21.moci.data.mistakes
import com.cbw21.moci.data.prunedTo
import com.cbw21.moci.data.restoreBackup
import com.cbw21.moci.data.totalGroups
import com.cbw21.moci.data.withGroupResult
import com.cbw21.moci.grading.AnswerGrader
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Library : Screen
    data class BookDetail(val bookId: String, val direction: StudyDirection) : Screen
    data class Mistakes(val bookId: String, val direction: StudyDirection) : Screen
    data class Quiz(val bookId: String, val direction: StudyDirection, val groupIndex: Int) : Screen
    data class Results(val bookId: String, val direction: StudyDirection, val groupIndex: Int) : Screen
    data class MistakeQuiz(val bookId: String, val direction: StudyDirection) : Screen
    data class MistakeResults(val bookId: String, val direction: StudyDirection) : Screen
}

data class ImportState(
    val page: Int = 0,
    val pageCount: Int = 0,
    val error: String? = null,
) {
    val progress: Float?
        get() = if (pageCount > 0) page.toFloat() / pageCount else null

    val isError: Boolean get() = error != null
}

data class BackupState(
    val busy: Boolean = false,
    val title: String = "",
    val message: String = "",
    val isError: Boolean = false,
)

class ReimportCandidate(
    val bookId: String,
    val imported: ImportedVocab,
    val existingTitle: String,
    val existingWordCount: Int,
)

class QuizSession(
    val bookId: String,
    val direction: StudyDirection,
    val groupIndex: Int?,
    val wordIndexes: List<Int>,
) {
    var current by mutableIntStateOf(0)
        private set
    var answers by mutableStateOf<Map<Int, String>>(emptyMap())
        private set

    val size: Int get() = wordIndexes.size
    val currentWordIndex: Int get() = wordIndexes[current]
    val isLast: Boolean get() = current == wordIndexes.lastIndex

    fun updateAnswer(text: String) {
        answers = answers + (currentWordIndex to text)
    }

    fun moveNext(): Boolean {
        if (current >= wordIndexes.lastIndex) return false
        current++
        return true
    }

    fun movePrevious() {
        if (current > 0) current--
    }

    fun hasAnyInput(): Boolean = answers.values.any { it.isNotBlank() }
}

class MistakePracticeResult(
    val bookId: String,
    val direction: StudyDirection,
    val items: List<MistakePracticeItem>,
) {
    val correctCount: Int get() = items.count { it.item.status == AnswerStatus.Correct }
    val closeCount: Int get() = items.count { it.item.status == AnswerStatus.Close }
    val wrongCount: Int get() = items.count { it.item.status == AnswerStatus.Wrong }
    val blankCount: Int get() = items.count { it.item.status == AnswerStatus.Blank }
    val total: Int get() = items.size
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val library = LibraryRepository(application)
    private val progressStore = ProgressRepository(application)
    private val backupStore = BackupRepository(application)
    private val importer = PdfVocabImporter(application)
    private val progressSaveMutex = Mutex()
    private val lastDirections = mutableMapOf<String, StudyDirection>()

    var screen by mutableStateOf<Screen>(Screen.Library)
        private set
    var books by mutableStateOf<List<VocabBook>>(emptyList())
        private set
    var progress by mutableStateOf(ProgressData())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var importState by mutableStateOf<ImportState?>(null)
        private set
    var reimportCandidate by mutableStateOf<ReimportCandidate?>(null)
        private set
    var quiz by mutableStateOf<QuizSession?>(null)
        private set
    var mistakeResult by mutableStateOf<MistakePracticeResult?>(null)
        private set
    var backupState by mutableStateOf<BackupState?>(null)
        private set

    init {
        viewModelScope.launch {
            val loadedBooks = library.loadBooks()
            val loadedProgress = progressStore.load()
            val validIds = loadedBooks.mapTo(HashSet()) { it.id }
            val cleaned = loadedProgress.copy(
                books = loadedProgress.books.filterKeys { it in validIds },
            )
            books = loadedBooks
            progress = cleaned
            isLoading = false
            if (cleaned != loadedProgress) persistProgress()
        }
    }

    fun findBook(bookId: String): VocabBook? = books.firstOrNull { it.id == bookId }

    fun openBook(bookId: String) {
        if (findBook(bookId) == null) return
        val direction = lastDirections[bookId] ?: StudyDirection.CnToEn
        screen = Screen.BookDetail(bookId, direction)
    }

    fun selectDirection(direction: StudyDirection) {
        val current = screen as? Screen.BookDetail ?: return
        lastDirections[current.bookId] = direction
        screen = current.copy(direction = direction)
    }

    fun openMistakes(bookId: String, direction: StudyDirection) {
        if (findBook(bookId) == null) return
        lastDirections[bookId] = direction
        screen = Screen.Mistakes(bookId, direction)
    }

    fun openGroup(bookId: String, direction: StudyDirection, groupIndex: Int) {
        val book = findBook(bookId) ?: return
        if (groupIndex !in 0 until book.groupCount()) return
        lastDirections[bookId] = direction
        if (progress.groupResult(bookId, direction, groupIndex) != null) {
            screen = Screen.Results(bookId, direction, groupIndex)
        } else {
            startQuiz(bookId, direction, groupIndex)
        }
    }

    fun startQuiz(bookId: String, direction: StudyDirection, groupIndex: Int) {
        val book = findBook(bookId) ?: return
        if (groupIndex !in 0 until book.groupCount()) return
        lastDirections[bookId] = direction
        quiz = QuizSession(
            bookId = bookId,
            direction = direction,
            groupIndex = groupIndex,
            wordIndexes = book.groupRange(groupIndex).toList(),
        )
        screen = Screen.Quiz(bookId, direction, groupIndex)
    }

    fun startMistakeQuiz(bookId: String, direction: StudyDirection) {
        val book = findBook(bookId) ?: return
        val remaining = progress.mistakes(book, direction)
        if (remaining.isEmpty()) return
        lastDirections[bookId] = direction
        mistakeResult = null
        quiz = QuizSession(
            bookId = bookId,
            direction = direction,
            groupIndex = null,
            wordIndexes = remaining.map { it.wordIndex },
        )
        screen = Screen.MistakeQuiz(bookId, direction)
    }

    fun updateQuizAnswer(text: String) {
        quiz?.updateAnswer(text)
    }

    fun nextQuestion() {
        val session = quiz ?: return
        if (!session.moveNext()) finishQuiz()
    }

    fun previousQuestion() {
        quiz?.movePrevious()
    }

    fun skipQuestion() {
        val session = quiz ?: return
        session.updateAnswer("")
        if (!session.moveNext()) finishQuiz()
    }

    fun exitQuiz() {
        val session = quiz ?: return
        quiz = null
        screen = if (session.groupIndex != null) {
            Screen.BookDetail(session.bookId, session.direction)
        } else {
            Screen.Mistakes(session.bookId, session.direction)
        }
    }

    fun retryCurrentGroup() {
        val current = screen as? Screen.Results ?: return
        startQuiz(current.bookId, current.direction, current.groupIndex)
    }

    fun retryMistakes() {
        val current = screen as? Screen.MistakeResults ?: return
        startMistakeQuiz(current.bookId, current.direction)
    }

    fun backToBook() {
        val (bookId, direction) = when (val state = screen) {
            is Screen.Results -> state.bookId to state.direction
            is Screen.Mistakes -> state.bookId to state.direction
            else -> return
        }
        screen = Screen.BookDetail(bookId, direction)
    }

    fun backToMistakes() {
        val (bookId, direction) = when (val state = screen) {
            is Screen.MistakeResults -> state.bookId to state.direction
            is Screen.Mistakes -> state.bookId to state.direction
            else -> return
        }
        mistakeResult = null
        screen = Screen.Mistakes(bookId, direction)
    }

    fun backToLibrary() {
        quiz = null
        mistakeResult = null
        screen = Screen.Library
    }

    fun overrideStatus(wordIndex: Int, status: AnswerStatus) {
        val current = screen as? Screen.Results ?: return
        applyOverride(current.bookId, current.direction, current.groupIndex, wordIndex, status)
    }

    fun overrideMistakeStatus(wordIndex: Int, status: AnswerStatus) {
        val current = screen as? Screen.MistakeResults ?: return
        val result = mistakeResult ?: return
        val practiceItem = result.items.firstOrNull { it.item.wordIndex == wordIndex } ?: return
        val applied = applyOverride(
            current.bookId,
            current.direction,
            practiceItem.groupIndex,
            wordIndex,
            status,
        )
        if (!applied) return
        mistakeResult = MistakePracticeResult(
            result.bookId,
            result.direction,
            result.items.map {
                if (it.item.wordIndex == wordIndex) {
                    it.copy(item = it.item.copy(status = status, overridden = true))
                } else {
                    it
                }
            },
        )
    }

    private fun applyOverride(
        bookId: String,
        direction: StudyDirection,
        groupIndex: Int,
        wordIndex: Int,
        status: AnswerStatus,
    ): Boolean {
        val record = progress.groupResult(bookId, direction, groupIndex) ?: return false
        if (record.items.none { it.wordIndex == wordIndex }) return false

        val updatedItems = record.items.map { item ->
            if (item.wordIndex == wordIndex) {
                item.copy(status = status, overridden = true)
            } else {
                item
            }
        }
        val correct = updatedItems.count { it.status == AnswerStatus.Correct }
        progress = progress.withGroupResult(
            bookId,
            direction,
            groupIndex,
            record.copy(
                items = updatedItems,
                bestCorrect = maxOf(record.bestCorrect, correct),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        persistProgress()
        return true
    }

    fun deleteBook(bookId: String) {
        if (findBook(bookId) == null) return
        books = books.filterNot { it.id == bookId }
        progress = progress.copy(books = progress.books - bookId)
        lastDirections.remove(bookId)
        if (quiz?.bookId == bookId) quiz = null
        if (mistakeResult?.bookId == bookId) mistakeResult = null
        screen = Screen.Library
        persistProgress()
        viewModelScope.launch { library.deleteBook(bookId) }
    }

    fun importPdf(uri: Uri) {
        if (importState != null || reimportCandidate != null) return
        importState = ImportState()
        viewModelScope.launch {
            try {
                val imported = importer.import(uri) { page, total ->
                    withContext(Dispatchers.Main.immediate) {
                        importState = importState?.copy(page = page, pageCount = total)
                    }
                }
                val existing = books.firstOrNull { it.title == imported.title }
                importState = null
                if (existing != null) {
                    reimportCandidate = ReimportCandidate(
                        bookId = existing.id,
                        imported = imported,
                        existingTitle = existing.title,
                        existingWordCount = existing.words.size,
                    )
                    return@launch
                }
                val book = VocabBook(
                    id = UUID.randomUUID().toString(),
                    title = imported.title,
                    importedAt = System.currentTimeMillis(),
                    words = imported.words,
                )
                library.saveBook(book)
                books = (books + book).sortedByDescending { it.importedAt }
                lastDirections[book.id] = StudyDirection.CnToEn
                screen = Screen.BookDetail(book.id, StudyDirection.CnToEn)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AppViewModel", "PDF import failed", e)
                val message = when (e) {
                    is VocabParseException -> e.message ?: "词表格式无法识别。"
                    is InvalidPasswordException -> "暂不支持加密的 PDF。"
                    else -> "导入失败，请确认文件是支持的双栏词表 PDF。"
                }
                importState = importState?.copy(error = message)
            }
        }
    }

    fun confirmReimport() {
        val candidate = reimportCandidate ?: return
        reimportCandidate = null
        val existing = findBook(candidate.bookId) ?: return
        if (existing.words == candidate.imported.words) {
            screen = Screen.BookDetail(existing.id, StudyDirection.CnToEn)
            return
        }

        val updated = existing.copy(words = candidate.imported.words)
        books = books.map { if (it.id == updated.id) updated else it }
        progress = progress.prunedTo(updated)
        persistProgress()
        viewModelScope.launch { library.saveBook(updated) }
        lastDirections[updated.id] = StudyDirection.CnToEn
        screen = Screen.BookDetail(updated.id, StudyDirection.CnToEn)
    }

    fun dismissReimport() {
        reimportCandidate = null
    }

    fun dismissImportError() {
        if (importState?.isError == true) importState = null
    }

    fun exportBackup(uri: Uri) {
        if (backupState?.busy == true) return
        val snapshotBooks = books
        val snapshotProgress = progress
        val groups = snapshotProgress.totalGroups(snapshotBooks)
        backupState = BackupState(
            busy = true,
            title = "正在导出备份",
            message = "正在写入词书与练习记录…",
        )
        viewModelScope.launch {
            try {
                backupStore.export(uri, snapshotBooks, snapshotProgress)
                backupState = BackupState(
                    title = "备份已导出",
                    message = "已导出 ${snapshotBooks.size} 本词书、$groups 组练习记录。" +
                        "安装正式版后，在词书页选择「从备份导入」即可恢复。",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AppViewModel", "Backup export failed", e)
                backupState = BackupState(
                    title = "导出失败",
                    message = "无法写入备份文件，请重新选择保存位置。",
                    isError = true,
                )
            }
        }
    }

    fun importBackup(uri: Uri) {
        if (backupState?.busy == true) return
        backupState = BackupState(
            busy = true,
            title = "正在导入备份",
            message = "正在读取并合并备份数据…",
        )
        viewModelScope.launch {
            try {
                val backup = backupStore.read(uri)
                val restored = restoreBackup(books, progress, backup)
                for (book in restored.addedBooks) library.saveBook(book)
                books = restored.books
                progress = restored.progress
                quiz = null
                mistakeResult = null
                screen = Screen.Library
                withContext(Dispatchers.IO) {
                    progressSaveMutex.withLock {
                        progressStore.save(restored.progress)
                    }
                }
                val groups = restored.progress.totalGroups(restored.books)
                val addedText = if (restored.addedBooks.isEmpty()) {
                    "词书均已在库中"
                } else {
                    "已恢复 ${restored.addedBooks.size} 本词书"
                }
                backupState = BackupState(
                    title = "备份已恢复",
                    message = "$addedText；当前共有 $groups 组练习记录。",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AppViewModel", "Backup import failed", e)
                backupState = BackupState(
                    title = "恢复失败",
                    message = e.message
                        ?: "备份文件无法读取，请确认选择的是墨词导出的 JSON 备份。",
                    isError = true,
                )
            }
        }
    }

    fun dismissBackup() {
        if (backupState?.busy != true) backupState = null
    }

    private fun finishQuiz() {
        val session = quiz ?: return
        val book = findBook(session.bookId) ?: return
        val items = session.wordIndexes.map { wordIndex ->
            val word = book.words[wordIndex]
            val answer = session.answers[wordIndex].orEmpty().trim()
            val expected = when (session.direction) {
                StudyDirection.CnToEn -> word.word
                StudyDirection.EnToCn -> word.meaning
            }
            AnswerRecord(
                wordIndex = wordIndex,
                answer = answer,
                status = AnswerGrader.grade(answer, expected, session.direction),
            )
        }
        val groupIndex = session.groupIndex
        if (groupIndex == null) {
            finishMistakeQuiz(session, items)
        } else {
            finishGroupQuiz(session, groupIndex, items)
        }
    }

    private fun finishGroupQuiz(
        session: QuizSession,
        groupIndex: Int,
        items: List<AnswerRecord>,
    ) {
        val correct = items.count { it.status == AnswerStatus.Correct }
        val previous = progress.groupResult(
            session.bookId,
            session.direction,
            groupIndex,
        )
        val result = GroupResult(
            items = items,
            bestCorrect = maxOf(previous?.bestCorrect ?: 0, correct),
            updatedAt = System.currentTimeMillis(),
        )
        progress = progress.withGroupResult(
            session.bookId,
            session.direction,
            groupIndex,
            result,
        )
        persistProgress()
        quiz = null
        screen = Screen.Results(session.bookId, session.direction, groupIndex)
    }

    private fun finishMistakeQuiz(session: QuizSession, items: List<AnswerRecord>) {
        val merged = progress.applyMistakePractice(session.bookId, session.direction, items)
        progress = merged.progress
        persistProgress()
        mistakeResult = MistakePracticeResult(
            bookId = session.bookId,
            direction = session.direction,
            items = merged.items,
        )
        quiz = null
        screen = Screen.MistakeResults(session.bookId, session.direction)
    }

    private fun persistProgress() {
        viewModelScope.launch(Dispatchers.IO) {
            progressSaveMutex.withLock {
                progressStore.save(progress)
            }
        }
    }
}
