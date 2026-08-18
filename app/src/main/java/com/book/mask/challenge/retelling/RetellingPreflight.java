package com.book.mask.challenge.retelling;

import android.content.Context;

import com.book.mask.reminder.config.ProviderSecretStore;
import com.book.mask.reminder.config.ReminderProviderConfig;
import com.book.mask.reminder.config.ReminderProviderConfigStore;

import java.security.GeneralSecurityException;

/**
 * 复述题启用前置检查：大模型配置（自定义 Provider 必须有 API Key）+ ASR 模型状态。
 * 设置页「切换题型」与悬浮窗内快捷切题型共用同一份判断，避免两处各自维护一份判断逻辑。
 */
public final class RetellingPreflight {

    private RetellingPreflight() {
    }

    /** 返回非空表示前置检查未通过，可直接展示给用户；返回 null 表示可以启用复述题。 */
    public static String check(Context context) {
        ReminderProviderConfig config = new ReminderProviderConfigStore().getActiveConfig();
        if (!config.isOfficial()) {
            try {
                String apiKey = new ProviderSecretStore(context).getApiKey(config.getProfileId());
                if (apiKey == null || apiKey.isEmpty()) {
                    return "自定义大模型未配置 API Key，请先到提醒设置中配置";
                }
            } catch (GeneralSecurityException e) {
                return "读取大模型配置失败，请重新配置";
            }
        }
        if (!SherpaOnnxTranscriber.getInstance(context).isReady()) {
            return "语音识别模型未就绪（ASR 模型缺失），请先放入模型文件";
        }
        return null;
    }
}
