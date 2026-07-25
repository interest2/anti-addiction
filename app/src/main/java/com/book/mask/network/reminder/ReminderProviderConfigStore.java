package com.book.mask.network.reminder;

import com.tencent.mmkv.MMKV;

public final class ReminderProviderConfigStore {
    private static final String STORAGE_ID = "reminder_provider_config";
    private static final String KEY_ACTIVE_TYPE = "active_type";
    private static final String KEY_CUSTOM_ENDPOINT = "custom_endpoint";
    private static final String KEY_CUSTOM_MODEL = "custom_model";
    private static final String KEY_CUSTOM_AUTH_TYPE = "custom_auth_type";
    private static final String KEY_CUSTOM_REFRESH_INTERVAL = "custom_refresh_interval";
    private static final String KEY_CUSTOM_REVISION = "custom_revision";
    private static final String KEY_CUSTOM_LAST_VERIFIED = "custom_last_verified";

    private final MMKV mmkv;

    public ReminderProviderConfigStore() {
        mmkv = MMKV.mmkvWithID(STORAGE_ID);
    }

    public ReminderProviderConfig getActiveConfig() {
        ReminderProviderConfig.ProviderType activeType =
                ReminderProviderConfig.ProviderType.fromStorageValue(
                        mmkv.getString(
                                KEY_ACTIVE_TYPE,
                                ReminderProviderConfig.ProviderType.OFFICIAL.getStorageValue()));
        return activeType == ReminderProviderConfig.ProviderType.OPENAI_COMPATIBLE
                ? getCustomConfig()
                : ReminderProviderConfig.official();
    }

    public ReminderProviderConfig getCustomConfig() {
        return new ReminderProviderConfig(
                ReminderProviderConfig.CUSTOM_PROFILE_ID,
                ReminderProviderConfig.ProviderType.OPENAI_COMPATIBLE,
                mmkv.getString(KEY_CUSTOM_ENDPOINT, ""),
                mmkv.getString(KEY_CUSTOM_MODEL, ""),
                ReminderProviderConfig.AuthType.fromStorageValue(
                        mmkv.getString(
                                KEY_CUSTOM_AUTH_TYPE,
                                ReminderProviderConfig.AuthType.BEARER.getStorageValue())),
                mmkv.getInt(
                        KEY_CUSTOM_REFRESH_INTERVAL,
                        ReminderProviderConfig.DEFAULT_REFRESH_INTERVAL_MINUTES),
                mmkv.getLong(KEY_CUSTOM_REVISION, 1),
                mmkv.getLong(KEY_CUSTOM_LAST_VERIFIED, 0));
    }

    public void activateOfficial() {
        mmkv.putString(
                        KEY_ACTIVE_TYPE,
                        ReminderProviderConfig.ProviderType.OFFICIAL.getStorageValue())
                .commit();
    }

    public ReminderProviderConfig saveAndActivateCustom(
            String endpointUrl,
            String model,
            ReminderProviderConfig.AuthType authType,
            int refreshIntervalMinutes,
            long verifiedAt) {
        long revision = mmkv.getLong(KEY_CUSTOM_REVISION, 1) + 1;
        mmkv.putString(KEY_CUSTOM_ENDPOINT, endpointUrl)
                .putString(KEY_CUSTOM_MODEL, model)
                .putString(KEY_CUSTOM_AUTH_TYPE, authType.getStorageValue())
                .putInt(KEY_CUSTOM_REFRESH_INTERVAL, Math.max(0, refreshIntervalMinutes))
                .putLong(KEY_CUSTOM_REVISION, revision)
                .putLong(KEY_CUSTOM_LAST_VERIFIED, verifiedAt)
                .putString(
                        KEY_ACTIVE_TYPE,
                        ReminderProviderConfig.ProviderType.OPENAI_COMPATIBLE.getStorageValue())
                .commit();
        return getCustomConfig();
    }

    public boolean isCustomActive() {
        return getActiveConfig().getProviderType()
                == ReminderProviderConfig.ProviderType.OPENAI_COMPATIBLE;
    }
}
