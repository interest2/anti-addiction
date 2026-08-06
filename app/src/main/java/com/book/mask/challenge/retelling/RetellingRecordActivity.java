package com.book.mask.challenge.retelling;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.book.mask.R;
import com.book.mask.constant.QuestionConst;

import java.util.Locale;

/**
 * 复述题录音 Activity。
 *
 * <p>透明主题，启动后本应用处于前台，可稳定取得麦克风「使用期间」权限。目标 APP 仍在背景可见，
 * 视觉上接近悬浮窗。录音数据保存在内存，结束即交回 {@link RetellingChallengeSession}，不保存录音文件；
 * 若会话已配置腾讯口语评测，则采集到的 PCM 分块会实时上传进行发音评测。
 */
public class RetellingRecordActivity extends AppCompatActivity {

    private static final String TAG = "RetellingRecord";
    private static final long TICK_MS = 1000L;

    /** 录音上限（秒），由会话按故事长度计算后传入；缺省回退默认 45 秒。 */
    public static final String EXTRA_RECORD_MAX_SECONDS = "record_max_seconds";

    private AudioCaptureManager audioCapture;
    private TextView timerText;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int elapsedSeconds;
    private int maxSeconds;
    private boolean finishing;
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            elapsedSeconds++;
            timerText.setText(formatTime(elapsedSeconds));
            if (elapsedSeconds >= maxSeconds) {
                finishRecording();
                return;
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_retelling_record);

        maxSeconds = getIntent().getIntExtra(
                EXTRA_RECORD_MAX_SECONDS, QuestionConst.RETELLING_RECORD_MAX_SECONDS);
        Log.d(TAG, "录音上限=" + maxSeconds + "s");

        timerText = findViewById(R.id.tv_record_timer);
        Button finishButton = findViewById(R.id.btn_record_finish);
        Button cancelButton = findViewById(R.id.btn_record_cancel);
        finishButton.setOnClickListener(v -> finishRecording());
        cancelButton.setOnClickListener(v -> cancelRecording());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "录音权限缺失，无法录音");
            notifyError("缺少麦克风权限");
            finish();
            return;
        }

        audioCapture = new AudioCaptureManager(maxSeconds);
        if (!audioCapture.start()) {
            Log.e(TAG, "启动录音失败");
            notifyError("无法开始录音");
            finish();
            return;
        }
        RetellingChallengeSession session = RetellingSessionRegistry.getActiveSession();
        if (session != null) {
            session.attachRecordingListener(audioCapture);
        }

        timerText.setText(formatTime(0));
        handler.postDelayed(ticker, TICK_MS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(ticker);
        if (audioCapture != null) {
            audioCapture.release();
            audioCapture = null;
        }
    }

    @Override
    public void onBackPressed() {
        // 返回键等同取消，不判通过
        cancelRecording();
    }

    private void finishRecording() {
        if (finishing) {
            return;
        }
        finishing = true;
        handler.removeCallbacks(ticker);
        audioCapture.stop(new AudioCaptureManager.Callback() {
            @Override
            public void onCaptured(float[] samples, AudioCaptureManager.CaptureMetrics metrics) {
                RetellingChallengeSession session = RetellingSessionRegistry.getActiveSession();
                RetellingSessionRegistry.clear();
                if (session != null) {
                    session.onRecordingFinished(samples, metrics);
                }
                finish();
            }

            @Override
            public void onError(String message) {
                RetellingChallengeSession session = RetellingSessionRegistry.getActiveSession();
                RetellingSessionRegistry.clear();
                if (session != null) {
                    session.onRecordingError(message);
                }
                finish();
            }
        });
    }

    private void cancelRecording() {
        if (finishing) {
            return;
        }
        finishing = true;
        handler.removeCallbacks(ticker);
        if (audioCapture != null) {
            audioCapture.cancel();
        }
        RetellingChallengeSession session = RetellingSessionRegistry.getActiveSession();
        RetellingSessionRegistry.clear();
        if (session != null) {
            session.onRecordingCancelled();
        }
        finish();
    }

    private void notifyError(String message) {
        RetellingChallengeSession session = RetellingSessionRegistry.getActiveSession();
        if (session != null) {
            session.onRecordingError(message);
        }
    }

    private static String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }
}
