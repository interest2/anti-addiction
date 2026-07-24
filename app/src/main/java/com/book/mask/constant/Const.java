package com.book.mask.constant;

public class Const {

    public static final int CHECK_SERVICE_RUNNING_DELAY = 30000;
    public static final long APP_STATE_CHECK_INTERVAL = 2000; // 轮询检查间隔，ms
    public static final long SYSTEM_UI_CONFIRM_DELAY_MS = 100; // 离开目标包名后的确认时长
    public static final boolean PACKAGE_TRANSITION_RECHECK_ENABLED = true; // 第300ms复核机制开关；开启时离开目标APP先保持悬浮窗、300ms后复核，若仍为目标包名则暂停检测再复核
    public static final int PACKAGE_TRANSITION_ANIMATION_DURATION_MS = 1000; // 过渡动画时长 a
    public static final int PACKAGE_TRANSITION_CHECK_DELAY_MS = 300; // 首次复核时刻 p
    public static final int TRANSITION_EARLY_RETURN_BUFFER_MS = 100;
    public static final long FLOATING_SHOW_PACKAGE_DETECTION_DEBOUNCE_MS = 500;
    public static final long PAGE_TRANSITION_WINDOW_REUSE_MS = 1500;
    public static final long PACKAGE_TRANSITION_WINDOW_REUSE_MS = 2000; // 300ms 包名复核场景：离开目标 APP 后保留暖窗口以便快速复用的截止时刻
    public static final long CONTENT_CHECK_DEBOUNCE_MS = 200; // 页面内容变化停止后再检测
    public static final long SHOW_BEFORE_CONTENT_CHECK_DELAY_MS = 100; // 先显示悬浮窗后，延后此时长再跑阻塞式关键词检测，确保首帧先渲染
    public static final long CONTENT_CHECK_MAX_WAIT_MS = 500; // 连续变化时的最长等待时间
    public static final int TRANSIENT_FEEDBACK_DURATION_MS = 1000; // 非常驻 UI 提示时长

    public static final String DEFAULT_HINT_SOURCE = "大模型";
    public static final String CUSTOM_HINT_SOURCE = "自定义";
    public static final String TARGET_TO_BE_SET = "待设置";

    // 广播Action常量
    public static final String ACTION_UPDATE_RELAXED_COUNT = "com.book.mask.ACTION_UPDATE_RELAXED_COUNT";


}
