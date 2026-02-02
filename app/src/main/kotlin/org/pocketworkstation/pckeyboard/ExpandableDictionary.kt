/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.pocketworkstation.pckeyboard

import android.content.Context
import android.os.AsyncTask
import java.util.LinkedList

/**
 * Base class for an in-memory dictionary that can grow dynamically and can
 * be searched for suggestions and valid words.
 */
open class ExpandableDictionary(
    private val mContext: Context,
    private val mDicTypeId: Int
) : Dictionary() {

    private var mWordBuilder = CharArray(MAX_WORD_LENGTH)
    private var mMaxDepth: Int = 0
    private var mInputLength: Int = 0
    private var mNextLettersFrequencies: IntArray? = null
    private val sb = StringBuilder(MAX_WORD_LENGTH)

    private var mRequiresReload: Boolean = false
    private var mUpdatingDictionary: Boolean = false
    private val mUpdatingLock = Any()

    private var mRoots: NodeArray = NodeArray()
    private var mCodes: Array<IntArray?> = arrayOfNulls(MAX_WORD_LENGTH)

    init {
        clearDictionary()
    }

    class Node {
        var code: Char = '\u0000'
        var frequency: Int = 0
        var terminal: Boolean = false
        var parent: Node? = null
        var children: NodeArray? = null
        var ngrams: LinkedList<NextWord>? = null
    }

    class NodeArray {
        var data: Array<Node?> = arrayOfNulls(INCREMENT)
        var length: Int = 0

        fun add(n: Node) {
            if (length + 1 > data.size) {
                val tempData = arrayOfNulls<Node>(length + INCREMENT)
                if (length > 0) {
                    System.arraycopy(data, 0, tempData, 0, length)
                }
                data = tempData
            }
            data[length++] = n
        }

        companion object {
            private const val INCREMENT = 2
        }
    }

    class NextWord(val word: Node, var frequency: Int) {
        var nextWord: NextWord? = null
    }

    fun loadDictionary() {
        synchronized(mUpdatingLock) {
            startDictionaryLoadingTaskLocked()
        }
    }

    open fun startDictionaryLoadingTaskLocked() {
        if (!mUpdatingDictionary) {
            mUpdatingDictionary = true
            mRequiresReload = false
            LoadDictionaryTask().execute()
        }
    }

    fun setRequiresReload(reload: Boolean) {
        synchronized(mUpdatingLock) {
            mRequiresReload = reload
        }
    }

    fun getRequiresReload(): Boolean = mRequiresReload

    /** Override to load your dictionary here, on a background thread. */
    open fun loadDictionaryAsync() {}

    fun getContext(): Context = mContext

    fun getMaxWordLength(): Int = MAX_WORD_LENGTH

    open fun addWord(word: String, frequency: Int) {
        addWordRec(mRoots, word, 0, frequency, null)
    }

    private fun addWordRec(
        children: NodeArray,
        word: String,
        depth: Int,
        frequency: Int,
        parentNode: Node?
    ) {
        val wordLength = word.length
        val c = word[depth]
        val childrenLength = children.length
        var childNode: Node? = null
        var found = false
        for (i in 0 until childrenLength) {
            childNode = children.data[i]
            if (childNode?.code == c) {
                found = true
                break
            }
        }
        if (!found) {
            childNode = Node()
            childNode.code = c
            childNode.parent = parentNode
            children.add(childNode)
        }
        if (wordLength == depth + 1) {
            childNode!!.terminal = true
            childNode.frequency = maxOf(frequency, childNode.frequency)
            if (childNode.frequency > 255) childNode.frequency = 255
            return
        }
        if (childNode!!.children == null) {
            childNode.children = NodeArray()
        }
        addWordRec(childNode.children!!, word, depth + 1, frequency, childNode)
    }

    override fun getWords(
        composer: WordComposer,
        callback: WordCallback,
        nextLettersFrequencies: IntArray?
    ) {
        synchronized(mUpdatingLock) {
            if (mRequiresReload) startDictionaryLoadingTaskLocked()
            if (mUpdatingDictionary) return
        }

        mInputLength = composer.size()
        mNextLettersFrequencies = nextLettersFrequencies
        if (mCodes.size < mInputLength) mCodes = arrayOfNulls(mInputLength)
        for (i in 0 until mInputLength) {
            mCodes[i] = composer.getCodesAt(i)
        }
        mMaxDepth = mInputLength * 3
        getWordsRec(mRoots, composer, mWordBuilder, 0, false, 1, 0, -1, callback)
        for (i in 0 until mInputLength) {
            getWordsRec(mRoots, composer, mWordBuilder, 0, false, 1, 0, i, callback)
        }
    }

    @Synchronized
    override fun isValidWord(word: CharSequence): Boolean {
        synchronized(mUpdatingLock) {
            if (mRequiresReload) startDictionaryLoadingTaskLocked()
            if (mUpdatingDictionary) return false
        }
        val freq = getWordFrequency(word)
        return freq > -1
    }

    fun getWordFrequency(word: CharSequence): Int {
        val node = searchNode(mRoots, word, 0, word.length)
        return node?.frequency ?: -1
    }

    protected open fun getWordsRec(
        roots: NodeArray,
        codes: WordComposer,
        word: CharArray,
        depth: Int,
        completion: Boolean,
        snr: Int,
        inputIndex: Int,
        skipPos: Int,
        callback: WordCallback
    ) {
        var isCompletion = completion
        val count = roots.length
        val codeSize = mInputLength
        if (depth > mMaxDepth) {
            return
        }
        var currentChars: IntArray? = null
        if (codeSize <= inputIndex) {
            isCompletion = true
        } else {
            currentChars = mCodes[inputIndex]
        }

        for (i in 0 until count) {
            val node = roots.data[i] ?: continue
            val c = node.code
            val lowerC = toLowerCase(c)
            val terminal = node.terminal
            val children = node.children
            val freq = node.frequency
            if (isCompletion) {
                word[depth] = c
                if (terminal) {
                    if (!callback.addWord(word, 0, depth + 1, freq * snr, mDicTypeId,
                            DataType.UNIGRAM)) {
                        return
                    }
                    if (mNextLettersFrequencies != null && depth >= inputIndex && skipPos < 0
                        && mNextLettersFrequencies!!.size > word[inputIndex].code) {
                        mNextLettersFrequencies!![word[inputIndex].code]++
                    }
                }
                if (children != null) {
                    getWordsRec(children, codes, word, depth + 1, isCompletion, snr, inputIndex,
                        skipPos, callback)
                }
            } else if ((c == QUOTE && currentChars!![0] != QUOTE.code) || depth == skipPos) {
                word[depth] = c
                if (children != null) {
                    getWordsRec(children, codes, word, depth + 1, isCompletion, snr, inputIndex,
                        skipPos, callback)
                }
            } else {
                val alternativesSize = if (skipPos >= 0) 1 else currentChars!!.size
                for (j in 0 until alternativesSize) {
                    val addedAttenuation = if (j > 0) 1 else 2
                    val currentChar = currentChars!![j]
                    if (currentChar == -1) {
                        break
                    }
                    if (currentChar == lowerC.code || currentChar == c.code) {
                        word[depth] = c

                        if (codeSize == inputIndex + 1) {
                            if (terminal) {
                                if (INCLUDE_TYPED_WORD_IF_VALID
                                    || !same(word, depth + 1, codes.typedWord)) {
                                    var finalFreq = freq * snr * addedAttenuation
                                    if (skipPos < 0) finalFreq *= FULL_WORD_FREQ_MULTIPLIER
                                    callback.addWord(word, 0, depth + 1, finalFreq, mDicTypeId,
                                        DataType.UNIGRAM)
                                }
                            }
                            if (children != null) {
                                getWordsRec(children, codes, word, depth + 1,
                                    true, snr * addedAttenuation, inputIndex + 1,
                                    skipPos, callback)
                            }
                        } else if (children != null) {
                            getWordsRec(children, codes, word, depth + 1,
                                false, snr * addedAttenuation, inputIndex + 1,
                                skipPos, callback)
                        }
                    }
                }
            }
        }
    }

    protected fun setBigram(word1: String, word2: String, frequency: Int): Int {
        return addOrSetBigram(word1, word2, frequency, false)
    }

    protected fun addBigram(word1: String, word2: String, frequency: Int): Int {
        return addOrSetBigram(word1, word2, frequency, true)
    }

    private fun addOrSetBigram(word1: String, word2: String, frequency: Int, addFrequency: Boolean): Int {
        val firstWord = searchWord(mRoots, word1, 0, null)
        val secondWord = searchWord(mRoots, word2, 0, null)
        var bigram = firstWord.ngrams
        if (bigram == null || bigram.size == 0) {
            firstWord.ngrams = LinkedList()
            bigram = firstWord.ngrams
        } else {
            for (nw in bigram) {
                if (nw.word === secondWord) {
                    if (addFrequency) {
                        nw.frequency += frequency
                    } else {
                        nw.frequency = frequency
                    }
                    return nw.frequency
                }
            }
        }
        val nw = NextWord(secondWord, frequency)
        firstWord.ngrams!!.add(nw)
        return frequency
    }

    private fun searchWord(children: NodeArray, word: String, depth: Int, parentNode: Node?): Node {
        val wordLength = word.length
        val c = word[depth]
        val childrenLength = children.length
        var childNode: Node? = null
        var found = false
        for (i in 0 until childrenLength) {
            childNode = children.data[i]
            if (childNode?.code == c) {
                found = true
                break
            }
        }
        if (!found) {
            childNode = Node()
            childNode.code = c
            childNode.parent = parentNode
            children.add(childNode)
        }
        if (wordLength == depth + 1) {
            childNode!!.terminal = true
            return childNode
        }
        if (childNode!!.children == null) {
            childNode.children = NodeArray()
        }
        return searchWord(childNode.children!!, word, depth + 1, childNode)
    }

    fun reloadDictionaryIfRequired(): Boolean {
        synchronized(mUpdatingLock) {
            if (mRequiresReload) startDictionaryLoadingTaskLocked()
            return mUpdatingDictionary
        }
    }

    private fun runReverseLookUp(previousWord: CharSequence, callback: WordCallback) {
        val prevWord = searchNode(mRoots, previousWord, 0, previousWord.length)
        if (prevWord != null && prevWord.ngrams != null) {
            reverseLookUp(prevWord.ngrams!!, callback)
        }
    }

    override fun getBigrams(
        composer: WordComposer,
        previousWord: CharSequence,
        callback: WordCallback,
        nextLettersFrequencies: IntArray?
    ) {
        if (!reloadDictionaryIfRequired()) {
            runReverseLookUp(previousWord, callback)
        }
    }

    fun waitForDictionaryLoading() {
        while (mUpdatingDictionary) {
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
            }
        }
    }

    private fun reverseLookUp(terminalNodes: LinkedList<NextWord>, callback: WordCallback) {
        for (nextWord in terminalNodes) {
            var node: Node? = nextWord.word
            val freq = nextWord.frequency
            if (freq >= UserBigramDictionary.SUGGEST_THRESHOLD) {
                sb.setLength(0)
                while (node != null) {
                    sb.insert(0, node.code)
                    node = node.parent
                }
                callback.addWord(sb.toString().toCharArray(), 0, sb.length, freq, mDicTypeId,
                    DataType.BIGRAM)
            }
        }
    }

    private fun searchNode(
        children: NodeArray,
        word: CharSequence,
        offset: Int,
        length: Int
    ): Node? {
        val count = children.length
        val currentChar = word[offset]
        for (j in 0 until count) {
            val node = children.data[j] ?: continue
            if (node.code == currentChar) {
                if (offset == length - 1) {
                    if (node.terminal) {
                        return node
                    }
                } else {
                    if (node.children != null) {
                        val returnNode = searchNode(node.children!!, word, offset + 1, length)
                        if (returnNode != null) return returnNode
                    }
                }
            }
        }
        return null
    }

    protected fun clearDictionary() {
        mRoots = NodeArray()
    }

    private inner class LoadDictionaryTask : AsyncTask<Void, Void, Void>() {
        override fun doInBackground(vararg v: Void): Void? {
            loadDictionaryAsync()
            synchronized(mUpdatingLock) {
                mUpdatingDictionary = false
            }
            return null
        }
    }

    companion object {
        /**
         * There is difference between what java and native code can handle.
         * It uses 32 because Java stack overflows when greater value is used.
         */
        protected const val MAX_WORD_LENGTH = 32

        private const val QUOTE = '\''

        @JvmStatic
        fun toLowerCase(c: Char): Char {
            var result = c
            if (result.code < BASE_CHARS.size) {
                result = BASE_CHARS[result.code]
            }
            if (result in 'A'..'Z') {
                result = (result.code or 32).toChar()
            } else if (result.code > 127) {
                result = Character.toLowerCase(result)
            }
            return result
        }

        /**
         * Table mapping most combined Latin, Greek, and Cyrillic characters
         * to their base characters.
         */
        @JvmStatic
        val BASE_CHARS = charArrayOf(
            '\u0000', '\u0001', '\u0002', '\u0003', '\u0004', '\u0005', '\u0006', '\u0007',
            '\u0008', '\u0009', '\u000a', '\u000b', '\u000c', '\u000d', '\u000e', '\u000f',
            '\u0010', '\u0011', '\u0012', '\u0013', '\u0014', '\u0015', '\u0016', '\u0017',
            '\u0018', '\u0019', '\u001a', '\u001b', '\u001c', '\u001d', '\u001e', '\u001f',
            '\u0020', '\u0021', '\u0022', '\u0023', '\u0024', '\u0025', '\u0026', '\u0027',
            '\u0028', '\u0029', '\u002a', '\u002b', '\u002c', '\u002d', '\u002e', '\u002f',
            '\u0030', '\u0031', '\u0032', '\u0033', '\u0034', '\u0035', '\u0036', '\u0037',
            '\u0038', '\u0039', '\u003a', '\u003b', '\u003c', '\u003d', '\u003e', '\u003f',
            '\u0040', '\u0041', '\u0042', '\u0043', '\u0044', '\u0045', '\u0046', '\u0047',
            '\u0048', '\u0049', '\u004a', '\u004b', '\u004c', '\u004d', '\u004e', '\u004f',
            '\u0050', '\u0051', '\u0052', '\u0053', '\u0054', '\u0055', '\u0056', '\u0057',
            '\u0058', '\u0059', '\u005a', '\u005b', '\u005c', '\u005d', '\u005e', '\u005f',
            '\u0060', '\u0061', '\u0062', '\u0063', '\u0064', '\u0065', '\u0066', '\u0067',
            '\u0068', '\u0069', '\u006a', '\u006b', '\u006c', '\u006d', '\u006e', '\u006f',
            '\u0070', '\u0071', '\u0072', '\u0073', '\u0074', '\u0075', '\u0076', '\u0077',
            '\u0078', '\u0079', '\u007a', '\u007b', '\u007c', '\u007d', '\u007e', '\u007f',
            '\u0080', '\u0081', '\u0082', '\u0083', '\u0084', '\u0085', '\u0086', '\u0087',
            '\u0088', '\u0089', '\u008a', '\u008b', '\u008c', '\u008d', '\u008e', '\u008f',
            '\u0090', '\u0091', '\u0092', '\u0093', '\u0094', '\u0095', '\u0096', '\u0097',
            '\u0098', '\u0099', '\u009a', '\u009b', '\u009c', '\u009d', '\u009e', '\u009f',
            '\u0020', '\u00a1', '\u00a2', '\u00a3', '\u00a4', '\u00a5', '\u00a6', '\u00a7',
            '\u0020', '\u00a9', '\u0061', '\u00ab', '\u00ac', '\u00ad', '\u00ae', '\u0020',
            '\u00b0', '\u00b1', '\u0032', '\u0033', '\u0020', '\u03bc', '\u00b6', '\u00b7',
            '\u0020', '\u0031', '\u006f', '\u00bb', '\u0031', '\u0031', '\u0033', '\u00bf',
            '\u0041', '\u0041', '\u0041', '\u0041', '\u0041', '\u0041', '\u00c6', '\u0043',
            '\u0045', '\u0045', '\u0045', '\u0045', '\u0049', '\u0049', '\u0049', '\u0049',
            '\u00d0', '\u004e', '\u004f', '\u004f', '\u004f', '\u004f', '\u004f', '\u00d7',
            '\u004f', '\u0055', '\u0055', '\u0055', '\u0055', '\u0059', '\u00de', '\u0073',
            '\u0061', '\u0061', '\u0061', '\u0061', '\u0061', '\u0061', '\u00e6', '\u0063',
            '\u0065', '\u0065', '\u0065', '\u0065', '\u0069', '\u0069', '\u0069', '\u0069',
            '\u00f0', '\u006e', '\u006f', '\u006f', '\u006f', '\u006f', '\u006f', '\u00f7',
            '\u006f', '\u0075', '\u0075', '\u0075', '\u0075', '\u0079', '\u00fe', '\u0079',
            '\u0041', '\u0061', '\u0041', '\u0061', '\u0041', '\u0061', '\u0043', '\u0063',
            '\u0043', '\u0063', '\u0043', '\u0063', '\u0043', '\u0063', '\u0044', '\u0064',
            '\u0110', '\u0111', '\u0045', '\u0065', '\u0045', '\u0065', '\u0045', '\u0065',
            '\u0045', '\u0065', '\u0045', '\u0065', '\u0047', '\u0067', '\u0047', '\u0067',
            '\u0047', '\u0067', '\u0047', '\u0067', '\u0048', '\u0068', '\u0126', '\u0127',
            '\u0049', '\u0069', '\u0049', '\u0069', '\u0049', '\u0069', '\u0049', '\u0069',
            '\u0049', '\u0131', '\u0049', '\u0069', '\u004a', '\u006a', '\u004b', '\u006b',
            '\u0138', '\u004c', '\u006c', '\u004c', '\u006c', '\u004c', '\u006c', '\u004c',
            '\u006c', '\u0141', '\u0142', '\u004e', '\u006e', '\u004e', '\u006e', '\u004e',
            '\u006e', '\u02bc', '\u014a', '\u014b', '\u004f', '\u006f', '\u004f', '\u006f',
            '\u004f', '\u006f', '\u0152', '\u0153', '\u0052', '\u0072', '\u0052', '\u0072',
            '\u0052', '\u0072', '\u0053', '\u0073', '\u0053', '\u0073', '\u0053', '\u0073',
            '\u0053', '\u0073', '\u0054', '\u0074', '\u0054', '\u0074', '\u0166', '\u0167',
            '\u0055', '\u0075', '\u0055', '\u0075', '\u0055', '\u0075', '\u0055', '\u0075',
            '\u0055', '\u0075', '\u0055', '\u0075', '\u0057', '\u0077', '\u0059', '\u0079',
            '\u0059', '\u005a', '\u007a', '\u005a', '\u007a', '\u005a', '\u007a', '\u0073',
            '\u0180', '\u0181', '\u0182', '\u0183', '\u0184', '\u0185', '\u0186', '\u0187',
            '\u0188', '\u0189', '\u018a', '\u018b', '\u018c', '\u018d', '\u018e', '\u018f',
            '\u0190', '\u0191', '\u0192', '\u0193', '\u0194', '\u0195', '\u0196', '\u0197',
            '\u0198', '\u0199', '\u019a', '\u019b', '\u019c', '\u019d', '\u019e', '\u019f',
            '\u004f', '\u006f', '\u01a2', '\u01a3', '\u01a4', '\u01a5', '\u01a6', '\u01a7',
            '\u01a8', '\u01a9', '\u01aa', '\u01ab', '\u01ac', '\u01ad', '\u01ae', '\u0055',
            '\u0075', '\u01b1', '\u01b2', '\u01b3', '\u01b4', '\u01b5', '\u01b6', '\u01b7',
            '\u01b8', '\u01b9', '\u01ba', '\u01bb', '\u01bc', '\u01bd', '\u01be', '\u01bf',
            '\u01c0', '\u01c1', '\u01c2', '\u01c3', '\u0044', '\u0044', '\u0064', '\u004c',
            '\u004c', '\u006c', '\u004e', '\u004e', '\u006e', '\u0041', '\u0061', '\u0049',
            '\u0069', '\u004f', '\u006f', '\u0055', '\u0075', '\u00dc', '\u00fc', '\u00dc',
            '\u00fc', '\u00dc', '\u00fc', '\u00dc', '\u00fc', '\u01dd', '\u00c4', '\u00e4',
            '\u0226', '\u0227', '\u00c6', '\u00e6', '\u01e4', '\u01e5', '\u0047', '\u0067',
            '\u004b', '\u006b', '\u004f', '\u006f', '\u01ea', '\u01eb', '\u01b7', '\u0292',
            '\u006a', '\u0044', '\u0044', '\u0064', '\u0047', '\u0067', '\u01f6', '\u01f7',
            '\u004e', '\u006e', '\u00c5', '\u00e5', '\u00c6', '\u00e6', '\u00d8', '\u00f8',
            '\u0041', '\u0061', '\u0041', '\u0061', '\u0045', '\u0065', '\u0045', '\u0065',
            '\u0049', '\u0069', '\u0049', '\u0069', '\u004f', '\u006f', '\u004f', '\u006f',
            '\u0052', '\u0072', '\u0052', '\u0072', '\u0055', '\u0075', '\u0055', '\u0075',
            '\u0053', '\u0073', '\u0054', '\u0074', '\u021c', '\u021d', '\u0048', '\u0068',
            '\u0220', '\u0221', '\u0222', '\u0223', '\u0224', '\u0225', '\u0041', '\u0061',
            '\u0045', '\u0065', '\u00d6', '\u00f6', '\u00d5', '\u00f5', '\u004f', '\u006f',
            '\u022e', '\u022f', '\u0059', '\u0079', '\u0234', '\u0235', '\u0236', '\u0237',
            '\u0238', '\u0239', '\u023a', '\u023b', '\u023c', '\u023d', '\u023e', '\u023f',
            '\u0240', '\u0241', '\u0242', '\u0243', '\u0244', '\u0245', '\u0246', '\u0247',
            '\u0248', '\u0249', '\u024a', '\u024b', '\u024c', '\u024d', '\u024e', '\u024f',
            '\u0250', '\u0251', '\u0252', '\u0253', '\u0254', '\u0255', '\u0256', '\u0257',
            '\u0258', '\u0259', '\u025a', '\u025b', '\u025c', '\u025d', '\u025e', '\u025f',
            '\u0260', '\u0261', '\u0262', '\u0263', '\u0264', '\u0265', '\u0266', '\u0267',
            '\u0268', '\u0269', '\u026a', '\u026b', '\u026c', '\u026d', '\u026e', '\u026f',
            '\u0270', '\u0271', '\u0272', '\u0273', '\u0274', '\u0275', '\u0276', '\u0277',
            '\u0278', '\u0279', '\u027a', '\u027b', '\u027c', '\u027d', '\u027e', '\u027f',
            '\u0280', '\u0281', '\u0282', '\u0283', '\u0284', '\u0285', '\u0286', '\u0287',
            '\u0288', '\u0289', '\u028a', '\u028b', '\u028c', '\u028d', '\u028e', '\u028f',
            '\u0290', '\u0291', '\u0292', '\u0293', '\u0294', '\u0295', '\u0296', '\u0297',
            '\u0298', '\u0299', '\u029a', '\u029b', '\u029c', '\u029d', '\u029e', '\u029f',
            '\u02a0', '\u02a1', '\u02a2', '\u02a3', '\u02a4', '\u02a5', '\u02a6', '\u02a7',
            '\u02a8', '\u02a9', '\u02aa', '\u02ab', '\u02ac', '\u02ad', '\u02ae', '\u02af',
            '\u0068', '\u0266', '\u006a', '\u0072', '\u0279', '\u027b', '\u0281', '\u0077',
            '\u0079', '\u02b9', '\u02ba', '\u02bb', '\u02bc', '\u02bd', '\u02be', '\u02bf',
            '\u02c0', '\u02c1', '\u02c2', '\u02c3', '\u02c4', '\u02c5', '\u02c6', '\u02c7',
            '\u02c8', '\u02c9', '\u02ca', '\u02cb', '\u02cc', '\u02cd', '\u02ce', '\u02cf',
            '\u02d0', '\u02d1', '\u02d2', '\u02d3', '\u02d4', '\u02d5', '\u02d6', '\u02d7',
            '\u0020', '\u0020', '\u0020', '\u0020', '\u0020', '\u0020', '\u02de', '\u02df',
            '\u0263', '\u006c', '\u0073', '\u0078', '\u0295', '\u02e5', '\u02e6', '\u02e7',
            '\u02e8', '\u02e9', '\u02ea', '\u02eb', '\u02ec', '\u02ed', '\u02ee', '\u02ef',
            '\u02f0', '\u02f1', '\u02f2', '\u02f3', '\u02f4', '\u02f5', '\u02f6', '\u02f7',
            '\u02f8', '\u02f9', '\u02fa', '\u02fb', '\u02fc', '\u02fd', '\u02fe', '\u02ff',
            '\u0300', '\u0301', '\u0302', '\u0303', '\u0304', '\u0305', '\u0306', '\u0307',
            '\u0308', '\u0309', '\u030a', '\u030b', '\u030c', '\u030d', '\u030e', '\u030f',
            '\u0310', '\u0311', '\u0312', '\u0313', '\u0314', '\u0315', '\u0316', '\u0317',
            '\u0318', '\u0319', '\u031a', '\u031b', '\u031c', '\u031d', '\u031e', '\u031f',
            '\u0320', '\u0321', '\u0322', '\u0323', '\u0324', '\u0325', '\u0326', '\u0327',
            '\u0328', '\u0329', '\u032a', '\u032b', '\u032c', '\u032d', '\u032e', '\u032f',
            '\u0330', '\u0331', '\u0332', '\u0333', '\u0334', '\u0335', '\u0336', '\u0337',
            '\u0338', '\u0339', '\u033a', '\u033b', '\u033c', '\u033d', '\u033e', '\u033f',
            '\u0300', '\u0301', '\u0342', '\u0313', '\u0308', '\u0345', '\u0346', '\u0347',
            '\u0348', '\u0349', '\u034a', '\u034b', '\u034c', '\u034d', '\u034e', '\u034f',
            '\u0350', '\u0351', '\u0352', '\u0353', '\u0354', '\u0355', '\u0356', '\u0357',
            '\u0358', '\u0359', '\u035a', '\u035b', '\u035c', '\u035d', '\u035e', '\u035f',
            '\u0360', '\u0361', '\u0362', '\u0363', '\u0364', '\u0365', '\u0366', '\u0367',
            '\u0368', '\u0369', '\u036a', '\u036b', '\u036c', '\u036d', '\u036e', '\u036f',
            '\u0370', '\u0371', '\u0372', '\u0373', '\u02b9', '\u0375', '\u0376', '\u0377',
            '\u0378', '\u0379', '\u0020', '\u037b', '\u037c', '\u037d', '\u003b', '\u037f',
            '\u0380', '\u0381', '\u0382', '\u0383', '\u0020', '\u00a8', '\u0391', '\u00b7',
            '\u0395', '\u0397', '\u0399', '\u038b', '\u039f', '\u038d', '\u03a5', '\u03a9',
            '\u03ca', '\u0391', '\u0392', '\u0393', '\u0394', '\u0395', '\u0396', '\u0397',
            '\u0398', '\u0399', '\u039a', '\u039b', '\u039c', '\u039d', '\u039e', '\u039f',
            '\u03a0', '\u03a1', '\u03a2', '\u03a3', '\u03a4', '\u03a5', '\u03a6', '\u03a7',
            '\u03a8', '\u03a9', '\u0399', '\u03a5', '\u03b1', '\u03b5', '\u03b7', '\u03b9',
            '\u03cb', '\u03b1', '\u03b2', '\u03b3', '\u03b4', '\u03b5', '\u03b6', '\u03b7',
            '\u03b8', '\u03b9', '\u03ba', '\u03bb', '\u03bc', '\u03bd', '\u03be', '\u03bf',
            '\u03c0', '\u03c1', '\u03c2', '\u03c3', '\u03c4', '\u03c5', '\u03c6', '\u03c7',
            '\u03c8', '\u03c9', '\u03b9', '\u03c5', '\u03bf', '\u03c5', '\u03c9', '\u03cf',
            '\u03b2', '\u03b8', '\u03a5', '\u03d2', '\u03d2', '\u03c6', '\u03c0', '\u03d7',
            '\u03d8', '\u03d9', '\u03da', '\u03db', '\u03dc', '\u03dd', '\u03de', '\u03df',
            '\u03e0', '\u03e1', '\u03e2', '\u03e3', '\u03e4', '\u03e5', '\u03e6', '\u03e7',
            '\u03e8', '\u03e9', '\u03ea', '\u03eb', '\u03ec', '\u03ed', '\u03ee', '\u03ef',
            '\u03ba', '\u03c1', '\u03c2', '\u03f3', '\u0398', '\u03b5', '\u03f6', '\u03f7',
            '\u03f8', '\u03a3', '\u03fa', '\u03fb', '\u03fc', '\u03fd', '\u03fe', '\u03ff',
            '\u0415', '\u0415', '\u0402', '\u0413', '\u0404', '\u0405', '\u0406', '\u0406',
            '\u0408', '\u0409', '\u040a', '\u040b', '\u041a', '\u0418', '\u0423', '\u040f',
            '\u0410', '\u0411', '\u0412', '\u0413', '\u0414', '\u0415', '\u0416', '\u0417',
            '\u0418', '\u0418', '\u041a', '\u041b', '\u041c', '\u041d', '\u041e', '\u041f',
            '\u0420', '\u0421', '\u0422', '\u0423', '\u0424', '\u0425', '\u0426', '\u0427',
            '\u0428', '\u0429', '\u042a', '\u042b', '\u042c', '\u042d', '\u042e', '\u042f',
            '\u0430', '\u0431', '\u0432', '\u0433', '\u0434', '\u0435', '\u0436', '\u0437',
            '\u0438', '\u0438', '\u043a', '\u043b', '\u043c', '\u043d', '\u043e', '\u043f',
            '\u0440', '\u0441', '\u0442', '\u0443', '\u0444', '\u0445', '\u0446', '\u0447',
            '\u0448', '\u0449', '\u044a', '\u044b', '\u044c', '\u044d', '\u044e', '\u044f',
            '\u0435', '\u0435', '\u0452', '\u0433', '\u0454', '\u0455', '\u0456', '\u0456',
            '\u0458', '\u0459', '\u045a', '\u045b', '\u043a', '\u0438', '\u0443', '\u045f',
            '\u0460', '\u0461', '\u0462', '\u0463', '\u0464', '\u0465', '\u0466', '\u0467',
            '\u0468', '\u0469', '\u046a', '\u046b', '\u046c', '\u046d', '\u046e', '\u046f',
            '\u0470', '\u0471', '\u0472', '\u0473', '\u0474', '\u0475', '\u0474', '\u0475',
            '\u0478', '\u0479', '\u047a', '\u047b', '\u047c', '\u047d', '\u047e', '\u047f',
            '\u0480', '\u0481', '\u0482', '\u0483', '\u0484', '\u0485', '\u0486', '\u0487',
            '\u0488', '\u0489', '\u048a', '\u048b', '\u048c', '\u048d', '\u048e', '\u048f',
            '\u0490', '\u0491', '\u0492', '\u0493', '\u0494', '\u0495', '\u0496', '\u0497',
            '\u0498', '\u0499', '\u049a', '\u049b', '\u049c', '\u049d', '\u049e', '\u049f',
            '\u04a0', '\u04a1', '\u04a2', '\u04a3', '\u04a4', '\u04a5', '\u04a6', '\u04a7',
            '\u04a8', '\u04a9', '\u04aa', '\u04ab', '\u04ac', '\u04ad', '\u04ae', '\u04af',
            '\u04b0', '\u04b1', '\u04b2', '\u04b3', '\u04b4', '\u04b5', '\u04b6', '\u04b7',
            '\u04b8', '\u04b9', '\u04ba', '\u04bb', '\u04bc', '\u04bd', '\u04be', '\u04bf',
            '\u04c0', '\u0416', '\u0436', '\u04c3', '\u04c4', '\u04c5', '\u04c6', '\u04c7',
            '\u04c8', '\u04c9', '\u04ca', '\u04cb', '\u04cc', '\u04cd', '\u04ce', '\u04cf',
            '\u0410', '\u0430', '\u0410', '\u0430', '\u04d4', '\u04d5', '\u0415', '\u0435',
            '\u04d8', '\u04d9', '\u04d8', '\u04d9', '\u0416', '\u0436', '\u0417', '\u0437',
            '\u04e0', '\u04e1', '\u0418', '\u0438', '\u0418', '\u0438', '\u041e', '\u043e',
            '\u04e8', '\u04e9', '\u04e8', '\u04e9', '\u042d', '\u044d', '\u0423', '\u0443',
            '\u0423', '\u0443', '\u0423', '\u0443', '\u0427', '\u0447', '\u04f6', '\u04f7',
            '\u042b', '\u044b', '\u04fa', '\u04fb', '\u04fc', '\u04fd', '\u04fe', '\u04ff'
        )
    }
}
