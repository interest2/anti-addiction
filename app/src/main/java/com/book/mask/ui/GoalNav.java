package com.book.mask.ui;

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
import com.book.mask.floating.FloatHelper;

public class GoalNav extends Fragment {

    private RelaxManager relaxManager;
    private AppSettingsManager appSettingsManager;
    private SettingsDialogManager settingsDialogManager;
    private TextView tvGoalCountdown;
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
        btnTagSetting = view.findViewById(R.id.btn_tag_setting);
        btnTargetDateSetting = view.findViewById(R.id.btn_target_date_setting);

        // 设置按钮点击事件
        btnTagSetting.setOnClickListener(v -> {
            settingsDialogManager.showTagSettingDialog(this::updateGoalInfo);
        });
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
        
        // 设置悬浮窗额外显示日常提醒按钮
        View floatingStrictReminderRow = view.findViewById(
                R.id.btn_floating_strict_reminder);
        floatingStrictReminderRow.setOnClickListener(v -> {
            settingsDialogManager.showFloatingStrictReminderDialog();
        });

        View leisureTimeRow = view.findViewById(R.id.btn_leisure_time);
        leisureTimeRow.setOnClickListener(v -> {
            settingsDialogManager.showLeisureTimeDialog();
        });
        return view;
    }

    private void openReminderProviderSettings() {
        getParentFragmentManager().beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
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

    private void updateGoalInfo() {
        // 目标标签
        String tag = appSettingsManager.getMotivationTag();
        btnTagSetting.setText(tag == null || tag.isEmpty() ? "目标标签设置" : tag);
        // 目标日期
        String date = appSettingsManager.getTargetCompletionDate();
        btnTargetDateSetting.setText((date == null || date.isEmpty() || "待设置".equals(date)) ? "目标日期" : date);
        // 倒计时
        String countdown = FloatHelper.hintDate(date);
        tvGoalCountdown.setText(countdown.isEmpty() ? "距离目标：--天" : countdown);
    }
}
