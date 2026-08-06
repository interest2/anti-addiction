package com.book.mask.challenge.listening;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import com.book.mask.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 听力音频播放器。
 *
 * <p>出题时调用 {@link #prepare} 用豆包语音（火山引擎 Speech SDK）后台合成听力原文；用户点击
 * 「播放 / 重听」时 {@link #play} 优先播放合成好的音频，尚未就绪或未配置凭据时回退内置占位 mp3，
 * 保证听力题始终可用。
 */
final class ListeningAudioPlayer {

    private static final String TAG = "ListeningAudio";

    private final ListeningSynthesizerHolder synthesizerHolder = new ListeningSynthesizerHolder();

    private MediaPlayer mediaPlayer;
    private List<File> audioFiles = Collections.emptyList();
    private int playingSegmentIndex;
    private boolean realAudioAvailable;
    private long prepareGeneration;

    /**
     * 后台合成听力原文，为「播放 / 重听」准备真实音频。
     */
    synchronized void prepare(Context context, String transcript) {
        audioFiles = Collections.emptyList();
        realAudioAvailable = false;
        final long generation = ++prepareGeneration;
        new Thread(() -> {
            synchronized (ListeningAudioPlayer.this) {
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
                                    realAudioAvailable = !audioFiles.isEmpty();
                                }
                                Log.d(TAG, "豆包合成音频已就绪，segments=" + files.size());
                            }

                            @Override
                            public void onError(String message) {
                                synchronized (ListeningAudioPlayer.this) {
                                    if (!isCurrentPreparation(generation)) {
                                        return;
                                    }
                                    realAudioAvailable = false;
                                }
                                Log.e(TAG, "豆包语音合成失败: " + message);
                            }

                            @Override
                            public void onUnavailable(String reason) {
                                synchronized (ListeningAudioPlayer.this) {
                                    if (!isCurrentPreparation(generation)) {
                                        return;
                                    }
                                    realAudioAvailable = false;
                                }
                                Log.w(TAG, "豆包语音不可用: " + reason);
                            }
                        });
            }
        }, "doubao-tts-prepare").start();
    }

    /**
     * 播放音频：真实合成已就绪则播合成音频，否则播占位 mp3。
     */
    synchronized void play(Context context) {
        releaseMediaPlayer();
        playingSegmentIndex = 0;
        if (realAudioAvailable && allAudioFilesExist()) {
            playCurrentSegment();
        } else {
            playPlaceholder(context);
        }
    }

    void stop() {
        synchronized (this) {
            prepareGeneration++;
            releaseMediaPlayer();
        }
        synthesizerHolder.stop();
    }

    void release() {
        synchronized (this) {
            prepareGeneration++;
            releaseMediaPlayer();
        }
        synthesizerHolder.destroy();
    }

    // ===== 内部实现 =====

    private boolean isCurrentPreparation(long generation) {
        return generation == prepareGeneration;
    }

    private void playPlaceholder(Context context) {
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
            player.setOnCompletionListener(mp -> releaseMediaPlayer());
            player.setOnErrorListener((mp, what, extra) -> {
                releaseMediaPlayer();
                return true;
            });
            player.prepareAsync();
            mediaPlayer = player;
            Log.d(TAG, "播放听力占位音频");
        } catch (Exception e) {
            Log.e(TAG, "播放占位音频异常", e);
            releaseMediaPlayer();
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

    private synchronized void playCurrentSegment() {
        if (playingSegmentIndex >= audioFiles.size()) {
            releaseMediaPlayer();
            return;
        }
        File file = audioFiles.get(playingSegmentIndex);
        int segmentNumber = playingSegmentIndex + 1;
        try {
            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            player.setDataSource(file.getAbsolutePath());
            player.setOnPreparedListener(mp -> mp.start());
            player.setOnCompletionListener(this::playNextSegment);
            player.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "播放合成音频出错 what=" + what + ", extra=" + extra);
                releaseCompletedPlayer(mp);
                return true;
            });
            player.prepareAsync();
            mediaPlayer = player;
            Log.d(TAG, "播放豆包合成音频: segment=" + segmentNumber + "/" + audioFiles.size());
        } catch (Exception e) {
            Log.e(TAG, "播放合成音频异常", e);
            releaseMediaPlayer();
        }
    }

    private synchronized void playNextSegment(MediaPlayer completedPlayer) {
        if (mediaPlayer != completedPlayer) {
            completedPlayer.release();
            return;
        }
        completedPlayer.release();
        mediaPlayer = null;
        playingSegmentIndex++;
        playCurrentSegment();
    }

    private synchronized void releaseCompletedPlayer(MediaPlayer completedPlayer) {
        if (mediaPlayer == completedPlayer) {
            mediaPlayer = null;
        }
        completedPlayer.release();
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (IllegalStateException ignored) {
                // 未启动或已释放时忽略
            }
            mediaPlayer.release();
            mediaPlayer = null;
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

        synchronized void stop() {
            if (synthesizer != null) {
                synthesizer.stop();
            }
        }

        synchronized void destroy() {
            if (synthesizer != null) {
                synthesizer.destroy();
                synthesizer = null;
            }
        }
    }
}
