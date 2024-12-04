package com.halilibo.richtext.commonmark.latex

import org.commonmark.parser.delimiter.DelimiterProcessor
import org.commonmark.parser.delimiter.DelimiterRun
import java.util.LinkedList

internal class StaggeredDelimiterProcessor(private val delim: Char) : DelimiterProcessor {
    private var minLength = 0
    private val processors = LinkedList<DelimiterProcessor>() // in reverse getMinLength order

    override fun getOpeningCharacter(): Char {
        return delim
    }

    override fun getClosingCharacter(): Char {
        return delim
    }

    override fun getMinLength(): Int {
        return minLength
    }

    fun add(dp: DelimiterProcessor) {
        val len = dp.getMinLength()
        val it = processors.listIterator()
        var added = false
        while (it.hasNext()) {
            val p = it.next()
            val pLen = p.getMinLength()
            if (len > pLen) {
                it.previous()
                it.add(dp)
                added = true
                break
            } else require(len != pLen) { "Cannot add two delimiter processors for char '" + delim + "' and minimum length " + len + "; conflicting processors: " + p + ", " + dp }
        }
        if (!added) {
            processors.add(dp)
            this.minLength = len
        }
    }

    private fun findProcessor(len: Int): DelimiterProcessor? {
        for (p in processors) {
            if (p.getMinLength() <= len) {
                return p
            }
        }
        return processors.getFirst()
    }

    override fun process(openingRun: DelimiterRun, closingRun: DelimiterRun?): Int {
        return findProcessor(openingRun.length())!!.process(openingRun, closingRun)
    }
}
