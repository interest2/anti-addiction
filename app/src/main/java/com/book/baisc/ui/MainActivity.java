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

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_ACCESSIBILITY_PERMISSION = 1003;
    private AppLifecycleObserver appLifecycleObserver;
    private DeviceInfoReporter deviceInfoReporter;
    private SettingsManager settingsManager;

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

        // 检查并请求所有必要权限
        checkAndRequestPermissions();
        
        // 设置优化指引按钮点击事件
//        setupOptimizationGuideButton();
        
        // 设置时间间隔设置按钮点击事件
        setupTimeSettingButtons();
        
        // 设置激励语标签按钮
        setupTagSettingButton();
        
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
            showTimeSettingDialog(true); // true for daily
        });
        
        Button casualButton = findViewById(R.id.btn_casual_time_setting);
        casualButton.setOnClickListener(v -> {
            showTimeSettingDialog(false); // false for casual
        });
    }
    
    private void showTimeSettingDialog(boolean isDaily) {
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
        
        String dialogTitle = isDaily ? "严格模式" : "宽松模式";

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
        explanation.append("新的时长，将在回答一次算术题后才会生效");

        new android.app.AlertDialog.Builder(this)
                .setTitle("解禁时长说明")
               .setMessage(explanation.toString())
                .setPositiveButton("好的", null)
                .show();
    }
    
    private void updateCasualButtonState() {
        Button casualButton = findViewById(R.id.btn_casual_time_setting);
        if (casualButton != null) {
            int closeCount = settingsManager.getCasualCloseCount();
            casualButton.setEnabled(closeCount < Const.CASUAL_LIMIT_COUNT);
        }
    }

    private void updateCasualCountDisplay() {
        TextView countText = findViewById(R.id.tv_casual_count);
        if (countText != null) {
            int closeCount = settingsManager.getCasualCloseCount();
            int remainingCount = Math.max(0, Const.CASUAL_LIMIT_COUNT - closeCount);
            countText.setText("今日剩余: " + remainingCount + "次");
        }
    }

    private void setupTagSettingButton() {
        Button tagButton = findViewById(R.id.btn_tag_setting);
        tagButton.setOnClickListener(v -> showTagSettingDialog());
        updateTagButtonText();
    }
    
    private void updateTagButtonText() {
        Button tagButton = findViewById(R.id.btn_tag_setting);
        if (tagButton != null) {
            String currentTag = settingsManager.getMotivationTag();
            tagButton.setText("目标: " + currentTag);
        }
    }

    private void showTagSettingDialog() {
        final String[] predefinedTags = SettingsManager.getAvailableTags();
        final String customTagOption = "自定义...";

        // 将预设标签和“自定义”选项合并
        final String[] dialogOptions = new String[predefinedTags.length + 1];
        System.arraycopy(predefinedTags, 0, dialogOptions, 0, predefinedTags.length);
        dialogOptions[dialogOptions.length - 1] = customTagOption;

        new android.app.AlertDialog.Builder(this)
                .setTitle("选择或自定义目标")
                .setItems(dialogOptions, (dialog, which) -> {
                    if (which == predefinedTags.length) {
                        // 点击了“自定义...”
                        showCustomTagInputDialog();
                    } else {
                        // 点击了预设标签
                        String selectedTag = dialogOptions[which];
                        settingsManager.setMotivationTag(selectedTag);
                        updateTagButtonText();
                        Toast.makeText(this, "已设置为: " + selectedTag, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCustomTagInputDialog() {
        final EditText input = new EditText(this);
        // 设置输入长度限制为8
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(8) });
        input.setHint("不超过8个字");

        new android.app.AlertDialog.Builder(this)
            .setTitle("自定义激励语标签")
            .setView(input)
            .setPositiveButton("确定", (dialog, which) -> {
                String customTag = input.getText().toString().trim();
                if (customTag.isEmpty()) {
                    Toast.makeText(this, "标签不能为空", Toast.LENGTH_SHORT).show();
                } else {
                    settingsManager.setMotivationTag(customTag);
                    updateTagButtonText();
                    Toast.makeText(this, "已设置为: " + customTag, Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
               .show();
    }
    
    private void setupLatestApkButton() {
        Button latestApkButton = findViewById(R.id.btn_latest_apk);
        latestApkButton.setOnClickListener(v -> {
            showLatestApkDialog();
        });
    }
    
    private void showLatestApkDialog() {
        try {
            // 获取当前版本信息
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            int versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            
            // 构建弹窗内容
            StringBuilder content = new StringBuilder();
            content.append("• 当前版本：").append(versionName).append("\n\n");
            content.append("• 下载页面（找到最新的 apk 文件下载）：\n");
            content.append("https://gitee.com/interest2/anti-addiction/releases\n");
            content.append("https://github.com/interest2/anti-addiction/releases\n\n");

            content.append("备注：\n");
            content.append("• gitee地址：需要登录\n");
            content.append("• github地址：无需登录，但网络可能不稳定\n\n");
            
            new android.app.AlertDialog.Builder(this)
                .setTitle("最新安装包地址")
                .setMessage(content.toString())
                .setPositiveButton("复制gitee地址", (dialog, which) -> {
                    copyToClipboard("https://gitee.com/interest2/anti-addiction/releases");
                    Toast.makeText(this, "gitee地址已复制到剪贴板", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("复制github地址", (dialog, which) -> {
                    copyToClipboard("https://github.com/interest2/anti-addiction/releases");
                    Toast.makeText(this, "gitHub地址已复制到剪贴板", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("关闭", null)
                .show();
                
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "获取版本信息失败", e);
            Toast.makeText(this, "获取版本信息失败", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void copyToClipboard(String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("下载地址", text);
        clipboard.setPrimaryClip(clip);
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