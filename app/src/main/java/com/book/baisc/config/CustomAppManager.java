package com.book.baisc.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class CustomAppManager {
    private static final String PREF_NAME = "custom_apps";
    private static final String KEY_CUSTOM_APPS = "custom_apps_list";
    private static CustomAppManager instance;
    private final Context context;
    private final SharedPreferences sharedPreferences;
    private final Gson gson;
    private List<Const.CustomApp> customApps;

    private CustomAppManager(Context context) {
        if (context != null) {
            this.context = context.getApplicationContext();
            this.sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            this.gson = new Gson();
            loadCustomApps();
        } else {
            // 只读模式，用于静态上下文
            this.context = null;
            this.sharedPreferences = null;
            this.gson = new Gson();
            this.customApps = new ArrayList<>();
        }
    }

    public static synchronized CustomAppManager getInstance() {
        if (instance == null) {
            // 如果没有初始化，返回一个只读的实例，不进行持久化操作
            // 这样可以避免在静态上下文中需要Context的问题
            instance = new CustomAppManager(null);
        }
        return instance;
    }
    
    /**
     * 初始化CustomAppManager（需要在Application或Activity中调用）
     */
    public static synchronized void initialize(Context context) {
        if (instance == null) {
            instance = new CustomAppManager(context);
        }
    }

    /**
     * 添加自定义APP
     */
    public boolean addCustomApp(String appName, String packageName, String targetWord, int casualLimitCount) {
        // 检查包名是否已存在
        if (Const.SupportedApp.isPackageNameExists(packageName)) {
            Log.w("CustomAppManager", "Package name already exists: " + packageName);
            return false;
        }

        // 创建新的自定义APP
        Const.CustomApp newApp = new Const.CustomApp(appName, packageName, targetWord, casualLimitCount);
        customApps.add(newApp);
        
        // 保存到SharedPreferences
        saveCustomApps();
        
        Log.d("CustomAppManager", "Added custom app: " + appName + " (" + packageName + ")");
        return true;
    }

    /**
     * 获取所有自定义APP
     */
    public List<Const.CustomApp> getCustomApps() {
        return new ArrayList<>(customApps);
    }

    /**
     * 检查包名是否已存在（仅检查自定义APP）
     */
    public boolean isPackageNameExists(String packageName) {
        for (Const.CustomApp app : customApps) {
            if (app.getPackageName().equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 删除自定义APP
     */
    public boolean removeCustomApp(String packageName) {
        for (int i = 0; i < customApps.size(); i++) {
            if (customApps.get(i).getPackageName().equals(packageName)) {
                customApps.remove(i);
                saveCustomApps();
                Log.d("CustomAppManager", "Removed custom app: " + packageName);
                return true;
            }
        }
        return false;
    }

    /**
     * 从SharedPreferences加载自定义APP
     */
    private void loadCustomApps() {
        if (sharedPreferences == null) {
            // 只读模式，使用空列表
            customApps = new ArrayList<>();
            return;
        }
        
        String json = sharedPreferences.getString(KEY_CUSTOM_APPS, "[]");
        try {
            Type type = new TypeToken<List<Const.CustomApp>>(){}.getType();
            customApps = gson.fromJson(json, type);
            if (customApps == null) {
                customApps = new ArrayList<>();
            }
        } catch (Exception e) {
            Log.e("CustomAppManager", "Error loading custom apps", e);
            customApps = new ArrayList<>();
        }
    }

    /**
     * 保存自定义APP到SharedPreferences
     */
    private void saveCustomApps() {
        if (sharedPreferences == null) {
            // 只读模式，不保存
            Log.w("CustomAppManager", "Cannot save in read-only mode");
            return;
        }
        
        try {
            String json = gson.toJson(customApps);
            sharedPreferences.edit().putString(KEY_CUSTOM_APPS, json).apply();
        } catch (Exception e) {
            Log.e("CustomAppManager", "Error saving custom apps", e);
        }
    }
} 