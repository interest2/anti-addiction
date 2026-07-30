package com.book.mask.constant;

public class Const {

    /**
     * 定期轮询
     */
    public static final int CHECK_SERVICE_RUNNING_DELAY = 30000; // 无障碍服务运行状态的定期检查间隔；发现服务未运行时通知上层尝试恢复。
    public static final long APP_STATE_CHECK_INTERVAL = 2000; // 前台 APP 状态的兜底轮询间隔，用于补偿无障碍窗口事件未及时上报的情况。

    /**
     * 内容检测防抖
     */
    public static final long CONTENT_CHECK_DEBOUNCE_MS = 200; // 页面内容停止变化后等待此时长再检测关键词，用于合并同一轮连续的内容变化事件。
    public static final long CONTENT_CHECK_MAX_WAIT_MS = 500; // 一轮页面内容连续变化允许等待的上限；达到上限后即使页面仍在变化也强制执行一次检测。

    /**
     * 进出 APP 场景的防抖
     */
    public static final long SYSTEM_UI_CONFIRM_DELAY_MS = 100; // 观察到 SystemUI 后的延迟确认时间；短暂进入系统界面时先保留悬浮窗，超时后再暂停。
    public static final boolean PACKAGE_TRANSITION_RECHECK_ENABLED = true; // 离开目标 APP 时是否启用包名延迟复核；开启后先保留暖窗口，复核确认离开后再恢复常规检测。
    public static final int PACKAGE_TRANSITION_ANIMATION_DURATION_MS = 1000; // 系统包名过渡动画的预估总时长，用于计算首次复核返回目标 APP 后还需暂停检测多久。
    public static final int PACKAGE_TRANSITION_CHECK_DELAY_MS = 300; // 离开目标 APP 后：首次复核当前前台包名的等待时间，用于过滤过渡动画期间的短暂包名变化。
    public static final int TRANSITION_EARLY_RETURN_BUFFER_MS = 100; // 首次复核已返回目标 APP 时追加的暂停缓冲，避免动画尾段的包名抖动触发悬浮窗闪现。
    public static final long FLOATING_SHOW_PACKAGE_DETECTION_DEBOUNCE_MS = 500; // 悬浮窗由隐藏变为显示后的包名检测暂停时间，避免窗口创建阶段的瞬时事件干扰前台 APP 判断。

    /**
     * 快速复用悬浮窗的配置
     */
    public static final long PAGE_TRANSITION_WINDOW_REUSE_MS = 1500; // 同一 APP 暂时离开目标页面时保留暖窗口的时长，期限内返回可直接复用，超时则释放。
    public static final long PACKAGE_TRANSITION_WINDOW_REUSE_MS = 2000; // 包名复核场景：离开目标 APP 后保留暖窗口的时长，期限内返回目标 APP 可快速复用。
    public static final long SHOW_BEFORE_CONTENT_CHECK_DELAY_MS = 100; // 先显示悬浮窗后延迟此时长再执行阻塞式关键词检测，确保悬浮窗首帧能够先完成渲染。

    /**
     * 非核心逻辑的常量
     */
    public static final int TRANSIENT_FEEDBACK_DURATION_MS = 1000; // 非常驻 UI 提示时长
    public static final boolean REMINDER_PROVIDER_SETTINGS_ENABLED = true;

    public static final String DEFAULT_HINT_SOURCE = "大模型";
    public static final String CUSTOM_HINT_SOURCE = "自定义";
    public static final String TARGET_TO_BE_SET = "待设置";

    // 悬浮窗良好习惯提醒默认文字（默认展示 + 输入框示例共用）
    public static final String DEFAULT_STRICT_REMINDER = "有志者事竟成！";

    // 用户改目标后，新提醒文字预取未完成时悬浮窗展示的占位文案
    public static final String REMINDER_LOADING_PLACEHOLDER = "加载中，请稍等";

    // 广播Action常量
    public static final String ACTION_UPDATE_RELAXED_COUNT = "com.book.mask.ACTION_UPDATE_RELAXED_COUNT";


}
