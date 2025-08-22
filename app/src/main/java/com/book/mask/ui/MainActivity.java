package com.book.mask.ui;

import android.content.Intent;
import android.content.SharedPreferences;
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

import com.book.mask.R;
import com.book.mask.config.Const;
import com.book.mask.config.CustomAppManager;
import com.book.mask.config.CustomApp;
import com.book.mask.floating.FloatService;
import com.book.mask.lifecycle.AppLifecycleObserver;
import com.book.mask.network.DeviceInfoReporter;
import com.book.mask.setting.RelaxManager;
import com.book.mask.network.TextFetcher;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_ACCESSIBILITY_PERMISSION = 1003;
    private AppLifecycleObserver appLifecycleObserver;
    private DeviceInfoReporter deviceInfoReporter;
    private RelaxManager relaxManager;
    private SettingsDialogManager settingsDialogManager;
    private HomeNav homeNav;
    private GoalNav goalNav;
    private SettingsNav settingsNav;
    private BroadcastReceiver relaxedCountUpdateReceiver;

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

        relaxManager = new RelaxManager(this);
        settingsDialogManager = new SettingsDialogManager(this, relaxManager);

        // 设置底部导航
        setupBottomNavigation();
        
        // 注册广播接收器
        registerRelaxedCountUpdateReceiver();
        
        // 检查并请求所有必要权限
        checkAndRequestPermissions();
        
        // 初始化设备信息上报器并上报设备信息
        deviceInfoReporter = new DeviceInfoReporter(this);
        deviceInfoReporter.reportDeviceInfo();

        // 初始化自定义应用管理器
        CustomAppManager.initialize(this);

        // 检查缓存并获取云端内容
        TextFetcher fetcher = new TextFetcher(this);
        fetcher.fetchLatestText(new TextFetcher.OnTextFetchListener() {
            @Override
            public void onTextFetched(String text) {
                android.util.Log.d("MainActivity", "云端文字获取成功: " + text);
            }

            @Override
            public void onFetchError(String error) {
                android.util.Log.w("MainActivity", "云端文字获取失败: " + error);
            }
        });

        resetRelaxedCount();
    }

    private void resetRelaxedCount() {
        // 1. 获取当前日期
        String currentDate = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
        SharedPreferences prefs = getSharedPreferences("relaxed_count_reset", MODE_PRIVATE);
        String lastResetDate = prefs.getString("last_reset_date", "");

// 2. 如果不是同一天，重置所有APP的宽松关闭次数为各自的最大值
        if (!currentDate.equals(lastResetDate)) {
            RelaxManager relaxManager = new RelaxManager(this);

            // 所有APP（包括预定义和自定义）
            CustomAppManager customAppManager = CustomAppManager.getInstance();
            for (CustomApp app : customAppManager.getAllApps()) {
                relaxManager.setAppRelaxedCloseCount(app, 0); // 这里应设置为0
            }

            // 记录本次重置日期
            prefs.edit().putString("last_reset_date", currentDate).apply();
        }
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnNavigationItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            if (item.getItemId() == R.id.navigation_home) {
                if (homeNav == null) homeNav = new HomeNav();
                selectedFragment = homeNav;
            } else if (item.getItemId() == R.id.navigation_goal) {
                if (goalNav == null) goalNav = new GoalNav();
                selectedFragment = goalNav;
            } else if (item.getItemId() == R.id.navigation_settings) {
                if (settingsNav == null) settingsNav = new SettingsNav();
                selectedFragment = settingsNav;
            }
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit();
            }
            return true;
        });
        // 默认显示首页
        if (homeNav == null) homeNav = new HomeNav();
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, homeNav)
            .commit();
        bottomNav.setSelectedItemId(R.id.navigation_home);
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
            Toast.makeText(this, "请开启无障碍服务以检测支持的APP", Toast.LENGTH_LONG).show();
        } else {
            // 已有所有权限，初始化应用生命周期监听器
            initAppLifecycleObserver();
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager accessibilityManager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> accessibilityServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        
        for (AccessibilityServiceInfo info : accessibilityServices) {
            if (info.getId().equals(getPackageName() + "/" + FloatService.class.getName())) {
                return true;
            }
        }
        
        try {
            String enabledServices = Settings.Secure.getString(
                getContentResolver(), 
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            android.util.Log.d("MainActivity", "系统启用的无障碍服务: " + enabledServices);
            
            if (enabledServices != null) {
                String ourService = getPackageName() + "/" + FloatService.class.getName();
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
        Toast.makeText(this, "检测功能已启用，打开支持的APP时会显示悬浮窗", Toast.LENGTH_LONG).show();
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
                Toast.makeText(this, "没有无障碍服务权限，无法检测支持的APP", Toast.LENGTH_LONG).show();
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

    private void registerRelaxedCountUpdateReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Const.ACTION_UPDATE_RELAXED_COUNT);
        relaxedCountUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Const.ACTION_UPDATE_RELAXED_COUNT.equals(intent.getAction())) {
                    // 通知HomeFragment更新APP卡片显示
                    if (homeNav != null) {
                        homeNav.updateAppCardsDisplay();
                    }
                }
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(relaxedCountUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(relaxedCountUpdateReceiver, filter);
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
        if (relaxedCountUpdateReceiver != null) {
            unregisterReceiver(relaxedCountUpdateReceiver);
            relaxedCountUpdateReceiver = null;
        }
    }
} 