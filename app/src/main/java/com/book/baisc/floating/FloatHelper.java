package com.book.baisc.floating;

import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.book.baisc.config.Share;

public class FloatHelper {
    private static final String TAG = "FloatingAccessibility";

    /**
     * 判断是否是输入法应用
     */
    static boolean isInputMethodApp(String packageName) {
        // 常见输入法包名列表
        String[] inputMethodPackages = {
                "com.baidu.input",           // 百度输入法
                "com.baidu.input_hihonor",   // 荣耀百度输入法
                "com.sohu.inputmethod.sogou", // 搜狗输入法
                "com.iflytek.inputmethod",   // 讯飞输入法
                "com.touchtype.swiftkey",    // SwiftKey
                "com.google.android.inputmethod.latin", // Google输入法
                "com.android.inputmethod.latin", // 系统输入法
                "com.samsung.android.honeyboard", // 三星输入法
                "com.huawei.inputmethod",    // 华为输入法
                "com.xiaomi.inputmethod",    // 小米输入法
                "com.tencent.qqpinyin",      // QQ输入法
                "com.qihoo.inputmethod"      // 360输入法
        };

        for (String inputMethodPackage : inputMethodPackages) {
            if (packageName.contains(inputMethodPackage) || packageName.contains("input")) {
                return true;
            }
        }

        return false;
    }


    static boolean findTextInNode(AccessibilityNodeInfo node, String targetText) {
        if (node == null) return false;

        // 检查当前节点的文本
        CharSequence text = node.getText();
        if (text != null && text.toString().equalsIgnoreCase(targetText)) {
            // 检查节点是否可见
            if (node.isVisibleToUser()) {
                Log.d(TAG, "找到目标文本: " + targetText + " (可见)");
                return true;
            } else {
                Log.d(TAG, "找到目标文本: " + targetText + " (不可见，忽略)");
            }
        }

        // 检查contentDescription
        CharSequence contentDesc = node.getContentDescription();
        if (contentDesc != null && contentDesc.toString().equalsIgnoreCase(targetText)) {
            // 检查节点是否可见
            if (node.isVisibleToUser()) {
                Log.d(TAG, "在contentDescription中找到目标文本: " + targetText + " (可见)");
                return true;
            } else {
                Log.d(TAG, "在contentDescription中找到目标文本: " + targetText + " (不可见，忽略)");
            }
        }

        // 递归检查子节点
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (findTextInNode(child, targetText)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }

        return false;
    }

    /**
     * 临时调试方法：输出可见文本内容
     */
    static void logVisibleTexts(AccessibilityNodeInfo node, int currentDepth, int maxDepth) {
        if (node == null || currentDepth > maxDepth) return;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < currentDepth; i++) {
            sb.append("  ");
        }
        String indent = sb.toString();

        // 输出当前节点的文本
        CharSequence text = node.getText();
        if (text != null && !text.toString().trim().isEmpty() && node.isVisibleToUser()) {
            Log.d(TAG, indent + "文本: " + text.toString().trim());
        }

        // 输出contentDescription
        CharSequence contentDesc = node.getContentDescription();
        if (contentDesc != null && !contentDesc.toString().trim().isEmpty() && node.isVisibleToUser()) {
            Log.d(TAG, indent + "描述: " + contentDesc.toString().trim());
        }

        // 递归检查子节点（限制数量）
        int childCount = Math.min(node.getChildCount(), 15);
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                logVisibleTexts(child, currentDepth + 1, maxDepth);
                child.recycle();
            }
        }
    }



}
