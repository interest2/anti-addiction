package com.book.mask.ui;

import android.os.Bundle;
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
import com.book.mask.config.PackageLogManager;
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
        view.findViewById(R.id.row_reset_floating)
                .setOnClickListener(v -> showResetFloatingDialog());
        view.findViewById(R.id.row_package_log)
                .setOnClickListener(v -> showPackageLogActionsDialog());
        return view;
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

    private void showPackageLogActionsDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_package_log_actions, null);
        ToggleButton packageLogToggle = dialogView.findViewById(R.id.toggle_package_log);
        packageLogToggle.setChecked(PackageLogManager.getInstance().isEnabled());
        packageLogToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PackageLogManager.getInstance().setEnabled(isChecked);
            UiFeedback.show(
                    requireContext(),
                    isChecked ? "已开启包名日志" : "已关闭包名日志"
            );
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
                btnCopy.setOnClickListener(b -> copyToClipboard(b, pkg));
                container.addView(itemView);
            }
        }

        new android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("确定", null)
            .show();
    }

    private void copyToClipboard(View feedbackAnchor, String text) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("text", text));
            UiFeedback.show(feedbackAnchor, "已复制：" + text);
        }
    }

}
