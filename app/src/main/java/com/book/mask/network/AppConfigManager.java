package com.book.mask.network;

import android.content.Context;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import com.book.mask.config.Share;
import com.book.mask.constant.CloudConst;
import com.book.mask.util.ContentUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;

public final class AppConfigManager {
    private static final String TAG = "AppConfigManager";
    private static final Object CONFIG_REQUEST_LOCK = new Object();
    private static final long CONFIG_VALIDITY_MS = 60_000L;
    private static volatile Context applicationContext;

    private AppConfigManager() {
    }

    /** 保存应用上下文，以便配置请求携带当前设备 ID。 */
    public static void initialize(Context context) {
        if (context != null) {
            applicationContext = context.getApplicationContext();
        }
    }

    public static void refreshConfig() {
        synchronized (CONFIG_REQUEST_LOCK) {
            if (isConfigFresh()) {
                return;
            }
            requestConfig();
        }
    }

    public static String getServerModel() {
        String serverModel = Share.serverModel;
        return serverModel == null || serverModel.trim().isEmpty()
                ? Share.DEFAULT_SERVER_MODEL
                : serverModel;
    }

    public static boolean isAppreciateEnabled(int localImageCode) {
        return localImageCode > 0 && Share.appreciateImageCode == localImageCode;
    }

    private static boolean isConfigFresh() {
        long now = System.currentTimeMillis();
        long timestamp = Share.latestVersionTimestamp;
        return timestamp > 0L
                && now >= timestamp
                && now - timestamp <= CONFIG_VALIDITY_MS;
    }

    private static void requestConfig() {
        try {
            String response = ContentUtils.doHttpGet(
                    buildConfigUrl(),
                    Collections.singletonMap("Accept", "application/json"));
            JSONObject config = new JSONObject(response);
            String latestVersion = getRequiredString(config, "latestVersion");
            String serverModel = getRequiredString(config, "serverModel");
            int appreciateImageCode = getAppreciateImageCode(config);

            Share.latestVersion = latestVersion;
            Share.serverModel = serverModel;
            Share.appreciateImageCode = appreciateImageCode;
            Share.latestVersionTimestamp = System.currentTimeMillis();
        } catch (Exception e) {
            Share.latestVersionTimestamp = 0L;
            Share.latestVersion = "获取失败";
            Share.serverModel = Share.DEFAULT_SERVER_MODEL;
            Share.appreciateImageCode = 0;
            Log.w(TAG, "获取远端配置失败，使用本地默认配置", e);
        }
    }

    private static String buildConfigUrl() {
        String url = CloudConst.DOMAIN_URL + CloudConst.GET_CONFIG_PATH;
        Context context = applicationContext;
        if (context == null) {
            return url;
        }
        String deviceId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return url;
        }
        return url + "?devid=" + Uri.encode(deviceId);
    }

    private static String getRequiredString(JSONObject config, String fieldName)
            throws JSONException {
        Object value = config.get(fieldName);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new IllegalArgumentException("配置字段无效: " + fieldName);
        }
        return ((String) value).trim();
    }

    private static int getAppreciateImageCode(JSONObject config) {
        Object value = config.opt("showAppreciate");
        if (!(value instanceof String) || !((String) value).matches("[0-9]+")) {
            return 0;
        }
        try {
            int imageCode = Integer.parseInt((String) value);
            return imageCode > 0 ? imageCode : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
