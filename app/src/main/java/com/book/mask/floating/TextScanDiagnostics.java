package com.book.mask.floating;

import android.util.Log;

import com.book.mask.config.CustomAppManager;

/**
 * 文字扫描诊断：仅当当前 APP 为抖音时收集一次页面扫描的诊断信息并输出一行日志（APP 内写死，
 * 不依赖云端开关）。其余 APP {@link #createIfEnabled} 返回 null，调用方据此走低开销路径——
 * 不逐节点判定可见性、不拼接诊断字符串，避免在小红书等 APP 上白白消耗。
 */
final class TextScanDiagnostics {
    // 沿用 AppStateManager 的日志 TAG，保持既有 logcat 过滤习惯
    private static final String TAG = "AppStateManager";

    private final String source;
    private final String activePackage;
    private int visitedNodeCount;
    private int visibleNodeCount;
    private int maxDepth;
    private int lastHitNodeIndex = -1;
    private int lastHitDepth = -1;
    private boolean lastHitVisible;

    private TextScanDiagnostics(String source, String activePackage) {
        this.source = source;
        this.activePackage = activePackage;
    }

    /** 仅当当前为抖音时返回收集器，否则返回 null（调用方据此走低开销路径）。 */
    static TextScanDiagnostics createIfEnabled(String source, String activePackage) {
        if (!CustomAppManager.DOUYIN_PACKAGE.equals(activePackage)) {
            return null;
        }
        return new TextScanDiagnostics(source, activePackage);
    }

    void onNodeVisited(int depth, boolean visible) {
        visitedNodeCount++;
        maxDepth = Math.max(maxDepth, depth);
        if (visible) {
            visibleNodeCount++;
        }
    }

    void onTargetHit(int depth, boolean visible) {
        lastHitNodeIndex = visitedNodeCount;
        lastHitDepth = depth;
        lastHitVisible = visible;
    }

    void log(String rootPackage, double rootMs, double traversalMs, boolean matched) {
        Log.d(TAG, "文字扫描诊断: source=" + source
                + ", activePackage=" + activePackage
                + ", rootPackage=" + rootPackage
                + ", rootMs=" + com.book.mask.util.DateUtils.formatMillis(rootMs)
                + ", traversalMs=" + com.book.mask.util.DateUtils.formatMillis(traversalMs)
                + ", visitedNodes=" + visitedNodeCount
                + ", visibleNodes=" + visibleNodeCount
                + ", maxDepth=" + maxDepth
                + ", lastHitIndex=" + lastHitNodeIndex
                + ", lastHitDepth=" + lastHitDepth
                + ", lastHitVisible=" + lastHitVisible
                + ", matched=" + matched);
    }
}
