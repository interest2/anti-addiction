package com.book.mask.network.reminder;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.tencent.mmkv.MMKV;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class ReminderProviderConfigStore {
    private static final String STORAGE_ID = "reminder_provider_profiles_v1";
    private static final String KEY_ACTIVE_PROFILE_ID = "active_profile_id";
    private static final String KEY_PROFILES = "profiles";

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

    private ReminderProviderConfig[] readProfiles() {
        String json = mmkv.getString(KEY_PROFILES, "[]");
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
