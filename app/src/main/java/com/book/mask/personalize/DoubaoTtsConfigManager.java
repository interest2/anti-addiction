package com.book.mask.personalize;

import android.content.Context;

import com.tencent.mmkv.MMKV;

/**
 * 豆包语音合成（火山引擎 Speech SDK）凭据配置存储。
 *
 * <p>字段对齐官方 demo 的在线合成参数：AppID、Token、Cluster（集群）、音色代号 VoiceType、发音人 Voice。
 * 任一字段为空即视为未配置，听力题将回退占位音频，不会发起合成请求。
 */
public class DoubaoTtsConfigManager {

    static final String KEY_APP_ID = "doubao_tts_app_id";
    static final String KEY_TOKEN = "doubao_tts_token";
    static final String KEY_CLUSTER = "doubao_tts_cluster";
    static final String KEY_VOICE_TYPE = "doubao_tts_voice_type";
    static final String KEY_VOICE = "doubao_tts_voice";

    private final MMKV mmkv;

    public DoubaoTtsConfigManager(Context context) {
        this.mmkv = MMKV.mmkvWithID("doubao_tts_config_v1");
    }

    public String getAppId() {
        return mmkv.getString(KEY_APP_ID, "").trim();
    }

    public void setAppId(String appId) {
        mmkv.putString(KEY_APP_ID, appId == null ? "" : appId.trim()).commit();
    }

    public String getToken() {
        return mmkv.getString(KEY_TOKEN, "").trim();
    }

    public void setToken(String token) {
        mmkv.putString(KEY_TOKEN, token == null ? "" : token.trim()).commit();
    }

    public String getCluster() {
        return mmkv.getString(KEY_CLUSTER, "").trim();
    }

    public void setCluster(String cluster) {
        mmkv.putString(KEY_CLUSTER, cluster == null ? "" : cluster.trim()).commit();
    }

    public String getVoiceType() {
        return mmkv.getString(KEY_VOICE_TYPE, "").trim();
    }

    public void setVoiceType(String voiceType) {
        mmkv.putString(KEY_VOICE_TYPE, voiceType == null ? "" : voiceType.trim()).commit();
    }

    public String getVoice() {
        return mmkv.getString(KEY_VOICE, "").trim();
    }

    public void setVoice(String voice) {
        mmkv.putString(KEY_VOICE, voice == null ? "" : voice.trim()).commit();
    }

    /** 在线合成所需凭据是否齐全：AppID / Token / Cluster / 音色代号 / 发音人均非空。 */
    public boolean isConfigured() {
        return !getAppId().isEmpty()
                && !getToken().isEmpty()
                && !getCluster().isEmpty()
                && !getVoiceType().isEmpty()
                && !getVoice().isEmpty();
    }
}
