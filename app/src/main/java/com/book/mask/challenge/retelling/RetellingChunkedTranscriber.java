package com.book.mask.challenge.retelling;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 复述题录音分段识别包装器。
 *
 * <p>离线模型对长单段输入识别质量会下降，因此把一段录音按「句子间隙」切成约 20-30 秒的单元，
 * 逐段调用底层 {@link SpeechTranscriber}，再把各段文本拼接后交给评分。切分优先落在静音间隙
 * （句间停顿）上，避免切断词中间。
 *
 * <p>不改变底层识别器行为，也不影响临时测试页对整段音频的长度测试。
 */
public final class RetellingChunkedTranscriber implements SpeechTranscriber {

    private static final String TAG = "RetellingChunked";

    /** 目标分段时长（毫秒） */
    private static final int TARGET_CHUNK_MS = 25_000;
    /** 无合适间隙时单段硬上限（毫秒） */
    private static final int MAX_CHUNK_MS = 30_000;
    /** 单段最短时长（毫秒），避免切出过短碎片 */
    private static final int MIN_CHUNK_MS = 10_000;
    /** 能量帧长（毫秒） */
    private static final int FRAME_MS = 25;
    /** 低于该 RMS 视为静音帧 */
    private static final float SILENCE_THRESHOLD = 0.01f;
    /** 静音连续达到该时长才算一个可切分的间隙 */
    private static final int MIN_SILENCE_GAP_MS = 250;
    /** 在目标时长附近多少毫秒内寻找间隙 */
    private static final int SPLIT_SEARCH_WINDOW_MS = 5_000;
    /** 默认采样率 */
    private static final int DEFAULT_SAMPLE_RATE = 16000;

    private final SpeechTranscriber delegate;

    public RetellingChunkedTranscriber(SpeechTranscriber delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean isReady() {
        return delegate.isReady();
    }

    @Override
    public TranscriptionResult transcribe(float[] samples, int sampleRate) {
        if (!delegate.isReady()) {
            return TranscriptionResult.failure("ASR 模型缺失，请先修复模型");
        }
        int rate = sampleRate > 0 ? sampleRate : DEFAULT_SAMPLE_RATE;
        float[][] chunks = splitIntoChunks(samples, rate);
        StringBuilder merged = new StringBuilder();
        int failureCount = 0;
        for (int index = 0; index < chunks.length; index++) {
            long startedAt = System.nanoTime();
            TranscriptionResult result = delegate.transcribe(chunks[index], rate);
            float elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000f;
            String text = result.isSuccess() ? result.getText() : "";
            if (!result.isSuccess()) {
                failureCount++;
                Log.w(TAG, "分段识别失败 " + (index + 1) + "/" + chunks.length
                        + "：" + result.getErrorMessage());
            }
            Log.d(TAG, String.format(
                    Locale.US,
                    "分段识别 %d/%d，时长=%.2fs，文本长度=%d，耗时=%.2fs",
                    index + 1, chunks.length,
                    chunks[index].length / (float) rate,
                    text.length(),
                    elapsedSeconds));
            if (!text.isEmpty()) {
                if (merged.length() > 0) {
                    merged.append(' ');
                }
                merged.append(text);
            }
        }
        if (failureCount == chunks.length && merged.length() == 0) {
            return TranscriptionResult.failure("语音识别失败");
        }
        return TranscriptionResult.success(merged.toString());
    }

    @Override
    public void release() {
        delegate.release();
    }

    /** 把整段 PCM 切成 20-30 秒的单元；优先在静音间隙处切分。 */
    private static float[][] splitIntoChunks(float[] samples, int sampleRate) {
        if (samples == null || samples.length == 0) {
            return new float[][]{samples};
        }
        int maxSamples = MAX_CHUNK_MS * sampleRate / 1000;
        if (samples.length <= maxSamples) {
            return new float[][]{samples};
        }

        List<Integer> candidateSplits = detectSilenceSplits(samples, sampleRate);
        List<float[]> chunks = new ArrayList<>();
        int start = 0;
        int total = samples.length;
        int targetSamples = TARGET_CHUNK_MS * sampleRate / 1000;
        int minSamples = MIN_CHUNK_MS * sampleRate / 1000;
        int searchWindowSamples = SPLIT_SEARCH_WINDOW_MS * sampleRate / 1000;

        while (start < total) {
            int remaining = total - start;
            int cut;
            if (remaining <= maxSamples) {
                cut = total;
            } else {
                int idealEnd = start + targetSamples;
                int hardMaxEnd = start + maxSamples;
                int searchStart = Math.max(start + minSamples, idealEnd - searchWindowSamples);
                int bestCandidate = -1;
                for (int candidate : candidateSplits) {
                    if (candidate >= searchStart && candidate <= hardMaxEnd
                            && (bestCandidate == -1
                            || Math.abs(candidate - idealEnd) < Math.abs(bestCandidate - idealEnd))) {
                        bestCandidate = candidate;
                    }
                }
                cut = bestCandidate != -1 ? bestCandidate : hardMaxEnd;
            }
            if (cut <= start) {
                cut = Math.min(start + targetSamples, total);
            }
            chunks.add(Arrays.copyOfRange(samples, start, cut));
            start = cut;
        }
        return chunks.toArray(new float[0][]);
    }

    /**
     * 检测静音间隙，返回每个足够长静音段的中点采样位置，作为候选切分点。
     * 这些位置通常在句与句之间，落在词中间的概率较低。
     */
    private static List<Integer> detectSilenceSplits(float[] samples, int sampleRate) {
        int frameSize = FRAME_MS * sampleRate / 1000;
        int frameCount = samples.length / frameSize;
        boolean[] silentFrames = new boolean[frameCount];
        for (int frame = 0; frame < frameCount; frame++) {
            int offset = frame * frameSize;
            double sumOfSquares = 0d;
            for (int index = 0; index < frameSize; index++) {
                float value = samples[offset + index];
                sumOfSquares += value * value;
            }
            silentFrames[frame] = Math.sqrt(sumOfSquares / frameSize) < SILENCE_THRESHOLD;
        }

        int minGapFrames = MIN_SILENCE_GAP_MS / FRAME_MS;
        List<Integer> splits = new ArrayList<>();
        int runStart = -1;
        for (int frame = 0; frame <= frameCount; frame++) {
            boolean silent = frame < frameCount && silentFrames[frame];
            if (silent) {
                if (runStart < 0) {
                    runStart = frame;
                }
            } else {
                if (runStart >= 0) {
                    int runLength = frame - runStart;
                    if (runLength >= minGapFrames) {
                        splits.add((runStart + runLength / 2) * frameSize);
                    }
                    runStart = -1;
                }
            }
        }
        return splits;
    }
}
