package com.book.mask.ui;

import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.book.mask.R;
import com.book.mask.config.InputMethodPackageManager;
import com.book.mask.config.Share;

import java.util.List;

public class SpecialDetailsNav extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_special_details, container, false);

        view.findViewById(R.id.btn_special_details_back)
                .setOnClickListener(v -> getParentFragmentManager().popBackStack());
        view.findViewById(R.id.row_keyboard_whitelist)
                .setOnClickListener(v -> showKeyboardWhitelistDialog());
        view.findViewById(R.id.row_reset_floating)
                .setOnClickListener(v -> showResetFloatingDialog());
        return view;
    }

    private void showKeyboardWhitelistDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_keyboard_whitelist, null);
        TextView keyboardPackageText = dialogView.findViewById(R.id.tv_keyboard_package);
        Button keyboardAllowButton = dialogView.findViewById(R.id.btn_keyboard_allow);

        updateKeyboardPackageText(keyboardPackageText);
        keyboardAllowButton.setOnClickListener(v -> detectAndSaveCurrentKeyboard(keyboardPackageText));

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.keyboard_whitelist)
                .setView(dialogView)
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showResetFloatingDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_reset_floating, null);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.reset_floating_window)
                .setView(dialogView)
                .setNegativeButton("关闭", null)
                .create();

        dialogView.findViewById(R.id.btn_reset_floating_state).setOnClickListener(v -> {
            java.util.Set<String> keys = Share.appManuallyHidden.keySet();
            for (String key : keys) {
                Share.appManuallyHidden.put(key, false);
            }
            dialog.dismiss();
            UiFeedback.show(requireContext(), "所有APP悬浮窗状态已重置");
        });
        dialog.show();
    }

    private void detectAndSaveCurrentKeyboard(TextView keyboardPackageText) {
        String packageName = getCurrentKeyboardPackageName();
        if (packageName.isEmpty()) {
            UiFeedback.showError(
                    keyboardPackageText,
                    "未检测到键盘包名，请确认键盘已弹出"
            );
            updateKeyboardPackageText(keyboardPackageText);
            return;
        }

        boolean added = InputMethodPackageManager.getInstance().addPackage(packageName);
        updateKeyboardPackageText(keyboardPackageText);
        UiFeedback.show(
                keyboardPackageText,
                added ? "已添加键盘包名：" + packageName : "键盘包名已存在：" + packageName
        );
    }

    private String getCurrentKeyboardPackageName() {
        String inputMethodId = Settings.Secure.getString(
                requireContext().getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD
        );
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
        keyboardPackageText.setText(getString(
                R.string.keyboard_whitelist_saved,
                android.text.TextUtils.join("、", packages)
        ));
    }

}
