package com.book.baisc.config;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 设置管理器
 * 用于管理应用的各种配置参数
 */
public class SettingsManager {
    
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_AUTO_SHOW_INTERVAL = "auto_show_interval";
    private static final String KEY_CASUAL_CLOSE_COUNT = "casual_close_count";
    private static final String KEY_LAST_CASUAL_CLOSE_DATE = "last_casual_close_date";
    private static final String KEY_MOTIVATION_TAG = "motivation_tag";
    
    // 默认自动显示间隔（秒）
    private static final int DEFAULT_AUTO_SHOW_INTERVAL = 5;

    // 激励语标签列表
    private static final String[] MOTIVATION_TAGS = {
            "高考", "考研", "保研", "出国升学", "跳槽", "找工作", "考公务员"
    };
    private static final String DEFAULT_MOTIVATION_TAG = "待设置";

    // 日常版时间间隔（秒）
    // 休闲版时间间隔（秒）
//    private static final int[] dailyIntervalArray = {3, 5};
//    private static final int[] casualIntervalArray = {10, 15};
    private static final int[] dailyIntervalArray = {10, 60};
    private static final int[] casualIntervalArray = {600, 900};

    private SharedPreferences prefs;
    
    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * 获取自动显示间隔（秒）
     */
    public int getAutoShowInterval() {
        return prefs.getInt(KEY_AUTO_SHOW_INTERVAL, dailyIntervalArray[0]);
    }
    
    /**
     * 设置自动显示间隔（秒）
     */
    public void setAutoShowInterval(int seconds) {
        int minInterval = dailyIntervalArray[0];
        int maxInterval = casualIntervalArray[casualIntervalArray.length - 1];
        if (seconds >= minInterval && seconds <= maxInterval) {
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
     * 设置激励语标签
     */
    public void setMotivationTag(String tag) {
        prefs.edit().putString(KEY_MOTIVATION_TAG, tag).apply();
        Const.MOTIVATE_CHANGE = true;
    }

    /**
     * 获取激励语标签
     */
    public String getMotivationTag() {
        return prefs.getString(KEY_MOTIVATION_TAG, DEFAULT_MOTIVATION_TAG);
    }

    /**
     * 获取可选的激励语标签列表
     */
    public static String[] getAvailableTags() {
        return MOTIVATION_TAGS;
    }
    
    /**
     * 判断当前是否是休闲版模式
     */
    public boolean isCasualMode() {
        int currentInterval = getAutoShowInterval();
        for (int interval : casualIntervalArray) {
            if (interval == currentInterval) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取当前日期字符串 "yyyy-MM-dd"
     */
    private String getCurrentDate() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
    }

    /**
     * 增加休闲版关闭次数，并处理每日重置
     */
    public void incrementCasualCloseCount() {
        String currentDate = getCurrentDate();
        String lastDate = prefs.getString(KEY_LAST_CASUAL_CLOSE_DATE, "");
        
        int count = prefs.getInt(KEY_CASUAL_CLOSE_COUNT, 0);

        if (currentDate.equals(lastDate)) {
            // 是同一天，计数+1
            count++;
        } else {
            // 是新的一天，重置为1
            count = 1;
        }

        prefs.edit()
             .putInt(KEY_CASUAL_CLOSE_COUNT, count)
             .putString(KEY_LAST_CASUAL_CLOSE_DATE, currentDate)
             .apply();
        
        android.util.Log.d("SettingsManager", "休闲版关闭次数增加. 当前次数: " + count + " 日期: " + currentDate);
    }

    /**
     * 获取今天的休闲版关闭次数
     */
    public int getCasualCloseCount() {
        String currentDate = getCurrentDate();
        String lastDate = prefs.getString(KEY_LAST_CASUAL_CLOSE_DATE, "");
        
        if (currentDate.equals(lastDate)) {
            return prefs.getInt(KEY_CASUAL_CLOSE_COUNT, 0);
        }
        
        // 如果不是同一天，返回0
        return 0;
    }

    /**
     * 获取日常版可选的时间间隔列表
     */
    public static int[] getDailyAvailableIntervals() {
        return dailyIntervalArray;
    }
    
    /**
     * 获取休闲版可选的时间间隔列表
     */
    public static int[] getCasualAvailableIntervals() {
        return casualIntervalArray;
    }
    
    /**
     * 获取日常版的最大时间间隔
     */
    public static int getMaxDailyInterval() {
        int max = 0;
        if (dailyIntervalArray != null && dailyIntervalArray.length > 0) {
            for (int interval : dailyIntervalArray) {
                if (interval > max) {
                    max = interval;
                }
            }
        }
        return max;
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