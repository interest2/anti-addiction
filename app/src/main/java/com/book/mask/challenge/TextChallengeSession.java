package com.book.mask.challenge;

import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.book.mask.config.ChallengeType;
import com.book.mask.constant.Const;
import com.book.mask.personalize.ReasoningRecord;
import com.book.mask.personalize.ReasoningRecordStore;

/**
 * 文本类答题会话：算术题 / 推理题 / 英文阅读。负责出题、答案校验、答错换题与回调。
 */
final class TextChallengeSession implements ChallengeSession {

    private static final String TAG = "TextChallenge";

    interface Callbacks {
        void onCorrect();

        void onCancel();
    }

    private final Handler handler;
    private final ChallengeQuestionProvider questionProvider;
    private final ChallengeViewController viewController;
    private Callbacks callbacks;
    private ChallengeType currentType = ChallengeType.ARITHMETIC;
    private String currentAnswer = "";
    private String currentQuestion = "";
    private boolean shown;

    TextChallengeSession(
            Context context,
            View floatingView,
            WindowManager windowManager,
            WindowManager.LayoutParams layoutParams,
            Handler handler,
            ChallengeQuestionProvider questionProvider) {
        this.handler = handler;
        this.questionProvider = questionProvider;
        this.viewController = new ChallengeViewController(
                context,
                floatingView,
                windowManager,
                layoutParams,
                handler,
                new ChallengeViewController.Callbacks() {
                    @Override
                    public void onSubmit(String answer) {
                        handleSubmitAnswer(answer);
                    }

                    @Override
                    public void onCancel() {
                        handleCancel();
                    }
                });
    }

    void setCallbacks(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    /**
     * 展示指定题型的新题目。
     *
     * @return 是否成功展示
     */
    boolean show(ChallengeType type) {
        ChallengeQuestionProvider.Question question = questionProvider.getQuestion(type);
        currentType = type;
        currentAnswer = question.getAnswer();
        currentQuestion = question.getContent();
        shown = viewController.show(type, question);
        return shown;
    }

    @Override
    public boolean isActive() {
        return shown;
    }

    @Override
    public void cancel() {
        handleCancel();
    }

    @Override
    public void destroy() {
        shown = false;
        viewController.hide();
    }

    private void handleCancel() {
        Log.d(TAG, "用户取消关闭");
        if (callbacks != null) {
            callbacks.onCancel();
        }
    }

    private void handleSubmitAnswer(String userAnswer) {
        if (TextUtils.isEmpty(userAnswer)) {
            viewController.showEmptyAnswer();
            return;
        }

        if (currentAnswer.equalsIgnoreCase(userAnswer)) {
            Log.d(TAG, "答题正确");
            recordReasoningAnswer(userAnswer, true);
            viewController.showCorrectAnswer();
            handler.postDelayed(() -> {
                viewController.hideKeyboard();
                if (callbacks != null) {
                    callbacks.onCorrect();
                }
            }, Const.TRANSIENT_FEEDBACK_DURATION_MS);
            return;
        }

        Log.d(TAG, "答题错误: " + userAnswer + " (正确答案: " + currentAnswer + ")");
        recordReasoningAnswer(userAnswer, false);
        viewController.showWrongAnswer();
        handler.postDelayed(() -> {
            if (!shown) {
                return;
            }
            ChallengeQuestionProvider.Question question =
                    questionProvider.getQuestion(currentType);
            currentAnswer = question.getAnswer();
            currentQuestion = question.getContent();
            viewController.updateQuestion(currentType, question);
            Log.d(TAG, "生成新题目，保持输入法显示");
        }, Const.TRANSIENT_FEEDBACK_DURATION_MS);
    }

    /**
     * 落地一条推理题答题记录（含混合题型中抽到的推理题），供「答题记录」展示；
     * 纯算术题不入记录。
     */
    private void recordReasoningAnswer(String userAnswer, boolean passed) {
        if (currentType != ChallengeType.REASONING) {
            return;
        }
        try {
            ReasoningRecord record = new ReasoningRecord();
            record.timestamp = System.currentTimeMillis();
            record.question = currentQuestion == null ? "" : currentQuestion;
            record.userAnswer = userAnswer == null ? "" : userAnswer;
            record.correctAnswer = currentAnswer == null ? "" : currentAnswer;
            record.passed = passed;
            new ReasoningRecordStore().addRecord(record);
            Log.d(TAG, "已保存推理题答题记录, 答对=" + passed);
        } catch (Exception e) {
            Log.e(TAG, "保存推理题答题记录失败", e);
        }
    }
}
