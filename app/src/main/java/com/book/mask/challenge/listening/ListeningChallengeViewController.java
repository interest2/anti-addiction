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
 * <p>默认只展示题干与选项，听力原文隐藏；答错后可揭示原文，揭示后本题进入「无法解禁」态，只能换一题。
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
    private Button playButton;
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
        hideTopInfo();
        hideStrictReminder();
        listeningLayout.setVisibility(View.VISIBLE);
        listeningLayout.bringToFront();
        questionText.setVisibility(View.GONE);
        transcriptScrollView.setVisibility(View.GONE);
        optionsContainer.setVisibility(View.GONE);
        playButton.setVisibility(View.GONE);
        replayButton.setVisibility(View.GONE);
        submitButton.setVisibility(View.GONE);
        cancelButton.setVisibility(View.VISIBLE);
        revealButton.setVisibility(View.GONE);
        nextButton.setVisibility(View.GONE);
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
        // 后台用豆包语音合成听力原文，供「播放 / 重听」使用；未配置凭据时自动回退占位音频
        audioPlayer.prepare(context, question == null ? "" : question.getTranscript());
        questionText.setText(question == null ? "" : question.getQuestion());
        questionText.setVisibility(View.VISIBLE);
        buildOptions(question);
        optionsContainer.setVisibility(View.VISIBLE);
        transcriptScrollView.setVisibility(View.GONE);
        playButton.setVisibility(View.VISIBLE);
        replayButton.setVisibility(View.VISIBLE);
        submitButton.setVisibility(View.VISIBLE);
        cancelButton.setVisibility(View.VISIBLE);
        revealButton.setVisibility(View.GONE);
        nextButton.setVisibility(View.GONE);
        setOptionsEnabled(true);
        hideResult();
        hintText.setText("请播放听力，然后选择答案");
        hintText.setTextColor(Color.WHITE);
    }

    void showWrongAnswer() {
        if (!initialized || !shown) {
            return;
        }
        optionsEnabled = false;
        setOptionsEnabled(false);
        submitButton.setVisibility(View.GONE);
        revealButton.setVisibility(View.VISIBLE);
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
        submitButton.setVisibility(View.GONE);
        hintText.setText("答案正确");
        hintText.setTextColor(Color.rgb(76, 175, 80));
        showResult("✅ 答案正确！", Color.rgb(76, 175, 80));
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
        audioPlayer.release();
        listeningLayout.setVisibility(View.GONE);
        restoreTopInfo();
        restoreStrictReminder();
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
        playButton.setEnabled(enabled);
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
        playButton = floatingView.findViewById(R.id.btn_listening_play);
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
                || playButton == null
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

        playButton.setOnClickListener(v -> audioPlayer.play(context));
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
