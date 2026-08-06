package com.book.mask.personalize;

import java.util.Locale;

/**
 * 复述题单次答题记录。仅用于「答题记录」展示，字段由 Gson 序列化到 MMKV。
 */
public final class RetellingRecord {

    /** 答题完成时间（毫秒时间戳） */
    public long timestamp;
    /** 原始故事 */
    public String story = "";
    /** 本地语音识别得到的复述文本 */
    public String recognizedText = "";
    /** 总分（0-100） */
    public int score;
    /** 内容完整度（权重 40） */
    public int coverage;
    /** 逻辑连贯性（权重 25） */
    public int order;
    /** 事实准确度（权重 20） */
    public int accuracy;
    /** 表达完整性（权重 15） */
    public int expression;
    /** 大模型反馈 */
    public String feedback = "";
    /** 是否达到合格线 */
    public boolean passed;
    /** 故事来源标识（builtin / custom） */
    public String storyId = "";
    /** 腾讯口语评测发音准确度（0-100），未评测为 -1 */
    public float pronunciationAccuracy = -1f;
    /** 腾讯口语评测发音流利度（0-100，原始 0-1 已 ×100），未评测为 -1 */
    public float pronunciationFluency = -1f;
    /** 腾讯口语评测建议得分（0-100），未评测为 -1 */
    public float suggestedScore = -1f;

    public RetellingRecord() {
    }

    /** 是否包含腾讯口语评测数据。 */
    public boolean hasPronunciationScore() {
        return pronunciationAccuracy >= 0 && pronunciationFluency >= 0;
    }

    /**
     * 口语评测三字段展示文本：建议分（1 位小数）/ 发音准确（1 位小数）/ 流利（3 位小数）。
     * 建议分未返回时以 {@code -} 占位；未评测返回 null。
     */
    public String formatPronunciationSummary() {
        if (!hasPronunciationScore()) {
            return null;
        }
        String suggested = suggestedScore >= 0
                ? String.format(Locale.US, "%.1f", suggestedScore)
                : "-";
        return String.format(Locale.US, "建议分 %s · 发音准确 %.1f 分 · 流利 %.3f",
                suggested, pronunciationAccuracy, pronunciationFluency);
    }
}
