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
import android.util.DisplayMetrics;

import com.book.baisc.R;
import com.book.baisc.config.Const;
import com.book.baisc.config.Share;
import com.book.baisc.lifecycle.ServiceKeepAliveManager;
import com.book.baisc.config.SettingsManager;
import com.book.baisc.network.DeviceInfoReporter;
import com.book.baisc.network.FloatingTextFetcher;

/**
 * 悬浮窗无障碍服务
 * 整合了无障碍服务和悬浮窗管理功能
 */
public class FloatingAccessibilityService extends AccessibilityService 
{

    private static final String TAG = "FloatingAccessibility";
    private static FloatingAccessibilityService instance;
    private boolean isFloatingWindowVisible = false;
    private boolean isInXHS = false;
    
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
    private static final long APP_STATE_CHECK_INTERVAL = 2000; // 2秒检查一次
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
    private boolean isManuallyHidden = false;
    private Handler autoShowHandler;
    private Runnable autoShowRunnable;

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
            
            boolean newState = Const.XHS_PACKAGE.equals(packageName);
            Log.d(TAG, "是否是小红书: " + newState + " (期望包名: " + Const.XHS_PACKAGE + ")");
            
            if (newState != isInXHS) {
                isInXHS = newState;
                Log.d(TAG, "小红书应用状态发生变化，新状态: " + (isInXHS ? "前台" : "后台"));
                
                if (!isInXHS) {
                    // 离开小红书，立即隐藏悬浮窗
                    Share.lastDetectedInterface = ""; // 清除缓存
                    hideFloatingWindow();
                } else {
                    // 进入小红书，立即开始检测文本内容
                    checkTextContentOptimized();
                }
            }
        }
    }
    
    private void handleWindowContentChanged(AccessibilityEvent event) {
        // 只在小红书应用中检测文本内容
        if (isInXHS && event.getPackageName() != null &&
                Const.XHS_PACKAGE.equals(event.getPackageName().toString())) {
            
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
            handler.postDelayed(contentCheckRunnable, 300); // 300ms防抖延迟
        }
    }

    /**
     * 优化版本的文本内容检测
     * 1. 先用快速检测，如果失败则用完整检测
     * 2. 优先检查常见的文本节点类型
     * 3. 使用缓存避免重复检测
     */
    void checkTextContentOptimized() {
        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                // 第一阶段：快速检测"发现"文本（限制深度）
//                boolean hasFaxian = findTextOptimized(rootNode, "发现", 5);
                boolean hasFaxian = false;

                // 第二阶段：如果快速检测没找到"发现"，使用完整检测作为备用
                if (!hasFaxian) {
                    Log.d(TAG, "快速检测未找到'发现'，启用完整检测");
                    hasFaxian = FloatHelper.findTextInNode(rootNode, "发现");

                    // 临时调试：如果还是找不到，输出一些可见文本内容
                    if (!hasFaxian) {
                        Log.d(TAG, "完整检测也未找到'发现'，输出部分可见文本:");
                        FloatHelper.logVisibleTexts(rootNode, 0, 2); // 只输出前2层的文本，避免刷屏
                    }
                }

                // 简化界面判断逻辑：只检测"发现"
                String currentInterface = hasFaxian ? "discover" : "other";

                // 添加详细调试信息
                Log.d(TAG, "文本检测结果: 发现=" + hasFaxian + ", 当前界面=" + currentInterface);

                // 只有界面状态发生变化时才执行操作
                if (!currentInterface.equals(Share.lastDetectedInterface)) {
                    Share.lastDetectedInterface = currentInterface;

                    Log.d(TAG, "界面变化检测: " + currentInterface);

                    if ("discover".equals(currentInterface)) {
                        if (!isFloatingWindowVisible && !isManuallyHidden) {
                            showFloatingWindow();
                        }
                    } else {
                        if (isFloatingWindowVisible) {
                            hideFloatingWindow();
                        }
                    }
                } else {
                    Log.d(TAG, "界面状态无变化，跳过处理: " + currentInterface);
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
        int topOffset = 130;
        int bottomOffset = 230;

        layoutParams.x = 0;
        layoutParams.y = topOffset;
        layoutParams.width = screenWidth;
        layoutParams.height = screenHeight - topOffset - bottomOffset;
        
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
                        if (isInXHS && "discover".equals(Share.lastDetectedInterface)) {
                            showFloatingWindow();
                        }
                    };
                    
                    // 使用用户设置的时间间隔
                    long interval = settingsManager.getAutoShowIntervalMillis();
                    autoShowHandler.postDelayed(autoShowRunnable, interval);
                    
                    String intervalText = SettingsManager.getIntervalDisplayText(settingsManager.getAutoShowInterval());
                    Log.d(TAG, "计划在" + intervalText + "后自动重新显示悬浮窗");

                    // 如果是宽松模式，增加关闭次数，并自动切为严格模式
                    if (settingsManager.isCasualMode()) {
                        int currentCount = settingsManager.getCasualCloseCount();
                        settingsManager.incrementCasualCloseCount();

                        Log.d(TAG, "宽松模式关闭。之前次数: " + currentCount + ", 现在次数: " + (currentCount + 1));
                        // 通知服务时间间隔已更改
                        notifyIntervalChanged();
                    }
                    Log.d(TAG, "下次时长：" + settingsManager.getAutoShowInterval());
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

//            // 如果目标更新，本次立即请求更新缓存
//            if(Const.MOTIVATE_CHANGE){
//                fetchNew();
//                Const.MOTIVATE_CHANGE = false;
//            }
            // 更新悬浮窗内容，显示当前时间间隔设置
            updateFloatingWindowContent();
            fetchNew();

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

    private void fetchNew() {
        // 异步获取最新的动态文字内容
        if (floatingTextFetcher != null) {
            floatingTextFetcher.fetchLatestText(new FloatingTextFetcher.OnTextFetchListener() {
                @Override
                public void onTextFetched(String text) {
                    Log.d(TAG, "获取到新的动态文字: " + text);
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
            if (floatingTextFetcher != null) {
                dynamicText = floatingTextFetcher.getCachedText();
            }
            
            // 显示动态文字和时间间隔信息
            String content = dynamicText;
            if (settingsManager != null) {
                String intervalText = SettingsManager.getIntervalDisplayText(settingsManager.getAutoShowInterval());

                String hintTIme = "\n若关闭，" + intervalText + "后将重新显示本页面";
                if (!dynamicText.isEmpty()) {
                    content = dynamicText + "\n" + hintTIme;
                } else {
                    content = hintTIme;
                }
            }
            
            contentText.setText(content);
            Log.d(TAG, "悬浮窗内容已更新: " + content);
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
                if (isInXHS && "discover".equals(Share.lastDetectedInterface) && !isManuallyHidden) {
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
                    if (isInXHS) {
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
                
                if (Const.XHS_PACKAGE.equals(currentPackage)) {
                    if (!isInXHS) {
                        Log.d(TAG, "应用状态检测：发现小红书应用");
                        isInXHS = true;
                        checkTextContentOptimized();
                    }
                } else {
                    if (isInXHS) {
                        Log.d(TAG, "应用状态检测：离开小红书应用");
                        isInXHS = false;
                        Share.lastDetectedInterface = "";
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
        
        // 清理悬浮窗文字获取器
        if (floatingTextFetcher != null) {
            floatingTextFetcher.cleanup();
            floatingTextFetcher = null;
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