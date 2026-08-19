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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    /** 单次 TASK_REQUEST 的文本上限（豆包 BiTTS 限制）。 */
    private static final int MAX_TASK_TEXT_LENGTH = 80;
    /** 句末标点：优先在此切分，保证每片听感自然。 */
    private static final String SENTENCE_ENDINGS = "。！？；!?;\n";
    /** 句中标点：整句仍超长时的次级切点。 */
    private static final String CLAUSE_ENDINGS = "，、：,:";

    public interface Callback {
        /**
         * 每合成好一句就回调一次（分段音频文件已落盘、可直接播放），供上层「边合成边播」。
         * 同一分段只回调一次；最终 {@link #onAudioReady} 汇总的列表与此处分段一致，上层需按路径去重。
         */
        void onSegmentReady(File segment);

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
    private List<String> pendingTexts = Collections.emptyList();
    private ByteArrayOutputStream sentenceAudioBuffer;
    private final List<File> sentenceAudioFiles = new ArrayList<>();
    private boolean audioDelivered;
    private boolean sessionActive;
    private boolean sessionFinished;
    private boolean dumpFinished;
    private long synthesisSequence;
    private String audioFilePrefix;
    private Map<String, AudioFingerprint> audiosBeforeSynthesis = new HashMap<>();

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
    public synchronized void synthesizeNewSequence(String text, Callback cb) {
        synthesize(text, cb, true);
    }

    /** 在当前已启动的引擎上直接创建下一次合成会话。 */
    public synchronized void synthesizeNext(String text, Callback cb) {
        synthesize(text, cb, false);
    }

    private void synthesize(String text, Callback cb, boolean newSequence) {
        if (!config.isConfigured()) {
            Log.w(TAG, "豆包语音凭据未配置，回退占位音频");
            postUnavailable(cb, "未配置豆包语音凭据");
            return;
        }
        if (text == null || text.trim().isEmpty()) {
            postError(cb, "合成文本为空");
            return;
        }
        if (sessionActive) {
            postError(cb, "上一次豆包语音会话尚未结束");
            return;
        }
        this.callback = cb;
        this.sentenceAudioBuffer = new ByteArrayOutputStream();
        this.sentenceAudioFiles.clear();
        this.audioDelivered = false;
        this.sessionFinished = false;
        this.dumpFinished = false;
        try {
            if (!ensureEngine()) {
                postUnavailable("豆包语音引擎初始化失败");
                return;
            }
            // 单次 TASK_REQUEST 受 80 字限制，但同一会话可续送多次：
            // 长文本按标点分片逐段送出，不再截断丢字
            List<String> ttsTexts = splitForSynthesis(text.trim());
            if (ttsTexts.isEmpty()) {
                postError("合成文本为空");
                return;
            }
            if (newSequence) {
                clearOldAudios();
            }
            audioFilePrefix = "tts_" + System.currentTimeMillis() + "_" + (++synthesisSequence);
            audiosBeforeSynthesis = snapshotAudios();
            pendingTexts = ttsTexts;
            if (engineStarted) {
                sendPendingTask();
            } else {
                startEngine();
            }
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
        pendingTexts = Collections.emptyList();
        sentenceAudioBuffer = null;
        callback = null;
        audioDelivered = true;
        sessionActive = false;
        sessionFinished = false;
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
        pendingTexts = Collections.emptyList();
        sentenceAudioBuffer = null;
        sessionActive = false;
        sessionFinished = false;
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
        int ret = engine.sendDirective(
                SpeechEngineDefines.DIRECTIVE_START_ENGINE, buildStartPayload());
        if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
            Log.e(TAG, "启动引擎失败 ret=" + ret);
            pendingTexts = Collections.emptyList();
            postError("启动引擎失败：" + ret);
        }
    }

    /** 引擎启动成功后发送本次合成任务；长文本在同一会话内分多片续送。 */
    private synchronized void sendPendingTask() {
        List<String> texts = pendingTexts;
        if (!engineStarted || texts.isEmpty()) {
            return;
        }
        pendingTexts = Collections.emptyList();
        try {
            int ret = engine.sendDirective(SpeechEngineDefines.DIRECTIVE_EVENT_START_SESSION, "");
            if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
                Log.e(TAG, "启动会话失败 ret=" + ret);
                postError("启动会话失败：" + ret);
                return;
            }
            sessionActive = true;
            for (int i = 0; i < texts.size(); i++) {
                ret = engine.sendDirective(SpeechEngineDefines.DIRECTIVE_EVENT_TASK_REQUEST,
                        buildTaskPayload(texts.get(i)));
                if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
                    Log.e(TAG, "发送合成文本失败 ret=" + ret
                            + ", 分片=" + (i + 1) + "/" + texts.size());
                    postError("发送合成文本失败：" + ret);
                    return;
                }
            }
            Log.d(TAG, "合成文本已全部送出，分片=" + texts.size());
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

    /**
     * 把待合成文本切成不超过 {@link #MAX_TASK_TEXT_LENGTH} 字的分片，在同一会话内分多次
     * TASK_REQUEST 送出（BiTTS 双向流式本就支持续送文本），避免长原文被截断丢字。
     *
     * <p>切点优先取句末标点，整句仍超长时退到句中标点，再不行才按长度硬切；相邻短片会
     * 贪心合并到上限以内，减少分片数量。
     */
    private static List<String> splitForSynthesis(String text) {
        List<String> pieces = new ArrayList<>();
        for (String sentence : splitAt(text, SENTENCE_ENDINGS)) {
            if (sentence.length() <= MAX_TASK_TEXT_LENGTH) {
                pieces.add(sentence);
                continue;
            }
            for (String clause : splitAt(sentence, CLAUSE_ENDINGS)) {
                addWithHardWrap(pieces, clause);
            }
        }
        return mergeAdjacent(pieces);
    }

    /** 按给定标点集切分，标点保留在前一片末尾；空白片丢弃。 */
    private static List<String> splitAt(String text, String delimiters) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (delimiters.indexOf(text.charAt(i)) < 0) {
                continue;
            }
            addIfNotBlank(parts, text.substring(start, i + 1));
            start = i + 1;
        }
        addIfNotBlank(parts, text.substring(start));
        return parts;
    }

    /** 无标点可切的超长片：按上限硬切，保证不丢字。 */
    private static void addWithHardWrap(List<String> pieces, String clause) {
        for (int start = 0; start < clause.length(); start += MAX_TASK_TEXT_LENGTH) {
            int end = Math.min(start + MAX_TASK_TEXT_LENGTH, clause.length());
            addIfNotBlank(pieces, clause.substring(start, end));
        }
    }

    /** 贪心合并相邻分片到上限以内，减少 TASK_REQUEST 次数。 */
    private static List<String> mergeAdjacent(List<String> pieces) {
        List<String> merged = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String piece : pieces) {
            if (current.length() > 0
                    && current.length() + piece.length() > MAX_TASK_TEXT_LENGTH) {
                merged.add(current.toString());
                current.setLength(0);
            }
            current.append(piece);
        }
        if (current.length() > 0) {
            merged.add(current.toString());
        }
        return merged;
    }

    private static void addIfNotBlank(List<String> parts, String part) {
        if (!part.trim().isEmpty()) {
            parts.add(part);
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
                saveSentenceAudio();
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_EVENT_SESSION_FINISHED:
                Log.d(TAG, "豆包语音会话结束");
                synchronized (this) {
                    sessionActive = false;
                    sessionFinished = true;
                }
                deliverSentenceAudios();
                if (dumpFinished) {
                    deliverDumpedAudio(true);
                }
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_TTS_FINISH_AUDIO_DUMP:
                Log.d(TAG, "豆包语音音频落盘完成");
                synchronized (this) {
                    dumpFinished = true;
                }
                if (sessionFinished) {
                    deliverDumpedAudio(true);
                }
                break;
            case SpeechEngineDefines.MESSAGE_TYPE_ENGINE_ERROR:
                Log.e(TAG, "豆包语音引擎错误: " + (message.isEmpty() ? "engine error" : message));
                synchronized (this) {
                    pendingTexts = Collections.emptyList();
                    sentenceAudioBuffer = null;
                    sessionActive = false;
                    sessionFinished = false;
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
        File file = new File(
                audioDir, audioFilePrefix + "_segment_" + segmentNumber + ".ogg");
        try (FileOutputStream output = new FileOutputStream(file)) {
            sentenceAudioBuffer.writeTo(output);
            sentenceAudioFiles.add(file);
            sentenceAudioBuffer = new ByteArrayOutputStream();
            Log.d(TAG, "豆包语音分段就绪: " + segmentNumber + ", bytes=" + file.length());
            // 分段落盘即回调，供上层边合成边播；不清空 callback，会话结束再由 onAudioReady 收尾
            postSegmentReady(file);
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
        File dumpedFile = findDumpedAudio();
        if (dumpedFile != null) {
            File file = copyDumpedAudio(dumpedFile);
            if (file == null) {
                postError("复制音频文件失败");
                return;
            }
            audioDelivered = true;
            sentenceAudioBuffer = null;
            Log.d(TAG, "豆包语音音频就绪: " + file.getName() + ", bytes=" + file.length());
            postAudioReady(Collections.singletonList(file));
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
        File newest = null;
        for (File file : audioFiles) {
            AudioFingerprint oldFingerprint = audiosBeforeSynthesis.get(file.getAbsolutePath());
            if (oldFingerprint != null && oldFingerprint.matches(file)) {
                continue;
            }
            if (newest == null || file.lastModified() > newest.lastModified()) {
                newest = file;
            }
        }
        return newest != null && newest.exists() && newest.length() > 0 ? newest : null;
    }

    private Map<String, AudioFingerprint> snapshotAudios() {
        Map<String, AudioFingerprint> snapshot = new HashMap<>();
        for (File file : listDumpedAudios()) {
            snapshot.put(file.getAbsolutePath(), new AudioFingerprint(file));
        }
        return snapshot;
    }

    private File copyDumpedAudio(File source) {
        String extension = source.getName().endsWith(".wav") ? ".wav" : ".ogg";
        File target = new File(audioDir, audioFilePrefix + "_dump" + extension);
        if (source.equals(target)) {
            return target;
        }
        byte[] buffer = new byte[8192];
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return target.length() > 0 ? target : null;
        } catch (IOException e) {
            Log.e(TAG, "复制豆包语音 dump 失败", e);
            return null;
        }
    }

    private File[] listDumpedAudios() {
        File[] audioFiles = audioDir.listFiles((dir, name) ->
                name.endsWith(".wav") || name.endsWith(".ogg"));
        return audioFiles == null ? new File[0] : audioFiles;
    }

    private static final class AudioFingerprint {
        private final long length;
        private final long lastModified;

        private AudioFingerprint(File file) {
            length = file.length();
            lastModified = file.lastModified();
        }

        private boolean matches(File file) {
            return length == file.length() && lastModified == file.lastModified();
        }
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

    /** 单个分段就绪回调（会话进行中多次触发，不清空 callback）。 */
    private void postSegmentReady(final File segment) {
        Callback cb;
        synchronized (this) {
            cb = callback;
        }
        if (cb != null && segment != null) {
            mainHandler.post(() -> cb.onSegmentReady(segment));
        }
    }

    private void postAudioReady(final List<File> files) {
        Callback cb;
        synchronized (this) {
            cb = callback;
            callback = null;
        }
        if (cb != null) {
            mainHandler.post(() -> cb.onAudioReady(files));
        }
    }

    private void postError(final String message) {
        Callback cb;
        boolean stopActiveSession;
        synchronized (this) {
            cb = callback;
            if (cb == null) {
                return;
            }
            callback = null;
            pendingTexts = Collections.emptyList();
            sentenceAudioBuffer = null;
            audioDelivered = true;
            stopActiveSession = sessionActive;
            sessionActive = false;
            sessionFinished = false;
            if (stopActiveSession) {
                engineStarted = false;
            }
        }
        if (stopActiveSession && engine != null && engineInited) {
            try {
                engine.sendDirective(SpeechEngineDefines.DIRECTIVE_STOP_ENGINE, "");
            } catch (Throwable ignored) {
                // 失败会话已终止，上层仍需及时收到错误回调
            }
        }
        mainHandler.post(() -> cb.onError(message));
    }

    private void postError(final Callback cb, final String message) {
        if (cb != null) {
            mainHandler.post(() -> cb.onError(message));
        }
    }

    private void postUnavailable(final String reason) {
        Callback cb;
        synchronized (this) {
            cb = callback;
            if (cb == null) {
                return;
            }
            callback = null;
            pendingTexts = Collections.emptyList();
            sentenceAudioBuffer = null;
            audioDelivered = true;
            sessionActive = false;
            sessionFinished = false;
        }
        mainHandler.post(() -> cb.onUnavailable(reason));
    }

    private void postUnavailable(final Callback cb, final String reason) {
        if (cb != null) {
            mainHandler.post(() -> cb.onUnavailable(reason));
        }
    }
}
