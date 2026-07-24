package com.book.mask.constant;

public class Const {

    public static final int CHECK_SERVICE_RUNNING_DELAY = 30000;
    public static final long APP_STATE_CHECK_INTERVAL = 2000; // 轮询检查间隔，ms
    public static final long SYSTEM_UI_CONFIRM_DELAY_MS = 100; // 离开目标包名后的确认时长
    public static final int PACKAGE_TRANSITION_ANIMATION_DURATION_MS = 1000; // 过渡动画时长 a
    public static final int PACKAGE_TRANSITION_CHECK_DELAY_MS = 300; // 首次复核时刻 p
    public static final int TRANSITION_EARLY_RETURN_BUFFER_MS = 100;
    public static final int TRANSITION_DIRECT_REENTRY_BUFFER_MS = 300;
    public static final long FLOATING_SHOW_PACKAGE_DETECTION_DEBOUNCE_MS = 350;
    public static final long PAGE_TRANSITION_WINDOW_REUSE_MS = 1000;
    public static final long CONTENT_CHECK_DEBOUNCE_MS = 200; // 页面内容变化停止后再检测
    public static final long CONTENT_CHECK_MAX_WAIT_MS = 500; // 连续变化时的最长等待时间
    public static final int TRANSIENT_FEEDBACK_DURATION_MS = 1000; // 非常驻 UI 提示时长

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
