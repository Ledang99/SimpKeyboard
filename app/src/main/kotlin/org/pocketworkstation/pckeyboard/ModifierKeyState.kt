/*
 * Copyright (C) 2010 Google Inc.
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
 * Tracks the state of a modifier key (Shift, Ctrl, Alt).
 * States: RELEASING (default), PRESSING (key is held), CHORDING (key held while another pressed)
 */
internal class ModifierKeyState {
    private var state = RELEASING

    fun onPress() {
        state = PRESSING
    }

    fun onRelease() {
        state = RELEASING
    }

    fun onOtherKeyPressed() {
        if (state == PRESSING) {
            state = CHORDING
        }
    }

    val isChording: Boolean
        get() = state == CHORDING

    override fun toString(): String = "ModifierKeyState:$state"

    companion object {
        private const val RELEASING = 0
        private const val PRESSING = 1
        private const val CHORDING = 2
    }
}
