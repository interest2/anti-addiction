package com.book.mask.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.util.Calendar;
import java.util.List;

import com.book.mask.R;
import com.book.mask.constant.Const;
import com.book.mask.config.CustomAppManager;
import com.book.mask.config.CustomApp;
import com.book.mask.lifecycle.AppLifecycleObserver;
import com.book.mask.network.DeviceInfoReporter;
import com.book.mask.setting.RelaxManager;
import com.book.mask.network.TextFetcher;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tencent.mmkv.MMKV;

public class MainActivity extends AppCompatActivity {

    private static final int NO_PENDING_PERMISSION_REQUEST = 0;
    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_ACCESSIBILITY_PERMISSION = 1003;
    private static final int REQUEST_BACKGROUND_PERMISSION = 1004;
    private static final String STATE_PENDING_PERMISSION_REQUEST = "pending_permission_request";
    private AppLifecycleObserver appLifecycleObserver;
    private DeviceInfoReporter deviceInfoReporter;
    private RelaxManager relaxManager;
    private SettingsDialogManager settingsDialogManager;
    private HomeNav homeNav;
    private GoalNav goalNav;
    private SettingsNav settingsNav;
    private BroadcastReceiver relaxedCountUpdateReceiver;
    private BottomNavigationView bottomNav;
    private AlertDialog permissionDialog;
    private int pendingPermissionRequest = NO_PENDING_PERMISSION_REQUEST;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            pendingPermissionRequest = savedInstanceState.getInt(
                    STATE_PENDING_PERMISSION_REQUEST,
                    NO_PENDING_PERMISSION_REQUEST
            );
        }
        // 初始化 MMKV
        String rootDir = MMKV.initialize(this);
        android.util.Log.d("MainActivity", "MMKV initialized, root: " + rootDir);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 初始化自定义应用管理器 - 提前到这里，为其他组件提供基础数据
        CustomAppManager.initialize(this);

        relaxManager = new RelaxManager(this);
        settingsDialogManager = new SettingsDialogManager(this, relaxManager);

        // 设置底部导航
        setupBottomNavigation();
        
        // 注册广播接收器
        registerRelaxedCountUpdateReceiver();
        
        // 初始化设备信息上报器并上报设备信息
        deviceInfoReporter = new DeviceInfoReporter(this);
        deviceInfoReporter.reportDeviceInfo();

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
        MMKV mmkv = MMKV.mmkvWithID("relaxed_count_reset");
        String lastResetDate = mmkv.getString("last_reset_date", "");

// 2. 如果不是同一天，重置所有APP的宽松关闭次数为各自的最大值
        if (!currentDate.equals(lastResetDate)) {
            RelaxManager relaxManager = new RelaxManager(this);

            // 所有APP（包括预定义和自定义）
            CustomAppManager customAppManager = CustomAppManager.getInstance();
            for (CustomApp app : customAppManager.getAllApps()) {
                relaxManager.setAppRelaxedCloseCount(app, 0); // 这里应设置为0
            }

            // 记录本次重置日期
            mmkv.putString("last_reset_date", currentDate).commit();
        }
    }

    private void setupBottomNavigation() {
        bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnNavigationItemSelectedListener(item -> {
            getSupportFragmentManager().popBackStackImmediate(
                    null,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE
            );
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
        bottomNav.setOnItemReselectedListener(item -> {
            if (item.getItemId() == R.id.navigation_settings
                    && getSupportFragmentManager().getBackStackEntryCount() > 0) {
                getSupportFragmentManager().popBackStack();
            }
        });
        // 默认显示首页
        if (homeNav == null) homeNav = new HomeNav();
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, homeNav)
            .commit();
        bottomNav.setSelectedItemId(R.id.navigation_home);
        
    }

    private void showOverlayPermissionDialog() {
        String title = "需要悬浮窗权限";
        String message = "悬浮窗用于在支持的APP上显示提醒。请在系统设置中允许本应用显示在其他应用上层。";
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        intent.setData(Uri.parse("package:" + getPackageName()));
        showPermissionDialog(title, message, intent, REQUEST_OVERLAY_PERMISSION);
    }

    private void showAccessibilityPermissionDialog() {
        String title = "需要开启无障碍服务";
        String message = "无障碍服务用于检测当前打开的APP。请在系统设置中找到本应用并开启服务。";
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        showPermissionDialog(title, message, intent, REQUEST_ACCESSIBILITY_PERMISSION);
    }

    private void showBackgroundPermissionDialog() {
        String title = "建议允许后台运行";
        String message = "允许忽略电池优化后，系统休眠时的后台检测会更稳定。";
        Intent intent = new Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()));
        showPermissionDialog(title, message, intent, REQUEST_BACKGROUND_PERMISSION);
    }

    private void showPermissionDialog(CharSequence title,
                                      CharSequence message,
                                      Intent settingsIntent,
                                      int requestCode) {
        if (permissionDialog != null && permissionDialog.isShowing()) {
            return;
        }

        permissionDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("去开启", (dialog, which) ->
                        openPermissionSettings(settingsIntent, requestCode))
                .setNegativeButton("暂不", null)
                .setCancelable(false)
                .create();
        permissionDialog.setOnDismissListener(dialog -> {
            permissionDialog = null;
            refreshHomePermissionStatus();
        });
        permissionDialog.show();
    }

    private void openPermissionSettings(Intent intent, int requestCode) {
        pendingPermissionRequest = requestCode;
        try {
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            if (requestCode == REQUEST_BACKGROUND_PERMISSION) {
                try {
                    startActivityForResult(
                            new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                            requestCode);
                    return;
                } catch (ActivityNotFoundException fallbackError) {
                    android.util.Log.e(
                            "MainActivity",
                            "无法打开电池优化设置页",
                            fallbackError);
                }
            }
            pendingPermissionRequest = NO_PENDING_PERMISSION_REQUEST;
            android.util.Log.e("MainActivity", "无法打开权限设置页", e);
            UiFeedback.showError(this, "无法打开系统权限设置");
        }
    }

    private boolean initAppLifecycleObserver() {
        if (appLifecycleObserver != null) {
            return false;
        }
        appLifecycleObserver = new AppLifecycleObserver(this);
        return true;
    }

    public void reviewOverlayPermission() {
        showOverlayPermissionDialog();
    }

    public void reviewAccessibilityPermission() {
        showAccessibilityPermissionDialog();
    }

    public void reviewBackgroundPermission() {
        showBackgroundPermissionDialog();
    }

    private void refreshHomePermissionStatus() {
        if (homeNav != null) {
            homeNav.refreshPermissionStatus();
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (pendingPermissionRequest != NO_PENDING_PERMISSION_REQUEST) {
            pendingPermissionRequest = NO_PENDING_PERMISSION_REQUEST;
            refreshHomePermissionStatus();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次返回时检查权限状态
        if (PermissionStatus.isAccessibilityServiceEnabled(this)
                && PermissionStatus.canDrawOverlays(this)) {
            initAppLifecycleObserver();
        }
        refreshHomePermissionStatus();
        
        // 检测当前时间是否晚于20:00
        checkTimeAndPerformAction();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_PENDING_PERMISSION_REQUEST, pendingPermissionRequest);
        super.onSaveInstanceState(outState);
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

    /**
     * 检测当前时间是否晚于20:00
     * @return true表示当前时间晚于20:00，false表示早于或等于20:00
     */
    private boolean isTimeAfterEightPM() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        // 检查是否晚于20:00（即hour >= 20）
        return hour >= 20;
    }

    /**
     * 检测时间并执行相应操作
     */
    private void checkTimeAndPerformAction() {
        if (isTimeAfterEightPM()) {
            performAfterEightPMAction();
        }
    }

    /**
     * 在20:00之后执行的操作
     * 如果卡片显示的宽松剩余次数>1，则重置为1
     */
    private void performAfterEightPMAction() {
        if (relaxManager == null) {
            relaxManager = new RelaxManager(this);
        }
        
        CustomAppManager customAppManager = CustomAppManager.getInstance();
        List<CustomApp> allApps = customAppManager.getAllApps();
        
        boolean hasChanges = false;
        
        for (CustomApp app : allApps) {
            int relaxedLimitCount = app.getRelaxedLimitCount();
            int relaxedCount = relaxManager.getAppRelaxedCloseCount(app);
            int remainingCount = Math.max(0, relaxedLimitCount - relaxedCount);
            
            // 如果剩余次数>1，则重置为1
            if (remainingCount > 1) {
                // 设置已用次数 = 最大次数 - 1，这样剩余次数就是1
                int newRelaxedCount = relaxedLimitCount - 1;
                // 确保不会设置为负数
                if (newRelaxedCount >= 0) {
                    relaxManager.setAppRelaxedCloseCount(app, newRelaxedCount);
                    hasChanges = true;
                    android.util.Log.d("MainActivity", 
                        app.getAppName() + " 宽松剩余次数从 " + remainingCount + " 重置为 1");
                }
            }
        }
        
        // 如果有变更，发送广播通知UI更新
        if (hasChanges) {
            Intent updateIntent = new Intent(Const.ACTION_UPDATE_RELAXED_COUNT);
            sendBroadcast(updateIntent);
            android.util.Log.d("MainActivity", "已重置宽松剩余次数，通知UI更新");
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
