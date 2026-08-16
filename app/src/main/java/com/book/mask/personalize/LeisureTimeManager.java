package com.book.mask.personalize;

import android.content.Context;

import com.book.mask.util.DateUtils;
import com.tencent.mmkv.MMKV;

/**
 * 管理休闲时刻的配置、每日额度和运行状态。
 */
public class LeisureTimeManager {

    // 宽松模式档位：一天总共 3 次 = 大档 2 次 + 小档 1 次
    public static final int RELAXED_LARGE_DAILY_LIMIT = 2;       // 大档每日次数
    public static final int RELAXED_SHORT_DAILY_LIMIT = 1;       // 小档每日次数
    public static final int RELAXED_DAILY_TOTAL = 3;             // 每天总次数
    private static final long RELAXED_TRIGGER_COOLDOWN_MILLIS = 3 * 60 * 60_000L;

    // 严格模式固定档位：仅 2 分钟一档，每天最多 3 次
    public static final int STRICT_LEISURE_DURATION_MINUTES = 2;
    public static final int STRICT_LEISURE_DAILY_COUNT = 3;

    // 宽松模式旧配置键（改档位方案后废弃，仅保留常量避免误用）
    static final String KEY_LEISURE_DURATION_MINUTES = "leisure_duration_minutes";
    static final String KEY_LEISURE_DAILY_COUNT = "leisure_daily_count";
    static final String KEY_LEISURE_USED_COUNT = "leisure_used_count";
    static final String KEY_LEISURE_LAST_USED_DATE = "leisure_last_used_date";
    static final String KEY_LEISURE_RELAXED_TIER = "leisure_relaxed_tier";
    // 宽松模式两档时长：用户可在各档允许区间内修改
    static final String KEY_LEISURE_RELAXED_LARGE_MINUTES = "leisure_relaxed_large_minutes";
    static final String KEY_LEISURE_RELAXED_SHORT_MINUTES = "leisure_relaxed_short_minutes";
    static final String KEY_LEISURE_RELAXED_LARGE_USED = "leisure_relaxed_large_used";
    static final String KEY_LEISURE_RELAXED_SHORT_USED = "leisure_relaxed_short_used";
    static final String KEY_LEISURE_RELAXED_COOLDOWN_UNTIL =
            "leisure_relaxed_cooldown_until";
    static final String KEY_STRICT_LEISURE_USED_COUNT = "strict_leisure_used_count";
    static final String KEY_STRICT_LEISURE_LAST_USED_DATE =
            "strict_leisure_last_used_date";
    static final String KEY_LEISURE_ACTIVE_UNTIL = "leisure_active_until";
    static final String KEY_STRICT_LEISURE_ACTIVE_UNTIL =
            "strict_leisure_active_until";
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

    /** 宽松模式的休闲档位：大档（默认 30 分钟，可改 15-30）与小档（默认 15 分钟，可改 10-15）。 */
    public enum LeisureTier {
        LARGE(KEY_LEISURE_RELAXED_LARGE_MINUTES, 25, 15, 30),
        SHORT(KEY_LEISURE_RELAXED_SHORT_MINUTES, 15, 10, 15);

        private final String minutesKey;
        private final int defaultMinutes;
        private final int minMinutes;
        private final int maxMinutes;

        LeisureTier(String minutesKey, int defaultMinutes, int minMinutes, int maxMinutes) {
            this.minutesKey = minutesKey;
            this.defaultMinutes = defaultMinutes;
            this.minMinutes = minMinutes;
            this.maxMinutes = maxMinutes;
        }

        public int getMinMinutes() {
            return minMinutes;
        }

        public int getMaxMinutes() {
            return maxMinutes;
        }

        private int clampMinutes(int minutes) {
            return Math.max(minMinutes, Math.min(maxMinutes, minutes));
        }
    }

    private final MMKV mmkv;

    public LeisureTimeManager(Context context) {
        mmkv = SettingsStorage.open();
    }

    /** 休闲时刻时长：宽松模式取当前所选档位的时长、严格模式固定 2 分钟。 */
    public int getLeisureDurationMinutes(LeisureMode mode) {
        return mode == LeisureMode.RELAXED
                ? getRelaxedTierMinutes(getSelectedRelaxedTier())
                : STRICT_LEISURE_DURATION_MINUTES;
    }

    /** 休闲时刻每日次数：宽松 / 严格均为一天最多 3 次。 */
    public int getLeisureDailyCount(LeisureMode mode) {
        return mode == LeisureMode.RELAXED
                ? RELAXED_DAILY_TOTAL
                : STRICT_LEISURE_DAILY_COUNT;
    }

    public int getLeisureUsedCountToday(LeisureMode mode) {
        if (mode == LeisureMode.RELAXED) {
            return getLargeUsedCountToday() + getShortUsedCountToday();
        }
        String lastUsedDate = mmkv.getString(
                KEY_STRICT_LEISURE_LAST_USED_DATE, "");
        if (!DateUtils.getCurrentDate().equals(lastUsedDate)) {
            return 0;
        }
        return mmkv.getInt(KEY_STRICT_LEISURE_USED_COUNT, 0);
    }

    public int getLeisureRemainingCountToday(LeisureMode mode) {
        if (mode == LeisureMode.RELAXED) {
            return getLeisureLargeRemainingCountToday()
                    + getLeisureShortRemainingCountToday();
        }
        return Math.max(0, getLeisureDailyCount(mode) - getLeisureUsedCountToday(mode));
    }

    /**
     * 保存宽松模式当前选中的档位（大档 30 分钟 / 小档 15 分钟）。
     */
    public void setSelectedRelaxedTier(LeisureTier tier) {
        if (tier == null) {
            throw new IllegalArgumentException("宽松模式档位不能为 null");
        }
        mmkv.putString(KEY_LEISURE_RELAXED_TIER, tier.name()).commit();
    }

    /** 宽松模式当前选中的档位，默认大档。 */
    public LeisureTier getSelectedRelaxedTier() {
        String value = mmkv.getString(KEY_LEISURE_RELAXED_TIER, LeisureTier.LARGE.name());
        try {
            return LeisureTier.valueOf(value);
        } catch (IllegalArgumentException e) {
            return LeisureTier.LARGE;
        }
    }

    /** 宽松模式指定档位的解禁时长，取值收敛在该档位允许区间内。 */
    public int getRelaxedTierMinutes(LeisureTier tier) {
        return tier.clampMinutes(mmkv.getInt(tier.minutesKey, tier.defaultMinutes));
    }

    /** 保存宽松模式指定档位的解禁时长；超出允许区间时按边界收敛。 */
    public void setRelaxedTierMinutes(LeisureTier tier, int minutes) {
        mmkv.putInt(tier.minutesKey, tier.clampMinutes(minutes)).commit();
    }

    /** 宽松模式大档今日剩余次数（每天最多 2 次）。 */
    public int getLeisureLargeRemainingCountToday() {
        return Math.max(0, RELAXED_LARGE_DAILY_LIMIT - getLargeUsedCountToday());
    }

    /** 宽松模式小档今日剩余次数（每天最多 1 次）。 */
    public int getLeisureShortRemainingCountToday() {
        return Math.max(0, RELAXED_SHORT_DAILY_LIMIT - getShortUsedCountToday());
    }

    /**
     * 20 点后的宽松休闲额度回收：只要当天还没用完，就把余额重置为「小档 1 次」。
     *
     * @return 是否发生了重置
     */
    public boolean resetRelaxedRemainingToShort() {
        synchronized (LeisureTimeManager.class) {
            if (getLeisureRemainingCountToday(LeisureMode.RELAXED) <= 0) {
                return false;
            }
            String currentDate = DateUtils.getCurrentDate();
            mmkv.putInt(KEY_LEISURE_RELAXED_LARGE_USED, RELAXED_LARGE_DAILY_LIMIT)
                    .putInt(KEY_LEISURE_RELAXED_SHORT_USED, 0)
                    .putString(KEY_LEISURE_LAST_USED_DATE, currentDate)
                    .commit();
            return true;
        }
    }

    private int getLargeUsedCountToday() {
        return isTodayLeisureDate()
                ? mmkv.getInt(KEY_LEISURE_RELAXED_LARGE_USED, 0)
                : 0;
    }

    private int getShortUsedCountToday() {
        return isTodayLeisureDate()
                ? mmkv.getInt(KEY_LEISURE_RELAXED_SHORT_USED, 0)
                : 0;
    }

    private boolean isTodayLeisureDate() {
        return DateUtils.getCurrentDate()
                .equals(mmkv.getString(KEY_LEISURE_LAST_USED_DATE, ""));
    }

    /**
     * 跨天后把两档已用次数一起清零，并把共用的日期推进到今天。
     * 必须在写入任一档已用次数前调用：两档共用一个日期 key，若只清写入档、
     * 另一档上次使用日的残留值会随日期推进被当成今日已用，凭空少掉次数。
     */
    private void rolloverRelaxedCountsIfNewDay() {
        if (isTodayLeisureDate()) {
            return;
        }
        mmkv.putInt(KEY_LEISURE_RELAXED_LARGE_USED, 0)
                .putInt(KEY_LEISURE_RELAXED_SHORT_USED, 0)
                .putString(KEY_LEISURE_LAST_USED_DATE, DateUtils.getCurrentDate())
                .commit();
    }

    public boolean isRelaxedTriggerCoolingDown() {
        return getRelaxedTriggerCooldownRemainingMillis() > 0;
    }

    public long getRelaxedTriggerCooldownRemainingMillis() {
        return Math.max(
                0,
                mmkv.getLong(KEY_LEISURE_RELAXED_COOLDOWN_UNTIL, 0)
                        - System.currentTimeMillis());
    }

    public boolean isLeisureTimeActive(LeisureMode mode) {
        return getLeisureTimeRemainingMillis(mode) > 0;
    }

    public boolean isAnyLeisureTimeActive() {
        return isLeisureTimeActive(LeisureMode.RELAXED)
                || isLeisureTimeActive(LeisureMode.STRICT);
    }

    public long getMaxLeisureTimeRemainingMillis() {
        return Math.max(
                getLeisureTimeRemainingMillis(LeisureMode.RELAXED),
                getLeisureTimeRemainingMillis(LeisureMode.STRICT));
    }

    public LeisureMode getActiveLeisureMode() {
        for (LeisureMode mode : LeisureMode.values()) {
            if (isLeisureTimeActive(mode)) {
                return mode;
            }
        }
        return null;
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
            if (isAnyLeisureTimeActive()) {
                return false;
            }
            if (mode == LeisureMode.RELAXED && isRelaxedTriggerCoolingDown()) {
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
     * 首次关闭悬浮窗时正式开始全局休闲解禁，并只消耗一次所选档位额度。
     *
     * @return 成功触发的模式；没有待触发模式或所选档位不可用时返回 null
     */
    public LeisureMode activateLeisureTimeForClose(LeisureTier tier) {
        synchronized (LeisureTimeManager.class) {
            if (!isLeisureTimeArmed()) {
                return null;
            }
            LeisureMode mode = getArmedLeisureMode();
            if (isLeisureTimeActive(mode)) {
                mmkv.putBoolean(KEY_LEISURE_ARMED, false).commit();
                return null;
            }

            if (mode == LeisureMode.RELAXED) {
                return activateRelaxedLeisure(tier);
            }
            return activateStrictLeisure();
        }
    }

    private LeisureMode activateRelaxedLeisure(LeisureTier tier) {
        if (isRelaxedTriggerCoolingDown()) {
            mmkv.putBoolean(KEY_LEISURE_ARMED, false).commit();
            return null;
        }
        LeisureTier effectiveTier = tier != null ? tier : getSelectedRelaxedTier();
        if (getRemainingCount(effectiveTier) <= 0) {
            mmkv.putBoolean(KEY_LEISURE_ARMED, false).commit();
            return null;
        }

        rolloverRelaxedCountsIfNewDay();
        int durationMinutes = getRelaxedTierMinutes(effectiveTier);
        String tierUsedKey = effectiveTier == LeisureTier.LARGE
                ? KEY_LEISURE_RELAXED_LARGE_USED
                : KEY_LEISURE_RELAXED_SHORT_USED;
        int tierUsedCount = mmkv.getInt(tierUsedKey, 0);
        long now = System.currentTimeMillis();
        mmkv.putInt(tierUsedKey, tierUsedCount + 1)
                .putLong(KEY_LEISURE_ACTIVE_UNTIL, now + durationMinutes * 60_000L)
                .putLong(
                        KEY_LEISURE_RELAXED_COOLDOWN_UNTIL,
                        now + RELAXED_TRIGGER_COOLDOWN_MILLIS)
                .putBoolean(KEY_LEISURE_ARMED, false)
                .commit();
        return LeisureMode.RELAXED;
    }

    private int getRemainingCount(LeisureTier tier) {
        return tier == LeisureTier.LARGE
                ? getLeisureLargeRemainingCountToday()
                : getLeisureShortRemainingCountToday();
    }

    private LeisureMode activateStrictLeisure() {
        if (getLeisureRemainingCountToday(LeisureMode.STRICT) <= 0) {
            mmkv.putBoolean(KEY_LEISURE_ARMED, false).commit();
            return null;
        }
        int strictUsedCount = getLeisureUsedCountToday(LeisureMode.STRICT);
        mmkv.putInt(KEY_STRICT_LEISURE_USED_COUNT, strictUsedCount + 1)
                .putString(KEY_STRICT_LEISURE_LAST_USED_DATE, DateUtils.getCurrentDate())
                .putLong(
                        KEY_STRICT_LEISURE_ACTIVE_UNTIL,
                        System.currentTimeMillis()
                                + getLeisureDurationMinutes(LeisureMode.STRICT) * 60_000L)
                .putBoolean(KEY_LEISURE_ARMED, false)
                .commit();
        return LeisureMode.STRICT;
    }

    private LeisureMode getArmedLeisureMode() {
        return LeisureMode.fromPreferenceValue(mmkv.getString(
                KEY_LEISURE_ARMED_MODE, LeisureMode.RELAXED.preferenceValue));
    }

    public long getLeisureTimeRemainingMillis(LeisureMode mode) {
        long remainingMillis = mmkv.getLong(getLeisureActiveUntilKey(mode), 0)
                - System.currentTimeMillis();
        return Math.max(remainingMillis, 0);
    }

    private static String getLeisureActiveUntilKey(LeisureMode mode) {
        return mode == LeisureMode.STRICT
                ? KEY_STRICT_LEISURE_ACTIVE_UNTIL
                : KEY_LEISURE_ACTIVE_UNTIL;
    }
}
