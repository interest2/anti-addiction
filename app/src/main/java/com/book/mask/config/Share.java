package com.book.mask.config;

import com.book.mask.constant.QuestionConst;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Share {
    public static boolean MOTIVATE_CHANGE = false;
    public static long mathChallengeStartTime = 0; // 数学题验证开始时间

    // 多APP状态管理
    public static Map<String, String> appStates = new HashMap<>(); // 使用包名作为键
    public static final String DEFAULT_SERVER_MODEL = "GLM";
    public static volatile String latestVersion = "";
    public static volatile long latestVersionTimestamp = 0L;
    public static volatile String serverModel = DEFAULT_SERVER_MODEL;
    public static volatile double mixedReasoningQuizRatio =
            QuestionConst.MIXED_REASONING_QUIZ_RATIO_DEFAULT;
    public static volatile int appreciateImageCode = 0;
    public static CustomApp currentApp = null; // 当前活跃的APP（统一使用CustomApp）
    public static boolean isFloatingWindowVisible = false; // 悬浮窗是否显示
    public static Map<String, Boolean> appManuallyHidden = new HashMap<>(); // 每个APP的手动隐藏状态（使用包名作为键）

    // 预置APP中默认开启监测的包名（微信、支付宝默认不开启）
    private static final Set<String> DEFAULT_ENABLED_PACKAGES = Set.of(
            CustomAppManager.XHS_PACKAGE,
            CustomAppManager.ZHIHU_PACKAGE,
            CustomAppManager.BILI_PACKAGE,
            CustomAppManager.DOUYIN_PACKAGE
    );

    public static boolean judgeEnabled(String packageName){
        return DEFAULT_ENABLED_PACKAGES.contains(packageName);
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
