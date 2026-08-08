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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 听力音频播放器。
 *
 * <p>出题时调用 {@link #prepare} 用豆包语音（火山引擎 Speech SDK）后台合成听力原文；用户点击
 * 「播放 / 重听」时 {@link #play} 优先播放合成好的音频，确定无法合成时才回退内置占位 mp3，
 * 保证听力题始终可用。
 */
final class ListeningAudioPlayer {

    private static final String TAG = "ListeningAudio";

    private enum AudioState {
        IDLE,
        PREPARING,
        REAL_AUDIO_READY,
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
    private List<File> audioFiles = Collections.emptyList();
    private int playingSegmentIndex;
    private AudioState audioState = AudioState.IDLE;
    private long prepareGeneration;
    private long playbackGeneration;
    /** 播放引导语所需的上下文（来自 prepare / play 入参）。 */
    private Context appContext;
    /** 整段播放结束（或播放失败兜底）时的回调，用于揭示题干与选项。 */
    private Runnable onPlaybackCompleted;

    /**
     * 后台合成听力原文，为「播放 / 重听」准备真实音频。
     */
    synchronized void prepare(Context context, String transcript, Runnable onPrepared) {
        this.appContext = context;
        audioFiles = Collections.emptyList();
        audioState = AudioState.PREPARING;
        final long generation = ++prepareGeneration;
        synthesizerExecutor.execute(() -> {
            if (!isCurrentPreparation(generation)) {
                return;
            }
            synthesizerHolder.get(context).synthesize(
                    transcript,
                    new DoubaoSpeechSynthesizer.Callback() {
                        @Override
                        public void onAudioReady(List<File> files) {
                            synchronized (ListeningAudioPlayer.this) {
                                if (!isCurrentPreparation(generation)) {
                                    return;
                                }
                                audioFiles = new ArrayList<>(files);
                                audioState = audioFiles.isEmpty()
                                        ? AudioState.PLACEHOLDER_READY
                                        : AudioState.REAL_AUDIO_READY;
                            }
                            Log.d(TAG, "豆包合成音频已就绪，segments=" + files.size());
                            notifyAudioPrepared(generation, onPrepared);
                        }

                        @Override
                        public void onError(String message) {
                            synchronized (ListeningAudioPlayer.this) {
                                if (!isCurrentPreparation(generation)) {
                                    return;
                                }
                                audioState = AudioState.PLACEHOLDER_READY;
                            }
                            Log.e(TAG, "豆包语音合成失败: " + message);
                            notifyAudioPrepared(generation, onPrepared);
                        }

                        @Override
                        public void onUnavailable(String reason) {
                            synchronized (ListeningAudioPlayer.this) {
                                if (!isCurrentPreparation(generation)) {
                                    return;
                                }
                                audioState = AudioState.PLACEHOLDER_READY;
                            }
                            Log.w(TAG, "豆包语音不可用: " + reason);
                            notifyAudioPrepared(generation, onPrepared);
                        }
                    });
        });
    }

    /**
     * 播放音频：等待合成结束后，播放真实合成音频或确定需要使用的占位音频。
     */
    synchronized void play(Context context) {
        if (audioState == AudioState.PREPARING || audioState == AudioState.IDLE) {
            Log.d(TAG, "听力音频尚未就绪，忽略播放请求");
            return;
        }
        this.appContext = context;
        releaseCurrentMediaPlayerAsync(true);
        playingSegmentIndex = 0;
        long generation = ++playbackGeneration;
        if (audioState == AudioState.REAL_AUDIO_READY && allAudioFilesExist()) {
            playCurrentSegment(generation);
        } else {
            audioState = AudioState.PLACEHOLDER_READY;
            playPlaceholder(context, generation);
        }
    }

    /**
     * 注册整段播放完成回调（播放失败时也会回调，避免题干 / 选项永久不揭示）。
     */
    synchronized void setOnPlaybackCompleted(Runnable listener) {
        this.onPlaybackCompleted = listener;
    }

    void stop() {
        synchronized (this) {
            prepareGeneration++;
            playbackGeneration++;
            audioFiles = Collections.emptyList();
            audioState = AudioState.IDLE;
            releaseCurrentMediaPlayerAsync(true);
        }
        synthesizerExecutor.execute(() -> {
            DoubaoSpeechSynthesizer synthesizer = synthesizerHolder.getCurrent();
            if (synthesizer != null) {
                synthesizer.stop();
            }
        });
    }

    void release() {
        synchronized (this) {
            prepareGeneration++;
            playbackGeneration++;
            audioFiles = Collections.emptyList();
            audioState = AudioState.IDLE;
            onPlaybackCompleted = null;
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

    // ===== 内部实现 =====

    private synchronized boolean isCurrentPreparation(long generation) {
        return generation == prepareGeneration;
    }

    private void notifyAudioPrepared(long generation, Runnable callback) {
        Runnable notification = () -> {
            synchronized (ListeningAudioPlayer.this) {
                if (!isCurrentPreparation(generation)) {
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
            player.setOnCompletionListener(mp -> {
                // 占位正文播完后，末尾追加引导语
                onSegmentFinished(mp, generation);
                playGuide(generation);
            });
            player.setOnErrorListener((mp, what, extra) -> {
                finishPlayback(mp, generation, false);
                return true;
            });
            mediaPlayer = player;
            player.prepareAsync();
            Log.d(TAG, "播放听力占位音频");
        } catch (Exception e) {
            Log.e(TAG, "播放占位音频异常", e);
            releaseCurrentMediaPlayerAsync(true);
            notifyPlaybackCompleted(generation);
        }
    }

    private boolean allAudioFilesExist() {
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

    private void playCurrentSegment(long generation) {
        File file;
        int segmentNumber;
        int segmentCount;
        synchronized (this) {
            if (generation != playbackGeneration || playingSegmentIndex >= audioFiles.size()) {
                file = null;
                segmentNumber = 0;
                segmentCount = 0;
            } else {
                file = audioFiles.get(playingSegmentIndex);
                segmentNumber = playingSegmentIndex + 1;
                segmentCount = audioFiles.size();
            }
        }
        if (file == null) {
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
                finishPlayback(mp, generation, false);
                return true;
            });
            synchronized (this) {
                mediaPlayer = player;
            }
            player.prepareAsync();
            Log.d(TAG, "播放豆包合成音频: segment=" + segmentNumber + "/" + segmentCount);
        } catch (Exception e) {
            Log.e(TAG, "播放合成音频异常", e);
            synchronized (this) {
                releaseCurrentMediaPlayerAsync(true);
            }
            notifyPlaybackCompleted(generation);
        }
    }

    private void playNextSegment(MediaPlayer completedPlayer, long generation) {
        boolean hasMoreSegments;
        synchronized (this) {
            if (mediaPlayer != completedPlayer || generation != playbackGeneration) {
                releaseMediaPlayerAsync(completedPlayer, false);
                return;
            }
            mediaPlayer = null;
            playingSegmentIndex++;
            hasMoreSegments = playingSegmentIndex < audioFiles.size();
        }
        releaseMediaPlayerAsync(completedPlayer, false);
        if (hasMoreSegments) {
            playCurrentSegment(generation);
        } else {
            // 最后一段正文播完后，末尾追加引导语
            playGuide(generation);
        }
    }

    /**
     * 一段音频（非末段）正常播完后的收尾：仅释放，不触发整段完成回调。
     */
    private void onSegmentFinished(MediaPlayer completedPlayer, long generation) {
        synchronized (this) {
            if (mediaPlayer == completedPlayer) {
                mediaPlayer = null;
            }
        }
        releaseMediaPlayerAsync(completedPlayer, false);
    }

    /**
     * 整段听力正文播完后，末尾追加一句引导语「请看提问，并做选择」；
     * 引导语播完（或播放失败）才回调整段播放完成，由界面揭示题干与选项。
     */
    private void playGuide(long generation) {
        Context context = appContext;
        if (context == null) {
            notifyPlaybackCompleted(generation);
            return;
        }
        try {
            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            Uri guideUri = Uri.parse(
                    "android.resource://" + context.getPackageName() + "/raw/listening_guide");
            player.setDataSource(context, guideUri);
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
            Log.d(TAG, "播放听力引导语");
        } catch (Exception e) {
            Log.e(TAG, "播放听力引导语异常", e);
            synchronized (this) {
                releaseCurrentMediaPlayerAsync(true);
            }
            notifyPlaybackCompleted(generation);
        }
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
