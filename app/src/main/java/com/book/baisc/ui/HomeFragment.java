package com.book.baisc.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.book.baisc.R;
import com.book.baisc.config.SettingsManager;
import com.book.baisc.config.Const;

public class HomeFragment extends Fragment {

    private SettingsManager settingsManager;
    private SettingsDialogManager settingsDialogManager;
    
    // 倒计时相关
    private Handler countdownHandler;
    private Runnable countdownRunnable;
    private TextView tvXhsCountdown;
    private TextView tvAlipayCountdown;
    
    // 宽松模式剩余次数相关
    private TextView tvXhsCasualCount;
    private TextView tvAlipayCasualCount;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        // 初始化设置管理器
        settingsManager = new SettingsManager(requireContext());
        settingsDialogManager = new SettingsDialogManager(requireContext(), settingsManager);
        
        // 初始化倒计时相关
        initCountdown(view);
        
        // 设置按钮点击事件
        setupTimeSettingButtons(view);
        setupTagSettingButton(view);
        setupTargetDateSettingButton(view);
        
        // 更新UI状态
        updateCasualButtonState(view);
        updateCasualCountDisplay(view);
        updateAppCasualCountDisplay(view);
        updateTagButtonText(view);
        updateTargetDateButtonText(view);
        
        return view;
    }

    private void setupTimeSettingButtons(View view) {
        Button dailyButton = view.findViewById(R.id.btn_daily_time_setting);
        dailyButton.setOnClickListener(v -> {
            settingsDialogManager.showTimeSettingDialog(true); // true for daily
        });
        
        Button casualButton = view.findViewById(R.id.btn_casual_time_setting);
        casualButton.setOnClickListener(v -> {
            settingsDialogManager.showTimeSettingDialog(false); // false for casual
        });
    }

    private void setupTagSettingButton(View view) {
        Button tagButton = view.findViewById(R.id.btn_tag_setting);
        tagButton.setOnClickListener(v -> settingsDialogManager.showTagSettingDialog());
    }

    private void setupTargetDateSettingButton(View view) {
        Button targetDateButton = view.findViewById(R.id.btn_target_date_setting);
        targetDateButton.setOnClickListener(v -> settingsDialogManager.showTargetDateSettingDialog());
    }

    private void updateCasualButtonState(View view) {
        Button casualButton = view.findViewById(R.id.btn_casual_time_setting);
        settingsDialogManager.updateCasualButtonState(casualButton);
    }

    private void updateCasualCountDisplay(View view) {
        TextView countText = view.findViewById(R.id.tv_casual_count);
        settingsDialogManager.updateCasualCountDisplay(countText);
    }

    private void updateAppCasualCountDisplay(View view) {
        // 更新小红书宽松模式剩余次数显示
        TextView xhsCasualCountText = view.findViewById(R.id.tv_xhs_casual_count);
        settingsDialogManager.updateAppCasualCountDisplay(xhsCasualCountText, Const.SupportedApp.XHS);

        // 更新支付宝宽松模式剩余次数显示
        TextView alipayCasualCountText = view.findViewById(R.id.tv_alipay_casual_count);
        settingsDialogManager.updateAppCasualCountDisplay(alipayCasualCountText, Const.SupportedApp.ALIPAY);
    }

    private void updateTagButtonText(View view) {
        Button tagButton = view.findViewById(R.id.btn_tag_setting);
        settingsDialogManager.updateTagButtonText(tagButton);
    }

    private void updateTargetDateButtonText(View view) {
        Button targetDateButton = view.findViewById(R.id.btn_target_date_setting);
        settingsDialogManager.updateDateButtonText(targetDateButton);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每次回到前台时更新UI状态
        if (getView() != null) {
            updateCasualButtonState(getView());
            updateCasualCountDisplay(getView());
            updateAppCasualCountDisplay(getView());
            updateTagButtonText(getView());
            updateTargetDateButtonText(getView());
        }
        
        // 启动倒计时
        startCountdown();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // 停止倒计时
        stopCountdown();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        // 清理资源
        stopCountdown();
        countdownHandler = null;
        countdownRunnable = null;
    }
    
    /**
     * 供外部调用的方法，用于更新目标日期按钮文本
     */
    public void updateTargetDateButtonText() {
        if (getView() != null) {
            updateTargetDateButtonText(getView());
        }
    }
    
    /**
     * 供外部调用的方法，用于更新宽松模式次数显示
     */
    public void updateCasualCountDisplay() {
        if (getView() != null) {
            updateCasualCountDisplay(getView());
        }
    }
    
    /**
     * 供外部调用的方法，用于更新APP宽松模式次数显示
     */
    public void updateAppCasualCountDisplay() {
        if (getView() != null) {
            updateAppCasualCountDisplay(getView());
        }
    }
    
    /**
     * 初始化倒计时相关组件
     */
    private void initCountdown(View view) {
        tvXhsCountdown = view.findViewById(R.id.tv_xhs_countdown);
        tvAlipayCountdown = view.findViewById(R.id.tv_alipay_countdown);
        
        // 初始化宽松模式剩余次数显示
        tvXhsCasualCount = view.findViewById(R.id.tv_xhs_casual_count);
        tvAlipayCasualCount = view.findViewById(R.id.tv_alipay_casual_count);
        
        countdownHandler = new Handler(Looper.getMainLooper());
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                updateCountdown();
                // 每秒更新一次
                countdownHandler.postDelayed(this, 1000);
            }
        };
    }
    
    /**
     * 启动倒计时
     */
    private void startCountdown() {
        if (countdownHandler != null && countdownRunnable != null) {
            countdownHandler.post(countdownRunnable);
        }
    }
    
    /**
     * 停止倒计时
     */
    private void stopCountdown() {
        if (countdownHandler != null && countdownRunnable != null) {
            countdownHandler.removeCallbacks(countdownRunnable);
        }
    }
    
    /**
     * 更新倒计时显示
     */
    private void updateCountdown() {
        if (tvXhsCountdown != null && tvAlipayCountdown != null && settingsManager != null) {
            // 更新小红书倒计时
            long xhsRemaining = settingsManager.getAppRemainingTime(Const.SupportedApp.XHS);
            String xhsText = SettingsManager.formatRemainingTime(xhsRemaining);
            tvXhsCountdown.setText(xhsText);
            
            // 更新支付宝倒计时
            long alipayRemaining = settingsManager.getAppRemainingTime(Const.SupportedApp.ALIPAY);
            String alipayText = SettingsManager.formatRemainingTime(alipayRemaining);
            tvAlipayCountdown.setText(alipayText);
            
            // 设置颜色：绿色表示可用，红色表示不可用
            tvXhsCountdown.setTextColor(xhsRemaining == -1 ? 0xFF4CAF50 : 0xFFE91E63);
            tvAlipayCountdown.setTextColor(alipayRemaining == -1 ? 0xFF4CAF50 : 0xFFE91E63);
        }
    }
} 