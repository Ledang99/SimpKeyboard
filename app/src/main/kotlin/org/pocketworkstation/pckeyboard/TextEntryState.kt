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
import android.text.format.DateFormat
import android.util.Log
import java.io.FileOutputStream
import java.io.IOException
import java.util.Calendar

/**
 * Tracks the state of text entry for the input method.
 */
object TextEntryState {
    private const val TAG = "TextEntryState"
    private const val DBG = false
    private const val LOGGING = false

    private var backspaceCount = 0
    private var autoSuggestCount = 0
    private var autoSuggestUndoneCount = 0
    private var manualSuggestCount = 0
    private var wordNotInDictionaryCount = 0
    private var sessionCount = 0
    private var typedChars = 0
    private var actualChars = 0

    enum class State {
        UNKNOWN,
        START,
        IN_WORD,
        ACCEPTED_DEFAULT,
        PICKED_SUGGESTION,
        PUNCTUATION_AFTER_WORD,
        PUNCTUATION_AFTER_ACCEPTED,
        SPACE_AFTER_ACCEPTED,
        SPACE_AFTER_PICKED,
        UNDO_COMMIT,
        CORRECTING,
        PICKED_CORRECTION
    }

    private var state = State.UNKNOWN
    private var keyLocationFile: FileOutputStream? = null
    private var userActionFile: FileOutputStream? = null

    @JvmStatic
    fun newSession(context: Context) {
        sessionCount++
        autoSuggestCount = 0
        backspaceCount = 0
        autoSuggestUndoneCount = 0
        manualSuggestCount = 0
        wordNotInDictionaryCount = 0
        typedChars = 0
        actualChars = 0
        state = State.START

        if (LOGGING) {
            try {
                keyLocationFile = context.openFileOutput("key.txt", Context.MODE_APPEND)
                userActionFile = context.openFileOutput("action.txt", Context.MODE_APPEND)
            } catch (ioe: IOException) {
                Log.e("TextEntryState", "Couldn't open file for output: $ioe")
            }
        }
    }

    @JvmStatic
    fun endSession() {
        if (keyLocationFile == null) {
            return
        }
        try {
            keyLocationFile?.close()
            // Write to log file
            // Write timestamp, settings,
            val out = DateFormat.format("MM:dd hh:mm:ss", Calendar.getInstance().time)
                .toString() +
                    " BS: $backspaceCount" +
                    " auto: $autoSuggestCount" +
                    " manual: $manualSuggestCount" +
                    " typed: $wordNotInDictionaryCount" +
                    " undone: $autoSuggestUndoneCount" +
                    " saved: ${(actualChars - typedChars).toFloat() / actualChars}\n"
            userActionFile?.write(out.toByteArray())
            userActionFile?.close()
            keyLocationFile = null
            userActionFile = null
        } catch (ioe: IOException) {
            // Ignore
        }
    }

    @JvmStatic
    fun acceptedDefault(typedWord: CharSequence?, actualWord: CharSequence) {
        if (typedWord == null) return
        if (typedWord != actualWord) {
            autoSuggestCount++
        }
        typedChars += typedWord.length
        actualChars += actualWord.length
        state = State.ACCEPTED_DEFAULT
        displayState()
    }

    // State.ACCEPTED_DEFAULT will be changed to other sub-states
    // (see "case ACCEPTED_DEFAULT" in typedCharacter() below),
    // and should be restored back to State.ACCEPTED_DEFAULT after processing for each sub-state.
    @JvmStatic
    fun backToAcceptedDefault(typedWord: CharSequence?) {
        if (typedWord == null) return
        when (state) {
            State.SPACE_AFTER_ACCEPTED,
            State.PUNCTUATION_AFTER_ACCEPTED,
            State.IN_WORD -> state = State.ACCEPTED_DEFAULT
            else -> { /* do nothing */ }
        }
        displayState()
    }

    @JvmStatic
    fun manualTyped(typedWord: CharSequence?) {
        state = State.START
        displayState()
    }

    @JvmStatic
    fun acceptedTyped(typedWord: CharSequence?) {
        wordNotInDictionaryCount++
        state = State.PICKED_SUGGESTION
        displayState()
    }

    @JvmStatic
    fun acceptedSuggestion(typedWord: CharSequence, actualWord: CharSequence) {
        manualSuggestCount++
        val oldState = state
        if (typedWord == actualWord) {
            acceptedTyped(typedWord)
        }
        state = if (oldState == State.CORRECTING || oldState == State.PICKED_CORRECTION) {
            State.PICKED_CORRECTION
        } else {
            State.PICKED_SUGGESTION
        }
        displayState()
    }

    @JvmStatic
    fun selectedForCorrection() {
        state = State.CORRECTING
        displayState()
    }

    @JvmStatic
    fun typedCharacter(c: Char, isSeparator: Boolean) {
        val isSpace = c == ' '
        when (state) {
            State.IN_WORD -> {
                if (isSpace || isSeparator) {
                    state = State.START
                }
                // State hasn't changed otherwise
            }
            State.ACCEPTED_DEFAULT, State.SPACE_AFTER_PICKED -> {
                state = when {
                    isSpace -> State.SPACE_AFTER_ACCEPTED
                    isSeparator -> State.PUNCTUATION_AFTER_ACCEPTED
                    else -> State.IN_WORD
                }
            }
            State.PICKED_SUGGESTION, State.PICKED_CORRECTION -> {
                state = when {
                    isSpace -> State.SPACE_AFTER_PICKED
                    isSeparator -> State.PUNCTUATION_AFTER_ACCEPTED // Swap
                    else -> State.IN_WORD
                }
            }
            State.START, State.UNKNOWN, State.SPACE_AFTER_ACCEPTED,
            State.PUNCTUATION_AFTER_ACCEPTED, State.PUNCTUATION_AFTER_WORD -> {
                state = if (!isSpace && !isSeparator) State.IN_WORD else State.START
            }
            State.UNDO_COMMIT -> {
                state = if (isSpace || isSeparator) State.ACCEPTED_DEFAULT else State.IN_WORD
            }
            State.CORRECTING -> {
                state = State.START
            }
        }
        displayState()
    }

    @JvmStatic
    fun backspace() {
        if (state == State.ACCEPTED_DEFAULT) {
            state = State.UNDO_COMMIT
            autoSuggestUndoneCount++
        } else if (state == State.UNDO_COMMIT) {
            state = State.IN_WORD
        }
        backspaceCount++
        displayState()
    }

    @JvmStatic
    fun reset() {
        state = State.START
        displayState()
    }

    @JvmStatic
    fun getState(): State {
        if (DBG) {
            Log.d(TAG, "Returning state = $state")
        }
        return state
    }

    @JvmStatic
    fun isCorrecting(): Boolean = state == State.CORRECTING || state == State.PICKED_CORRECTION

    @JvmStatic
    fun keyPressedAt(key: Keyboard.Key, x: Int, y: Int) {
        if (LOGGING && keyLocationFile != null && key.codes[0] >= 32) {
            val out = "KEY: ${key.codes[0].toChar()}" +
                    " X: $x" +
                    " Y: $y" +
                    " MX: ${key.x + key.width / 2}" +
                    " MY: ${key.y + key.height / 2}\n"
            try {
                keyLocationFile?.write(out.toByteArray())
            } catch (ioe: IOException) {
                // TODO: May run out of space
            }
        }
    }

    private fun displayState() {
        if (DBG) {
            Log.i(TAG, "State = $state")
        }
    }
}
