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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.book.mask.R;
import com.book.mask.personalize.BackupManager;
import com.book.mask.personalize.RelaxManager;
import com.book.mask.config.Share;
import com.book.mask.network.LatestVersionManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsNav extends Fragment {
    private static final String TAG = "SettingsNav";
    private static final long VERSION_BADGE_REFRESH_DELAY_MS = 500L;

    private RelaxManager relaxManager;
    private SettingsDialogManager settingsDialogManager;

    // 待写入用户所选文件的备份 JSON，点击导出时生成、写入完成后清空
    private String pendingBackupJson;
    private final ActivityResultLauncher<String> createBackupLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("application/json"),
                    this::onBackupDocumentCreated);
    private final ActivityResultLauncher<String[]> openBackupLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    this::onBackupDocumentPicked);

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
        view.findViewById(R.id.row_export_backup)
                .setOnClickListener(v -> showBackupOptionsDialog());
        view.findViewById(R.id.row_special_details)
                .setOnClickListener(v -> openSpecialDetails());
    }

    private void showBackupOptionsDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_backup, null);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.export_backup)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btn_backup_export).setOnClickListener(v -> {
            dialog.dismiss();
            startBackupExport();
        });
        dialogView.findViewById(R.id.btn_backup_import).setOnClickListener(v -> {
            dialog.dismiss();
            startBackupImport();
        });

        dialog.show();
    }

    private void startBackupExport() {
        try {
            pendingBackupJson = new BackupManager(requireContext()).exportToJson();
        } catch (Exception e) {
            android.util.Log.e(TAG, "生成备份失败", e);
            UiFeedback.showError(requireContext(), "生成备份失败");
            return;
        }

        String fileName = "防沉迷提醒_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date())
                + ".json";
        try {
            createBackupLauncher.launch(fileName);
        } catch (Exception e) {
            pendingBackupJson = null;
            android.util.Log.e(TAG, "无法打开文件保存界面", e);
            UiFeedback.showError(requireContext(), "无法打开文件保存界面");
        }
    }

    private void onBackupDocumentCreated(@Nullable Uri uri) {
        String json = pendingBackupJson;
        pendingBackupJson = null;
        if (uri == null || json == null) {
            // 用户取消，或没有待写入内容
            return;
        }

        try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
            if (out == null) {
                UiFeedback.showError(requireContext(), "导出失败：无法写入所选文件");
                return;
            }
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.flush();
            UiFeedback.show(requireContext(), "备份已导出");
        } catch (Exception e) {
            android.util.Log.e(TAG, "写入备份文件失败", e);
            UiFeedback.showError(requireContext(), "导出失败：" + e.getMessage());
        }
    }

    private void startBackupImport() {
        try {
            openBackupLauncher.launch(new String[]{"*/*"});
        } catch (Exception e) {
            android.util.Log.e(TAG, "无法打开文件选择界面", e);
            UiFeedback.showError(requireContext(), "无法打开文件选择界面");
        }
    }

    private void onBackupDocumentPicked(@Nullable Uri uri) {
        if (uri == null) {
            // 用户取消
            return;
        }

        String json;
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            if (in == null) {
                UiFeedback.showError(requireContext(), "导入失败：无法读取所选文件");
                return;
            }
            json = readAll(in);
        } catch (Exception e) {
            android.util.Log.e(TAG, "读取备份文件失败", e);
            UiFeedback.showError(requireContext(), "导入失败：无法读取文件");
            return;
        }

        try {
            BackupManager.ImportResult result =
                    new BackupManager(requireContext()).importFromJson(json);
            String message = "导入完成：成功 " + result.imported + " 项"
                    + (result.skipped > 0 ? "，跳过 " + result.skipped + " 项" : "");
            UiFeedback.show(requireContext(), message);
        } catch (Exception e) {
            android.util.Log.e(TAG, "导入备份失败", e);
            UiFeedback.showError(requireContext(),
                    "导入失败：" + (e.getMessage() != null ? e.getMessage() : "文件内容无效"));
        }
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
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
            requestApkDownload(freshVersion, versionDialog, versionDetail);
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
                    UiFeedback.showError(
                            versionDetail,
                            getString(R.string.latest_version_fetch_failed)
                    );
                    return;
                }
                requestApkDownload(latestVersion, versionDialog, versionDetail);
            });
        }).start();
    }

    private void requestApkDownload(String latestVersion,
                                    android.app.AlertDialog versionDialog,
                                    View feedbackAnchor) {
        String downloadUrl = LatestVersionManager.buildLatestApkDownloadUrl(latestVersion);
        Intent downloadIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
        try {
            startActivity(downloadIntent);
            versionDialog.dismiss();
        } catch (ActivityNotFoundException e) {
            android.util.Log.e(TAG, "没有可处理安装包下载链接的应用", e);
            UiFeedback.showError(
                    feedbackAnchor,
                    getString(R.string.apk_download_unavailable)
            );
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
                    copyToClipboard(widget, urlSpan.getURL());
                }
            }, start, end, flags);
        }

        textView.setText(copyableText);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void copyToClipboard(View feedbackAnchor, String text) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("text", text));
            UiFeedback.show(feedbackAnchor, "已复制：" + text);
        }
    }

    private void updateVersionBadge(View view) {
        boolean hasUpdate = false;
        String remoteVersion = Share.latestVersion;
        if (remoteVersion != null
                && !remoteVersion.trim().isEmpty()
                && !"获取失败".equals(remoteVersion)) {
            hasUpdate = compareVersion(remoteVersion, getLocalVersion()) > 0;
        }

        View redDot = view.findViewById(R.id.iv_version_update_red_dot);
        redDot.setVisibility(hasUpdate ? View.VISIBLE : View.GONE);
    }

    /**
     * 比较两个形如 "2.8.0" 的版本号。
     * 返回正数表示 left 更新（更大），0 表示相等，负数表示 left 更旧。
     * 任一版本无法解析为数字段时，回退为字符串不等即视为有更新。
     */
    private static int compareVersion(String left, String right) {
        String[] leftParts = left.trim().split("\\.");
        String[] rightParts = right.trim().split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            Integer leftPart = parseVersionPart(leftParts, i);
            Integer rightPart = parseVersionPart(rightParts, i);
            if (leftPart == null || rightPart == null) {
                return left.trim().equals(right.trim()) ? 0 : 1;
            }
            if (!leftPart.equals(rightPart)) {
                return leftPart > rightPart ? 1 : -1;
            }
        }
        return 0;
    }

    private static Integer parseVersionPart(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index].trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
