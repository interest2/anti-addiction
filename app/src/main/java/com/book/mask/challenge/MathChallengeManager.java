package com.book.mask.challenge;

import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.book.mask.config.ChallengeType;
import com.book.mask.config.CustomApp;
import com.book.mask.config.CustomAppManager;
import com.book.mask.floating.FloatService;

/**
 * 编排答题会话、答案校验及业务回调。
 */
public class MathChallengeManager {

    private static final String TAG = "MathChallenge";
    private static final long RESULT_DISPLAY_MILLIS = 1000L;

    public interface OnMathChallengeListener {
        void onAnswerCorrect();

        void onChallengeCancel();
    }

    private final Handler handler;
    private final FloatService accessibilityService;
    private final ChallengeQuestionProvider questionProvider;
    private final ChallengeViewController viewController;

    private CustomApp currentApp;
    private OnMathChallengeListener listener;
    private ChallengeType currentType = ChallengeType.ARITHMETIC;
    private String currentAnswer = "";
    private boolean challengeActive;

    public MathChallengeManager(
            Context context,
            View floatingView,
            WindowManager windowManager,
            WindowManager.LayoutParams layoutParams,
            Handler handler,
            FloatService accessibilityService) {
        this.handler = handler;
        this.accessibilityService = accessibilityService;
        this.questionProvider = new ChallengeQuestionProvider(context);
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

    public void setCurrentApp(CustomApp app) {
        this.currentApp = app;
    }

    public void setOnMathChallengeListener(OnMathChallengeListener listener) {
        this.listener = listener;
    }

    public OnMathChallengeListener getOnMathChallengeListener() {
        return listener;
    }

    public boolean isMathChallengeActive() {
        return challengeActive;
    }

    public void showMathChallenge() {
        ChallengeType selectedType = questionProvider.selectType();
        ChallengeQuestionProvider.Question question =
                questionProvider.getQuestion(selectedType);
        currentType = selectedType;
        currentAnswer = question.getAnswer();

        if (!viewController.show(currentType, question)) {
            return;
        }

        challengeActive = true;
        if (accessibilityService != null) {
            accessibilityService.onMathChallengeStart();
        }
        Log.d(TAG, "显示答题验证界面");
    }

    public void hideMathChallenge() {
        if (!challengeActive) {
            return;
        }

        challengeActive = false;
        viewController.hide();
        if (accessibilityService != null) {
            accessibilityService.onMathChallengeEnd();
        }
        Log.d(TAG, "隐藏答题验证界面");
    }

    private void handleCancel() {
        Log.d(TAG, "用户取消关闭");
        boolean isWechat =
                currentApp != null
                        && CustomAppManager.WECHAT_PACKAGE.equals(currentApp.getPackageName());
        hideMathChallenge();

        if (listener == null) {
            return;
        }
        if (isWechat) {
            Log.d(TAG, "微信APP取消按钮被点击，直接当作答题通过");
            listener.onAnswerCorrect();
        } else {
            listener.onChallengeCancel();
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
                if (listener != null) {
                    listener.onAnswerCorrect();
                }
            }, RESULT_DISPLAY_MILLIS);
            return;
        }

        Log.d(TAG, "答题错误: " + userAnswer + " (正确答案: " + currentAnswer + ")");
        viewController.showWrongAnswer();
        handler.postDelayed(() -> {
            if (!challengeActive) {
                return;
            }
            ChallengeQuestionProvider.Question question =
                    questionProvider.getQuestion(currentType);
            currentAnswer = question.getAnswer();
            viewController.updateQuestion(currentType, question);
            Log.d(TAG, "生成新题目，保持输入法显示");
        }, RESULT_DISPLAY_MILLIS);
    }
}
