package com.book.mask.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.book.mask.challenge.retelling.SherpaOnnxTranscriber;
import com.book.mask.config.ChallengeType;
import com.book.mask.constant.Const;
import com.book.mask.constant.QuestionConst;
import com.book.mask.personalize.RelaxManager;
import com.book.mask.personalize.AppSettingsManager;
import com.book.mask.personalize.ChallengeSettingsManager;
import com.book.mask.personalize.DoubaoTtsConfigManager;
import com.book.mask.personalize.LeisureTimeManager;
import com.book.mask.personalize.RetellingRecord;
import com.book.mask.personalize.RetellingRecordStore;
import com.book.mask.personalize.SoeConfigManager;
import com.book.mask.personalize.ChallengeRecord;
import com.book.mask.personalize.ChallengeRecordStore;
import com.book.mask.personalize.ListeningRecord;
import com.book.mask.personalize.ListeningRecordStore;
import com.book.mask.config.CustomApp;
import com.book.mask.floating.FloatService;
import com.book.mask.reminder.config.ProviderSecretStore;
import com.book.mask.reminder.config.ReminderProviderConfig;
import com.book.mask.reminder.config.ReminderProviderConfigStore;
import com.book.mask.util.DateUtils;
import com.book.mask.R;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 设置对话框管理器
 * 负责处理所有设置相关的弹窗和文字内容逻辑
 */
public class SettingsDialogManager {
    private static final long LEISURE_STATE_REFRESH_INTERVAL_MS = 200;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 2001;

    /** 答题记录表格中使用的紧凑时间格式（月-日 时:分）。 */
    private static final java.text.SimpleDateFormat RECORD_TABLE_TIME_FORMAT =
            new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    /** 复述题保存前申请麦克风权限：权限结果返回后继续保存流程。 */
    public interface PendingRetellingAction {
        void run(boolean granted);
    }

    private static PendingRetellingAction pendingRetellingAction;

    /**
     * 供 MainActivity 转发运行时权限结果：麦克风权限授权后继续复述题保存。
     */
    public static void handleMicPermissionResult(int requestCode, boolean granted) {
        if (requestCode != REQUEST_RECORD_AUDIO_PERMISSION) {
            return;
        }
        PendingRetellingAction action = pendingRetellingAction;
        pendingRetellingAction = null;
        if (action != null) {
            action.run(granted);
        }
    }

    private final Context context;
    private final RelaxManager relaxManager;
    private final AppSettingsManager appSettingsManager;
    private final ChallengeSettingsManager challengeSettingsManager;
    private final LeisureTimeManager leisureTimeManager;
    private final ReminderProviderConfigStore providerConfigStore;
    private final ProviderSecretStore providerSecretStore;

    private static String[] appOptions;
    private static CustomApp[] apps; // 改为CustomApp类型

    public SettingsDialogManager(Context context, RelaxManager relaxManager) {
        this.context = context;
        this.relaxManager = relaxManager;
        this.appSettingsManager = new AppSettingsManager(context);
        this.challengeSettingsManager = new ChallengeSettingsManager(context);
        this.leisureTimeManager = new LeisureTimeManager(context);
        this.providerConfigStore = new ReminderProviderConfigStore();
        this.providerSecretStore = new ProviderSecretStore(context);
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
               .setNegativeButton("关闭", (dialog, which) -> {
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
                .setPositiveButton("关闭", null)
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

        // 点击弹窗空白处，让输入框失焦并收起键盘
        dialogView.setOnClickListener(v ->
                clearLeisureInputFocus(dialogView, relaxedViews, strictViews));

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
                FloatService.notifyLeisureTimeChanged();
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
                FloatService.notifyLeisureTimeChanged();
                refreshLeisureState.run();
                return;
            }
            if (!saveLeisureTimeSettings(targetViews)) {
                refreshLeisureState.run();
                return;
            }
            if (leisureTimeManager.tryStartLeisureTime(targetViews.mode)) {
                FloatService.notifyLeisureTimeChanged();
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
        TagFlowLayout targetFlow = new TagFlowLayout(context, dpToPx(8), dpToPx(10));
        targetFlow.setClipChildren(false);
        targetFlow.setClipToPadding(false);
        targetFlow.setPadding(dpToPx(24), dpToPx(18), dpToPx(24), 0);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
                .setTitle("设定目标")
                .setView(targetFlow)
                .setNegativeButton("关闭", null)
                .create();
        renderTargetCards(targetFlow, dialog, onSettingChanged);
        dialog.show();
    }

    private void renderTargetCards(
            TagFlowLayout targetFlow,
            android.app.AlertDialog dialog,
            Runnable onSettingChanged) {
        int[] accentColors = {
                0xFF5BAFFB, 0xFFFB9B59, 0xFF8A2B00, 0xFFDB8828,
                0xFF00A6A6, 0xFF7856C8, 0xFF4C9C58
        };
        String[] availableTags = appSettingsManager.getAvailableTags();
        String currentTag = appSettingsManager.getMotivationTag();
        targetFlow.removeAllViews();

        for (int index = 0; index < availableTags.length; index++) {
            String label = availableTags[index];
            boolean isCustomTag = !AppSettingsManager.isPredefinedMotivationTag(label);
            int accentColor = accentColors[index % accentColors.length];
            android.widget.FrameLayout targetCard = createTargetCard(
                    label, accentColor, label.equals(currentTag), isCustomTag, targetFlow, dialog, onSettingChanged);
            targetFlow.addView(targetCard, new TagFlowLayout.LayoutParams(
                    TagFlowLayout.LayoutParams.WRAP_CONTENT, dpToPx(34)));
        }

        TextView addCard = createAddTargetCard();
        addCard.setOnClickListener(v -> {
            dialog.dismiss();
            showCustomTagInputDialog(onSettingChanged);
        });
        targetFlow.addView(addCard, new TagFlowLayout.LayoutParams(
                TagFlowLayout.LayoutParams.WRAP_CONTENT, dpToPx(34)));
    }

    private android.widget.FrameLayout createTargetCard(
            String label,
            int accentColor,
            boolean selected,
            boolean isCustomTag,
            TagFlowLayout targetFlow,
            android.app.AlertDialog dialog,
            Runnable onSettingChanged) {
        android.widget.FrameLayout container = new android.widget.FrameLayout(context);
        TextView card = new TextView(context);
        card.setBackground(createTargetCardBackground(accentColor, selected));
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dpToPx(12), 0, dpToPx(12), 0);
        card.setTextSize(14);
        card.setTextColor(0xFF111111);
        card.setText(label);
        card.setContentDescription(label);
        card.setOnClickListener(v -> {
            appSettingsManager.setMotivationTag(label);
            if (onSettingChanged != null) {
                onSettingChanged.run();
            }
            renderTargetCards(targetFlow, dialog, onSettingChanged);
        });
        container.addView(card, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

        if (isCustomTag) {
            TextView deleteButton = new TextView(context);
            deleteButton.setBackground(createCircularDrawable(0xFFE84B4B));
            deleteButton.setGravity(Gravity.CENTER);
            deleteButton.setText("×");
            deleteButton.setTextSize(15);
            deleteButton.setTextColor(0xFFFFFFFF);
            deleteButton.setContentDescription("删除" + label);
            deleteButton.setVisibility(View.GONE);
            deleteButton.setOnClickListener(v -> {
                appSettingsManager.removeCustomMotivationTag(label);
                if (onSettingChanged != null) {
                    onSettingChanged.run();
                }
                renderTargetCards(targetFlow, dialog, onSettingChanged);
            });
            android.widget.FrameLayout.LayoutParams deleteParams =
                    new android.widget.FrameLayout.LayoutParams(dpToPx(18), dpToPx(18), Gravity.TOP | Gravity.END);
            deleteParams.topMargin = -dpToPx(5);
            deleteParams.rightMargin = -dpToPx(5);
            container.addView(deleteButton, deleteParams);
            card.setOnLongClickListener(v -> {
                deleteButton.setVisibility(View.VISIBLE);
                return true;
            });
        }
        return container;
    }

    private TextView createAddTargetCard() {
        TextView card = new TextView(context);
        card.setBackground(createTargetCardBackground(0xFF777777, false));
        card.setGravity(Gravity.CENTER);
        card.setPadding(dpToPx(14), 0, dpToPx(14), 0);
        card.setText("+");
        card.setTextSize(22);
        card.setTextColor(0xFF555555);
        card.setContentDescription("添加自定义目标");
        return card;
    }

    private android.graphics.drawable.GradientDrawable createTargetCardBackground(int accentColor, boolean selected) {
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(0xFFFBFBFB);
        background.setCornerRadius(dpToPx(17));
        background.setStroke(dpToPx(selected ? 2 : 1), selected ? accentColor : 0xFFEBD6CF);
        return background;
    }

    private android.graphics.drawable.GradientDrawable createCircularDrawable(int color) {
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        background.setColor(color);
        return background;
    }

    private static class TagFlowLayout extends android.view.ViewGroup {
        private final int horizontalSpacing;
        private final int verticalSpacing;

        private TagFlowLayout(Context context, int horizontalSpacing, int verticalSpacing) {
            super(context);
            this.horizontalSpacing = horizontalSpacing;
            this.verticalSpacing = verticalSpacing;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int availableWidth = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
            int usedWidth = 0;
            int rowHeight = 0;
            int totalHeight = getPaddingTop() + getPaddingBottom();
            boolean hasItemInRow = false;

            for (int index = 0; index < getChildCount(); index++) {
                View child = getChildAt(index);
                if (child.getVisibility() == GONE) continue;
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                LayoutParams params = (LayoutParams) child.getLayoutParams();
                int childWidth = child.getMeasuredWidth() + params.leftMargin + params.rightMargin;
                int childHeight = child.getMeasuredHeight() + params.topMargin + params.bottomMargin;
                if (hasItemInRow && usedWidth + horizontalSpacing + childWidth > availableWidth) {
                    totalHeight += rowHeight + verticalSpacing;
                    usedWidth = 0;
                    rowHeight = 0;
                    hasItemInRow = false;
                }
                usedWidth += (hasItemInRow ? horizontalSpacing : 0) + childWidth;
                rowHeight = Math.max(rowHeight, childHeight);
                hasItemInRow = true;
            }
            if (hasItemInRow) {
                totalHeight += rowHeight;
            }
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
                    resolveSize(totalHeight, heightMeasureSpec));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int availableWidth = getWidth() - getPaddingLeft() - getPaddingRight();
            int childLeft = getPaddingLeft();
            int childTop = getPaddingTop();
            int rowHeight = 0;

            for (int index = 0; index < getChildCount(); index++) {
                View child = getChildAt(index);
                if (child.getVisibility() == GONE) continue;
                LayoutParams params = (LayoutParams) child.getLayoutParams();
                int childWidth = child.getMeasuredWidth() + params.leftMargin + params.rightMargin;
                int childHeight = child.getMeasuredHeight() + params.topMargin + params.bottomMargin;
                if (childLeft > getPaddingLeft()
                        && childLeft - getPaddingLeft() + horizontalSpacing + childWidth > availableWidth) {
                    childLeft = getPaddingLeft();
                    childTop += rowHeight + verticalSpacing;
                    rowHeight = 0;
                }
                if (childLeft > getPaddingLeft()) {
                    childLeft += horizontalSpacing;
                }
                int childX = childLeft + params.leftMargin;
                int childY = childTop + params.topMargin;
                child.layout(childX, childY, childX + child.getMeasuredWidth(), childY + child.getMeasuredHeight());
                childLeft += childWidth;
                rowHeight = Math.max(rowHeight, childHeight);
            }
        }

        @Override
        protected LayoutParams generateDefaultLayoutParams() {
            return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        }

        @Override
        public LayoutParams generateLayoutParams(android.util.AttributeSet attrs) {
            return new LayoutParams(getContext(), attrs);
        }

        @Override
        protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams params) {
            return new LayoutParams(params);
        }

        @Override
        protected boolean checkLayoutParams(ViewGroup.LayoutParams params) {
            return params instanceof LayoutParams;
        }

        private static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams {
            private LayoutParams(int width, int height) {
                super(width, height);
            }

            private LayoutParams(Context context, android.util.AttributeSet attrs) {
                super(context, attrs);
            }

            private LayoutParams(ViewGroup.LayoutParams params) {
                super(params);
            }
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    /**
     * 显示自定义标签输入对话框
     */
    public void showCustomTagInputDialog(Runnable onSettingChanged) {
        final EditText input = new EditText(context);
        // 设置输入长度限制
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(12) });
        input.setHint("不超过 12 个字");
        String currentTag = appSettingsManager.getMotivationTag();
        if (!Const.TARGET_TO_BE_SET.equals(currentTag)
                && !AppSettingsManager.isPredefinedMotivationTag(currentTag)) {
            input.setText(currentTag);
            input.setSelection(input.length());
        }

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
            .setTitle("定个目标")
            .setView(createInputContainer(input))
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
                appSettingsManager.addCustomMotivationTag(customTag);
                appSettingsManager.setMotivationTag(customTag);
                dialog.dismiss();
                UiFeedback.show(context, "已设置为：" + customTag);
                if (onSettingChanged != null) onSettingChanged.run();
            }));
        dialog.show();
    }
    
    public void showReminderStyleDialog(Runnable onSettingChanged) {
        final String[] styles = {
                AppSettingsManager.DEFAULT_REMINDER_STYLE,
                "励志",
                "毒舌",
                "幽默",
                "温馨",
                "理性",
                "挖苦",
                "严厉",
                "发人深省"
        };
        int[] accentColors = {
                0xFF5BAFFB, 0xFFFB9B59, 0xFF8A2B00, 0xFFDB8828,
                0xFF00A6A6, 0xFF7856C8, 0xFF4C9C58
        };
        TagFlowLayout styleFlow = new TagFlowLayout(context, dpToPx(8), dpToPx(10));
        styleFlow.setClipChildren(false);
        styleFlow.setClipToPadding(false);
        styleFlow.setPadding(dpToPx(24), dpToPx(18), dpToPx(24), 0);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
                .setTitle("警示语风格")
                .setView(styleFlow)
                .setNegativeButton("关闭", null)
                .create();

        renderStyleCards(dialog, styleFlow, styles, accentColors, onSettingChanged);
        dialog.show();
    }

    private void renderStyleCards(
            android.app.AlertDialog dialog,
            TagFlowLayout styleFlow,
            String[] styles,
            int[] accentColors,
            Runnable onSettingChanged) {
        String currentStyle = appSettingsManager.getReminderStyle();
        String currentCustomStyle = appSettingsManager.getReminderCustomStyle();
        styleFlow.removeAllViews();
        for (int index = 0; index < styles.length; index++) {
            String style = styles[index];
            TextView styleCard = createStyleCard(
                    style, accentColors[index % accentColors.length], style.equals(currentStyle));
            styleCard.setOnClickListener(v -> {
                appSettingsManager.setReminderStyle(style);
                if (onSettingChanged != null) {
                    onSettingChanged.run();
                }
                renderStyleCards(dialog, styleFlow, styles, accentColors, onSettingChanged);
            });
            styleFlow.addView(styleCard, new TagFlowLayout.LayoutParams(
                    TagFlowLayout.LayoutParams.WRAP_CONTENT, dpToPx(34)));
        }

        String[] customStyles = appSettingsManager.getCustomReminderStyles();
        for (int index = 0; index < customStyles.length; index++) {
            String customStyle = customStyles[index];
            boolean selected = "自定义".equals(currentStyle)
                    && customStyle.equals(currentCustomStyle);
            android.widget.FrameLayout styleCard = createCustomStyleCard(
                    customStyle,
                    accentColors[(styles.length + index) % accentColors.length],
                    selected,
                    dialog,
                    styleFlow,
                    styles,
                    accentColors,
                    onSettingChanged);
            styleFlow.addView(styleCard, new TagFlowLayout.LayoutParams(
                    TagFlowLayout.LayoutParams.WRAP_CONTENT, dpToPx(34)));
        }

        TextView addCard = createAddTargetCard();
        addCard.setContentDescription("添加自定义风格");
        addCard.setOnClickListener(v -> {
            dialog.dismiss();
            showCustomReminderStyleInputDialog(
                    onSettingChanged,
                    () -> showReminderStyleDialog(onSettingChanged));
        });
        styleFlow.addView(addCard, new TagFlowLayout.LayoutParams(
                TagFlowLayout.LayoutParams.WRAP_CONTENT, dpToPx(34)));
    }

    private TextView createStyleCard(String style, int accentColor, boolean selected) {
        TextView card = new TextView(context);
        card.setBackground(createTargetCardBackground(accentColor, selected));
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dpToPx(12), 0, dpToPx(12), 0);
        card.setText(style);
        card.setTextSize(14);
        card.setTextColor(0xFF111111);
        card.setContentDescription(style);
        return card;
    }

    private android.widget.FrameLayout createCustomStyleCard(
            String style,
            int accentColor,
            boolean selected,
            android.app.AlertDialog dialog,
            TagFlowLayout styleFlow,
            String[] styles,
            int[] accentColors,
            Runnable onSettingChanged) {
        android.widget.FrameLayout container = new android.widget.FrameLayout(context);
        TextView card = createStyleCard(style, accentColor, selected);
        card.setOnClickListener(v -> {
            appSettingsManager.setReminderCustomStyle(style);
            appSettingsManager.setReminderStyle("自定义");
            if (onSettingChanged != null) {
                onSettingChanged.run();
            }
            renderStyleCards(dialog, styleFlow, styles, accentColors, onSettingChanged);
        });
        container.addView(card, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

        TextView deleteButton = new TextView(context);
        deleteButton.setBackground(createCircularDrawable(0xFFE84B4B));
        deleteButton.setGravity(Gravity.CENTER);
        deleteButton.setText("×");
        deleteButton.setTextSize(15);
        deleteButton.setTextColor(0xFFFFFFFF);
        deleteButton.setContentDescription("删除" + style);
        deleteButton.setVisibility(View.GONE);
        deleteButton.setOnClickListener(v -> {
            appSettingsManager.removeCustomReminderStyle(style);
            if (onSettingChanged != null) {
                onSettingChanged.run();
            }
            renderStyleCards(dialog, styleFlow, styles, accentColors, onSettingChanged);
        });
        android.widget.FrameLayout.LayoutParams deleteParams =
                new android.widget.FrameLayout.LayoutParams(dpToPx(18), dpToPx(18), Gravity.TOP | Gravity.END);
        deleteParams.topMargin = -dpToPx(5);
        deleteParams.rightMargin = -dpToPx(5);
        container.addView(deleteButton, deleteParams);
        card.setOnLongClickListener(v -> {
            deleteButton.setVisibility(View.VISIBLE);
            return true;
        });
        return container;
    }

    private LinearLayout createInputContainer(EditText input) {
        int contentPadding = dpToPx(24);
        LinearLayout inputContainer = new LinearLayout(context);
        inputContainer.setPadding(contentPadding, contentPadding, contentPadding, contentPadding);
        inputContainer.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return inputContainer;
    }

    private void showCustomReminderStyleInputDialog(
            Runnable onSettingChanged, Runnable onStyleSaved) {
        EditText input = new EditText(context);
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(10)});
        input.setHint("不超过 10 个字");

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
                .setTitle("自定义风格")
                .setView(createInputContainer(input))
                .setPositiveButton("确定", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    input.setError(null);
                    String customStyle = input.getText().toString().trim();
                    if (customStyle.isEmpty()) {
                        showInputError(input, "请输入风格要求");
                        return;
                    }
                    appSettingsManager.addCustomReminderStyle(customStyle);
                    appSettingsManager.setReminderCustomStyle(customStyle);
                    appSettingsManager.setReminderStyle("自定义");
                    dialog.dismiss();
                    if (onSettingChanged != null) {
                        onSettingChanged.run();
                    }
                    if (onStyleSaved != null) {
                        onStyleSaved.run();
                    }
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
        // 日期组件里已高亮所选日期，隐藏顶栏下方冗余的日期回显，并削减组件与按钮间的大段留白。
        // 必须在 show() 之前完成精简：若放到 OnShowListener 里做，弹窗已显示后再改尺寸会触发重新布局与重新居中，造成肉眼可见的位置跳变/闪烁。
        trimDatePickerChrome(datePickerDialog.getDatePicker());
        datePickerDialog.show();
    }

    /**
     * 精简日期选择器的多余视觉元素：
     * 1. 隐藏顶部显示所选日期的 header（日历里已高亮，无需重复展示）；
     * 2. 去掉日历组件底部的留白，使其贴近“取消/确定”按钮。
     */
    private void trimDatePickerChrome(android.widget.DatePicker datePicker) {
        if (datePicker == null) return;
        int headerId = context.getResources().getIdentifier("date_picker_header", "id", "android");
        if (headerId != 0) {
            View header = datePicker.findViewById(headerId);
            if (header != null) header.setVisibility(View.GONE);
        }
        datePicker.setPadding(datePicker.getPaddingLeft(), 0, datePicker.getPaddingRight(), 0);
        // Hiding the header leaves the framework minimum height in place.
        datePicker.setMinimumHeight(0);
    }
    
    public void showDouyinFirstTextCheckDelayDialog() {
        EditText input = new EditText(context);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(appSettingsManager.getDouyinFirstTextCheckDelayMs()));
        input.setSelectAllOnFocus(true);
        input.setHint("默认: " + AppSettingsManager.DEFAULT_DOUYIN_FIRST_TEXT_CHECK_DELAY_MS);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(24), dpToPx(8), dpToPx(24), 0);
        layout.addView(input);

        TextView hint = new TextView(context);
        hint.setText("单位：毫秒。仅控制刚进入抖音、悬浮窗抢先显示后的首次文字检测；后续内容变化防抖不受影响。");
        hint.setTextSize(14);
        hint.setTextColor(0xFF666666);
        layout.addView(hint);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
                .setTitle("抖音首次文字检测延迟")
                .setView(layout)
                .setPositiveButton("确定", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    input.setError(null);
                    Integer delayMs = parseInteger(input);
                    if (delayMs == null || delayMs < 0 || delayMs > 5000) {
                        showInputError(input, "请输入 0-5000 之间的数字");
                        return;
                    }
                    appSettingsManager.setDouyinFirstTextCheckDelayMs(delayMs);
                    dialog.dismiss();
                    UiFeedback.show(context, "抖音首次检测延迟已更新为 " + delayMs + "ms");
                }));
        dialog.show();
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
     * 返回当前答题类型。
     */
    public ChallengeType getChallengeType() {
        return challengeSettingsManager.getChallengeType();
    }

    /**
     * 切换答题类型。
     * 复述题需先通过前置检查（大模型 / ASR），任一不满足则返回错误信息且不切换；
     * 算术题 / 英文阅读切换后弹出对应的次级设置弹窗。
     *
     * @param onApplied 切换成功后回调（用于刷新页面选中态）
     * @return 非 null 表示前置检查未通过，未切换
     */
    public String applyChallengeTypeSelection(ChallengeType selectedType, Runnable onApplied) {
        if (selectedType == ChallengeType.RETELLING) {
            String preflightError = checkRetellingPreflight();
            if (preflightError != null) {
                return preflightError;
            }
            challengeSettingsManager.setChallengeType(ChallengeType.RETELLING);
            UiFeedback.show(context, "已设置复述题");
            android.util.Log.d("SettingsDialog", "答题类型设置: 已切换为复述题 (枚举=" + ChallengeType.RETELLING + ")");
            if (onApplied != null) {
                onApplied.run();
            }
            return null;
        }

        challengeSettingsManager.setChallengeType(selectedType);
        android.util.Log.d("SettingsDialog", "答题类型设置: 已设置题型=" + selectedType.getDisplayName()
                + " (枚举=" + selectedType + ")");
        if (selectedType == ChallengeType.ARITHMETIC) {
            showArithmeticDifficultyDialog();
        } else if (selectedType == ChallengeType.ENGLISH_READING) {
            showEnglishReadingLengthDialog();
        } else {
            UiFeedback.show(context, "已设置为" + selectedType.getDisplayName());
        }
        if (onApplied != null) {
            onApplied.run();
        }
        return null;
    }

    /** 绑定答题计时三选项（不显示 / 分钟 mm / 分钟和秒 mm:s），点击即持久化。 */
    public void bindChallengeTimerOptions(View containerView) {
        TextView none = containerView.findViewById(R.id.tv_timer_none);
        TextView minutes = containerView.findViewById(R.id.tv_timer_minutes);
        TextView minutesSeconds = containerView.findViewById(R.id.tv_timer_minutes_seconds);
        final TextView[] options = {none, minutes, minutesSeconds};

        applyTimerOptionSelection(options, challengeSettingsManager.getChallengeTimerMode());

        View.OnClickListener listener = v -> {
            int selectedMode;
            if (v == none) {
                selectedMode = ChallengeSettingsManager.TIMER_MODE_NONE;
            } else if (v == minutes) {
                selectedMode = ChallengeSettingsManager.TIMER_MODE_MINUTES;
            } else {
                selectedMode = ChallengeSettingsManager.TIMER_MODE_MINUTES_SECONDS;
            }
            challengeSettingsManager.setChallengeTimerMode(selectedMode);
            applyTimerOptionSelection(options, selectedMode);
            android.util.Log.d("SettingsDialog", "答题类型弹窗: 答题计时模式=" + selectedMode);
        };
        none.setOnClickListener(listener);
        minutes.setOnClickListener(listener);
        minutesSeconds.setOnClickListener(listener);
    }

    /** 按下标同步选中态：0 不显示 / 1 分钟 / 2 分钟和秒。 */
    private void applyTimerOptionSelection(TextView[] options, int mode) {
        for (int i = 0; i < options.length; i++) {
            options[i].setSelected(i == mode);
        }
    }

    /** 复述题行内摘要：字数/时间/合格线，取自复述题设置弹窗的当前值。 */
    public String formatRetellingSummary() {
        return "字数" + challengeSettingsManager.getRetellingStoryLength()
                + " · " + challengeSettingsManager.getRetellingDisplaySeconds() + "秒"
                + " · 合格线" + challengeSettingsManager.getRetellingPassScore();
    }

    /**
     * 复述题设置弹窗：一次保存故事字数、展示秒数、通过分数三个值。
     * 保存前检查麦克风权限（未授予则回调 MainActivity 申请）、大模型配置、ASR 模型状态；
     * 任一不满足则不保存复述题，保留原题型。
     *
     * @param onSaved 保存成功后回调（用于刷新答题类型页面内的摘要与选中态）
     */
    public void showRetellingSettingsDialog(Runnable onSaved) {
        View dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_retelling_settings, null);
        EditText storyLengthInput = dialogView.findViewById(R.id.et_retelling_story_length);
        EditText displaySecondsInput = dialogView.findViewById(R.id.et_retelling_display_seconds);
        EditText passScoreInput = dialogView.findViewById(R.id.et_retelling_pass_score);
        storyLengthInput.setText(String.valueOf(challengeSettingsManager.getRetellingStoryLength()));
        displaySecondsInput.setText(String.valueOf(challengeSettingsManager.getRetellingDisplaySeconds()));
        passScoreInput.setText(String.valueOf(challengeSettingsManager.getRetellingPassScore()));

        final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
                .setTitle("复述题设置")
                .setView(dialogView)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    RetellingSettings values = parseAndValidateRetellingSettings(
                            storyLengthInput, displaySecondsInput, passScoreInput);
                    if (values == null) {
                        return;
                    }
                    if (!hasMicPermission()) {
                        requestMicPermissionForSave(values, dialog, onSaved);
                        return;
                    }
                    performRetellingSave(values, dialog, onSaved);
                }));
        dialog.show();
    }

    private void performRetellingSave(RetellingSettings values,
                                      android.app.AlertDialog dialog,
                                      Runnable onSaved) {
        String preflightError = checkRetellingPreflight();
        if (preflightError != null) {
            UiFeedback.showError(context, preflightError);
            return;
        }
        challengeSettingsManager.setRetellingStoryLength(values.storyLength);
        challengeSettingsManager.setRetellingDisplaySeconds(values.displaySeconds);
        challengeSettingsManager.setRetellingPassScore(values.passScore);
        challengeSettingsManager.setChallengeType(ChallengeType.RETELLING);
        dialog.dismiss();
        UiFeedback.show(context, "已设置复述题");
        android.util.Log.d("SettingsDialog", "答题类型弹窗: 保存复述题设置 字数=" + values.storyLength
                + " 秒数=" + values.displaySeconds + " 合格线=" + values.passScore
                + " 题型设为复述题 (枚举=" + ChallengeType.RETELLING + ")");
        if (onSaved != null) {
            onSaved.run();
        }
    }

    private RetellingSettings parseAndValidateRetellingSettings(
            EditText storyLengthInput,
            EditText displaySecondsInput,
            EditText passScoreInput) {
        storyLengthInput.setError(null);
        displaySecondsInput.setError(null);
        passScoreInput.setError(null);

        Integer storyLength = parseInteger(storyLengthInput);
        Integer displaySeconds = parseInteger(displaySecondsInput);
        Integer passScore = parseInteger(passScoreInput);

        boolean valid = true;
        valid &= validateIntegerInput(
                storyLengthInput,
                storyLength,
                QuestionConst.RETELLING_STORY_LENGTH_MIN,
                QuestionConst.RETELLING_STORY_LENGTH_MAX,
                "故事字数");
        valid &= validateIntegerInput(
                displaySecondsInput,
                displaySeconds,
                QuestionConst.RETELLING_DISPLAY_SECONDS_MIN,
                QuestionConst.RETELLING_DISPLAY_SECONDS_MAX,
                "展示秒数");
        valid &= validateIntegerInput(
                passScoreInput,
                passScore,
                QuestionConst.RETELLING_PASS_SCORE_MIN,
                QuestionConst.RETELLING_PASS_SCORE_MAX,
                "通过分数");
        if (!valid) {
            return null;
        }
        return new RetellingSettings(storyLength, displaySeconds, passScore);
    }

    private boolean hasMicPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 未授予麦克风权限：弹出说明并回调 MainActivity 申请运行时权限；
     * 授权成功后继续保存，拒绝则不保存复述题。
     */
    private void requestMicPermissionForSave(final RetellingSettings values,
                                             final android.app.AlertDialog settingsDialog,
                                             final Runnable onSaved) {
        new android.app.AlertDialog.Builder(context)
                .setTitle("需要麦克风权限")
                .setMessage("复述题需要录音权限，用于本地语音识别。是否授予？")
                .setPositiveButton("去授权", (dialog, which) -> {
                    if (!(context instanceof Activity)) {
                        UiFeedback.showError(context, "无法申请权限");
                        return;
                    }
                    pendingRetellingAction = granted -> {
                        if (granted) {
                            performRetellingSave(values, settingsDialog, onSaved);
                        } else {
                            UiFeedback.showError(context, "未授予麦克风权限，复述题未保存");
                            settingsDialog.dismiss();
                        }
                    };
                    ActivityCompat.requestPermissions(
                            (Activity) context,
                            new String[]{Manifest.permission.RECORD_AUDIO},
                            REQUEST_RECORD_AUDIO_PERMISSION);
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    UiFeedback.showError(context, "未授予麦克风权限，复述题未保存");
                    settingsDialog.dismiss();
                })
                .show();
    }

    /**
     * 保存前检查：大模型配置（自定义 Provider 必须有 API Key）+ ASR 模型状态。
     * 返回非空表示前置检查未通过，复述题不保存。
     */
    private String checkRetellingPreflight() {
        ReminderProviderConfig config = providerConfigStore.getActiveConfig();
        if (!config.isOfficial()) {
            try {
                String apiKey = providerSecretStore.getApiKey(config.getProfileId());
                if (apiKey == null || apiKey.isEmpty()) {
                    return "自定义大模型未配置 API Key，请先到提醒设置中配置";
                }
            } catch (GeneralSecurityException e) {
                return "读取大模型配置失败，请重新配置";
            }
        }
        if (!SherpaOnnxTranscriber.getInstance(context).isReady()) {
            return "语音识别模型未就绪（ASR 模型缺失），请先放入模型文件";
        }
        return null;
    }

    private static class RetellingSettings {
        final int storyLength;
        final int displaySeconds;
        final int passScore;

        RetellingSettings(int storyLength, int displaySeconds, int passScore) {
            this.storyLength = storyLength;
            this.displaySeconds = displaySeconds;
            this.passScore = passScore;
        }
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
     * 豆包语音（听力题）凭据设置弹窗：API Key / ResourceID / Speaker（双向流式 TTS）。
     * 留空任一字段即视为未配置，听力题回退占位音频。
     */
    public void showDoubaoTtsSettingsDialog() {
        View dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_doubao_tts_settings, null);
        EditText apiKeyInput = dialogView.findViewById(R.id.et_doubao_tts_api_key);
        EditText resourceIdInput = dialogView.findViewById(R.id.et_doubao_tts_resource_id);
        EditText speakerInput = dialogView.findViewById(R.id.et_doubao_tts_speaker);

        DoubaoTtsConfigManager config = new DoubaoTtsConfigManager(context);
        apiKeyInput.setText(config.getApiKey());
        resourceIdInput.setText(config.getResourceId());
        speakerInput.setText(config.getSpeaker());

        final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
                .setTitle("豆包语音（听力题）")
                .setView(dialogView)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    config.setApiKey(apiKeyInput.getText().toString());
                    config.setResourceId(resourceIdInput.getText().toString());
                    config.setSpeaker(speakerInput.getText().toString());
                    dialog.dismiss();
                    UiFeedback.show(context,
                            config.isConfigured()
                                    ? "已保存豆包语音配置"
                                    : "已保存（未完整配置，听力题使用占位音频）");
                }));
        dialog.show();
    }

    /**
     * 腾讯口语评测（复述题）凭据设置弹窗：AppID / SecretId / SecretKey / ScoreCoeff。
     * 留空关键凭据即视为未配置，复述题回退本地识别 + 纯文本评分。
     */
    public void showSoeSettingsDialog() {
        View dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_soe_settings, null);
        EditText appIdInput = dialogView.findViewById(R.id.et_soe_app_id);
        EditText secretIdInput = dialogView.findViewById(R.id.et_soe_secret_id);
        EditText secretKeyInput = dialogView.findViewById(R.id.et_soe_secret_key);
        EditText scoreCoeffInput = dialogView.findViewById(R.id.et_soe_score_coeff);

        SoeConfigManager config = new SoeConfigManager(context);
        appIdInput.setText(config.getAppId());
        secretIdInput.setText(config.getSecretId());
        secretKeyInput.setText(config.getSecretKey());
        scoreCoeffInput.setText(String.valueOf(config.getScoreCoeff()));

        final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
                .setTitle("腾讯口语评测（复述题）")
                .setView(dialogView)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    config.setAppId(appIdInput.getText().toString());
                    config.setSecretId(secretIdInput.getText().toString());
                    config.setSecretKey(secretKeyInput.getText().toString());
                    config.setScoreCoeff(parseScoreCoeff(scoreCoeffInput.getText().toString()));
                    dialog.dismiss();
                    UiFeedback.show(context,
                            config.isConfigured()
                                    ? "已保存腾讯口语评测配置"
                                    : "已保存（未完整配置，复述题使用本地识别）");
                }));
        dialog.show();
    }

    private static float parseScoreCoeff(String text) {
        if (text == null || text.trim().isEmpty()) {
            return SoeConfigManager.DEFAULT_SCORE_COEFF;
        }
        try {
            return Float.parseFloat(text.trim());
        } catch (NumberFormatException e) {
            return SoeConfigManager.DEFAULT_SCORE_COEFF;
        }
    }

    /**
     * 显示悬浮窗额外显示日常提醒设置对话框
     */
    public void showFloatingStrictReminderDialog(Runnable onSettingChanged) {
        // 记录用户点击了设置按钮
        appSettingsManager.setFloatingStrictReminderSettingsClicked(true);
        
        // 创建自定义布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        // 添加说明文字
        android.widget.TextView messageText = new android.widget.TextView(context);
        messageText.setText("将在悬浮窗中间固定展示");
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
        input.setHint(Const.DEFAULT_STRICT_REMINDER);
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
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 30));
        layout.addView(fontSizeSpacer);

        // 字体大小标题
        android.widget.TextView fontSizeTitle = new android.widget.TextView(context);
        fontSizeTitle.setText("字体大小: " + appSettingsManager.getFloatingStrictReminderFontSize() + "sp");
        fontSizeTitle.setTextSize(14);
        fontSizeTitle.setTextColor(0xFF666666);
        fontSizeTitle.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(fontSizeTitle);

        // 字体大小滑块
        Slider fontSizeSlider = new Slider(context);
        fontSizeSlider.setValueFrom(12);
        fontSizeSlider.setValueTo(32);
        fontSizeSlider.setStepSize(1);
        fontSizeSlider.setTickVisible(false);
        fontSizeSlider.setLabelBehavior(2); // LabelBehavior.LABEL_GONE in Material Components 1.10.0.
        fontSizeSlider.setValue(appSettingsManager.getFloatingStrictReminderFontSize());
        fontSizeSlider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(fontSizeSlider);

        // 监听字体大小变化
        fontSizeSlider.addOnChangeListener((slider, value, fromUser) -> {
            int fontSize = Math.round(value);
            fontSizeTitle.setText("字体大小: " + fontSize + "sp");
        });

        // 添加字体颜色设置区域
        final int[] selectedFontColor = { appSettingsManager.getFloatingStrictReminderFontColor() };
        addStrictReminderColorPicker(layout, selectedFontColor);

        final android.app.AlertDialog[] dialogRef = new android.app.AlertDialog[1];
        addStrictReminderActionButtons(
                layout, dialogRef, input, fontSizeSlider, selectedFontColor, onSettingChanged);

        dialogRef[0] = new android.app.AlertDialog.Builder(context)
                .setTitle("座右铭")
                .setView(layout)
                .create();
        dialogRef[0].show();
    }

    private void addStrictReminderActionButtons(
            LinearLayout parent,
            android.app.AlertDialog[] dialogRef,
            EditText input,
            Slider fontSizeSlider,
            int[] selectedFontColor,
            Runnable onSettingChanged) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.END);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = (int) (20 * density);
        row.setLayoutParams(rowParams);

        Button cancelButton = new Button(context);
        cancelButton.setText("取消");
        cancelButton.setOnClickListener(v -> dialogRef[0].dismiss());
        row.addView(cancelButton);

        Button saveButton = new Button(context);
        saveButton.setText("保存");
        saveButton.setOnClickListener(v -> {
            String reminder = input.getText().toString().trim();
            int fontSize = Math.round(fontSizeSlider.getValue());
            appSettingsManager.setFloatingStrictReminder(reminder);
            appSettingsManager.setFloatingStrictReminderFontSize(fontSize);
            appSettingsManager.setFloatingStrictReminderFontColor(selectedFontColor[0]);
            if (onSettingChanged != null) {
                onSettingChanged.run();
            }
            dialogRef[0].dismiss();
            showStrictReminderSavedFeedback(reminder, fontSize);
        });
        row.addView(saveButton);
        parent.addView(row);
    }

    private void showStrictReminderSavedFeedback(String reminder, int fontSize) {
        if (reminder.isEmpty()) {
            UiFeedback.show(context, "已保存");
            return;
        }
        UiFeedback.show(context, "已保存日常提醒：" + reminder + "，字体大小：" + fontSize + "sp");
    }

    /**
     * 座右铭字体颜色候选（7 种常见颜色，红色为默认）
     */
    private static final int[] STRICT_REMINDER_FONT_COLORS = {
            0xFF000000, // 黑色
            AppSettingsManager.DEFAULT_STRICT_REMINDER_FONT_COLOR, // 红色（默认）
            0xFF399C3F, // 绿色
            0xFFE91E63, // 粉色
            0xFF9C27B0, // 紫色
            0xFF2196F3, // 蓝色
            0xFF3F51B5, // 靛蓝
    };

    /**
     * 在座右铭设置对话框中追加字体颜色选择区域。
     * 展示圆形色块，点击切换选中态，选中结果写回 selectedColor[0]。
     */
    private void addStrictReminderColorPicker(LinearLayout parent, final int[] selectedColor) {
        float density = context.getResources().getDisplayMetrics().density;
        int swatchSize = (int) (26 * density);
        int swatchMargin = (int) (6 * density);
        int borderWidth = (int) (3 * density);

        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (8 * density)));
        parent.addView(spacer);

        TextView title = new TextView(context);
        title.setText("字体颜色");
        title.setTextSize(14);
        title.setTextColor(0xFF666666);
        parent.addView(title);

        final View[] swatches = new View[STRICT_REMINDER_FONT_COLORS.length];
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = swatchMargin;
        row.setLayoutParams(rowParams);
        parent.addView(row);
        for (int i = 0; i < STRICT_REMINDER_FONT_COLORS.length; i++) {
            final int color = STRICT_REMINDER_FONT_COLORS[i];
            View swatch = new View(context);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(swatchSize, swatchSize);
            p.rightMargin = swatchMargin;
            swatch.setLayoutParams(p);
            swatch.setBackground(makeSwatchDrawable(color, color == selectedColor[0], borderWidth));
            swatch.setOnClickListener(v -> {
                selectedColor[0] = color;
                for (int j = 0; j < swatches.length; j++) {
                    swatches[j].setBackground(makeSwatchDrawable(
                            STRICT_REMINDER_FONT_COLORS[j],
                            STRICT_REMINDER_FONT_COLORS[j] == color,
                            borderWidth));
                }
            });
            swatches[i] = swatch;
            row.addView(swatch);
        }
    }

    /**
     * 构造圆形色块背景：选中态描橙色边，未选中描浅灰细边。
     */
    private android.graphics.drawable.GradientDrawable makeSwatchDrawable(
            int color, boolean selected, int borderWidth) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setColor(color);
        if (selected) {
            d.setStroke(borderWidth, 0xFFFF9800);
        } else {
            d.setStroke((int) (context.getResources().getDisplayMetrics().density), 0xFFBBBBBB);
        }
        return d;
    }

    /**
     * 展示答题记录弹窗：滚动列表按时间倒序展示复述题、推理题与听力题记录，点击卡片展开详情。
     */
    public void showAnswerRecordsDialog() {
        View dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_answer_records, null);
        LinearLayout container = dialogView.findViewById(R.id.ll_answer_records);
        Button clearButton = dialogView.findViewById(R.id.btn_clear_answer_records);

        List<AnswerRecordEntry> entries = collectAnswerRecordEntries();
        if (entries.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("暂无答题记录\n答题完成后会自动记录在这里");
            empty.setTextColor(0xFF8A8A8A);
            empty.setTextSize(15);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(36), 0, dp(36));
            container.addView(empty);
        } else {
            container.addView(buildRecordTableHeader());
            for (AnswerRecordEntry entry : entries) {
                container.addView(buildRecordTableRow(entry));
            }
        }

        final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
                .setTitle("答题记录")
                .setView(dialogView)
                .setNegativeButton("关闭", null)
                .create();

        clearButton.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(context)
                    .setTitle("清空答题记录")
                    .setMessage("确定清空全部答题记录？此操作不可恢复。")
                    .setPositiveButton("清空", (d, w) -> {
                        new RetellingRecordStore().clear();
                        new ChallengeRecordStore().clear();
                        new ListeningRecordStore().clear();
                        dialog.dismiss();
                        showAnswerRecordsDialog();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        dialog.show();
    }

    /** 汇总复述题、非算术题与听力题记录，按答题时间倒序合并，供「答题记录」统一展示。 */
    private List<AnswerRecordEntry> collectAnswerRecordEntries() {
        List<AnswerRecordEntry> entries = new ArrayList<>();
        for (RetellingRecord record : new RetellingRecordStore().getRecords()) {
            entries.add(new AnswerRecordEntry(record));
        }
        for (ChallengeRecord record : new ChallengeRecordStore().getRecords()) {
            entries.add(new AnswerRecordEntry(record));
        }
        for (ListeningRecord record : new ListeningRecordStore().getRecords()) {
            entries.add(new AnswerRecordEntry(record));
        }
        entries.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        return entries;
    }

    /** 复述题 / 非算术题 / 听力题记录的统一展示封装，卡片渲染时按类型取对应字段。 */
    private static final class AnswerRecordEntry {
        final long timestamp;
        final boolean isChallenge;
        final boolean isListening;
        final ChallengeRecord challenge;
        final RetellingRecord retelling;
        final ListeningRecord listening;

        AnswerRecordEntry(ChallengeRecord record) {
            this.timestamp = record.timestamp;
            this.isChallenge = true;
            this.isListening = false;
            this.challenge = record;
            this.retelling = null;
            this.listening = null;
        }

        AnswerRecordEntry(RetellingRecord record) {
            this.timestamp = record.timestamp;
            this.isChallenge = false;
            this.isListening = false;
            this.challenge = null;
            this.retelling = record;
            this.listening = null;
        }

        AnswerRecordEntry(ListeningRecord record) {
            this.timestamp = record.timestamp;
            this.isChallenge = false;
            this.isListening = true;
            this.challenge = null;
            this.retelling = null;
            this.listening = record;
        }
    }

    /** 构造答题记录表头：题型 / 时间 / 耗时 / 结果。 */
    private View buildRecordTableHeader() {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(8), dp(8), dp(8));
        header.setBackgroundColor(0xFFE8E8E8);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        header.addView(buildTableHeaderCell("题型", 1));
        header.addView(buildTableHeaderCell("时间", 2));
        header.addView(buildTableHeaderCell("耗时", 1));
        header.addView(buildTableHeaderCell("结果", 1));
        return header;
    }

    /** 构造单行答题记录：按表头列宽填充单元格，点击弹出详情。 */
    private View buildRecordTableRow(final AnswerRecordEntry entry) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));
        row.setBackground(buildDayBackground(entry.timestamp));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(2);
        row.setLayoutParams(rowParams);
        row.setClickable(true);
        row.setFocusable(true);

        row.addView(buildTableCell(formatRecordType(entry), 1));
        row.addView(buildTableCell(formatRecordTime(entry.timestamp), 2));
        row.addView(buildTableCell(formatRecordElapsed(entry), 1));
        row.addView(buildResultCell(entry));

        row.setOnClickListener(v -> showRecordDetailDialog(entry));
        return row;
    }

    /** 表格普通单元格：单行 + 居中 + 超长省略。 */
    private TextView buildTableCell(String text, int weight) {
        TextView cell = new TextView(context);
        cell.setText(text);
        cell.setTextSize(13);
        cell.setTextColor(0xFF333333);
        cell.setSingleLine(true);
        cell.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cell.setGravity(Gravity.CENTER);
        cell.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, weight));
        return cell;
    }

    /** 表格表头单元格：加粗。 */
    private TextView buildTableHeaderCell(String text, int weight) {
        TextView cell = buildTableCell(text, weight);
        cell.setTextColor(0xFF252525);
        cell.setTypeface(cell.getTypeface(), android.graphics.Typeface.BOLD);
        return cell;
    }

    private String formatRecordType(AnswerRecordEntry entry) {
        if (entry.isChallenge) {
            return "推理";
        }
        return entry.isListening ? "听力" : "复述";
    }

    private String formatRecordTime(long timestamp) {
        return RECORD_TABLE_TIME_FORMAT.format(new java.util.Date(timestamp));
    }

    /** 耗时列：仅推理 / 混合题记录带耗时，其余显示 "-"。 */
    private String formatRecordElapsed(AnswerRecordEntry entry) {
        if (entry.isChallenge && entry.challenge.elapsedSeconds > 0) {
            return formatChallengeDuration(entry.challenge.elapsedSeconds);
        }
        return "-";
    }

    /** 记录是否通过。 */
    private static boolean isPassed(AnswerRecordEntry entry) {
        if (entry.isChallenge) {
            return entry.challenge.passed;
        }
        if (entry.isListening) {
            return entry.listening.passed;
        }
        return entry.retelling.passed;
    }

    /** 结果单元格：绿√ / 红×，带浅色圆角底。 */
    private View buildResultCell(AnswerRecordEntry entry) {
        boolean passed = isPassed(entry);
        TextView symbol = new TextView(context);
        symbol.setText(passed ? "√" : "×");
        symbol.setTextSize(15);
        symbol.setTypeface(symbol.getTypeface(), android.graphics.Typeface.BOLD);
        symbol.setTextColor(passed ? 0xFF2E7D32 : 0xFFD32F2F);
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(passed ? 0xFFE8F5E9 : 0xFFFFEBEE);
        bg.setCornerRadius(dp(6));
        symbol.setBackground(bg);
        symbol.setPadding(dp(7), dp(2), dp(7), dp(2));

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setGravity(Gravity.CENTER);
        wrapper.addView(symbol);
        wrapper.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return wrapper;
    }

    /** 表格行浅色背景色组，同一整天用同色、相邻不同天交替。 */
    private static final int[] RECORD_DAY_BG_COLORS = {
            0xFFF0F4FF, // 浅蓝
            0xFFFFF8E1, // 浅黄
            0xFFF3E5F5, // 浅紫
            0xFFE8F5E9, // 浅绿
            0xFFFFF3E0, // 浅橙
    };

    private android.graphics.drawable.GradientDrawable buildDayBackground(long timestamp) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        long dayIndex = calendar.getTimeInMillis() / (24 * 60 * 60 * 1000L);
        int color = RECORD_DAY_BG_COLORS[(int) Math.floorMod(dayIndex, RECORD_DAY_BG_COLORS.length)];
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(12));
        return bg;
    }

    /** 答题记录使用的完整耗时格式：MM:SS。 */
    private static String formatChallengeDuration(int elapsedSeconds) {
        return DateUtils.formatChallengeDurationFull(elapsedSeconds);
    }

    /** 「我的答案」单字母答案统一转大写显示（如 a → A），其余原样。 */
    private static String normalizeAnswerCase(String answer) {
        if (answer == null) {
            return "";
        }
        String trimmed = answer.trim();
        if (trimmed.length() == 1) {
            char c = trimmed.charAt(0);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                return trimmed.toUpperCase(Locale.US);
            }
        }
        return answer;
    }

    /** 点击表格行弹出完整答题详情。 */
    private void showRecordDetailDialog(final AnswerRecordEntry entry) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(12), dp(20), dp(8));

        content.addView(buildRecordLine("【答题时间】" + DateUtils.formatTime(entry.timestamp), 14, 0xFF252525, 0));
        if (entry.isChallenge) {
            ChallengeRecord r = entry.challenge;
            content.addView(buildRecordLine("【耗时】"
                    + (r.elapsedSeconds > 0 ? formatChallengeDuration(r.elapsedSeconds) : "-"), 13, 0xFF333333, 0));
            content.addView(buildRecordLine("【结果】" + (r.passed ? "√" : "×"), 13,
                    r.passed ? 0xFF2E7D32 : 0xFFD32F2F, 0));
            content.addView(buildRecordLine("【完整题干】\n" + r.question, 13, 0xFF333333, 0));
            if (!r.passed) {
                content.addView(buildRecordLine("【我的答案】\n"
                        + normalizeAnswerCase(r.userAnswer), 13, 0xFF333333, 0));
            }
            content.addView(buildRecordLine("【正确答案】\n" + r.correctAnswer, 13, 0xFF333333, 0));
        } else if (entry.isListening) {
            ListeningRecord r = entry.listening;
            content.addView(buildRecordLine("【题型】听力题", 13, 0xFF333333, 0));
            content.addView(buildRecordLine("【结果】" + (r.passed ? "√" : "×"), 13,
                    r.passed ? 0xFF2E7D32 : 0xFFD32F2F, 0));
            content.addView(buildRecordLine("【完整题干】\n" + r.question, 13, 0xFF333333, 0));
            content.addView(buildRecordLine("【听力原文】\n" + r.transcript, 13, 0xFF333333, 0));
            if (!r.passed) {
                content.addView(buildRecordLine("【我的答案】\n"
                        + normalizeAnswerCase(r.userAnswer), 13, 0xFF333333, 0));
            }
            content.addView(buildRecordLine("【正确答案】\n" + r.correctAnswer, 13, 0xFF333333, 0));
        } else {
            RetellingRecord r = entry.retelling;
            content.addView(buildRecordLine("【题型】复述题", 13, 0xFF333333, 0));
            content.addView(buildRecordLine("【结果】" + (r.passed ? "通过" : "未通过")
                    + " · 得分 " + r.score, 13, 0xFF333333, 0));
            content.addView(buildRecordLine("【完整故事】\n" + r.story, 13, 0xFF333333, 0));
            content.addView(buildRecordLine("【完整回答】\n" + r.recognizedText, 13, 0xFF333333, 0));
            if (r.offlineRecognizedText != null && !r.offlineRecognizedText.isEmpty()) {
                content.addView(buildRecordLine("【离线识别】\n" + r.offlineRecognizedText, 13, 0xFF8A8A8A, 0));
            }
            content.addView(buildRecordLine("维度：内容完整 " + r.coverage
                    + " · 逻辑连贯 " + r.order
                    + " · 事实准确 " + r.accuracy
                    + " · 表达完整 " + r.expression, 12, 0xFF8A8A8A, 0));
            String pronunciation = r.formatPronunciationSummary();
            if (pronunciation != null) {
                content.addView(buildRecordLine("【口语评测】" + pronunciation, 12, 0xFF8A8A8A, 0));
            }
            if (r.feedback != null && !r.feedback.isEmpty()) {
                content.addView(buildRecordLine("【建议】\n" + r.feedback, 12, 0xFF8A8A8A, 0));
            }
        }

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(content);
        scrollView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, dp(400)));

        new android.app.AlertDialog.Builder(context)
                .setTitle("答题详情")
                .setView(scrollView)
                .setNegativeButton("关闭", null)
                .show();
    }

    private TextView buildRecordLine(String text, int sp, int color, int maxLines) {
        TextView tv = new TextView(context);
        tv.setText(text == null ? "" : text);
        tv.setTextColor(color);
        tv.setTextSize(sp);
        tv.setLineSpacing(0f, 1.15f);
        if (maxLines > 0) {
            tv.setMaxLines(maxLines);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        }
        return tv;
    }

    private int dp(int value) {
        return (int) (context.getResources().getDisplayMetrics().density * value);
    }

}
