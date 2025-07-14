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

public class HomeFragment extends Fragment {

    private SettingsManager settingsManager;
    private SettingsDialogManager settingsDialogManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        // 初始化设置管理器
        settingsManager = new SettingsManager(requireContext());
        settingsDialogManager = new SettingsDialogManager(requireContext(), settingsManager);
        
        // 设置按钮点击事件
        setupTimeSettingButtons(view);
        setupTagSettingButton(view);
        setupTargetDateSettingButton(view);
        
        // 更新UI状态
        updateCasualButtonState(view);
        updateCasualCountDisplay(view);
        updateTagButtonText(view);
        updateTargetDateButtonText(view);
        
        return view;
    }

    private void setupTimeSettingButtons(View view) {
        Button dailyButton = view.findViewById(R.id.btn_daily_time_setting);
        dailyButton.setOnClickListener(v -> {
            settingsDialogManager.showTimeSettingDialog(true); // true for daily
        });
        
        Button casualButton = view.findViewById(R.id.btn_casual_time_setting);
        casualButton.setOnClickListener(v -> {
            settingsDialogManager.showTimeSettingDialog(false); // false for casual
        });
    }

    private void setupTagSettingButton(View view) {
        Button tagButton = view.findViewById(R.id.btn_tag_setting);
        tagButton.setOnClickListener(v -> settingsDialogManager.showTagSettingDialog());
    }

    private void setupTargetDateSettingButton(View view) {
        Button targetDateButton = view.findViewById(R.id.btn_target_date_setting);
        targetDateButton.setOnClickListener(v -> settingsDialogManager.showTargetDateSettingDialog());
    }

    private void updateCasualButtonState(View view) {
        Button casualButton = view.findViewById(R.id.btn_casual_time_setting);
        settingsDialogManager.updateCasualButtonState(casualButton);
    }

    private void updateCasualCountDisplay(View view) {
        TextView countText = view.findViewById(R.id.tv_casual_count);
        settingsDialogManager.updateCasualCountDisplay(countText);
    }

    private void updateTagButtonText(View view) {
        Button tagButton = view.findViewById(R.id.btn_tag_setting);
        settingsDialogManager.updateTagButtonText(tagButton);
    }

    private void updateTargetDateButtonText(View view) {
        Button targetDateButton = view.findViewById(R.id.btn_target_date_setting);
        settingsDialogManager.updateDateButtonText(targetDateButton);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每次回到前台时更新UI状态
        if (getView() != null) {
            updateCasualButtonState(getView());
            updateCasualCountDisplay(getView());
            updateTagButtonText(getView());
            updateTargetDateButtonText(getView());
        }
    }
    
    /**
     * 供外部调用的方法，用于更新目标日期按钮文本
     */
    public void updateTargetDateButtonText() {
        if (getView() != null) {
            updateTargetDateButtonText(getView());
        }
    }
} 