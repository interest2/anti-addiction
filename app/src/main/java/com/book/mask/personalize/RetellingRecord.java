package com.book.mask.personalize;

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

    public RetellingRecord() {
    }
}
