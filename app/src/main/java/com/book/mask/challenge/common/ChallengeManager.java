package com.book.mask.challenge.common;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.book.mask.config.CustomApp;
import com.book.mask.config.CustomAppManager;
import com.book.mask.floating.FloatService;

/**
 * 编排答题会话：展示算术题并转发结果。
 */
public class ChallengeManager {

    private static final String TAG = "ChallengeManager";

    public interface OnChallengeListener {
        void onAnswerCorrect();

        void onChallengeCancel();
    }

    private final Handler handler;
    private final FloatService accessibilityService;
    private final TextChallengeSession textSession;

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
        this.textSession = new TextChallengeSession(
                context,
                floatingView,
                windowManager,
                layoutParams,
                handler,
                new ChallengeQuestionProvider(context));
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
        if (!textSession.show()) {
            Log.w(TAG, "文本答题界面展示失败");
            return;
        }
        challengeActive = true;
        if (accessibilityService != null) {
            accessibilityService.onChallengeStart();
        }
        Log.d(TAG, "显示答题验证界面");
    }

    public void hideChallenge() {
        if (!challengeActive) {
            return;
        }

        challengeActive = false;
        textSession.destroy();
        if (accessibilityService != null) {
            accessibilityService.onChallengeEnd();
        }
        Log.d(TAG, "隐藏答题验证界面");
    }

    /** 永久移除悬浮窗时释放答题会话持有的资源。 */
    public void destroy() {
        boolean wasActive = challengeActive;
        challengeActive = false;
        listener = null;
        textSession.destroy();
        if (wasActive && accessibilityService != null) {
            accessibilityService.onChallengeEnd();
        }
        Log.d(TAG, "销毁答题验证会话");
    }

    private void handleCancelInternal() {
        Log.d(TAG, "用户取消关闭");
        boolean canCloseWechatWithoutChallenge =
                currentApp != null
                        && CustomAppManager.WECHAT_PACKAGE.equals(currentApp.getPackageName())
                        && !currentApp.isGlobalBlock();
        hideChallenge();

        if (listener == null) {
            return;
        }
        if (canCloseWechatWithoutChallenge) {
            Log.d(TAG, "微信未开启答题解锁，取消答题时直接关闭");
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
