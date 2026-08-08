package com.book.mask.challenge.listening;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.book.mask.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 听力题答题界面（悬浮窗内）。负责听力题布局的懒加载、状态渲染与按钮回调。
 *
 * <p>默认只展示题干与选项，听力原文隐藏；答错后可揭示原文，揭示后本题进入「无法解禁」态，只能换一题；
 * 答对后可回看原文，不影响解禁。
 */
final class ListeningChallengeViewController {

    private static final String TAG = "ListeningView";

    interface Callbacks {
        void onSubmit(String answerLetter);

        void onRevealTranscript();

        void onNextQuestion();

        void onCancel();
    }

    private final Context context;
    private final View floatingView;
    private final Callbacks callbacks;
    private final ListeningAudioPlayer audioPlayer = new ListeningAudioPlayer();

    private View listeningLayout;
    private TextView hintText;
    private TextView questionText;
    private ScrollView transcriptScrollView;
    private TextView transcriptText;
    private LinearLayout optionsContainer;
    private Button replayButton;
    private Button submitButton;
    private Button cancelButton;
    private Button revealButton;
    private Button nextButton;
    private TextView resultText;
    private View topInfoLayout;
    private View strictReminderLayout;

    private final List<TextView> optionViews = new ArrayList<>();
    private int selectedOptionIndex = -1;
    private boolean optionsEnabled = true;
    /** 题干与选项是否已在播放结束后揭示。 */
    private boolean questionRevealed;

    private boolean initialized;
    private boolean shown;
    private boolean topInfoVisibilityCaptured;
    private int topInfoVisibilityBeforeChallenge;
    private boolean strictReminderVisibilityCaptured;
    private int strictReminderVisibilityBeforeChallenge;

    ListeningChallengeViewController(Context context, View floatingView, Callbacks callbacks) {
        this.context = context;
        this.floatingView = floatingView;
        this.callbacks = callbacks;
    }

    /**
     * 展示听力题面板，进入「正在生成题目」的加载态。题目就绪后调用 {@link #showQuestion}。
     *
     * @return 是否成功展示
     */
    boolean show() {
        if (!ensureInitialized()) {
            return false;
        }
        shown = true;
        resetSelection();
        optionsEnabled = true;
        questionRevealed = false;
        hideTopInfo();
        hideStrictReminder();
        listeningLayout.setVisibility(View.VISIBLE);
        listeningLayout.bringToFront();
        questionText.setVisibility(View.GONE);
        transcriptScrollView.setVisibility(View.GONE);
        optionsContainer.setVisibility(View.GONE);
        replayButton.setVisibility(View.GONE);
        submitButton.setVisibility(View.GONE);
        cancelButton.setVisibility(View.VISIBLE);
        cancelButton.setText("取消");
        revealButton.setVisibility(View.GONE);
        nextButton.setVisibility(View.GONE);
        nextButton.setText("换一题");
        hideResult();
        hintText.setText("正在生成听力题…");
        return true;
    }

    /**
     * 展示一道新题目（初始或换题），回到可作答状态。
     */
    void showQuestion(ListeningQuestion question) {
        if (!initialized || !shown) {
            return;
        }
        optionsEnabled = true;
        resetSelection();
        questionRevealed = false;
        audioPlayer.stop();
        // 题干与选项待整段播放结束后才揭示，避免先读题再听音降低难度
        audioPlayer.setOnPlaybackCompleted(this::onPlaybackCompleted);
        questionText.setText(question == null ? "" : question.getQuestion());
        questionText.setVisibility(View.GONE);
        buildOptions(question);
        optionsContainer.setVisibility(View.GONE);
        transcriptScrollView.setVisibility(View.GONE);
        replayButton.setVisibility(View.VISIBLE);
        submitButton.setVisibility(View.GONE);
        cancelButton.setVisibility(View.VISIBLE);
        cancelButton.setText("取消");
        revealButton.setVisibility(View.GONE);
        revealButton.setText("显示听力原文");
        nextButton.setVisibility(View.GONE);
        nextButton.setText("换一题");
        setOptionsEnabled(false);
        hideResult();
        hintText.setText("正在生成听力音频…");
        hintText.setTextColor(Color.WHITE);
        // 后台用豆包语音合成听力原文；确定真实音频或占位回退可用后，才允许播放
        audioPlayer.prepare(
                context,
                question == null ? "" : question.getTranscript(),
                this::onAudioPrepared);
    }

    /**
     * 真实音频或占位回退准备完成后自动开始播放，无需用户点击。
     */
    void onAudioPrepared() {
        if (!initialized || !shown) {
            return;
        }
        replayButton.setEnabled(true);
        hintText.setText("正在播放听力音频…");
        hintText.setTextColor(Color.WHITE);
        audioPlayer.play(context);
    }

    /**
     * 整段播放结束后揭示题干与选项并允许作答。
     */
    void onPlaybackCompleted() {
        if (!initialized || !shown || questionRevealed) {
            return;
        }
        questionRevealed = true;
        questionText.setVisibility(View.VISIBLE);
        optionsContainer.setVisibility(View.VISIBLE);
        submitButton.setVisibility(View.VISIBLE);
        setOptionsEnabled(true);
        hintText.setText("请选择答案");
        hintText.setTextColor(Color.WHITE);
    }

    void showWrongAnswer() {
        if (!initialized || !shown) {
            return;
        }
        optionsEnabled = false;
        setOptionsEnabled(false);
        submitButton.setVisibility(View.GONE);
        cancelButton.setText("取消");
        revealButton.setText("显示听力原文");
        revealButton.setVisibility(View.VISIBLE);
        nextButton.setText("换一题");
        nextButton.setVisibility(View.VISIBLE);
        hintText.setText("答案错误");
        hintText.setTextColor(Color.WHITE);
        showResult("❌ 答案错误，可查看原文或换一题", Color.rgb(255, 87, 34));
    }

    void showTranscript(String transcript) {
        if (!initialized || !shown) {
            return;
        }
        transcriptText.setText(transcript == null ? "" : "【听力原文】\n" + transcript);
        transcriptScrollView.setVisibility(View.VISIBLE);
        revealButton.setVisibility(View.GONE);
        nextButton.setText("换一题");
        nextButton.setVisibility(View.VISIBLE);
        hintText.setText("已显示听力原文");
        hintText.setTextColor(Color.rgb(255, 193, 7));
        hideResult();
    }

    void showCorrectAnswer() {
        if (!initialized || !shown) {
            return;
        }
        setOptionsEnabled(false);
        // 答对后仍可重听听力音频
        replayButton.setEnabled(true);
        submitButton.setVisibility(View.GONE);
        cancelButton.setText("完成解禁");
        revealButton.setText("查看原文");
        revealButton.setVisibility(View.VISIBLE);
        nextButton.setVisibility(View.GONE);
        hintText.setText("答案正确");
        hintText.setTextColor(Color.rgb(76, 175, 80));
        showResult("✅ 答案正确！", Color.rgb(76, 175, 80));
    }

    /**
     * 答对后回看听力原文，不影响解禁；提供「完成」按钮关闭。
     */
    void showTranscriptAfterCorrect(String transcript) {
        if (!initialized || !shown) {
            return;
        }
        transcriptText.setText(transcript == null ? "" : "【听力原文】\n" + transcript);
        transcriptScrollView.setVisibility(View.VISIBLE);
        revealButton.setVisibility(View.GONE);
        cancelButton.setVisibility(View.GONE);
        nextButton.setText("立即解锁");
        replayButton.setEnabled(true);
        nextButton.setVisibility(View.VISIBLE);
        hideResult();
    }

    void showLoadError(String message) {
        if (!initialized || !shown) {
            return;
        }
        hintText.setText(message == null || message.isEmpty() ? "获取题目失败，请重试" : message);
        hintText.setTextColor(Color.WHITE);
        cancelButton.setVisibility(View.VISIBLE);
    }

    void hide() {
        if (!initialized) {
            return;
        }
        shown = false;
        audioPlayer.stop();
        listeningLayout.setVisibility(View.GONE);
        restoreTopInfo();
        restoreStrictReminder();
    }

    void destroy() {
        shown = false;
        audioPlayer.release();
        if (initialized) {
            listeningLayout.setVisibility(View.GONE);
            restoreTopInfo();
            restoreStrictReminder();
        }
    }

    boolean isShown() {
        return shown;
    }

    // ===== 内部渲染与状态 =====

    private void buildOptions(ListeningQuestion question) {
        optionsContainer.removeAllViews();
        optionViews.clear();
        if (question == null) {
            return;
        }
        String[] labels = question.toOptionLabels();
        for (int index = 0; index < labels.length; index++) {
            final TextView option = createOptionView(labels[index]);
            final int optionIndex = index;
            option.setOnClickListener(v -> onOptionClicked(optionIndex));
            optionsContainer.addView(option, optionLayoutParams());
            optionViews.add(option);
        }
    }

    private TextView createOptionView(String label) {
        TextView option = new TextView(context);
        option.setText(label);
        option.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        option.setTextColor(Color.WHITE);
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.setPadding(dp(12), dp(8), dp(12), dp(8));
        option.setBackgroundResource(R.drawable.bg_listening_option);
        option.setClickable(true);
        option.setFocusable(true);
        option.setTypeface(Typeface.DEFAULT);
        option.setLineSpacing(0f, 1.15f);
        return option;
    }

    private LinearLayout.LayoutParams optionLayoutParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(6);
        return params;
    }

    private void onOptionClicked(int index) {
        if (!optionsEnabled || !shown) {
            return;
        }
        selectedOptionIndex = index;
        for (int i = 0; i < optionViews.size(); i++) {
            optionViews.get(i).setSelected(i == index);
        }
        hideResult();
    }

    private void setOptionsEnabled(boolean enabled) {
        for (TextView option : optionViews) {
            option.setEnabled(enabled);
        }
        submitButton.setEnabled(enabled);
        replayButton.setEnabled(enabled);
    }

    private void resetSelection() {
        selectedOptionIndex = -1;
        for (TextView option : optionViews) {
            option.setSelected(false);
        }
    }

    private void showResult(String message, int color) {
        if (!initialized) {
            return;
        }
        resultText.setText(message);
        resultText.setTextColor(color);
        resultText.setVisibility(View.VISIBLE);
    }

    private void hideResult() {
        if (resultText != null) {
            resultText.setText("");
            resultText.setVisibility(View.GONE);
        }
    }

    private boolean ensureInitialized() {
        if (initialized) {
            return true;
        }
        if (floatingView == null) {
            return false;
        }

        ViewStub listeningStub = floatingView.findViewById(R.id.listening_challenge_stub);
        if (listeningStub != null) {
            listeningStub.inflate();
        }

        listeningLayout = floatingView.findViewById(R.id.listening_challenge_layout);
        hintText = floatingView.findViewById(R.id.tv_listening_hint);
        questionText = floatingView.findViewById(R.id.tv_listening_question);
        transcriptScrollView = floatingView.findViewById(R.id.sv_listening_transcript);
        transcriptText = floatingView.findViewById(R.id.tv_listening_transcript);
        optionsContainer = floatingView.findViewById(R.id.ll_listening_options);
        replayButton = floatingView.findViewById(R.id.btn_listening_replay);
        submitButton = floatingView.findViewById(R.id.btn_listening_submit);
        cancelButton = floatingView.findViewById(R.id.btn_listening_cancel);
        revealButton = floatingView.findViewById(R.id.btn_listening_reveal);
        nextButton = floatingView.findViewById(R.id.btn_listening_next);
        resultText = floatingView.findViewById(R.id.tv_listening_result);
        topInfoLayout = floatingView.findViewById(R.id.top_info_layout);
        strictReminderLayout = floatingView.findViewById(R.id.strict_reminder_layout);

        if (listeningLayout == null
                || hintText == null
                || questionText == null
                || transcriptScrollView == null
                || transcriptText == null
                || optionsContainer == null
                || replayButton == null
                || submitButton == null
                || cancelButton == null
                || revealButton == null
                || nextButton == null
                || resultText == null
                || topInfoLayout == null) {
            Log.e(TAG, "听力题布局懒加载失败");
            return false;
        }

        // 听力题不提供原文选择复制，避免长按选中干扰作答
        questionText.setTextIsSelectable(false);
        questionText.setLongClickable(false);

        replayButton.setOnClickListener(v -> audioPlayer.play(context));
        submitButton.setOnClickListener(v -> {
            if (selectedOptionIndex < 0) {
                showResult("⚠️ 请先选择一个答案", Color.rgb(255, 87, 34));
                return;
            }
            if (callbacks != null) {
                callbacks.onSubmit(String.valueOf((char) ('A' + selectedOptionIndex)));
            }
        });
        cancelButton.setOnClickListener(v -> {
            if (callbacks != null) {
                callbacks.onCancel();
            }
        });
        revealButton.setOnClickListener(v -> {
            if (callbacks != null) {
                callbacks.onRevealTranscript();
            }
        });
        nextButton.setOnClickListener(v -> {
            if (callbacks != null) {
                callbacks.onNextQuestion();
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

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
