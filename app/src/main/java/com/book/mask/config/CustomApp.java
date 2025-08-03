package com.book.mask.config;

import java.util.List;

/**
 * 自定义APP管理类，用于管理所有APP（包括预定义和用户自定义）
 */
public class CustomApp {
    private final String appName;
    private final String packageName;
    private final String targetWord;
    private int casualLimitCount; // 改为非final，支持修改
    
    public CustomApp(String appName, String packageName, String targetWord, int casualLimitCount) {
        this.appName = appName;
        this.packageName = packageName;
        this.targetWord = targetWord;
        this.casualLimitCount = casualLimitCount;
    }
    
    public String getAppName() {
        return appName;
    }
    
    public String getPackageName() {
        return packageName;
    }
    
    public String getTargetWord() {
        return targetWord;
    }
    
    public int getCasualLimitCount() {
        return casualLimitCount;
    }
    
    public void setCasualLimitCount(int casualLimitCount) {
        this.casualLimitCount = casualLimitCount;
    }
    
    /**
     * 根据包名获取对应的APP
     */
    public static CustomApp getByPackageName(String packageName) {
        return CustomAppManager.getInstance().getAppByPackageName(packageName);
    }
    
    /**
     * 获取所有APP列表
     */
    public static List<CustomApp> getAllApps() {
        return CustomAppManager.getInstance().getAllApps();
    }
    
    /**
     * 检查包名是否已存在
     */
    public static boolean isPackageNameExists(String packageName) {
        return CustomAppManager.getInstance().isPackageNameExists(packageName);
    }
} 