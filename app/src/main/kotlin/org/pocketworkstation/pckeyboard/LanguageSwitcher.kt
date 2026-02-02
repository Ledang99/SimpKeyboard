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

import android.content.SharedPreferences
import android.preference.PreferenceManager
import java.util.Locale

/**
 * Keeps track of list of selected input languages and the current
 * input language that the user has selected.
 */
class LanguageSwitcher(private val ime: LatinIME) {

    private var locales: Array<Locale> = emptyArray()
    private var selectedLanguageArray: Array<String>? = null
    private var selectedLanguages: String? = null
    private var currentIndex = 0
    private var defaultInputLanguage: String? = null
    private var defaultInputLocale: Locale? = null
    var systemLocale: Locale? = null

    fun getLocales(): Array<Locale> = locales

    val localeCount: Int
        get() = locales.size

    /**
     * Loads the currently selected input languages from shared preferences.
     * @param sp SharedPreferences
     * @return whether there was any change
     */
    fun loadLocales(sp: SharedPreferences): Boolean {
        val selectedLangs = sp.getString(LatinIME.PREF_SELECTED_LANGUAGES, null)
        val currentLanguage = sp.getString(LatinIME.PREF_INPUT_LANGUAGE, null)

        if (selectedLangs == null || selectedLangs.length < 1) {
            loadDefaults()
            if (locales.isEmpty()) {
                return false
            }
            locales = emptyArray()
            return true
        }
        if (selectedLangs == selectedLanguages) {
            return false
        }
        selectedLanguageArray = selectedLangs.split(",").toTypedArray()
        selectedLanguages = selectedLangs // Cache it for comparison later
        constructLocales()
        currentIndex = 0
        if (currentLanguage != null) {
            // Find the index
            currentIndex = 0
            selectedLanguageArray?.forEachIndexed { i, lang ->
                if (lang == currentLanguage) {
                    currentIndex = i
                    return@forEachIndexed
                }
            }
            // If we didn't find the index, use the first one
        }
        return true
    }

    private fun loadDefaults() {
        defaultInputLocale = ime.resources.configuration.locale
        val country = defaultInputLocale?.country ?: ""
        defaultInputLanguage = (defaultInputLocale?.language ?: "") +
                if (country.isEmpty()) "" else "_$country"
    }

    private fun constructLocales() {
        val langArray = selectedLanguageArray ?: return
        locales = Array(langArray.size) { i ->
            val lang = langArray[i]
            Locale(
                lang.substring(0, 2),
                if (lang.length > 4) lang.substring(3, 5) else ""
            )
        }
    }

    /**
     * Returns the currently selected input language code, or the display language code if
     * no specific locale was selected for input.
     */
    val inputLanguage: String?
        get() = if (localeCount == 0) defaultInputLanguage else selectedLanguageArray?.get(currentIndex)

    fun allowAutoCap(): Boolean {
        var lang = inputLanguage ?: return true
        if (lang.length > 2) lang = lang.substring(0, 2)
        return !InputLanguageSelection.NOCAPS_LANGUAGES.contains(lang)
    }

    fun allowDeadKeys(): Boolean {
        var lang = inputLanguage ?: return true
        if (lang.length > 2) lang = lang.substring(0, 2)
        return !InputLanguageSelection.NODEADKEY_LANGUAGES.contains(lang)
    }

    fun allowAutoSpace(): Boolean {
        var lang = inputLanguage ?: return true
        if (lang.length > 2) lang = lang.substring(0, 2)
        return !InputLanguageSelection.NOAUTOSPACE_LANGUAGES.contains(lang)
    }

    /**
     * Returns the list of enabled language codes.
     */
    val enabledLanguages: Array<String>?
        get() = selectedLanguageArray

    /**
     * Returns the currently selected input locale, or the display locale if no specific
     * locale was selected for input.
     */
    val inputLocale: Locale?
        get() {
            val locale = if (localeCount == 0) defaultInputLocale else locales[currentIndex]
            LatinIME.sKeyboardSettings.inputLocale = locale ?: Locale.getDefault()
            return locale
        }

    /**
     * Returns the next input locale in the list. Wraps around to the beginning of the
     * list if we're at the end of the list.
     */
    val nextInputLocale: Locale?
        get() = if (localeCount == 0) defaultInputLocale else locales[(currentIndex + 1) % locales.size]

    /**
     * Returns the previous input locale in the list. Wraps around to the end of the
     * list if we're at the beginning of the list.
     */
    val prevInputLocale: Locale?
        get() = if (localeCount == 0) defaultInputLocale else locales[(currentIndex - 1 + locales.size) % locales.size]

    fun reset() {
        currentIndex = 0
        selectedLanguages = ""
        loadLocales(PreferenceManager.getDefaultSharedPreferences(ime))
    }

    operator fun next() {
        currentIndex++
        if (currentIndex >= locales.size) currentIndex = 0 // Wrap around
    }

    fun prev() {
        currentIndex--
        if (currentIndex < 0) currentIndex = locales.size - 1 // Wrap around
    }

    fun persist() {
        val sp = PreferenceManager.getDefaultSharedPreferences(ime)
        val editor = sp.edit()
        editor.putString(LatinIME.PREF_INPUT_LANGUAGE, inputLanguage)
        SharedPreferencesCompat.apply(editor)
    }

    companion object {
        @JvmStatic
        fun toTitleCase(s: String): String {
            if (s.isEmpty()) {
                return s
            }
            return s[0].uppercaseChar() + s.substring(1)
        }
    }
}
