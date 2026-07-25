package com.book.mask.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
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
import com.book.mask.personalize.RelaxManager;
import com.book.mask.personalize.AppSettingsManager;
import com.book.mask.constant.Const;
import com.book.mask.constant.QuestionConst;
import com.book.mask.config.CustomAppManager;
import com.book.mask.config.CustomApp;
import com.book.mask.network.LatestVersionManager;
import com.book.mask.util.ArithmeticUtils;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.color.MaterialColors;

import java.util.List;

public class HomeNav extends Fragment implements
    AppCardAdapter.OnAppCardClickListener,
    AppCardAdapter.OnMonitorToggleListener,
    AppCardAdapter.OnEditClickListener,
    AppCardAdapter.OnDeleteClickListener {
    private static final String TAG = "HomeNav";

    private RelaxManager relaxManager;
    private AppSettingsManager appSettingsManager;
    private SettingsDialogManager settingsDialogManager;
    private CustomAppManager customAppManager;
    
    // APP卡片相关
    private RecyclerView rvAppCards;
    private AppCardAdapter appCardAdapter;
    private List<CustomApp> allApps; // 包含预定义APP和自定义APP
    private TextView permissionStatusView;
    private TextView permissionOptionalView;
    
    // 倒计时相关
    private Handler countdownHandler;
    private Runnable countdownRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        // 初始化设置管理器
        relaxManager = new RelaxManager(requireContext());
        appSettingsManager = new AppSettingsManager(requireContext());
        settingsDialogManager = new SettingsDialogManager(requireContext(), relaxManager);
        customAppManager = CustomAppManager.getInstance();

        // 设置页的云端最新版本获取
        new Thread(() -> {
            String response = LatestVersionManager.refreshLatestVersion();
            if (response != null) {
                Log.d(TAG, "版本接口响应: " + response);
            } else {
                Log.e(TAG, "最新版本获取失败");
            }
        }).start();

        // 初始化APP列表
        updateAppList();
        
        // 初始化APP卡片RecyclerView
        initAppCards(view);
        
        // 设置加号按钮点击事件
        setupAddButton(view);

        permissionStatusView = view.findViewById(R.id.tv_description);
        permissionOptionalView = view.findViewById(R.id.tv_optional_permissions);
        if (permissionStatusView != null) {
            permissionStatusView.setMovementMethod(LinkMovementMethod.getInstance());
            permissionStatusView.setHighlightColor(android.graphics.Color.TRANSPARENT);
        }
        if (permissionOptionalView != null) {
            permissionOptionalView.setMovementMethod(LinkMovementMethod.getInstance());
            permissionOptionalView.setHighlightColor(android.graphics.Color.TRANSPARENT);
        }
        refreshPermissionStatus();

        // 启动倒计时更新
        startCountdown();
        
        return view;
    }

    public void refreshPermissionStatus() {
        if (!isAdded() || permissionStatusView == null || permissionOptionalView == null) {
            return;
        }

        SpannableStringBuilder status = new SpannableStringBuilder("必需权限 3 个");
        status.setSpan(
                new StyleSpan(Typeface.BOLD),
                0,
                status.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        status.setSpan(
                new RelativeSizeSpan(1.0625f),
                0,
                status.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        appendPermissionStatus(
                status,
                "1.悬浮窗",
                PermissionStatus.canDrawOverlays(requireContext()),
                MainActivity::reviewOverlayPermission);
        appendPermissionStatus(
                status,
                "2.无障碍服务",
                PermissionStatus.isAccessibilityServiceEnabled(requireContext()),
                MainActivity::reviewAccessibilityPermission);
        appendBackgroundRunHint(status);
        permissionStatusView.setText(status);

        SpannableStringBuilder optional = new SpannableStringBuilder("可选权限");
        optional.setSpan(
                new StyleSpan(Typeface.BOLD),
                0,
                optional.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        optional.setSpan(
                new RelativeSizeSpan(1.0625f),
                0,
                optional.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        optional.append(" ");
        int clickStart = optional.length();
        optional.append("点这");
        int clickColor = MaterialColors.getColor(
                permissionOptionalView,
                com.google.android.material.R.attr.colorPrimary);
        optional.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                showOptionalPermissionsDialog();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint drawState) {
                drawState.setColor(clickColor);
                drawState.setUnderlineText(true);
            }
        }, clickStart, optional.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        optional.setSpan(
                new StyleSpan(Typeface.BOLD),
                clickStart,
                optional.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        permissionOptionalView.setText(optional);
    }

    private void showOptionalPermissionsDialog() {
        if (!isAdded()) {
            return;
        }
        android.app.AlertDialog[] dialogHolder = new android.app.AlertDialog[1];

        SpannableStringBuilder message = new SpannableStringBuilder();
        appendPlainPermissionHint(
                message,
                "开机自启",
                "建议加上，请自行设置");
        message.append('\n').append("忽略电池优化设置").append("：");
        int hintStart = message.length();
        message.append("点击开启");
        int hintColor = MaterialColors.getColor(
                permissionStatusView,
                com.google.android.material.R.attr.colorPrimary);
        message.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                if (dialogHolder[0] != null) {
                    dialogHolder[0].dismiss();
                }
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).reviewBackgroundPermission();
                }
            }

            @Override
            public void updateDrawState(@NonNull TextPaint drawState) {
                drawState.setColor(hintColor);
                drawState.setUnderlineText(false);
            }
        }, hintStart, message.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        // 去掉首行多余的换行
        if (message.length() > 0 && message.charAt(0) == '\n') {
            message.delete(0, 1);
        }

        TextView messageView = new TextView(requireContext());
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        messageView.setPadding(padding, padding, padding, 0);
        messageView.setTextSize(16);
        messageView.setLineSpacing(0, 1.3f);
        messageView.setText(message);
        messageView.setMovementMethod(LinkMovementMethod.getInstance());
        messageView.setHighlightColor(android.graphics.Color.TRANSPARENT);

        dialogHolder[0] = new android.app.AlertDialog.Builder(requireContext())
                .setTitle("可选权限")
                .setView(messageView)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void appendPlainPermissionHint(
            SpannableStringBuilder text,
            String label,
            String hint) {
        text.append('\n').append(label).append("：").append(hint);
    }

    private void appendBackgroundRunHint(SpannableStringBuilder text) {
        text.append('\n').append("3.允许后台活动").append("：");
        int clickStart = text.length();
        text.append("点这");
        int clickColor = MaterialColors.getColor(
                permissionStatusView,
                com.google.android.material.R.attr.colorPrimary);
        text.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                showBackgroundRunDialog();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint drawState) {
                drawState.setColor(clickColor);
                drawState.setUnderlineText(true);
            }
        }, clickStart, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(
                new StyleSpan(Typeface.BOLD),
                clickStart,
                text.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void showBackgroundRunDialog() {
        if (!isAdded()) {
            return;
        }
        String message = "1、该权限不同手机设置方式不同，请自行了解设置；\n"
                + "2、某些场景易丢失，或需重设它：重启手机、卸载重装本 APP。";

        TextView messageView = new TextView(requireContext());
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        messageView.setPadding(padding, padding, padding, 0);
        messageView.setTextSize(16);
        messageView.setLineSpacing(0, 1.3f);
        messageView.setText(message);

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("允许后台活动")
                .setView(messageView)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void appendPermissionStatus(
            SpannableStringBuilder text,
            String label,
            boolean enabled,
            PermissionReviewAction reviewAction) {
        text.append('\n').append(label).append("：");
        int statusStart = text.length();
        text.append(enabled ? "已开启" : "未开启，点这设置");
        int statusColor = enabled
                ? Color.parseColor("#2E7D32")
                : MaterialColors.getColor(
                        permissionStatusView,
                        com.google.android.material.R.attr.colorError);
        if (enabled) {
            text.setSpan(
                    new ForegroundColorSpan(statusColor),
                    statusStart,
                    text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            return;
        }
        text.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                if (getActivity() instanceof MainActivity) {
                    reviewAction.review((MainActivity) getActivity());
                }
            }

            @Override
            public void updateDrawState(@NonNull TextPaint drawState) {
                drawState.setColor(statusColor);
                drawState.setUnderlineText(false);
            }
        }, statusStart, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private interface PermissionReviewAction {
        void review(MainActivity activity);
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
        TextInputEditText etRelaxedLimitCount = dialogView.findViewById(R.id.et_relaxed_limit_count);
        TextInputLayout appNameLayout = dialogView.findViewById(R.id.layout_app_name);
        TextInputLayout packageNameLayout = dialogView.findViewById(R.id.layout_package_name);
        TextInputLayout targetWordLayout = dialogView.findViewById(R.id.layout_target_word);
        TextInputLayout relaxedLimitCountLayout =
                dialogView.findViewById(R.id.layout_relaxed_limit_count);
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
            String relaxedLimitCountStr = etRelaxedLimitCount.getText().toString().trim();

            appNameLayout.setError(null);
            packageNameLayout.setError(null);
            targetWordLayout.setError(null);
            relaxedLimitCountLayout.setError(null);

            // 验证输入
            if (appName.isEmpty()) {
                showInputError(appNameLayout, etAppName, "请输入 APP 名称");
                return;
            }
            
            if (packageName.isEmpty()) {
                showInputError(packageNameLayout, etPackageName, "请输入包名");
                return;
            }
            
            if (targetWord.isEmpty()) {
                showInputError(targetWordLayout, etTargetWord, "请输入屏蔽关键词");
                return;
            }
            
            int relaxedLimitCount = 1;
            if (!relaxedLimitCountStr.isEmpty()) {
                try {
                    relaxedLimitCount = Integer.parseInt(relaxedLimitCountStr);
                    if (relaxedLimitCount <= 0) {
                        showInputError(
                                relaxedLimitCountLayout,
                                etRelaxedLimitCount,
                                "宽松模式次数必须大于 0");
                        return;
                    }
                } catch (NumberFormatException e) {
                    showInputError(
                            relaxedLimitCountLayout,
                            etRelaxedLimitCount,
                            "请输入有效的数字");
                    return;
                }
            }
            
            // 保存新APP
            boolean success = customAppManager.addCustomApp(appName, packageName, targetWord, relaxedLimitCount);
            if (success) {
                dialog.dismiss();
                UiFeedback.show(requireContext(), "APP 添加成功");
                // 更新APP列表和卡片显示
                updateAppList();
                if (appCardAdapter != null) {
                    appCardAdapter.updateData(allApps);
                }
            } else {
                showInputError(
                        packageNameLayout,
                        etPackageName,
                        "包名无效或已存在，请检查后重试");
            }
        });
        
        dialog.show();
    }

    private void updateAppList() {
        // 获取所有APP（预定义 + 自定义）
        allApps = new java.util.ArrayList<>();
        
        // 添加所有APP（包括预定义和自定义）
        allApps.addAll(customAppManager.getAllApps());
    }

    private void initAppCards(View view) {
        rvAppCards = view.findViewById(R.id.rv_app_cards);
        android.util.Log.d("HomeFragment", "RecyclerView找到: " + (rvAppCards != null));
        
        if (rvAppCards != null) {
            // 使用GridLayoutManager实现两列布局
            androidx.recyclerview.widget.GridLayoutManager layoutManager = 
                new androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2);
            rvAppCards.setLayoutManager(layoutManager);
            appCardAdapter = new AppCardAdapter(allApps, relaxManager, this, this, this, this);
            rvAppCards.setAdapter(appCardAdapter);
        }
    }

    @Override
    public void onAppCardClick(CustomApp app) {
        // 显示APP设置弹窗
        showAppSettingsDialog(app);
    }

    @Override
    public void onMonitorToggle(CustomApp app, boolean isEnabled) {
        // 处理监测开关状态变化
        String packageName = getPackageName(app);
        if (packageName != null) {
            // 如果要关闭屏蔽，需要算术题验证
            if (!isEnabled) {
                showMathChallengeForMonitorToggle(app, packageName);
            } else {
                // 开启监测直接执行
                relaxManager.setAppMonitoringEnabled(packageName, isEnabled);
                android.util.Log.d("HomeFragment", "监测开关状态改变: " + packageName + " = " + isEnabled);
                
                // 显示提示
                String status = isEnabled ? "已开启监测" : "已关闭屏蔽";
                UiFeedback.show(requireContext(), status);
            }
        }
    }

    @Override
    public void onEditClick(CustomApp app) {
        // 处理编辑图标点击
        String appName = getAppName(app);
        UiFeedback.show(requireContext(), "编辑 " + appName);
        // TODO: 实现编辑功能
    }

    @Override
    public void onDeleteClick(CustomApp app) {
        // 仅手动添加的自定义APP允许删除，再校验一次防御
        if (app == null || !customAppManager.isCustomApp(app.getPackageName())) {
            return;
        }
        showMathChallengeForDelete(app);
    }

    private String getPackageName(CustomApp app) {
        return app.getPackageName();
    }

    private String getAppName(CustomApp app) {
        return app.getAppName();
    }

    private void showAppSettingsDialog(CustomApp app) {
        // 显示"单次解禁时长"弹窗
        showTimeSettingDialogForApp(app);
    }

    private void showTimeSettingDialogForApp(CustomApp app) {
        // 创建自定义布局的弹窗
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_time_setting, null);
        
        Button strictModeButton = dialogView.findViewById(R.id.btn_strict_mode);
        Button relaxedModeButton = dialogView.findViewById(R.id.btn_relaxed_mode);
        
        // 新的UI组件
        LinearLayout layoutRelaxedCountDisplay = dialogView.findViewById(R.id.layout_relaxed_count_display);
        LinearLayout layoutRelaxedCountEdit = dialogView.findViewById(R.id.layout_relaxed_count_edit);
        TextView tvRelaxedCountDisplay = dialogView.findViewById(R.id.tv_relaxed_count_display);
        ImageView ivEditRelaxedCount = dialogView.findViewById(R.id.iv_edit_relaxed_count);
        EditText etRelaxedLimitCount = dialogView.findViewById(R.id.et_relaxed_limit_count);
        TextView ivSaveRelaxedCount = dialogView.findViewById(R.id.iv_save_relaxed_count);
        
        // targetWord编辑组件
        LinearLayout layoutTargetWordDisplay = dialogView.findViewById(R.id.layout_target_word_display);
        LinearLayout layoutTargetWordEdit = dialogView.findViewById(R.id.layout_target_word_edit);
        TextView tvTargetWordDisplay = dialogView.findViewById(R.id.tv_target_word_display);
        ImageView ivEditTargetWord = dialogView.findViewById(R.id.iv_edit_target_word);
        EditText etTargetWord = dialogView.findViewById(R.id.et_target_word);
        TextView ivSaveTargetWord = dialogView.findViewById(R.id.iv_save_target_word);
        
        // 获取APP信息
        String appName;
        int relaxedLimitCount;
        String targetWord;
        
        appName = app.getAppName();
        relaxedLimitCount = app.getRelaxedLimitCount();
        targetWord = app.getTargetWord();
        
        // 设置显示文本的当前值
        tvRelaxedCountDisplay.setText(String.valueOf(relaxedLimitCount));
        tvTargetWordDisplay.setText(targetWord);
        
        // 检查宽松模式剩余次数
        int relaxedCount = relaxManager.getAppRelaxedCloseCount(app);
        int remainingCount = Math.max(0, relaxedLimitCount - relaxedCount);
        
        // 如果宽松模式次数用完，置灰按钮
        if (remainingCount <= 0) {
            relaxedModeButton.setEnabled(false);
            relaxedModeButton.setAlpha(0.5f);
            relaxedModeButton.setText("宽松模式 (次数已用完)");
        }
        
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
            .setTitle(appName)
            .setView(dialogView)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定", null)
            .create();
        
        // 编辑图标点击事件
        ivEditRelaxedCount.setOnClickListener(v -> {
            // 隐藏显示布局，显示编辑布局
            layoutRelaxedCountDisplay.setVisibility(View.GONE);
            layoutRelaxedCountEdit.setVisibility(View.VISIBLE);
            
            // 设置输入框的当前值
            etRelaxedLimitCount.setText(tvRelaxedCountDisplay.getText().toString());
            etRelaxedLimitCount.requestFocus();
            
            // 显示软键盘
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) 
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etRelaxedLimitCount, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        });
        
        // 保存图标点击事件
        ivSaveRelaxedCount.setOnClickListener(v -> {
            etRelaxedLimitCount.setError(null);
            String inputText = etRelaxedLimitCount.getText().toString().trim();
            if (inputText.isEmpty()) {
                showInputError(etRelaxedLimitCount, "请输入数字");
                return;
            }
            
            try {
                int newLimitCount = Integer.parseInt(inputText);
                if (newLimitCount < 1 || newLimitCount > 3) {
                    showInputError(etRelaxedLimitCount, "请输入 1-3 之间的数字");
                    return;
                }
                
                // 更新显示文本
                tvRelaxedCountDisplay.setText(String.valueOf(newLimitCount));
                
                // 更新APP的relaxedLimitCount
                app.setRelaxedLimitCount(newLimitCount);
                customAppManager.saveCustomAppsChanges(); // 保存到本地存储
                UiFeedback.show(requireContext(), "保存成功");
                
                // 更新APP列表显示
                updateAppCardsDisplay();
                
                // 隐藏编辑布局，显示正常布局
                layoutRelaxedCountEdit.setVisibility(View.GONE);
                layoutRelaxedCountDisplay.setVisibility(View.VISIBLE);
                
                // 隐藏软键盘
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) 
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(etRelaxedLimitCount.getWindowToken(), 0);
                }
                
            } catch (NumberFormatException e) {
                showInputError(etRelaxedLimitCount, "请输入有效的数字");
            }
        });
        
        // 输入框回车键保存
        etRelaxedLimitCount.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || 
                (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                ivSaveRelaxedCount.performClick();
                return true;
            }
            return false;
        });
        
        // targetWord编辑图标点击事件
        ivEditTargetWord.setOnClickListener(v -> {
            // 隐藏显示布局，显示编辑布局
            layoutTargetWordDisplay.setVisibility(View.GONE);
            layoutTargetWordEdit.setVisibility(View.VISIBLE);
            
            // 设置输入框的当前值
            etTargetWord.setText(tvTargetWordDisplay.getText().toString());
            etTargetWord.requestFocus();
            
            // 显示软键盘
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) 
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etTargetWord, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        });
        
        // targetWord保存图标点击事件
        ivSaveTargetWord.setOnClickListener(v -> {
            etTargetWord.setError(null);
            String inputText = etTargetWord.getText().toString().trim();
            if (inputText.isEmpty()) {
                showInputError(etTargetWord, "请输入关键词");
                return;
            }
            
            // 更新显示文本
            tvTargetWordDisplay.setText(inputText);
            
            // 更新APP的targetWord
            app.setTargetWord(inputText);
            
            // 统一使用updatePredefinedApp方法，它会自动判断是否是预定义APP
            customAppManager.updatePredefinedApp(app);
            
            UiFeedback.show(requireContext(), "关键词保存成功");
            
            // 更新APP列表显示
            updateAppCardsDisplay();
            
            // 隐藏编辑布局，显示正常布局
            layoutTargetWordEdit.setVisibility(View.GONE);
            layoutTargetWordDisplay.setVisibility(View.VISIBLE);
            
            // 隐藏软键盘
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) 
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(etTargetWord.getWindowToken(), 0);
            }
        });
        
        // targetWord输入框回车键保存
        etTargetWord.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || 
                (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                ivSaveTargetWord.performClick();
                return true;
            }
            return false;
        });
        
        // 设置按钮点击事件
        strictModeButton.setOnClickListener(v -> {
            dialog.dismiss();
            settingsDialogManager.showTimeSettingDialogForApp(app, true);
        });
        
        relaxedModeButton.setOnClickListener(v -> {
            // 只有在按钮可用时才执行
            if (relaxedModeButton.isEnabled()) {
                dialog.dismiss();
                settingsDialogManager.showTimeSettingDialogForApp(app, false);
            }
        });

        // 设置更换悬浮窗警示文字来源按钮点击事件
        Button changeFloatingTextSourceButton = dialogView.findViewById(R.id.btn_change_floating_text_source);
        changeFloatingTextSourceButton.setOnClickListener(v -> {
            showFloatingTextSourceDialog(app);
        });

        Button floatingSizeSettingsButton = dialogView.findViewById(R.id.btn_floating_size_settings);
        floatingSizeSettingsButton.setOnClickListener(v ->
                settingsDialogManager.showFloatingPositionDialogForApp(app));
        
        dialog.show();
    }

    /**
     * 显示悬浮窗警示文字来源选择对话框（单选列表风格）
     */
    private void showFloatingTextSourceDialog(CustomApp app) {
        String packageName = getPackageName(app);
        String currentSource = appSettingsManager.getAppHintSource(packageName);

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
                    UiFeedback.show(requireContext(), "已选择大模型作为悬浮窗警示文字来源");
                }
                dialog.dismiss();
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 显示自定义文字输入对话框
     */
    private void showCustomTextInputDialog(CustomApp app) {
        EditText input = new EditText(requireContext());
        input.setHint("请输入自定义警示文字（不超过100字）");
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
            .setTitle("输入自定义警示文字")
            .setView(input)
            .setPositiveButton("确定", null)
            .setNegativeButton("取消", null)
            .create();
        dialog.setOnShowListener(ignored ->
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                input.setError(null);
                String customText = input.getText().toString().trim();
                if (!customText.isEmpty()) {
                    recordFloatingTextSource(Const.CUSTOM_HINT_SOURCE, customText, app);
                    dialog.dismiss();
                    UiFeedback.show(requireContext(), "自定义警示文字设置成功");
                } else {
                    showInputError(input, "请输入内容");
                }
            }));
        dialog.show();
    }

    /**
     * 记录悬浮窗警示文字来源
     */
    private void recordFloatingTextSource(String source, String customText, CustomApp app) {
        // 获取当前选中的APP包名
        String currentAppPackage = getPackageName(app);
        if (currentAppPackage == null) {
            android.util.Log.e("HomeFragment", "无法获取当前选中的APP包名");
            return;
        }
        
        // 使用SettingsManager存储，为每个APP独立存储
        appSettingsManager.setAppHintSource(currentAppPackage, source);
        if (customText != null && !customText.isEmpty()) {
            appSettingsManager.setAppHintCustomText(currentAppPackage, customText);
        }
        android.util.Log.d("HomeFragment", "APP " + currentAppPackage + " 悬浮窗警示文字来源已保存: " + source + ", 自定义文字: " + customText);
    }
    
    /**
     * 记录悬浮窗警示文字来源（重载方法，用于只有source参数的情况）
     */
    private void recordFloatingTextSource(String source, CustomApp app) {
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
     * 显示"删除前验证"算术题弹窗，仅用于删除手动添加的自定义APP
     */
    private void showMathChallengeForDelete(CustomApp app) {
        final String packageName = app.getPackageName();

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_math_challenge, null);

        TextView headerText = dialogView.findViewById(R.id.tv_math_header);
        TextView questionText = dialogView.findViewById(R.id.tv_math_question);
        EditText answerEdit = dialogView.findViewById(R.id.et_math_answer);
        TextView resultText = dialogView.findViewById(R.id.tv_math_result);
        Button submitButton = dialogView.findViewById(R.id.btn_submit_answer);
        Button cancelButton = dialogView.findViewById(R.id.btn_cancel_close);

        if (headerText != null) {
            headerText.setText("🔢 回答算术题才能删除APP");
        }

        String question = ArithmeticUtils.customArithmetic(QuestionConst.ADD_LEN_CARD, QuestionConst.SUB_LEN_CARD, QuestionConst.MUL_FIRST_CARD, QuestionConst.MUL_SECOND_CARD);
        final int[] correctAnswer = {ArithmeticUtils.getMathAnswer(question)};
        questionText.setText(question);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
            .setTitle("删除前验证")
            .setView(dialogView)
            .setCancelable(false)
            .create();

        submitButton.setOnClickListener(v -> {
            String userAnswer = answerEdit.getText().toString().trim();
            if (userAnswer.isEmpty()) {
                UiFeedback.showTemporaryText(resultText, "⚠️ 请输入答案", 0xFFFF5722);
                return;
            }

            try {
                int answer = Integer.parseInt(userAnswer);
                if (answer == correctAnswer[0]) {
                    UiFeedback.showTemporaryText(
                            resultText,
                            "✅ 答案正确！",
                            requireContext().getColor(android.R.color.holo_green_light));

                    new Handler().postDelayed(() -> {
                        dialog.dismiss();
                        // 删除APP并清理相关持久化设置
                        boolean removed = customAppManager.removeCustomApp(packageName);
                        if (removed) {
                            android.util.Log.d(TAG, "已删除自定义APP: " + packageName);
                            updateAppCardsDisplay();
                            showDeleteUndo(app);
                        } else {
                            UiFeedback.showError(requireContext(), "删除失败");
                        }
                    }, Const.TRANSIENT_FEEDBACK_DURATION_MS);

                } else {
                    UiFeedback.showTemporaryText(
                            resultText,
                            "❌ 答案错误，切到下一题",
                            requireContext().getColor(android.R.color.holo_red_light));

                    answerEdit.setText("");

                    new Handler().postDelayed(() -> {
                        String newQuestion = ArithmeticUtils.customArithmetic(
                                QuestionConst.ADD_LEN_MAX, QuestionConst.ADD_LEN_MAX, QuestionConst.MUL_LEN_MAX, QuestionConst.MUL_LEN_MAX);
                        correctAnswer[0] = ArithmeticUtils.getMathAnswer(newQuestion);
                        questionText.setText(newQuestion);
                        answerEdit.setText("");
                        resultText.setVisibility(View.GONE);
                    }, Const.TRANSIENT_FEEDBACK_DURATION_MS);
                }
            } catch (NumberFormatException e) {
                UiFeedback.showTemporaryText(resultText, "⚠️ 请输入有效数字", 0xFFFF5722);
            }
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        answerEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                submitButton.performClick();
                return true;
            }
            return false;
        });

        dialog.show();

        answerEdit.requestFocus();
    }

    /**
     * 显示算术题验证弹窗用于关闭屏蔽
     */
    private void showMathChallengeForMonitorToggle(CustomApp app, String packageName) {
        if(CustomAppManager.WECHAT_PACKAGE.equals(packageName)){
            relaxManager.setAppMonitoringEnabled(packageName, false);
            updateAppCardsDisplay();
            return;
        }

        // 创建算术题验证弹窗
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_math_challenge, null);
        
        TextView questionText = dialogView.findViewById(R.id.tv_math_question);
        EditText answerEdit = dialogView.findViewById(R.id.et_math_answer);
        TextView resultText = dialogView.findViewById(R.id.tv_math_result);
        Button submitButton = dialogView.findViewById(R.id.btn_submit_answer);
        Button cancelButton = dialogView.findViewById(R.id.btn_cancel_close);
        
        // 生成算术题
        String question = ArithmeticUtils.customArithmetic(QuestionConst.ADD_LEN_CARD, QuestionConst.SUB_LEN_CARD, QuestionConst.MUL_FIRST_CARD, QuestionConst.MUL_SECOND_CARD);
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
                UiFeedback.showTemporaryText(resultText, "⚠️ 请输入答案", 0xFFFF5722);
                return;
            }
            
            try {
                int answer = Integer.parseInt(userAnswer);
                if (answer == correctAnswer[0]) {
                    // 答案正确，关闭屏蔽
                    UiFeedback.showTemporaryText(
                            resultText,
                            "✅ 答案正确！",
                            requireContext().getColor(android.R.color.holo_green_light));
                    
                    // 延迟关闭弹窗并执行关闭屏蔽
                    new Handler().postDelayed(() -> {
                        dialog.dismiss();
                        relaxManager.setAppMonitoringEnabled(packageName, false);
                        android.util.Log.d("HomeFragment", "算术题验证通过，关闭屏蔽: " + packageName);
                        UiFeedback.show(requireContext(), "已关闭屏蔽");
                        
                        // 更新APP列表显示
                        updateAppCardsDisplay();
                    }, Const.TRANSIENT_FEEDBACK_DURATION_MS);
                    
                } else {
                    // 答案错误
                    UiFeedback.showTemporaryText(
                            resultText,
                            "❌ 答案错误，切到下一题",
                            requireContext().getColor(android.R.color.holo_red_light));
                    
                    // 清空输入框
                    answerEdit.setText("");
                    
                    // 3秒后生成新题目
                    new Handler().postDelayed(() -> {
                        String newQuestion = ArithmeticUtils.customArithmetic(
                                QuestionConst.ADD_LEN_MAX, QuestionConst.ADD_LEN_MAX, QuestionConst.MUL_LEN_MAX, QuestionConst.MUL_LEN_MAX);
                        correctAnswer[0] = ArithmeticUtils.getMathAnswer(newQuestion);
                        questionText.setText(newQuestion);
                        answerEdit.setText("");
                        resultText.setVisibility(View.GONE);
                    }, Const.TRANSIENT_FEEDBACK_DURATION_MS);
                }
            } catch (NumberFormatException e) {
                UiFeedback.showTemporaryText(resultText, "⚠️ 请输入有效数字", 0xFFFF5722);
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

    private void showDeleteUndo(CustomApp deletedApp) {
        boolean[] restored = {false};
        Snackbar snackbar = UiFeedback.make(requireView(), "已删除")
                .setAction("撤销", v -> {
                    restored[0] = customAppManager.addCustomApp(
                            deletedApp.getAppName(),
                            deletedApp.getPackageName(),
                            deletedApp.getTargetWord(),
                            deletedApp.getRelaxedLimitCount());
                    if (restored[0]) {
                        updateAppCardsDisplay();
                    }
                });
        snackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                if (!restored[0]) {
                    relaxManager.clearAppSettings(deletedApp.getPackageName());
                    appSettingsManager.clearAppSettings(deletedApp.getPackageName());
                }
            }
        });
        snackbar.show();
    }

    private void showInputError(
            TextInputLayout layout,
            TextInputEditText input,
            String message) {
        UiFeedback.showInputError(layout, input, message);
    }

    private void showInputError(EditText input, String message) {
        UiFeedback.showInputError(input, message);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshPermissionStatus();
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

    @Override
    public void onDestroyView() {
        permissionStatusView = null;
        permissionOptionalView = null;
        super.onDestroyView();
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
