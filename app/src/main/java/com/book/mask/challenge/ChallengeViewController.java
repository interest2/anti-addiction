package com.book.mask.challenge;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.book.mask.R;
import com.book.mask.config.ChallengeType;

/**
 * 负责答题悬浮层的组件绑定、渲染、输入法与焦点管理。
 */
final class ChallengeViewController {

    private static final String TAG = "ChallengeView";
    private static final long KEYBOARD_DELAY_MILLIS = 300L;
    private static final long FOCUS_CHECK_INTERVAL_MILLIS = 1000L;

    interface Callbacks {
        void onSubmit(String answer);

        void onCancel();
    }

    private final Context context;
    private final View floatingView;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams windowLayoutParams;
    private final Handler handler;
    private final Callbacks callbacks;

    private View challengeLayout;
    private TextView questionText;
    private EditText answerEdit;
    private TextView resultText;
    private ScrollView questionScrollView;

    private boolean initialized;
    private boolean shown;
    private boolean selectingText;

    private final Runnable focusKeeper = this::keepAnswerInputFocused;
    private final Runnable showKeyboardRunnable = this::showKeyboardAndStartFocusKeeper;

    ChallengeViewController(
            Context context,
            View floatingView,
            WindowManager windowManager,
            WindowManager.LayoutParams windowLayoutParams,
            Handler handler,
            Callbacks callbacks) {
        this.context = context;
        this.floatingView = floatingView;
        this.windowManager = windowManager;
        this.windowLayoutParams = windowLayoutParams;
        this.handler = handler;
        this.callbacks = callbacks;
    }

    boolean show(ChallengeType type, ChallengeQuestionProvider.Question question) {
        if (!ensureInitialized()) {
            return false;
        }

        stopFocusTasks();
        shown = true;
        renderQuestion(type, question);
        answerEdit.setText("");
        hideResult();
        challengeLayout.setVisibility(View.VISIBLE);

        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        windowManager.updateViewLayout(floatingView, windowLayoutParams);

        answerEdit.setFocusable(true);
        answerEdit.setFocusableInTouchMode(true);
        answerEdit.requestFocus();
        handler.postDelayed(showKeyboardRunnable, KEYBOARD_DELAY_MILLIS);
        return true;
    }

    void updateQuestion(ChallengeType type, ChallengeQuestionProvider.Question question) {
        if (!initialized || !shown) {
            return;
        }
        renderQuestion(type, question);
        answerEdit.setText("");
        answerEdit.requestFocus();
        hideResult();
    }

    void showEmptyAnswer() {
        showResult("⚠️ 请输入答案", 0xFFFF5722);
    }

    void showCorrectAnswer() {
        showResult(
                "✅ 答案正确！",
                context.getColor(android.R.color.holo_green_light));
    }

    void showWrongAnswer() {
        showResult(
                "❌ 答案错误，请重新计算",
                context.getColor(android.R.color.holo_red_light));
        answerEdit.setText("");
    }

    void hideKeyboard() {
        if (!initialized) {
            return;
        }
        InputMethodManager inputMethodManager =
                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(answerEdit.getWindowToken(), 0);
        }
    }

    void hide() {
        if (!initialized) {
            return;
        }

        shown = false;
        selectingText = false;
        stopFocusTasks();
        hideKeyboard();
        answerEdit.clearFocus();
        applyQuestionStyle(ChallengeType.ARITHMETIC);
        challengeLayout.setVisibility(View.GONE);

        windowLayoutParams.flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        windowManager.updateViewLayout(floatingView, windowLayoutParams);
    }

    private boolean ensureInitialized() {
        if (initialized) {
            return true;
        }
        if (floatingView == null) {
            return false;
        }

        ViewStub challengeStub = floatingView.findViewById(R.id.math_challenge_stub);
        if (challengeStub != null) {
            challengeStub.inflate();
        }

        challengeLayout = floatingView.findViewById(R.id.math_challenge_layout);
        questionText = floatingView.findViewById(R.id.tv_math_question);
        answerEdit = floatingView.findViewById(R.id.et_math_answer);
        resultText = floatingView.findViewById(R.id.tv_math_result);
        questionScrollView = floatingView.findViewById(R.id.sv_math_question);
        Button submitButton = floatingView.findViewById(R.id.btn_submit_answer);
        Button cancelButton = floatingView.findViewById(R.id.btn_cancel_close);
        if (challengeLayout == null
                || questionText == null
                || answerEdit == null
                || resultText == null
                || submitButton == null
                || cancelButton == null) {
            Log.e(TAG, "答题布局懒加载失败");
            return false;
        }

        configureQuestionSelection();
        submitButton.setOnClickListener(
                view -> callbacks.onSubmit(answerEdit.getText().toString().trim()));
        cancelButton.setOnClickListener(view -> callbacks.onCancel());
        answerEdit.setOnEditorActionListener((view, actionId, event) -> {
            boolean isEnterKey =
                    event != null
                            && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_DONE || isEnterKey) {
                submitButton.performClick();
                return true;
            }
            return false;
        });
        answerEdit.setOnClickListener(view -> {
            answerEdit.requestFocus();
            showKeyboard(InputMethodManager.SHOW_FORCED);
        });
        answerEdit.setOnFocusChangeListener((view, hasFocus) -> {
            Log.d(TAG, "答案输入框焦点状态变化: " + hasFocus);
            if (hasFocus && shown) {
                showKeyboard(InputMethodManager.SHOW_IMPLICIT);
            }
        });

        initialized = true;
        return true;
    }

    private void configureQuestionSelection() {
        questionText.setTextIsSelectable(true);
        questionText.setLongClickable(true);
        questionText.setFocusable(true);
        questionText.setFocusableInTouchMode(true);
        if (questionScrollView != null) {
            questionScrollView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }
        questionText.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                selectingText = true;
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                handler.postDelayed(() -> selectingText = false, KEYBOARD_DELAY_MILLIS);
            }
        });
    }

    private void renderQuestion(
            ChallengeType type,
            ChallengeQuestionProvider.Question question) {
        applyQuestionStyle(type);
        questionText.setText(question.getContent());
    }

    private void applyQuestionStyle(ChallengeType type) {
        RelativeLayout.LayoutParams challengeParams =
                (RelativeLayout.LayoutParams) challengeLayout.getLayoutParams();
        LinearLayout.LayoutParams scrollParams = questionScrollView == null
                ? null
                : (LinearLayout.LayoutParams) questionScrollView.getLayoutParams();

        if (type == ChallengeType.ENGLISH_READING) {
            challengeLayout.setBackgroundColor(0xFFF5F5F5);
            questionText.setTextColor(0xFF000000);
            questionText.setTypeface(null, Typeface.NORMAL);
            questionText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

            challengeParams.removeRule(RelativeLayout.BELOW);
            challengeParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            challengeParams.topMargin = dpToPx(90);
            challengeParams.leftMargin = 0;
            challengeParams.rightMargin = 0;
            if (scrollParams != null) {
                scrollParams.height = dpToPx(500);
            }
        } else {
            challengeLayout.setBackgroundColor(0xFF333333);
            questionText.setTextColor(0xFFFFFFFF);
            questionText.setTypeface(null, Typeface.BOLD);
            int fontSize = type == ChallengeType.MIXED || type == ChallengeType.REASONING
                    ? 16
                    : 20;
            questionText.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);

            challengeParams.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
            challengeParams.addRule(RelativeLayout.BELOW, R.id.top_info_layout);
            challengeParams.topMargin = dpToPx(10);
            challengeParams.leftMargin = dpToPx(20);
            challengeParams.rightMargin = dpToPx(20);
            if (scrollParams != null) {
                scrollParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            }
        }

        challengeLayout.setLayoutParams(challengeParams);
        if (questionScrollView != null) {
            questionScrollView.setLayoutParams(scrollParams);
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
        resultText.setText("");
        resultText.setVisibility(View.GONE);
    }

    private void updateTextSelectionState() {
        if (!selectingText) {
            return;
        }
        try {
            int selectionStart = questionText.getSelectionStart();
            int selectionEnd = questionText.getSelectionEnd();
            if (selectionStart < 0 || selectionEnd < 0 || selectionStart == selectionEnd) {
                selectingText = false;
            }
        } catch (RuntimeException exception) {
            selectingText = false;
        }
    }

    private void keepAnswerInputFocused() {
        if (!shown) {
            return;
        }

        updateTextSelectionState();
        if (!selectingText && !answerEdit.hasFocus()) {
            Log.d(TAG, "检测到答案输入框失去焦点，重新获得焦点");
            answerEdit.requestFocus();
            showKeyboard(InputMethodManager.SHOW_IMPLICIT);
        }
        handler.postDelayed(focusKeeper, FOCUS_CHECK_INTERVAL_MILLIS);
    }

    private void showKeyboardAndStartFocusKeeper() {
        if (!shown) {
            return;
        }
        answerEdit.requestFocus();
        showKeyboard(InputMethodManager.SHOW_FORCED);
        handler.postDelayed(focusKeeper, FOCUS_CHECK_INTERVAL_MILLIS);
        Log.d(TAG, "输入法显示完成，开始焦点保持机制");
    }

    private void showKeyboard(int mode) {
        InputMethodManager inputMethodManager =
                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.showSoftInput(answerEdit, mode);
        }
    }

    private void stopFocusTasks() {
        handler.removeCallbacks(showKeyboardRunnable);
        handler.removeCallbacks(focusKeeper);
    }

    private int dpToPx(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }
}
