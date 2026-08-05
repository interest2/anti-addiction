package com.book.mask.challenge.retelling;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import com.book.mask.challenge.ChallengeSession;
import com.book.mask.constant.QuestionConst;
import com.book.mask.floating.FloatService;
import com.book.mask.personalize.ChallengeSettingsManager;
import com.book.mask.personalize.RetellingRecord;
import com.book.mask.personalize.RetellingRecordStore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 复述题答题会话：状态机与流程编排。
 *
 * <p>流程：加载故事 → 限时阅读 → 清空原文 → 录音（透明 Activity）→ 本地识别 → 大模型评分 → 达标关闭。
 * sessionId 贯穿一次答题，异步识别 / 评分返回时先判断是否仍是当前会话，防止取消后旧请求继续改界面。
 */
public final class RetellingChallengeSession implements ChallengeSession {

    private static final String TAG = "RetellingSession";
    private static final int RECORD_SAMPLE_RATE = 16000;
    private static final int SCORE_MAX_RETRY = 1;

    public interface Callbacks {
        void onPassed();

        void onCancel();

    }

    private final Context context;
    private final FloatService service;
    private final RetellingViewController view;
    private final RetellingStoryRepository storyRepository;
    private final RetellingEvaluator evaluator;
    private final SpeechTranscriber transcriber;
    private final Handler handler;
    private final ExecutorService executor;
    private final Callbacks callbacks;

    private RetellingState state = RetellingState.ERROR;
    private long sessionId;
    private boolean active;
    private RetellingStoryRepository.Story currentStory;
    private int storyLength;
    private int displaySeconds;
    private int passScore;
    private int recordMaxSeconds;
    private int countdownTicks;
    private String lastRecognizedText;
    private int scoreRetryCount;

    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            onCountdownTick();
        }
    };

    public RetellingChallengeSession(
            Context context,
            View floatingView,
            FloatService service,
            Callbacks callbacks) {
        this.context = context;
        this.service = service;
        this.callbacks = callbacks;
        this.view = new RetellingViewController(
                context,
                floatingView,
                new RetellingViewController.Callbacks() {
                    @Override
                    public void onStartRecord() {
                        startRecording();
                    }

                    @Override
                    public void onDone() {
                        handleDone();
                    }

                    @Override
                    public void onCancel() {
                        cancel();
                    }
                });
        this.storyRepository = new RetellingStoryRepository(context);
        this.evaluator = new RetellingEvaluator(context);
        // 长录音按句子间隙切成 20-30 秒单元逐段识别，避免整段一次识别质量下降
        this.transcriber = new RetellingChunkedTranscriber(
                SherpaOnnxTranscriber.getInstance(context));
        this.handler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
    }

    public boolean start() {
        sessionId++;
        active = true;
        scoreRetryCount = 0;
        ChallengeSettingsManager settings = new ChallengeSettingsManager(context);
        storyLength = settings.getRetellingStoryLength();
        displaySeconds = settings.getRetellingDisplaySeconds();
        passScore = settings.getRetellingPassScore();
        recordMaxSeconds = QuestionConst.retellingRecordSeconds(storyLength);
        Log.d(TAG, "复述题参数: 故事字数=" + storyLength
                + ", 展示秒=" + displaySeconds
                + ", 通过分=" + passScore
                + ", 录音上限=" + recordMaxSeconds + "s");
        setState(RetellingState.LOADING_STORY);
        loadStory(false);
        return true;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void cancel() {
        if (!active) {
            return;
        }
        active = false;
        sessionId++;
        handler.removeCallbacks(countdownRunnable);
        RetellingSessionRegistry.clear();
        if (service != null) {
            service.resumeFloatingWindowFromRecording();
        }
        view.hide();
        if (callbacks != null) {
            callbacks.onCancel();
        }
    }

    @Override
    public void destroy() {
        active = false;
        sessionId++;
        handler.removeCallbacks(countdownRunnable);
        RetellingSessionRegistry.clear();
        if (service != null) {
            service.resumeFloatingWindowFromRecording();
        }
        view.hide();
        executor.shutdownNow();
    }

    /**
     * 由 ChallengeManager 静默关闭复述题界面（如答题通过 / 切换算术题），不回调上层。
     */
    public void hideQuietly() {
        active = false;
        sessionId++;
        handler.removeCallbacks(countdownRunnable);
        RetellingSessionRegistry.clear();
        if (service != null) {
            service.resumeFloatingWindowFromRecording();
        }
        view.hide();
    }

    // ===== 录音 Activity 回调（跨界面，经 RetellingSessionRegistry） =====

    void onRecordingFinished(
            float[] samples, AudioCaptureManager.CaptureMetrics metrics) {
        if (!active || state != RetellingState.RECORDING) {
            return;
        }
        Log.d(TAG, "结束录音，" + (metrics == null
                ? "采样数=" + (samples == null ? 0 : samples.length)
                : metrics.toLogMessage()));
        resumeAfterRecording();
        if (samples == null || samples.length == 0) {
            setState(RetellingState.READY_TO_RECORD);
            view.showReadyToRecord("没有录到有效语音，请重录");
            return;
        }
        setState(RetellingState.TRANSCRIBING);
        view.showTranscribing();
        final long sid = sessionId;
        executor.execute(() -> {
            TranscriptionResult result = transcriber.transcribe(samples, RECORD_SAMPLE_RATE);
            handler.post(() -> {
                if (sid != sessionId || !active) {
                    return;
                }
                if (result.isSuccess()) {
                    onTranscribed(result.getText());
                } else {
                    onTranscriptionError(result.getErrorMessage());
                }
            });
        });
    }

    void onRecordingCancelled() {
        if (!active || state != RetellingState.RECORDING) {
            return;
        }
        Log.d(TAG, "结束录音（取消）");
        resumeAfterRecording();
        setState(RetellingState.READY_TO_RECORD);
        view.showReadyToRecord("已取消录音，可重新开始");
    }

    void onRecordingError(String message) {
        if (!active || state != RetellingState.RECORDING) {
            return;
        }
        Log.d(TAG, "录音出错：" + message);
        resumeAfterRecording();
        setState(RetellingState.READY_TO_RECORD);
        view.showReadyToRecord(message == null || message.isEmpty()
                ? "无法开始录音"
                : message);
    }

    private void resumeAfterRecording() {
        RetellingSessionRegistry.clear();
        if (service != null) {
            service.resumeFloatingWindowFromRecording();
        }
    }

    // ===== 状态流转 =====

    private void loadStory(final boolean advance) {
        final long sid = sessionId;
        executor.execute(() -> {
            RetellingStoryRepository.Story story = advance
                    ? storyRepository.advance(storyLength)
                    : storyRepository.obtainStory(storyLength);
            handler.post(() -> {
                if (sid != sessionId || !active) {
                    return;
                }
                if (story == null) {
                    handleLoadError();
                    return;
                }
                currentStory = story;
                beginReading();
            });
        });
    }

    private void beginReading() {
        setState(RetellingState.READING);
        if (!view.showStory(currentStory, displaySeconds)) {
            handleLoadError();
            return;
        }
        countdownTicks = displaySeconds;
        handler.removeCallbacks(countdownRunnable);
        handler.postDelayed(countdownRunnable, 1000);
    }

    private void onCountdownTick() {
        if (!active || state != RetellingState.READING) {
            return;
        }
        countdownTicks--;
        if (countdownTicks <= 0) {
            onReadingTimeUp();
        } else {
            view.updateCountdown(countdownTicks);
            handler.postDelayed(countdownRunnable, 1000);
        }
    }

    private void onReadingTimeUp() {
        setState(RetellingState.READY_TO_RECORD);
        view.showReadyToRecord();
    }

    private void startRecording() {
        if (!active
                || (state != RetellingState.READY_TO_RECORD
                && state != RetellingState.READING)) {
            return;
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            view.showRecordingUnavailable();
            return;
        }
        // 阅读阶段可提前开始复述：停止倒计时并清空原文，避免边看边念。
        if (state == RetellingState.READING) {
            handler.removeCallbacks(countdownRunnable);
            view.showReadyToRecord();
        }
        setState(RetellingState.RECORDING);
        Log.d(TAG, "开始录音");
        if (service != null) {
            service.suspendFloatingWindowForRecording();
        }
        RetellingSessionRegistry.setActiveSession(this);
        try {
            Intent intent = new Intent(context, RetellingRecordActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(RetellingRecordActivity.EXTRA_RECORD_MAX_SECONDS, recordMaxSeconds);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "启动录音 Activity 失败", e);
            resumeAfterRecording();
            setState(RetellingState.READY_TO_RECORD);
            view.showReadyToRecord("无法打开录音界面");
        }
    }

    private void onTranscriptionError(String message) {
        Log.w(TAG, "识别失败: " + message);
        setState(RetellingState.READY_TO_RECORD);
        boolean modelMissing = message != null && message.contains("模型缺失");
        view.showReadyToRecord(modelMissing
                ? "语音识别模型未就绪，请修复后重试"
                : "识别失败，请重录");
    }

    private void onTranscribed(String recognizedText) {
        lastRecognizedText = recognizedText;
        Log.d(TAG, "语音识别文本：" + (recognizedText == null ? "" : recognizedText));
        setState(RetellingState.SCORING);
        view.showRecognizedText(recognizedText);
        view.showScoring();
        runEvaluation();
    }

    private void runEvaluation() {
        final long sid = sessionId;
        final String storyText = currentStory == null ? "" : currentStory.getStory();
        final String recognizedText = lastRecognizedText;
        executor.execute(() -> {
            RetellingEvaluator.EvaluationResult result =
                    evaluator.evaluate(storyText, recognizedText);
            handler.post(() -> {
                if (sid != sessionId || !active) {
                    return;
                }
                handleEvaluationResult(result);
            });
        });
    }

    private void handleEvaluationResult(RetellingEvaluator.EvaluationResult result) {
        switch (result.getKind()) {
            case RERECORD:
                setState(RetellingState.READY_TO_RECORD);
                view.showReadyToRecord("没有识别到有效语音，请重录");
                break;
            case TOO_SHORT:
                showResult(new RetellingScore(
                        0, 0, 0, 0, 0, "复述内容过短，请记住更多关键信息"), false);
                break;
            case SCORED:
                boolean passed = result.getScore().getScore() >= passScore;
                showResult(result.getScore(), passed);
                break;
            case ERROR:
                handleScoreError(result.getErrorMessage());
                break;
            default:
                break;
        }
    }

    private void handleScoreError(String message) {
        scoreRetryCount++;
        if (scoreRetryCount <= SCORE_MAX_RETRY) {
            Log.w(TAG, "评分失败，自动重试第 " + scoreRetryCount + " 次: " + message);
            setState(RetellingState.SCORING);
            view.showScoring();
            runEvaluation();
        } else {
            Log.w(TAG, "评分连续失败，等待用户手动进入下一题: " + message);
            showResult(new RetellingScore(
                    0, 0, 0, 0, 0, "评分失败，请点击下一题重新作答"), false);
        }
    }

    private void showResult(RetellingScore score, boolean passed) {
        saveRecord(score, passed);
        setState(passed ? RetellingState.PASSED : RetellingState.FAILED);
        view.showResult(score, passed);
    }

    /** 落地一条答题记录：原故事 + 识别文本 + 评分信息，供「答题记录」展示。 */
    private void saveRecord(RetellingScore score, boolean passed) {
        try {
            RetellingStoryRepository.Story story = currentStory;
            RetellingRecord record = new RetellingRecord();
            record.timestamp = System.currentTimeMillis();
            record.story = story == null ? "" : story.getStory();
            record.recognizedText = lastRecognizedText == null ? "" : lastRecognizedText;
            record.score = score.getScore();
            record.coverage = score.getCoverage();
            record.order = score.getOrder();
            record.accuracy = score.getAccuracy();
            record.expression = score.getExpression();
            record.feedback = score.getFeedback();
            record.passed = passed;
            record.storyId = story == null ? "" : story.getStoryId();
            new RetellingRecordStore().addRecord(record);
            Log.d(TAG, "已保存复述答题记录，得分=" + record.score + ", 通过=" + passed);
        } catch (Exception e) {
            Log.e(TAG, "保存复述答题记录失败", e);
        }
    }

    private void handleDone() {
        if (state == RetellingState.PASSED) {
            if (callbacks != null) {
                callbacks.onPassed();
            }
        } else if (state == RetellingState.FAILED) {
            startNewRound();
        }
    }

    private void startNewRound() {
        sessionId++;
        scoreRetryCount = 0;
        setState(RetellingState.LOADING_STORY);
        loadStory(true);
    }

    private void handleLoadError() {
        setState(RetellingState.ERROR);
        view.showError("获取故事失败，请重试");
    }

    private void setState(RetellingState newState) {
        Log.d(TAG, "状态: " + state + " -> " + newState);
        this.state = newState;
    }
}
