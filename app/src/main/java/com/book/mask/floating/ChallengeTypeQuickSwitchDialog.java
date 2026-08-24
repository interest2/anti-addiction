package com.book.mask.floating;

import android.app.AlertDialog;
import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.book.mask.challenge.retelling.RetellingPreflight;
import com.book.mask.challenge.retelling.ListeningRetellingPreflight;
import com.book.mask.config.ChallengeType;
import com.book.mask.constant.QuestionConst;
import com.book.mask.personalize.ChallengeSettingsManager;

/**
 * 悬浮窗警示语区左上角双击手势：弹出题型快捷切换弹窗。
 * 只切换题型本身，不涉及难度等细节的修改；细节沿用对应题型此前已设置过的状态。
 */
final class ChallengeTypeQuickSwitchDialog {

    /** 角落命中区域边长，只在警示语区左上角这块区域内响应双击，避免误触。 */
    private static final int CORNER_HIT_SIZE_DP = 56;

    private static AlertDialog activeDialog;

    private ChallengeTypeQuickSwitchDialog() {
    }

    /** 在警示语区 View 上安装角落双击手势；floatingView 每次重新 inflate 都需重新调用。 */
    static void attach(Context context, View topInfoLayout) {
        float cornerPx = CORNER_HIT_SIZE_DP * topInfoLayout.getResources().getDisplayMetrics().density;

        GestureDetector detector = new GestureDetector(
                context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                showDialog(context);
                return true;
            }
        });

        boolean[] cornerGestureActive = {false};
        topInfoLayout.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                cornerGestureActive[0] = event.getX() <= cornerPx && event.getY() <= cornerPx;
            }
            if (!cornerGestureActive[0]) {
                return false;
            }
            detector.onTouchEvent(event);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                cornerGestureActive[0] = false;
            }
            return true;
        });
    }

    /** 悬浮窗隐藏或暂停时收起弹窗，避免离开目标 APP 后弹窗孤悬在别的界面上。 */
    static void dismissIfShowing() {
        if (activeDialog != null && activeDialog.isShowing()) {
            activeDialog.dismiss();
        }
        activeDialog = null;
    }

    private static void showDialog(Context context) {
        dismissIfShowing();

        ChallengeSettingsManager settings = new ChallengeSettingsManager(context);
        ChallengeType[] options = ChallengeType.availableOptions(
                QuestionConst.ENGLISH_READING_ENABLED,
                settings.isVoiceChallengeEnabled(),
                settings.isListeningRetellingEnabled());
        ChallengeType currentType = settings.getChallengeType();

        String[] labels = new String[options.length];
        int checkedIndex = -1;
        for (int i = 0; i < options.length; i++) {
            labels[i] = options[i].getDisplayName();
            if (options[i] == currentType) {
                checkedIndex = i;
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("切换题型")
                .setSingleChoiceItems(labels, checkedIndex, (d, which) -> {
                    d.dismiss();
                    applySelection(context, settings, options[which]);
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnDismissListener(d -> {
            if (activeDialog == d) {
                activeDialog = null;
            }
        });
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        activeDialog = dialog;
        dialog.show();
    }

    /** 仅持久化题型；复述类题目先跑前置检查，避免切到一个当下无法工作的题型。 */
    private static void applySelection(
            Context context, ChallengeSettingsManager settings, ChallengeType selected) {
        if (selected == ChallengeType.RETELLING) {
            String preflightError = RetellingPreflight.check(context);
            if (preflightError != null) {
                Toast.makeText(context, preflightError, Toast.LENGTH_SHORT).show();
                return;
            }
        } else if (selected == ChallengeType.LISTENING_RETELLING) {
            String preflightError = ListeningRetellingPreflight.check(context);
            if (preflightError != null) {
                Toast.makeText(context, preflightError, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        settings.setChallengeType(selected);
        Toast.makeText(context, "已切换为" + selected.getDisplayName(), Toast.LENGTH_SHORT).show();
    }
}
