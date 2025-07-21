package com.book.baisc.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.book.baisc.R;
import com.book.baisc.config.SettingsManager;

public class SettingsFragment extends Fragment {

    private SettingsManager settingsManager;
    private SettingsDialogManager settingsDialogManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        
        // 初始化设置管理器
        settingsManager = new SettingsManager(requireContext());
        settingsDialogManager = new SettingsDialogManager(requireContext(), settingsManager);
        
        // 设置最新安装包地址按钮
        setupLatestApkButton(view);
        
        // 设置悬浮窗位置按钮
        setupFloatingPositionButton(view);
        
        // 设置目标设置按钮
        setupGoalSettingButtons(view);
        
        // 更新UI状态
        updateGoalButtonTexts(view);

        // 设置重置所有APP悬浮窗状态按钮
        Button resetFloatingStateButton = view.findViewById(R.id.btn_reset_floating_state);
        resetFloatingStateButton.setOnClickListener(v -> {
            // 将Share.appManuallyHidden所有key的value设为false
            java.util.Set<String> keys = com.book.baisc.config.Share.appManuallyHidden.keySet();
            for (String key : keys) {
                com.book.baisc.config.Share.appManuallyHidden.put(key, false);
            }
            android.widget.Toast.makeText(requireContext(), "所有APP悬浮窗状态已重置", android.widget.Toast.LENGTH_SHORT).show();
        });
        
        return view;
    }

    private void setupLatestApkButton(View view) {
        Button latestApkButton = view.findViewById(R.id.btn_latest_apk);
        latestApkButton.setOnClickListener(v -> {
            settingsDialogManager.showLatestApkDialog();
        });
    }

    private void setupFloatingPositionButton(View view) {
        Button floatingPositionButton = view.findViewById(R.id.btn_floating_position);
        floatingPositionButton.setOnClickListener(v -> {
            settingsDialogManager.showFloatingPositionDialog();
        });
    }
    
    private void setupGoalSettingButtons(View view) {
        Button tagButton = view.findViewById(R.id.btn_tag_setting);
        tagButton.setOnClickListener(v -> settingsDialogManager.showTagSettingDialog());
        
        Button targetDateButton = view.findViewById(R.id.btn_target_date_setting);
        targetDateButton.setOnClickListener(v -> settingsDialogManager.showTargetDateSettingDialog());
    }
    
    private void updateGoalButtonTexts(View view) {
        Button tagButton = view.findViewById(R.id.btn_tag_setting);
        settingsDialogManager.updateTagButtonText(tagButton);
        
        Button targetDateButton = view.findViewById(R.id.btn_target_date_setting);
        settingsDialogManager.updateDateButtonText(targetDateButton);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // 每次回到前台时更新UI状态
        if (getView() != null) {
            updateGoalButtonTexts(getView());
        }
    }
} 