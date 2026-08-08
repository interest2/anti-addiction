package com.book.mask.challenge.retelling.soe;

import android.util.Base64;
import android.util.Log;

import com.book.mask.personalize.SoeConfigManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * 腾讯云口语评测（智聆口语评测 SOE）客户端：自由说模式（eval_mode=3）流式评测。
 *
 * <p>录音开始后建立 WebSocket，随录随推 PCM（16k / 单声道 / 16bit）；录音结束发送
 * {@code {"type":"end"}}，服务端返回 {@code final:1} 消息，内含语音转写文本（{@code Words[].Word}
 * 拼接）与发音评分（准确度 / 流利度 / 建议得分）。转写文本供大模型内容评分，发音分供语音维度合并。
 *
 * <p>签名：HMAC-SHA1(SecretKey) 后 Base64，结果按字典序参数拼接的 URL 部分。凭据来自
 * {@link SoeConfigManager}；未配置或连接失败通过 {@link Listener} 回调，由上层回退本地识别。
 */
public final class SoeSpeechEvaluator {

    private static final String TAG = "SoeEvaluator";
    private static final String WS_HOST = "soe.cloud.tencent.com";
    private static final String WS_PATH = "/soe/api/";
    private static final String SERVER_ENGINE_TYPE = "16k_zh";
    private static final int EVAL_MODE_FREE_SPEECH = 3;
    private static final int REC_MODE_REALTIME = 0;
    private static final int VOICE_FORMAT_PCM = 0;
    private static final int SIGNATURE_VALID_SECONDS = 300;

    /** 口语评测结果：转写文本 + 发音分。任一分数为 -1 表示该维度未返回。 */
    public static final class SoeResult {
        private final String transcript;
        private final float accuracy;
        private final float fluency;
        private final float suggestedScore;

        SoeResult(String transcript, float accuracy, float fluency, float suggestedScore) {
            this.transcript = transcript;
            this.accuracy = accuracy;
            this.fluency = fluency;
            this.suggestedScore = suggestedScore;
        }

        public String getTranscript() {
            return transcript;
        }

        public float getAccuracy() {
            return accuracy;
        }

        public float getFluency() {
            return fluency;
        }

        public float getSuggestedScore() {
            return suggestedScore;
        }

        public boolean hasPronunciationScore() {
            return accuracy >= 0 && fluency >= 0;
        }
    }

    public interface Listener {
        /** 最终评测结果（回调在 OkHttp 线程，上层自行切主线程）。 */
        void onResult(SoeResult result);

        /** 评测失败（未配置 / 连接异常 / 业务错误码），上层回退本地识别。 */
        void onError(String message);
    }

    private final SoeConfigManager config;
    private OkHttpClient client;
    private WebSocket webSocket;
    private volatile boolean active;
    private Listener listener;

    public SoeSpeechEvaluator(SoeConfigManager config) {
        this.config = config;
    }

    public boolean isAvailable() {
        return config != null && config.isConfigured();
    }

    public boolean isActive() {
        return active;
    }

    /** 建立评测连接。未配置凭据时立即回调 onError。 */
    public void start(Listener listener) {
        this.listener = listener;
        active = false;
        if (!isAvailable()) {
            notifyError("未配置腾讯口语评测凭据");
            return;
        }
        try {
            String url = buildSignedUrl();
            client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    // 流式长连接，读超时交给业务错误码（如长时间静音由服务端断开）处理
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .build();
            Request request = new Request.Builder().url(url).build();
            webSocket = client.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket ws, Response response) {
                    Log.d(TAG, "SOE 连接已建立");
                }

                @Override
                public void onMessage(WebSocket ws, String text) {
                    handleMessage(ws, text);
                }

                @Override
                public void onFailure(WebSocket ws, Throwable t, Response response) {
                    Log.w(TAG, "SOE 连接异常", t);
                    closeQuietly(ws);
                    notifyError(t == null || t.getMessage() == null
                            ? "口语评测连接异常"
                            : "口语评测连接异常：" + t.getMessage());
                }

                @Override
                public void onClosed(WebSocket ws, int code, String reason) {
                    active = false;
                }
            });
            active = true;
        } catch (Exception e) {
            Log.e(TAG, "SOE 启动异常", e);
            notifyError("口语评测启动失败：" + e.getMessage());
        }
    }

    /**
     * 推送一段 PCM 采样（16k / mono / 16bit，little-endian）。由录音线程随录随推，速率天然 1:1。
     */
    public void sendPcmChunk(short[] samples, int length) {
        WebSocket ws = webSocket;
        if (!active || ws == null || samples == null || length <= 0) {
            return;
        }
        byte[] pcm = new byte[length * 2];
        for (int i = 0; i < length; i++) {
            short sample = samples[i];
            pcm[i * 2] = (byte) (sample & 0xFF);
            pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        ws.send(okio.ByteString.of(pcm));
    }

    /** 录音结束，通知服务端停止接收并返回最终结果。 */
    public void finish() {
        WebSocket ws = webSocket;
        if (active && ws != null) {
            ws.send("{\"type\":\"end\"}");
        }
    }

    /** 释放连接与资源。录音取消 / 会话销毁时调用。 */
    public void close() {
        active = false;
        closeQuietly(webSocket);
        webSocket = null;
        if (client != null) {
            try {
                client.dispatcher().executorService().shutdown();
                client.connectionPool().evictAll();
            } catch (Exception ignored) {
                // 释放失败可忽略
            }
            client = null;
        }
    }

    private void closeQuietly(WebSocket ws) {
        if (ws != null) {
            try {
                ws.close(1000, null);
            } catch (Exception ignored) {
                // 已关闭可忽略
            }
        }
    }

    private void handleMessage(WebSocket ws, String text) {
        try {
            JSONObject json = new JSONObject(text);
            int code = json.optInt("code", 0);
            if (code != 0) {
                Log.w(TAG, "SOE 业务错误 code=" + code + ", message=" + json.optString("message"));
                closeQuietly(ws);
                notifyError("口语评测失败：" + json.optString("message", "code=" + code));
                return;
            }
            if (json.optInt("final", 0) == 1) {
                SoeResult result = parseResult(json.optJSONObject("result"));
                Log.i(TAG, String.format("SOE 评分：SuggestedScore=%.1f, PronAccuracy=%.1f, PronFluency=%.1f",
                        result.getSuggestedScore(), result.getAccuracy(), result.getFluency()));
                closeQuietly(ws);
                active = false;
                if (listener != null) {
                    listener.onResult(result);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "解析 SOE 消息失败", e);
        }
    }

    private static SoeResult parseResult(JSONObject result) {
        if (result == null) {
            return new SoeResult("", -1f, -1f, -1f);
        }
        float suggested = (float) result.optDouble("SuggestedScore", -1);
        float accuracy = (float) result.optDouble("PronAccuracy", -1);
        // PronFluency 原始量纲为 0-1，统一换算为 0-100 供合并 / 记录 / 展示使用
        double rawFluency = result.optDouble("PronFluency", -1);
        float fluency = rawFluency < 0 ? -1f : (float) (rawFluency * 100f);
        StringBuilder transcript = new StringBuilder();
        JSONArray words = result.optJSONArray("Words");
        if (words != null) {
            for (int i = 0; i < words.length(); i++) {
                JSONObject word = words.optJSONObject(i);
                if (word != null) {
                    String text = word.optString("Word", "");
                    if (!text.isEmpty()) {
                        transcript.append(text);
                    }
                }
            }
        }
        return new SoeResult(transcript.toString().trim(), accuracy, fluency, suggested);
    }

    /**
     * 构建带签名的 WebSocket 地址。签名原文 = {@code host + path + appid + "?" + 参数字典序拼接}，
     * 参数除 signature 外全部参与签名；签名结果 Base64 后 urlencode 追加到 URL。
     */
    private String buildSignedUrl() {
        long now = System.currentTimeMillis() / 1000;
        String timestamp = String.valueOf(now);
        String expired = String.valueOf(now + SIGNATURE_VALID_SECONDS);
        String nonce = String.valueOf(new Random().nextInt(1_000_000_000));
        String voiceId = UUID.randomUUID().toString();

        TreeMap<String, String> params = new TreeMap<>();
        params.put("eval_mode", String.valueOf(EVAL_MODE_FREE_SPEECH));
        params.put("expired", expired);
        params.put("nonce", nonce);
        params.put("rec_mode", String.valueOf(REC_MODE_REALTIME));
        params.put("ref_text", "");
        params.put("score_coeff", String.valueOf(config.getScoreCoeff()));
        params.put("secretid", config.getSecretId());
        params.put("sentence_info_enabled", "0");
        params.put("server_engine_type", SERVER_ENGINE_TYPE);
        params.put("text_mode", "0");
        params.put("timestamp", timestamp);
        params.put("voice_format", String.valueOf(VOICE_FORMAT_PCM));
        params.put("voice_id", voiceId);

        StringBuilder paramString = new StringBuilder();
        for (String key : params.keySet()) {
            if (paramString.length() > 0) {
                paramString.append("&");
            }
            paramString.append(key).append("=").append(params.get(key));
        }
        String signSource = WS_HOST + WS_PATH + config.getAppId() + "?" + paramString;
        String signature = sign(signSource, config.getSecretKey());
        return "wss://" + WS_HOST + WS_PATH + config.getAppId()
                + "?" + paramString + "&signature=" + urlEncode(signature);
    }

    private static String sign(String source, String secretKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] raw = mac.doFinal(source.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(raw, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "SOE 签名失败", e);
            return "";
        }
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private void notifyError(String message) {
        active = false;
        Listener l = listener;
        if (l != null) {
            l.onError(message);
        }
    }
}
