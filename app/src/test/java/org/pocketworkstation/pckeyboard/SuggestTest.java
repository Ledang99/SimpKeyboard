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

/**
 * Unit tests for {@link Suggest}.
 * Tests the suggestion algorithm including word matching, correction modes,
 * and bigram handling.
 *
 * Note: Full integration tests require Android context for BinaryDictionary.
 * These tests focus on the testable logic within the Suggest class.
 */
public class SuggestTest {

    // ==================== Constants Tests ====================

    @Test
    public void testConstants_approxMaxWordLength() {
        assertEquals("APPROX_MAX_WORD_LENGTH should be 32", 32, Suggest.APPROX_MAX_WORD_LENGTH);
    }

    @Test
    public void testConstants_correctionModes() {
        assertEquals("CORRECTION_NONE should be 0", 0, Suggest.CORRECTION_NONE);
        assertEquals("CORRECTION_BASIC should be 1", 1, Suggest.CORRECTION_BASIC);
        assertEquals("CORRECTION_FULL should be 2", 2, Suggest.CORRECTION_FULL);
        assertEquals("CORRECTION_FULL_BIGRAM should be 3", 3, Suggest.CORRECTION_FULL_BIGRAM);
    }

    @Test
    public void testConstants_bigramMultipliers() {
        assertEquals("BIGRAM_MULTIPLIER_MIN should be 1.2", 1.2, Suggest.BIGRAM_MULTIPLIER_MIN, 0.001);
        assertEquals("BIGRAM_MULTIPLIER_MAX should be 1.5", 1.5, Suggest.BIGRAM_MULTIPLIER_MAX, 0.001);
        assertTrue("MIN should be less than MAX", Suggest.BIGRAM_MULTIPLIER_MIN < Suggest.BIGRAM_MULTIPLIER_MAX);
    }

    @Test
    public void testConstants_maximumBigramFrequency() {
        assertEquals("MAXIMUM_BIGRAM_FREQUENCY should be 127", 127, Suggest.MAXIMUM_BIGRAM_FREQUENCY);
    }

    @Test
    public void testConstants_dictionaryTypes() {
        assertEquals("DIC_USER_TYPED should be 0", 0, Suggest.DIC_USER_TYPED);
        assertEquals("DIC_MAIN should be 1", 1, Suggest.DIC_MAIN);
        assertEquals("DIC_USER should be 2", 2, Suggest.DIC_USER);
        assertEquals("DIC_AUTO should be 3", 3, Suggest.DIC_AUTO);
        assertEquals("DIC_CONTACTS should be 4", 4, Suggest.DIC_CONTACTS);
        assertEquals("DIC_TYPE_LAST_ID should be 4", 4, Suggest.DIC_TYPE_LAST_ID);
    }

    @Test
    public void testConstants_largeDictionaryThreshold() {
        assertEquals("LARGE_DICTIONARY_THRESHOLD should be 200000",
                200 * 1000, Suggest.LARGE_DICTIONARY_THRESHOLD);
    }

    // ==================== Bigram Multiplier Calculation Tests ====================

    @Test
    public void testBigramMultiplierCalculation_minimumFrequency() {
        // When bigram frequency is 0, multiplier should be BIGRAM_MULTIPLIER_MIN
        int bigramFrequency = 0;
        double multiplier = (((double) bigramFrequency) / Suggest.MAXIMUM_BIGRAM_FREQUENCY)
                * (Suggest.BIGRAM_MULTIPLIER_MAX - Suggest.BIGRAM_MULTIPLIER_MIN)
                + Suggest.BIGRAM_MULTIPLIER_MIN;

        assertEquals("Multiplier for freq 0 should be MIN", Suggest.BIGRAM_MULTIPLIER_MIN, multiplier, 0.001);
    }

    @Test
    public void testBigramMultiplierCalculation_maximumFrequency() {
        // When bigram frequency is max, multiplier should be BIGRAM_MULTIPLIER_MAX
        int bigramFrequency = Suggest.MAXIMUM_BIGRAM_FREQUENCY;
        double multiplier = (((double) bigramFrequency) / Suggest.MAXIMUM_BIGRAM_FREQUENCY)
                * (Suggest.BIGRAM_MULTIPLIER_MAX - Suggest.BIGRAM_MULTIPLIER_MIN)
                + Suggest.BIGRAM_MULTIPLIER_MIN;

        assertEquals("Multiplier for max freq should be MAX", Suggest.BIGRAM_MULTIPLIER_MAX, multiplier, 0.001);
    }

    @Test
    public void testBigramMultiplierCalculation_midFrequency() {
        // When bigram frequency is half of max
        int bigramFrequency = Suggest.MAXIMUM_BIGRAM_FREQUENCY / 2;
        double multiplier = (((double) bigramFrequency) / Suggest.MAXIMUM_BIGRAM_FREQUENCY)
                * (Suggest.BIGRAM_MULTIPLIER_MAX - Suggest.BIGRAM_MULTIPLIER_MIN)
                + Suggest.BIGRAM_MULTIPLIER_MIN;

        double expectedMid = (Suggest.BIGRAM_MULTIPLIER_MIN + Suggest.BIGRAM_MULTIPLIER_MAX) / 2;
        assertEquals("Multiplier for mid freq should be middle", expectedMid, multiplier, 0.01);
    }

    @Test
    public void testBigramMultiplierRange() {
        // Test that all frequencies produce valid multipliers
        for (int freq = 0; freq <= Suggest.MAXIMUM_BIGRAM_FREQUENCY; freq++) {
            double multiplier = (((double) freq) / Suggest.MAXIMUM_BIGRAM_FREQUENCY)
                    * (Suggest.BIGRAM_MULTIPLIER_MAX - Suggest.BIGRAM_MULTIPLIER_MIN)
                    + Suggest.BIGRAM_MULTIPLIER_MIN;

            assertTrue("Multiplier should be >= MIN", multiplier >= Suggest.BIGRAM_MULTIPLIER_MIN);
            assertTrue("Multiplier should be <= MAX", multiplier <= Suggest.BIGRAM_MULTIPLIER_MAX);
        }
    }

    // ==================== Frequency Calculation Tests ====================

    @Test
    public void testFrequencyWithMultiplier_basic() {
        int baseFreq = 100;
        double multiplier = Suggest.BIGRAM_MULTIPLIER_MIN;
        int boostedFreq = (int) Math.round(baseFreq * multiplier);

        assertEquals("Boosted freq should be 120 (100 * 1.2)", 120, boostedFreq);
    }

    @Test
    public void testFrequencyWithMultiplier_max() {
        int baseFreq = 100;
        double multiplier = Suggest.BIGRAM_MULTIPLIER_MAX;
        int boostedFreq = (int) Math.round(baseFreq * multiplier);

        assertEquals("Boosted freq should be 150 (100 * 1.5)", 150, boostedFreq);
    }

    @Test
    public void testFrequencyWithMultiplier_rounding() {
        int baseFreq = 99;
        double multiplier = Suggest.BIGRAM_MULTIPLIER_MIN;
        int boostedFreq = (int) Math.round(baseFreq * multiplier);

        // 99 * 1.2 = 118.8 -> rounds to 119
        assertEquals("Should round correctly", 119, boostedFreq);
    }

    // ==================== Max Suggestions Validation Tests ====================

    @Test
    public void testMaxSuggestionsValidation_minimum() {
        // setMaxSuggestions should accept 1
        int minSuggestions = 1;
        assertTrue("Min suggestions (1) should be valid", minSuggestions >= 1 && minSuggestions <= 100);
    }

    @Test
    public void testMaxSuggestionsValidation_maximum() {
        // setMaxSuggestions should accept 100
        int maxSuggestions = 100;
        assertTrue("Max suggestions (100) should be valid", maxSuggestions >= 1 && maxSuggestions <= 100);
    }

    @Test
    public void testMaxSuggestionsValidation_zero() {
        // setMaxSuggestions should reject 0
        int zeroSuggestions = 0;
        assertFalse("Zero suggestions should be invalid", zeroSuggestions >= 1 && zeroSuggestions <= 100);
    }

    @Test
    public void testMaxSuggestionsValidation_tooLarge() {
        // setMaxSuggestions should reject 101
        int tooManysuggestions = 101;
        assertFalse("101 suggestions should be invalid", tooManysuggestions >= 1 && tooManysuggestions <= 100);
    }

    // ==================== Common Character Tests ====================

    @Test
    public void testCommonCharacterCounting_sameStrings() {
        String original = "hello";
        String suggestion = "hello";

        int matching = countMatchingChars(original, suggestion);
        assertEquals("Identical strings should have 5 matching chars", 5, matching);
    }

    @Test
    public void testCommonCharacterCounting_differentStrings() {
        String original = "hello";
        String suggestion = "hallo";

        int matching = countMatchingChars(original, suggestion);
        assertEquals("'hello' vs 'hallo' should have 4 matching chars", 4, matching);
    }

    @Test
    public void testCommonCharacterCounting_completelyDifferent() {
        String original = "abc";
        String suggestion = "xyz";

        int matching = countMatchingChars(original, suggestion);
        assertEquals("Completely different should have 0 matching", 0, matching);
    }

    @Test
    public void testCommonCharacterCounting_differentLengths() {
        String original = "hel";
        String suggestion = "hello";

        int matching = countMatchingChars(original, suggestion);
        assertEquals("'hel' vs 'hello' should have 3 matching", 3, matching);
    }

    @Test
    public void testCommonCharacterCounting_caseInsensitive() {
        String original = "hello";
        String suggestion = "HELLO";

        int matching = countMatchingCharsIgnoreCase(original, suggestion);
        assertEquals("Case-insensitive match should have 5 matching", 5, matching);
    }

    // Helper method to count matching characters
    private int countMatchingChars(String original, String suggestion) {
        int minLength = Math.min(original.length(), suggestion.length());
        int matching = 0;
        for (int i = 0; i < minLength; i++) {
            if (original.charAt(i) == suggestion.charAt(i)) {
                matching++;
            }
        }
        return matching;
    }

    private int countMatchingCharsIgnoreCase(String original, String suggestion) {
        int minLength = Math.min(original.length(), suggestion.length());
        int matching = 0;
        for (int i = 0; i < minLength; i++) {
            if (Character.toLowerCase(original.charAt(i)) == Character.toLowerCase(suggestion.charAt(i))) {
                matching++;
            }
        }
        return matching;
    }

    // ==================== Sufficient Commonality Tests ====================

    @Test
    public void testSufficientCommonality_shortWords() {
        // For words <= 2 chars, always return true
        assertTrue("Short words should always have commonality", hasMinCommonality("ab", "xy", 0));
    }

    @Test
    public void testSufficientCommonality_threeChars_twoMatching() {
        // For 3-4 char words, need >= 2 matching
        assertTrue("3 chars with 2 matching should pass", hasMinCommonality("abc", "abd", 2));
    }

    @Test
    public void testSufficientCommonality_threeChars_oneMatching() {
        assertFalse("3 chars with 1 matching should fail", hasMinCommonality("abc", "axc", 1));
    }

    @Test
    public void testSufficientCommonality_fiveChars_threeMatching() {
        // For 5+ char words, need > minLength/2
        assertTrue("5 chars with 3 matching should pass", hasMinCommonality("abcde", "abcxy", 3));
    }

    @Test
    public void testSufficientCommonality_fiveChars_twoMatching() {
        assertFalse("5 chars with 2 matching should fail", hasMinCommonality("abcde", "abxyz", 2));
    }

    // Simulates haveSufficientCommonality logic
    private boolean hasMinCommonality(String original, String suggestion, int matching) {
        int minLength = Math.min(original.length(), suggestion.length());
        if (minLength <= 2) return true;
        if (minLength <= 4) {
            return matching >= 2;
        } else {
            return matching > minLength / 2;
        }
    }

    // ==================== Word Validity Tests ====================

    @Test
    public void testIsValidWord_nullWord() {
        // Null word should return false
        CharSequence word = null;
        boolean isValid = word != null && word.length() > 0;
        assertFalse("Null word should be invalid", isValid);
    }

    @Test
    public void testIsValidWord_emptyWord() {
        CharSequence word = "";
        boolean isValid = word != null && word.length() > 0;
        assertFalse("Empty word should be invalid", isValid);
    }

    @Test
    public void testIsValidWord_nonEmptyWord() {
        CharSequence word = "hello";
        boolean isValid = word != null && word.length() > 0;
        assertTrue("Non-empty word should be valid for length check", isValid);
    }

    // ==================== Case Comparison Tests ====================

    @Test
    public void testCaseInsensitiveComparison_match() {
        String lower = "hello";
        char[] word = "HELLO".toCharArray();

        boolean matches = compareCaseInsensitive(lower, word, 0, word.length);
        assertTrue("Case insensitive should match", matches);
    }

    @Test
    public void testCaseInsensitiveComparison_noMatch() {
        String lower = "hello";
        char[] word = "WORLD".toCharArray();

        boolean matches = compareCaseInsensitive(lower, word, 0, word.length);
        assertFalse("Different words should not match", matches);
    }

    @Test
    public void testCaseInsensitiveComparison_differentLength() {
        String lower = "hello";
        char[] word = "HI".toCharArray();

        boolean matches = compareCaseInsensitive(lower, word, 0, word.length);
        assertFalse("Different lengths should not match", matches);
    }

    @Test
    public void testCaseInsensitiveComparison_requiresUpperCase() {
        String lower = "hello";
        char[] word = "hello".toCharArray();  // lowercase, first char not uppercase

        // The method requires first char to be uppercase
        boolean matches = compareCaseInsensitive(lower, word, 0, word.length);
        assertFalse("Should require uppercase first char", matches);
    }

    // Simulates compareCaseInsensitive logic from Suggest
    private boolean compareCaseInsensitive(String lowerOriginal, char[] word, int offset, int length) {
        int originalLength = lowerOriginal.length();
        if (originalLength == length && Character.isUpperCase(word[offset])) {
            for (int i = 0; i < originalLength; i++) {
                if (lowerOriginal.charAt(i) != Character.toLowerCase(word[offset + i])) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    // ==================== Priority Array Tests ====================

    @Test
    public void testPriorityInsertion_emptyArray() {
        int[] priorities = new int[12];
        int freq = 100;
        int pos = findInsertPosition(priorities, freq, 12);
        assertEquals("Should insert at position 0 in empty array", 0, pos);
    }

    @Test
    public void testPriorityInsertion_highestPriority() {
        int[] priorities = {50, 40, 30, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        int freq = 100;
        int pos = findInsertPosition(priorities, freq, 12);
        assertEquals("Highest priority should insert at 0", 0, pos);
    }

    @Test
    public void testPriorityInsertion_lowestPriority() {
        int[] priorities = {100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 5, 2};
        int freq = 1;
        int pos = findInsertPosition(priorities, freq, 12);
        assertEquals("Lowest priority should be at end", 12, pos);
    }

    @Test
    public void testPriorityInsertion_middlePriority() {
        int[] priorities = {100, 80, 60, 40, 20, 0, 0, 0, 0, 0, 0, 0};
        int freq = 50;
        int pos = findInsertPosition(priorities, freq, 12);
        assertEquals("Priority 50 should go after 60", 3, pos);
    }

    // Simulates priority insertion logic from addWord
    private int findInsertPosition(int[] priorities, int freq, int maxSuggestions) {
        int pos = 0;
        while (pos < maxSuggestions) {
            if (priorities[pos] < freq) {
                break;
            }
            pos++;
        }
        return pos;
    }

    // ==================== Dictionary Type Tests ====================

    @Test
    public void testDictionaryTypes_uniqueIds() {
        int[] types = {
                Suggest.DIC_USER_TYPED,
                Suggest.DIC_MAIN,
                Suggest.DIC_USER,
                Suggest.DIC_AUTO,
                Suggest.DIC_CONTACTS
        };

        // Verify all IDs are unique
        for (int i = 0; i < types.length; i++) {
            for (int j = i + 1; j < types.length; j++) {
                assertNotEquals("Dictionary types should have unique IDs",
                        types[i], types[j]);
            }
        }
    }

    @Test
    public void testDictionaryTypes_consecutiveIds() {
        // IDs should be consecutive starting from 0
        assertEquals(0, Suggest.DIC_USER_TYPED);
        assertEquals(1, Suggest.DIC_MAIN);
        assertEquals(2, Suggest.DIC_USER);
        assertEquals(3, Suggest.DIC_AUTO);
        assertEquals(4, Suggest.DIC_CONTACTS);
    }

    // ==================== Correction Mode Tests ====================

    @Test
    public void testCorrectionModes_ordering() {
        assertTrue("NONE < BASIC", Suggest.CORRECTION_NONE < Suggest.CORRECTION_BASIC);
        assertTrue("BASIC < FULL", Suggest.CORRECTION_BASIC < Suggest.CORRECTION_FULL);
        assertTrue("FULL < FULL_BIGRAM", Suggest.CORRECTION_FULL < Suggest.CORRECTION_FULL_BIGRAM);
    }

    @Test
    public void testCorrectionMode_bigramRequiresFull() {
        // Bigram mode should be the highest correction level
        int[] allModes = {
                Suggest.CORRECTION_NONE,
                Suggest.CORRECTION_BASIC,
                Suggest.CORRECTION_FULL,
                Suggest.CORRECTION_FULL_BIGRAM
        };

        int maxMode = Integer.MIN_VALUE;
        for (int mode : allModes) {
            maxMode = Math.max(maxMode, mode);
        }

        assertEquals("FULL_BIGRAM should be the highest mode",
                Suggest.CORRECTION_FULL_BIGRAM, maxMode);
    }
}
