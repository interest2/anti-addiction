package com.book.baisc;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.util.DisplayMetrics;
import android.view.MotionEvent;

public class FloatingAccessibilityService extends AccessibilityService {

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
        if (event.getPackageName() != null) {
            String packageName = event.getPackageName().toString();
            Log.d(TAG, "窗口状态改变，当前应用: " + packageName);
            
            // 过滤掉我们自己的应用，避免悬浮窗显示时触发状态变化
            if (packageName.equals(getPackageName())) {
                Log.d(TAG, "忽略自己的应用: " + packageName);
                return;
            }
            
            boolean newState = XIAOHONGSHU_PACKAGE.equals(packageName);
            Log.d(TAG, "是否是小红书: " + newState + " (期望包名: " + XIAOHONGSHU_PACKAGE + ")");
            
            if (newState != isInXiaohongshu) {
                isInXiaohongshu = newState;
                Log.d(TAG, "小红书应用状态发生变化，新状态: " + (isInXiaohongshu ? "前台" : "后台"));
                
                if (!isInXiaohongshu) {
                    // 离开小红书，立即隐藏悬浮窗
                    lastDetectedInterface = ""; // 清除缓存
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
        if (isInXiaohongshu && event.getPackageName() != null && 
            XIAOHONGSHU_PACKAGE.equals(event.getPackageName().toString())) {
            
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
            handler.postDelayed(contentCheckRunnable, CONTENT_CHECK_DELAY);
        }
    }

    private boolean findTextInNode(AccessibilityNodeInfo node, String targetText) {
        if (node == null) return false;
        
        // 检查当前节点的文本
        CharSequence text = node.getText();
        if (text != null && text.toString().contains(targetText)) {
            // 检查节点是否可见
            if (node.isVisibleToUser()) {
                Log.d(TAG, "找到目标文本: " + targetText + " (可见)");
                return true;
            } else {
                Log.d(TAG, "找到目标文本: " + targetText + " (不可见，忽略)");
            }
        }
        
        // 检查contentDescription
        CharSequence contentDesc = node.getContentDescription();
        if (contentDesc != null && contentDesc.toString().contains(targetText)) {
            // 检查节点是否可见
            if (node.isVisibleToUser()) {
                Log.d(TAG, "在contentDescription中找到目标文本: " + targetText + " (可见)");
                return true;
            } else {
                Log.d(TAG, "在contentDescription中找到目标文本: " + targetText + " (不可见，忽略)");
            }
        }
        
        // 递归检查子节点
        for (int i = 0; i < node.getChildCount(); i++) {
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
     * 优化版本的文本内容检测
     * 1. 先用快速检测，如果失败则用完整检测
     * 2. 优先检查常见的文本节点类型
     * 3. 使用缓存避免重复检测
     */
    private void checkTextContentOptimized() {
        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                // 第一阶段：快速检测关键文本（限制深度）
                boolean hasFaxian = findTextOptimized(rootNode, "发现", 5); // 增加到5层
                boolean isSearchInterface = findTextOptimized(rootNode, "搜索框", 4) || 
                                          findTextOptimized(rootNode, "搜索结果", 4) ||
                                          findTextOptimized(rootNode, "搜索历史", 3) ||
                                          findTextOptimized(rootNode, "热门搜索", 3);
                
                // 第二阶段：如果快速检测没找到"发现"，使用完整检测作为备用
                if (!hasFaxian) {
                    Log.d(TAG, "快速检测未找到'发现'，启用完整检测");
                    hasFaxian = findTextInNode(rootNode, "发现");
                    
                    // 临时调试：如果还是找不到，输出一些可见文本内容
                    if (!hasFaxian) {
                        Log.d(TAG, "完整检测也未找到'发现'，输出部分可见文本:");
                        logVisibleTexts(rootNode, 0, 2); // 只输出前2层的文本，避免刷屏
                    }
                }
                
                // 缓存检测结果，避免重复处理
                String currentInterface = "";
                if (hasFaxian && !isSearchInterface) {
                    currentInterface = "discover";
                } else if (isSearchInterface) {
                    currentInterface = "search";
                } else {
                    currentInterface = "other";
                }
                
                // 添加详细调试信息
                Log.d(TAG, "文本检测详情: 发现=" + hasFaxian + ", 搜索界面=" + isSearchInterface + ", 当前界面=" + currentInterface);
                
                // 只有界面状态发生变化时才执行操作
                if (!currentInterface.equals(lastDetectedInterface)) {
                    lastDetectedInterface = currentInterface;
                    
                    Log.d(TAG, "界面变化检测: " + lastDetectedInterface + " → " + currentInterface);
                    
                    if ("discover".equals(currentInterface)) {
                        if (!isFloatingWindowVisible) {
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
     * 优化版本的文本查找，限制递归深度
     */
    private boolean findTextOptimized(AccessibilityNodeInfo node, String targetText, int maxDepth) {
        if (node == null || maxDepth <= 0) return false;
        
        // 检查当前节点的文本
        CharSequence text = node.getText();
        if (text != null && node.isVisibleToUser() && text.toString().contains(targetText)) {
            Log.d(TAG, "快速找到目标文本: " + targetText + " (深度: " + (6-maxDepth) + ")");
            return true;
        }
        
        // 检查contentDescription
        CharSequence contentDesc = node.getContentDescription();
        if (contentDesc != null && node.isVisibleToUser() && contentDesc.toString().contains(targetText)) {
            Log.d(TAG, "在contentDescription中快速找到: " + targetText + " (深度: " + (6-maxDepth) + ")");
            return true;
        }
        
        // 增加子节点检查数量，确保不遗漏
        int childCount = Math.min(node.getChildCount(), 20); // 增加到20个子节点
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (findTextOptimized(child, targetText, maxDepth - 1)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }
        
        return false;
    }
    
    /**
     * 临时调试方法：输出可见文本内容
     */
    private void logVisibleTexts(AccessibilityNodeInfo node, int currentDepth, int maxDepth) {
        if (node == null || currentDepth > maxDepth) return;
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < currentDepth; i++) {
            sb.append("  ");
        }
        String indent = sb.toString();
        
        // 输出当前节点的文本
        CharSequence text = node.getText();
        if (text != null && !text.toString().trim().isEmpty() && node.isVisibleToUser()) {
            Log.d(TAG, indent + "文本: " + text.toString().trim());
        }
        
        // 输出contentDescription
        CharSequence contentDesc = node.getContentDescription();
        if (contentDesc != null && !contentDesc.toString().trim().isEmpty() && node.isVisibleToUser()) {
            Log.d(TAG, indent + "描述: " + contentDesc.toString().trim());
        }
        
        // 递归检查子节点（限制数量）
        int childCount = Math.min(node.getChildCount(), 15);
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                logVisibleTexts(child, currentDepth + 1, maxDepth);
                child.recycle();
            }
        }
    }

    private void showFloatingWindow() {
        if (!isFloatingWindowVisible && !isManuallyHidden) {
            Log.d(TAG, "开始显示悬浮窗");
            
            // 如果悬浮窗已存在，先移除
            if (floatingView != null) {
                try {
                    windowManager.removeView(floatingView);
                } catch (Exception e) {
                    Log.e(TAG, "移除旧悬浮窗时出错", e);
                }
            }
            
            // 创建悬浮窗布局
            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window_layout, null);
            
            // 设置悬浮窗参数
            layoutParams = new WindowManager.LayoutParams();
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            layoutParams.format = PixelFormat.TRANSLUCENT;
            layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
            
            // 计算悬浮窗位置和大小
            DisplayMetrics displayMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
            
            int screenWidth = displayMetrics.widthPixels;
            int screenHeight = displayMetrics.heightPixels;
            
            // 获取状态栏高度
            int statusBarHeight = 0;
            int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                statusBarHeight = getResources().getDimensionPixelSize(resourceId);
            }
            
            // 获取导航栏高度
            int navigationBarHeight = 0;
            resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (resourceId > 0) {
                navigationBarHeight = getResources().getDimensionPixelSize(resourceId);
            }
            
            // 设置悬浮窗位置和大小
//            int topOffset = statusBarHeight + (int) (130 * getResources().getDisplayMetrics().density); // 用户调整的130dp
//            int bottomOffset = (int) (230 * getResources().getDisplayMetrics().density); // 用户调整的230dp

            int topOffset = 130;
            int bottomOffset = 230;

            layoutParams.x = 0;
            layoutParams.y = topOffset;
            layoutParams.width = screenWidth;
            layoutParams.height = screenHeight - topOffset - bottomOffset;
            
            Log.d(TAG, "悬浮窗参数: 宽度=" + layoutParams.width + ", 高度=" + layoutParams.height + ", X=" + layoutParams.x + ", Y=" + layoutParams.y);
            
            // 设置界面元素
            TextView statusText = floatingView.findViewById(R.id.tv_content);
            statusText.setText("✅ 检测到\"发现\"页面");
            
            Button closeButton = floatingView.findViewById(R.id.btn_close);
            closeButton.setOnClickListener(v -> {
                Log.d(TAG, "用户点击关闭按钮");
                isManuallyHidden = true;
                hideFloatingWindow();
                
                // 5秒后自动重新显示
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
                autoShowHandler.postDelayed(autoShowRunnable, 5000);
            });
            
            closeButton.setOnLongClickListener(v -> {
                Log.d(TAG, "用户长按关闭按钮");
                isManuallyHidden = true;
                hideFloatingWindow();
                
                // 取消自动显示
                if (autoShowRunnable != null) {
                    autoShowHandler.removeCallbacks(autoShowRunnable);
                }
                return true;
            });
            
            // 设置拖拽功能
            View dragArea = floatingView.findViewById(R.id.top_info_layout);
            dragArea.setOnTouchListener(new FloatingOnTouchListener(layoutParams, windowManager));
            
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
    
    private void hideFloatingWindow() {
        if (isFloatingWindowVisible) {
            Log.d(TAG, "开始隐藏悬浮窗");
            
            try {
                if (floatingView != null && windowManager != null) {
                    windowManager.removeView(floatingView);
                    floatingView = null;
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
        
        // 清理悬浮窗
        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
                floatingView = null;
            } catch (Exception e) {
                Log.e(TAG, "清理悬浮窗时出错", e);
            }
        }
        
        Log.d(TAG, "AccessibilityService 已销毁");
        Toast.makeText(this, "无障碍服务已停止", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 悬浮窗拖拽监听器
     */
    private static class FloatingOnTouchListener implements View.OnTouchListener {
        private final WindowManager.LayoutParams layoutParams;
        private final WindowManager windowManager;
        private int initialX, initialY;
        private float initialTouchX, initialTouchY;
        
        public FloatingOnTouchListener(WindowManager.LayoutParams params, WindowManager manager) {
            this.layoutParams = params;
            this.windowManager = manager;
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
                    windowManager.updateViewLayout(v.getRootView(), layoutParams);
                    return true;
                    
                case MotionEvent.ACTION_UP:
                    return true;
            }
            return false;
        }
    }
} 