package com.book.mask.challenge.retelling;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import com.book.mask.challenge.ChallengeSession;
import com.book.mask.challenge.retelling.soe.SoeSpeechEvaluator;
import com.book.mask.constant.QuestionConst;
import com.book.mask.floating.FloatService;
import com.book.mask.personalize.AnswerOverviewRecord;
import com.book.mask.personalize.AnswerOverviewStore;
import com.book.mask.personalize.ChallengeSettingsManager;
import com.book.mask.personalize.RetellingRecord;
import com.book.mask.personalize.RetellingRecordStore;
import com.book.mask.personalize.SoeConfigManager;

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
    /** 等待腾讯口语评测最终结果的超时时间，超时未回则回退本地识别，避免会话卡死。 */
    private static final long SOE_RESULT_TIMEOUT_MS = 20000L;

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
    /** 离线对比识别专用线程池：SOE 在线转写成功时额外跑一次本地识别，不阻塞主流程评分 / 取故事。 */
    private final ExecutorService offlineExecutor;
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
    /** 腾讯口语评测（可选）：未配置 / 失败时回退本地 Sherpa 识别 + 纯文本评分。 */
    private SoeConfigManager soeConfig;
    private SoeSpeechEvaluator soeEvaluator;
    private float soeAccuracy = -1f;
    private float soeFluency = -1f;
    private float soeSuggestedScore = -1f;
    private boolean soeEvaluated;
    private boolean soeResultReceived;
    /** SOE 失败回退本地识别所需的本次录音采样。 */
    private float[] pendingFallbackSamples;
    /** 腾讯在线（SOE）转写文本，非 null 表示本次评分/展示使用的是在线转写，用于与离线识别对比。 */
    private String onlineTranscript;
    /** 本地离线（Sherpa）识别文本，仅用于与在线转写对比准确性展示。 */
    private String offlineTranscript;

    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            onCountdownTick();
        }
    };

    private final Runnable soeTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            onSoeTimeout();
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
        this.offlineExecutor = Executors.newSingleThreadExecutor();
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
        handler.removeCallbacks(soeTimeoutRunnable);
        RetellingSessionRegistry.clear();
        closeSoe();
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
        handler.removeCallbacks(soeTimeoutRunnable);
        RetellingSessionRegistry.clear();
        closeSoe();
        if (service != null) {
            service.resumeFloatingWindowFromRecording();
        }
        view.hide();
        executor.shutdownNow();
        offlineExecutor.shutdownNow();
    }

    /**
     * 由 ChallengeManager 静默关闭复述题界面（如答题通过 / 切换算术题），不回调上层。
     */
    public void hideQuietly() {
        active = false;
        sessionId++;
        handler.removeCallbacks(countdownRunnable);
        handler.removeCallbacks(soeTimeoutRunnable);
        RetellingSessionRegistry.clear();
        closeSoe();
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
            closeSoe();
            setState(RetellingState.READY_TO_RECORD);
            view.showReadyToRecord("没有录到有效语音，请重录");
            return;
        }
        setState(RetellingState.TRANSCRIBING);
        view.showTranscribing();
        if (soeEvaluator != null && soeEvaluator.isActive()) {
            // SOE 路径：发送结束消息，等 final 结果（转写 + 发音分）；失败或超时回退本地识别
            pendingFallbackSamples = samples;
            soeResultReceived = false;
            handler.postDelayed(soeTimeoutRunnable, SOE_RESULT_TIMEOUT_MS);
            soeEvaluator.finish();
        } else {
            runLocalTranscription(samples);
        }
    }

    private void onSoeTimeout() {
        if (!active || state != RetellingState.TRANSCRIBING || soeResultReceived) {
            return;
        }
        Log.w(TAG, "SOE 评测超时，回退本地识别");
        fallbackToLocalTranscription(false);
    }

    private void runLocalTranscription(float[] samples) {
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

    private void handleSoeResult(SoeSpeechEvaluator.SoeResult result) {
        if (!active || state != RetellingState.TRANSCRIBING || soeResultReceived) {
            return;
        }
        soeAccuracy = result.getAccuracy();
        soeFluency = result.getFluency();
        soeSuggestedScore = result.getSuggestedScore();
        soeEvaluated = result.hasPronunciationScore();
        String transcript = result.getTranscript();
        if (transcript == null || transcript.trim().isEmpty()) {
            Log.w(TAG, "SOE 转写为空，回退本地识别");
            fallbackToLocalTranscription(soeEvaluated);
            return;
        }
        onlineTranscript = transcript;
        // 先取走样本再 settle（settle 会把 pendingFallbackSamples 置空），供离线识别对比
        float[] comparisonSamples = pendingFallbackSamples;
        settleSoeResult();
        onTranscribed(transcript);
        runOfflineComparison(comparisonSamples);
    }

    private void handleSoeError(String message) {
        if (!active || state != RetellingState.TRANSCRIBING || soeResultReceived) {
            return;
        }
        Log.w(TAG, "SOE 评测失败，回退本地识别: " + message);
        fallbackToLocalTranscription(false);
    }

    private void fallbackToLocalTranscription(boolean keepPronunciationScore) {
        onlineTranscript = null;
        float[] samples = pendingFallbackSamples;
        if (!keepPronunciationScore) {
            soeEvaluated = false;
        }
        settleSoeResult();
        runLocalTranscription(samples);
    }

    /**
     * 腾讯在线转写已到手时，额外用本地离线模型识别同一段录音，供对比在线 / 离线准确性。
     * 结果异步返回后更新识别文本区为两行对比；离线识别失败不影响已进行的在线评分。
     */
    private void runOfflineComparison(float[] samples) {
        if (samples == null || samples.length == 0) {
            return;
        }
        final long sid = sessionId;
        offlineExecutor.execute(() -> {
            TranscriptionResult result = transcriber.transcribe(samples, RECORD_SAMPLE_RATE);
            handler.post(() -> {
                if (sid != sessionId || !active) {
                    return;
                }
                offlineTranscript = result.isSuccess() ? result.getText() : null;
                showTranscriptComparison();
            });
        });
    }

    private void showTranscriptComparison() {
        if (!active || state == RetellingState.RECORDING) {
            return;
        }
        view.showTranscriptComparison(onlineTranscript, offlineTranscript);
    }

    private void settleSoeResult() {
        soeResultReceived = true;
        handler.removeCallbacks(soeTimeoutRunnable);
        closeSoe();
        pendingFallbackSamples = null;
    }

    void onRecordingCancelled() {
        if (!active || state != RetellingState.RECORDING) {
            return;
        }
        Log.d(TAG, "结束录音（取消）");
        resumeAfterRecording();
        closeSoe();
        setState(RetellingState.READY_TO_RECORD);
        view.showReadyToRecord("已取消录音，可重新开始");
    }

    void onRecordingError(String message) {
        if (!active || state != RetellingState.RECORDING) {
            return;
        }
        Log.d(TAG, "录音出错：" + message);
        resumeAfterRecording();
        closeSoe();
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
        initSoeForRecording();
        try {
            Intent intent = new Intent(context, RetellingRecordActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(RetellingRecordActivity.EXTRA_RECORD_MAX_SECONDS, recordMaxSeconds);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "启动录音 Activity 失败", e);
            closeSoe();
            resumeAfterRecording();
            setState(RetellingState.READY_TO_RECORD);
            view.showReadyToRecord("无法打开录音界面");
        }
    }

    /** 由录音 Activity 在启动采集后调用，把 PCM 流实时导向腾讯口语评测。 */
    void attachRecordingListener(AudioCaptureManager capture) {
        capture.setPcmChunkListener((chunk, length) -> {
            SoeSpeechEvaluator evaluator = soeEvaluator;
            if (evaluator != null) {
                evaluator.sendPcmChunk(chunk, length);
            }
        });
    }

    /** 初始化腾讯口语评测（仅当凭据已配置）。未配置 / 启动失败不影响本次录音。 */
    private void initSoeForRecording() {
        soeAccuracy = -1f;
        soeFluency = -1f;
        soeSuggestedScore = -1f;
        soeEvaluated = false;
        soeResultReceived = false;
        onlineTranscript = null;
        offlineTranscript = null;
        handler.removeCallbacks(soeTimeoutRunnable);
        pendingFallbackSamples = null;
        closeSoe();
        soeConfig = new SoeConfigManager(context);
        if (!soeConfig.isConfigured()) {
            Log.d(TAG, "未配置腾讯口语评测，本次复述走本地识别");
            return;
        }
        soeEvaluator = new SoeSpeechEvaluator(soeConfig);
        soeEvaluator.start(new SoeSpeechEvaluator.Listener() {
            @Override
            public void onResult(SoeSpeechEvaluator.SoeResult result) {
                handler.post(() -> handleSoeResult(result));
            }

            @Override
            public void onError(String message) {
                handler.post(() -> handleSoeError(message));
            }
        });
        Log.d(TAG, "已启动腾讯口语评测");
    }

    private void closeSoe() {
        if (soeEvaluator != null) {
            soeEvaluator.close();
            soeEvaluator = null;
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
        view.showRecognizedText(onlineTranscript != null ? "腾讯转写：" : null, recognizedText);
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
                RetellingScore finalScore = mergeSpeechScore(result.getScore());
                boolean passed = finalScore.getScore() >= passScore;
                showResult(finalScore, passed);
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
            // 把具体失败原因透传到反馈文案，便于直接在悬浮窗排查（同时写入答题记录）
            String detail = (message == null || message.trim().isEmpty())
                    ? "评分失败，请点击下一题重新作答"
                    : "评分失败：" + message;
            showResult(new RetellingScore(0, 0, 0, 0, 0, detail), false);
        }
    }

    /** 内容分与 SOE 语音分合并：总分 = 0.7×内容 + 0.3×语音。语音分不可用时仅内容分。 */
    private RetellingScore mergeSpeechScore(RetellingScore contentScore) {
        if (!soeEvaluated || soeAccuracy < 0f || soeFluency < 0f) {
            return contentScore;
        }
        // PronAccuracy 与 PronFluency 均已统一为 0-100 分制，直接取平均
        int speech = Math.round((soeAccuracy + soeFluency) / 2f);
        int content = contentScore.getScore();
        int merged = Math.round(content * 0.7f + speech * 0.3f);
        return new RetellingScore(
                merged,
                contentScore.getCoverage(),
                contentScore.getAccuracy(),
                contentScore.getOrder(),
                contentScore.getExpression(),
                contentScore.getFeedback(),
                soeAccuracy,
                soeFluency,
                soeSuggestedScore);
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
            long now = System.currentTimeMillis();
            record.timestamp = now;
            record.story = story == null ? "" : story.getStory();
            record.recognizedText = lastRecognizedText == null ? "" : lastRecognizedText;
            record.offlineRecognizedText = offlineTranscript == null ? "" : offlineTranscript;
            record.score = score.getScore();
            record.coverage = score.getCoverage();
            record.order = score.getOrder();
            record.accuracy = score.getAccuracy();
            record.expression = score.getExpression();
            record.feedback = score.getFeedback();
            record.passed = passed;
            record.storyId = story == null ? "" : story.getStoryId();
            record.pronunciationAccuracy = score.getPronunciationAccuracy();
            record.pronunciationFluency = score.getPronunciationFluency();
            record.suggestedScore = score.getSuggestedScore();
            new RetellingRecordStore().addRecord(record);
            AnswerOverviewRecord overview = new AnswerOverviewRecord();
            overview.type = AnswerOverviewRecord.TYPE_RETELLING;
            overview.timestamp = now;
            overview.passed = passed;
            new AnswerOverviewStore().addRecord(overview);
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
