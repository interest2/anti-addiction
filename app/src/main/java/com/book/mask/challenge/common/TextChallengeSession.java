package com.book.mask.challenge.common;

import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.book.mask.constant.Const;
import com.book.mask.personalize.AnswerOverviewRecord;
import com.book.mask.personalize.AnswerOverviewStore;
import com.book.mask.personalize.ChallengeRecord;
import com.book.mask.personalize.ChallengeRecordStore;


/**
 * 文本类答题会话（算术题）。负责出题、答案校验、答错换题与回调。
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
    private String currentAnswer = "";
    private String currentQuestion = "";
    private boolean shown;

    /** 当前题目开始作答的时刻；0 表示未开始。用于最短作答等待与答题记录的耗时统计。 */
    private long questionStartTime = 0;

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
     * 展示新的算术题。
     *
     * @return 是否成功展示
     */
    boolean show() {
        ChallengeQuestionProvider.Question question = questionProvider.getQuestion();
        currentAnswer = question.getAnswer();
        currentQuestion = question.getContent();
        shown = viewController.show(question);
        if (shown) {
            startQuestionClock();
        }
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
        stopQuestionClock();
        viewController.hide();
    }

    private void handleCancel() {
        Log.d(TAG, "用户取消关闭");
        if (callbacks != null) {
            callbacks.onCancel();
        }
    }

    private void handleSubmitAnswer(String userAnswer) {
        if (questionStartTime != 0
                && System.currentTimeMillis() - questionStartTime < Const.MIN_ANSWER_DELAY_MS) {
            viewController.showWaitHint();
            return;
        }

        if (TextUtils.isEmpty(userAnswer)) {
            viewController.showEmptyAnswer();
            return;
        }

        if (currentAnswer.equalsIgnoreCase(userAnswer)) {
            Log.d(TAG, "答题正确");
            recordAnswer(userAnswer, true);
            stopQuestionClock();
            viewController.showCorrectAnswer();
            handler.postDelayed(() -> {
                if (!shown) {
                    return;
                }
                viewController.hideKeyboard();
                if (callbacks != null) {
                    callbacks.onCorrect();
                }
            }, Const.TRANSIENT_FEEDBACK_DURATION_MS);
            return;
        }

        Log.d(TAG, "答题错误: " + userAnswer + " (正确答案: " + currentAnswer + ")");
        recordAnswer(userAnswer, false);
        viewController.showWrongAnswer();
        handler.postDelayed(() -> {
            if (!shown) {
                return;
            }
            ChallengeQuestionProvider.Question question = questionProvider.getQuestion();
            currentAnswer = question.getAnswer();
            currentQuestion = question.getContent();
            viewController.updateQuestion(question);
            startQuestionClock();
            Log.d(TAG, "生成新题目，保持输入法显示");
        }, Const.TRANSIENT_FEEDBACK_DURATION_MS);
    }

    /**
     * 落地一条答题记录，供「答题记录」展示；
     * 详情存 {@link ChallengeRecordStore}，概况存 {@link AnswerOverviewStore}。
     */
    private void recordAnswer(String userAnswer, boolean passed) {
        try {
            ChallengeRecord record = new ChallengeRecord();
            long now = System.currentTimeMillis();
            record.timestamp = now;
            record.question = currentQuestion == null ? "" : currentQuestion;
            record.userAnswer = userAnswer == null ? "" : userAnswer;
            record.correctAnswer = currentAnswer == null ? "" : currentAnswer;
            record.passed = passed;
            record.elapsedSeconds = (int) computeElapsedSeconds();
            new ChallengeRecordStore().addRecord(record);
            AnswerOverviewRecord overview = new AnswerOverviewRecord();
            overview.type = AnswerOverviewRecord.TYPE_ARITHMETIC;
            overview.timestamp = now;
            overview.elapsedSeconds = record.elapsedSeconds;
            overview.passed = passed;
            new AnswerOverviewStore().addRecord(overview);
            Log.d(TAG, "已保存答题记录, 答对=" + passed + ", 耗时=" + record.elapsedSeconds);
        } catch (Exception e) {
            Log.e(TAG, "保存答题记录失败", e);
        }
    }

    private long computeElapsedSeconds() {
        if (questionStartTime == 0) {
            return 0;
        }
        return (System.currentTimeMillis() - questionStartTime) / 1000;
    }

    /** 记录当前题目的开始作答时刻，供最短作答等待与答题记录耗时使用（悬浮窗不展示计时）。 */
    private void startQuestionClock() {
        questionStartTime = System.currentTimeMillis();
    }

    private void stopQuestionClock() {
        questionStartTime = 0;
    }
}
