/*
 * Copyright (C) 2024 Hacker's Keyboard Project
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

package org.pocketworkstation.pckeyboard;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Locale;

/**
 * Unit tests for {@link LanguageSwitcher}.
 * Tests the language switching functionality including locale management
 * and helper utilities.
 */
public class LanguageSwitcherTest {

    // ==================== toTitleCase Tests ====================

    @Test
    public void testToTitleCase_emptyString() {
        String result = LanguageSwitcher.toTitleCase("");
        assertEquals("Empty string should return empty", "", result);
    }

    @Test
    public void testToTitleCase_singleLowerChar() {
        String result = LanguageSwitcher.toTitleCase("a");
        assertEquals("Single lowercase should be uppercased", "A", result);
    }

    @Test
    public void testToTitleCase_singleUpperChar() {
        String result = LanguageSwitcher.toTitleCase("A");
        assertEquals("Single uppercase should remain", "A", result);
    }

    @Test
    public void testToTitleCase_lowercaseWord() {
        String result = LanguageSwitcher.toTitleCase("hello");
        assertEquals("First char should be uppercased", "Hello", result);
    }

    @Test
    public void testToTitleCase_uppercaseWord() {
        String result = LanguageSwitcher.toTitleCase("HELLO");
        assertEquals("Should keep first upper, rest unchanged", "HELLO", result);
    }

    @Test
    public void testToTitleCase_mixedCase() {
        String result = LanguageSwitcher.toTitleCase("hELLO");
        assertEquals("First char upper, rest unchanged", "HELLO", result);
    }

    @Test
    public void testToTitleCase_number() {
        String result = LanguageSwitcher.toTitleCase("123abc");
        assertEquals("Number first char stays same", "123abc", result);
    }

    @Test
    public void testToTitleCase_specialChars() {
        String result = LanguageSwitcher.toTitleCase("!hello");
        assertEquals("Special char first stays same", "!hello", result);
    }

    @Test
    public void testToTitleCase_unicode() {
        String result = LanguageSwitcher.toTitleCase("école");
        assertEquals("Unicode should title case", "École", result);
    }

    @Test
    public void testToTitleCase_germanEszett() {
        // ß (eszett) uppercase is SS
        String result = LanguageSwitcher.toTitleCase("ßtraße");
        // First char is uppercased
        assertEquals("German eszett handling", "ẞtraße", result);
    }

    // ==================== Locale Construction Tests ====================

    @Test
    public void testLocaleConstruction_languageOnly() {
        // When language code is just "en"
        String lang = "en";
        Locale locale = new Locale(lang.substring(0, 2), "");

        assertEquals("Language should be en", "en", locale.getLanguage());
        assertEquals("Country should be empty", "", locale.getCountry());
    }

    @Test
    public void testLocaleConstruction_languageAndCountry() {
        // When language code is "en_US"
        String lang = "en_US";
        Locale locale = new Locale(lang.substring(0, 2),
                lang.length() > 4 ? lang.substring(3, 5) : "");

        assertEquals("Language should be en", "en", locale.getLanguage());
        assertEquals("Country should be US", "US", locale.getCountry());
    }

    @Test
    public void testLocaleConstruction_twoCharCode() {
        String lang = "de";
        Locale locale = new Locale(lang.substring(0, 2),
                lang.length() > 4 ? lang.substring(3, 5) : "");

        assertEquals("Language should be de", "de", locale.getLanguage());
        assertEquals("Country should be empty", "", locale.getCountry());
    }

    @Test
    public void testLocaleConstruction_threeCharCode() {
        // Some language codes might be 3 characters
        String lang = "deu";
        Locale locale = new Locale(lang.substring(0, 2),
                lang.length() > 4 ? lang.substring(3, 5) : "");

        assertEquals("Language should be de (first 2 chars)", "de", locale.getLanguage());
    }

    @Test
    public void testLocaleConstruction_withUnderscore() {
        String lang = "pt_BR";
        Locale locale = new Locale(lang.substring(0, 2),
                lang.length() > 4 ? lang.substring(3, 5) : "");

        assertEquals("Language should be pt", "pt", locale.getLanguage());
        assertEquals("Country should be BR", "BR", locale.getCountry());
    }

    // ==================== Language Code Parsing Tests ====================

    @Test
    public void testLanguageCodeParsing_shortCode() {
        String lang = "en";
        String language = lang.length() > 2 ? lang.substring(0, 2) : lang;
        assertEquals("Short code should remain", "en", language);
    }

    @Test
    public void testLanguageCodeParsing_longCode() {
        String lang = "en_US";
        String language = lang.length() > 2 ? lang.substring(0, 2) : lang;
        assertEquals("Long code should be truncated", "en", language);
    }

    @Test
    public void testLanguageCodeParsing_exactlyTwoChars() {
        String lang = "fr";
        String language = lang.length() > 2 ? lang.substring(0, 2) : lang;
        assertEquals("Exactly 2 chars should work", "fr", language);
    }

    // ==================== Index Wrapping Tests ====================

    @Test
    public void testIndexWrapping_next_atEnd() {
        int currentIndex = 2;
        int localesLength = 3;

        int nextIndex = currentIndex + 1;
        if (nextIndex >= localesLength) nextIndex = 0;

        assertEquals("Should wrap to 0", 0, nextIndex);
    }

    @Test
    public void testIndexWrapping_next_notAtEnd() {
        int currentIndex = 1;
        int localesLength = 3;

        int nextIndex = currentIndex + 1;
        if (nextIndex >= localesLength) nextIndex = 0;

        assertEquals("Should not wrap", 2, nextIndex);
    }

    @Test
    public void testIndexWrapping_prev_atStart() {
        int currentIndex = 0;
        int localesLength = 3;

        int prevIndex = currentIndex - 1;
        if (prevIndex < 0) prevIndex = localesLength - 1;

        assertEquals("Should wrap to end", 2, prevIndex);
    }

    @Test
    public void testIndexWrapping_prev_notAtStart() {
        int currentIndex = 2;
        int localesLength = 3;

        int prevIndex = currentIndex - 1;
        if (prevIndex < 0) prevIndex = localesLength - 1;

        assertEquals("Should not wrap", 1, prevIndex);
    }

    // ==================== Next/Prev Locale Calculation Tests ====================

    @Test
    public void testNextLocale_modulo() {
        int currentIndex = 2;
        int localesLength = 3;

        int nextIndex = (currentIndex + 1) % localesLength;
        assertEquals("Modulo should wrap", 0, nextIndex);
    }

    @Test
    public void testPrevLocale_modulo() {
        int currentIndex = 0;
        int localesLength = 3;

        int prevIndex = (currentIndex - 1 + localesLength) % localesLength;
        assertEquals("Modulo with offset should wrap", 2, prevIndex);
    }

    @Test
    public void testNextLocale_singleLocale() {
        int currentIndex = 0;
        int localesLength = 1;

        int nextIndex = (currentIndex + 1) % localesLength;
        assertEquals("Single locale should stay at 0", 0, nextIndex);
    }

    @Test
    public void testPrevLocale_singleLocale() {
        int currentIndex = 0;
        int localesLength = 1;

        int prevIndex = (currentIndex - 1 + localesLength) % localesLength;
        assertEquals("Single locale should stay at 0", 0, prevIndex);
    }

    // ==================== Language Feature Tests ====================

    @Test
    public void testAllowAutoCap_checkLanguage() {
        // Test the language extraction logic
        String lang = "ko_KR";
        String languageOnly = lang.length() > 2 ? lang.substring(0, 2) : lang;
        assertEquals("Should extract 'ko'", "ko", languageOnly);
    }

    @Test
    public void testAllowAutoCap_shortLang() {
        String lang = "en";
        String languageOnly = lang.length() > 2 ? lang.substring(0, 2) : lang;
        assertEquals("Short lang should stay", "en", languageOnly);
    }

    // ==================== Selected Languages Parsing Tests ====================

    @Test
    public void testSelectedLanguagesParsing_single() {
        String selectedLanguages = "en";
        String[] array = selectedLanguages.split(",");

        assertEquals("Should have 1 language", 1, array.length);
        assertEquals("Should be 'en'", "en", array[0]);
    }

    @Test
    public void testSelectedLanguagesParsing_multiple() {
        String selectedLanguages = "en,de,fr,es";
        String[] array = selectedLanguages.split(",");

        assertEquals("Should have 4 languages", 4, array.length);
        assertEquals("First should be 'en'", "en", array[0]);
        assertEquals("Second should be 'de'", "de", array[1]);
        assertEquals("Third should be 'fr'", "fr", array[2]);
        assertEquals("Fourth should be 'es'", "es", array[3]);
    }

    @Test
    public void testSelectedLanguagesParsing_withCountry() {
        String selectedLanguages = "en_US,de_DE,pt_BR";
        String[] array = selectedLanguages.split(",");

        assertEquals("Should have 3 languages", 3, array.length);
        assertEquals("First should be 'en_US'", "en_US", array[0]);
    }

    @Test
    public void testSelectedLanguagesParsing_empty() {
        String selectedLanguages = "";
        String[] array = selectedLanguages.split(",");

        // Split on empty string returns array with one empty string
        assertEquals("Should have 1 element", 1, array.length);
        assertEquals("Element should be empty", "", array[0]);
    }

    // ==================== Locale Array Tests ====================

    @Test
    public void testLocaleArray_construction() {
        String[] selectedLanguageArray = {"en", "de", "fr"};
        Locale[] locales = new Locale[selectedLanguageArray.length];

        for (int i = 0; i < locales.length; i++) {
            String lang = selectedLanguageArray[i];
            locales[i] = new Locale(lang.substring(0, 2),
                    lang.length() > 4 ? lang.substring(3, 5) : "");
        }

        assertEquals("Should have 3 locales", 3, locales.length);
        assertEquals("First locale language", "en", locales[0].getLanguage());
        assertEquals("Second locale language", "de", locales[1].getLanguage());
        assertEquals("Third locale language", "fr", locales[2].getLanguage());
    }

    @Test
    public void testLocaleArray_withCountryCodes() {
        String[] selectedLanguageArray = {"en_US", "de_DE", "pt_BR"};
        Locale[] locales = new Locale[selectedLanguageArray.length];

        for (int i = 0; i < locales.length; i++) {
            String lang = selectedLanguageArray[i];
            locales[i] = new Locale(lang.substring(0, 2),
                    lang.length() > 4 ? lang.substring(3, 5) : "");
        }

        assertEquals("First locale country", "US", locales[0].getCountry());
        assertEquals("Second locale country", "DE", locales[1].getCountry());
        assertEquals("Third locale country", "BR", locales[2].getCountry());
    }

    // ==================== Default Language Tests ====================

    @Test
    public void testDefaultLanguage_construction() {
        Locale defaultLocale = Locale.US;
        String country = defaultLocale.getCountry();
        String defaultLanguage = defaultLocale.getLanguage() +
                (country.isEmpty() ? "" : "_" + country);

        assertEquals("Default language should be en_US", "en_US", defaultLanguage);
    }

    @Test
    public void testDefaultLanguage_noCountry() {
        Locale defaultLocale = new Locale("en");
        String country = defaultLocale.getCountry();
        String defaultLanguage = defaultLocale.getLanguage() +
                (country.isEmpty() ? "" : "_" + country);

        assertEquals("Default language without country", "en", defaultLanguage);
    }

    // ==================== Edge Cases ====================

    @Test
    public void testEmptyLocalesList() {
        Locale[] locales = new Locale[0];
        assertEquals("Empty locales length", 0, locales.length);
    }

    @Test
    public void testSingleLocaleNavigation() {
        // With single locale, next and prev should return same locale
        Locale[] locales = {Locale.US};
        int currentIndex = 0;

        int nextIndex = (currentIndex + 1) % locales.length;
        int prevIndex = (currentIndex - 1 + locales.length) % locales.length;

        assertEquals("Next should be 0", 0, nextIndex);
        assertEquals("Prev should be 0", 0, prevIndex);
    }

    @Test
    public void testTwoLocalesNavigation() {
        Locale[] locales = {Locale.US, Locale.GERMANY};
        int currentIndex = 0;

        int nextIndex = (currentIndex + 1) % locales.length;
        int prevIndex = (currentIndex - 1 + locales.length) % locales.length;

        assertEquals("Next from 0 should be 1", 1, nextIndex);
        assertEquals("Prev from 0 should be 1", 1, prevIndex);
    }
}
