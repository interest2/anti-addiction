package com.book.mask.floating;

import android.accessibilityservice.AccessibilityService;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.book.mask.config.PackageLogManager;

/**
 * 无障碍窗口 / 系统设置的只读探查工具：读取前台根窗口包名、判断是否应忽略的包名、
 * 以及系统过渡动画是否关闭。均为无状态查询，不依赖 AppStateManager 的运行时状态。
 */
class AccessibilityWindowInspector {
    private static final String TAG = "AppStateManager";

    private final AccessibilityService service;

    AccessibilityWindowInspector(AccessibilityService service) {
        this.service = service;
    }

    /**
     * 获取当前前台根窗口的包名；若为空、为本 APP 自身或输入法应用则返回空串。
     */
    String getActiveRootPackage() {
        String rootPackage = getRawActiveRootPackage();
        if (!rootPackage.isEmpty()
                && !rootPackage.equals(service.getPackageName())
                && !FloatHelper.isInputMethodApp(rootPackage)) {
            return rootPackage;
        }
        return "";
    }

    private String getRawActiveRootPackage() {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        try {
            if (root != null && root.getPackageName() != null) {
                return root.getPackageName().toString();
            }
            return "";
        } finally {
            if (root != null) {
                root.recycle();
            }
        }
    }

    /**
     * 从窗口状态事件中取出应当处理的包名，并记入 PackageLogManager；
     * 包名为空、或为本 APP 自身 / 输入法应用时返回 null。
     * 注意：包名防抖暂停由调用方在入口处判断，此处不涉及运行时状态。
     */
    String extractObservablePackageName(AccessibilityEvent event) {
        CharSequence packageName = event.getPackageName();
        if (packageName == null) {
            return null;
        }
        String packageNameString = packageName.toString();
        PackageLogManager.getInstance().record(packageNameString);

        if (shouldIgnorePackage(packageNameString)) {
            return null;
        }
        Log.d(TAG, "窗口状态改变，当前包名: " + packageNameString);
        return packageNameString;
    }

    /**
     * 判断是否应忽略该包名（自身应用 / 输入法应用）
     */
    boolean shouldIgnorePackage(String packageName) {
        // 过滤掉此 APP 自身，避免悬浮窗显示时触发状态变化
        if (packageName.equals(service.getPackageName())) {
            return true;
        }
        // 过滤掉输入法应用，避免输入法弹出时误判
        if (FloatHelper.isInputMethodApp(packageName)) {
            Log.d(TAG, "忽略输入法应用: " + packageName);
            return true;
        }
        return false;
    }

    /**
     * 系统"过渡动画"是否已被用户关闭（开发者选项里"过渡动画缩放"设为 0）。
     * 关闭动画时包名切换是瞬时的，无需等待 300ms 复核。
     */
    boolean isAnimationDisabled() {
        if (service == null) {
            return false;
        }
        float scale = Settings.Global.getFloat(
                service.getContentResolver(),
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1.0f);
        return scale == 0f;
    }

    static String packageNameForLog(String packageName) {
        return packageName.isEmpty() ? "未知" : packageName;
    }
}
