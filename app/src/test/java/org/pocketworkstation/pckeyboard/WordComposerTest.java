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

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link WordComposer}.
 * Tests the word composition functionality including keystroke tracking,
 * capitalization detection, and backspace handling.
 */
public class WordComposerTest {

    private WordComposer mWordComposer;

    @Before
    public void setUp() {
        mWordComposer = new WordComposer();
    }

    // ==================== Constructor Tests ====================

    @Test
    public void testConstructor_initialState() {
        assertEquals("Initial size should be 0", 0, mWordComposer.size());
        assertNull("Initial typed word should be null", mWordComposer.getTypedWord());
        assertFalse("Initial first char should not be capitalized", mWordComposer.isFirstCharCapitalized());
        assertFalse("Initial all uppercase should be false", mWordComposer.isAllUpperCase());
        assertFalse("Initial auto capitalized should be false", mWordComposer.isAutoCapitalized());
    }

    @Test
    public void testCopyConstructor() {
        // Set up the original
        mWordComposer.add('H', new int[]{'h', 'g', 'j'});
        mWordComposer.add('i', new int[]{'i', 'u', 'o'});
        mWordComposer.setFirstCharCapitalized(true);
        mWordComposer.setPreferredWord("Hi");
        mWordComposer.setAutoCapitalized(true);

        // Create a copy
        WordComposer copy = new WordComposer(mWordComposer);

        assertEquals("Copy should have same size", mWordComposer.size(), copy.size());
        assertEquals("Copy should have same typed word",
                mWordComposer.getTypedWord().toString(), copy.getTypedWord().toString());
        assertEquals("Copy should have same preferred word",
                mWordComposer.getPreferredWord().toString(), copy.getPreferredWord().toString());
    }

    // ==================== Add Tests ====================

    @Test
    public void testAdd_singleLowercaseCharacter() {
        mWordComposer.add('a', new int[]{'a', 's', 'q'});

        assertEquals("Size should be 1", 1, mWordComposer.size());
        assertEquals("Typed word should be 'a'", "a", mWordComposer.getTypedWord().toString());
        assertFalse("Should not be all uppercase", mWordComposer.isAllUpperCase());
    }

    @Test
    public void testAdd_singleUppercaseCharacter() {
        mWordComposer.add('A', new int[]{'a', 's', 'q'});

        assertEquals("Size should be 1", 1, mWordComposer.size());
        assertEquals("Typed word should be 'A'", "A", mWordComposer.getTypedWord().toString());
        assertTrue("Should be all uppercase with single uppercase char", mWordComposer.isAllUpperCase());
    }

    @Test
    public void testAdd_multipleCharacters() {
        mWordComposer.add('h', new int[]{'h', 'g', 'j'});
        mWordComposer.add('e', new int[]{'e', 'w', 'r'});
        mWordComposer.add('l', new int[]{'l', 'k', 'o'});
        mWordComposer.add('l', new int[]{'l', 'k', 'o'});
        mWordComposer.add('o', new int[]{'o', 'i', 'p'});

        assertEquals("Size should be 5", 5, mWordComposer.size());
        assertEquals("Typed word should be 'hello'", "hello", mWordComposer.getTypedWord().toString());
    }

    @Test
    public void testAdd_mixedCase() {
        mWordComposer.add('H', new int[]{'h', 'g', 'j'});
        mWordComposer.add('e', new int[]{'e', 'w', 'r'});
        mWordComposer.add('L', new int[]{'l', 'k', 'o'});
        mWordComposer.add('l', new int[]{'l', 'k', 'o'});
        mWordComposer.add('O', new int[]{'o', 'i', 'p'});

        assertEquals("Size should be 5", 5, mWordComposer.size());
        assertEquals("Typed word should be 'HeLlO'", "HeLlO", mWordComposer.getTypedWord().toString());
        assertFalse("Should not be all uppercase", mWordComposer.isAllUpperCase());
        assertTrue("Should have mostly caps", mWordComposer.isMostlyCaps());
    }

    @Test
    public void testAdd_allUppercase() {
        mWordComposer.add('H', new int[]{'h', 'g', 'j'});
        mWordComposer.add('I', new int[]{'i', 'u', 'o'});

        assertEquals("Size should be 2", 2, mWordComposer.size());
        assertEquals("Typed word should be 'HI'", "HI", mWordComposer.getTypedWord().toString());
        assertTrue("Should be all uppercase", mWordComposer.isAllUpperCase());
    }

    @Test
    public void testAdd_codesAreLowercased() {
        int[] codes = new int[]{'A', 'S', 'Q'};
        mWordComposer.add('a', codes);

        int[] storedCodes = mWordComposer.getCodesAt(0);
        assertEquals("First code should be lowercased", 'a', storedCodes[0]);
        assertEquals("Second code should be lowercased", 's', storedCodes[1]);
        assertEquals("Third code should be lowercased", 'q', storedCodes[2]);
    }

    @Test
    public void testAdd_correctPrimaryJuxtaposition() {
        // Simulate where the primary code is in second position due to touch position
        int[] codes = new int[]{'s', 'a', 'q'};  // 'a' is in second position but is primary
        mWordComposer.add('a', codes);

        int[] storedCodes = mWordComposer.getCodesAt(0);
        assertEquals("Primary should be swapped to first position", 'a', storedCodes[0]);
        assertEquals("Previous first should be in second position", 's', storedCodes[1]);
    }

    @Test
    public void testAdd_noSwapWhenPrimaryIsFirst() {
        int[] codes = new int[]{'a', 's', 'q'};
        mWordComposer.add('a', codes);

        int[] storedCodes = mWordComposer.getCodesAt(0);
        assertEquals("First should remain 'a'", 'a', storedCodes[0]);
        assertEquals("Second should remain 's'", 's', storedCodes[1]);
        assertEquals("Third should remain 'q'", 'q', storedCodes[2]);
    }

    @Test
    public void testAdd_shortCodesArray() {
        int[] codes = new int[]{'a'};
        mWordComposer.add('a', codes);

        assertEquals("Size should be 1", 1, mWordComposer.size());
        int[] storedCodes = mWordComposer.getCodesAt(0);
        assertEquals("Single code should be stored", 'a', storedCodes[0]);
    }

    // ==================== Delete Tests ====================

    @Test
    public void testDeleteLast_singleCharacter() {
        mWordComposer.add('a', new int[]{'a', 's', 'q'});
        mWordComposer.deleteLast();

        assertEquals("Size should be 0 after delete", 0, mWordComposer.size());
        assertNull("Typed word should be null after deleting last char", mWordComposer.getTypedWord());
    }

    @Test
    public void testDeleteLast_multipleCharacters() {
        mWordComposer.add('h', new int[]{'h'});
        mWordComposer.add('i', new int[]{'i'});
        mWordComposer.deleteLast();

        assertEquals("Size should be 1 after delete", 1, mWordComposer.size());
        assertEquals("Typed word should be 'h'", "h", mWordComposer.getTypedWord().toString());
    }

    @Test
    public void testDeleteLast_updatesCapCount() {
        mWordComposer.add('H', new int[]{'h'});
        mWordComposer.add('I', new int[]{'i'});
        assertTrue("Should be all uppercase before delete", mWordComposer.isAllUpperCase());

        mWordComposer.deleteLast();
        assertTrue("Should still be all uppercase after deleting 'I'", mWordComposer.isAllUpperCase());

        mWordComposer.deleteLast();
        assertFalse("Should not be all uppercase after deleting all", mWordComposer.isAllUpperCase());
    }

    @Test
    public void testDeleteLast_emptyWord() {
        // Should not throw exception
        mWordComposer.deleteLast();
        assertEquals("Size should still be 0", 0, mWordComposer.size());
    }

    @Test
    public void testDeleteLast_decrementsCapsCount() {
        mWordComposer.add('A', new int[]{'a'});
        mWordComposer.add('B', new int[]{'b'});
        mWordComposer.add('c', new int[]{'c'});

        assertTrue("Should have mostly caps (2 caps)", mWordComposer.isMostlyCaps());

        mWordComposer.deleteLast(); // remove 'c'
        assertTrue("Should still have mostly caps", mWordComposer.isMostlyCaps());

        mWordComposer.deleteLast(); // remove 'B'
        assertFalse("Should not have mostly caps (only 1 cap)", mWordComposer.isMostlyCaps());
    }

    // ==================== Reset Tests ====================

    @Test
    public void testReset_clearsAllState() {
        mWordComposer.add('H', new int[]{'h'});
        mWordComposer.add('i', new int[]{'i'});
        mWordComposer.setFirstCharCapitalized(true);
        mWordComposer.setPreferredWord("Hello");

        mWordComposer.reset();

        assertEquals("Size should be 0 after reset", 0, mWordComposer.size());
        assertNull("Typed word should be null after reset", mWordComposer.getTypedWord());
        assertFalse("First char capitalized should be false after reset", mWordComposer.isFirstCharCapitalized());
        assertFalse("All uppercase should be false after reset", mWordComposer.isAllUpperCase());
    }

    // ==================== Capitalization Tests ====================

    @Test
    public void testIsFirstCharCapitalized_whenSet() {
        mWordComposer.setFirstCharCapitalized(true);
        assertTrue("Should be first char capitalized when set", mWordComposer.isFirstCharCapitalized());

        mWordComposer.setFirstCharCapitalized(false);
        assertFalse("Should not be first char capitalized when unset", mWordComposer.isFirstCharCapitalized());
    }

    @Test
    public void testIsAllUpperCase_emptyWord() {
        assertFalse("Empty word should not be all uppercase", mWordComposer.isAllUpperCase());
    }

    @Test
    public void testIsAllUpperCase_allLowercase() {
        mWordComposer.add('h', new int[]{'h'});
        mWordComposer.add('i', new int[]{'i'});

        assertFalse("All lowercase should not be all uppercase", mWordComposer.isAllUpperCase());
    }

    @Test
    public void testIsMostlyCaps_singleUppercase() {
        mWordComposer.add('A', new int[]{'a'});

        assertFalse("Single uppercase should not be mostly caps", mWordComposer.isMostlyCaps());
    }

    @Test
    public void testIsMostlyCaps_twoUppercase() {
        mWordComposer.add('A', new int[]{'a'});
        mWordComposer.add('B', new int[]{'b'});

        assertTrue("Two uppercase should be mostly caps", mWordComposer.isMostlyCaps());
    }

    // ==================== Preferred Word Tests ====================

    @Test
    public void testGetPreferredWord_whenNotSet() {
        mWordComposer.add('h', new int[]{'h'});
        mWordComposer.add('i', new int[]{'i'});

        assertEquals("Preferred word should be typed word when not set",
                "hi", mWordComposer.getPreferredWord().toString());
    }

    @Test
    public void testGetPreferredWord_whenSet() {
        mWordComposer.add('h', new int[]{'h'});
        mWordComposer.add('i', new int[]{'i'});
        mWordComposer.setPreferredWord("hello");

        assertEquals("Preferred word should be 'hello' when set",
                "hello", mWordComposer.getPreferredWord().toString());
    }

    @Test
    public void testGetPreferredWord_emptyWord() {
        assertNull("Preferred word should be null for empty word", mWordComposer.getPreferredWord());
    }

    // ==================== Auto Capitalization Tests ====================

    @Test
    public void testAutoCapitalized() {
        assertFalse("Auto capitalized should be false initially", mWordComposer.isAutoCapitalized());

        mWordComposer.setAutoCapitalized(true);
        assertTrue("Auto capitalized should be true when set", mWordComposer.isAutoCapitalized());

        mWordComposer.setAutoCapitalized(false);
        assertFalse("Auto capitalized should be false when unset", mWordComposer.isAutoCapitalized());
    }

    // ==================== GetCodesAt Tests ====================

    @Test
    public void testGetCodesAt_validIndex() {
        int[] codes1 = new int[]{'h', 'g', 'j'};
        int[] codes2 = new int[]{'i', 'u', 'o'};

        mWordComposer.add('h', codes1);
        mWordComposer.add('i', codes2);

        int[] retrieved1 = mWordComposer.getCodesAt(0);
        int[] retrieved2 = mWordComposer.getCodesAt(1);

        // Note: codes are lowercased
        assertEquals("First code at 0 should match", 'h', retrieved1[0]);
        assertEquals("First code at 1 should match", 'i', retrieved2[0]);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testGetCodesAt_invalidIndex() {
        mWordComposer.add('h', new int[]{'h'});
        mWordComposer.getCodesAt(5); // Should throw
    }

    // ==================== Size Tests ====================

    @Test
    public void testSize_empty() {
        assertEquals("Size of empty composer should be 0", 0, mWordComposer.size());
    }

    @Test
    public void testSize_afterAdditions() {
        mWordComposer.add('a', new int[]{'a'});
        assertEquals("Size after 1 add should be 1", 1, mWordComposer.size());

        mWordComposer.add('b', new int[]{'b'});
        assertEquals("Size after 2 adds should be 2", 2, mWordComposer.size());

        mWordComposer.add('c', new int[]{'c'});
        assertEquals("Size after 3 adds should be 3", 3, mWordComposer.size());
    }

    @Test
    public void testSize_afterDeletions() {
        mWordComposer.add('a', new int[]{'a'});
        mWordComposer.add('b', new int[]{'b'});
        mWordComposer.add('c', new int[]{'c'});

        mWordComposer.deleteLast();
        assertEquals("Size after 1 delete should be 2", 2, mWordComposer.size());

        mWordComposer.deleteLast();
        assertEquals("Size after 2 deletes should be 1", 1, mWordComposer.size());
    }

    // ==================== GetTypedWord Tests ====================

    @Test
    public void testGetTypedWord_empty() {
        assertNull("Typed word should be null when empty", mWordComposer.getTypedWord());
    }

    @Test
    public void testGetTypedWord_preservesCase() {
        mWordComposer.add('H', new int[]{'h'});
        mWordComposer.add('e', new int[]{'e'});
        mWordComposer.add('L', new int[]{'l'});
        mWordComposer.add('l', new int[]{'l'});
        mWordComposer.add('O', new int[]{'o'});

        assertEquals("Typed word should preserve case", "HeLlO", mWordComposer.getTypedWord().toString());
    }

    // ==================== Edge Case Tests ====================

    @Test
    public void testSpecialCharacters() {
        mWordComposer.add('\'', new int[]{'\''});
        mWordComposer.add('-', new int[]{'-'});

        assertEquals("Size should be 2", 2, mWordComposer.size());
        assertEquals("Typed word should be '\"-\"'", "'-", mWordComposer.getTypedWord().toString());
    }

    @Test
    public void testNegativeCodes() {
        // -1 is often used as a sentinel value
        int[] codes = new int[]{'a', -1, -1};
        mWordComposer.add('a', codes);

        int[] storedCodes = mWordComposer.getCodesAt(0);
        assertEquals("Should preserve negative sentinel values", -1, storedCodes[1]);
    }

    @Test
    public void testUnicodeCharacters() {
        mWordComposer.add('é', new int[]{'e', 'é'});
        mWordComposer.add('ñ', new int[]{'n', 'ñ'});

        assertEquals("Size should be 2", 2, mWordComposer.size());
        assertEquals("Should handle unicode", "éñ", mWordComposer.getTypedWord().toString());
    }

    @Test
    public void testLongWord() {
        String longWord = "supercalifragilisticexpialidocious";
        for (char c : longWord.toCharArray()) {
            mWordComposer.add(c, new int[]{c});
        }

        assertEquals("Should handle long words", longWord.length(), mWordComposer.size());
        assertEquals("Should match long word", longWord, mWordComposer.getTypedWord().toString());
    }
}
