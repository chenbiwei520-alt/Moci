package com.cbw21.moci.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MistakesTest {
    @Test
    fun collectsOnlyWrongAndCloseEntries() {
        val book = book(wordCount = 60)
        val progress = progress(
            StudyDirection.CnToEn,
            0 to result(
                AnswerRecord(0, "wrong", AnswerStatus.Wrong),
                AnswerRecord(1, "correct", AnswerStatus.Correct),
                AnswerRecord(2, "close", AnswerStatus.Close),
                AnswerRecord(3, "", AnswerStatus.Blank),
            ),
            1 to result(
                AnswerRecord(25, "late close", AnswerStatus.Close),
            ),
        )

        val mistakes = progress.mistakes(book, StudyDirection.CnToEn)

        assertEquals(listOf(0, 2, 25), mistakes.map { it.wordIndex })
        assertEquals(listOf(0, 0, 1), mistakes.map { it.groupIndex })
        assertEquals(
            listOf(AnswerStatus.Wrong, AnswerStatus.Close, AnswerStatus.Close),
            mistakes.map { it.status },
        )
        assertEquals("wrong", mistakes.first().answer)
    }

    @Test
    fun keepsDirectionsIndependent() {
        val book = book(wordCount = 30)
        val progress = progress(
            StudyDirection.CnToEn,
            0 to result(AnswerRecord(4, "", AnswerStatus.Wrong)),
        )

        assertTrue(progress.mistakes(book, StudyDirection.EnToCn).isEmpty())
        assertEquals(1, progress.mistakes(book, StudyDirection.CnToEn).size)
    }

    @Test
    fun dropsOutOfRangeWordIndexes() {
        val book = book(wordCount = 20)
        val progress = progress(
            StudyDirection.CnToEn,
            0 to result(
                AnswerRecord(19, "", AnswerStatus.Wrong),
                AnswerRecord(20, "", AnswerStatus.Close),
            ),
        )

        val mistakes = progress.mistakes(book, StudyDirection.CnToEn)

        assertEquals(listOf(19), mistakes.map { it.wordIndex })
    }

    @Test
    fun mistakePracticeMergesIntoOriginalGroupsAndClearsCorrectedWords() {
        val book = book(wordCount = 40)
        val progress = progress(
            StudyDirection.CnToEn,
            0 to result(
                AnswerRecord(0, "old", AnswerStatus.Wrong),
                AnswerRecord(1, "old", AnswerStatus.Wrong),
                AnswerRecord(2, "ok", AnswerStatus.Correct),
                AnswerRecord(3, "close", AnswerStatus.Close),
            ),
            1 to result(
                AnswerRecord(25, "old", AnswerStatus.Wrong),
            ),
        )

        val merged = progress.applyMistakePractice(
            "book",
            StudyDirection.CnToEn,
            listOf(
                AnswerRecord(0, "recovered", AnswerStatus.Correct),
                AnswerRecord(1, "again", AnswerStatus.Wrong),
                AnswerRecord(25, "recovered", AnswerStatus.Correct),
            ),
        )

        assertEquals(
            listOf(0, 1, 25),
            merged.items.map { it.item.wordIndex },
        )
        assertEquals(listOf(0, 0, 1), merged.items.map { it.groupIndex })

        val group0 = merged.progress.groupResult("book", StudyDirection.CnToEn, 0)!!
        assertEquals(
            listOf(AnswerStatus.Correct, AnswerStatus.Wrong, AnswerStatus.Correct, AnswerStatus.Close),
            group0.items.map { it.status },
        )
        assertEquals("recovered", group0.items.first().answer)
        assertEquals(2, group0.correctCount)
        assertEquals(2, group0.bestCorrect)

        val group1 = merged.progress.groupResult("book", StudyDirection.CnToEn, 1)!!
        assertEquals(AnswerStatus.Correct, group1.items.single().status)
        assertEquals(1, group1.bestCorrect)

        assertEquals(
            listOf(1, 3),
            merged.progress.mistakes(book, StudyDirection.CnToEn).map { it.wordIndex },
        )
    }

    @Test
    fun mistakePracticeKeepsHigherBestCorrect() {
        val book = book(wordCount = 20)
        val items = (0 until 20).map { index ->
            AnswerRecord(
                wordIndex = index,
                answer = "old",
                status = if (index == 0) AnswerStatus.Wrong else AnswerStatus.Correct,
            )
        }
        val progress = progress(
            StudyDirection.CnToEn,
            0 to GroupResult(items = items, bestCorrect = 19, updatedAt = 0L),
        )

        val merged = progress.applyMistakePractice(
            "book",
            StudyDirection.CnToEn,
            listOf(AnswerRecord(0, "still wrong", AnswerStatus.Wrong)),
        )

        val group = merged.progress.groupResult("book", StudyDirection.CnToEn, 0)!!
        assertEquals(19, group.correctCount)
        assertEquals(19, group.bestCorrect)
        assertEquals(
            listOf(0),
            merged.progress.mistakes(book, StudyDirection.CnToEn).map { it.wordIndex },
        )
    }

    @Test
    fun mistakePracticeIgnoresWordsOutsideGroups() {
        val book = book(wordCount = 20)
        val progress = progress(
            StudyDirection.CnToEn,
            0 to result(AnswerRecord(0, "old", AnswerStatus.Wrong)),
        )

        val merged = progress.applyMistakePractice(
            "book",
            StudyDirection.CnToEn,
            listOf(AnswerRecord(0, "fixed", AnswerStatus.Correct), AnswerRecord(99, "?", AnswerStatus.Correct)),
        )

        assertEquals(listOf(0), merged.items.map { it.item.wordIndex })
        assertTrue(merged.progress.mistakes(book, StudyDirection.CnToEn).isEmpty())
        assertEquals(progress.groupResult("book", StudyDirection.CnToEn, 0)!!.items.size, 1)
        assertEquals(1, merged.progress.groupResult("book", StudyDirection.CnToEn, 0)!!.bestCorrect)
    }

    private fun book(wordCount: Int) = VocabBook(
        id = "book",
        title = "测试词书",
        importedAt = 0L,
        words = (1..wordCount).map { VocabWord(it, "word$it", "释义$it") },
    )

    private fun result(vararg items: AnswerRecord) = GroupResult(
        items = items.toList(),
        bestCorrect = items.count { it.status == AnswerStatus.Correct },
        updatedAt = 0L,
    )

    private fun progress(
        direction: StudyDirection,
        vararg groups: Pair<Int, GroupResult>,
    ) = ProgressData(
        books = mapOf(
            "book" to BookProgress(
                directions = mapOf(
                    direction.storageKey to DirectionProgress(groups.toMap()),
                ),
            ),
        ),
    )
}
