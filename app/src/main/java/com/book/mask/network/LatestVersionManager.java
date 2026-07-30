package com.book.mask.network;

import com.book.mask.config.Share;

public final class LatestVersionManager {
    private static final Object VERSION_REQUEST_LOCK = new Object();
    private static final long LATEST_VERSION_VALIDITY_MS = 60_000L;
    private static final String APK_DOWNLOAD_URL_PREFIX =
            "https://gitee.com/interest2/anti-addiction/releases/download/v";
    private static final String APK_DOWNLOAD_URL_SUFFIX = "/app-release.apk";

    private LatestVersionManager() {
    }

    public static String refreshLatestVersion() {
        synchronized (VERSION_REQUEST_LOCK) {
            return requestLatestVersion();
        }
    }

    public static String getLatestVersionForDownload() {
        synchronized (VERSION_REQUEST_LOCK) {
            String freshVersion = getFreshLatestVersion();
            return freshVersion != null ? freshVersion : requestLatestVersion();
        }
    }

    private static String requestLatestVersion() {
        AppConfigManager.refreshConfig();
        return getFreshLatestVersion();
    }

    public static String getFreshLatestVersion() {
        String version = Share.latestVersion;
        long timestamp = Share.latestVersionTimestamp;
        long now = System.currentTimeMillis();
        boolean isFresh = version != null
                && !version.trim().isEmpty()
                && !"获取失败".equals(version)
                && timestamp > 0L
                && now >= timestamp
                && now - timestamp <= LATEST_VERSION_VALIDITY_MS;
        return isFresh ? version : null;
    }

    public static String buildLatestApkDownloadUrl(String version) {
        return APK_DOWNLOAD_URL_PREFIX + version.trim() + APK_DOWNLOAD_URL_SUFFIX;
    }
}
