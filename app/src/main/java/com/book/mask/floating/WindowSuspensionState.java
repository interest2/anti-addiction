package com.book.mask.floating;

import java.util.EnumSet;

/**
 * Tracks why an attached floating window must remain hidden.
 *
 * <p>The first reason applies the physical hide operation. The last reason removed restores the
 * window. This prevents overlapping transitions from making the window visible too early.</p>
 */
final class WindowSuspensionState {
    enum Reason {
        SYSTEM_UI,
        PAGE_TRANSITION,
        RECORDING
    }

    enum ResumeAction {
        NONE,
        KEEP_HIDDEN,
        RESTORE
    }

    private final EnumSet<Reason> reasons = EnumSet.noneOf(Reason.class);

    boolean suspend(Reason reason) {
        boolean wasVisible = reasons.isEmpty();
        boolean added = reasons.add(reason);
        return added && wasVisible;
    }

    ResumeAction resume(Reason reason) {
        if (!reasons.remove(reason)) {
            return ResumeAction.NONE;
        }
        return reasons.isEmpty() ? ResumeAction.RESTORE : ResumeAction.KEEP_HIDDEN;
    }

    boolean hasReason(Reason reason) {
        return reasons.contains(reason);
    }

    boolean isSuspended() {
        return !reasons.isEmpty();
    }

    void clear() {
        reasons.clear();
    }
}
