package com.book.mask.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.book.mask.R;
import com.book.mask.setting.RelaxManager;
import com.book.mask.config.InputMethodPackageManager;
import com.book.mask.config.PackageLogManager;
import com.book.mask.config.Share;

import java.util.List;

public class SettingsNav extends Fragment {
    private static final String TAG = "SettingsNav";
    private static final long VERSION_BADGE_REFRESH_DELAY_MS = 500L;

    private RelaxManager relaxManager;
    private SettingsDialogManager settingsDialogManager;
    private final Handler versionBadgeHandler = new Handler(Looper.getMainLooper());
    private final Runnable versionBadgeRefresh = new Runnable() {
        @Override
        public void run() {
            View view = getView();
            if (view == null) {
                return;
            }
            updateVersionBadge(view);
            if (isVersionStatusPending()) {
                versionBadgeHandler.postDelayed(this, VERSION_BADGE_REFRESH_DELAY_MS);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        
        // 初始化设置管理器
        relaxManager = new RelaxManager(requireContext());
        settingsDialogManager = new SettingsDialogManager(requireContext(), relaxManager);

        setupMenuEntries(view);
        updateVersionBadge(view);
        return view;
    }

    private void setupMenuEntries(View view) {
        view.findViewById(R.id.row_version_update)
                .setOnClickListener(v -> showVersionUpdateDialog());
        view.findViewById(R.id.row_floating_settings)
                .setOnClickListener(v -> settingsDialogManager.showFloatingPositionDialog());
        view.findViewById(R.id.row_keyboard_whitelist)
                .setOnClickListener(v -> showKeyboardWhitelistDialog());
        view.findViewById(R.id.row_reset_floating)
                .setOnClickListener(v -> showResetFloatingDialog());
        view.findViewById(R.id.row_package_log)
                .setOnClickListener(v -> showPackageLogActionsDialog());
    }

    private void showVersionUpdateDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_version_update, null);
        TextView versionDetail = dialogView.findViewById(R.id.tv_version_detail);
        versionDetail.setText(buildVersionDetail());
        android.app.AlertDialog versionDialog = new android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.version_update)
                .setView(dialogView)
                .setNegativeButton("关闭", null)
                .create();
        dialogView.findViewById(R.id.btn_latest_apk)
                .setOnClickListener(v -> settingsDialogManager.showLatestApkDialog(versionDialog::dismiss));

        versionDialog.show();
    }

    private String buildVersionDetail() {
        String localVersion = getLocalVersion();
        String remoteVersion = Share.latestVersion;
        if (remoteVersion == null || remoteVersion.trim().isEmpty()) {
            return "当前版本 " + localVersion + "，正在获取最新版本";
        }
        if ("获取失败".equals(remoteVersion)) {
            return "当前版本 " + localVersion + "，最新版本获取失败";
        }
        if (localVersion.equals(remoteVersion)) {
            return "当前已是最新版本（" + localVersion + "）";
        }
        return "当前版本 " + localVersion + "，最新发布 " + remoteVersion;
    }

    private String getLocalVersion() {
        try {
            return requireContext()
                    .getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0)
                    .versionName;
        } catch (Exception e) {
            android.util.Log.w(TAG, "读取本地版本失败", e);
            return "未成功获取";
        }
    }

    private void showKeyboardWhitelistDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_keyboard_whitelist, null);
        TextView keyboardPackageText = dialogView.findViewById(R.id.tv_keyboard_package);
        Button keyboardAllowButton = dialogView.findViewById(R.id.btn_keyboard_allow);

        updateKeyboardPackageText(keyboardPackageText);
        keyboardAllowButton.setOnClickListener(v -> detectAndSaveCurrentKeyboard(keyboardPackageText));

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("键盘白名单")
                .setView(dialogView)
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showResetFloatingDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_reset_floating, null);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
                .setTitle("重置悬浮窗")
                .setView(dialogView)
                .setNegativeButton("关闭", null)
                .create();

        dialogView.findViewById(R.id.btn_reset_floating_state).setOnClickListener(v -> {
            java.util.Set<String> keys = Share.appManuallyHidden.keySet();
            for (String key : keys) {
                Share.appManuallyHidden.put(key, false);
            }
            android.widget.Toast.makeText(requireContext(),
                    "所有APP悬浮窗状态已重置",
                    android.widget.Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void showPackageLogActionsDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_package_log_actions, null);
        ToggleButton packageLogToggle = dialogView.findViewById(R.id.toggle_package_log);
        packageLogToggle.setChecked(PackageLogManager.getInstance().isEnabled());
        packageLogToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PackageLogManager.getInstance().setEnabled(isChecked);
            android.widget.Toast.makeText(requireContext(),
                    isChecked ? "已开启包名日志" : "已关闭包名日志",
                    android.widget.Toast.LENGTH_SHORT).show();
        });
        dialogView.findViewById(R.id.btn_package_log)
                .setOnClickListener(v -> showPackageLogDialog());

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("包名日志")
                .setView(dialogView)
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showPackageLogDialog() {
        List<String> logs = PackageLogManager.getInstance().getLogs();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View dialogView = inflater.inflate(R.layout.dialog_package_log, null);
        android.widget.LinearLayout container = dialogView.findViewById(R.id.ll_log_items);

        if (logs.isEmpty()) {
            TextView emptyText = new TextView(requireContext());
            emptyText.setText("（暂无记录，请打开记录包名的开关）");
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

    private void updateVersionBadge(View view) {
        boolean hasUpdate = false;
        String remoteVersion = Share.latestVersion;
        if (remoteVersion != null
                && !remoteVersion.trim().isEmpty()
                && !"获取失败".equals(remoteVersion)) {
            hasUpdate = !remoteVersion.equals(getLocalVersion());
        }

        View redDot = view.findViewById(R.id.iv_version_update_red_dot);
        redDot.setVisibility(hasUpdate ? View.VISIBLE : View.GONE);
    }

    private boolean isVersionStatusPending() {
        return Share.latestVersion == null || Share.latestVersion.trim().isEmpty();
    }

    private void detectAndSaveCurrentKeyboard(TextView keyboardPackageText) {
        String packageName = getCurrentKeyboardPackageName();
        if (packageName.isEmpty()) {
            android.widget.Toast.makeText(requireContext(),
                    "未检测到键盘包名，请确认键盘已弹出",
                    android.widget.Toast.LENGTH_SHORT).show();
            updateKeyboardPackageText(keyboardPackageText);
            return;
        }

        boolean added = InputMethodPackageManager.getInstance().addPackage(packageName);
        updateKeyboardPackageText(keyboardPackageText);
        android.widget.Toast.makeText(requireContext(),
                added ? "已添加键盘包名：" + packageName : "键盘包名已存在：" + packageName,
                android.widget.Toast.LENGTH_SHORT).show();
    }

    private String getCurrentKeyboardPackageName() {
        String inputMethodId = Settings.Secure.getString(
                requireContext().getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD);
        if (inputMethodId == null || inputMethodId.trim().isEmpty()) {
            return "";
        }

        int separatorIndex = inputMethodId.indexOf('/');
        if (separatorIndex <= 0) {
            return inputMethodId.trim();
        }
        return inputMethodId.substring(0, separatorIndex).trim();
    }

    private void updateKeyboardPackageText(TextView keyboardPackageText) {
        List<String> packages = InputMethodPackageManager.getInstance().getPackages();
        if (packages.isEmpty()) {
            keyboardPackageText.setVisibility(View.GONE);
            return;
        }

        keyboardPackageText.setVisibility(View.VISIBLE);
        keyboardPackageText.setText("已手动免屏蔽：" + android.text.TextUtils.join("、", packages));
    }
    
    @Override
    public void onResume() {
        super.onResume();
        versionBadgeHandler.removeCallbacks(versionBadgeRefresh);
        versionBadgeHandler.post(versionBadgeRefresh);
    }

    @Override
    public void onPause() {
        versionBadgeHandler.removeCallbacks(versionBadgeRefresh);
        super.onPause();
    }
} 
