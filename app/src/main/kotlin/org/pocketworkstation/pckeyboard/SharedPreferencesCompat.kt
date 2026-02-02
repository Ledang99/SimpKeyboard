/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.SharedPreferences

/**
 * Utility object for SharedPreferences Editor operations.
 * Note: On modern Android, apply() is always available, but this maintains
 * compatibility with the original codebase design.
 */
object SharedPreferencesCompat {
    @JvmStatic
    fun apply(editor: SharedPreferences.Editor) {
        editor.apply()
    }
}
