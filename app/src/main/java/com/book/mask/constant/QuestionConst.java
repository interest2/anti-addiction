package com.book.mask.constant;

public final class QuestionConst {

    private QuestionConst() {
    }

    // 算术题-悬浮窗：默认数字位数
    public static final int ADD_LEN_DEFAULT = 5;
    public static final int SUB_LEN_DEFAULT = 5;
    public static final int MUL_FIRST_LEN_DEFAULT = 2;
    public static final int MUL_SECOND_LEN_DEFAULT = 2;
    // 混合题型比例：云端题 20%，其余 80% 为本地算术题
    public static final int MIXED_REMOTE_CHALLENGE_PERCENT = 20;
    // 算术题-设置：自定义难度 位数范围
    public static final int ADD_LEN_MIN = 4;
    public static final int ADD_LEN_MAX = 7;
    public static final int MUL_LEN_MIN = 2;
    public static final int MUL_LEN_MAX = 4;
    // 英文阅读功能开关，默认不启用
    public static final boolean ENGLISH_READING_ENABLED = false;
    // 英文阅读字数范围
    public static final int ENGLISH_READING_LENGTH_MIN = 200;
    public static final int ENGLISH_READING_LENGTH_MAX = 1000;
    public static final int ENGLISH_READING_LENGTH_DEFAULT = 300;
    // 算术题-卡片难度
    public static final int ADD_LEN_CARD = 7;
    public static final int SUB_LEN_CARD = 7;
    public static final int MUL_FIRST_CARD = 4;
    public static final int MUL_SECOND_CARD = 4;
}
