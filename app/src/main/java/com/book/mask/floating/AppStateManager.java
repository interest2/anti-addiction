package com.book.mask.floating;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.book.mask.config.Const;
import com.book.mask.config.CustomApp;
import com.book.mask.config.CustomAppManager;
import com.book.mask.config.Share;
import com.book.mask.setting.AppSettingsManager;
import com.book.mask.setting.RelaxManager;
import com.book.mask.util.DateUtils;

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
    private static final int HIDE_PACKAGE_LOG_INTERVAL_MS = 100;
    private static final int HIDE_PACKAGE_LOG_DURATION_MS = 1200;
    
    private AccessibilityService service;
    private Handler handler;
    private Handler autoShowHandler;
    private RelaxManager relaxManager;
    private AppSettingsManager appSettingsManager;

    // 应用状态相关
    private CustomApp currentActiveApp = null;
    private long contentCheckBurstStartedAt = 0;
    private String latestObservedPackage = null;
    private Runnable pendingPackageConfirmation;
    private long packageConfirmationGeneration = 0;
    private boolean suspendedForSystemUi = false;
    private long lastTargetExitElapsedTime = 0;
    private Runnable pendingTargetReentryConfirmation;
    private long targetReentryConfirmationGeneration = 0;

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
        void onAppLeft(CustomApp app);
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
        Long timestamp= Share.getHiddenTimestamp(currentActiveApp.getPackageName());
        long currentInterval = relaxManager.getAppIntervalMillis(currentActiveApp);

        if(System.currentTimeMillis() - timestamp < currentInterval){
            Log.d(TAG, "APP " + currentActiveApp.getAppName() + " 被手动隐藏，跳过显示悬浮窗。当前配的使用时长（ms）为" + currentInterval);
            return true;
        }else{
            Log.d(TAG, "虽然状态是手动隐藏，但超过时间了，不该继续隐藏");
            Share.setAppManuallyHidden(currentActiveApp, false);
            return false;
        }
    }
    
    /**
     * 创建定时器任务
     */
    public Runnable createTimerTask(CustomApp app) {
        CustomApp appForTimer = app;

        Runnable nextShowTask = () -> {
            Log.d(TAG, appForTimer + " 到达预期时间");
            if (appForTimer != null) {
                boolean beforeState = Share.isAppManuallyHidden(appForTimer);
                String timerAppName = appForTimer.getAppName();
                Log.d(TAG, "定时器触发 - APP: " + timerAppName + ", 设置前手动隐藏状态: " + beforeState);

                Share.setAppManuallyHidden(appForTimer, false);

                boolean afterState = Share.isAppManuallyHidden(appForTimer);
                Log.d(TAG, "解除APP " + timerAppName + " 的手动隐藏状态 - 设置后状态: " + afterState);

                // 如果是宽松模式，现在切换到严格模式
                if (relaxManager.isAppRelaxedMode(appForTimer)) {
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
        // 如果已有当前 APP 的定时显示任务，则移除它
        if (appTimers.get(app) != null) {
            autoShowHandler.removeCallbacks(appTimers.get(app));
        }

        // 创建新的定时任务
        Runnable nextShowTask = createTimerTask(app);

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

        CustomApp observedApp = detectSupportedApp(packageName);
        latestObservedPackage = packageName;

        long reentryDebounceRemaining = getTargetReentryDebounceRemaining(observedApp);
        if (reentryDebounceRemaining > 0) {
            scheduleTargetReentryConfirmation(source, reentryDebounceRemaining);
            return;
        }
        cancelPendingTargetReentryConfirmation();

        boolean willHideVisibleFloatingWindow = currentActiveApp != null
                && observedApp == null
                && Share.isFloatingWindowVisible
                && !suspendedForSystemUi;
        if (willHideVisibleFloatingWindow) {
            schedulePackageConfirmation(
                    source + "悬浮窗离开防抖",
                    appSettingsManager.getFloatingWindowExitConfirmDelayMs(),
                    true
            );
            return;
        }

        if (shouldDelayPackageConfirmation(packageName)) {
            schedulePackageConfirmation(source, Const.SYSTEM_UI_CONFIRM_DELAY_MS);
            return;
        }

        cancelPendingPackageConfirmation();
        applyConfirmedPackage(packageName, source);
        setSuspendedForSystemUi(false);
    }

    private boolean shouldDelayPackageConfirmation(String packageName) {
        if (currentActiveApp == null) {
            return false;
        }
        if (Const.PACKAGE_CONFIRMATION_MODE == Const.PackageConfirmationMode.SYSTEM_UI) {
            return SYSTEM_UI_PACKAGE.equals(packageName);
        }
        return !currentActiveApp.getPackageName().equals(packageName);
    }

    private long getTargetReentryDebounceRemaining(CustomApp observedApp) {
        if (currentActiveApp != null || observedApp == null || lastTargetExitElapsedTime == 0) {
            return 0;
        }

        long elapsed = SystemClock.elapsedRealtime() - lastTargetExitElapsedTime;
        int debounceMs = appSettingsManager.getAppStateDebounceMs();
        if (elapsed < 0 || elapsed >= debounceMs) {
            return 0;
        }
        return debounceMs - elapsed;
    }

    private void scheduleTargetReentryConfirmation(String source, long delayMillis) {
        if (pendingTargetReentryConfirmation != null) {
            return;
        }

        long generation = ++targetReentryConfirmationGeneration;
        pendingTargetReentryConfirmation = () -> {
            if (generation != targetReentryConfirmationGeneration) {
                return;
            }
            pendingTargetReentryConfirmation = null;
            if (currentActiveApp != null) {
                return;
            }

            String confirmedPackage = getActiveRootPackage();
            if (confirmedPackage.isEmpty()) {
                Log.d(TAG, "目标重新进入确认时包名不明确，保持隐藏并稍后重试");
                scheduleTargetReentryConfirmation("包名不明确重试", UNKNOWN_PACKAGE_RETRY_DELAY_MS);
                return;
            }

            if (detectSupportedApp(confirmedPackage) == null) {
                Log.d(TAG, "目标重新进入防抖结束，当前仍为非目标包名: " + confirmedPackage);
                return;
            }

            latestObservedPackage = confirmedPackage;
            applyConfirmedPackage(confirmedPackage, "目标重新进入防抖");
        };

        handler.postDelayed(pendingTargetReentryConfirmation, delayMillis);
        Log.d(TAG, source + "防抖观察到目标 APP，" + delayMillis + "ms 后复核，期间保持隐藏");
    }

    private void cancelPendingTargetReentryConfirmation() {
        targetReentryConfirmationGeneration++;
        if (pendingTargetReentryConfirmation != null) {
            handler.removeCallbacks(pendingTargetReentryConfirmation);
            pendingTargetReentryConfirmation = null;
        }
    }

    /**
     * 第一次观察到待确认包名时启动一次确认；后续同类事件不会延长等待时间。
     */
    private void schedulePackageConfirmation(String source, long delayMillis) {
        schedulePackageConfirmation(source, delayMillis, false);
    }

    private void schedulePackageConfirmation(String source, long delayMillis, boolean logPackageSamples) {
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

            latestObservedPackage = confirmedPackage;
            if (expectedApp.getPackageName().equals(confirmedPackage)) {
                Log.d(TAG, "在确认期限内回到目标 APP，保持悬浮窗");
                setSuspendedForSystemUi(false);
            } else if (Const.PACKAGE_CONFIRMATION_MODE == Const.PackageConfirmationMode.SYSTEM_UI
                    && SYSTEM_UI_PACKAGE.equals(confirmedPackage)) {
                Log.d(TAG, "SystemUI 持续超过设定阈值，临时暂停悬浮窗");
                setSuspendedForSystemUi(true);
            } else {
                Log.d(TAG, "延迟确认后进入其他包名: " + confirmedPackage);
                applyConfirmedPackage(confirmedPackage, "包名延迟确认");
                setSuspendedForSystemUi(false);
            }
        };

        handler.postDelayed(pendingPackageConfirmation, delayMillis);
        if (logPackageSamples) {
            startHidePackageLogSampling();
        }
        Log.d(TAG, source + "观察到待确认包名，" + delayMillis + "ms 后确认，期间保持悬浮窗");
    }

    private void startHidePackageLogSampling() {
        for (int elapsedMs = HIDE_PACKAGE_LOG_INTERVAL_MS;
             elapsedMs <= HIDE_PACKAGE_LOG_DURATION_MS;
             elapsedMs += HIDE_PACKAGE_LOG_INTERVAL_MS) {
            final int logElapsedMs = elapsedMs;
            handler.postDelayed(() ->
                    Log.d(TAG, "第 " + logElapsedMs + " ms，包名 " + getActiveRootPackageForLog()),
                    logElapsedMs
            );
        }
    }

    private String getActiveRootPackageForLog() {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        try {
            if (root == null || root.getPackageName() == null) {
                return "未知";
            }
            String packageName = root.getPackageName().toString();
            return packageName.isEmpty() ? "未知" : packageName;
        } finally {
            if (root != null) {
                root.recycle();
            }
        }
    }

    private String getActiveRootPackage() {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        try {
            if (root != null && root.getPackageName() != null) {
                String rootPackage = root.getPackageName().toString();
                if (!rootPackage.isEmpty()
                        && !rootPackage.equals(service.getPackageName())
                        && !FloatHelper.isInputMethodApp(rootPackage)) {
                    return rootPackage;
                }
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
                checkTextContentOptimized();
            } else {
                requestContentCheck();
            }
            return;
        }

        if (currentActiveApp == null) {
            return;
        }

        CustomApp leftApp = currentActiveApp;
        Log.d(TAG, source + "确认离开 APP: " + leftApp.getAppName());
        Share.clearAppState(leftApp);
        cancelPendingContentCheck();
        lastTargetExitElapsedTime = SystemClock.elapsedRealtime();
        currentActiveApp = null;
        Share.currentApp = null;
        if (listener != null) {
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
        cancelPendingTargetReentryConfirmation();
    }
}
