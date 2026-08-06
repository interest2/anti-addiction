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

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 豆包语音合成（火山引擎 Speech SDK · 双向流式 TTS，BiTTS）封装。
 *
 * <p>按官方 demo（SpeechDemoAndroid / BiTtsActivity）与文档接入：引擎名 {@code BITTS_ENGINE}，
 * 鉴权用 API Key，接口为 {@code /api/v3/tts/bidirection}。流程：
 * {@code DIRECTIVE_START_ENGINE}（携带 speaker 的 startSessionPayload）→
 * {@code DIRECTIVE_EVENT_START_SESSION} → {@code DIRECTIVE_EVENT_TASK_REQUEST}（携带文本）→
 * {@code DIRECTIVE_EVENT_FINISH_SESSION}。通过 {@code MESSAGE_TYPE_EVENT_TTS_RESPONSE} 接收
 * Ogg/Opus 音频分片，在 {@code MESSAGE_TYPE_EVENT_SESSION_FINISHED} 后汇总落盘并交由上层播放。
 *
 * <p>未配置凭据、或当前 ABI（SDK 仅支持 arm 架构）不支持时回调 {@link Callback#onUnavailable}，
 * 上层回退占位音频，不应让听力题卡死。
 */
public final class DoubaoSpeechSynthesizer implements SpeechEngine.SpeechListener {

    private static final String TAG = "DoubaoTts";
    private static final String TTS_ADDRESS = "wss://openspeech.bytedance.com";
    private static final String TTS_URI = "/api/v3/tts/bidirection";

    public interface Callback {
        void onAudioReady(List<File> audioFiles);

        void onError(String message);

        void onUnavailable(String reason);
    }

    private final Context context;
    private final DoubaoTtsConfigManager config;
    private final File audioDir;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SpeechEngine engine;
    private boolean engineInited;
    private boolean engineStarted;
    private Callback callback;
    private String pendingText;
    private ByteArrayOutputStream sentenceAudioBuffer;
    private final List<File> sentenceAudioFiles = new ArrayList<>();
    private boolean audioDelivered;

    public DoubaoSpeechSynthesizer(Context context) {
        this.context = context.getApplicationContext();
        this.config = new DoubaoTtsConfigManager(context);
        this.audioDir = new File(context.getCacheDir(), "doubao_tts");
        //noinspection ResultOfMethodCallIgnored
        audioDir.mkdirs();
    }

    /**
     * 合成一段文本。结果（按句分段的音频文件 / 错误 / 不可用）通过回调在主线程返回。
     */
    public synchronized void synthesize(String text, Callback cb) {
        if (!config.isConfigured()) {
            Log.w(TAG, "豆包语音凭据未配置，回退占位音频");
            postUnavailable(cb, "未配置豆包语音凭据");
            return;
        }
        if (text == null || text.trim().isEmpty()) {
            postError(cb, "合成文本为空");
            return;
        }
        this.callback = cb;
        this.sentenceAudioBuffer = new ByteArrayOutputStream();
        this.sentenceAudioFiles.clear();
        this.audioDelivered = false;
        try {
            if (!ensureEngine()) {
                postUnavailable("豆包语音引擎初始化失败");
                return;
            }
            // 双向流式 TTS 单次合成文本也受 80 字限制；超长时截断保证合成可用，原文仍完整展示
            String ttsText = text.trim();
            if (ttsText.length() > 80) {
                Log.w(TAG, "合成文本超过 80 字，截断至 80 字");
                ttsText = ttsText.substring(0, 80);
            }
            clearOldAudios();
            pendingText = ttsText;
            startEngine();
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
        pendingText = null;
        sentenceAudioBuffer = null;
        engineStarted = false;
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
        pendingText = null;
        sentenceAudioBuffer = null;
        engineStarted = false;
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
        applyEngineParams();
        engine.setContext(context);
        int ret = engine.initEngine();
        if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
            Log.e(TAG, "初始化豆包语音引擎失败, ret=" + ret);
            return false;
        }
        engine.setListener(this);
        engineInited = true;
        return true;
    }

    /** 引擎级参数：对齐 BiTtsActivity configInitParams 中在线合成相关配置。 */
    private void applyEngineParams() {
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_ENGINE_NAME_STRING,
                SpeechEngineDefines.BITTS_ENGINE);
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_DEBUG_PATH_STRING,
                audioDir.getAbsolutePath());
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_LOG_LEVEL_STRING,
                SpeechEngineDefines.LOG_LEVEL_ERROR);
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_UID_STRING, deviceId());
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_DEVICE_ID_STRING, deviceId());
        // 双向流式 TTS 鉴权：API Key（可包名授权，控制台>API Key）
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_API_KEY_STRING, config.getApiKey());
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_ADDRESS_STRING, TTS_ADDRESS);
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_URI_STRING, TTS_URI);
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_RESOURCE_ID_STRING,
                config.getResourceId());
        engine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_ENABLE_WS_RECONNECT_BOOL, true);
        // 保留 SDK dump 作为兼容兜底；BiTTS 主路径直接汇总 3010 回调的 Ogg/Opus 分片
        engine.setOptionString(SpeechEngineDefines.PARAMS_KEY_TTS_AUDIO_PATH_STRING,
                audioDir.getAbsolutePath());
        engine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_TTS_ENABLE_DUMP_BOOL, true);
        // 关闭 SDK 内置播放器，由上层在用户点击「播放 / 重听」时播放落盘音频
        engine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_TTS_ENABLE_PLAYER_BOOL, false);
        // BiTTS 初始化会强制读取该 option（缺失则 Init 报 "Failed to get option: enable_player_audio_callback"）；
        // 用落盘播放，无需 PCM 回调，置 false
        engine.setOptionBoolean(SpeechEngineDefines.PARAMS_KEY_ENABLE_PLAYER_AUDIO_CALLBACK_BOOL, false);
    }

    /** 启动双向流式引擎；必须等待 ENGINE_START 回调后才能发送合成任务。 */
    private void startEngine() throws Exception {
        int ret = engine.sendDirective(SpeechEngineDefines.DIRECTIVE_SYNC_STOP_ENGINE, "");
        if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
            Log.w(TAG, "同步停止上一次合成返回 ret=" + ret);
        }
        engineStarted = false;
        ret = engine.sendDirective(SpeechEngineDefines.DIRECTIVE_START_ENGINE, buildStartPayload());
        if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
            Log.e(TAG, "启动引擎失败 ret=" + ret);
            pendingText = null;
            postError("启动引擎失败：" + ret);
        }
    }

    /** 引擎启动成功后发送本次合成任务。 */
    private synchronized void sendPendingTask() {
        String text = pendingText;
        if (!engineStarted || text == null) {
            return;
        }
        pendingText = null;
        try {
            int ret = engine.sendDirective(SpeechEngineDefines.DIRECTIVE_EVENT_START_SESSION, "");
            if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
                Log.e(TAG, "启动会话失败 ret=" + ret);
                postError("启动会话失败：" + ret);
                return;
            }
            ret = engine.sendDirective(SpeechEngineDefines.DIRECTIVE_EVENT_TASK_REQUEST,
                    buildTaskPayload(text));
            if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
                Log.e(TAG, "发送合成文本失败 ret=" + ret);
                postError("发送合成文本失败：" + ret);
                return;
            }
            ret = engine.sendDirective(SpeechEngineDefines.DIRECTIVE_EVENT_FINISH_SESSION, "");
            if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
                Log.e(TAG, "结束会话失败 ret=" + ret);
                postError("结束会话失败：" + ret);
            }
        } catch (Throwable t) {
            Log.e(TAG, "发送豆包语音合成任务异常", t);
            postError("发送合成任务异常：" + t.getMessage());
        }
    }

    /** startSessionPayload：携带发音人 speaker 与音量等音频参数。 */
    private String buildStartPayload() throws Exception {
        JSONObject payload = new JSONObject();
        JSONObject user = new JSONObject();
        user.put("uid", deviceId());
        payload.put("user", user);
        JSONObject reqParams = new JSONObject();
        reqParams.put("speaker", config.getSpeaker());
        JSONObject audioParams = new JSONObject();
        audioParams.put("loudness_rate", 50);
        reqParams.put("audio_params", audioParams);
        payload.put("req_params", reqParams);
        return payload.toString();
    }

    /** 单次合成文本的 task payload。 */
    private String buildTaskPayload(String text) throws Exception {
        JSONObject payload = new JSONObject();
        JSONObject reqParams = new JSONObject();
        reqParams.put("text", text);
        payload.put("req_params", reqParams);
        return payload.toString();
    }

    // ===== SDK 回调 =====

    @Override
    public void onSpeechMessage(int type, byte[] data, int len) {
        if (type == SpeechEngineDefines.MESSAGE_TYPE_EVENT_TTS_RESPONSE) {
            appendAudio(data, len);
            return;
        }
        String message = len > 0 ? new String(data, 0, len) : "";
        switch (type) {
            case SpeechEngineDefines.MESSAGE_TYPE_ENGINE_START:
                Log.d(TAG, "豆包语音引擎启动成功");
                synchronized (this) {
                    engineStarted = true;
                }
                sendPendingTask();
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_ENGINE_STOP:
                synchronized (this) {
                    engineStarted = false;
                }
                Log.d(TAG, "豆包语音引擎已停止");
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_EVENT_TTS_SENTENCE_START:
                Log.d(TAG, "豆包语音开始合成: " + message);
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_EVENT_TTS_SENTENCE_END:
                Log.d(TAG, "豆包语音合成结束: " + message);
                saveSentenceAudio();
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_EVENT_TTS_ENDED:
                Log.d(TAG, "豆包语音流结束: " + message);
                deliverSentenceAudios();
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_EVENT_SESSION_FINISHED:
                Log.d(TAG, "豆包语音会话结束");
                deliverSentenceAudios();
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_TTS_FINISH_AUDIO_DUMP:
                Log.d(TAG, "豆包语音音频落盘完成");
                deliverDumpedAudio(true);
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_ENGINE_ERROR:
                Log.e(TAG, "豆包语音引擎错误: " + (message.isEmpty() ? "engine error" : message));
                synchronized (this) {
                    pendingText = null;
                    sentenceAudioBuffer = null;
                    engineStarted = false;
                }
                postError(message.isEmpty() ? "engine error" : message);
                break;
            default:
                Log.v(TAG, "豆包语音回调 type=" + type + ", bytes=" + len);
                break;
        }
    }

    @Override
    public void onSpeechLogid(String logid) {
        Log.d(TAG, "豆包语音 logid=" + logid);
    }

    private synchronized void appendAudio(byte[] data, int len) {
        if (audioDelivered || sentenceAudioBuffer == null || data == null || len <= 0) {
            return;
        }
        sentenceAudioBuffer.write(data, 0, Math.min(len, data.length));
    }

    private synchronized void saveSentenceAudio() {
        if (sentenceAudioBuffer == null || sentenceAudioBuffer.size() == 0) {
            return;
        }
        int segmentNumber = sentenceAudioFiles.size() + 1;
        File file = new File(audioDir, "tts_segment_" + segmentNumber + ".ogg");
        try (FileOutputStream output = new FileOutputStream(file)) {
            sentenceAudioBuffer.writeTo(output);
            sentenceAudioFiles.add(file);
            sentenceAudioBuffer = new ByteArrayOutputStream();
            Log.d(TAG, "豆包语音分段就绪: " + segmentNumber + ", bytes=" + file.length());
        } catch (IOException e) {
            Log.e(TAG, "保存豆包语音分段失败", e);
            postError("保存音频失败：" + e.getMessage());
        }
    }

    private synchronized void deliverSentenceAudios() {
        if (audioDelivered) {
            return;
        }
        saveSentenceAudio();
        if (sentenceAudioFiles.isEmpty()) {
            return;
        }
        audioDelivered = true;
        sentenceAudioBuffer = null;
        List<File> files = new ArrayList<>(sentenceAudioFiles);
        long totalBytes = 0;
        for (File file : files) {
            totalBytes += file.length();
        }
        Log.d(TAG, "豆包语音音频就绪: segments=" + files.size() + ", bytes=" + totalBytes);
        postAudioReady(files);
    }

    private synchronized void deliverDumpedAudio(boolean dumpFinished) {
        if (audioDelivered) {
            return;
        }
        File file = findDumpedAudio();
        if (file != null) {
            audioDelivered = true;
            sentenceAudioBuffer = null;
            Log.d(TAG, "豆包语音音频就绪: " + file.getName() + ", bytes=" + file.length());
            postAudioReady(java.util.Collections.singletonList(file));
        } else if (dumpFinished) {
            deliverSentenceAudios();
            if (!audioDelivered) {
                Log.e(TAG, "豆包语音音频落盘完成但文件未生成");
                postError("音频文件未生成");
            }
        }
    }

    /** 清空目录内旧合成音频，避免与本次结果混淆。 */
    private void clearOldAudios() {
        File[] audioFiles = listDumpedAudios();
        for (File file : audioFiles) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    /** 查找本次合成落盘文件；优先兼容 SDK 的 wav dump，也支持本地汇总的 Ogg。 */
    private File findDumpedAudio() {
        File[] audioFiles = listDumpedAudios();
        if (audioFiles.length == 0) {
            return null;
        }
        File newest = audioFiles[0];
        for (File file : audioFiles) {
            if (file.lastModified() > newest.lastModified()) {
                newest = file;
            }
        }
        return newest.exists() && newest.length() > 0 ? newest : null;
    }

    private File[] listDumpedAudios() {
        File[] audioFiles = audioDir.listFiles((dir, name) ->
                name.endsWith(".wav") || name.endsWith(".ogg"));
        return audioFiles == null ? new File[0] : audioFiles;
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

    private void postAudioReady(final List<File> files) {
        mainHandler.post(() -> {
            Callback cb = callback;
            if (cb != null) {
                cb.onAudioReady(files);
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

    private void postError(final Callback cb, final String message) {
        if (cb != null) {
            mainHandler.post(() -> cb.onError(message));
        }
    }

    private void postUnavailable(final String reason) {
        mainHandler.post(() -> {
            Callback cb = callback;
            if (cb != null) {
                cb.onUnavailable(reason);
            }
        });
    }

    private void postUnavailable(final Callback cb, final String reason) {
        if (cb != null) {
            mainHandler.post(() -> cb.onUnavailable(reason));
        }
    }
}
