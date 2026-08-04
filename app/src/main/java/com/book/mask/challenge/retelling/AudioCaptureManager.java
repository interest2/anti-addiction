package com.book.mask.challenge.retelling;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.book.mask.constant.QuestionConst;

import java.util.ArrayList;
import java.util.List;

/**
 * 麦克风 PCM 采集。16kHz / 单声道 / 16bit，VOICE_RECOGNITION 音源。
 *
 * <p>两分钟数据约 3.84MB，只保存在内存，无需生成临时 WAV 文件。AudioRecord 要求应用主动循环读取，
 * 因此在后台线程读 short[] 片段。录音最长 {@link QuestionConst#RETELLING_RECORD_MAX_SECONDS} 秒，
 * 达到上限后自动停止采集（由上层计时器负责触发 {@link #stop(Callback)} 取回采样）。
 */
public final class AudioCaptureManager {

    private static final String TAG = "AudioCapture";
    private static final int SAMPLE_RATE = 16000;
    private static final int MAX_RECORD_MS =
            QuestionConst.RETELLING_RECORD_MAX_SECONDS * 1000;

    public interface Callback {
        void onCaptured(float[] samples);

        void onError(String message);
    }

    private final Handler handler;
    private AudioRecord audioRecord;
    private Thread recordingThread;
    private volatile boolean recording;
    private final List<short[]> chunks = new ArrayList<>();
    private int totalSamples;
    private int bufferSize;

    public AudioCaptureManager() {
        this.handler = new Handler(Looper.getMainLooper());
    }

    public boolean isRecording() {
        return recording;
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
            }
            if (SystemClock.elapsedRealtime() - startTime > MAX_RECORD_MS) {
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
        recording = false;
        joinCaptureThread();
        stopAudioRecord();
        float[] samples = toFloatSamples();
        recordingThread = null;
        if (samples == null || samples.length == 0) {
            callback.onError("没有录到有效语音");
        } else {
            handler.post(() -> callback.onCaptured(samples));
        }
    }

    /**
     * 丢弃本次录音数据，不取回采样。离开答题流程时立即释放麦克风。
     */
    public void cancel() {
        recording = false;
        joinCaptureThread();
        stopAudioRecord();
        synchronized (chunks) {
            chunks.clear();
            totalSamples = 0;
        }
        recordingThread = null;
    }

    public void release() {
        cancel();
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
}
