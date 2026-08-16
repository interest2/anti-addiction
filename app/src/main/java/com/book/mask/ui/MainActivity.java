package com.book.mask.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
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
import com.book.mask.network.AppConfigManager;
import com.book.mask.network.DeviceInfoReporter;
import com.book.mask.personalize.LeisureTimeManager;
import com.book.mask.personalize.RelaxManager;
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
    private static final String STATE_AUTO_OVERLAY_GUIDED = "auto_overlay_guided";
    private static final String STATE_AUTO_ACCESSIBILITY_GUIDED = "auto_accessibility_guided";
    private AppLifecycleObserver appLifecycleObserver;
    private DeviceInfoReporter deviceInfoReporter;
    private RelaxManager relaxManager;
    private LeisureTimeManager leisureTimeManager;
    private SettingsDialogManager settingsDialogManager;
    private HomeNav homeNav;
    private GoalNav goalNav;
    private BroadcastReceiver relaxedCountUpdateReceiver;
    private BottomNavigationView bottomNav;
    private AlertDialog permissionDialog;
    private int pendingPermissionRequest = NO_PENDING_PERMISSION_REQUEST;
    // 打开APP时自动引导前两个权限（悬浮窗、无障碍），每个权限一次启动内最多自动跳转一次，避免用户拒绝后陷入死循环
    private boolean autoOverlayGuided = false;
    private boolean autoAccessibilityGuided = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            pendingPermissionRequest = savedInstanceState.getInt(
                    STATE_PENDING_PERMISSION_REQUEST,
                    NO_PENDING_PERMISSION_REQUEST
            );
            autoOverlayGuided = savedInstanceState.getBoolean(STATE_AUTO_OVERLAY_GUIDED, false);
            autoAccessibilityGuided =
                    savedInstanceState.getBoolean(STATE_AUTO_ACCESSIBILITY_GUIDED, false);
        }
        // 初始化 MMKV
        String rootDir = MMKV.initialize(this);
        android.util.Log.d("MainActivity", "MMKV initialized, root: " + rootDir);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            // 系统栏已在根布局统一让开，这里必须消费掉：原样返回会继续下发给子 View，
            // 而 BottomNavigationView 自带的 inset 处理会把同一份底部 inset 再加成自己的
            // paddingBottom，把固定高度（55dp）的底栏内容整个挤没，只剩背景胶囊。
            // 将来若有子 View 需要 inset，应在此处按需分发，不要恢复成原样返回。
            return WindowInsetsCompat.CONSUMED;
        });

        // 初始化自定义应用管理器 - 提前到这里，为其他组件提供基础数据
        CustomAppManager.initialize(this);

        relaxManager = new RelaxManager(this);
        settingsDialogManager = new SettingsDialogManager(this, relaxManager);

        // 设置底部导航
        setupBottomNavigation(savedInstanceState != null);
        
        // 注册广播接收器
        registerRelaxedCountUpdateReceiver();
        
        new Thread(AppConfigManager::refreshConfig).start();

        // 初始化设备信息上报器并上报设备信息
        deviceInfoReporter = new DeviceInfoReporter(this);
        deviceInfoReporter.reportDeviceInfo();

        // 检查缓存并获取云端内容
        TextFetcher fetcher = new TextFetcher(this);
        fetcher.fetchLatestText(new TextFetcher.OnTextFetchListener() {
            @Override
            public void onTextFetched(String text) {
                android.util.Log.d("MainActivity", "提醒文字获取成功");
            }

            @Override
            public void onFetchError(String error) {
                android.util.Log.w("MainActivity", "云端文字获取失败: " + error);
            }
        });

        resetRelaxedCount();
        alignRelaxedCountToLimit();
    }

    /**
     * 宽松次数上限下调后的一次性对齐。
     *
     * <p>20 点重置把「剩余 1 次」编码成「已用 = 上限 - 1」（见 {@link #performAfterEightPMAction()}），
     * 这个编码绑死了写入时的上限。上限一旦下调，同一份已用次数会被重新翻译成剩余 0 次，
     * 表现为升级当天各卡片突然全部显示 0 次，且当天再也补不回来
     * （剩余为 0 时不满足 20 点重置的 {@code > 1} 条件）。
     *
     * <p>对齐口径与「20 点后最多 1 次」一致：已用次数够到新上限时压到「剩余 1 次」，够不到则不动。
     * 标记 key 带上限值，因此以后每次调整上限都会自动再对齐一轮。
     */
    private void alignRelaxedCountToLimit() {
        MMKV mmkv = MMKV.mmkvWithID("relaxed_count_reset");
        String alignedKey = "limit_aligned_" + CustomApp.MAX_RELAXED_LIMIT_COUNT;
        if (mmkv.getBoolean(alignedKey, false)) {
            return;
        }

        if (relaxManager == null) {
            relaxManager = new RelaxManager(this);
        }
        CustomAppManager customAppManager = CustomAppManager.getInstance();
        for (CustomApp app : customAppManager.getAllApps()) {
            int limit = alignLimitCount(customAppManager, app);
            if (relaxManager.getAppRelaxedCloseCount(app) >= limit) {
                relaxManager.setAppRelaxedCloseCount(app, limit - 1);
                android.util.Log.d("MainActivity",
                        app.getAppName() + " 宽松已用次数按新上限 " + limit + " 对齐为剩余 1 次");
            }
        }

        mmkv.putBoolean(alignedKey, true).commit();
    }

    /**
     * 把该 APP 存量的宽松次数上限钳到当前上限并落库，返回对齐后的上限。
     * 存量值可能来自旧版本的默认值或旧备份，不一起降下来的话，之后打开宽松弹窗才降，
     * 那时对齐已经跑过，用户又会撞上一次剩余 0 次。
     */
    private int alignLimitCount(CustomAppManager customAppManager, CustomApp app) {
        int limit = app.getRelaxedLimitCount();
        int aligned = CustomApp.clampRelaxedLimitCount(limit);
        if (aligned != limit) {
            app.setRelaxedLimitCount(aligned);
            customAppManager.persistAppChange(app);
        }
        return aligned;
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

    private void setupBottomNavigation(boolean restored) {
        bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnNavigationItemSelectedListener(item -> {
            FragmentManager fragmentManager = getSupportFragmentManager();
            Fragment selectedFragment = null;
            if (item.getItemId() == R.id.navigation_home) {
                if (homeNav == null) homeNav = new HomeNav();
                selectedFragment = homeNav;
            } else if (item.getItemId() == R.id.navigation_goal) {
                if (goalNav == null) goalNav = new GoalNav();
                selectedFragment = goalNav;
            } else if (item.getItemId() == R.id.navigation_settings) {
                // 每次都新建实例：SettingsNav 的 ActivityResultLauncher 只在首次 attach 时注册，
                // 复用被 replace 销毁过的旧实例会导致 launcher 未注册而崩溃（导出/导入备份）
                selectedFragment = new SettingsNav();
            }
            if (selectedFragment != null) {
                View fragmentContainer = findViewById(R.id.fragment_container);
                boolean hasDetailPage = fragmentManager.getBackStackEntryCount() > 0;
                if (hasDetailPage) {
                    fragmentContainer.setVisibility(View.INVISIBLE);
                    fragmentManager.popBackStackImmediate(
                            null,
                            FragmentManager.POP_BACK_STACK_INCLUSIVE
                    );
                }
                if (hasDetailPage) {
                    fragmentManager.beginTransaction()
                            .setReorderingAllowed(true)
                            .replace(R.id.fragment_container, selectedFragment)
                            .commitNow();
                    fragmentContainer.setVisibility(View.VISIBLE);
                } else {
                    fragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, selectedFragment)
                            .commit();
                }
            }
            return true;
        });
        bottomNav.setOnItemReselectedListener(item -> {
            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                getSupportFragmentManager().popBackStack();
            }
        });
        if (restored && adoptRestoredFragment()) {
            // Activity 重建（如设置壁纸后系统重建）时，FragmentManager 与底栏各自恢复上次的状态，
            // 此处若再铺一次首页，就会变成「底栏高亮更多、内容却是首页」，
            // 且底栏认为更多仍是选中项，再点它只走 reselect 不换页，表现为点击无反应。
            return;
        }

        // 默认显示首页
        if (homeNav == null) homeNav = new HomeNav();
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, homeNav)
            .commit();
        bottomNav.setSelectedItemId(R.id.navigation_home);

    }

    /**
     * 打开二级页面。转场动画与回退栈规则收敛在此，避免各 tab 页各写一套逐渐漂移。
     */
    public void openSubPage(Fragment page) {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                )
                .replace(R.id.fragment_container, page)
                .addToBackStack(page.getClass().getSimpleName())
                .commit();
    }

    /**
     * 把重建后 FragmentManager 恢复出来的页面接回成员变量，避免下次切换时重复新建。
     *
     * @return 容器里确有恢复出来的页面，调用方无需再铺默认页
     */
    private boolean adoptRestoredFragment() {
        Fragment restored = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (restored instanceof HomeNav) {
            homeNav = (HomeNav) restored;
        } else if (restored instanceof GoalNav) {
            goalNav = (GoalNav) restored;
        }
        return restored != null;
    }

    private Intent overlaySettingsIntent() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        intent.setData(Uri.parse("package:" + getPackageName()));
        return intent;
    }

    private Intent accessibilitySettingsIntent() {
        return new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
    }

    private void showOverlayPermissionDialog() {
        String title = "需要悬浮窗权限";
        String message = "悬浮窗用于在支持的 APP 上显示提醒。请在系统设置中允许本应用显示在其他应用上层。";
        showPermissionDialog(title, message, overlaySettingsIntent(), REQUEST_OVERLAY_PERMISSION);
    }

    private void showAccessibilityPermissionDialog() {
        String title = "需要开启无障碍服务";
        String message = "无障碍服务用于检测当前打开的APP。请在系统设置中找到本应用并开启服务。";
        showPermissionDialog(title, message, accessibilitySettingsIntent(), REQUEST_ACCESSIBILITY_PERMISSION);
    }

    private Intent backgroundSettingsIntent() {
        return new Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()));
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
        openPermissionSettings(backgroundSettingsIntent(), REQUEST_BACKGROUND_PERMISSION);
    }

    private void refreshHomePermissionStatus() {
        if (homeNav != null) {
            homeNav.refreshPermissionStatus();
        }
    }

    /**
     * 打开APP时自动引导前两个权限：悬浮窗、无障碍。
     * 未开启则直接跳到对应系统设置页；两者按顺序逐个引导（返回后由 onPostResume 继续下一个）。
     * 每个权限一次启动内最多自动跳一次（guided 标记），用户拒绝也不会被反复拉回设置页。
     */
    private void advanceAutoPermissionGuide() {
        if (pendingPermissionRequest != NO_PENDING_PERMISSION_REQUEST) {
            return;
        }
        if (permissionDialog != null && permissionDialog.isShowing()) {
            return;
        }
        if (!autoOverlayGuided && !PermissionStatus.canDrawOverlays(this)) {
            autoOverlayGuided = true;
            openPermissionSettings(overlaySettingsIntent(), REQUEST_OVERLAY_PERMISSION);
            return;
        }
        if (!autoAccessibilityGuided && !PermissionStatus.isAccessibilityServiceEnabled(this)) {
            autoAccessibilityGuided = true;
            openPermissionSettings(accessibilitySettingsIntent(), REQUEST_ACCESSIBILITY_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
        SettingsDialogManager.handleMicPermissionResult(requestCode, granted);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (pendingPermissionRequest != NO_PENDING_PERMISSION_REQUEST) {
            int finishedRequest = pendingPermissionRequest;
            pendingPermissionRequest = NO_PENDING_PERMISSION_REQUEST;
            if (finishedRequest == REQUEST_BACKGROUND_PERMISSION
                    && PermissionStatus.isBackgroundRunningAllowed(this)) {
                UiFeedback.show(this, "设置成功");
            }
            refreshHomePermissionStatus();
        }
        advanceAutoPermissionGuide();
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
        outState.putBoolean(STATE_AUTO_OVERLAY_GUIDED, autoOverlayGuided);
        outState.putBoolean(STATE_AUTO_ACCESSIBILITY_GUIDED, autoAccessibilityGuided);
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
        // 休闲-宽松：只要当天没用完，余额重置为小档 1 次（15 分钟）
        if (leisureTimeManager == null) {
            leisureTimeManager = new LeisureTimeManager(this);
        }
        leisureTimeManager.resetRelaxedRemainingToShort();

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
