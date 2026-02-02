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

import java.util.Arrays

/**
 * Key detector that considers nearby keys based on touch proximity.
 */
open class ProximityKeyDetector : KeyDetector() {

    override fun setKeyboard(keyboard: Keyboard, correctionX: Float, correctionY: Float) {
        super.setKeyboard(keyboard, correctionX, correctionY)
        proximityThreshold = (keyboard.minWidth * SEARCH_DISTANCE).toInt()
        proximityThreshold *= proximityThreshold
    }

    override fun getKeyIndexAndNearbyCodes(x: Int, y: Int, allKeys: IntArray?): Int {
        val keys = this.keys
        val touchX = getTouchX(x)
        val touchY = getTouchY(y)
        var primaryIndex = NOT_A_KEY
        var closestKey = NOT_A_KEY
        var closestKeyDist = proximityThreshold + 1
        val distances = mDistances
        Arrays.fill(distances, Int.MAX_VALUE)
        val primaryCodes = allKeys?.also { Arrays.fill(it, NOT_A_KEY) }

        if (keys != null) {
            val count = keys.size
            for (i in 0 until count) {
                val key = keys[i]
                val dist = key.squaredDistanceFrom(touchX, touchY)
                if (dist < proximityThreshold || key.isInside(touchX, touchY)) {
                    // Find insertion point
                    val nCodes = key.codes.size
                    if (dist < closestKeyDist) {
                        closestKeyDist = dist
                        closestKey = i
                    }
                    if (primaryCodes != null && nCodes <= primaryCodes.size) {
                        for (j in distances.indices) {
                            if (distances[j] > dist) {
                                // Make room for a new code
                                System.arraycopy(distances, j, distances, j + nCodes,
                                    distances.size - j - nCodes)
                                System.arraycopy(primaryCodes, j, primaryCodes, j + nCodes,
                                    primaryCodes.size - j - nCodes)
                                for (c in 0 until nCodes) {
                                    primaryCodes[j + c] = key.codes[c]
                                    distances[j + c] = dist
                                }
                                break
                            }
                        }
                    }
                }
                if (key.isInside(touchX, touchY)) {
                    primaryIndex = i
                }
            }
        }
        return if (primaryIndex == NOT_A_KEY) closestKey else primaryIndex
    }

    companion object {
        private const val MAX_NEARBY_KEYS = 12
        private const val SEARCH_DISTANCE = 1.2f
        private val mDistances = IntArray(MAX_NEARBY_KEYS)
    }
}
