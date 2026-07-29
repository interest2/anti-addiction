package com.book.mask.personalize;

import android.content.Context;

import com.book.mask.config.CustomApp;
import com.book.mask.config.CustomAppManager;
import com.book.mask.config.Share;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tencent.mmkv.MMKV;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 备份导出：把用户可自定义的配置（首页、个性化、更多）汇总为 JSON 文本。
 * <p>
 * 只导出用户主动配置的项，刻意排除运行态数据（休闲时刻已用次数 / 悬浮窗关闭记录 /
 * 各 APP 的解禁计时等），避免把临时状态一并带走。各 APP 的解禁间隔档位属用户配置，纳入备份。
 */
public class BackupManager {

    public static final int BACKUP_VERSION = 1;

    // 与 CustomAppManager 中的存储标识/键保持一致（引用其常量，避免魔法值副本）
    private static final String CUSTOM_APPS_STORAGE_ID = CustomAppManager.PREF_NAME;
    private static final String KEY_CUSTOM_APPS_LIST = CustomAppManager.KEY_CUSTOM_APPS;
    private static final String KEY_PREDEFINED_MODIFICATIONS =
            CustomAppManager.KEY_DEFAULT_APP_MODIFY;

    // 个性化：字符串型配置键（均引用各 manager 的原始常量定义）
    private static final String[] PERSONALIZE_STRING_KEYS = {
            AppSettingsManager.KEY_MOTIVATION_TAG,             // 目标
            AppSettingsManager.KEY_TARGET_COMPLETION_DATE,     // 目标日期
            AppSettingsManager.KEY_FLOATING_STRICT_REMINDER,   // 悬浮窗额外文字内容
            ChallengeSettingsManager.KEY_MATH_QUESTION_TYPE,   // 题目类型
            ChallengeSettingsManager.KEY_MATH_DIFFICULTY_MODE, // 算术题难度模式（default / custom）
    };

    // 个性化：整型配置键
    private static final String[] PERSONALIZE_INT_KEYS = {
            AppSettingsManager.KEY_FLOATING_STRICT_REMINDER_FONT_SIZE,            // 悬浮窗额外文字字号
            ChallengeSettingsManager.KEY_MATH_ADDITION_DIGITS,                    // 算术题难度：加法位数
            ChallengeSettingsManager.KEY_MATH_SUBTRACTION_DIGITS,                 // 算术题难度：减法位数
            ChallengeSettingsManager.KEY_MATH_MULTIPLICATION_MULTIPLIER_DIGITS,   // 算术题难度：乘数位数
            ChallengeSettingsManager.KEY_MATH_MULTIPLICATION_MULTIPLICAND_DIGITS, // 算术题难度：被乘数位数
            ChallengeSettingsManager.KEY_ENGLISH_READING_LENGTH,                  // 英文阅读题长度
            LeisureTimeManager.KEY_LEISURE_DURATION_MINUTES,                      // 休闲时刻·宽松模式：时长
            LeisureTimeManager.KEY_LEISURE_DAILY_COUNT,                           // 休闲时刻·宽松模式：次数
            LeisureTimeManager.KEY_STRICT_LEISURE_DURATION_MINUTES,               // 休闲时刻·严格模式：时长
            LeisureTimeManager.KEY_STRICT_LEISURE_DAILY_COUNT,                    // 休闲时刻·严格模式：次数
    };

    // 更多：悬浮窗默认大小（全局）
    private static final String[] MORE_INT_KEYS = {
            AppSettingsManager.KEY_FLOATING_TOP_OFFSET,
            AppSettingsManager.KEY_FLOATING_BOTTOM_OFFSET,
    };

    // 首页各卡片：每 APP 独立的键前缀
    private static final String PREFIX_HINT_SOURCE = AppSettingsManager.KEY_APP_HINT_SOURCE;
    private static final String PREFIX_HINT_CUSTOM = AppSettingsManager.KEY_APP_HINT_CUSTOM;
    private static final String PREFIX_FLOATING_TOP = AppSettingsManager.KEY_APP_FLOATING_TOP_OFFSET;
    private static final String PREFIX_FLOATING_BOTTOM =
            AppSettingsManager.KEY_APP_FLOATING_BOTTOM_OFFSET;
    private static final String PREFIX_MONITORING = RelaxManager.KEY_APP_MONITORING_ENABLED;
    private static final String PREFIX_SHOW_INTERVAL = RelaxManager.KEY_SHOW_INTERVAL;

    private final MMKV settings;
    private final MMKV customApps;

    public BackupManager(Context context) {
        this.settings = SettingsStorage.open();
        this.customApps = MMKV.mmkvWithID(CUSTOM_APPS_STORAGE_ID);
    }

    /**
     * 汇总所有可备份配置并序列化为带缩进的 JSON。
     */
    public String exportToJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", BACKUP_VERSION);
        root.put("exportTime", System.currentTimeMillis());
        root.put("home", buildHome());
        root.put("personalize", buildPersonalize());
        root.put("more", buildMore());
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    private Map<String, Object> buildHome() {
        Map<String, Object> home = new LinkedHashMap<>();
        // 手动添加的 APP、以及预定义 APP 的关键词等修改，原样保留其 JSON 串
        home.put("customAppsList", customApps.getString(KEY_CUSTOM_APPS_LIST, "[]"));
        home.put("predefinedModifications",
                customApps.getString(KEY_PREDEFINED_MODIFICATIONS, "[]"));
        // 各卡片的警示文字来源、悬浮窗大小
        home.put("appSettings", buildPerAppSettings());
        return home;
    }

    /**
     * 扫描 app_settings 中带每-APP 前缀的键，按包名归拢成结构化数据。
     * 只收录用户实际设置过的键，未设置的走各自默认值、不占备份。
     */
    private Map<String, Object> buildPerAppSettings() {
        Map<String, Object> byPackage = new LinkedHashMap<>();
        String[] allKeys = settings.allKeys();
        if (allKeys == null) {
            return byPackage;
        }
        for (String key : allKeys) {
            if (key.startsWith(PREFIX_HINT_SOURCE)) {
                perApp(byPackage, key.substring(PREFIX_HINT_SOURCE.length()))
                        .put("hintSource", settings.getString(key, null));
            } else if (key.startsWith(PREFIX_HINT_CUSTOM)) {
                perApp(byPackage, key.substring(PREFIX_HINT_CUSTOM.length()))
                        .put("hintCustomText", settings.getString(key, null));
            } else if (key.startsWith(PREFIX_FLOATING_TOP)) {
                perApp(byPackage, key.substring(PREFIX_FLOATING_TOP.length()))
                        .put("floatingTopOffset", settings.getInt(key, 0));
            } else if (key.startsWith(PREFIX_FLOATING_BOTTOM)) {
                perApp(byPackage, key.substring(PREFIX_FLOATING_BOTTOM.length()))
                        .put("floatingBottomOffset", settings.getInt(key, 0));
            } else if (key.startsWith(PREFIX_MONITORING)) {
                perApp(byPackage, key.substring(PREFIX_MONITORING.length()))
                        .put("monitoringEnabled", settings.getBoolean(key, false));
            } else if (key.startsWith(PREFIX_SHOW_INTERVAL)) {
                perApp(byPackage, key.substring(PREFIX_SHOW_INTERVAL.length()))
                        .put("showInterval", settings.getInt(key, 0));
            }
        }
        // 剔除孤儿包名：删除自定义 APP 但残留的每-APP 设置，不应污染备份。
        // 只保留当前真实存在的 APP（预定义 + 当前自定义列表）。
        CustomAppManager appManager = CustomAppManager.getInstance();
        byPackage.keySet().removeIf(pkg -> !appManager.isPackageNameExists(pkg));
        return byPackage;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> perApp(Map<String, Object> byPackage, String pkg) {
        Map<String, Object> map = (Map<String, Object>) byPackage.get(pkg);
        if (map == null) {
            map = new LinkedHashMap<>();
            byPackage.put(pkg, map);
        }
        return map;
    }

    private Map<String, Object> buildPersonalize() {
        Map<String, Object> personalize = new LinkedHashMap<>();
        putStringsIfSet(personalize, PERSONALIZE_STRING_KEYS);
        putIntsIfSet(personalize, PERSONALIZE_INT_KEYS);
        return personalize;
    }

    private Map<String, Object> buildMore() {
        Map<String, Object> more = new LinkedHashMap<>();
        putIntsIfSet(more, MORE_INT_KEYS);
        return more;
    }

    private void putStringsIfSet(Map<String, Object> target, String[] keys) {
        for (String key : keys) {
            if (settings.contains(key)) {
                target.put(key, settings.getString(key, null));
            }
        }
    }

    private void putIntsIfSet(Map<String, Object> target, String[] keys) {
        for (String key : keys) {
            if (settings.contains(key)) {
                target.put(key, settings.getInt(key, 0));
            }
        }
    }

    // ===== 导入 =====

    /**
     * 导入统计：成功应用的条目数与被跳过（出错或重复）的条目数。
     */
    public static class ImportResult {
        public int imported;
        public int skipped;
    }

    /**
     * 从备份 JSON 逐条恢复配置。任一条目解析或写入失败都只跳过该条、继续其余，
     * 不会因个别脏数据中断整个导入。
     *
     * @throws IllegalArgumentException 整个 JSON 无法解析时抛出
     */
    public ImportResult importFromJson(String json) {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalArgumentException("备份文件格式不正确");
        }

        ImportResult result = new ImportResult();

        JsonObject home = optObject(root, "home");
        if (home != null) {
            importCustomApps(home, result);
            importPredefinedModifications(home, result);
            importPerAppSettings(home, result);
        }

        JsonObject personalize = optObject(root, "personalize");
        if (personalize != null) {
            importStrings(personalize, PERSONALIZE_STRING_KEYS, result);
            importInts(personalize, PERSONALIZE_INT_KEYS, result);
        }

        JsonObject more = optObject(root, "more");
        if (more != null) {
            importInts(more, MORE_INT_KEYS, result);
        }

        // 手动导入备份说明用户已熟悉个性化设置，不再显示悬浮窗设置途径提示
        settings.putBoolean(AppSettingsManager.KEY_FLOATING_STRICT_REMINDER_SETTINGS_CLICKED, true)
                .commit();
        // 目标/日期等可能已变化，标记让首页倒计时与激励文案重新计算
        Share.MOTIVATE_CHANGE = true;
        return result;
    }

    private void importCustomApps(JsonObject home, ImportResult result) {
        JsonArray arr = optArrayFromString(home, "customAppsList");
        if (arr == null) {
            return;
        }
        CustomAppManager manager = CustomAppManager.getInstance();
        for (JsonElement el : arr) {
            try {
                JsonObject o = el.getAsJsonObject();
                boolean ok = manager.addCustomApp(
                        o.get("appName").getAsString(),
                        o.get("packageName").getAsString(),
                        optString(o, "targetWord", ""),
                        optInt(o, "relaxedLimitCount", 1));
                if (ok) {
                    result.imported++;
                } else {
                    // 包名非法或已存在，跳过
                    result.skipped++;
                }
            } catch (Exception e) {
                result.skipped++;
            }
        }
    }

    private void importPredefinedModifications(JsonObject home, ImportResult result) {
        JsonArray arr = optArrayFromString(home, "predefinedModifications");
        if (arr == null) {
            return;
        }
        CustomAppManager manager = CustomAppManager.getInstance();
        for (JsonElement el : arr) {
            try {
                JsonObject o = el.getAsJsonObject();
                CustomApp app = new CustomApp(
                        o.get("appName").getAsString(),
                        o.get("packageName").getAsString(),
                        optString(o, "targetWord", ""),
                        optInt(o, "relaxedLimitCount", 1));
                manager.updatePredefinedApp(app);
                result.imported++;
            } catch (Exception e) {
                result.skipped++;
            }
        }
    }

    private void importPerAppSettings(JsonObject home, ImportResult result) {
        JsonObject appSettings = optObject(home, "appSettings");
        if (appSettings == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : appSettings.entrySet()) {
            String pkg = entry.getKey();
            try {
                JsonObject o = entry.getValue().getAsJsonObject();
                if (has(o, "hintSource")) {
                    settings.putString(PREFIX_HINT_SOURCE + pkg, o.get("hintSource").getAsString());
                }
                if (has(o, "hintCustomText")) {
                    settings.putString(PREFIX_HINT_CUSTOM + pkg, o.get("hintCustomText").getAsString());
                }
                if (has(o, "floatingTopOffset")) {
                    settings.putInt(PREFIX_FLOATING_TOP + pkg, o.get("floatingTopOffset").getAsInt());
                }
                if (has(o, "floatingBottomOffset")) {
                    settings.putInt(PREFIX_FLOATING_BOTTOM + pkg,
                            o.get("floatingBottomOffset").getAsInt());
                }
                if (has(o, "monitoringEnabled")) {
                    settings.putBoolean(PREFIX_MONITORING + pkg,
                            o.get("monitoringEnabled").getAsBoolean());
                }
                if (has(o, "showInterval")) {
                    settings.putInt(PREFIX_SHOW_INTERVAL + pkg, o.get("showInterval").getAsInt());
                }
                settings.commit();
                result.imported++;
            } catch (Exception e) {
                result.skipped++;
            }
        }
    }

    private void importStrings(JsonObject obj, String[] keys, ImportResult result) {
        for (String key : keys) {
            if (!has(obj, key)) {
                continue;
            }
            try {
                settings.putString(key, obj.get(key).getAsString()).commit();
                result.imported++;
            } catch (Exception e) {
                result.skipped++;
            }
        }
    }

    private void importInts(JsonObject obj, String[] keys, ImportResult result) {
        for (String key : keys) {
            if (!has(obj, key)) {
                continue;
            }
            try {
                settings.putInt(key, obj.get(key).getAsInt()).commit();
                result.imported++;
            } catch (Exception e) {
                result.skipped++;
            }
        }
    }

    private static boolean has(JsonObject obj, String name) {
        return obj.has(name) && !obj.get(name).isJsonNull();
    }

    private static String optString(JsonObject obj, String name, String fallback) {
        return has(obj, name) ? obj.get(name).getAsString() : fallback;
    }

    private static int optInt(JsonObject obj, String name, int fallback) {
        return has(obj, name) ? obj.get(name).getAsInt() : fallback;
    }

    private static JsonObject optObject(JsonObject parent, String name) {
        try {
            if (parent.has(name) && parent.get(name).isJsonObject()) {
                return parent.getAsJsonObject(name);
            }
        } catch (Exception ignored) {
            // 结构不符，按缺失处理
        }
        return null;
    }

    private static JsonArray optArrayFromString(JsonObject parent, String name) {
        try {
            if (!has(parent, name)) {
                return null;
            }
            JsonElement el = JsonParser.parseString(parent.get(name).getAsString());
            return el.isJsonArray() ? el.getAsJsonArray() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
