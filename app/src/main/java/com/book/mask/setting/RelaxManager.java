package com.book.mask.setting;

import android.content.Context;
import android.content.SharedPreferences;

import com.book.mask.config.CustomApp;
import com.book.mask.util.DateUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 解禁时长和严格宽松模式管理器
 * 用于管理应用的时间间隔设置和宽松版关闭次数统计
 */
public class RelaxManager {
    
    private static final String PREFS_NAME = "app_settings";
    // 每个APP独立的设置键名前缀
    private static final String KEY_DEFAULT_SHOW_INTERVAL = "default_show_interval";
    private static final String KEY_SHOW_INTERVAL = "app_show_interval_";
    private static final String KEY_RELAXED_CLOSE_COUNT = "app_relaxed_close_count_";
    private static final String KEY_LAST_RELAXED_CLOSE_DATE = "app_last_relaxed_close_date_";
    private static final String KEY_LAST_CLOSE_TIME = "app_last_close_time_";
    private static final String KEY_LAST_CLOSE_INTERVAL = "app_last_close_interval_";

    // 严格、宽松模式的各选项
    private static final int[] strictIntervalArray = {30, 60, 120};
    private static final int[] relaxedIntervalArray = {600, 1200, 1800};

    private SharedPreferences prefs;

    public RelaxManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // 时间格式化器
    private static final SimpleDateFormat timeFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    
    /**
     * 格式化时间戳为可读格式
     */
    private static String formatTime(long timestamp) {
        if (timestamp == 0) return "未设置";
        return timeFormatter.format(new Date(timestamp));
    }

    /**
     * 获取日常版可选的时间间隔列表
     */
    public static int[] getStrictIntervals() {
        return strictIntervalArray;
    }
    
    /**
     * 获取宽松版可选的时间间隔列表
     */
    public static int[] getRelaxedIntervals() {
        return relaxedIntervalArray;
    }
    
    /**
     * 获取严格模式的最大时间间隔
     */
    public static int getMaxStrictInterval() {
        return strictIntervalArray[strictIntervalArray.length - 1];
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
    
    // ===== 多APP独立设置相关方法 =====
    /**
     * 兜底的间隔（秒），当 currentActiveApp 为空时触发
     */
    public int getDefaultInterval() {
        return prefs.getInt(KEY_DEFAULT_SHOW_INTERVAL, getMaxStrictInterval());
    }

    /**
     * 获取指定APP的自动显示间隔（秒）
     */
    public int getAppInterval(CustomApp app) {
        String key = KEY_SHOW_INTERVAL + app.getPackageName();
        return prefs.getInt(key, getMaxStrictInterval());
    }

    /**
     * 设置指定APP的自动显示间隔（秒）
     */
    public void setAppInterval(CustomApp app, int seconds) {
        String packageName = app.getPackageName();
        if (packageName == null) return;
        
        String key = KEY_SHOW_INTERVAL + packageName;
        prefs.edit().putInt(key, seconds).apply();

        android.util.Log.d("SettingsManager", "APP " + packageName + " 设置时间间隔为: " + seconds + "秒");
        android.util.Log.d("SettingsManager", "  新设置将在下次关闭悬浮窗后生效");
    }
    
    /**
     * 获取指定APP的自动显示间隔（毫秒）
     */
    public long getAppIntervalMillis(CustomApp app) {
        return getAppInterval(app) * 1000L;
    }

    /**
     * 判断指定APP当前是否是宽松版模式
     */
    public boolean isAppRelaxedMode(CustomApp app) {
        int currentInterval = getAppInterval(app);
        for (int interval : relaxedIntervalArray) {
            if (interval == currentInterval) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断指定APP上次关闭时是否是宽松模式
     */
    public boolean isLastRelaxedMode(int lastInterval) {
        for (int interval : relaxedIntervalArray) {
            if (interval == lastInterval) {
                return true;
            }
        }
        return false;
    }

    /**
     * 增加指定APP的宽松版关闭次数，并处理每日重置
     */
    public void incrementAppRelaxedCloseCount(CustomApp app) {
        String packageName = app.getPackageName();
        if (packageName == null) return;
        
        String currentDate = DateUtils.getCurrentDate();
        String countKey = KEY_RELAXED_CLOSE_COUNT + packageName;
        String dateKey = KEY_LAST_RELAXED_CLOSE_DATE + packageName;
        String lastDate = prefs.getString(dateKey, "");
        
        int count = prefs.getInt(countKey, 0);

        if (currentDate.equals(lastDate)) {
            // 是同一天，计数+1
            count++;
        } else {
            // 是新的一天，重置为1
            count = 1;
        }

        prefs.edit()
             .putInt(countKey, count)
             .putString(dateKey, currentDate)
             .apply();
        
        android.util.Log.d("SettingsManager", "APP " + packageName + " 宽松版关闭次数增加. 当前次数: " + count + " 日期: " + currentDate);
    }
    
    /**
     * 获取指定APP今天的宽松版关闭次数
     */
    public int getAppRelaxedCloseCount(CustomApp app) {
        String currentDate = DateUtils.getCurrentDate();
        String countKey = KEY_RELAXED_CLOSE_COUNT + app.getPackageName();
        String dateKey = KEY_LAST_RELAXED_CLOSE_DATE + app.getPackageName();
        String lastDate = prefs.getString(dateKey, "");
        
        if (currentDate.equals(lastDate)) {
            return prefs.getInt(countKey, 0);
        }
        
        // 如果不是同一天，返回0
        return 0;
    }
    
    /**
     * 设置指定APP的宽松版关闭次数
     */
    public void setAppRelaxedCloseCount(CustomApp app, int count) {
        String countKey = KEY_RELAXED_CLOSE_COUNT + app.getPackageName();
        prefs.edit().putInt(countKey, count).apply();
    }
    
    /**
     * 记录指定APP的悬浮窗关闭时间和使用的时间间隔
     */
    public void recordAppCloseTime(CustomApp app, int intervalSeconds) {
        String packageName = app.getPackageName();
        if (packageName == null) return;
        
        String timeKey = KEY_LAST_CLOSE_TIME + packageName;
        String intervalKey = KEY_LAST_CLOSE_INTERVAL + packageName;
        long currentTime = System.currentTimeMillis();
        /* 这里是分别对 2 个 key 进行设置 */
        prefs.edit()
            .putLong(timeKey, currentTime)
            .putInt(intervalKey, intervalSeconds)
            .apply();
        android.util.Log.d("SettingsManager", "记录APP " + packageName + " 关闭时间: " + formatTime(currentTime) + ", 使用间隔: " + intervalSeconds + "秒");
    }
    
    /**
     * 获取指定APP的上次关闭时间
     */
    public long getAppLastCloseTime(CustomApp app) {
        String key = KEY_LAST_CLOSE_TIME + app.getPackageName();
        return prefs.getLong(key, 0);
    }

    /**
     * 获取指定APP上次关闭时使用的时间间隔（秒）
     */
    public int getAppLastCloseInterval(CustomApp app) {
        String key = KEY_LAST_CLOSE_INTERVAL + app.getPackageName();
        return prefs.getInt(key, getMaxStrictInterval());
    }

    /**
     * 计算指定APP的剩余可用时间（毫秒）
     * @param app 指定的APP
     * @return 剩余时间（毫秒），如果可以自由使用则返回-1
     */
    public long getAppRemainingTime(CustomApp app) {
        // 如果悬浮窗正在显示，且是当前APP，则不可用
        if (com.book.mask.config.Share.isFloatingWindowVisible && app == com.book.mask.config.Share.currentApp) {
            return 0;
        }
        
        long lastCloseTime = getAppLastCloseTime(app);
        if (lastCloseTime == 0) {
            // 从未关闭过，可以自由使用
            return -1;
        }

        long currentTime = System.currentTimeMillis();
        // 使用上次关闭时记录的时间间隔，而不是当前设置的时间间隔
        int intervalSeconds = getAppLastCloseInterval(app);

        long nextAvailableTime = lastCloseTime + intervalSeconds * 1000L;

        if (currentTime >= nextAvailableTime) {
            // 已经超过等待时间，可以自由使用
//            android.util.Log.d("SettingsManager", "  结果: 可以自由使用");
            return -1;
        } else {
            // 还在等待期间，返回剩余时间
            long remainingTime = nextAvailableTime - currentTime;
            android.util.Log.d("SettingsManager", "  结果: 剩余时间 " + remainingTime + "毫秒 (" + (remainingTime/1000) + "秒)");
            return remainingTime;
        }
    }
    
    /**
     * 获取APP监测开关状态
     */
    public Boolean isAppMonitoringEnabled(String packageName) {
        String key = "app_monitoring_enabled_" + packageName;
        if (!prefs.contains(key)) {
            return null; // 还没有设置过
        }
        return prefs.getBoolean(key, false);
    }

    /**
     * 设置APP监测开关状态
     */
    public void setAppMonitoringEnabled(String packageName, boolean enabled) {
        String key = "app_monitoring_enabled_" + packageName;
        prefs.edit().putBoolean(key, enabled).apply();
        android.util.Log.d("SettingsManager", "设置APP监测状态: " + packageName + " = " + enabled);
    }

    /**
     * 检查APP是否应该被监测
     */
    public boolean shouldMonitorApp(String packageName) {
        Boolean isEnabled = isAppMonitoringEnabled(packageName);
        if (isEnabled == null) {
            // 如果还没有设置过，使用默认值
            return com.book.mask.config.Share.judgeEnabled(packageName); // 小红书默认开启
        }
        return isEnabled;
    }

}