package com.cbw21.moci.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabTextParserTest {
    @Test
    fun parsesAllPagesOfASampleBook() {
        val pages = syntheticPages()

        val words = VocabTextParser.parsePages(pages)

        assertEquals(60, words.size)
        assertEquals((1..60).toList(), words.map { it.number })
        assertTrue(words.all { it.word.isNotBlank() && it.meaning.isNotBlank() })
        assertEquals("word60", words.last().word)
    }

    @Test
    fun joinsHyphenatedWordWrapsAndNormalizesRadicals() {
        val page = """
            Word Meaning
            1 merry-
            go-round
            2 sample
            Word Meaning
            1 n. 旋转木马
            2 n. ⻨子
        """.trimIndent()

        val words = VocabTextParser.parsePages(listOf(page))

        assertEquals("merry-go-round", words.first().word)
        assertEquals("n. 麦子", words.last().meaning)

        val radicalOnly = words.asSequence()
            .flatMap { sequenceOf(it.word, it.meaning) }
            .flatMap { it.asSequence() }
            .any { it.code in 0x2E80..0x2EFF }
        assertFalse("CJK radicals must be normalized before display", radicalOnly)
    }

    @Test
    fun keepsContinuationLinesInMeanings() {
        val page = """
            Word Meaning
            1 bargain
            Word Meaning
            1 n. 便宜货;协议,协定
            vi. 讨价还价,洽谈,谈条件
        """.trimIndent()

        val words = VocabTextParser.parsePages(listOf(page))
        val bargain = words.first()

        assertTrue(bargain.meaning.contains("\n"))
        assertTrue(bargain.meaning.contains("vi. 讨价还价,洽谈,谈条件"))
    }

    @Test(expected = VocabParseException::class)
    fun rejectsPageWithoutTwoColumnHeaders() {
        VocabTextParser.parsePages(listOf("1 word\n2 another\n1 meaning\n2 another meaning"))
    }

    @Test
    fun dropsBookTitleFootersFromMeanings() {
        val page = """
            Word Meaning
            80 favour
            Word Meaning
            80 n. 帮助,恩惠;赞同,支持;偏爱
            vt. 更喜欢;偏爱;有利于
            2027考研英语红宝书(乱序版)
        """.trimIndent()

        val words = VocabTextParser.parsePages(listOf(page))

        assertEquals(1, words.size)
        assertEquals("favour", words.single().word)
        assertEquals(
            "n. 帮助,恩惠;赞同,支持;偏爱\nvt. 更喜欢;偏爱;有利于",
            words.single().meaning,
        )
    }

    @Test
    fun dropsRepeatedPageChromeFromMeanings() {
        fun page(first: Int, second: Int, firstMeaning: String, secondMeaning: String) = """
            Word Meaning
            $first alpha$first
            $second beta$second
            Word Meaning
            $first n. $firstMeaning
            $second n. $secondMeaning
            内部资料 请勿外传
        """.trimIndent()

        val pages = listOf(
            page(1, 2, "甲", "乙"),
            page(3, 4, "丙", "丁"),
            page(5, 6, "戊", "己"),
        )
        val words = VocabTextParser.parsePages(pages)

        assertEquals(6, words.size)
        assertTrue(words.none { it.meaning.contains("内部资料") })
        assertEquals("n. 甲", words.first().meaning)
    }

    @Test
    fun keepsPosMeaningThatMentionsBookWords() {
        val page = """
            Word Meaning
            1 toefl
            Word Meaning
            1 n. 托福考试
        """.trimIndent()

        val words = VocabTextParser.parsePages(listOf(page))

        assertEquals("n. 托福考试", words.single().meaning)
    }

    private fun syntheticPages(): List<String> = (1..3).map { pageIndex ->
        val start = (pageIndex - 1) * 20 + 1
        val end = start + 19
        buildString {
            appendLine("Word Meaning")
            for (number in start..end) appendLine("$number word$number")
            appendLine("Word Meaning")
            for (number in start..end) appendLine("$number n. 释义$number")
            appendLine("共 60 词 第 $pageIndex 页")
        }
    }
}
