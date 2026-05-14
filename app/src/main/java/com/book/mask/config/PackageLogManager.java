package com.book.mask.config;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tencent.mmkv.MMKV;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 包名访问 LRU 日志：记录最近访问过的 10 个不同包名，下标 0 为最新
 */
public class PackageLogManager {
    private static final String TAG = "PackageLogManager";
    private static final String PREF_NAME = "package_log";
    private static final String KEY_LOGS = "logs";
    private static final String KEY_ENABLED = "enabled";
    private static final int MAX_SIZE = 8;

    private static volatile PackageLogManager instance;

    private MMKV mmkv;
    private final Gson gson = new Gson();
    private List<String> logs = new ArrayList<>();
    private boolean enabled = false;

    private PackageLogManager() {
        try {
            mmkv = MMKV.mmkvWithID(PREF_NAME);
        } catch (Exception e) {
            Log.w(TAG, "MMKV 初始化失败", e);
            mmkv = null;
        }
        load();
    }

    public static PackageLogManager getInstance() {
        if (instance == null) {
            synchronized (PackageLogManager.class) {
                if (instance == null) {
                    instance = new PackageLogManager();
                }
            }
        }
        return instance;
    }

    private void load() {
        if (mmkv == null) return;
        enabled = mmkv.getBoolean(KEY_ENABLED, false);
        String json = mmkv.getString(KEY_LOGS, "[]");
        try {
            Type type = new TypeToken<List<String>>() {}.getType();
            List<String> loaded = gson.fromJson(json, type);
            if (loaded != null) {
                logs = loaded;
            }
        } catch (Exception e) {
            Log.w(TAG, "load failed", e);
        }
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized void setEnabled(boolean value) {
        enabled = value;
        if (mmkv != null) {
            mmkv.putBoolean(KEY_ENABLED, value).commit();
        }
    }

    private void save() {
        if (mmkv == null) return;
        try {
            mmkv.putString(KEY_LOGS, gson.toJson(logs)).commit();
        } catch (Exception e) {
            Log.w(TAG, "save failed", e);
        }
    }

    /**
     * 记录一次包名访问：已在队首则跳过；否则移动/插入到队首，超过容量则裁掉尾部
     */
    public synchronized void record(String packageName) {
        if (!enabled) return;
        if (packageName == null || packageName.isEmpty()) return;
        if (packageName.contains("android")) return;
        if (!logs.isEmpty() && packageName.equals(logs.get(0))) return;
        logs.remove(packageName);
        logs.add(0, packageName);
        while (logs.size() > MAX_SIZE) {
            logs.remove(logs.size() - 1);
        }
        save();
    }

    /**
     * 返回当前 LRU 列表的快照，下标 0 为最新
     */
    public synchronized List<String> getLogs() {
        return Collections.unmodifiableList(new ArrayList<>(logs));
    }
}
