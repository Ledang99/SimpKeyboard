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

/**
 * A place to store the currently composing word with information such as adjacent key codes as well.
 */
class WordComposer {
    /**
     * The list of unicode values for each keystroke (including surrounding keys)
     */
    private val codes: MutableList<IntArray> = ArrayList(12)

    /**
     * The word chosen from the candidate list, until it is committed.
     */
    private var preferredWord: String? = null

    private val typedWordBuilder: StringBuilder = StringBuilder(20)

    private var capsCount: Int = 0

    private var autoCapitalized: Boolean = false

    /**
     * Whether the user chose to capitalize the first char of the word.
     */
    var isFirstCharCapitalized: Boolean = false
        private set

    constructor()

    constructor(copy: WordComposer) {
        codes.addAll(copy.codes)
        preferredWord = copy.preferredWord
        typedWordBuilder.append(copy.typedWordBuilder)
        capsCount = copy.capsCount
        autoCapitalized = copy.autoCapitalized
        isFirstCharCapitalized = copy.isFirstCharCapitalized
    }

    /**
     * Clear out the keys registered so far.
     */
    fun reset() {
        codes.clear()
        isFirstCharCapitalized = false
        preferredWord = null
        typedWordBuilder.setLength(0)
        capsCount = 0
    }

    /**
     * Number of keystrokes in the composing word.
     * @return the number of keystrokes
     */
    fun size(): Int = codes.size

    /**
     * Returns the codes at a particular position in the word.
     * @param index the position in the word
     * @return the unicode for the pressed and surrounding keys
     */
    fun getCodesAt(index: Int): IntArray = codes[index]

    /**
     * Add a new keystroke, with codes[0] containing the pressed key's unicode and the rest of
     * the array containing unicode for adjacent keys, sorted by reducing probability/proximity.
     * @param primaryCode the primary key code
     * @param codes the array of unicode values
     */
    fun add(primaryCode: Int, codes: IntArray) {
        typedWordBuilder.append(primaryCode.toChar())
        correctPrimaryJuxtapos(primaryCode, codes)
        correctCodesCase(codes)
        this.codes.add(codes)
        if (Character.isUpperCase(primaryCode.toChar())) {
            capsCount++
        }
    }

    /**
     * Swaps the first and second values in the codes array if the primary code is not the first
     * value in the array but the second. This happens when the preferred key is not the key that
     * the user released the finger on.
     * @param primaryCode the preferred character
     * @param codes array of codes based on distance from touch point
     */
    private fun correctPrimaryJuxtapos(primaryCode: Int, codes: IntArray) {
        if (codes.size < 2) return
        if (codes[0] > 0 && codes[1] > 0 && codes[0] != primaryCode && codes[1] == primaryCode) {
            codes[1] = codes[0]
            codes[0] = primaryCode
        }
    }

    // Prediction expects the keyCodes to be lowercase
    private fun correctCodesCase(codes: IntArray) {
        for (i in codes.indices) {
            val code = codes[i]
            if (code > 0) {
                codes[i] = Character.toLowerCase(code)
            }
        }
    }

    /**
     * Delete the last keystroke as a result of hitting backspace.
     */
    fun deleteLast() {
        val codesSize = codes.size
        if (codesSize > 0) {
            codes.removeAt(codesSize - 1)
            val lastPos = typedWordBuilder.length - 1
            val last = typedWordBuilder[lastPos]
            typedWordBuilder.deleteCharAt(lastPos)
            if (Character.isUpperCase(last)) {
                capsCount--
            }
        }
    }

    /**
     * Returns the word as it was typed, without any correction applied.
     * @return the word that was typed so far
     */
    val typedWord: CharSequence?
        get() = if (codes.isEmpty()) null else typedWordBuilder

    fun setFirstCharCapitalized(capitalized: Boolean) {
        isFirstCharCapitalized = capitalized
    }

    /**
     * Whether or not all of the user typed chars are upper case
     * @return true if all user typed chars are upper case, false otherwise
     */
    val isAllUpperCase: Boolean
        get() = capsCount > 0 && capsCount == size()

    /**
     * Stores the user's selected word, before it is actually committed to the text field.
     * @param preferred the preferred word
     */
    fun setPreferredWord(preferred: String?) {
        preferredWord = preferred
    }

    /**
     * Return the word chosen by the user, or the typed word if no other word was chosen.
     * @return the preferred word
     */
    fun getPreferredWord(): CharSequence? = preferredWord ?: typedWord

    /**
     * Returns true if more than one character is upper case, otherwise returns false.
     */
    fun isMostlyCaps(): Boolean = capsCount > 1

    /**
     * Saves the reason why the word is capitalized - whether it was automatic or
     * due to the user hitting shift in the middle of a sentence.
     * @param auto whether it was an automatic capitalization due to start of sentence
     */
    fun setAutoCapitalized(auto: Boolean) {
        autoCapitalized = auto
    }

    /**
     * Returns whether the word was automatically capitalized.
     * @return whether the word was automatically capitalized
     */
    fun isAutoCapitalized(): Boolean = autoCapitalized
}
