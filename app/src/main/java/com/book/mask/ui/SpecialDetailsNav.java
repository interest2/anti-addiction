package com.book.mask.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.book.mask.R;
import com.book.mask.config.Share;

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

}
