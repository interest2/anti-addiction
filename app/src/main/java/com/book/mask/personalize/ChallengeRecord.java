package com.book.mask.personalize;

/**
 * 文本类单次答题记录（算术 / 推理 / 混合 / 英文阅读）。仅用于「答题记录」展示，字段由 Gson 序列化到 MMKV。
 */
public final class ChallengeRecord {

    /** 答题完成时间（毫秒时间戳） */
    public long timestamp;
    /** 题干 */
    public String question = "";
    /** 用户提交的答案 */
    public String userAnswer = "";
    /** 正确答案 */
    public String correctAnswer = "";
    /** 是否答对 */
    public boolean passed;
    /** 答题耗时（秒）：当前题目展示到提交答案；未计时的题目（如算术兜底）为 0。 */
    public int elapsedSeconds;

    public ChallengeRecord() {
    }
}
