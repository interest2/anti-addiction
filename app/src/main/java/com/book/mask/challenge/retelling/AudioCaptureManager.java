package com.book.mask.challenge.retelling;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 麦克风 PCM 采集。16kHz / 单声道 / 16bit，VOICE_RECOGNITION 音源。
 *
 * <p>数据只保存在内存，无需生成临时 WAV 文件（180 秒约 5.76MB）。AudioRecord 要求应用主动循环读取，
 * 因此在后台线程读 short[] 片段。录音上限由构造传入（秒），达到上限后自动停止采集
 * （由上层计时器负责触发 {@link #stop(Callback)} 取回采样）。
 */
public final class AudioCaptureManager {

    private static final String TAG = "AudioCapture";
    private static final int SAMPLE_RATE = 16000;

    public interface Callback {
        void onCaptured(float[] samples, CaptureMetrics metrics);

        void onError(String message);
    }

    /** 流式 PCM 片段监听：每读入一块采样即回调，供腾讯口语评测随录随推。 */
    public interface PcmChunkListener {
        void onPcmChunk(short[] chunk, int length);
    }

    /** 本次录音的波形指标，仅用于诊断「有 PCM 但模型输出为空」的场景。 */
    public static final class CaptureMetrics {
        private final int sampleCount;
        private final float peak;
        private final float rms;
        private final float nonSilentPercent;

        private CaptureMetrics(int sampleCount, float peak, float rms, float nonSilentPercent) {
            this.sampleCount = sampleCount;
            this.peak = peak;
            this.rms = rms;
            this.nonSilentPercent = nonSilentPercent;
        }

        public int getSampleCount() {
            return sampleCount;
        }

        public float getDurationSeconds() {
            return sampleCount / (float) SAMPLE_RATE;
        }

        public float getPeak() {
            return peak;
        }

        public float getRms() {
            return rms;
        }

        public float getNonSilentPercent() {
            return nonSilentPercent;
        }

        String toLogMessage() {
            return String.format(
                    Locale.US,
                    "时长=%.3fs，采样数=%d，峰值=%.4f，RMS=%.4f，非静音=%.1f%%",
                    getDurationSeconds(), sampleCount, peak, rms, nonSilentPercent);
        }
    }

    private final Handler handler;
    private final int maxRecordMs;
    private AudioRecord audioRecord;
    private Thread recordingThread;
    private volatile boolean recording;
    private final List<short[]> chunks = new ArrayList<>();
    private int totalSamples;
    private int bufferSize;
    private volatile PcmChunkListener pcmChunkListener;

    public AudioCaptureManager(int maxSeconds) {
        this.handler = new Handler(Looper.getMainLooper());
        this.maxRecordMs = Math.max(1, maxSeconds) * 1000;
    }

    public boolean isRecording() {
        return recording;
    }

    /**
     * 挂接流式 PCM 监听（通常由会话注入腾讯口语评测推流器）。
     * 回调发生在采集线程，实现需保证线程安全。
     */
    public void setPcmChunkListener(PcmChunkListener listener) {
        this.pcmChunkListener = listener;
    }

    /**
     * 开始录音。失败（无权限 / 麦克风被占用 / 初始化失败）返回 false。
     */
    public boolean start() {
        if (recording) {
            return true;
        }
        try {
            int minBuffer = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (minBuffer <= 0) {
                Log.e(TAG, "无法获取最小录音缓冲");
                return false;
            }
            bufferSize = Math.max(minBuffer, SAMPLE_RATE / 10);
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize);
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord 初始化失败");
                release();
                return false;
            }
            chunks.clear();
            totalSamples = 0;
            audioRecord.startRecording();
            recording = true;
            recordingThread = new Thread(this::readLoop, "retelling-audio-capture");
            recordingThread.start();
            Log.d(TAG, "开始录音");
            return true;
        } catch (SecurityException e) {
            Log.e(TAG, "无录音权限", e);
            release();
            return false;
        } catch (Exception e) {
            Log.e(TAG, "启动录音异常", e);
            release();
            return false;
        }
    }

    private void readLoop() {
        short[] buffer = new short[bufferSize];
        long startTime = SystemClock.elapsedRealtime();
        while (recording) {
            int read;
            try {
                read = audioRecord.read(buffer, 0, buffer.length);
            } catch (Exception e) {
                Log.e(TAG, "读取录音数据异常", e);
                break;
            }
            if (read > 0) {
                short[] chunk = new short[read];
                System.arraycopy(buffer, 0, chunk, 0, read);
                synchronized (chunks) {
                    chunks.add(chunk);
                    totalSamples += read;
                }
                PcmChunkListener listener = pcmChunkListener;
                if (listener != null) {
                    listener.onPcmChunk(chunk, read);
                }
            }
            if (SystemClock.elapsedRealtime() - startTime > maxRecordMs) {
                Log.d(TAG, "录音达到上限，停止采集");
                recording = false;
                break;
            }
        }
    }

    /**
     * 停止录音并取回 PCM 浮点采样（回调在主线程）。无有效语音时回调 onError。
     * 超时自动结束（readLoop 已把 recording 置 false）也应正常取回已采集的采样，故不做「未在录音」拦截。
     */
    public void stop(final Callback callback) {
        if (callback == null) {
            return;
        }
        Log.d(TAG, "结束录音");
        recording = false;
        stopAudioRecord();
        joinCaptureThread();
        float[] samples = toFloatSamples();
        recordingThread = null;
        if (samples == null || samples.length == 0) {
            callback.onError("没有录到有效语音");
        } else {
            CaptureMetrics metrics = calculateMetrics(samples);
            Log.d(TAG, "录音采集完成，" + metrics.toLogMessage());
            handler.post(() -> callback.onCaptured(samples, metrics));
        }
    }

    /**
     * 丢弃本次录音数据，不取回采样。离开答题流程时立即释放麦克风。
     */
    public void cancel() {
        Log.d(TAG, "结束录音（取消）");
        recording = false;
        stopAudioRecord();
        joinCaptureThread();
        synchronized (chunks) {
            chunks.clear();
            totalSamples = 0;
        }
        recordingThread = null;
    }

    public void release() {
        if (recording || recordingThread != null) {
            cancel();
        }
        if (audioRecord != null) {
            try {
                audioRecord.release();
            } catch (Exception ignored) {
                // 释放失败可忽略
            }
            audioRecord = null;
        }
    }

    private void joinCaptureThread() {
        Thread thread = recordingThread;
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void stopAudioRecord() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
            } catch (Exception e) {
                Log.w(TAG, "停止录音异常", e);
            }
        }
    }

    private float[] toFloatSamples() {
        final int[] snapshot;
        final List<short[]> copy;
        synchronized (chunks) {
            if (totalSamples == 0) {
                return null;
            }
            copy = new ArrayList<>(chunks);
            snapshot = new int[]{totalSamples};
        }
        float[] result = new float[snapshot[0]];
        int offset = 0;
        for (short[] chunk : copy) {
            for (short sample : chunk) {
                result[offset++] = sample / 32768.0f;
            }
        }
        return result;
    }

    private static CaptureMetrics calculateMetrics(float[] samples) {
        float peak = 0f;
        double sumOfSquares = 0d;
        int nonSilentCount = 0;
        for (float sample : samples) {
            float amplitude = Math.abs(sample);
            peak = Math.max(peak, amplitude);
            sumOfSquares += sample * sample;
            if (amplitude >= 0.01f) {
                nonSilentCount++;
            }
        }
        float rms = (float) Math.sqrt(sumOfSquares / samples.length);
        float nonSilentPercent = nonSilentCount * 100f / samples.length;
        return new CaptureMetrics(samples.length, peak, rms, nonSilentPercent);
    }
}
