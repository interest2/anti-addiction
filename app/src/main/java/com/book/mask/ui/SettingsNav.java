package com.book.mask.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.book.mask.R;
import com.book.mask.setting.RelaxManager;
import com.book.mask.config.PackageLogManager;
import com.book.mask.config.Share;

import java.util.List;

public class SettingsNav extends Fragment {
    private static final String TAG = "SettingsNav";

    private RelaxManager relaxManager;
    private SettingsDialogManager settingsDialogManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        
        // 初始化设置管理器
        relaxManager = new RelaxManager(requireContext());
        settingsDialogManager = new SettingsDialogManager(requireContext(), relaxManager);
        setupLatestApkButton(view);

        // 设置版本信息小字
        TextView tvVersionDetail = view.findViewById(R.id.tv_version_detail);

        // 获取当前版本信息
        String localVer = "";
        try {
            localVer = requireContext()
                    .getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0)
                    .versionName;
        } catch (Exception e) {
            localVer = "未成功获取";
        }

        String remoteVer = Share.latestVersion;
        boolean isLatest = localVer.equals(remoteVer);

        String hintVersion = "";
        if(isLatest){
            hintVersion = "当前已是最新版本（" + localVer + "）";
        }else {
            hintVersion = "当前版本 " + localVer + "，最新发布 " + remoteVer;
        }

        tvVersionDetail.setText(hintVersion);

        // 设置版本更新红色小圆点
        setupVersionUpdateRedDot(view, isLatest);

        // 设置悬浮窗位置按钮
        setupFloatingPositionButton(view);
        // 设置重置所有APP悬浮窗状态按钮
        Button resetFloatingStateButton = view.findViewById(R.id.btn_reset_floating_state);
        resetFloatingStateButton.setOnClickListener(v -> {
            java.util.Set<String> keys = Share.appManuallyHidden.keySet();
            for (String key : keys) {
                Share.appManuallyHidden.put(key, false);
            }
            android.widget.Toast.makeText(requireContext(), "所有APP悬浮窗状态已重置", android.widget.Toast.LENGTH_SHORT).show();
        });

        // 包名日志开关（拟物 ToggleButton，沿用首页卡片样式）
        android.widget.ToggleButton packageLogToggle = view.findViewById(R.id.toggle_package_log);
        packageLogToggle.setChecked(PackageLogManager.getInstance().isEnabled());
        packageLogToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PackageLogManager.getInstance().setEnabled(isChecked);
            android.widget.Toast.makeText(requireContext(),
                    isChecked ? "已开启包名日志" : "已关闭包名日志",
                    android.widget.Toast.LENGTH_SHORT).show();
        });

        // 包名日志按钮
        Button packageLogButton = view.findViewById(R.id.btn_package_log);
        packageLogButton.setOnClickListener(v -> showPackageLogDialog());
        return view;
    }

    private void showPackageLogDialog() {
        List<String> logs = PackageLogManager.getInstance().getLogs();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View dialogView = inflater.inflate(R.layout.dialog_package_log, null);
        android.widget.LinearLayout container = dialogView.findViewById(R.id.ll_log_items);

        if (logs.isEmpty()) {
            TextView emptyText = new TextView(requireContext());
            emptyText.setText("（暂无记录）");
            emptyText.setTextSize(14);
            container.addView(emptyText);
        } else {
            for (int i = 0; i < logs.size(); i++) {
                final String pkg = logs.get(i);
                View itemView = inflater.inflate(R.layout.item_package_log, container, false);
                TextView tvText = itemView.findViewById(R.id.tv_log_text);
                Button btnCopy = itemView.findViewById(R.id.btn_copy);
                tvText.setText((i + 1) + ". " + pkg);
                btnCopy.setOnClickListener(b -> copyToClipboard(pkg));
                container.addView(itemView);
            }
        }

        new android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("确定", null)
            .show();
    }

    private void copyToClipboard(String packageName) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("package", packageName));
            android.widget.Toast.makeText(requireContext(),
                    "已复制：" + packageName,
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void setupLatestApkButton(View view) {
        Button latestApkButton = view.findViewById(R.id.btn_latest_apk);
        latestApkButton.setOnClickListener(v -> {
            settingsDialogManager.showLatestApkDialog();
        });
    }

    private void setupVersionUpdateRedDot(View view, boolean isLatest) {
        RelativeLayout redDot = view.findViewById(R.id.iv_version_update_red_dot);
        if (redDot != null) {
            if (isLatest) {
                redDot.setVisibility(View.GONE);
            } else {
                redDot.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setupFloatingPositionButton(View view) {
        Button floatingPositionButton = view.findViewById(R.id.btn_floating_position);
        floatingPositionButton.setOnClickListener(v -> {
            settingsDialogManager.showFloatingPositionDialog();
        });
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