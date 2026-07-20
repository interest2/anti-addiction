package com.book.mask.config;

import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tencent.mmkv.MMKV;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores user-detected input method packages so keyboard windows are not treated as target apps.
 */
public class InputMethodPackageManager {
    private static final String TAG = "InputMethodPackageMgr";
    private static final String PREF_NAME = "input_method_packages";
    private static final String KEY_PACKAGES = "packages";

    private static volatile InputMethodPackageManager instance;

    private final Gson gson = new Gson();
    private MMKV mmkv;
    private List<String> packages = new ArrayList<>();

    private InputMethodPackageManager() {
        try {
            mmkv = MMKV.mmkvWithID(PREF_NAME);
        } catch (Exception e) {
            Log.w(TAG, "MMKV init failed", e);
            mmkv = null;
        }
        load();
    }

    public static InputMethodPackageManager getInstance() {
        if (instance == null) {
            synchronized (InputMethodPackageManager.class) {
                if (instance == null) {
                    instance = new InputMethodPackageManager();
                }
            }
        }
        return instance;
    }

    public synchronized boolean addPackage(String packageName) {
        String normalizedPackageName = normalize(packageName);
        if (normalizedPackageName.isEmpty() || packages.contains(normalizedPackageName)) {
            return false;
        }

        packages.add(normalizedPackageName);
        save();
        return true;
    }

    public synchronized boolean contains(String packageName) {
        String normalizedPackageName = normalize(packageName);
        if (normalizedPackageName.isEmpty()) {
            return false;
        }

        for (String savedPackage : packages) {
            if (normalizedPackageName.equals(savedPackage)
                    || normalizedPackageName.contains(savedPackage)
                    || savedPackage.contains(normalizedPackageName)) {
                return true;
            }
        }
        return false;
    }

    public synchronized List<String> getPackages() {
        return Collections.unmodifiableList(new ArrayList<>(packages));
    }

    private void load() {
        if (mmkv == null) {
            return;
        }

        String json = mmkv.getString(KEY_PACKAGES, "[]");
        try {
            Type type = new TypeToken<List<String>>() {}.getType();
            List<String> loadedPackages = gson.fromJson(json, type);
            if (loadedPackages != null) {
                packages = new ArrayList<>();
                for (String packageName : loadedPackages) {
                    String normalizedPackageName = normalize(packageName);
                    if (!normalizedPackageName.isEmpty() && !packages.contains(normalizedPackageName)) {
                        packages.add(normalizedPackageName);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "load failed", e);
        }
    }

    private void save() {
        if (mmkv == null) {
            return;
        }

        try {
            mmkv.putString(KEY_PACKAGES, gson.toJson(packages)).commit();
        } catch (Exception e) {
            Log.w(TAG, "save failed", e);
        }
    }

    private String normalize(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return "";
        }
        return packageName.trim();
    }
}
