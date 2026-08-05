package com.book.mask.challenge.listening;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import com.book.mask.personalize.DoubaoTtsConfigManager;
import com.bytedance.speech.speechengine.SpeechEngine;
import com.bytedance.speech.speechengine.SpeechEngineDefines;
import com.bytedance.speech.speechengine.SpeechEngineGenerator;

import java.io.File;
import java.util.UUID;

/**
 * 豆包语音合成（火山引擎 Speech SDK）封装。
 *
 * <p>按官方 demo（SpeechDemoAndroid / TtsNormalActivity）的调用流程接入：
 * {@link SpeechEngineGenerator} 创建引擎 → 配置在线合成参数 → {@code initEngine} → 设置文本与 reqid →
 * {@code DIRECTIVE_START_ENGINE} 发起合成；开启音频落盘（dump）后通过 {@code MESSAGE_TYPE_TTS_FINISH_AUDIO_DUMP}
 * 拿到合成的 wav 文件，交由上层按需播放。
 *
 * <p>未配置凭据、或当前 ABI（SDK 仅支持 arm 架构）不支持时回调 {@link Callback#onUnavailable}，
 * 上层回退占位音频，不应让听力题卡死。
 */
public final class DoubaoSpeechSynthesizer implements SpeechEngine.SpeechListener {

    private static final String TAG = "DoubaoTts";
    private static final String TTS_ADDRESS = "wss://openspeech.bytedance.com";
    private static final String TTS_URI = "/api/v1/tts/ws_binary";

    public interface Callback {
        void onAudioReady(File audioFile);

        void onError(String message);

        void onUnavailable(String reason);
    }

    private final Context context;
    private final DoubaoTtsConfigManager config;
    private final File audioDir;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SpeechEngine engine;
    private boolean engineInited;
    private Callback callback;
    private String currentReqId;

    public DoubaoSpeechSynthesizer(Context context) {
        this.context = context.getApplicationContext();
        this.config = new DoubaoTtsConfigManager(context);
        this.audioDir = new File(context.getCacheDir(), "doubao_tts");
        //noinspection ResultOfMethodCallIgnored
        audioDir.mkdirs();
    }

    /**
     * 合成一段文本。结果（音频文件 / 错误 / 不可用）通过回调在主线程返回。
     */
    public synchronized void synthesize(String text, Callback cb) {
        this.callback = cb;
        if (!config.isConfigured()) {
            Log.w(TAG, "豆包语音凭据未配置，回退占位音频");
            postUnavailable("未配置豆包语音凭据");
            return;
        }
        try {
            if (!ensureEngine()) {
                postUnavailable("豆包语音引擎初始化失败");
                return;
            }
            if (text == null || text.trim().isEmpty()) {
                postError("合成文本为空");
                return;
            }
            // 在线合成单次文本上限 80 字；超长时截断保证合成可用，原文仍完整展示
            String ttsText = text.trim();
            if (ttsText.length() > 80) {
                Log.w(TAG, "合成文本超过 80 字，截断至 80 字");
                ttsText = ttsText.substring(0, 80);
            }
            currentReqId = UUID.randomUUID().toString();
            startSynthesis(ttsText);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "当前设备 ABI 不支持豆包语音 SDK", e);
            postUnavailable("当前设备不支持豆包语音合成");
        } catch (Throwable t) {
            Log.e(TAG, "豆包语音合成异常", t);
            postError("合成异常：" + t.getMessage());
        }
    }

    /** 停止当前合成。 */
    public synchronized void stop() {
        if (engine != null && engineInited) {
            try {
                engine.sendDirective(SpeechEngineDefines.DIRECTIVE_STOP_ENGINE, "");
            } catch (Throwable ignored) {
                // 引擎未就绪时忽略
            }
        }
    }

    /** 释放引擎资源。 */
    public synchronized void destroy() {
        callback = null;
        if (engine != null) {
            try {
                engine.destroyEngine();
            } catch (Throwable ignored) {
                // 释放时忽略异常
            }
            engine = null;
            engineInited = false;
        }
    }

    // ===== 引擎初始化与合成 =====

    private boolean ensureEngine() {
        if (engine != null && engineInited) {
            return true;
        }
        if (engine == null) {
            engine = SpeechEngineGenerator.getInstance();
            engine.createEngine();
        }
        engine.setContext(context);
        engine.setListener(this);
        applyEngineParams();
        int ret = engine.initEngine();
        if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
            Log.e(TAG, "初始化豆包语音引擎失败, ret=" + ret);
            return false;
        }
        engineInited = true;
        return true;
    }

    /** 引擎级参数：对齐 demo 的 configInitParams 中在线合成相关配置。 */
    private void applyEngineParams() {
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_ENGINE_NAME_STRING,
                SpeechEngineDefines.TTS_ENGINE);
        engine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_TTS_WORK_MODE_INT,
                SpeechEngineDefines.TTS_WORK_MODE_ONLINE);
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_DEBUG_PATH_STRING,
                audioDir.getAbsolutePath());
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_LOG_LEVEL_STRING,
                SpeechEngineDefines.LOG_LEVEL_ERROR);
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_UID_STRING, deviceId());
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_DEVICE_ID_STRING, deviceId());
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_APP_ID_STRING, config.getAppId());
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_APP_TOKEN_STRING, config.getToken());
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_ADDRESS_STRING, TTS_ADDRESS);
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_URI_STRING, TTS_URI);
        engine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_ENABLE_WS_RECONNECT_BOOL, true);
        engine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_TTS_COMPRESSION_RATE_INT, 10);
        // 音频落盘：合成完成后在 audioDir 生成 tts_{reqid}.wav，供上层按需播放
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_AUDIO_PATH_STRING,
                audioDir.getAbsolutePath());
        engine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_TTS_ENABLE_DUMP_BOOL, true);
        // 关闭 SDK 内置播放器，由上层在用户点击「播放 / 重听」时播放落盘的 wav
        engine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_PLAYER_DISABLE_REUSE_BOOL, true);
        engine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_AUDIO_STREAM_TYPE_INT,
                SpeechEngineDefines.AUDIO_STREAM_TYPE_MEDIA);
        engine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_TTS_SAMPLE_RATE_INT, 24000);
    }

    /** 单次合成参数：对齐 demo 的 configStartTtsParams 中在线合成相关配置。 */
    private void startSynthesis(String text) {
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_SCENARIO_STRING,
                SpeechEngineDefines.TTS_SCENARIO_TYPE_NORMAL);
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_TEXT_STRING, text);
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_TEXT_TYPE_STRING,
                SpeechEngineDefines.TTS_TEXT_TYPE_PLAIN);
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_CLUSTER_STRING, config.getCluster());
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_VOICE_ONLINE_STRING, config.getVoice());
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_VOICE_TYPE_ONLINE_STRING,
                config.getVoiceType());
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_REQUEST_ID_STRING, currentReqId);
        engine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_TTS_ENABLE_PLAYER_BOOL, false);
        engine.setOptionInt(SpeechEngineDefines.PARAMS_KEY_TTS_DATA_CALLBACK_MODE_INT,
                SpeechEngineDefines.TTS_DATA_CALLBACK_MODE_NONE);

        int ret = engine.sendDirective(SpeechEngineDefines.DIRECTIVE_SYNC_STOP_ENGINE, "");
        if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
            Log.w(TAG, "同步停止上一次合成返回 ret=" + ret);
        }
        ret = engine.sendDirective(SpeechEngineDefines.DIRECTIVE_START_ENGINE, "");
        if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
            Log.e(TAG, "启动合成失败 ret=" + ret);
            postError("启动合成失败：" + ret);
        }
    }

    // ===== SDK 回调 =====

    @Override
    public void onSpeechMessage(int type, byte[] data, int len) {
        switch (type) {
            case SpeechEngineDefines.MESSAGE_TYPE_TTS_FINISH_AUDIO_DUMP:
                File file = findDumpedAudio();
                if (file != null) {
                    postAudioReady(file);
                } else {
                    postError("音频文件未生成");
                }
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_ENGINE_ERROR:
                String error = len > 0 ? new String(data, 0, len) : "engine error";
                Log.e(TAG, "豆包语音引擎错误: " + error);
                postError(error);
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_ENGINE_START:
            case SpeechEngineDefines.MESSAGE_TYPE_ENGINE_STOP:
            case SpeechEngineDefines.MESSAGE_TYPE_TTS_SYNTHESIS_BEGIN:
            case SpeechEngineDefines.MESSAGE_TYPE_TTS_SYNTHESIS_END:
            default:
                break;
        }
    }

    /** 查找本次合成落盘的 wav 文件；优先按 reqid 精确匹配，兜底取目录中最新 wav。 */
    private File findDumpedAudio() {
        File expected = new File(audioDir, "tts_" + currentReqId + ".wav");
        if (expected.exists() && expected.length() > 0) {
            return expected;
        }
        File[] wavFiles = audioDir.listFiles((dir, name) -> name.endsWith(".wav"));
        if (wavFiles == null || wavFiles.length == 0) {
            return null;
        }
        File newest = wavFiles[0];
        for (File file : wavFiles) {
            if (file.lastModified() > newest.lastModified()) {
                newest = file;
            }
        }
        return newest.exists() && newest.length() > 0 ? newest : null;
    }

    private String deviceId() {
        try {
            String androidId = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ANDROID_ID);
            return androidId == null ? "" : androidId;
        } catch (Exception e) {
            return "";
        }
    }

    private void postAudioReady(final File file) {
        mainHandler.post(() -> {
            Callback cb = callback;
            if (cb != null) {
                cb.onAudioReady(file);
            }
        });
    }

    private void postError(final String message) {
        mainHandler.post(() -> {
            Callback cb = callback;
            if (cb != null) {
                cb.onError(message);
            }
        });
    }

    private void postUnavailable(final String reason) {
        mainHandler.post(() -> {
            Callback cb = callback;
            if (cb != null) {
                cb.onUnavailable(reason);
            }
        });
    }
}
