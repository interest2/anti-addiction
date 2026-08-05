package com.book.mask.config;

/**
 * 关闭悬浮窗时的答题类型。
 * 声明顺序即设置弹窗中的展示顺序。
 */
public enum ChallengeType {
    ARITHMETIC(0, "arithmetic_only", "算术题"),
    REASONING(2, "reasoning", "推理题"),
    RETELLING(4, "retelling", "复述题"),
    LISTENING(5, "listening", "听力题"),
    MIXED(1, "mixed", "各题型混合"),
    ENGLISH_READING(3, "english_reading", "英文阅读");

    private final int requestType;
    private final String preferenceValue;
    private final String displayName;

    ChallengeType(int requestType, String preferenceValue, String displayName) {
        this.requestType = requestType;
        this.preferenceValue = preferenceValue;
        this.displayName = displayName;
    }

    public int getRequestType() {
        return requestType;
    }

    public String getPreferenceValue() {
        return preferenceValue;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ChallengeType fromPreferenceValue(String preferenceValue) {
        for (ChallengeType type : values()) {
            if (type.preferenceValue.equals(preferenceValue)) {
                return type;
            }
        }
        return MIXED;
    }

    public static ChallengeType[] settingsOptions(boolean includeEnglishReading) {
        if (includeEnglishReading) {
            return values();
        }
        return new ChallengeType[]{ARITHMETIC, REASONING, RETELLING, LISTENING, MIXED};
    }
}
