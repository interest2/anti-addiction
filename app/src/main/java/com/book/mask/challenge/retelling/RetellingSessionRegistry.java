package com.book.mask.challenge.retelling;

/**
 * 复述题会话的进程内桥接：透明录音 Activity 与悬浮窗内会话不在同一界面层级，
 * 通过静态引用把录音结果交回当前活跃会话。录音结束后立即清除，避免旧引用泄漏。
 */
public final class RetellingSessionRegistry {

    private static volatile RetellingChallengeSession activeSession;

    private RetellingSessionRegistry() {
    }

    public static void setActiveSession(RetellingChallengeSession session) {
        activeSession = session;
    }

    public static RetellingChallengeSession getActiveSession() {
        return activeSession;
    }

    public static void clear() {
        activeSession = null;
    }
}
