package com.book.mask.reminder.config;

import java.util.Locale;

public final class ReminderProviderConfig {
    public static final String OFFICIAL_PROFILE_ID = "official";
    public static final String OFFICIAL_PRESET_ID = "official";

    public enum ProviderType {
        OFFICIAL,
        OPENAI_CHAT
    }

    private final String profileId;
    private final String presetId;
    private final ProviderType providerType;
    private final String providerName;
    private final String endpointUrl;
    private final String model;
    private final long configRevision;
    private final long lastVerifiedAt;

    public ReminderProviderConfig(
            String profileId,
            String presetId,
            ProviderType providerType,
            String providerName,
            String endpointUrl,
            String model,
            long configRevision,
            long lastVerifiedAt) {
        this.profileId = profileId;
        this.presetId = presetId;
        this.providerType = providerType;
        this.providerName = providerName;
        this.endpointUrl = endpointUrl;
        this.model = model;
        this.configRevision = Math.max(1, configRevision);
        this.lastVerifiedAt = Math.max(0, lastVerifiedAt);
    }

    public static ReminderProviderConfig official() {
        return new ReminderProviderConfig(
                OFFICIAL_PROFILE_ID,
                OFFICIAL_PRESET_ID,
                ProviderType.OFFICIAL,
                "",
                "",
                "",
                1,
                0);
    }

    public String getProfileId() {
        return profileId;
    }

    public String getPresetId() {
        return presetId;
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
                providerType.name(),
                endpointUrl,
                model,
                configRevision);
    }
}
