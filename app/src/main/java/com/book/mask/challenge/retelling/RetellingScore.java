package com.book.mask.challenge.retelling;

import java.util.Locale;

/**
 * 复述题结构化评分结果。内容维度权重合计 100，对齐《复述训练指南》：
 * 内容完整度 40 / 逻辑连贯性 25 / 事实准确 20 / 表达完整 15。
 *
 * <p>接入腾讯口语评测后新增语音维度：发音准确度（0-100）/ 发音流利度（0-100，原始 0-1 已 ×100）/ 建议得分（0-100）。
 * 未做语音评测时该三值为 -1，总分即内容分；已做语音评测时总分 = 0.7×内容分 + 0.3×语音分。
 */
public final class RetellingScore {

    private final int score;
    private final int coverage;
    private final int accuracy;
    private final int order;
    private final int expression;
    private final String feedback;
    private final float pronunciationAccuracy;
    private final float pronunciationFluency;
    private final float suggestedScore;

    /** 纯内容评分（无语音评测）：语音维度为 -1。 */
    public RetellingScore(
            int score,
            int coverage,
            int accuracy,
            int order,
            int expression,
            String feedback) {
        this(score, coverage, accuracy, order, expression, feedback, -1f, -1f, -1f);
    }

    /** 含语音维度的评分（无建议得分）。 */
    public RetellingScore(
            int score,
            int coverage,
            int accuracy,
            int order,
            int expression,
            String feedback,
            float pronunciationAccuracy,
            float pronunciationFluency) {
        this(score, coverage, accuracy, order, expression, feedback,
                pronunciationAccuracy, pronunciationFluency, -1f);
    }

    /** 含语音维度与建议得分的评分。 */
    public RetellingScore(
            int score,
            int coverage,
            int accuracy,
            int order,
            int expression,
            String feedback,
            float pronunciationAccuracy,
            float pronunciationFluency,
            float suggestedScore) {
        this.score = score;
        this.coverage = coverage;
        this.accuracy = accuracy;
        this.order = order;
        this.expression = expression;
        this.feedback = feedback == null ? "" : feedback;
        this.pronunciationAccuracy = pronunciationAccuracy;
        this.pronunciationFluency = pronunciationFluency;
        this.suggestedScore = suggestedScore;
    }

    public int getScore() {
        return score;
    }

    public int getCoverage() {
        return coverage;
    }

    public int getAccuracy() {
        return accuracy;
    }

    public int getOrder() {
        return order;
    }

    public int getExpression() {
        return expression;
    }

    public String getFeedback() {
        return feedback;
    }

    public float getPronunciationAccuracy() {
        return pronunciationAccuracy;
    }

    public float getPronunciationFluency() {
        return pronunciationFluency;
    }

    public float getSuggestedScore() {
        return suggestedScore;
    }

    public boolean hasPronunciationScore() {
        return pronunciationAccuracy >= 0 && pronunciationFluency >= 0;
    }

    /**
     * 口语评测三字段展示文本：建议分（1 位小数）/ 发音准确（1 位小数）/ 流利（1 位小数）。
     * 建议分未返回时以 {@code -} 占位。
     */
    public String formatPronunciationSummary() {
        String suggested = suggestedScore >= 0
                ? String.format(Locale.US, "%.1f", suggestedScore)
                : "-";
        return String.format(Locale.US, "建议分 %s · 发音准确 %.1f 分 · 流利 %.1f",
                suggested, pronunciationAccuracy, pronunciationFluency);
    }
}
