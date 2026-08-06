package com.book.mask.personalize;

/**
 * 非算术题（推理 / 混合等云端题目）单次答题记录。仅用于「答题记录」展示，字段由 Gson 序列化到 MMKV。
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

    public ChallengeRecord() {
    }
}
