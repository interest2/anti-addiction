package com.book.mask.personalize;

/**
 * 听力题单次答题记录。仅用于「答题记录」展示，字段由 Gson 序列化到 MMKV。
 */
public final class ListeningRecord {

    /** 答题完成时间（毫秒时间戳） */
    public long timestamp;
    /** 题干 */
    public String question = "";
    /** 听力原文 */
    public String transcript = "";
    /** 用户提交的答案（含选项文本，如 "B. xxx"） */
    public String userAnswer = "";
    /** 正确答案（含选项文本，如 "B. xxx"） */
    public String correctAnswer = "";
    /** 是否答对 */
    public boolean passed;

    public ListeningRecord() {
    }
}
