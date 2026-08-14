package com.book.mask.personalize;

import android.content.Context;
import android.util.Log;

import com.book.mask.config.CustomApp;
import com.book.mask.config.CustomAppManager;
import com.book.mask.config.Share;
import com.book.mask.constant.Const;
import com.book.mask.reminder.config.ProviderSecretStore;
import com.book.mask.reminder.config.ReminderProviderConfig;
import com.book.mask.reminder.config.ReminderProviderConfigStore;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tencent.mmkv.MMKV;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 备份导出：把用户可自定义的配置（首页、个性化、更多）汇总为 JSON 文本。
 * <p>
 * 只导出用户主动配置的项，刻意排除运行态数据（休闲时刻已用次数 / 悬浮窗关闭记录 /
 * 各 APP 的解禁计时等），避免把临时状态一并带走。各 APP 的解禁间隔档位属用户配置，纳入备份。
 * 大模型 Provider 配置（endpoint/model/名称/家选择）纳入备份，但密钥（ProviderSecretStore）
 * 默认排除，导入后需用户重填密钥再启用。
 * <p>
 * 如需把密钥一并带走（Provider API Key / SOE 凭据 / 豆包 TTS API Key），调用
 * {@link #exportToJson(boolean)} 并传 {@code true}。导出入口按 {@link #hasSensitiveData()}
 * 决定按钮形态：无密钥时仅提供「导出」；有密钥时提供「导出(含密钥) / 导出(不含密钥)」
 * 两个按钮，并在界面提示导出风险，由用户自行决定。
 */
public class BackupManager {

    private static final String TAG = "BackupManager";

    public static final int BACKUP_VERSION = 1;

    // 与 CustomAppManager 中的存储标识/键保持一致（引用其常量，避免魔法值副本）
    private static final String CUSTOM_APPS_STORAGE_ID = CustomAppManager.PREF_NAME;
    private static final String KEY_CUSTOM_APPS_LIST = CustomAppManager.KEY_CUSTOM_APPS;
    private static final String KEY_PREDEFINED_MODIFICATIONS =
            CustomAppManager.KEY_DEFAULT_APP_MODIFY;

    // 个性化：字符串型配置键（均引用各 manager 的原始常量定义）
    private static final String[] PERSONALIZE_STRING_KEYS = {
            AppSettingsManager.KEY_MOTIVATION_TAG,             // 当前目标
            AppSettingsManager.KEY_CUSTOM_MOTIVATION_TAGS,     // 自定义目标列表
            AppSettingsManager.KEY_TARGET_COMPLETION_DATE,     // 目标日期
            AppSettingsManager.KEY_REMINDER_STYLE,             // 大模型提醒风格
            AppSettingsManager.KEY_REMINDER_CUSTOM_STYLE,      // 自定义提醒风格
            AppSettingsManager.KEY_FLOATING_STRICT_REMINDER,   // 座右铭内容
            ChallengeSettingsManager.KEY_MATH_QUESTION_TYPE,   // 题目类型
            ChallengeSettingsManager.KEY_MATH_DIFFICULTY_MODE, // 算术题难度模式（default / custom）
            ChallengeSettingsManager.KEY_MATH_MULTIPLICATION_MULTIPLIER_TIER,
            ChallengeSettingsManager.KEY_MATH_MULTIPLICATION_MULTIPLICAND_TIER,
            LeisureTimeManager.KEY_LEISURE_RELAXED_TIER,                     // 休闲时刻·宽松模式：当前档位
    };

    // 个性化：整型配置键
    private static final String[] PERSONALIZE_INT_KEYS = {
            AppSettingsManager.KEY_FLOATING_STRICT_REMINDER_FONT_SIZE,            // 座右铭字号
            AppSettingsManager.KEY_FLOATING_STRICT_REMINDER_FONT_COLOR,           // 座右铭字体颜色
            ChallengeSettingsManager.KEY_MATH_ADDITION_DIGITS,                    // 算术题难度：加法位数
            ChallengeSettingsManager.KEY_MATH_SUBTRACTION_DIGITS,                 // 算术题难度：减法位数
            ChallengeSettingsManager.KEY_MATH_MULTIPLICATION_MULTIPLIER_DIGITS,   // 算术题难度：乘数位数
            ChallengeSettingsManager.KEY_MATH_MULTIPLICATION_MULTIPLICAND_DIGITS, // 算术题难度：被乘数位数
            ChallengeSettingsManager.KEY_REASONING_DIFFICULTY_LEVEL,              // 推理题难度：默认 / 0档 / 1档 / 2档
            ChallengeSettingsManager.KEY_CHALLENGE_TIMER_MODE,                    // 答题计时显示模式
            ChallengeSettingsManager.KEY_ENGLISH_READING_LENGTH,                  // 英文阅读题长度
            ChallengeSettingsManager.KEY_RETELLING_STORY_LENGTH,                  // 复述题：故事字数
            ChallengeSettingsManager.KEY_RETELLING_DISPLAY_SECONDS,               // 复述题：展示秒数
            ChallengeSettingsManager.KEY_RETELLING_PASS_SCORE,                    // 复述题：通过分数
            LeisureTimeManager.KEY_LEISURE_RELAXED_LARGE_MINUTES,                 // 休闲时刻·宽松模式：大档时长
            LeisureTimeManager.KEY_LEISURE_RELAXED_SHORT_MINUTES,                 // 休闲时刻·宽松模式：小档时长
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

    private final Context context;
    private final MMKV settings;
    private final MMKV customApps;
    private final ReminderProviderConfigStore providerStore;
    private final AppSettingsManager appSettingsManager;

    public BackupManager(Context context) {
        this.context = context.getApplicationContext();
        this.settings = SettingsStorage.open();
        this.customApps = MMKV.mmkvWithID(CUSTOM_APPS_STORAGE_ID);
        this.providerStore = new ReminderProviderConfigStore();
        this.appSettingsManager = new AppSettingsManager(context);
    }

    /**
     * 汇总所有可备份配置并序列化为带缩进的 JSON，不含任何密钥。
     */
    public String exportToJson() {
        return exportToJson(false);
    }

    /**
     * 汇总所有可备份配置并序列化为带缩进的 JSON。
     *
     * @param includeSecrets 是否携带密钥（Provider API Key / SOE 凭据 / 豆包 TTS API Key）。
     *                       为 true 时生成额外的 {@code secrets} 区块，仅应在用户确认后调用。
     */
    public String exportToJson(boolean includeSecrets) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", BACKUP_VERSION);
        root.put("exportTime", System.currentTimeMillis());
        root.put("home", buildHome());
        root.put("personalize", buildPersonalize());
        root.put("more", buildMore());
        Map<String, Object> reminderProvider = buildReminderProvider();
        if (reminderProvider != null) {
            root.put("reminderProvider", reminderProvider);
        }
        Map<String, Object> doubaoTts = buildDoubaoTtsConfig();
        if (doubaoTts != null) {
            root.put("doubaoTts", doubaoTts);
        }
        if (includeSecrets) {
            Map<String, Object> secrets = buildSecrets();
            if (secrets != null) {
                root.put("secrets", secrets);
            }
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    /**
     * 大模型 Provider 配置：导出 Provider 定义（endpoint/model/名称等，不含密钥）与
     * 各 owner 下用户自填的自定义模型名。两者皆无时返回 null，不占备份。
     */
    private Map<String, Object> buildReminderProvider() {
        String profiles = providerStore.exportProfilesJson();
        boolean hasProfiles = profiles != null && !"[]".equals(profiles);
        Map<String, List<String>> customModels = providerStore.exportCustomModels();
        boolean hasModels = customModels != null && !customModels.isEmpty();
        if (!hasProfiles && !hasModels) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        if (hasProfiles) {
            map.put("profiles", profiles);
        }
        if (hasModels) {
            map.put("customModels", customModels);
        }
        return map;
    }

    // ===== 密钥（敏感数据）检测与导出 =====

    /**
     * 是否存在用户配置的密钥：自定义 Provider 的 API Key、SOE 凭据、豆包 TTS API Key。
     * 导出前据此判断是否需要向用户确认是否携带密钥。
     */
    public boolean hasSensitiveData() {
        return hasProviderSecret() || hasSoeSecret() || hasDoubaoSecret();
    }

    private boolean hasProviderSecret() {
        ProviderSecretStore secretStore = new ProviderSecretStore(context);
        for (ReminderProviderConfig profile : providerStore.getProfiles()) {
            try {
                if (secretStore.hasApiKey(profile.getProfileId())) {
                    return true;
                }
            } catch (Exception e) {
                Log.w(TAG, "检查 Provider 密钥失败: " + profile.getProfileId(), e);
            }
        }
        return false;
    }

    private boolean hasSoeSecret() {
        SoeConfigManager soe = new SoeConfigManager(context);
        return !soe.getAppId().isEmpty()
                || !soe.getSecretId().isEmpty()
                || !soe.getSecretKey().isEmpty();
    }

    private boolean hasDoubaoSecret() {
        DoubaoTtsConfigManager doubao = new DoubaoTtsConfigManager(context);
        return !doubao.getApiKey().isEmpty();
    }

    /**
     * 汇总全部密钥区块；无任何密钥时返回 null。
     * 仅应在用户确认携带密钥导出后调用，产物里的密钥为明文，落盘即存在泄露面。
     */
    private Map<String, Object> buildSecrets() {
        Map<String, Object> secrets = new LinkedHashMap<>();
        Map<String, Object> provider = buildProviderSecrets();
        if (provider != null) {
            secrets.put("provider", provider);
        }
        Map<String, Object> soe = buildSoeSecrets();
        if (soe != null) {
            secrets.put("soe", soe);
        }
        Map<String, Object> doubao = buildDoubaoSecrets();
        if (doubao != null) {
            secrets.put("doubao", doubao);
        }
        return secrets.isEmpty() ? null : secrets;
    }

    /** 每个自定义 Provider 的 API Key，按 profileId 归组；无密钥的 Provider 不出现。 */
    private Map<String, Object> buildProviderSecrets() {
        List<ReminderProviderConfig> profiles = providerStore.getProfiles();
        if (profiles.isEmpty()) {
            return null;
        }
        ProviderSecretStore secretStore = new ProviderSecretStore(context);
        Map<String, Object> map = new LinkedHashMap<>();
        for (ReminderProviderConfig profile : profiles) {
            String profileId = profile.getProfileId();
            try {
                if (secretStore.hasApiKey(profileId)) {
                    String apiKey = secretStore.getApiKey(profileId);
                    if (apiKey != null && !apiKey.isEmpty()) {
                        map.put(profileId, apiKey);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "读取 Provider 密钥失败，已跳过: " + profileId, e);
            }
        }
        return map.isEmpty() ? null : map;
    }

    /** SOE 配置块：AppID / SecretId / SecretKey 任一存在即整体导出（含苛刻指数），保证换机可完整恢复。 */
    private Map<String, Object> buildSoeSecrets() {
        SoeConfigManager soe = new SoeConfigManager(context);
        if (soe.getAppId().isEmpty()
                && soe.getSecretId().isEmpty()
                && soe.getSecretKey().isEmpty()) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("appId", soe.getAppId());
        map.put("secretId", soe.getSecretId());
        map.put("secretKey", soe.getSecretKey());
        map.put("scoreCoeff", soe.getScoreCoeff());
        return map;
    }

    /** 豆包 TTS 密钥区块：仅 API Key。资源 ID / 发音人属非密钥配置，走 doubaoTts 区块始终导出。 */
    private Map<String, Object> buildDoubaoSecrets() {
        DoubaoTtsConfigManager doubao = new DoubaoTtsConfigManager(context);
        if (doubao.getApiKey().isEmpty()) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("apiKey", doubao.getApiKey());
        return map;
    }

    /** 豆包 TTS 非密钥配置（资源 ID / 发音人），始终纳入备份；API Key 属于密钥，不在此列。 */
    private Map<String, Object> buildDoubaoTtsConfig() {
        DoubaoTtsConfigManager doubao = new DoubaoTtsConfigManager(context);
        Map<String, Object> map = new LinkedHashMap<>();
        if (!doubao.getResourceId().isEmpty()) {
            map.put("resourceId", doubao.getResourceId());
        }
        if (!doubao.getSpeaker().isEmpty()) {
            map.put("speaker", doubao.getSpeaker());
        }
        return map.isEmpty() ? null : map;
    }

    private Map<String, Object> buildHome() {
        Map<String, Object> home = new LinkedHashMap<>();
        // 手动添加的 APP、以及预定义 APP 的关键词等修改，原样保留其 JSON 串
        home.put("customAppsList", customApps.getString(KEY_CUSTOM_APPS_LIST, "[]"));
        home.put("predefinedModifications",
                customApps.getString(KEY_PREDEFINED_MODIFICATIONS, "[]"));
        // 各卡片的警示语来源、悬浮窗大小
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
            importLegacyCustomMotivationTag(personalize, result);
            importInts(personalize, PERSONALIZE_INT_KEYS, result);
        }

        JsonObject more = optObject(root, "more");
        if (more != null) {
            importInts(more, MORE_INT_KEYS, result);
        }

        JsonObject reminderProvider = optObject(root, "reminderProvider");
        if (reminderProvider != null) {
            importReminderProvider(reminderProvider, result);
        }

        JsonObject doubaoTts = optObject(root, "doubaoTts");
        if (doubaoTts != null) {
            importDoubaoTtsConfig(doubaoTts, result);
        }

        // 密钥区块依赖 reminderProvider 先恢复出 Provider 定义，故放在其后处理
        JsonObject secrets = optObject(root, "secrets");
        if (secrets != null) {
            importSecrets(secrets, result);
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
                        optInt(o, "relaxedLimitCount", 1),
                        optBoolean(o, "globalBlock", false));
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

    private void importReminderProvider(JsonObject provider, ImportResult result) {
        if (has(provider, "profiles")) {
            try {
                int merged = providerStore.restoreProfiles(provider.get("profiles").getAsString());
                result.imported += merged;
            } catch (Exception e) {
                result.skipped++;
            }
        }
        if (has(provider, "customModels") && provider.get("customModels").isJsonObject()) {
            try {
                Map<String, List<String>> customModels =
                        parseCustomModels(provider.getAsJsonObject("customModels"));
                result.imported += providerStore.restoreCustomModels(customModels);
            } catch (Exception e) {
                result.skipped++;
            }
        }
    }

    /** 解析 {@code {ownerKey: [模型名...]}} 为去空串的 Map。 */
    private static Map<String, List<String>> parseCustomModels(JsonObject obj) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (!entry.getValue().isJsonArray()) {
                continue;
            }
            List<String> models = new ArrayList<>();
            for (JsonElement model : entry.getValue().getAsJsonArray()) {
                if (model.isJsonPrimitive() && !model.getAsString().trim().isEmpty()) {
                    models.add(model.getAsString());
                }
            }
            if (!models.isEmpty()) {
                map.put(entry.getKey(), models);
            }
        }
        return map;
    }

    /** 恢复豆包 TTS 非密钥配置（资源 ID / 发音人）。 */
    private void importDoubaoTtsConfig(JsonObject config, ImportResult result) {
        if (!has(config, "resourceId") && !has(config, "speaker")) {
            return;
        }
        DoubaoTtsConfigManager doubaoManager = new DoubaoTtsConfigManager(context);
        try {
            if (has(config, "resourceId")) {
                doubaoManager.setResourceId(config.get("resourceId").getAsString());
            }
            if (has(config, "speaker")) {
                doubaoManager.setSpeaker(config.get("speaker").getAsString());
            }
            result.imported++;
        } catch (Exception e) {
            Log.w(TAG, "恢复豆包 TTS 配置失败", e);
            result.skipped++;
        }
    }

    /**
     * 恢复密钥区块：Provider API Key 经 Android Keystore 重新加密落盘，
     * SOE / 豆包 TTS 写回各自配置存储。任一失败只跳过该条。
     * Provider 密钥仅在对应 profile 存在时才恢复，避免产生无主密钥文件。
     */
    private void importSecrets(JsonObject secrets, ImportResult result) {
        JsonObject provider = optObject(secrets, "provider");
        if (provider != null) {
            ProviderSecretStore secretStore = new ProviderSecretStore(context);
            for (Map.Entry<String, JsonElement> entry : provider.entrySet()) {
                String profileId = entry.getKey();
                try {
                    if (providerStore.getProfile(profileId) == null) {
                        result.skipped++;
                        continue;
                    }
                    String apiKey = entry.getValue().getAsString();
                    if (apiKey == null || apiKey.isEmpty()) {
                        result.skipped++;
                        continue;
                    }
                    secretStore.saveApiKey(profileId, apiKey);
                    result.imported++;
                } catch (Exception e) {
                    Log.w(TAG, "恢复 Provider 密钥失败: " + profileId, e);
                    result.skipped++;
                }
            }
        }

        JsonObject soe = optObject(secrets, "soe");
        if (soe != null) {
            SoeConfigManager soeManager = new SoeConfigManager(context);
            try {
                if (has(soe, "appId")) {
                    soeManager.setAppId(soe.get("appId").getAsString());
                }
                if (has(soe, "secretId")) {
                    soeManager.setSecretId(soe.get("secretId").getAsString());
                }
                if (has(soe, "secretKey")) {
                    soeManager.setSecretKey(soe.get("secretKey").getAsString());
                }
                if (has(soe, "scoreCoeff")) {
                    soeManager.setScoreCoeff(soe.get("scoreCoeff").getAsFloat());
                }
                result.imported++;
            } catch (Exception e) {
                Log.w(TAG, "恢复 SOE 配置失败", e);
                result.skipped++;
            }
        }

        JsonObject doubao = optObject(secrets, "doubao");
        if (doubao != null) {
            DoubaoTtsConfigManager doubaoManager = new DoubaoTtsConfigManager(context);
            try {
                if (has(doubao, "apiKey")) {
                    doubaoManager.setApiKey(doubao.get("apiKey").getAsString());
                }
                if (has(doubao, "resourceId")) {
                    doubaoManager.setResourceId(doubao.get("resourceId").getAsString());
                }
                if (has(doubao, "speaker")) {
                    doubaoManager.setSpeaker(doubao.get("speaker").getAsString());
                }
                result.imported++;
            } catch (Exception e) {
                Log.w(TAG, "恢复豆包 TTS 配置失败", e);
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
                        optInt(o, "relaxedLimitCount", 1),
                        optBoolean(o, "globalBlock", false));
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

    private void importLegacyCustomMotivationTag(JsonObject personalize, ImportResult result) {
        if (has(personalize, AppSettingsManager.KEY_CUSTOM_MOTIVATION_TAGS)
                || !has(personalize, AppSettingsManager.KEY_MOTIVATION_TAG)) {
            return;
        }
        try {
            String tag = personalize.get(AppSettingsManager.KEY_MOTIVATION_TAG).getAsString();
            if (!Const.TARGET_TO_BE_SET.equals(tag)
                    && !AppSettingsManager.isPredefinedMotivationTag(tag)) {
                appSettingsManager.addCustomMotivationTag(tag);
                result.imported++;
            }
        } catch (Exception e) {
            result.skipped++;
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

    private static boolean optBoolean(JsonObject obj, String name, boolean fallback) {
        return has(obj, name) ? obj.get(name).getAsBoolean() : fallback;
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
