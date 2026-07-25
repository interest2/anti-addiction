package com.book.mask.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.book.mask.config.ChallengeType;
import com.book.mask.constant.Const;
import com.book.mask.constant.QuestionConst;
import com.book.mask.personalize.RelaxManager;
import com.book.mask.personalize.AppSettingsManager;
import com.book.mask.personalize.ChallengeSettingsManager;
import com.book.mask.personalize.LeisureTimeManager;
import com.book.mask.config.CustomApp;
import com.book.mask.floating.FloatService;
import com.book.mask.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * 设置对话框管理器
 * 负责处理所有设置相关的弹窗和文字内容逻辑
 */
public class SettingsDialogManager {
    private static final long LEISURE_STATE_REFRESH_INTERVAL_MS = 200;
    
    private final Context context;
    private final RelaxManager relaxManager;
    private final AppSettingsManager appSettingsManager;
    private final ChallengeSettingsManager challengeSettingsManager;
    private final LeisureTimeManager leisureTimeManager;

    private static String[] appOptions;
    private static CustomApp[] apps; // 改为CustomApp类型

    public SettingsDialogManager(Context context, RelaxManager relaxManager) {
        this.context = context;
        this.relaxManager = relaxManager;
        this.appSettingsManager = new AppSettingsManager(context);
        this.challengeSettingsManager = new ChallengeSettingsManager(context);
        this.leisureTimeManager = new LeisureTimeManager(context);
        updateAppOptions(); // 动态更新APP选项
    }

    /**
     * 更新APP选项列表
     */
    private void updateAppOptions() {
        // 获取所有APP（预定义+自定义）
        java.util.List<CustomApp> allApps = CustomApp.getAllApps();
        apps = allApps.toArray(new CustomApp[0]);
        
        appOptions = new String[apps.length];
        for (int i = 0; i < apps.length; i++) {
            appOptions[i] = apps[i].getAppName();
        }
    }

    /**
     * 为指定APP显示时间设置对话框 - 支持自定义APP
     */
    public void showTimeSettingDialogForApp(CustomApp app, boolean isStrict) {
        final int[] intervals = isStrict ? 
            RelaxManager.getStrictIntervals() :
            RelaxManager.getRelaxedIntervals();
        
        String[] intervalOptions = new String[intervals.length];
        for (int i = 0; i < intervals.length; i++) {
            intervalOptions[i] = RelaxManager.getIntervalDisplayText(intervals[i]);
        }

        // 获取指定APP的当前设置
        int currentInterval = relaxManager.getAppInterval(app);
        
        int checkedItem = -1;
        for (int i = 0; i < intervals.length; i++) {
            if (intervals[i] == currentInterval) {
                checkedItem = i;
                break;
            }
        }
        
        String dialogTitle = isStrict ? "严格模式" : "宽松模式";
        String packageName = app.getPackageName();
        String fullTitle = dialogTitle + " - " + app.getAppName();

        android.util.Log.d("SettingsDialog", "显示时间设置对话框: " + fullTitle);
        android.util.Log.d("SettingsDialog", "APP " + packageName + " 当前时间间隔: " + currentInterval + "秒");

        new android.app.AlertDialog.Builder(context)
            .setTitle(fullTitle)
            .setSingleChoiceItems(intervalOptions, checkedItem, (dialog, which) -> {
                int selectedInterval = intervals[which];
                
                // 为指定APP设置时间间隔
                relaxManager.setAppInterval(app, selectedInterval);
                android.util.Log.d("SettingsDialog", "设置APP " + packageName + " 时间间隔为: " + selectedInterval + "秒");
                
                // 验证设置是否成功
                int verifyInterval = relaxManager.getAppInterval(app);
                android.util.Log.d("SettingsDialog", "验证APP " + packageName + " 实际保存的时间间隔: " + verifyInterval + "秒");
                
                // 检查是否是宽松模式
                boolean isRelaxedMode = relaxManager.isAppRelaxedMode(app);
                android.util.Log.d("SettingsDialog", "APP " + packageName + " 是否宽松模式: " + isRelaxedMode);
                
                // 检查宽松模式的关闭次数
                int relaxedCount = relaxManager.getAppRelaxedCloseCount(app);
                android.util.Log.d("SettingsDialog", "APP " + packageName + " 今日宽松模式关闭次数: " + relaxedCount);
                
                // 通知服务配置已更改
                FloatService.notifyIntervalChanged();
                       
                // 显示提示信息
                showIntervalExplanation(selectedInterval);
                
                dialog.dismiss();
               })
               .setNegativeButton("取消", (dialog, which) -> {
                   // 用户取消时，不调用回调，因为没有设置被更改
                   // 移除这里的 onSettingChanged.run() 调用
               })
               .show();
    }
    
    /**
     * 显示时间间隔说明
     */
    public void showIntervalExplanation(int interval) {
        StringBuilder explanation = new StringBuilder();
        explanation.append("新的时长，将在下次成功关闭悬浮窗后生效");

        new android.app.AlertDialog.Builder(context)
                .setTitle("解禁时长说明")
               .setMessage(explanation.toString())
                .setPositiveButton("好的", null)
                .show();
    }

    /**
     * 显示休闲时刻设置对话框。
     */
    public void showLeisureTimeDialog() {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_leisure_time, null);
        TextView description = dialogView.findViewById(R.id.tv_leisure_description);
        LeisureModeViews relaxedViews = new LeisureModeViews(
                LeisureTimeManager.LeisureMode.RELAXED,
                dialogView.findViewById(R.id.layout_relaxed_leisure_duration),
                dialogView.findViewById(R.id.layout_relaxed_leisure_count),
                dialogView.findViewById(R.id.et_relaxed_leisure_duration),
                dialogView.findViewById(R.id.et_relaxed_leisure_count),
                dialogView.findViewById(R.id.btn_start_relaxed_leisure_time),
                dialogView.findViewById(R.id.tv_relaxed_leisure_remaining_count));
        LeisureModeViews strictViews = new LeisureModeViews(
                LeisureTimeManager.LeisureMode.STRICT,
                dialogView.findViewById(R.id.layout_strict_leisure_duration),
                dialogView.findViewById(R.id.layout_strict_leisure_count),
                dialogView.findViewById(R.id.et_strict_leisure_duration),
                dialogView.findViewById(R.id.et_strict_leisure_count),
                dialogView.findViewById(R.id.btn_start_strict_leisure_time),
                dialogView.findViewById(R.id.tv_strict_leisure_remaining_count));

        description.setText(
                "开启后，首个关闭悬浮窗的 APP 无需答题即可解禁");
        setupLeisureModeInputs(relaxedViews);
        setupLeisureModeInputs(strictViews);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
                .setTitle("休闲时刻（免答题）")
                .setView(dialogView)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();

        Handler stateHandler = new Handler(Looper.getMainLooper());
        boolean[] leisureRefreshEnabled = {false};
        Runnable[] refreshLeisureState = new Runnable[1];
        refreshLeisureState[0] = () -> {
            stateHandler.removeCallbacks(refreshLeisureState[0]);
            if (!leisureRefreshEnabled[0]) {
                return;
            }
            updateLeisureStartState(relaxedViews);
            updateLeisureStartState(strictViews);
            stateHandler.postDelayed(
                    refreshLeisureState[0], LEISURE_STATE_REFRESH_INTERVAL_MS);
        };
        Runnable startLeisureRefresh = () -> {
            leisureRefreshEnabled[0] = true;
            refreshLeisureState[0].run();
        };
        Runnable stopLeisureRefresh = () -> {
            leisureRefreshEnabled[0] = false;
            stateHandler.removeCallbacks(refreshLeisureState[0]);
        };
        TextWatcher countWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshLeisureState[0].run();
            }
        };
        relaxedViews.countInput.addTextChangedListener(countWatcher);
        strictViews.countInput.addTextChangedListener(countWatcher);

        LifecycleOwner lifecycleOwner = context instanceof LifecycleOwner
                ? (LifecycleOwner) context
                : null;
        LifecycleEventObserver[] lifecycleObserver = new LifecycleEventObserver[1];
        lifecycleObserver[0] = (source, event) -> {
            if (event == Lifecycle.Event.ON_START && dialog.isShowing()) {
                startLeisureRefresh.run();
            } else if (event == Lifecycle.Event.ON_STOP) {
                stopLeisureRefresh.run();
            } else if (event == Lifecycle.Event.ON_DESTROY) {
                stopLeisureRefresh.run();
                source.getLifecycle().removeObserver(lifecycleObserver[0]);
            }
        };
        if (lifecycleOwner != null) {
            lifecycleOwner.getLifecycle().addObserver(lifecycleObserver[0]);
        }

        dialog.setOnShowListener(ignored -> {
            startLeisureRefresh.run();

            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!saveLeisureTimeSettings(relaxedViews, strictViews)) {
                    return;
                }
                clearLeisureInputFocus(dialogView, relaxedViews, strictViews);
                UiFeedback.show(dialogView, "保存成功");
            });

            setLeisureStartListener(
                    relaxedViews, dialogView, refreshLeisureState[0]);
            setLeisureStartListener(
                    strictViews, dialogView, refreshLeisureState[0]);
        });
        dialog.setOnDismissListener(ignored -> {
            stopLeisureRefresh.run();
            if (lifecycleOwner != null) {
                lifecycleOwner.getLifecycle().removeObserver(lifecycleObserver[0]);
            }
        });
        dialog.show();
    }

    private void clearLeisureInputFocus(
            View dialogView, LeisureModeViews... modeViews) {
        for (LeisureModeViews views : modeViews) {
            views.durationInput.clearFocus();
            views.countInput.clearFocus();
        }
        dialogView.requestFocus();

        InputMethodManager inputMethodManager = (InputMethodManager)
                context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(dialogView.getWindowToken(), 0);
        }
    }

    private void setupLeisureModeInputs(LeisureModeViews views) {
        String durationRange = LeisureTimeManager.getLeisureDurationRangeText(views.mode)
                .replace('-', '~');
        String countRange = LeisureTimeManager.getLeisureDailyCountRangeText(views.mode)
                .replace('-', '~');
        views.durationInput.setHint(durationRange);
        views.countInput.setHint(countRange);
        centerLeisureInputSuffix(views.durationLayout);
        centerLeisureInputSuffix(views.countLayout);
        views.durationInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(
                String.valueOf(
                        LeisureTimeManager.getLeisureDurationMaxMinutes(views.mode)).length())});
        views.countInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(
                String.valueOf(LeisureTimeManager.getLeisureDailyCountMax(views.mode)).length())});
        views.durationInput.setText(String.valueOf(
                leisureTimeManager.getLeisureDurationMinutes(views.mode)));
        views.countInput.setText(String.valueOf(
                leisureTimeManager.getLeisureDailyCount(views.mode)));
    }

    private void centerLeisureInputSuffix(TextInputLayout inputLayout) {
        TextView suffixText = inputLayout.findViewById(
                com.google.android.material.R.id.textinput_suffix_text);
        suffixText.setGravity(Gravity.CENTER_VERTICAL);
        if (suffixText.getParent() instanceof LinearLayout) {
            ((LinearLayout) suffixText.getParent()).setGravity(Gravity.CENTER_VERTICAL);
        }
    }

    private void setLeisureStartListener(
            LeisureModeViews targetViews,
            View dialogView,
            Runnable refreshLeisureState) {
        targetViews.startButton.setOnClickListener(v -> {
            if (leisureTimeManager.isLeisureTimeArmed(targetViews.mode)) {
                leisureTimeManager.cancelPendingLeisureTime(targetViews.mode);
                refreshLeisureState.run();
                return;
            }
            if (!saveLeisureTimeSettings(targetViews)) {
                refreshLeisureState.run();
                return;
            }
            if (leisureTimeManager.tryStartLeisureTime(targetViews.mode)) {
                UiFeedback.show(
                        dialogView,
                        "已开启" + getLeisureModeName(targetViews.mode)
                                + "，首个关闭悬浮窗的 APP 将免答题解禁");
            } else if (leisureTimeManager.isLeisureTimeActive(targetViews.mode)) {
                UiFeedback.show(dialogView, getLeisureModeName(targetViews.mode) + "正在进行中");
            } else {
                UiFeedback.showError(
                        dialogView,
                        getLeisureModeName(targetViews.mode) + "今日次数已用完");
            }
            refreshLeisureState.run();
        });
    }

    private boolean saveLeisureTimeSettings(LeisureModeViews... modeViews) {
        boolean valid = true;

        for (LeisureModeViews views : modeViews) {
            views.durationLayout.setError(null);
            views.countLayout.setError(null);

            Integer durationMinutes = parseInteger(views.durationInput);
            Integer dailyCount = parseInteger(views.countInput);
            if (durationMinutes == null
                    || !LeisureTimeManager.isValidLeisureDurationMinutes(
                    views.mode, durationMinutes)) {
                UiFeedback.showInputError(
                        views.durationLayout,
                        views.durationInput,
                        "请输入" + LeisureTimeManager.getLeisureDurationRangeText(views.mode)
                                + "分钟");
                valid = false;
            }
            if (dailyCount == null
                    || !LeisureTimeManager.isValidLeisureDailyCount(views.mode, dailyCount)) {
                UiFeedback.showInputError(
                        views.countLayout,
                        views.countInput,
                        "请输入" + LeisureTimeManager.getLeisureDailyCountRangeText(views.mode)
                                + "次");
                valid = false;
            }
        }
        if (!valid) {
            return false;
        }

        for (LeisureModeViews views : modeViews) {
            leisureTimeManager.setLeisureTimeSettings(
                    views.mode,
                    parseInteger(views.durationInput),
                    parseInteger(views.countInput));
        }
        return true;
    }

    private void updateLeisureStartState(LeisureModeViews views) {
        Integer configuredDailyCount = parseInteger(views.countInput);
        Integer remainingCount = configuredDailyCount == null
                || !LeisureTimeManager.isValidLeisureDailyCount(
                views.mode, configuredDailyCount)
                ? null
                : Math.max(
                        0,
                        configuredDailyCount
                                - leisureTimeManager.getLeisureUsedCountToday(views.mode));
        views.remainingCountText.setText(remainingCount == null
                ? "今日剩余 -- 次"
                : "今日剩余 " + remainingCount + " 次");

        if (leisureTimeManager.isLeisureTimeActive(views.mode)) {
            views.startButton.setEnabled(false);
            views.startButton.setTextOn("进行中");
            views.startButton.setChecked(true);
            return;
        }

        if (leisureTimeManager.isLeisureTimeArmed(views.mode)) {
            views.startButton.setEnabled(true);
            views.startButton.setTextOn("");
            views.startButton.setChecked(true);
            return;
        }

        // 次数用完时不禁用开关：开关的禁用态与关闭态外观一致，禁用后点击毫无反馈，
        // 改为放行点击、由点击回调提示"今日次数已用完"
        views.startButton.setEnabled(true);
        views.startButton.setTextOff("");
        views.startButton.setChecked(false);
    }

    private String getLeisureModeName(LeisureTimeManager.LeisureMode mode) {
        return mode == LeisureTimeManager.LeisureMode.STRICT ? "严格模式" : "宽松模式";
    }

    private static class LeisureModeViews {
        private final LeisureTimeManager.LeisureMode mode;
        private final TextInputLayout durationLayout;
        private final TextInputLayout countLayout;
        private final TextInputEditText durationInput;
        private final TextInputEditText countInput;
        private final ToggleButton startButton;
        private final TextView remainingCountText;

        private LeisureModeViews(
                LeisureTimeManager.LeisureMode mode,
                TextInputLayout durationLayout,
                TextInputLayout countLayout,
                TextInputEditText durationInput,
                TextInputEditText countInput,
                ToggleButton startButton,
                TextView remainingCountText) {
            this.mode = mode;
            this.durationLayout = durationLayout;
            this.countLayout = countLayout;
            this.durationInput = durationInput;
            this.countInput = countInput;
            this.startButton = startButton;
            this.remainingCountText = remainingCountText;
        }
    }

    private Integer parseInteger(EditText input) {
        String value = input.getText().toString().trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 显示标签设置对话框
     */
    public void showTagSettingDialog(Runnable onSettingChanged) {
        final String[] predefinedTags = AppSettingsManager.getAvailableTags();
        final String customTagOption = "自定义...";

        // 将预设标签和"自定义"选项合并
        final String[] dialogOptions = new String[predefinedTags.length + 1];
        System.arraycopy(predefinedTags, 0, dialogOptions, 0, predefinedTags.length);
        dialogOptions[dialogOptions.length - 1] = customTagOption;

        new android.app.AlertDialog.Builder(context)
                .setTitle("选择或自定义目标")
                .setItems(dialogOptions, (dialog, which) -> {
                    if (which == predefinedTags.length) {
                        // 点击了"自定义..."
                        showCustomTagInputDialog(onSettingChanged);
                    } else {
                        // 点击了预设标签
                        String selectedTag = dialogOptions[which];
                        appSettingsManager.setMotivationTag(selectedTag);
                        UiFeedback.show(context, "已设置为：" + selectedTag);
                    }
                    if (onSettingChanged != null) onSettingChanged.run();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 显示自定义标签输入对话框
     */
    public void showCustomTagInputDialog(Runnable onSettingChanged) {
        final EditText input = new EditText(context);
        // 设置输入长度限制为8
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(10) });
        input.setHint("不超过 10 个字");

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
            .setTitle("定个目标")
            .setView(input)
            .setPositiveButton("确定", null)
            .setNegativeButton("取消", null)
            .create();
        dialog.setOnShowListener(ignored ->
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                input.setError(null);
                String customTag = input.getText().toString().trim();
                if (customTag.isEmpty()) {
                    showInputError(input, "目标不能为空");
                    return;
                }
                appSettingsManager.setMotivationTag(customTag);
                dialog.dismiss();
                UiFeedback.show(context, "已设置为：" + customTag);
                if (onSettingChanged != null) onSettingChanged.run();
            }));
        dialog.show();
    }
    
    /**
     * 显示目标完成日期选择对话框
     */
    public void showTargetDateSettingDialog(Runnable onSettingChanged) {
        // 获取当前设置的日期
        String currentDate = appSettingsManager.getTargetCompletionDate();
        
        // 创建日期选择器
        android.app.DatePickerDialog datePickerDialog = new android.app.DatePickerDialog(
            context,
            (view, year, month, dayOfMonth) -> {
                // 格式化日期为 yyyy-MM-dd 格式
                String selectedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth);
                appSettingsManager.setTargetCompletionDate(selectedDate);
                UiFeedback.show(context, "目标完成日期已设置为：" + selectedDate);
                
                // 通知设置页面更新按钮文本（如果当前在设置页面）
                if (context instanceof MainActivity) {
                    if (onSettingChanged != null) onSettingChanged.run();
                }
            },
            java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
            java.util.Calendar.getInstance().get(java.util.Calendar.MONTH),
            java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
        );
        
        // 设置最小日期为今天
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis());
        
        // 如果当前有设置日期，解析并设置为当前选择
        if (!Const.TARGET_TO_BE_SET.equals(currentDate) && !currentDate.isEmpty()) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
                java.util.Date date = sdf.parse(currentDate);
                if (date != null) {
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.setTime(date);
                    datePickerDialog.updateDate(cal.get(java.util.Calendar.YEAR), 
                                               cal.get(java.util.Calendar.MONTH), 
                                               cal.get(java.util.Calendar.DAY_OF_MONTH));
                }
            } catch (Exception e) {
                android.util.Log.w("SettingsDialogManager", "解析当前日期失败", e);
            }
        }
        
        datePickerDialog.setTitle("选择目标完成日期");
        datePickerDialog.show();
    }
    
    /**
     * 显示悬浮窗位置设置对话框
     */
    public void showFloatingPositionDialog() {
        showFloatingPositionDialog(
                "调整悬浮窗边缘位置\n（全局默认）",
                "说明：此处是各 APP 的默认设置，针对某个的可在首页对应卡片内修改",
                appSettingsManager.getFloatingTopOffset(),
                appSettingsManager.getFloatingBottomOffset(),
                (topOffset, bottomOffset) -> {
                    appSettingsManager.setFloatingTopOffset(topOffset);
                    appSettingsManager.setFloatingBottomOffset(bottomOffset);
                });
    }

    /**
     * 为指定APP显示悬浮窗位置设置对话框。
     */
    public void showFloatingPositionDialogForApp(CustomApp app) {
        if (app == null || app.getPackageName() == null || app.getPackageName().isEmpty()) {
            UiFeedback.showError(context, "无法获取当前 APP");
            return;
        }

        String packageName = app.getPackageName();
        showFloatingPositionDialog(
                "调整悬浮窗边缘位置\n（" + app.getAppName() + "）",
                "说明：此处设置仅对当前 APP 生效",
                appSettingsManager.getAppFloatingTopOffset(packageName),
                appSettingsManager.getAppFloatingBottomOffset(packageName),
                (topOffset, bottomOffset) -> {
                    appSettingsManager.setAppFloatingTopOffset(packageName, topOffset);
                    appSettingsManager.setAppFloatingBottomOffset(packageName, bottomOffset);
                });
    }

    private void showFloatingPositionDialog(String title, String hint,
                                            int currentTopOffset, int currentBottomOffset,
                                            FloatingPositionSaver saver) {
        // 创建自定义布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        // 上边缘设置
        android.widget.TextView topLabel = new android.widget.TextView(context);
        topLabel.setText("上边缘距离顶部距离（像素）:");
        topLabel.setTextSize(16);
        layout.addView(topLabel);

        final android.widget.EditText topEdit = new android.widget.EditText(context);
        topEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        topEdit.setText(String.valueOf(currentTopOffset));
        topEdit.setHint("默认: 130");
        layout.addView(topEdit);

        // 添加间距
        android.view.View spacer1 = new android.view.View(context);
        spacer1.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 30));
        layout.addView(spacer1);

        // 下边缘设置
        android.widget.TextView bottomLabel = new android.widget.TextView(context);
        bottomLabel.setText("下边缘距离底部距离（像素）:");
        bottomLabel.setTextSize(16);
        layout.addView(bottomLabel);

        final android.widget.EditText bottomEdit = new android.widget.EditText(context);
        bottomEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        bottomEdit.setText(String.valueOf(currentBottomOffset));
        bottomEdit.setHint("默认: 230");
        layout.addView(bottomEdit);

        // 添加说明文字
        android.view.View spacer2 = new android.view.View(context);
        spacer2.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 20));
        layout.addView(spacer2);

        android.widget.TextView hintText = new android.widget.TextView(context);
        hintText.setText(hint);
        hintText.setTextSize(14);
        hintText.setTextColor(0xFF666666);
        layout.addView(hintText);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton("确定", null)
            .setNegativeButton("取消", null)
            .create();
        dialog.setOnShowListener(ignored ->
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                topEdit.setError(null);
                bottomEdit.setError(null);

                Integer topOffset = parseInteger(topEdit);
                Integer bottomOffset = parseInteger(bottomEdit);
                if (topOffset == null) {
                    showInputError(topEdit, "请输入有效的上边缘距离");
                    return;
                }
                if (bottomOffset == null) {
                    showInputError(bottomEdit, "请输入有效的下边缘距离");
                    return;
                }
                if (topOffset < 0 || topOffset > 300) {
                    showInputError(topEdit, "请输入 0-300 之间的数字");
                    return;
                }
                if (bottomOffset < 0 || bottomOffset > 400) {
                    showInputError(bottomEdit, "请输入 0-400 之间的数字");
                    return;
                }

                saver.save(topOffset, bottomOffset);
                dialog.dismiss();
                UiFeedback.show(context, "悬浮窗位置已更新");
            }));
        dialog.show();
    }

    private interface FloatingPositionSaver {
        void save(int topOffset, int bottomOffset);
    }
    
    /**
     * 更新日期按钮文本
     */
    public void updateDateButtonText(Button dateButton) {
        if (dateButton != null) {
            String currentDate = appSettingsManager.getTargetCompletionDate();
            dateButton.setText("完成日期: " + currentDate);
        }
    }

    /**
     * 显示算术题难度设置对话框
     */
    public void showMathDifficultyDialog() {
        ChallengeType[] challengeTypes =
                ChallengeType.settingsOptions(QuestionConst.ENGLISH_READING_ENABLED);
        String[] difficultyOptions = new String[challengeTypes.length];
        ChallengeType currentType = challengeSettingsManager.getChallengeType();
        int checkedItem = 0;
        for (int i = 0; i < challengeTypes.length; i++) {
            difficultyOptions[i] = challengeTypes[i].getDisplayName();
            if (challengeTypes[i] == currentType) {
                checkedItem = i;
            }
        }

        new android.app.AlertDialog.Builder(context)
            .setTitle("关闭悬浮窗所需答题的类型")
            .setSingleChoiceItems(difficultyOptions, checkedItem, (dialog, which) -> {
                ChallengeType selectedType = challengeTypes[which];
                challengeSettingsManager.setChallengeType(selectedType);

                if (selectedType == ChallengeType.ARITHMETIC) {
                    showArithmeticDifficultyDialog();
                } else if (selectedType == ChallengeType.ENGLISH_READING) {
                    showEnglishReadingLengthDialog();
                } else {
                    UiFeedback.show(context, "已设置为" + selectedType.getDisplayName());
                }
                dialog.dismiss();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 显示英文阅读字数设置对话框（次级弹窗）
     */
    private void showEnglishReadingLengthDialog() {
        // 创建输入框
        android.widget.EditText input = new android.widget.EditText(context);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("请输入阅读字数");
        int currentLength = challengeSettingsManager.getEnglishReadingLength();
        input.setText(String.valueOf(currentLength));
        input.setSelection(input.getText().length()); // 选中所有文本，方便修改

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
            .setTitle("设置阅读字数")
            .setMessage("阅读字数范围：200-1000")
            .setView(input)
            .setPositiveButton("确定", null)
            .setNegativeButton("取消", null)
            .create();
        dialog.setOnShowListener(ignored ->
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                input.setError(null);
                Integer length = parseInteger(input);
                if (length == null) {
                    showInputError(input, "请输入有效数字");
                    return;
                }
                if (length < QuestionConst.ENGLISH_READING_LENGTH_MIN
                        || length > QuestionConst.ENGLISH_READING_LENGTH_MAX) {
                    showInputError(
                            input,
                            "请输入 " + QuestionConst.ENGLISH_READING_LENGTH_MIN
                                    + "-" + QuestionConst.ENGLISH_READING_LENGTH_MAX + " 之间的数字");
                    return;
                }

                challengeSettingsManager.setEnglishReadingLength(length);
                dialog.dismiss();
                UiFeedback.show(context, "已设置阅读字数为：" + length);
            }));
        dialog.show();
    }

    /**
     * 显示纯算术题难度设置对话框（次级弹窗）
     */
    private void showArithmeticDifficultyDialog() {
        String[] difficultyOptions = {"默认难度", "自定义难度"};
        String currentMode = challengeSettingsManager.getMathDifficultyMode();
        int checkedItem = "custom".equals(currentMode) ? 1 : 0;

        new android.app.AlertDialog.Builder(context)
            .setTitle("算术题难度设置")
            .setSingleChoiceItems(difficultyOptions, checkedItem, (dialog, which) -> {
                if (which == 0) {
                    // 选择默认难度
                    challengeSettingsManager.setMathDifficultyMode("default");
                    dialog.dismiss();
                    UiFeedback.show(context, "已设置为默认难度");
                } else if (which == 1) {
                    // 选择自定义难度
                    challengeSettingsManager.setMathDifficultyMode("custom");
                    dialog.dismiss();
                    showCustomMathDifficultyDialog();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 显示自定义算术题难度设置对话框
     */
    private void showCustomMathDifficultyDialog() {
        // 创建自定义布局的弹窗
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(context);
        android.view.View dialogView = inflater.inflate(R.layout.dialog_math_difficulty_custom, null);
        
        // 获取输入框
        android.widget.EditText etAdditionDigits = dialogView.findViewById(R.id.et_addition_digits);
        android.widget.EditText etSubtractionDigits = dialogView.findViewById(R.id.et_subtraction_digits);
        android.widget.EditText etMultiplicationMultiplierDigits = dialogView.findViewById(R.id.et_multiplication_multiplier_digits);
        android.widget.EditText etMultiplicationMultiplicandDigits = dialogView.findViewById(R.id.et_multiplication_multiplicand_digits);
        
        // 设置当前值
        etAdditionDigits.setText(String.valueOf(challengeSettingsManager.getMathAdditionDigits()));
        etSubtractionDigits.setText(String.valueOf(challengeSettingsManager.getMathSubtractionDigits()));
        etMultiplicationMultiplierDigits.setText(String.valueOf(
                challengeSettingsManager.getMathMultiplicationMultiplierDigits()));
        etMultiplicationMultiplicandDigits.setText(String.valueOf(
                challengeSettingsManager.getMathMultiplicationMultiplicandDigits()));

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
            .setTitle("数字位数设置")
            .setView(dialogView)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create();
        dialog.setOnShowListener(ignored ->
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!saveCustomMathDifficulty(
                        etAdditionDigits,
                        etSubtractionDigits,
                        etMultiplicationMultiplierDigits,
                        etMultiplicationMultiplicandDigits)) {
                    return;
                }
                dialog.dismiss();
                UiFeedback.show(context, "已保存自定义难度设置");
            }));
        dialog.show();
    }

    private boolean saveCustomMathDifficulty(
            EditText additionInput,
            EditText subtractionInput,
            EditText multiplierInput,
            EditText multiplicandInput) {
        additionInput.setError(null);
        subtractionInput.setError(null);
        multiplierInput.setError(null);
        multiplicandInput.setError(null);

        Integer additionDigits = parseInteger(additionInput);
        Integer subtractionDigits = parseInteger(subtractionInput);
        Integer multiplierDigits = parseInteger(multiplierInput);
        Integer multiplicandDigits = parseInteger(multiplicandInput);

        boolean valid = true;
        valid &= validateIntegerInput(
                additionInput,
                additionDigits,
                QuestionConst.ADD_LEN_MIN,
                QuestionConst.ADD_LEN_MAX,
                "加法位数");
        valid &= validateIntegerInput(
                subtractionInput,
                subtractionDigits,
                QuestionConst.ADD_LEN_MIN,
                QuestionConst.ADD_LEN_MAX,
                "减法位数");
        valid &= validateIntegerInput(
                multiplierInput,
                multiplierDigits,
                QuestionConst.MUL_LEN_MIN,
                QuestionConst.MUL_LEN_MAX,
                "乘数位数");
        valid &= validateIntegerInput(
                multiplicandInput,
                multiplicandDigits,
                QuestionConst.MUL_LEN_MIN,
                QuestionConst.MUL_LEN_MAX,
                "被乘数位数");
        if (!valid) {
            return false;
        }

        challengeSettingsManager.setMathAdditionDigits(additionDigits);
        challengeSettingsManager.setMathSubtractionDigits(subtractionDigits);
        challengeSettingsManager.setMathMultiplicationMultiplierDigits(multiplierDigits);
        challengeSettingsManager.setMathMultiplicationMultiplicandDigits(multiplicandDigits);
        return true;
    }

    private boolean validateIntegerInput(
            EditText input,
            Integer value,
            int min,
            int max,
            String fieldName) {
        if (value == null) {
            showInputError(input, "请输入" + fieldName);
            return false;
        }
        if (value < min || value > max) {
            showInputError(input, "请输入 " + min + "-" + max + " 之间的数字");
            return false;
        }
        return true;
    }

    private void showInputError(EditText input, String message) {
        UiFeedback.showInputError(input, message);
    }

    /**
     * 显示悬浮窗额外显示日常提醒设置对话框
     */
    public void showFloatingStrictReminderDialog() {
        // 记录用户点击了设置按钮
        appSettingsManager.setFloatingStrictReminderSettingsClicked(true);
        
        // 创建自定义布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        // 添加说明文字
        android.widget.TextView messageText = new android.widget.TextView(context);
        messageText.setText("设置的文字将在悬浮窗上额外显示");
        messageText.setTextSize(14);
        messageText.setTextColor(0xFF666666);
        messageText.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(messageText);

        // 添加小间距
        android.view.View spacer = new android.view.View(context);
        spacer.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 16));
        layout.addView(spacer);

        // 添加输入框
        final EditText input = new EditText(context);
        // 设置输入长度限制为50
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(50) });
        input.setHint("例如：玩手机？不如去喝水");
        input.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        
        // 设置当前已保存的文字
        String currentReminder = appSettingsManager.getFloatingStrictReminder();
        if (!currentReminder.isEmpty()) {
            input.setText(currentReminder);
        }
        layout.addView(input);

        // 添加字体大小设置区域
        android.view.View fontSizeSpacer = new android.view.View(context);
        fontSizeSpacer.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 20));
        layout.addView(fontSizeSpacer);

        // 字体大小标题
        android.widget.TextView fontSizeTitle = new android.widget.TextView(context);
        fontSizeTitle.setText("字体大小设置");
        fontSizeTitle.setTextSize(14);
        fontSizeTitle.setTextColor(0xFF666666);
        fontSizeTitle.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(fontSizeTitle);

        // 字体大小选择器
        android.widget.SeekBar fontSizeSeekBar = new android.widget.SeekBar(context);
        fontSizeSeekBar.setMax(20); // 12sp到32sp，共21个选项
        fontSizeSeekBar.setProgress(appSettingsManager.getFloatingStrictReminderFontSize() - 12); // 当前字体大小减去最小值
        fontSizeSeekBar.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(fontSizeSeekBar);

        // 字体大小显示
        final android.widget.TextView fontSizeDisplay = new android.widget.TextView(context);
        fontSizeDisplay.setText("当前字体大小: " + appSettingsManager.getFloatingStrictReminderFontSize() + "sp");
        fontSizeDisplay.setTextSize(12);
        fontSizeDisplay.setTextColor(0xFF999999);
        fontSizeDisplay.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(fontSizeDisplay);

        // 监听字体大小变化
        fontSizeSeekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                int fontSize = progress + 12; // 12sp到32sp
                fontSizeDisplay.setText("当前字体大小: " + fontSize + "sp");
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        new android.app.AlertDialog.Builder(context)
            .setTitle("自定义提醒")
            .setView(layout)
            .setPositiveButton("保存", (dialog, which) -> {
                String reminder = input.getText().toString().trim();
                int fontSize = fontSizeSeekBar.getProgress() + 12; // 获取当前字体大小
                
                appSettingsManager.setFloatingStrictReminder(reminder);
                appSettingsManager.setFloatingStrictReminderFontSize(fontSize);
                
                if (reminder.isEmpty()) {
                    UiFeedback.show(context, "已清除日常提醒");
                } else {
                    UiFeedback.show(
                            context,
                            "已保存日常提醒：" + reminder + "，字体大小：" + fontSize + "sp");
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

}
