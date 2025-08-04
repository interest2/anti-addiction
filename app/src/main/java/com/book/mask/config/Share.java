package com.book.mask.config;

import java.util.HashMap;
import java.util.Map;

public class Share {
    public static boolean MOTIVATE_CHANGE = false;

    // 多APP状态管理
    public static Map<String, String> appStates = new HashMap<>(); // 使用包名作为键
    public static String latestVersion = "";
    public static String className = "";
    public static String packageName = "";
    public static CustomApp currentApp = null; // 当前活跃的APP（统一使用CustomApp）
    public static boolean isFloatingWindowVisible = false; // 悬浮窗是否显示
    public static Map<String, Boolean> appManuallyHidden = new HashMap<>(); // 每个APP的手动隐藏状态（使用包名作为键）
    public static Map<String, Long> hiddenTimestamp = new HashMap<>();
    
    // 调试信息
    public static int lastEventType = 0; // 最后接收到的事件类型
    public static long lastEventTime = 0; // 最后接收到事件的时间戳
    public static long findTextInNodeTime = 0; // forceCheck条件触发时间戳
    
    // 调试时间戳变量
    public static long h0 = 0; // 调试时间戳 h0
    public static long h1 = 0; // 调试时间戳 h1
    public static long h7 = 0; // 调试时间戳 h7
    public static long h8 = 0; // 调试时间戳 h8
    
    // checkTextContentOptimized 方法调试变量
    public static String currentInterface = ""; // 当前界面状态
    public static boolean forceCheck = false; // 是否强制检查

    public static boolean judgeEnabled(String packageName){
        return "com.xingin.xhs".equals(packageName)
                || "com.zhihu.android".equals(packageName)
                || "com.ss.android.ugc.aweme".equals(packageName);
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
