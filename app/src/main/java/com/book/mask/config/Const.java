package com.book.mask.config;

import java.util.ArrayList;
import java.util.List;

public class Const {

    // 算术题-悬浮窗：默认数字位数
    public static final int ADD_LEN_DEFAULT = 5;
    public static final int SUB_LEN_DEFAULT = 5;
    public static final int MUL_FIRST_LEN_DEFAULT = 2;
    public static final int MUL_SECOND_LEN_DEFAULT = 2;

    // 混合题型比例：应用题 12%，其余 88% 为算术题
    public static final int MIXED_WORD_PROBLEM_PERCENT = 20;

    // 算术题-设置：自定义难度 位数范围
    public static final int ADD_LEN_MIN = 4;
    public static final int ADD_LEN_MAX = 7;
    public static final int MUL_LEN_MIN = 2;
    public static final int MUL_LEN_MAX = 4;

    // 英文阅读字数范围
    public static final int ENGLISH_READING_LENGTH_MIN = 200;
    public static final int ENGLISH_READING_LENGTH_MAX = 1000;
    public static final int ENGLISH_READING_LENGTH_DEFAULT = 300;

//    算术题-卡片难度
    public static final int ADD_LEN_CARD = 7;
    public static final int SUB_LEN_CARD = 7;
    public static final int MUL_FIRST_CARD = 4;
    public static final int MUL_SECOND_CARD = 4;

    public static final int CHECK_SERVICE_RUNNING_DELAY = 30000;

    public static final long APP_STATE_CHECK_INTERVAL = 2000; // 轮询检查间隔，ms
    public static final long SYSTEM_UI_CONFIRM_DELAY_MS = 100; // 离开目标包名后的确认时长
    public static final int DEFAULT_APP_STATE_DEBOUNCE_MS = 350; // 原有的目标 APP 动画残留防抖
    public static final int DEFAULT_FLOATING_WINDOW_EXIT_CONFIRM_DELAY_MS = 350; // 普通场景隐藏前的复核延迟
    public static final int MAX_DEBOUNCE_SETTING_MS = 10000;
    public static final long CONTENT_CHECK_DEBOUNCE_MS = 200; // 页面内容变化停止后再检测
    public static final long CONTENT_CHECK_MAX_WAIT_MS = 500; // 连续变化时的最长等待时间

    public enum PackageConfirmationMode {
        SYSTEM_UI,
        NON_TARGET
    }

    // SYSTEM_UI：仅 SystemUI 触发延迟确认；NON_TARGET：任意非当前目标包名触发延迟确认
    public static final PackageConfirmationMode PACKAGE_CONFIRMATION_MODE = PackageConfirmationMode.SYSTEM_UI;

    public static final String DEFAULT_HINT_SOURCE = "大模型";
    public static final String CUSTOM_HINT_SOURCE = "自定义";
    public static final String TARGET_TO_BE_SET = "待设置";

    // 云端接口配置
    public static final String DOMAIN_URL = "https://www.ratetend.com:5001/antiAddict"; // 请替换为实际的地址
    public static final String LLM_PATH_V2 = "/llm/v2";
    public static final String CHALLENGE = "/challenge";
    public static final String ENGLISH_READING = "/reading";
    public static final String REPORT_PATH = "/report";
    public static final String LATEST_VERSION_PATH = "/latestAppVersion";
    public static final String CONTENT_TYPE = "application/json";

    // 广播Action常量
    public static final String ACTION_UPDATE_RELAXED_COUNT = "com.book.mask.ACTION_UPDATE_RELAXED_COUNT";


}
