package com.book.mask.floating;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import com.book.mask.config.Const;
import com.book.mask.config.Share;
import com.book.mask.config.CustomAppManager;
import com.book.mask.lifecycle.ServiceKeepAliveManager;
import com.book.mask.setting.RelaxManager;
import com.book.mask.setting.AppSettingsManager;
import com.book.mask.config.CustomApp;
import com.book.mask.network.DeviceInfoReporter;
import com.book.mask.network.TextFetcher;
import android.content.Intent;

import androidx.annotation.NonNull;

/**
 * 悬浮窗无障碍服务
 * 协调各个管理器，提供核心服务功能
 */
public class FloatService extends AccessibilityService
{
    private static final String TAG = "FloatingAccessibility";
    private static FloatService instance;
    
    // 时间格式化器
    private static final SimpleDateFormat timeFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    /**
     * 格式化时间戳为可读格式
     */
    private static String formatTime(long timestamp) {
        if (timestamp == 0) return "未设置";
        return timeFormatter.format(new Date(timestamp));
    }

    // 核心管理器
    private FloatingWindowManager floatingWindowManager;
    private AppStateManager appStateManager;
    
    // 保活管理器
    private ServiceKeepAliveManager keepAliveManager;
    
    // 设置管理器
    private RelaxManager relaxManager;
    private AppSettingsManager appSettingsManager;
    
    // 设备信息上报器
    private DeviceInfoReporter deviceInfoReporter;
    
    // 悬浮窗文字获取器
    private TextFetcher textFetcher;

    // 悬浮窗管理相关
    private WindowManager windowManager;
    private Handler handler;
    private long mathChallengeStartTime = 0; // 数学题验证开始时间

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "AccessibilityService 已连接！");
        Toast.makeText(this, "无障碍服务已启动", Toast.LENGTH_LONG).show();

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
     * 记录数学题验证开始时间
     */
    public void onMathChallengeStart() {
        mathChallengeStartTime = System.currentTimeMillis();
        Log.d(TAG, "数学题验证开始，暂停应用状态检测 [时间: " + formatTime(mathChallengeStartTime) + "]");
    }

    /**
     * 重置数学题验证时间
     */
    public void onMathChallengeEnd() {
        long endTime = System.currentTimeMillis();
        long duration = mathChallengeStartTime > 0 ? (endTime - mathChallengeStartTime) : 0;
        Log.d(TAG, "数学题验证结束，恢复应用状态检测 [开始: " + formatTime(mathChallengeStartTime) + ", 结束: " + formatTime(endTime) + ", 用时: " + duration + "ms]");
        mathChallengeStartTime = 0;
    }

    /**
     * 初始化核心管理器
     */
    private void initManagers() {
        // 初始化应用状态管理器
        appStateManager = new AppStateManager(this, relaxManager);
        appStateManager.setOnAppStateListener(new AppStateManager.OnAppStateListener() {
            @Override
            public void onAppStateChanged(CustomApp app, boolean isTargetInterface) {
                if (isTargetInterface) {
                    if (!floatingWindowManager.isFloatingWindowVisible()) {
                        floatingWindowManager.showFloatingWindow(app);
                    }
                } else {
                    if (floatingWindowManager.isFloatingWindowVisible()) {
                        floatingWindowManager.hideFloatingWindow();
                    }
                }
            }
            
            @Override
            public void onAppLeft(CustomApp app) {
                floatingWindowManager.hideFloatingWindow();
            }
            
            @Override
            public void onTimerTriggered(CustomApp app) {
                // 定时器触发时的处理
            }
        });
        
        // 初始化悬浮窗管理器
        floatingWindowManager = new FloatingWindowManager(this, windowManager, 
                                                        appSettingsManager, relaxManager, 
                                                        textFetcher, handler);
        floatingWindowManager.setOnFloatingWindowListener(new FloatingWindowManager.OnFloatingWindowListener() {
            @Override
            public void onMathChallengeCorrect() {
                handleMathChallengeCorrect();
            }
            
            @Override
            public void onMathChallengeCancel() {
                Log.d(TAG, "用户取消数学题验证");
            }
        });
        
        // 启动应用状态检测增强机制
        appStateManager.initAppStateEnhancement();
    }
    
    /**
     * 处理数学题验证成功
     */
    private void handleMathChallengeCorrect() {
        Log.d(TAG, "数学题验证成功，关闭悬浮窗");

        // 获取当前的时间间隔（宽松模式生效一次）
        long interval;
        int intervalSeconds;
        
        CustomApp currentActiveApp = Share.currentApp;
        if (currentActiveApp != null) {
            // 使用当前APP的时间间隔安排下次显示
            interval = relaxManager.getAppIntervalMillis(currentActiveApp);
            intervalSeconds = relaxManager.getAppInterval(currentActiveApp);
            
            String appName = currentActiveApp.getAppName();
            Log.d(TAG, "APP " + appName + " 当前设置的时间间隔: " + intervalSeconds + "秒");
            
            // 记录关闭时刻、所用时间间隔
            relaxManager.recordAppCloseTime(currentActiveApp, intervalSeconds);
            Share.setAppManuallyHidden(currentActiveApp, true);
            Log.d(TAG, "设置APP " + appName + " 为手动隐藏状态");
            
            // 检查是否是宽松模式
            boolean isRelaxedMode = relaxManager.isAppRelaxedMode(currentActiveApp);
            Log.d(TAG, "APP " + appName + " 当前是否宽松模式: " + isRelaxedMode);
            
            // 如果是宽松模式，使用次数+1。
            if (isRelaxedMode) {
                int currentCount = relaxManager.getAppRelaxedCloseCount(currentActiveApp);
                relaxManager.incrementAppRelaxedCloseCount(currentActiveApp);
                Log.d(TAG, "APP " + appName + " 宽松模式关闭。之前次数: " + currentCount + ", 现在次数: " + (currentCount + 1));
                
                // 通知HomeFragment更新UI显示
                notifyHomeFragmentUpdate(currentActiveApp);
                
                // 注意：不要在这里立即切换时间间隔，保持原来的时间间隔用于定时器
                // 定时器到期后，在重新检测内容时再切换到严格模式
                Log.d(TAG, "APP " + appName + " 宽松模式一次性生效，定时器将使用原时间间隔: " + intervalSeconds + "秒");
            }

            // 隐藏悬浮窗
            floatingWindowManager.hideFloatingWindow();

            // 启动定时器
            appStateManager.startTimer(currentActiveApp, interval);

            String intervalText = RelaxManager.getIntervalDisplayText(intervalSeconds);
            Log.d(TAG, "计划在" + intervalText + "后自动重新显示悬浮窗 (APP: " + appName + ")");

            // 显示下次使用的时间间隔
            int nextIntervalSeconds = currentActiveApp != null ?
                    relaxManager.getAppInterval(currentActiveApp) :
                    relaxManager.getDefaultInterval();
            Log.d(TAG, "下次时长：" + nextIntervalSeconds + "秒 (APP: " + appName + ")");

            Share.setHiddenTimestamp(currentActiveApp.getPackageName(), System.currentTimeMillis());
        }
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
        Log.d(TAG, "AccessibilityService 被中断");
        Toast.makeText(this, "无障碍服务被中断", Toast.LENGTH_SHORT).show();
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
        
        // 清理多APP状态
        Share.clearAllAppStates();

        Log.d(TAG, "AccessibilityService 已销毁");
        Toast.makeText(this, "无障碍服务已停止", Toast.LENGTH_SHORT).show();
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