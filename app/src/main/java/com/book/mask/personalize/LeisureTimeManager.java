package com.book.mask.personalize;

import android.content.Context;

import com.book.mask.util.DateUtils;
import com.tencent.mmkv.MMKV;

/**
 * 管理休闲时刻的配置、每日额度和运行状态。
 */
public class LeisureTimeManager {

    // 宽松模式范围
    public static final int LEISURE_DURATION_MIN_MINUTES = 10;
    public static final int LEISURE_DURATION_MAX_MINUTES = 30;
    public static final int LEISURE_DAILY_COUNT_MIN = 1;
    public static final int LEISURE_DAILY_COUNT_MAX = 2;

    // 严格模式范围
    public static final int STRICT_LEISURE_DURATION_MIN_MINUTES = 1;
    public static final int STRICT_LEISURE_DURATION_MAX_MINUTES = 2;
    public static final int STRICT_LEISURE_DAILY_COUNT_MIN = 1;
    public static final int STRICT_LEISURE_DAILY_COUNT_MAX = 3;

    private static final int DEFAULT_LEISURE_DURATION_MINUTES = 15;
    private static final int DEFAULT_LEISURE_DAILY_COUNT = 2;
    private static final int DEFAULT_STRICT_LEISURE_DURATION_MINUTES = 2;
    private static final int DEFAULT_STRICT_LEISURE_DAILY_COUNT = 2;

    static final String KEY_LEISURE_DURATION_MINUTES = "leisure_duration_minutes";
    static final String KEY_LEISURE_DAILY_COUNT = "leisure_daily_count";
    static final String KEY_LEISURE_USED_COUNT = "leisure_used_count";
    static final String KEY_LEISURE_LAST_USED_DATE = "leisure_last_used_date";
    static final String KEY_STRICT_LEISURE_DURATION_MINUTES =
            "strict_leisure_duration_minutes";
    static final String KEY_STRICT_LEISURE_DAILY_COUNT = "strict_leisure_daily_count";
    static final String KEY_STRICT_LEISURE_USED_COUNT = "strict_leisure_used_count";
    static final String KEY_STRICT_LEISURE_LAST_USED_DATE =
            "strict_leisure_last_used_date";
    static final String KEY_LEISURE_ACTIVE_UNTIL = "leisure_active_until";
    static final String KEY_LEISURE_ACTIVE_PACKAGE = "leisure_active_package";
    static final String KEY_STRICT_LEISURE_ACTIVE_UNTIL =
            "strict_leisure_active_until";
    static final String KEY_STRICT_LEISURE_ACTIVE_PACKAGE =
            "strict_leisure_active_package";
    static final String KEY_LEISURE_ARMED = "leisure_armed";
    static final String KEY_LEISURE_ARMED_MODE = "leisure_armed_mode";

    public enum LeisureMode {
        RELAXED("relaxed"),
        STRICT("strict");

        private final String preferenceValue;

        LeisureMode(String preferenceValue) {
            this.preferenceValue = preferenceValue;
        }

        private static LeisureMode fromPreferenceValue(String value) {
            return STRICT.preferenceValue.equals(value) ? STRICT : RELAXED;
        }
    }

    private final MMKV mmkv;

    public LeisureTimeManager(Context context) {
        mmkv = SettingsStorage.open();
    }

    public static boolean isValidLeisureDurationMinutes(
            LeisureMode mode, int durationMinutes) {
        return durationMinutes >= getLeisureDurationMinMinutes(mode)
                && durationMinutes <= getLeisureDurationMaxMinutes(mode);
    }

    public static boolean isValidLeisureDailyCount(LeisureMode mode, int dailyCount) {
        return dailyCount >= getLeisureDailyCountMin(mode)
                && dailyCount <= getLeisureDailyCountMax(mode);
    }

    public static int getLeisureDurationMaxMinutes(LeisureMode mode) {
        return mode == LeisureMode.STRICT
                ? STRICT_LEISURE_DURATION_MAX_MINUTES
                : LEISURE_DURATION_MAX_MINUTES;
    }

    public static int getLeisureDailyCountMax(LeisureMode mode) {
        return mode == LeisureMode.STRICT
                ? STRICT_LEISURE_DAILY_COUNT_MAX
                : LEISURE_DAILY_COUNT_MAX;
    }

    public static String getLeisureDurationRangeText(LeisureMode mode) {
        return getLeisureDurationMinMinutes(mode) + "-" + getLeisureDurationMaxMinutes(mode);
    }

    public static String getLeisureDailyCountRangeText(LeisureMode mode) {
        return getLeisureDailyCountMin(mode) + "-" + getLeisureDailyCountMax(mode);
    }

    private static int getLeisureDurationMinMinutes(LeisureMode mode) {
        return mode == LeisureMode.STRICT
                ? STRICT_LEISURE_DURATION_MIN_MINUTES
                : LEISURE_DURATION_MIN_MINUTES;
    }

    private static int getLeisureDailyCountMin(LeisureMode mode) {
        return mode == LeisureMode.STRICT
                ? STRICT_LEISURE_DAILY_COUNT_MIN
                : LEISURE_DAILY_COUNT_MIN;
    }

    /**
     * 保存休闲时刻设置。
     */
    public void setLeisureTimeSettings(
            LeisureMode mode, int durationMinutes, int dailyCount) {
        if (!isValidLeisureDurationMinutes(mode, durationMinutes)) {
            throw new IllegalArgumentException(
                    "休闲时刻时长必须在" + getLeisureDurationRangeText(mode) + "分钟之间");
        }
        if (!isValidLeisureDailyCount(mode, dailyCount)) {
            throw new IllegalArgumentException(
                    "休闲时刻次数必须在" + getLeisureDailyCountRangeText(mode) + "次之间");
        }

        mmkv.putInt(getLeisureDurationKey(mode), durationMinutes)
                .putInt(getLeisureDailyCountKey(mode), dailyCount)
                .commit();
    }

    public int getLeisureDurationMinutes(LeisureMode mode) {
        int durationMinutes = mmkv.getInt(
                getLeisureDurationKey(mode),
                mode == LeisureMode.STRICT
                        ? DEFAULT_STRICT_LEISURE_DURATION_MINUTES
                        : DEFAULT_LEISURE_DURATION_MINUTES);
        return Math.max(
                getLeisureDurationMinMinutes(mode),
                Math.min(durationMinutes, getLeisureDurationMaxMinutes(mode)));
    }

    public int getLeisureDailyCount(LeisureMode mode) {
        int dailyCount = mmkv.getInt(
                getLeisureDailyCountKey(mode),
                mode == LeisureMode.STRICT
                        ? DEFAULT_STRICT_LEISURE_DAILY_COUNT
                        : DEFAULT_LEISURE_DAILY_COUNT);
        return Math.max(
                getLeisureDailyCountMin(mode),
                Math.min(dailyCount, getLeisureDailyCountMax(mode)));
    }

    public int getLeisureUsedCountToday(LeisureMode mode) {
        String lastUsedDate = mmkv.getString(getLeisureLastUsedDateKey(mode), "");
        if (!DateUtils.getCurrentDate().equals(lastUsedDate)) {
            return 0;
        }
        return mmkv.getInt(getLeisureUsedCountKey(mode), 0);
    }

    public int getLeisureRemainingCountToday(LeisureMode mode) {
        return Math.max(0, getLeisureDailyCount(mode) - getLeisureUsedCountToday(mode));
    }

    public long getLeisureTimeRemainingMillis() {
        long relaxedRemaining = getLeisureTimeRemainingMillis(LeisureMode.RELAXED);
        long strictRemaining = getLeisureTimeRemainingMillis(LeisureMode.STRICT);
        if (relaxedRemaining == 0) {
            return strictRemaining;
        }
        if (strictRemaining == 0) {
            return relaxedRemaining;
        }
        return Math.min(relaxedRemaining, strictRemaining);
    }

    public long getLeisureTimeRemainingMillisForApp(String packageName) {
        long remainingMillis = 0;
        for (LeisureMode mode : LeisureMode.values()) {
            if (isLeisureTimeActiveForApp(mode, packageName)) {
                remainingMillis = Math.max(
                        remainingMillis, getLeisureTimeRemainingMillis(mode));
            }
        }
        return remainingMillis;
    }

    public boolean isLeisureTimeActive(LeisureMode mode) {
        return getLeisureTimeRemainingMillis(mode) > 0;
    }

    public boolean isLeisureTimeActiveForApp(String packageName) {
        return isLeisureTimeActiveForApp(LeisureMode.RELAXED, packageName)
                || isLeisureTimeActiveForApp(LeisureMode.STRICT, packageName);
    }

    public boolean isLeisureTimeArmed() {
        return mmkv.getBoolean(KEY_LEISURE_ARMED, false);
    }

    public boolean isLeisureTimeArmed(LeisureMode mode) {
        return isLeisureTimeArmed() && getArmedLeisureMode() == mode;
    }

    /**
     * 获取当前待触发休闲时刻配置的解禁时长；没有待触发模式时返回 {@code null}。
     */
    public Integer getArmedLeisureDurationMinutes() {
        if (!isLeisureTimeArmed()) {
            return null;
        }
        return getLeisureDurationMinutes(getArmedLeisureMode());
    }

    /**
     * 尝试开启一次休闲时刻。此时只进入待触发状态，关闭悬浮窗后才开始计时并消耗次数。
     *
     * @return 当前未开启、今日仍有次数并成功开启时返回 true
     */
    public boolean tryStartLeisureTime(LeisureMode mode) {
        synchronized (LeisureTimeManager.class) {
            if (isLeisureTimeActive(mode)) {
                return false;
            }

            if (getLeisureRemainingCountToday(mode) <= 0) {
                return false;
            }

            mmkv.putString(KEY_LEISURE_ARMED_MODE, mode.preferenceValue)
                    .putBoolean(KEY_LEISURE_ARMED, true)
                    .commit();
            return true;
        }
    }

    /**
     * 取消尚未通过关闭悬浮窗激活的休闲时刻。
     */
    public boolean cancelPendingLeisureTime(LeisureMode mode) {
        synchronized (LeisureTimeManager.class) {
            if (!isLeisureTimeArmed(mode)) {
                return false;
            }
            mmkv.putBoolean(KEY_LEISURE_ARMED, false).commit();
            return true;
        }
    }

    /**
     * 关闭悬浮窗并正式开始休闲时刻。每段休闲时刻只在首次关闭时消耗一次。
     *
     * @return 成功绑定当前 APP 的模式；没有待触发模式时返回 null
     */
    public LeisureMode activateLeisureTimeForClose(String packageName) {
        synchronized (LeisureTimeManager.class) {
            if (packageName == null || !isLeisureTimeArmed()) {
                return null;
            }
            LeisureMode mode = getArmedLeisureMode();
            if (isLeisureTimeActive(mode)) {
                mmkv.putBoolean(KEY_LEISURE_ARMED, false).commit();
                return null;
            }
            if (getLeisureRemainingCountToday(mode) <= 0) {
                mmkv.putBoolean(KEY_LEISURE_ARMED, false).commit();
                return null;
            }

            String currentDate = DateUtils.getCurrentDate();
            String lastUsedDate = mmkv.getString(getLeisureLastUsedDateKey(mode), "");
            int usedCount = currentDate.equals(lastUsedDate)
                    ? mmkv.getInt(getLeisureUsedCountKey(mode), 0)
                    : 0;

            mmkv.putInt(getLeisureUsedCountKey(mode), usedCount + 1)
                    .putString(getLeisureLastUsedDateKey(mode), currentDate)
                    .putLong(
                            getLeisureActiveUntilKey(mode),
                            System.currentTimeMillis()
                                    + getLeisureDurationMinutes(mode) * 60_000L)
                    .putString(getLeisureActivePackageKey(mode), packageName)
                    .putBoolean(KEY_LEISURE_ARMED, false)
                    .commit();
            return mode;
        }
    }

    private LeisureMode getArmedLeisureMode() {
        return LeisureMode.fromPreferenceValue(mmkv.getString(
                KEY_LEISURE_ARMED_MODE, LeisureMode.RELAXED.preferenceValue));
    }

    private long getLeisureTimeRemainingMillis(LeisureMode mode) {
        long remainingMillis = mmkv.getLong(getLeisureActiveUntilKey(mode), 0)
                - System.currentTimeMillis();
        return Math.max(remainingMillis, 0);
    }

    private boolean isLeisureTimeActiveForApp(LeisureMode mode, String packageName) {
        return packageName != null
                && isLeisureTimeActive(mode)
                && packageName.equals(mmkv.getString(getLeisureActivePackageKey(mode), ""));
    }

    private static String getLeisureDurationKey(LeisureMode mode) {
        return mode == LeisureMode.STRICT
                ? KEY_STRICT_LEISURE_DURATION_MINUTES
                : KEY_LEISURE_DURATION_MINUTES;
    }

    private static String getLeisureDailyCountKey(LeisureMode mode) {
        return mode == LeisureMode.STRICT
                ? KEY_STRICT_LEISURE_DAILY_COUNT
                : KEY_LEISURE_DAILY_COUNT;
    }

    private static String getLeisureUsedCountKey(LeisureMode mode) {
        return mode == LeisureMode.STRICT
                ? KEY_STRICT_LEISURE_USED_COUNT
                : KEY_LEISURE_USED_COUNT;
    }

    private static String getLeisureLastUsedDateKey(LeisureMode mode) {
        return mode == LeisureMode.STRICT
                ? KEY_STRICT_LEISURE_LAST_USED_DATE
                : KEY_LEISURE_LAST_USED_DATE;
    }

    private static String getLeisureActiveUntilKey(LeisureMode mode) {
        return mode == LeisureMode.STRICT
                ? KEY_STRICT_LEISURE_ACTIVE_UNTIL
                : KEY_LEISURE_ACTIVE_UNTIL;
    }

    private static String getLeisureActivePackageKey(LeisureMode mode) {
        return mode == LeisureMode.STRICT
                ? KEY_STRICT_LEISURE_ACTIVE_PACKAGE
                : KEY_LEISURE_ACTIVE_PACKAGE;
    }
}
