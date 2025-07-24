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

public class GoalFragment extends Fragment {

    private SettingsManager settingsManager;
    private SettingsDialogManager settingsDialogManager;
    private TextView tvGoalCountdown, tvGoalMotivation;
    private Button btnTagSetting, btnTargetDateSetting;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_goal, container, false);
        settingsManager = new SettingsManager(requireContext());
        settingsDialogManager = new SettingsDialogManager(requireContext(), settingsManager);

        tvGoalCountdown = view.findViewById(R.id.tv_goal_countdown);
        tvGoalMotivation = view.findViewById(R.id.tv_goal_motivation);
        btnTagSetting = view.findViewById(R.id.btn_tag_setting);
        btnTargetDateSetting = view.findViewById(R.id.btn_target_date_setting);

        btnTagSetting.setOnClickListener(v -> settingsDialogManager.showTagSettingDialog());
        btnTargetDateSetting.setOnClickListener(v -> settingsDialogManager.showTargetDateSettingDialog());

        updateGoalInfo();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateGoalInfo();
    }

    private void updateGoalInfo() {
        // 倒计时
        String targetDate = settingsManager.getTargetCompletionDate();
        String countdown = FloatHelper.hintDate(targetDate);
        tvGoalCountdown.setText(countdown.isEmpty() ? "距离目标：--天" : countdown);
        // 激励语
        String motivation = settingsManager.getMotivationTag();
        tvGoalMotivation.setText("激励语：" + (motivation == null ? "--" : motivation));
    }
} 