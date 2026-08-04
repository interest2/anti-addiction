package com.book.mask.challenge;

import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.book.mask.config.ChallengeType;
import com.book.mask.constant.Const;

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
        viewController.showWrongAnswer();
        handler.postDelayed(() -> {
            if (!shown) {
                return;
            }
            ChallengeQuestionProvider.Question question =
                    questionProvider.getQuestion(currentType);
            currentAnswer = question.getAnswer();
            viewController.updateQuestion(currentType, question);
            Log.d(TAG, "生成新题目，保持输入法显示");
        }, Const.TRANSIENT_FEEDBACK_DURATION_MS);
    }
}
