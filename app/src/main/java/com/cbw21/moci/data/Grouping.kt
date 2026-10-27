package com.cbw21.moci.data

const val GROUP_SIZE = 20

fun VocabBook.groupCount(): Int = (words.size + GROUP_SIZE - 1) / GROUP_SIZE

fun VocabBook.groupRange(groupIndex: Int): IntRange {
    val start = groupIndex * GROUP_SIZE
    val endExclusive = minOf(start + GROUP_SIZE, words.size)
    return start until endExclusive
}

fun VocabBook.groupSize(groupIndex: Int): Int = groupRange(groupIndex).count()

fun VocabBook.wordIndexFor(groupIndex: Int, offset: Int): Int = groupIndex * GROUP_SIZE + offset
