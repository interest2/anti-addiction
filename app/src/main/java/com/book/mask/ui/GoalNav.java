package com.book.mask.ui;

import android.os.Bundle;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.book.mask.R;
import com.book.mask.constant.Const;
import com.book.mask.personalize.RelaxManager;
import com.book.mask.personalize.AppSettingsManager;
import com.book.mask.floating.FloatService;
import com.book.mask.reminder.config.ReminderProviderConfig;
import com.book.mask.reminder.config.ReminderProviderConfigStore;
import com.book.mask.util.DateUtils;

public class GoalNav extends Fragment {

    private RelaxManager relaxManager;
    private AppSettingsManager appSettingsManager;
    private SettingsDialogManager settingsDialogManager;
    private TextView tvGoalCountdown;
    private TextView tvReminderStyleValue;
    private TextView tvReminderProviderValue;
    private TextView tvGlobalHintSourceValue;
    private TextView tvFloatingStrictReminderValue;
    private TextView tvMathDifficultyValue;
    private Button btnTagSetting, btnTargetDateSetting;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_goal, container, false);
        relaxManager = new RelaxManager(requireContext());
        appSettingsManager = new AppSettingsManager(requireContext());
        settingsDialogManager = new SettingsDialogManager(requireContext(), relaxManager);

        // 初始化控件
        tvGoalCountdown = view.findViewById(R.id.tv_goal_countdown);
        tvReminderStyleValue = view.findViewById(R.id.tv_reminder_style_value);
        tvReminderProviderValue = view.findViewById(R.id.tv_reminder_provider_value);
        tvGlobalHintSourceValue = view.findViewById(R.id.tv_global_hint_source_value);
        tvFloatingStrictReminderValue = view.findViewById(R.id.tv_floating_strict_reminder_value);
        tvMathDifficultyValue = view.findViewById(R.id.tv_math_difficulty_value);
        btnTagSetting = view.findViewById(R.id.btn_tag_setting);
        btnTargetDateSetting = view.findViewById(R.id.btn_target_date_setting);

        // 设置按钮点击事件
        btnTagSetting.setOnClickListener(v ->
                settingsDialogManager.showTagSettingDialog(this::onMotivationTagChanged));
        btnTargetDateSetting.setOnClickListener(v -> {
            settingsDialogManager.showTargetDateSettingDialog(this::updateGoalInfo);
        });

        View reminderProviderRow = view.findViewById(R.id.btn_reminder_provider);
        View reminderProviderDivider = view.findViewById(R.id.divider_reminder_provider);
        int reminderProviderVisibility = Const.REMINDER_PROVIDER_SETTINGS_ENABLED
                ? View.VISIBLE
                : View.GONE;
        reminderProviderRow.setVisibility(reminderProviderVisibility);
        reminderProviderDivider.setVisibility(reminderProviderVisibility);
        if (Const.REMINDER_PROVIDER_SETTINGS_ENABLED) {
            reminderProviderRow.setOnClickListener(v -> openReminderProviderSettings());
        }

        // 算术题难度：点击直接弹出难度设置弹窗，设置完成后刷新右侧显示的当前难度
        view.findViewById(R.id.btn_math_difficulty).setOnClickListener(v ->
                settingsDialogManager.showArithmeticDifficultyDialog(this::updateMathDifficultyValue));

        View reminderStyleRow = view.findViewById(R.id.btn_reminder_style);
        reminderStyleRow.setOnClickListener(v ->
                settingsDialogManager.showReminderStyleDialog(this::onReminderStyleChanged));

        view.findViewById(R.id.btn_global_hint_source)
                .setOnClickListener(v -> showGlobalHintSourceDialog());

        // 设置悬浮窗额外显示日常提醒按钮
        View floatingStrictReminderRow = view.findViewById(
                R.id.btn_floating_strict_reminder);
        floatingStrictReminderRow.setOnClickListener(v ->
                settingsDialogManager.showFloatingStrictReminderDialog(this::updateReminderValues));

        View leisureTimeRow = view.findViewById(R.id.btn_leisure_time);
        leisureTimeRow.setOnClickListener(v -> {
            settingsDialogManager.showLeisureTimeDialog();
        });

        // 答题记录（可用 Const.ANSWER_RECORDS_ENABLED 控制入口显隐，便于后续恢复）
        View answerRecordsRow = view.findViewById(R.id.btn_answer_records);
        View answerRecordsDivider = view.findViewById(R.id.divider_answer_records);
        int answerRecordsVisibility = Const.ANSWER_RECORDS_ENABLED
                ? View.VISIBLE
                : View.GONE;
        answerRecordsRow.setVisibility(answerRecordsVisibility);
        answerRecordsDivider.setVisibility(answerRecordsVisibility);
        if (Const.ANSWER_RECORDS_ENABLED) {
            answerRecordsRow.setOnClickListener(v -> {
                settingsDialogManager.showAnswerRecordsDialog();
            });
        }

        return view;
    }

    private void openReminderProviderSettings() {
        getParentFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        0,
                        0
                )
                .replace(R.id.fragment_container, new ReminderProviderSettingsNav())
                .addToBackStack(ReminderProviderSettingsNav.class.getSimpleName())
                .commit();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateGoalInfo();
    }

    private void onMotivationTagChanged() {
        updateGoalInfo();
        FloatService.notifyReminderContentChanged();
    }

    private void onReminderStyleChanged() {
        updateReminderValues();
        FloatService.notifyReminderContentChanged();
    }

    private void showGlobalHintSourceDialog() {
        String[] options = {Const.CUSTOM_HINT_SOURCE, Const.DEFAULT_HINT_SOURCE};
        int checkedItem = Const.CUSTOM_HINT_SOURCE.equals(appSettingsManager.getGlobalHintSource())
                ? 0 : 1;
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("将作用于所有目标APP的默认警示语")
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) {
                        showGlobalHintCustomTextDialog();
                    } else {
                        appSettingsManager.setGlobalHintSource(Const.DEFAULT_HINT_SOURCE, null);
                        onGlobalHintSourceChanged();
                        UiFeedback.show(requireContext(), "已选择大模型作为悬浮窗警示语来源");
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showGlobalHintCustomTextDialog() {
        EditText input = new EditText(requireContext());
        input.setHint("请输入自定义警示语（不超过100字）");
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});
        String currentText = appSettingsManager.getGlobalHintCustomText();
        input.setText(currentText);
        input.setSelection(input.length());

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
                .setTitle("输入自定义警示语")
                .setMessage("此警示语将用于所有 APP")
                .setView(input)
                .setPositiveButton("确定", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    input.setError(null);
                    String customText = input.getText().toString().trim();
                    if (customText.isEmpty()) {
                        UiFeedback.showInputError(input, "请输入内容");
                        return;
                    }
                    appSettingsManager.setGlobalHintSource(Const.CUSTOM_HINT_SOURCE, customText);
                    onGlobalHintSourceChanged();
                    dialog.dismiss();
                    UiFeedback.show(requireContext(), "自定义警示语设置成功");
                }));
        dialog.show();
    }

    private void onGlobalHintSourceChanged() {
        updateReminderValues();
        FloatService.notifyHintSourceChanged();
    }

    private void updateReminderValues() {
        tvGlobalHintSourceValue.setText(appSettingsManager.getGlobalHintSource());
        String style = appSettingsManager.getReminderStyle();
        tvReminderStyleValue.setText("自定义".equals(style)
                ? appSettingsManager.getReminderCustomStyle()
                : style);

        ReminderProviderConfig provider = new ReminderProviderConfigStore().getActiveConfig();
        tvReminderProviderValue.setText(provider.isOfficial()
                ? "默认"
                : provider.getModel());

        String strictReminder = appSettingsManager.getFloatingStrictReminder();
        tvFloatingStrictReminderValue.setText(strictReminder.isEmpty()
                ? Const.DEFAULT_STRICT_REMINDER
                : strictReminder);
    }

    private void updateGoalInfo() {
        // 目标标签
        String tag = appSettingsManager.getMotivationTag();
        btnTagSetting.setText(tag == null || tag.isEmpty() ? "目标标签设置" : tag);
        // 目标日期
        String date = appSettingsManager.getTargetCompletionDate();
        btnTargetDateSetting.setText((date == null || date.isEmpty() || "待设置".equals(date)) ? "目标日期" : date);
        // 倒计时
        tvGoalCountdown.setText(DateUtils.countdownDate(date));
        // 算术题难度
        updateMathDifficultyValue();
        updateReminderValues();
    }

    private void updateMathDifficultyValue() {
        tvMathDifficultyValue.setText(settingsDialogManager.getMathDifficultyModeLabel());
    }
}
