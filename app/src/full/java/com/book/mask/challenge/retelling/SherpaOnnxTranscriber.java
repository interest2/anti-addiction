package com.book.mask.challenge.retelling;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineStream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基于 Sherpa-ONNX 的离线语音识别实现。
 *
 * <p>开发阶段模型放在 assets/asr/paraformer/（model.int8.onnx + tokens.txt）；
 * 正式版可改为下载到 filesDir 后从绝对路径加载。模型缺失时 {@link #isReady()} 返回 false，
 * 识别返回失败结果，由上层提示「修复模型」，不会崩溃。
 *
 * <p>生命周期：单线程初始化一次、多次复用；识别放工作线程；APP 退出时 {@link #release()}。
 */
public final class SherpaOnnxTranscriber implements SpeechTranscriber {

    private static final String TAG = "SherpaOnnx";

    private static final String MODEL_ASSET_DIR = "asr/paraformer";
    private static final String MODEL_FILE = "model.int8.onnx";
    private static final String TOKENS_FILE = "tokens.txt";
    private static final int SAMPLE_RATE = 16000;

    private static volatile SherpaOnnxTranscriber instance;

    public static SherpaOnnxTranscriber getInstance(Context context) {
        if (instance == null) {
            synchronized (SherpaOnnxTranscriber.class) {
                if (instance == null) {
                    instance = new SherpaOnnxTranscriber(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /** 进程退出或彻底不再使用语音识别时释放单例，方便下次重新初始化（模型加载失败可重试）。 */
    public static void releaseInstance() {
        SherpaOnnxTranscriber current = instance;
        instance = null;
        if (current != null) {
            current.release();
        }
    }

    private final Context context;
    private final Object initLock = new Object();
    private volatile OfflineRecognizer recognizer;
    private volatile boolean ready;
    private volatile String modelPath;
    private volatile String tokensPath;

    private SherpaOnnxTranscriber(Context context) {
        this.context = context;
    }

    @Override
    public boolean isReady() {
        ensureLoaded();
        return ready;
    }

    @Override
    public TranscriptionResult transcribe(float[] samples, int sampleRate) {
        ensureLoaded();
        OfflineRecognizer current = recognizer;
        if (current == null || !ready) {
            return TranscriptionResult.failure("ASR 模型缺失，请先修复模型");
        }
        if (samples == null || samples.length == 0) {
            return TranscriptionResult.failure("没有录到有效语音");
        }

        OfflineStream stream = null;
        try {
            stream = current.createStream();
            stream.acceptWaveform(samples, sampleRate > 0 ? sampleRate : SAMPLE_RATE);
            current.decode(stream);
            OfflineRecognizerResult result = current.getResult(stream);
            String text = result.getText();
            text = text == null ? "" : text.trim();
            List<TranscriptionResult.TimedToken> timedTokens = extractTimedTokens(result);
            Log.d(TAG, "识别完成，文本长度=" + text.length()
                    + "，时间戳 token=" + timedTokens.size());
            return TranscriptionResult.success(text, timedTokens);
        } catch (Throwable e) {
            Log.e(TAG, "识别失败", e);
            return TranscriptionResult.failure("识别失败：" + e.getMessage());
        } finally {
            if (stream != null) {
                try {
                    stream.release();
                } catch (Throwable ignored) {
                    // 释放失败不影响结果
                }
            }
        }
    }

    private static List<TranscriptionResult.TimedToken> extractTimedTokens(
            OfflineRecognizerResult result) {
        String[] tokens = result.getTokens();
        float[] timestamps = result.getTimestamps();
        if (tokens == null || timestamps == null
                || tokens.length == 0 || timestamps.length != tokens.length) {
            return Collections.emptyList();
        }
        float[] durations = result.getDurations();
        List<TranscriptionResult.TimedToken> timedTokens = new ArrayList<>(tokens.length);
        for (int index = 0; index < tokens.length; index++) {
            float start = timestamps[index];
            if (!Float.isFinite(start) || start < 0f) {
                continue;
            }
            float end = start;
            if (durations != null && durations.length == tokens.length
                    && Float.isFinite(durations[index]) && durations[index] > 0f) {
                end += durations[index];
            } else if (index + 1 < timestamps.length
                    && Float.isFinite(timestamps[index + 1])
                    && timestamps[index + 1] > start) {
                end = timestamps[index + 1];
            }
            timedTokens.add(new TranscriptionResult.TimedToken(tokens[index], start, end));
        }
        return timedTokens;
    }

    @Override
    public void release() {
        synchronized (initLock) {
            OfflineRecognizer current = recognizer;
            recognizer = null;
            ready = false;
            if (current != null) {
                try {
                    current.release();
                } catch (Throwable e) {
                    Log.w(TAG, "释放识别器异常", e);
                }
            }
        }
    }

    /**
     * 加载失败可重试：每次 isReady()/transcribe() 都会尝试加载，模型文件补齐后即可恢复。
     *
     * <p>注意：Sherpa 原生层在读取不到模型文件时会直接 assert 终止进程（Java 异常拦不住），
     * 因此构造 {@link OfflineRecognizer} 之前必须先确认模型与词表文件确实存在。
     */
    private void ensureLoaded() {
        if (ready) {
            return;
        }
        synchronized (initLock) {
            if (ready) {
                return;
            }
            resolveModelPaths();
            if (!modelFilesExist()) {
                recognizer = null;
                ready = false;
                return;
            }
            try {
                AssetManager assetManager = context.getAssets();
                FeatureConfig featureConfig = new FeatureConfig();
                featureConfig.setSampleRate(SAMPLE_RATE);
                featureConfig.setFeatureDim(80);

                OfflineParaformerModelConfig paraformer = new OfflineParaformerModelConfig();
                paraformer.setModel(modelPath);

                OfflineModelConfig modelConfig = new OfflineModelConfig();
                modelConfig.setParaformer(paraformer);
                modelConfig.setTokens(tokensPath);
                modelConfig.setNumThreads(2);
                modelConfig.setDebug(false);
                modelConfig.setProvider("cpu");

                OfflineRecognizerConfig recognizerConfig = new OfflineRecognizerConfig();
                recognizerConfig.setFeatConfig(featureConfig);
                recognizerConfig.setModelConfig(modelConfig);
                recognizerConfig.setDecodingMethod("greedy_search");

                recognizer = new OfflineRecognizer(assetManager, recognizerConfig);
                ready = true;
                Log.d(TAG, "ASR 模型加载成功");
            } catch (Throwable e) {
                // 模型缺失时只记一行告警，避免反复打完整堆栈刷屏
                Log.w(TAG, "ASR 模型加载失败，model=" + modelPath
                        + "，原因=" + e.getClass().getSimpleName() + ": " + e.getMessage());
                recognizer = null;
                ready = false;
            }
        }
    }

    /**
     * 构造识别器前确认模型与词表都存在：filesDir 绝对路径用 File 检查，assets 路径用 AssetManager 探测。
     * 避免 Sherpa 原生层因文件缺失直接 assert 崩溃。
     */
    private boolean modelFilesExist() {
        if (modelPath.startsWith("/")) {
            return new File(modelPath).exists() && new File(tokensPath).exists();
        }
        try {
            InputStream model = context.getAssets().open(modelPath);
            model.close();
            InputStream tokens = context.getAssets().open(tokensPath);
            tokens.close();
            return true;
        } catch (IOException e) {
            Log.w(TAG, "ASR 模型文件缺失: " + modelPath + " / " + tokensPath);
            return false;
        }
    }

    /**
     * 优先 filesDir（正式版下载路径），其次 assets（开发阶段路径）。两者都缺失时返回资产路径，
     * 构造识别器时会失败并置为不可用，由上层提示修复模型。
     */
    private void resolveModelPaths() {
        File filesModel = new File(new File(new File(
                context.getFilesDir(), "asr"), "paraformer"), MODEL_FILE);
        File filesTokens = new File(new File(new File(
                context.getFilesDir(), "asr"), "paraformer"), TOKENS_FILE);
        if (filesModel.exists() && filesTokens.exists()) {
            modelPath = filesModel.getAbsolutePath();
            tokensPath = filesTokens.getAbsolutePath();
            return;
        }
        modelPath = MODEL_ASSET_DIR + "/" + MODEL_FILE;
        tokensPath = MODEL_ASSET_DIR + "/" + TOKENS_FILE;
    }
}
