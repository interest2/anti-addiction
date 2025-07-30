package com.book.mask.config;

import java.util.HashMap;
import java.util.Map;

public class Share {
    public static boolean MOTIVATE_CHANGE = false;

    // 多APP状态管理
    public static Map<String, String> appStates = new HashMap<>(); // 使用包名作为键
    public static String latestVersion = "";
    public static Object currentApp = null; // 当前活跃的APP（支持预定义和自定义APP）
    public static boolean isFloatingWindowVisible = false; // 悬浮窗是否显示
    public static Map<String, Boolean> appManuallyHidden = new HashMap<>(); // 每个APP的手动隐藏状态（使用包名作为键）

    public static boolean judgeEnabled(String packageName){
        return Const.SupportedApp.XHS.getPackageName().equals(packageName)
                || Const.SupportedApp.ZHIHU.getPackageName().equals(packageName)
                || Const.SupportedApp.DOUYIN.getPackageName().equals(packageName);
    }

    /**
     * 获取指定APP的状态
     */
    public static String getAppState(Const.SupportedApp app) {
        return appStates.get(app.getPackageName());
    }
    
    /**
     * 获取指定APP的状态 - 支持自定义APP
     */
    public static String getAppState(Object app) {
        String packageName = getPackageName(app);
        return appStates.get(packageName);
    }

    /**
     * 设置指定APP的状态 - 支持自定义APP
     */
    public static void setAppState(Object app, String state) {
        String packageName = getPackageName(app);
        appStates.put(packageName, state);
    }

    /**
     * 清除指定APP的状态 - 支持自定义APP
     */
    public static void clearAppState(Object app) {
        String packageName = getPackageName(app);
        appStates.remove(packageName);
    }
    
    /**
     * 清除所有APP状态
     */
    public static void clearAllAppStates() {
        appStates.clear();
        appManuallyHidden.clear();
        currentApp = null;
    }
    
    /**
     * 设置指定APP的手动隐藏状态 - 支持自定义APP
     */
    public static void setAppManuallyHidden(Object app, boolean hidden) {
        String packageName = getPackageName(app);
        appManuallyHidden.put(packageName, hidden);
    }
    
    /**
     * 获取指定APP的手动隐藏状态
     */
    public static boolean isAppManuallyHidden(Const.SupportedApp app) {
        return appManuallyHidden.getOrDefault(app.getPackageName(), false);
    }
    
    /**
     * 获取指定APP的手动隐藏状态 - 支持自定义APP
     */
    public static boolean isAppManuallyHidden(Object app) {
        String packageName = getPackageName(app);
        return appManuallyHidden.getOrDefault(packageName, false);
    }
    
    /**
     * 获取APP的包名
     */
    private static String getPackageName(Object app) {
        if (app instanceof Const.SupportedApp) {
            return ((Const.SupportedApp) app).getPackageName();
        } else if (app instanceof Const.CustomApp) {
            return ((Const.CustomApp) app).getPackageName();
        }
        return "unknown";
    }
}
