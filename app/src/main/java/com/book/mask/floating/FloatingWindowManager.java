package com.book.mask.floating;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.os.Handler;

import com.book.mask.R;
import com.book.mask.config.Const;
import com.book.mask.config.CustomApp;
import com.book.mask.config.Share;
import com.book.mask.setting.AppSettingsManager;
import com.book.mask.setting.RelaxManager;
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
    
    // 管理器依赖
    private MathChallengeManager mathChallengeManager;
    private AppSettingsManager appSettingsManager;
    private RelaxManager relaxManager;
    private TextFetcher textFetcher;
    private Handler handler;
    
    // 回调接口
    private OnFloatingWindowListener listener;
    
    public interface OnFloatingWindowListener {
        void onMathChallengeCorrect();
        void onMathChallengeCancel();
    }
    
    public FloatingWindowManager(Context context, WindowManager windowManager, 
                                AppSettingsManager appSettingsManager, RelaxManager relaxManager,
                                TextFetcher textFetcher, Handler handler) {
        this.context = context;
        this.windowManager = windowManager;
        this.appSettingsManager = appSettingsManager;
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
        layoutParams = FloatHelper.getLayoutParams(windowManager, appSettingsManager);
        
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
                
                // 微信APP直接当作答题通过，不显示数学题
                if (currentActiveApp != null && 
                    "com.tencent.mm".equals(currentActiveApp.getPackageName())) {
                    Log.d(TAG, "微信APP直接当作答题通过");
                    // 直接调用答题成功的逻辑
                    if (mathChallengeManager != null && mathChallengeManager.getOnMathChallengeListener() != null) {
                        mathChallengeManager.getOnMathChallengeListener().onAnswerCorrect();
                    }
                } else {
                    // 其他APP显示数学题验证界面
                    mathChallengeManager.showMathChallenge();
                }
            });

            // 更新悬浮窗内容，显示当前时间间隔设置
            updateFloatingWindowContent(currentActiveApp);

            // 添加悬浮窗到窗口管理器
            try {
                windowManager.addView(floatingView, layoutParams);
                isFloatingWindowVisible = true;
                Share.isFloatingWindowVisible = true; // 同步状态
                Log.d(TAG, "悬浮窗显示成功");
            } catch (Exception e) {
                Log.e(TAG, "显示悬浮窗失败", e);
                isFloatingWindowVisible = false;
                Share.isFloatingWindowVisible = false; // 同步状态
            }
            
            // 获取下次的文字
            if (currentActiveApp != null) {
                String packageName = currentActiveApp.getPackageName();
                String source = appSettingsManager.getAppHintSource(packageName);
                // 默认来源（大模型）
                if (Const.DEFAULT_HINT_SOURCE.equals(source)){
                    fetchNew();
                }
            }
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
            Share.isFloatingWindowVisible = false; // 同步状态
        }
    }
    
    /**
     * 更新悬浮窗内容
     */
    public void updateFloatingWindowContent(CustomApp currentActiveApp) {
        if (floatingView == null) return;
        
        TextView contentText = floatingView.findViewById(R.id.tv_content);
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
                    if(relaxManager.isLastRelaxedMode(appLastCloseInterval)){
                        intervalSeconds = relaxManager.getMaxStrictInterval();
                        relaxManager.setAppInterval(currentActiveApp, intervalSeconds);
                    }else {
                        intervalSeconds = relaxManager.getAppInterval(currentActiveApp);
                    }
                } else {
                    intervalSeconds = relaxManager.getDefaultInterval();
                }
                
                String intervalText = RelaxManager.getIntervalDisplayText(intervalSeconds);
                String hintTIme = "\n若关闭，" + intervalText + "后将重新显示本页面";
                if (!dynamicText.isEmpty()) {
                    content = dynamicText + "\n" + hintTIme;
                } else {
                    content = hintTIme;
                }
            }
            String targetDateStr = appSettingsManager.getTargetCompletionDate();
            content = FloatHelper.hintDate(targetDateStr) + content;
            contentText.setText(content);
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
            
            // 应用自定义字体大小
            strictReminderText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize);
            
            // 如果用户没有设置过提醒文字，显示默认文字
            if (strictReminder.isEmpty()) {
                strictReminderText.setText("玩手机？不如——\n多喝水、多起身活动");
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
    
    private void fetchNew() {
        // 异步获取最新的动态文字内容
        if (textFetcher != null) {
            textFetcher.fetchLatestText(new TextFetcher.OnTextFetchListener() {
                @Override
                public void onTextFetched(String text) {
                    Log.d(TAG, "获取到新的动态文字");
                }
                @Override
                public void onFetchError(String error) {
                    Log.w(TAG, "获取动态文字失败: " + error);
                    // 保持使用缓存的文字，不做额外处理
                }
            });
        }
    }
    
    public boolean isFloatingWindowVisible() {
        return isFloatingWindowVisible;
    }
    
    public MathChallengeManager getMathChallengeManager() {
        return mathChallengeManager;
    }
    
    public void cleanup() {
        hideFloatingWindow();
    }
}
