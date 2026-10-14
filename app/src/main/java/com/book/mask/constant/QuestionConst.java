package com.book.mask.constant;

public final class QuestionConst {

    private QuestionConst() {
    }

    // 算术题-悬浮窗：默认数字位数
    public static final int ADD_LEN_DEFAULT = 5;
    public static final int SUB_LEN_DEFAULT = 5;
    public static final int MUL_FIRST_LEN_DEFAULT = 2;
    public static final int MUL_SECOND_LEN_DEFAULT = 2;
    // 算术题-设置：自定义难度 位数范围
    public static final int ADD_LEN_MIN = 4;
    public static final int ADD_LEN_MAX = 7;
    public static final int MUL_LEN_MIN = 2;
    public static final int MUL_LEN_MAX = 4;
    // 算术题-卡片难度
    public static final int ADD_LEN_CARD = 7;
    public static final int SUB_LEN_CARD = 7;
    public static final int MUL_FIRST_CARD = 4;
    public static final int MUL_SECOND_CARD = 4;
    // 答题记录：「详情」最多保留的记录条数，新的在前，超过上限丢弃最旧
    public static final int CHALLENGE_RECORD_MAX = 2;
    // 答题记录：「概况」最多保留的记录条数，为详情量的 10 倍（仅存概要字段，内存占用低）
    public static final int CHALLENGE_OVERVIEW_MAX = CHALLENGE_RECORD_MAX * 10;
}
