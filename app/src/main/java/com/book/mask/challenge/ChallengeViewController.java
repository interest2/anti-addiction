package com.book.mask.challenge;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
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
import com.book.mask.constant.Const;

/**
 * 负责答题悬浮层的组件绑定、渲染、输入法与焦点管理。
 */
final class ChallengeViewController {

    private static final String TAG = "ChallengeView";
    private static final long KEYBOARD_DELAY_MILLIS = 300L;
    private static final long FOCUS_CHECK_INTERVAL_MILLIS = 1000L;
    private static final int KEYBOARD_VISIBILITY_THRESHOLD_DP = 100;
    private static final int KEYBOARD_CHALLENGE_GAP_DP = 8;
    private static final int MIN_QUESTION_HEIGHT_DP = 72;
    private static final int MAX_QUESTION_HEIGHT_DP = 300;
    private static final int ENGLISH_QUESTION_HEIGHT_DP = 500;
    private static final int CHALLENGE_TOP_MARGIN_DP = 16;

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
    private TextView challengeTitle;
    private TextView questionText;
    private EditText answerEdit;
    private TextView resultText;
    private ScrollView questionScrollView;
    private View closeButton;
    private View topInfoLayout;

    private boolean initialized;
    private boolean shown;
    private boolean selectingText;
    private boolean keyboardLayoutListenerRegistered;
    private boolean topInfoVisibilityCaptured;
    private int topInfoVisibilityBeforeChallenge;
    private ChallengeType currentType = ChallengeType.ARITHMETIC;

    private final Runnable focusKeeper = this::keepAnswerInputFocused;
    private final Runnable showKeyboardRunnable = this::showKeyboardAndStartFocusKeeper;
    private final Runnable keyboardPositionUpdater = this::updateQuestionHeightForKeyboard;
    private final Runnable resultHider = this::hideResult;
    private final ViewTreeObserver.OnGlobalLayoutListener keyboardLayoutListener =
            this::updateQuestionHeightForKeyboard;

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
        hideTopInfo();
        renderQuestion(type, question);
        answerEdit.setText("");
        hideResult();
        challengeLayout.setTranslationY(0);
        challengeLayout.setVisibility(View.VISIBLE);
        challengeLayout.bringToFront();
        closeButton.bringToFront();

        windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        windowManager.updateViewLayout(floatingView, windowLayoutParams);
        startKeyboardAwarePositioning();

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
                "❌ 答案错误，切到下一题",
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
        stopKeyboardAwarePositioning();
        hideKeyboard();
        answerEdit.clearFocus();
        applyQuestionStyle(ChallengeType.ARITHMETIC);
        challengeLayout.setTranslationY(0);
        challengeLayout.setVisibility(View.GONE);
        restoreTopInfo();

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
        challengeTitle = floatingView.findViewById(R.id.tv_challenge_title);
        questionText = floatingView.findViewById(R.id.tv_math_question);
        answerEdit = floatingView.findViewById(R.id.et_math_answer);
        resultText = floatingView.findViewById(R.id.tv_math_result);
        questionScrollView = floatingView.findViewById(R.id.sv_math_question);
        closeButton = floatingView.findViewById(R.id.btn_close);
        topInfoLayout = floatingView.findViewById(R.id.top_info_layout);
        Button submitButton = floatingView.findViewById(R.id.btn_submit_answer);
        Button cancelButton = floatingView.findViewById(R.id.btn_cancel_close);
        if (challengeLayout == null
                || questionText == null
                || answerEdit == null
                || resultText == null
                || closeButton == null
                || topInfoLayout == null
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
        currentType = type;
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
            challengeLayout.setBackgroundResource(R.drawable.floating_challenge_bg_light);
            if (challengeTitle != null) {
                challengeTitle.setTextColor(0xFF000000);
            }
            questionText.setTextColor(0xFF000000);
            questionText.setTypeface(null, Typeface.NORMAL);
            questionText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

            challengeParams.removeRule(RelativeLayout.BELOW);
            challengeParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            challengeParams.topMargin = dpToPx(CHALLENGE_TOP_MARGIN_DP);
            challengeParams.leftMargin = 0;
            challengeParams.rightMargin = 0;
            if (scrollParams != null) {
                scrollParams.height = dpToPx(500);
            }
        } else {
            challengeLayout.setBackgroundResource(R.drawable.floating_window_bg);
            if (challengeTitle != null) {
                challengeTitle.setTextColor(0xFFFFFFFF);
            }
            questionText.setTextColor(0xFFFFFFFF);
            questionText.setTypeface(null, Typeface.NORMAL);
            int fontSize = type == ChallengeType.MIXED || type == ChallengeType.REASONING
                    ? 16
                    : 20;
            questionText.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);

            if (shown) {
                challengeParams.removeRule(RelativeLayout.BELOW);
                challengeParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                challengeParams.topMargin = dpToPx(CHALLENGE_TOP_MARGIN_DP);
            } else {
                challengeParams.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
                challengeParams.addRule(RelativeLayout.BELOW, R.id.top_info_layout);
                challengeParams.topMargin = dpToPx(10);
            }
            challengeParams.leftMargin = 0;
            challengeParams.rightMargin = 0;
            if (scrollParams != null) {
                scrollParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            }
        }

        challengeLayout.setLayoutParams(challengeParams);
        questionText.setGravity(
                type == ChallengeType.ARITHMETIC ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        if (questionScrollView != null) {
            questionScrollView.setLayoutParams(scrollParams);
        }
    }

    private void updateQuestionHeightForKeyboard() {
        if (!shown
                || challengeLayout.getVisibility() != View.VISIBLE
                || challengeLayout.getHeight() == 0
                || floatingView.getHeight() == 0) {
            return;
        }

        Rect visibleFrame = new Rect();
        int[] floatingLocation = new int[2];
        floatingView.getWindowVisibleDisplayFrame(visibleFrame);
        floatingView.getLocationOnScreen(floatingLocation);

        int expectedWindowHeight = windowLayoutParams.height > 0
                ? windowLayoutParams.height
                : floatingView.getHeight();
        int availableBottom = Math.min(
                floatingView.getHeight(),
                Math.max(0, visibleFrame.bottom - floatingLocation[1]));
        boolean keyboardVisible = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsets insets = floatingView.getRootWindowInsets();
            if (insets != null && insets.isVisible(WindowInsets.Type.ime())) {
                int imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom;
                if (imeBottom > 0) {
                    keyboardVisible = true;
                    availableBottom = Math.min(
                            availableBottom,
                            Math.max(0, expectedWindowHeight - imeBottom));
                }
            }
        }

        int obscuredHeight = expectedWindowHeight - availableBottom;
        if (!keyboardVisible
                && obscuredHeight < dpToPx(KEYBOARD_VISIBILITY_THRESHOLD_DP)) {
            restoreQuestionHeight();
            challengeLayout.setTranslationY(0);
            return;
        }

        constrainQuestionHeight(availableBottom);
        challengeLayout.setTranslationY(0);
    }

    private int constrainQuestionHeight(int availableBottom) {
        if (questionScrollView == null || questionScrollView.getHeight() == 0) {
            return challengeLayout.getHeight();
        }

        int fixedChallengeHeight =
                Math.max(0, challengeLayout.getHeight() - questionScrollView.getHeight());
        int maximumQuestionHeight = Math.max(
                1,
                availableBottom
                        - challengeLayout.getTop()
                        - dpToPx(KEYBOARD_CHALLENGE_GAP_DP)
                        - fixedChallengeHeight);
        int preferredQuestionHeight = currentType == ChallengeType.ENGLISH_READING
                ? dpToPx(ENGLISH_QUESTION_HEIGHT_DP)
                : Math.min(
                        dpToPx(MAX_QUESTION_HEIGHT_DP),
                        Math.max(questionScrollView.getHeight(), questionText.getMeasuredHeight()));
        int minimumQuestionHeight = Math.min(
                preferredQuestionHeight,
                dpToPx(MIN_QUESTION_HEIGHT_DP));
        int targetQuestionHeight =
                Math.min(preferredQuestionHeight, maximumQuestionHeight);
        if (maximumQuestionHeight >= minimumQuestionHeight) {
            targetQuestionHeight = Math.max(minimumQuestionHeight, targetQuestionHeight);
        }

        LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams) questionScrollView.getLayoutParams();
        if (params.height != targetQuestionHeight) {
            params.height = targetQuestionHeight;
            questionScrollView.setLayoutParams(params);
        }
        return fixedChallengeHeight + targetQuestionHeight;
    }

    private void restoreQuestionHeight() {
        if (questionScrollView == null) {
            return;
        }
        int height = currentType == ChallengeType.ENGLISH_READING
                ? dpToPx(ENGLISH_QUESTION_HEIGHT_DP)
                : LinearLayout.LayoutParams.WRAP_CONTENT;
        LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams) questionScrollView.getLayoutParams();
        if (params.height != height) {
            params.height = height;
            questionScrollView.setLayoutParams(params);
        }
    }

    private void startKeyboardAwarePositioning() {
        if (!keyboardLayoutListenerRegistered) {
            ViewTreeObserver observer = floatingView.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.addOnGlobalLayoutListener(keyboardLayoutListener);
                keyboardLayoutListenerRegistered = true;
            }
        }
        floatingView.removeCallbacks(keyboardPositionUpdater);
        floatingView.post(keyboardPositionUpdater);
    }

    private void stopKeyboardAwarePositioning() {
        floatingView.removeCallbacks(keyboardPositionUpdater);
        if (keyboardLayoutListenerRegistered) {
            ViewTreeObserver observer = floatingView.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnGlobalLayoutListener(keyboardLayoutListener);
            }
            keyboardLayoutListenerRegistered = false;
        }
    }

    private void showResult(String message, int color) {
        if (!initialized) {
            return;
        }
        resultText.setText(message);
        resultText.setTextColor(color);
        resultText.setVisibility(View.VISIBLE);
        handler.removeCallbacks(resultHider);
        handler.postDelayed(resultHider, Const.TRANSIENT_FEEDBACK_DURATION_MS);
    }

    private void hideResult() {
        handler.removeCallbacks(resultHider);
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
