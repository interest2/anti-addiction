package com.book.mask.floating;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WindowSuspensionStateTest {
    @Test
    public void firstReasonAppliesHideAndLastReasonRestores() {
        WindowSuspensionState state = new WindowSuspensionState();

        assertTrue(state.suspend(WindowSuspensionState.Reason.PAGE_TRANSITION));
        assertFalse(state.suspend(WindowSuspensionState.Reason.SYSTEM_UI));

        assertEquals(
                WindowSuspensionState.ResumeAction.KEEP_HIDDEN,
                state.resume(WindowSuspensionState.Reason.PAGE_TRANSITION)
        );
        assertEquals(
                WindowSuspensionState.ResumeAction.RESTORE,
                state.resume(WindowSuspensionState.Reason.SYSTEM_UI)
        );
        assertFalse(state.isSuspended());
    }

    @Test
    public void duplicateReasonDoesNotApplyHideTwice() {
        WindowSuspensionState state = new WindowSuspensionState();

        assertTrue(state.suspend(WindowSuspensionState.Reason.PACKAGE_TRANSITION));
        assertFalse(state.suspend(WindowSuspensionState.Reason.PACKAGE_TRANSITION));
        assertEquals(
                WindowSuspensionState.ResumeAction.RESTORE,
                state.resume(WindowSuspensionState.Reason.PACKAGE_TRANSITION)
        );
        assertEquals(
                WindowSuspensionState.ResumeAction.NONE,
                state.resume(WindowSuspensionState.Reason.PACKAGE_TRANSITION)
        );
    }

    @Test
    public void pageReasonCanTransferToPackageReasonWithoutRestoring() {
        WindowSuspensionState state = new WindowSuspensionState();

        assertTrue(state.suspend(WindowSuspensionState.Reason.PAGE_TRANSITION));
        assertFalse(state.suspend(WindowSuspensionState.Reason.PACKAGE_TRANSITION));
        assertEquals(
                WindowSuspensionState.ResumeAction.KEEP_HIDDEN,
                state.resume(WindowSuspensionState.Reason.PAGE_TRANSITION)
        );
        assertTrue(state.hasReason(WindowSuspensionState.Reason.PACKAGE_TRANSITION));
    }
}
