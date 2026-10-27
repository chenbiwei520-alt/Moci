package com.cbw21.moci.data

import java.text.Normalizer

class VocabParseException(message: String) : Exception(message)

/**
 * Parses the two-column "Word / Meaning" vocabulary pages used by the supported PDF layout.
 * Text extraction yields: header, word column, header, meaning column, page footer.
 */
object VocabTextParser {
    private const val COLUMN_HEADER = "Word Meaning"

    private val entryNumber = Regex("""^(\d{1,4})(?:\s+(.*\S))?\s*$""")
    private val posLine = Regex(
        """(?i)^(?:n|v|vt|vi|adj|adv|prep|pron|conj|num|art|int|aux|abbr|det|pref|suff|usage|vlink|pl)\.(?![a-z])""",
    )
    private val footer = Regex(
        """^(共\s*\d+\s*词.*|\d+/\d+\s*页|英语专业.*|扫码听单词|纸上默写.*|Word\s+Meaning)$""",
    )
    private val bookChrome = Regex(
        """红宝书|乱序版|顺序版|扫码|公众号|微信|QQ群|词汇书|单词书|专四|专八|四六级|雅思|托福|考研|版权所有|翻印必究|侵权必究""",
        RegexOption.IGNORE_CASE,
    )
    private val yearTitle = Regex(
        """(?<!\d)(?:19|20)\d{2}(?!\d).*(?:考研|英语|词汇|单词|红宝书|乱序版|顺序版|版)""",
        RegexOption.IGNORE_CASE,
    )

    // NFKC converts Kangxi radicals, but not these CJK Radicals Supplement variants.
    private val supplementRadicals = mapOf(
        '⻨' to '麦', '⻛' to '风', '⻘' to '青', '⻰' to '龙', '⻝' to '食', '⻜' to '飞',
        '⻆' to '角', '⻉' to '贝', '⻦' to '鸟', '⻤' to '鬼', '⻅' to '见', '⻓' to '长',
        '⻔' to '门', '⻢' to '马', '⻣' to '骨', '⻚' to '页', '⻩' to '黄', '⺠' to '民',
        '⻮' to '齿', '⻬' to '齐', '⻥' to '鱼', '⻋' to '车',
    )

    fun parsePages(pages: List<String>): List<VocabWord> {
        if (pages.isEmpty()) throw VocabParseException("PDF 中没有可读取的页面。")

        val normalizedPages = pages.map(::normalize)
        val noiseLines = repeatedChrome(normalizedPages)
        val result = ArrayList<VocabWord>()
        var expectedNumber: Int? = null

        normalizedPages.forEachIndexed { index, pageText ->
            val pageNumber = index + 1
            val words = parsePage(pageText, pageNumber, noiseLines)
            val first = words.first().number
            if (expectedNumber != null && first != expectedNumber) {
                throw VocabParseException(
                    "第 $pageNumber 页词条编号不连续（期望 $expectedNumber，实际 $first）。",
                )
            }
            result.addAll(words)
            expectedNumber = words.last().number + 1
        }

        if (result.isEmpty()) throw VocabParseException("没有解析到任何词条。")
        return result
    }

    private fun repeatedChrome(pages: List<String>): Set<String> {
        if (pages.size < 2) return emptySet()
        val counts = HashMap<String, Int>()
        for (page in pages) {
            val seen = HashSet<String>()
            for (rawLine in page.lines()) {
                val line = rawLine.trim()
                if (line.isEmpty() || line == COLUMN_HEADER) continue
                if (entryNumber.matches(line) || posLine.containsMatchIn(line)) continue
                if (line.none { it in '\u4e00'..'\u9fff' }) continue
                if (seen.add(line)) counts[line] = (counts[line] ?: 0) + 1
            }
        }
        val threshold = maxOf(3, pages.size / 10)
        return counts.filterValues { it >= threshold }.keys
    }

    private fun isChrome(line: String): Boolean {
        // A leading entry number means the line is content (e.g. "1952 n. 词汇表"), not a footer.
        if (entryNumber.matches(line)) return false
        return !posLine.containsMatchIn(line) &&
            (bookChrome.containsMatchIn(line) || yearTitle.containsMatchIn(line))
    }

    private fun parsePage(
        normalizedPage: String,
        pageNumber: Int,
        noiseLines: Set<String>,
    ): List<VocabWord> {
        val lines = normalizedPage.lines().map { it.trim() }
        val headerIndexes = lines.indices.filter { lines[it] == COLUMN_HEADER }
        if (headerIndexes.size != 2) {
            throw VocabParseException(
                "第 $pageNumber 页结构不匹配：应有两栏 Word / Meaning 表头，实际找到 ${headerIndexes.size} 个。",
            )
        }

        val wordEntries = parseColumn(
            lines = lines.subList(headerIndexes[0] + 1, headerIndexes[1]),
            pageNumber = pageNumber,
            columnName = "Word",
            joinWithNewLine = false,
            noiseLines = noiseLines,
        )
        val meaningEntries = parseColumn(
            lines = lines.subList(headerIndexes[1] + 1, lines.size),
            pageNumber = pageNumber,
            columnName = "Meaning",
            joinWithNewLine = true,
            noiseLines = noiseLines,
        )

        val wordNumbers = wordEntries.map { it.number }
        val meaningNumbers = meaningEntries.map { it.number }
        if (wordNumbers != meaningNumbers) {
            throw VocabParseException("第 $pageNumber 页 Word 与 Meaning 两栏编号不一致。")
        }
        if (wordNumbers.zipWithNext().any { (a, b) -> b != a + 1 }) {
            throw VocabParseException("第 $pageNumber 页词条编号不连续。")
        }

        return wordEntries.mapIndexed { index, entry ->
            VocabWord(
                number = entry.number,
                word = entry.text,
                meaning = meaningEntries[index].text,
            )
        }
    }

    private data class ColumnEntry(val number: Int, val text: String)

    private fun parseColumn(
        lines: List<String>,
        pageNumber: Int,
        columnName: String,
        joinWithNewLine: Boolean,
        noiseLines: Set<String>,
    ): List<ColumnEntry> {
        val parts = LinkedHashMap<Int, MutableList<String>>()
        var currentNumber: Int? = null

        for (line in lines) {
            if (line.isEmpty() || footer.matches(line)) continue
            if (line in noiseLines || isChrome(line)) continue

            val match = entryNumber.matchEntire(line)
            if (match != null) {
                val number = match.groupValues[1].toInt()
                if (parts.containsKey(number)) {
                    throw VocabParseException("第 $pageNumber 页 $columnName 栏出现重复编号 $number。")
                }
                currentNumber = number
                val text = match.groupValues[2].trim()
                parts[number] = mutableListOf<String>().apply {
                    if (text.isNotEmpty()) add(text)
                }
            } else {
                val number = currentNumber
                if (number == null) {
                    throw VocabParseException("第 $pageNumber 页 $columnName 栏出现无法归属的文本：$line")
                }
                parts.getValue(number).add(line)
            }
        }

        if (parts.isEmpty()) {
            throw VocabParseException("第 $pageNumber 页 $columnName 栏没有词条。")
        }

        return parts.map { (number, entryParts) ->
            val text = if (joinWithNewLine) {
                entryParts.joinToString("\n")
            } else {
                joinWordParts(entryParts)
            }.trim()
            if (text.isEmpty()) {
                throw VocabParseException("第 $pageNumber 页 $columnName 栏编号 $number 的内容为空。")
            }
            ColumnEntry(number, text)
        }
    }

    private fun joinWordParts(parts: List<String>): String {
        val builder = StringBuilder()
        parts.forEachIndexed { index, part ->
            when {
                index == 0 -> builder.append(part)
                parts[index - 1].endsWith("-") -> builder.append(part)
                else -> builder.append(' ').append(part)
            }
        }
        return builder.toString().replace(Regex("""\s+"""), " ")
    }

    private fun normalize(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
        if (normalized.none { it in supplementRadicals }) return normalized

        return buildString(normalized.length) {
            for (char in normalized) append(supplementRadicals[char] ?: char)
        }
    }
}
