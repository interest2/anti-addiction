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
        
        // 设置按钮点击事件
        setupTagSettingButton(view);
        setupTargetDateSettingButton(view);
        
        // 更新UI状态
        updateTagButtonText(view);
        updateTargetDateButtonText(view);
        
        // 启动倒计时更新
        startCountdown();
        
        return view;
    }

    private void initAppCards(View view) {
        rvAppCards = view.findViewById(R.id.rv_app_cards);
        android.util.Log.d("HomeFragment", "RecyclerView找到: " + (rvAppCards != null));
        
        if (rvAppCards != null) {
            rvAppCards.setLayoutManager(new LinearLayoutManager(requireContext()));
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
        // 创建弹窗显示"单次解禁时长"和两个按钮
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle(app.getAppName() + " - 单次解禁时长")
            .setMessage("请选择解禁模式：")
            .setPositiveButton("严格模式", (dialog, which) -> {
                settingsDialogManager.showTimeSettingDialogForApp(app, true);
            })
            .setNegativeButton("宽松模式", (dialog, which) -> {
                settingsDialogManager.showTimeSettingDialogForApp(app, false);
            })
            .setNeutralButton("取消", null)
            .show();
    }

    private void setupTagSettingButton(View view) {
        Button tagButton = view.findViewById(R.id.btn_tag_setting);
        tagButton.setOnClickListener(v -> settingsDialogManager.showTagSettingDialog());
    }

    private void setupTargetDateSettingButton(View view) {
        Button targetDateButton = view.findViewById(R.id.btn_target_date_setting);
        targetDateButton.setOnClickListener(v -> settingsDialogManager.showTargetDateSettingDialog());
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
            updateTagButtonText(getView());
            updateTargetDateButtonText(getView());
        }
        
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
     * 供外部调用的方法，用于更新目标日期按钮文本
     */
    public void updateTargetDateButtonText() {
        if (getView() != null) {
            updateTargetDateButtonText(getView());
        }
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