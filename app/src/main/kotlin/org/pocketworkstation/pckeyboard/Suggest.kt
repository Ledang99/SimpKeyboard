/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.content.Context
import android.text.AutoText
import android.text.TextUtils
import android.util.Log
import android.view.View
import java.nio.ByteBuffer
import java.util.Arrays

/**
 * This class loads a dictionary and provides a list of suggestions for a given sequence of
 * characters. This includes corrections and completions.
 */
class Suggest : Dictionary.WordCallback {

    private var mMainDict: BinaryDictionary
    private var mUserDictionary: Dictionary? = null
    private var mAutoDictionary: Dictionary? = null
    private var mContactsDictionary: Dictionary? = null
    private var mUserBigramDictionary: Dictionary? = null

    private var mPrefMaxSuggestions = 12
    private var mAutoTextEnabled: Boolean = false

    private var mPriorities = IntArray(mPrefMaxSuggestions)
    private var mBigramPriorities = IntArray(PREF_MAX_BIGRAMS)

    private val mNextLettersFrequencies = IntArray(1280)
    private val mSuggestions = ArrayList<CharSequence>()
    val bigramSuggestions = ArrayList<CharSequence>()
    private val mStringPool = ArrayList<CharSequence>()
    private var mHaveCorrection: Boolean = false
    private var mOriginalWord: CharSequence? = null
    private var mLowerOriginalWord: String = ""

    private var mIsFirstCharCapitalized: Boolean = false
    private var mIsAllUpperCase: Boolean = false

    var correctionMode = CORRECTION_BASIC
        private set

    constructor(context: Context, dictionaryResId: IntArray?) {
        mMainDict = BinaryDictionary(context, dictionaryResId, DIC_MAIN)
        if (!hasMainDictionary()) {
            val locale = context.resources.configuration.locale
            val plug = PluginManager.getDictionary(context, locale.language)
            if (plug != null) {
                mMainDict.close()
                mMainDict = plug
            }
        }
        initPool()
    }

    constructor(context: Context, byteBuffer: ByteBuffer?) {
        mMainDict = BinaryDictionary(context, byteBuffer, DIC_MAIN)
        initPool()
    }

    private fun initPool() {
        for (i in 0 until mPrefMaxSuggestions) {
            val sb = StringBuilder(approxMaxWordLength)
            mStringPool.add(sb)
        }
    }

    fun setAutoTextEnabled(enabled: Boolean) {
        mAutoTextEnabled = enabled
    }

    fun setCorrectionMode(mode: Int) {
        correctionMode = mode
    }

    fun hasMainDictionary(): Boolean = mMainDict.getSize() > LARGE_DICTIONARY_THRESHOLD

    val approxMaxWordLength: Int
        get() = APPROX_MAX_WORD_LENGTH

    fun setUserDictionary(userDictionary: Dictionary?) {
        mUserDictionary = userDictionary
    }

    fun setContactsDictionary(userDictionary: Dictionary?) {
        mContactsDictionary = userDictionary
    }

    fun setAutoDictionary(autoDictionary: Dictionary?) {
        mAutoDictionary = autoDictionary
    }

    fun setUserBigramDictionary(userBigramDictionary: Dictionary?) {
        mUserBigramDictionary = userBigramDictionary
    }

    fun setMaxSuggestions(maxSuggestions: Int) {
        require(maxSuggestions in 1..100) { "maxSuggestions must be between 1 and 100" }
        mPrefMaxSuggestions = maxSuggestions
        mPriorities = IntArray(mPrefMaxSuggestions)
        mBigramPriorities = IntArray(PREF_MAX_BIGRAMS)
        collectGarbage(mSuggestions, mPrefMaxSuggestions)
        while (mStringPool.size < mPrefMaxSuggestions) {
            val sb = StringBuilder(approxMaxWordLength)
            mStringPool.add(sb)
        }
    }

    private fun haveSufficientCommonality(original: String, suggestion: CharSequence): Boolean {
        val originalLength = original.length
        val suggestionLength = suggestion.length
        val minLength = minOf(originalLength, suggestionLength)
        if (minLength <= 2) return true
        var matching = 0
        var lessMatching = 0
        for (i in 0 until minLength) {
            val origChar = ExpandableDictionary.toLowerCase(original[i])
            if (origChar == ExpandableDictionary.toLowerCase(suggestion[i])) {
                matching++
                lessMatching++
            } else if (i + 1 < suggestionLength
                && origChar == ExpandableDictionary.toLowerCase(suggestion[i + 1])
            ) {
                lessMatching++
            }
        }
        matching = maxOf(matching, lessMatching)

        return if (minLength <= 4) {
            matching >= 2
        } else {
            matching > minLength / 2
        }
    }

    fun getSuggestions(
        view: View?,
        wordComposer: WordComposer,
        includeTypedWordIfValid: Boolean,
        prevWordForBigram: CharSequence?
    ): List<CharSequence> {
        mHaveCorrection = false
        mIsFirstCharCapitalized = wordComposer.isFirstCharCapitalized
        mIsAllUpperCase = wordComposer.isAllUpperCase
        collectGarbage(mSuggestions, mPrefMaxSuggestions)
        Arrays.fill(mPriorities, 0)
        Arrays.fill(mNextLettersFrequencies, 0)

        mOriginalWord = wordComposer.typedWord
        if (mOriginalWord != null) {
            val originalWordString = mOriginalWord.toString()
            mOriginalWord = originalWordString
            mLowerOriginalWord = originalWordString.lowercase()
        } else {
            mLowerOriginalWord = ""
        }

        var prevWord = prevWordForBigram

        if (wordComposer.size() == 1 && (correctionMode == CORRECTION_FULL_BIGRAM
                    || correctionMode == CORRECTION_BASIC)
        ) {
            Arrays.fill(mBigramPriorities, 0)
            collectGarbage(bigramSuggestions, PREF_MAX_BIGRAMS)

            if (!TextUtils.isEmpty(prevWord)) {
                val lowerPrevWord = prevWord.toString().lowercase()
                if (mMainDict.isValidWord(lowerPrevWord)) {
                    prevWord = lowerPrevWord
                }
                mUserBigramDictionary?.getBigrams(
                    wordComposer, prevWord!!,
                    this, mNextLettersFrequencies
                )
                mContactsDictionary?.getBigrams(
                    wordComposer, prevWord!!,
                    this, mNextLettersFrequencies
                )
                mMainDict.getBigrams(
                    wordComposer, prevWord!!,
                    this, mNextLettersFrequencies
                )
                val currentChar = wordComposer.typedWord[0]
                val currentCharUpper = Character.toUpperCase(currentChar)
                var count = 0
                val bigramSuggestionSize = bigramSuggestions.size
                for (i in 0 until bigramSuggestionSize) {
                    if (bigramSuggestions[i][0] == currentChar
                        || bigramSuggestions[i][0] == currentCharUpper
                    ) {
                        val poolSize = mStringPool.size
                        val sb = if (poolSize > 0)
                            mStringPool.removeAt(poolSize - 1) as StringBuilder
                        else
                            StringBuilder(approxMaxWordLength)
                        sb.setLength(0)
                        sb.append(bigramSuggestions[i])
                        mSuggestions.add(count++, sb)
                        if (count > mPrefMaxSuggestions) break
                    }
                }
            }
        } else if (wordComposer.size() > 1) {
            if (mUserDictionary != null || mContactsDictionary != null) {
                mUserDictionary?.getWords(wordComposer, this, mNextLettersFrequencies)
                mContactsDictionary?.getWords(wordComposer, this, mNextLettersFrequencies)

                if (mSuggestions.size > 0 && isValidWord(mOriginalWord)
                    && (correctionMode == CORRECTION_FULL
                            || correctionMode == CORRECTION_FULL_BIGRAM)
                ) {
                    mHaveCorrection = true
                }
            }
            mMainDict.getWords(wordComposer, this, mNextLettersFrequencies)
            if ((correctionMode == CORRECTION_FULL || correctionMode == CORRECTION_FULL_BIGRAM)
                && mSuggestions.size > 0
            ) {
                mHaveCorrection = true
            }
        }
        if (mOriginalWord != null) {
            mSuggestions.add(0, mOriginalWord.toString())
        }

        if (wordComposer.size() > 1 && mSuggestions.size > 1
            && (correctionMode == CORRECTION_FULL
                    || correctionMode == CORRECTION_FULL_BIGRAM)
        ) {
            if (!haveSufficientCommonality(mLowerOriginalWord, mSuggestions[1])) {
                mHaveCorrection = false
            }
        }
        if (mAutoTextEnabled && view != null) {
            var i = 0
            var max = 6
            if (correctionMode == CORRECTION_BASIC) max = 1
            while (i < mSuggestions.size && i < max) {
                val suggestedWord = mSuggestions[i].toString().lowercase()
                val autoText = AutoText.get(suggestedWord, 0, suggestedWord.length, view)
                var canAdd = autoText != null
                canAdd = canAdd && !TextUtils.equals(autoText, mSuggestions[i])
                if (canAdd && i + 1 < mSuggestions.size && correctionMode != CORRECTION_BASIC) {
                    canAdd = canAdd && !TextUtils.equals(autoText, mSuggestions[i + 1])
                }
                if (canAdd) {
                    mHaveCorrection = true
                    mSuggestions.add(i + 1, autoText!!)
                    i++
                }
                i++
            }
        }
        removeDupes()
        return mSuggestions
    }

    fun getNextLettersFrequencies(): IntArray = mNextLettersFrequencies

    private fun removeDupes() {
        val suggestions = mSuggestions
        if (suggestions.size < 2) return
        var i = 1
        while (i < suggestions.size) {
            val cur = suggestions[i]
            for (j in 0 until i) {
                val previous = suggestions[j]
                if (TextUtils.equals(cur, previous)) {
                    removeFromSuggestions(i)
                    i--
                    break
                }
            }
            i++
        }
    }

    private fun removeFromSuggestions(index: Int) {
        val garbage = mSuggestions.removeAt(index)
        if (garbage is StringBuilder) {
            mStringPool.add(garbage)
        }
    }

    fun hasMinimalCorrection(): Boolean = mHaveCorrection

    private fun compareCaseInsensitive(
        lowerOriginalWord: String,
        word: CharArray,
        offset: Int,
        length: Int
    ): Boolean {
        val originalLength = lowerOriginalWord.length
        if (originalLength == length && Character.isUpperCase(word[offset])) {
            for (i in 0 until originalLength) {
                if (lowerOriginalWord[i] != Character.toLowerCase(word[offset + i])) {
                    return false
                }
            }
            return true
        }
        return false
    }

    override fun addWord(
        word: CharArray,
        wordOffset: Int,
        wordLength: Int,
        frequency: Int,
        dicTypeId: Int,
        dataType: Dictionary.DataType
    ): Boolean {
        var freq = frequency
        val suggestions: ArrayList<CharSequence>
        val priorities: IntArray
        val prefMaxSuggestions: Int
        if (dataType == Dictionary.DataType.BIGRAM) {
            suggestions = bigramSuggestions
            priorities = mBigramPriorities
            prefMaxSuggestions = PREF_MAX_BIGRAMS
        } else {
            suggestions = mSuggestions
            priorities = mPriorities
            prefMaxSuggestions = mPrefMaxSuggestions
        }

        var pos = 0

        if (compareCaseInsensitive(mLowerOriginalWord, word, wordOffset, wordLength)) {
            pos = 0
        } else {
            if (dataType == Dictionary.DataType.UNIGRAM) {
                val bigramSuggestion = searchBigramSuggestion(word, wordOffset, wordLength)
                if (bigramSuggestion >= 0) {
                    val multiplier = (mBigramPriorities[bigramSuggestion].toDouble()
                            / MAXIMUM_BIGRAM_FREQUENCY) *
                            (BIGRAM_MULTIPLIER_MAX - BIGRAM_MULTIPLIER_MIN) +
                            BIGRAM_MULTIPLIER_MIN
                    freq = Math.round(freq * multiplier).toInt()
                }
            }

            if (priorities[prefMaxSuggestions - 1] >= freq) return true
            while (pos < prefMaxSuggestions) {
                if (priorities[pos] < freq
                    || (priorities[pos] == freq && wordLength < suggestions[pos].length)
                ) {
                    break
                }
                pos++
            }
        }
        if (pos >= prefMaxSuggestions) {
            return true
        }

        System.arraycopy(priorities, pos, priorities, pos + 1, prefMaxSuggestions - pos - 1)
        priorities[pos] = freq
        val poolSize = mStringPool.size
        val sb = if (poolSize > 0)
            mStringPool.removeAt(poolSize - 1) as StringBuilder
        else
            StringBuilder(approxMaxWordLength)
        sb.setLength(0)
        if (mIsAllUpperCase) {
            sb.append(String(word, wordOffset, wordLength).uppercase())
        } else if (mIsFirstCharCapitalized) {
            sb.append(Character.toUpperCase(word[wordOffset]))
            if (wordLength > 1) {
                sb.append(word, wordOffset + 1, wordLength - 1)
            }
        } else {
            sb.append(word, wordOffset, wordLength)
        }
        suggestions.add(pos, sb)
        if (suggestions.size > prefMaxSuggestions) {
            val garbage = suggestions.removeAt(prefMaxSuggestions)
            if (garbage is StringBuilder) {
                mStringPool.add(garbage)
            }
        }
        return true
    }

    private fun searchBigramSuggestion(word: CharArray, offset: Int, length: Int): Int {
        val bigramSuggestSize = bigramSuggestions.size
        for (i in 0 until bigramSuggestSize) {
            if (bigramSuggestions[i].length == length) {
                var chk = true
                for (j in 0 until length) {
                    if (bigramSuggestions[i][j] != word[offset + j]) {
                        chk = false
                        break
                    }
                }
                if (chk) return i
            }
        }
        return -1
    }

    fun isValidWord(word: CharSequence?): Boolean {
        if (word == null || word.isEmpty()) {
            return false
        }
        return mMainDict.isValidWord(word)
                || (mUserDictionary?.isValidWord(word) == true)
                || (mAutoDictionary?.isValidWord(word) == true)
                || (mContactsDictionary?.isValidWord(word) == true)
    }

    private fun collectGarbage(suggestions: ArrayList<CharSequence>, prefMaxSuggestions: Int) {
        var poolSize = mStringPool.size
        var garbageSize = suggestions.size
        while (poolSize < prefMaxSuggestions && garbageSize > 0) {
            val garbage = suggestions[garbageSize - 1]
            if (garbage is StringBuilder) {
                mStringPool.add(garbage)
                poolSize++
            }
            garbageSize--
        }
        if (poolSize == prefMaxSuggestions + 1) {
            Log.w("Suggest", "String pool got too big: $poolSize")
        }
        suggestions.clear()
    }

    fun close() {
        mMainDict.close()
    }

    companion object {
        private const val TAG = "PCKeyboard"

        const val APPROX_MAX_WORD_LENGTH = 32

        const val CORRECTION_NONE = 0
        const val CORRECTION_BASIC = 1
        const val CORRECTION_FULL = 2
        const val CORRECTION_FULL_BIGRAM = 3

        const val BIGRAM_MULTIPLIER_MIN = 1.2
        const val BIGRAM_MULTIPLIER_MAX = 1.5

        const val MAXIMUM_BIGRAM_FREQUENCY = 127

        const val DIC_USER_TYPED = 0
        const val DIC_MAIN = 1
        const val DIC_USER = 2
        const val DIC_AUTO = 3
        const val DIC_CONTACTS = 4
        const val DIC_TYPE_LAST_ID = 4

        const val LARGE_DICTIONARY_THRESHOLD = 200 * 1000

        private const val PREF_MAX_BIGRAMS = 60
    }
}
