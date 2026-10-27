package com.cbw21.moci.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VocabWord(
    val number: Int,
    val word: String,
    val meaning: String,
)

@Serializable
data class VocabBook(
    val id: String,
    val title: String,
    val importedAt: Long,
    val words: List<VocabWord>,
)

enum class StudyDirection(
    val storageKey: String,
    val label: String,
    val promptLabel: String,
) {
    CnToEn("cn_to_en", "中 → 英", "看中文，写英文"),
    EnToCn("en_to_cn", "英 → 中", "看英文，写中文");

    val targetLanguage: HandwritingLanguage
        get() = if (this == CnToEn) HandwritingLanguage.English else HandwritingLanguage.Chinese
}

enum class HandwritingLanguage(val languageTag: String) {
    English("en-US"),
    Chinese("zh-Hani-CN"),
}

@Serializable
enum class AnswerStatus {
    @SerialName("Correct") Correct,
    @SerialName("Close") Close,
    @SerialName("Wrong") Wrong,
    @SerialName("Blank") Blank,
}

@Serializable
data class AnswerRecord(
    val wordIndex: Int,
    val answer: String,
    val status: AnswerStatus,
    val overridden: Boolean = false,
)

@Serializable
data class GroupResult(
    val items: List<AnswerRecord>,
    val bestCorrect: Int,
    val updatedAt: Long,
) {
    val correctCount: Int get() = items.count { it.status == AnswerStatus.Correct }
    val closeCount: Int get() = items.count { it.status == AnswerStatus.Close }
    val wrongCount: Int get() = items.count { it.status == AnswerStatus.Wrong }
    val blankCount: Int get() = items.count { it.status == AnswerStatus.Blank }
    val total: Int get() = items.size
}

@Serializable
data class DirectionProgress(
    val groups: Map<Int, GroupResult> = emptyMap(),
)

@Serializable
data class BookProgress(
    val directions: Map<String, DirectionProgress> = emptyMap(),
)

const val BACKUP_VERSION = 1

@Serializable
data class BackupData(
    val version: Int = BACKUP_VERSION,
    val exportedAt: Long = 0L,
    val books: List<VocabBook> = emptyList(),
    val progress: ProgressData = ProgressData(),
)

class BackupRestore(
    val books: List<VocabBook>,
    val addedBooks: List<VocabBook>,
    val progress: ProgressData,
    val restoredGroups: Int,
)

@Serializable
data class ProgressData(
    val books: Map<String, BookProgress> = emptyMap(),
) {
    fun groupResult(bookId: String, direction: StudyDirection, groupIndex: Int): GroupResult? =
        books[bookId]?.directions?.get(direction.storageKey)?.groups?.get(groupIndex)

    fun directionProgress(bookId: String, direction: StudyDirection): DirectionProgress =
        books[bookId]?.directions?.get(direction.storageKey) ?: DirectionProgress()

    fun masteredWords(book: VocabBook, direction: StudyDirection): Int {
        val groups = directionProgress(book.id, direction).groups
        var count = 0
        for ((groupIndex, record) in groups) {
            val groupWordCount = book.groupSize(groupIndex)
            count += record.bestCorrect.coerceAtMost(groupWordCount)
        }
        return count
    }

    fun completedGroups(book: VocabBook, direction: StudyDirection): Int =
        directionProgress(book.id, direction).groups.size
}

data class MistakeEntry(
    val wordIndex: Int,
    val groupIndex: Int,
    val status: AnswerStatus,
    val answer: String,
)

data class MistakePracticeItem(
    val groupIndex: Int,
    val item: AnswerRecord,
)

class MistakePracticeMerge(
    val progress: ProgressData,
    val items: List<MistakePracticeItem>,
)

fun ProgressData.mistakes(book: VocabBook, direction: StudyDirection): List<MistakeEntry> =
    directionProgress(book.id, direction).groups.entries
        .flatMap { (groupIndex, result) ->
            result.items.mapNotNull { item ->
                if (item.status != AnswerStatus.Wrong && item.status != AnswerStatus.Close) {
                    null
                } else if (item.wordIndex !in book.words.indices) {
                    null
                } else {
                    MistakeEntry(
                        wordIndex = item.wordIndex,
                        groupIndex = groupIndex,
                        status = item.status,
                        answer = item.answer,
                    )
                }
            }
        }
        .distinctBy { it.wordIndex }
        .sortedBy { it.wordIndex }

fun ProgressData.applyMistakePractice(
    bookId: String,
    direction: StudyDirection,
    graded: List<AnswerRecord>,
): MistakePracticeMerge {
    val groupByWord = mutableMapOf<Int, Int>()
    for ((groupIndex, result) in directionProgress(bookId, direction).groups) {
        result.items.forEach { groupByWord[it.wordIndex] = groupIndex }
    }
    val practiceItems = graded.mapNotNull { record ->
        val groupIndex = groupByWord[record.wordIndex] ?: return@mapNotNull null
        MistakePracticeItem(groupIndex, record)
    }

    var updated = this
    for ((groupIndex, groupItems) in practiceItems.groupBy { it.groupIndex }) {
        val record = updated.groupResult(bookId, direction, groupIndex) ?: continue
        val replacements = groupItems.associate { it.item.wordIndex to it.item }
        val mergedItems = record.items.map { replacements[it.wordIndex] ?: it }
        val correct = mergedItems.count { it.status == AnswerStatus.Correct }
        updated = updated.withGroupResult(
            bookId,
            direction,
            groupIndex,
            record.copy(
                items = mergedItems,
                bestCorrect = maxOf(record.bestCorrect, correct),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
    return MistakePracticeMerge(updated, practiceItems)
}

fun ProgressData.withGroupResult(
    bookId: String,
    direction: StudyDirection,
    groupIndex: Int,
    result: GroupResult,
): ProgressData {
    val bookProgress = books[bookId] ?: BookProgress()
    val directionProgress = bookProgress.directions[direction.storageKey] ?: DirectionProgress()
    val updatedDirection = directionProgress.copy(
        groups = directionProgress.groups + (groupIndex to result),
    )
    val updatedBook = bookProgress.copy(
        directions = bookProgress.directions + (direction.storageKey to updatedDirection),
    )
    return copy(books = books + (bookId to updatedBook))
}

fun ProgressData.totalGroups(books: List<VocabBook>): Int =
    books.sumOf { book ->
        StudyDirection.entries.sumOf { direction ->
            directionProgress(book.id, direction).groups.size
        }
    }

fun restoreBackup(
    localBooks: List<VocabBook>,
    localProgress: ProgressData,
    backup: BackupData,
): BackupRestore {
    val localById = localBooks.associateBy { it.id }
    val alias = HashMap<String, String>()
    val added = ArrayList<VocabBook>()
    val seenBackupIds = HashSet<String>()

    for (book in backup.books) {
        if (book.id.isBlank() || book.words.isEmpty()) continue
        if (!seenBackupIds.add(book.id)) continue
        if (localById.containsKey(book.id)) continue
        val sameContent = (localBooks + added).firstOrNull {
            it.title == book.title && it.words == book.words
        }
        if (sameContent != null) {
            alias[book.id] = sameContent.id
        } else {
            added += book
        }
    }

    val allBooks = (localBooks + added).sortedByDescending { it.importedAt }
    val targetIds = allBooks.mapTo(HashSet()) { it.id }
    var merged = localProgress
    var restoredGroups = 0

    for ((sourceId, bookProgress) in backup.progress.books) {
        val targetId = alias[sourceId] ?: sourceId
        if (targetId !in targetIds) continue
        for ((storageKey, directionProgress) in bookProgress.directions) {
            val direction = StudyDirection.entries.firstOrNull {
                it.storageKey == storageKey
            } ?: continue
            for ((groupIndex, incoming) in directionProgress.groups) {
                val existing = merged.groupResult(targetId, direction, groupIndex)
                val chosen = newerRecord(existing, incoming)
                if (chosen != existing) {
                    merged = merged.withGroupResult(targetId, direction, groupIndex, chosen)
                    restoredGroups++
                }
            }
        }
    }

    val pruned = allBooks.fold(merged) { data, book -> data.prunedTo(book) }
    val cleaned = pruned.copy(books = pruned.books.filterKeys { it in targetIds })
    return BackupRestore(allBooks, added, cleaned, restoredGroups)
}

private fun newerRecord(existing: GroupResult?, incoming: GroupResult): GroupResult {
    if (existing == null) return incoming
    if (incoming.updatedAt != existing.updatedAt) {
        return if (incoming.updatedAt > existing.updatedAt) incoming else existing
    }
    return if (incoming.bestCorrect > existing.bestCorrect) incoming else existing
}

fun ProgressData.prunedTo(book: VocabBook): ProgressData {
    val bookProgress = books[book.id] ?: return this
    val groupCount = book.groupCount()
    var changed = false
    val directions = bookProgress.directions.mapValues { (_, direction) ->
        val groups = direction.groups.mapNotNull { (index, result) ->
            if (index !in 0 until groupCount) {
                changed = true
                return@mapNotNull null
            }
            val items = result.items.filter { it.wordIndex in book.words.indices }
            val bestCorrect = result.bestCorrect.coerceAtMost(book.groupRange(index).count())
            if (items.size != result.items.size || bestCorrect != result.bestCorrect) {
                changed = true
            }
            index to result.copy(items = items, bestCorrect = bestCorrect)
        }.toMap()
        direction.copy(groups = groups)
    }
    if (!changed) return this
    return copy(books = books + (book.id to bookProgress.copy(directions = directions)))
}
