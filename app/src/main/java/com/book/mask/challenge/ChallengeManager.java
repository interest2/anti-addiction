package com.book.mask.challenge;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.book.mask.challenge.listening.ListeningChallengeSession;
import com.book.mask.challenge.retelling.RetellingChallengeSession;
import com.book.mask.config.ChallengeType;
import com.book.mask.config.CustomApp;
import com.book.mask.config.CustomAppManager;
import com.book.mask.floating.FloatService;

/**
 * 编排答题会话：按当前题型选择文本类题目或复述题，转发结果。
 */
public class ChallengeManager {

    private static final String TAG = "ChallengeManager";

    public interface OnChallengeListener {
        void onAnswerCorrect();

        void onChallengeCancel();
    }

    private final Handler handler;
    private final FloatService accessibilityService;
    private final ChallengeQuestionProvider questionProvider;
    private final TextChallengeSession textSession;
    private final RetellingChallengeSession retellingSession;
    private final ListeningChallengeSession listeningSession;

    private CustomApp currentApp;
    private OnChallengeListener listener;
    private boolean challengeActive;

    public ChallengeManager(
            Context context,
            View floatingView,
            WindowManager windowManager,
            WindowManager.LayoutParams layoutParams,
            Handler handler,
            FloatService accessibilityService) {
        this.handler = handler;
        this.accessibilityService = accessibilityService;
        this.questionProvider = new ChallengeQuestionProvider(context);
        this.textSession = new TextChallengeSession(
                context,
                floatingView,
                windowManager,
                layoutParams,
                handler,
                questionProvider);
        this.textSession.setCallbacks(new TextChallengeSession.Callbacks() {
            @Override
            public void onCorrect() {
                onAnswerCorrectInternal();
            }

            @Override
            public void onCancel() {
                handleCancelInternal();
            }
        });
        this.retellingSession = new RetellingChallengeSession(
                context,
                floatingView,
                accessibilityService,
                new RetellingChallengeSession.Callbacks() {
                    @Override
                    public void onPassed() {
                        onAnswerCorrectInternal();
                    }

                    @Override
                    public void onCancel() {
                        handleCancelInternal();
                    }
                });
        this.listeningSession = new ListeningChallengeSession(
                context,
                floatingView,
                new ListeningChallengeSession.Callbacks() {
                    @Override
                    public void onCorrect() {
                        onAnswerCorrectInternal();
                    }

                    @Override
                    public void onCancel() {
                        handleCancelInternal();
                    }
                });
    }

    public void setCurrentApp(CustomApp app) {
        this.currentApp = app;
    }

    public void setOnChallengeListener(OnChallengeListener listener) {
        this.listener = listener;
    }

    public OnChallengeListener getOnChallengeListener() {
        return listener;
    }

    public boolean isChallengeActive() {
        return challengeActive;
    }

    public void showChallenge() {
        ChallengeType selectedType = questionProvider.selectType();
        if (selectedType == ChallengeType.LISTENING) {
            showListeningChallenge();
        } else if (selectedType == ChallengeType.RETELLING) {
            showRetellingChallenge();
        } else {
            showTextChallenge(selectedType);
        }
    }

    private void showTextChallenge(ChallengeType type) {
        if (!textSession.show(type)) {
            Log.w(TAG, "文本答题界面展示失败");
            return;
        }
        challengeActive = true;
        if (accessibilityService != null) {
            accessibilityService.onChallengeStart();
        }
        Log.d(TAG, "显示答题验证界面");
    }

    private void showListeningChallenge() {
        if (!listeningSession.show()) {
            Log.w(TAG, "听力题启动失败，回退本地算术题");
            showTextChallenge(ChallengeType.ARITHMETIC);
            return;
        }
        challengeActive = true;
        if (accessibilityService != null) {
            accessibilityService.onChallengeStart();
        }
        Log.d(TAG, "显示听力题验证界面");
    }

    private void showRetellingChallenge() {
        if (!retellingSession.start()) {
            Log.w(TAG, "复述题启动失败，回退本地算术题");
            showTextChallenge(ChallengeType.ARITHMETIC);
            return;
        }
        challengeActive = true;
        if (accessibilityService != null) {
            accessibilityService.onChallengeStart();
        }
        Log.d(TAG, "显示复述题验证界面");
    }

    public void hideChallenge() {
        if (!challengeActive) {
            return;
        }

        challengeActive = false;
        textSession.destroy();
        retellingSession.hideQuietly();
        listeningSession.hideQuietly();
        if (accessibilityService != null) {
            accessibilityService.onChallengeEnd();
        }
        Log.d(TAG, "隐藏答题验证界面");
    }

    /** 永久移除悬浮窗时释放各题型会话持有的线程与媒体资源。 */
    public void destroy() {
        boolean wasActive = challengeActive;
        challengeActive = false;
        listener = null;
        textSession.destroy();
        retellingSession.destroy();
        listeningSession.destroy();
        if (wasActive && accessibilityService != null) {
            accessibilityService.onChallengeEnd();
        }
        Log.d(TAG, "销毁答题验证会话");
    }

    private void handleCancelInternal() {
        Log.d(TAG, "用户取消关闭");
        boolean isWechat =
                currentApp != null
                        && CustomAppManager.WECHAT_PACKAGE.equals(currentApp.getPackageName());
        hideChallenge();

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

    private void onAnswerCorrectInternal() {
        Log.d(TAG, "答题正确");
        hideChallenge();
        if (listener != null) {
            listener.onAnswerCorrect();
        }
    }
}
