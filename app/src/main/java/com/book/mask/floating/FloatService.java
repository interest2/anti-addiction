package com.book.mask.floating;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

import com.book.mask.R;
import com.book.mask.config.Const;
import com.book.mask.config.CustomAppManager;
import com.book.mask.config.Share;
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
 * 整合了无障碍服务和悬浮窗管理功能
 */
public class FloatService extends AccessibilityService
{

    private static final String TAG = "FloatingAccessibility";
    private static FloatService instance;
    private boolean isFloatingWindowVisible = false;
    private CustomApp currentActiveApp = null; // 当前活跃的APP（统一使用CustomApp）
    
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
    private long lastWindowCheckTime = 0;

    // 窗口状态变化锁机制
    public static String currentPackageName = "";
    // 包名记录和比较
    private String lastPackageName = null; // 记录上一次的包名

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
    private RelaxManager relaxManager;
    private AppSettingsManager appSettingsManager;
    
    // 设备信息上报器
    private DeviceInfoReporter deviceInfoReporter;
    
    // 悬浮窗文字获取器
    private TextFetcher textFetcher;

    // 悬浮窗管理相关
    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams layoutParams;
    private Handler autoShowHandler;
    private Runnable autoShowRunnable;
    
    // 为每个APP维护独立的定时器
    private Map<CustomApp, Runnable> appTimers = new HashMap<>();

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "AccessibilityService 已连接！");
        Toast.makeText(this, "无障碍服务已启动", Toast.LENGTH_LONG).show();

        Log.d(TAG, "AccessibilityService 开始连接");
        
        // 初始化处理器
        handler = new Handler(Looper.getMainLooper());
        autoShowHandler = new Handler(Looper.getMainLooper());

        // 初始化悬浮窗管理器
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        // 配置无障碍服务
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(info);
        
        // 初始化保活管理器
        try {
            Log.d(TAG, "开始初始化保活管理器");
            initKeepAliveManager();
            Log.d(TAG, "保活管理器初始化完成");
        } catch (Exception e) {
            Log.e(TAG, "保活管理器初始化失败", e);
        }
        
        // 初始化应用状态检测增强机制
        try {
            Log.d(TAG, "开始初始化应用状态检测增强机制");
            initAppStateEnhancement();
            Log.d(TAG, "应用状态检测增强机制初始化完成");
        } catch (Exception e) {
            Log.e(TAG, "应用状态检测增强机制初始化失败", e);
        }
        
        // 初始化设置管理器
        relaxManager = new RelaxManager(this);
        appSettingsManager = new AppSettingsManager(this);
        
        // 初始化设备信息上报器并上报设备信息
        deviceInfoReporter = new DeviceInfoReporter(this);
        deviceInfoReporter.reportDeviceInfo();
        
        // 初始化悬浮窗文字获取器
        textFetcher = new TextFetcher(this);
        
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
        String eventPackageName = (String)event.getPackageName();
        if(!eventPackageName.equals(getPackageName())){
            lastPackageName = (String) event.getPackageName();
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
            CustomApp detectedApp = detectSupportedApp(packageName);

            Log.d(TAG, "当前应用: " + packageName
                    + "，lastPackageName: " + lastPackageName);

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastWindowCheckTime < 350
                    && detectSupportedApp(lastPackageName) != null
                    && detectedApp == null) {
                Log.d(TAG, "短时内切换窗口，需忽略");
                return; // 短时内切换事件直接忽略
            }
            lastWindowCheckTime = currentTime;
            
            if (detectedApp != null) {
                String appName = detectedApp.getAppName();
                Log.d(TAG, "检测到支持的APP: " + appName + " (包名: " + packageName + ")");
                
                if (detectedApp != currentActiveApp) {
                    // 切换到新的APP
                    if (currentActiveApp != null) {
                        String oldAppName = currentActiveApp.getAppName();
                        Log.d(TAG, "离开APP: " + oldAppName);
                        Share.clearAppState(currentActiveApp);
                        // 不清理手动隐藏状态，保持到下次自动解除
                    }
                    
                    currentActiveApp = detectedApp;
                    Share.currentApp = currentActiveApp;
                    Log.d(TAG, "进入APP: " + appName);

//                    // 检查当前APP是否被手动隐藏
//                    boolean appManuallyHidden = Share.isAppManuallyHidden(currentActiveApp);
//                    Log.d(TAG, "APP " + appName + " 手动隐藏状态: " + appManuallyHidden);

                    // 立即开始检测文本内容
                    checkTextContentOptimized();
                }
            } else {
                // 离开所有支持的APP
                if (currentActiveApp != null) {
                    String oldAppName = currentActiveApp.getAppName();
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
            if (currentActiveApp.getPackageName().equals(packageName)) {
                
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

            currentPackageName = currentActiveApp.getPackageName();
            // 有活跃APP的前提下，进行包名比较：如果与上次不同：抢先显示悬浮窗
            if (!currentPackageName.equals(lastPackageName)) {
                Log.d(TAG, "包名变化，抢先显示悬浮窗: " + currentPackageName);
                showFloatingWindow();
            }
            Log.d(TAG, "当前有活跃的APP，且符合条件，开始文本检测");
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
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
                    Log.d(TAG, "检测到目标界面 - APP: " + appName + ", 手动隐藏状态: " + appManuallyHidden +
                            ", 悬浮窗可见: " + isFloatingWindowVisible + ", 强制检查: " + forceCheck);

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
        layoutParams = FloatHelper.getLayoutParams(windowManager, appSettingsManager);
        
        // 初始化数学题验证管理器
        if (floatingView != null) {
            mathChallengeManager = new MathChallengeManager(
                this, floatingView, windowManager, layoutParams, handler, this
            );
            
            // 设置当前APP，用于微信APP的特殊处理
            if (currentActiveApp != null) {
                mathChallengeManager.setCurrentApp(currentActiveApp);
            }
            
            // 更新悬浮窗内容（包括日常提醒）
            updateFloatingWindowContent();
            
            mathChallengeManager.setOnMathChallengeListener(new MathChallengeManager.OnMathChallengeListener() {
                @Override
                public void onAnswerCorrect() {
                    Log.d(TAG, "数学题验证成功，关闭悬浮窗");

                    // 获取当前的时间间隔（宽松模式生效一次）
                    long interval;
                    int intervalSeconds;
                    
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
                        hideFloatingWindow();

                        // 如果已有当前 APP 的定时显示任务，则移除它，下面会重设
                        if (appTimers.get(currentActiveApp) != null) {
                            autoShowHandler.removeCallbacks(appTimers.get(currentActiveApp));
                        }

                        // 到期将执行的任务内容
                        Runnable nextShowTask = getRunnable();

                        // 使用当前的时间间隔 安排下次显示
                        autoShowHandler.postDelayed(nextShowTask, interval);
                        appTimers.put(currentActiveApp, nextShowTask);

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
                
                @Override
                public void onChallengeCancel() {
                    Log.d(TAG, "用户取消数学题验证");
                }
            });
            
            Button closeButton = floatingView.findViewById(R.id.btn_close);
            closeButton.setOnClickListener(v -> {
                Log.d(TAG, "用户点击关闭按钮");
                
                // 微信APP直接当作答题通过，不显示数学题
                if (CustomAppManager.WECHAT_PACKAGE.equals(currentActiveApp.getPackageName())) {
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
            String packageName = currentActiveApp.getPackageName();
            String source = appSettingsManager.getAppHintSource(packageName);
            // 默认来源（大模型）
            if (Const.DEFAULT_HINT_SOURCE.equals(source)){
                fetchNew();
            }
        }
    }

    @NonNull
    private Runnable getRunnable() {
        CustomApp appForTimer = currentActiveApp;

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
            }
        };
        return nextShowTask;
    }

    private void fetchNew() {
        // 异步获取最新的动态文字内容
        if (textFetcher != null) {
            textFetcher.fetchLatestText(new TextFetcher.OnTextFetchListener() {
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

            String packageName = currentActiveApp.getPackageName();
            String source = appSettingsManager.getAppHintSource(packageName);

            // 自定义来源
            if (Const.CUSTOM_HINT_SOURCE.equals(source)) {
                dynamicText =  appSettingsManager.getAppHintCustomText(packageName);
            } else {
            // 大模型来源
                if (textFetcher != null) {
                    dynamicText = textFetcher.getCachedText();
                }
            }

            // 显示动态文字和时间间隔信息
            String content = dynamicText;
            if (relaxManager != null) {
                // 使用当前APP的时间间隔显示
                int intervalSeconds;
                if (currentActiveApp != null) {

                    /*如果上次关闭时是宽松模式，则本次显示应当切为严格模式*/
                    int appLastCloseInterval = relaxManager.getAppLastCloseInterval(currentActiveApp);
                    if(relaxManager.isLastRelaxedMode(appLastCloseInterval)){
                        intervalSeconds = relaxManager.getMaxStrictInterval();
                        relaxManager.setAppInterval(currentActiveApp, intervalSeconds);
                    }else {
                        intervalSeconds = relaxManager.getAppInterval(currentActiveApp);
                    }

//                    appName = currentActiveApp.getAppName();
//                    Log.d(TAG, "悬浮窗显示APP " + appName + " 的时间间隔: " + intervalSeconds + "秒");
                } else {
                    intervalSeconds = relaxManager.getDefaultInterval();
                }
                
                String intervalText = RelaxManager.getIntervalDisplayText(intervalSeconds);
                String hintTIme = "\n若关闭，" + intervalText + "后将重新显示本页面";
                if (!dynamicText.isEmpty()) {
                    content = dynamicText + "\n" + hintTIme;
                } else {
                    content = hintTIme;
                }
            }
            String targetDateStr = appSettingsManager.getTargetCompletionDate();
            content = FloatHelper.hintDate(targetDateStr) + content;
            contentText.setText(content);
        }
        
        // 更新日常提醒显示
        updateStrictReminderDisplay();
    }

    /**
     * 更新日常提醒显示
     */
    private void updateStrictReminderDisplay() {
        if (floatingView == null) return;
        
        android.view.View strictReminderLayout = floatingView.findViewById(R.id.strict_reminder_layout);
        android.widget.TextView strictReminderText = floatingView.findViewById(R.id.tv_strict_reminder);
        android.widget.TextView strictReminderHint = floatingView.findViewById(R.id.tv_strict_reminder_hint);
        
        if (strictReminderLayout != null && strictReminderText != null && strictReminderHint != null) {
            String strictReminder = appSettingsManager.getFloatingStrictReminder();
            boolean hasClickedSettings = appSettingsManager.getFloatingStrictReminderSettingsClicked();
            int fontSize = appSettingsManager.getFloatingStrictReminderFontSize();
            
            // 应用自定义字体大小
            strictReminderText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize);
            
            // 如果用户没有设置过提醒文字，显示默认文字
            if (strictReminder.isEmpty()) {
                strictReminderText.setText("玩手机？不如——\n多喝水、多起身活动");
                strictReminderLayout.setVisibility(android.view.View.VISIBLE);
                
                // 如果用户没有点击过设置按钮，显示小字提示
                if (!hasClickedSettings) {
                    strictReminderHint.setVisibility(android.view.View.VISIBLE);
                } else {
                    // 用户点击过设置按钮，只隐藏小字提示
                    strictReminderHint.setVisibility(android.view.View.GONE);
                }
            } else {
                // 用户设置了自定义提醒文字
                strictReminderText.setText(strictReminder);
                strictReminderHint.setVisibility(android.view.View.GONE);
                strictReminderLayout.setVisibility(android.view.View.VISIBLE);
//                Log.d(TAG, "显示自定义日常提醒: " + strictReminder + "，字体大小: " + fontSize + "sp");
            }
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
                for (Map.Entry<CustomApp, Runnable> entry : instance.appTimers.entrySet()) {
                    boolean appManuallyHidden = Share.isAppManuallyHidden(entry.getKey());
                    if (appManuallyHidden) {
                        // 取消当前的定时器
                        instance.autoShowHandler.removeCallbacks(entry.getValue());
                        
                        // 使用当前APP的新时间间隔重新启动定时器
                        long newInterval = instance.relaxManager.getAppIntervalMillis(entry.getKey());
                        instance.autoShowHandler.postDelayed(entry.getValue(), newInterval);
                        
                        int intervalSeconds = instance.relaxManager.getAppInterval(entry.getKey());
                        String intervalText = RelaxManager.getIntervalDisplayText(intervalSeconds);
                        String appName = entry.getKey().getAppName();
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
                            String appName = currentActiveApp.getAppName();
                            Log.d(TAG, "屏幕解锁后恢复悬浮窗显示 (APP: " + appName + ")");
                            showFloatingWindow();
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
//                handler.postDelayed(() -> {
//
//                }, 100);
                if (currentActiveApp != null) {
                    String appName = currentActiveApp.getAppName();
                    Log.d(TAG, "用户解锁后重新检测APP: " + appName);
                    checkTextContentOptimized();
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
                CustomApp detectedApp = detectSupportedApp(currentPackage);
                Log.d(TAG, "detectedApp 包名： " + currentPackage);
                if(currentActiveApp != null){
                    Log.d(TAG, "currentActiveApp 包名： " + currentActiveApp.getPackageName());
                }

                // 多APP状态检测
                if (detectedApp != null) {
                    if (detectedApp != currentActiveApp) {
                        String appName = detectedApp.getAppName();
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
                        String appName = currentActiveApp.getAppName();
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
        if (textFetcher != null) {
            textFetcher.cleanup();
            textFetcher = null;
        }
        
        // 清理多APP状态
        Share.clearAllAppStates();
        currentActiveApp = null;

        Log.d(TAG, "AccessibilityService 已销毁");
        Toast.makeText(this, "无障碍服务已停止", Toast.LENGTH_SHORT).show();
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

    /**
     * 检测包名对应的支持APP（统一使用CustomApp）
     */
    private CustomApp detectSupportedApp(String packageName) {
        // 检查所有APP（包括预定义和自定义）
        try {
            com.book.mask.config.CustomAppManager customAppManager = 
                com.book.mask.config.CustomAppManager.getInstance();
            CustomApp app = customAppManager.getAppByPackageName(packageName);
            
            if (app != null) {
                // 检查该APP的监测开关状态
                if (!relaxManager.shouldMonitorApp(packageName)) {
                    Log.d(TAG, "APP " + app.getAppName() + " 监测已关闭，跳过检测");
                    return null;
                }
                return app;
            }
        } catch (Exception e) {
            Log.w(TAG, "检查APP时出错", e);
        }
        
        return null;
    }
    
}