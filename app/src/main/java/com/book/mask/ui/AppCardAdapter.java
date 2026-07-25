package com.book.mask.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.book.mask.R;
import com.book.mask.personalize.RelaxManager;
import com.book.mask.config.CustomApp;
import com.book.mask.config.CustomAppManager;
import com.book.mask.config.Share;
import com.book.mask.util.DateUtils;

import java.util.List;

public class AppCardAdapter extends RecyclerView.Adapter<AppCardAdapter.AppCardViewHolder> {

    private List<CustomApp> apps; // 包含预定义APP和自定义APP
    private RelaxManager relaxManager;
    private OnAppCardClickListener listener;
    private OnMonitorToggleListener monitorListener;
    private OnEditClickListener editListener;
    private OnDeleteClickListener deleteListener;

        public interface OnAppCardClickListener {
        void onAppCardClick(CustomApp app);
    }

    public interface OnMonitorToggleListener {
        void onMonitorToggle(CustomApp app, boolean isEnabled);
    }

    public interface OnEditClickListener {
        void onEditClick(CustomApp app);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(CustomApp app);
    }

    public AppCardAdapter(List<CustomApp> apps, RelaxManager relaxManager,
                         OnAppCardClickListener listener, OnMonitorToggleListener monitorListener,
                         OnEditClickListener editListener, OnDeleteClickListener deleteListener) {
        this.apps = apps;
        this.relaxManager = relaxManager;
        this.listener = listener;
        this.monitorListener = monitorListener;
        this.editListener = editListener;
        this.deleteListener = deleteListener;
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
        CustomApp app = apps.get(position);
        holder.bind(app);
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    public void updateData(List<CustomApp> newApps) {
        this.apps = newApps;
        notifyDataSetChanged();
    }

    /**
     * 计算 APP 名称的起始外边距：默认 20dp；当名称过长、按默认外边距会与右侧开关重叠时，
     * 自动缩小外边距让名称整体左移（下限 0dp），仍放不下才由 ellipsize 兜底截断。
     */
    private void adjustAppNameStartMargin(TextView nameView, View deleteBtn, View toggle) {
        nameView.post(() -> {
            View row = (View) nameView.getParent();
            if (row == null || row.getWidth() <= 0) {
                return;
            }
            float density = nameView.getResources().getDisplayMetrics().density;
            int defaultMargin = (int) (20 * density);
            int deleteWidth = (deleteBtn != null && deleteBtn.getVisibility() == View.VISIBLE)
                    ? (int) (20 * density) : 0;
            int toggleWidth = (int) (36 * density);
            int available = row.getWidth() - row.getPaddingStart() - row.getPaddingEnd()
                    - deleteWidth - toggleWidth;
            float textWidth = nameView.getPaint().measureText(nameView.getText().toString());

            int desiredMargin;
            if (textWidth + defaultMargin > available) {
                desiredMargin = Math.max(0, (int) (available - textWidth));
            } else {
                desiredMargin = defaultMargin;
            }

            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) nameView.getLayoutParams();
            if (lp.getMarginStart() != desiredMargin) {
                lp.setMarginStart(desiredMargin);
                nameView.setLayoutParams(lp);
            }
        });
    }

    class AppCardViewHolder extends RecyclerView.ViewHolder {
        private TextView tvAppName;
        private TextView tvRemainingTime;
        private TextView tvRelaxedCount;
        private ToggleButton toggleMonitor;
        private TextView btnDeleteApp;

        public AppCardViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAppName = itemView.findViewById(R.id.tv_app_name);
            tvRemainingTime = itemView.findViewById(R.id.tv_remaining_time);
            tvRelaxedCount = itemView.findViewById(R.id.tv_relaxed_count);
            toggleMonitor = itemView.findViewById(R.id.toggle_monitor);
            btnDeleteApp = itemView.findViewById(R.id.btn_delete_app);

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
                    CustomApp app = apps.get(position);
                    boolean isEnabled = toggleMonitor.isChecked();
                    monitorListener.onMonitorToggle(app, isEnabled);
                }
            });

            // 删除按钮点击事件
            if (btnDeleteApp != null) {
                btnDeleteApp.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && deleteListener != null) {
                        deleteListener.onDeleteClick(apps.get(position));
                    }
                });
            }

        }

        public void bind(CustomApp app) {
            if (app == null || relaxManager == null) {
                return;
            }
            
            String appName;
            int relaxedLimitCount;
            String packageName;
            
            // 获取APP信息
            appName = app.getAppName();
            relaxedLimitCount = app.getRelaxedLimitCount();
            packageName = app.getPackageName();
            
            // 设置APP名称
            if (tvAppName != null) {
                tvAppName.setText(appName);
            }

            // 删除按钮仅对手动添加的自定义APP显示
            if (btnDeleteApp != null) {
                boolean isCustom = CustomAppManager.getInstance().isCustomApp(packageName);
                btnDeleteApp.setVisibility(isCustom ? View.VISIBLE : View.GONE);
            }

            // 默认 marginStart 20dp；APP名较长会与右侧开关重叠时，自动左移（缩小起始外边距）
            if (tvAppName != null) {
                adjustAppNameStartMargin(tvAppName, btnDeleteApp, toggleMonitor);
            }

            // 设置监测开关状态
            if (toggleMonitor != null) {
                // 部分默认开启，其他默认关闭
                Boolean isEnabled = relaxManager.isAppMonitoringEnabled(packageName);
                if (isEnabled == null) {
                    // 如果还没有设置过，使用默认值
                    isEnabled = Share.judgeEnabled(packageName);
                }
                toggleMonitor.setChecked(isEnabled);
            }

            // 设置剩余时长
            long remainingTime = relaxManager.getAppRemainingTime(app);
            String timeText;
            int timeColor;
            if (remainingTime <= 0) {
                timeText = "倒计时：00:00";
                timeColor = 0xFF4CAF50; // 绿色
            } else {
                timeText = "倒计时: " + DateUtils.formatRemainingTime(remainingTime);
                timeColor = 0xFFE91E63; // 红色
            }
            
            if (tvRemainingTime != null) {
                tvRemainingTime.setText(timeText);
                tvRemainingTime.setTextColor(timeColor);
            }

            // 设置宽松模式剩余次数
            int relaxedCount = relaxManager.getAppRelaxedCloseCount(app);
            int remainingCount = Math.max(0, relaxedLimitCount - relaxedCount);
            
            if (tvRelaxedCount != null) {
                tvRelaxedCount.setText("宽松剩余: " + remainingCount + "次");
            }
        }
    }

}
