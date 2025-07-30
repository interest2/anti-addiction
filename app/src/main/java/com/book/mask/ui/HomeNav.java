package com.book.mask.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.view.inputmethod.EditorInfo;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.book.mask.R;
import com.book.mask.config.SettingsManager;
import com.book.mask.config.Const;
import com.book.mask.config.CustomAppManager;
import com.book.mask.config.Share;
import com.book.mask.util.ContentUtils;
import com.book.mask.util.ArithmeticUtils;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.util.List;

public class HomeNav extends Fragment implements
    AppCardAdapter.OnAppCardClickListener,
    AppCardAdapter.OnMonitorToggleListener,
    AppCardAdapter.OnEditClickListener {
    private static final String TAG = "HomeNav";

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

        // 设置页的云端最新版本获取
        new Thread(() -> {
            try {
                String versionUrl = Const.DOMAIN_URL + Const.LATEST_VERSION_PATH;
                String response = ContentUtils.doHttpPost(
                        versionUrl,
                        null,
                        java.util.Collections.singletonMap("Content-Type", Const.CONTENT_TYPE)
                );
                Share.latestVersion = response;
                Log.d(TAG, "版本接口响应: " + response);
            } catch (IOException e) {
                Log.e(TAG, "网络请求失败", e);
                Share.latestVersion = "获取失败";
            }
        }).start();

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
                "<b>功能</b>：悬浮窗遮盖APP推荐内容，但保留搜索等功能。<br/>" +
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
            // 如果要关闭屏蔽，需要算术题验证
            if (!isEnabled) {
                showMathChallengeForMonitorToggle(app, packageName);
            } else {
                // 开启监测直接执行
                settingsManager.setAppMonitoringEnabled(packageName, isEnabled);
                android.util.Log.d("HomeFragment", "监测开关状态改变: " + packageName + " = " + isEnabled);
                
                // 显示提示
                String status = isEnabled ? "已开启监测" : "已关闭屏蔽";
                Toast.makeText(requireContext(), status, Toast.LENGTH_SHORT).show();
            }
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
        
        // 新的UI组件
        LinearLayout layoutCasualCountDisplay = dialogView.findViewById(R.id.layout_casual_count_display);
        LinearLayout layoutCasualCountEdit = dialogView.findViewById(R.id.layout_casual_count_edit);
        TextView tvCasualCountDisplay = dialogView.findViewById(R.id.tv_casual_count_display);
        ImageView ivEditCasualCount = dialogView.findViewById(R.id.iv_edit_casual_count);
        EditText etCasualLimitCount = dialogView.findViewById(R.id.et_casual_limit_count);
        TextView ivSaveCasualCount = dialogView.findViewById(R.id.iv_save_casual_count);
        
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
        
        // 设置显示文本的当前值
        tvCasualCountDisplay.setText(String.valueOf(casualLimitCount));
        
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
        
        // 编辑图标点击事件
        ivEditCasualCount.setOnClickListener(v -> {
            // 隐藏显示布局，显示编辑布局
            layoutCasualCountDisplay.setVisibility(View.GONE);
            layoutCasualCountEdit.setVisibility(View.VISIBLE);
            
            // 设置输入框的当前值
            etCasualLimitCount.setText(tvCasualCountDisplay.getText().toString());
            etCasualLimitCount.requestFocus();
            
            // 显示软键盘
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) 
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etCasualLimitCount, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        });
        
        // 保存图标点击事件
        ivSaveCasualCount.setOnClickListener(v -> {
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
                
                // 更新显示文本
                tvCasualCountDisplay.setText(String.valueOf(newLimitCount));
                
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
                
                // 隐藏编辑布局，显示正常布局
                layoutCasualCountEdit.setVisibility(View.GONE);
                layoutCasualCountDisplay.setVisibility(View.VISIBLE);
                
                // 隐藏软键盘
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) 
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(etCasualLimitCount.getWindowToken(), 0);
                }
                
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "请输入有效的数字", Toast.LENGTH_SHORT).show();
            }
        });
        
        // 输入框回车键保存
        etCasualLimitCount.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || 
                (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                ivSaveCasualCount.performClick();
                return true;
            }
            return false;
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

        // 设置更换悬浮窗警示文字来源按钮点击事件
        Button changeFloatingTextSourceButton = dialogView.findViewById(R.id.btn_change_floating_text_source);
        changeFloatingTextSourceButton.setOnClickListener(v -> {
            showFloatingTextSourceDialog(app);
        });
        
        dialog.show();
    }

    /**
     * 显示悬浮窗警示文字来源选择对话框（单选列表风格）
     */
    private void showFloatingTextSourceDialog(Object app) {
        String packageName = getPackageName(app);
        String currentSource = settingsManager.getAppHintSource(packageName);

        String[] options = {Const.CUSTOM_HINT_SOURCE, Const.DEFAULT_HINT_SOURCE};
        int checkedItem = 0;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(currentSource)) {
                checkedItem = i;
                break;
            }
        }

        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("选择悬浮窗警示文字来源")
            .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                if (which == 0) {
                    showCustomTextInputDialog(app);
                } else if (which == 1) {
                    recordFloatingTextSource(Const.DEFAULT_HINT_SOURCE, app);
                    Toast.makeText(requireContext(), "已选择大模型作为悬浮窗警示文字来源", Toast.LENGTH_SHORT).show();
                }
                dialog.dismiss();
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 显示自定义文字输入对话框
     */
    private void showCustomTextInputDialog(Object app) {
        EditText input = new EditText(requireContext());
        input.setHint("请输入自定义警示文字（不超过100字）");
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});
        
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("输入自定义警示文字")
            .setView(input)
            .setPositiveButton("确定", (dialog, which) -> {
                String customText = input.getText().toString().trim();
                if (!customText.isEmpty()) {
                    // 记录到变量
                    recordFloatingTextSource(Const.CUSTOM_HINT_SOURCE, customText, app);
                    Toast.makeText(requireContext(), "自定义警示文字设置成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "请输入内容", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 记录悬浮窗警示文字来源
     */
    private void recordFloatingTextSource(String source, String customText, Object app) {
        // 获取当前选中的APP包名
        String currentAppPackage = getPackageName(app);
        if (currentAppPackage == null) {
            android.util.Log.e("HomeFragment", "无法获取当前选中的APP包名");
            return;
        }
        
        // 使用SettingsManager存储，为每个APP独立存储
        settingsManager.setAppHintSource(currentAppPackage, source);
        if (customText != null && !customText.isEmpty()) {
            settingsManager.setAppHintCustomText(currentAppPackage, customText);
        }
        android.util.Log.d("HomeFragment", "APP " + currentAppPackage + " 悬浮窗警示文字来源已保存: " + source + ", 自定义文字: " + customText);
    }
    
    /**
     * 记录悬浮窗警示文字来源（重载方法，用于只有source参数的情况）
     */
    private void recordFloatingTextSource(String source, Object app) {
        recordFloatingTextSource(source, null, app);
    }

    /**
     * 获取当前选中的APP包名
     */
    private String getCurrentSelectedAppPackage() {
        // 这里需要根据你的实际逻辑获取当前选中的APP包名
        // 可能需要从全局变量、SharedPreferences或其他地方获取
        // 暂时返回null，需要你补充具体的获取逻辑
        return null;
    }

    /**
     * 显示算术题验证弹窗用于关闭屏蔽
     */
    private void showMathChallengeForMonitorToggle(Object app, String packageName) {
        // 创建算术题验证弹窗
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_math_challenge, null);
        
        TextView questionText = dialogView.findViewById(R.id.tv_math_question);
        EditText answerEdit = dialogView.findViewById(R.id.et_math_answer);
        TextView resultText = dialogView.findViewById(R.id.tv_math_result);
        Button submitButton = dialogView.findViewById(R.id.btn_submit_answer);
        Button cancelButton = dialogView.findViewById(R.id.btn_cancel_close);
        
        // 生成算术题
        String question = ArithmeticUtils.customArithmetic(7, 7, 4, 4);
        final int[] correctAnswer = {ArithmeticUtils.getMathAnswer(question)};
        questionText.setText(question);
        
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
            .setTitle("关闭屏蔽验证")
            .setView(dialogView)
            .setCancelable(false)
            .create();
        
        // 提交答案按钮
        submitButton.setOnClickListener(v -> {
            String userAnswer = answerEdit.getText().toString().trim();
            if (userAnswer.isEmpty()) {
                resultText.setText("⚠️ 请输入答案");
                resultText.setVisibility(View.VISIBLE);
                return;
            }
            
            try {
                int answer = Integer.parseInt(userAnswer);
                if (answer == correctAnswer[0]) {
                    // 答案正确，关闭屏蔽
                    resultText.setText("✅ 答案正确！");
                    resultText.setTextColor(requireContext().getResources().getColor(android.R.color.holo_green_light));
                    resultText.setVisibility(View.VISIBLE);
                    
                    // 延迟关闭弹窗并执行关闭屏蔽
                    new Handler().postDelayed(() -> {
                        dialog.dismiss();
                        settingsManager.setAppMonitoringEnabled(packageName, false);
                        android.util.Log.d("HomeFragment", "算术题验证通过，关闭屏蔽: " + packageName);
                        Toast.makeText(requireContext(), "已关闭屏蔽", Toast.LENGTH_SHORT).show();
                        
                        // 更新APP列表显示
                        updateAppCardsDisplay();
                    }, 1000);
                    
                } else {
                    // 答案错误
                    resultText.setText("❌ 答案错误，请重新计算");
                    resultText.setTextColor(requireContext().getResources().getColor(android.R.color.holo_red_light));
                    resultText.setVisibility(View.VISIBLE);
                    
                    // 清空输入框
                    answerEdit.setText("");
                    
                    // 3秒后生成新题目
                    new Handler().postDelayed(() -> {
                        String newQuestion = ArithmeticUtils.customArithmetic(
                                Const.MAX_ADD_LEN, Const.MAX_ADD_LEN, Const.MAX_MULTIPLE_LEN, Const.MAX_MULTIPLE_LEN);
                        correctAnswer[0] = ArithmeticUtils.getMathAnswer(newQuestion);
                        questionText.setText(newQuestion);
                        answerEdit.setText("");
                        resultText.setVisibility(View.GONE);
                    }, 1000);
                }
            } catch (NumberFormatException e) {
                resultText.setText("⚠️ 请输入有效数字");
                resultText.setVisibility(View.VISIBLE);
            }
        });
        
        // 取消按钮
        cancelButton.setOnClickListener(v -> {
            dialog.dismiss();
            // 取消关闭屏蔽，恢复开关状态
            if (appCardAdapter != null) {
                appCardAdapter.updateData(allApps);
            }
        });
        
        // 回车键提交答案
        answerEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || 
                (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                submitButton.performClick();
                return true;
            }
            return false;
        });
        
        dialog.show();
        
        // 让输入框获得焦点
        answerEdit.requestFocus();
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