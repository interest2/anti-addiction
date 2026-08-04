package com.book.mask.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
    private TextView tvFloatingStrictReminderValue;
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
        tvFloatingStrictReminderValue = view.findViewById(R.id.tv_floating_strict_reminder_value);
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

        // 设置算术题难度设置按钮
        View mathDifficultyRow = view.findViewById(R.id.btn_math_difficulty);
        mathDifficultyRow.setOnClickListener(v -> {
            settingsDialogManager.showMathDifficultyDialog();
        });
        
        View reminderStyleRow = view.findViewById(R.id.btn_reminder_style);
        reminderStyleRow.setOnClickListener(v ->
                settingsDialogManager.showReminderStyleDialog(this::onReminderStyleChanged));

        // 设置悬浮窗额外显示日常提醒按钮
        View floatingStrictReminderRow = view.findViewById(
                R.id.btn_floating_strict_reminder);
        floatingStrictReminderRow.setOnClickListener(v ->
                settingsDialogManager.showFloatingStrictReminderDialog(this::updateReminderValues));

        View leisureTimeRow = view.findViewById(R.id.btn_leisure_time);
        leisureTimeRow.setOnClickListener(v -> {
            settingsDialogManager.showLeisureTimeDialog();
        });

        // 临时调试入口：内置音频不同长度识别测试
        View asrTestRow = view.findViewById(R.id.btn_asr_test);
        asrTestRow.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AsrTestActivity.class)));
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

    private void updateReminderValues() {
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
        updateReminderValues();
    }
}
