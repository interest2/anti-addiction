package com.book.mask.personalize;

import android.content.Context;

import com.tencent.mmkv.MMKV;

/**
 * 豆包语音合成（火山引擎 Speech SDK · 双向流式 TTS）凭据配置存储。
 *
 * <p>字段对齐官方 demo 的 BiTTS 在线合成参数：API Key、ResourceID（音色克隆/大模型资源）、Speaker（发音人）。
 * 任一字段为空即视为未配置，听力题将回退占位音频，不会发起合成请求。
 */
public class DoubaoTtsConfigManager {

    static final String KEY_API_KEY = "doubao_tts_api_key";
    static final String KEY_RESOURCE_ID = "doubao_tts_resource_id";
    static final String KEY_SPEAKER = "doubao_tts_speaker";

    private final MMKV mmkv;

    public DoubaoTtsConfigManager(Context context) {
        this.mmkv = MMKV.mmkvWithID("doubao_tts_config_v1");
    }

    public String getApiKey() {
        return mmkv.getString(KEY_API_KEY, "").trim();
    }

    public void setApiKey(String apiKey) {
        mmkv.putString(KEY_API_KEY, apiKey == null ? "" : apiKey.trim()).commit();
    }

    public String getResourceId() {
        return mmkv.getString(KEY_RESOURCE_ID, "").trim();
    }

    public void setResourceId(String resourceId) {
        mmkv.putString(KEY_RESOURCE_ID, resourceId == null ? "" : resourceId.trim()).commit();
    }

    public String getSpeaker() {
        return mmkv.getString(KEY_SPEAKER, "").trim();
    }

    public void setSpeaker(String speaker) {
        mmkv.putString(KEY_SPEAKER, speaker == null ? "" : speaker.trim()).commit();
    }

    /** 双向流式 TTS 所需凭据是否齐全：API Key / ResourceID / Speaker 均非空。 */
    public boolean isConfigured() {
        return !getApiKey().isEmpty()
                && !getResourceId().isEmpty()
                && !getSpeaker().isEmpty();
    }
}
