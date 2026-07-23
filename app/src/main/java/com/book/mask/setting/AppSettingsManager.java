package com.book.mask.setting;

import android.content.Context;

import com.book.mask.config.ChallengeType;
import com.book.mask.config.Const;
import com.book.mask.config.Share;
import com.tencent.mmkv.MMKV;

/**
 * 应用设置管理器
 * 用于管理算术题难度、悬浮窗设置、个人目标等配置参数
 */
public class AppSettingsManager {

    private static final String PREFS_NAME = "app_settings";

    // 每个APP独立的悬浮窗警示文字来源相关
    private static final String KEY_APP_HINT_SOURCE = "app_hint_source_";
    private static final String KEY_APP_HINT_CUSTOM = "app_hint_custom_";

    // 悬浮窗额外显示日常提醒
    private static final String KEY_FLOATING_STRICT_REMINDER = "floating_strict_reminder";
    private static final String KEY_FLOATING_STRICT_REMINDER_SETTINGS_CLICKED = "floating_strict_reminder_settings_clicked";
    private static final String KEY_FLOATING_STRICT_REMINDER_FONT_SIZE = "floating_strict_reminder_font_size";

    // 个人目标标签列表
    private static final String[] MOTIVATION_TAGS = {
            "高考", "考研", "保研", "出国升学", "跳槽", "找工作", "考公务员"
    };

    // 悬浮窗位置默认值（像素）
    private static final int DEFAULT_TOP_OFFSET = 130;
    private static final int DEFAULT_BOTTOM_OFFSET = 230;

    // 算术题难度设置相关常量
    private static final String KEY_MATH_DIFFICULTY_MODE = "math_difficulty_mode";
    private static final String KEY_MATH_ADDITION_DIGITS = "math_addition_digits";
    private static final String KEY_MATH_SUBTRACTION_DIGITS = "math_subtraction_digits";
    private static final String KEY_MATH_MULTIPLICATION_MULTIPLIER_DIGITS = "math_multiplication_multiplier_digits";
    private static final String KEY_MATH_MULTIPLICATION_MULTIPLICAND_DIGITS = "math_multiplication_multiplicand_digits";

    // 个人目标相关
    private static final String KEY_MOTIVATION_TAG = "motivation_tag";
    private static final String KEY_TARGET_COMPLETION_DATE = "target_completion_date";

    // 悬浮窗位置相关
    private static final String KEY_FLOATING_TOP_OFFSET = "floating_top_offset";
    private static final String KEY_FLOATING_BOTTOM_OFFSET = "floating_bottom_offset";
    private static final String KEY_APP_FLOATING_TOP_OFFSET = "app_floating_top_offset_";
    private static final String KEY_APP_FLOATING_BOTTOM_OFFSET = "app_floating_bottom_offset_";

    private MMKV mmkv;

    public AppSettingsManager(Context context) {
        mmkv = MMKV.mmkvWithID(PREFS_NAME);
    }

    // ===== 悬浮窗警示文字来源相关方法 =====

    /**
     * 设置指定APP的悬浮窗警示文字来源
     */
    public void setAppHintSource(String packageName, String source) {
        mmkv.putString(KEY_APP_HINT_SOURCE + packageName, source).commit();
        android.util.Log.d("SettingsManager", "设置APP " + packageName + " 悬浮窗警示文字来源: " + source);
    }

    /**
     * 获取指定APP的悬浮窗警示文字来源
     */
    public String getAppHintSource(String packageName) {
        return mmkv.getString(KEY_APP_HINT_SOURCE + packageName, Const.DEFAULT_HINT_SOURCE);
    }

    /**
     * 设置指定APP的自定义悬浮窗警示文字
     */
    public void setAppHintCustomText(String packageName, String customText) {
        mmkv.putString(KEY_APP_HINT_CUSTOM + packageName, customText).commit();
        android.util.Log.d("SettingsManager", "设置APP " + packageName + " 自定义悬浮窗警示文字: " + customText);
    }

    /**
     * 获取指定APP的自定义悬浮窗警示文字
     */
    public String getAppHintCustomText(String packageName) {
        return mmkv.getString(KEY_APP_HINT_CUSTOM + packageName, "");
    }

    /**
     * 清除指定APP在本管理器中的所有持久化设置（悬浮窗警示文字来源、自定义文字等）
     */
    public void clearAppSettings(String packageName) {
        if (packageName == null) return;
        mmkv.removeValueForKey(KEY_APP_HINT_SOURCE + packageName);
        mmkv.removeValueForKey(KEY_APP_HINT_CUSTOM + packageName);
        mmkv.removeValueForKey(KEY_APP_FLOATING_TOP_OFFSET + packageName);
        mmkv.removeValueForKey(KEY_APP_FLOATING_BOTTOM_OFFSET + packageName);
        android.util.Log.d("SettingsManager", "清除APP在AppSettingsManager中的所有设置: " + packageName);
    }

    // ===== 算术题难度设置相关方法 =====

    /**
     * 设置算术题难度模式
     *
     * @param mode "default" 或 "custom"
     */
    public void setMathDifficultyMode(String mode) {
        android.util.Log.d("SettingsManager", "设置难度模式: " + mode);
        mmkv.putString(KEY_MATH_DIFFICULTY_MODE, mode).commit();
        android.util.Log.d("SettingsManager", "难度模式设置完成");
    }

    public String getMathDifficultyMode() {
        String mode = mmkv.getString(KEY_MATH_DIFFICULTY_MODE, "default");
        android.util.Log.d("SettingsManager", "获取难度模式: " + mode);
        return mode;
    }

    /**
     * 设置加法数字位数
     */
    public void setMathAdditionDigits(int digits) {
        mmkv.putInt(KEY_MATH_ADDITION_DIGITS, digits).commit();
    }

    /**
     * 获取加法数字位数
     */
    public int getMathAdditionDigits() {
        return mmkv.getInt(KEY_MATH_ADDITION_DIGITS, Const.ADD_LEN_DEFAULT);
    }

    /**
     * 设置减法数字位数
     */
    public void setMathSubtractionDigits(int digits) {
        mmkv.putInt(KEY_MATH_SUBTRACTION_DIGITS, digits).commit();
    }

    /**
     * 获取减法数字位数
     */
    public int getMathSubtractionDigits() {
        return mmkv.getInt(KEY_MATH_SUBTRACTION_DIGITS, Const.SUB_LEN_DEFAULT);
    }

    /**
     * 设置乘法乘数位数
     */
    public void setMathMultiplicationMultiplierDigits(int digits) {
        mmkv.putInt(KEY_MATH_MULTIPLICATION_MULTIPLIER_DIGITS, digits).commit();
    }

    /**
     * 获取乘法乘数位数
     */
    public int getMathMultiplicationMultiplierDigits() {
        return mmkv.getInt(KEY_MATH_MULTIPLICATION_MULTIPLIER_DIGITS, Const.MUL_FIRST_LEN_DEFAULT);
    }

    /**
     * 设置乘法被乘数位数
     */
    public void setMathMultiplicationMultiplicandDigits(int digits) {
        mmkv.putInt(KEY_MATH_MULTIPLICATION_MULTIPLICAND_DIGITS, digits).commit();
    }

    /**
     * 获取乘法被乘数位数
     */
    public int getMathMultiplicationMultiplicandDigits() {
        return mmkv.getInt(KEY_MATH_MULTIPLICATION_MULTIPLICAND_DIGITS, Const.MUL_SECOND_LEN_DEFAULT);
    }

    // ===== 悬浮窗额外显示日常提醒相关方法 =====

    /**
     * 设置悬浮窗额外显示日常提醒文字
     */
    public void setFloatingStrictReminder(String reminder) {
        mmkv.putString(KEY_FLOATING_STRICT_REMINDER, reminder).commit();
        android.util.Log.d("SettingsManager", "设置悬浮窗日常提醒: " + reminder);
    }

    /**
     * 获取悬浮窗额外显示日常提醒文字
     */
    public String getFloatingStrictReminder() {
        return mmkv.getString(KEY_FLOATING_STRICT_REMINDER, "");
    }

    /**
     * 记录用户是否点击过设置按钮
     */
    public void setFloatingStrictReminderSettingsClicked(boolean clicked) {
        mmkv.putBoolean(KEY_FLOATING_STRICT_REMINDER_SETTINGS_CLICKED, clicked).commit();
        android.util.Log.d("SettingsManager", "设置悬浮窗日常提醒设置按钮点击状态: " + clicked);
    }

    /**
     * 获取用户是否点击过设置按钮
     */
    public boolean getFloatingStrictReminderSettingsClicked() {
        return mmkv.getBoolean(KEY_FLOATING_STRICT_REMINDER_SETTINGS_CLICKED, false);
    }

    /**
     * 设置悬浮窗良好习惯提醒字体大小
     */
    public void setFloatingStrictReminderFontSize(int fontSize) {
        mmkv.putInt(KEY_FLOATING_STRICT_REMINDER_FONT_SIZE, fontSize).commit();
        android.util.Log.d("SettingsManager", "设置悬浮窗良好习惯提醒字体大小: " + fontSize);
    }

    /**
     * 获取悬浮窗良好习惯提醒字体大小
     */
    public int getFloatingStrictReminderFontSize() {
        return mmkv.getInt(KEY_FLOATING_STRICT_REMINDER_FONT_SIZE, 18); // 默认18sp
    }

    // ===== 个人目标相关方法 =====

    /**
     * 设置个人目标标签
     */
    public void setMotivationTag(String tag) {
        mmkv.putString(KEY_MOTIVATION_TAG, tag).commit();
        Share.MOTIVATE_CHANGE = true;
    }

    /**
     * 获取个人目标标签
     */
    public String getMotivationTag() {
        return mmkv.getString(KEY_MOTIVATION_TAG, Const.TARGET_TO_BE_SET);
    }

    /**
     * 设置目标完成日期
     */
    public void setTargetCompletionDate(String date) {
        mmkv.putString(KEY_TARGET_COMPLETION_DATE, date).commit();
        Share.MOTIVATE_CHANGE = true;
    }

    /**
     * 获取目标完成日期
     */
    public String getTargetCompletionDate() {
        return mmkv.getString(KEY_TARGET_COMPLETION_DATE, Const.TARGET_TO_BE_SET);
    }

    /**
     * 获取可选的个人目标标签列表
     */
    public static String[] getAvailableTags() {
        return MOTIVATION_TAGS;
    }

    // ===== 悬浮窗位置相关方法 =====

    /**
     * 设置悬浮窗上边缘距离顶部的距离
     */
    public void setFloatingTopOffset(int offset) {
        mmkv.putInt(KEY_FLOATING_TOP_OFFSET, offset).commit();
    }

    /**
     * 获取悬浮窗上边缘距离顶部的距离
     */
    public int getFloatingTopOffset() {
        return mmkv.getInt(KEY_FLOATING_TOP_OFFSET, DEFAULT_TOP_OFFSET);
    }

    /**
     * 设置悬浮窗下边缘距离底部的距离
     */
    public void setFloatingBottomOffset(int offset) {
        mmkv.putInt(KEY_FLOATING_BOTTOM_OFFSET, offset).commit();
    }

    /**
     * 获取悬浮窗下边缘距离底部的距离
     */
    public int getFloatingBottomOffset() {
        return mmkv.getInt(KEY_FLOATING_BOTTOM_OFFSET, DEFAULT_BOTTOM_OFFSET);
    }

    /**
     * 设置指定APP的悬浮窗上边缘距离。
     */
    public void setAppFloatingTopOffset(String packageName, int offset) {
        mmkv.putInt(KEY_APP_FLOATING_TOP_OFFSET + packageName, offset).commit();
    }

    /**
     * 获取指定APP的悬浮窗上边缘距离，未单独设置时使用全局默认设置。
     */
    public int getAppFloatingTopOffset(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return getFloatingTopOffset();
        }
        return mmkv.getInt(KEY_APP_FLOATING_TOP_OFFSET + packageName, getFloatingTopOffset());
    }

    /**
     * 设置指定APP的悬浮窗下边缘距离。
     */
    public void setAppFloatingBottomOffset(String packageName, int offset) {
        mmkv.putInt(KEY_APP_FLOATING_BOTTOM_OFFSET + packageName, offset).commit();
    }

    /**
     * 获取指定APP的悬浮窗下边缘距离，未单独设置时使用全局默认设置。
     */
    public int getAppFloatingBottomOffset(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return getFloatingBottomOffset();
        }
        return mmkv.getInt(KEY_APP_FLOATING_BOTTOM_OFFSET + packageName, getFloatingBottomOffset());
    }

    // 题型设置相关常量
    private static final String KEY_MATH_QUESTION_TYPE = "math_question_type";

    // 英文阅读字数设置相关常量
    private static final String KEY_ENGLISH_READING_LENGTH = "english_reading_length";

    /**
     * 获取数学题题型
     */
    public ChallengeType getChallengeType() {
        String preferenceValue = mmkv.getString(
                KEY_MATH_QUESTION_TYPE, ChallengeType.MIXED.getPreferenceValue());
        ChallengeType challengeType = ChallengeType.fromPreferenceValue(preferenceValue);
        if (!Const.ENGLISH_READING_ENABLED && challengeType == ChallengeType.ENGLISH_READING) {
            return ChallengeType.MIXED;
        }
        return challengeType;
    }

    /**
     * 设置数学题题型
     */
    public void setChallengeType(ChallengeType challengeType) {
        mmkv.putString(KEY_MATH_QUESTION_TYPE, challengeType.getPreferenceValue()).commit();
    }

    /**
     * 获取英文阅读字数（确保至少为最小值200）
     */
    public int getEnglishReadingLength() {
        int length = mmkv.getInt(KEY_ENGLISH_READING_LENGTH, Const.ENGLISH_READING_LENGTH_DEFAULT);
        // 确保返回值至少为最小值
        return Math.max(length, Const.ENGLISH_READING_LENGTH_MIN);
    }

    /**
     * 设置英文阅读字数（确保至少为最小值200）
     */
    public void setEnglishReadingLength(int length) {
        // 确保设置的值至少为最小值
        int validLength = Math.max(length, Const.ENGLISH_READING_LENGTH_MIN);
        mmkv.putInt(KEY_ENGLISH_READING_LENGTH, validLength).commit();
    }
}
