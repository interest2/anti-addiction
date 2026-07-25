package com.book.mask.config;

import android.content.Context;
import android.util.Log;

import com.book.mask.personalize.RelaxManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tencent.mmkv.MMKV;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class CustomAppManager {
    private static final String TAG = "CustomAppManager";

    private static final String PREF_NAME = "custom_apps";
    private static final String KEY_CUSTOM_APPS = "custom_apps_list";
    private static final String KEY_DEFAULT_APP_MODIFY = "predefined_apps_modifications";

    /*预置支持 APP 的包名常量*/
    public static final String XHS_PACKAGE = "com.xingin.xhs";
    public static final String WECHAT_PACKAGE = "com.tencent.mm";
    public static final String ZHIHU_PACKAGE = "com.zhihu.android";
    public static final String DOUYIN_PACKAGE = "com.ss.android.ugc.aweme";
    public static final String BILI_PACKAGE = "tv.danmaku.bili";
    public static final String ALIPAY_PACKAGE = "com.eg.android.AlipayGphone";
    private static CustomAppManager instance;
    private final Context context;
    private MMKV mmkv;
    private final Gson gson;
    private List<CustomApp> customApps;
    
    // 预定义的应用列表
    private static final List<CustomApp> PREDEFINED_APPS = new ArrayList<>();
    // 预定义APP的修改记录
    private List<CustomApp> predefinedAppModifications;
    
    static {
        // 初始化预定义应用
        PREDEFINED_APPS.add(new CustomApp("小红书", XHS_PACKAGE, "发现", 3));
        PREDEFINED_APPS.add(new CustomApp("知乎", ZHIHU_PACKAGE, "热榜", 2));
        PREDEFINED_APPS.add(new CustomApp("抖音", DOUYIN_PACKAGE, "推荐 精选 热点", 2));
        PREDEFINED_APPS.add(new CustomApp("哔哩哔哩", BILI_PACKAGE, "推荐", 3));
        PREDEFINED_APPS.add(new CustomApp("支付宝", ALIPAY_PACKAGE, "行情 持有", 1));
        PREDEFINED_APPS.add(new CustomApp("微信", WECHAT_PACKAGE, "微信关键词不起作用", 3));
    }

    private CustomAppManager(Context context) {
        if (context != null) {
            this.context = context.getApplicationContext();
            this.mmkv = MMKV.mmkvWithID(PREF_NAME);
            this.gson = new Gson();
            loadCustomApps();
            loadPredefinedAppModifications();
        } else {
            // 只读模式，用于静态上下文
            this.context = null;
            this.mmkv = null;
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
    public boolean addCustomApp(String appName, String packageName, String targetWord, int relaxedLimitCount) {
        // 入口清洗：去除零宽字符等不可见字符，并校验包名合法性
        String cleanedPkg = sanitizePackageName(packageName);
        if (!isValidPackageName(cleanedPkg)) {
            Log.w(TAG, "Reject invalid package name: original=[" + packageName
                    + "] cleaned=[" + cleanedPkg + "]");
            return false;
        }
        if (!cleanedPkg.equals(packageName)) {
            Log.w(TAG, "Sanitized package name on add: [" + packageName + "] -> [" + cleanedPkg + "]");
        }
        packageName = cleanedPkg;

        // 检查包名是否已存在
        if (isPackageNameExists(packageName)) {
            Log.w(TAG, "Package name already exists: " + packageName);
            return false;
        }

        // 创建新的自定义APP
        CustomApp newApp = new CustomApp(appName, packageName, targetWord, relaxedLimitCount);
        customApps.add(newApp);

        // 保存到MMKV
        saveCustomApps();

        Log.d(TAG, "Added custom app: " + appName + " (" + packageName + ")");
        return true;
    }

    /**
     * 清洗包名：白名单方案，仅保留 [a-zA-Z0-9._]，杜绝零宽 / 控制 / 同形字符
     */
    private static String sanitizePackageName(String pkg) {
        if (pkg == null) return "";
        return pkg.replaceAll("[^a-zA-Z0-9._]", "");
    }

    /**
     * 校验包名是否符合 Android 规则：
     * 至少含一个点，每段以字母开头，仅含字母数字下划线
     */
    private static boolean isValidPackageName(String pkg) {
        return pkg != null && pkg.matches("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$");
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
        Log.d(TAG, "customApps: " + customApps);
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
     * 检测包名对应的支持APP（统一使用CustomApp）
     * @param packageName 包名
     * @param relaxManager 用于检查监测开关状态
     * @return 支持的APP，如果不支持或监测关闭则返回null
     */
    public CustomApp detectSupportedApp(String packageName, RelaxManager relaxManager) {
        // 检查所有APP（包括预定义和自定义）
        try {
            CustomApp app = getAppByPackageName(packageName);
            
            if (app != null) {
                // 检查该APP的监测开关状态
                if (relaxManager != null && !relaxManager.shouldMonitorApp(packageName)) {
                    Log.d("CustomAppManager", "APP " + app.getAppName() + " 监测已关闭，跳过检测");
                    return null;
                }
                return app;
            }
        } catch (Exception e) {
            Log.w("CustomAppManager", "检查APP时出错", e);
        }
        
        return null;
    }
    
    /**
     * 获取当前活跃的APP
     * @return 当前活跃的APP，如果没有则返回null
     */
    public CustomApp getCurrentActiveApp() {
        return Share.currentApp;
    }

    /**
     * 判断是否为用户手动添加的自定义APP（非预定义）
     */
    public boolean isCustomApp(String packageName) {
        if (packageName == null) return false;
        for (CustomApp predefinedApp : PREDEFINED_APPS) {
            if (predefinedApp.getPackageName().equals(packageName)) {
                return false;
            }
        }
        for (CustomApp app : customApps) {
            if (app.getPackageName().equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 删除用户手动添加的自定义APP（预定义APP不可删除）
     */
    public boolean removeCustomApp(String packageName) {
        if (packageName == null) return false;
        boolean removed = false;
        for (int i = customApps.size() - 1; i >= 0; i--) {
            if (packageName.equals(customApps.get(i).getPackageName())) {
                customApps.remove(i);
                removed = true;
            }
        }
        if (removed) {
            saveCustomApps();
            Log.d(TAG, "Removed custom app: " + packageName);
        }
        return removed;
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
     * 从MMKV加载自定义APP
     */
    private void loadCustomApps() {
        if (mmkv == null) {
            // 只读模式，使用空列表
            customApps = new ArrayList<>();
            return;
        }

        String json = mmkv.getString(KEY_CUSTOM_APPS, "[]");
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

        // 存量清洗：修复历史脏数据（含零宽字符等不可见字符的旧记录）
        if (sanitizeAppListInPlace(customApps, "customApps")) {
            saveCustomApps();
        }
    }

    /**
     * 对列表中的 CustomApp 做包名清洗，发现脏数据时原地替换为重建后的对象。
     * @return 是否发生过清洗（true 表示需要回写存储）
     */
    private boolean sanitizeAppListInPlace(List<CustomApp> apps, String tagForLog) {
        boolean dirty = false;
        for (int i = 0; i < apps.size(); i++) {
            CustomApp app = apps.get(i);
            if (app == null) continue;
            String original = app.getPackageName();
            String cleaned = sanitizePackageName(original);
            if (!cleaned.equals(original)) {
                Log.w(TAG, "Cleaning dirty packageName in " + tagForLog
                        + ": [" + original + "] -> [" + cleaned + "]");
                // packageName 是 final，需要重建对象
                CustomApp rebuilt = new CustomApp(
                        app.getAppName(), cleaned, app.getTargetWord(), app.getRelaxedLimitCount());
                apps.set(i, rebuilt);
                dirty = true;
            }
        }
        return dirty;
    }

    /**
     * 保存自定义APP到MMKV
     */
    private void saveCustomApps() {
        if (mmkv == null) {
            // 尝试重新初始化（如果有context的话）
            if (context != null) {
                Log.w("CustomAppManager", "MMKV is null, attempting to reinitialize...");
                try {
                    mmkv = MMKV.mmkvWithID(PREF_NAME);
                    Log.i("CustomAppManager", "Successfully reinitialized MMKV");
                } catch (Exception e) {
                    Log.e("CustomAppManager", "Failed to reinitialize MMKV", e);
                    return;
                }
            } else {
                // 只读模式，不保存
                Log.w("CustomAppManager", "Cannot save in read-only mode - no context available");
                return;
            }
        }
        
        try {
            String json = gson.toJson(customApps);
            mmkv.putString(KEY_CUSTOM_APPS, json).commit();
            
            // 验证保存是否成功 - 从MMKV读取内容
            String savedJson = mmkv.getString(KEY_CUSTOM_APPS, null);
            if (savedJson != null && savedJson.equals(json)) {
                Log.d(TAG, "Custom apps saved successfully. Data verified.");
                Log.d(TAG, "Saved data: " + savedJson);
            } else {
                Log.e(TAG, "Failed to save custom apps - verification failed");
                Log.e(TAG, "Expected: " + json);
                Log.e(TAG, "Actual: " + savedJson);
            }
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
        // 入口清洗：拒绝带不可见字符的脏数据
        String pkg = modifiedApp.getPackageName();
        String cleanedPkg = sanitizePackageName(pkg);
        if (!isValidPackageName(cleanedPkg) || !cleanedPkg.equals(pkg)) {
            Log.w(TAG, "Reject updatePredefinedApp with invalid/dirty package name: original=["
                    + pkg + "] cleaned=[" + cleanedPkg + "]");
            return;
        }

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
        if (mmkv == null) {
            predefinedAppModifications = new ArrayList<>();
            return;
        }

        String json = mmkv.getString(KEY_DEFAULT_APP_MODIFY, "[]");
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

        // 存量清洗：修复历史脏数据
        if (sanitizeAppListInPlace(predefinedAppModifications, "predefinedAppModifications")) {
            savePredefinedAppModifications();
        }
    }
    
    /**
     * 保存预定义APP的修改记录
     */
    private void savePredefinedAppModifications() {
        if (mmkv == null) {
            Log.w("CustomAppManager", "Cannot save predefined app modifications in read-only mode");
            return;
        }
        
        try {
            String json = gson.toJson(predefinedAppModifications);
            mmkv.putString(KEY_DEFAULT_APP_MODIFY, json).commit();
        } catch (Exception e) {
            Log.e("CustomAppManager", "Error saving predefined app modifications", e);
        }
    }
}