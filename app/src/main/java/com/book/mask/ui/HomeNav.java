package com.book.mask.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ToggleButton;
import android.view.inputmethod.EditorInfo;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.book.mask.R;
import com.book.mask.personalize.RelaxManager;
import com.book.mask.personalize.AppSettingsManager;
import com.book.mask.personalize.AppSettingsSnapshot;
import com.book.mask.constant.Const;
import com.book.mask.constant.QuestionConst;
import com.book.mask.config.CustomAppManager;
import com.book.mask.config.CustomApp;
import com.book.mask.floating.FloatService;
import com.book.mask.network.LatestVersionManager;
import com.book.mask.util.ArithmeticUtils;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.color.MaterialColors;

import java.util.List;

public class HomeNav extends Fragment implements
    AppCardAdapter.OnAppCardClickListener,
    AppCardAdapter.OnMonitorToggleListener,
    AppCardAdapter.OnEditClickListener,
    AppCardAdapter.OnDeleteClickListener {
    private static final String TAG = "HomeNav";

    private RelaxManager relaxManager;
    private AppSettingsManager appSettingsManager;
    private SettingsDialogManager settingsDialogManager;
    private CustomAppManager customAppManager;
    
    // APP卡片相关
    private RecyclerView rvAppCards;
    private AppCardAdapter appCardAdapter;
    private List<CustomApp> allApps; // 包含预定义APP和自定义APP
    private TextView permissionStatusView;
    private TextView permissionOptionalView;
    private View permissionCardView;
    private View permissionExpandedView;
    private View permissionHideButton;
    private View permissionCollapsedView;

    // 倒计时相关
    private Handler countdownHandler;
    private Runnable countdownRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        // 初始化设置管理器
        relaxManager = new RelaxManager(requireContext());
        appSettingsManager = new AppSettingsManager(requireContext());
        settingsDialogManager = new SettingsDialogManager(requireContext(), relaxManager);
        customAppManager = CustomAppManager.getInstance();

        // 设置页的云端最新版本获取
        new Thread(() -> {
            String response = LatestVersionManager.refreshLatestVersion();
            if (response != null) {
                Log.d(TAG, "版本接口响应: " + response);
            } else {
                Log.e(TAG, "最新版本获取失败");
            }
        }).start();

        // 初始化APP列表
        updateAppList();
        
        // 初始化APP卡片RecyclerView
        initAppCards(view);

        permissionStatusView = view.findViewById(R.id.tv_description);
        permissionOptionalView = view.findViewById(R.id.tv_optional_permissions);
        permissionCardView = view.findViewById(R.id.ll_permissions);
        permissionExpandedView = view.findViewById(R.id.ll_permission_expanded);
        permissionCollapsedView = view.findViewById(R.id.tv_show_permissions);
        permissionHideButton = view.findViewById(R.id.tv_hide_permissions);
        permissionHideButton.setOnClickListener(v -> setPermissionCardExpanded(false));
        permissionCollapsedView.setOnClickListener(v -> {
            setPermissionCardExpanded(true);
            refreshPermissionStatus();
        });
        applyPermissionCardExpanded(!appSettingsManager.isPermissionCardCollapsed());
        if (permissionStatusView != null) {
            permissionStatusView.setMovementMethod(LinkMovementMethod.getInstance());
            permissionStatusView.setHighlightColor(android.graphics.Color.TRANSPARENT);
        }
        if (permissionOptionalView != null) {
            permissionOptionalView.setMovementMethod(LinkMovementMethod.getInstance());
            permissionOptionalView.setHighlightColor(android.graphics.Color.TRANSPARENT);
        }
        refreshPermissionStatus();

        // 启动倒计时更新
        startCountdown();
        
        return view;
    }

    private void setPermissionCardExpanded(boolean expanded) {
        applyPermissionCardExpanded(expanded);
        appSettingsManager.setPermissionCardCollapsed(!expanded);
    }

    private void applyPermissionCardExpanded(boolean expanded) {
        permissionExpandedView.setVisibility(expanded ? View.VISIBLE : View.GONE);
        permissionHideButton.setVisibility(expanded ? View.VISIBLE : View.GONE);
        permissionCollapsedView.setVisibility(expanded ? View.GONE : View.VISIBLE);
        // 展开时铺满约束宽度（0dp），折叠时宽度自适应内容
        ViewGroup.LayoutParams lp = permissionCardView.getLayoutParams();
        lp.width = expanded ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT;
        permissionCardView.setLayoutParams(lp);
    }

    public void refreshPermissionStatus() {
        if (!isAdded() || permissionStatusView == null || permissionOptionalView == null) {
            return;
        }

        SpannableStringBuilder status = new SpannableStringBuilder("必需权限 3 个");
        status.setSpan(
                new StyleSpan(Typeface.BOLD),
                0,
                status.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        status.setSpan(
                new RelativeSizeSpan(1.0625f),
                0,
                status.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        appendPermissionStatus(
                status,
                "1.悬浮窗",
                PermissionStatus.canDrawOverlays(requireContext()),
                MainActivity::reviewOverlayPermission);
        appendPermissionStatus(
                status,
                "2.无障碍服务",
                PermissionStatus.isAccessibilityServiceEnabled(requireContext()),
                MainActivity::reviewAccessibilityPermission);
        appendBackgroundRunHint(status);
        permissionStatusView.setText(status);

        SpannableStringBuilder optional = new SpannableStringBuilder("可选权限");
        optional.setSpan(
                new StyleSpan(Typeface.BOLD),
                0,
                optional.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        optional.setSpan(
                new RelativeSizeSpan(1.0625f),
                0,
                optional.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        optional.append(" ");
        int clickStart = optional.length();
        optional.append("查看");
        int clickColor = Color.rgb(66, 133, 190);
        optional.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                showOptionalPermissionsDialog();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint drawState) {
                drawState.setColor(clickColor);
                drawState.setUnderlineText(true);
            }
        }, clickStart, optional.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        optional.setSpan(
                new StyleSpan(Typeface.BOLD),
                clickStart,
                optional.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        permissionOptionalView.setText(optional);
    }

    private void showOptionalPermissionsDialog() {
        if (!isAdded()) {
            return;
        }
        android.app.AlertDialog[] dialogHolder = new android.app.AlertDialog[1];

        SpannableStringBuilder message = new SpannableStringBuilder();
        appendPlainPermissionHint(
                message,
                "1.开机自启",
                "建议加上，请自行设置");
        appendPlainPermissionHint(
                message,
                "2.省电模式",
                "可能导致悬浮窗延迟出现，如需排查可尝试关闭省电模式");
        message.append('\n').append("3.忽略电池优化设置").append("：");
        int hintStart = message.length();
        message.append("点击开启");
        int hintColor = MaterialColors.getColor(
                permissionStatusView,
                com.google.android.material.R.attr.colorPrimary);
        message.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                if (dialogHolder[0] != null) {
                    dialogHolder[0].dismiss();
                }
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).reviewBackgroundPermission();
                }
            }

            @Override
            public void updateDrawState(@NonNull TextPaint drawState) {
                drawState.setColor(hintColor);
                drawState.setUnderlineText(false);
            }
        }, hintStart, message.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        // 去掉首行多余的换行
        if (message.length() > 0 && message.charAt(0) == '\n') {
            message.delete(0, 1);
        }

        TextView messageView = new TextView(requireContext());
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        messageView.setPadding(padding, padding, padding, 0);
        messageView.setTextSize(16);
        messageView.setLineSpacing(0, 1.3f);
        messageView.setText(message);
        messageView.setMovementMethod(LinkMovementMethod.getInstance());
        messageView.setHighlightColor(android.graphics.Color.TRANSPARENT);

        dialogHolder[0] = new android.app.AlertDialog.Builder(requireContext())
                .setTitle("可选权限")
                .setView(messageView)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void appendPlainPermissionHint(
            SpannableStringBuilder text,
            String label,
            String hint) {
        text.append('\n').append(label).append("：").append(hint);
    }

    private void appendBackgroundRunHint(SpannableStringBuilder text) {
        text.append('\n').append("3.允许后台活动").append("：");
        int clickStart = text.length();
        text.append("查看");
        int clickColor = Color.rgb(66, 133, 190);
        text.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                showBackgroundRunDialog();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint drawState) {
                drawState.setColor(clickColor);
                drawState.setUnderlineText(true);
            }
        }, clickStart, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(
                new StyleSpan(Typeface.BOLD),
                clickStart,
                text.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void showBackgroundRunDialog() {
        if (!isAdded()) {
            return;
        }
        String linkText = getString(R.string.blog_link_text);
        SpannableStringBuilder message = new SpannableStringBuilder()
                .append("1、该权限很重要，不设置可能导致无障碍也异常；\n")
                .append("2、不同手机设置它的方式不同，可自行了解，或参考");
        int linkStart = message.length();
        message.append(linkText)
                .append("；\n")
                .append("3、某些场景该权限可能被重置，或需手动重设它，如：手机系统升级引起的重启，卸载重装本 APP");
        message.setSpan(
                new URLSpan(getString(R.string.install_troubleshooting_url)),
                linkStart,
                linkStart + linkText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        TextView messageView = new TextView(requireContext());
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        messageView.setPadding(padding, padding, padding, 0);
        messageView.setTextSize(16);
        messageView.setLineSpacing(0, 1.3f);
        messageView.setText(message);
        messageView.setMovementMethod(LinkMovementMethod.getInstance());
        messageView.setLinksClickable(true);

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("允许后台活动")
                .setView(messageView)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void appendPermissionStatus(
            SpannableStringBuilder text,
            String label,
            boolean enabled,
            PermissionReviewAction reviewAction) {
        text.append('\n').append(label).append("：");
        int statusStart = text.length();
        text.append(enabled ? "已开启" : "未开启，点这设置");
        int statusColor = enabled
                ? Color.parseColor("#2E7D32")
                : MaterialColors.getColor(
                        permissionStatusView,
                        com.google.android.material.R.attr.colorError);
        if (enabled) {
            text.setSpan(
                    new ForegroundColorSpan(statusColor),
                    statusStart,
                    text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            return;
        }
        text.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                if (getActivity() instanceof MainActivity) {
                    reviewAction.review((MainActivity) getActivity());
                }
            }

            @Override
            public void updateDrawState(@NonNull TextPaint drawState) {
                drawState.setColor(statusColor);
                drawState.setUnderlineText(false);
            }
        }, statusStart, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private interface PermissionReviewAction {
        void review(MainActivity activity);
    }

    private void showAddAppDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_app, null);

        TextInputEditText etTargetWord = dialogView.findViewById(R.id.et_target_word);
        TextInputEditText etRelaxedLimitCount = dialogView.findViewById(R.id.et_relaxed_limit_count);
        TextInputLayout targetWordLayout = dialogView.findViewById(R.id.layout_target_word);
        TextInputLayout relaxedLimitCountLayout =
                dialogView.findViewById(R.id.layout_relaxed_limit_count);
        ImageView ivSelectedIcon = dialogView.findViewById(R.id.iv_selected_icon);
        TextView tvSelectedName = dialogView.findViewById(R.id.tv_selected_name);
        TextView tvSelectedPackage = dialogView.findViewById(R.id.tv_selected_package);
        View layoutSelectedApp = dialogView.findViewById(R.id.layout_selected_app);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnSave = dialogView.findViewById(R.id.btn_save);
        Button btnPickInstalledApp = dialogView.findViewById(R.id.btn_pick_installed_app);

        // 已选应用（通过“从已安装 APP 选择”得到），保存前必须已选择
        final InstalledAppInfo[] selectedApp = {null};

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnPickInstalledApp.setOnClickListener(v -> showInstalledAppPicker(installedApp -> {
            selectedApp[0] = installedApp;
            ivSelectedIcon.setImageDrawable(installedApp.icon);
            tvSelectedName.setText(installedApp.label);
            tvSelectedPackage.setText(installedApp.packageName);
            layoutSelectedApp.setVisibility(View.VISIBLE);
        }));

        btnSave.setOnClickListener(v -> {
            String targetWord = etTargetWord.getText().toString().trim();
            String relaxedLimitCountStr = etRelaxedLimitCount.getText().toString().trim();

            targetWordLayout.setError(null);
            relaxedLimitCountLayout.setError(null);

            // 逐项校验，所有未填写/非法字段一次性全部提示，而非只提示第一个
            boolean valid = true;

            if (targetWord.isEmpty()) {
                showInputError(targetWordLayout, etTargetWord, "请输入屏蔽关键词");
                valid = false;
            }

            int relaxedLimitCount = 1;
            if (!relaxedLimitCountStr.isEmpty()) {
                try {
                    relaxedLimitCount = Integer.parseInt(relaxedLimitCountStr);
                    if (relaxedLimitCount < 1 || relaxedLimitCount > 3) {
                        showInputError(
                                relaxedLimitCountLayout,
                                etRelaxedLimitCount,
                                "请输入 1-3 之间的数字");
                        valid = false;
                    }
                } catch (NumberFormatException e) {
                    showInputError(
                            relaxedLimitCountLayout,
                            etRelaxedLimitCount,
                            "请输入有效的数字");
                    valid = false;
                }
            }

            if (selectedApp[0] == null) {
                UiFeedback.show(requireContext(), "请先从已安装 APP 中选择一个 APP");
                valid = false;
            }

            if (!valid) {
                return;
            }

            // 保存新APP
            boolean success = customAppManager.addCustomApp(
                    selectedApp[0].label, selectedApp[0].packageName, targetWord, relaxedLimitCount);
            if (success) {
                dialog.dismiss();
                UiFeedback.show(requireContext(), "APP 添加成功");
                // 新增 APP 默认开启监测，卡片右上角开关随之置为打开
                relaxManager.setAppMonitoringEnabled(selectedApp[0].packageName, true);
                // 更新APP列表和卡片显示
                updateAppList();
                if (appCardAdapter != null) {
                    appCardAdapter.updateData(allApps);
                }
            } else {
                UiFeedback.show(requireContext(), "该应用无效或已添加，请重新选择");
            }
        });

        dialog.show();
    }

    /**
     * 已安装 APP 信息（用于“从已安装 APP 选择”选择器）
     */
    private static class InstalledAppInfo {
        final String label;
        final String packageName;
        final android.graphics.drawable.Drawable icon;

        InstalledAppInfo(String label, String packageName, android.graphics.drawable.Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
        }
    }

    /**
     * 弹出已安装 APP 选择器：先显示加载动画，在后台线程读取应用列表，加载完成后展示列表。
     * 已添加过的应用与本应用自身不出现在列表中。
     */
    private void showInstalledAppPicker(java.util.function.Consumer<InstalledAppInfo> onPicked) {
        View loadingView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_loading, null);
        android.app.AlertDialog loadingDialog = new android.app.AlertDialog.Builder(requireContext())
                .setView(loadingView)
                .setCancelable(false)
                .create();
        loadingDialog.show();

        Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            List<InstalledAppInfo> allInstalled = loadInstalledApps();
            mainHandler.post(() -> {
                // Fragment 已脱离时不再操作 UI
                if (!isAdded()) {
                    return;
                }
                loadingDialog.dismiss();
                if (allInstalled.isEmpty()) {
                    UiFeedback.show(requireContext(), "未获取到可选择的应用");
                    return;
                }
                displayInstalledAppPicker(allInstalled, onPicked);
            });
        }).start();
    }

    /**
     * 展示已加载好的应用列表（带名称/包名搜索过滤），选中后回调。
     */
    private void displayInstalledAppPicker(
            List<InstalledAppInfo> allInstalled,
            java.util.function.Consumer<InstalledAppInfo> onPicked) {
        View pickerView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_app_picker, null);
        EditText etFilter = pickerView.findViewById(R.id.et_app_filter);
        android.widget.ListView listView = pickerView.findViewById(R.id.lv_installed_apps);

        final List<InstalledAppInfo> shown = new java.util.ArrayList<>(allInstalled);
        android.widget.BaseAdapter adapter = new android.widget.BaseAdapter() {
            @Override
            public int getCount() {
                return shown.size();
            }

            @Override
            public Object getItem(int position) {
                return shown.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = convertView;
                if (row == null) {
                    row = LayoutInflater.from(requireContext())
                            .inflate(R.layout.item_app_picker, parent, false);
                }
                InstalledAppInfo item = shown.get(position);
                ImageView ivIcon = row.findViewById(R.id.iv_app_icon);
                TextView tvLabel = row.findViewById(R.id.tv_app_label);
                TextView tvPackage = row.findViewById(R.id.tv_app_package);
                ivIcon.setImageDrawable(item.icon);
                tvLabel.setText(item.label);
                tvPackage.setText(item.packageName);
                return row;
            }
        };
        listView.setAdapter(adapter);

        android.app.AlertDialog pickerDialog = new android.app.AlertDialog.Builder(requireContext())
                .setView(pickerView)
                .setNegativeButton("取消", null)
                .create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            InstalledAppInfo picked = shown.get(position);
            pickerDialog.dismiss();
            onPicked.accept(picked);
        });

        etFilter.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                String keyword = s.toString().trim().toLowerCase();
                shown.clear();
                if (keyword.isEmpty()) {
                    shown.addAll(allInstalled);
                } else {
                    for (InstalledAppInfo info : allInstalled) {
                        if (info.label.toLowerCase().contains(keyword)
                                || info.packageName.toLowerCase().contains(keyword)) {
                            shown.add(info);
                        }
                    }
                }
                adapter.notifyDataSetChanged();
            }
        });

        pickerDialog.show();
    }

    /**
     * 读取系统中可启动的应用列表，排除本应用及已添加过的应用，按名称排序。
     */
    private List<InstalledAppInfo> loadInstalledApps() {
        List<InstalledAppInfo> result = new java.util.ArrayList<>();
        android.content.pm.PackageManager pm = requireContext().getPackageManager();
        android.content.Intent launcherIntent =
                new android.content.Intent(android.content.Intent.ACTION_MAIN)
                        .addCategory(android.content.Intent.CATEGORY_LAUNCHER);
        List<android.content.pm.ResolveInfo> resolved =
                pm.queryIntentActivities(launcherIntent, 0);
        String ownPackage = requireContext().getPackageName();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (android.content.pm.ResolveInfo info : resolved) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (pkg == null || pkg.equals(ownPackage)) continue;
            if (!seen.add(pkg)) continue;
            if (customAppManager.isPackageNameExists(pkg)) continue;
            String label = info.loadLabel(pm).toString();
            android.graphics.drawable.Drawable icon = info.loadIcon(pm);
            result.add(new InstalledAppInfo(label, pkg, icon));
        }
        java.util.Collections.sort(result,
                (a, b) -> a.label.compareToIgnoreCase(b.label));
        return result;
    }

    private void updateAppList() {
        // 获取所有APP（预定义 + 自定义）
        allApps = new java.util.ArrayList<>();
        
        // 添加所有APP（包括预定义和自定义）
        allApps.addAll(customAppManager.getAllApps());
    }

    private void initAppCards(View view) {
        rvAppCards = view.findViewById(R.id.rv_app_cards);
        android.util.Log.d("HomeFragment", "RecyclerView找到: " + (rvAppCards != null));
        
        if (rvAppCards != null) {
            // 使用GridLayoutManager实现两列布局
            androidx.recyclerview.widget.GridLayoutManager layoutManager =
                    new androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2);
            layoutManager.setSpanSizeLookup(new androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    return appCardAdapter != null
                            && allApps.size() % 2 == 0
                            && appCardAdapter.isAddCardPosition(position) ? 2 : 1;
                }
            });
            rvAppCards.setLayoutManager(layoutManager);
            appCardAdapter = new AppCardAdapter(
                    allApps, relaxManager, this, this, this, this, this::showAddAppDialog);
            rvAppCards.setAdapter(appCardAdapter);
        }
    }

    @Override
    public void onAppCardClick(CustomApp app) {
        // 显示APP设置弹窗
        showConfigDialogForApp(app);
    }

    @Override
    public void onMonitorToggle(CustomApp app, boolean isEnabled) {
        // 处理监测开关状态变化
        String packageName = getPackageName(app);
        if (packageName != null) {
            // 如果要关闭屏蔽，需要算术题验证
            if (!isEnabled) {
                showMathChallengeForMonitorToggle(app, packageName);
            } else {
                // 开启监测直接执行
                relaxManager.setAppMonitoringEnabled(packageName, isEnabled);
                android.util.Log.d("HomeFragment", "监测开关状态改变: " + packageName + " = " + isEnabled);
                
                // 显示提示
                String status = isEnabled ? "已开启监测" : "已关闭屏蔽";
                UiFeedback.show(requireContext(), status);
            }
        }
    }

    @Override
    public void onEditClick(CustomApp app) {
        // 处理编辑图标点击
        String appName = getAppName(app);
        UiFeedback.show(requireContext(), "编辑 " + appName);
        // TODO: 实现编辑功能
    }

    @Override
    public void onDeleteClick(CustomApp app) {
        // 仅手动添加的自定义APP允许删除，再校验一次防御
        if (app == null || !customAppManager.isCustomApp(app.getPackageName())) {
            return;
        }
        showMathChallengeForDelete(app);
    }

    private String getPackageName(CustomApp app) {
        return app.getPackageName();
    }

    private String getAppName(CustomApp app) {
        return app.getAppName();
    }

    private void showConfigDialogForApp(CustomApp app) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_time_setting, null);

        ToggleButton globalBlockToggle = dialogView.findViewById(R.id.toggle_global_block);
        ImageView globalBlockHelp = dialogView.findViewById(R.id.iv_global_block_help);
        View targetWordSetting = dialogView.findViewById(R.id.layout_target_word_setting);
        View strictIntervalSetting = dialogView.findViewById(R.id.layout_strict_interval_setting);
        View relaxedIntervalSetting = dialogView.findViewById(R.id.layout_relaxed_interval_setting);
        TextView targetWordValue = dialogView.findViewById(R.id.tv_target_word_value);
        TextView strictIntervalValue = dialogView.findViewById(R.id.tv_strict_interval_value);
        TextView relaxedIntervalValue = dialogView.findViewById(R.id.tv_relaxed_interval_value);
        TextView relaxedRemainingCount = dialogView.findViewById(R.id.tv_relaxed_remaining_count);
        TextView floatingTextSourceValue = dialogView.findViewById(R.id.tv_floating_text_source_value);
        View floatingTextSourceSetting =
                dialogView.findViewById(R.id.layout_floating_text_source_setting);
        View floatingSizeSetting = dialogView.findViewById(R.id.layout_floating_size_setting);

        targetWordValue.setText(app.getTargetWord());
        floatingTextSourceValue.setText(
                appSettingsManager.getAppHintSource(app.getPackageName()));

        Runnable refreshIntervalState = () -> updateIntervalSettingValues(
                app,
                strictIntervalValue,
                relaxedIntervalValue,
                relaxedRemainingCount);
        refreshIntervalState.run();

        strictIntervalSetting.setOnClickListener(v -> showIntervalOptionsDialog(
                "严格模式",
                app,
                RelaxManager.getStrictIntervals(),
                refreshIntervalState));
        relaxedIntervalSetting.setOnClickListener(v -> showRelaxedModeDialog(
                app,
                refreshIntervalState));

        targetWordSetting.setOnClickListener(v ->
                showTargetWordInputDialog(app, targetWordValue));
        floatingTextSourceSetting.setOnClickListener(v ->
                showFloatingTextSourceDialog(app, () -> floatingTextSourceValue.setText(
                        appSettingsManager.getAppHintSource(app.getPackageName()))));
        floatingSizeSetting.setOnClickListener(v ->
                settingsDialogManager.showFloatingPositionDialogForApp(app));

        globalBlockToggle.setChecked(app.isGlobalBlock());
        bindGlobalBlockToggle(globalBlockToggle, app);
        globalBlockHelp.setOnClickListener(v -> new android.app.AlertDialog.Builder(requireContext())
                .setMessage("开启后该 APP 所有页面都会被遮挡，也无法搜索，如需保留搜索功能可留言反馈")
                .setPositiveButton("知道了", null)
                .show());

        // 弹窗标题（APP名），水平居中
        TextView dialogTitleView = new TextView(requireContext());
        dialogTitleView.setText(app.getAppName());
        dialogTitleView.setTextSize(20);
        dialogTitleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        dialogTitleView.setTextColor(0xFF333333);
        dialogTitleView.setGravity(android.view.Gravity.CENTER);
        int titleTopPadding = (int) (10 * getResources().getDisplayMetrics().density);
        dialogTitleView.setPadding(0, titleTopPadding, 0, 0);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
                .setCustomTitle(dialogTitleView)
                .setView(dialogView)
                .setPositiveButton("关闭", null)
                .create();


        dialog.show();

        // 缩小底部“关闭”按钮的上下边距
        Button exitButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
        if (exitButton != null) {
            int vPad = (int) (4 * getResources().getDisplayMetrics().density);
            exitButton.setMinHeight(0);
            exitButton.setMinimumHeight(0);
            exitButton.setPadding(
                    exitButton.getPaddingLeft(), vPad,
                    exitButton.getPaddingRight(), vPad);
        }
    }

    private void updateIntervalSettingValues(
            CustomApp app,
            TextView strictIntervalValue,
            TextView relaxedIntervalValue,
            TextView relaxedRemainingCount) {
        int currentInterval = relaxManager.getAppInterval(app);
        // 实际生效间隔：宽松次数用完时回退到严格档最长时长，严格行应显示该回退值
        int effectiveInterval = relaxManager.getEffectiveAppInterval(app);
        boolean relaxedMode = relaxManager.isAppRelaxedMode(app);
        int usedCount = relaxManager.getAppRelaxedCloseCount(app);
        int remainingCount = Math.max(0, app.getRelaxedLimitCount() - usedCount);
        // 宽松模式但次数已用完：宽松不可用，实际回退到严格档，严格行显示回退时长
        boolean relaxedUsable = relaxedMode && remainingCount > 0;

        strictIntervalValue.setText(relaxedUsable
                ? "-"
                : RelaxManager.getIntervalDisplayText(effectiveInterval));
        relaxedIntervalValue.setText(relaxedUsable
                ? RelaxManager.getIntervalDisplayText(currentInterval)
                : "-");
        relaxedRemainingCount.setText("剩余 " + remainingCount + " 次");
    }

    private void showIntervalOptionsDialog(
            String title,
            CustomApp app,
            int[] intervals,
            Runnable onSelectionChanged) {
        String[] options = new String[intervals.length];
        // 用实际生效间隔计算选中项：宽松次数用完时回退到严格档最长时长，严格弹窗应选中该项
        int effectiveInterval = relaxManager.getEffectiveAppInterval(app);
        int checkedItem = -1;
        for (int i = 0; i < intervals.length; i++) {
            options[i] = RelaxManager.getIntervalDisplayText(intervals[i]);
            if (intervals[i] == effectiveInterval) {
                checkedItem = i;
            }
        }

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    relaxManager.setAppInterval(app, intervals[which]);
                    FloatService.notifyIntervalChanged();
                    onSelectionChanged.run();
                    dialog.dismiss();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    /**
     * 宽松模式设置弹窗：左列选时长、右列选宽松次数，一次保存。
     * 宽松次数已用完时禁用时长列（今日无法再进入宽松），但仍可调整次数。
     */
    private void showRelaxedModeDialog(CustomApp app, Runnable onSelectionChanged) {
        int[] intervals = RelaxManager.getRelaxedIntervals();
        int currentInterval = relaxManager.getAppInterval(app);
        int usedCount = relaxManager.getAppRelaxedCloseCount(app);
        int remainingCount = Math.max(0, app.getRelaxedLimitCount() - usedCount);

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.HORIZONTAL);
        int pad = dp(24);
        root.setPadding(pad, dp(16), pad, 0);

        RadioGroup durationGroup = buildRelaxedOptionColumn(
                root,
                "时长",
                intervalOptions(intervals),
                indexOfInterval(intervals, currentInterval),
                remainingCount > 0);
        RadioGroup countGroup = buildRelaxedOptionColumn(
                root,
                "宽松次数",
                new String[]{"1 次", "2 次", "3 次"},
                Math.max(0, Math.min(app.getRelaxedLimitCount() - 1, 2)),
                true);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
                .setTitle("宽松模式")
                .setView(root)
                .setPositiveButton("确定", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    int intervalIndex = durationGroup.getCheckedRadioButtonId();
                    int countIndex = countGroup.getCheckedRadioButtonId();
                    if (countIndex >= 0 && countIndex < 3) {
                        app.setRelaxedLimitCount(countIndex + 1);
                        customAppManager.persistAppChange(app);
                    }
                    if (intervalIndex >= 0 && intervalIndex < intervals.length) {
                        relaxManager.setAppInterval(app, intervals[intervalIndex]);
                        FloatService.notifyIntervalChanged();
                    }
                    onSelectionChanged.run();
                    updateAppCardsDisplay();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    /**
     * 构造宽松弹窗的一列单选组：标题 + RadioGroup，加入横向 root 中。
     */
    private RadioGroup buildRelaxedOptionColumn(
            LinearLayout root,
            String title,
            String[] options,
            int checkedIndex,
            boolean enabled) {
        LinearLayout column = new LinearLayout(requireContext());
        column.setOrientation(LinearLayout.VERTICAL);
        column.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleView = new TextView(requireContext());
        titleView.setText(title);
        titleView.setTextSize(15);
        titleView.setTextColor(0xFF333333);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        titleView.setPadding(0, 0, 0, dp(8));
        column.addView(titleView);

        RadioGroup group = new RadioGroup(requireContext());
        group.setOrientation(RadioGroup.VERTICAL);
        for (int i = 0; i < options.length; i++) {
            RadioButton rb = new RadioButton(requireContext());
            rb.setText(options[i]);
            rb.setTextSize(15);
            rb.setId(i);
            rb.setEnabled(enabled);
            group.addView(rb);
        }
        if (checkedIndex >= 0 && checkedIndex < options.length) {
            group.check(checkedIndex);
        }
        group.setEnabled(enabled);
        column.addView(group);
        root.addView(column);
        return group;
    }

    private String[] intervalOptions(int[] intervals) {
        String[] options = new String[intervals.length];
        for (int i = 0; i < intervals.length; i++) {
            options[i] = RelaxManager.getIntervalDisplayText(intervals[i]);
        }
        return options;
    }

    private int indexOfInterval(int[] intervals, int interval) {
        for (int i = 0; i < intervals.length; i++) {
            if (intervals[i] == interval) {
                return i;
            }
        }
        return -1;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void showTargetWordInputDialog(CustomApp app, TextView targetWordValue) {
        EditText input = new EditText(requireContext());
        input.setHint("若多个关键词需空格分隔");
        input.setSingleLine(true);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(50)});
        input.setText(app.getTargetWord());
        input.setSelection(input.length());

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
                .setTitle("页面屏蔽关键词")
                .setView(input)
                .setPositiveButton("确定", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    input.setError(null);
                    String targetWord = input.getText().toString().trim();
                    if (targetWord.isEmpty()) {
                        showInputError(input, "请输入关键词");
                        return;
                    }
                    app.setTargetWord(targetWord);
                    customAppManager.persistAppChange(app);
                    targetWordValue.setText(targetWord);
                    updateAppCardsDisplay();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    /**
     * 显示悬浮窗警示语来源选择对话框（单选列表风格）
     */
    private void showFloatingTextSourceDialog(CustomApp app, Runnable onSourceChanged) {
        String packageName = getPackageName(app);
        String currentSource = appSettingsManager.getAppHintSource(packageName);

        String[] options = {Const.CUSTOM_HINT_SOURCE, Const.DEFAULT_HINT_SOURCE};
        int checkedItem = 0;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(currentSource)) {
                checkedItem = i;
                break;
            }
        }

        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("选择悬浮窗警示语来源")
            .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                if (which == 0) {
                    showCustomTextInputDialog(app, onSourceChanged);
                } else if (which == 1) {
                    recordFloatingTextSource(Const.DEFAULT_HINT_SOURCE, app);
                    onSourceChanged.run();
                    UiFeedback.show(requireContext(), "已选择大模型作为悬浮窗警示语来源");
                }
                dialog.dismiss();
            })
            .setNegativeButton("关闭", null)
            .show();
    }

    /**
     * 显示自定义文字输入对话框
     */
    private void showCustomTextInputDialog(CustomApp app, Runnable onSourceChanged) {
        EditText input = new EditText(requireContext());
        input.setHint("请输入自定义警示语（不超过100字）");
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});
        String currentCustomText = appSettingsManager.getAppHintCustomText(getPackageName(app));
        if (!currentCustomText.isEmpty()) {
            input.setText(currentCustomText);
            input.setSelection(input.length());
        }

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
            .setTitle("输入自定义警示语")
            .setView(input)
            .setPositiveButton("确定", null)
            .setNegativeButton("取消", null)
            .create();
        dialog.setOnShowListener(ignored ->
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                input.setError(null);
                String customText = input.getText().toString().trim();
                if (!customText.isEmpty()) {
                    recordFloatingTextSource(Const.CUSTOM_HINT_SOURCE, customText, app);
                    onSourceChanged.run();
                    dialog.dismiss();
                    UiFeedback.show(requireContext(), "自定义警示语设置成功");
                } else {
                    showInputError(input, "请输入内容");
                }
            }));
        dialog.show();
    }

    private void bindGlobalBlockToggle(ToggleButton globalBlockToggle, CustomApp app) {
        globalBlockToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                setGlobalBlock(app, true);
                return;
            }
            showMathChallengeForGlobalBlockToggle(globalBlockToggle, app);
        });
    }

    private void showMathChallengeForGlobalBlockToggle(ToggleButton globalBlockToggle, CustomApp app) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_math_challenge, null);
        TextView headerText = dialogView.findViewById(R.id.tv_math_header);
        TextView questionText = dialogView.findViewById(R.id.tv_math_question);
        EditText answerEdit = dialogView.findViewById(R.id.et_math_answer);
        TextView resultText = dialogView.findViewById(R.id.tv_math_result);
        Button submitButton = dialogView.findViewById(R.id.btn_submit_answer);
        Button cancelButton = dialogView.findViewById(R.id.btn_cancel_close);

        headerText.setText("🔢 回答算术题才能关闭全局屏蔽");
        String question = createDefaultMathQuestion();
        final int[] correctAnswer = {ArithmeticUtils.getMathAnswer(question)};
        questionText.setText(question);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
                .setTitle("关闭全局屏蔽验证")
                .setView(dialogView)
                .create();
        final boolean[] answerVerified = {false};
        dialog.setOnCancelListener(ignored -> {
            if (!answerVerified[0]) {
                restoreGlobalBlockToggle(globalBlockToggle, app);
            }
        });

        submitButton.setOnClickListener(v -> {
            String userAnswer = answerEdit.getText().toString().trim();
            if (userAnswer.isEmpty()) {
                UiFeedback.showTemporaryText(resultText, "⚠️ 请输入答案", 0xFFFF5722);
                return;
            }

            try {
                if (Integer.parseInt(userAnswer) == correctAnswer[0]) {
                    answerVerified[0] = true;
                    submitButton.setEnabled(false);
                    UiFeedback.showTemporaryText(
                            resultText,
                            "✅ 答案正确！",
                            requireContext().getColor(android.R.color.holo_green_light));
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        dialog.dismiss();
                        setGlobalBlock(app, false);
                    }, Const.TRANSIENT_FEEDBACK_DURATION_MS);
                    return;
                }

                UiFeedback.showTemporaryText(
                        resultText,
                        "❌ 答案错误，切到下一题",
                        requireContext().getColor(android.R.color.holo_red_light));
                answerEdit.setText("");
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    String newQuestion = createDefaultMathQuestion();
                    correctAnswer[0] = ArithmeticUtils.getMathAnswer(newQuestion);
                    questionText.setText(newQuestion);
                    resultText.setVisibility(View.GONE);
                }, Const.TRANSIENT_FEEDBACK_DURATION_MS);
            } catch (NumberFormatException e) {
                UiFeedback.showTemporaryText(resultText, "⚠️ 请输入有效数字", 0xFFFF5722);
            }
        });

        cancelButton.setOnClickListener(v -> dialog.cancel());
        answerEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                submitButton.performClick();
                return true;
            }
            return false;
        });

        dialog.show();
        answerEdit.requestFocus();
    }

    private String createDefaultMathQuestion() {
        return ArithmeticUtils.customArithmetic(
                QuestionConst.ADD_LEN_DEFAULT,
                QuestionConst.SUB_LEN_DEFAULT,
                QuestionConst.MUL_FIRST_LEN_DEFAULT,
                QuestionConst.MUL_SECOND_LEN_DEFAULT);
    }

    private void restoreGlobalBlockToggle(ToggleButton globalBlockToggle, CustomApp app) {
        globalBlockToggle.setOnCheckedChangeListener(null);
        globalBlockToggle.setChecked(true);
        bindGlobalBlockToggle(globalBlockToggle, app);
    }

    private void setGlobalBlock(CustomApp app, boolean enabled) {
        app.setGlobalBlock(enabled);
        customAppManager.persistAppChange(app);
        UiFeedback.show(requireContext(), enabled ? "已开启全局屏蔽" : "已关闭全局屏蔽");
    }

    /**
     * 记录悬浮窗警示语来源
     */
    private void recordFloatingTextSource(String source, String customText, CustomApp app) {
        // 获取当前选中的APP包名
        String currentAppPackage = getPackageName(app);
        if (currentAppPackage == null) {
            android.util.Log.e("HomeFragment", "无法获取当前选中的APP包名");
            return;
        }
        
        // 使用SettingsManager存储，为每个APP独立存储
        appSettingsManager.setAppHintSource(currentAppPackage, source);
        if (customText != null && !customText.isEmpty()) {
            appSettingsManager.setAppHintCustomText(currentAppPackage, customText);
        }
        android.util.Log.d("HomeFragment", "APP " + currentAppPackage + " 悬浮窗警示语来源已保存: " + source + ", 自定义文字: " + customText);
    }
    
    /**
     * 记录悬浮窗警示语来源（重载方法，用于只有source参数的情况）
     */
    private void recordFloatingTextSource(String source, CustomApp app) {
        recordFloatingTextSource(source, null, app);
    }

    /**
     * 获取当前选中的APP包名
     */
    private String getCurrentSelectedAppPackage() {
        // 这里需要根据你的实际逻辑获取当前选中的APP包名
        // 可能需要从全局变量、SharedPreferences或其他地方获取
        // 暂时返回null，需要你补充具体的获取逻辑
        return null;
    }

    /**
     * 显示"删除前验证"算术题弹窗，仅用于删除手动添加的自定义APP
     */
    private void showMathChallengeForDelete(CustomApp app) {
        final String packageName = app.getPackageName();

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_math_challenge, null);

        TextView headerText = dialogView.findViewById(R.id.tv_math_header);
        TextView questionText = dialogView.findViewById(R.id.tv_math_question);
        EditText answerEdit = dialogView.findViewById(R.id.et_math_answer);
        TextView resultText = dialogView.findViewById(R.id.tv_math_result);
        Button submitButton = dialogView.findViewById(R.id.btn_submit_answer);
        Button cancelButton = dialogView.findViewById(R.id.btn_cancel_close);

        if (headerText != null) {
            headerText.setText("🔢 回答算术题才能删除APP");
        }

        String question = ArithmeticUtils.customArithmetic(QuestionConst.ADD_LEN_CARD, QuestionConst.SUB_LEN_CARD, QuestionConst.MUL_FIRST_CARD, QuestionConst.MUL_SECOND_CARD);
        final int[] correctAnswer = {ArithmeticUtils.getMathAnswer(question)};
        questionText.setText(question);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
            .setTitle("删除前验证")
            .setView(dialogView)
            .create();

        submitButton.setOnClickListener(v -> {
            String userAnswer = answerEdit.getText().toString().trim();
            if (userAnswer.isEmpty()) {
                UiFeedback.showTemporaryText(resultText, "⚠️ 请输入答案", 0xFFFF5722);
                return;
            }

            try {
                int answer = Integer.parseInt(userAnswer);
                if (answer == correctAnswer[0]) {
                    UiFeedback.showTemporaryText(
                            resultText,
                            "✅ 答案正确！",
                            requireContext().getColor(android.R.color.holo_green_light));

                    new Handler().postDelayed(() -> {
                        dialog.dismiss();
                        // 删除APP并立即清理每-APP设置；先快照以便撤销时恢复
                        boolean removed = customAppManager.removeCustomApp(packageName);
                        if (removed) {
                            android.util.Log.d(TAG, "已删除自定义APP: " + packageName);
                            AppSettingsSnapshot snapshot = new AppSettingsSnapshot();
                            appSettingsManager.captureInto(snapshot, packageName);
                            relaxManager.captureInto(snapshot, packageName);
                            appSettingsManager.clearAppSettings(packageName);
                            relaxManager.clearAppSettings(packageName);
                            updateAppCardsDisplay();
                            showDeleteUndo(app, snapshot);
                        } else {
                            UiFeedback.showError(requireContext(), "删除失败");
                        }
                    }, Const.TRANSIENT_FEEDBACK_DURATION_MS);

                } else {
                    UiFeedback.showTemporaryText(
                            resultText,
                            "❌ 答案错误，切到下一题",
                            requireContext().getColor(android.R.color.holo_red_light));

                    answerEdit.setText("");

                    new Handler().postDelayed(() -> {
                        String newQuestion = ArithmeticUtils.customArithmetic(
                                QuestionConst.ADD_LEN_MAX, QuestionConst.ADD_LEN_MAX, QuestionConst.MUL_LEN_MAX, QuestionConst.MUL_LEN_MAX);
                        correctAnswer[0] = ArithmeticUtils.getMathAnswer(newQuestion);
                        questionText.setText(newQuestion);
                        answerEdit.setText("");
                        resultText.setVisibility(View.GONE);
                    }, Const.TRANSIENT_FEEDBACK_DURATION_MS);
                }
            } catch (NumberFormatException e) {
                UiFeedback.showTemporaryText(resultText, "⚠️ 请输入有效数字", 0xFFFF5722);
            }
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        answerEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                submitButton.performClick();
                return true;
            }
            return false;
        });

        dialog.show();

        answerEdit.requestFocus();
    }

    /**
     * 显示算术题验证弹窗用于关闭屏蔽
     */
    private void showMathChallengeForMonitorToggle(CustomApp app, String packageName) {
        if(CustomAppManager.WECHAT_PACKAGE.equals(packageName)){
            relaxManager.setAppMonitoringEnabled(packageName, false);
            updateAppCardsDisplay();
            return;
        }

        // 创建算术题验证弹窗
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_math_challenge, null);
        
        TextView questionText = dialogView.findViewById(R.id.tv_math_question);
        EditText answerEdit = dialogView.findViewById(R.id.et_math_answer);
        TextView resultText = dialogView.findViewById(R.id.tv_math_result);
        Button submitButton = dialogView.findViewById(R.id.btn_submit_answer);
        Button cancelButton = dialogView.findViewById(R.id.btn_cancel_close);
        
        // 生成算术题
        String question = ArithmeticUtils.customArithmetic(QuestionConst.ADD_LEN_CARD, QuestionConst.SUB_LEN_CARD, QuestionConst.MUL_FIRST_CARD, QuestionConst.MUL_SECOND_CARD);
        final int[] correctAnswer = {ArithmeticUtils.getMathAnswer(question)};
        questionText.setText(question);
        
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
            .setTitle("关闭屏蔽验证")
            .setView(dialogView)
            .create();
        
        // 提交答案按钮
        submitButton.setOnClickListener(v -> {
            String userAnswer = answerEdit.getText().toString().trim();
            if (userAnswer.isEmpty()) {
                UiFeedback.showTemporaryText(resultText, "⚠️ 请输入答案", 0xFFFF5722);
                return;
            }
            
            try {
                int answer = Integer.parseInt(userAnswer);
                if (answer == correctAnswer[0]) {
                    // 答案正确，关闭屏蔽
                    UiFeedback.showTemporaryText(
                            resultText,
                            "✅ 答案正确！",
                            requireContext().getColor(android.R.color.holo_green_light));
                    
                    // 延迟关闭弹窗并执行关闭屏蔽
                    new Handler().postDelayed(() -> {
                        dialog.dismiss();
                        relaxManager.setAppMonitoringEnabled(packageName, false);
                        android.util.Log.d("HomeFragment", "算术题验证通过，关闭屏蔽: " + packageName);
                        UiFeedback.show(requireContext(), "已关闭屏蔽");
                        
                        // 更新APP列表显示
                        updateAppCardsDisplay();
                    }, Const.TRANSIENT_FEEDBACK_DURATION_MS);
                    
                } else {
                    // 答案错误
                    UiFeedback.showTemporaryText(
                            resultText,
                            "❌ 答案错误，切到下一题",
                            requireContext().getColor(android.R.color.holo_red_light));
                    
                    // 清空输入框
                    answerEdit.setText("");
                    
                    // 3秒后生成新题目
                    new Handler().postDelayed(() -> {
                        String newQuestion = ArithmeticUtils.customArithmetic(
                                QuestionConst.ADD_LEN_MAX, QuestionConst.ADD_LEN_MAX, QuestionConst.MUL_LEN_MAX, QuestionConst.MUL_LEN_MAX);
                        correctAnswer[0] = ArithmeticUtils.getMathAnswer(newQuestion);
                        questionText.setText(newQuestion);
                        answerEdit.setText("");
                        resultText.setVisibility(View.GONE);
                    }, Const.TRANSIENT_FEEDBACK_DURATION_MS);
                }
            } catch (NumberFormatException e) {
                UiFeedback.showTemporaryText(resultText, "⚠️ 请输入有效数字", 0xFFFF5722);
            }
        });
        
        // 取消按钮
        cancelButton.setOnClickListener(v -> {
            dialog.dismiss();
            // 取消关闭屏蔽，恢复开关状态
            if (appCardAdapter != null) {
                appCardAdapter.updateData(allApps);
            }
        });
        
        // 回车键提交答案
        answerEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || 
                (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                submitButton.performClick();
                return true;
            }
            return false;
        });
        
        dialog.show();
        
        // 让输入框获得焦点
        answerEdit.requestFocus();
    }

    private void showDeleteUndo(CustomApp deletedApp, AppSettingsSnapshot snapshot) {
        Snackbar snackbar = UiFeedback.make(requireView(), "已删除")
                .setAction("撤销", v -> {
                    boolean restored = customAppManager.addCustomApp(
                            deletedApp.getAppName(),
                            deletedApp.getPackageName(),
                            deletedApp.getTargetWord(),
                            deletedApp.getRelaxedLimitCount());
                    if (restored) {
                        // 每-APP设置已在删除时清理，此处从快照一并恢复
                        appSettingsManager.restoreFrom(snapshot, deletedApp.getPackageName());
                        relaxManager.restoreFrom(snapshot, deletedApp.getPackageName());
                        updateAppCardsDisplay();
                    }
                });
        snackbar.show();
    }

    private void showInputError(
            TextInputLayout layout,
            TextInputEditText input,
            String message) {
        UiFeedback.showInputError(layout, input, message);
    }

    private void showInputError(EditText input, String message) {
        UiFeedback.showInputError(input, message);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshPermissionStatus();
        // 更新APP列表
        updateAppList();
        // 更新APP卡片数据
        if (appCardAdapter != null) {
            appCardAdapter.updateData(allApps);
        }
        
        // 启动倒计时
        startCountdown();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // 停止倒计时
        stopCountdown();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        // 清理资源
        stopCountdown();
        countdownHandler = null;
        countdownRunnable = null;
    }

    @Override
    public void onDestroyView() {
        permissionStatusView = null;
        permissionOptionalView = null;
        super.onDestroyView();
    }
    
    /**
     * 供外部调用的方法，用于更新APP卡片显示
     */
    public void updateAppCardsDisplay() {
        updateAppList();
        if (appCardAdapter != null) {
            appCardAdapter.updateData(allApps);
        }
    }
    
    /**
     * 启动倒计时
     */
    private void startCountdown() {
        if (countdownHandler == null) {
            countdownHandler = new Handler(Looper.getMainLooper());
        }
        
        if (countdownRunnable == null) {
            countdownRunnable = new Runnable() {
                @Override
                public void run() {
                    // 更新APP卡片数据
                    if (appCardAdapter != null) {
                        appCardAdapter.updateData(allApps);
                    }
                    // 每秒更新一次
                    countdownHandler.postDelayed(this, 1000);
                }
            };
        }
        
        countdownHandler.post(countdownRunnable);
    }
    
    /**
     * 停止倒计时
     */
    private void stopCountdown() {
        if (countdownHandler != null && countdownRunnable != null) {
            countdownHandler.removeCallbacks(countdownRunnable);
        }
    }
} 
