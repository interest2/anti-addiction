package com.book.mask.challenge.retelling;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.book.mask.R;
import com.book.mask.challenge.retelling.evaluation.RetellingScore;
import com.book.mask.challenge.retelling.recording.RetellingRecordActivity;

import java.util.Locale;

/**
 * 复述题答题界面（悬浮窗内）。负责复述题布局的懒加载、状态渲染与按钮回调。
 * 录音阶段在透明 {@link RetellingRecordActivity} 中完成，本控制器不展示录音计时。
 */
final class RetellingViewController {

    private static final String TAG = "RetellingView";

    interface Callbacks {
        void onStartRecord();

        void onReplayAudio();

        void onDone();

        void onCancel();
    }

    private final Context context;
    private final View floatingView;
    private final Callbacks callbacks;

    private View retellingLayout;
    private TextView titleText;
    private TextView hintText;
    private TextView countdownText;
    private TextView storyText;
    private ScrollView storyScrollView;
    private Button recordButton;
    private Button replayButton;
    private Button cancelButton;
    private ScrollView recognizedScrollView;
    private TextView recognizedText;
    private LinearLayout resultLayout;
    private TextView scoreText;
    private TextView speechScoreText;
    private TextView dimensionText;
    private Button showOriginalButton;
    private TextView originalLabelText;
    private ScrollView originalScrollView;
    private TextView originalText;
    private TextView feedbackText;
    private Button doneButton;
    private LinearLayout recordHolder;
    private LinearLayout replayHolder;
    private LinearLayout buttonRow;
    private View topInfoLayout;
    private View strictReminderLayout;

    private RetellingStoryRepository.Story currentStory;
    private View breakdownLayout;
    private TextView breakdownConflictText;
    private TextView breakdownActionText;
    private TextView breakdownOutcomeText;
    private TextView breakdownMoralText;

    private boolean initialized;
    private boolean shown;
    private RetellingStimulusMode stimulusMode = RetellingStimulusMode.TEXT;
    private boolean topInfoVisibilityCaptured;
    private int topInfoVisibilityBeforeChallenge;
    private boolean strictReminderVisibilityCaptured;
    private int strictReminderVisibilityBeforeChallenge;

    RetellingViewController(Context context, View floatingView, Callbacks callbacks) {
        this.context = context;
        this.floatingView = floatingView;
        this.callbacks = callbacks;
    }

    /**
     * 出题加载态：后台拉取 / 生成故事期间先展示面板并提示「出题中」，避免点击关闭后界面卡住无反馈。
     * 隐藏其余内容、置灰并隐藏录音按钮，仅保留取消入口；故事就绪后由 {@link #showStory} 接管。
     */
    void showLoading(RetellingStimulusMode mode) {
        if (!ensureInitialized()) {
            return;
        }
        stimulusMode = mode == null ? RetellingStimulusMode.TEXT : mode;
        currentStory = null;
        updateTitle();
        shown = true;
        hideTopInfo();
        hideStrictReminder();
        retellingLayout.setVisibility(View.VISIBLE);
        retellingLayout.bringToFront();
        resultLayout.setVisibility(View.GONE);
        storyScrollView.setVisibility(View.GONE);
        storyText.setText("");
        countdownText.setVisibility(View.GONE);
        clearRecognizedText();
        hideBreakdown();
        hideOriginal();
        restoreRecordButton();
        restoreReplayButton();
        recordButton.setEnabled(false);
        recordButton.setVisibility(View.GONE);
        replayButton.setVisibility(View.GONE);
        setHintText(stimulusMode == RetellingStimulusMode.AUDIO
                ? "正在生成听述材料…"
                : "出题中…");
        cancelButton.setVisibility(View.VISIBLE);
    }

    boolean showStory(RetellingStoryRepository.Story story, int displaySeconds) {
        if (!ensureInitialized()) {
            return false;
        }
        currentStory = story;
        stimulusMode = RetellingStimulusMode.TEXT;
        updateTitle();
        shown = true;
        hideTopInfo();
        hideStrictReminder();
        retellingLayout.setVisibility(View.VISIBLE);
        retellingLayout.bringToFront();
        resultLayout.setVisibility(View.GONE);
        storyScrollView.setVisibility(View.VISIBLE);
        storyText.setText(story == null ? "" : story.getStory());
        storyText.setVisibility(View.VISIBLE);
        clearRecognizedText();
        hideBreakdown();
        hideOriginal();
        restoreRecordButton();
        restoreReplayButton();
        setHintText("请记住下面的故事，可随时开始复述");
        countdownText.setText("剩余 " + displaySeconds + " 秒");
        countdownText.setVisibility(View.VISIBLE);
        recordButton.setVisibility(View.VISIBLE);
        recordButton.setEnabled(true);
        recordButton.setText("开始录音");
        replayButton.setVisibility(View.GONE);
        cancelButton.setVisibility(View.VISIBLE);
        return true;
    }

    /** 听述材料已取得，进入严格 TTS 合成阶段；原文只保存在内存中，不在答题前显示。 */
    void showAudioPreparing(RetellingStoryRepository.Story story) {
        if (!ensureInitialized()) {
            return;
        }
        stimulusMode = RetellingStimulusMode.AUDIO;
        currentStory = story;
        updateTitle();
        shown = true;
        hideTopInfo();
        hideStrictReminder();
        retellingLayout.setVisibility(View.VISIBLE);
        retellingLayout.bringToFront();
        resultLayout.setVisibility(View.GONE);
        storyText.setText("");
        storyScrollView.setVisibility(View.GONE);
        countdownText.setVisibility(View.GONE);
        clearRecognizedText();
        hideBreakdown();
        hideOriginal();
        restoreRecordButton();
        restoreReplayButton();
        recordButton.setEnabled(false);
        recordButton.setVisibility(View.GONE);
        replayButton.setVisibility(View.GONE);
        cancelButton.setVisibility(View.VISIBLE);
        setHintText("正在生成听述音频……");
    }

    void showAudioPlaying() {
        if (!initialized) {
            return;
        }
        resultLayout.setVisibility(View.GONE);
        storyText.setText("");
        storyScrollView.setVisibility(View.GONE);
        countdownText.setVisibility(View.GONE);
        clearRecognizedText();
        hideBreakdown();
        hideOriginal();
        restoreRecordButton();
        restoreReplayButton();
        recordButton.setEnabled(false);
        recordButton.setVisibility(View.GONE);
        replayButton.setVisibility(View.GONE);
        cancelButton.setVisibility(View.VISIBLE);
        setHintText("正在播放听述音频，请认真听……");
    }

    void showAudioReadyToRecord() {
        showReadyToRecord("播放完成，请开始复述");
    }

    void showAudioError(String message) {
        if (!initialized) {
            return;
        }
        restoreRecordButton();
        restoreReplayButton();
        recordButton.setVisibility(View.VISIBLE);
        recordButton.setEnabled(true);
        recordButton.setText("重新合成");
        replayButton.setVisibility(View.GONE);
        cancelButton.setVisibility(View.VISIBLE);
        clearRecognizedText();
        hideBreakdown();
        hideOriginal();
        resultLayout.setVisibility(View.GONE);
        setHintText(message == null || message.trim().isEmpty()
                ? "听述音频不可用，请重新合成"
                : message);
    }

    void updateCountdown(int remainingSeconds) {
        if (!initialized) {
            return;
        }
        countdownText.setText("剩余 " + Math.max(0, remainingSeconds) + " 秒");
    }

    /**
     * 阅读时间结束后清空原文（setText("")，不只是隐藏），并启用录音按钮。
     */
    void showReadyToRecord() {
        showReadyToRecord("请开始复述");
    }

    void showReadyToRecord(String hint) {
        if (!initialized) {
            return;
        }
        storyText.setText("");
        storyScrollView.setVisibility(View.GONE);
        countdownText.setVisibility(View.GONE);
        clearRecognizedText();
        setHintText(hint);
        resultLayout.setVisibility(View.GONE);
        hideBreakdown();
        hideOriginal();
        restoreRecordButton();
        restoreReplayButton();
        recordButton.setVisibility(View.VISIBLE);
        recordButton.setEnabled(true);
        recordButton.setText("开始录音");
        replayButton.setVisibility(stimulusMode == RetellingStimulusMode.AUDIO
                ? View.VISIBLE
                : View.GONE);
        replayButton.setEnabled(true);
        cancelButton.setVisibility(View.VISIBLE);
    }

    void showTranscribing() {
        if (!initialized) {
            return;
        }
        recordButton.setEnabled(false);
        replayButton.setVisibility(View.GONE);
        resultLayout.setVisibility(View.GONE);
        clearRecognizedText();
        setHintText("正在识别语音……");
    }

    void showScoring() {
        if (!initialized) {
            return;
        }
        setHintText("正在评估复述……");
        replayButton.setVisibility(View.GONE);
    }

    /**
     * 展示本地离线识别文本（腾讯转写内容不在悬浮窗展示）；离线识别为空时不做改动。
     */
    void showOfflineTranscript(String offlineText) {
        if (!initialized) {
            return;
        }
        if (offlineText == null || offlineText.trim().isEmpty()) {
            return;
        }
        recognizedText.setText(highlightLabel("识别文本：", offlineText.trim()));
        recognizedScrollView.setVisibility(View.VISIBLE);
    }

    /** 阶段提示统一走这里：设置文本并恢复可见（评分完成后由 showResult 隐藏）。 */
    private void setHintText(String text) {
        hintText.setText(text);
        hintText.setVisibility(View.VISIBLE);
    }

    /** 引导词用高亮色（与「原文」一致），正文保持原色。 */
    private static CharSequence highlightLabel(String label, String content) {
        SpannableString spannable = new SpannableString(label + content);
        spannable.setSpan(new ForegroundColorSpan(0xFFFFC107), 0, label.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    private void clearRecognizedText() {
        if (recognizedScrollView == null || recognizedText == null) {
            return;
        }
        recognizedText.setText("");
        recognizedScrollView.setVisibility(View.GONE);
    }

    void showResult(RetellingScore score, boolean canUnlock) {
        if (!initialized) {
            return;
        }
        cancelButton.setVisibility(View.GONE);
        // 评分完成后不再需要阶段提示，隐藏占位，避免识别文本与标题之间留白
        hintText.setVisibility(View.GONE);
        storyScrollView.setVisibility(View.GONE);
        scoreText.setText((stimulusMode == RetellingStimulusMode.AUDIO
                ? "听述得分："
                : "复述得分：") + score.getScore() + "分");
        if (speechScoreText != null) {
            if (score.hasPronunciationScore()) {
                speechScoreText.setText(score.formatPronunciationSummary());
                speechScoreText.setVisibility(View.VISIBLE);
            } else {
                speechScoreText.setVisibility(View.GONE);
            }
        }
        feedbackText.setText(score.getFeedback());
        // 已通过（本次或此前通过）时主按钮解禁，否则为「下一题」换新故事
        doneButton.setText(canUnlock ? "答题通过" : "下一题");
        doneButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        canUnlock ? 0xFF4CAF50 : 0xFFFF9800));
        showBreakdown();
        prepareOriginalForResult();
        showDimensions(score);
        // 听述题成绩页把重听与直接重答拆开，用户可选择是否在新一轮前再次播放材料。
        if (stimulusMode == RetellingStimulusMode.AUDIO) {
            moveReplayButtonToResultRow();
            replayButton.setVisibility(View.VISIBLE);
            replayButton.setEnabled(true);
        } else {
            restoreReplayButton();
            replayButton.setVisibility(View.GONE);
        }
        moveRecordButtonToResultRow();
        recordButton.setVisibility(View.VISIBLE);
        recordButton.setEnabled(true);
        recordButton.setText(stimulusMode == RetellingStimulusMode.AUDIO
                ? "直接重答"
                : "重答本题");
        resultLayout.setVisibility(View.VISIBLE);
    }

    /** 听述题评分后只提供展开按钮；普通复述题继续直接展示原文。 */
    private void prepareOriginalForResult() {
        if (!hasOriginal()) {
            hideOriginal();
            return;
        }
        if (stimulusMode == RetellingStimulusMode.AUDIO) {
            hideOriginal();
            showOriginalButton.setVisibility(View.VISIBLE);
            return;
        }
        showOriginal();
    }

    /** 用户主动展开后展示故事原文，供结果对照。 */
    private void showOriginal() {
        if (showOriginalButton == null || originalLabelText == null
                || originalScrollView == null || originalText == null) {
            return;
        }
        boolean visible = hasOriginal();
        showOriginalButton.setVisibility(View.GONE);
        originalText.setText(visible ? currentStory.getStory() : "");
        originalLabelText.setVisibility(visible ? View.VISIBLE : View.GONE);
        originalScrollView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private boolean hasOriginal() {
        return currentStory != null
                && currentStory.getStory() != null
                && !currentStory.getStory().trim().isEmpty();
    }

    private void hideOriginal() {
        if (showOriginalButton != null) {
            showOriginalButton.setVisibility(View.GONE);
        }
        if (originalText != null) {
            originalText.setText("");
        }
        if (originalScrollView != null) {
            originalScrollView.setVisibility(View.GONE);
        }
        if (originalLabelText != null) {
            originalLabelText.setVisibility(View.GONE);
        }
    }

    /**
     * 内容评分四维明细与加权总分：内容完整度 40 / 逻辑连贯性 25 / 事实准确度 20 / 表达完整性 15。
     * 总分按权重重算（区别于含语音评测的最终得分）。
     * 异常场景（直接低分 / 评分失败，四维全 0）无参考价值，保持隐藏。
     */
    private void showDimensions(RetellingScore score) {
        if (dimensionText == null) {
            return;
        }
        if (score.getCoverage() + score.getOrder()
                + score.getAccuracy() + score.getExpression() <= 0) {
            dimensionText.setVisibility(View.GONE);
            return;
        }
        int contentTotal = Math.round(score.getCoverage() * 0.4f
                + score.getOrder() * 0.25f
                + score.getAccuracy() * 0.2f
                + score.getExpression() * 0.15f);
        dimensionText.setText(String.format(Locale.CHINA,
                "内容总分 %d\n内容完整度 %d · 逻辑连贯性 %d\n事实准确度 %d · 表达完整性 %d",
                contentTotal,
                score.getCoverage(), score.getOrder(), score.getAccuracy(), score.getExpression()));
        dimensionText.setVisibility(View.VISIBLE);
    }

    private void moveRecordButtonToResultRow() {
        moveActionButton(recordButton, buttonRow, 8, 0);
    }

    private void moveReplayButtonToResultRow() {
        moveActionButton(replayButton, buttonRow, 8, 0);
    }

    private void restoreRecordButton() {
        if (recordHolder == null || recordButton == null) {
            return;
        }
        moveActionButton(recordButton, recordHolder, 0, 8);
    }

    private void restoreReplayButton() {
        if (replayHolder == null || replayButton == null) {
            return;
        }
        moveActionButton(replayButton, replayHolder, 0, 8);
    }

    /** 把操作按钮整体从当前父容器移到目标容器（同一 View 不能有两个父节点，须先摘除）。 */
    private void moveActionButton(
            Button button, ViewGroup target, int marginLeftDp, int marginBottomDp) {
        if (target == null || button == null) {
            return;
        }
        ViewGroup parent = (ViewGroup) button.getParent();
        if (parent == target) {
            return;
        }
        if (parent != null) {
            parent.removeView(button);
        }
        int density = (int) (context.getResources().getDisplayMetrics().density + 0.5f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(marginLeftDp * density, 0, 0, marginBottomDp * density);
        target.addView(button, lp);
    }

    /** 展示三幕法拆解 + 寓意；故事未携带元数据时保持隐藏。 */
    private void showBreakdown() {
        if (breakdownLayout == null || currentStory == null) {
            return;
        }
        breakdownConflictText.setText(label("冲突", currentStory.getConflict()));
        breakdownActionText.setText(label("行动", currentStory.getAction()));
        breakdownOutcomeText.setText(label("结局", currentStory.getOutcome()));
        breakdownMoralText.setText(label("寓意", currentStory.getMoral()));
        boolean visible = currentStory.getConflict() != null
                || currentStory.getAction() != null
                || currentStory.getOutcome() != null
                || currentStory.getMoral() != null;
        breakdownLayout.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void hideBreakdown() {
        if (breakdownLayout != null) {
            breakdownLayout.setVisibility(View.GONE);
        }
    }

    private static String label(String prefix, String value) {
        return value == null || value.trim().isEmpty() ? null : prefix + "：" + value.trim();
    }

    void showError(String message) {
        if (!initialized) {
            return;
        }
        restoreRecordButton();
        restoreReplayButton();
        recordButton.setEnabled(false);
        recordButton.setVisibility(View.GONE);
        replayButton.setVisibility(View.GONE);
        cancelButton.setVisibility(View.VISIBLE);
        setHintText(message == null || message.isEmpty() ? "出错了" : message);
        clearRecognizedText();
        hideOriginal();
        resultLayout.setVisibility(View.GONE);
    }

    void hide() {
        if (!initialized) {
            return;
        }
        shown = false;
        retellingLayout.setVisibility(View.GONE);
        resultLayout.setVisibility(View.GONE);
        hideOriginal();
        replayButton.setVisibility(View.GONE);
        restoreRecordButton();
        restoreReplayButton();
        restoreTopInfo();
        restoreStrictReminder();
    }

    boolean isShown() {
        return shown;
    }

    private boolean ensureInitialized() {
        if (initialized) {
            return true;
        }
        if (floatingView == null) {
            return false;
        }

        ViewStub retellingStub = floatingView.findViewById(R.id.retelling_challenge_stub);
        if (retellingStub != null) {
            retellingStub.inflate();
        }

        retellingLayout = floatingView.findViewById(R.id.retelling_challenge_layout);
        titleText = floatingView.findViewById(R.id.tv_retelling_title);
        hintText = floatingView.findViewById(R.id.tv_retelling_hint);
        countdownText = floatingView.findViewById(R.id.tv_retelling_countdown);
        storyText = floatingView.findViewById(R.id.tv_retelling_story);
        storyScrollView = floatingView.findViewById(R.id.sv_retelling_story);
        recordButton = floatingView.findViewById(R.id.btn_retelling_record);
        replayButton = floatingView.findViewById(R.id.btn_retelling_replay);
        cancelButton = floatingView.findViewById(R.id.btn_retelling_cancel);
        recognizedScrollView = floatingView.findViewById(R.id.sv_retelling_recognized);
        recognizedText = floatingView.findViewById(R.id.tv_retelling_recognized);
        resultLayout = floatingView.findViewById(R.id.layout_retelling_result);
        scoreText = floatingView.findViewById(R.id.tv_retelling_score);
        speechScoreText = floatingView.findViewById(R.id.tv_retelling_speech_score);
        dimensionText = floatingView.findViewById(R.id.tv_retelling_dimensions);
        showOriginalButton = floatingView.findViewById(R.id.btn_retelling_show_original);
        originalLabelText = floatingView.findViewById(R.id.tv_retelling_original_label);
        originalScrollView = floatingView.findViewById(R.id.sv_retelling_original);
        originalText = floatingView.findViewById(R.id.tv_retelling_original);
        feedbackText = floatingView.findViewById(R.id.tv_retelling_feedback);
        doneButton = floatingView.findViewById(R.id.btn_retelling_done);
        recordHolder = floatingView.findViewById(R.id.layout_retelling_record_holder);
        replayHolder = floatingView.findViewById(R.id.layout_retelling_replay_holder);
        buttonRow = floatingView.findViewById(R.id.layout_retelling_button_row);
        topInfoLayout = floatingView.findViewById(R.id.top_info_layout);
        strictReminderLayout = floatingView.findViewById(R.id.strict_reminder_layout);
        breakdownLayout = floatingView.findViewById(R.id.layout_retelling_breakdown);
        breakdownConflictText = floatingView.findViewById(R.id.tv_retelling_breakdown_conflict);
        breakdownActionText = floatingView.findViewById(R.id.tv_retelling_breakdown_action);
        breakdownOutcomeText = floatingView.findViewById(R.id.tv_retelling_breakdown_outcome);
        breakdownMoralText = floatingView.findViewById(R.id.tv_retelling_breakdown_moral);

        if (retellingLayout == null
                || hintText == null
                || titleText == null
                || storyText == null
                || storyScrollView == null
                || recordButton == null
                || replayButton == null
                || cancelButton == null
                || recognizedScrollView == null
                || recognizedText == null
                || resultLayout == null
                || scoreText == null
                || dimensionText == null
                || showOriginalButton == null
                || originalLabelText == null
                || originalScrollView == null
                || originalText == null
                || feedbackText == null
                || doneButton == null
                || recordHolder == null
                || replayHolder == null
                || buttonRow == null
                || topInfoLayout == null) {
            Log.e(TAG, "复述题布局懒加载失败");
            return false;
        }

        // 复述题禁止复制选择原文
        storyText.setTextIsSelectable(false);
        storyText.setLongClickable(false);
        originalText.setLongClickable(false);

        showOriginalButton.setOnClickListener(v -> showOriginal());

        recordButton.setOnClickListener(v -> {
            if (callbacks != null) {
                callbacks.onStartRecord();
            }
        });
        replayButton.setOnClickListener(v -> {
            if (callbacks != null) {
                callbacks.onReplayAudio();
            }
        });
        cancelButton.setOnClickListener(v -> {
            if (callbacks != null) {
                callbacks.onCancel();
            }
        });
        doneButton.setOnClickListener(v -> {
            if (callbacks != null) {
                callbacks.onDone();
            }
        });

        initialized = true;
        updateTitle();
        return true;
    }

    private void updateTitle() {
        if (titleText != null) {
            titleText.setText(stimulusMode == RetellingStimulusMode.AUDIO ? "听述题" : "复述题");
        }
    }

    private void hideTopInfo() {
        if (!topInfoVisibilityCaptured) {
            topInfoVisibilityBeforeChallenge = topInfoLayout.getVisibility();
            topInfoVisibilityCaptured = true;
        }
        topInfoLayout.setVisibility(View.GONE);
    }

    private void restoreTopInfo() {
        if (!topInfoVisibilityCaptured) {
            return;
        }
        topInfoLayout.setVisibility(topInfoVisibilityBeforeChallenge);
        topInfoVisibilityCaptured = false;
    }

    private void hideStrictReminder() {
        if (strictReminderLayout == null) {
            return;
        }
        if (!strictReminderVisibilityCaptured) {
            strictReminderVisibilityBeforeChallenge = strictReminderLayout.getVisibility();
            strictReminderVisibilityCaptured = true;
        }
        strictReminderLayout.setVisibility(View.GONE);
    }

    private void restoreStrictReminder() {
        if (strictReminderLayout == null) {
            return;
        }
        strictReminderLayout.setVisibility(strictReminderVisibilityBeforeChallenge);
        strictReminderVisibilityCaptured = false;
    }
}
