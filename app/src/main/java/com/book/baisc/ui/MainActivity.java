package com.book.baisc.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.TextView;

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
import com.book.baisc.config.Const;
import com.book.baisc.floating.FloatingAccessibilityService;
import com.book.baisc.lifecycle.AppLifecycleObserver;
import com.book.baisc.network.DeviceInfoReporter;
import com.book.baisc.config.SettingsManager;
import com.book.baisc.network.FloatingTextFetcher;
import com.book.baisc.ui.SettingsDialogManager;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_ACCESSIBILITY_PERMISSION = 1003;
    private AppLifecycleObserver appLifecycleObserver;
    private DeviceInfoReporter deviceInfoReporter;
    private SettingsManager settingsManager;
    private SettingsDialogManager settingsDialogManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            
            // 将 dp 值转换为像素
            int horizontalPadding = (int) (32 * getResources().getDisplayMetrics().density);
            
            v.setPadding(
                systemBars.left + horizontalPadding, 
                systemBars.top, 
                systemBars.right + horizontalPadding, 
                systemBars.bottom
            );
            return insets;
        });

        settingsManager = new SettingsManager(this);
        settingsDialogManager = new SettingsDialogManager(this, settingsManager);

        // 检查并请求所有必要权限
        checkAndRequestPermissions();
        
        // 设置优化指引按钮点击事件
//        setupOptimizationGuideButton();
        
        // 设置时间间隔设置按钮点击事件
        setupTimeSettingButtons();
        
        // 设置激励语标签按钮
        setupTagSettingButton();
        
        // 设置目标完成日期按钮
        setupTargetDateSettingButton();
        
        // 设置最新安装包地址按钮
        setupLatestApkButton();
        
        // 初始化设备信息上报器并上报设备信息
        deviceInfoReporter = new DeviceInfoReporter(this);
        deviceInfoReporter.reportDeviceInfo();

        // 检查缓存并获取云端内容
        checkAndFetchCachedContent();

        updateCasualButtonState();
        updateCasualCountDisplay();
    }

    private void checkAndFetchCachedContent() {
        // 创建 FloatingTextFetcher 实例
        FloatingTextFetcher textFetcher = new FloatingTextFetcher(this);
        
        // 检查是否有缓存内容
        String cachedText = textFetcher.getCachedText();
        if (cachedText == null || cachedText.isEmpty()) {
            // 没有缓存内容，立即获取
            textFetcher.fetchLatestText(new FloatingTextFetcher.OnTextFetchListener() {
                @Override
                public void onTextFetched(String text) {
                    android.util.Log.d("MainActivity", "应用启动时获取到云端内容: " + text);
                }

                @Override
                public void onFetchError(String error) {
                    android.util.Log.w("MainActivity", "应用启动时获取云端内容失败: " + error);
                }
            });
        } else {
            android.util.Log.d("MainActivity", "应用启动时发现缓存内容: " + cachedText);
        }
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

        private void setupTimeSettingButtons() {
        Button dailyButton = findViewById(R.id.btn_daily_time_setting);
        dailyButton.setOnClickListener(v -> {
            settingsDialogManager.showTimeSettingDialog(true); // true for daily
        });
        
        Button casualButton = findViewById(R.id.btn_casual_time_setting);
        casualButton.setOnClickListener(v -> {
            settingsDialogManager.showTimeSettingDialog(false); // false for casual
        });
    }

    
    
    private void updateCasualButtonState() {
        Button casualButton = findViewById(R.id.btn_casual_time_setting);
        settingsDialogManager.updateCasualButtonState(casualButton);
    }

    private void updateCasualCountDisplay() {
        TextView countText = findViewById(R.id.tv_casual_count);
        settingsDialogManager.updateCasualCountDisplay(countText);
    }

    private void setupTagSettingButton() {
        Button tagButton = findViewById(R.id.btn_tag_setting);
        tagButton.setOnClickListener(v -> settingsDialogManager.showTagSettingDialog());
        updateTagButtonText();
    }
    
    private void updateTagButtonText() {
        Button tagButton = findViewById(R.id.btn_tag_setting);
        settingsDialogManager.updateTagButtonText(tagButton);
    }

    private void setupTargetDateSettingButton() {
        Button targetDateButton = findViewById(R.id.btn_target_date_setting);
        targetDateButton.setOnClickListener(v -> settingsDialogManager.showTargetDateSettingDialog());
        updateTargetDateButtonText();
    }

    public void updateTargetDateButtonText() {
        Button targetDateButton = findViewById(R.id.btn_target_date_setting);
        settingsDialogManager.updateDateButtonText(targetDateButton);
    }

    private void setupLatestApkButton() {
        Button latestApkButton = findViewById(R.id.btn_latest_apk);
        latestApkButton.setOnClickListener(v -> {
            settingsDialogManager.showLatestApkDialog();
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
        checkAndRequestPermissions();
        updateCasualButtonState();
        updateCasualCountDisplay();
        updateTagButtonText();
        updateTargetDateButtonText();
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