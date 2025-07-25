package com.book.baisc.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.book.baisc.R;
import com.book.baisc.config.SettingsManager;
import com.book.baisc.floating.FloatHelper;

public class GoalNav extends Fragment {

    private SettingsManager settingsManager;
    private SettingsDialogManager settingsDialogManager;
    private TextView tvGoalCountdown;
    private Button btnTagSetting, btnTargetDateSetting;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_goal, container, false);
        settingsManager = new SettingsManager(requireContext());
        settingsDialogManager = new SettingsDialogManager(requireContext(), settingsManager);

        tvGoalCountdown = view.findViewById(R.id.tv_goal_countdown);
        btnTagSetting = view.findViewById(R.id.btn_tag_setting);
        btnTargetDateSetting = view.findViewById(R.id.btn_target_date_setting);

        btnTagSetting.setOnClickListener(v -> {
            settingsDialogManager.showTagSettingDialog(this::updateGoalInfo);
        });
        btnTargetDateSetting.setOnClickListener(v -> {
            settingsDialogManager.showTargetDateSettingDialog(this::updateGoalInfo);
        });
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateGoalInfo();
    }

    private void updateGoalInfo() {
        // 目标标签
        String tag = settingsManager.getMotivationTag();
        btnTagSetting.setText(tag == null || tag.isEmpty() ? "目标标签设置" : tag);
        // 目标日期
        String date = settingsManager.getTargetCompletionDate();
        btnTargetDateSetting.setText((date == null || date.isEmpty() || "待设置".equals(date)) ? "目标日期" : date);
        // 倒计时
        String countdown = FloatHelper.hintDate(date);
        tvGoalCountdown.setText(countdown.isEmpty() ? "距离目标：--天" : countdown);
    }
} 