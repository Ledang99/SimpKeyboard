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
 * Key detector specifically for mini/popup keyboards.
 * Uses a simpler detection algorithm without proximity consideration.
 */
class MiniKeyboardKeyDetector(private val slideAllowance: Float) : KeyDetector() {

    override fun getKeyIndexAndNearbyCodes(x: Int, y: Int, allKeys: IntArray?): Int {
        val keys = this.keys ?: return NOT_A_KEY
        val touchX = getTouchX(x)
        val touchY = getTouchY(y)
        var closestKey = NOT_A_KEY
        var closestKeyDist = (slideAllowance + 1).toInt()
        closestKeyDist *= closestKeyDist
        val count = keys.size
        for (i in 0 until count) {
            val key = keys[i]
            val dist = key.squaredDistanceFrom(touchX, touchY)
            if (dist < closestKeyDist) {
                closestKeyDist = dist
                closestKey = i
            }
        }
        if (allKeys != null && closestKey != NOT_A_KEY) {
            allKeys[0] = keys[closestKey].codes[0]
        }
        return closestKey
    }
}
