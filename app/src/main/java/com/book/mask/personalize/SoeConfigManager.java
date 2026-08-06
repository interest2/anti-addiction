package com.book.mask.personalize;

import android.content.Context;

import com.tencent.mmkv.MMKV;

/**
 * 腾讯云口语评测（智聆口语评测 SOE）凭据配置存储。
 *
 * <p>字段对齐腾讯云控制台的 AppID / SecretId / SecretKey，外加评分苛刻指数 ScoreCoeff。
 * 任一关键凭据为空即视为未配置，复述题将回退本地语音识别 + 纯文本评分，不发起评测请求。
 *
 * <p>安全提示：SecretKey 落入 APK 存在逆向泄露风险，仅供本地验证；正式发布应改由后端签发
 * 短期签名 URL（见 {@code CloudConst} 规划路径），客户端不再持有任何密钥。
 */
public class SoeConfigManager {

    static final String KEY_APP_ID = "soe_app_id";
    static final String KEY_SECRET_ID = "soe_secret_id";
    static final String KEY_SECRET_KEY = "soe_secret_key";
    static final String KEY_SCORE_COEFF = "soe_score_coeff";

    /** 评测苛刻指数默认值（腾讯文档建议区间 1.0-4.0）。 */
    public static final float DEFAULT_SCORE_COEFF = 1.5f;
    private static final float SCORE_COEFF_MIN = 1.0f;
    private static final float SCORE_COEFF_MAX = 4.0f;

    private final MMKV mmkv;

    public SoeConfigManager(Context context) {
        this.mmkv = MMKV.mmkvWithID("soe_config_v1");
    }

    public String getAppId() {
        return mmkv.getString(KEY_APP_ID, "").trim();
    }

    public void setAppId(String appId) {
        mmkv.putString(KEY_APP_ID, appId == null ? "" : appId.trim()).commit();
    }

    public String getSecretId() {
        return mmkv.getString(KEY_SECRET_ID, "").trim();
    }

    public void setSecretId(String secretId) {
        mmkv.putString(KEY_SECRET_ID, secretId == null ? "" : secretId.trim()).commit();
    }

    public String getSecretKey() {
        return mmkv.getString(KEY_SECRET_KEY, "").trim();
    }

    public void setSecretKey(String secretKey) {
        mmkv.putString(KEY_SECRET_KEY, secretKey == null ? "" : secretKey.trim()).commit();
    }

    public float getScoreCoeff() {
        float value = mmkv.getFloat(KEY_SCORE_COEFF, DEFAULT_SCORE_COEFF);
        if (Float.isNaN(value)) {
            return DEFAULT_SCORE_COEFF;
        }
        return Math.max(SCORE_COEFF_MIN, Math.min(SCORE_COEFF_MAX, value));
    }

    public void setScoreCoeff(float scoreCoeff) {
        float valid = Float.isNaN(scoreCoeff)
                ? DEFAULT_SCORE_COEFF
                : Math.max(SCORE_COEFF_MIN, Math.min(SCORE_COEFF_MAX, scoreCoeff));
        mmkv.putFloat(KEY_SCORE_COEFF, valid).commit();
    }

    /** 发起口语评测所需的凭据是否齐全：AppID / SecretId / SecretKey 均非空。 */
    public boolean isConfigured() {
        return !getAppId().isEmpty() && !getSecretId().isEmpty() && !getSecretKey().isEmpty();
    }
}
