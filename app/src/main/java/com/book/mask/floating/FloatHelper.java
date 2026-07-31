package com.book.mask.floating;

import android.graphics.PixelFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;

import com.book.mask.config.InputMethodPackageManager;
import com.book.mask.personalize.AppSettingsManager;


public class FloatHelper {
    private static final String TAG = "FloatingAccessibility";

    /**
     * 判断是否是输入法应用
     */
    static boolean isInputMethodApp(String packageName) {
        // 输入法特殊包名列表（含input的包名直接放行，详见方法后的注释）
        String[] inputMethodPackages = {
                "com.touchtype.swiftkey",    // SwiftKey
                "com.tencent.qqpinyin",      // QQ输入法
                "com.samsung.android.honeyboard" // 三星输入法
        };

        for (String inputMethodPackage : inputMethodPackages) {
            if (packageName.contains("input") || packageName.contains(inputMethodPackage)) {
                return true;
            }
        }

        return InputMethodPackageManager.getInstance().contains(packageName);
//        "com.baidu.input",           // 百度输入法
//        "com.baidu.input_hihonor",   // 荣耀百度输入法
//        "com.sohu.inputmethod.sogou", // 搜狗输入法
//        "com.iflytek.inputmethod",   // 讯飞输入法
//        "com.google.android.inputmethod.latin", // Google输入法
//        "com.android.inputmethod.latin", // 系统输入法
//        "com.huawei.inputmethod",    // 华为输入法
//        "com.xiaomi.inputmethod",    // 小米输入法
//        "com.qihoo.inputmethod",     // 360输入法
//        "com.oppo.inputmethod",      // OPPO输入法
//        "com.coloros.inputmethod",   // ColorOS输入法
//        "com.vivo.inputmethod",      // vivo输入法
//        "com.bbk.inputmethod"       // vivo输入法（旧版本）
    }

    static boolean findTargetText(AccessibilityNodeInfo node, String targetText,
                                  TextScanDiagnostics diagnostics) {
        return findTargetTextInNode(node, targetText, 0, diagnostics);
    }

    private static boolean findTargetTextInNode(AccessibilityNodeInfo node, String targetText,
                                                int depth, TextScanDiagnostics diagnostics) {
        if (node == null) return false;

        // 排查模式下逐节点判定可见性以统计诊断信息；正常模式跳过这次开销，只在命中节点再判可见性
        boolean visible = diagnostics != null && node.isVisibleToUser();
        if (diagnostics != null) {
            diagnostics.onNodeVisited(depth, visible);
        }

        CharSequence text = node.getText();
        text = !isEmpty(text) ? text : node.getContentDescription();

        if (!isEmpty(text) && targetText.contains(text.toString())) {
            if (diagnostics == null) {
                visible = node.isVisibleToUser();
            } else {
                diagnostics.onTargetHit(depth, visible);
            }
            if (visible) {
                Log.d(TAG, "找到目标文本: " + targetText + " (可见)");
                return true;
            }
            Log.d(TAG, "找到目标文本: " + targetText + " (不可见，忽略)");
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (findTargetTextInNode(child, targetText, depth + 1, diagnostics)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }

        return false;
    }

    public static WindowManager.LayoutParams getLayoutParams(WindowManager windowManager,
                                                              AppSettingsManager appSettingsManager,
                                                              String packageName) {

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
        layoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;

        // 计算悬浮窗位置和大小
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);

        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;

        // 设置悬浮窗位置和大小
        int topOffset = appSettingsManager.getAppFloatingTopOffset(packageName);
        int bottomOffset = appSettingsManager.getAppFloatingBottomOffset(packageName);

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
