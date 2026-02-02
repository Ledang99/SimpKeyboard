/*
 * Copyright (C) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.pocketworkstation.pckeyboard

import android.text.TextUtils
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.regex.Pattern

/**
 * Utility methods to deal with editing text through an InputConnection.
 */
object EditingUtil {

    private const val LOOKBACK_CHARACTER_NUM = 15

    private var sMethodsInitialized = false
    private var sMethodGetSelectedText: Method? = null
    private var sMethodSetComposingRegion: Method? = null

    /**
     * Append newText to the text field represented by connection.
     * The new text becomes selected.
     */
    @JvmStatic
    fun appendText(connection: InputConnection?, newText: String) {
        if (connection == null) {
            return
        }

        var text = newText
        connection.finishComposingText()

        val charBeforeCursor = connection.getTextBeforeCursor(1, 0)
        if (charBeforeCursor != null
            && charBeforeCursor != " "
            && charBeforeCursor.isNotEmpty()
        ) {
            text = " $text"
        }

        connection.setComposingText(text, 1)
    }

    private fun getCursorPosition(connection: InputConnection): Int {
        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0)
            ?: return -1
        return extracted.startOffset + extracted.selectionStart
    }

    @JvmStatic
    fun getWordAtCursor(
        connection: InputConnection?,
        separators: String,
        range: Range?
    ): String? {
        val r = getWordRangeAtCursor(connection, separators, range)
        return r?.word
    }

    @JvmStatic
    fun deleteWordAtCursor(connection: InputConnection?, separators: String) {
        val range = getWordRangeAtCursor(connection, separators, null) ?: return

        connection!!.finishComposingText()
        val newCursor = getCursorPosition(connection) - range.charsBefore
        connection.setSelection(newCursor, newCursor)
        connection.deleteSurroundingText(0, range.charsBefore + range.charsAfter)
    }

    class Range {
        var charsBefore: Int = 0
        var charsAfter: Int = 0
        var word: String? = null

        constructor()

        constructor(charsBefore: Int, charsAfter: Int, word: String?) {
            require(charsBefore >= 0 && charsAfter >= 0)
            this.charsBefore = charsBefore
            this.charsAfter = charsAfter
            this.word = word
        }
    }

    private fun getWordRangeAtCursor(
        connection: InputConnection?,
        sep: String?,
        range: Range?
    ): Range? {
        if (connection == null || sep == null) {
            return null
        }
        val before = connection.getTextBeforeCursor(1000, 0)
        val after = connection.getTextAfterCursor(1000, 0)
        if (before == null || after == null) {
            return null
        }

        var start = before.length
        while (start > 0 && !isWhitespace(before[start - 1].code, sep)) start--

        var end = -1
        while (++end < after.length && !isWhitespace(after[end].code, sep)) { }

        val cursor = getCursorPosition(connection)
        if (start >= 0 && cursor + end <= after.length + before.length) {
            val word = before.toString().substring(start, before.length) +
                    after.toString().substring(0, end)

            val returnRange = range ?: Range()
            returnRange.charsBefore = before.length - start
            returnRange.charsAfter = end
            returnRange.word = word
            return returnRange
        }

        return null
    }

    private fun isWhitespace(code: Int, whitespace: String): Boolean {
        return whitespace.contains(code.toChar().toString())
    }

    private val spaceRegex = Pattern.compile("\\s+")

    @JvmStatic
    fun getPreviousWord(
        connection: InputConnection?,
        sentenceSeparators: String
    ): CharSequence? {
        val prev = connection?.getTextBeforeCursor(LOOKBACK_CHARACTER_NUM, 0) ?: return null
        val w = spaceRegex.split(prev)
        if (w.size >= 2 && w[w.size - 2].isNotEmpty()) {
            val lastChar = w[w.size - 2][w[w.size - 2].length - 1]
            if (sentenceSeparators.contains(lastChar.toString())) {
                return null
            }
            return w[w.size - 2]
        }
        return null
    }

    class SelectedWord {
        var start: Int = 0
        var end: Int = 0
        var word: CharSequence? = null
    }

    private fun isWordBoundary(singleChar: CharSequence?, wordSeparators: String): Boolean {
        return TextUtils.isEmpty(singleChar) || wordSeparators.contains(singleChar!!)
    }

    @JvmStatic
    fun getWordAtCursorOrSelection(
        ic: InputConnection?,
        selStart: Int,
        selEnd: Int,
        wordSeparators: String
    ): SelectedWord? {
        if (selStart == selEnd) {
            val range = Range()
            val touching = getWordAtCursor(ic, wordSeparators, range)
            if (!TextUtils.isEmpty(touching)) {
                val selWord = SelectedWord()
                selWord.word = touching
                selWord.start = selStart - range.charsBefore
                selWord.end = selEnd + range.charsAfter
                return selWord
            }
        } else {
            val charsBefore = ic?.getTextBeforeCursor(1, 0)
            if (!isWordBoundary(charsBefore, wordSeparators)) {
                return null
            }

            val charsAfter = ic?.getTextAfterCursor(1, 0)
            if (!isWordBoundary(charsAfter, wordSeparators)) {
                return null
            }

            val touching = getSelectedText(ic!!, selStart, selEnd)
            if (TextUtils.isEmpty(touching)) return null
            val length = touching!!.length
            for (i in 0 until length) {
                if (wordSeparators.contains(touching.subSequence(i, i + 1))) {
                    return null
                }
            }
            val selWord = SelectedWord()
            selWord.start = selStart
            selWord.end = selEnd
            selWord.word = touching
            return selWord
        }
        return null
    }

    private fun initializeMethodsForReflection() {
        try {
            sMethodGetSelectedText = InputConnection::class.java.getMethod(
                "getSelectedText",
                Int::class.javaPrimitiveType
            )
            sMethodSetComposingRegion = InputConnection::class.java.getMethod(
                "setComposingRegion",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
        } catch (exc: NoSuchMethodException) {
            // Ignore
        }
        sMethodsInitialized = true
    }

    private fun getSelectedText(ic: InputConnection, selStart: Int, selEnd: Int): CharSequence? {
        var result: CharSequence? = null
        if (!sMethodsInitialized) {
            initializeMethodsForReflection()
        }
        if (sMethodGetSelectedText != null) {
            try {
                result = sMethodGetSelectedText!!.invoke(ic, 0) as? CharSequence
                return result
            } catch (exc: InvocationTargetException) {
                // Ignore
            } catch (e: IllegalArgumentException) {
                // Ignore
            } catch (e: IllegalAccessException) {
                // Ignore
            }
        }
        ic.setSelection(selStart, selEnd)
        result = ic.getTextAfterCursor(selEnd - selStart, 0)
        ic.setSelection(selStart, selEnd)
        return result
    }

    @JvmStatic
    fun underlineWord(ic: InputConnection?, word: SelectedWord) {
        if (ic == null) return
        if (!sMethodsInitialized) {
            initializeMethodsForReflection()
        }
        if (sMethodSetComposingRegion != null) {
            try {
                sMethodSetComposingRegion!!.invoke(ic, word.start, word.end)
            } catch (exc: InvocationTargetException) {
                // Ignore
            } catch (e: IllegalArgumentException) {
                // Ignore
            } catch (e: IllegalAccessException) {
                // Ignore
            }
        }
    }
}
