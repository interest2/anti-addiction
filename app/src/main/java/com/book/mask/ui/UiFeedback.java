package com.book.mask.ui;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Map;
import java.util.WeakHashMap;

public final class UiFeedback {
    private static final long INPUT_ERROR_DURATION_MS = 1000L;
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
        make(anchor, message, Snackbar.LENGTH_LONG).show();
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
        Snackbar snackbar = make(anchor, message, Snackbar.LENGTH_LONG);
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

    public static Snackbar make(View anchor, CharSequence message, int duration) {
        return center(Snackbar.make(anchor, message, duration));
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

        return make(contentView, message, Snackbar.LENGTH_LONG);
    }

    private static Snackbar center(Snackbar snackbar) {
        View snackbarView = snackbar.getView();
        ViewGroup.LayoutParams params = snackbarView.getLayoutParams();
        if (params instanceof CoordinatorLayout.LayoutParams) {
            ((CoordinatorLayout.LayoutParams) params).gravity = Gravity.CENTER;
            snackbarView.setLayoutParams(params);
        } else if (params instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) params).gravity = Gravity.CENTER;
            snackbarView.setLayoutParams(params);
        }
        return snackbar;
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
        owner.postDelayed(task[0], INPUT_ERROR_DURATION_MS);
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
