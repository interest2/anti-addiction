package com.book.mask.floating;

import android.graphics.PixelFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;

import com.book.mask.config.Const;
import com.book.mask.setting.RelaxManager;
import com.book.mask.setting.AppSettingsManager;

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

    public static WindowManager.LayoutParams getLayoutParams(WindowManager windowManager, AppSettingsManager appSettingsManager){

        // 设置悬浮窗参数
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        // 添加FLAG_NOT_FOCUSABLE确保悬浮窗不会获得焦点，避免影响前台应用检测
        // 添加FLAG_NOT_TOUCH_MODAL确保触摸事件可以传递到下层窗口
        // 添加FLAG_NOT_TOUCHABLE确保悬浮窗默认不拦截触摸事件（除了特定区域）
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        layoutParams.format = PixelFormat.TRANSLUCENT;
        layoutParams.gravity = Gravity.LEFT | Gravity.TOP;

        // 计算悬浮窗位置和大小
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);

        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;

        // 设置悬浮窗位置和大小
        int topOffset = appSettingsManager.getFloatingTopOffset();
        int bottomOffset = appSettingsManager.getFloatingBottomOffset();

        layoutParams.x = 0;
        layoutParams.y = topOffset;
        layoutParams.width = screenWidth;
        layoutParams.height = screenHeight - topOffset - bottomOffset;
        return layoutParams;
    }

    public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

}
