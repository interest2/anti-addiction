package com.book.mask.config;

import java.util.List;

/**
 * 自定义APP管理类，用于管理所有APP（包括预定义和用户自定义）
 */
public class CustomApp {
    /** 每日宽松次数上限。改这里即可，UI 选项与导入钳位都由它派生。 */
    public static final int MAX_RELAXED_LIMIT_COUNT = 2;

    private final String appName;
    private final String packageName;
    private String targetWord; // 改为非final，支持修改
    private int relaxedLimitCount; // 改为非final，支持修改
    private boolean globalBlock;

    public CustomApp(String appName, String packageName, String targetWord, int relaxedLimitCount) {
        this(appName, packageName, targetWord, relaxedLimitCount, false);
    }

    public CustomApp(String appName, String packageName, String targetWord, int relaxedLimitCount,
                     boolean globalBlock) {
        this.appName = appName;
        this.packageName = packageName;
        this.targetWord = targetWord;
        this.relaxedLimitCount = relaxedLimitCount;
        this.globalBlock = globalBlock;
    }

    /**
     * 拷贝构造：用于从只读默认模板生成可变副本，避免调用方就地改写共享模板对象。
     */
    public CustomApp(CustomApp other) {
        this(other.appName, other.packageName, other.targetWord, other.relaxedLimitCount,
                other.globalBlock);
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
    
    public int getRelaxedLimitCount() {
        return relaxedLimitCount;
    }
    
    public void setRelaxedLimitCount(int relaxedLimitCount) {
        this.relaxedLimitCount = relaxedLimitCount;
    }

    /**
     * 把外部来源（备份导入、历史数据）的宽松次数钳位到 1 ~ {@link #MAX_RELAXED_LIMIT_COUNT}。
     * 旧版本备份里可能存着超出当前上限的次数，不钳位会绕过上限继续生效。
     */
    public static int clampRelaxedLimitCount(int count) {
        return Math.max(1, Math.min(count, MAX_RELAXED_LIMIT_COUNT));
    }
    
    public void setTargetWord(String targetWord) {
        this.targetWord = targetWord;
    }

    public boolean isGlobalBlock() {
        return globalBlock;
    }

    public void setGlobalBlock(boolean globalBlock) {
        this.globalBlock = globalBlock;
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