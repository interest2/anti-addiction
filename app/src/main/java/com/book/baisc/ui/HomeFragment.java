package com.book.baisc.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.book.baisc.R;
import com.book.baisc.config.SettingsManager;
import com.book.baisc.config.Const;
import com.book.baisc.config.CustomAppManager;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class HomeFragment extends Fragment implements 
    AppCardAdapter.OnAppCardClickListener,
    AppCardAdapter.OnMonitorToggleListener,
    AppCardAdapter.OnEditClickListener {

    private SettingsManager settingsManager;
    private SettingsDialogManager settingsDialogManager;
    private CustomAppManager customAppManager;
    
    // APP卡片相关
    private RecyclerView rvAppCards;
    private AppCardAdapter appCardAdapter;
    private List<Object> allApps; // 包含预定义APP和自定义APP
    
    // 倒计时相关
    private Handler countdownHandler;
    private Runnable countdownRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        // 初始化设置管理器
        settingsManager = new SettingsManager(requireContext());
        settingsDialogManager = new SettingsDialogManager(requireContext(), settingsManager);
        customAppManager = CustomAppManager.getInstance();
        
        // 初始化APP列表
        updateAppList();
        
        // 初始化APP卡片RecyclerView
        initAppCards(view);
        
        // 设置加号按钮点击事件
        setupAddButton(view);
        
        // 设置HTML文本，让"功能"二字加粗
        TextView tvDescription = view.findViewById(R.id.tv_description);
        if (tvDescription != null) {
            tvDescription.setText(android.text.Html.fromHtml(
                "<b>功能</b>：打开支持的APP，悬浮窗会遮盖特定页面（如首页）。<br/>" +
                "<b>所需权限</b>：显示在其他应用的上层、无障碍服务、允许后台运行。"
            ));
        }
        
        // 启动倒计时更新
        startCountdown();
        
        return view;
    }

    private void setupAddButton(View view) {
        ImageButton btnAddApp = view.findViewById(R.id.btn_add_app);
        if (btnAddApp != null) {
            btnAddApp.setOnClickListener(v -> showAddAppDialog());
        }
    }

    private void showAddAppDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_app, null);
        
        TextInputEditText etAppName = dialogView.findViewById(R.id.et_app_name);
        TextInputEditText etPackageName = dialogView.findViewById(R.id.et_package_name);
        TextInputEditText etTargetWord = dialogView.findViewById(R.id.et_target_word);
        TextInputEditText etCasualLimitCount = dialogView.findViewById(R.id.et_casual_limit_count);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnSave = dialogView.findViewById(R.id.btn_save);
        
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create();
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        btnSave.setOnClickListener(v -> {
            String appName = etAppName.getText().toString().trim();
            String packageName = etPackageName.getText().toString().trim();
            String targetWord = etTargetWord.getText().toString().trim();
            String casualLimitCountStr = etCasualLimitCount.getText().toString().trim();
            
            // 验证输入
            if (appName.isEmpty()) {
                Toast.makeText(requireContext(), "请输入APP名称", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (packageName.isEmpty()) {
                Toast.makeText(requireContext(), "请输入包名", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (targetWord.isEmpty()) {
                Toast.makeText(requireContext(), "请输入屏蔽关键词", Toast.LENGTH_SHORT).show();
                return;
            }
            
            int casualLimitCount = 1;
            if (!casualLimitCountStr.isEmpty()) {
                try {
                    casualLimitCount = Integer.parseInt(casualLimitCountStr);
                    if (casualLimitCount <= 0) {
                        Toast.makeText(requireContext(), "宽松模式次数必须大于0", Toast.LENGTH_SHORT).show();
                        return;
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(requireContext(), "请输入有效的数字", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            // 保存新APP
            boolean success = customAppManager.addCustomApp(appName, packageName, targetWord, casualLimitCount);
            if (success) {
                Toast.makeText(requireContext(), "APP添加成功", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                // 更新APP列表和卡片显示
                updateAppList();
                if (appCardAdapter != null) {
                    appCardAdapter.updateData(allApps);
                }
            } else {
                Toast.makeText(requireContext(), "包名已存在，请使用其他包名", Toast.LENGTH_SHORT).show();
            }
        });
        
        dialog.show();
    }

    private void updateAppList() {
        // 获取所有APP（预定义 + 自定义）
        allApps = new java.util.ArrayList<>();
        
        // 添加预定义的APP
        for (Const.SupportedApp app : Const.SupportedApp.values()) {
            allApps.add(app);
        }
        
        // 添加自定义APP
        allApps.addAll(customAppManager.getCustomApps());
    }

    private void initAppCards(View view) {
        rvAppCards = view.findViewById(R.id.rv_app_cards);
        android.util.Log.d("HomeFragment", "RecyclerView找到: " + (rvAppCards != null));
        
        if (rvAppCards != null) {
            // 使用GridLayoutManager实现两列布局
            androidx.recyclerview.widget.GridLayoutManager layoutManager = 
                new androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2);
            rvAppCards.setLayoutManager(layoutManager);
            appCardAdapter = new AppCardAdapter(allApps, settingsManager, this, this, this);
            rvAppCards.setAdapter(appCardAdapter);
        }
    }

    @Override
    public void onAppCardClick(Object app) {
        // 显示APP设置弹窗
        showAppSettingsDialog(app);
    }

    @Override
    public void onMonitorToggle(Object app, boolean isEnabled) {
        // 处理监测开关状态变化
        String packageName = getPackageName(app);
        if (packageName != null) {
            settingsManager.setAppMonitoringEnabled(packageName, isEnabled);
            android.util.Log.d("HomeFragment", "监测开关状态改变: " + packageName + " = " + isEnabled);
            
            // 显示提示
            String status = isEnabled ? "已开启监测" : "已关闭监测";
            Toast.makeText(requireContext(), status, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onEditClick(Object app) {
        // 处理编辑图标点击
        String appName = getAppName(app);
        Toast.makeText(requireContext(), "编辑 " + appName, Toast.LENGTH_SHORT).show();
        // TODO: 实现编辑功能
    }

    private String getPackageName(Object app) {
        if (app instanceof Const.SupportedApp) {
            return ((Const.SupportedApp) app).getPackageName();
        } else if (app instanceof Const.CustomApp) {
            return ((Const.CustomApp) app).getPackageName();
        }
        return null;
    }

    private String getAppName(Object app) {
        if (app instanceof Const.SupportedApp) {
            return ((Const.SupportedApp) app).getAppName();
        } else if (app instanceof Const.CustomApp) {
            return ((Const.CustomApp) app).getAppName();
        }
        return "未知APP";
    }

    private void showAppSettingsDialog(Object app) {
        // 显示"单次解禁时长"弹窗
        showTimeSettingDialogForApp(app);
    }

    private void showTimeSettingDialogForApp(Object app) {
        // 创建自定义布局的弹窗
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_time_setting, null);
        
        Button strictModeButton = dialogView.findViewById(R.id.btn_strict_mode);
        Button casualModeButton = dialogView.findViewById(R.id.btn_casual_mode);
        EditText etCasualLimitCount = dialogView.findViewById(R.id.et_casual_limit_count);
        Button btnSaveCasualLimit = dialogView.findViewById(R.id.btn_save_casual_limit);
        
        // 获取APP信息
        String appName;
        int casualLimitCount;
        
        if (app instanceof Const.SupportedApp) {
            Const.SupportedApp supportedApp = (Const.SupportedApp) app;
            appName = supportedApp.getAppName();
            // 优先使用自定义设置，如果没有则使用默认值
            Integer customLimit = settingsManager.getCustomCasualLimitCount(supportedApp.getPackageName());
            casualLimitCount = customLimit != null ? customLimit : supportedApp.getCasualLimitCount();
        } else if (app instanceof Const.CustomApp) {
            Const.CustomApp customApp = (Const.CustomApp) app;
            appName = customApp.getAppName();
            casualLimitCount = customApp.getCasualLimitCount();
        } else {
            return;
        }
        
        // 设置输入框的当前值
        etCasualLimitCount.setText(String.valueOf(casualLimitCount));
        
        // 检查宽松模式剩余次数
        int casualCount = settingsManager.getAppCasualCloseCount(app);
        int remainingCount = Math.max(0, casualLimitCount - casualCount);
        
        // 如果宽松模式次数用完，置灰按钮
        if (remainingCount <= 0) {
            casualModeButton.setEnabled(false);
            casualModeButton.setAlpha(0.5f);
            casualModeButton.setText("宽松模式 (次数已用完)");
        }
        
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
            .setTitle(appName)
            .setView(dialogView)
            .setNegativeButton("取消", null)
            .create();
        
        // 设置保存按钮点击事件
        btnSaveCasualLimit.setOnClickListener(v -> {
            String inputText = etCasualLimitCount.getText().toString().trim();
            if (inputText.isEmpty()) {
                Toast.makeText(requireContext(), "请输入数字", Toast.LENGTH_SHORT).show();
                return;
            }
            
            try {
                int newLimitCount = Integer.parseInt(inputText);
                if (newLimitCount < 1 || newLimitCount > 3) {
                    Toast.makeText(requireContext(), "请输入1-3之间的数字", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // 更新APP的casualLimitCount
                if (app instanceof Const.SupportedApp) {
                    // 对于预定义APP，保存自定义次数设置
                    Const.SupportedApp supportedApp = (Const.SupportedApp) app;
                    settingsManager.setCustomCasualLimitCount(supportedApp.getPackageName(), newLimitCount);
                    Toast.makeText(requireContext(), "保存成功", Toast.LENGTH_SHORT).show();
                    
                    // 更新APP列表显示
                    updateAppCardsDisplay();
                } else if (app instanceof Const.CustomApp) {
                    Const.CustomApp customApp = (Const.CustomApp) app;
                    customApp.setCasualLimitCount(newLimitCount);
                    customAppManager.saveCustomAppsChanges(); // 保存到本地存储
                    Toast.makeText(requireContext(), "保存成功", Toast.LENGTH_SHORT).show();
                    
                    // 更新APP列表显示
                    updateAppCardsDisplay();
                }
                
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "请输入有效的数字", Toast.LENGTH_SHORT).show();
            }
        });
        
        // 设置按钮点击事件
        strictModeButton.setOnClickListener(v -> {
            dialog.dismiss();
            settingsDialogManager.showTimeSettingDialogForApp(app, true);
        });
        
        casualModeButton.setOnClickListener(v -> {
            // 只有在按钮可用时才执行
            if (casualModeButton.isEnabled()) {
                dialog.dismiss();
                settingsDialogManager.showTimeSettingDialogForApp(app, false);
            }
        });
        
        dialog.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 更新APP列表
        updateAppList();
        // 更新APP卡片数据
        if (appCardAdapter != null) {
            appCardAdapter.updateData(allApps);
        }
        
        // 启动倒计时
        startCountdown();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // 停止倒计时
        stopCountdown();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        // 清理资源
        stopCountdown();
        countdownHandler = null;
        countdownRunnable = null;
    }
    
    /**
     * 供外部调用的方法，用于更新APP卡片显示
     */
    public void updateAppCardsDisplay() {
        updateAppList();
        if (appCardAdapter != null) {
            appCardAdapter.updateData(allApps);
        }
    }
    
    /**
     * 启动倒计时
     */
    private void startCountdown() {
        if (countdownHandler == null) {
            countdownHandler = new Handler(Looper.getMainLooper());
        }
        
        if (countdownRunnable == null) {
            countdownRunnable = new Runnable() {
                @Override
                public void run() {
                    // 更新APP卡片数据
                    if (appCardAdapter != null) {
                        appCardAdapter.updateData(allApps);
                    }
                    // 每秒更新一次
                    countdownHandler.postDelayed(this, 1000);
                }
            };
        }
        
        countdownHandler.post(countdownRunnable);
    }
    
    /**
     * 停止倒计时
     */
    private void stopCountdown() {
        if (countdownHandler != null && countdownRunnable != null) {
            countdownHandler.removeCallbacks(countdownRunnable);
        }
    }
} 