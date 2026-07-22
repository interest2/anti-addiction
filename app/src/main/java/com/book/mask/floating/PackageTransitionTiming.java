package com.book.mask.floating;

import com.book.mask.config.Const;

/**
 * 普通包名切换的时间计算，所有入参和返回值单位均为毫秒。
 */
public final class PackageTransitionTiming {
    private PackageTransitionTiming() {
    }

    public static long getEarlyReturnPauseDuration() {
        return Math.max(
                0L,
                (long) Const.PACKAGE_TRANSITION_ANIMATION_DURATION_MS
                        - Const.PACKAGE_TRANSITION_CHECK_DELAY_MS
                        + Const.TRANSITION_EARLY_RETURN_BUFFER_MS
        );
    }

    public static long getDirectReentryWindowDuration() {
        return (long) Const.PACKAGE_TRANSITION_ANIMATION_DURATION_MS
                + Const.TRANSITION_DIRECT_REENTRY_BUFFER_MS;
    }

    public static boolean isWithinDirectReentryWindow(
            long transitionStartedAt,
            long now
    ) {
        long elapsed = now - transitionStartedAt;
        return elapsed >= 0 && elapsed <= getDirectReentryWindowDuration();
    }
}
