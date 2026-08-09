package com.book.mask.challenge.retelling;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.book.mask.R;

/**
 * 复述题答题界面（悬浮窗内）。负责复述题布局的懒加载、状态渲染与按钮回调。
 * 录音阶段在透明 {@link RetellingRecordActivity} 中完成，本控制器不展示录音计时。
 */
final class RetellingViewController {

    private static final String TAG = "RetellingView";

    interface Callbacks {
        void onStartRecord();

        void onDone();

        void onCancel();
    }

    private final Context context;
    private final View floatingView;
    private final Callbacks callbacks;

    private View retellingLayout;
    private TextView hintText;
    private TextView countdownText;
    private TextView storyText;
    private ScrollView storyScrollView;
    private Button recordButton;
    private Button cancelButton;
    private ScrollView recognizedScrollView;
    private TextView recognizedText;
    private LinearLayout resultLayout;
    private TextView scoreText;
    private TextView speechScoreText;
    private TextView feedbackText;
    private Button doneButton;
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
    private boolean topInfoVisibilityCaptured;
    private int topInfoVisibilityBeforeChallenge;
    private boolean strictReminderVisibilityCaptured;
    private int strictReminderVisibilityBeforeChallenge;

    RetellingViewController(Context context, View floatingView, Callbacks callbacks) {
        this.context = context;
        this.floatingView = floatingView;
        this.callbacks = callbacks;
    }

    boolean showStory(RetellingStoryRepository.Story story, int displaySeconds) {
        if (!ensureInitialized()) {
            return false;
        }
        currentStory = story;
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
        hintText.setText("请记住下面的故事，可随时开始复述");
        countdownText.setText("剩余 " + displaySeconds + " 秒");
        countdownText.setVisibility(View.VISIBLE);
        recordButton.setEnabled(true);
        recordButton.setText("开始录音");
        cancelButton.setVisibility(View.VISIBLE);
        return true;
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
        hintText.setText(hint);
        recordButton.setEnabled(true);
        recordButton.setText("开始录音");
    }

    void showTranscribing() {
        if (!initialized) {
            return;
        }
        recordButton.setEnabled(false);
        resultLayout.setVisibility(View.GONE);
        clearRecognizedText();
        hintText.setText("正在识别语音……");
    }

    void showScoring() {
        if (!initialized) {
            return;
        }
        hintText.setText("正在评估复述……");
    }

    /**
     * 展示语音识别的文字结果；空文本时隐藏。{@code source} 为 null 时用默认前缀「识别文本：」，
     * 否则原样作前缀（如「腾讯转写：」），用于区分在线 / 离线识别来源。
     */
    void showRecognizedText(String source, String text) {
        if (!initialized) {
            return;
        }
        if (text == null || text.trim().isEmpty()) {
            clearRecognizedText();
            return;
        }
        String prefix = (source == null || source.isEmpty()) ? "识别文本：" : source;
        recognizedText.setText(prefix + text.trim());
        recognizedScrollView.setVisibility(View.VISIBLE);
    }

    /**
     * 展示腾讯在线转写与本地离线识别两行对比，供核对识别准确性；在线转写为空时不做改动。
     */
    void showTranscriptComparison(String onlineText, String offlineText) {
        if (!initialized) {
            return;
        }
        if (onlineText == null || onlineText.trim().isEmpty()) {
            return;
        }
        StringBuilder builder = new StringBuilder("腾讯转写：").append(onlineText.trim());
        if (offlineText != null && !offlineText.trim().isEmpty()) {
            builder.append("\n离线识别：").append(offlineText.trim());
        }
        recognizedText.setText(builder.toString());
        recognizedScrollView.setVisibility(View.VISIBLE);
    }

    private void clearRecognizedText() {
        if (recognizedScrollView == null || recognizedText == null) {
            return;
        }
        recognizedText.setText("");
        recognizedScrollView.setVisibility(View.GONE);
    }

    void showResult(RetellingScore score, boolean passed) {
        if (!initialized) {
            return;
        }
        recordButton.setEnabled(false);
        cancelButton.setVisibility(View.GONE);
        hintText.setText("评分完成");
        scoreText.setText("复述得分：" + score.getScore() + "分");
        if (speechScoreText != null) {
            if (score.hasPronunciationScore()) {
                speechScoreText.setText(score.formatPronunciationSummary());
                speechScoreText.setVisibility(View.VISIBLE);
            } else {
                speechScoreText.setVisibility(View.GONE);
            }
        }
        feedbackText.setText(score.getFeedback());
        doneButton.setText(passed ? "答题通过" : "下一题");
        doneButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        passed ? 0xFF4CAF50 : 0xFFFF9800));
        showBreakdown();
        resultLayout.setVisibility(View.VISIBLE);
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
        recordButton.setEnabled(false);
        cancelButton.setVisibility(View.VISIBLE);
        hintText.setText(message == null || message.isEmpty() ? "出错了" : message);
        clearRecognizedText();
        resultLayout.setVisibility(View.GONE);
    }

    void hide() {
        if (!initialized) {
            return;
        }
        shown = false;
        retellingLayout.setVisibility(View.GONE);
        resultLayout.setVisibility(View.GONE);
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
        hintText = floatingView.findViewById(R.id.tv_retelling_hint);
        countdownText = floatingView.findViewById(R.id.tv_retelling_countdown);
        storyText = floatingView.findViewById(R.id.tv_retelling_story);
        storyScrollView = floatingView.findViewById(R.id.sv_retelling_story);
        recordButton = floatingView.findViewById(R.id.btn_retelling_record);
        cancelButton = floatingView.findViewById(R.id.btn_retelling_cancel);
        recognizedScrollView = floatingView.findViewById(R.id.sv_retelling_recognized);
        recognizedText = floatingView.findViewById(R.id.tv_retelling_recognized);
        resultLayout = floatingView.findViewById(R.id.layout_retelling_result);
        scoreText = floatingView.findViewById(R.id.tv_retelling_score);
        speechScoreText = floatingView.findViewById(R.id.tv_retelling_speech_score);
        feedbackText = floatingView.findViewById(R.id.tv_retelling_feedback);
        doneButton = floatingView.findViewById(R.id.btn_retelling_done);
        topInfoLayout = floatingView.findViewById(R.id.top_info_layout);
        strictReminderLayout = floatingView.findViewById(R.id.strict_reminder_layout);
        breakdownLayout = floatingView.findViewById(R.id.layout_retelling_breakdown);
        breakdownConflictText = floatingView.findViewById(R.id.tv_retelling_breakdown_conflict);
        breakdownActionText = floatingView.findViewById(R.id.tv_retelling_breakdown_action);
        breakdownOutcomeText = floatingView.findViewById(R.id.tv_retelling_breakdown_outcome);
        breakdownMoralText = floatingView.findViewById(R.id.tv_retelling_breakdown_moral);

        if (retellingLayout == null
                || hintText == null
                || storyText == null
                || storyScrollView == null
                || recordButton == null
                || cancelButton == null
                || recognizedScrollView == null
                || recognizedText == null
                || resultLayout == null
                || scoreText == null
                || feedbackText == null
                || doneButton == null
                || topInfoLayout == null) {
            Log.e(TAG, "复述题布局懒加载失败");
            return false;
        }

        // 复述题禁止复制选择原文
        storyText.setTextIsSelectable(false);
        storyText.setLongClickable(false);

        recordButton.setOnClickListener(v -> {
            if (callbacks != null) {
                callbacks.onStartRecord();
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
        return true;
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
