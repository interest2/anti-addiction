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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.book.baisc.R;
import com.book.baisc.config.SettingsManager;
import com.book.baisc.config.Const;

import java.util.Arrays;
import java.util.List;

public class HomeFragment extends Fragment implements AppCardAdapter.OnAppCardClickListener {

    private SettingsManager settingsManager;
    private SettingsDialogManager settingsDialogManager;
    
    // APP卡片相关
    private RecyclerView rvAppCards;
    private AppCardAdapter appCardAdapter;
    private List<Const.SupportedApp> supportedApps;
    
    // 倒计时相关
    private Handler countdownHandler;
    private Runnable countdownRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        // 初始化设置管理器
        settingsManager = new SettingsManager(requireContext());
        settingsDialogManager = new SettingsDialogManager(requireContext(), settingsManager);
        
        // 初始化支持的APP列表
        supportedApps = Arrays.asList(Const.SupportedApp.values());
        
        // 初始化APP卡片RecyclerView
        initAppCards(view);
        

        
        // 启动倒计时更新
        startCountdown();
        
        return view;
    }

    private void initAppCards(View view) {
        rvAppCards = view.findViewById(R.id.rv_app_cards);
        android.util.Log.d("HomeFragment", "RecyclerView找到: " + (rvAppCards != null));
        
        if (rvAppCards != null) {
            // 使用GridLayoutManager实现两列布局
            androidx.recyclerview.widget.GridLayoutManager layoutManager = 
                new androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2);
            rvAppCards.setLayoutManager(layoutManager);
            appCardAdapter = new AppCardAdapter(supportedApps, settingsManager, this);
            rvAppCards.setAdapter(appCardAdapter);
        }
    }

    @Override
    public void onAppCardClick(Const.SupportedApp app) {
        // 显示APP设置弹窗
        showAppSettingsDialog(app);
    }

    private void showAppSettingsDialog(Const.SupportedApp app) {
        // 显示"单次解禁时长"弹窗
        showTimeSettingDialogForApp(app);
    }

    private void showTimeSettingDialogForApp(Const.SupportedApp app) {
        // 创建自定义布局的弹窗
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_time_setting, null);
        
        Button strictModeButton = dialogView.findViewById(R.id.btn_strict_mode);
        Button casualModeButton = dialogView.findViewById(R.id.btn_casual_mode);
        
        // 检查宽松模式剩余次数
        int casualCount = settingsManager.getAppCasualCloseCount(app);
        int remainingCount = Math.max(0, app.getCasualLimitCount() - casualCount);
        
        // 如果宽松模式次数用完，置灰按钮
        if (remainingCount <= 0) {
            casualModeButton.setEnabled(false);
            casualModeButton.setAlpha(0.5f);
            casualModeButton.setText("宽松模式 (次数已用完)");
        }
        
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
            .setTitle(app.getAppName() + " - 单次解禁时长")
            .setView(dialogView)
            .setNegativeButton("取消", null)
            .create();
        
        // 设置按钮点击事件
        strictModeButton.setOnClickListener(v -> {
            dialog.dismiss();
            settingsDialogManager.showTimeSettingDialogForApp(app, true);
        });
        
        casualModeButton.setOnClickListener(v -> {
            // 只有在按钮可用时才执行
            if (casualModeButton.isEnabled()) {
                dialog.dismiss();
                settingsDialogManager.showTimeSettingDialogForApp(app, false);
            }
        });
        
        dialog.show();
    }



    @Override
    public void onResume() {
        super.onResume();
        // 更新APP卡片数据
        if (appCardAdapter != null) {
            appCardAdapter.updateData();
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
     * 供外部调用的方法，用于更新APP卡片显示
     */
    public void updateAppCardsDisplay() {
        if (appCardAdapter != null) {
            appCardAdapter.updateData();
        }
    }
    
    /**
     * 启动倒计时
     */
    private void startCountdown() {
        if (countdownHandler == null) {
            countdownHandler = new Handler(Looper.getMainLooper());
        }
        
        if (countdownRunnable == null) {
            countdownRunnable = new Runnable() {
                @Override
                public void run() {
                    // 更新APP卡片数据
                    if (appCardAdapter != null) {
                        appCardAdapter.updateData();
                    }
                    // 每秒更新一次
                    countdownHandler.postDelayed(this, 1000);
                }
            };
        }
        
        countdownHandler.post(countdownRunnable);
    }
    
    /**
     * 停止倒计时
     */
    private void stopCountdown() {
        if (countdownHandler != null && countdownRunnable != null) {
            countdownHandler.removeCallbacks(countdownRunnable);
        }
    }
    

} 