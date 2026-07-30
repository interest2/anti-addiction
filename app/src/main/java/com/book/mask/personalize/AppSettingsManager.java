package com.book.mask.personalize;

import android.content.Context;

import com.book.mask.constant.Const;
import com.book.mask.config.Share;
import com.tencent.mmkv.MMKV;

/**
 * 应用设置管理器
 * 用于管理悬浮窗设置、个人目标等通用配置参数
 */
public class AppSettingsManager {

    // 每个APP独立的悬浮窗警示语来源相关
    static final String KEY_APP_HINT_SOURCE = "app_hint_source_";
    static final String KEY_APP_HINT_CUSTOM = "app_hint_custom_";

    // 悬浮窗额外显示日常提醒
    static final String KEY_FLOATING_STRICT_REMINDER = "floating_strict_reminder";
    static final String KEY_FLOATING_STRICT_REMINDER_SETTINGS_CLICKED = "floating_strict_reminder_settings_clicked";
    static final String KEY_FLOATING_STRICT_REMINDER_FONT_SIZE = "floating_strict_reminder_font_size";
    static final String KEY_FLOATING_STRICT_REMINDER_FONT_COLOR = "floating_strict_reminder_font_color";

    // 座右铭默认字体颜色（与布局 floating_window_layout.xml 中 tv_strict_reminder 一致）
    public static final int DEFAULT_STRICT_REMINDER_FONT_COLOR = 0xFFF44336;

    // 个人目标标签列表
    private static final String[] MOTIVATION_TAGS = {
            "高考", "考研", "保研", "出国升学", "跳槽", "找工作", "考公务员"
    };

    // 悬浮窗位置默认值（像素）
    private static final int DEFAULT_TOP_OFFSET = 130;
    private static final int DEFAULT_BOTTOM_OFFSET = 230;

    // 个人目标与大模型提醒风格相关
    static final String KEY_MOTIVATION_TAG = "motivation_tag";
    static final String KEY_TARGET_COMPLETION_DATE = "target_completion_date";
    static final String KEY_REMINDER_STYLE = "reminder_style";
    static final String KEY_REMINDER_CUSTOM_STYLE = "reminder_custom_style";
    public static final String DEFAULT_REMINDER_STYLE = "默认";

    // 悬浮窗位置相关
    static final String KEY_FLOATING_TOP_OFFSET = "floating_top_offset";
    static final String KEY_FLOATING_BOTTOM_OFFSET = "floating_bottom_offset";
    static final String KEY_APP_FLOATING_TOP_OFFSET = "app_floating_top_offset_";
    static final String KEY_APP_FLOATING_BOTTOM_OFFSET = "app_floating_bottom_offset_";

    private MMKV mmkv;

    public AppSettingsManager(Context context) {
        mmkv = SettingsStorage.open();
    }

    // ===== 悬浮窗警示语来源相关方法 =====

    /**
     * 设置指定APP的悬浮窗警示语来源
     */
    public void setAppHintSource(String packageName, String source) {
        mmkv.putString(KEY_APP_HINT_SOURCE + packageName, source).commit();
        android.util.Log.d("SettingsManager", "设置APP " + packageName + " 悬浮窗警示语来源: " + source);
    }

    /**
     * 获取指定APP的悬浮窗警示语来源
     */
    public String getAppHintSource(String packageName) {
        return mmkv.getString(KEY_APP_HINT_SOURCE + packageName, Const.DEFAULT_HINT_SOURCE);
    }

    /**
     * 设置指定APP的自定义悬浮窗警示语
     */
    public void setAppHintCustomText(String packageName, String customText) {
        mmkv.putString(KEY_APP_HINT_CUSTOM + packageName, customText).commit();
        android.util.Log.d("SettingsManager", "设置APP " + packageName + " 自定义悬浮窗警示语: " + customText);
    }

    /**
     * 获取指定APP的自定义悬浮窗警示语
     */
    public String getAppHintCustomText(String packageName) {
        return mmkv.getString(KEY_APP_HINT_CUSTOM + packageName, "");
    }

    /**
     * 清除指定APP在本管理器中的所有持久化设置（悬浮窗警示语来源、自定义文字等）
     */
    public void clearAppSettings(String packageName) {
        if (packageName == null) return;
        mmkv.removeValueForKey(KEY_APP_HINT_SOURCE + packageName);
        mmkv.removeValueForKey(KEY_APP_HINT_CUSTOM + packageName);
        mmkv.removeValueForKey(KEY_APP_FLOATING_TOP_OFFSET + packageName);
        mmkv.removeValueForKey(KEY_APP_FLOATING_BOTTOM_OFFSET + packageName);
        android.util.Log.d("SettingsManager", "清除APP在AppSettingsManager中的所有设置: " + packageName);
    }

    /**
     * 把指定APP在本管理器中已显式设置过的每-APP项读入快照（未设置的键保持 null）。
     */
    public void captureInto(AppSettingsSnapshot snapshot, String packageName) {
        if (packageName == null) return;
        String hintSourceKey = KEY_APP_HINT_SOURCE + packageName;
        if (mmkv.contains(hintSourceKey)) {
            snapshot.hintSource = mmkv.getString(hintSourceKey, Const.DEFAULT_HINT_SOURCE);
        }
        String hintCustomKey = KEY_APP_HINT_CUSTOM + packageName;
        if (mmkv.contains(hintCustomKey)) {
            snapshot.hintCustom = mmkv.getString(hintCustomKey, "");
        }
        String topKey = KEY_APP_FLOATING_TOP_OFFSET + packageName;
        if (mmkv.contains(topKey)) {
            snapshot.floatingTopOffset = mmkv.getInt(topKey, getFloatingTopOffset());
        }
        String bottomKey = KEY_APP_FLOATING_BOTTOM_OFFSET + packageName;
        if (mmkv.contains(bottomKey)) {
            snapshot.floatingBottomOffset = mmkv.getInt(bottomKey, getFloatingBottomOffset());
        }
    }

    /**
     * 从快照恢复指定APP的每-APP项（仅恢复删除前确实设置过的项）。
     */
    public void restoreFrom(AppSettingsSnapshot snapshot, String packageName) {
        if (packageName == null) return;
        if (snapshot.hintSource != null) {
            setAppHintSource(packageName, snapshot.hintSource);
        }
        if (snapshot.hintCustom != null) {
            setAppHintCustomText(packageName, snapshot.hintCustom);
        }
        if (snapshot.floatingTopOffset != null) {
            setAppFloatingTopOffset(packageName, snapshot.floatingTopOffset);
        }
        if (snapshot.floatingBottomOffset != null) {
            setAppFloatingBottomOffset(packageName, snapshot.floatingBottomOffset);
        }
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
        return mmkv.getInt(KEY_FLOATING_STRICT_REMINDER_FONT_SIZE, 28); // 默认28sp
    }

    /**
     * 设置悬浮窗座右铭字体颜色
     */
    public void setFloatingStrictReminderFontColor(int color) {
        mmkv.putInt(KEY_FLOATING_STRICT_REMINDER_FONT_COLOR, color).commit();
        android.util.Log.d("SettingsManager", "设置悬浮窗座右铭字体颜色: " + color);
    }

    /**
     * 获取悬浮窗座右铭字体颜色
     */
    public int getFloatingStrictReminderFontColor() {
        return mmkv.getInt(KEY_FLOATING_STRICT_REMINDER_FONT_COLOR, DEFAULT_STRICT_REMINDER_FONT_COLOR);
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

    public void setReminderStyle(String style) {
        mmkv.putString(KEY_REMINDER_STYLE, style).commit();
    }

    public String getReminderStyle() {
        return mmkv.getString(KEY_REMINDER_STYLE, DEFAULT_REMINDER_STYLE);
    }

    public void setReminderCustomStyle(String style) {
        mmkv.putString(KEY_REMINDER_CUSTOM_STYLE, style).commit();
    }

    public String getReminderCustomStyle() {
        return mmkv.getString(KEY_REMINDER_CUSTOM_STYLE, "");
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

}
