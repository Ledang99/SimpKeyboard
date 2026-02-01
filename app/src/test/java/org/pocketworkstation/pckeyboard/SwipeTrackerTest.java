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
 * Unit tests for {@link SwipeTracker} and its inner class {@link SwipeTracker.EventRingBuffer}.
 * Tests the swipe velocity calculation and event buffering functionality.
 */
public class SwipeTrackerTest {

    // ==================== EventRingBuffer Tests ====================

    /**
     * Tests for the EventRingBuffer inner class.
     */
    public static class EventRingBufferTest {

        private SwipeTracker.EventRingBuffer mBuffer;

        @Before
        public void setUp() {
            mBuffer = new SwipeTracker.EventRingBuffer(4);
        }

        @Test
        public void testInitialState_empty() {
            assertEquals("Initial size should be 0", 0, mBuffer.size());
        }

        @Test
        public void testAdd_singleEvent() {
            mBuffer.add(10.0f, 20.0f, 1000L);

            assertEquals("Size should be 1", 1, mBuffer.size());
            assertEquals("X should be 10", 10.0f, mBuffer.getX(0), 0.001f);
            assertEquals("Y should be 20", 20.0f, mBuffer.getY(0), 0.001f);
            assertEquals("Time should be 1000", 1000L, mBuffer.getTime(0));
        }

        @Test
        public void testAdd_multipleEvents() {
            mBuffer.add(10.0f, 20.0f, 1000L);
            mBuffer.add(30.0f, 40.0f, 2000L);
            mBuffer.add(50.0f, 60.0f, 3000L);

            assertEquals("Size should be 3", 3, mBuffer.size());

            // Position 0 is oldest
            assertEquals("X at 0 should be 10", 10.0f, mBuffer.getX(0), 0.001f);
            assertEquals("X at 1 should be 30", 30.0f, mBuffer.getX(1), 0.001f);
            assertEquals("X at 2 should be 50", 50.0f, mBuffer.getX(2), 0.001f);
        }

        @Test
        public void testAdd_overflow() {
            // Buffer size is 4
            mBuffer.add(10.0f, 10.0f, 1000L);
            mBuffer.add(20.0f, 20.0f, 2000L);
            mBuffer.add(30.0f, 30.0f, 3000L);
            mBuffer.add(40.0f, 40.0f, 4000L);
            mBuffer.add(50.0f, 50.0f, 5000L); // This should push out the first

            assertEquals("Size should remain 4", 4, mBuffer.size());

            // Oldest should now be 20 (second original entry)
            assertEquals("Oldest X should be 20", 20.0f, mBuffer.getX(0), 0.001f);
            assertEquals("Newest X should be 50", 50.0f, mBuffer.getX(3), 0.001f);
        }

        @Test
        public void testClear() {
            mBuffer.add(10.0f, 20.0f, 1000L);
            mBuffer.add(30.0f, 40.0f, 2000L);

            mBuffer.clear();

            assertEquals("Size should be 0 after clear", 0, mBuffer.size());
        }

        @Test
        public void testDropOldest() {
            mBuffer.add(10.0f, 10.0f, 1000L);
            mBuffer.add(20.0f, 20.0f, 2000L);
            mBuffer.add(30.0f, 30.0f, 3000L);

            mBuffer.dropOldest();

            assertEquals("Size should be 2 after drop", 2, mBuffer.size());
            assertEquals("Oldest X should now be 20", 20.0f, mBuffer.getX(0), 0.001f);
        }

        @Test
        public void testDropOldest_multiple() {
            mBuffer.add(10.0f, 10.0f, 1000L);
            mBuffer.add(20.0f, 20.0f, 2000L);
            mBuffer.add(30.0f, 30.0f, 3000L);

            mBuffer.dropOldest();
            mBuffer.dropOldest();

            assertEquals("Size should be 1 after two drops", 1, mBuffer.size());
            assertEquals("Remaining X should be 30", 30.0f, mBuffer.getX(0), 0.001f);
        }

        @Test
        public void testCircularBuffer_wrapAround() {
            // Fill buffer
            mBuffer.add(10.0f, 10.0f, 1000L);
            mBuffer.add(20.0f, 20.0f, 2000L);
            mBuffer.add(30.0f, 30.0f, 3000L);
            mBuffer.add(40.0f, 40.0f, 4000L);

            // Add more to wrap
            mBuffer.add(50.0f, 50.0f, 5000L);
            mBuffer.add(60.0f, 60.0f, 6000L);

            assertEquals("Size should be 4", 4, mBuffer.size());
            assertEquals("Oldest should be 30", 30.0f, mBuffer.getX(0), 0.001f);
            assertEquals("Newest should be 60", 60.0f, mBuffer.getX(3), 0.001f);
        }

        @Test
        public void testNegativeCoordinates() {
            mBuffer.add(-10.0f, -20.0f, 1000L);

            assertEquals("Negative X should be stored", -10.0f, mBuffer.getX(0), 0.001f);
            assertEquals("Negative Y should be stored", -20.0f, mBuffer.getY(0), 0.001f);
        }

        @Test
        public void testZeroCoordinates() {
            mBuffer.add(0.0f, 0.0f, 0L);

            assertEquals("Zero X should be stored", 0.0f, mBuffer.getX(0), 0.001f);
            assertEquals("Zero Y should be stored", 0.0f, mBuffer.getY(0), 0.001f);
            assertEquals("Zero time should be stored", 0L, mBuffer.getTime(0));
        }

        @Test
        public void testLargeValues() {
            mBuffer.add(Float.MAX_VALUE, Float.MIN_VALUE, Long.MAX_VALUE);

            assertEquals("Max float X should be stored", Float.MAX_VALUE, mBuffer.getX(0), 0.001f);
            assertEquals("Min float Y should be stored", Float.MIN_VALUE, mBuffer.getY(0), 0.001f);
            assertEquals("Max long time should be stored", Long.MAX_VALUE, mBuffer.getTime(0));
        }
    }

    // ==================== SwipeTracker Velocity Tests ====================

    private SwipeTracker mSwipeTracker;

    @Before
    public void setUp() {
        mSwipeTracker = new SwipeTracker();
    }

    @Test
    public void testInitialVelocity_zero() {
        mSwipeTracker.computeCurrentVelocity(1000);

        assertEquals("Initial X velocity should be 0", 0.0f, mSwipeTracker.getXVelocity(), 0.001f);
        assertEquals("Initial Y velocity should be 0", 0.0f, mSwipeTracker.getYVelocity(), 0.001f);
    }

    @Test
    public void testComputeVelocity_horizontalSwipe() {
        // Simulate horizontal swipe: 100 pixels in 100ms = 1000 pixels/second
        mSwipeTracker.mBuffer.add(0.0f, 0.0f, 0L);
        mSwipeTracker.mBuffer.add(100.0f, 0.0f, 100L);

        mSwipeTracker.computeCurrentVelocity(1000);

        assertTrue("X velocity should be positive for right swipe", mSwipeTracker.getXVelocity() > 0);
        assertEquals("Y velocity should be ~0 for horizontal swipe", 0.0f, mSwipeTracker.getYVelocity(), 0.001f);
    }

    @Test
    public void testComputeVelocity_verticalSwipe() {
        // Simulate vertical swipe
        mSwipeTracker.mBuffer.add(0.0f, 0.0f, 0L);
        mSwipeTracker.mBuffer.add(0.0f, 100.0f, 100L);

        mSwipeTracker.computeCurrentVelocity(1000);

        assertEquals("X velocity should be ~0 for vertical swipe", 0.0f, mSwipeTracker.getXVelocity(), 0.001f);
        assertTrue("Y velocity should be positive for down swipe", mSwipeTracker.getYVelocity() > 0);
    }

    @Test
    public void testComputeVelocity_diagonalSwipe() {
        mSwipeTracker.mBuffer.add(0.0f, 0.0f, 0L);
        mSwipeTracker.mBuffer.add(100.0f, 100.0f, 100L);

        mSwipeTracker.computeCurrentVelocity(1000);

        assertTrue("X velocity should be positive", mSwipeTracker.getXVelocity() > 0);
        assertTrue("Y velocity should be positive", mSwipeTracker.getYVelocity() > 0);
    }

    @Test
    public void testComputeVelocity_negativeDirection() {
        mSwipeTracker.mBuffer.add(100.0f, 100.0f, 0L);
        mSwipeTracker.mBuffer.add(0.0f, 0.0f, 100L);

        mSwipeTracker.computeCurrentVelocity(1000);

        assertTrue("X velocity should be negative for left swipe", mSwipeTracker.getXVelocity() < 0);
        assertTrue("Y velocity should be negative for up swipe", mSwipeTracker.getYVelocity() < 0);
    }

    @Test
    public void testComputeVelocity_maxVelocity() {
        // Very fast swipe
        mSwipeTracker.mBuffer.add(0.0f, 0.0f, 0L);
        mSwipeTracker.mBuffer.add(10000.0f, 0.0f, 1L);

        float maxVelocity = 5000.0f;
        mSwipeTracker.computeCurrentVelocity(1000, maxVelocity);

        assertTrue("X velocity should be capped at max", mSwipeTracker.getXVelocity() <= maxVelocity);
    }

    @Test
    public void testComputeVelocity_maxVelocityNegative() {
        // Very fast swipe in negative direction
        mSwipeTracker.mBuffer.add(10000.0f, 0.0f, 0L);
        mSwipeTracker.mBuffer.add(0.0f, 0.0f, 1L);

        float maxVelocity = 5000.0f;
        mSwipeTracker.computeCurrentVelocity(1000, maxVelocity);

        assertTrue("X velocity should be capped at -max", mSwipeTracker.getXVelocity() >= -maxVelocity);
    }

    @Test
    public void testComputeVelocity_multiplePoints() {
        // Simulate a curve with multiple points
        mSwipeTracker.mBuffer.add(0.0f, 0.0f, 0L);
        mSwipeTracker.mBuffer.add(25.0f, 10.0f, 25L);
        mSwipeTracker.mBuffer.add(50.0f, 25.0f, 50L);
        mSwipeTracker.mBuffer.add(100.0f, 50.0f, 100L);

        mSwipeTracker.computeCurrentVelocity(1000);

        assertTrue("X velocity should be positive", mSwipeTracker.getXVelocity() > 0);
        assertTrue("Y velocity should be positive", mSwipeTracker.getYVelocity() > 0);
    }

    @Test
    public void testComputeVelocity_stationaryTouch() {
        // No movement
        mSwipeTracker.mBuffer.add(50.0f, 50.0f, 0L);
        mSwipeTracker.mBuffer.add(50.0f, 50.0f, 100L);

        mSwipeTracker.computeCurrentVelocity(1000);

        assertEquals("X velocity should be 0 for stationary", 0.0f, mSwipeTracker.getXVelocity(), 0.001f);
        assertEquals("Y velocity should be 0 for stationary", 0.0f, mSwipeTracker.getYVelocity(), 0.001f);
    }

    @Test
    public void testComputeVelocity_differentUnits() {
        mSwipeTracker.mBuffer.add(0.0f, 0.0f, 0L);
        mSwipeTracker.mBuffer.add(100.0f, 0.0f, 100L);

        // Units of 1 (pixels per millisecond)
        mSwipeTracker.computeCurrentVelocity(1);
        float velocityPxPerMs = mSwipeTracker.getXVelocity();

        // Reset and compute with units of 1000 (pixels per second)
        mSwipeTracker.mBuffer.clear();
        mSwipeTracker.mBuffer.add(0.0f, 0.0f, 0L);
        mSwipeTracker.mBuffer.add(100.0f, 0.0f, 100L);
        mSwipeTracker.computeCurrentVelocity(1000);
        float velocityPxPerSec = mSwipeTracker.getXVelocity();

        // Velocity in px/s should be ~1000x velocity in px/ms
        assertEquals("Velocities should scale with units", velocityPxPerMs * 1000, velocityPxPerSec, 1.0f);
    }

    @Test
    public void testBufferAccess() {
        assertNotNull("Buffer should not be null", mSwipeTracker.mBuffer);
    }
}
