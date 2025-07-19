package com.book.baisc.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.book.baisc.R;
import com.book.baisc.config.Const;
import com.book.baisc.config.SettingsManager;

import java.util.List;

public class AppCardAdapter extends RecyclerView.Adapter<AppCardAdapter.AppCardViewHolder> {

    private List<Const.SupportedApp> apps;
    private SettingsManager settingsManager;
    private OnAppCardClickListener listener;

    public interface OnAppCardClickListener {
        void onAppCardClick(Const.SupportedApp app);
    }

    public AppCardAdapter(List<Const.SupportedApp> apps, SettingsManager settingsManager, OnAppCardClickListener listener) {
        this.apps = apps;
        this.settingsManager = settingsManager;
        this.listener = listener;
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
        Const.SupportedApp app = apps.get(position);
        holder.bind(app);
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    public void updateData() {
        notifyDataSetChanged();
    }

    class AppCardViewHolder extends RecyclerView.ViewHolder {
        private TextView tvAppName;
        private TextView tvRemainingTime;
        private TextView tvCasualCount;

        public AppCardViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAppName = itemView.findViewById(R.id.tv_app_name);
            tvRemainingTime = itemView.findViewById(R.id.tv_remaining_time);
            tvCasualCount = itemView.findViewById(R.id.tv_casual_count);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onAppCardClick(apps.get(position));
                }
            });
        }

        public void bind(Const.SupportedApp app) {
            if (app == null || settingsManager == null) {
                return;
            }
            
            // 设置APP名称
            if (tvAppName != null) {
                tvAppName.setText(app.getAppName());
            }

            // 设置剩余时长
            long remainingTime = settingsManager.getAppRemainingTime(app);
            String timeText;
            int timeColor;
            if (remainingTime == -1) {
                timeText = "剩余时长: 可用";
                timeColor = 0xFF4CAF50; // 绿色
            } else {
                timeText = "剩余时长: " + SettingsManager.formatRemainingTime(remainingTime);
                timeColor = 0xFFE91E63; // 红色
            }
            
            if (tvRemainingTime != null) {
                tvRemainingTime.setText(timeText);
                tvRemainingTime.setTextColor(timeColor);
            }

            // 设置宽松模式剩余次数
            int casualCount = settingsManager.getAppCasualCloseCount(app);
            int remainingCount = Math.max(0, Const.CASUAL_LIMIT_COUNT - casualCount);
            
            if (tvCasualCount != null) {
                tvCasualCount.setText("宽松剩余: " + remainingCount + "次");
            }
        }
    }
} 