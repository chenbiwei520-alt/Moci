package com.cbw21.moci.grading

import com.cbw21.moci.data.AnswerStatus
import com.cbw21.moci.data.StudyDirection
import java.text.Normalizer
import java.util.Locale

object AnswerGrader {
    private val posTag = Regex(
        """(?i)(?<![a-z])(?:n|v|vt|vi|adj|adv|prep|pron|conj|num|art|int|aux|abbr|det|pref|suff|usage|vlink|pl)\.(?![a-z])""",
    )
    private val hanOnly = Regex("""[^\p{IsHan}]+""")
    private val spaces = Regex("""\s+""")
    private val dash = Regex("""[‐‑‒–—―−]""")
    private val spacedDash = Regex("""\s*([-'/])\s*""")
    private val leadingArticle = Regex("""^(?:a|an|the)\s+""", RegexOption.IGNORE_CASE)
    private val separator = Regex("""[\s-]+""")
    private val nonLetter = Regex("""[^\p{L}]""")

    private val trivialHan = setOf('的', '地', '得', '了', '着', '过', '之', '等', '与', '和', '或', '及')
    private val negationHan = setOf('不', '无', '未', '没', '别', '勿', '非', '莫')
    private val sFinalWords = setOf(
        "news", "series", "species", "means", "physics", "mathematics", "politics",
        "economics", "ethics", "jeans", "clothes", "scissors", "trousers",
        "headquarters", "crossroads",
    )

    fun grade(
        answer: String,
        expected: String,
        direction: StudyDirection,
    ): AnswerStatus {
        val trimmed = answer.trim()
        if (trimmed.isEmpty()) return AnswerStatus.Blank

        return when (direction) {
            StudyDirection.CnToEn -> gradeEnglish(expected, trimmed)
            StudyDirection.EnToCn -> gradeChinese(expected, trimmed)
        }
    }

    fun gradeEnglish(expected: String, answer: String): AnswerStatus {
        val correct = normalizeEnglish(expected)
        val actual = normalizeEnglish(answer)
        if (actual.isEmpty()) return AnswerStatus.Wrong
        if (actual == correct) return AnswerStatus.Correct

        val correctCompact = correct.replace(separator, "")
        val actualCompact = actual.replace(separator, "")
        if (correctCompact.isNotEmpty() && correctCompact == actualCompact) {
            return AnswerStatus.Correct
        }
        if (
            correctCompact.isNotEmpty() &&
            correctCompact.replace("'", "") == actualCompact.replace("'", "")
        ) {
            return AnswerStatus.Close
        }

        val correctNoArticle = dropLeadingArticle(correct)
        val actualNoArticle = dropLeadingArticle(actual)
        if (
            correctNoArticle.isNotEmpty() &&
            correctNoArticle.replace(separator, "") == actualNoArticle.replace(separator, "")
        ) {
            return AnswerStatus.Correct
        }
        if (inflectionMatches(correctNoArticle, actualNoArticle)) {
            return AnswerStatus.Close
        }

        val correctLetters = correctCompact.replace(nonLetter, "")
        val actualLetters = actualCompact.replace(nonLetter, "")
        if (correctLetters.isEmpty() || actualLetters.isEmpty()) return AnswerStatus.Wrong

        val distance = editDistance(correctLetters, actualLetters)
        val tolerance = when {
            correctLetters.length < 4 -> 0
            correctLetters.length <= 8 -> 1
            else -> 2
        }
        return if (distance in 1..tolerance) AnswerStatus.Close else AnswerStatus.Wrong
    }

    fun gradeChinese(expected: String, answer: String): AnswerStatus {
        val expectedSegments = chineseSegments(expected)
        val actualSegments = chineseSegments(answer)
        if (expectedSegments.isEmpty()) {
            return if (normalizeChineseText(expected) == normalizeChineseText(answer)) {
                AnswerStatus.Correct
            } else {
                AnswerStatus.Wrong
            }
        }
        if (actualSegments.isEmpty()) return AnswerStatus.Wrong

        for (actual in actualSegments) {
            for (expectedSegment in expectedSegments) {
                if (negationChars(actual) != negationChars(expectedSegment)) continue
                if (actual == expectedSegment) return AnswerStatus.Correct
                if (expectedSegment.length >= 2 && actual.contains(expectedSegment)) {
                    return AnswerStatus.Correct
                }
                if (actual.length >= 2 && expectedSegment.contains(actual)) {
                    return AnswerStatus.Correct
                }
            }
        }

        var bestSimilarity = 0.0
        var bestSharesCharacter = false
        for (actual in actualSegments) {
            for (expectedSegment in expectedSegments) {
                if (negationChars(actual) != negationChars(expectedSegment)) continue
                if (
                    actual.length == 1 &&
                    actual[0] !in trivialHan &&
                    actual[0] !in negationHan &&
                    expectedSegment.length >= 2 &&
                    expectedSegment.contains(actual)
                ) {
                    return AnswerStatus.Close
                }
                if (actual.length >= 2 && expectedSegment.length >= 2) {
                    val similarity = similarity(actual, expectedSegment)
                    val sharesCharacter = actual.any { it in expectedSegment }
                    if (
                        similarity > bestSimilarity ||
                        (similarity == bestSimilarity && sharesCharacter)
                    ) {
                        bestSimilarity = similarity
                        bestSharesCharacter = sharesCharacter
                    }
                }
            }
        }

        val fuzzy = bestSimilarity >= 0.66 ||
            (bestSimilarity >= 0.5 && bestSharesCharacter)
        return if (fuzzy) AnswerStatus.Close else AnswerStatus.Wrong
    }

    private fun chineseSegments(text: String): List<String> =
        normalizeChineseText(text)
            .split(hanOnly)
            .filter { it.isNotEmpty() }
            .distinct()

    private fun normalizeChineseText(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(posTag, "")

    private fun normalizeEnglish(text: String): String {
        var value = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace(dash, "-")
        value = spacedDash.replace(value) { it.groupValues[1] }
        value = spaces.replace(value.trim(), " ")
        return value.trim { it in ".,;:!?\"'，。；：！？、 \t\n" }
    }

    private fun dropLeadingArticle(value: String): String =
        value.replace(leadingArticle, "").trim()

    private fun inflectionMatches(expected: String, actual: String): Boolean {
        val expectedTokens = expected.split(' ').filter { it.isNotEmpty() }
        val actualTokens = actual.split(' ').filter { it.isNotEmpty() }
        if (expectedTokens.size != actualTokens.size) return false
        return expectedTokens.indices.all { index ->
            tokenFormsMatch(expectedTokens[index], actualTokens[index])
        }
    }

    private fun tokenFormsMatch(expected: String, actual: String): Boolean {
        if (expected == actual) return true
        if (expected.replace("'", "") == actual.replace("'", "")) return true
        val expectedBase = baseForms(expected)
        val actualBase = baseForms(actual)
        return expectedBase.any { it in actualBase }
    }

    private fun baseForms(token: String): Set<String> {
        val forms = linkedSetOf(token)
        if (token.length > 2 && token.endsWith("'s")) forms += token.dropLast(2)

        if (token.length > 4 && token.endsWith("ies")) forms += token.dropLast(3) + "y"
        if (token.length > 4 && token.endsWith("ied")) forms += token.dropLast(3) + "y"
        if (token.length > 4 && token.endsWith("es")) forms += token.dropLast(2)
        if (
            token.length > 3 &&
            token.endsWith("s") &&
            token !in sFinalWords &&
            !token.endsWith("ss") &&
            !token.endsWith("us") &&
            !token.endsWith("is")
        ) {
            forms += token.dropLast(1)
        }
        if (token.length > 4 && token.endsWith("ed")) {
            val stem = token.dropLast(2)
            forms += stem
            forms += stem + "e"
            if (isDoubledConsonant(stem)) forms += stem.dropLast(1)
        }
        if (token.length > 5 && token.endsWith("ing")) {
            val stem = token.dropLast(3)
            forms += stem
            forms += stem + "e"
            if (isDoubledConsonant(stem)) forms += stem.dropLast(1)
        }
        return forms
    }

    private fun isDoubledConsonant(stem: String): Boolean =
        stem.length >= 3 &&
            stem.last() == stem[stem.lastIndex - 1] &&
            stem.last() !in "aeiou"

    private fun negationChars(text: String): Set<Char> =
        text.filter { it in negationHan }.toSet()

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val maxLength = maxOf(a.length, b.length)
        if (maxLength == 0) return 1.0
        return 1.0 - editDistance(a, b).toDouble() / maxLength
    }

    fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previousPrevious = IntArray(b.length + 1)
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    substitution,
                )
                if (
                    i > 1 && j > 1 &&
                    a[i - 1] == b[j - 2] &&
                    a[i - 2] == b[j - 1]
                ) {
                    current[j] = minOf(current[j], previousPrevious[j - 2] + 1)
                }
            }
            val oldPreviousPrevious = previousPrevious
            previousPrevious = previous
            previous = current
            current = oldPreviousPrevious
        }
        return previous[b.length]
    }
}
