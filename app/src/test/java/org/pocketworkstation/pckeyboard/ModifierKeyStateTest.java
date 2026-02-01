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
 * Unit tests for {@link ModifierKeyState}.
 * Tests the modifier key state machine that tracks pressing, releasing,
 * and chording (holding while pressing another key) states.
 */
public class ModifierKeyStateTest {

    private ModifierKeyState mModifierKeyState;

    @Before
    public void setUp() {
        mModifierKeyState = new ModifierKeyState();
    }

    // ==================== Initial State Tests ====================

    @Test
    public void testInitialState_notChording() {
        assertFalse("Initial state should not be chording", mModifierKeyState.isChording());
    }

    // ==================== Press and Release Tests ====================

    @Test
    public void testOnPress_notChording() {
        mModifierKeyState.onPress();
        assertFalse("After press, should not be chording yet", mModifierKeyState.isChording());
    }

    @Test
    public void testOnRelease_afterPress_notChording() {
        mModifierKeyState.onPress();
        mModifierKeyState.onRelease();
        assertFalse("After release, should not be chording", mModifierKeyState.isChording());
    }

    @Test
    public void testOnRelease_withoutPress() {
        mModifierKeyState.onRelease();
        assertFalse("Release without press should not be chording", mModifierKeyState.isChording());
    }

    // ==================== Chording Tests ====================

    @Test
    public void testChording_pressOtherKeyWhilePressing() {
        mModifierKeyState.onPress();
        mModifierKeyState.onOtherKeyPressed();
        assertTrue("Should be chording when other key pressed while modifier is pressed",
                mModifierKeyState.isChording());
    }

    @Test
    public void testChording_remainsAfterMultipleOtherKeys() {
        mModifierKeyState.onPress();
        mModifierKeyState.onOtherKeyPressed();
        mModifierKeyState.onOtherKeyPressed();
        mModifierKeyState.onOtherKeyPressed();
        assertTrue("Should remain chording after multiple other keys", mModifierKeyState.isChording());
    }

    @Test
    public void testChording_clearedByRelease() {
        mModifierKeyState.onPress();
        mModifierKeyState.onOtherKeyPressed();
        assertTrue("Should be chording", mModifierKeyState.isChording());

        mModifierKeyState.onRelease();
        assertFalse("Chording should be cleared after release", mModifierKeyState.isChording());
    }

    @Test
    public void testOtherKeyPressed_whileReleasing() {
        mModifierKeyState.onOtherKeyPressed();
        assertFalse("Other key pressed while releasing should not chord", mModifierKeyState.isChording());
    }

    @Test
    public void testOtherKeyPressed_afterRelease() {
        mModifierKeyState.onPress();
        mModifierKeyState.onRelease();
        mModifierKeyState.onOtherKeyPressed();
        assertFalse("Other key pressed after release should not chord", mModifierKeyState.isChording());
    }

    // ==================== State Cycle Tests ====================

    @Test
    public void testFullCycle_pressChordRelease() {
        assertFalse("Initial: not chording", mModifierKeyState.isChording());

        mModifierKeyState.onPress();
        assertFalse("After press: not chording", mModifierKeyState.isChording());

        mModifierKeyState.onOtherKeyPressed();
        assertTrue("After other key: chording", mModifierKeyState.isChording());

        mModifierKeyState.onRelease();
        assertFalse("After release: not chording", mModifierKeyState.isChording());
    }

    @Test
    public void testMultipleCycles() {
        // First cycle
        mModifierKeyState.onPress();
        mModifierKeyState.onOtherKeyPressed();
        assertTrue(mModifierKeyState.isChording());
        mModifierKeyState.onRelease();
        assertFalse(mModifierKeyState.isChording());

        // Second cycle
        mModifierKeyState.onPress();
        assertFalse(mModifierKeyState.isChording());
        mModifierKeyState.onOtherKeyPressed();
        assertTrue(mModifierKeyState.isChording());
        mModifierKeyState.onRelease();
        assertFalse(mModifierKeyState.isChording());

        // Third cycle without chording
        mModifierKeyState.onPress();
        mModifierKeyState.onRelease();
        assertFalse(mModifierKeyState.isChording());
    }

    @Test
    public void testPressWithoutChord() {
        mModifierKeyState.onPress();
        mModifierKeyState.onRelease();
        assertFalse("Simple press/release without chord should not be chording",
                mModifierKeyState.isChording());
    }

    // ==================== ToString Tests ====================

    @Test
    public void testToString_containsStateName() {
        String str = mModifierKeyState.toString();
        assertNotNull("toString should not be null", str);
        assertTrue("toString should contain 'ModifierKeyState'", str.contains("ModifierKeyState"));
    }

    @Test
    public void testToString_differentStates() {
        String initialStr = mModifierKeyState.toString();

        mModifierKeyState.onPress();
        String pressedStr = mModifierKeyState.toString();

        mModifierKeyState.onOtherKeyPressed();
        String chordingStr = mModifierKeyState.toString();

        // Each state should have a different string representation
        assertFalse("Initial and pressed should differ", initialStr.equals(pressedStr));
        assertFalse("Pressed and chording should differ", pressedStr.equals(chordingStr));
    }

    // ==================== Edge Case Tests ====================

    @Test
    public void testMultiplePress_withoutRelease() {
        mModifierKeyState.onPress();
        mModifierKeyState.onPress();
        mModifierKeyState.onPress();
        assertFalse("Multiple presses should not chord", mModifierKeyState.isChording());
    }

    @Test
    public void testMultipleRelease_withoutPress() {
        mModifierKeyState.onRelease();
        mModifierKeyState.onRelease();
        mModifierKeyState.onRelease();
        assertFalse("Multiple releases should not chord", mModifierKeyState.isChording());
    }

    @Test
    public void testRapidPressReleaseCycles() {
        for (int i = 0; i < 100; i++) {
            mModifierKeyState.onPress();
            if (i % 2 == 0) {
                mModifierKeyState.onOtherKeyPressed();
                assertTrue("Should chord on even iterations", mModifierKeyState.isChording());
            }
            mModifierKeyState.onRelease();
            assertFalse("Should not chord after release", mModifierKeyState.isChording());
        }
    }
}
