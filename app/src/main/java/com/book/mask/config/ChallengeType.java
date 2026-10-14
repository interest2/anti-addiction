package com.book.mask.config;

/**
 * 关闭悬浮窗时的答题类型。
 * 声明顺序即设置页中的展示顺序；目前仅保留算术题。
 */
public enum ChallengeType {
    ARITHMETIC("arithmetic_only", "算术题");

    private final String preferenceValue;
    private final String displayName;

    ChallengeType(String preferenceValue, String displayName) {
        this.preferenceValue = preferenceValue;
        this.displayName = displayName;
    }

    public String getPreferenceValue() {
        return preferenceValue;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 历史版本（含备份文件）可能保存过已下线的题型（推理 / 混合 / 英文阅读 / 听力 / 复述 / 听述），
     * 无法识别的偏好值一律回落为算术题。
     */
    public static ChallengeType fromPreferenceValue(String preferenceValue) {
        for (ChallengeType type : values()) {
            if (type.preferenceValue.equals(preferenceValue)) {
                return type;
            }
        }
        return ARITHMETIC;
    }
}
