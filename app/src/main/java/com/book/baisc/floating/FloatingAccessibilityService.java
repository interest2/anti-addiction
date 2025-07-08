package com.book.baisc.floating;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;

import com.book.baisc.R;
import com.book.baisc.lifecycle.ServiceKeepAliveManager;
import com.book.baisc.config.SettingsManager;
import com.book.baisc.network.DeviceInfoReporter;

/**
 * 悬浮窗无障碍服务
 * 整合了无障碍服务和悬浮窗管理功能
 */
public class FloatingAccessibilityService extends AccessibilityService 
{

    private static final String TAG = "FloatingAccessibility";
    private static final String XIAOHONGSHU_PACKAGE = "com.xingin.xhs";
    private static FloatingAccessibilityService instance;
    private boolean isFloatingWindowVisible = false;
    private boolean isInXiaohongshu = false;
    
    // 性能优化相关
    private Handler handler;
    private Runnable contentCheckRunnable;
    private static final long CONTENT_CHECK_DELAY = 300; // 300ms防抖延迟
    private long lastContentCheckTime = 0;
    private String lastDetectedInterface = ""; // 缓存上次检测的界面类型
    
    // 悬浮窗管理相关
    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams layoutParams;
    private boolean isManuallyHidden = false;
    private Handler autoShowHandler;
    private Runnable autoShowRunnable;
    
    // 数学题验证管理器
    private MathChallengeManager mathChallengeManager;
    
    // 保活管理器
    private ServiceKeepAliveManager keepAliveManager;
    
    // 应用状态检测增强
    private Handler appStateHandler;
    private Runnable appStateCheckRunnable;
    private long lastAppStateCheckTime = 0;
    private static final long APP_STATE_CHECK_INTERVAL = 2000; // 2秒检查一次
    private long mathChallengeStartTime = 0; // 数学题验证开始时间
    
    // 设置管理器
    private SettingsManager settingsManager;
    
    // 设备信息上报器
    private DeviceInfoReporter deviceInfoReporter;

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
        
        Log.d(TAG, "AccessibilityService 配置完成");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Log.d(TAG, "onAccessibilityEvent 被调用，事件类型: " + event.getEventType());
        
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleWindowStateChanged(event);
        } else if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            handleWindowContentChanged(event);
        }
    }

    private void handleWindowStateChanged(AccessibilityEvent event) {
        CharSequence packageName = event.getPackageName();
        Log.d(TAG, "窗口状态变化，包名: " + packageName);
        
        if (packageName != null) {
            String currentPackage = packageName.toString();
            
            if (XIAOHONGSHU_PACKAGE.equals(currentPackage)) {
                Log.d(TAG, "检测到进入小红书应用");
                isInXiaohongshu = true;
                
                // 防抖：延迟检查内容，避免频繁更新
                if (contentCheckRunnable != null) {
                    handler.removeCallbacks(contentCheckRunnable);
                }
                contentCheckRunnable = () -> checkTextContentOptimized();
                handler.postDelayed(contentCheckRunnable, CONTENT_CHECK_DELAY);
            } else {
                Log.d(TAG, "离开小红书应用，当前包名: " + currentPackage);
                isInXiaohongshu = false;
                lastDetectedInterface = "";
                hideFloatingWindow();
            }
        }
    }
    
    private void handleWindowContentChanged(AccessibilityEvent event) {
        // 只在小红书应用内处理内容变化
        if (isInXiaohongshu) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastContentCheckTime > CONTENT_CHECK_DELAY) {
                lastContentCheckTime = currentTime;
                
                // 防抖：延迟检查内容
                if (contentCheckRunnable != null) {
                    handler.removeCallbacks(contentCheckRunnable);
                }
                contentCheckRunnable = () -> checkTextContentOptimized();
                handler.postDelayed(contentCheckRunnable, CONTENT_CHECK_DELAY);
            }
        }
    }
    
    /**
     * 优化的文本内容检查方法
     */
    private void checkTextContentOptimized() {
        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode == null) {
                Log.v(TAG, "rootNode为null，跳过检查");
                return;
            }
            
            // 使用递归查找关键文本
            boolean hasDiscoverText = findTextInNode(rootNode, "发现");
            boolean hasSearchText = findTextInNode(rootNode, "搜索");
            
            String currentInterface = "";
            if (hasDiscoverText && !hasSearchText) {
                currentInterface = "discover";
            } else if (hasSearchText) {
                currentInterface = "search";
            }
            
            // 只在界面类型真正变化时更新悬浮窗状态
            if (!currentInterface.equals(lastDetectedInterface)) {
                lastDetectedInterface = currentInterface;
                Log.d(TAG, "界面类型变化: " + currentInterface);
                
                if ("discover".equals(currentInterface) && !isManuallyHidden) {
                    // 发现页面且没有手动隐藏 - 显示悬浮窗
                    showFloatingWindow();
                } else {
                    // 搜索页面或其他页面 - 隐藏悬浮窗
                    hideFloatingWindow();
                }
            }
            
            rootNode.recycle();
        } catch (Exception e) {
            Log.e(TAG, "检查文本内容时出错", e);
        }
    }
    
    /**
     * 在节点树中递归查找指定文本
     */
    private boolean findTextInNode(AccessibilityNodeInfo node, String targetText) {
        if (node == null) return false;
        
        // 检查当前节点的文本
        CharSequence nodeText = node.getText();
        if (nodeText != null && nodeText.toString().contains(targetText)) {
            return true;
        }
        
        // 检查内容描述
        CharSequence contentDesc = node.getContentDescription();
        if (contentDesc != null && contentDesc.toString().contains(targetText)) {
            return true;
        }
        
        // 递归检查子节点
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (findTextInNode(child, targetText)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }
        
        return false;
    }

    /**
     * 创建并显示悬浮窗
     */
    private void showFloatingWindow() {
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
        
        // 设置窗口参数
        layoutParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        );
        
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = 100;
        layoutParams.y = 200;
        
        // 初始化数学题验证管理器
        if (floatingView != null) {
            mathChallengeManager = new MathChallengeManager(
                this, floatingView, windowManager, layoutParams, handler, this
            );
            
            mathChallengeManager.setOnMathChallengeListener(new MathChallengeManager.OnMathChallengeListener() {
                @Override
                public void onAnswerCorrect() {
                    Log.d(TAG, "数学题验证成功，关闭悬浮窗");
                    isManuallyHidden = true;
                    hideFloatingWindow();
                    
                    // 根据用户设置的时间间隔自动重新显示
                    if (autoShowRunnable != null) {
                        autoShowHandler.removeCallbacks(autoShowRunnable);
                    }
                    autoShowRunnable = () -> {
                        Log.d(TAG, "自动重新显示悬浮窗");
                        isManuallyHidden = false;
                        if (isInXiaohongshu && "discover".equals(lastDetectedInterface)) {
                            showFloatingWindow();
                        }
                    };
                    
                    // 使用用户设置的时间间隔
                    long interval = settingsManager.getAutoShowIntervalMillis();
                    autoShowHandler.postDelayed(autoShowRunnable, interval);
                    
                    String intervalText = SettingsManager.getIntervalDisplayText(settingsManager.getAutoShowInterval());
                    Log.d(TAG, "计划在" + intervalText + "后自动重新显示悬浮窗");
                }
                
                @Override
                public void onChallengeCancel() {
                    Log.d(TAG, "用户取消数学题验证");
                }
            });
            
            Button closeButton = floatingView.findViewById(R.id.btn_close);
            closeButton.setOnClickListener(v -> {
                Log.d(TAG, "用户点击关闭按钮");
                // 显示数学题验证界面
                mathChallengeManager.showMathChallenge();
            });

            // 设置拖拽功能
            View dragArea = floatingView.findViewById(R.id.top_info_layout);
            dragArea.setOnTouchListener(new FloatingOnTouchListener(layoutParams, windowManager));
            
            // 更新悬浮窗内容，显示当前时间间隔设置
            updateFloatingWindowContent();
            
            // 添加悬浮窗到窗口管理器
            try {
                windowManager.addView(floatingView, layoutParams);
                isFloatingWindowVisible = true;
                Log.d(TAG, "悬浮窗显示成功");
            } catch (Exception e) {
                Log.e(TAG, "显示悬浮窗失败", e);
                isFloatingWindowVisible = false;
            }
        }
    }
    
    /**
     * 更新悬浮窗内容
     */
    private void updateFloatingWindowContent() {
        if (floatingView == null || settingsManager == null) return;
        
        TextView contentText = floatingView.findViewById(R.id.tv_content);
        if (contentText != null) {
            String intervalText = SettingsManager.getIntervalDisplayText(settingsManager.getAutoShowInterval());
            String content = "小红书应用正在运行\n关闭后" + intervalText + "自动重新显示";
            contentText.setText(content);
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
        }
    }

    public static boolean isFloatingWindowVisible() {
        return instance != null && instance.isFloatingWindowVisible;
    }
    
    public static boolean isInXiaohongshu() {
        return instance != null && instance.isInXiaohongshu;
    }

    public static boolean isServiceRunning() {
        return instance != null;
    }
    
    /**
     * 检查是否正在运行自动显示定时器
     */
    public static boolean isAutoShowTimerRunning() {
        return instance != null && instance.autoShowRunnable != null && instance.isManuallyHidden;
    }
    
    /**
     * 通知设置已更新，刷新悬浮窗内容
     */
    public static void notifySettingsChanged() {
        if (instance != null && instance.isFloatingWindowVisible) {
            instance.updateFloatingWindowContent();
            Log.d(instance.TAG, "设置已更新，悬浮窗内容已刷新");
        }
    }
    
    /**
     * 通知时间间隔设置已更新，立即应用新的间隔
     */
    public static void notifyIntervalChanged() {
        if (instance != null) {
            instance.updateFloatingWindowContent();
            
            // 如果当前有正在运行的自动显示定时器，重新启动它
            if (instance.autoShowRunnable != null && instance.isManuallyHidden) {
                // 取消当前的定时器
                instance.autoShowHandler.removeCallbacks(instance.autoShowRunnable);
                
                // 使用新的时间间隔重新启动定时器
                long newInterval = instance.settingsManager.getAutoShowIntervalMillis();
                instance.autoShowHandler.postDelayed(instance.autoShowRunnable, newInterval);
                
                String intervalText = SettingsManager.getIntervalDisplayText(instance.settingsManager.getAutoShowInterval());
                Log.d(instance.TAG, "时间间隔设置已更新，立即应用新间隔: " + intervalText);
                
                // 显示提示
                android.widget.Toast.makeText(instance, 
                    "⏰ 定时器已更新，将在" + intervalText + "后重新显示", 
                    android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    /**
     * 记录数学题验证开始时间
     */
    public void onMathChallengeStart() {
        mathChallengeStartTime = System.currentTimeMillis();
        Log.d(TAG, "数学题验证开始，暂停应用状态检测");
    }
    
    /**
     * 重置数学题验证时间
     */
    public void onMathChallengeEnd() {
        mathChallengeStartTime = 0;
        Log.d(TAG, "数学题验证结束，恢复应用状态检测");
    }

    // ... 其他方法保持不变 ...
    
    /**
     * 初始化保活管理器
     */
    private void initKeepAliveManager() {
        keepAliveManager = new ServiceKeepAliveManager(this);
        keepAliveManager.setOnServiceStateListener(new ServiceKeepAliveManager.OnServiceStateListener() {
            @Override
            public void onScreenUnlocked() {
                Log.d(TAG, "屏幕解锁，检查悬浮窗状态");
                // 屏幕解锁后，重新检查小红书状态
                if (isInXiaohongshu && "discover".equals(lastDetectedInterface) && !isManuallyHidden) {
                    if (!isFloatingWindowVisible) {
                        handler.postDelayed(() -> {
                            Log.d(TAG, "屏幕解锁后恢复悬浮窗显示");
                            showFloatingWindow();
                        }, 1000);
                    }
                }
            }
            
            @Override
            public void onUserPresent() {
                Log.d(TAG, "用户解锁设备，重新检查应用状态");
                // 用户解锁后，重新检测当前是否在小红书
                handler.postDelayed(() -> {
                    if (isInXiaohongshu) {
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
                if (currentTime - lastAppStateCheckTime > APP_STATE_CHECK_INTERVAL) {
                    lastAppStateCheckTime = currentTime;
                    
                    // 检查当前前台应用状态
                    checkCurrentAppState();
                }
                
                // 继续循环检查
                appStateHandler.postDelayed(this, APP_STATE_CHECK_INTERVAL);
            }
        };
        
        // 开始定期检查
        appStateHandler.postDelayed(appStateCheckRunnable, APP_STATE_CHECK_INTERVAL);
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
                Log.v(TAG, "数学题验证界面刚显示，暂停应用状态检测");
                return;
            }
            
            // 获取当前窗口信息
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                String currentPackage = rootNode.getPackageName() != null ? 
                    rootNode.getPackageName().toString() : "";
                
                if (XIAOHONGSHU_PACKAGE.equals(currentPackage)) {
                    if (!isInXiaohongshu) {
                        Log.d(TAG, "应用状态检测：发现小红书应用");
                        isInXiaohongshu = true;
                        checkTextContentOptimized();
                    }
                } else {
                    if (isInXiaohongshu) {
                        Log.d(TAG, "应用状态检测：离开小红书应用");
                        isInXiaohongshu = false;
                        lastDetectedInterface = "";
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
        
        Log.d(TAG, "AccessibilityService 已销毁");
        Toast.makeText(this, "无障碍服务已停止", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 悬浮窗拖拽监听器
     */
    private static class FloatingOnTouchListener implements View.OnTouchListener {
        private WindowManager.LayoutParams layoutParams;
        private WindowManager windowManager;
        private int initialX, initialY;
        private float initialTouchX, initialTouchY;
        
        public FloatingOnTouchListener(WindowManager.LayoutParams layoutParams, WindowManager windowManager) {
            this.layoutParams = layoutParams;
            this.windowManager = windowManager;
        }
        
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = layoutParams.x;
                    initialY = layoutParams.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    layoutParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                    layoutParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                    windowManager.updateViewLayout(v.getParent() instanceof View ? (View) v.getParent() : v, layoutParams);
                    return true;
                    
                case MotionEvent.ACTION_UP:
                    return true;
            }
            return false;
        }
    }
} 