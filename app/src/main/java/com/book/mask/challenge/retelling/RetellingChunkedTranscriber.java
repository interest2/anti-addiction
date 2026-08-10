package com.book.mask.challenge.retelling;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 复述题录音分段识别包装器。
 *
 * <p>主识别仍按句间停顿切成约 20-30 秒的单元。主识别完成后，再为每个分割点截取由前后句间
 * 停顿包住的完整语句区间，单独识别并替换主识别中同一时间范围的 token，修复分割点附近因
 * 上下文不完整产生的识别错误。
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
    /** 完整语句修复区间的最大时长（毫秒），避免把长录音再次整段送入模型 */
    private static final int MAX_REPAIR_WINDOW_MS = 30_000;
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
        if (samples == null || samples.length == 0) {
            return delegate.transcribe(samples, rate);
        }
        List<Integer> silenceSplits = detectSilenceSplits(samples, rate);
        List<AudioRange> chunks = splitIntoChunks(samples, rate, silenceSplits);
        List<RecognizedRange> primaryResults = transcribeRanges(samples, rate, chunks, "分段识别");
        String primaryText = mergeRawText(primaryResults);
        if (primaryText.isEmpty() && allFailed(primaryResults)) {
            return TranscriptionResult.failure("语音识别失败");
        }
        if (chunks.size() == 1) {
            return TranscriptionResult.success(primaryText);
        }

        List<AudioRange> repairWindows = buildRepairWindows(
                chunks, silenceSplits, samples.length, rate);
        if (repairWindows.isEmpty()) {
            return TranscriptionResult.success(primaryText);
        }
        List<RecognizedRange> repairResults = transcribeRanges(
                samples, rate, repairWindows, "边界完整句重识别");
        String repairedText = replaceBoundaryText(primaryResults, repairResults, rate);
        if (repairedText == null || repairedText.isEmpty()) {
            Log.w(TAG, "边界文本无法安全替换，保留原分段识别结果");
            return TranscriptionResult.success(primaryText);
        }
        return TranscriptionResult.success(repairedText);
    }

    @Override
    public void release() {
        delegate.release();
    }

    private List<RecognizedRange> transcribeRanges(
            float[] samples,
            int sampleRate,
            List<AudioRange> ranges,
            String operation) {
        List<RecognizedRange> results = new ArrayList<>(ranges.size());
        for (int index = 0; index < ranges.size(); index++) {
            AudioRange range = ranges.get(index);
            long startedAt = System.nanoTime();
            TranscriptionResult result = delegate.transcribe(
                    Arrays.copyOfRange(samples, range.start, range.end), sampleRate);
            float elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000f;
            String text = result.isSuccess() ? result.getText() : "";
            if (!result.isSuccess()) {
                Log.w(TAG, operation + "失败 " + (index + 1) + "/" + ranges.size()
                        + "：" + result.getErrorMessage());
            }
            Log.d(TAG, String.format(
                    Locale.US,
                    "%s %d/%d，区间=%.2f-%.2fs，文本长度=%d，耗时=%.2fs",
                    operation,
                    index + 1,
                    ranges.size(),
                    range.start / (float) sampleRate,
                    range.end / (float) sampleRate,
                    text.length(),
                    elapsedSeconds));
            results.add(new RecognizedRange(range, result));
        }
        return results;
    }

    /** 保留原有的 20-30 秒分段策略与默认切点。 */
    private static List<AudioRange> splitIntoChunks(
            float[] samples,
            int sampleRate,
            List<Integer> candidateSplits) {
        List<AudioRange> chunks = new ArrayList<>();
        if (samples == null || samples.length == 0) {
            chunks.add(new AudioRange(0, samples == null ? 0 : samples.length));
            return chunks;
        }
        int total = samples.length;
        int maxSamples = MAX_CHUNK_MS * sampleRate / 1000;
        if (total <= maxSamples) {
            chunks.add(new AudioRange(0, total));
            return chunks;
        }

        int start = 0;
        int targetSamples = TARGET_CHUNK_MS * sampleRate / 1000;
        int minSamples = MIN_CHUNK_MS * sampleRate / 1000;
        int searchWindowSamples = SPLIT_SEARCH_WINDOW_MS * sampleRate / 1000;
        while (start < total) {
            int cut;
            if (total - start <= maxSamples) {
                cut = total;
            } else {
                int idealEnd = start + targetSamples;
                int hardMaxEnd = start + maxSamples;
                int searchStart = Math.max(start + minSamples, idealEnd - searchWindowSamples);
                int bestCandidate = -1;
                for (int candidate : candidateSplits) {
                    if (candidate >= searchStart && candidate <= hardMaxEnd
                            && (bestCandidate < 0
                            || Math.abs(candidate - idealEnd) < Math.abs(bestCandidate - idealEnd))) {
                        bestCandidate = candidate;
                    }
                }
                cut = bestCandidate >= 0 ? bestCandidate : hardMaxEnd;
            }
            if (cut <= start) {
                cut = Math.min(start + targetSamples, total);
            }
            chunks.add(new AudioRange(start, cut));
            start = cut;
        }
        return chunks;
    }

    /**
     * 为每个主分割点取前一个句间停顿到后一个句间停顿，得到分割点附近的完整语句区间。
     * 若主分割点本身就在句间停顿上，区间会同时包含其前后两个完整语句，为两侧边界提供上下文。
     */
    private static List<AudioRange> buildRepairWindows(
            List<AudioRange> chunks,
            List<Integer> silenceSplits,
            int totalSamples,
            int sampleRate) {
        List<AudioRange> windows = new ArrayList<>();
        int toleranceSamples = FRAME_MS * sampleRate / 1000;
        int maxRepairSamples = MAX_REPAIR_WINDOW_MS * sampleRate / 1000;
        for (int index = 0; index < chunks.size() - 1; index++) {
            int split = chunks.get(index).end;
            int previousSilence = previousSplit(
                    silenceSplits, split - toleranceSamples, 0);
            int nextSilence = nextSplit(
                    silenceSplits, split + toleranceSamples, totalSamples);
            int splitSilence = nearbySplit(silenceSplits, split, toleranceSamples);

            if (nextSilence - previousSilence <= maxRepairSamples) {
                addRepairWindow(windows, previousSilence, nextSilence, maxRepairSamples);
            } else if (splitSilence >= 0) {
                // 分割点正好在句间停顿：组合区间过长时，分别重识别左右两句
                addRepairWindow(windows, previousSilence, splitSilence, maxRepairSamples);
                addRepairWindow(windows, splitSilence, nextSilence, maxRepairSamples);
            } else {
                Log.w(TAG, String.format(
                        Locale.US,
                        "分割点 %.2fs 所在完整语句超过 %.1fs，跳过局部修复",
                        split / (float) sampleRate,
                        MAX_REPAIR_WINDOW_MS / 1000f));
            }
        }
        return windows;
    }

    /** 修复窗口始终以静音中点为边界；重叠时从上一窗口末尾开始，避免重复文本。 */
    private static void addRepairWindow(
            List<AudioRange> windows,
            int start,
            int end,
            int maxRepairSamples) {
        if (!windows.isEmpty()) {
            start = Math.max(start, windows.get(windows.size() - 1).end);
        }
        if (end <= start || end - start > maxRepairSamples) {
            return;
        }
        windows.add(new AudioRange(start, end));
    }

    private static int previousSplit(List<Integer> splits, int position, int fallback) {
        int result = fallback;
        for (int split : splits) {
            if (split >= position) {
                break;
            }
            result = split;
        }
        return result;
    }

    private static int nextSplit(List<Integer> splits, int position, int fallback) {
        for (int split : splits) {
            if (split > position) {
                return split;
            }
        }
        return fallback;
    }

    private static int nearbySplit(List<Integer> splits, int position, int tolerance) {
        for (int split : splits) {
            if (Math.abs(split - position) <= tolerance) {
                return split;
            }
            if (split > position + tolerance) {
                break;
            }
        }
        return -1;
    }

    /**
     * 用完整语句重识别结果替换主分段结果中相同时间范围的 token。只有主结果与修复结果都带
     * Sherpa token 时间戳时才执行；任何一侧缺少时间戳都会保留原结果，避免按字符串猜位置。
     */
    private static String replaceBoundaryText(
            List<RecognizedRange> primaryResults,
            List<RecognizedRange> repairResults,
            int sampleRate) {
        List<AbsoluteToken> primaryTokens = new ArrayList<>();
        for (RecognizedRange recognized : primaryResults) {
            if (!recognized.result.isSuccess() || recognized.result.getText().isEmpty()) {
                continue;
            }
            if (!recognized.result.hasTimedTokens()) {
                return null;
            }
            addAbsoluteTokens(primaryTokens, recognized, sampleRate);
        }

        List<RecognizedRange> successfulRepairs = new ArrayList<>();
        List<AbsoluteToken> repairedTokens = new ArrayList<>();
        for (RecognizedRange recognized : repairResults) {
            if (!recognized.result.isSuccess()
                    || recognized.result.getText().isEmpty()
                    || !recognized.result.hasTimedTokens()) {
                continue;
            }
            successfulRepairs.add(recognized);
            addAbsoluteTokens(repairedTokens, recognized, sampleRate);
        }
        if (successfulRepairs.isEmpty() || repairedTokens.isEmpty()) {
            return null;
        }

        List<AbsoluteToken> merged = new ArrayList<>(primaryTokens.size() + repairedTokens.size());
        for (AbsoluteToken token : primaryTokens) {
            if (!fallsInsideAny(token, successfulRepairs)) {
                merged.add(token);
            }
        }
        merged.addAll(repairedTokens);
        merged.sort(Comparator
                .comparingInt((AbsoluteToken token) -> token.start)
                .thenComparingInt(token -> token.end));
        return detokenize(merged).trim();
    }

    private static void addAbsoluteTokens(
            List<AbsoluteToken> destination,
            RecognizedRange recognized,
            int sampleRate) {
        for (TranscriptionResult.TimedToken token : recognized.result.getTimedTokens()) {
            int start = recognized.range.start
                    + Math.round(token.getStartSeconds() * sampleRate);
            int end = recognized.range.start
                    + Math.round(token.getEndSeconds() * sampleRate);
            start = Math.max(recognized.range.start, Math.min(start, recognized.range.end));
            end = Math.max(start, Math.min(end, recognized.range.end));
            destination.add(new AbsoluteToken(token.getText(), start, end));
        }
    }

    private static boolean fallsInsideAny(
            AbsoluteToken token,
            List<RecognizedRange> repairResults) {
        int midpoint = token.start + (token.end - token.start) / 2;
        for (RecognizedRange repair : repairResults) {
            if (midpoint >= repair.range.start && midpoint < repair.range.end) {
                return true;
            }
        }
        return false;
    }

    /** 把 Sherpa 的中英文及 BPE token 还原成可读文本。 */
    private static String detokenize(List<AbsoluteToken> tokens) {
        StringBuilder text = new StringBuilder();
        String previous = null;
        boolean previousJoinsNext = false;
        for (AbsoluteToken timedToken : tokens) {
            String token = timedToken.text == null ? "" : timedToken.text.trim();
            if (token.isEmpty() || isSpecialToken(token)) {
                continue;
            }
            boolean joinsNext = token.endsWith("@@");
            if (joinsNext) {
                token = token.substring(0, token.length() - 2);
            }
            if (token.isEmpty()) {
                previousJoinsNext = true;
                continue;
            }
            if (shouldInsertSpace(previous, token, previousJoinsNext)) {
                text.append(' ');
            }
            text.append(token);
            previous = token;
            previousJoinsNext = joinsNext;
        }
        return text.toString();
    }

    private static boolean shouldInsertSpace(
            String previous,
            String current,
            boolean previousJoinsNext) {
        if (previous == null || previousJoinsNext || startsWithPunctuation(current)) {
            return false;
        }
        int previousCodePoint = previous.codePointBefore(previous.length());
        int currentCodePoint = current.codePointAt(0);
        if (isAsciiWordStart(currentCodePoint) && endsWithEnglishPunctuation(previousCodePoint)) {
            return true;
        }
        if (isCjk(previousCodePoint) || isCjk(currentCodePoint)) {
            return false;
        }
        return Character.isLetterOrDigit(previousCodePoint)
                && Character.isLetterOrDigit(currentCodePoint);
    }

    private static boolean isAsciiWordStart(int codePoint) {
        return codePoint < 128 && Character.isLetterOrDigit(codePoint);
    }

    private static boolean endsWithEnglishPunctuation(int codePoint) {
        return ".,!?;:%)]}".indexOf(codePoint) >= 0;
    }

    private static boolean startsWithPunctuation(String value) {
        int codePoint = value.codePointAt(0);
        return ".,!?;:%)]}，。！？；：、）】》’'".indexOf(codePoint) >= 0;
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private static boolean isSpecialToken(String token) {
        return token.length() > 2 && token.charAt(0) == '<'
                && token.charAt(token.length() - 1) == '>';
    }

    private static boolean allFailed(List<RecognizedRange> results) {
        for (RecognizedRange recognized : results) {
            if (recognized.result.isSuccess()) {
                return false;
            }
        }
        return true;
    }

    private static String mergeRawText(List<RecognizedRange> results) {
        StringBuilder merged = new StringBuilder();
        for (RecognizedRange recognized : results) {
            if (!recognized.result.isSuccess() || recognized.result.getText().isEmpty()) {
                continue;
            }
            if (merged.length() > 0) {
                merged.append(' ');
            }
            merged.append(recognized.result.getText());
        }
        return merged.toString();
    }

    /**
     * 检测静音间隙，返回每个足够长静音段的中点采样位置，作为候选切分点和完整语句边界。
     */
    private static List<Integer> detectSilenceSplits(float[] samples, int sampleRate) {
        List<Integer> splits = new ArrayList<>();
        if (samples == null || samples.length == 0) {
            return splits;
        }
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
        int runStart = -1;
        for (int frame = 0; frame <= frameCount; frame++) {
            boolean silent = frame < frameCount && silentFrames[frame];
            if (silent) {
                if (runStart < 0) {
                    runStart = frame;
                }
            } else if (runStart >= 0) {
                int runLength = frame - runStart;
                if (runLength >= minGapFrames) {
                    splits.add((runStart + runLength / 2) * frameSize);
                }
                runStart = -1;
            }
        }
        return splits;
    }

    private static final class AudioRange {
        private final int start;
        private final int end;

        private AudioRange(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private static final class RecognizedRange {
        private final AudioRange range;
        private final TranscriptionResult result;

        private RecognizedRange(AudioRange range, TranscriptionResult result) {
            this.range = range;
            this.result = result;
        }
    }

    private static final class AbsoluteToken {
        private final String text;
        private final int start;
        private final int end;

        private AbsoluteToken(String text, int start, int end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }
    }
}
