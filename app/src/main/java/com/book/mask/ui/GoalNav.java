package com.book.mask.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.book.mask.R;
import com.book.mask.setting.RelaxManager;
import com.book.mask.setting.AppSettingsManager;
import com.book.mask.floating.FloatHelper;

public class GoalNav extends Fragment {

    private RelaxManager relaxManager;
    private AppSettingsManager appSettingsManager;
    private SettingsDialogManager settingsDialogManager;
    private TextView tvGoalCountdown;
    private Button btnTagSetting, btnTargetDateSetting;
    
    // 连续点击计数器
    private int clickCount = 0;
    private long lastClickTime = 0;
    private static final int REQUIRED_CLICKS = 20;
    private static final long CLICK_TIMEOUT = 3000; // 3秒内有效

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_goal, container, false);
        relaxManager = new RelaxManager(requireContext());
        appSettingsManager = new AppSettingsManager(requireContext());
        settingsDialogManager = new SettingsDialogManager(requireContext(), relaxManager);

        // 初始化控件
        tvGoalCountdown = view.findViewById(R.id.tv_goal_countdown);
        btnTagSetting = view.findViewById(R.id.btn_tag_setting);
        btnTargetDateSetting = view.findViewById(R.id.btn_target_date_setting);

        // 设置按钮点击事件
        btnTagSetting.setOnClickListener(v -> {
            settingsDialogManager.showTagSettingDialog(this::updateGoalInfo);
        });
        btnTargetDateSetting.setOnClickListener(v -> {
            settingsDialogManager.showTargetDateSettingDialog(this::updateGoalInfo);
        });

        // 设置算术题难度设置按钮
        Button btnMathDifficulty = view.findViewById(R.id.btn_math_difficulty);
        btnMathDifficulty.setOnClickListener(v -> {
            settingsDialogManager.showMathDifficultyDialog();
        });
        
        // 设置悬浮窗额外显示日常提醒按钮
        Button btnFloatingStrictReminder = view.findViewById(R.id.btn_floating_strict_reminder);
        btnFloatingStrictReminder.setOnClickListener(v -> {
            settingsDialogManager.showFloatingStrictReminderDialog();
        });
        
        // 在空白处（标题）添加连续点击20次的检测逻辑
        TextView tvTitle = view.findViewById(R.id.tv_goal_title);
        tvTitle.setOnClickListener(v -> handleSecretClick());
        
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateGoalInfo();
    }

    private void updateGoalInfo() {
        // 目标标签
        String tag = appSettingsManager.getMotivationTag();
        btnTagSetting.setText(tag == null || tag.isEmpty() ? "目标标签设置" : tag);
        // 目标日期
        String date = appSettingsManager.getTargetCompletionDate();
        btnTargetDateSetting.setText((date == null || date.isEmpty() || "待设置".equals(date)) ? "目标日期" : date);
        // 倒计时
        String countdown = FloatHelper.hintDate(date);
        tvGoalCountdown.setText(countdown.isEmpty() ? "距离目标：--天" : countdown);
    }
    
    /**
     * 处理隐藏的连续点击检测
     */
    private void handleSecretClick() {
        long currentTime = System.currentTimeMillis();
        
        // 如果距离上次点击超过3秒，重置计数器
        if (currentTime - lastClickTime > CLICK_TIMEOUT) {
            clickCount = 0;
        }
        
        clickCount++;
        lastClickTime = currentTime;
        
        // 达到20次点击，解锁英文阅读功能
        if (clickCount >= REQUIRED_CLICKS) {
            if (!appSettingsManager.isEnglishReadingUnlocked()) {
                appSettingsManager.setEnglishReadingUnlocked(true);
                android.widget.Toast.makeText(requireContext(), 
                    "🎉 恭喜！已解锁英文阅读答题功能", 
                    android.widget.Toast.LENGTH_LONG).show();
            } else {
                android.widget.Toast.makeText(requireContext(), 
                    "英文阅读功能已经解锁了哦", 
                    android.widget.Toast.LENGTH_SHORT).show();
            }
            clickCount = 0; // 重置计数器
        } else if (clickCount >= 15) {
            // 给用户一些提示
            android.widget.Toast.makeText(requireContext(), 
                "再点击 " + (REQUIRED_CLICKS - clickCount) + " 次...", 
                android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}