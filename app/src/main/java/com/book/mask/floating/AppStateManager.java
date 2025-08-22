package com.book.mask.floating;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.book.mask.config.Const;
import com.book.mask.config.CustomApp;
import com.book.mask.config.CustomAppManager;
import com.book.mask.config.Share;
import com.book.mask.setting.RelaxManager;

import java.util.Map;
import java.util.HashMap;

/**
 * 应用状态管理器
 * 负责应用状态检测、文本内容检测、定时器管理等
 */
public class AppStateManager {
    private static final String TAG = "AppStateManager";
    
    private AccessibilityService service;
    private Handler handler;
    private Handler autoShowHandler;
    private RelaxManager relaxManager;
    
    // 应用状态相关
    private CustomApp currentActiveApp = null;
    private String lastPackageName = null;
    private long lastContentCheckTime = 0;
    private long lastWindowCheckTime = 0;

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
        void onTimerTriggered(CustomApp app);
    }
    
    public AppStateManager(AccessibilityService service, RelaxManager relaxManager) {
        this.service = service;
        this.relaxManager = relaxManager;
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
        
        String eventPackageName = (String)event.getPackageName();
        if(!eventPackageName.equals(service.getPackageName())){
            lastPackageName = (String) event.getPackageName();
        }
    }
    
    /**
     * 处理窗口状态变化
     */
    private void handleWindowStateChanged(AccessibilityEvent event) {
        if (event.getPackageName() != null) {
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

            // 检测当前是否是支持的APP（包括预定义和自定义）
            CustomApp detectedApp = detectSupportedApp(packageName);

            Log.d(TAG, "当前应用: " + packageName + "，lastPackageName: " + lastPackageName);

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
                    if (listener != null) {
                        listener.onAppLeft(currentActiveApp);
                    }
                }
            }
        }
    }
    
    /**
     * 处理窗口内容变化
     */
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
            // 获取当前窗口信息
            AccessibilityNodeInfo rootNode = service.getRootInActiveWindow();
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
                        cancelTimer(currentActiveApp);
                        
                        currentActiveApp = null;
                        Share.currentApp = null;
                        if (listener != null) {
                            listener.onAppLeft(currentActiveApp);
                        }
                    }
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
        // 检查所有APP（包括预定义和自定义）
        try {
            CustomAppManager customAppManager = CustomAppManager.getInstance();
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
    
    public CustomApp getCurrentActiveApp() {
        return currentActiveApp;
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
        if (handler != null && contentCheckRunnable != null) {
            handler.removeCallbacks(contentCheckRunnable);
        }
    }
}
