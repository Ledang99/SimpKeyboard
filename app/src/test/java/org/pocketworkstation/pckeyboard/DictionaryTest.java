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
 * Unit tests for {@link Dictionary} base class.
 * Tests the abstract dictionary functionality including word matching
 * and the same() method.
 */
public class DictionaryTest {

    // Test implementation of abstract Dictionary for testing
    private static class TestDictionary extends Dictionary {
        @Override
        public void getWords(WordComposer composer, WordCallback callback, int[] nextLettersFrequencies) {
            // No-op for testing
        }

        @Override
        public boolean isValidWord(CharSequence word) {
            return word != null && word.length() > 0;
        }

        // Expose protected method for testing
        public boolean testSame(char[] word, int length, CharSequence typedWord) {
            return same(word, length, typedWord);
        }
    }

    private TestDictionary mDictionary;

    @org.junit.Before
    public void setUp() {
        mDictionary = new TestDictionary();
    }

    // ==================== Constants Tests ====================

    @Test
    public void testConstants_includeTypedWordIfValid() {
        assertFalse("INCLUDE_TYPED_WORD_IF_VALID should be false", Dictionary.INCLUDE_TYPED_WORD_IF_VALID);
    }

    @Test
    public void testConstants_fullWordFreqMultiplier() {
        assertEquals("FULL_WORD_FREQ_MULTIPLIER should be 2", 2, Dictionary.FULL_WORD_FREQ_MULTIPLIER);
    }

    // ==================== DataType Enum Tests ====================

    @Test
    public void testDataType_values() {
        Dictionary.DataType[] values = Dictionary.DataType.values();
        assertEquals("Should have 2 data types", 2, values.length);
    }

    @Test
    public void testDataType_unigram() {
        assertEquals("UNIGRAM ordinal should be 0", 0, Dictionary.DataType.UNIGRAM.ordinal());
    }

    @Test
    public void testDataType_bigram() {
        assertEquals("BIGRAM ordinal should be 1", 1, Dictionary.DataType.BIGRAM.ordinal());
    }

    @Test
    public void testDataType_valueOf() {
        assertEquals("valueOf UNIGRAM should work", Dictionary.DataType.UNIGRAM, Dictionary.DataType.valueOf("UNIGRAM"));
        assertEquals("valueOf BIGRAM should work", Dictionary.DataType.BIGRAM, Dictionary.DataType.valueOf("BIGRAM"));
    }

    // ==================== same() Method Tests ====================

    @Test
    public void testSame_identicalWord() {
        char[] word = "hello".toCharArray();
        assertTrue("Identical words should match", mDictionary.testSame(word, 5, "hello"));
    }

    @Test
    public void testSame_differentLength() {
        char[] word = "hello".toCharArray();
        assertFalse("Different lengths should not match", mDictionary.testSame(word, 5, "hell"));
    }

    @Test
    public void testSame_differentContent() {
        char[] word = "hello".toCharArray();
        assertFalse("Different content should not match", mDictionary.testSame(word, 5, "world"));
    }

    @Test
    public void testSame_partialWord() {
        char[] word = "hello world".toCharArray();
        assertTrue("Partial word should match", mDictionary.testSame(word, 5, "hello"));
    }

    @Test
    public void testSame_emptyWord() {
        char[] word = new char[0];
        assertTrue("Empty words should match", mDictionary.testSame(word, 0, ""));
    }

    @Test
    public void testSame_singleChar() {
        char[] word = "a".toCharArray();
        assertTrue("Single char should match", mDictionary.testSame(word, 1, "a"));
    }

    @Test
    public void testSame_singleCharNoMatch() {
        char[] word = "a".toCharArray();
        assertFalse("Different single chars should not match", mDictionary.testSame(word, 1, "b"));
    }

    @Test
    public void testSame_caseSensitive() {
        char[] word = "Hello".toCharArray();
        assertFalse("same() is case sensitive", mDictionary.testSame(word, 5, "hello"));
    }

    @Test
    public void testSame_allCaps() {
        char[] word = "HELLO".toCharArray();
        assertTrue("All caps should match", mDictionary.testSame(word, 5, "HELLO"));
    }

    @Test
    public void testSame_specialChars() {
        char[] word = "it's".toCharArray();
        assertTrue("Words with apostrophe should match", mDictionary.testSame(word, 4, "it's"));
    }

    @Test
    public void testSame_unicode() {
        char[] word = "café".toCharArray();
        assertTrue("Unicode words should match", mDictionary.testSame(word, 4, "café"));
    }

    @Test
    public void testSame_unicodeNoMatch() {
        char[] word = "café".toCharArray();
        assertFalse("Different unicode should not match", mDictionary.testSame(word, 4, "cafe"));
    }

    @Test
    public void testSame_zerLengthWithContent() {
        char[] word = "hello".toCharArray();
        assertTrue("Zero length comparison should match empty string", mDictionary.testSame(word, 0, ""));
    }

    @Test
    public void testSame_numberInWord() {
        char[] word = "test123".toCharArray();
        assertTrue("Numbers in words should match", mDictionary.testSame(word, 7, "test123"));
    }

    @Test
    public void testSame_longWord() {
        String longWord = "supercalifragilisticexpialidocious";
        char[] word = longWord.toCharArray();
        assertTrue("Long words should match", mDictionary.testSame(word, longWord.length(), longWord));
    }

    // ==================== getBigrams Tests ====================

    @Test
    public void testGetBigrams_defaultImplementation() {
        // Default implementation does nothing, should not throw
        WordComposer composer = new WordComposer();
        mDictionary.getBigrams(composer, "previous", new Dictionary.WordCallback() {
            @Override
            public boolean addWord(char[] word, int wordOffset, int wordLength, int frequency, int dicTypeId, Dictionary.DataType dataType) {
                fail("Default getBigrams should not call callback");
                return false;
            }
        }, null);
        // No assertion needed - just verifying no exception is thrown
    }

    // ==================== close() Tests ====================

    @Test
    public void testClose_defaultImplementation() {
        // Default implementation does nothing, should not throw
        mDictionary.close();
        // No assertion needed - just verifying no exception is thrown
    }

    // ==================== WordCallback Interface Tests ====================

    @Test
    public void testWordCallback_addWord() {
        final boolean[] called = {false};
        final char[] capturedWord = new char[10];
        final int[] capturedFreq = {0};

        Dictionary.WordCallback callback = new Dictionary.WordCallback() {
            @Override
            public boolean addWord(char[] word, int wordOffset, int wordLength, int frequency, int dicTypeId, Dictionary.DataType dataType) {
                called[0] = true;
                System.arraycopy(word, wordOffset, capturedWord, 0, wordLength);
                capturedFreq[0] = frequency;
                return true;
            }
        };

        char[] testWord = "test".toCharArray();
        boolean result = callback.addWord(testWord, 0, 4, 100, 0, Dictionary.DataType.UNIGRAM);

        assertTrue("Callback should have been called", called[0]);
        assertTrue("addWord should return true", result);
        assertEquals("Frequency should be captured", 100, capturedFreq[0]);
    }

    @Test
    public void testWordCallback_returnFalse() {
        Dictionary.WordCallback callback = new Dictionary.WordCallback() {
            @Override
            public boolean addWord(char[] word, int wordOffset, int wordLength, int frequency, int dicTypeId, Dictionary.DataType dataType) {
                return false; // Signal to stop adding words
            }
        };

        char[] testWord = "test".toCharArray();
        boolean result = callback.addWord(testWord, 0, 4, 100, 0, Dictionary.DataType.UNIGRAM);

        assertFalse("addWord should return false", result);
    }

    @Test
    public void testWordCallback_withOffset() {
        final String[] capturedWord = {""};

        Dictionary.WordCallback callback = new Dictionary.WordCallback() {
            @Override
            public boolean addWord(char[] word, int wordOffset, int wordLength, int frequency, int dicTypeId, Dictionary.DataType dataType) {
                capturedWord[0] = new String(word, wordOffset, wordLength);
                return true;
            }
        };

        char[] buffer = "xxxhelloxxx".toCharArray();
        callback.addWord(buffer, 3, 5, 100, 0, Dictionary.DataType.UNIGRAM);

        assertEquals("Should extract 'hello' from buffer", "hello", capturedWord[0]);
    }

    @Test
    public void testWordCallback_dicTypeId() {
        final int[] capturedDicType = {-1};

        Dictionary.WordCallback callback = new Dictionary.WordCallback() {
            @Override
            public boolean addWord(char[] word, int wordOffset, int wordLength, int frequency, int dicTypeId, Dictionary.DataType dataType) {
                capturedDicType[0] = dicTypeId;
                return true;
            }
        };

        callback.addWord("test".toCharArray(), 0, 4, 100, 42, Dictionary.DataType.UNIGRAM);

        assertEquals("DicTypeId should be captured", 42, capturedDicType[0]);
    }

    @Test
    public void testWordCallback_dataType() {
        final Dictionary.DataType[] capturedType = {null};

        Dictionary.WordCallback callback = new Dictionary.WordCallback() {
            @Override
            public boolean addWord(char[] word, int wordOffset, int wordLength, int frequency, int dicTypeId, Dictionary.DataType dataType) {
                capturedType[0] = dataType;
                return true;
            }
        };

        callback.addWord("test".toCharArray(), 0, 4, 100, 0, Dictionary.DataType.BIGRAM);

        assertEquals("DataType should be BIGRAM", Dictionary.DataType.BIGRAM, capturedType[0]);
    }

    // ==================== isValidWord Tests ====================

    @Test
    public void testIsValidWord_nonEmpty() {
        assertTrue("Non-empty word should be valid", mDictionary.isValidWord("hello"));
    }

    @Test
    public void testIsValidWord_empty() {
        assertFalse("Empty word should be invalid", mDictionary.isValidWord(""));
    }

    @Test
    public void testIsValidWord_null() {
        assertFalse("Null word should be invalid", mDictionary.isValidWord(null));
    }

    // ==================== Frequency Multiplier Tests ====================

    @Test
    public void testFrequencyMultiplier_application() {
        int baseFreq = 100;
        int multipliedFreq = baseFreq * Dictionary.FULL_WORD_FREQ_MULTIPLIER;
        assertEquals("Multiplied frequency should be 200", 200, multipliedFreq);
    }

    @Test
    public void testFrequencyMultiplier_zeroBase() {
        int baseFreq = 0;
        int multipliedFreq = baseFreq * Dictionary.FULL_WORD_FREQ_MULTIPLIER;
        assertEquals("Zero frequency multiplied should be 0", 0, multipliedFreq);
    }
}
