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
    
    // 每个APP独立的悬浮窗警示文字来源相关
    private static final String KEY_APP_HINT_SOURCE = "app_hint_source_";
    private static final String KEY_APP_HINT_CUSTOM = "app_hint_custom_";

    // 激励语标签列表
    private static final String[] MOTIVATION_TAGS = {
            "高考", "考研", "保研", "出国升学", "跳槽", "找工作", "考公务员"
    };

    // 悬浮窗位置默认值（像素）
    private static final int DEFAULT_TOP_OFFSET = 130;
    private static final int DEFAULT_BOTTOM_OFFSET = 230;

    // 日常版时间间隔（秒）
    // 休闲版时间间隔（秒）
//    private static final int[] dailyIntervalArray = {3, 5};
//    private static final int[] casualIntervalArray = {20, 30};
    private static final int[] dailyIntervalArray = {20, 60};
    private static final int[] casualIntervalArray = {600, 900};

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
        return prefs.getString(KEY_MOTIVATION_TAG, Const.TARGET_TO_BE_SET);
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
        return prefs.getString(KEY_TARGET_COMPLETION_DATE, Const.TARGET_TO_BE_SET);
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
        int defaultIndex = 1;
        if (packageName == null) return dailyIntervalArray[defaultIndex];
        
        String key = KEY_APP_AUTO_SHOW_INTERVAL + packageName;
        return prefs.getInt(key, dailyIntervalArray[defaultIndex]);
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
     * 触发立即检查是否需要显示悬浮窗 - 支持自定义APP
     */
    private void triggerImmediateFloatingWindowCheck(Object app) {
        try {
            // 通过静态方法通知无障碍服务
            Class<?> serviceClass = Class.forName("com.book.baisc.floating.FloatService");
            java.lang.reflect.Method method = serviceClass.getMethod("triggerImmediateCheck", Object.class);
            method.invoke(null, app);
            String packageName = getPackageName(app);
            android.util.Log.d("SettingsManager", "  已通知无障碍服务立即检查APP " + packageName);
        } catch (Exception e) {
            android.util.Log.w("SettingsManager", "  无法通知无障碍服务: " + e.getMessage());
        }
    }
    
    /**
     * 获取指定APP的自动显示间隔（毫秒）- 支持自定义APP
     */
    public long getAppAutoShowIntervalMillis(Object app) {
        return getAppAutoShowInterval(app) * 1000L;
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
        
        String key = KEY_APP_CASUAL_CLOSE_COUNT + packageName;
        return prefs.getInt(key, 0);
    }

    /**
     * 设置APP的宽松模式关闭次数
     */
    public void setAppCasualCloseCount(Object app, int count) {
        String packageName = getPackageName(app);
        if (packageName == null) return;
        
        String key = KEY_APP_CASUAL_CLOSE_COUNT + packageName;
        prefs.edit().putInt(key, count).apply();
    }

    /**
     * 获取预定义APP的自定义次数设置
     */
    public Integer getCustomCasualLimitCount(String packageName) {
        String key = "custom_casual_limit_" + packageName;
        int value = prefs.getInt(key, -1);
        return value == -1 ? null : value; // 返回null表示使用默认值
    }

    /**
     * 设置预定义APP的自定义次数设置
     */
    public void setCustomCasualLimitCount(String packageName, int count) {
        String key = "custom_casual_limit_" + packageName;
        prefs.edit().putInt(key, count).apply();
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
            return Share.judgeEnabled(packageName); // 小红书默认开启
        }
        return isEnabled;
    }

    // ===== 悬浮窗警示文字来源相关方法 =====
    
    /**
     * 设置指定APP的悬浮窗警示文字来源
     */
    public void setAppHintSource(String packageName, String source) {
        prefs.edit().putString(KEY_APP_HINT_SOURCE + packageName, source).apply();
        android.util.Log.d("SettingsManager", "设置APP " + packageName + " 悬浮窗警示文字来源: " + source);
    }
    
    /**
     * 获取指定APP的悬浮窗警示文字来源
     */
    public String getAppHintSource(String packageName) {
        return prefs.getString(KEY_APP_HINT_SOURCE + packageName, Const.DEFAULT_HINT_SOURCE);
    }
    
    /**
     * 设置指定APP的自定义悬浮窗警示文字
     */
    public void setAppHintCustomText(String packageName, String customText) {
        prefs.edit().putString(KEY_APP_HINT_CUSTOM + packageName, customText).apply();
        android.util.Log.d("SettingsManager", "设置APP " + packageName + " 自定义悬浮窗警示文字: " + customText);
    }
    
    /**
     * 获取指定APP的自定义悬浮窗警示文字
     */
    public String getAppHintCustomText(String packageName) {
        return prefs.getString(KEY_APP_HINT_CUSTOM + packageName, "");
    }
    
    // 算术题难度设置相关常量
    private static final String KEY_MATH_DIFFICULTY_MODE = "math_difficulty_mode";
    private static final String KEY_MATH_ADDITION_DIGITS = "math_addition_digits";
    private static final String KEY_MATH_SUBTRACTION_DIGITS = "math_subtraction_digits";
    private static final String KEY_MATH_MULTIPLICATION_MULTIPLIER_DIGITS = "math_multiplication_multiplier_digits";
    private static final String KEY_MATH_MULTIPLICATION_MULTIPLICAND_DIGITS = "math_multiplication_multiplicand_digits";
    
    // 默认数字位数
    public static final int DEFAULT_ADD_DIGITS = 4;
    public static final int DEFAULT_SUBTRACT_DIGITS = 4;
    public static final int DEFAULT_MULTIPLIER_FIRST_DIGITS = 2;
    public static final int DEFAULT_MULTIPLIER_SECOND_DIGITS = 2;

    /**
     * 设置算术题难度模式
     * @param mode "default" 或 "custom"
     */
    public void setMathDifficultyMode(String mode) {
        android.util.Log.d("SettingsManager", "设置难度模式: " + mode);
        prefs.edit().putString(KEY_MATH_DIFFICULTY_MODE, mode).apply();
        android.util.Log.d("SettingsManager", "难度模式设置完成");
    }

    public String getMathDifficultyMode() {
        String mode = prefs.getString(KEY_MATH_DIFFICULTY_MODE, "default");
        android.util.Log.d("SettingsManager", "获取难度模式: " + mode);
        return mode;
    }

    /**
     * 设置加法数字位数
     */
    public void setMathAdditionDigits(int digits) {
        prefs.edit().putInt(KEY_MATH_ADDITION_DIGITS, digits).apply();
    }

    /**
     * 获取加法数字位数
     */
    public int getMathAdditionDigits() {
        return prefs.getInt(KEY_MATH_ADDITION_DIGITS, DEFAULT_ADD_DIGITS);
    }

    /**
     * 设置减法数字位数
     */
    public void setMathSubtractionDigits(int digits) {
        prefs.edit().putInt(KEY_MATH_SUBTRACTION_DIGITS, digits).apply();
    }

    /**
     * 获取减法数字位数
     */
    public int getMathSubtractionDigits() {
        return prefs.getInt(KEY_MATH_SUBTRACTION_DIGITS, DEFAULT_SUBTRACT_DIGITS);
    }

    /**
     * 设置乘法乘数位数
     */
    public void setMathMultiplicationMultiplierDigits(int digits) {
        prefs.edit().putInt(KEY_MATH_MULTIPLICATION_MULTIPLIER_DIGITS, digits).apply();
    }

    /**
     * 获取乘法乘数位数
     */
    public int getMathMultiplicationMultiplierDigits() {
        return prefs.getInt(KEY_MATH_MULTIPLICATION_MULTIPLIER_DIGITS, DEFAULT_MULTIPLIER_FIRST_DIGITS);
    }

    /**
     * 设置乘法被乘数位数
     */
    public void setMathMultiplicationMultiplicandDigits(int digits) {
        prefs.edit().putInt(KEY_MATH_MULTIPLICATION_MULTIPLICAND_DIGITS, digits).apply();
    }

    /**
     * 获取乘法被乘数位数
     */
    public int getMathMultiplicationMultiplicandDigits() {
        return prefs.getInt(KEY_MATH_MULTIPLICATION_MULTIPLICAND_DIGITS, DEFAULT_MULTIPLIER_SECOND_DIGITS);
    }


}