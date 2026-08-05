package com.book.mask.challenge.listening;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import com.book.mask.R;

import java.io.File;

/**
 * 听力音频播放器。
 *
 * <p>出题时调用 {@link #prepare} 用豆包语音（火山引擎 Speech SDK）后台合成听力原文；用户点击
 * 「播放 / 重听」时 {@link #play} 优先播放合成好的 wav，尚未就绪或未配置凭据时回退内置占位 mp3，
 * 保证听力题始终可用。
 */
final class ListeningAudioPlayer {

    private static final String TAG = "ListeningAudio";

    private final ListeningSynthesizerHolder synthesizerHolder = new ListeningSynthesizerHolder();

    private MediaPlayer mediaPlayer;
    private File audioFile;
    private boolean realAudioAvailable;
    private boolean released;

    /**
     * 后台合成听力原文，为「播放 / 重听」准备真实音频。
     */
    void prepare(Context context, String transcript) {
        released = false;
        audioFile = null;
        realAudioAvailable = false;
        final DoubaoSpeechSynthesizer synthesizer = synthesizerHolder.get(context);
        new Thread(() -> synthesizer.synthesize(transcript, new DoubaoSpeechSynthesizer.Callback() {
            @Override
            public void onAudioReady(File file) {
                audioFile = file;
                realAudioAvailable = true;
            }

            @Override
            public void onError(String message) {
                realAudioAvailable = false;
            }

            @Override
            public void onUnavailable(String reason) {
                realAudioAvailable = false;
            }
        }), "doubao-tts-prepare").start();
    }

    /**
     * 播放音频：真实合成已就绪则播真实 wav，否则播占位 mp3。
     */
    void play(Context context) {
        releaseMediaPlayer();
        if (realAudioAvailable && audioFile != null && audioFile.exists()) {
            playFile(audioFile);
        } else {
            playPlaceholder(context);
        }
    }

    void stop() {
        releaseMediaPlayer();
        synthesizerHolder.stop();
    }

    void release() {
        released = true;
        releaseMediaPlayer();
        synthesizerHolder.destroy();
    }

    // ===== 内部实现 =====

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

    private void playFile(File file) {
        try {
            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            player.setDataSource(file.getAbsolutePath());
            player.setOnPreparedListener(mp -> mp.start());
            player.setOnCompletionListener(mp -> releaseMediaPlayer());
            player.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "播放合成音频出错 what=" + what + ", extra=" + extra);
                releaseMediaPlayer();
                return true;
            });
            player.prepareAsync();
            mediaPlayer = player;
            Log.d(TAG, "播放豆包合成音频");
        } catch (Exception e) {
            Log.e(TAG, "播放合成音频异常", e);
            releaseMediaPlayer();
        }
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
