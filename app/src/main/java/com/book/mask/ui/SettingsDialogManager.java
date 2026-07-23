package com.book.mask.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.book.mask.config.ChallengeType;
import com.book.mask.constant.Const;
import com.book.mask.constant.QuestionConst;
import com.book.mask.setting.RelaxManager;
import com.book.mask.setting.AppSettingsManager;
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
    
    private final Context context;
    private final RelaxManager relaxManager;
    private final AppSettingsManager appSettingsManager;

    private static String[] appOptions;
    private static CustomApp[] apps; // 改为CustomApp类型

    public SettingsDialogManager(Context context, RelaxManager relaxManager) {
        this.context = context;
        this.relaxManager = relaxManager;
        this.appSettingsManager = new AppSettingsManager(context);
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
        TextInputLayout durationLayout = dialogView.findViewById(R.id.layout_leisure_duration);
        TextInputLayout countLayout = dialogView.findViewById(R.id.layout_leisure_count);
        TextInputEditText durationInput = dialogView.findViewById(R.id.et_leisure_duration);
        TextInputEditText countInput = dialogView.findViewById(R.id.et_leisure_count);
        Button startButton = dialogView.findViewById(R.id.btn_start_leisure_time);

        durationInput.setText(String.valueOf(appSettingsManager.getLeisureDurationMinutes()));
        countInput.setText(String.valueOf(appSettingsManager.getLeisureDailyCount()));

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
                .setTitle("休闲时刻")
                .setView(dialogView)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();

        Handler stateHandler = new Handler(Looper.getMainLooper());
        Runnable[] refreshStartButton = new Runnable[1];
        refreshStartButton[0] = () -> {
            stateHandler.removeCallbacks(refreshStartButton[0]);
            updateLeisureStartButton(startButton, parseInteger(countInput));
            long remainingMillis = appSettingsManager.getLeisureTimeRemainingMillis();
            if (remainingMillis > 0) {
                stateHandler.postDelayed(refreshStartButton[0], remainingMillis + 100);
            }
        };
        countInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshStartButton[0].run();
            }
        });

        dialog.setOnShowListener(ignored -> {
            refreshStartButton[0].run();

            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!saveLeisureTimeSettings(
                        durationLayout, countLayout, durationInput, countInput)) {
                    return;
                }
                Toast.makeText(context, "休闲时刻设置已保存", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });

            startButton.setOnClickListener(v -> {
                if (!saveLeisureTimeSettings(
                        durationLayout, countLayout, durationInput, countInput)) {
                    return;
                }
                if (appSettingsManager.tryStartLeisureTime()) {
                    Toast.makeText(
                            context,
                            "已开启休闲，第一个关闭悬浮窗的APP将免答题解禁",
                            Toast.LENGTH_SHORT).show();
                    refreshStartButton[0].run();
                } else if (appSettingsManager.isLeisureTimeReadyForClose()) {
                    Toast.makeText(context, "休闲时刻已经开启", Toast.LENGTH_SHORT).show();
                    refreshStartButton[0].run();
                } else {
                    Toast.makeText(context, "今日休闲时刻次数已用完", Toast.LENGTH_SHORT).show();
                    refreshStartButton[0].run();
                }
            });
        });
        dialog.setOnDismissListener(ignored ->
                stateHandler.removeCallbacks(refreshStartButton[0]));
        dialog.show();
    }

    private boolean saveLeisureTimeSettings(
            TextInputLayout durationLayout,
            TextInputLayout countLayout,
            EditText durationInput,
            EditText countInput) {
        durationLayout.setError(null);
        countLayout.setError(null);

        Integer durationMinutes = parseInteger(durationInput);
        Integer dailyCount = parseInteger(countInput);
        boolean valid = true;

        if (durationMinutes == null
                || durationMinutes < AppSettingsManager.LEISURE_DURATION_MIN_MINUTES
                || durationMinutes > AppSettingsManager.LEISURE_DURATION_MAX_MINUTES) {
            durationLayout.setError("请输入15-30分钟");
            valid = false;
        }
        if (dailyCount == null
                || dailyCount < AppSettingsManager.LEISURE_DAILY_COUNT_MIN
                || dailyCount > AppSettingsManager.LEISURE_DAILY_COUNT_MAX) {
            countLayout.setError("请输入1-2次");
            valid = false;
        }
        if (!valid) {
            return false;
        }

        appSettingsManager.setLeisureTimeSettings(durationMinutes, dailyCount);
        return true;
    }

    private void updateLeisureStartButton(Button startButton, Integer configuredDailyCount) {
        if (appSettingsManager.isLeisureTimeActive()) {
            startButton.setEnabled(false);
            startButton.setText("休闲时刻进行中");
            return;
        }

        if (appSettingsManager.isLeisureTimeArmed()) {
            startButton.setEnabled(false);
            startButton.setText("已开启，待关闭悬浮窗");
            return;
        }

        if (configuredDailyCount == null
                || configuredDailyCount < AppSettingsManager.LEISURE_DAILY_COUNT_MIN
                || configuredDailyCount > AppSettingsManager.LEISURE_DAILY_COUNT_MAX) {
            startButton.setEnabled(false);
            startButton.setText("开启休闲时刻");
            return;
        }

        int remainingCount = Math.max(
                0,
                configuredDailyCount - appSettingsManager.getLeisureUsedCountToday());
        startButton.setEnabled(remainingCount > 0);
        startButton.setText(remainingCount > 0
                ? "开启休闲时刻（今日剩余" + remainingCount + "次）"
                : "今日次数已用完");
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
                        Toast.makeText(context, "已设置为: " + selectedTag, Toast.LENGTH_SHORT).show();
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

        new android.app.AlertDialog.Builder(context)
            .setTitle("定个目标")
            .setView(input)
            .setPositiveButton("确定", (dialog, which) -> {
                String customTag = input.getText().toString().trim();
                if (customTag.isEmpty()) {
                    Toast.makeText(context, "目标不能为空", Toast.LENGTH_SHORT).show();
                } else {
                    appSettingsManager.setMotivationTag(customTag);
                    Toast.makeText(context, "已设置为: " + customTag, Toast.LENGTH_SHORT).show();
                }
                if (onSettingChanged != null) onSettingChanged.run();
            })
            .setNegativeButton("取消", null)
               .show();
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
                Toast.makeText(context, "目标完成日期已设置为: " + selectedDate, Toast.LENGTH_SHORT).show();
                
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
            Toast.makeText(context, "无法获取当前APP", Toast.LENGTH_SHORT).show();
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

        new android.app.AlertDialog.Builder(context)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton("确定", (dialog, which) -> {
                try {
                    String topText = topEdit.getText().toString().trim();
                    String bottomText = bottomEdit.getText().toString().trim();
                    
                    if (topText.isEmpty() || bottomText.isEmpty()) {
                        Toast.makeText(context, "请填写完整的数值", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    int topOffset = Integer.parseInt(topText);
                    int bottomOffset = Integer.parseInt(bottomText);
                    
                    // 数值范围检查
                    if (topOffset < 0 || topOffset > 300) {
                        Toast.makeText(context, "上边缘距离应在0-300像素之间", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    if (bottomOffset < 0 || bottomOffset > 400) {
                        Toast.makeText(context, "下边缘距离应在0-400像素之间", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    saver.save(topOffset, bottomOffset);
                    
                    Toast.makeText(context, "悬浮窗位置已更新", Toast.LENGTH_SHORT).show();
                    
                } catch (NumberFormatException e) {
                    Toast.makeText(context, "请输入有效的数字", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
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
        ChallengeType currentType = appSettingsManager.getChallengeType();
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
                appSettingsManager.setChallengeType(selectedType);
                android.widget.Toast.makeText(
                        context,
                        "已设置为" + selectedType.getDisplayName(),
                        android.widget.Toast.LENGTH_SHORT).show();

                if (selectedType == ChallengeType.ARITHMETIC) {
                    showArithmeticDifficultyDialog();
                } else if (selectedType == ChallengeType.ENGLISH_READING) {
                    showEnglishReadingLengthDialog();
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
        int currentLength = appSettingsManager.getEnglishReadingLength();
        input.setText(String.valueOf(currentLength));
        input.setSelection(input.getText().length()); // 选中所有文本，方便修改

        new android.app.AlertDialog.Builder(context)
            .setTitle("设置阅读字数")
            .setMessage("阅读字数范围：200-1000")
            .setView(input)
            .setPositiveButton("确定", (dialog, which) -> {
                String inputText = input.getText().toString().trim();
                if (!inputText.isEmpty()) {
                    try {
                        int length = Integer.parseInt(inputText);
                        if (length >= QuestionConst.ENGLISH_READING_LENGTH_MIN && length <= QuestionConst.ENGLISH_READING_LENGTH_MAX) {
                            appSettingsManager.setEnglishReadingLength(length);
                            android.widget.Toast.makeText(context, "已设置阅读字数为：" + length, android.widget.Toast.LENGTH_SHORT).show();
                        } else {
                            android.widget.Toast.makeText(context, "阅读字数必须在" + QuestionConst.ENGLISH_READING_LENGTH_MIN + "-" + QuestionConst.ENGLISH_READING_LENGTH_MAX + "之间", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    } catch (NumberFormatException e) {
                        android.widget.Toast.makeText(context, "请输入有效数字", android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 显示纯算术题难度设置对话框（次级弹窗）
     */
    private void showArithmeticDifficultyDialog() {
        String[] difficultyOptions = {"默认难度", "自定义难度"};
        String currentMode = appSettingsManager.getMathDifficultyMode();
        int checkedItem = "custom".equals(currentMode) ? 1 : 0;

        new android.app.AlertDialog.Builder(context)
            .setTitle("算术题难度设置")
            .setSingleChoiceItems(difficultyOptions, checkedItem, (dialog, which) -> {
                if (which == 0) {
                    // 选择默认难度
                    appSettingsManager.setMathDifficultyMode("default");
                    android.widget.Toast.makeText(context, "已设置为默认难度", android.widget.Toast.LENGTH_SHORT).show();
                } else if (which == 1) {
                    // 选择自定义难度
                    appSettingsManager.setMathDifficultyMode("custom");
                    showCustomMathDifficultyDialog();
                }
                dialog.dismiss();
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
        etAdditionDigits.setText(String.valueOf(appSettingsManager.getMathAdditionDigits()));
        etSubtractionDigits.setText(String.valueOf(appSettingsManager.getMathSubtractionDigits()));
        etMultiplicationMultiplierDigits.setText(String.valueOf(appSettingsManager.getMathMultiplicationMultiplierDigits()));
        etMultiplicationMultiplicandDigits.setText(String.valueOf(appSettingsManager.getMathMultiplicationMultiplicandDigits()));

        new android.app.AlertDialog.Builder(context)
            .setTitle("数字位数设置")
            .setView(dialogView)
            .setPositiveButton("保存", (dialog, which) -> {
                // 验证并保存加法位数
                String additionInput = etAdditionDigits.getText().toString().trim();
                if (additionInput.isEmpty()) {
                    android.widget.Toast.makeText(context, "请输入加法位数", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // 验证并保存减法位数
                String subtractionInput = etSubtractionDigits.getText().toString().trim();
                if (subtractionInput.isEmpty()) {
                    android.widget.Toast.makeText(context, "请输入减法位数", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // 验证并保存乘法位数
                String multiplierInput = etMultiplicationMultiplierDigits.getText().toString().trim();
                String multiplicandInput = etMultiplicationMultiplicandDigits.getText().toString().trim();
                if (multiplierInput.isEmpty()) {
                    android.widget.Toast.makeText(context, "请输入乘数位数", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                if (multiplicandInput.isEmpty()) {
                    android.widget.Toast.makeText(context, "请输入被乘数位数", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                
                try {
                    int additionDigits = Integer.parseInt(additionInput);
                    int subtractionDigits = Integer.parseInt(subtractionInput);
                    int multiplierDigits = Integer.parseInt(multiplierInput);
                    int multiplicandDigits = Integer.parseInt(multiplicandInput);
                    
                    // 验证范围
                    if (additionDigits < QuestionConst.ADD_LEN_MIN || additionDigits > QuestionConst.ADD_LEN_MAX) {
                        android.widget.Toast.makeText(context, "加法位数请输入合理范围数字", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (subtractionDigits < QuestionConst.ADD_LEN_MIN || subtractionDigits > QuestionConst.ADD_LEN_MAX) {
                        android.widget.Toast.makeText(context, "减法位数请输入合理范围数字", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (multiplierDigits < QuestionConst.MUL_LEN_MIN || multiplierDigits > QuestionConst.MUL_LEN_MAX) {
                        android.widget.Toast.makeText(context, "乘数位数请输入合理范围数字", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (multiplicandDigits < QuestionConst.MUL_LEN_MIN || multiplicandDigits > QuestionConst.MUL_LEN_MAX) {
                        android.widget.Toast.makeText(context, "被乘数位数请输入合理范围数字", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    // 保存设置
                    appSettingsManager.setMathAdditionDigits(additionDigits);
                    appSettingsManager.setMathSubtractionDigits(subtractionDigits);
                    appSettingsManager.setMathMultiplicationMultiplierDigits(multiplierDigits);
                    appSettingsManager.setMathMultiplicationMultiplicandDigits(multiplicandDigits);
                    
                    android.widget.Toast.makeText(context, "已保存自定义难度设置", android.widget.Toast.LENGTH_SHORT).show();
                } catch (NumberFormatException e) {
                    android.widget.Toast.makeText(context, "请输入有效的数字", android.widget.Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
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
                    Toast.makeText(context, "已清除日常提醒", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "已保存日常提醒: " + reminder + "，字体大小: " + fontSize + "sp", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

}
