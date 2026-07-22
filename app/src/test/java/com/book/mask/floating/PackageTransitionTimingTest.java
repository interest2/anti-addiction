package com.book.mask.floating;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PackageTransitionTimingTest {
    @Test
    public void defaultEarlyReturnPauseEndsAtAnimationDurationPlusBuffer() {
        assertEquals(800, PackageTransitionTiming.getEarlyReturnPauseDuration(1000, 300));
    }

    @Test
    public void earlyReturnPauseNeverBecomesNegative() {
        assertEquals(0, PackageTransitionTiming.getEarlyReturnPauseDuration(100, 300));
    }

    @Test
    public void directReentryIncludesDeadlineAndRejectsLaterReturn() {
        assertTrue(PackageTransitionTiming.isWithinDirectReentryWindow(5000, 6300, 1000));
        assertFalse(PackageTransitionTiming.isWithinDirectReentryWindow(5000, 6301, 1000));
    }
}
