package com.book.mask.challenge.retelling;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

import com.book.mask.personalize.SoeConfigManager;
import com.book.mask.reminder.config.ProviderSecretStore;
import com.book.mask.reminder.config.ReminderProviderConfig;
import com.book.mask.reminder.config.ReminderProviderConfigStore;

import java.security.GeneralSecurityException;
import java.util.List;

/**
 * 复述类题目启用前置检查：麦克风、腾讯口语评测、本地 ASR 与内容评分 Provider。
 * 设置页「切换题型」与悬浮窗内快捷切题型共用同一份判断，避免两处各自维护一份判断逻辑。
 */
public final class RetellingPreflight {

    private RetellingPreflight() {
    }

    /** 返回非空表示前置检查未通过，可直接展示给用户；返回 null 表示可以启用复述题。 */
    public static String check(Context context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            return "未授予麦克风权限，请先在题目设置中完成授权";
        }
        if (!new SoeConfigManager(context).isConfigured()) {
            return "未配置腾讯口语评测，请先在题目类型设置中配置";
        }
        if (!SherpaOnnxTranscriber.getInstance(context).isReady()) {
            return "语音识别模型未就绪（ASR 模型缺失），请先放入模型文件";
        }
        return checkScoringProvider(context);
    }

    /** 当前激活官方服务时，内容评分会回退到最近配置且带密钥的自定义 Provider。 */
    private static String checkScoringProvider(Context context) {
        ReminderProviderConfigStore configStore = new ReminderProviderConfigStore();
        ReminderProviderConfig active = configStore.getActiveConfig();
        ProviderSecretStore secretStore = new ProviderSecretStore(context);
        try {
            if (!active.isOfficial()) {
                return hasApiKey(secretStore, active.getProfileId())
                        ? null
                        : "自定义大模型未配置 API Key，请先到提醒设置中配置";
            }
            List<ReminderProviderConfig> profiles = configStore.getProfiles();
            for (int i = profiles.size() - 1; i >= 0; i--) {
                ReminderProviderConfig config = profiles.get(i);
                if (!config.isOfficial() && hasApiKey(secretStore, config.getProfileId())) {
                    return null;
                }
            }
            return "复述内容评分需要自定义模型服务商，请先到提醒设置中配置";
        } catch (GeneralSecurityException e) {
            return "读取大模型配置失败，请重新配置";
        }
    }

    private static boolean hasApiKey(ProviderSecretStore secretStore, String profileId)
            throws GeneralSecurityException {
        String apiKey = secretStore.getApiKey(profileId);
        return apiKey != null && !apiKey.isEmpty();
    }
}
