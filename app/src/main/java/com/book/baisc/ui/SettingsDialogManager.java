package com.book.baisc.ui;

import android.content.Context;
import android.text.InputFilter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.book.baisc.config.Const;
import com.book.baisc.config.SettingsManager;
import com.book.baisc.floating.FloatingAccessibilityService;

/**
 * 设置对话框管理器
 * 负责处理所有设置相关的弹窗和文字内容逻辑
 */
public class SettingsDialogManager {
    
    private final Context context;
    private final SettingsManager settingsManager;

    private static String[] appOptions;
    private static Const.SupportedApp[] apps = Const.SupportedApp.values();

    public SettingsDialogManager(Context context, SettingsManager settingsManager) {
        this.context = context;
        this.settingsManager = settingsManager;
    }

    static {
        appOptions = new String[apps.length];
        for (int i = 0; i < apps.length; i++) {
            appOptions[i] = apps[i].getAppName();
        }
    }

    /**
     * 显示时间设置对话框
     */
    public void showTimeSettingDialog(boolean isDaily) {
        // 获取当前活跃APP
        Const.SupportedApp currentApp = com.book.baisc.config.Share.currentApp;
        
        if (currentApp != null) {
            // 直接为当前活跃APP设置时间间隔
            showTimeSettingDialogForApp(currentApp, isDaily);
        } else {
            // 没有活跃APP，让用户选择为哪个APP设置
            showAppSelectionDialog(isDaily);
        }
    }
    
    /**
     * 显示APP选择对话框
     */
    private void showAppSelectionDialog(boolean isDaily) {
        String dialogTitle = isDaily ? "严格模式 - 选择APP" : "宽松模式 - 选择APP";

        android.util.Log.d("SettingsDialog", "显示APP选择对话框: " + dialogTitle);
        
        new android.app.AlertDialog.Builder(context)
            .setTitle(dialogTitle)
            .setItems(appOptions, (dialog, which) -> {
                Const.SupportedApp selectedApp = apps[which];
                android.util.Log.d("SettingsDialog", "用户选择APP: " + selectedApp.name());
                showTimeSettingDialogForApp(selectedApp, isDaily);
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 为指定APP显示时间设置对话框
     */
    private void showTimeSettingDialogForApp(Const.SupportedApp app, boolean isDaily) {
        final int[] intervals = isDaily ? 
            SettingsManager.getDailyAvailableIntervals() : 
            SettingsManager.getCasualAvailableIntervals();
        
        String[] intervalOptions = new String[intervals.length];
        for (int i = 0; i < intervals.length; i++) {
            intervalOptions[i] = SettingsManager.getIntervalDisplayText(intervals[i]);
        }

        // 获取指定APP的当前设置
        int currentInterval = settingsManager.getAppAutoShowInterval(app);
        
        int checkedItem = -1;
        for (int i = 0; i < intervals.length; i++) {
            if (intervals[i] == currentInterval) {
                checkedItem = i;
                break;
            }
        }
        
        String dialogTitle = isDaily ? "严格模式" : "宽松模式";
        String appName = app.getAppName();
        String fullTitle = dialogTitle + " - " + appName;

        android.util.Log.d("SettingsDialog", "显示时间设置对话框: " + fullTitle);
        android.util.Log.d("SettingsDialog", "APP " + app.name() + " 当前时间间隔: " + currentInterval + "秒");

        new android.app.AlertDialog.Builder(context)
            .setTitle(fullTitle)
            .setSingleChoiceItems(intervalOptions, checkedItem, (dialog, which) -> {
                int selectedInterval = intervals[which];
                
                // 为指定APP设置时间间隔
                settingsManager.setAppAutoShowInterval(app, selectedInterval);
                android.util.Log.d("SettingsDialog", "设置APP " + app.name() + " 时间间隔为: " + selectedInterval + "秒");
                
                // 验证设置是否成功
                int verifyInterval = settingsManager.getAppAutoShowInterval(app);
                android.util.Log.d("SettingsDialog", "验证APP " + app.name() + " 实际保存的时间间隔: " + verifyInterval + "秒");
                
                // 检查是否是宽松模式
                boolean isCasualMode = settingsManager.isAppCasualMode(app);
                android.util.Log.d("SettingsDialog", "APP " + app.name() + " 是否宽松模式: " + isCasualMode);
                
                // 检查宽松模式的关闭次数
                int casualCount = settingsManager.getAppCasualCloseCount(app);
                android.util.Log.d("SettingsDialog", "APP " + app.name() + " 今日宽松模式关闭次数: " + casualCount);
                
                // 通知服务配置已更改
                FloatingAccessibilityService.notifyIntervalChanged();
                       
                // 显示提示信息
                showIntervalExplanation(selectedInterval);
                
                String modeText = isCasualMode ? "宽松模式" : "严格模式";
                Toast.makeText(context, "已为" + appName + "设置为: " + intervalOptions[which] + " (" + modeText + ")", Toast.LENGTH_LONG).show();
                dialog.dismiss();
               })
               .setNegativeButton("取消", null)
               .show();
    }
    
    /**
     * 显示时间间隔说明
     */
    public void showIntervalExplanation(int interval) {
        StringBuilder explanation = new StringBuilder();
        explanation.append("新的时长，将在回答一次算术题后才会生效");

        new android.app.AlertDialog.Builder(context)
                .setTitle("解禁时长说明")
               .setMessage(explanation.toString())
                .setPositiveButton("好的", null)
                .show();
    }
    
    /**
     * 显示标签设置对话框
     */
    public void showTagSettingDialog() {
        final String[] predefinedTags = SettingsManager.getAvailableTags();
        final String customTagOption = "自定义...";

        // 将预设标签和"自定义"选项合并
        final String[] dialogOptions = new String[predefinedTags.length + 1];
        System.arraycopy(predefinedTags, 0, dialogOptions, 0, predefinedTags.length);
        dialogOptions[dialogOptions.length - 1] = customTagOption;

        new android.app.AlertDialog.Builder(context)
                .setTitle("选择或自定义目标")
                .setItems(dialogOptions, (dialog, which) -> {
                    if (which == predefinedTags.length) {
                        // 点击了"自定义..."
                        showCustomTagInputDialog();
                    } else {
                        // 点击了预设标签
                        String selectedTag = dialogOptions[which];
                        settingsManager.setMotivationTag(selectedTag);
                        Toast.makeText(context, "已设置为: " + selectedTag, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 显示自定义标签输入对话框
     */
    public void showCustomTagInputDialog() {
        final EditText input = new EditText(context);
        // 设置输入长度限制为8
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(8) });
        input.setHint("不超过8个字");

        new android.app.AlertDialog.Builder(context)
            .setTitle("自定义激励语标签")
            .setView(input)
            .setPositiveButton("确定", (dialog, which) -> {
                String customTag = input.getText().toString().trim();
                if (customTag.isEmpty()) {
                    Toast.makeText(context, "标签不能为空", Toast.LENGTH_SHORT).show();
                } else {
                    settingsManager.setMotivationTag(customTag);
                    Toast.makeText(context, "已设置为: " + customTag, Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
               .show();
    }
    
    /**
     * 显示最新安装包地址对话框
     */
    public void showLatestApkDialog() {
        try {
            // 获取当前版本信息
            String versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            
            // 构建弹窗内容
            StringBuilder content = new StringBuilder();
            content.append("• 本机当前版本：").append(versionName).append("\n\n");
            content.append("• 下载页面（找到最新的 apk 文件下载）：\n");
            content.append("https://gitee.com/interest2/anti-addiction/releases\n");
            content.append("https://github.com/interest2/anti-addiction/releases\n\n");

            content.append("备注：\n");
            content.append("• gitee地址：需要登录\n");
            content.append("• github地址：无需登录，但网络可能不稳定\n\n");
            
            new android.app.AlertDialog.Builder(context)
                .setTitle("最新安装包地址")
                .setMessage(content.toString())
                .setPositiveButton("复制gitee地址", (dialog, which) -> {
                    copyToClipboard("https://gitee.com/interest2/anti-addiction/releases");
                    Toast.makeText(context, "gitee地址已复制到剪贴板", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("复制github地址", (dialog, which) -> {
                    copyToClipboard("https://github.com/interest2/anti-addiction/releases");
                    Toast.makeText(context, "gitHub地址已复制到剪贴板", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("关闭", null)
                .show();
                
        } catch (Exception e) {
            android.util.Log.e("SettingsDialogManager", "获取版本信息失败", e);
            Toast.makeText(context, "获取版本信息失败", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 显示目标完成日期选择对话框
     */
    public void showTargetDateSettingDialog() {
        // 获取当前设置的日期
        String currentDate = settingsManager.getTargetCompletionDate();
        
        // 创建日期选择器
        android.app.DatePickerDialog datePickerDialog = new android.app.DatePickerDialog(
            context,
            (view, year, month, dayOfMonth) -> {
                // 格式化日期为 yyyy-MM-dd 格式
                String selectedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth);
                settingsManager.setTargetCompletionDate(selectedDate);
                Toast.makeText(context, "目标完成日期已设置为: " + selectedDate, Toast.LENGTH_SHORT).show();
                
                // 通知MainActivity更新按钮文本
                if (context instanceof MainActivity) {
                    ((MainActivity) context).updateTargetDateButtonText();
                }
            },
            java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
            java.util.Calendar.getInstance().get(java.util.Calendar.MONTH),
            java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
        );
        
        // 设置最小日期为今天
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis());
        
        // 如果当前有设置日期，解析并设置为当前选择
        if (!"待设置".equals(currentDate) && !currentDate.isEmpty()) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
                java.util.Date date = sdf.parse(currentDate);
                if (date != null) {
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.setTime(date);
                    datePickerDialog.updateDate(cal.get(java.util.Calendar.YEAR), 
                                               cal.get(java.util.Calendar.MONTH), 
                                               cal.get(java.util.Calendar.DAY_OF_MONTH));
                }
            } catch (Exception e) {
                android.util.Log.w("SettingsDialogManager", "解析当前日期失败", e);
            }
        }
        
        datePickerDialog.setTitle("选择目标完成日期");
        datePickerDialog.show();
    }
    
    /**
     * 显示悬浮窗位置设置对话框
     */
    public void showFloatingPositionDialog() {
        // 创建自定义布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        // 上边缘设置
        android.widget.TextView topLabel = new android.widget.TextView(context);
        topLabel.setText("上边缘距离顶部距离（像素）:");
        topLabel.setTextSize(16);
        layout.addView(topLabel);

        final android.widget.EditText topEdit = new android.widget.EditText(context);
        topEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        topEdit.setText(String.valueOf(settingsManager.getFloatingTopOffset()));
        topEdit.setHint("默认: 130");
        layout.addView(topEdit);

        // 添加间距
        android.view.View spacer1 = new android.view.View(context);
        spacer1.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 30));
        layout.addView(spacer1);

        // 下边缘设置
        android.widget.TextView bottomLabel = new android.widget.TextView(context);
        bottomLabel.setText("下边缘距离底部距离（像素）:");
        bottomLabel.setTextSize(16);
        layout.addView(bottomLabel);

        final android.widget.EditText bottomEdit = new android.widget.EditText(context);
        bottomEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        bottomEdit.setText(String.valueOf(settingsManager.getFloatingBottomOffset()));
        bottomEdit.setHint("默认: 230");
        layout.addView(bottomEdit);

        // 添加说明文字
        android.view.View spacer2 = new android.view.View(context);
        spacer2.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 20));
        layout.addView(spacer2);

        android.widget.TextView hintText = new android.widget.TextView(context);
        hintText.setText("说明：不同机型分辨率不同，因此悬浮窗边缘距离顶部底部的距离可能需手动调整，直到观察到APP遮挡区域合适为止。");
        hintText.setTextSize(14);
        hintText.setTextColor(0xFF666666);
        layout.addView(hintText);

        new android.app.AlertDialog.Builder(context)
            .setTitle("调整悬浮窗边缘位置")
            .setView(layout)
            .setPositiveButton("确定", (dialog, which) -> {
                try {
                    String topText = topEdit.getText().toString().trim();
                    String bottomText = bottomEdit.getText().toString().trim();
                    
                    if (topText.isEmpty() || bottomText.isEmpty()) {
                        Toast.makeText(context, "请填写完整的数值", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    int topOffset = Integer.parseInt(topText);
                    int bottomOffset = Integer.parseInt(bottomText);
                    
                    // 数值范围检查
                    if (topOffset < 0 || topOffset > 300) {
                        Toast.makeText(context, "上边缘距离应在0-300像素之间", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    if (bottomOffset < 0 || bottomOffset > 400) {
                        Toast.makeText(context, "下边缘距离应在0-400像素之间", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    // 保存设置
                    settingsManager.setFloatingTopOffset(topOffset);
                    settingsManager.setFloatingBottomOffset(bottomOffset);
                    
                    Toast.makeText(context, "悬浮窗位置已更新", Toast.LENGTH_SHORT).show();
                    
                } catch (NumberFormatException e) {
                    Toast.makeText(context, "请输入有效的数字", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 复制文本到剪贴板
     */
    private void copyToClipboard(String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("下载地址", text);
        clipboard.setPrimaryClip(clip);
    }
    
    /**
     * 更新标签按钮文本
     */
    public void updateTagButtonText(Button tagButton) {
        if (tagButton != null) {
            String currentTag = settingsManager.getMotivationTag();
            tagButton.setText("目标: " + currentTag);
        }
    }
    
    /**
     * 更新日期按钮文本
     */
    public void updateDateButtonText(Button dateButton) {
        if (dateButton != null) {
            String currentDate = settingsManager.getTargetCompletionDate();
            dateButton.setText("完成日期: " + currentDate);
        }
    }
    
    /**
     * 更新宽松模式按钮状态
     */
    public void updateCasualButtonState(Button casualButton) {
        if (casualButton != null) {
            // 计算所有支持APP的宽松模式次数总和
            int totalCloseCount = 0;
            for (Const.SupportedApp app : Const.SupportedApp.values()) {
                totalCloseCount += settingsManager.getAppCasualCloseCount(app);
            }
            
            // 如果所有APP都没有使用过，则使用全局次数（兼容性）
            if (totalCloseCount == 0) {
                totalCloseCount = settingsManager.getCasualCloseCount();
            }
            
            casualButton.setEnabled(totalCloseCount < Const.CASUAL_LIMIT_COUNT);
        }
    }
    
    /**
     * 更新宽松模式次数显示
     */
    public void updateCasualCountDisplay(TextView countText) {
        if (countText != null) {
            // 计算所有支持APP的宽松模式次数总和
            int totalCloseCount = 0;
            for (Const.SupportedApp app : Const.SupportedApp.values()) {
                totalCloseCount += settingsManager.getAppCasualCloseCount(app);
            }
            
            // 如果所有APP都没有使用过，则使用全局次数（兼容性）
            if (totalCloseCount == 0) {
                totalCloseCount = settingsManager.getCasualCloseCount();
            }
            
            int remainingCount = Math.max(0, Const.CASUAL_LIMIT_COUNT - totalCloseCount);
            countText.setText("总剩余: " + remainingCount + "次");
        }
    }
    
    /**
     * 更新特定APP的宽松模式剩余次数显示
     */
    public void updateAppCasualCountDisplay(TextView countText, Const.SupportedApp app) {
        if (countText != null) {
            int closeCount = settingsManager.getAppCasualCloseCount(app);
            int remainingCount = Math.max(0, Const.CASUAL_LIMIT_COUNT - closeCount);
            countText.setText("宽松剩余: " + remainingCount + "次");
        }
    }
} 