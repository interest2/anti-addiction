package com.book.mask.challenge.listening;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import com.book.mask.challenge.ChallengeSession;
import com.book.mask.constant.Const;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 听力题答题会话：出题 → 播放（模拟豆包语音）→ 选择作答 → 通过 / 答错揭示原文后换题。
 *
 * <p>核心规则：一旦揭示听力原文，本题即「失效」——即使随后答对也不再回调通过，
 * 只能换一题重新作答。sessionId 贯穿一次答题，异步出题返回时先判断是否仍是当前会话，防止取消后旧请求继续改界面。
 */
public final class ListeningChallengeSession implements ChallengeSession {

    private static final String TAG = "ListeningSession";

    private enum State {
        LOADING,
        ANSWER,
        WRONG,
        REVEALED,
        ERROR
    }

    public interface Callbacks {
        void onCorrect();

        void onCancel();
    }

    private final ListeningChallengeViewController view;
    private final ListeningQuestionRepository questionRepository;
    private final Handler handler;
    private final ExecutorService executor;
    private final Callbacks callbacks;

    private State state = State.ERROR;
    private long sessionId;
    private boolean active;
    private ListeningQuestion currentQuestion;
    private boolean forfeited;

    public ListeningChallengeSession(Context context, View floatingView, Callbacks callbacks) {
        this.callbacks = callbacks;
        this.view = new ListeningChallengeViewController(
                context,
                floatingView,
                new ListeningChallengeViewController.Callbacks() {
                    @Override
                    public void onSubmit(String answerLetter) {
                        handleSubmit(answerLetter);
                    }

                    @Override
                    public void onRevealTranscript() {
                        handleRevealTranscript();
                    }

                    @Override
                    public void onNextQuestion() {
                        handleNextQuestion();
                    }

                    @Override
                    public void onCancel() {
                        cancel();
                    }
                });
        this.questionRepository = new ListeningQuestionRepository(context);
        this.handler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * 展示听力题面板并加载第一道题。
     *
     * @return 是否成功展示
     */
    public boolean show() {
        sessionId++;
        active = true;
        forfeited = false;
        state = State.LOADING;
        if (!view.show()) {
            Log.w(TAG, "听力题界面展示失败");
            return false;
        }
        Log.d(TAG, "开始加载听力题");
        loadQuestion(false);
        return true;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void cancel() {
        if (!active) {
            return;
        }
        active = false;
        sessionId++;
        handler.removeCallbacksAndMessages(null);
        view.hide();
        if (callbacks != null) {
            callbacks.onCancel();
        }
        Log.d(TAG, "听力题已取消");
    }

    @Override
    public void destroy() {
        active = false;
        sessionId++;
        handler.removeCallbacksAndMessages(null);
        view.hide();
        executor.shutdownNow();
    }

    /**
     * 由 ChallengeManager 静默关闭听力题界面（如答题通过 / 切换题型），不回调上层。
     */
    public void hideQuietly() {
        active = false;
        sessionId++;
        handler.removeCallbacksAndMessages(null);
        view.hide();
    }

    // ===== 流程 =====

    private void loadQuestion(final boolean advance) {
        final long sid = sessionId;
        executor.execute(() -> {
            ListeningQuestionRepository.Question question = advance
                    ? questionRepository.advance()
                    : questionRepository.obtainQuestion();
            handler.post(() -> {
                if (sid != sessionId || !active) {
                    return;
                }
                if (question == null || question.getQuestion() == null) {
                    handleLoadError();
                    return;
                }
                currentQuestion = question.getQuestion();
                forfeited = false;
                state = State.ANSWER;
                view.showQuestion(currentQuestion);
                Log.d(TAG, "听力题就绪，questionId=" + question.getQuestionId());
            });
        });
    }

    private void handleSubmit(String answerLetter) {
        // 空答案由界面层拦截；揭示过原文的本题已失效，不再受理提交
        if (!active || state != State.ANSWER || currentQuestion == null || forfeited) {
            return;
        }
        String answer = answerLetter == null ? "" : answerLetter.trim().toUpperCase();
        if (currentQuestion.getAnswer().equalsIgnoreCase(answer)) {
            Log.d(TAG, "听力题答对");
            state = State.ANSWER;
            view.showCorrectAnswer();
            handler.postDelayed(() -> {
                if (!active) {
                    return;
                }
                if (callbacks != null) {
                    callbacks.onCorrect();
                }
            }, Const.TRANSIENT_FEEDBACK_DURATION_MS);
            return;
        }
        Log.d(TAG, "听力题答错: " + answer + " (正确答案: " + currentQuestion.getAnswer() + ")");
        state = State.WRONG;
        view.showWrongAnswer();
    }

    private void handleRevealTranscript() {
        if (!active || state != State.WRONG || currentQuestion == null) {
            return;
        }
        forfeited = true;
        state = State.REVEALED;
        view.showTranscript(currentQuestion.getTranscript());
        Log.d(TAG, "已显示听力原文");
    }

    private void handleNextQuestion() {
        if (!active) {
            return;
        }
        state = State.LOADING;
        view.show();
        loadQuestion(true);
    }

    private void handleLoadError() {
        state = State.ERROR;
        view.showLoadError("获取听力题失败，请重试");
        Log.w(TAG, "获取听力题失败");
    }
}
