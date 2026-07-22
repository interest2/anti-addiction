package com.book.mask.floating;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PackageTransitionTimingTest {
    @Test
    public void earlyReturnPauseEndsAtAnimationDurationPlusBuffer() {
        assertEquals(800, PackageTransitionTiming.getEarlyReturnPauseDuration());
    }

    @Test
    public void directReentryIncludesDeadlineAndRejectsLaterReturn() {
        assertTrue(PackageTransitionTiming.isWithinDirectReentryWindow(5000, 6300));
        assertFalse(PackageTransitionTiming.isWithinDirectReentryWindow(5000, 6301));
    }
}
