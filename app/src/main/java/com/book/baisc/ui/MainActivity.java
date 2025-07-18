package com.book.baisc.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

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
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_ACCESSIBILITY_PERMISSION = 1003;
    private AppLifecycleObserver appLifecycleObserver;
    private DeviceInfoReporter deviceInfoReporter;
    private SettingsManager settingsManager;
    private SettingsDialogManager settingsDialogManager;
    private HomeFragment homeFragment;
    private BroadcastReceiver casualCountUpdateReceiver;

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

        settingsManager = new SettingsManager(this);
        settingsDialogManager = new SettingsDialogManager(this, settingsManager);

        // 设置底部导航
        setupBottomNavigation();
        
        // 注册广播接收器
        registerCasualCountUpdateReceiver();
        
        // 检查并请求所有必要权限
        checkAndRequestPermissions();
        
        // 初始化设备信息上报器并上报设备信息
        deviceInfoReporter = new DeviceInfoReporter(this);
        deviceInfoReporter.reportDeviceInfo();

        // 检查缓存并获取云端内容
        FloatingTextFetcher fetcher = new FloatingTextFetcher(this);
        fetcher.fetchLatestText(new FloatingTextFetcher.OnTextFetchListener() {
            @Override
            public void onTextFetched(String text) {
                android.util.Log.d("MainActivity", "云端文字获取成功: " + text);
            }

            @Override
            public void onFetchError(String error) {
                android.util.Log.w("MainActivity", "云端文字获取失败: " + error);
            }
        });
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnNavigationItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            if (item.getItemId() == R.id.nav_home) {
                if (homeFragment == null) {
                    homeFragment = new HomeFragment();
                }
                selectedFragment = homeFragment;
            } else if (item.getItemId() == R.id.nav_settings) {
                selectedFragment = new SettingsFragment();
            }
            
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit();
            }
            return true;
        });
        
        // 默认显示首页
        homeFragment = new HomeFragment();
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, homeFragment)
            .commit();
        bottomNav.setSelectedItemId(R.id.nav_home);
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
        AccessibilityManager accessibilityManager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> accessibilityServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        
        for (AccessibilityServiceInfo info : accessibilityServices) {
            if (info.getId().equals(getPackageName() + "/" + FloatingAccessibilityService.class.getName())) {
                return true;
            }
        }
        
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

    /**
     * 供外部调用的方法，用于更新目标日期按钮文本
     */
    public void updateTargetDateButtonText() {
        if (homeFragment != null) {
            homeFragment.updateTargetDateButtonText();
        }
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
            if (appLifecycleObserver == null) {
                initAppLifecycleObserver();
            }
        }
    }

    private void registerCasualCountUpdateReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Const.ACTION_UPDATE_CASUAL_COUNT);
        casualCountUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Const.ACTION_UPDATE_CASUAL_COUNT.equals(intent.getAction())) {
                    // 通知HomeFragment更新宽松模式次数显示
                    if (homeFragment != null && homeFragment.getView() != null) {
                        homeFragment.updateCasualCountDisplay();
                        homeFragment.updateAppCasualCountDisplay();
                    }
                }
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(casualCountUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(casualCountUpdateReceiver, filter);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理设备信息上报器
        if (deviceInfoReporter != null) {
            deviceInfoReporter.release();
            deviceInfoReporter = null;
        }
        // 注销广播接收器
        if (casualCountUpdateReceiver != null) {
            unregisterReceiver(casualCountUpdateReceiver);
            casualCountUpdateReceiver = null;
        }
    }
} 