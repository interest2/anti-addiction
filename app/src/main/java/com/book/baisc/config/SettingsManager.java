package com.book.baisc.config;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
    private static final String KEY_TARGET_COMPLETION_DATE = "target_completion_date";
    private static final String KEY_FLOATING_TOP_OFFSET = "floating_top_offset";
    private static final String KEY_FLOATING_BOTTOM_OFFSET = "floating_bottom_offset";
    
    // 每个APP独立的设置键名前缀
    private static final String KEY_APP_AUTO_SHOW_INTERVAL = "app_auto_show_interval_";
    private static final String KEY_APP_CASUAL_CLOSE_COUNT = "app_casual_close_count_";
    private static final String KEY_APP_LAST_CASUAL_CLOSE_DATE = "app_last_casual_close_date_";
    private static final String KEY_APP_LAST_CLOSE_TIME = "app_last_close_time_";
    private static final String KEY_APP_LAST_CLOSE_INTERVAL = "app_last_close_interval_";

    // 激励语标签列表
    private static final String[] MOTIVATION_TAGS = {
            "高考", "考研", "保研", "出国升学", "跳槽", "找工作", "考公务员"
    };
    private static final String DEFAULT_MOTIVATION_TAG = "待设置";
    private static final String DEFAULT_TARGET_DATE = "待设置";
    
    // 悬浮窗位置默认值（像素）
    private static final int DEFAULT_TOP_OFFSET = 130;
    private static final int DEFAULT_BOTTOM_OFFSET = 230;

    // 日常版时间间隔（秒）
    // 休闲版时间间隔（秒）
    private static final int[] dailyIntervalArray = {3, 5};
    private static final int[] casualIntervalArray = {20, 90};
//    private static final int[] dailyIntervalArray = {10, 60};
//    private static final int[] casualIntervalArray = {600, 900};

    private SharedPreferences prefs;
    
    // 时间格式化器
    private static final SimpleDateFormat timeFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    
    /**
     * 格式化时间戳为可读格式
     */
    private static String formatTime(long timestamp) {
        if (timestamp == 0) return "未设置";
        return timeFormatter.format(new Date(timestamp));
    }
    
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
     * 设置激励语标签
     */
    public void setMotivationTag(String tag) {
        prefs.edit().putString(KEY_MOTIVATION_TAG, tag).apply();
        Share.MOTIVATE_CHANGE = true;
    }

    /**
     * 获取激励语标签
     */
    public String getMotivationTag() {
        return prefs.getString(KEY_MOTIVATION_TAG, DEFAULT_MOTIVATION_TAG);
    }

    /**
     * 设置目标完成日期
     */
    public void setTargetCompletionDate(String date) {
        prefs.edit().putString(KEY_TARGET_COMPLETION_DATE, date).apply();
        Share.MOTIVATE_CHANGE = true;
    }

    /**
     * 获取目标完成日期
     */
    public String getTargetCompletionDate() {
        return prefs.getString(KEY_TARGET_COMPLETION_DATE, DEFAULT_TARGET_DATE);
    }

    /**
     * 设置悬浮窗上边缘距离顶部的距离
     */
    public void setFloatingTopOffset(int offset) {
        prefs.edit().putInt(KEY_FLOATING_TOP_OFFSET, offset).apply();
    }

    /**
     * 获取悬浮窗上边缘距离顶部的距离
     */
    public int getFloatingTopOffset() {
        return prefs.getInt(KEY_FLOATING_TOP_OFFSET, DEFAULT_TOP_OFFSET);
    }

    /**
     * 设置悬浮窗下边缘距离底部的距离
     */
    public void setFloatingBottomOffset(int offset) {
        prefs.edit().putInt(KEY_FLOATING_BOTTOM_OFFSET, offset).apply();
    }

    /**
     * 获取悬浮窗下边缘距离底部的距离
     */
    public int getFloatingBottomOffset() {
        return prefs.getInt(KEY_FLOATING_BOTTOM_OFFSET, DEFAULT_BOTTOM_OFFSET);
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
     * 获取严格模式的最大时间间隔
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
    
    // ===== 多APP独立设置相关方法 =====
    
    /**
     * 获取指定APP的自动显示间隔（秒）
     */
    public int getAppAutoShowInterval(Const.SupportedApp app) {
        String key = KEY_APP_AUTO_SHOW_INTERVAL + app.name();
        return prefs.getInt(key, dailyIntervalArray[0]);
    }
    
    /**
     * 获取指定APP的自动显示间隔（秒）- 支持自定义APP
     */
    public int getAppAutoShowInterval(Object app) {
        String packageName = getPackageName(app);
        if (packageName == null) return dailyIntervalArray[0];
        
        String key = KEY_APP_AUTO_SHOW_INTERVAL + packageName;
        return prefs.getInt(key, dailyIntervalArray[0]);
    }
    
    /**
     * 设置指定APP的自动显示间隔（秒）
     */
    public void setAppAutoShowInterval(Const.SupportedApp app, int seconds) {
        int minInterval = dailyIntervalArray[0];
        int maxInterval = casualIntervalArray[casualIntervalArray.length - 1];
        if (seconds >= minInterval && seconds <= maxInterval) {
            String key = KEY_APP_AUTO_SHOW_INTERVAL + app.name();
            
            // 检查是否需要立即生效
            int oldInterval = getAppAutoShowInterval(app);
            boolean wasInFreeMode = (getAppRemainingTime(app) == -1);
            boolean isSwitchingToStrictMode = (seconds < oldInterval) && wasInFreeMode;
            
            android.util.Log.d("SettingsManager", "APP " + app.name() + " 设置时间间隔: " + oldInterval + "秒 -> " + seconds + "秒");
            android.util.Log.d("SettingsManager", "  当前是否自由使用: " + wasInFreeMode);
            android.util.Log.d("SettingsManager", "  是否切换到严格模式: " + isSwitchingToStrictMode);
            
            // 更新设置
            prefs.edit().putInt(key, seconds).apply();
            
            // 如果是从自由使用状态切换到更严格的模式，立即生效
            if (isSwitchingToStrictMode) {
                recordAppCloseTime(app, seconds);
                android.util.Log.d("SettingsManager", "  立即生效: 记录新的关闭时间和时间间隔");
                
                // 通知无障碍服务立即检查是否需要显示悬浮窗
                triggerImmediateFloatingWindowCheck(app);
            }
        }
    }
    
    /**
     * 设置指定APP的自动显示间隔（秒）- 支持自定义APP
     */
    public void setAppAutoShowInterval(Object app, int seconds) {
        String packageName = getPackageName(app);
        if (packageName == null) return;
        
        int minInterval = dailyIntervalArray[0];
        int maxInterval = casualIntervalArray[casualIntervalArray.length - 1];
        if (seconds >= minInterval && seconds <= maxInterval) {
            String key = KEY_APP_AUTO_SHOW_INTERVAL + packageName;
            
            // 检查是否需要立即生效
            int oldInterval = getAppAutoShowInterval(app);
            boolean wasInFreeMode = (getAppRemainingTime(app) == -1);
            boolean isSwitchingToStrictMode = (seconds < oldInterval) && wasInFreeMode;
            
            android.util.Log.d("SettingsManager", "APP " + packageName + " 设置时间间隔: " + oldInterval + "秒 -> " + seconds + "秒");
            android.util.Log.d("SettingsManager", "  当前是否自由使用: " + wasInFreeMode);
            android.util.Log.d("SettingsManager", "  是否切换到严格模式: " + isSwitchingToStrictMode);
            
            // 更新设置
            prefs.edit().putInt(key, seconds).apply();
            
            // 如果是从自由使用状态切换到更严格的模式，立即生效
            if (isSwitchingToStrictMode) {
                recordAppCloseTime(app, seconds);
                android.util.Log.d("SettingsManager", "  立即生效: 记录新的关闭时间和时间间隔");
                
                // 通知无障碍服务立即检查是否需要显示悬浮窗
                triggerImmediateFloatingWindowCheck(app);
            }
        }
    }
    
    /**
     * 触发立即检查是否需要显示悬浮窗
     */
    private void triggerImmediateFloatingWindowCheck(Const.SupportedApp app) {
        try {
            // 通过静态方法通知无障碍服务
            Class<?> serviceClass = Class.forName("com.book.baisc.floating.FloatingAccessibilityService");
            java.lang.reflect.Method method = serviceClass.getMethod("triggerImmediateCheck", Const.SupportedApp.class);
            method.invoke(null, app);
            android.util.Log.d("SettingsManager", "  已通知无障碍服务立即检查APP " + app.name());
        } catch (Exception e) {
            android.util.Log.w("SettingsManager", "  无法通知无障碍服务: " + e.getMessage());
        }
    }
    
    /**
     * 触发立即检查是否需要显示悬浮窗 - 支持自定义APP
     */
    private void triggerImmediateFloatingWindowCheck(Object app) {
        try {
            // 通过静态方法通知无障碍服务
            Class<?> serviceClass = Class.forName("com.book.baisc.floating.FloatingAccessibilityService");
            java.lang.reflect.Method method = serviceClass.getMethod("triggerImmediateCheck", Object.class);
            method.invoke(null, app);
            String packageName = getPackageName(app);
            android.util.Log.d("SettingsManager", "  已通知无障碍服务立即检查APP " + packageName);
        } catch (Exception e) {
            android.util.Log.w("SettingsManager", "  无法通知无障碍服务: " + e.getMessage());
        }
    }
    
    /**
     * 获取指定APP的自动显示间隔（毫秒）
     */
    public long getAppAutoShowIntervalMillis(Const.SupportedApp app) {
        return getAppAutoShowInterval(app) * 1000L;
    }
    
    /**
     * 获取指定APP的自动显示间隔（毫秒）- 支持自定义APP
     */
    public long getAppAutoShowIntervalMillis(Object app) {
        return getAppAutoShowInterval(app) * 1000L;
    }
    
    /**
     * 判断指定APP当前是否是休闲版模式
     */
    public boolean isAppCasualMode(Const.SupportedApp app) {
        int currentInterval = getAppAutoShowInterval(app);
        for (int interval : casualIntervalArray) {
            if (interval == currentInterval) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 判断指定APP当前是否是休闲版模式 - 支持自定义APP
     */
    public boolean isAppCasualMode(Object app) {
        int currentInterval = getAppAutoShowInterval(app);
        for (int interval : casualIntervalArray) {
            if (interval == currentInterval) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 增加指定APP的休闲版关闭次数，并处理每日重置
     */
    public void incrementAppCasualCloseCount(Const.SupportedApp app) {
        String currentDate = getCurrentDate();
        String countKey = KEY_APP_CASUAL_CLOSE_COUNT + app.name();
        String dateKey = KEY_APP_LAST_CASUAL_CLOSE_DATE + app.name();
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
        
        android.util.Log.d("SettingsManager", "APP " + app.name() + " 休闲版关闭次数增加. 当前次数: " + count + " 日期: " + currentDate);
    }
    
    /**
     * 增加指定APP的休闲版关闭次数，并处理每日重置 - 支持自定义APP
     */
    public void incrementAppCasualCloseCount(Object app) {
        String packageName = getPackageName(app);
        if (packageName == null) return;
        
        String currentDate = getCurrentDate();
        String countKey = KEY_APP_CASUAL_CLOSE_COUNT + packageName;
        String dateKey = KEY_APP_LAST_CASUAL_CLOSE_DATE + packageName;
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
        
        android.util.Log.d("SettingsManager", "APP " + packageName + " 休闲版关闭次数增加. 当前次数: " + count + " 日期: " + currentDate);
    }
    
    /**
     * 获取指定APP今天的休闲版关闭次数
     */
    public int getAppCasualCloseCount(Const.SupportedApp app) {
        String currentDate = getCurrentDate();
        String countKey = KEY_APP_CASUAL_CLOSE_COUNT + app.name();
        String dateKey = KEY_APP_LAST_CASUAL_CLOSE_DATE + app.name();
        String lastDate = prefs.getString(dateKey, "");
        
        if (currentDate.equals(lastDate)) {
            return prefs.getInt(countKey, 0);
        }
        
        // 如果不是同一天，返回0
        return 0;
    }
    
    /**
     * 获取指定APP今天的休闲版关闭次数 - 支持自定义APP
     */
    public int getAppCasualCloseCount(Object app) {
        String packageName = getPackageName(app);
        if (packageName == null) return 0;
        
        String currentDate = getCurrentDate();
        String countKey = KEY_APP_CASUAL_CLOSE_COUNT + packageName;
        String dateKey = KEY_APP_LAST_CASUAL_CLOSE_DATE + packageName;
        String lastDate = prefs.getString(dateKey, "");
        
        if (currentDate.equals(lastDate)) {
            return prefs.getInt(countKey, 0);
        }
        
        // 如果不是同一天，返回0
        return 0;
    }
    
    /**
     * 记录指定APP的悬浮窗关闭时间和使用的时间间隔
     */
    public void recordAppCloseTime(Const.SupportedApp app, int intervalSeconds) {
        String timeKey = KEY_APP_LAST_CLOSE_TIME + app.name();
        String intervalKey = KEY_APP_LAST_CLOSE_INTERVAL + app.name();
        long currentTime = System.currentTimeMillis();
        prefs.edit()
            .putLong(timeKey, currentTime)
            .putInt(intervalKey, intervalSeconds)
            .apply();
        android.util.Log.d("SettingsManager", "记录APP " + app.name() + " 关闭时间: " + formatTime(currentTime) + ", 使用间隔: " + intervalSeconds + "秒");
    }
    
    /**
     * 记录指定APP的悬浮窗关闭时间和使用的时间间隔 - 支持自定义APP
     */
    public void recordAppCloseTime(Object app, int intervalSeconds) {
        String packageName = getPackageName(app);
        if (packageName == null) return;
        
        String timeKey = KEY_APP_LAST_CLOSE_TIME + packageName;
        String intervalKey = KEY_APP_LAST_CLOSE_INTERVAL + packageName;
        long currentTime = System.currentTimeMillis();
        prefs.edit()
            .putLong(timeKey, currentTime)
            .putInt(intervalKey, intervalSeconds)
            .apply();
        android.util.Log.d("SettingsManager", "记录APP " + packageName + " 关闭时间: " + formatTime(currentTime) + ", 使用间隔: " + intervalSeconds + "秒");
    }
    
    /**
     * 获取指定APP的上次关闭时间
     */
    public long getAppLastCloseTime(Const.SupportedApp app) {
        String key = KEY_APP_LAST_CLOSE_TIME + app.name();
        return prefs.getLong(key, 0);
    }
    
    /**
     * 获取指定APP的上次关闭时间 - 支持自定义APP
     */
    public long getAppLastCloseTime(Object app) {
        String packageName = getPackageName(app);
        if (packageName == null) return 0;
        
        String key = KEY_APP_LAST_CLOSE_TIME + packageName;
        return prefs.getLong(key, 0);
    }
    
    /**
     * 获取指定APP上次关闭时使用的时间间隔（秒）
     */
    public int getAppLastCloseInterval(Const.SupportedApp app) {
        String key = KEY_APP_LAST_CLOSE_INTERVAL + app.name();
        return prefs.getInt(key, dailyIntervalArray[0]);
    }
    
    /**
     * 获取指定APP上次关闭时使用的时间间隔（秒）- 支持自定义APP
     */
    public int getAppLastCloseInterval(Object app) {
        String packageName = getPackageName(app);
        if (packageName == null) return dailyIntervalArray[0];
        
        String key = KEY_APP_LAST_CLOSE_INTERVAL + packageName;
        return prefs.getInt(key, dailyIntervalArray[0]);
    }
    
    /**
     * 计算指定APP的剩余可用时间（毫秒）
     * @param app 指定的APP
     * @return 剩余时间（毫秒），如果可以自由使用则返回-1
     */
    public long getAppRemainingTime(Const.SupportedApp app) {
        // 如果悬浮窗正在显示，且是当前APP，则不可用
        if (Share.isFloatingWindowVisible && app == Share.currentApp) {
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
        long intervalMillis = intervalSeconds * 1000L;
        long nextAvailableTime = lastCloseTime + intervalMillis;
        
//        android.util.Log.d("SettingsManager", "APP " + app.name() + " 倒计时计算:");
//        android.util.Log.d("SettingsManager", "  上次关闭时间: " + formatTime(lastCloseTime));
//        android.util.Log.d("SettingsManager", "  当前时间: " + formatTime(currentTime));
//        android.util.Log.d("SettingsManager", "  记录的时间间隔: " + intervalSeconds + "秒");
//        android.util.Log.d("SettingsManager", "  下次可用时间: " + formatTime(nextAvailableTime));
//        android.util.Log.d("SettingsManager", "  当前设置的时间间隔: " + getAppAutoShowInterval(app) + "秒");
        
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
     * 计算指定APP的剩余可用时间（毫秒）- 支持自定义APP
     * @param app 指定的APP
     * @return 剩余时间（毫秒），如果可以自由使用则返回-1
     */
    public long getAppRemainingTime(Object app) {
        String packageName = getPackageName(app);
        if (packageName == null) return -1;
        
        // 如果悬浮窗正在显示，且是当前APP，则不可用
        if (Share.isFloatingWindowVisible && packageName.equals(getPackageName(Share.currentApp))) {
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
        long intervalMillis = intervalSeconds * 1000L;
        long nextAvailableTime = lastCloseTime + intervalMillis;
        
        if (currentTime >= nextAvailableTime) {
            // 已经超过等待时间，可以自由使用
            return -1;
        } else {
            // 还在等待期间，返回剩余时间
            long remainingTime = nextAvailableTime - currentTime;
            android.util.Log.d("SettingsManager", "APP " + packageName + " 剩余时间: " + remainingTime + "毫秒 (" + (remainingTime/1000) + "秒)");
            return remainingTime;
        }
    }
    
    /**
     * 判断指定APP是否可以自由使用
     */
    public boolean isAppFreeToUse(Const.SupportedApp app) {
        return getAppRemainingTime(app) == -1;
    }
    
    /**
     * 判断指定APP是否可以自由使用 - 支持自定义APP
     */
    public boolean isAppFreeToUse(Object app) {
        return getAppRemainingTime(app) == -1;
    }
    
    /**
     * 获取APP的包名
     */
    private String getPackageName(Object app) {
        if (app instanceof Const.SupportedApp) {
            return ((Const.SupportedApp) app).getPackageName();
        } else if (app instanceof Const.CustomApp) {
            return ((Const.CustomApp) app).getPackageName();
        }
        return null;
    }
    
    /**
     * 格式化剩余时间为MM:SS格式
     */
    public static String formatRemainingTime(long remainingMillis) {
        if (remainingMillis <= 0) {
            return "00:00";
        }
        
        int totalSeconds = (int) (remainingMillis / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        
        return String.format("%02d:%02d", minutes, seconds);
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
            return "com.xingin.xhs".equals(packageName); // 小红书默认开启
        }
        return isEnabled;
    }
} 