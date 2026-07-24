package com.book.mask.floating;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PackageTransitionDecisionTest {
    @Test
    public void targetPackageTriggersPauseAndRecheck() {
        assertEquals(
                PackageTransitionDecision.Action.PAUSE_AND_RECHECK,
                PackageTransitionDecision.decide("target.package", "target.package")
        );
    }

    @Test
    public void otherPackageResumesDetection() {
        assertEquals(
                PackageTransitionDecision.Action.RESUME_DETECTION,
                PackageTransitionDecision.decide("target.package", "other.package")
        );
    }

    @Test
    public void unknownPackageAlsoResumesDetection() {
        assertEquals(
                PackageTransitionDecision.Action.RESUME_DETECTION,
                PackageTransitionDecision.decide("target.package", "")
        );
    }
}
