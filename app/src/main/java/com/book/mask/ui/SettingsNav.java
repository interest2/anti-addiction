package com.book.mask.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
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
import com.book.mask.config.PackageLogManager;
import com.book.mask.config.Share;
import com.book.mask.network.LatestVersionManager;

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
        view.findViewById(R.id.row_install_troubleshooting)
                .setOnClickListener(v -> showInstallTroubleshootingDialog());
        view.findViewById(R.id.row_floating_settings)
                .setOnClickListener(v -> settingsDialogManager.showFloatingPositionDialog());
        view.findViewById(R.id.row_special_details)
                .setOnClickListener(v -> openSpecialDetails());
        view.findViewById(R.id.row_package_log)
                .setOnClickListener(v -> showPackageLogActionsDialog());
    }

    private void openSpecialDetails() {
        getParentFragmentManager().beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        R.anim.slide_in_left,
                        R.anim.slide_out_right
                )
                .replace(R.id.fragment_container, new SpecialDetailsNav())
                .addToBackStack(SpecialDetailsNav.class.getSimpleName())
                .commit();
    }

    private void showVersionUpdateDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_version_update, null);
        TextView versionDetail = dialogView.findViewById(R.id.tv_version_detail);
        versionDetail.setText(buildVersionDetail());
        TextView releaseNotesHint = dialogView.findViewById(R.id.tv_release_notes_hint);
        enableUrlCopy(releaseNotesHint);
        android.app.AlertDialog versionDialog = new android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.version_update)
                .setView(dialogView)
                .setNegativeButton("关闭", null)
                .create();
        Button downloadButton = dialogView.findViewById(R.id.btn_latest_apk);
        downloadButton.setOnClickListener(v -> downloadLatestApk(
                versionDialog,
                versionDetail,
                downloadButton
        ));

        versionDialog.show();
    }

    private void showInstallTroubleshootingDialog() {
        String messageText = getString(R.string.install_troubleshooting_message);
        String linkText = getString(R.string.blog_link_text);
        int linkStart = messageText.indexOf(linkText);
        SpannableString message = new SpannableString(messageText);
        if (linkStart >= 0) {
            message.setSpan(
                    new URLSpan(getString(R.string.install_troubleshooting_url)),
                    linkStart,
                    linkStart + linkText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.install_troubleshooting)
                .setMessage(message)
                .setNegativeButton("关闭", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            TextView messageView = dialog.findViewById(android.R.id.message);
            if (messageView != null) {
                messageView.setMovementMethod(LinkMovementMethod.getInstance());
                messageView.setLinksClickable(true);
            }
        });
        dialog.show();
    }

    private void downloadLatestApk(android.app.AlertDialog versionDialog,
                                   TextView versionDetail,
                                   Button downloadButton) {
        String freshVersion = LatestVersionManager.getFreshLatestVersion();
        if (freshVersion != null) {
            requestApkDownload(freshVersion, versionDialog);
            return;
        }

        downloadButton.setEnabled(false);
        downloadButton.setText(R.string.fetching_latest_version);
        new Thread(() -> {
            String latestVersion = LatestVersionManager.getLatestVersionForDownload();
            versionBadgeHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }

                View view = getView();
                if (versionDialog.isShowing()) {
                    downloadButton.setEnabled(true);
                    downloadButton.setText(R.string.download_latest_apk);
                    versionDetail.setText(buildVersionDetail());
                }
                if (view != null) {
                    updateVersionBadge(view);
                }
                if (latestVersion == null) {
                    android.widget.Toast.makeText(
                            requireContext(),
                            R.string.latest_version_fetch_failed,
                            android.widget.Toast.LENGTH_SHORT
                    ).show();
                    return;
                }
                requestApkDownload(latestVersion, versionDialog);
            });
        }).start();
    }

    private void requestApkDownload(String latestVersion,
                                    android.app.AlertDialog versionDialog) {
        String downloadUrl = LatestVersionManager.buildLatestApkDownloadUrl(latestVersion);
        Intent downloadIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
        try {
            startActivity(downloadIntent);
            versionDialog.dismiss();
        } catch (ActivityNotFoundException e) {
            android.util.Log.e(TAG, "没有可处理安装包下载链接的应用", e);
            android.widget.Toast.makeText(
                    requireContext(),
                    R.string.apk_download_unavailable,
                    android.widget.Toast.LENGTH_SHORT
            ).show();
        }
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

    private void enableUrlCopy(TextView textView) {
        CharSequence text = textView.getText();
        if (!(text instanceof Spanned)) {
            return;
        }

        Spanned spanned = (Spanned) text;
        SpannableString copyableText = new SpannableString(spanned);
        URLSpan[] urlSpans = spanned.getSpans(0, spanned.length(), URLSpan.class);
        for (URLSpan urlSpan : urlSpans) {
            int start = spanned.getSpanStart(urlSpan);
            int end = spanned.getSpanEnd(urlSpan);
            int flags = spanned.getSpanFlags(urlSpan);
            copyableText.removeSpan(urlSpan);
            copyableText.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    copyToClipboard(urlSpan.getURL());
                }
            }, start, end, flags);
        }

        textView.setText(copyableText);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void copyToClipboard(String text) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("text", text));
            android.widget.Toast.makeText(requireContext(),
                    "已复制：" + text,
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
