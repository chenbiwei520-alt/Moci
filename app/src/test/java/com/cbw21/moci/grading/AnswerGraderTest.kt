package com.cbw21.moci.grading

import com.cbw21.moci.data.AnswerStatus
import com.cbw21.moci.data.StudyDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerGraderTest {
    @Test
    fun englishIgnoresCaseAndSpacing() {
        assertEquals(
            AnswerStatus.Correct,
            AnswerGrader.grade("  BARGAIN ", "bargain", StudyDirection.CnToEn),
        )
        assertEquals(
            AnswerStatus.Correct,
            AnswerGrader.grade("according   to", "according to", StudyDirection.CnToEn),
        )
    }

    @Test
    fun englishFlagsSmallTyposAsClose() {
        assertEquals(
            AnswerStatus.Close,
            AnswerGrader.grade("bargian", "bargain", StudyDirection.CnToEn),
        )
        assertEquals(
            AnswerStatus.Close,
            AnswerGrader.grade("principel", "principle", StudyDirection.CnToEn),
        )
        assertEquals(
            AnswerStatus.Wrong,
            AnswerGrader.grade("barley", "bargain", StudyDirection.CnToEn),
        )
    }

    @Test
    fun englishToleratesInflectionAndArticleDifferences() {
        assertEquals(
            AnswerStatus.Close,
            AnswerGrader.grade("bargains", "bargain", StudyDirection.CnToEn),
        )
        assertEquals(
            AnswerStatus.Close,
            AnswerGrader.grade("bargained", "bargain", StudyDirection.CnToEn),
        )
        assertEquals(
            AnswerStatus.Close,
            AnswerGrader.grade("studies", "study", StudyDirection.CnToEn),
        )
        assertEquals(
            AnswerStatus.Close,
            AnswerGrader.grade("running", "run", StudyDirection.CnToEn),
        )
        assertEquals(
            AnswerStatus.Correct,
            AnswerGrader.grade("a bargain", "bargain", StudyDirection.CnToEn),
        )
        assertEquals(
            AnswerStatus.Correct,
            AnswerGrader.grade("merry go round", "merry-go-round", StudyDirection.CnToEn),
        )
        assertEquals(
            AnswerStatus.Close,
            AnswerGrader.grade("dont", "don't", StudyDirection.CnToEn),
        )
        assertEquals(
            AnswerStatus.Close,
            AnswerGrader.grade("new", "news", StudyDirection.CnToEn),
        )
        assertEquals(
            AnswerStatus.Close,
            AnswerGrader.grade("according", "according to", StudyDirection.CnToEn),
        )
    }

    @Test
    fun englishBlankAndPunctuationOnly() {
        assertEquals(
            AnswerStatus.Blank,
            AnswerGrader.grade("   ", "bargain", StudyDirection.CnToEn),
        )
        assertEquals(
            AnswerStatus.Wrong,
            AnswerGrader.grade("...", "bargain", StudyDirection.CnToEn),
        )
    }

    @Test
    fun chineseAcceptsAnyCorrectGloss() {
        val meaning = "n. 便宜货;协议,协定\nvi. 讨价还价,洽谈,谈条件"

        assertEquals(
            AnswerStatus.Correct,
            AnswerGrader.grade("协议", meaning, StudyDirection.EnToCn),
        )
        assertEquals(
            AnswerStatus.Correct,
            AnswerGrader.grade("便宜货", meaning, StudyDirection.EnToCn),
        )
        assertEquals(
            AnswerStatus.Correct,
            AnswerGrader.grade("讨价还价，谈条件", meaning, StudyDirection.EnToCn),
        )
        assertEquals(
            AnswerStatus.Correct,
            AnswerGrader.grade("n. 协定", meaning, StudyDirection.EnToCn),
        )
    }

    @Test
    fun chineseHandlesParentheticalAndEllipsisGlosses() {
        val meaning = "adj. (合同) 无效的;缺乏...的;空的"

        assertEquals(
            AnswerStatus.Correct,
            AnswerGrader.grade("无效的", meaning, StudyDirection.EnToCn),
        )
        assertEquals(
            AnswerStatus.Correct,
            AnswerGrader.grade("缺乏的", meaning, StudyDirection.EnToCn),
        )
        assertEquals(
            AnswerStatus.Correct,
            AnswerGrader.grade("空的", meaning, StudyDirection.EnToCn),
        )
    }

    @Test
    fun chineseMarksPartialWordingAsCloseAndUnrelatedAsWrong() {
        val meaning = "n. 便宜货;协议,协定\nvi. 讨价还价,洽谈,谈条件"

        assertEquals(
            AnswerStatus.Wrong,
            AnswerGrader.grade("昂贵", meaning, StudyDirection.EnToCn),
        )
        assertEquals(
            AnswerStatus.Close,
            AnswerGrader.grade("协", meaning, StudyDirection.EnToCn),
        )
        assertEquals(
            AnswerStatus.Close,
            AnswerGrader.grade("讨价还钱", meaning, StudyDirection.EnToCn),
        )
    }

    @Test
    fun chineseAcceptsNearSynonymsAndShortGlosses() {
        assertEquals(
            AnswerStatus.Close,
            AnswerGrader.grade("合约", "n. 合同;契约", StudyDirection.EnToCn),
        )
        assertEquals(
            AnswerStatus.Close,
            AnswerGrader.grade("丢弃", "v. 放弃,抛弃", StudyDirection.EnToCn),
        )
        assertEquals(
            AnswerStatus.Close,
            AnswerGrader.grade("空", "adj. 空的", StudyDirection.EnToCn),
        )
        assertEquals(
            AnswerStatus.Correct,
            AnswerGrader.grade("便宜", "n. 便宜货", StudyDirection.EnToCn),
        )
    }

    @Test
    fun chineseDoesNotTreatOppositesAsClose() {
        assertEquals(
            AnswerStatus.Wrong,
            AnswerGrader.grade("有效", "adj. 无效的", StudyDirection.EnToCn),
        )
        assertEquals(
            AnswerStatus.Wrong,
            AnswerGrader.grade("合法", "adj. 非法的", StudyDirection.EnToCn),
        )
        assertEquals(
            AnswerStatus.Wrong,
            AnswerGrader.grade("可能", "adj. 不可能的", StudyDirection.EnToCn),
        )
    }

    @Test
    fun chineseBlank() {
        assertEquals(
            AnswerStatus.Blank,
            AnswerGrader.grade(" ", "n. 伏特加", StudyDirection.EnToCn),
        )
    }
}
