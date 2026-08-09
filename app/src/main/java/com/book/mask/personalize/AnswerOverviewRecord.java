package com.book.mask.personalize;

/**
 * 答题「概况」单条记录：仅保留列表所需的概要字段，供「答题记录」概况展示。
 * 与 {@link ChallengeRecord} / {@link ListeningRecord} / {@link RetellingRecord}（详情）分开保存，
 * 概况保留量更大（详见 {@link com.book.mask.constant.QuestionConst} 中 *_OVERVIEW_MAX），详情保留量见 *_RECORD_MAX。
 */
public final class AnswerOverviewRecord {

    public static final String TYPE_ARITHMETIC = "arithmetic";
    public static final String TYPE_CHALLENGE = "challenge";
    public static final String TYPE_LISTENING = "listening";
    public static final String TYPE_RETELLING = "retelling";

    /** 题型：TYPE_ARITHMETIC / TYPE_CHALLENGE / TYPE_LISTENING / TYPE_RETELLING */
    public String type = "";
    /** 答题完成时间（毫秒时间戳），与对应详情记录保持一致 */
    public long timestamp;
    /** 答题耗时（秒）：仅文本类（算术 / 推理 / 混合）计时，听力 / 复述为 0 */
    public int elapsedSeconds;
    /** 是否通过 */
    public boolean passed;

    public AnswerOverviewRecord() {
    }
}
