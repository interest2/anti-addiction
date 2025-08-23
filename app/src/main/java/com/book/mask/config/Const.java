package com.book.mask.config;

import java.util.ArrayList;
import java.util.List;

public class Const {

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

//    算术题-卡片难度
    public static final int ADD_LEN_CARD = 7;
    public static final int SUB_LEN_CARD = 7;
    public static final int MUL_FIRST_CARD = 4;
    public static final int MUL_SECOND_CARD = 4;

    public static final int CHECK_SERVICE_RUNNING_DELAY = 30000;

    public static final long APP_STATE_CHECK_INTERVAL = 2000; // 轮询检查间隔，ms

    public static final String DEFAULT_HINT_SOURCE = "大模型";
    public static final String CUSTOM_HINT_SOURCE = "自定义";
    public static final String TARGET_TO_BE_SET = "待设置";

    // 云端接口配置
    public static final String DOMAIN_URL = "https://www.ratetend.com:5001/antiAddict"; // 请替换为实际的地址
    public static final String LLM_PATH_V2 = "/llm/v2";
    public static final String CHALLENGE = "/challenge";
    public static final String REPORT_PATH = "/report";
    public static final String LATEST_VERSION_PATH = "/latestAppVersion";
    public static final String CONTENT_TYPE = "application/json";

    // 广播Action常量
    public static final String ACTION_UPDATE_RELAXED_COUNT = "com.book.mask.ACTION_UPDATE_RELAXED_COUNT";


}
