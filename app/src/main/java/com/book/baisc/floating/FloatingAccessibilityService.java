package com.book.baisc.floating;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.util.DisplayMetrics;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

import com.book.baisc.R;
import com.book.baisc.config.Const;
import com.book.baisc.config.Share;
import com.book.baisc.lifecycle.ServiceKeepAliveManager;
import com.book.baisc.config.SettingsManager;
import com.book.baisc.network.DeviceInfoReporter;
import com.book.baisc.network.FloatingTextFetcher;
import android.content.Intent;

/**
 * 悬浮窗无障碍服务
 * 整合了无障碍服务和悬浮窗管理功能
 */
public class FloatingAccessibilityService extends AccessibilityService 
{

    private static final String TAG = "FloatingAccessibility";
    private static FloatingAccessibilityService instance;
    private boolean isFloatingWindowVisible = false;
    private Object currentActiveApp = null; // 当前活跃的APP（支持预定义和自定义APP）
    
    // 时间格式化器
    private static final SimpleDateFormat timeFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    
    /**
     * 格式化时间戳为可读格式
     */
    private static String formatTime(long timestamp) {
        if (timestamp == 0) return "未设置";
        return timeFormatter.format(new Date(timestamp));
    }
    
    // 性能优化相关
    private Handler handler;
    private Runnable contentCheckRunnable;
    private long lastContentCheckTime = 0;

    // 数学题验证管理器
    private MathChallengeManager mathChallengeManager;
    
    // 保活管理器
    private ServiceKeepAliveManager keepAliveManager;
    
    // 应用状态检测增强
    private Handler appStateHandler;
    private Runnable appStateCheckRunnable;
    private long lastAppStateCheckTime = 0;
    private long mathChallengeStartTime = 0; // 数学题验证开始时间
    
    // 设置管理器
    private SettingsManager settingsManager;
    
    // 设备信息上报器
    private DeviceInfoReporter deviceInfoReporter;
    
    // 悬浮窗文字获取器
    private FloatingTextFetcher floatingTextFetcher;

    // 悬浮窗管理相关
    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams layoutParams;
    private Handler autoShowHandler;
    private Runnable autoShowRunnable;
    
    // 为每个APP维护独立的定时器
    private Map<Object, Runnable> appTimers = new HashMap<>();

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "AccessibilityService 已连接！");
        Toast.makeText(this, "无障碍服务已启动", Toast.LENGTH_LONG).show();
        
        // 初始化Handler
        handler = new Handler(Looper.getMainLooper());
        autoShowHandler = new Handler(Looper.getMainLooper());
        
        // 初始化悬浮窗管理器
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        // 配置服务参数
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(info);
        
        // 初始化保活管理器
        initKeepAliveManager();
        
        // 初始化应用状态检测增强机制
        initAppStateEnhancement();
        
        // 初始化设置管理器
        settingsManager = new SettingsManager(this);
        
        // 初始化设备信息上报器并上报设备信息
        deviceInfoReporter = new DeviceInfoReporter(this);
        deviceInfoReporter.reportDeviceInfo();
        
        // 初始化悬浮窗文字获取器
        floatingTextFetcher = new FloatingTextFetcher(this);
        
        Log.d(TAG, "AccessibilityService 配置完成");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
//        Log.d(TAG, "onAccessibilityEvent 被调用，事件类型: " + event.getEventType());
        
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleWindowStateChanged(event);
        } else if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            handleWindowContentChanged(event);
        }
    }

    private void handleWindowStateChanged(AccessibilityEvent event) {
        if (event.getPackageName() != null) {
            String packageName = event.getPackageName().toString();
            Log.d(TAG, "窗口状态改变，当前应用: " + packageName);
            
            // 过滤掉我们自己的应用，避免悬浮窗显示时触发状态变化
            if (packageName.equals(getPackageName())) {
                Log.d(TAG, "忽略自己的应用: " + packageName);
                return;
            }
            
            // 过滤掉输入法应用，避免输入法弹出时误判
            if (FloatHelper.isInputMethodApp(packageName)) {
                Log.d(TAG, "忽略输入法应用: " + packageName);
                return;
            }
            
            // 检测当前是否是支持的APP（包括预定义和自定义）
            Object detectedApp = detectSupportedApp(packageName);
            
            if (detectedApp != null) {
                String appName = getAppName(detectedApp);
                Log.d(TAG, "检测到支持的APP: " + appName + " (包名: " + packageName + ")");
                
                if (detectedApp != currentActiveApp) {
                    // 切换到新的APP
                    if (currentActiveApp != null) {
                        String oldAppName = getAppName(currentActiveApp);
                        Log.d(TAG, "离开APP: " + oldAppName);
                        Share.clearAppState(currentActiveApp);
                        // 不清理手动隐藏状态，保持到下次自动解除
                    }
                    
                    currentActiveApp = detectedApp;
                    Share.currentApp = currentActiveApp;
                    Log.d(TAG, "进入APP: " + appName);
                    
                    // 检查当前APP是否被手动隐藏
                    boolean appManuallyHidden = Share.isAppManuallyHidden(currentActiveApp);
                    Log.d(TAG, "APP " + appName + " 手动隐藏状态: " + appManuallyHidden);
                    
                    // 立即开始检测文本内容
                    checkTextContentOptimized();
                }
            } else {
                // 离开所有支持的APP
                if (currentActiveApp != null) {
                    String oldAppName = getAppName(currentActiveApp);
                    Log.d(TAG, "离开APP: " + oldAppName);
                    Share.clearAppState(currentActiveApp);
                    // 不清理手动隐藏状态，保持到下次自动解除
                    currentActiveApp = null;
                    Share.currentApp = null;
                    hideFloatingWindow();
                }
            }
        }
    }
    
    private void handleWindowContentChanged(AccessibilityEvent event) {
        // 只在支持的APP中检测文本内容
        if (currentActiveApp != null && event.getPackageName() != null) {
            String packageName = event.getPackageName().toString();
            if (getAppPackageName(currentActiveApp).equals(packageName)) {
                
                // 防抖机制：避免频繁检测
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastContentCheckTime < 200) {
                    return; // 200ms内的重复事件直接忽略
                }
                lastContentCheckTime = currentTime;
                
                // 使用Handler延迟执行，进一步防抖
                if (contentCheckRunnable == null) {
                    contentCheckRunnable = new Runnable() {
                        @Override
                        public void run() {
                            checkTextContentOptimized();
                        }
                    };
                }
                
                // 取消之前的延迟任务，重新安排
                handler.removeCallbacks(contentCheckRunnable);
                handler.postDelayed(contentCheckRunnable, 10); // 300ms防抖延迟
            }
        }
    }

    /**
     * 优化版本的文本内容检测
     * 2. 优先检查常见的文本节点类型
     * 3. 使用缓存避免重复检测
     * 4. 支持多APP的不同目标词
     */
    void checkTextContentOptimized() {
        checkTextContentOptimized(false);
    }
    
    /**
     * 优化版本的文本内容检测
     * @param forceCheck 是否强制检查（用于定时器触发的情况）
     */
    void checkTextContentOptimized(boolean forceCheck) {
        try {
            if (currentActiveApp == null) {
                Log.d(TAG, "当前没有活跃的APP，跳过文本检测");
                return;
            }

            if(mathChallengeManager != null && mathChallengeManager.isMathChallengeActive()){
                Log.d(TAG, "数学题正展示，停止检测");
                return;
            }

            // 检查当前APP是否被手动隐藏
            boolean appManuallyHidden = currentActiveApp != null ?
                    Share.isAppManuallyHidden(currentActiveApp) : false;
            String appName = getAppName(currentActiveApp);

            if (appManuallyHidden) {
                Log.d(TAG, "APP " + appName + " 被手动隐藏，跳过显示悬浮窗");
                return;
            }

            Log.d(TAG, "当前有活跃的APP，开始文本检测");
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {

                String targetWord = getAppTargetWord(currentActiveApp);

                long start = System.currentTimeMillis();
                boolean hasTargetWord = FloatHelper.findTextInNode(rootNode, targetWord);
                if(appName.equals("微信")){
                    hasTargetWord = true;
                }

                long end = System.currentTimeMillis();
                double deltaSeconds = (end - start) / 1000.0;
                Log.d(TAG, "检测耗时：" + String.format("%.3f", deltaSeconds));

                // 简化界面判断逻辑：只检测目标词
                String currentInterface = hasTargetWord ? "target" : "other";

                // 添加详细调试信息
                Log.d(TAG, "文本检测结果: " + targetWord + "=" + hasTargetWord + ", 当前界面=" + currentInterface + ", APP=" + appName);

                // 获取当前APP的状态
                String lastAppState = Share.getAppState(currentActiveApp);
                
                // 如果是强制检查或者界面状态发生变化时才执行操作
                if (forceCheck || !currentInterface.equals(lastAppState)) {
                    if (!forceCheck) {
                        Share.setAppState(currentActiveApp, currentInterface);
                        Log.d(TAG, "界面变化检测: " + currentInterface + " (APP: " + appName + ")");
                    } else {
                        Log.d(TAG, "强制检查模式 - 界面: " + currentInterface + " (APP: " + appName + ")");
                    }

                    if ("target".equals(currentInterface)) {
                        Log.d(TAG, "检测到目标界面 - APP: " + appName +
                            ", 手动隐藏状态: " + appManuallyHidden + 
                            ", 悬浮窗可见: " + isFloatingWindowVisible + 
                            ", 强制检查: " + forceCheck);
                        
                        if (!isFloatingWindowVisible) {
                            Log.d(TAG, "显示悬浮窗 - APP: " + appName);
                            showFloatingWindow();
                        } else if (isFloatingWindowVisible) {
                            Log.d(TAG, "悬浮窗已显示，跳过重复显示");
                        }
                    } else {
                        if (isFloatingWindowVisible) {
                            hideFloatingWindow();
                        }
                    }
                } else {
                    Log.d(TAG, "界面状态无变化，跳过处理: " + currentInterface + " (APP: " + appName + ")");
                }

                rootNode.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "优化版文本检测失败", e);
        }
    }

    /**
     * 创建并显示悬浮窗
     */
    void showFloatingWindow() {
        if (isFloatingWindowVisible) {
            Log.v(TAG, "悬浮窗已显示，跳过重复显示");
            return;
        }
        Log.d(TAG, "开始显示悬浮窗");
        
        // 移除现有悬浮窗（如果存在）
        if (floatingView != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                Log.w(TAG, "移除旧悬浮窗时出错", e);
            }
            floatingView = null;
        }
        
        // 创建悬浮窗布局
        LayoutInflater inflater = LayoutInflater.from(this);
        floatingView = inflater.inflate(R.layout.floating_window_layout, null);
        layoutParams = getLayoutParams();
        
        // 初始化数学题验证管理器
        if (floatingView != null) {
            mathChallengeManager = new MathChallengeManager(
                this, floatingView, windowManager, layoutParams, handler, this
            );
            
            // 设置当前APP，用于微信APP的特殊处理
            if (currentActiveApp != null) {
                mathChallengeManager.setCurrentApp(currentActiveApp);
            }
            
            mathChallengeManager.setOnMathChallengeListener(new MathChallengeManager.OnMathChallengeListener() {
                @Override
                public void onAnswerCorrect() {
                    Log.d(TAG, "数学题验证成功，关闭悬浮窗");

                    // 获取当前的时间间隔（宽松模式生效一次）
                    long interval;
                    int intervalSeconds;
                    
                    if (currentActiveApp != null) {
                        // 使用当前APP的时间间隔安排下次显示
                        interval = settingsManager.getAppAutoShowIntervalMillis(currentActiveApp);
                        intervalSeconds = settingsManager.getAppAutoShowInterval(currentActiveApp);
                        
                        String appName = getAppName(currentActiveApp);
                        Log.d(TAG, "APP " + appName + " 当前设置的时间间隔: " + intervalSeconds + "秒");
                        
                        // 记录关闭时间和当前使用的时间间隔
                        settingsManager.recordAppCloseTime(currentActiveApp, intervalSeconds);
                        Share.setAppManuallyHidden(currentActiveApp, true);
                        Log.d(TAG, "设置APP " + appName + " 为手动隐藏状态");
                        
                        // 检查是否是宽松模式
                        boolean isCasualMode = settingsManager.isAppCasualMode(currentActiveApp);
                        Log.d(TAG, "APP " + appName + " 当前是否宽松模式: " + isCasualMode);
                        
                        // 如果是宽松模式，使用次数+1。
                        if (isCasualMode) {
                            int currentCount = settingsManager.getAppCasualCloseCount(currentActiveApp);
                            settingsManager.incrementAppCasualCloseCount(currentActiveApp);
                            Log.d(TAG, "APP " + appName + " 宽松模式关闭。之前次数: " + currentCount + ", 现在次数: " + (currentCount + 1));
                            
                            // 通知HomeFragment更新UI显示
                            notifyHomeFragmentUpdate(currentActiveApp);
                            
                            // 注意：不要在这里立即切换时间间隔，保持原来的时间间隔用于定时器
                            // 定时器到期后，在重新检测内容时再切换到严格模式
                            Log.d(TAG, "APP " + appName + " 宽松模式一次性生效，定时器将使用原时间间隔: " + intervalSeconds + "秒");
                        }
                        hideFloatingWindow();

                        // 根据当前APP的用户设置时间间隔 自动重新显示
                        if (appTimers.get(currentActiveApp) != null) {
                            autoShowHandler.removeCallbacks(appTimers.get(currentActiveApp));
                        }

                        // 保存当前APP的引用，用于定时器回调
                        Object appForTimer = currentActiveApp;

                        Runnable nextShowTask = () -> {
                            Log.d(TAG, appForTimer + " 到达预期时间");
                            if (appForTimer != null) {
                                boolean beforeState = Share.isAppManuallyHidden(appForTimer);
                                String timerAppName = getAppName(appForTimer);
                                Log.d(TAG, "定时器触发 - APP: " + timerAppName +
                                        ", 设置前手动隐藏状态: " + beforeState);

                                Share.setAppManuallyHidden(appForTimer, false);

                                boolean afterState = Share.isAppManuallyHidden(appForTimer);
                                Log.d(TAG, "解除APP " + timerAppName + " 的手动隐藏状态 - 设置后状态: " + afterState);

                                // 如果是宽松模式，现在切换到严格模式
                                if (settingsManager.isAppCasualMode(appForTimer)) {
                                    settingsManager.setAppAutoShowInterval(appForTimer, settingsManager.getMaxDailyInterval());
                                    Log.d(TAG, "APP " + timerAppName + " 宽松模式已切换到严格模式");
                                }

                                // 检查当前是否在该APP中，如果是则尝试显示悬浮窗
                                if (currentActiveApp == appForTimer) {
                                    // 重新检测内容并显示悬浮窗
                                    Log.d(TAG, "开始重新检测内容 - APP: " + timerAppName);
                                    checkTextContentOptimized(true); // 使用强制检查模式
                                    Log.d(TAG, "自动重新显示悬浮窗 - 重新检测内容完成 - APP: " + timerAppName);
                                } else {
                                    String currentAppName = currentActiveApp != null ? getAppName(currentActiveApp) : "null";
                                    Log.d(TAG, "自动重新显示条件不满足 - 当前APP: " + currentAppName +
                                            ", 定时器APP: " + timerAppName +
                                            " (用户可能已离开该APP)");
                                }
                            }
                        };

                        // 使用当前（宽松模式）的时间间隔安排下次显示
                        autoShowHandler.postDelayed(nextShowTask, interval);
                        appTimers.put(currentActiveApp, nextShowTask);

                        String intervalText = SettingsManager.getIntervalDisplayText(intervalSeconds);
                        Log.d(TAG, "计划在" + intervalText + "后自动重新显示悬浮窗 (APP: " + appName + ")");

                        // 显示下次使用的时间间隔
                        int nextIntervalSeconds = currentActiveApp != null ?
                                settingsManager.getAppAutoShowInterval(currentActiveApp) :
                                settingsManager.getAutoShowInterval();
                        Log.d(TAG, "下次时长：" + nextIntervalSeconds + "秒 (APP: " + appName + ")");
                    }

                }
                
                @Override
                public void onChallengeCancel() {
                    Log.d(TAG, "用户取消数学题验证");
                }
            });
            
            Button closeButton = floatingView.findViewById(R.id.btn_close);
            closeButton.setOnClickListener(v -> {
                Log.d(TAG, "用户点击关闭按钮");
                
                // 微信APP直接当作答题通过，不显示数学题
                if (currentActiveApp instanceof Const.SupportedApp && currentActiveApp == Const.SupportedApp.WECHAT) {
                    Log.d(TAG, "微信APP直接当作答题通过");
                    // 直接调用答题成功的逻辑
                    if (mathChallengeManager != null && mathChallengeManager.getOnMathChallengeListener() != null) {
                        mathChallengeManager.getOnMathChallengeListener().onAnswerCorrect();
                    }
                } else {
                    // 其他APP显示数学题验证界面
                    mathChallengeManager.showMathChallenge();
                }
            });

            // 更新悬浮窗内容，显示当前时间间隔设置
            updateFloatingWindowContent();

            // 添加悬浮窗到窗口管理器
            try {
                windowManager.addView(floatingView, layoutParams);
                isFloatingWindowVisible = true;
                Share.isFloatingWindowVisible = true; // 同步状态
                Log.d(TAG, "悬浮窗显示成功");
            } catch (Exception e) {
                Log.e(TAG, "显示悬浮窗失败", e);
                isFloatingWindowVisible = false;
                Share.isFloatingWindowVisible = false; // 同步状态
            }
            // 获取下次的文字
            String packageName = getAppPackageName(currentActiveApp);
            String source = settingsManager.getAppHintSource(packageName);
            // 自定义来源
            if (Const.DEFAULT_HINT_SOURCE.equals(source)){
                fetchNew();
            }
        }
    }

    private void fetchNew() {
        // 异步获取最新的动态文字内容
        if (floatingTextFetcher != null) {
            floatingTextFetcher.fetchLatestText(new FloatingTextFetcher.OnTextFetchListener() {
                @Override
                public void onTextFetched(String text) {
                    Log.d(TAG, "获取到新的动态文字");
                }
                @Override
                public void onFetchError(String error) {
                    Log.w(TAG, "获取动态文字失败: " + error);
                    // 保持使用缓存的文字，不做额外处理
                }
            });
        }
    }

    /**
     * 更新悬浮窗内容
     */
    private void updateFloatingWindowContent() {
        if (floatingView == null) return;
        
        TextView contentText = floatingView.findViewById(R.id.tv_content);
        if (contentText != null) {
            // 获取缓存的动态文字内容
            String dynamicText = "";

            String packageName = getAppPackageName(currentActiveApp);
            String source = settingsManager.getAppHintSource(packageName);

            // 自定义来源
            if (Const.CUSTOM_HINT_SOURCE.equals(source)) {
                dynamicText =  settingsManager.getAppHintCustomText(packageName);
            } else {
            // 大模型来源
                if (floatingTextFetcher != null) {
                    dynamicText = floatingTextFetcher.getCachedText();
                }
            }

            // 显示动态文字和时间间隔信息
            String content = dynamicText;
            if (settingsManager != null) {
                // 使用当前APP的时间间隔显示
                int intervalSeconds;
                String appName = "";
                if (currentActiveApp != null) {
                    intervalSeconds = settingsManager.getAppAutoShowInterval(currentActiveApp);
                    appName = getAppName(currentActiveApp);
                    Log.d(TAG, "悬浮窗显示APP " + appName + " 的时间间隔: " + intervalSeconds + "秒");
                } else {
                    intervalSeconds = settingsManager.getAutoShowInterval();
                    Log.d(TAG, "悬浮窗显示全局时间间隔: " + intervalSeconds + "秒");
                }
                
                String intervalText = SettingsManager.getIntervalDisplayText(intervalSeconds);
                String hintTIme = "\n若关闭，" + intervalText + "后将重新显示本页面";
                if (!dynamicText.isEmpty()) {
                    content = dynamicText + "\n" + hintTIme;
                } else {
                    content = hintTIme;
                }
            }
            String targetDateStr = settingsManager.getTargetCompletionDate();
            content = FloatHelper.hintDate(targetDateStr) + content;
            contentText.setText(content);
            Log.d(TAG, "悬浮窗内容已更新");
        }
    }

    private void hideFloatingWindow() {
        if (isFloatingWindowVisible) {
            Log.d(TAG, "开始隐藏悬浮窗");
            
            // 隐藏数学题验证界面
            if (mathChallengeManager != null && mathChallengeManager.isMathChallengeActive()) {
                mathChallengeManager.hideMathChallenge();
            }
            
            try {
                if (floatingView != null && windowManager != null) {
                    windowManager.removeView(floatingView);
                    floatingView = null;
                    mathChallengeManager = null; // 清理管理器引用
                    Log.d(TAG, "悬浮窗隐藏成功");
                }
            } catch (Exception e) {
                Log.e(TAG, "隐藏悬浮窗失败", e);
            }
            
            isFloatingWindowVisible = false;
            Share.isFloatingWindowVisible = false; // 同步状态
        }
    }

    public static boolean isServiceRunning() {
        return instance != null;
    }

    
    /**
     * 通知时间间隔设置已更新，立即应用新的间隔
     */
    public static void notifyIntervalChanged() {
        if (instance != null) {
            instance.updateFloatingWindowContent();
            
            // 如果当前有正在运行的自动显示定时器，重新启动它
            if (instance.appTimers.size() > 0) {
                for (Map.Entry<Object, Runnable> entry : instance.appTimers.entrySet()) {
                    boolean appManuallyHidden = Share.isAppManuallyHidden(entry.getKey());
                    if (appManuallyHidden) {
                        // 取消当前的定时器
                        instance.autoShowHandler.removeCallbacks(entry.getValue());
                        
                        // 使用当前APP的新时间间隔重新启动定时器
                        long newInterval = instance.settingsManager.getAppAutoShowIntervalMillis(entry.getKey());
                        instance.autoShowHandler.postDelayed(entry.getValue(), newInterval);
                        
                        int intervalSeconds = instance.settingsManager.getAppAutoShowInterval(entry.getKey());
                        String intervalText = SettingsManager.getIntervalDisplayText(intervalSeconds);
                        String appName = instance.getAppName(entry.getKey());
                        Log.d(instance.TAG, "时间间隔设置已更新，立即应用新间隔: " + intervalText + " (APP: " + appName + ")");
                        
                        // 显示提示
                        Toast.makeText(instance,
                            "⏰ 定时器已更新，将在" + intervalText + "后重新显示", 
                            Toast.LENGTH_SHORT).show();
                    }
                }
            }
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
     * 触发立即检查是否需要显示悬浮窗
     * 当用户从宽松模式切换到严格模式时调用
     */
    public static void triggerImmediateCheck(Const.SupportedApp app) {
        if (instance != null) {
            Log.d(TAG, "收到立即检查请求，目标APP: " + app.name());
            
            // 检查当前是否在目标APP中
            if (app == instance.currentActiveApp) {
                Log.d(TAG, "当前正在目标APP中，检查是否需要显示悬浮窗");
                
                // 检查APP状态
                String appState = Share.getAppState(app);
                boolean isManuallyHidden = Share.isAppManuallyHidden(app);
                
                Log.d(TAG, "APP " + app.name() + " 状态: " + appState + ", 手动隐藏: " + isManuallyHidden);
                
                // 如果APP状态是target且没有手动隐藏，立即显示悬浮窗
                if ("target".equals(appState) && !isManuallyHidden) {
                    Log.d(TAG, "立即显示悬浮窗");
                    instance.showFloatingWindow();
                } else {
                    Log.d(TAG, "不满足显示条件，不显示悬浮窗");
                }
            } else {
                Log.d(TAG, "当前不在目标APP中，无需显示悬浮窗");
            }
        } else {
            Log.w(TAG, "无障碍服务实例不存在，无法立即检查");
        }
    }
    
    /**
     * 触发立即检查是否需要显示悬浮窗 - 支持自定义APP
     */
    public static void triggerImmediateCheck(Object app) {
        if (instance != null) {
            String appName = instance.getAppName(app);
            Log.d(TAG, "收到立即检查请求，目标APP: " + appName);
            
            // 检查当前是否在目标APP中
            if (app == instance.currentActiveApp) {
                Log.d(TAG, "当前正在目标APP中，检查是否需要显示悬浮窗");
                
                // 检查APP状态
                String appState = Share.getAppState(app);
                boolean isManuallyHidden = Share.isAppManuallyHidden(app);
                
                Log.d(TAG, "APP " + appName + " 状态: " + appState + ", 手动隐藏: " + isManuallyHidden);
                
                // 如果APP状态是target且没有手动隐藏，立即显示悬浮窗
                if ("target".equals(appState) && !isManuallyHidden) {
                    Log.d(TAG, "立即显示悬浮窗");
                    instance.showFloatingWindow();
                } else {
                    Log.d(TAG, "不满足显示条件，不显示悬浮窗");
                }
            } else {
                Log.d(TAG, "当前不在目标APP中，无需显示悬浮窗");
            }
        } else {
            Log.w(TAG, "无障碍服务实例不存在，无法立即检查");
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
                if (currentActiveApp != null && "target".equals(Share.getAppState(currentActiveApp))) {
                    boolean appManuallyHidden = Share.isAppManuallyHidden(currentActiveApp);
                    if (!isFloatingWindowVisible && !appManuallyHidden) {
                        handler.postDelayed(() -> {
                            String appName = getAppName(currentActiveApp);
                            Log.d(TAG, "屏幕解锁后恢复悬浮窗显示 (APP: " + appName + ")");
                            showFloatingWindow();
                        }, 1000);
                    } else if (appManuallyHidden) {
                        String appName = getAppName(currentActiveApp);
                        Log.d(TAG, "APP " + appName + " 被手动隐藏，屏幕解锁后不恢复悬浮窗");
                    }
                }
            }
            
            @Override
            public void onUserPresent() {
                Log.d(TAG, "用户解锁设备，重新检查应用状态");
                // 用户解锁后，重新检测当前是否在支持的APP
                handler.postDelayed(() -> {
                    if (currentActiveApp != null) {
                        String appName = getAppName(currentActiveApp);
                        Log.d(TAG, "用户解锁后重新检测APP: " + appName);
                        checkTextContentOptimized();
                    }
                }, 1500);
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
    
    /**
     * 初始化应用状态检测增强机制
     */
    private void initAppStateEnhancement() {
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
            if (mathChallengeManager != null && mathChallengeManager.isMathChallengeActive()) {
                Log.v(TAG, "数学题验证界面活跃，暂停应用状态检测");
                return;
            }
            
            // 如果数学题验证界面刚刚显示（前5秒），也暂停状态检测
            // 这可以避免输入法显示过程中的误判
            if (mathChallengeStartTime > 0 && (System.currentTimeMillis() - mathChallengeStartTime) < 5000) {
                long currentTime = System.currentTimeMillis();
                long timeSinceStart = currentTime - mathChallengeStartTime;
                Log.v(TAG, "数学题验证界面刚显示，暂停应用状态检测 [开始时间: " + formatTime(mathChallengeStartTime) + ", 当前时间: " + formatTime(currentTime) + ", 已过去: " + timeSinceStart + "ms]");
                return;
            }
            
            // 获取当前窗口信息
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                String currentPackage = rootNode.getPackageName() != null ? 
                    rootNode.getPackageName().toString() : "";
                
                // 检测当前是否是支持的APP（包括预定义和自定义）
                Object detectedApp = detectSupportedApp(currentPackage);
                Log.d(TAG, "detectedApp 包名： " + currentPackage);

                // 多APP状态检测
                if (detectedApp != null) {
                    if (detectedApp != currentActiveApp) {
                        String appName = getAppName(detectedApp);
                        Log.d(TAG, "应用状态检测：发现支持的APP - " + appName);
                        currentActiveApp = detectedApp;
                        Share.currentApp = currentActiveApp;
                        
                        // 检查当前APP是否被手动隐藏
                        boolean appManuallyHidden = Share.isAppManuallyHidden(currentActiveApp);
                        Log.d(TAG, "APP " + appName + " 手动隐藏状态: " + appManuallyHidden);
                        
                        checkTextContentOptimized();
                    }
                } else {
                    if (currentActiveApp != null) {
                        String appName = getAppName(currentActiveApp);
                        Log.d(TAG, "应用状态检测：离开支持的APP - " + appName);
                        Share.clearAppState(currentActiveApp);
                        // 不清理手动隐藏状态，保持到下次自动解除
                        
                        // 清理该APP的定时器
                        if (appTimers.containsKey(currentActiveApp)) {
                            autoShowHandler.removeCallbacks(appTimers.get(currentActiveApp));
                            appTimers.remove(currentActiveApp);
                            Log.d(TAG, "清理APP " + appName + " 的定时器");
                        }
                        
                        currentActiveApp = null;
                        Share.currentApp = null;
                        hideFloatingWindow();
                    }
                }
                
                rootNode.recycle();
            }
        } catch (Exception e) {
            Log.w(TAG, "应用状态检测出错", e);
        }
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
        
        // 清理Handler资源
        if (handler != null && contentCheckRunnable != null) {
            handler.removeCallbacks(contentCheckRunnable);
        }
        
        // 清理自动显示Handler
        if (autoShowHandler != null && autoShowRunnable != null) {
            autoShowHandler.removeCallbacks(autoShowRunnable);
        }
        
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
        
        // 清理悬浮窗
        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
                floatingView = null;
            } catch (Exception e) {
                Log.e(TAG, "清理悬浮窗时出错", e);
            }
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
        if (floatingTextFetcher != null) {
            floatingTextFetcher.cleanup();
            floatingTextFetcher = null;
        }
        
        // 清理多APP状态
        Share.clearAllAppStates();
        currentActiveApp = null;

        Log.d(TAG, "AccessibilityService 已销毁");
        Toast.makeText(this, "无障碍服务已停止", Toast.LENGTH_SHORT).show();
    }

    /**
     * 通知HomeFragment更新特定APP的UI显示
     */
    private void notifyHomeFragmentUpdate(Const.SupportedApp app) {
        try {
            // 通过广播通知MainActivity更新HomeFragment
            Intent intent = new Intent(Const.ACTION_UPDATE_CASUAL_COUNT);
            intent.putExtra("app_name", app.name());
            sendBroadcast(intent);
            Log.d(TAG, "已发送更新APP " + app.name() + " 宽松模式次数的广播");
        } catch (Exception e) {
            Log.w(TAG, "发送更新广播失败: " + e.getMessage());
        }
    }
    
    /**
     * 通知HomeFragment更新特定APP的UI显示 - 支持自定义APP
     */
    private void notifyHomeFragmentUpdate(Object app) {
        try {
            // 通过广播通知MainActivity更新HomeFragment
            Intent intent = new Intent(Const.ACTION_UPDATE_CASUAL_COUNT);
            String appName = getAppName(app);
            intent.putExtra("app_name", appName);
            sendBroadcast(intent);
            Log.d(TAG, "已发送更新APP " + appName + " 宽松模式次数的广播");
        } catch (Exception e) {
            Log.w(TAG, "发送更新广播失败: " + e.getMessage());
        }
    }


    private WindowManager.LayoutParams getLayoutParams(){

        // 设置悬浮窗参数
        layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        // 添加FLAG_NOT_FOCUSABLE确保悬浮窗不会获得焦点，避免影响前台应用检测
        // 添加FLAG_NOT_TOUCH_MODAL确保触摸事件可以传递到下层窗口
        // 添加FLAG_NOT_TOUCHABLE确保悬浮窗默认不拦截触摸事件（除了特定区域）
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        layoutParams.format = PixelFormat.TRANSLUCENT;
        layoutParams.gravity = Gravity.LEFT | Gravity.TOP;

        // 计算悬浮窗位置和大小
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);

        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;

        // 设置悬浮窗位置和大小
        int topOffset = settingsManager.getFloatingTopOffset();
        int bottomOffset = settingsManager.getFloatingBottomOffset();

        layoutParams.x = 0;
        layoutParams.y = topOffset;
        layoutParams.width = screenWidth;
        layoutParams.height = screenHeight - topOffset - bottomOffset;
        return layoutParams;
    }

    /**
     * 检测包名对应的支持APP（包括预定义和自定义）
     */
    private Object detectSupportedApp(String packageName) {
        // 首先检查预定义的APP
        Const.SupportedApp supportedApp = Const.SupportedApp.getByPackageName(packageName);
        if (supportedApp != null) {
            // 检查该APP的监测开关状态
            if (!settingsManager.shouldMonitorApp(packageName)) {
                Log.d(TAG, "APP " + supportedApp.getAppName() + " 监测已关闭，跳过检测");
                return null;
            }
            return supportedApp;
        }
        
        // 然后检查自定义APP
        try {
            com.book.baisc.config.CustomAppManager customAppManager = 
                com.book.baisc.config.CustomAppManager.getInstance();
            java.util.List<Const.CustomApp> customApps = customAppManager.getCustomApps();
            
            for (Const.CustomApp customApp : customApps) {
                if (customApp.getPackageName().equals(packageName)) {
                    // 检查该APP的监测开关状态
                    if (!settingsManager.shouldMonitorApp(packageName)) {
                        Log.d(TAG, "自定义APP " + customApp.getAppName() + " 监测已关闭，跳过检测");
                        return null;
                    }
                    return customApp;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "检查自定义APP时出错", e);
        }
        
        return null;
    }
    
    /**
     * 获取APP名称
     */
    private String getAppName(Object app) {
        if (app instanceof Const.SupportedApp) {
            return ((Const.SupportedApp) app).getAppName();
        } else if (app instanceof Const.CustomApp) {
            return ((Const.CustomApp) app).getAppName();
        }
        return "未知APP";
    }
    
    /**
     * 获取APP包名
     */
    private String getAppPackageName(Object app) {
        if (app instanceof Const.SupportedApp) {
            return ((Const.SupportedApp) app).getPackageName();
        } else if (app instanceof Const.CustomApp) {
            return ((Const.CustomApp) app).getPackageName();
        }
        return "unknown";
    }
    
    /**
     * 获取APP目标词
     */
    private String getAppTargetWord(Object app) {
        if (app instanceof Const.SupportedApp) {
            return ((Const.SupportedApp) app).getTargetWord();
        } else if (app instanceof Const.CustomApp) {
            return ((Const.CustomApp) app).getTargetWord();
        }
        return "";
    }
} 