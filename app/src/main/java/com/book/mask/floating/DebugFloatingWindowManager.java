package com.book.mask.floating;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.book.mask.R;
import com.book.mask.config.Share;
import com.book.mask.config.CustomApp;
import com.book.mask.config.SettingsManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 调试悬浮窗管理器
 * 用于在页面底部显示调试信息，高度150dp
 */
public class DebugFloatingWindowManager {
    
    private static final String TAG = "DebugFloatingWindow";
    private static DebugFloatingWindowManager instance;
    
    private Context context;
    private WindowManager windowManager;
    private View debugFloatingView;
    private WindowManager.LayoutParams layoutParams;
    private boolean isDebugWindowVisible = false;
    
    private Handler handler;
    private Runnable updateRunnable;
    private static final long UPDATE_INTERVAL = 2000; // 2秒更新一次
    
    private SettingsManager settingsManager;
    
    // 时间格式化器
    private static final SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    
    private DebugFloatingWindowManager(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.handler = new Handler(Looper.getMainLooper());
        this.settingsManager = new SettingsManager(context);
    }
    
    public static DebugFloatingWindowManager getInstance(Context context) {
        if (instance == null) {
            instance = new DebugFloatingWindowManager(context);
        }
        return instance;
    }
    
    /**
     * 显示调试悬浮窗
     */
    public void showDebugWindow() {
        if (isDebugWindowVisible) {
            Log.d(TAG, "调试悬浮窗已显示，跳过重复显示");
            return;
        }
        
        Log.d(TAG, "开始显示调试悬浮窗");
        
        // 移除现有悬浮窗（如果存在）
        if (debugFloatingView != null) {
            try {
                windowManager.removeView(debugFloatingView);
            } catch (Exception e) {
                Log.w(TAG, "移除旧调试悬浮窗时出错", e);
            }
            debugFloatingView = null;
        }
        
        // 创建调试悬浮窗布局
        LayoutInflater inflater = LayoutInflater.from(context);
        debugFloatingView = inflater.inflate(R.layout.debug_floating_window_layout, null);
        layoutParams = getDebugLayoutParams();
        
        // 设置按钮点击事件
        setupButtonListeners();
        
        // 添加悬浮窗到窗口管理器
        try {
            windowManager.addView(debugFloatingView, layoutParams);
            isDebugWindowVisible = true;
            Log.d(TAG, "调试悬浮窗显示成功");
            
            // 开始定期更新调试信息
            startPeriodicUpdate();
            
        } catch (Exception e) {
            Log.e(TAG, "显示调试悬浮窗失败", e);
            isDebugWindowVisible = false;
        }
    }
    
    /**
     * 隐藏调试悬浮窗
     */
    public void hideDebugWindow() {
        if (!isDebugWindowVisible) {
            Log.d(TAG, "调试悬浮窗未显示，跳过隐藏");
            return;
        }
        
        Log.d(TAG, "开始隐藏调试悬浮窗");
        
        // 停止定期更新
        stopPeriodicUpdate();
        
        try {
            if (debugFloatingView != null && windowManager != null) {
                windowManager.removeView(debugFloatingView);
                debugFloatingView = null;
                Log.d(TAG, "调试悬浮窗隐藏成功");
            }
        } catch (Exception e) {
            Log.e(TAG, "隐藏调试悬浮窗失败", e);
        }
        
        isDebugWindowVisible = false;
    }
    
    /**
     * 获取调试悬浮窗的布局参数
     */
    private WindowManager.LayoutParams getDebugLayoutParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        // 设置更高的层级，确保置顶显示
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        params.format = PixelFormat.TRANSLUCENT;
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        
        // 计算屏幕尺寸
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;
        
        // 设置悬浮窗位置和大小
        params.x = 0;
        params.y = 0; // 底部对齐
        params.width = screenWidth;
        params.height = screenHeight / 2; // 屏幕高度的1/3
        
        return params;
    }
    
    /**
     * 设置按钮点击事件
     */
    private void setupButtonListeners() {
        if (debugFloatingView == null) return;
        
        Button refreshButton = debugFloatingView.findViewById(R.id.btn_refresh_debug);
        Button hideButton = debugFloatingView.findViewById(R.id.btn_hide_debug);
        
        refreshButton.setOnClickListener(v -> {
            Log.d(TAG, "用户点击刷新调试信息");
            updateDebugContent();
            Toast.makeText(context, "调试信息已刷新", Toast.LENGTH_SHORT).show();
        });
        
        hideButton.setOnClickListener(v -> {
            Log.d(TAG, "用户点击隐藏调试悬浮窗");
            hideDebugWindow();
            Toast.makeText(context, "调试悬浮窗已隐藏", Toast.LENGTH_SHORT).show();
        });
    }
    
    /**
     * 开始定期更新调试信息
     */
    private void startPeriodicUpdate() {
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
        
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateDebugContent();
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
        
        handler.post(updateRunnable);
        Log.d(TAG, "开始定期更新调试信息，间隔: " + UPDATE_INTERVAL + "ms");
    }
    
    /**
     * 停止定期更新
     */
    private void stopPeriodicUpdate() {
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
            updateRunnable = null;
        }
        Log.d(TAG, "停止定期更新调试信息");
    }
    
    /**
     * 更新调试内容
     */
    private void updateDebugContent() {
        if (debugFloatingView == null) return;
        
        TextView debugContent = debugFloatingView.findViewById(R.id.tv_debug_content);
        if (debugContent == null) return;
        
        StringBuilder debugInfo = new StringBuilder();
        
        // 当前时间
        String currentTime = timeFormatter.format(new Date());
        debugInfo.append("⏰ 当前时间: ").append(currentTime).append("\n");
        
        // 无障碍事件类型
        String eventTypeText = getEventTypeText(Share.lastEventType);
        debugInfo.append("📡 事件类型: ").append(eventTypeText).append("\n");
        
        // 事件触发时间
        if (Share.lastEventTime > 0) {
            String eventTimeText = timeFormatter.format(new Date(Share.lastEventTime));
            debugInfo.append("⏱️ 事件时间: ").append(eventTimeText).append("\n");
        } else {
            debugInfo.append("⏱️ 事件时间: 无\n");
        }
        
        // forceCheck条件触发时间
        if (Share.findTextInNodeTime > 0) {
            String findText = timeFormatter.format(new Date(Share.findTextInNodeTime));
            debugInfo.append("🔍 findText触发: ").append(findText).append("\n");
        } else {
            debugInfo.append("🔍 findText触发: 无\n");
        }
        
        // checkTextContentOptimized 方法调试变量
        debugInfo.append("  当前界面: ").append(Share.currentInterface).append("\n");
        debugInfo.append("  强制检查: ").append(Share.forceCheck ? "是" : "否").append("\n");
        
        // 调试时间戳变量
        debugInfo.append("🔧 调试时间戳:\n");
        if (Share.h0 > 0) {
            String h0TimeText = timeFormatter.format(new Date(Share.h0));
            debugInfo.append("  h0: ").append(h0TimeText).append("\n");
        }
        if (Share.h1 > 0) {
            String h1TimeText = timeFormatter.format(new Date(Share.h1));
            debugInfo.append("  h1: ").append(h1TimeText).append("\n");
        }
        if (Share.h7 > 0) {
            String h7TimeText = timeFormatter.format(new Date(Share.h7));
            debugInfo.append("  h7: ").append(h7TimeText).append("\n");
        }
        if (Share.h8 > 0) {
            String h8TimeText = timeFormatter.format(new Date(Share.h8));
            debugInfo.append("  h8: ").append(h8TimeText).append("\n");
        }
        debugInfo.append("\n");
        
        // 当前活跃APP信息
        if (Share.currentApp != null) {
            CustomApp currentApp = Share.currentApp;
            debugInfo.append("📱 当前APP: ").append(currentApp.getAppName()).append("\n");
            debugInfo.append("📦 包名: ").append(currentApp.getPackageName()).append("\n");
            debugInfo.append("📦 类名: ").append(Share.className).append("\n");

            // APP状态
            String appState = Share.getAppState(currentApp);
            debugInfo.append("📊 APP状态: ").append(appState != null ? appState : "未知").append("\n");

            // 隐藏时间戳
            Long hiddenTime = Share.getHiddenTimestamp(currentApp.getPackageName());
            if (hiddenTime != null) {
                String hiddenTimeStr = timeFormatter.format(new Date(hiddenTime));
                debugInfo.append("⏱️ 隐藏时间: ").append(hiddenTimeStr).append("\n");
            }
            
            debugInfo.append("\n");
        } else {
            debugInfo.append("📱 当前APP: 无\n\n");
        }
        
        debugContent.setText(debugInfo.toString());
    }
    
    /**
     * 格式化内存大小
     */
    private String formatMemorySize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
    
    /**
     * 获取事件类型的可读文本
     */
    private String getEventTypeText(int eventType) {
        switch (eventType) {
            case android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED:
                return "VIEW_CLICKED (1)";
            case android.view.accessibility.AccessibilityEvent.TYPE_VIEW_LONG_CLICKED:
                return "VIEW_LONG_CLICKED (2)";
            case android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED:
                return "VIEW_SELECTED (4)";
            case android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED:
                return "VIEW_FOCUSED (8)";
            case android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
                return "VIEW_TEXT_CHANGED (16)";
            case android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                return "应用切换 (32)";
            case android.view.accessibility.AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                return "NOTIFICATION_STATE_CHANGED (64)";
            case android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED:
                return "VIEW_SCROLLED (128)";
            case android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED:
                return "VIEW_TEXT_SELECTION_CHANGED (256)";
            case android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                return "界面内容变化 (2048)";
            case android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
                return "VIEW_ACCESSIBILITY_FOCUSED (32768)";
            case android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED:
                return "VIEW_ACCESSIBILITY_FOCUS_CLEARED (65536)";
            case android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY:
                return "VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY (131072)";
            case android.view.accessibility.AccessibilityEvent.TYPE_GESTURE_DETECTION_START:
                return "GESTURE_DETECTION_START (262144)";
            case android.view.accessibility.AccessibilityEvent.TYPE_GESTURE_DETECTION_END:
                return "GESTURE_DETECTION_END (524288)";
            case android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_INTERACTION_START:
                return "TOUCH_INTERACTION_START (1048576)";
            case android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_INTERACTION_END:
                return "TOUCH_INTERACTION_END (2097152)";
            case android.view.accessibility.AccessibilityEvent.TYPE_WINDOWS_CHANGED:
                return "WINDOWS_CHANGED (4194304)";
            case android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED:
                return "VIEW_CONTEXT_CLICKED (8388608)";
            case android.view.accessibility.AccessibilityEvent.TYPE_ANNOUNCEMENT:
                return "ANNOUNCEMENT (16384)";
            case android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START:
                return "TOUCH_EXPLORATION_GESTURE_START (33554432)";
            case android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END:
                return "TOUCH_EXPLORATION_GESTURE_END (67108864)";
            case android.view.accessibility.AccessibilityEvent.TYPE_VIEW_HOVER_ENTER:
                return "VIEW_HOVER_ENTER (134217728)";
            case android.view.accessibility.AccessibilityEvent.TYPE_VIEW_HOVER_EXIT:
                return "VIEW_HOVER_EXIT (268435456)";
            default:
                return "UNKNOWN (" + eventType + ")";
        }
    }
    
    /**
     * 检查调试悬浮窗是否可见
     */
    public boolean isDebugWindowVisible() {
        return isDebugWindowVisible;
    }
    
    /**
     * 切换调试悬浮窗显示状态
     */
    public void toggleDebugWindow() {
        if (isDebugWindowVisible) {
            hideDebugWindow();
        } else {
            showDebugWindow();
        }
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        hideDebugWindow();
        if (handler != null && updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
        instance = null;
        Log.d(TAG, "调试悬浮窗管理器已清理");
    }
} 