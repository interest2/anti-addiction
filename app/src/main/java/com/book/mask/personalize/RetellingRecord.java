package com.book.mask.personalize;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 复述题单次答题记录。仅用于「答题记录」展示，字段由 Gson 序列化到 MMKV。
 */
public final class RetellingRecord {

    public static final String STIMULUS_TEXT = "text";
    public static final String STIMULUS_AUDIO = "audio";

    /** 答题完成时间（毫秒时间戳） */
    public long timestamp;
    /** 原始故事 */
    public String story = "";
    /** 本地离线语音识别（Sherpa）转写文本，即复述内容评分实际使用的文本；腾讯转写不参与评分，不存储 */
    public String offlineRecognizedText = "";
    /** 总分（0-100） */
    public int score;
    /** 同一材料历次作答分数，按作答先后排列；旧记录缺失时回退到 {@link #score}。 */
    public List<Integer> attemptScores = new ArrayList<>();
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
    /** 材料呈现方式：text=复述题，audio=听述题；旧记录缺失时按 text 兼容。 */
    public String stimulusMode = STIMULUS_TEXT;
    /** 腾讯口语评测发音准确度（0-100），未评测为 -1 */
    public float pronunciationAccuracy = -1f;
    /** 腾讯口语评测发音流利度（0-100，原始 0-1 已 ×100），未评测为 -1 */
    public float pronunciationFluency = -1f;
    /** 腾讯口语评测建议得分（0-100），未评测为 -1 */
    public float suggestedScore = -1f;
    /** 本次答题录音文件名（WAV，存于应用私有目录 {@link RetellingAudioStore}），无录音或保存失败为空串 */
    public String audioPath = "";

    public RetellingRecord() {
    }

    public boolean isListeningRetelling() {
        return STIMULUS_AUDIO.equals(stimulusMode);
    }

    public int getAttemptCount() {
        return attemptScores == null || attemptScores.isEmpty() ? 1 : attemptScores.size();
    }

    public String formatAttemptScores() {
        if (attemptScores == null || attemptScores.isEmpty()) {
            return String.valueOf(score);
        }
        StringBuilder result = new StringBuilder();
        for (Integer attemptScore : attemptScores) {
            if (result.length() > 0) {
                result.append('、');
            }
            result.append(attemptScore == null ? 0 : attemptScore);
        }
        return result.toString();
    }

    /** 是否包含腾讯口语评测数据。 */
    public boolean hasPronunciationScore() {
        return pronunciationAccuracy >= 0 && pronunciationFluency >= 0;
    }

    /**
     * 口语评测三字段展示文本：建议分（1 位小数）/ 发音准确（1 位小数）/ 流利（1 位小数）。
     * 建议分未返回时以 {@code -} 占位；未评测返回 null。
     */
    public String formatPronunciationSummary() {
        if (!hasPronunciationScore()) {
            return null;
        }
        String suggested = suggestedScore >= 0
                ? String.format(Locale.US, "%.1f", suggestedScore)
                : "-";
        return String.format(Locale.US, "建议分 %s · 发音准确 %.1f 分 · 流利 %.1f",
                suggested, pronunciationAccuracy, pronunciationFluency);
    }
}
