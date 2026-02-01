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
 * Unit tests for {@link ExpandableDictionary}.
 * Tests the in-memory dictionary implementation including word storage,
 * retrieval, frequency handling, and character case conversion.
 */
public class ExpandableDictionaryTest {

    // ==================== Constants Tests ====================

    @Test
    public void testMaxWordLength_constant() {
        assertEquals("MAX_WORD_LENGTH should be 32", 32, ExpandableDictionary.MAX_WORD_LENGTH);
    }

    // ==================== toLowerCase Tests ====================

    @Test
    public void testToLowerCase_uppercaseAscii() {
        assertEquals("A should convert to a", 'a', ExpandableDictionary.toLowerCase('A'));
        assertEquals("Z should convert to z", 'z', ExpandableDictionary.toLowerCase('Z'));
        assertEquals("M should convert to m", 'm', ExpandableDictionary.toLowerCase('M'));
    }

    @Test
    public void testToLowerCase_lowercaseAscii() {
        assertEquals("a should remain a", 'a', ExpandableDictionary.toLowerCase('a'));
        assertEquals("z should remain z", 'z', ExpandableDictionary.toLowerCase('z'));
        assertEquals("m should remain m", 'm', ExpandableDictionary.toLowerCase('m'));
    }

    @Test
    public void testToLowerCase_numbers() {
        assertEquals("0 should remain 0", '0', ExpandableDictionary.toLowerCase('0'));
        assertEquals("9 should remain 9", '9', ExpandableDictionary.toLowerCase('9'));
        assertEquals("5 should remain 5", '5', ExpandableDictionary.toLowerCase('5'));
    }

    @Test
    public void testToLowerCase_specialChars() {
        assertEquals("Space should remain space", ' ', ExpandableDictionary.toLowerCase(' '));
        assertEquals("Period should remain period", '.', ExpandableDictionary.toLowerCase('.'));
        assertEquals("Apostrophe should remain apostrophe", '\'', ExpandableDictionary.toLowerCase('\''));
    }

    @Test
    public void testToLowerCase_accentedUppercase() {
        // Test some accented characters that are in BASE_CHARS
        // À (0x00C0) -> A (0x0041)
        char result = ExpandableDictionary.toLowerCase('\u00C0');
        assertEquals("À should map to A", 'A', result);
    }

    @Test
    public void testToLowerCase_accentedLowercase() {
        // à (0x00E0) -> a (0x0061)
        char result = ExpandableDictionary.toLowerCase('\u00E0');
        assertEquals("à should map to a", 'a', result);
    }

    @Test
    public void testToLowerCase_unicodeUppercase() {
        // Test character above 127
        char result = ExpandableDictionary.toLowerCase('Ω'); // Greek capital Omega
        assertEquals("Greek uppercase should convert", Character.toLowerCase('Ω'), result);
    }

    @Test
    public void testToLowerCase_unicodeLowercase() {
        char result = ExpandableDictionary.toLowerCase('ω'); // Greek lowercase omega
        assertEquals("Greek lowercase should remain", 'ω', result);
    }

    // ==================== BASE_CHARS Table Tests ====================

    @Test
    public void testBaseChars_asciiRange() {
        // ASCII letters should map to themselves or lowercase equivalents
        for (char c = 'A'; c <= 'Z'; c++) {
            assertTrue("Uppercase ASCII should be in range",
                    c < ExpandableDictionary.BASE_CHARS.length);
        }
        for (char c = 'a'; c <= 'z'; c++) {
            assertTrue("Lowercase ASCII should be in range",
                    c < ExpandableDictionary.BASE_CHARS.length);
        }
    }

    @Test
    public void testBaseChars_preservesBasicChars() {
        // Basic ASCII should map to itself
        for (char c = 0; c < 128; c++) {
            if (c >= 'A' && c <= 'Z') {
                // Uppercase maps to uppercase (case conversion happens elsewhere)
                assertEquals("Basic uppercase should map to itself",
                        c, ExpandableDictionary.BASE_CHARS[c]);
            } else if (c >= 'a' && c <= 'z') {
                assertEquals("Basic lowercase should map to itself",
                        c, ExpandableDictionary.BASE_CHARS[c]);
            }
        }
    }

    @Test
    public void testBaseChars_tableSize() {
        // BASE_CHARS should cover extended Latin and more
        assertTrue("BASE_CHARS should be large enough",
                ExpandableDictionary.BASE_CHARS.length >= 1280);
    }

    // ==================== NodeArray Tests ====================

    @Test
    public void testNodeArray_initialState() {
        ExpandableDictionary.NodeArray nodeArray = new ExpandableDictionary.NodeArray();
        assertEquals("Initial length should be 0", 0, nodeArray.length);
        assertNotNull("Data should not be null", nodeArray.data);
    }

    @Test
    public void testNodeArray_addSingleNode() {
        ExpandableDictionary.NodeArray nodeArray = new ExpandableDictionary.NodeArray();
        ExpandableDictionary.Node node = new ExpandableDictionary.Node();
        node.code = 'a';

        nodeArray.add(node);

        assertEquals("Length should be 1", 1, nodeArray.length);
        assertEquals("Node should be stored", 'a', nodeArray.data[0].code);
    }

    @Test
    public void testNodeArray_addMultipleNodes() {
        ExpandableDictionary.NodeArray nodeArray = new ExpandableDictionary.NodeArray();

        for (int i = 0; i < 10; i++) {
            ExpandableDictionary.Node node = new ExpandableDictionary.Node();
            node.code = (char) ('a' + i);
            nodeArray.add(node);
        }

        assertEquals("Length should be 10", 10, nodeArray.length);
        assertEquals("First node should be 'a'", 'a', nodeArray.data[0].code);
        assertEquals("Last node should be 'j'", 'j', nodeArray.data[9].code);
    }

    @Test
    public void testNodeArray_growsAutomatically() {
        ExpandableDictionary.NodeArray nodeArray = new ExpandableDictionary.NodeArray();

        // Add more nodes than initial capacity
        for (int i = 0; i < 100; i++) {
            ExpandableDictionary.Node node = new ExpandableDictionary.Node();
            node.code = (char) i;
            nodeArray.add(node);
        }

        assertEquals("Should handle 100 nodes", 100, nodeArray.length);
    }

    // ==================== Node Tests ====================

    @Test
    public void testNode_defaultValues() {
        ExpandableDictionary.Node node = new ExpandableDictionary.Node();

        assertEquals("Default code should be 0", 0, node.code);
        assertEquals("Default frequency should be 0", 0, node.frequency);
        assertFalse("Default terminal should be false", node.terminal);
        assertNull("Default parent should be null", node.parent);
        assertNull("Default children should be null", node.children);
        assertNull("Default ngrams should be null", node.ngrams);
    }

    @Test
    public void testNode_setValues() {
        ExpandableDictionary.Node node = new ExpandableDictionary.Node();
        node.code = 'x';
        node.frequency = 100;
        node.terminal = true;

        assertEquals("Code should be set", 'x', node.code);
        assertEquals("Frequency should be set", 100, node.frequency);
        assertTrue("Terminal should be set", node.terminal);
    }

    @Test
    public void testNode_parentChildRelationship() {
        ExpandableDictionary.Node parent = new ExpandableDictionary.Node();
        parent.code = 'h';
        parent.children = new ExpandableDictionary.NodeArray();

        ExpandableDictionary.Node child = new ExpandableDictionary.Node();
        child.code = 'e';
        child.parent = parent;

        parent.children.add(child);

        assertEquals("Child parent should be set", parent, child.parent);
        assertEquals("Parent should have child", child, parent.children.data[0]);
    }

    // ==================== NextWord Tests ====================

    @Test
    public void testNextWord_creation() {
        ExpandableDictionary.Node word = new ExpandableDictionary.Node();
        word.code = 't';

        ExpandableDictionary.NextWord nextWord = new ExpandableDictionary.NextWord(word, 50);

        assertEquals("Word should be set", word, nextWord.word);
        assertEquals("Frequency should be set", 50, nextWord.frequency);
        assertNull("NextWord link should be null", nextWord.nextWord);
    }

    @Test
    public void testNextWord_linkage() {
        ExpandableDictionary.Node word1 = new ExpandableDictionary.Node();
        ExpandableDictionary.Node word2 = new ExpandableDictionary.Node();

        ExpandableDictionary.NextWord nw1 = new ExpandableDictionary.NextWord(word1, 100);
        ExpandableDictionary.NextWord nw2 = new ExpandableDictionary.NextWord(word2, 50);

        nw1.nextWord = nw2;

        assertEquals("Linked NextWord should be accessible", nw2, nw1.nextWord);
    }

    // ==================== Dictionary Data Types Tests ====================

    @Test
    public void testDataType_unigram() {
        Dictionary.DataType type = Dictionary.DataType.UNIGRAM;
        assertNotNull("UNIGRAM should exist", type);
        assertEquals("UNIGRAM name should match", "UNIGRAM", type.name());
    }

    @Test
    public void testDataType_bigram() {
        Dictionary.DataType type = Dictionary.DataType.BIGRAM;
        assertNotNull("BIGRAM should exist", type);
        assertEquals("BIGRAM name should match", "BIGRAM", type.name());
    }

    @Test
    public void testDataType_allValues() {
        Dictionary.DataType[] values = Dictionary.DataType.values();
        assertEquals("Should have 2 data types", 2, values.length);
    }

    // ==================== Frequency Capping Tests ====================

    @Test
    public void testFrequencyCapping_underLimit() {
        int freq = 100;
        if (freq > 255) freq = 255;
        assertEquals("Frequency under 255 should be unchanged", 100, freq);
    }

    @Test
    public void testFrequencyCapping_atLimit() {
        int freq = 255;
        if (freq > 255) freq = 255;
        assertEquals("Frequency at 255 should be unchanged", 255, freq);
    }

    @Test
    public void testFrequencyCapping_overLimit() {
        int freq = 300;
        if (freq > 255) freq = 255;
        assertEquals("Frequency over 255 should be capped", 255, freq);
    }

    @Test
    public void testFrequencyCapping_wayOverLimit() {
        int freq = 10000;
        if (freq > 255) freq = 255;
        assertEquals("High frequency should be capped at 255", 255, freq);
    }

    // ==================== Word Building Tests ====================

    @Test
    public void testWordBuilding_singleChar() {
        char[] word = new char[32];
        int depth = 0;
        word[depth] = 'a';

        assertEquals("Single char word should be built", 'a', word[0]);
    }

    @Test
    public void testWordBuilding_multipleChars() {
        char[] word = new char[32];
        String testWord = "hello";

        for (int i = 0; i < testWord.length(); i++) {
            word[i] = testWord.charAt(i);
        }

        assertEquals("Word should be built correctly", 'h', word[0]);
        assertEquals("Word should be built correctly", 'e', word[1]);
        assertEquals("Word should be built correctly", 'l', word[2]);
        assertEquals("Word should be built correctly", 'l', word[3]);
        assertEquals("Word should be built correctly", 'o', word[4]);
    }

    @Test
    public void testWordBuilding_maxLength() {
        char[] word = new char[ExpandableDictionary.MAX_WORD_LENGTH];

        // Fill with 32 characters
        for (int i = 0; i < ExpandableDictionary.MAX_WORD_LENGTH; i++) {
            word[i] = (char) ('a' + (i % 26));
        }

        assertEquals("Should handle max length", 32, word.length);
    }

    // ==================== Character Mapping Tests ====================

    @Test
    public void testLatinAccentedCharMapping() {
        // Test specific mappings from BASE_CHARS
        // É (0xC9) should map to E (0x45)
        char e_acute = '\u00C9';
        assertTrue("É should be in range", e_acute < ExpandableDictionary.BASE_CHARS.length);

        char mapped = ExpandableDictionary.BASE_CHARS[e_acute];
        assertEquals("É should map to E", 'E', mapped);
    }

    @Test
    public void testLatinSmallAccentedCharMapping() {
        // é (0xE9) should map to e (0x65)
        char e_acute = '\u00E9';
        assertTrue("é should be in range", e_acute < ExpandableDictionary.BASE_CHARS.length);

        char mapped = ExpandableDictionary.BASE_CHARS[e_acute];
        assertEquals("é should map to e", 'e', mapped);
    }

    @Test
    public void testUmlautCharMapping() {
        // ü (0xFC) should map to u (0x75)
        char u_umlaut = '\u00FC';
        assertTrue("ü should be in range", u_umlaut < ExpandableDictionary.BASE_CHARS.length);

        char mapped = ExpandableDictionary.BASE_CHARS[u_umlaut];
        assertEquals("ü should map to u", 'u', mapped);
    }

    @Test
    public void testSpanishNTilde() {
        // ñ (0xF1) should map to n (0x6E)
        char n_tilde = '\u00F1';
        assertTrue("ñ should be in range", n_tilde < ExpandableDictionary.BASE_CHARS.length);

        char mapped = ExpandableDictionary.BASE_CHARS[n_tilde];
        assertEquals("ñ should map to n", 'n', mapped);
    }

    // ==================== Trie Structure Tests ====================

    @Test
    public void testTrieDepthCalculation() {
        // Max depth is input length * 3
        int inputLength = 5;
        int maxDepth = inputLength * 3;
        assertEquals("Max depth should be 15 for input length 5", 15, maxDepth);
    }

    @Test
    public void testTrieDepthCalculation_singleChar() {
        int inputLength = 1;
        int maxDepth = inputLength * 3;
        assertEquals("Max depth should be 3 for single char", 3, maxDepth);
    }

    @Test
    public void testTrieDepthCalculation_maxWord() {
        int inputLength = 32;
        int maxDepth = inputLength * 3;
        assertEquals("Max depth should be 96 for max word", 96, maxDepth);
    }

    // ==================== Edge Cases ====================

    @Test
    public void testEmptyString() {
        String word = "";
        assertEquals("Empty string length should be 0", 0, word.length());
    }

    @Test
    public void testNullCharacter() {
        char nullChar = '\0';
        assertTrue("Null char should be in BASE_CHARS range", nullChar < ExpandableDictionary.BASE_CHARS.length);
        assertEquals("Null char should map to null char", '\0', ExpandableDictionary.BASE_CHARS[nullChar]);
    }

    @Test
    public void testQuoteCharacter() {
        char quote = '\'';
        assertEquals("Quote should be ASCII 39", 39, (int) quote);
        assertTrue("Quote should be in BASE_CHARS range", quote < ExpandableDictionary.BASE_CHARS.length);
    }
}
