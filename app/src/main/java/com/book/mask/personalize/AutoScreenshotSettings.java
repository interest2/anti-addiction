package com.book.mask.personalize;

import com.tencent.mmkv.MMKV;

/** 自动截图的持久化开关和最近一次执行结果。 */
public final class AutoScreenshotSettings {
    private static final String KEY_ENABLED = "auto_screenshot_enabled";
    private static final String KEY_LAST_RESULT = "auto_screenshot_last_result";
    private static final String KEY_LAST_RESULT_AT = "auto_screenshot_last_result_at";
    private static final String KEY_LAST_ERROR = "auto_screenshot_last_error";

    public static final int RESULT_NONE = 0;
    public static final int RESULT_SUCCESS = 1;
    public static final int RESULT_FAILURE = 2;

    private final MMKV storage;

    public AutoScreenshotSettings() {
        storage = SettingsStorage.open();
    }

    public boolean isEnabled() {
        return storage.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        storage.putBoolean(KEY_ENABLED, enabled).commit();
        if (enabled) {
            clearLastResult();
        }
    }

    public int getLastResult() {
        return storage.getInt(KEY_LAST_RESULT, RESULT_NONE);
    }

    public long getLastResultAt() {
        return storage.getLong(KEY_LAST_RESULT_AT, 0L);
    }

    public String getLastError() {
        return storage.getString(KEY_LAST_ERROR, "");
    }

    public void recordSuccess(long timestamp) {
        storage.putInt(KEY_LAST_RESULT, RESULT_SUCCESS)
                .putLong(KEY_LAST_RESULT_AT, timestamp)
                .remove(KEY_LAST_ERROR)
                .commit();
    }

    public void recordFailure(long timestamp, String error) {
        storage.putInt(KEY_LAST_RESULT, RESULT_FAILURE)
                .putLong(KEY_LAST_RESULT_AT, timestamp)
                .putString(KEY_LAST_ERROR, error == null ? "未知错误" : error)
                .commit();
    }

    private void clearLastResult() {
        storage.removeValuesForKeys(new String[]{
                KEY_LAST_RESULT,
                KEY_LAST_RESULT_AT,
                KEY_LAST_ERROR
        });
    }
}
