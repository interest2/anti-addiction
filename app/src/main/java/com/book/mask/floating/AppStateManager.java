package com.book.mask.floating;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.book.mask.constant.Const;
import com.book.mask.config.CustomApp;
import com.book.mask.config.CustomAppManager;
import com.book.mask.config.Share;
import com.book.mask.setting.AppSettingsManager;
import com.book.mask.setting.RelaxManager;

import java.util.Map;
import java.util.HashMap;

/**
 * 应用状态管理器
 * 负责应用状态检测、文本内容检测、定时器管理等
 */
public class AppStateManager {
    private static final String TAG = "AppStateManager";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final long UNKNOWN_PACKAGE_RETRY_DELAY_MS = 150;
    private static final long PACKAGE_NAME_LOG_INTERVAL_MS = 100;
    private static final long PACKAGE_NAME_LOG_DURATION_MS = 1500;
    
    private AccessibilityService service;
    private Handler handler;
    private Handler autoShowHandler;
    private RelaxManager relaxManager;
    private AppSettingsManager appSettingsManager;

    // 应用状态相关
    private CustomApp currentActiveApp = null;
    private String lastObservedPackage;
    private long contentCheckBurstStartedAt = 0;
    private Runnable pendingPackageConfirmation;
    private long packageConfirmationGeneration = 0;
    private boolean suspendedForSystemUi = false;
    private PackageHideTransition packageHideTransition;
    private Runnable pendingPackageTransitionStep;
    private long packageTransitionGeneration = 0;
    private Runnable packageNameLogRunnable;
    private int packageNameLogSampleIndex = 0;
    private long floatingShowPackageDetectionPausedUntil = 0;
    private Runnable floatingShowPackageDetectionResumeRunnable;

    private enum PackageTransitionPhase {
        WAITING_FOR_INITIAL_CHECK,
        PAUSED_AFTER_EARLY_RETURN,
        MONITORING_DIRECT_REENTRY
    }

    private static final class PackageHideTransition {
        private final CustomApp targetApp;
        private final long startedAt;
        private PackageTransitionPhase phase = PackageTransitionPhase.WAITING_FOR_INITIAL_CHECK;

        private PackageHideTransition(CustomApp targetApp, long startedAt) {
            this.targetApp = targetApp;
            this.startedAt = startedAt;
        }
    }

    // 定时器相关
    private Map<CustomApp, Runnable> appTimers = new HashMap<>();
    private Runnable contentCheckRunnable;
    
    // 应用状态检测增强
    private Handler appStateHandler;
    private Runnable appStateCheckRunnable;
    private long lastAppStateCheckTime = 0;
    
    // 回调接口
    private OnAppStateListener listener;
    
    public interface OnAppStateListener {
        void onAppStateChanged(CustomApp app, boolean isTargetInterface);
        void onTargetPackageEnteredBeforeContentCheck(CustomApp app);
        void onAppLeft(CustomApp app);
        void onPackageTransitionStarted(CustomApp app);
        void onPackageTransitionViewDiscarded(CustomApp app);
        void onSystemUiSuspensionChanged(boolean suspended);
        void onTimerTriggered(CustomApp app);
        boolean isMathChallengeActive();
    }
    
    public AppStateManager(AccessibilityService service,
                           RelaxManager relaxManager,
                           AppSettingsManager appSettingsManager) {
        this.service = service;
        this.relaxManager = relaxManager;
        this.appSettingsManager = appSettingsManager;
        this.handler = new Handler(Looper.getMainLooper());
        this.autoShowHandler = new Handler(Looper.getMainLooper());
    }
    
    public void setOnAppStateListener(OnAppStateListener listener) {
        this.listener = listener;
    }

    /**
     * 处理无障碍事件
     */
    public void handleAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (isFloatingShowPackageDetectionPaused()) {
                return;
            }
            handleWindowStateChanged(event);
        } else if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            handleWindowContentChanged(event);
        }
    }
    
    /**
     * 处理窗口状态变化
     */
    private void handleWindowStateChanged(AccessibilityEvent event) {
        if (event.getPackageName() == null) {
            return;
        }

        String packageName = event.getPackageName().toString();
        Log.d(TAG, "窗口状态改变，当前应用: " + packageName);

        // 过滤掉我们自己的应用，避免悬浮窗显示时触发状态变化
        if (packageName.equals(service.getPackageName())) {
            Log.d(TAG, "忽略自己的应用: " + packageName);
            return;
        }

        // 过滤掉输入法应用，避免输入法弹出时误判
        if (FloatHelper.isInputMethodApp(packageName)) {
            Log.d(TAG, "忽略输入法应用: " + packageName);
            return;
        }

        // 记录包名访问 LRU（用于"包名日志"调试）
        com.book.mask.config.PackageLogManager.getInstance().record(packageName);
        handleObservedPackage(packageName, "窗口事件");
    }
    
    /**
     * 处理窗口内容变化
     */
    private void handleWindowContentChanged(AccessibilityEvent event) {
        if (isDetectionPaused()) {
            return;
        }

        // 只在支持的APP中检测文本内容
        if (currentActiveApp != null && event.getPackageName() != null) {
            String packageName = event.getPackageName().toString();
            if (currentActiveApp.getPackageName().equals(packageName)) {
                requestContentCheck();
            }
        }
    }

    /**
     * 合并同一轮页面变化，在页面稳定后检测；连续变化超过上限时强制执行一次。
     */
    private void requestContentCheck() {
        long now = SystemClock.elapsedRealtime();
        if (contentCheckBurstStartedAt == 0) {
            contentCheckBurstStartedAt = now;
        }

        if (contentCheckRunnable == null) {
            contentCheckRunnable = () -> {
                contentCheckBurstStartedAt = 0;
                checkTextContentOptimized();
            };
        }

        long burstElapsed = now - contentCheckBurstStartedAt;
        long maxWaitRemaining = Math.max(0, Const.CONTENT_CHECK_MAX_WAIT_MS - burstElapsed);
        long delayMillis = Math.min(Const.CONTENT_CHECK_DEBOUNCE_MS, maxWaitRemaining);
        handler.removeCallbacks(contentCheckRunnable);
        handler.postDelayed(contentCheckRunnable, delayMillis);
    }

    private void cancelPendingContentCheck() {
        contentCheckBurstStartedAt = 0;
        if (contentCheckRunnable != null) {
            handler.removeCallbacks(contentCheckRunnable);
        }
    }
    
    /**
     * 优化版本的文本内容检测
     */
    public void checkTextContentOptimized() {
        checkTextContentOptimized(false);
    }
    
    /**
     * 优化版本的文本内容检测
     * @param forceCheck 是否强制检查（用于定时器触发的情况）
     */
    public void checkTextContentOptimized(boolean forceCheck) {
        try {
            if (isDetectionPaused()) {
                Log.v(TAG, "检测防抖尚未结束，暂停页面关键词检测");
                return;
            }

            if (currentActiveApp == null) {
                Log.d(TAG, "当前没有活跃的APP，跳过文本检测");
                return;
            }

            if(listener != null && listener.isMathChallengeActive()){
                Log.d(TAG, "数学题正展示，暂停检测");
                return;
            }

            // 检查当前APP是否被手动隐藏
            boolean appManuallyHidden = currentActiveApp != null ?
                    Share.isAppManuallyHidden(currentActiveApp) : false;
            String appName = currentActiveApp.getAppName();

            if (appManuallyHidden) {
                boolean shouldHide = stillInHidePeriod();
                if(shouldHide){
                    return;
                }
            }

            String currentPackageName = currentActiveApp.getPackageName();
            Log.d(TAG, "当前有活跃的APP，且符合条件，开始文本检测");
            AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
            String targetWord = currentActiveApp.getTargetWord();
            boolean hasTargetWord = false;
            if (rootNode != null) {
                long start = System.currentTimeMillis();
                hasTargetWord = FloatHelper.findTextInNode(rootNode, targetWord);
                if(currentPackageName.equals(CustomAppManager.WECHAT_PACKAGE)){
                    hasTargetWord = true;
                }
                long end = System.currentTimeMillis();
                double deltaSeconds = (end - start) / 1000.0;
                Log.d(TAG, "检测耗时：" + String.format("%.3f", deltaSeconds));

                rootNode.recycle();
            }else{
                Log.d(TAG, "rootNode 为空");
                if(currentPackageName.equals(CustomAppManager.WECHAT_PACKAGE)){
                    hasTargetWord = true;
                }
            }
            // 简化界面判断逻辑：只检测目标词
            String currentInterface = hasTargetWord ? "target" : "not target";

            // 添加详细调试信息
            Log.d(TAG, "文本检测结果: " + targetWord + "=" + hasTargetWord + ", 当前界面=" + currentInterface + ", APP=" + appName);

            // 获取当前APP的状态
            String lastAppState = Share.getAppState(currentActiveApp);

            // 如果是强制检查或者界面状态发生变化时才执行操作
            if (forceCheck || !currentInterface.equals(lastAppState)) {
                if (!forceCheck) {
                    // 更新调试信息中的forceCheck触发时间
                    Share.setAppState(currentActiveApp, currentInterface);
                    Log.d(TAG, "界面变化检测: " + currentInterface + " (APP: " + appName + ")");
                } else {
                    Log.d(TAG, "强制检查模式 - 界面: " + currentInterface + " (APP: " + appName + ")");
                }

                if ("target".equals(currentInterface)) {
                    Log.d(TAG, "检测到目标界面 - APP: " + appName + ", 手动隐藏状态: " + appManuallyHidden + ", 强制检查: " + forceCheck);
                    if (listener != null) {
                        listener.onAppStateChanged(currentActiveApp, true);
                    }
                } else {
                    if (listener != null) {
                        listener.onAppStateChanged(currentActiveApp, false);
                    }
                }
            } else {
                Log.d(TAG, "界面状态无变化，跳过处理: " + currentInterface + " (APP: " + appName + ")");
            }
        } catch (Exception e) {
            Log.e(TAG, "优化版文本检测失败", e);
        }
    }
    
    private boolean stillInHidePeriod() {
        long remainingMillis = relaxManager.getAppRemainingTime(currentActiveApp);
        if (remainingMillis > 0) {
            Log.d(TAG, "APP " + currentActiveApp.getAppName()
                    + " 被手动隐藏，剩余 " + remainingMillis + "ms");
            return true;
        } else {
            Log.d(TAG, "虽然状态是手动隐藏，但超过时间了，不该继续隐藏");
            Share.setAppManuallyHidden(currentActiveApp, false);
            return false;
        }
    }
    
    /**
     * 创建定时器任务
     */
    public Runnable createTimerTask(CustomApp app) {
        return createTimerTask(app, true);
    }

    private Runnable createTimerTask(CustomApp app, boolean resetRelaxedModeOnTrigger) {
        CustomApp appForTimer = app;

        Runnable nextShowTask = () -> {
            Log.d(TAG, appForTimer + " 到达预期时间");
            if (appForTimer != null) {
                long leisureRemainingMillis =
                        appSettingsManager.getLeisureTimeRemainingMillis();
                if (appSettingsManager.isLeisureTimeActiveForApp(
                        appForTimer.getPackageName())) {
                    scheduleTimer(
                            appForTimer,
                            leisureRemainingMillis + 100,
                            resetRelaxedModeOnTrigger);
                    Log.d(TAG, "休闲时刻进行中，APP " + appForTimer.getAppName()
                            + " 的自动显示定时器顺延至休闲结束");
                    return;
                }

                boolean beforeState = Share.isAppManuallyHidden(appForTimer);
                String timerAppName = appForTimer.getAppName();
                Log.d(TAG, "定时器触发 - APP: " + timerAppName + ", 设置前手动隐藏状态: " + beforeState);

                Share.setAppManuallyHidden(appForTimer, false);

                boolean afterState = Share.isAppManuallyHidden(appForTimer);
                Log.d(TAG, "解除APP " + timerAppName + " 的手动隐藏状态 - 设置后状态: " + afterState);

                // 如果是宽松模式，现在切换到严格模式
                if (resetRelaxedModeOnTrigger
                        && relaxManager.isAppRelaxedMode(appForTimer)) {
                    relaxManager.setAppInterval(appForTimer, relaxManager.getMaxStrictInterval());
                    Log.d(TAG, "APP " + timerAppName + " 宽松模式已切换到严格模式");
                }

                // 检查当前是否在该APP中，如果是则尝试显示悬浮窗
                if (currentActiveApp == appForTimer) {
                    // 重新检测内容并显示悬浮窗
                    Log.d(TAG, "开始重新检测内容 - APP: " + timerAppName);
                    checkTextContentOptimized(true); // 使用强制检查模式
                    Log.d(TAG, "自动重新显示悬浮窗 - 重新检测内容完成 - APP: " + timerAppName);
                } else {
                    String currentAppName = currentActiveApp != null ? currentActiveApp.getAppName() : "null";
                    Log.d(TAG, "自动重新显示条件不满足 - 当前APP: " + currentAppName + ", 定时器APP: " + timerAppName + " (用户可能已离开该APP)");
                }
                
                if (listener != null) {
                    listener.onTimerTriggered(appForTimer);
                }
            }
        };
        return nextShowTask;
    }
    
    /**
     * 启动定时器
     */
    public void startTimer(CustomApp app, long interval) {
        scheduleTimer(app, interval, true);
    }

    /**
     * 启动休闲解禁定时器；到期时不消耗或重置该 APP 的宽松模式。
     */
    public void startLeisureTimer(CustomApp app, long interval) {
        scheduleTimer(app, interval, false);
    }

    private void scheduleTimer(
            CustomApp app,
            long interval,
            boolean resetRelaxedModeOnTrigger
    ) {
        // 如果已有当前 APP 的定时显示任务，则移除它
        if (appTimers.get(app) != null) {
            autoShowHandler.removeCallbacks(appTimers.get(app));
        }

        // 创建新的定时任务
        Runnable nextShowTask = createTimerTask(app, resetRelaxedModeOnTrigger);

        // 使用当前的时间间隔安排下次显示
        autoShowHandler.postDelayed(nextShowTask, interval);
        appTimers.put(app, nextShowTask);

        String intervalText = RelaxManager.getIntervalDisplayText((int)(interval / 1000));
        Log.d(TAG, "计划在" + intervalText + "后自动重新显示悬浮窗 (APP: " + app.getAppName() + ")");
    }
    
    /**
     * 取消定时器
     */
    public void cancelTimer(CustomApp app) {
        if (appTimers.containsKey(app)) {
            autoShowHandler.removeCallbacks(appTimers.get(app));
            appTimers.remove(app);
            Log.d(TAG, "取消APP " + app.getAppName() + " 的定时器");
        }
    }
    
    /**
     * 初始化应用状态检测增强机制
     */
    public void initAppStateEnhancement() {
        appStateHandler = new Handler(Looper.getMainLooper());
        
        appStateCheckRunnable = new Runnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastAppStateCheckTime > Const.APP_STATE_CHECK_INTERVAL) {
                    lastAppStateCheckTime = currentTime;
                    
                    // 检查当前前台应用状态
                    checkCurrentAppState();
                }
                
                // 继续循环检查
                appStateHandler.postDelayed(this, Const.APP_STATE_CHECK_INTERVAL);
            }
        };
        
        // 开始定期检查
        appStateHandler.postDelayed(appStateCheckRunnable, Const.APP_STATE_CHECK_INTERVAL);
        Log.d(TAG, "应用状态检测增强机制已启动");
    }
    
    /**
     * 检查当前前台应用状态
     */
    private void checkCurrentAppState() {
        try {
            if (isFloatingShowPackageDetectionPaused()) {
                return;
            }

            // 如果数学题验证界面正在显示，暂停状态检测
            if (listener != null && listener.isMathChallengeActive()) {
                Log.v(TAG, "数学题验证界面活跃，暂停应用状态检测");
                return;
            }

            // 获取当前窗口信息
            AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
            if (rootNode != null) {
                String currentPackage = rootNode.getPackageName() != null ? 
                    rootNode.getPackageName().toString() : "";
                Log.d(TAG, "定时轮询包名: " + currentPackage);
                if (!currentPackage.equals(service.getPackageName())
                        && !FloatHelper.isInputMethodApp(currentPackage)) {
                    handleObservedPackage(currentPackage, "定时轮询");
                }
                rootNode.recycle();
            }
        } catch (Exception e) {
            Log.w(TAG, "应用状态检测出错", e);
        }
    }
    
    /**
     * 检测包名对应的支持APP（统一使用CustomApp）
     */
    private CustomApp detectSupportedApp(String packageName) {
        return CustomAppManager.getInstance().detectSupportedApp(packageName, relaxManager);
    }

    /**
     * 所有包名观察统一进入这里，避免窗口事件和定时轮询采用不同的离开规则。
     */
    private void handleObservedPackage(String packageName, String source) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        if (isFloatingShowPackageDetectionPaused()) {
            Log.v(TAG, source + "处于悬浮窗显示防抖阶段，忽略包名变化: " + packageName);
            return;
        }

        boolean enteredFromSystemUi = SYSTEM_UI_PACKAGE.equals(lastObservedPackage);
        lastObservedPackage = packageName;

        if (packageHideTransition != null) {
            handlePackageDuringHideTransition(packageName, source);
            return;
        }

        // SystemUI 沿用原有的延迟确认及 View 暂停/恢复逻辑。
        if (currentActiveApp != null && SYSTEM_UI_PACKAGE.equals(packageName)) {
            schedulePackageConfirmation(source, Const.SYSTEM_UI_CONFIRM_DELAY_MS);
            return;
        }

        boolean leavesCurrentTargetWithVisibleFloatingWindow = currentActiveApp != null
                && !currentActiveApp.getPackageName().equals(packageName)
                && Share.isFloatingWindowVisible
                && !suspendedForSystemUi;
        if (leavesCurrentTargetWithVisibleFloatingWindow) {
            startPackageHideTransition(source);
            return;
        }

        cancelPendingPackageConfirmation();
        applyConfirmedPackage(packageName, source, enteredFromSystemUi);
        setSuspendedForSystemUi(false);
    }

    private void startPackageHideTransition(String source) {
        if (currentActiveApp == null || packageHideTransition != null) {
            return;
        }

        cancelPendingPackageConfirmation();
        cancelPendingContentCheck();
        Share.clearAppState(currentActiveApp);

        packageHideTransition = new PackageHideTransition(
                currentActiveApp,
                SystemClock.elapsedRealtime()
        );

        if (listener != null) {
            listener.onPackageTransitionStarted(currentActiveApp);
        }
        startPackageNameLogging();

        schedulePackageTransitionStep(
                Const.PACKAGE_TRANSITION_CHECK_DELAY_MS,
                this::confirmPackageAtInitialCheck
        );
        Log.d(TAG, source + "观察到离开目标 APP，悬浮窗已立即隐藏；"
                + Const.PACKAGE_TRANSITION_CHECK_DELAY_MS + "ms 后首次复核包名");
    }

    private void handlePackageDuringHideTransition(String packageName, String source) {
        PackageHideTransition transition = packageHideTransition;
        if (transition == null) {
            return;
        }

        if (transition.phase != PackageTransitionPhase.MONITORING_DIRECT_REENTRY) {
            Log.v(TAG, source + "处于包名检测暂停阶段，忽略包名事件: " + packageName);
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (transition.targetApp.getPackageName().equals(packageName)
                && PackageTransitionTiming.isWithinDirectReentryWindow(
                        transition.startedAt,
                        now
                )) {
            reenterTargetAndShowBeforeContentCheck(transition.targetApp, source);
            return;
        }

        if (!PackageTransitionTiming.isWithinDirectReentryWindow(
                transition.startedAt,
                now
        )) {
            discardPackageTransitionView(transition.targetApp);
            cancelPackageHideTransition();
            handleObservedPackage(packageName, source + "（短时重入窗口已结束）");
            return;
        }

        CustomApp observedApp = detectSupportedApp(packageName);
        if (observedApp != null && observedApp != transition.targetApp) {
            discardPackageTransitionView(transition.targetApp);
        }
        applyConfirmedPackage(packageName, source + "（等待原目标短时重入）");
        setSuspendedForSystemUi(false);
    }

    private void confirmPackageAtInitialCheck() {
        PackageHideTransition transition = packageHideTransition;
        if (transition == null
                || transition.phase != PackageTransitionPhase.WAITING_FOR_INITIAL_CHECK) {
            return;
        }

        String confirmedPackage = getActiveRootPackage();
        if (transition.targetApp.getPackageName().equals(confirmedPackage)) {
            transition.phase = PackageTransitionPhase.PAUSED_AFTER_EARLY_RETURN;
            long pauseDuration = PackageTransitionTiming.getEarlyReturnPauseDuration();
            schedulePackageTransitionStep(pauseDuration, this::resumeAfterEarlyReturnPause);
            Log.d(TAG, "首次复核已回到目标 APP，继续暂停检测 " + pauseDuration + "ms");
            return;
        }

        transition.phase = PackageTransitionPhase.MONITORING_DIRECT_REENTRY;
        Log.d(TAG, "首次复核仍为非目标包名: " + packageNameForLog(confirmedPackage)
                + "，开始监听短时重入");
        CustomApp confirmedApp = detectSupportedApp(confirmedPackage);
        if (confirmedApp == null) {
            confirmCurrentAppLeft("首次包名复核", false);
        } else {
            discardPackageTransitionView(transition.targetApp);
            applyConfirmedPackage(confirmedPackage, "首次包名复核");
        }
        setSuspendedForSystemUi(false);

        long deadline = transition.startedAt
                + PackageTransitionTiming.getDirectReentryWindowDuration();
        long remaining = Math.max(0L, deadline - SystemClock.elapsedRealtime());
        schedulePackageTransitionStep(remaining, this::finishDirectReentryMonitoring);
    }

    private void resumeAfterEarlyReturnPause() {
        PackageHideTransition transition = packageHideTransition;
        if (transition == null
                || transition.phase != PackageTransitionPhase.PAUSED_AFTER_EARLY_RETURN) {
            return;
        }

        CustomApp targetApp = transition.targetApp;
        String confirmedPackage = getActiveRootPackage();

        if (targetApp.getPackageName().equals(confirmedPackage)) {
            cancelPackageHideTransition();
            Share.clearAppState(targetApp);
            currentActiveApp = targetApp;
            Share.currentApp = targetApp;
            Log.d(TAG, "提前返回暂停结束，重新检测目标 APP 页面关键词");
            checkTextContentOptimized();
            return;
        }

        discardPackageTransitionView(targetApp);
        cancelPackageHideTransition();
        Log.d(TAG, "提前返回暂停结束，当前为非目标包名: "
                + packageNameForLog(confirmedPackage));
        applyConfirmedPackage(confirmedPackage, "提前返回暂停结束");
        setSuspendedForSystemUi(false);
    }

    private void reenterTargetAndShowBeforeContentCheck(CustomApp targetApp, String source) {
        cancelPackageHideTransition();
        cancelPendingPackageConfirmation();
        cancelPendingContentCheck();

        if (currentActiveApp != null && currentActiveApp != targetApp) {
            Share.clearAppState(currentActiveApp);
            if (listener != null) {
                listener.onAppLeft(currentActiveApp);
            }
        }

        currentActiveApp = targetApp;
        Share.currentApp = targetApp;
        Share.clearAppState(targetApp);
        setSuspendedForSystemUi(false);

        Log.d(TAG, source + "在短时窗口内回到目标 APP，先直接显示悬浮窗");
        if (listener != null) {
            listener.onAppStateChanged(targetApp, true);
        }
        Log.d(TAG, "悬浮窗直接显示完成，立即检测页面关键词");
        checkTextContentOptimized();
    }

    private void finishDirectReentryMonitoring() {
        PackageHideTransition transition = packageHideTransition;
        if (transition == null
                || transition.phase != PackageTransitionPhase.MONITORING_DIRECT_REENTRY) {
            return;
        }
        Log.d(TAG, "短时重入窗口结束，后续进入目标 APP 恢复常规关键词检测");
        discardPackageTransitionView(transition.targetApp);
        cancelPackageHideTransition();
    }

    private void discardPackageTransitionView(CustomApp targetApp) {
        if (listener != null) {
            listener.onPackageTransitionViewDiscarded(targetApp);
        }
    }

    public void startFloatingShowPackageDetectionDebounce() {
        floatingShowPackageDetectionPausedUntil = SystemClock.elapsedRealtime()
                + Const.FLOATING_SHOW_PACKAGE_DETECTION_DEBOUNCE_MS;

        if (floatingShowPackageDetectionResumeRunnable != null) {
            handler.removeCallbacks(floatingShowPackageDetectionResumeRunnable);
        }
        floatingShowPackageDetectionResumeRunnable =
                this::finishFloatingShowPackageDetectionDebounce;
        handler.postDelayed(
                floatingShowPackageDetectionResumeRunnable,
                Const.FLOATING_SHOW_PACKAGE_DETECTION_DEBOUNCE_MS
        );
        Log.d(TAG, "悬浮窗由无/隐藏变为显示，暂停检测包名变化 "
                + Const.FLOATING_SHOW_PACKAGE_DETECTION_DEBOUNCE_MS + "ms");
    }

    /**
     * 当前 APP 的休闲解禁正式开始后，清理它已经排队的页面检测任务。
     */
    public void pauseDetectionForLeisureTime(CustomApp app) {
        cancelPendingContentCheck();
        cancelPendingPackageConfirmation();
        cancelPackageHideTransition();
        Log.d(TAG, "APP " + app.getAppName() + " 的休闲解禁已开始，暂停该 APP 页面检测");
    }

    private void finishFloatingShowPackageDetectionDebounce() {
        long remaining = floatingShowPackageDetectionPausedUntil - SystemClock.elapsedRealtime();
        if (remaining > 0) {
            handler.postDelayed(floatingShowPackageDetectionResumeRunnable, remaining);
            return;
        }

        floatingShowPackageDetectionPausedUntil = 0;
        floatingShowPackageDetectionResumeRunnable = null;
        Log.d(TAG, "悬浮窗显示防抖结束，主动复核当前包名");

        String confirmedPackage = getActiveRootPackage();
        if (confirmedPackage.isEmpty()) {
            Log.d(TAG, "悬浮窗显示防抖结束时包名不明确，等待后续事件或轮询复核");
            return;
        }

        handleObservedPackage(confirmedPackage, "悬浮窗显示防抖结束");
    }

    private boolean isDetectionPaused() {
        return isLeisureDetectionPaused()
                || isPackageTransitionDetectionPaused();
    }

    private boolean isLeisureDetectionPaused() {
        return currentActiveApp != null
                && appSettingsManager.isLeisureTimeActiveForApp(
                        currentActiveApp.getPackageName());
    }

    private boolean isPackageTransitionDetectionPaused() {
        return packageHideTransition != null
                && packageHideTransition.phase != PackageTransitionPhase.MONITORING_DIRECT_REENTRY;
    }

    private boolean isFloatingShowPackageDetectionPaused() {
        return SystemClock.elapsedRealtime() < floatingShowPackageDetectionPausedUntil;
    }

    private String packageNameForLog(String packageName) {
        return packageName.isEmpty() ? "未知" : packageName;
    }

    private void startPackageNameLogging() {
        if (packageNameLogRunnable != null) {
            handler.removeCallbacks(packageNameLogRunnable);
        }

        packageNameLogSampleIndex = 0;
        packageNameLogRunnable = new Runnable() {
            @Override
            public void run() {
                packageNameLogSampleIndex++;
                long elapsedMillis = packageNameLogSampleIndex * PACKAGE_NAME_LOG_INTERVAL_MS;
                Log.d(TAG, "悬浮窗隐藏后当前包名 [" + elapsedMillis + "ms]: "
                        + packageNameForLog(getRawActiveRootPackage()));

                if (elapsedMillis < PACKAGE_NAME_LOG_DURATION_MS) {
                    handler.postDelayed(this, PACKAGE_NAME_LOG_INTERVAL_MS);
                } else {
                    packageNameLogRunnable = null;
                }
            }
        };
        handler.postDelayed(packageNameLogRunnable, PACKAGE_NAME_LOG_INTERVAL_MS);
    }

    private void schedulePackageTransitionStep(long delayMillis, Runnable step) {
        long generation = packageTransitionGeneration;
        pendingPackageTransitionStep = () -> {
            if (generation != packageTransitionGeneration) {
                return;
            }
            pendingPackageTransitionStep = null;
            step.run();
        };
        handler.postDelayed(pendingPackageTransitionStep, Math.max(0L, delayMillis));
    }

    private void cancelPackageHideTransition() {
        packageTransitionGeneration++;
        if (pendingPackageTransitionStep != null) {
            handler.removeCallbacks(pendingPackageTransitionStep);
            pendingPackageTransitionStep = null;
        }
        packageHideTransition = null;
    }

    /**
     * 第一次观察到待确认包名时启动一次确认；后续同类事件不会延长等待时间。
     */
    private void schedulePackageConfirmation(String source, long delayMillis) {
        if (pendingPackageConfirmation != null || suspendedForSystemUi) {
            return;
        }

        CustomApp expectedApp = currentActiveApp;
        long generation = ++packageConfirmationGeneration;
        pendingPackageConfirmation = () -> {
            if (generation != packageConfirmationGeneration) {
                return;
            }
            pendingPackageConfirmation = null;
            if (currentActiveApp != expectedApp || currentActiveApp == null) {
                return;
            }

            String confirmedPackage = getActiveRootPackage();
            if (confirmedPackage.isEmpty()
                    || confirmedPackage.equals(service.getPackageName())
                    || FloatHelper.isInputMethodApp(confirmedPackage)) {
                Log.d(TAG, "延迟确认时包名不明确，保持遮罩并稍后重试");
                schedulePackageConfirmation("包名不明确重试", UNKNOWN_PACKAGE_RETRY_DELAY_MS);
                return;
            }

            if (expectedApp.getPackageName().equals(confirmedPackage)) {
                Log.d(TAG, "在确认期限内回到目标 APP，保持悬浮窗");
                setSuspendedForSystemUi(false);
            } else if (SYSTEM_UI_PACKAGE.equals(confirmedPackage)) {
                Log.d(TAG, "SystemUI 持续超过设定阈值，临时暂停悬浮窗");
                setSuspendedForSystemUi(true);
            } else {
                Log.d(TAG, "延迟确认后进入其他包名: " + confirmedPackage);
                applyConfirmedPackage(confirmedPackage, "包名延迟确认", true);
                setSuspendedForSystemUi(false);
            }
        };

        handler.postDelayed(pendingPackageConfirmation, delayMillis);
        Log.d(TAG, source + "观察到待确认包名，" + delayMillis + "ms 后确认，期间保持悬浮窗");
    }

    private String getActiveRootPackage() {
        String rootPackage = getRawActiveRootPackage();
        if (!rootPackage.isEmpty()
                && !rootPackage.equals(service.getPackageName())
                && !FloatHelper.isInputMethodApp(rootPackage)) {
            return rootPackage;
        }
        return "";
    }

    private String getRawActiveRootPackage() {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        try {
            if (root != null && root.getPackageName() != null) {
                return root.getPackageName().toString();
            }
            return "";
        } finally {
            if (root != null) {
                root.recycle();
            }
        }
    }

    private void cancelPendingPackageConfirmation() {
        packageConfirmationGeneration++;
        if (pendingPackageConfirmation != null) {
            handler.removeCallbacks(pendingPackageConfirmation);
            pendingPackageConfirmation = null;
        }
    }

    private void applyConfirmedPackage(String packageName, String source) {
        applyConfirmedPackage(packageName, source, false);
    }

    private void applyConfirmedPackage(
            String packageName,
            String source,
            boolean enteredFromSystemUi
    ) {
        CustomApp detectedApp = detectSupportedApp(packageName);
        if (detectedApp != null) {
            if (detectedApp != currentActiveApp) {
                if (currentActiveApp != null) {
                    Log.d(TAG, source + "确认离开 APP: " + currentActiveApp.getAppName());
                    Share.clearAppState(currentActiveApp);
                }

                cancelPendingContentCheck();
                currentActiveApp = detectedApp;
                Share.currentApp = currentActiveApp;
                Log.d(TAG, source + "确认进入 APP: " + detectedApp.getAppName());
                boolean shouldShowBeforeContentCheck = !enteredFromSystemUi
                        && !suspendedForSystemUi
                        && !Share.isAppManuallyHidden(detectedApp);
                if (shouldShowBeforeContentCheck && listener != null) {
                    Log.d(TAG, "检测到从非目标包名进入 " + detectedApp.getAppName()
                            + "，先显示悬浮窗再检测页面文字");
                    listener.onTargetPackageEnteredBeforeContentCheck(detectedApp);
                    Log.d(TAG, detectedApp.getAppName()
                            + " 悬浮窗已先显示，立即检测页面文字");
                    checkTextContentOptimized();
                } else {
                    checkTextContentOptimized();
                }
            } else {
                requestContentCheck();
            }
            return;
        }

        if (currentActiveApp == null) {
            return;
        }

        confirmCurrentAppLeft(source, true);
    }

    private void confirmCurrentAppLeft(String source, boolean notifyListener) {
        if (currentActiveApp == null) {
            return;
        }

        CustomApp leftApp = currentActiveApp;
        Log.d(TAG, source + "确认离开 APP: " + leftApp.getAppName());
        Share.clearAppState(leftApp);
        cancelPendingContentCheck();
        currentActiveApp = null;
        Share.currentApp = null;
        if (notifyListener && listener != null) {
            listener.onAppLeft(leftApp);
        }
    }

    private void setSuspendedForSystemUi(boolean suspended) {
        if (suspendedForSystemUi == suspended) {
            return;
        }
        suspendedForSystemUi = suspended;
        if (listener != null) {
            listener.onSystemUiSuspensionChanged(suspended);
        }
    }

    public void cleanup() {
        // 清理所有APP的定时器
        if (autoShowHandler != null && appTimers != null) {
            for (Runnable timer : appTimers.values()) {
                autoShowHandler.removeCallbacks(timer);
            }
            appTimers.clear();
        }
        
        // 清理应用状态检测Handler
        if (appStateHandler != null && appStateCheckRunnable != null) {
            appStateHandler.removeCallbacks(appStateCheckRunnable);
        }
        
        // 清理内容检测Handler
        cancelPendingContentCheck();
        cancelPendingPackageConfirmation();
        cancelPackageHideTransition();
        if (packageNameLogRunnable != null) {
            handler.removeCallbacks(packageNameLogRunnable);
            packageNameLogRunnable = null;
        }
        packageNameLogSampleIndex = 0;
        lastObservedPackage = null;
        floatingShowPackageDetectionPausedUntil = 0;
        if (floatingShowPackageDetectionResumeRunnable != null) {
            handler.removeCallbacks(floatingShowPackageDetectionResumeRunnable);
            floatingShowPackageDetectionResumeRunnable = null;
        }
    }
}
