package com.book.baisc.ui;

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

import com.book.baisc.R;
import com.book.baisc.floating.FloatingAccessibilityService;
import com.book.baisc.lifecycle.AppLifecycleObserver;
import com.book.baisc.network.DeviceInfoReporter;
import com.book.baisc.config.SettingsManager;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_ACCESSIBILITY_PERMISSION = 1003;
    private AppLifecycleObserver appLifecycleObserver;
    private DeviceInfoReporter deviceInfoReporter;

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
        
        // 设置优化指引按钮点击事件
        setupOptimizationGuideButton();
        
        // 设置时间间隔设置按钮点击事件
        setupTimeSettingButtons();
        
        // 初始化设备信息上报器并上报设备信息
        deviceInfoReporter = new DeviceInfoReporter(this);
        deviceInfoReporter.reportDeviceInfo();
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
    
    private void setupOptimizationGuideButton() {
        Button optimizationButton = findViewById(R.id.btn_optimization_guide);
        optimizationButton.setOnClickListener(v -> showOptimizationGuide());
    }

    private void showOptimizationGuide() {
        StringBuilder guide = new StringBuilder();
        guide.append("🔋 电池优化指引\n\n");
        guide.append("为了确保悬浮窗功能正常使用，请进行以下设置：\n\n");
        
        guide.append("1️⃣ 电池优化白名单\n");
        guide.append("- 设置 → 电池 → 电池优化 → 不限制\n");
        guide.append("- 或设置 → 应用管理 → 电池优化 → 允许后台运行\n\n");
        
        guide.append("2️⃣ 自启动管理\n");
        guide.append("- 设置 → 应用管理 → 自启动管理 → 允许\n");
        guide.append("- 华为/荣耀: 手机管家 → 应用启动管理 → 手动管理\n\n");
        
        guide.append("3️⃣ 后台应用限制\n");
        guide.append("- 设置 → 应用管理 → 后台应用刷新 → 允许\n");
        guide.append("- 小米: 设置 → 省电与电池 → 应用配置 → 无限制\n\n");
        
        guide.append("4️⃣ 通知权限\n");
        guide.append("- 设置 → 通知管理 → 允许通知\n\n");
        
        guide.append("5️⃣ 锁屏清理\n");
        guide.append("- 设置 → 锁屏 → 锁屏清理 → 关闭\n\n");
        
        guide.append("⚠️ 注意：不同品牌手机设置路径可能不同\n");
        guide.append("如果仍有问题，请重启手机后再试");
        
        // 显示指引
        new android.app.AlertDialog.Builder(this)
               .setTitle("电池优化指引")
               .setMessage(guide.toString())
               .setPositiveButton("去电池设置", (dialog, which) -> {
                   try {
                       Intent intent = new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                       startActivity(intent);
                   } catch (Exception e) {
                       try {
                           Intent intent = new Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS);
                           startActivity(intent);
                       } catch (Exception ex) {
                           Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
                           startActivity(intent);
                       }
                   }
               })
               .setNegativeButton("稍后处理", null)
               .show();
    }

    private void setupTimeSettingButtons() {
        Button dailyButton = findViewById(R.id.btn_daily_time_setting);
        dailyButton.setOnClickListener(v -> {
            showTimeSettingDialog(true); // true for daily
        });
        
        Button casualButton = findViewById(R.id.btn_casual_time_setting);
        casualButton.setOnClickListener(v -> {
            showTimeSettingDialog(false); // false for casual
        });
    }

    private void showTimeSettingDialog(boolean isDaily) {
        final SettingsManager settingsManager = new SettingsManager(this);
        final int[] intervals = isDaily ? 
            SettingsManager.getDailyAvailableIntervals() : 
            SettingsManager.getCasualAvailableIntervals();
        
        String[] intervalOptions = new String[intervals.length];
        for (int i = 0; i < intervals.length; i++) {
            intervalOptions[i] = SettingsManager.getIntervalDisplayText(intervals[i]);
        }

        int currentInterval = settingsManager.getAutoShowInterval();
        int checkedItem = -1;
        for (int i = 0; i < intervals.length; i++) {
            if (intervals[i] == currentInterval) {
                checkedItem = i;
                break;
            }
        }
        
        String dialogTitle = isDaily ? "日常版时间间隔" : "休闲版时间间隔";

        new android.app.AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setSingleChoiceItems(intervalOptions, checkedItem, (dialog, which) -> {
                int selectedInterval = intervals[which];
                settingsManager.setAutoShowInterval(selectedInterval);
                
                // 通知服务配置已更改
                FloatingAccessibilityService.notifyIntervalChanged();
                
                // 显示提示信息
                showIntervalExplanation(selectedInterval);
                
                Toast.makeText(this, "已设置为: " + intervalOptions[which], Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showIntervalExplanation(int interval) {
        StringBuilder explanation = new StringBuilder();
        explanation.append("⏰ 时间间隔设置说明\n\n");
        explanation.append("当前设置: ").append(SettingsManager.getIntervalDisplayText(interval)).append("\n\n");
        explanation.append("📌 说明:\n");
        explanation.append("• 关闭悬浮窗后，等待设定时间再自动显示\n");
        explanation.append("• 较短间隔：更频繁提醒，防沉迷效果更强\n");
        explanation.append("• 较长间隔：减少打扰，适合偶尔使用\n\n");
        explanation.append("💡 建议:\n");
        explanation.append("• 强制防沉迷：3-5秒\n");
        explanation.append("• 平衡使用：10-15秒\n");
        explanation.append("• 轻度提醒：30-60秒\n\n");
        explanation.append("⚠️ 注意：设置立即生效，正在运行的定时器会立即更新");
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("设置完成")
               .setMessage(explanation.toString())
               .setPositiveButton("知道了", null)
               .show();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 释放设备信息上报器资源
        if (deviceInfoReporter != null) {
            deviceInfoReporter.release();
            deviceInfoReporter = null;
        }
        
        // 释放应用生命周期监听器
        if (appLifecycleObserver != null) {
            appLifecycleObserver = null;
        }
    }
} 