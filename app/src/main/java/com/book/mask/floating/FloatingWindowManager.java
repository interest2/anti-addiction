package com.book.mask.floating;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.os.Handler;
import android.os.SystemClock;

import com.book.mask.R;
import com.book.mask.challenge.MathChallengeManager;
import com.book.mask.constant.Const;
import com.book.mask.config.CustomApp;
import com.book.mask.config.InputMethodPackageManager;
import com.book.mask.config.Share;
import com.book.mask.personalize.AppSettingsManager;
import com.book.mask.personalize.LeisureTimeManager;
import com.book.mask.personalize.RelaxManager;
import com.book.mask.network.TextFetcher;

/**
 * 悬浮窗管理器
 * 负责悬浮窗的显示、隐藏、内容更新等所有相关功能
 */
public class FloatingWindowManager {
    private static final String TAG = "FloatingWindowManager";
    
    private Context context;
    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams layoutParams;
    private boolean isFloatingWindowVisible = false;
    private CustomApp currentWindowApp;
    private final WindowSuspensionState windowSuspensionState = new WindowSuspensionState();
    private SuspensionMode suspensionMode = SuspensionMode.NONE;
    private float suspensionOriginalWindowAlpha = 1.0f;
    private int suspensionOriginalWindowFlags = 0;
    private String pageTransitionTargetPackage;
    private Runnable pageTransitionExpiryRunnable;
    private long pageTransitionGeneration = 0;
    private CustomApp systemUiRecoveryApp;

    private enum SuspensionMode {
        NONE,
        TRANSPARENT_WINDOW,
        INVISIBLE_VIEW
    }
    
    // 管理器依赖
    private MathChallengeManager mathChallengeManager;
    private AppSettingsManager appSettingsManager;
    private LeisureTimeManager leisureTimeManager;
    private RelaxManager relaxManager;
    private TextFetcher textFetcher;
    private Handler handler;
    
    // 回调接口
    private OnFloatingWindowListener listener;
    
    public interface OnFloatingWindowListener {
        void onMathChallengeCorrect();
        void onMathChallengeCancel();
        boolean onLeisureTimeCloseRequested();
        void onFloatingWindowShownFromHidden();
    }
    
    public FloatingWindowManager(Context context, WindowManager windowManager, 
                                AppSettingsManager appSettingsManager,
                                LeisureTimeManager leisureTimeManager,
                                RelaxManager relaxManager,
                                TextFetcher textFetcher, Handler handler) {
        this.context = context;
        this.windowManager = windowManager;
        this.appSettingsManager = appSettingsManager;
        this.leisureTimeManager = leisureTimeManager;
        this.relaxManager = relaxManager;
        this.textFetcher = textFetcher;
        this.handler = handler;
    }
    
    public void setOnFloatingWindowListener(OnFloatingWindowListener listener) {
        this.listener = listener;
    }
    
    /**
     * 显示悬浮窗
     */
    public void showFloatingWindow(CustomApp currentActiveApp) {
        if (currentActiveApp != null
                && leisureTimeManager.isLeisureTimeActiveForApp(
                        currentActiveApp.getPackageName())) {
            Log.v(TAG, "APP " + currentActiveApp.getAppName()
                    + " 正在休闲解禁，跳过显示悬浮窗");
            return;
        }

        if (tryResumeFromPageTransition(currentActiveApp)) {
            return;
        }

        if (isFloatingWindowVisible) {
            Log.v(TAG, "悬浮窗已显示，跳过重复显示");
            return;
        }
        Log.d(TAG, "开始显示悬浮窗");
        
        // 移除现有悬浮窗（如果存在）
        if (floatingView != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                Log.w(TAG, "移除旧悬浮窗时出错", e);
            }
            floatingView = null;
        }
        
        // 创建悬浮窗布局
        LayoutInflater inflater = LayoutInflater.from(context);
        floatingView = inflater.inflate(R.layout.floating_window_layout, null);
        currentWindowApp = currentActiveApp;
        String currentPackageName = currentActiveApp == null ? null : currentActiveApp.getPackageName();
        layoutParams = FloatHelper.getLayoutParams(windowManager, appSettingsManager, currentPackageName);
        
        // 初始化数学题验证管理器
        if (floatingView != null) {
            mathChallengeManager = new MathChallengeManager(
                context, floatingView, windowManager, layoutParams, handler, (FloatService) context
            );
            
            // 设置当前APP，用于微信APP的特殊处理
            if (currentActiveApp != null) {
                mathChallengeManager.setCurrentApp(currentActiveApp);
            }
            
            // 更新悬浮窗内容（包括日常提醒）
            updateFloatingWindowContent(currentActiveApp);
            
            mathChallengeManager.setOnMathChallengeListener(new MathChallengeManager.OnMathChallengeListener() {
                @Override
                public void onAnswerCorrect() {
                    Log.d(TAG, "数学题验证成功，关闭悬浮窗");
                    if (listener != null) {
                        listener.onMathChallengeCorrect();
                    }
                }
                
                @Override
                public void onChallengeCancel() {
                    Log.d(TAG, "用户取消数学题验证");
                    if (listener != null) {
                        listener.onMathChallengeCancel();
                    }
                }
            });
            
            Button closeButton = floatingView.findViewById(R.id.btn_close);
            closeButton.setOnClickListener(v -> {
                Log.d(TAG, "用户点击关闭按钮");

                if (leisureTimeManager.isLeisureTimeArmed()
                        && listener != null
                        && listener.onLeisureTimeCloseRequested()) {
                    Log.d(TAG, "使用休闲时刻免答题关闭悬浮窗");
                // 微信APP直接当作答题通过，不显示数学题
                } else if (currentWindowApp != null &&
                    "com.tencent.mm".equals(currentWindowApp.getPackageName())) {
                    Log.d(TAG, "微信APP直接当作答题通过");
                    // 直接调用答题成功的逻辑
                    if (mathChallengeManager != null && mathChallengeManager.getOnMathChallengeListener() != null) {
                        mathChallengeManager.getOnMathChallengeListener().onAnswerCorrect();
                    }
                } else {
                    // 其他APP显示数学题验证界面
                    InputMethodPackageManager.getInstance().registerDefaultInputMethod(context);
                    mathChallengeManager.showMathChallenge();
                }
            });

            // 添加悬浮窗到窗口管理器
            try {
                windowManager.addView(floatingView, layoutParams);
                isFloatingWindowVisible = true;
                clearAllSuspensionState();
                Share.isFloatingWindowVisible = true; // 同步状态
                Log.d(TAG, "悬浮窗显示成功");
                notifyShownFromHidden();
            } catch (Exception e) {
                Log.e(TAG, "显示悬浮窗失败", e);
                isFloatingWindowVisible = false;
                currentWindowApp = null;
                Share.isFloatingWindowVisible = false; // 同步状态
            }

            // 展示已用上次预取的缓存文字渲染；此处静默预取下次文字，不刷新当前窗口
            prefetchReminderText(currentActiveApp);
        }
    }
    
    /**
     * 隐藏悬浮窗
     */
    public void hideFloatingWindow() {
        if (isFloatingWindowVisible) {
            Log.d(TAG, "开始隐藏悬浮窗");
            
            // 隐藏数学题验证界面
            if (mathChallengeManager != null && mathChallengeManager.isMathChallengeActive()) {
                mathChallengeManager.hideMathChallenge();
            }
            
            try {
                if (floatingView != null && windowManager != null) {
                    windowManager.removeView(floatingView);
                    floatingView = null;
                    
                    mathChallengeManager = null; // 清理管理器引用
                    
                    Log.d(TAG, "悬浮窗隐藏成功");
                }
            } catch (Exception e) {
                Log.e(TAG, "隐藏悬浮窗失败", e);
            }

            isFloatingWindowVisible = false;
            currentWindowApp = null;
            clearAllSuspensionState();
            Share.isFloatingWindowVisible = false; // 同步状态
        }
    }

    /**
     * 同一 APP 暂时离开目标页面时保留已绘制 Window；超过保留期仍未返回则释放。
     */
    public void suspendForPageTransition(CustomApp targetApp) {
        suspendForTransitionReuse(targetApp, Const.PAGE_TRANSITION_WINDOW_REUSE_MS, "离开目标页面");
    }

    /**
     * 300ms 包名复核场景：离开目标 APP 时保留已绘制 Window（透明且不可触摸），
     * 供复核期及后续短时返回复用；超过保留期仍未返回则释放。
     */
    public void suspendForPackageTransition(CustomApp targetApp) {
        suspendForTransitionReuse(targetApp, Const.PACKAGE_TRANSITION_WINDOW_REUSE_MS, "离开目标 APP");
    }

    private void suspendForTransitionReuse(CustomApp targetApp, long reuseMs, String leaveLabel) {
        if (!isFloatingWindowVisible || floatingView == null || isSuspendedForPageTransition()) {
            return;
        }

        String targetPackage = targetApp == null ? null : targetApp.getPackageName();
        String currentPackage = currentWindowApp == null ? null : currentWindowApp.getPackageName();
        if (targetPackage == null || !targetPackage.equals(currentPackage)) {
            hideFloatingWindow();
            return;
        }

        if (!suspendAttachedWindow(WindowSuspensionState.Reason.PAGE_TRANSITION)) {
            Log.w(TAG, leaveLabel + "时无法暂停 Window，直接移除后等待按需重建");
            hideFloatingWindow();
            return;
        }

        pageTransitionTargetPackage = targetPackage;
        schedulePageTransitionExpiry(reuseMs);
        Log.d(TAG, leaveLabel + "，悬浮窗已临时隐藏，方式=" + suspensionModeForLog()
                + "，保留 " + reuseMs + "ms");
    }

    private boolean tryResumeFromPageTransition(CustomApp targetApp) {
        if (!isSuspendedForPageTransition() || floatingView == null) {
            return false;
        }

        String targetPackage = targetApp == null ? null : targetApp.getPackageName();
        if (targetPackage == null) {
            finishPageTransitionHide();
            return false;
        }

        long startedAt = SystemClock.elapsedRealtimeNanos();
        // 暖窗口原本绑定离开时的目标 APP；切到另一个目标 APP 时不再丢弃重建，
        // 而是就地把已绘制的 Window 重新适配到新 APP（几何、内容、答题上下文）后复用。
        boolean switchingTargetApp = !targetPackage.equals(pageTransitionTargetPackage);
        if (switchingTargetApp) {
            adaptSuspendedWindowToApp(targetApp);
        }
        updateFloatingWindowContent(targetApp);
        if (mathChallengeManager != null) {
            mathChallengeManager.setCurrentApp(targetApp);
        }
        if (!resumeAttachedWindow(WindowSuspensionState.Reason.PAGE_TRANSITION)) {
            Log.e(TAG, "恢复页面切换保留的 Window 失败，重新创建悬浮窗");
            finishPageTransitionHide();
            return false;
        }
        currentWindowApp = targetApp;
        clearPageTransitionMetadata();
        Log.d(TAG, (switchingTargetApp
                        ? "悬浮窗跨目标 APP 复用暖窗口完成，耗时 "
                        : "悬浮窗从页面切换临时隐藏中恢复完成，耗时 ")
                + elapsedMillisSince(startedAt) + "ms");
        prefetchReminderText(targetApp);
        notifyIfWindowActuallyShown();
        return true;
    }

    /**
     * 复用暖窗口切到另一个目标 APP 时，按新 APP 的偏移重算 Window 几何。
     * layoutParams 被 ChallengeViewController 持有引用，只能原地改写、不能替换。
     * 此时 Window 仍处暂停态（透明或 INVISIBLE），更新几何不会造成可见跳动。
     */
    private void adaptSuspendedWindowToApp(CustomApp targetApp) {
        if (layoutParams == null || windowManager == null || floatingView == null) {
            return;
        }

        String packageName = targetApp.getPackageName();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        int topOffset = appSettingsManager.getAppFloatingTopOffset(packageName);
        int bottomOffset = appSettingsManager.getAppFloatingBottomOffset(packageName);
        layoutParams.y = topOffset;
        layoutParams.height = displayMetrics.heightPixels - topOffset - bottomOffset;
        try {
            windowManager.updateViewLayout(floatingView, layoutParams);
        } catch (RuntimeException e) {
            Log.w(TAG, "跨目标 APP 复用时更新悬浮窗几何失败", e);
        }
    }

    private void schedulePageTransitionExpiry(long reuseMs) {
        clearPageTransitionExpiryCallback();
        long generation = ++pageTransitionGeneration;
        pageTransitionExpiryRunnable = () -> {
            if (generation != pageTransitionGeneration || !isSuspendedForPageTransition()) {
                return;
            }
            pageTransitionExpiryRunnable = null;
            Log.d(TAG, "临时保留期结束，移除悬浮窗");
            finishPageTransitionHide();
        };
        handler.postDelayed(pageTransitionExpiryRunnable, reuseMs);
    }

    private void finishPageTransitionHide() {
        if (!isSuspendedForPageTransition()) {
            return;
        }
        hideFloatingWindow();
    }

    private void clearPageTransitionMetadata() {
        clearPageTransitionExpiryCallback();
        pageTransitionTargetPackage = null;
    }

    private void clearPageTransitionExpiryCallback() {
        pageTransitionGeneration++;
        if (pageTransitionExpiryRunnable != null) {
            handler.removeCallbacks(pageTransitionExpiryRunnable);
            pageTransitionExpiryRunnable = null;
        }
    }

    private boolean suspendAttachedWindow(WindowSuspensionState.Reason reason) {
        boolean shouldApplyPhysicalHide = windowSuspensionState.suspend(reason);
        if (!shouldApplyPhysicalHide) {
            return true;
        }

        if (layoutParams != null && windowManager != null && floatingView.getVisibility() == View.VISIBLE) {
            float originalAlpha = layoutParams.alpha;
            int originalFlags = layoutParams.flags;
            layoutParams.alpha = 0.0f;
            layoutParams.flags = originalFlags | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            try {
                windowManager.updateViewLayout(floatingView, layoutParams);
                suspensionOriginalWindowAlpha = originalAlpha;
                suspensionOriginalWindowFlags = originalFlags;
                suspensionMode = SuspensionMode.TRANSPARENT_WINDOW;
                return true;
            } catch (RuntimeException e) {
                layoutParams.alpha = originalAlpha;
                layoutParams.flags = originalFlags;
                Log.w(TAG, "无法使用透明 Window 临时隐藏，回退到 INVISIBLE", e);
            }
        }

        try {
            floatingView.setVisibility(View.INVISIBLE);
            suspensionMode = SuspensionMode.INVISIBLE_VIEW;
            return true;
        } catch (RuntimeException e) {
            windowSuspensionState.resume(reason);
            resetSuspensionRenderingState();
            Log.e(TAG, "悬浮窗临时隐藏失败", e);
            return false;
        }
    }

    private boolean resumeAttachedWindow(WindowSuspensionState.Reason reason) {
        WindowSuspensionState.ResumeAction action = windowSuspensionState.resume(reason);
        if (action == WindowSuspensionState.ResumeAction.NONE
                || action == WindowSuspensionState.ResumeAction.KEEP_HIDDEN) {
            return true;
        }

        try {
            if (suspensionMode == SuspensionMode.TRANSPARENT_WINDOW) {
                layoutParams.alpha = suspensionOriginalWindowAlpha;
                layoutParams.flags = suspensionOriginalWindowFlags;
                windowManager.updateViewLayout(floatingView, layoutParams);
            } else if (suspensionMode == SuspensionMode.INVISIBLE_VIEW) {
                floatingView.setVisibility(View.VISIBLE);
            }
            resetSuspensionRenderingState();
            return true;
        } catch (RuntimeException e) {
            resetSuspensionRenderingState();
            Log.e(TAG, "恢复临时隐藏的悬浮窗失败", e);
            return false;
        }
    }

    private String suspensionModeForLog() {
        return suspensionMode == SuspensionMode.TRANSPARENT_WINDOW
                ? "透明且不可触摸"
                : "INVISIBLE 回退";
    }

    private void notifyIfWindowActuallyShown() {
        if (!windowSuspensionState.isSuspended()) {
            notifyShownFromHidden();
        }
    }

    private void notifyShownFromHidden() {
        if (listener != null) {
            listener.onFloatingWindowShownFromHidden();
        }
    }

    private void clearAllSuspensionState() {
        windowSuspensionState.clear();
        clearPageTransitionMetadata();
        systemUiRecoveryApp = null;
        resetSuspensionRenderingState();
    }

    private void resetSuspensionRenderingState() {
        suspensionMode = SuspensionMode.NONE;
        suspensionOriginalWindowAlpha = 1.0f;
        suspensionOriginalWindowFlags = 0;
    }

    private double elapsedMillisSince(long startedAtNanos) {
        return (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000_000.0;
    }

    /**
     * SystemUI 持续超过确认时间时临时暂停遮罩，但不从 WindowManager 移除。
     * 回到目标 APP 后直接恢复原 View，避免重新 inflate/addView 产生额外空档。
     */
    public void setSuspendedForSystemUi(boolean suspended) {
        boolean currentlySuspended = windowSuspensionState.hasReason(
                WindowSuspensionState.Reason.SYSTEM_UI
        );
        if (currentlySuspended == suspended) {
            if (!suspended && !isFloatingWindowVisible && systemUiRecoveryApp != null) {
                CustomApp recoveryApp = systemUiRecoveryApp;
                systemUiRecoveryApp = null;
                showFloatingWindow(recoveryApp);
            }
            return;
        }

        if (suspended) {
            if (!isFloatingWindowVisible || floatingView == null) {
                return;
            }
            systemUiRecoveryApp = null;
            if (!suspendAttachedWindow(WindowSuspensionState.Reason.SYSTEM_UI)) {
                CustomApp recoveryApp = currentWindowApp;
                hideFloatingWindow();
                systemUiRecoveryApp = recoveryApp;
                return;
            }
            Log.d(TAG, "SystemUI 持续存在，临时暂停悬浮窗，方式=" + suspensionModeForLog());
            return;
        }

        CustomApp recoveryApp = currentWindowApp;
        if (!resumeAttachedWindow(WindowSuspensionState.Reason.SYSTEM_UI)) {
            hideFloatingWindow();
            if (recoveryApp != null) {
                showFloatingWindow(recoveryApp);
            }
            return;
        }
        Log.d(TAG, "回到目标 APP，恢复悬浮窗");
        notifyIfWindowActuallyShown();
    }
    
    /**
     * 更新悬浮窗内容
     */
    public void updateFloatingWindowContent(CustomApp currentActiveApp) {
        if (floatingView == null) return;
        
        TextView contentText = floatingView.findViewById(R.id.tv_content);
        TextView unlockDurationText = floatingView.findViewById(R.id.tv_unlock_duration_hint);
        if (unlockDurationText != null) {
            unlockDurationText.setVisibility(View.GONE);
        }
        if (contentText != null) {
            // 获取缓存的动态文字内容
            String dynamicText = "";
    
            if (currentActiveApp != null) {
                String packageName = currentActiveApp.getPackageName();
                String source = appSettingsManager.getAppHintSource(packageName);
    
                // 自定义来源
                if (Const.CUSTOM_HINT_SOURCE.equals(source)) {
                    dynamicText = appSettingsManager.getAppHintCustomText(packageName);
                } else {
                    // 大模型来源
                    if (textFetcher != null) {
                        dynamicText = textFetcher.getCachedText();
                    }
                }
            }
    
            // 显示动态文字和时间间隔信息
            String content = dynamicText;
            if (relaxManager != null) {
                // 使用当前APP的时间间隔显示
                int intervalSeconds;
                if (currentActiveApp != null) {
                    /*如果上次关闭时是宽松模式，则本次显示应当切为严格模式*/
                    int appLastCloseInterval = relaxManager.getAppLastCloseInterval(currentActiveApp);
                    Log.d(TAG, "appLastCloseInterval: " + appLastCloseInterval);
    //                    if(relaxManager.isLastRelaxedMode(appLastCloseInterval)){
    //
    //                        intervalSeconds = relaxManager.getMaxStrictInterval();
    //                        relaxManager.setAppInterval(currentActiveApp, intervalSeconds);
    //                    }else {
                        intervalSeconds = relaxManager.getAppInterval(currentActiveApp);
    //                    }
                } else {
                    intervalSeconds = relaxManager.getDefaultInterval();
                }

                String intervalMinutes = intervalSeconds % 60 == 0
                        ? String.valueOf(intervalSeconds / 60)
                        : String.valueOf(intervalSeconds / 60.0);
                if (unlockDurationText != null) {
                    unlockDurationText.setText("下次解禁时长 " + intervalMinutes + " 分钟");
                    unlockDurationText.setVisibility(View.VISIBLE);
                }
                 
                // 添加日期前缀
                String targetDateStr = appSettingsManager.getTargetCompletionDate();
                String datePrefix = FloatHelper.hintDate(targetDateStr);
                content = datePrefix + content;

                contentText.setText(content);
                contentText.setVisibility(content.isEmpty() ? View.GONE : View.VISIBLE);
            }
        }
        
        // 更新日常提醒显示
        updateStrictReminderDisplay();
    }
    
    /**
     * 更新日常提醒显示
     */
    private void updateStrictReminderDisplay() {
        if (floatingView == null) return;
        
        android.view.View strictReminderLayout = floatingView.findViewById(R.id.strict_reminder_layout);
        android.widget.TextView strictReminderText = floatingView.findViewById(R.id.tv_strict_reminder);
        android.widget.TextView strictReminderHint = floatingView.findViewById(R.id.tv_strict_reminder_hint);
        
        if (strictReminderLayout != null && strictReminderText != null && strictReminderHint != null) {
            String strictReminder = appSettingsManager.getFloatingStrictReminder();
            boolean hasClickedSettings = appSettingsManager.getFloatingStrictReminderSettingsClicked();
            int fontSize = appSettingsManager.getFloatingStrictReminderFontSize();
            int fontColor = appSettingsManager.getFloatingStrictReminderFontColor();

            // 应用自定义字体大小
            strictReminderText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize);
            // 应用自定义字体颜色
            strictReminderText.setTextColor(fontColor);
            
            // 如果用户没有设置过提醒文字，显示默认文字
            if (strictReminder.isEmpty()) {
                strictReminderText.setText(Const.DEFAULT_STRICT_REMINDER);
                strictReminderLayout.setVisibility(android.view.View.VISIBLE);
                
                // 如果用户没有点击过设置按钮，显示小字提示
                if (!hasClickedSettings) {
                    strictReminderHint.setVisibility(android.view.View.VISIBLE);
                } else {
                    // 用户点击过设置按钮，只隐藏小字提示
                    strictReminderHint.setVisibility(android.view.View.GONE);
                }
            } else {
                // 用户设置了自定义提醒文字
                strictReminderText.setText(strictReminder);
                strictReminderHint.setVisibility(android.view.View.GONE);
                strictReminderLayout.setVisibility(android.view.View.VISIBLE);
            }
        }
    }

    /**
     * 悬浮窗展示（首次显示或暖窗口复用）时，预取当前目标与风格对应的提醒文字。
     * 请求完成后仅更新缓存，不替换当前已经展示的文案；新文案在下次创建窗口时读取。
     */
    private void prefetchReminderText(CustomApp app) {
        if (textFetcher == null || app == null) {
            return;
        }
        String packageName = app.getPackageName();
        if (!Const.DEFAULT_HINT_SOURCE.equals(appSettingsManager.getAppHintSource(packageName))) {
            return;
        }
        textFetcher.prefetchLatestText();
    }

    public void onReminderContentChanged() {
        if (textFetcher != null) {
            textFetcher.onProviderConfigurationChanged();
        }
        if (currentWindowApp == null) {
            return;
        }
        updateFloatingWindowContent(currentWindowApp);
        prefetchReminderText(currentWindowApp);
    }

    public void onReminderProviderChanged() {
        onReminderContentChanged();
    }
    
    public boolean isFloatingWindowVisible() {
        return isFloatingWindowVisible;
    }
    
    public MathChallengeManager getMathChallengeManager() {
        return mathChallengeManager;
    }

    public boolean isSuspendedForPageTransition() {
        return windowSuspensionState.hasReason(WindowSuspensionState.Reason.PAGE_TRANSITION);
    }
    
    public void cleanup() {
        hideFloatingWindow();
    }
}
