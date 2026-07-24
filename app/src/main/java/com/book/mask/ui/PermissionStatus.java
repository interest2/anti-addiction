package com.book.mask.ui;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import com.book.mask.floating.FloatService;

import java.util.List;

public final class PermissionStatus {
    private PermissionStatus() {
    }

    public static boolean canDrawOverlays(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(context);
    }

    public static boolean isAccessibilityServiceEnabled(Context context) {
        ComponentName service = new ComponentName(context, FloatService.class);
        AccessibilityManager manager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager != null) {
            List<AccessibilityServiceInfo> enabledServices =
                    manager.getEnabledAccessibilityServiceList(
                            AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            for (AccessibilityServiceInfo info : enabledServices) {
                if (service.flattenToString().equals(info.getId())) {
                    return true;
                }
            }
        }

        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }

        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            ComponentName enabledService = ComponentName.unflattenFromString(splitter.next());
            if (service.equals(enabledService)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBackgroundRunningAllowed(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager powerManager =
                (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return powerManager != null
                && powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
    }
}
