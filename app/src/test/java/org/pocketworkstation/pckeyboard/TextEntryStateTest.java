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
 * Unit tests for {@link TextEntryState}.
 * Tests the state machine that tracks text input states including
 * typing, accepting suggestions, and undo operations.
 */
public class TextEntryStateTest {

    @Before
    public void setUp() {
        // Reset the state machine before each test
        TextEntryState.reset();
    }

    // ==================== Initial State Tests ====================

    @Test
    public void testReset_setsStateToStart() {
        TextEntryState.typedCharacter('a', false);
        TextEntryState.reset();
        assertEquals("State should be START after reset", TextEntryState.State.START, TextEntryState.getState());
    }

    // ==================== State Transition from START ====================

    @Test
    public void testTypedCharacter_startToInWord() {
        TextEntryState.reset();
        TextEntryState.typedCharacter('a', false);
        assertEquals("Typing a character from START should go to IN_WORD",
                TextEntryState.State.IN_WORD, TextEntryState.getState());
    }

    @Test
    public void testTypedCharacter_startToStart_withSpace() {
        TextEntryState.reset();
        TextEntryState.typedCharacter(' ', false);
        assertEquals("Typing space from START should stay in START",
                TextEntryState.State.START, TextEntryState.getState());
    }

    @Test
    public void testTypedCharacter_startToStart_withSeparator() {
        TextEntryState.reset();
        TextEntryState.typedCharacter('.', true);
        assertEquals("Typing separator from START should stay in START",
                TextEntryState.State.START, TextEntryState.getState());
    }

    // ==================== State Transition from IN_WORD ====================

    @Test
    public void testTypedCharacter_inWordStaysInWord() {
        TextEntryState.reset();
        TextEntryState.typedCharacter('a', false);
        TextEntryState.typedCharacter('b', false);
        assertEquals("Typing another character in IN_WORD should stay IN_WORD",
                TextEntryState.State.IN_WORD, TextEntryState.getState());
    }

    @Test
    public void testTypedCharacter_inWordToStart_withSpace() {
        TextEntryState.reset();
        TextEntryState.typedCharacter('a', false);
        TextEntryState.typedCharacter(' ', false);
        assertEquals("Typing space in IN_WORD should go to START",
                TextEntryState.State.START, TextEntryState.getState());
    }

    @Test
    public void testTypedCharacter_inWordToStart_withSeparator() {
        TextEntryState.reset();
        TextEntryState.typedCharacter('a', false);
        TextEntryState.typedCharacter('.', true);
        assertEquals("Typing separator in IN_WORD should go to START",
                TextEntryState.State.START, TextEntryState.getState());
    }

    // ==================== Accepted Default Tests ====================

    @Test
    public void testAcceptedDefault_setsState() {
        TextEntryState.reset();
        TextEntryState.acceptedDefault("test", "test");
        assertEquals("acceptedDefault should set state to ACCEPTED_DEFAULT",
                TextEntryState.State.ACCEPTED_DEFAULT, TextEntryState.getState());
    }

    @Test
    public void testAcceptedDefault_nullTypedWord() {
        TextEntryState.reset();
        TextEntryState.acceptedDefault(null, "test");
        // Should not throw and state should remain START
        assertEquals("acceptedDefault with null should remain in previous state",
                TextEntryState.State.START, TextEntryState.getState());
    }

    @Test
    public void testTypedCharacter_acceptedDefaultToSpaceAfterAccepted() {
        TextEntryState.reset();
        TextEntryState.acceptedDefault("test", "test");
        TextEntryState.typedCharacter(' ', false);
        assertEquals("Typing space after ACCEPTED_DEFAULT should go to SPACE_AFTER_ACCEPTED",
                TextEntryState.State.SPACE_AFTER_ACCEPTED, TextEntryState.getState());
    }

    @Test
    public void testTypedCharacter_acceptedDefaultToPunctuationAfterAccepted() {
        TextEntryState.reset();
        TextEntryState.acceptedDefault("test", "test");
        TextEntryState.typedCharacter('.', true);
        assertEquals("Typing punctuation after ACCEPTED_DEFAULT should go to PUNCTUATION_AFTER_ACCEPTED",
                TextEntryState.State.PUNCTUATION_AFTER_ACCEPTED, TextEntryState.getState());
    }

    @Test
    public void testTypedCharacter_acceptedDefaultToInWord() {
        TextEntryState.reset();
        TextEntryState.acceptedDefault("test", "test");
        TextEntryState.typedCharacter('a', false);
        assertEquals("Typing character after ACCEPTED_DEFAULT should go to IN_WORD",
                TextEntryState.State.IN_WORD, TextEntryState.getState());
    }

    // ==================== Backspace Tests ====================

    @Test
    public void testBackspace_acceptedDefaultToUndoCommit() {
        TextEntryState.reset();
        TextEntryState.acceptedDefault("test", "test");
        TextEntryState.backspace();
        assertEquals("Backspace from ACCEPTED_DEFAULT should go to UNDO_COMMIT",
                TextEntryState.State.UNDO_COMMIT, TextEntryState.getState());
    }

    @Test
    public void testBackspace_undoCommitToInWord() {
        TextEntryState.reset();
        TextEntryState.acceptedDefault("test", "test");
        TextEntryState.backspace(); // UNDO_COMMIT
        TextEntryState.backspace(); // IN_WORD
        assertEquals("Backspace from UNDO_COMMIT should go to IN_WORD",
                TextEntryState.State.IN_WORD, TextEntryState.getState());
    }

    @Test
    public void testBackspace_otherStatesNoChange() {
        TextEntryState.reset();
        TextEntryState.typedCharacter('a', false); // IN_WORD
        TextEntryState.State stateBefore = TextEntryState.getState();
        TextEntryState.backspace();
        // Backspace in IN_WORD doesn't change state (only ACCEPTED_DEFAULT and UNDO_COMMIT have special handling)
        assertEquals("Backspace from other states should not change specific state",
                stateBefore, TextEntryState.getState());
    }

    // ==================== Undo Commit Tests ====================

    @Test
    public void testTypedCharacter_undoCommitToAcceptedDefault_withSpace() {
        TextEntryState.reset();
        TextEntryState.acceptedDefault("test", "test");
        TextEntryState.backspace(); // UNDO_COMMIT
        TextEntryState.typedCharacter(' ', false);
        assertEquals("Typing space in UNDO_COMMIT should go to ACCEPTED_DEFAULT",
                TextEntryState.State.ACCEPTED_DEFAULT, TextEntryState.getState());
    }

    @Test
    public void testTypedCharacter_undoCommitToAcceptedDefault_withSeparator() {
        TextEntryState.reset();
        TextEntryState.acceptedDefault("test", "test");
        TextEntryState.backspace(); // UNDO_COMMIT
        TextEntryState.typedCharacter('.', true);
        assertEquals("Typing separator in UNDO_COMMIT should go to ACCEPTED_DEFAULT",
                TextEntryState.State.ACCEPTED_DEFAULT, TextEntryState.getState());
    }

    @Test
    public void testTypedCharacter_undoCommitToInWord() {
        TextEntryState.reset();
        TextEntryState.acceptedDefault("test", "test");
        TextEntryState.backspace(); // UNDO_COMMIT
        TextEntryState.typedCharacter('a', false);
        assertEquals("Typing character in UNDO_COMMIT should go to IN_WORD",
                TextEntryState.State.IN_WORD, TextEntryState.getState());
    }

    // ==================== Picked Suggestion Tests ====================

    @Test
    public void testAcceptedTyped_setsPickedSuggestion() {
        TextEntryState.reset();
        TextEntryState.acceptedTyped("test");
        assertEquals("acceptedTyped should set state to PICKED_SUGGESTION",
                TextEntryState.State.PICKED_SUGGESTION, TextEntryState.getState());
    }

    @Test
    public void testTypedCharacter_pickedSuggestionToSpaceAfterPicked() {
        TextEntryState.reset();
        TextEntryState.acceptedTyped("test");
        TextEntryState.typedCharacter(' ', false);
        assertEquals("Typing space after PICKED_SUGGESTION should go to SPACE_AFTER_PICKED",
                TextEntryState.State.SPACE_AFTER_PICKED, TextEntryState.getState());
    }

    @Test
    public void testTypedCharacter_pickedSuggestionToPunctuationAfterAccepted() {
        TextEntryState.reset();
        TextEntryState.acceptedTyped("test");
        TextEntryState.typedCharacter('.', true);
        assertEquals("Typing punctuation after PICKED_SUGGESTION should go to PUNCTUATION_AFTER_ACCEPTED",
                TextEntryState.State.PUNCTUATION_AFTER_ACCEPTED, TextEntryState.getState());
    }

    @Test
    public void testTypedCharacter_pickedSuggestionToInWord() {
        TextEntryState.reset();
        TextEntryState.acceptedTyped("test");
        TextEntryState.typedCharacter('a', false);
        assertEquals("Typing character after PICKED_SUGGESTION should go to IN_WORD",
                TextEntryState.State.IN_WORD, TextEntryState.getState());
    }

    // ==================== Space After Picked Tests ====================

    @Test
    public void testTypedCharacter_spaceAfterPickedToSpaceAfterAccepted() {
        TextEntryState.reset();
        TextEntryState.acceptedTyped("test");
        TextEntryState.typedCharacter(' ', false); // SPACE_AFTER_PICKED
        TextEntryState.typedCharacter(' ', false);
        assertEquals("Typing space in SPACE_AFTER_PICKED should go to SPACE_AFTER_ACCEPTED",
                TextEntryState.State.SPACE_AFTER_ACCEPTED, TextEntryState.getState());
    }

    @Test
    public void testTypedCharacter_spaceAfterPickedToInWord() {
        TextEntryState.reset();
        TextEntryState.acceptedTyped("test");
        TextEntryState.typedCharacter(' ', false); // SPACE_AFTER_PICKED
        TextEntryState.typedCharacter('a', false);
        assertEquals("Typing character in SPACE_AFTER_PICKED should go to IN_WORD",
                TextEntryState.State.IN_WORD, TextEntryState.getState());
    }

    // ==================== Punctuation After Accepted Tests ====================

    @Test
    public void testTypedCharacter_punctuationAfterAcceptedToInWord() {
        TextEntryState.reset();
        TextEntryState.acceptedDefault("test", "test");
        TextEntryState.typedCharacter('.', true); // PUNCTUATION_AFTER_ACCEPTED
        TextEntryState.typedCharacter('a', false);
        assertEquals("Typing character after punctuation should go to IN_WORD",
                TextEntryState.State.IN_WORD, TextEntryState.getState());
    }

    @Test
    public void testTypedCharacter_punctuationAfterAcceptedToStart_withSpace() {
        TextEntryState.reset();
        TextEntryState.acceptedDefault("test", "test");
        TextEntryState.typedCharacter('.', true); // PUNCTUATION_AFTER_ACCEPTED
        TextEntryState.typedCharacter(' ', false);
        assertEquals("Typing space after punctuation should go to START",
                TextEntryState.State.START, TextEntryState.getState());
    }

    // ==================== Space After Accepted Tests ====================

    @Test
    public void testTypedCharacter_spaceAfterAcceptedToInWord() {
        TextEntryState.reset();
        TextEntryState.acceptedDefault("test", "test");
        TextEntryState.typedCharacter(' ', false); // SPACE_AFTER_ACCEPTED
        TextEntryState.typedCharacter('a', false);
        assertEquals("Typing character after space should go to IN_WORD",
                TextEntryState.State.IN_WORD, TextEntryState.getState());
    }

    @Test
    public void testTypedCharacter_spaceAfterAcceptedToStart() {
        TextEntryState.reset();
        TextEntryState.acceptedDefault("test", "test");
        TextEntryState.typedCharacter(' ', false); // SPACE_AFTER_ACCEPTED
        TextEntryState.typedCharacter(' ', false);
        assertEquals("Typing space after space should go to START",
                TextEntryState.State.START, TextEntryState.getState());
    }

    // ==================== Correcting Tests ====================

    @Test
    public void testSelectedForCorrection() {
        TextEntryState.reset();
        TextEntryState.selectedForCorrection();
        assertEquals("selectedForCorrection should set state to CORRECTING",
                TextEntryState.State.CORRECTING, TextEntryState.getState());
    }

    @Test
    public void testIsCorrecting_whenCorrecting() {
        TextEntryState.reset();
        TextEntryState.selectedForCorrection();
        assertTrue("isCorrecting should be true when CORRECTING", TextEntryState.isCorrecting());
    }

    @Test
    public void testIsCorrecting_whenNotCorrecting() {
        TextEntryState.reset();
        TextEntryState.typedCharacter('a', false);
        assertFalse("isCorrecting should be false when not CORRECTING", TextEntryState.isCorrecting());
    }

    @Test
    public void testTypedCharacter_correctingToStart() {
        TextEntryState.reset();
        TextEntryState.selectedForCorrection();
        TextEntryState.typedCharacter('a', false);
        assertEquals("Typing in CORRECTING should go to START",
                TextEntryState.State.START, TextEntryState.getState());
    }

    // ==================== Picked Correction Tests ====================

    @Test
    public void testAcceptedSuggestion_whileCorrecting() {
        TextEntryState.reset();
        TextEntryState.selectedForCorrection();
        TextEntryState.acceptedSuggestion("tset", "test");
        assertEquals("Accepting suggestion while CORRECTING should go to PICKED_CORRECTION",
                TextEntryState.State.PICKED_CORRECTION, TextEntryState.getState());
    }

    @Test
    public void testIsCorrecting_whenPickedCorrection() {
        TextEntryState.reset();
        TextEntryState.selectedForCorrection();
        TextEntryState.acceptedSuggestion("tset", "test");
        assertTrue("isCorrecting should be true when PICKED_CORRECTION", TextEntryState.isCorrecting());
    }

    @Test
    public void testTypedCharacter_pickedCorrectionToSpaceAfterPicked() {
        TextEntryState.reset();
        TextEntryState.selectedForCorrection();
        TextEntryState.acceptedSuggestion("tset", "test"); // PICKED_CORRECTION
        TextEntryState.typedCharacter(' ', false);
        assertEquals("Typing space after PICKED_CORRECTION should go to SPACE_AFTER_PICKED",
                TextEntryState.State.SPACE_AFTER_PICKED, TextEntryState.getState());
    }

    // ==================== Accepted Suggestion Tests ====================

    @Test
    public void testAcceptedSuggestion_normalCase() {
        TextEntryState.reset();
        TextEntryState.typedCharacter('t', false);
        TextEntryState.acceptedSuggestion("te", "test");
        assertEquals("acceptedSuggestion should set state to PICKED_SUGGESTION",
                TextEntryState.State.PICKED_SUGGESTION, TextEntryState.getState());
    }

    @Test
    public void testAcceptedSuggestion_sameWord() {
        TextEntryState.reset();
        TextEntryState.acceptedSuggestion("test", "test");
        assertEquals("acceptedSuggestion with same words should go to PICKED_SUGGESTION",
                TextEntryState.State.PICKED_SUGGESTION, TextEntryState.getState());
    }

    // ==================== Back To Accepted Default Tests ====================

    @Test
    public void testBackToAcceptedDefault_fromSpaceAfterAccepted() {
        TextEntryState.reset();
        TextEntryState.acceptedDefault("test", "test");
        TextEntryState.typedCharacter(' ', false); // SPACE_AFTER_ACCEPTED
        TextEntryState.backToAcceptedDefault("test");
        assertEquals("backToAcceptedDefault from SPACE_AFTER_ACCEPTED should go to ACCEPTED_DEFAULT",
                TextEntryState.State.ACCEPTED_DEFAULT, TextEntryState.getState());
    }

    @Test
    public void testBackToAcceptedDefault_fromPunctuationAfterAccepted() {
        TextEntryState.reset();
        TextEntryState.acceptedDefault("test", "test");
        TextEntryState.typedCharacter('.', true); // PUNCTUATION_AFTER_ACCEPTED
        TextEntryState.backToAcceptedDefault("test");
        assertEquals("backToAcceptedDefault from PUNCTUATION_AFTER_ACCEPTED should go to ACCEPTED_DEFAULT",
                TextEntryState.State.ACCEPTED_DEFAULT, TextEntryState.getState());
    }

    @Test
    public void testBackToAcceptedDefault_fromInWord() {
        TextEntryState.reset();
        TextEntryState.acceptedDefault("test", "test");
        TextEntryState.typedCharacter('a', false); // IN_WORD
        TextEntryState.backToAcceptedDefault("test");
        assertEquals("backToAcceptedDefault from IN_WORD should go to ACCEPTED_DEFAULT",
                TextEntryState.State.ACCEPTED_DEFAULT, TextEntryState.getState());
    }

    @Test
    public void testBackToAcceptedDefault_nullTypedWord() {
        TextEntryState.reset();
        TextEntryState.acceptedDefault("test", "test");
        TextEntryState.typedCharacter(' ', false); // SPACE_AFTER_ACCEPTED
        TextEntryState.State stateBefore = TextEntryState.getState();
        TextEntryState.backToAcceptedDefault(null);
        assertEquals("backToAcceptedDefault with null should not change state",
                stateBefore, TextEntryState.getState());
    }

    // ==================== Manual Typed Tests ====================

    @Test
    public void testManualTyped_setsStart() {
        TextEntryState.reset();
        TextEntryState.typedCharacter('a', false);
        TextEntryState.manualTyped("test");
        assertEquals("manualTyped should set state to START",
                TextEntryState.State.START, TextEntryState.getState());
    }

    // ==================== Complex Scenario Tests ====================

    @Test
    public void testComplexScenario_typeWordAcceptContinue() {
        TextEntryState.reset();

        // Type "hel"
        TextEntryState.typedCharacter('h', false);
        TextEntryState.typedCharacter('e', false);
        TextEntryState.typedCharacter('l', false);
        assertEquals(TextEntryState.State.IN_WORD, TextEntryState.getState());

        // Accept suggestion "hello"
        TextEntryState.acceptedDefault("hel", "hello");
        assertEquals(TextEntryState.State.ACCEPTED_DEFAULT, TextEntryState.getState());

        // Type space
        TextEntryState.typedCharacter(' ', false);
        assertEquals(TextEntryState.State.SPACE_AFTER_ACCEPTED, TextEntryState.getState());

        // Start new word
        TextEntryState.typedCharacter('w', false);
        assertEquals(TextEntryState.State.IN_WORD, TextEntryState.getState());
    }

    @Test
    public void testComplexScenario_typeWordUndoRetype() {
        TextEntryState.reset();

        // Type and accept
        TextEntryState.typedCharacter('h', false);
        TextEntryState.acceptedDefault("h", "hello");
        assertEquals(TextEntryState.State.ACCEPTED_DEFAULT, TextEntryState.getState());

        // Backspace to undo
        TextEntryState.backspace();
        assertEquals(TextEntryState.State.UNDO_COMMIT, TextEntryState.getState());

        // Continue typing
        TextEntryState.typedCharacter('i', false);
        assertEquals(TextEntryState.State.IN_WORD, TextEntryState.getState());
    }

    @Test
    public void testComplexScenario_correctionFlow() {
        TextEntryState.reset();

        // User selects word for correction
        TextEntryState.selectedForCorrection();
        assertEquals(TextEntryState.State.CORRECTING, TextEntryState.getState());
        assertTrue(TextEntryState.isCorrecting());

        // User picks a correction
        TextEntryState.acceptedSuggestion("teh", "the");
        assertEquals(TextEntryState.State.PICKED_CORRECTION, TextEntryState.getState());
        assertTrue(TextEntryState.isCorrecting());

        // User types space after correction
        TextEntryState.typedCharacter(' ', false);
        assertEquals(TextEntryState.State.SPACE_AFTER_PICKED, TextEntryState.getState());
        assertFalse(TextEntryState.isCorrecting());
    }

    // ==================== State Enum Tests ====================

    @Test
    public void testAllStatesExist() {
        // Verify all expected states exist
        assertNotNull(TextEntryState.State.UNKNOWN);
        assertNotNull(TextEntryState.State.START);
        assertNotNull(TextEntryState.State.IN_WORD);
        assertNotNull(TextEntryState.State.ACCEPTED_DEFAULT);
        assertNotNull(TextEntryState.State.PICKED_SUGGESTION);
        assertNotNull(TextEntryState.State.PUNCTUATION_AFTER_WORD);
        assertNotNull(TextEntryState.State.PUNCTUATION_AFTER_ACCEPTED);
        assertNotNull(TextEntryState.State.SPACE_AFTER_ACCEPTED);
        assertNotNull(TextEntryState.State.SPACE_AFTER_PICKED);
        assertNotNull(TextEntryState.State.UNDO_COMMIT);
        assertNotNull(TextEntryState.State.CORRECTING);
        assertNotNull(TextEntryState.State.PICKED_CORRECTION);
    }
}
