package com.book.mask.network.reminder;

import com.book.mask.R;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ProviderPresetCatalog {
    public static final String CUSTOM_PRESET_ID = "custom";

    private static final List<ProviderPreset> PRESETS = Collections.unmodifiableList(Arrays.asList(
            new ProviderPreset(
                    "openai",
                    R.string.provider_preset_openai,
                    "https://api.openai.com/v1/chat/completions",
                    "gpt-5.6-luna",
                    "gpt-5.6-terra",
                    "gpt-5.6-sol"),
            new ProviderPreset(
                    "deepseek",
                    R.string.provider_preset_deepseek,
                    "https://api.deepseek.com/chat/completions",
                    "deepseek-v4-flash",
                    "deepseek-v4-pro"),
            new ProviderPreset(
                    "moonshot",
                    R.string.provider_preset_moonshot,
                    "https://api.moonshot.cn/v1/chat/completions",
                    "kimi-k2.6",
                    "kimi-k3"),
            new ProviderPreset(
                    "zhipu",
                    R.string.provider_preset_zhipu,
                    "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                    "glm-4.7-flash",
                    "glm-4.6",
                    "glm-5.2")
    ));

    private ProviderPresetCatalog() {
    }

    public static List<ProviderPreset> getAll() {
        return PRESETS;
    }

    public static ProviderPreset getById(String id) {
        if (id == null) {
            return null;
        }
        for (ProviderPreset preset : PRESETS) {
            if (preset.getId().equals(id)) {
                return preset;
            }
        }
        return null;
    }
}
