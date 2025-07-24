package com.book.baisc.floating;

import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.book.baisc.config.Const;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;


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

    public static String hintDate(String targetDateStr){
        String dateHint = "";
        // 获取目标完成日期

        // 如果不是默认值，则计算剩余天数
        if (!Const.TARGET_TO_BE_SET.equals(targetDateStr) && !targetDateStr.isEmpty()) {
            try {
                // 解析目标日期
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date targetDate = sdf.parse(targetDateStr);

                if (targetDate != null) {
                    // 获取今天的日期（去掉时间部分）
                    Calendar today = Calendar.getInstance();
                    today.set(Calendar.HOUR_OF_DAY, 0);
                    today.set(Calendar.MINUTE, 0);
                    today.set(Calendar.SECOND, 0);
                    today.set(Calendar.MILLISECOND, 0);

                    // 获取目标日期的日历对象
                    Calendar targetCalendar = Calendar.getInstance();
                    targetCalendar.setTime(targetDate);
                    targetCalendar.set(Calendar.HOUR_OF_DAY, 0);
                    targetCalendar.set(Calendar.MINUTE, 0);
                    targetCalendar.set(Calendar.SECOND, 0);
                    targetCalendar.set(Calendar.MILLISECOND, 0);

                    // 计算天数差（毫秒转换为天）
                    long timeDiff = targetCalendar.getTimeInMillis() - today.getTimeInMillis();
                    int daysRemaining = (int) (timeDiff / (24 * 60 * 60 * 1000));

                    // 根据剩余天数生成提示文本
                    if (daysRemaining > 0) {
                        dateHint = "距离目标只剩 " + daysRemaining + " 天！";
                    } else if (daysRemaining == 0) {
                        dateHint = "今天是目标日期！";
                    } else {
                        dateHint = "目标日期已过期 " + Math.abs(daysRemaining) + " 天！";
                    }

                    // 使用 dateHint 变量
                    Log.d(TAG, "日期提示: " + dateHint);

                }
            } catch (Exception e) {
                Log.e(TAG, "计算剩余天数失败", e);
            }
        }
        if(!dateHint.isEmpty()){
            dateHint = dateHint + "\n";
        }
        return dateHint;
    }

//    static boolean findTextInNode(AccessibilityNodeInfo node, HashSet targetText) {
    static boolean findTextInNode(AccessibilityNodeInfo node, String targetText) {
        if (node == null) return false;

        // 检查当前节点的文本
        CharSequence text = node.getText();
        text = !isEmpty(text) ? text : node.getContentDescription();

        if (!isEmpty(text) && targetText.contains(text.toString())) {
            // 检查节点是否可见
            if (node.isVisibleToUser()) {
                Log.d(TAG, "找到目标文本: " + targetText + " (可见)");
                return true;
            } else {
                Log.d(TAG, "找到目标文本: " + targetText + " (不可见，忽略)");
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

    public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

}
