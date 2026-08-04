package com.book.mask.constant;

public final class QuestionConst {

    private QuestionConst() {
    }

    // 算术题-悬浮窗：默认数字位数
    public static final int ADD_LEN_DEFAULT = 5;
    public static final int SUB_LEN_DEFAULT = 5;
    public static final int MUL_FIRST_LEN_DEFAULT = 2;
    public static final int MUL_SECOND_LEN_DEFAULT = 2;
    // 混合题型比例默认值：推理题 20%，其余 80% 为本地算术题
    public static final double MIXED_REASONING_QUIZ_RATIO_DEFAULT = 0.2D;
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
    // 复述题：故事字数
    public static final int RETELLING_STORY_LENGTH_DEFAULT = 150;
    public static final int RETELLING_STORY_LENGTH_MIN = 80;
    public static final int RETELLING_STORY_LENGTH_MAX = 300;
    // 复述题：限时展示时长（秒）
    public static final int RETELLING_DISPLAY_SECONDS_DEFAULT = 30;
    public static final int RETELLING_DISPLAY_SECONDS_MIN = 10;
    public static final int RETELLING_DISPLAY_SECONDS_MAX = 120;
    // 复述题：通过分数
    public static final int RETELLING_PASS_SCORE_DEFAULT = 75;
    public static final int RETELLING_PASS_SCORE_MIN = 50;
    public static final int RETELLING_PASS_SCORE_MAX = 95;
    // 复述题：最长录音时长（秒），超时自动结束
    public static final int RETELLING_RECORD_MAX_SECONDS = 120;
    // 复述题：识别文本过短的本地粗检阈值（占原文长度比例）
    public static final double RETELLING_TEXT_TOO_SHORT_RATIO = 0.15D;
    // 复述题：内置故事池，接口不可用时兜底，避免复述题卡死
    public static final String[] RETELLING_BUILTIN_STORIES = {
            "小明在周末去了山里的外婆家。外婆给了他一张旧地图，告诉他山后有一座废弃的老屋，传说里面藏着一本家族相册。"
                    + "小明翻过山头找到了老屋，在积满灰尘的抽屉里发现了相册。翻开第一页，他看到祖父年轻时在河边钓鱼的照片，背面写着「外婆亲手写的话：愿每个孩子都能记住家的方向。」",
            "小丽在一家咖啡店打工时，经常看到一位老人独自坐在窗边。老人总会点一杯不加糖的咖啡，然后安静地看书。"
                    + "有一天老人把一本旧书送给了小丽，说这是他年轻时最爱读的小说。小丽翻开书，发现夹着一张泛黄的车票，目的地正是她出生的小城。",
            "一只小狗走丢了，在街头流浪了好几天。它遇到了一位小女孩，小女孩把它带回家并给它起名叫球球。"
                    + "几天后，小狗的主人通过寻狗启事找到了它们。小女孩虽然舍不得，还是把球球还给了主人。主人很感动，邀请小女孩每周都来和球球一起玩。"
    };
}
