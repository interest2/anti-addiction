package com.book.baisc;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.content.Context;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityManager;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_ACCESSIBILITY_PERMISSION = 1003;
    private AppLifecycleObserver appLifecycleObserver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 检查并请求所有必要权限
        checkAndRequestPermissions();
        
        // 设置测试按钮点击事件
        setupTestButton();
        
        // 设置显示当前应用按钮点击事件
        setupShowCurrentAppButton();
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // 没有悬浮窗权限，引导用户去设置
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
                Toast.makeText(this, "请开启悬浮窗权限以使用此功能", Toast.LENGTH_LONG).show();
            } else {
                // 有悬浮窗权限，检查无障碍服务权限
                checkAccessibilityPermission();
            }
        } else {
            // Android 6.0以下默认有悬浮窗权限
            checkAccessibilityPermission();
        }
    }

    private void checkAccessibilityPermission() {
        if (!isAccessibilityServiceEnabled()) {
            // 没有无障碍服务权限，引导用户去设置
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivityForResult(intent, REQUEST_ACCESSIBILITY_PERMISSION);
            Toast.makeText(this, "请开启无障碍服务以检测小红书APP", Toast.LENGTH_LONG).show();
        } else {
            // 已有所有权限，初始化应用生命周期监听器
            initAppLifecycleObserver();
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        // 方法1：直接检查AccessibilityService是否运行
        if (FloatingAccessibilityService.isServiceRunning()) {
            android.util.Log.d("MainActivity", "通过静态方法检查：AccessibilityService 正在运行");
            return true;
        }
        
        // 方法2：通过AccessibilityManager检查
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am != null) {
            List<AccessibilityServiceInfo> runningServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            
            String targetServiceName = getPackageName() + "/" + FloatingAccessibilityService.class.getName();
            android.util.Log.d("MainActivity", "期望的服务名称: " + targetServiceName);
            android.util.Log.d("MainActivity", "已启用的无障碍服务数量: " + runningServices.size());
            
            for (AccessibilityServiceInfo service : runningServices) {
                String serviceId = service.getId();
                android.util.Log.d("MainActivity", "发现的服务ID: " + serviceId);
                
                if (serviceId.contains(getPackageName()) && serviceId.contains("FloatingAccessibilityService")) {
                    android.util.Log.d("MainActivity", "找到匹配的无障碍服务!");
                    return true;
                }
            }
            android.util.Log.d("MainActivity", "未找到匹配的无障碍服务");
        }
        
        // 方法3：通过Settings.Secure检查
        try {
            String enabledServices = Settings.Secure.getString(
                getContentResolver(), 
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            android.util.Log.d("MainActivity", "系统启用的无障碍服务: " + enabledServices);
            
            if (enabledServices != null) {
                String ourService = getPackageName() + "/" + FloatingAccessibilityService.class.getName();
                boolean found = enabledServices.contains(ourService);
                android.util.Log.d("MainActivity", "在系统设置中查找 " + ourService + ": " + found);
                return found;
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "检查系统设置失败", e);
        }
        
        return false;
    }

    private void initAppLifecycleObserver() {
        appLifecycleObserver = new AppLifecycleObserver(this);
        Toast.makeText(this, "检测功能已启用，打开小红书时会显示悬浮窗", Toast.LENGTH_LONG).show();
    }
    
    private void setupTestButton() {
        Button testButton = findViewById(R.id.btn_test);
        testButton.setOnClickListener(v -> {
            // 显示当前权限状态
            boolean hasOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
            boolean hasAccessibility = isAccessibilityServiceEnabled();
            
            String status = String.format("权限状态:\n悬浮窗: %s\n无障碍: %s", 
                hasOverlay ? "✓" : "✗", 
                hasAccessibility ? "✓" : "✗");
            
            Toast.makeText(this, status, Toast.LENGTH_LONG).show();
            android.util.Log.d("MainActivity", status);
            
            if (hasOverlay && hasAccessibility) {
                // 无障碍服务已启动，悬浮窗功能已可用
                Toast.makeText(this, "✅ 服务已启动，打开小红书时会显示悬浮窗", Toast.LENGTH_SHORT).show();
            } else {
                String missing = "";
                if (!hasOverlay) missing += "悬浮窗权限 ";
                if (!hasAccessibility) missing += "无障碍服务 ";
                Toast.makeText(this, "请先开启：" + missing, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void setupShowCurrentAppButton() {
        Button showAppButton = findViewById(R.id.btn_show_current_app);
        showAppButton.setOnClickListener(v -> {
            // 检查无障碍服务的状态
            boolean isAccessibilityRunning = FloatingAccessibilityService.isServiceRunning();
            
            String serviceStatus = String.format("服务状态:\n无障碍服务: %s", 
                isAccessibilityRunning ? "✓" : "✗");
            
            Toast.makeText(this, serviceStatus, Toast.LENGTH_LONG).show();
            android.util.Log.d("MainActivity", serviceStatus);
            
            // 如果AccessibilityService正在运行，显示当前状态
            if (isAccessibilityRunning) {
                boolean isInXhs = FloatingAccessibilityService.isInXiaohongshu();
                boolean isFloatingVisible = FloatingAccessibilityService.isFloatingWindowVisible();
                String message = String.format("小红书状态: %s\n悬浮窗状态: %s", 
                    isInXhs ? "前台运行" : "未运行",
                    isFloatingVisible ? "显示中" : "隐藏中");
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                android.util.Log.d("MainActivity", message);
            }
            
            // 显示检测逻辑信息（已优化：取消广播通信）
            String detectionInfo = "检测逻辑 (已优化响应速度):\n小红书包名: com.xingin.xhs\n显示条件: 检测到\"发现\"文本\n隐藏条件: 检测到\"搜索\"文本或离开小红书\n手动关闭: 5秒后自动重新显示\n性能优化: 防抖+缓存+限制递归深度\n⚡ 新优化: 取消广播通信，直接管理悬浮窗";
            Toast.makeText(this, detectionInfo, Toast.LENGTH_LONG).show();
            android.util.Log.d("MainActivity", detectionInfo);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    // 获得悬浮窗权限，继续检查无障碍服务权限
                    checkAccessibilityPermission();
                } else {
                    Toast.makeText(this, "没有悬浮窗权限，功能无法使用", Toast.LENGTH_LONG).show();
                }
            }
        } else if (requestCode == REQUEST_ACCESSIBILITY_PERMISSION) {
            if (isAccessibilityServiceEnabled()) {
                // 获得无障碍服务权限，初始化应用生命周期监听器
                initAppLifecycleObserver();
            } else {
                Toast.makeText(this, "没有无障碍服务权限，无法检测小红书APP", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次返回时检查权限状态
        if (isAccessibilityServiceEnabled() && 
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this))) {
            // 权限已开启，确保显示正确的状态
            if (appLifecycleObserver == null) {
                initAppLifecycleObserver();
            }
        }
    }
}