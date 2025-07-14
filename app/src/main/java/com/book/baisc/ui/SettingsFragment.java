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
        
        return view;
    }

    private void setupLatestApkButton(View view) {
        Button latestApkButton = view.findViewById(R.id.btn_latest_apk);
        latestApkButton.setOnClickListener(v -> {
            settingsDialogManager.showLatestApkDialog();
        });
    }
} 