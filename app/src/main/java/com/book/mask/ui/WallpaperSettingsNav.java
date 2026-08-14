package com.book.mask.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.book.mask.R;
import com.book.mask.lifecycle.WallpaperImageStore;
import com.book.mask.lifecycle.WallpaperKeepAliveService;

/**
 * 壁纸设置子页面：选一张本机照片（或直接选用纯色）作为桌面壁纸，
 * 用动态壁纸把本应用进程留在系统的保活名单里。图片只在本机处理与存储，不经任何网络。
 */
public class WallpaperSettingsNav extends Fragment {

    private static final String TAG = "WallpaperSettingsNav";

    /**
     * 免挑图的纯色预设：偏暗但不死黑，桌面图标压得住又不像关机黑屏，
     * 三色分开（蓝灰 / 松绿 / 星空靛蓝），亮度接近以免并排时轻重不一。
     */
    private static final int[] SOLID_COLORS = {
            0xFF37474F,
            0xFF3E5641,
            0xFF2E3A66,
    };

    private static final int SWATCH_WIDTH_DP = 64;
    private static final int SWATCH_HEIGHT_DP = 44;
    private static final int SWATCH_GAP_DP = 14;
    private static final int SWATCH_CORNER_DP = 12;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 系统相册选择器（Photo Picker），无需任何存储权限
    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhotoLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.PickVisualMedia(),
                    this::onPhotoPicked);

    private boolean processing;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_wallpaper_settings, container, false);

        view.findViewById(R.id.btn_wallpaper_settings_back)
                .setOnClickListener(v -> getParentFragmentManager().popBackStack());
        view.findViewById(R.id.btn_wallpaper_pick)
                .setOnClickListener(v -> pickPhoto());
        view.findViewById(R.id.btn_wallpaper_apply)
                .setOnClickListener(v -> applyWallpaper());
        view.findViewById(R.id.btn_wallpaper_clear)
                .setOnClickListener(v -> showClearConfirmDialog());

        setupSolidColors(view);
        refreshState(view);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // 从系统壁纸预览页返回时，生效状态可能已变化
        View view = getView();
        if (view != null) {
            refreshState(view);
        }
    }

    @Override
    public void onDestroyView() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    private void setupSolidColors(View view) {
        LinearLayout container = view.findViewById(R.id.ll_wallpaper_solid_colors);
        container.removeAllViews();

        float density = getResources().getDisplayMetrics().density;
        int width = Math.round(SWATCH_WIDTH_DP * density);
        int height = Math.round(SWATCH_HEIGHT_DP * density);
        int gap = Math.round(SWATCH_GAP_DP * density);
        for (int i = 0; i < SOLID_COLORS.length; i++) {
            int color = SOLID_COLORS[i];
            View swatch = new View(requireContext());
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(width, height);
            if (i > 0) {
                params.setMarginStart(gap);
            }
            swatch.setLayoutParams(params);
            swatch.setBackground(buildSwatchBackground(color, density));
            swatch.setContentDescription(getString(R.string.wallpaper_solid_color_desc, i + 1));
            swatch.setOnClickListener(v -> applySolidColor(color));
            container.addView(swatch);
        }
    }

    private Drawable buildSwatchBackground(int color, float density) {
        GradientDrawable swatch = new GradientDrawable();
        swatch.setShape(GradientDrawable.RECTANGLE);
        swatch.setCornerRadius(SWATCH_CORNER_DP * density);
        swatch.setColor(color);
        // 描边让纯黑 / 米白这类贴近底色的色块也有清晰边界
        swatch.setStroke(Math.max(1, Math.round(density)), 0xFFDDDDDD);
        return swatch;
    }

    private void pickPhoto() {
        try {
            pickPhotoLauncher.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        } catch (Exception e) {
            Log.e(TAG, "无法打开相册选择界面", e);
            UiFeedback.showError(requireContext(), getString(R.string.wallpaper_pick_unavailable));
        }
    }

    private void onPhotoPicked(@Nullable Uri uri) {
        if (uri == null) {
            // 用户取消
            return;
        }

        Context appContext = requireContext().getApplicationContext();
        saveInBackground(
                () -> WallpaperImageStore.saveFromUri(appContext, uri),
                R.string.wallpaper_saved);
    }

    private void applySolidColor(int color) {
        if (processing) {
            return;
        }

        Context appContext = requireContext().getApplicationContext();
        saveInBackground(
                () -> WallpaperImageStore.saveSolidColor(appContext, color),
                R.string.wallpaper_solid_color_selected);
    }

    private void saveInBackground(BackgroundSave save, @StringRes int successMessage) {
        setProcessing(true);
        new Thread(() -> {
            String error = null;
            try {
                save.run();
            } catch (Exception e) {
                Log.e(TAG, "保存壁纸图片失败", e);
                error = e.getMessage();
            }

            String failure = error;
            mainHandler.post(() -> onImageSaved(failure, successMessage));
        }).start();
    }

    private void onImageSaved(@Nullable String failure, @StringRes int successMessage) {
        if (!isAdded()) {
            return;
        }

        setProcessing(false);
        if (failure != null) {
            UiFeedback.showError(requireContext(), getString(R.string.wallpaper_save_failed));
            return;
        }
        UiFeedback.show(requireContext(), getString(successMessage));
    }

    private void applyWallpaper() {
        Context context = requireContext();
        if (!WallpaperImageStore.hasImage(context)) {
            UiFeedback.showError(context, getString(R.string.wallpaper_need_image_first));
            return;
        }

        try {
            startActivity(WallpaperKeepAliveService.buildApplyIntent(context));
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "系统不支持直接预览动态壁纸，回退到壁纸选择器", e);
            openWallpaperChooser();
        }
    }

    private void openWallpaperChooser() {
        try {
            startActivity(WallpaperKeepAliveService.buildChooserIntent());
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "无法打开系统壁纸设置", e);
            UiFeedback.showError(requireContext(),
                    getString(R.string.wallpaper_chooser_unavailable));
        }
    }

    private void showClearConfirmDialog() {
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.wallpaper_clear)
                .setMessage("清除后，若本应用仍是当前桌面壁纸，桌面将只显示纯色背景。")
                .setPositiveButton("清除", (dialog, which) -> clearPhoto())
                .setNegativeButton("取消", null)
                .show();
    }

    private void clearPhoto() {
        WallpaperImageStore.clear(requireContext().getApplicationContext());
        View view = getView();
        if (view != null) {
            refreshState(view);
        }
        UiFeedback.show(requireContext(), getString(R.string.wallpaper_cleared));
    }

    private void setProcessing(boolean processing) {
        this.processing = processing;
        View view = getView();
        if (view != null) {
            refreshState(view);
        }
    }

    private void refreshState(View view) {
        Context context = requireContext();
        boolean hasImage = WallpaperImageStore.hasImage(context);
        boolean active = hasImage && WallpaperKeepAliveService.isActive(context);

        updatePreview(view, hasImage ? WallpaperImageStore.loadBitmap(context) : null);
        ((TextView) view.findViewById(R.id.tv_wallpaper_status))
                .setText(buildStatusText(hasImage, active));

        Button pickButton = view.findViewById(R.id.btn_wallpaper_pick);
        Button clearButton = view.findViewById(R.id.btn_wallpaper_clear);
        pickButton.setEnabled(!processing);
        clearButton.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        clearButton.setEnabled(!processing);
        updateApplyButton(view.findViewById(R.id.btn_wallpaper_apply), hasImage, active);
        updateSolidColorsEnabled(view);
    }

    /**
     * 没选图时「应用」无从谈起，整个按钮隐藏，只留「选择照片」；
     * 已生效时换图由壁纸引擎即时重绘，再跳一趟系统壁纸页没有意义，故禁用。
     * 背景与文字都是固定色，禁用不会自动变灰，需手动调淡以免看着可点却点不动。
     */
    private void updateApplyButton(Button applyButton, boolean hasImage, boolean active) {
        applyButton.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        applyButton.setEnabled(!processing && !active);
        applyButton.setAlpha(active ? 0.65f : 1f);
    }

    private void updateSolidColorsEnabled(View view) {
        LinearLayout container = view.findViewById(R.id.ll_wallpaper_solid_colors);
        for (int i = 0; i < container.getChildCount(); i++) {
            View swatch = container.getChildAt(i);
            swatch.setEnabled(!processing);
            swatch.setAlpha(processing ? 0.4f : 1f);
        }
    }

    private void updatePreview(View view, @Nullable Bitmap bitmap) {
        ImageView preview = view.findViewById(R.id.iv_wallpaper_preview);
        preview.setImageBitmap(bitmap);
        view.findViewById(R.id.tv_wallpaper_preview_empty)
                .setVisibility(bitmap == null ? View.VISIBLE : View.GONE);
    }

    private String buildStatusText(boolean hasImage, boolean active) {
        if (processing) {
            return getString(R.string.wallpaper_processing);
        }
        if (!hasImage) {
            return getString(R.string.wallpaper_status_no_image);
        }
        return active
                ? getString(R.string.wallpaper_status_active)
                : getString(R.string.wallpaper_status_not_applied);
    }

    /** 后台保存动作，允许抛出受检异常由 {@link #saveInBackground} 统一兜住。 */
    private interface BackgroundSave {
        void run() throws Exception;
    }
}
