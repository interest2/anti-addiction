package com.book.mask.ui;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.book.mask.constant.Const;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Map;
import java.util.WeakHashMap;

public final class UiFeedback {
    private static final Map<View, Runnable> ERROR_CLEAR_TASKS = new WeakHashMap<>();

    private UiFeedback() {
    }

    public static void show(Context context, CharSequence message) {
        Snackbar snackbar = createSnackbar(context, message);
        if (snackbar != null) {
            snackbar.show();
        }
    }

    public static void show(View anchor, CharSequence message) {
        make(anchor, message).show();
    }

    public static void showError(Context context, CharSequence message) {
        Snackbar snackbar = createSnackbar(context, message);
        if (snackbar == null) {
            return;
        }

        applyErrorStyle(snackbar);
        snackbar.show();
    }

    public static void showError(View anchor, CharSequence message) {
        Snackbar snackbar = make(anchor, message);
        applyErrorStyle(snackbar);
        snackbar.show();
    }

    public static void showAction(Context context,
                                  CharSequence message,
                                  CharSequence actionText,
                                  View.OnClickListener listener) {
        Snackbar snackbar = createSnackbar(context, message);
        if (snackbar != null) {
            snackbar.setAction(actionText, listener);
            snackbar.show();
        }
    }

    public static Snackbar make(View anchor, CharSequence message) {
        return centerAndSize(
                Snackbar.make(anchor, message, Const.TRANSIENT_FEEDBACK_DURATION_MS),
                anchor);
    }

    public static void showInputError(EditText input, CharSequence message) {
        input.setError(message);
        input.requestFocus();
        scheduleErrorClear(input, () -> input.setError(null));
    }

    public static void showInputError(
            TextInputLayout layout,
            EditText input,
            CharSequence message) {
        layout.setError(message);
        input.requestFocus();
        scheduleErrorClear(layout, () -> layout.setError(null));
    }

    public static void showTemporaryText(
            TextView textView,
            CharSequence message,
            int color) {
        textView.setText(message);
        textView.setTextColor(color);
        textView.setVisibility(View.VISIBLE);
        scheduleErrorClear(textView, () -> {
            textView.setText("");
            textView.setVisibility(View.GONE);
        });
    }

    private static void applyErrorStyle(Snackbar snackbar) {
        View snackbarView = snackbar.getView();
        int errorColor = MaterialColors.getColor(
                snackbarView,
                com.google.android.material.R.attr.colorError
        );
        int onErrorColor = MaterialColors.getColor(
                snackbarView,
                com.google.android.material.R.attr.colorOnError
        );
        snackbar.setBackgroundTint(errorColor);
        snackbar.setTextColor(onErrorColor);
    }

    private static Snackbar createSnackbar(Context context, CharSequence message) {
        Activity activity = findActivity(context);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return null;
        }

        View contentView = activity.findViewById(android.R.id.content);
        if (contentView == null) {
            return null;
        }

        return make(contentView, message);
    }

    private static Snackbar centerAndSize(Snackbar snackbar, View anchor) {
        View snackbarView = snackbar.getView();
        ViewGroup.LayoutParams params = snackbarView.getLayoutParams();
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        snackbarView.setMinimumWidth(0);
        int bottomOffset = Math.round(getFeedbackHostHeight(anchor) * 2f / 5f);
        if (params instanceof ViewGroup.MarginLayoutParams) {
            int margin = Math.round(16 * snackbarView.getResources()
                    .getDisplayMetrics().density);
            ((ViewGroup.MarginLayoutParams) params).leftMargin = margin;
            ((ViewGroup.MarginLayoutParams) params).rightMargin = margin;
            ((ViewGroup.MarginLayoutParams) params).bottomMargin = bottomOffset;
        }
        int gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        if (params instanceof CoordinatorLayout.LayoutParams) {
            ((CoordinatorLayout.LayoutParams) params).gravity = gravity;
            snackbarView.setLayoutParams(params);
        } else if (params instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) params).gravity = gravity;
            snackbarView.setLayoutParams(params);
        }
        return snackbar;
    }

    /**
     * 提示的纵向偏移按承载窗口的高度计算：弹窗内锚定时窗口只有弹窗那么高，
     * 若按整屏高度算偏移会把提示挤出弹窗、被裁剪掉。
     */
    private static int getFeedbackHostHeight(View anchor) {
        int rootHeight = anchor.getRootView().getHeight();
        return rootHeight > 0
                ? rootHeight
                : anchor.getResources().getDisplayMetrics().heightPixels;
    }

    private static void scheduleErrorClear(View owner, Runnable clearError) {
        Runnable previous = ERROR_CLEAR_TASKS.remove(owner);
        if (previous != null) {
            owner.removeCallbacks(previous);
        }

        Runnable[] task = new Runnable[1];
        task[0] = () -> {
            if (ERROR_CLEAR_TASKS.get(owner) == task[0]) {
                ERROR_CLEAR_TASKS.remove(owner);
                clearError.run();
            }
        };
        ERROR_CLEAR_TASKS.put(owner, task[0]);
        owner.postDelayed(task[0], Const.TRANSIENT_FEEDBACK_DURATION_MS);
    }

    private static Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            Context baseContext = ((ContextWrapper) current).getBaseContext();
            if (baseContext == current) {
                break;
            }
            current = baseContext;
        }
        return current instanceof Activity ? (Activity) current : null;
    }
}
