package com.book.mask.floating;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import com.book.mask.constant.Const;
import com.book.mask.challenge.retelling.SherpaOnnxTranscriber;
import com.book.mask.config.Share;
import com.book.mask.lifecycle.ServiceKeepAliveManager;
import com.book.mask.personalize.RelaxManager;
import com.book.mask.personalize.AppSettingsManager;
import com.book.mask.personalize.LeisureTimeManager;
import com.book.mask.config.CustomApp;
import com.book.mask.network.DeviceInfoReporter;
import com.book.mask.network.TextFetcher;
import com.book.mask.util.DateUtils;
import com.tencent.mmkv.MMKV;

/**
 * 悬浮窗无障碍服务
 * 协调各个管理器，提供核心服务功能
 */
public class FloatService extends AccessibilityService
{
    private static final String TAG = "FloatingAccessibility";
    private static FloatService instance;


    // 核心管理器
    private FloatingWindowManager floatingWindowManager;
    private AppStateManager appStateManager;
    
    // 保活管理器
    private ServiceKeepAliveManager keepAliveManager;
    
    // 设置管理器
    private RelaxManager relaxManager;
    private AppSettingsManager appSettingsManager;
    private LeisureTimeManager leisureTimeManager;
    
    // 设备信息上报器
    private DeviceInfoReporter deviceInfoReporter;
    
    // 悬浮窗文字获取器
    private TextFetcher textFetcher;

    // 悬浮窗管理相关
    private WindowManager windowManager;
    private Handler handler;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        MMKV.initialize(this);
        instance = this;
        Log.d(TAG, "AccessibilityService 已连接！");

        Log.d(TAG, "AccessibilityService 开始连接");
        
        // 初始化处理器
        handler = new Handler(Looper.getMainLooper());

        // 初始化悬浮窗管理器
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        // 配置无障碍服务
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(info);
        
        // 初始化设置管理器
        relaxManager = new RelaxManager(this);
        appSettingsManager = new AppSettingsManager(this);
        leisureTimeManager = new LeisureTimeManager(this);
        
        // 初始化设备信息上报器并上报设备信息
        deviceInfoReporter = new DeviceInfoReporter(this);
        deviceInfoReporter.reportDeviceInfo();
        
        // 初始化悬浮窗文字获取器
        textFetcher = new TextFetcher(this);
        
        // 初始化核心管理器
        initManagers();
        
        // 初始化保活管理器
        try {
            Log.d(TAG, "开始初始化保活管理器");
            initKeepAliveManager();
            Log.d(TAG, "保活管理器初始化完成");
        } catch (Exception e) {
            Log.e(TAG, "保活管理器初始化失败", e);
        }
        
        Log.d(TAG, "AccessibilityService 配置完成");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (appStateManager != null) {
            appStateManager.handleAccessibilityEvent(event);
        }
    }

    /**
     * 记录答题验证开始时间
     */
    public void onChallengeStart() {
        Share.challengeStartTime = System.currentTimeMillis();
        Log.d(TAG, "答题验证开始，暂停应用状态检测 [时间: " + DateUtils.formatTime(Share.challengeStartTime) + "]");
    }

    /**
     * 重置答题验证时间
     */
    public void onChallengeEnd() {
        long endTime = System.currentTimeMillis();
        long duration = Share.challengeStartTime > 0 ? (endTime - Share.challengeStartTime) : 0;
        Log.d(TAG, "答题验证结束，恢复应用状态检测 [开始: " + DateUtils.formatTime(Share.challengeStartTime)
                + ", 结束: " + DateUtils.formatTime(endTime) + ", 用时: " + duration + "ms]");
        Share.challengeStartTime = 0;
    }

    /**
     * 复述题录音阶段：透明 Activity 前台期间暂停悬浮窗，避免遮罩盖住录音界面。
     */
    public void suspendFloatingWindowForRecording() {
        if (floatingWindowManager != null) {
            floatingWindowManager.suspendForRecording();
        }
    }

    /**
     * 复述题录音结束（完成 / 取消 / 出错）后恢复悬浮窗。
     */
    public void resumeFloatingWindowFromRecording() {
        if (floatingWindowManager != null) {
            floatingWindowManager.resumeFromRecording();
        }
    }

    /**
     * 初始化核心管理器
     */
    private void initManagers() {
        // 初始化应用状态管理器
        appStateManager = new AppStateManager(this, relaxManager, leisureTimeManager);
        appStateManager.setOnAppStateListener(new AppStateManager.OnAppStateListener() {
            @Override
            public void onAppStateChanged(CustomApp app, boolean isTargetInterface) {
                if (isTargetInterface) {
                    if (floatingWindowManager.isSuspendedForPageTransition()
                            || !floatingWindowManager.isFloatingWindowVisible()) {
                        floatingWindowManager.showFloatingWindow(app);
                    }
                } else {
                    if (floatingWindowManager.isFloatingWindowVisible()) {
                        floatingWindowManager.suspendForPageTransition(app);
                    }
                }
            }

            @Override
            public void onTargetPackageEnteredBeforeContentCheck(CustomApp app) {
                floatingWindowManager.showFloatingWindow(app);
            }
            
            @Override
            public void onAppLeft(CustomApp app) {
                floatingWindowManager.hideFloatingWindow();
            }

            @Override
            public void onTargetPackageTransitionLeft(CustomApp app) {
                floatingWindowManager.suspendForPackageTransition(app);
            }

            @Override
            public void onSystemUiSuspensionChanged(boolean suspended) {
                floatingWindowManager.setSuspendedForSystemUi(suspended);
            }
            
            @Override
            public void onTimerTriggered(CustomApp app) {
                // 定时器触发时的处理
            }
            
            @Override
            public boolean isChallengeActive() {
                return floatingWindowManager.getChallengeManager() != null &&
                       floatingWindowManager.getChallengeManager().isChallengeActive();
            }
        });
        
        // 初始化悬浮窗管理器
        floatingWindowManager = new FloatingWindowManager(this, windowManager, 
                                                        appSettingsManager, leisureTimeManager,
                                                        relaxManager,
                                                        textFetcher, handler);
        floatingWindowManager.setOnFloatingWindowListener(new FloatingWindowManager.OnFloatingWindowListener() {
            @Override
            public void onChallengeCorrect() {
                handleChallengeCorrect();
            }

            @Override
            public void onChallengeCancel() {
                Log.d(TAG, "用户取消答题验证");
            }

            @Override
            public boolean onLeisureTimeCloseRequested() {
                return handleLeisureTimeClose();
            }

            @Override
            public void onFloatingWindowShownFromHidden() {
                appStateManager.startFloatingShowPackageDetectionDebounce();
            }
        });
        
        // 启动应用状态检测增强机制
        appStateManager.initAppStateEnhancement();
    }
    
    /**
     * 处理答题验证成功
     */
    private void handleChallengeCorrect() {
        Log.d(TAG, "答题验证成功，关闭悬浮窗");

        CustomApp currentActiveApp = Share.currentApp;
        if (currentActiveApp != null) {
            closeFloatingWindow(currentActiveApp);
        }
    }

    /**
     * 尝试使用一次休闲时刻免答题关闭悬浮窗。
     */
    private boolean handleLeisureTimeClose() {
        CustomApp currentActiveApp = Share.currentApp;
        if (currentActiveApp == null) {
            return false;
        }

        LeisureTimeManager.LeisureMode leisureMode =
                leisureTimeManager.activateLeisureTimeForClose(currentActiveApp.getPackageName());
        if (leisureMode == null) {
            return false;
        }

        int leisureSeconds = leisureTimeManager.getLeisureDurationMinutes(leisureMode) * 60;
        logInfoOfLeisure(currentActiveApp, leisureMode, leisureSeconds);

        // 仅当休闲时刻用的宽松档，才消耗宽松次数
        if (leisureMode == LeisureTimeManager.LeisureMode.RELAXED) {
            relaxManager.incrementAppRelaxedCloseCount(currentActiveApp);
            notifyHomeFragmentUpdate(currentActiveApp);
        }
        // 记录本次关闭时间，供暖窗口复用期间按"记录的剩余时长"判断是否仍应隐藏
        relaxManager.recordAppCloseTime(currentActiveApp, leisureSeconds);
        // 标为手动隐藏，防止后续检测
        Share.setAppManuallyHidden(currentActiveApp, true);
        // 取消排队的页面检测任务
        appStateManager.pauseDetectionForLeisureTime(currentActiveApp);

        // 悬浮窗的隐藏、到期恢复定时器
        floatingWindowManager.hideFloatingWindow();
        appStateManager.startLeisureTimer(currentActiveApp, leisureSeconds * 1000L);
        return true;
    }

    /**
     * 打印本次休闲解禁获得的时长及今日消耗进度。
     */
    private void logInfoOfLeisure(CustomApp app, LeisureTimeManager.LeisureMode leisureMode, int leisureSeconds) {
        Log.d(TAG, "APP " + app.getAppName()
                + " 已获得 " + RelaxManager.getIntervalDisplayText(leisureSeconds)
                + " 休闲解禁，今日已消耗 "
                + leisureTimeManager.getLeisureUsedCountToday(leisureMode) + "/"
                + leisureTimeManager.getLeisureDailyCount(leisureMode) + " 次");
    }

    private void closeFloatingWindow(CustomApp currentActiveApp) {
        String appName = currentActiveApp.getAppName();
        boolean isRelaxedMode = relaxManager.isAppRelaxedMode(currentActiveApp);
        if (isRelaxedMode) {
            int currentCount = relaxManager.getAppRelaxedCloseCount(currentActiveApp);
            if (currentCount < currentActiveApp.getRelaxedLimitCount()) {
                relaxManager.incrementAppRelaxedCloseCount(currentActiveApp);
                Log.d(TAG, "APP " + appName + " 宽松模式关闭。之前次数: " + currentCount
                        + ", 现在次数: " + (currentCount + 1));
            } else {
                // 宽松次数已用完：强制切回严格档，本次关闭按严格档计时
                relaxManager.setAppInterval(
                        currentActiveApp, RelaxManager.getMaxStrictInterval());
                Log.d(TAG, "APP " + appName + " 宽松次数已用完，强制切回严格模式");
            }
            notifyHomeFragmentUpdate(currentActiveApp);
        }

        int intervalSeconds = relaxManager.getAppInterval(currentActiveApp);
        long interval = intervalSeconds * 1000L;
        Log.d(TAG, "APP " + appName + " 本次关闭时长: " + intervalSeconds + "秒");

        relaxManager.recordAppCloseTime(currentActiveApp, intervalSeconds);
        Share.setAppManuallyHidden(currentActiveApp, true);
        floatingWindowManager.hideFloatingWindow();
        appStateManager.startTimer(currentActiveApp, interval);

        String intervalText = RelaxManager.getIntervalDisplayText(intervalSeconds);
        Log.d(TAG, "计划在" + intervalText + "后自动重新显示悬浮窗 (APP: " + appName + ")");
    }
    
    /**
     * 初始化保活管理器
     */
    private void initKeepAliveManager() {
        keepAliveManager = new ServiceKeepAliveManager(this);
        keepAliveManager.setOnServiceStateListener(new ServiceKeepAliveManager.OnServiceStateListener() {
            @Override
            public void onScreenUnlocked() {
                Log.d(TAG, "屏幕解锁，检查悬浮窗状态");
                // 屏幕解锁后，重新检查当前APP状态
                CustomApp currentActiveApp = Share.currentApp;
                if (currentActiveApp != null && "target".equals(Share.getAppState(currentActiveApp))) {
                    boolean appManuallyHidden = Share.isAppManuallyHidden(currentActiveApp);
                    if (!floatingWindowManager.isFloatingWindowVisible() && !appManuallyHidden) {
                        handler.postDelayed(() -> {
                            String appName = currentActiveApp.getAppName();
                            Log.d(TAG, "屏幕解锁后恢复悬浮窗显示 (APP: " + appName + ")");
                            floatingWindowManager.showFloatingWindow(currentActiveApp);
                        }, 100);
                    } else if (appManuallyHidden) {
                        String appName = currentActiveApp.getAppName();
                        Log.d(TAG, "APP " + appName + " 被手动隐藏，屏幕解锁后不恢复悬浮窗");
                    }
                }
            }
            
            @Override
            public void onUserPresent() {
                Log.d(TAG, "用户解锁设备，重新检查应用状态");
                // 用户解锁后，重新检测当前是否在支持的APP
                CustomApp currentActiveApp = Share.currentApp;
                if (currentActiveApp != null) {
                    String appName = currentActiveApp.getAppName();
                    Log.d(TAG, "用户解锁后重新检测APP: " + appName);
                    appStateManager.checkTextContentOptimized();
                }
            }
            
            @Override
            public void onServiceNeedRestart() {
                Log.w(TAG, "检测到服务需要重启，但AccessibilityService由系统管理");
                // AccessibilityService由系统管理，这里主要是记录日志
                // 用户需要手动到设置中重新开启无障碍服务
            }
        });
        
        // 启动保活机制
        keepAliveManager.startKeepAlive();
        keepAliveManager.startPeriodicCheck();
        
        Log.d(TAG, "保活管理器已初始化");
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "AccessibilityService 被中断");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        
        // 清理管理器资源
        if (appStateManager != null) {
            appStateManager.cleanup();
        }
        
        if (floatingWindowManager != null) {
            floatingWindowManager.cleanup();
        }
        
        // 清理保活管理器
        if (keepAliveManager != null) {
            keepAliveManager.stopKeepAlive();
        }
        
        // 释放设备信息上报器资源
        if (deviceInfoReporter != null) {
            deviceInfoReporter.release();
            deviceInfoReporter = null;
        }
        
        // 清理悬浮窗文字获取器
        if (textFetcher != null) {
            textFetcher.cleanup();
            textFetcher = null;
        }
        
        // 释放本地语音识别器（复述题），进程退出前清理
        SherpaOnnxTranscriber.releaseInstance();

        // 清理多APP状态
        Share.clearAllAppStates();

        Log.d(TAG, "AccessibilityService 已销毁");
    }

    public static boolean isServiceRunning() {
        return instance != null;
    }

    /**
     * 通知时间间隔设置已更新，立即应用新的间隔
     */
    public static void notifyIntervalChanged() {
        if (instance != null && instance.floatingWindowManager != null) {
            CustomApp currentActiveApp = Share.currentApp;
            instance.floatingWindowManager.updateFloatingWindowContent(currentActiveApp);
            
            // 如果当前有正在运行的自动显示定时器，重新启动它
            if (instance.appStateManager != null) {
                // 重新启动定时器逻辑
                Log.d(instance.TAG, "时间间隔设置已更新");
            }
        }
    }

    public static void notifyLeisureTimeChanged() {
        if (instance != null && instance.floatingWindowManager != null) {
            instance.floatingWindowManager.updateFloatingWindowContent(Share.currentApp);
        }
    }

    public static void notifyReminderProviderChanged() {
        notifyReminderContentChanged();
    }

    public static void notifyReminderContentChanged() {
        if (instance != null && instance.floatingWindowManager != null) {
            instance.floatingWindowManager.onReminderContentChanged();
        }
    }
    
    /**
     * 通知HomeFragment更新特定APP的UI显示 - 支持自定义APP
     */
    private void notifyHomeFragmentUpdate(CustomApp app) {
        try {
            // 通过广播通知MainActivity更新HomeFragment
            Intent intent = new Intent(Const.ACTION_UPDATE_RELAXED_COUNT);
            String appName = app.getAppName();
            intent.putExtra("app_name", appName);
            sendBroadcast(intent);
            Log.d(TAG, "已发送更新APP " + appName + " 宽松模式次数的广播");
        } catch (Exception e) {
            Log.w(TAG, "发送更新广播失败: " + e.getMessage());
        }
    }
}
