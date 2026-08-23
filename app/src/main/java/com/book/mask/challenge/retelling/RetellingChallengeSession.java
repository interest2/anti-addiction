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
import com.book.mask.personalize.RetellingAudioStore;
import com.book.mask.personalize.RetellingRecord;
import com.book.mask.personalize.RetellingRecordStore;
import com.book.mask.personalize.SoeConfigManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 复述题答题会话：状态机与流程编排。
 *
 * <p>流程：加载故事 → 限时阅读 → 清空原文 → 录音（透明 Activity，PCM 实时推给腾讯口语评测）→
 * 腾讯口语评测确认有效语音 + 发音分 → 本地模型转写 → 自定义模型服务商内容评分 → 合并达标关闭。
 * 内容评分只使用本地转写文本，腾讯转写不参与评分，仅用于判断本次录音是否有效语音。
 * sessionId 贯穿一次答题，异步识别 / 评分返回时先判断是否仍是当前会话，防止取消后旧请求继续改界面。
 *
 * <p>评分依赖三处，任一不可用即评分失败、不写记录：腾讯口语评测（有效语音判断 + 发音分）、
 * 本地语音识别（内容评分文本）与用户自定义模型服务商（内容四维评分）。
 */
public final class RetellingChallengeSession implements ChallengeSession {

    private static final String TAG = "RetellingSession";
    private static final int RECORD_SAMPLE_RATE = 16000;
    private static final int SCORE_MAX_RETRY = 1;
    /** 等待腾讯口语评测最终结果的超时时间，超时未回则直接评分失败，避免会话卡死。 */
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
    /** 本地识别专用线程池：SOE 确认有效语音后在此线程做本地转写，结果即内容评分实际使用的文本。 */
    private final ExecutorService offlineExecutor;
    private final Callbacks callbacks;

    private RetellingState state = RetellingState.ERROR;
    private long sessionId;
    private boolean active;
    /** 本次答题会话是否已通过（本次或此前一次）。一旦通过即置真并保持，后续练习答错也不丢失解禁资格。 */
    private boolean sessionEverPassed;
    private RetellingStoryRepository.Story currentStory;
    private int storyLength;
    private int displaySeconds;
    private int passScore;
    private int recordMaxSeconds;
    private int countdownTicks;
    private String lastRecognizedText;
    private int scoreRetryCount;
    /** 腾讯口语评测：仅用于发音分与有效语音判断，未配置 / 失败时直接评分失败；内容评分文本改用本地识别（见 offlineTranscript）。 */
    private SoeSpeechEvaluator soeEvaluator;
    private float soeAccuracy = -1f;
    private float soeFluency = -1f;
    private float soeSuggestedScore = -1f;
    private boolean soeEvaluated;
    private boolean soeResultReceived;
    /** SOE 本次录音采样，转写成功后交给本地模型识别；SOE 失败时作废。 */
    private float[] pendingFallbackSamples;
    /** 本地离线（Sherpa）识别文本，复述内容评分实际使用的文本，同时供悬浮窗展示。 */
    private String offlineTranscript;
    /** 本次录音的原始采样，评分出结果后随记录落盘为 WAV；会话结束 / 重录时置空。 */
    private float[] recordedSamples;

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
        sessionEverPassed = false;
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
        // 后台出题（自定义服务商走大模型网络请求，可能数秒）期间先亮出「出题中」，避免界面卡住无反馈
        view.showLoading();
        // 每次点击关闭发起答题都换一篇新故事（advance 消费预取的下一篇，无预取则同步取新），
        // 不复用单例仓库里残留的上一次会话故事
        loadStory(true);
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
        recordedSamples = null;
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
        recordedSamples = null;
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
        recordedSamples = null;
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
            recordedSamples = null;
            closeSoe();
            setState(RetellingState.READY_TO_RECORD);
            view.showReadyToRecord("没有录到有效语音，请重录");
            return;
        }
        // 保留本次采样，评分出结果后随记录落盘为 WAV，供答题记录回放
        recordedSamples = samples;
        setState(RetellingState.TRANSCRIBING);
        view.showTranscribing();
        if (soeEvaluator != null && soeEvaluator.isActive()) {
            // SOE 路径：发送结束消息，等 final 结果（转写 + 发音分）
            pendingFallbackSamples = samples;
            soeResultReceived = false;
            handler.postDelayed(soeTimeoutRunnable, SOE_RESULT_TIMEOUT_MS);
            soeEvaluator.finish();
        } else {
            soeFailed("腾讯口语评测不可用");
        }
    }

    private void onSoeTimeout() {
        if (!active || state != RetellingState.TRANSCRIBING || soeResultReceived) {
            return;
        }
        Log.w(TAG, "SOE 评测超时");
        soeFailed("腾讯口语评测超时");
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
            Log.w(TAG, "SOE 转写为空");
            soeFailed("未识别到语音，请重录");
            return;
        }
        // 先取走样本再 settle（settle 会把 pendingFallbackSamples 置空），供本地识别使用
        float[] samples = pendingFallbackSamples;
        settleSoeResult();
        runLocalTranscription(samples);
    }

    private void handleSoeError(String message) {
        if (!active || state != RetellingState.TRANSCRIBING || soeResultReceived) {
            return;
        }
        Log.w(TAG, "SOE 评测失败: " + message);
        soeFailed("腾讯口语评测失败：" + message);
    }

    /**
     * 腾讯口语评测不可用（未配置 / 连接失败 / 超时 / 转写为空）：评分没有数据来源，
     * 直接作废本次录音并提示失败，不写答题记录，可重新录音。
     */
    private void soeFailed(String detail) {
        soeResultReceived = true;
        handler.removeCallbacks(soeTimeoutRunnable);
        closeSoe();
        pendingFallbackSamples = null;
        recordedSamples = null;
        setState(RetellingState.READY_TO_RECORD);
        view.showReadyToRecord("评分失败：" + detail);
    }

    /**
     * 腾讯口语评测确认本次录音是有效语音后，改用本地离线模型重新识别同一段录音；内容评分
     * 使用的是这里的识别结果，不使用腾讯转写（腾讯转写只做了前面的有效性判断）。
     * 识别失败或结果为空则请求重录，不再调用大模型评分。
     */
    private void runLocalTranscription(float[] samples) {
        if (samples == null || samples.length == 0) {
            recordedSamples = null;
            setState(RetellingState.READY_TO_RECORD);
            view.showReadyToRecord("没有录到有效语音，请重录");
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
                showOfflineTranscript();
                if (offlineTranscript == null || offlineTranscript.trim().isEmpty()) {
                    Log.w(TAG, "本地识别失败或为空"
                            + (result.isSuccess() ? "" : "：" + result.getErrorMessage()));
                    recordedSamples = null;
                    setState(RetellingState.READY_TO_RECORD);
                    view.showReadyToRecord(result.isSuccess()
                            ? "没有识别到有效语音，请重录"
                            : "本地语音识别失败：" + result.getErrorMessage());
                    return;
                }
                onTranscribed(offlineTranscript);
            });
        });
    }

    private void showOfflineTranscript() {
        if (!active || state == RetellingState.RECORDING) {
            return;
        }
        view.showOfflineTranscript(offlineTranscript);
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
                && state != RetellingState.READING
                && state != RetellingState.FAILED
                && state != RetellingState.PASSED)) {
            return;
        }
        // 阅读阶段可提前开始复述：停止倒计时并清空原文，避免边看边念。
        // 重答本题（未达标 / 已通过后再练同题）：同样清空原文与上一轮识别文本、重置评分重试计数，重新进入录音流程。
        if (state == RetellingState.READING
                || state == RetellingState.FAILED
                || state == RetellingState.PASSED) {
            handler.removeCallbacks(countdownRunnable);
            lastRecognizedText = null;
            scoreRetryCount = 0;
            view.showReadyToRecord();
        }
        // 评分唯一依赖腾讯口语评测，未配置时直接提示，不进入录音
        if (!new SoeConfigManager(context).isConfigured()) {
            setState(RetellingState.READY_TO_RECORD);
            view.showReadyToRecord("未配置腾讯口语评测，无法评分");
            return;
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

    /** 初始化腾讯口语评测。调用前须已确认凭据配置，启动失败经 onError 回调处理。 */
    private void initSoeForRecording() {
        soeAccuracy = -1f;
        soeFluency = -1f;
        soeSuggestedScore = -1f;
        soeEvaluated = false;
        soeResultReceived = false;
        offlineTranscript = null;
        handler.removeCallbacks(soeTimeoutRunnable);
        pendingFallbackSamples = null;
        closeSoe();
        soeEvaluator = new SoeSpeechEvaluator(new SoeConfigManager(context));
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

    private void onTranscribed(String recognizedText) {
        lastRecognizedText = recognizedText;
        Log.d(TAG, "本地识别文本：" + (recognizedText == null ? "" : recognizedText));
        setState(RetellingState.SCORING);
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
        if (passed) {
            sessionEverPassed = true;
        }
        setState(passed ? RetellingState.PASSED : RetellingState.FAILED);
        // 一旦通过过（本次或此前），主按钮恒为「答题通过」解禁；从未通过且答错时为「下一题」换新故事
        view.showResult(score, sessionEverPassed);
    }

    /** 落地一条答题记录：原故事 + 识别文本 + 评分信息 + 录音文件，供「答题记录」展示与回放。 */
    private void saveRecord(RetellingScore score, boolean passed) {
        try {
            RetellingStoryRepository.Story story = currentStory;
            RetellingRecord record = new RetellingRecord();
            long now = System.currentTimeMillis();
            record.timestamp = now;
            record.story = story == null ? "" : story.getStory();
            // 本地识别文本即评分依据，写入答题记录供回看；腾讯转写不参与评分，不存储
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
            // 本次录音落盘为 WAV（失败不阻断记录保存，audioPath 保持空串）
            record.audioPath = new RetellingAudioStore(context).save(recordedSamples, now);
            recordedSamples = null;
            new RetellingRecordStore(context).addRecord(record);
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
        if (sessionEverPassed) {
            // 已通过（本次或此前）：主按钮为「答题通过」，解禁关闭
            if (callbacks != null) {
                callbacks.onPassed();
            }
        } else if (state == RetellingState.FAILED) {
            // 从未通过：主按钮为「下一题」，换新故事重来
            startNewRound();
        }
    }

    private void startNewRound() {
        sessionId++;
        scoreRetryCount = 0;
        setState(RetellingState.LOADING_STORY);
        // 换新故事同样可能走网络出题，先提示「出题中」
        view.showLoading();
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
