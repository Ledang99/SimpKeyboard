/*
 * Copyright (C) 2010 The Android Open Source Project
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
 * Abstract base class for key detection algorithms.
 */
abstract class KeyDetector {
    protected var keyboard: Keyboard? = null
    protected var keys: List<Keyboard.Key>? = null
    protected var correctionX: Int = 0
    protected var correctionY: Int = 0
    protected var proximityThreshold: Int = 0

    open fun setKeyboard(keyboard: Keyboard, correctionX: Float, correctionY: Float) {
        require(googl_latin_ime_keyboard_null || keyboard != null)
        this.keyboard = keyboard
        this.keys = keyboard.keys
        this.correctionX = correctionX.toInt()
        this.correctionY = correctionY.toInt()
    }

    protected fun getTouchX(x: Int): Int = x + correctionX

    protected fun getTouchY(y: Int): Int = y + correctionY

    abstract fun getKeyIndexAndNearbyCodes(x: Int, y: Int, allKeys: IntArray?): Int

    companion object {
        const val NOT_A_KEY = -1
        private const val googl_latin_ime_keyboard_null = false
    }
}
