package com.book.mask.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.book.mask.R;
import com.book.mask.challenge.retelling.SherpaOnnxTranscriber;
import com.book.mask.challenge.retelling.TranscriptionResult;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 临时调试工具：对 APK 内置的多份音频，各按多个前缀时长做离线识别。
 *
 * <p>音频为 16kHz / 单声道 / PCM16 little-endian 的 .pcm 文件，放在
 * {@code app/src/full/assets/asr-test/} 下，打包进 fullDebug APK。页面列出所有音频，
 * 逐个点击即可对该音频做不同长度识别测试。
 */
public class AsrTestActivity extends AppCompatActivity {

    private static final String TAG = "AsrTest";
    private static final int SAMPLE_RATE = 16000;
    private static final String ASSET_DIR = "asr-test";
    private static final int[] DURATIONS_SECONDS = {
            5, 10, 20, 25, 30, 31, 35, 40, 45, 60, 75, 90, 120
    };
    private static final float SILENCE_THRESHOLD = 0.01f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private LinearLayout llFiles;
    private TextView tvHint;
    private TextView tvResult;
    private final List<Button> fileButtons = new ArrayList<>();
    private boolean running;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_asr_test);

        llFiles = findViewById(R.id.ll_asr_files);
        tvHint = findViewById(R.id.tv_asr_hint);
        tvResult = findViewById(R.id.tv_asr_result);

        if (!SherpaOnnxTranscriber.getInstance(this).isReady()) {
            tvHint.setText("ASR 模型未就绪，请使用 fullDebug 包（lite 不含模型）");
        }
        buildFileList();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }

    /** 枚举内置 PCM，为每份生成一个可点击的测试按钮。 */
    private void buildFileList() {
        fileButtons.clear();
        llFiles.removeAllViews();
        String[] names;
        try {
            names = getAssets().list(ASSET_DIR);
        } catch (Exception e) {
            names = null;
        }
        List<String> pcms = new ArrayList<>();
        if (names != null) {
            for (String name : names) {
                if (name != null && name.toLowerCase(Locale.US).endsWith(".pcm")) {
                    pcms.add(name);
                }
            }
        }
        if (pcms.isEmpty()) {
            tvHint.setText("未找到测试音频。\n请将 16kHz 单声道 PCM16 的 .pcm 文件放入\napp/src/full/assets/asr-test/ 后重新打包。");
            return;
        }
        for (String name : pcms) {
            Button button = new Button(this);
            button.setText(name.endsWith(".pcm") ? name.substring(0, name.length() - 4) : name);
            button.setAllCaps(false);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            button.setTextColor(Color.WHITE);
            button.setBackgroundTintList(ColorStateList.valueOf(0xFF2196F3));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            int bottomMargin = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
            params.bottomMargin = bottomMargin;
            button.setLayoutParams(params);
            button.setOnClickListener(v -> runLengthTest(name));
            fileButtons.add(button);
            llFiles.addView(button);
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        for (Button button : fileButtons) {
            button.setEnabled(enabled);
        }
    }

    private void runLengthTest(final String name) {
        if (running) {
            return;
        }
        running = true;
        setButtonsEnabled(false);
        tvHint.setText("正在测试 " + name + " ……");
        tvResult.setText("");
        executor.execute(() -> {
            String output;
            try {
                output = runOne(name);
            } catch (Exception e) {
                Log.e(TAG, "测试失败: " + name, e);
                output = "测试失败：" + e.getMessage();
            }
            final String resultText = output;
            handler.post(() -> {
                tvHint.setText("测试完成：" + name);
                tvResult.setText(resultText);
                setButtonsEnabled(true);
                running = false;
            });
        });
    }

    private String runOne(String name) throws Exception {
        PcmData pcm = readPcm(name);
        StringBuilder output = new StringBuilder();
        output.append("音频：").append(name).append("\n")
                .append("SHA-256：").append(pcm.sha256).append("\n")
                .append("总时长：").append(formatSeconds(pcm.samples.length / (float) SAMPLE_RATE))
                .append("，采样数=").append(pcm.samples.length).append("\n\n");

        SherpaOnnxTranscriber transcriber = SherpaOnnxTranscriber.getInstance(this);
        for (int duration : DURATIONS_SECONDS) {
            int sampleCount = Math.min(duration * SAMPLE_RATE, pcm.samples.length);
            float[] samples = new float[sampleCount];
            System.arraycopy(pcm.samples, 0, samples, 0, sampleCount);
            Metrics metrics = calculateMetrics(samples);
            long startedAt = System.nanoTime();
            TranscriptionResult result = transcriber.transcribe(samples, SAMPLE_RATE);
            float elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000f;

            output.append("[").append(duration).append("s 请求 / ")
                    .append(formatSeconds(sampleCount / (float) SAMPLE_RATE)).append("] ")
                    .append("RMS=").append(formatDbfs(metrics.rms))
                    .append(" dBFS，峰值=").append(formatDbfs(metrics.peak))
                    .append("，非静音=")
                    .append(String.format(Locale.US, "%.1f%%", metrics.nonSilentPercent))
                    .append("，推理=").append(formatSeconds(elapsedSeconds)).append("\n");
            if (result.isSuccess()) {
                output.append("识别: ")
                        .append(result.getText().isEmpty() ? "[空结果]" : result.getText())
                        .append("\n\n");
            } else {
                output.append("识别: [失败] ").append(result.getErrorMessage()).append("\n\n");
            }
        }
        return output.toString();
    }

    /** 读取 APK 内置的 PCM16 little-endian，不做媒体解码、声道混音或重采样。 */
    private PcmData readPcm(String name) throws Exception {
        byte[] raw;
        try (InputStream input = getAssets().open(ASSET_DIR + "/" + name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            raw = output.toByteArray();
        }
        if (raw.length == 0 || raw.length % 2 != 0) {
            throw new IllegalStateException("PCM16 文件长度无效：" + name + " (" + raw.length + " 字节)");
        }
        float[] samples = new float[raw.length / 2];
        for (int index = 0; index < samples.length; index++) {
            int offset = index * 2;
            short value = (short) ((raw[offset] & 0xff) | (raw[offset + 1] << 8));
            samples[index] = value / 32768f;
        }
        return new PcmData(samples, sha256(raw));
    }

    private static String sha256(byte[] input) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            result.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static Metrics calculateMetrics(float[] samples) {
        float peak = 0f;
        double sumOfSquares = 0d;
        int nonSilentCount = 0;
        for (float sample : samples) {
            float amplitude = Math.abs(sample);
            peak = Math.max(peak, amplitude);
            sumOfSquares += sample * sample;
            if (amplitude >= SILENCE_THRESHOLD) {
                nonSilentCount++;
            }
        }
        return new Metrics(
                peak,
                (float) Math.sqrt(sumOfSquares / samples.length),
                nonSilentCount * 100f / samples.length);
    }

    private static String formatDbfs(float value) {
        if (value <= 0f) {
            return "-inf";
        }
        return String.format(Locale.US, "%.1f", 20f * (float) Math.log10(value));
    }

    private static String formatSeconds(float seconds) {
        return String.format(Locale.US, "%.3fs", seconds);
    }

    private static final class PcmData {
        final float[] samples;
        final String sha256;

        PcmData(float[] samples, String sha256) {
            this.samples = samples;
            this.sha256 = sha256;
        }
    }

    private static final class Metrics {
        final float peak;
        final float rms;
        final float nonSilentPercent;

        Metrics(float peak, float rms, float nonSilentPercent) {
            this.peak = peak;
            this.rms = rms;
            this.nonSilentPercent = nonSilentPercent;
        }
    }
}
