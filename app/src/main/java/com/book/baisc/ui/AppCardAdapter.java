package com.book.baisc.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.book.baisc.R;
import com.book.baisc.config.Const;
import com.book.baisc.config.SettingsManager;
import com.book.baisc.config.Share;

import java.util.List;

public class AppCardAdapter extends RecyclerView.Adapter<AppCardAdapter.AppCardViewHolder> {

    private List<Object> apps; // 包含预定义APP和自定义APP
    private SettingsManager settingsManager;
    private OnAppCardClickListener listener;
    private OnMonitorToggleListener monitorListener;
    private OnEditClickListener editListener;

    public interface OnAppCardClickListener {
        void onAppCardClick(Object app);
    }

    public interface OnMonitorToggleListener {
        void onMonitorToggle(Object app, boolean isEnabled);
    }

    public interface OnEditClickListener {
        void onEditClick(Object app);
    }

    public AppCardAdapter(List<Object> apps, SettingsManager settingsManager, 
                         OnAppCardClickListener listener, OnMonitorToggleListener monitorListener,
                         OnEditClickListener editListener) {
        this.apps = apps;
        this.settingsManager = settingsManager;
        this.listener = listener;
        this.monitorListener = monitorListener;
        this.editListener = editListener;
    }

    @NonNull
    @Override
    public AppCardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_card, parent, false);
        return new AppCardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppCardViewHolder holder, int position) {
        Object app = apps.get(position);
        holder.bind(app);
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    public void updateData(List<Object> newApps) {
        this.apps = newApps;
        notifyDataSetChanged();
    }

    class AppCardViewHolder extends RecyclerView.ViewHolder {
        private TextView tvAppName;
        private TextView tvRemainingTime;
        private TextView tvCasualCount;
        private ToggleButton toggleMonitor;

        public AppCardViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAppName = itemView.findViewById(R.id.tv_app_name);
            tvRemainingTime = itemView.findViewById(R.id.tv_remaining_time);
            tvCasualCount = itemView.findViewById(R.id.tv_casual_count);
            toggleMonitor = itemView.findViewById(R.id.toggle_monitor);

            // 卡片点击事件
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onAppCardClick(apps.get(position));
                }
            });

            // 监测开关点击事件
            toggleMonitor.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && monitorListener != null) {
                    Object app = apps.get(position);
                    boolean isEnabled = toggleMonitor.isChecked();
                    monitorListener.onMonitorToggle(app, isEnabled);
                }
            });

        }

        public void bind(Object app) {
            if (app == null || settingsManager == null) {
                return;
            }
            
            String appName;
            int casualLimitCount;
            String packageName;
            
            // 根据APP类型获取信息
            if (app instanceof Const.SupportedApp) {
                Const.SupportedApp supportedApp = (Const.SupportedApp) app;
                appName = supportedApp.getAppName();
                // 优先使用自定义设置，如果没有则使用默认值
                Integer customLimit = settingsManager.getCustomCasualLimitCount(supportedApp.getPackageName());
                casualLimitCount = customLimit != null ? customLimit : supportedApp.getCasualLimitCount();
                packageName = supportedApp.getPackageName();
            } else if (app instanceof Const.CustomApp) {
                Const.CustomApp customApp = (Const.CustomApp) app;
                appName = customApp.getAppName();
                casualLimitCount = customApp.getCasualLimitCount();
                packageName = customApp.getPackageName();
            } else {
                return;
            }
            
            // 设置APP名称
            if (tvAppName != null) {
                tvAppName.setText(appName);
            }

            // 设置监测开关状态
            if (toggleMonitor != null) {
                // 小红书默认开启，其他默认关闭
                Boolean isEnabled = settingsManager.isAppMonitoringEnabled(packageName);
                if (isEnabled == null) {
                    // 如果还没有设置过，使用默认值
                    isEnabled = Share.judgeEnabled(packageName);
                }
                toggleMonitor.setChecked(isEnabled);
            }

            // 设置剩余时长
            long remainingTime = settingsManager.getAppRemainingTime(app);
            String timeText;
            int timeColor;
            if (remainingTime == -1) {
                timeText = "倒计时：00:00";
                timeColor = 0xFF4CAF50; // 绿色
            } else {
                timeText = "倒计时: " + SettingsManager.formatRemainingTime(remainingTime);
                timeColor = 0xFFE91E63; // 红色
            }
            
            if (tvRemainingTime != null) {
                tvRemainingTime.setText(timeText);
                tvRemainingTime.setTextColor(timeColor);
            }

            // 设置宽松模式剩余次数
            int casualCount = settingsManager.getAppCasualCloseCount(app);
            int remainingCount = Math.max(0, casualLimitCount - casualCount);
            
            if (tvCasualCount != null) {
                tvCasualCount.setText("宽松模式剩余: " + remainingCount + "次");
            }
        }
    }

} 