package com.book.baisc.config;

import java.util.HashMap;
import java.util.Map;

public class Share {
    public static boolean MOTIVATE_CHANGE = false;

    // 多APP状态管理
    public static Map<Const.SupportedApp, String> appStates = new HashMap<>();
    public static Const.SupportedApp currentApp = null; // 当前活跃的APP
    public static boolean isFloatingWindowVisible = false; // 悬浮窗是否显示
    public static Map<Const.SupportedApp, Boolean> appManuallyHidden = new HashMap<>(); // 每个APP的手动隐藏状态
    
    /**
     * 获取指定APP的状态
     */
    public static String getAppState(Const.SupportedApp app) {
        return appStates.get(app);
    }
    
    /**
     * 设置指定APP的状态
     */
    public static void setAppState(Const.SupportedApp app, String state) {
        appStates.put(app, state);
    }
    
    /**
     * 清除指定APP的状态
     */
    public static void clearAppState(Const.SupportedApp app) {
        appStates.remove(app);
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
     * 设置指定APP的手动隐藏状态
     */
    public static void setAppManuallyHidden(Const.SupportedApp app, boolean hidden) {
        appManuallyHidden.put(app, hidden);
    }
    
    /**
     * 获取指定APP的手动隐藏状态
     */
    public static boolean isAppManuallyHidden(Const.SupportedApp app) {
        return appManuallyHidden.getOrDefault(app, false);
    }
}
