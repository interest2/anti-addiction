package com.book.baisc;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 设置管理器
 * 用于管理应用的各种配置参数
 */
public class SettingsManager {
    
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_AUTO_SHOW_INTERVAL = "auto_show_interval";
    
    // 默认自动显示间隔（秒）
    private static final int DEFAULT_AUTO_SHOW_INTERVAL = 5;
    
    private SharedPreferences prefs;
    
    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * 获取自动显示间隔（秒）
     */
    public int getAutoShowInterval() {
        return prefs.getInt(KEY_AUTO_SHOW_INTERVAL, DEFAULT_AUTO_SHOW_INTERVAL);
    }
    
    /**
     * 设置自动显示间隔（秒）
     */
    public void setAutoShowInterval(int seconds) {
        if (seconds >= 3 && seconds <= 60) {
            prefs.edit().putInt(KEY_AUTO_SHOW_INTERVAL, seconds).apply();
        }
    }
    
    /**
     * 获取自动显示间隔（毫秒）
     */
    public long getAutoShowIntervalMillis() {
        return getAutoShowInterval() * 1000L;
    }
    
    /**
     * 重置为默认设置
     */
    public void resetToDefault() {
        prefs.edit().clear().apply();
    }
    
    /**
     * 获取可选的时间间隔列表
     */
    public static int[] getAvailableIntervals() {
        return new int[]{5, 10, 15, 20, 30, 45, 60};
    }
    
    /**
     * 获取时间间隔的显示文本
     */
    public static String getIntervalDisplayText(int seconds) {
        if (seconds < 60) {
            return seconds + "秒";
        } else {
            return (seconds / 60) + "分钟";
        }
    }
} 