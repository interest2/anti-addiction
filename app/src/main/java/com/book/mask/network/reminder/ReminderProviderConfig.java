package com.book.mask.network.reminder;

import java.util.Locale;

public final class ReminderProviderConfig {
    public static final String OFFICIAL_PROFILE_ID = "official";
    public static final String CUSTOM_PROFILE_ID = "custom_default";
    public static final int DEFAULT_REFRESH_INTERVAL_MINUTES = 0;

    public enum ProviderType {
        OFFICIAL("official"),
        OPENAI_COMPATIBLE("openai_compatible");

        private final String storageValue;

        ProviderType(String storageValue) {
            this.storageValue = storageValue;
        }

        public String getStorageValue() {
            return storageValue;
        }

        public static ProviderType fromStorageValue(String value) {
            return OPENAI_COMPATIBLE.storageValue.equals(value)
                    ? OPENAI_COMPATIBLE
                    : OFFICIAL;
        }
    }

    private final String profileId;
    private final ProviderType providerType;
    private final String providerName;
    private final String endpointUrl;
    private final String model;
    private final int refreshIntervalMinutes;
    private final long configRevision;
    private final long lastVerifiedAt;

    public ReminderProviderConfig(
            String profileId,
            ProviderType providerType,
            String providerName,
            String endpointUrl,
            String model,
            int refreshIntervalMinutes,
            long configRevision,
            long lastVerifiedAt) {
        this.profileId = profileId;
        this.providerType = providerType;
        this.providerName = providerName;
        this.endpointUrl = endpointUrl;
        this.model = model;
        this.refreshIntervalMinutes = Math.max(0, refreshIntervalMinutes);
        this.configRevision = Math.max(1, configRevision);
        this.lastVerifiedAt = Math.max(0, lastVerifiedAt);
    }

    public static ReminderProviderConfig official() {
        return new ReminderProviderConfig(
                OFFICIAL_PROFILE_ID,
                ProviderType.OFFICIAL,
                "",
                "",
                "",
                0,
                1,
                0);
    }

    public String getProfileId() {
        return profileId;
    }

    public ProviderType getProviderType() {
        return providerType;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public String getModel() {
        return model;
    }

    public int getRefreshIntervalMinutes() {
        return refreshIntervalMinutes;
    }

    public long getConfigRevision() {
        return configRevision;
    }

    public long getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public boolean isOfficial() {
        return providerType == ProviderType.OFFICIAL;
    }

    public String cacheIdentity() {
        return String.format(
                Locale.US,
                "%s|%s|%s|%s|%d",
                profileId,
                providerType.getStorageValue(),
                endpointUrl,
                model,
                configRevision);
    }
}
