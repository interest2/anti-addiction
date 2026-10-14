package com.book.mask.config;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 自定义APP管理类，用于管理所有APP（包括预定义和用户自定义）
 */
public class CustomApp {
    /** 每日宽松次数上限。改这里即可，UI 选项与导入钳位都由它派生。 */
    public static final int MAX_RELAXED_LIMIT_COUNT = 2;

    /** 用户未显式设置时默认采用「宽松匹配」（只匹配可见节点）的包名：豆瓣。 */
    private static final Set<String> DEFAULT_LOOSE_MATCH_PACKAGES =
            Collections.singleton("com.douban.frodo");

    private final String appName;
    private final String packageName;
    private String targetWord; // 改为非final，支持修改
    private int relaxedLimitCount; // 改为非final，支持修改
    private boolean globalBlock;
    /** 宽松匹配开关；null 表示用户未设置，取包名默认值（旧数据无此字段时 Gson 也还原为 null）。 */
    private Boolean looseMatch;

    public CustomApp(String appName, String packageName, String targetWord, int relaxedLimitCount) {
        this(appName, packageName, targetWord, relaxedLimitCount, false);
    }

    public CustomApp(String appName, String packageName, String targetWord, int relaxedLimitCount,
                     boolean globalBlock) {
        this(appName, packageName, targetWord, relaxedLimitCount, globalBlock, null);
    }

    public CustomApp(String appName, String packageName, String targetWord, int relaxedLimitCount,
                     boolean globalBlock, Boolean looseMatch) {
        this.appName = appName;
        this.packageName = packageName;
        this.targetWord = targetWord;
        this.relaxedLimitCount = relaxedLimitCount;
        this.globalBlock = globalBlock;
        this.looseMatch = looseMatch;
    }

    /**
     * 拷贝构造：用于从只读默认模板生成可变副本，避免调用方就地改写共享模板对象。
     */
    public CustomApp(CustomApp other) {
        this(other.appName, other.packageName, other.targetWord, other.relaxedLimitCount,
                other.globalBlock, other.looseMatch);
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
     * 是否宽松匹配：开启后页面关键词只匹配可见节点，用于规避 APP 把不可见页面（如其他 tab）
     * 的文字也挂在无障碍树上而误屏蔽；关闭时不可见节点也参与匹配。
     */
    public boolean isLooseMatch() {
        return looseMatch != null ? looseMatch : DEFAULT_LOOSE_MATCH_PACKAGES.contains(packageName);
    }

    public void setLooseMatch(boolean looseMatch) {
        this.looseMatch = looseMatch;
    }

    /** 用户显式设置的原始值（null=未设置），供重建对象时原样保留，不把包名默认值固化下来。 */
    Boolean getLooseMatchSetting() {
        return looseMatch;
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