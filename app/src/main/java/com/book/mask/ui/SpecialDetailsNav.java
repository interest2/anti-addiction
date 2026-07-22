package com.book.mask.ui;

import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.book.mask.R;
import com.book.mask.config.Const;
import com.book.mask.config.InputMethodPackageManager;
import com.book.mask.config.Share;
import com.book.mask.setting.AppSettingsManager;

import java.util.List;

public class SpecialDetailsNav extends Fragment {
    private AppSettingsManager appSettingsManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_special_details, container, false);
        appSettingsManager = new AppSettingsManager(requireContext());

        view.findViewById(R.id.btn_special_details_back)
                .setOnClickListener(v -> getParentFragmentManager().popBackStack());
        view.findViewById(R.id.row_keyboard_whitelist)
                .setOnClickListener(v -> showKeyboardWhitelistDialog());
        view.findViewById(R.id.row_reset_floating)
                .setOnClickListener(v -> showResetFloatingDialog());
        setupDebounceSettings(view);
        return view;
    }

    private void setupDebounceSettings(View view) {
        EditText animationDurationInput = view.findViewById(
                R.id.et_transition_animation_duration_ms
        );
        EditText packageCheckDelayInput = view.findViewById(
                R.id.et_transition_package_check_delay_ms
        );
        View saveButton = view.findViewById(R.id.btn_save_debounce_settings);

        animationDurationInput.setText(String.valueOf(
                appSettingsManager.getTransitionAnimationDurationMs()
        ));
        packageCheckDelayInput.setText(String.valueOf(
                appSettingsManager.getTransitionPackageCheckDelayMs()
        ));

        saveButton.setOnClickListener(v -> {
            Integer animationDuration = readTransitionTimingValue(animationDurationInput);
            Integer packageCheckDelay = readTransitionTimingValue(packageCheckDelayInput);
            if (animationDuration == null || packageCheckDelay == null) {
                return;
            }

            appSettingsManager.setTransitionAnimationDurationMs(animationDuration);
            appSettingsManager.setTransitionPackageCheckDelayMs(packageCheckDelay);
            Toast.makeText(
                    requireContext(),
                    R.string.debounce_settings_saved,
                    Toast.LENGTH_SHORT
            ).show();
        });

        packageCheckDelayInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                saveButton.performClick();
                return true;
            }
            return false;
        });
    }

    private Integer readTransitionTimingValue(EditText input) {
        String value = input.getText() == null ? "" : input.getText().toString().trim();
        if (value.isEmpty()) {
            input.setError(getString(R.string.debounce_value_required));
            return null;
        }

        try {
            int delayMs = Integer.parseInt(value);
            if (delayMs < 0 || delayMs > Const.MAX_TRANSITION_TIMING_SETTING_MS) {
                input.setError(getString(R.string.debounce_value_out_of_range));
                return null;
            }
            input.setError(null);
            return delayMs;
        } catch (NumberFormatException e) {
            input.setError(getString(R.string.debounce_value_out_of_range));
            return null;
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
            Toast.makeText(
                    requireContext(),
                    "所有APP悬浮窗状态已重置",
                    Toast.LENGTH_SHORT
            ).show();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void detectAndSaveCurrentKeyboard(TextView keyboardPackageText) {
        String packageName = getCurrentKeyboardPackageName();
        if (packageName.isEmpty()) {
            Toast.makeText(
                    requireContext(),
                    "未检测到键盘包名，请确认键盘已弹出",
                    Toast.LENGTH_SHORT
            ).show();
            updateKeyboardPackageText(keyboardPackageText);
            return;
        }

        boolean added = InputMethodPackageManager.getInstance().addPackage(packageName);
        updateKeyboardPackageText(keyboardPackageText);
        Toast.makeText(
                requireContext(),
                added ? "已添加键盘包名：" + packageName : "键盘包名已存在：" + packageName,
                Toast.LENGTH_SHORT
        ).show();
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
