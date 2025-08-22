package com.book.mask.config;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Share {
    public static boolean MOTIVATE_CHANGE = false;
    public static long mathChallengeStartTime = 0; // 数学题验证开始时间

    // 多APP状态管理
    public static Map<String, String> appStates = new HashMap<>(); // 使用包名作为键
    public static String latestVersion = "";
    public static CustomApp currentApp = null; // 当前活跃的APP（统一使用CustomApp）
    public static boolean isFloatingWindowVisible = false; // 悬浮窗是否显示
    public static Map<String, Boolean> appManuallyHidden = new HashMap<>(); // 每个APP的手动隐藏状态（使用包名作为键）
    public static Map<String, Long> hiddenTimestamp = new HashMap<>();

    public static boolean judgeEnabled(String packageName){
        return CustomAppManager.XHS_PACKAGE.equals(packageName)
                || CustomAppManager.ZHIHU_PACKAGE.equals(packageName)
                || CustomAppManager.BILI_PACKAGE.equals(packageName)
                || CustomAppManager.DOUYIN_PACKAGE.equals(packageName);
    }

    /**
     * 获取指定APP的状态 - 统一使用CustomApp
     */
    public static String getAppState(CustomApp app) {
        return appStates.get(app.getPackageName());
    }
    
    /**
     * 清除所有APP状态
     */
    public static void clearAllAppStates() {
        appStates.clear();
        appManuallyHidden.clear();
        hiddenTimestamp.clear();
        currentApp = null;
    }
    
    /**
     * 设置指定APP的手动隐藏状态
     */
    public static void setAppManuallyHidden(CustomApp app, boolean hidden) {
        appManuallyHidden.put(app.getPackageName(), hidden);
    }
    
    /**
     * 获取指定APP的手动隐藏状态
     */
    public static boolean isAppManuallyHidden(CustomApp app) {
        return appManuallyHidden.getOrDefault(app.getPackageName(), false);
    }

    public static void setHiddenTimestamp(String packageName, long hiddenTime) {
        hiddenTimestamp.put(packageName, hiddenTime);
    }

    public static Long getHiddenTimestamp(String packageName) {
        return hiddenTimestamp.get(packageName);
    }
    
    /**
     * 设置指定APP的状态
     */
    public static void setAppState(CustomApp app, String state) {
        appStates.put(app.getPackageName(), state);
    }
    
    /**
     * 清除指定APP的状态
     */
    public static void clearAppState(CustomApp app) {
        appStates.remove(app.getPackageName());
    }
}
