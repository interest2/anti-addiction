package com.book.mask.challenge.listening;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 语音题音频播放器（边合成边播）。
 *
 * <p>出题时调用 {@link #prepare} 用豆包语音（火山引擎 Speech SDK）后台合成听力原文 + 提问句。
 * 豆包 BiTTS 逐句流式返回音频，每合成好一句就落盘一个可独立播放的分段文件；本类拿到「第一段」
 * 就回调 {@code onReadyToPlay}，上层随即调用 {@link #play} 开始播放，并在后续分段陆续到达时无缝续播，
 * 无需等整段合成完毕。若播放追上了合成进度，就在句边界处等待下一段到达再继续（不会切断句子）。
 *
 * <p>确定无法合成（未配置凭据 / 设备不支持 / 首段前出错或超时）时回退内置占位 mp3，保证听力题始终可用；
 * 已开始播放真实音频后遇到中途异常，则在最后一段完整播完后收尾揭示题干，不再回退占位。
 */
public final class ListeningAudioPlayer {

    /** 严格模式下合成或播放失败的回调；听述题不能用占位音频代替原文。 */
    public interface ErrorCallback {
        void onError(String message);
    }

    private static final String TAG = "ListeningAudio";
    /** 合成停滞看护：首段迟迟不来、或句间迟迟等不到下一段时的兜底时长。 */
    private static final long TTS_STALL_TIMEOUT_MS = 30_000L;

    private enum AudioState {
        IDLE,
        PREPARING,
        REAL_AUDIO,
        PLACEHOLDER_READY
    }

    private static final ExecutorService PLAYER_RELEASE_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "listening-player-release");
                thread.setDaemon(true);
                return thread;
            });

    private final ListeningSynthesizerHolder synthesizerHolder = new ListeningSynthesizerHolder();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService synthesizerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "doubao-tts-lifecycle");
        thread.setDaemon(true);
        return thread;
    });

    private MediaPlayer mediaPlayer;
    /** 当前序列已就绪的分段音频，按合成顺序追加，播放时顺序消费。 */
    private final List<File> audioFiles = new ArrayList<>();
    /** 已追加分段的绝对路径，用于对 onSegmentReady 与 onAudioReady 去重。 */
    private final Set<String> appendedPaths = new HashSet<>();
    private int playingSegmentIndex;
    private AudioState audioState = AudioState.IDLE;
    /** 本序列（原文 + 提问句）是否已全部合成完毕，无更多分段到达。 */
    private boolean streamComplete;
    /** 播放已消费完现有分段、但合成尚未结束，正在句边界等待下一段。 */
    private boolean waitingForSegment;
    /** 处于等待态的播放轮次，用于新分段到达时精确恢复对应播放。 */
    private long waitingPlaybackGeneration;
    /** 本序列是否已调用过 play()（用于占位兜底时判断能否再回退）。 */
    private boolean playbackStarted;
    /** 本序列是否已回调过 onReadyToPlay，保证只触发一次首播。 */
    private boolean readyNotified;
    /** 标识一次合成序列（一道题）。 */
    private long prepareGeneration;
    /** 标识一次播放轮次（首播 / 每次重听）。 */
    private long playbackGeneration;
    private Runnable stallTimeout;
    /** 首段就绪 / 占位确定后触发首播的回调（由 prepare 传入，供停滞兜底复用）。 */
    private Runnable pendingReadyCallback;
    /** 是否允许首段前失败时播放占位音频；普通听力题允许，听述题禁止。 */
    private boolean placeholderFallbackAllowed = true;
    private ErrorCallback pendingErrorCallback;
    private boolean errorNotified;
    /** 整段播放结束（或播放失败兜底）时的回调，用于揭示题干与选项。 */
    private Runnable onPlaybackCompleted;

    /**
     * 后台合成听力原文 + 提问句，边合成边播：第一段就绪（或确定回退占位）后回调 {@code onReadyToPlay}，
     * 上层随即 {@link #play} 开始播放，后续分段到达时自动续播。
     */
    public synchronized void prepare(
            Context context, String transcript, String question, Runnable onReadyToPlay) {
        prepareInternal(context, transcript, question, onReadyToPlay, true, null);
    }

    /**
     * 合成一段必须完整播放的复述材料。任何合成停滞、分段缺失或播放错误均回调失败，绝不回退占位音频。
     */
    public synchronized void prepareStrictPassage(
            Context context,
            String passage,
            Runnable onReadyToPlay,
            ErrorCallback onError) {
        prepareInternal(context, passage, "", onReadyToPlay, false, onError);
    }

    private void prepareInternal(
            Context context,
            String transcript,
            String question,
            Runnable onReadyToPlay,
            boolean allowPlaceholderFallback,
            ErrorCallback onError) {
        cancelStallTimeoutLocked();
        releaseCurrentMediaPlayerAsync(true);
        audioFiles.clear();
        appendedPaths.clear();
        streamComplete = false;
        waitingForSegment = false;
        playbackStarted = false;
        readyNotified = false;
        playingSegmentIndex = 0;
        playbackGeneration++;
        pendingReadyCallback = onReadyToPlay;
        placeholderFallbackAllowed = allowPlaceholderFallback;
        pendingErrorCallback = onError;
        errorNotified = false;
        audioState = AudioState.PREPARING;
        final long seqGen = ++prepareGeneration;
        final String questionText = question == null ? "" : question.trim();
        final String transcriptText = transcript == null ? "" : transcript;
        armStallTimeoutLocked();
        synthesizerExecutor.execute(() -> {
            if (!isCurrentSequence(seqGen)) {
                return;
            }
            synthesizerHolder.get(context).synthesizeNewSequence(
                    transcriptText, transcriptCallback(context, seqGen, questionText));
        });
    }

    /**
     * 播放音频：真实合成音频按分段顺序流式播放（追上合成则句边界等待续段），
     * 或播放确定需要使用的占位音频。首播由 prepare 就绪后触发，重听由按钮触发。
     */
    public synchronized void play(Context context) {
        if (audioState == AudioState.PREPARING || audioState == AudioState.IDLE) {
            Log.d(TAG, "听力音频尚未就绪，忽略播放请求");
            return;
        }
        releaseCurrentMediaPlayerAsync(true);
        playingSegmentIndex = 0;
        waitingForSegment = false;
        playbackStarted = true;
        long generation = ++playbackGeneration;
        boolean canPlayReal = audioState == AudioState.REAL_AUDIO
                && !audioFiles.isEmpty()
                && (!streamComplete || allAudioFilesExist());
        if (canPlayReal) {
            playCurrentSegment(generation);
        } else if (!placeholderFallbackAllowed) {
            notifyStrictError(prepareGeneration, "听述音频文件不可用，请重新合成");
        } else {
            audioState = AudioState.PLACEHOLDER_READY;
            playPlaceholder(context, generation);
        }
    }

    /**
     * 注册整段播放完成回调（播放失败时也会回调，避免题干 / 选项永久不揭示）。
     */
    public synchronized void setOnPlaybackCompleted(Runnable listener) {
        this.onPlaybackCompleted = listener;
    }

    public void stop() {
        synchronized (this) {
            prepareGeneration++;
            playbackGeneration++;
            cancelStallTimeoutLocked();
            resetSequenceStateLocked();
            releaseCurrentMediaPlayerAsync(true);
        }
        stopSynthesizerAsync();
    }

    public void release() {
        synchronized (this) {
            prepareGeneration++;
            playbackGeneration++;
            cancelStallTimeoutLocked();
            resetSequenceStateLocked();
            onPlaybackCompleted = null;
            pendingReadyCallback = null;
            pendingErrorCallback = null;
            releaseCurrentMediaPlayerAsync(true);
        }
        synthesizerExecutor.execute(() -> {
            DoubaoSpeechSynthesizer synthesizer = synthesizerHolder.detach();
            if (synthesizer != null) {
                synthesizer.destroy();
            }
        });
        synthesizerExecutor.shutdown();
    }

    // ===== 合成回调编排 =====

    /** 听力原文合成回调：分段流式追加，会话结束后再合成提问句；首段前失败则回退占位。 */
    private DoubaoSpeechSynthesizer.Callback transcriptCallback(
            Context context, long seqGen, String questionText) {
        return new DoubaoSpeechSynthesizer.Callback() {
            @Override
            public void onSegmentReady(File segment) {
                handleSegmentReady(seqGen, segment);
            }

            @Override
            public void onAudioReady(List<File> files) {
                reconcileFiles(seqGen, files);
                if (hasAudio(seqGen)) {
                    Log.d(TAG, "豆包听力原文合成完成，继续合成提问句");
                    synthesizeQuestion(context, seqGen, questionText);
                } else {
                    Log.w(TAG, "听力原文无有效分段，回退占位音频");
                    fallbackToPlaceholder(seqGen, "听力原文未生成有效音频");
                }
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "豆包语音合成失败: " + message);
                terminateSequence(seqGen, message);
            }

            @Override
            public void onUnavailable(String reason) {
                Log.w(TAG, "豆包语音不可用: " + reason);
                terminateSequence(seqGen, reason);
            }
        };
    }

    /**
     * 听力原文合成成功后，接着合成提问句音频（各自独立请求，避免拼接原文后单次文本超 80 字上限）；
     * 提问句合成失败 / 不可用时仅播放原文，不影响主流程。
     */
    private void synthesizeQuestion(Context context, long seqGen, String question) {
        if (!isCurrentSequence(seqGen)) {
            return;
        }
        if (question.isEmpty()) {
            markStreamComplete(seqGen);
            return;
        }
        synthesizerExecutor.execute(() -> {
            if (!isCurrentSequence(seqGen)) {
                return;
            }
            synthesizerHolder.get(context).synthesizeNext(
                    question,
                    new DoubaoSpeechSynthesizer.Callback() {
                        @Override
                        public void onSegmentReady(File segment) {
                            handleSegmentReady(seqGen, segment);
                        }

                        @Override
                        public void onAudioReady(List<File> files) {
                            reconcileFiles(seqGen, files);
                            Log.d(TAG, "豆包提问句合成完成，序列结束");
                            markStreamComplete(seqGen);
                        }

                        @Override
                        public void onError(String message) {
                            Log.e(TAG, "提问句语音合成失败，仅播放听力原文: " + message);
                            markStreamComplete(seqGen);
                        }

                        @Override
                        public void onUnavailable(String reason) {
                            Log.w(TAG, "提问句语音不可用，仅播放听力原文: " + reason);
                            markStreamComplete(seqGen);
                        }
                    });
        });
    }

    /** 新分段到达：去重追加；首段触发首播，后续分段唤醒等待中的播放。 */
    private void handleSegmentReady(long seqGen, File segment) {
        boolean firstSegment;
        synchronized (this) {
            if (seqGen != prepareGeneration || segment == null) {
                return;
            }
            if (!appendedPaths.add(segment.getAbsolutePath())) {
                return;
            }
            audioFiles.add(segment);
            firstSegment = audioState == AudioState.PREPARING;
            if (firstSegment) {
                audioState = AudioState.REAL_AUDIO;
            }
            // 合成有进展，撤掉停滞看护；若播放随后再次追上会重新武装
            cancelStallTimeoutLocked();
        }
        if (firstSegment) {
            Log.d(TAG, "听力首段就绪，开始首播");
            notifyReadyToPlay(seqGen);
        } else {
            resumePlaybackIfWaiting(seqGen);
        }
    }

    /** onAudioReady 汇总列表兜底：把尚未通过 onSegmentReady 追加过的分段补齐（如 dump 单文件路径）。 */
    private void reconcileFiles(long seqGen, List<File> files) {
        if (files == null) {
            return;
        }
        for (File file : files) {
            handleSegmentReady(seqGen, file);
        }
    }

    /** 合成中途失败 / 不可用：无任何分段则回退占位，已产出分段则播完收尾。 */
    private void terminateSequence(long seqGen, String reason) {
        if (!isPlaceholderFallbackAllowed(seqGen)) {
            notifyStrictError(seqGen, reason);
        } else if (hasAudio(seqGen)) {
            markStreamComplete(seqGen);
        } else {
            fallbackToPlaceholder(seqGen, reason);
        }
    }

    /** 标记序列合成完毕：唤醒正等待续段的播放使其收尾。 */
    private void markStreamComplete(long seqGen) {
        boolean resumeToFinish = false;
        long playGen = 0;
        boolean fireReady = false;
        boolean strictEmpty = false;
        synchronized (this) {
            if (seqGen != prepareGeneration || streamComplete) {
                return;
            }
            streamComplete = true;
            cancelStallTimeoutLocked();
            if (audioFiles.isEmpty()) {
                if (placeholderFallbackAllowed) {
                    audioState = AudioState.PLACEHOLDER_READY;
                    fireReady = !playbackStarted;
                } else {
                    strictEmpty = true;
                }
            } else if (waitingForSegment && waitingPlaybackGeneration == playbackGeneration) {
                waitingForSegment = false;
                resumeToFinish = true;
                playGen = playbackGeneration;
            }
        }
        if (strictEmpty) {
            notifyStrictError(seqGen, "听述材料未生成有效音频");
            return;
        }
        if (fireReady) {
            notifyReadyToPlay(seqGen);
        }
        if (resumeToFinish) {
            playCurrentSegment(playGen);
        }
    }

    /** 首段前彻底失败：清空真实音频、转占位，并触发首播（若尚未开始播放）。 */
    private void fallbackToPlaceholder(long seqGen, String reason) {
        if (!isPlaceholderFallbackAllowed(seqGen)) {
            notifyStrictError(seqGen, reason);
            return;
        }
        boolean fireReady;
        synchronized (this) {
            if (seqGen != prepareGeneration) {
                return;
            }
            streamComplete = true;
            cancelStallTimeoutLocked();
            audioFiles.clear();
            appendedPaths.clear();
            audioState = AudioState.PLACEHOLDER_READY;
            fireReady = !playbackStarted;
        }
        stopSynthesizerAsync();
        if (fireReady) {
            notifyReadyToPlay(seqGen);
        }
    }

    // ===== 停滞看护 =====

    /** 武装停滞看护：首段迟迟不来 / 句间迟迟等不到下一段时兜底（须在持锁时调用）。 */
    private void armStallTimeoutLocked() {
        cancelStallTimeoutLocked();
        final long seqGen = prepareGeneration;
        Runnable timeout = () -> onStallTimeout(seqGen);
        stallTimeout = timeout;
        mainHandler.postDelayed(timeout, TTS_STALL_TIMEOUT_MS);
    }

    private void cancelStallTimeoutLocked() {
        if (stallTimeout == null) {
            return;
        }
        mainHandler.removeCallbacks(stallTimeout);
        stallTimeout = null;
    }

    private void onStallTimeout(long seqGen) {
        boolean placeholder;
        boolean strict;
        synchronized (this) {
            if (seqGen != prepareGeneration || streamComplete) {
                return;
            }
            stallTimeout = null;
            placeholder = audioFiles.isEmpty();
            strict = !placeholderFallbackAllowed;
        }
        Log.e(TAG, "听力合成停滞超时，占位=" + placeholder);
        stopSynthesizerAsync();
        if (strict) {
            // 听述材料必须完整。即使已经合成并播放了若干分段，也不能把超时截断的内容
            // 当作完整题面进入录音阶段。
            notifyStrictError(seqGen, "听述音频合成超时，请重新合成");
        } else if (placeholder) {
            fallbackToPlaceholder(seqGen, "语音合成超时");
        } else {
            markStreamComplete(seqGen);
        }
    }

    // ===== 播放引擎 =====

    private void playCurrentSegment(long generation) {
        File file = null;
        boolean finished = false;
        int segmentNumber = 0;
        int segmentCount = 0;
        synchronized (this) {
            if (generation != playbackGeneration) {
                return;
            }
            if (playingSegmentIndex < audioFiles.size()) {
                file = audioFiles.get(playingSegmentIndex);
                segmentNumber = playingSegmentIndex + 1;
                segmentCount = audioFiles.size();
            } else if (streamComplete) {
                finished = true;
            } else {
                // 边合成边播：播放追上了合成，在句边界等待下一段到达再续播
                waitingForSegment = true;
                waitingPlaybackGeneration = generation;
                armStallTimeoutLocked();
                Log.d(TAG, "听力播放追上合成，等待下一分段");
                return;
            }
        }
        if (finished) {
            notifyPlaybackCompleted(generation);
            return;
        }

        try {
            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            player.setDataSource(file.getAbsolutePath());
            player.setOnPreparedListener(mp -> mp.start());
            player.setOnCompletionListener(mp -> playNextSegment(mp, generation));
            player.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "播放合成音频出错 what=" + what + ", extra=" + extra);
                if (isStrictPlayback(generation)) {
                    failStrictPlayback(mp, generation, "听述音频播放失败，请重新合成");
                } else {
                    finishPlayback(mp, generation, false);
                }
                return true;
            });
            synchronized (this) {
                mediaPlayer = player;
            }
            player.prepareAsync();
            Log.d(TAG, "播放豆包合成音频: segment=" + segmentNumber + "/" + segmentCount
                    + (streamComplete ? "" : "(合成中)"));
        } catch (Exception e) {
            Log.e(TAG, "播放合成音频异常", e);
            synchronized (this) {
                releaseCurrentMediaPlayerAsync(true);
            }
            if (isStrictPlayback(generation)) {
                notifyStrictError(prepareGeneration, "听述音频播放失败，请重新合成");
            } else {
                notifyPlaybackCompleted(generation);
            }
        }
    }

    private void playNextSegment(MediaPlayer completedPlayer, long generation) {
        synchronized (this) {
            if (mediaPlayer != completedPlayer || generation != playbackGeneration) {
                releaseMediaPlayerAsync(completedPlayer, false);
                return;
            }
            mediaPlayer = null;
            playingSegmentIndex++;
        }
        releaseMediaPlayerAsync(completedPlayer, false);
        playCurrentSegment(generation);
    }

    /** 新分段到达时，唤醒正在句边界等待的当前播放继续消费。 */
    private void resumePlaybackIfWaiting(long seqGen) {
        long playGen;
        synchronized (this) {
            if (seqGen != prepareGeneration || !waitingForSegment
                    || waitingPlaybackGeneration != playbackGeneration) {
                return;
            }
            waitingForSegment = false;
            playGen = playbackGeneration;
        }
        playCurrentSegment(playGen);
    }

    private void playPlaceholder(Context context, long generation) {
        try {
            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            Uri placeholderUri = Uri.parse(
                    "android.resource://" + context.getPackageName() + "/raw/listening_placeholder");
            player.setDataSource(context, placeholderUri);
            player.setOnPreparedListener(mp -> mp.start());
            player.setOnCompletionListener(mp -> finishPlayback(mp, generation, false));
            player.setOnErrorListener((mp, what, extra) -> {
                finishPlayback(mp, generation, false);
                return true;
            });
            synchronized (this) {
                mediaPlayer = player;
            }
            player.prepareAsync();
            Log.d(TAG, "播放听力占位音频");
        } catch (Exception e) {
            Log.e(TAG, "播放占位音频异常", e);
            synchronized (this) {
                releaseCurrentMediaPlayerAsync(true);
            }
            notifyPlaybackCompleted(generation);
        }
    }

    private synchronized boolean allAudioFilesExist() {
        if (audioFiles.isEmpty()) {
            return false;
        }
        for (File file : audioFiles) {
            if (!file.exists()) {
                return false;
            }
        }
        return true;
    }

    private void finishPlayback(MediaPlayer completedPlayer, long generation, boolean stopFirst) {
        boolean completedCurrentPlayback;
        synchronized (this) {
            completedCurrentPlayback = mediaPlayer == completedPlayer
                    && generation == playbackGeneration;
            if (mediaPlayer == completedPlayer) {
                mediaPlayer = null;
            }
        }
        if (completedCurrentPlayback) {
            notifyPlaybackCompleted(generation);
        }
        releaseMediaPlayerAsync(completedPlayer, stopFirst);
    }

    private void failStrictPlayback(
            MediaPlayer failedPlayer, long generation, String message) {
        boolean current;
        synchronized (this) {
            current = mediaPlayer == failedPlayer && generation == playbackGeneration;
            if (mediaPlayer == failedPlayer) {
                mediaPlayer = null;
            }
        }
        releaseMediaPlayerAsync(failedPlayer, false);
        if (current) {
            notifyStrictError(prepareGeneration, message);
        }
    }

    private void releaseCurrentMediaPlayerAsync(boolean stopFirst) {
        MediaPlayer player = mediaPlayer;
        mediaPlayer = null;
        releaseMediaPlayerAsync(player, stopFirst);
    }

    private void releaseMediaPlayerAsync(MediaPlayer player, boolean stopFirst) {
        if (player == null) {
            return;
        }
        PLAYER_RELEASE_EXECUTOR.execute(() -> {
            if (stopFirst) {
                try {
                    player.stop();
                } catch (IllegalStateException ignored) {
                    // 未启动或已释放时忽略
                }
            }
            try {
                player.release();
            } catch (RuntimeException e) {
                Log.w(TAG, "释放听力播放器失败", e);
            }
        });
    }

    // ===== 状态与线程调度辅助 =====

    private synchronized boolean isCurrentSequence(long seqGen) {
        return seqGen == prepareGeneration;
    }

    private synchronized boolean hasAudio(long seqGen) {
        return seqGen == prepareGeneration && !audioFiles.isEmpty();
    }

    private synchronized boolean isPlaceholderFallbackAllowed(long seqGen) {
        return seqGen == prepareGeneration && placeholderFallbackAllowed;
    }

    private synchronized boolean isStrictPlayback(long generation) {
        return generation == playbackGeneration && !placeholderFallbackAllowed;
    }

    private void resetSequenceStateLocked() {
        audioFiles.clear();
        appendedPaths.clear();
        streamComplete = false;
        waitingForSegment = false;
        playbackStarted = false;
        readyNotified = false;
        playingSegmentIndex = 0;
        pendingReadyCallback = null;
        placeholderFallbackAllowed = true;
        pendingErrorCallback = null;
        errorNotified = false;
        audioState = AudioState.IDLE;
    }

    private void stopSynthesizerAsync() {
        synthesizerExecutor.execute(() -> {
            DoubaoSpeechSynthesizer synthesizer = synthesizerHolder.getCurrent();
            if (synthesizer != null) {
                synthesizer.stop();
            }
        });
    }

    private void notifyReadyToPlay(long seqGen) {
        Runnable callback;
        synchronized (this) {
            if (seqGen != prepareGeneration || readyNotified || errorNotified) {
                return;
            }
            readyNotified = true;
            callback = pendingReadyCallback;
        }
        Runnable notification = () -> {
            synchronized (ListeningAudioPlayer.this) {
                if (seqGen != prepareGeneration) {
                    return;
                }
            }
            if (callback != null) {
                callback.run();
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            notification.run();
        } else {
            mainHandler.post(notification);
        }
    }

    private void notifyPlaybackCompleted(long generation) {
        Runnable callback = () -> {
            Runnable cb;
            synchronized (ListeningAudioPlayer.this) {
                if (generation != playbackGeneration) {
                    return;
                }
                cb = onPlaybackCompleted;
            }
            if (cb != null) {
                cb.run();
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback.run();
        } else {
            mainHandler.post(callback);
        }
    }

    /** 严格模式失败：终止当前合成与播放，并在主线程只回调一次。 */
    private void notifyStrictError(long seqGen, String message) {
        ErrorCallback callback;
        synchronized (this) {
            if (seqGen != prepareGeneration || placeholderFallbackAllowed || errorNotified) {
                return;
            }
            errorNotified = true;
            streamComplete = true;
            waitingForSegment = false;
            cancelStallTimeoutLocked();
            audioFiles.clear();
            appendedPaths.clear();
            audioState = AudioState.IDLE;
            playbackGeneration++;
            releaseCurrentMediaPlayerAsync(true);
            callback = pendingErrorCallback;
        }
        stopSynthesizerAsync();
        Runnable notification = () -> {
            synchronized (ListeningAudioPlayer.this) {
                if (seqGen != prepareGeneration || !errorNotified) {
                    return;
                }
            }
            if (callback != null) {
                callback.onError(message == null || message.trim().isEmpty()
                        ? "听述音频不可用，请重新合成"
                        : message);
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            notification.run();
        } else {
            mainHandler.post(notification);
        }
    }

    /**
     * 持有豆包合成器：构造时机延迟到首次 prepare，释放后置空以便复用。
     */
    private static final class ListeningSynthesizerHolder {
        private DoubaoSpeechSynthesizer synthesizer;

        synchronized DoubaoSpeechSynthesizer get(Context context) {
            if (synthesizer == null) {
                synthesizer = new DoubaoSpeechSynthesizer(context.getApplicationContext());
            }
            return synthesizer;
        }

        synchronized DoubaoSpeechSynthesizer getCurrent() {
            return synthesizer;
        }

        synchronized DoubaoSpeechSynthesizer detach() {
            DoubaoSpeechSynthesizer current = synthesizer;
            synthesizer = null;
            return current;
        }
    }
}
