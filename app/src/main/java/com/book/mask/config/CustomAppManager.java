package com.book.mask.config;

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
    private static final String KEY_DEFAULT_APP_MODIFY = "predefined_apps_modifications";
    public static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static CustomAppManager instance;
    private final Context context;
    private final SharedPreferences sharedPreferences;
    private final Gson gson;
    private List<CustomApp> customApps;
    
    // 预定义的应用列表
    private static final List<CustomApp> PREDEFINED_APPS = new ArrayList<>();
    // 预定义APP的修改记录
    private List<CustomApp> predefinedAppModifications;
    
    static {
        // 初始化预定义应用
        PREDEFINED_APPS.add(new CustomApp("小红书", "com.xingin.xhs", "发现", 3));
        PREDEFINED_APPS.add(new CustomApp("知乎", "com.zhihu.android", "热榜", 2));
        PREDEFINED_APPS.add(new CustomApp("抖音", "com.ss.android.ugc.aweme", "推荐 精选 热点", 2));
        PREDEFINED_APPS.add(new CustomApp("哔哩哔哩", "tv.danmaku.bili", "推荐", 1));
        PREDEFINED_APPS.add(new CustomApp("支付宝", "com.eg.android.AlipayGphone", "股票 行情 持有", 3));
        PREDEFINED_APPS.add(new CustomApp("微信", WECHAT_PACKAGE, "关键词无效", 3));
    }

    private CustomAppManager(Context context) {
        if (context != null) {
            this.context = context.getApplicationContext();
            this.sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            this.gson = new Gson();
            loadCustomApps();
            loadPredefinedAppModifications();
        } else {
            // 只读模式，用于静态上下文
            this.context = null;
            this.sharedPreferences = null;
            this.gson = new Gson();
            this.customApps = new ArrayList<>();
            this.predefinedAppModifications = new ArrayList<>();
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
        if (isPackageNameExists(packageName)) {
            Log.w("CustomAppManager", "Package name already exists: " + packageName);
            return false;
        }

        // 创建新的自定义APP
        CustomApp newApp = new CustomApp(appName, packageName, targetWord, casualLimitCount);
        customApps.add(newApp);
        
        // 保存到SharedPreferences
        saveCustomApps();
        
        Log.d("CustomAppManager", "Added custom app: " + appName + " (" + packageName + ")");
        return true;
    }
    
    /**
     * 获取所有APP（包括预定义和自定义）
     */
    public List<CustomApp> getAllApps() {
        List<CustomApp> allApps = new ArrayList<>();
        // 添加预定义应用（优先使用修改后的版本）
        for (CustomApp predefinedApp : PREDEFINED_APPS) {
            CustomApp modifiedApp = getModifiedPredefinedApp(predefinedApp.getPackageName());
            allApps.add(modifiedApp != null ? modifiedApp : predefinedApp);
        }
        // 添加自定义应用
        allApps.addAll(customApps);
        return allApps;
    }
    
    /**
     * 根据包名获取APP
     */
    public CustomApp getAppByPackageName(String packageName) {
        // 先检查预定义应用的修改版本
        CustomApp modifiedApp = getModifiedPredefinedApp(packageName);
        if (modifiedApp != null) {
            return modifiedApp;
        }
        
        // 再检查原始预定义应用
        for (CustomApp app : PREDEFINED_APPS) {
            if (app.getPackageName().equals(packageName)) {
                return app;
            }
        }
        
        // 最后检查自定义应用
        for (CustomApp app : customApps) {
            if (app.getPackageName().equals(packageName)) {
                return app;
            }
        }
        return null;
    }

    /**
     * 检查包名是否已存在（包括预定义和自定义APP）
     */
    public boolean isPackageNameExists(String packageName) {
        // 检查预定义应用
        for (CustomApp app : PREDEFINED_APPS) {
            if (app.getPackageName().equals(packageName)) {
                return true;
            }
        }
        // 检查自定义应用
        for (CustomApp app : customApps) {
            if (app.getPackageName().equals(packageName)) {
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
            Type type = new TypeToken<List<CustomApp>>(){}.getType();
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
    
    /**
     * 保存自定义APP的更改（公共方法）
     */
    public void saveCustomAppsChanges() {
        saveCustomApps();
    }
    
    /**
     * 更新预定义APP（保存修改）
     */
    public void updatePredefinedApp(CustomApp modifiedApp) {
        // 检查是否是预定义APP
        boolean isPredefined = false;
        for (CustomApp predefinedApp : PREDEFINED_APPS) {
            if (predefinedApp.getPackageName().equals(modifiedApp.getPackageName())) {
                isPredefined = true;
                break;
            }
        }
        
        if (isPredefined) {
            // 更新或添加修改记录
            boolean found = false;
            for (int i = 0; i < predefinedAppModifications.size(); i++) {
                if (predefinedAppModifications.get(i).getPackageName().equals(modifiedApp.getPackageName())) {
                    predefinedAppModifications.set(i, modifiedApp);
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                predefinedAppModifications.add(modifiedApp);
            }
            
            // 保存修改
            savePredefinedAppModifications();
        }
    }
    
    /**
     * 获取修改后的预定义APP
     */
    private CustomApp getModifiedPredefinedApp(String packageName) {
        for (CustomApp modifiedApp : predefinedAppModifications) {
            if (modifiedApp.getPackageName().equals(packageName)) {
                return modifiedApp;
            }
        }
        return null;
    }
    
    /**
     * 加载预定义APP的修改记录
     */
    private void loadPredefinedAppModifications() {
        if (sharedPreferences == null) {
            predefinedAppModifications = new ArrayList<>();
            return;
        }
        
        String json = sharedPreferences.getString(KEY_DEFAULT_APP_MODIFY, "[]");
        try {
            Type type = new TypeToken<List<CustomApp>>(){}.getType();
            predefinedAppModifications = gson.fromJson(json, type);
            if (predefinedAppModifications == null) {
                predefinedAppModifications = new ArrayList<>();
            }
        } catch (Exception e) {
            Log.e("CustomAppManager", "Error loading predefined app modifications", e);
            predefinedAppModifications = new ArrayList<>();
        }
    }
    
    /**
     * 保存预定义APP的修改记录
     */
    private void savePredefinedAppModifications() {
        if (sharedPreferences == null) {
            Log.w("CustomAppManager", "Cannot save predefined app modifications in read-only mode");
            return;
        }
        
        try {
            String json = gson.toJson(predefinedAppModifications);
            sharedPreferences.edit().putString(KEY_DEFAULT_APP_MODIFY, json).apply();
        } catch (Exception e) {
            Log.e("CustomAppManager", "Error saving predefined app modifications", e);
        }
    }
} 