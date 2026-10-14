package com.book.mask.personalize;

/**
 * 答题「概况」单条记录：仅保留列表所需的概要字段，供「答题记录」概况展示。
 * 与 {@link ChallengeRecord}（详情）分开保存，概况保留量更大（详见
 * {@link com.book.mask.constant.QuestionConst} 中 CHALLENGE_OVERVIEW_MAX / CHALLENGE_RECORD_MAX）。
 */
public final class AnswerOverviewRecord {

    public static final String TYPE_ARITHMETIC = "arithmetic";

    /** 题型：目前仅算术题；历史版本写入的其他题型记录不再展示，并在下次写入时被丢弃。 */
    public String type = "";
    /** 答题完成时间（毫秒时间戳），与对应详情记录保持一致 */
    public long timestamp;
    /** 答题耗时（秒） */
    public int elapsedSeconds;
    /** 是否通过 */
    public boolean passed;

    public AnswerOverviewRecord() {
    }
}
