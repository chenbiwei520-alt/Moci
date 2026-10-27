package com.cbw21.moci.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    @Test
    fun restoresBooksAndProgressIntoEmptyLibrary() {
        val book = book("book-1", 45)
        val backup = BackupData(
            exportedAt = 1_000L,
            books = listOf(book),
            progress = progress(
                "book-1",
                StudyDirection.CnToEn,
                0 to result(
                    AnswerRecord(0, "apple", AnswerStatus.Correct),
                    AnswerRecord(1, "bannana", AnswerStatus.Close),
                ),
            ),
        )

        val restored = restoreBackup(emptyList(), ProgressData(), backup)

        assertEquals(listOf(book), restored.books)
        assertEquals(1, restored.addedBooks.size)
        assertEquals(1, restored.restoredGroups)
        assertEquals(1, restored.progress.totalGroups(restored.books))
        assertEquals(
            "bannana",
            restored.progress
                .groupResult("book-1", StudyDirection.CnToEn, 0)
                ?.items
                ?.get(1)
                ?.answer,
        )
    }

    @Test
    fun keepsNewestRecordPerGroup() {
        val book = book("book-1", 60)
        val local = progress(
            "book-1",
            StudyDirection.CnToEn,
            0 to result(
                AnswerRecord(0, "local", AnswerStatus.Correct),
                updatedAt = 500L,
            ),
        )
        val incoming = progress(
            "book-1",
            StudyDirection.CnToEn,
            0 to result(
                AnswerRecord(0, "backup", AnswerStatus.Correct),
                AnswerRecord(1, "backup", AnswerStatus.Correct),
                AnswerRecord(2, "backup", AnswerStatus.Correct),
                updatedAt = 100L,
            ),
            1 to result(
                AnswerRecord(20, "newer", AnswerStatus.Wrong),
                updatedAt = 600L,
            ),
        )

        val restored = restoreBackup(
            listOf(book),
            local,
            BackupData(books = listOf(book), progress = incoming),
        )

        val group0 = restored.progress.groupResult("book-1", StudyDirection.CnToEn, 0)
        assertEquals("local", group0?.items?.single()?.answer)
        assertEquals(1, group0?.bestCorrect)
        assertEquals(1, restored.restoredGroups)
        assertTrue(restored.addedBooks.isEmpty())
    }

    @Test
    fun tiesPreferTheHigherScore() {
        val book = book("book-1", 20)
        val local = progress(
            "book-1",
            StudyDirection.EnToCn,
            0 to result(AnswerRecord(0, "a", AnswerStatus.Wrong), updatedAt = 100L),
        )
        val incoming = progress(
            "book-1",
            StudyDirection.EnToCn,
            0 to result(
                AnswerRecord(0, "a", AnswerStatus.Correct),
                AnswerRecord(1, "b", AnswerStatus.Correct),
                updatedAt = 100L,
            ),
        )

        val restored = restoreBackup(
            listOf(book),
            local,
            BackupData(books = listOf(book), progress = incoming),
        )

        assertEquals(
            2,
            restored.progress.groupResult("book-1", StudyDirection.EnToCn, 0)?.bestCorrect,
        )
    }

    @Test
    fun remapsProgressForExistingBookWithSameContent() {
        val localBook = book("local-id", 30, title = "同名词书")
        val backupBook = book("backup-id", 30, title = "同名词书")
        val backupProgress = progress(
            "backup-id",
            StudyDirection.CnToEn,
            0 to result(AnswerRecord(0, "apple", AnswerStatus.Correct)),
        )

        val restored = restoreBackup(
            listOf(localBook),
            ProgressData(),
            BackupData(books = listOf(backupBook), progress = backupProgress),
        )

        assertEquals(listOf(localBook), restored.books)
        assertTrue(restored.addedBooks.isEmpty())
        assertEquals(
            1,
            restored.progress.directionProgress("local-id", StudyDirection.CnToEn).groups.size,
        )
        assertTrue(restored.progress.books.containsKey("local-id"))
    }

    @Test
    fun dropsUnknownBooksAndOutOfRangeGroups() {
        val book = book("known", 20)
        val backup = BackupData(
            books = listOf(book),
            progress = ProgressData(
                books = mapOf(
                    "known" to BookProgress(
                        directions = mapOf(
                            StudyDirection.CnToEn.storageKey to DirectionProgress(
                                mapOf(
                                    0 to result(
                                        AnswerRecord(0, "ok", AnswerStatus.Correct),
                                        AnswerRecord(99, "gone", AnswerStatus.Wrong),
                                    ),
                                    7 to result(AnswerRecord(0, "out", AnswerStatus.Wrong)),
                                ),
                            ),
                        ),
                    ),
                    "ghost" to BookProgress(
                        directions = mapOf(
                            StudyDirection.CnToEn.storageKey to DirectionProgress(
                                mapOf(0 to result(AnswerRecord(0, "", AnswerStatus.Wrong))),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val restored = restoreBackup(emptyList(), ProgressData(), backup)

        assertEquals(1, restored.books.size)
        assertEquals(1, restored.progress.totalGroups(restored.books))
        assertEquals(
            1,
            restored.progress
                .groupResult("known", StudyDirection.CnToEn, 0)
                ?.items
                ?.size,
        )
        assertTrue(!restored.progress.books.containsKey("ghost"))
    }

    @Test
    fun reimportingTheSameBackupIsIdempotent() {
        val book = book("book-1", 25)
        val backup = BackupData(
            books = listOf(book),
            progress = progress(
                "book-1",
                StudyDirection.CnToEn,
                0 to result(AnswerRecord(3, "pear", AnswerStatus.Correct)),
            ),
        )

        val first = restoreBackup(emptyList(), ProgressData(), backup)
        val second = restoreBackup(first.books, first.progress, backup)

        assertEquals(0, second.addedBooks.size)
        assertEquals(first.progress, second.progress)
        assertEquals(0, second.restoredGroups)
    }

    @Test
    fun jsonRoundTripKeepsAllFields() {
        val backup = BackupData(
            exportedAt = 123L,
            books = listOf(book("book-1", 25)),
            progress = progress(
                "book-1",
                StudyDirection.EnToCn,
                0 to result(
                    AnswerRecord(0, "苹果", AnswerStatus.Correct),
                    AnswerRecord(1, "", AnswerStatus.Blank),
                ),
            ),
        )
        val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        val text = json.encodeToString(BackupData.serializer(), backup)
        val decoded = json.decodeFromString(BackupData.serializer(), text)

        assertEquals(backup, decoded)
        assertEquals(1, decoded.version)
    }

    @Test
    fun answerStatusKeepsLegacySerialNames() {
        val json = Json
        assertEquals("\"Correct\"", json.encodeToString(AnswerStatus.serializer(), AnswerStatus.Correct))
        assertEquals("\"Close\"", json.encodeToString(AnswerStatus.serializer(), AnswerStatus.Close))
        assertEquals("\"Wrong\"", json.encodeToString(AnswerStatus.serializer(), AnswerStatus.Wrong))
        assertEquals("\"Blank\"", json.encodeToString(AnswerStatus.serializer(), AnswerStatus.Blank))
    }

    private fun book(
        id: String,
        wordCount: Int,
        title: String = "测试词书",
    ) = VocabBook(
        id = id,
        title = title,
        importedAt = 1L,
        words = (1..wordCount).map { VocabWord(it, "word$it", "释义$it") },
    )

    private fun result(
        vararg items: AnswerRecord,
        updatedAt: Long = 0L,
    ) = GroupResult(
        items = items.toList(),
        bestCorrect = items.count { it.status == AnswerStatus.Correct },
        updatedAt = updatedAt,
    )

    private fun progress(
        bookId: String,
        direction: StudyDirection,
        vararg groups: Pair<Int, GroupResult>,
    ) = ProgressData(
        books = mapOf(
            bookId to BookProgress(
                directions = mapOf(
                    direction.storageKey to DirectionProgress(groups.toMap()),
                ),
            ),
        ),
    )
}
