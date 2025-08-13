package com.book.mask.config;

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
    private static final String KEY_RELAXED_CLOSE_COUNT = "relaxed_close_count";
    private static final String KEY_LAST_RELAXED_CLOSE_DATE = "last_relaxed_close_date";
    private static final String KEY_MOTIVATION_TAG = "motivation_tag";
    private static final String KEY_TARGET_COMPLETION_DATE = "target_completion_date";
    private static final String KEY_FLOATING_TOP_OFFSET = "floating_top_offset";
    private static final String KEY_FLOATING_BOTTOM_OFFSET = "floating_bottom_offset";
    
    // 每个APP独立的设置键名前缀
    private static final String KEY_DEFAULT_SHOW_INTERVAL = "default_show_interval";
    private static final String KEY_APP_SHOW_INTERVAL = "app_show_interval_";
    private static final String KEY_APP_RELAXED_CLOSE_COUNT = "app_relaxed_close_count_";
    private static final String KEY_APP_LAST_RELAXED_CLOSE_DATE = "app_last_relaxed_close_date_";
    private static final String KEY_APP_LAST_CLOSE_TIME = "app_last_close_time_";
    private static final String KEY_APP_LAST_CLOSE_INTERVAL = "app_last_close_interval_";
    
    // 每个APP独立的悬浮窗警示文字来源相关
    private static final String KEY_APP_HINT_SOURCE = "app_hint_source_";
    private static final String KEY_APP_HINT_CUSTOM = "app_hint_custom_";

    // 悬浮窗额外显示日常提醒
    private static final String KEY_FLOATING_STRICT_REMINDER = "floating_strict_reminder";
    private static final String KEY_FLOATING_STRICT_REMINDER_SETTINGS_CLICKED = "floating_strict_reminder_settings_clicked";

    // 个人目标标签列表
    private static final String[] MOTIVATION_TAGS = {
            "高考", "考研", "保研", "出国升学", "跳槽", "找工作", "考公务员"
    };

    // 悬浮窗位置默认值（像素）
    private static final int DEFAULT_TOP_OFFSET = 130;
    private static final int DEFAULT_BOTTOM_OFFSET = 230;

//    private static final int[] strictIntervalArray = {5, 10, 20};
//    private static final int[] relaxedIntervalArray = {60, 90, 120};

    // 严格模式默认的 interval 在数组的索引
    private static final int DEFAULT_STRICT_INDEX = 2;
    // 严格、宽松模式的各选项
    private static final int[] strictIntervalArray = {30, 60, 120};
    private static final int[] relaxedIntervalArray = {900, 1320, 1800};

    private SharedPreferences prefs;

    public SettingsManager(Context context) {
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
     * 获取今天的休闲版关闭次数
     */
    public int getRelaxedCloseCount() {
        String currentDate = getCurrentDate();
        String lastDate = prefs.getString(KEY_LAST_RELAXED_CLOSE_DATE, "");
        
        if (currentDate.equals(lastDate)) {
            return prefs.getInt(KEY_RELAXED_CLOSE_COUNT, 0);
        }
        
        // 如果不是同一天，返回0
        return 0;
    }

    /**
     * 获取日常版可选的时间间隔列表
     */
    public static int[] getStrictIntervals() {
        return strictIntervalArray;
    }
    
    /**
     * 获取休闲版可选的时间间隔列表
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
        return prefs.getInt(KEY_DEFAULT_SHOW_INTERVAL, strictIntervalArray[DEFAULT_STRICT_INDEX]);
    }

    /**
     * 获取指定APP的自动显示间隔（秒）
     */
    public int getAppInterval(CustomApp app) {
        String key = KEY_APP_SHOW_INTERVAL + app.getPackageName();
        return prefs.getInt(key, strictIntervalArray[DEFAULT_STRICT_INDEX]);
    }

    /**
     * 设置指定APP的自动显示间隔（秒）
     */
    public void setAppInterval(CustomApp app, int seconds) {
        String packageName = app.getPackageName();
        if (packageName == null) return;
        
        String key = KEY_APP_SHOW_INTERVAL + packageName;
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
     * 判断指定APP当前是否是休闲版模式
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
     * 增加指定APP的休闲版关闭次数，并处理每日重置
     */
    public void incrementAppRelaxedCloseCount(CustomApp app) {
        String packageName = app.getPackageName();
        if (packageName == null) return;
        
        String currentDate = getCurrentDate();
        String countKey = KEY_APP_RELAXED_CLOSE_COUNT + packageName;
        String dateKey = KEY_APP_LAST_RELAXED_CLOSE_DATE + packageName;
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
    public int getAppRelaxedCloseCount(CustomApp app) {
        String currentDate = getCurrentDate();
        String countKey = KEY_APP_RELAXED_CLOSE_COUNT + app.getPackageName();
        String dateKey = KEY_APP_LAST_RELAXED_CLOSE_DATE + app.getPackageName();
        String lastDate = prefs.getString(dateKey, "");
        
        if (currentDate.equals(lastDate)) {
            return prefs.getInt(countKey, 0);
        }
        
        // 如果不是同一天，返回0
        return 0;
    }
    
    /**
     * 设置指定APP的休闲版关闭次数
     */
    public void setAppRelaxedCloseCount(CustomApp app, int count) {
        String countKey = KEY_APP_RELAXED_CLOSE_COUNT + app.getPackageName();
        prefs.edit().putInt(countKey, count).apply();
    }
    
    /**
     * 记录指定APP的悬浮窗关闭时间和使用的时间间隔
     */
    public void recordAppCloseTime(CustomApp app, int intervalSeconds) {
        String packageName = app.getPackageName();
        if (packageName == null) return;
        
        String timeKey = KEY_APP_LAST_CLOSE_TIME + packageName;
        String intervalKey = KEY_APP_LAST_CLOSE_INTERVAL + packageName;
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
        String key = KEY_APP_LAST_CLOSE_TIME + app.getPackageName();
        return prefs.getLong(key, 0);
    }

    /**
     * 获取指定APP上次关闭时使用的时间间隔（秒）
     */
    public int getAppLastCloseInterval(CustomApp app) {
        String key = KEY_APP_LAST_CLOSE_INTERVAL + app.getPackageName();
        return prefs.getInt(key, strictIntervalArray[DEFAULT_STRICT_INDEX]);
    }

    /**
     * 计算指定APP的剩余可用时间（毫秒）
     * @param app 指定的APP
     * @return 剩余时间（毫秒），如果可以自由使用则返回-1
     */
    public long getAppRemainingTime(CustomApp app) {
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
        return prefs.getInt(KEY_MATH_ADDITION_DIGITS, Const.ADD_LEN_DEFAULT);
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
        return prefs.getInt(KEY_MATH_SUBTRACTION_DIGITS, Const.SUB_LEN_DEFAULT);
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
        return prefs.getInt(KEY_MATH_MULTIPLICATION_MULTIPLIER_DIGITS, Const.MUL_FIRST_LEN_DEFAULT);
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
        return prefs.getInt(KEY_MATH_MULTIPLICATION_MULTIPLICAND_DIGITS, Const.MUL_SECOND_LEN_DEFAULT);
    }

    // ===== 悬浮窗额外显示日常提醒相关方法 =====
    
    /**
     * 设置悬浮窗额外显示日常提醒文字
     */
    public void setFloatingStrictReminder(String reminder) {
        prefs.edit().putString(KEY_FLOATING_STRICT_REMINDER, reminder).apply();
        android.util.Log.d("SettingsManager", "设置悬浮窗日常提醒: " + reminder);
    }
    
    /**
     * 获取悬浮窗额外显示日常提醒文字
     */
    public String getFloatingStrictReminder() {
        return prefs.getString(KEY_FLOATING_STRICT_REMINDER, "");
    }

    /**
     * 记录用户是否点击过设置按钮
     */
    public void setFloatingStrictReminderSettingsClicked(boolean clicked) {
        prefs.edit().putBoolean(KEY_FLOATING_STRICT_REMINDER_SETTINGS_CLICKED, clicked).apply();
        android.util.Log.d("SettingsManager", "设置悬浮窗日常提醒设置按钮点击状态: " + clicked);
    }

    /**
     * 获取用户是否点击过设置按钮
     */
    public boolean getFloatingStrictReminderSettingsClicked() {
        return prefs.getBoolean(KEY_FLOATING_STRICT_REMINDER_SETTINGS_CLICKED, false);
    }

    /**
     * 设置个人目标标签
     */
    public void setMotivationTag(String tag) {
        prefs.edit().putString(KEY_MOTIVATION_TAG, tag).apply();
        Share.MOTIVATE_CHANGE = true;
    }

    /**
     * 获取个人目标标签
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
     * 获取可选的个人目标标签列表
     */
    public static String[] getAvailableTags() {
        return MOTIVATION_TAGS;
    }

    /**
     * 获取当前日期字符串 "yyyy-MM-dd"
     */
    private String getCurrentDate() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
    }

}