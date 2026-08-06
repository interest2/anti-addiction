package com.book.mask.challenge;

import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.book.mask.config.ChallengeType;
import com.book.mask.constant.Const;
import com.book.mask.personalize.ChallengeRecord;
import com.book.mask.personalize.ChallengeRecordStore;
import com.book.mask.personalize.ChallengeSettingsManager;

/**
 * 文本类答题会话：算术题 / 推理题 / 英文阅读。负责出题、答案校验、答错换题与回调。
 */
final class TextChallengeSession implements ChallengeSession {

    private static final String TAG = "TextChallenge";
    private static final long TIMER_TICK_INTERVAL_MILLIS = 1000L;

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

    /** 当前题目开始计时的时刻；0 表示未计时。 */
    private long questionStartTime = 0;

    /** 左上角正计时刷新任务：每秒刷新一次计时文案（mm 或 mm:s）。 */
    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (questionStartTime == 0 || !shown) {
                return;
            }
            viewController.setTimerText(formatElapsed(computeElapsedSeconds()));
            handler.postDelayed(this, TIMER_TICK_INTERVAL_MILLIS);
        }
    };

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
        if (shown) {
            startTimer(shouldShowTimer());
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
        stopTimer();
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
            recordNotArithmeticAnswer(userAnswer, true);
            stopTimer();
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
        recordNotArithmeticAnswer(userAnswer, false);
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
            startTimer(shouldShowTimer());
            Log.d(TAG, "生成新题目，保持输入法显示");
        }, Const.TRANSIENT_FEEDBACK_DURATION_MS);
    }

    /**
     * 落地一条非算术题答题记录（推理 / 混合等云端题目），供「答题记录」展示；
     * 纯算术题为本地生成，不入记录。
     */
    private void recordNotArithmeticAnswer(String userAnswer, boolean passed) {
        if (currentType == ChallengeType.ARITHMETIC) {
            return;
        }
        try {
            ChallengeRecord record = new ChallengeRecord();
            record.timestamp = System.currentTimeMillis();
            record.question = currentQuestion == null ? "" : currentQuestion;
            record.userAnswer = userAnswer == null ? "" : userAnswer;
            record.correctAnswer = currentAnswer == null ? "" : currentAnswer;
            record.passed = passed;
            record.elapsedSeconds = (int) computeElapsedSeconds();
            new ChallengeRecordStore().addRecord(record);
            Log.d(TAG, "已保存非算术题答题记录, 答对=" + passed + ", 耗时=" + record.elapsedSeconds);
        } catch (Exception e) {
            Log.e(TAG, "保存非算术题答题记录失败", e);
        }
    }

    /** 算术 / 推理 / 混合题均支持计时，显示模式由「答题计时」选项控制；听力 / 复述除外。 */
    private boolean shouldShowTimer() {
        return questionProvider.getChallengeTimerMode() != ChallengeSettingsManager.TIMER_MODE_NONE
                && (currentType == ChallengeType.ARITHMETIC
                        || currentType == ChallengeType.REASONING
                        || currentType == ChallengeType.MIXED);
    }

    private long computeElapsedSeconds() {
        if (questionStartTime == 0) {
            return 0;
        }
        return (System.currentTimeMillis() - questionStartTime) / 1000;
    }

    /** 启动 / 停止当前题目正计时：计时时重置起点并开始刷新，否则隐藏左上角计时。 */
    private void startTimer(boolean show) {
        stopTimer();
        viewController.showTimer(show);
        if (show) {
            questionStartTime = System.currentTimeMillis();
            viewController.setTimerText(formatElapsed(0));
            handler.postDelayed(timerTick, TIMER_TICK_INTERVAL_MILLIS);
        }
    }

    private void stopTimer() {
        handler.removeCallbacks(timerTick);
        questionStartTime = 0;
    }

    /** 按当前「答题计时」模式格式化：仅分钟（mm）或分钟和秒（mm:s）。 */
    private String formatElapsed(long elapsedSeconds) {
        if (questionProvider.getChallengeTimerMode() == ChallengeSettingsManager.TIMER_MODE_MINUTES) {
            return String.valueOf(elapsedSeconds / 60);
        }
        return formatTimer(elapsedSeconds);
    }

    /** 格式化为 mm:s，秒位只显示到十秒位（0-5），如 83 秒 → "1:3"。 */
    private static String formatTimer(long elapsedSeconds) {
        long minutes = elapsedSeconds / 60;
        long tensSeconds = (elapsedSeconds % 60) / 10;
        return minutes + ":" + tensSeconds;
    }
}
