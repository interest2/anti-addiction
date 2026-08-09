package com.book.mask.reminder.config;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.tencent.mmkv.MMKV;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReminderProviderConfigStore {
    private static final String STORAGE_ID = "reminder_provider_profiles_v1";
    private static final String KEY_ACTIVE_PROFILE_ID = "active_profile_id";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_CUSTOM_MODELS_PREFIX = "custom_models_";

    private final MMKV mmkv;
    private final Gson gson = new Gson();

    public ReminderProviderConfigStore() {
        mmkv = MMKV.mmkvWithID(STORAGE_ID);
    }

    public synchronized ReminderProviderConfig getActiveConfig() {
        String activeProfileId = mmkv.getString(
                KEY_ACTIVE_PROFILE_ID,
                ReminderProviderConfig.OFFICIAL_PROFILE_ID);
        if (ReminderProviderConfig.OFFICIAL_PROFILE_ID.equals(activeProfileId)) {
            return ReminderProviderConfig.official();
        }
        ReminderProviderConfig profile = getProfile(activeProfileId);
        return profile == null ? ReminderProviderConfig.official() : profile;
    }

    public synchronized List<ReminderProviderConfig> getProfiles() {
        ReminderProviderConfig[] profiles = readProfiles();
        if (profiles.length == 0) {
            return Collections.emptyList();
        }
        return new ArrayList<>(Arrays.asList(profiles));
    }

    public synchronized ReminderProviderConfig getProfile(String profileId) {
        if (profileId == null || ReminderProviderConfig.OFFICIAL_PROFILE_ID.equals(profileId)) {
            return null;
        }
        for (ReminderProviderConfig profile : readProfiles()) {
            if (profileId.equals(profile.getProfileId())) {
                return profile;
            }
        }
        return null;
    }

    public String newProfileId() {
        return UUID.randomUUID().toString();
    }

    public synchronized ReminderProviderConfig saveAndActivate(ReminderProviderConfig draft) {
        List<ReminderProviderConfig> profiles = new ArrayList<>(Arrays.asList(readProfiles()));
        int index = indexOf(profiles, draft.getProfileId());
        long revision = index < 0 ? 1 : profiles.get(index).getConfigRevision() + 1;
        ReminderProviderConfig saved = new ReminderProviderConfig(
                draft.getProfileId(),
                draft.getPresetId(),
                ReminderProviderConfig.ProviderType.OPENAI_CHAT,
                draft.getProviderName(),
                draft.getEndpointUrl(),
                draft.getModel(),
                revision,
                draft.getLastVerifiedAt());
        if (index < 0) {
            profiles.add(saved);
        } else {
            profiles.set(index, saved);
        }
        mmkv.putString(KEY_PROFILES, gson.toJson(profiles))
                .putString(KEY_ACTIVE_PROFILE_ID, saved.getProfileId())
                .commit();
        return saved;
    }

    public synchronized boolean activate(String profileId) {
        if (ReminderProviderConfig.OFFICIAL_PROFILE_ID.equals(profileId)) {
            activateOfficial();
            return true;
        }
        if (getProfile(profileId) == null) {
            return false;
        }
        mmkv.putString(KEY_ACTIVE_PROFILE_ID, profileId).commit();
        return true;
    }

    public synchronized void activateOfficial() {
        mmkv.putString(
                KEY_ACTIVE_PROFILE_ID,
                ReminderProviderConfig.OFFICIAL_PROFILE_ID).commit();
    }

    public synchronized boolean delete(String profileId) {
        if (profileId == null || ReminderProviderConfig.OFFICIAL_PROFILE_ID.equals(profileId)) {
            return false;
        }
        List<ReminderProviderConfig> profiles = new ArrayList<>(Arrays.asList(readProfiles()));
        int index = indexOf(profiles, profileId);
        if (index < 0) {
            return false;
        }
        profiles.remove(index);
        String activeProfileId = mmkv.getString(
                KEY_ACTIVE_PROFILE_ID,
                ReminderProviderConfig.OFFICIAL_PROFILE_ID);
        mmkv.putString(KEY_PROFILES, gson.toJson(profiles));
        if (profileId.equals(activeProfileId)) {
            mmkv.putString(
                    KEY_ACTIVE_PROFILE_ID,
                    ReminderProviderConfig.OFFICIAL_PROFILE_ID);
        }
        mmkv.commit();
        return true;
    }

    /** 备份导出：返回原始 profiles JSON（仅含 endpoint/model/名称等定义，不含密钥）。 */
    public synchronized String exportProfilesJson() {
        return mmkv.getString(KEY_PROFILES, "[]");
    }

    /**
     * 备份导入：按 profileId 逐条 upsert 合并 profiles，不改动当前激活项。
     * 密钥不在备份内，故不自动激活导入的自定义 Provider（避免运行时因缺密钥失败）。
     *
     * @return 实际合并（新增或更新）的 Provider 条数
     */
    public synchronized int restoreProfiles(String profilesJson) {
        ReminderProviderConfig[] parsed;
        try {
            parsed = gson.fromJson(profilesJson, ReminderProviderConfig[].class);
        } catch (JsonSyntaxException e) {
            return 0;
        }
        if (parsed == null || parsed.length == 0) {
            return 0;
        }
        List<ReminderProviderConfig> profiles = new ArrayList<>(Arrays.asList(readProfiles()));
        int count = 0;
        for (ReminderProviderConfig incoming : parsed) {
            if (incoming == null || incoming.getProfileId() == null
                    || ReminderProviderConfig.OFFICIAL_PROFILE_ID.equals(incoming.getProfileId())) {
                continue;
            }
            int index = indexOf(profiles, incoming.getProfileId());
            if (index < 0) {
                profiles.add(incoming);
            } else {
                profiles.set(index, incoming);
            }
            count++;
        }
        if (count > 0) {
            mmkv.putString(KEY_PROFILES, gson.toJson(profiles)).commit();
        }
        return count;
    }

    /** 读取某预置服务商下用户曾输入并保存过的自定义模型名（不含预置目录里的模型）。 */
    public synchronized List<String> getCustomModels(String presetId) {
        if (presetId == null || presetId.isEmpty()) {
            return new ArrayList<>();
        }
        String json = mmkv.getString(KEY_CUSTOM_MODELS_PREFIX + presetId, "[]");
        try {
            String[] models = gson.fromJson(json, String[].class);
            return models == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(models));
        } catch (JsonSyntaxException e) {
            return new ArrayList<>();
        }
    }

    /** 记录一个用户自定义的模型名到该预置服务商下（去重）。 */
    public synchronized void addCustomModel(String presetId, String model) {
        if (presetId == null || presetId.isEmpty() || model == null || model.trim().isEmpty()) {
            return;
        }
        List<String> models = getCustomModels(presetId);
        if (!models.contains(model)) {
            models.add(model);
            mmkv.putString(KEY_CUSTOM_MODELS_PREFIX + presetId, gson.toJson(models)).commit();
        }
    }

    /** 删除某 owner 下的一个自定义模型名。 */
    public synchronized boolean removeCustomModel(String ownerKey, String model) {
        if (ownerKey == null || ownerKey.isEmpty() || model == null) {
            return false;
        }
        List<String> models = getCustomModels(ownerKey);
        if (!models.remove(model)) {
            return false;
        }
        mmkv.putString(KEY_CUSTOM_MODELS_PREFIX + ownerKey, gson.toJson(models)).commit();
        return true;
    }

    /**
     * 备份导出：扫描所有 {@code custom_models_} 前缀键，按 ownerKey（预置服务商 id 或自定义 Provider
     * profileId）归组成 {ownerKey: [模型名...]}。仅含非空列表，无任何自定义模型时返回空 Map。
     */
    public synchronized Map<String, List<String>> exportCustomModels() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        String[] allKeys = mmkv.allKeys();
        if (allKeys == null) {
            return result;
        }
        for (String key : allKeys) {
            if (key.startsWith(KEY_CUSTOM_MODELS_PREFIX)) {
                String ownerKey = key.substring(KEY_CUSTOM_MODELS_PREFIX.length());
                List<String> models = getCustomModels(ownerKey);
                if (!models.isEmpty()) {
                    result.put(ownerKey, models);
                }
            }
        }
        return result;
    }

    /**
     * 备份导入：按 ownerKey 逐条合并自定义模型名（去重），返回实际写入的模型数。
     */
    public synchronized int restoreCustomModels(Map<String, List<String>> customModels) {
        if (customModels == null || customModels.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Map.Entry<String, List<String>> entry : customModels.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isEmpty()) {
                continue;
            }
            for (String model : entry.getValue()) {
                if (model != null && !model.trim().isEmpty()) {
                    addCustomModel(entry.getKey(), model);
                    count++;
                }
            }
        }
        return count;
    }

    private ReminderProviderConfig[] readProfiles() {        String json = mmkv.getString(KEY_PROFILES, "[]");
        try {
            ReminderProviderConfig[] profiles = gson.fromJson(
                    json,
                    ReminderProviderConfig[].class);
            return profiles == null ? new ReminderProviderConfig[0] : profiles;
        } catch (JsonSyntaxException e) {
            return new ReminderProviderConfig[0];
        }
    }

    private static int indexOf(List<ReminderProviderConfig> profiles, String profileId) {
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getProfileId().equals(profileId)) {
                return i;
            }
        }
        return -1;
    }
}
