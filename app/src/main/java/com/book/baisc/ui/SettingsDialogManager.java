package com.book.baisc.ui;

import android.content.Context;
import android.text.InputFilter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.book.baisc.config.SettingsManager;
import com.book.baisc.floating.FloatingAccessibilityService;

/**
 * 设置对话框管理器
 * 负责处理所有设置相关的弹窗和文字内容逻辑
 */
public class SettingsDialogManager {
    
    private final Context context;
    private final SettingsManager settingsManager;
    
    public SettingsDialogManager(Context context, SettingsManager settingsManager) {
        this.context = context;
        this.settingsManager = settingsManager;
    }
    
    /**
     * 显示时间设置对话框
     */
    public void showTimeSettingDialog(boolean isDaily) {
        final int[] intervals = isDaily ? 
            SettingsManager.getDailyAvailableIntervals() : 
            SettingsManager.getCasualAvailableIntervals();
        
        String[] intervalOptions = new String[intervals.length];
        for (int i = 0; i < intervals.length; i++) {
            intervalOptions[i] = SettingsManager.getIntervalDisplayText(intervals[i]);
        }

        int currentInterval = settingsManager.getAutoShowInterval();
        int checkedItem = -1;
        for (int i = 0; i < intervals.length; i++) {
            if (intervals[i] == currentInterval) {
                checkedItem = i;
                break;
            }
        }
        
        String dialogTitle = isDaily ? "严格模式" : "宽松模式";

        new android.app.AlertDialog.Builder(context)
            .setTitle(dialogTitle)
            .setSingleChoiceItems(intervalOptions, checkedItem, (dialog, which) -> {
                int selectedInterval = intervals[which];
                settingsManager.setAutoShowInterval(selectedInterval);
                
                // 通知服务配置已更改
                FloatingAccessibilityService.notifyIntervalChanged();
                       
                // 显示提示信息
                showIntervalExplanation(selectedInterval);
                
                Toast.makeText(context, "已设置为: " + intervalOptions[which], Toast.LENGTH_SHORT).show();
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
     * 更新宽松模式按钮状态
     */
    public void updateCasualButtonState(Button casualButton) {
        if (casualButton != null) {
            int closeCount = settingsManager.getCasualCloseCount();
            casualButton.setEnabled(closeCount < com.book.baisc.config.Const.CASUAL_LIMIT_COUNT);
        }
    }
    
    /**
     * 更新宽松模式次数显示
     */
    public void updateCasualCountDisplay(TextView countText) {
        if (countText != null) {
            int closeCount = settingsManager.getCasualCloseCount();
            int remainingCount = Math.max(0, com.book.baisc.config.Const.CASUAL_LIMIT_COUNT - closeCount);
            countText.setText("今日剩余: " + remainingCount + "次");
        }
    }
} 