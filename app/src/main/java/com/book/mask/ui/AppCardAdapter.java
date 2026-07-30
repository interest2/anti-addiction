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

public class AppCardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_APP = 0;
    private static final int VIEW_TYPE_ADD = 1;

    private List<CustomApp> apps; // 包含预定义APP和自定义APP
    private RelaxManager relaxManager;
    private OnAppCardClickListener listener;
    private OnMonitorToggleListener monitorListener;
    private OnEditClickListener editListener;
    private OnDeleteClickListener deleteListener;
    private OnAddClickListener addListener;

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

    public interface OnAddClickListener {
        void onAddClick();
    }

    public AppCardAdapter(List<CustomApp> apps, RelaxManager relaxManager,
                         OnAppCardClickListener listener, OnMonitorToggleListener monitorListener,
                         OnEditClickListener editListener, OnDeleteClickListener deleteListener,
                         OnAddClickListener addListener) {
        this.apps = apps;
        this.relaxManager = relaxManager;
        this.listener = listener;
        this.monitorListener = monitorListener;
        this.editListener = editListener;
        this.deleteListener = deleteListener;
        this.addListener = addListener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = viewType == VIEW_TYPE_ADD ? R.layout.item_add_app_card : R.layout.item_app_card;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return viewType == VIEW_TYPE_ADD ? new AddAppViewHolder(view) : new AppCardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AppCardViewHolder) {
            ((AppCardViewHolder) holder).bind(apps.get(position));
        } else if (holder instanceof AddAppViewHolder) {
            ((AddAppViewHolder) holder).bind(apps.size() % 2 == 0);
        }
    }

    @Override
    public int getItemCount() {
        return apps.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        return position == apps.size() ? VIEW_TYPE_ADD : VIEW_TYPE_APP;
    }

    public boolean isAddCardPosition(int position) {
        return position == apps.size();
    }

    public void updateData(List<CustomApp> newApps) {
        this.apps = newApps;
        notifyDataSetChanged();
    }

    class AddAppViewHolder extends RecyclerView.ViewHolder {
        private final View addCard;

        AddAppViewHolder(@NonNull View itemView) {
            super(itemView);
            addCard = itemView.findViewById(R.id.card_add_app);
            View.OnClickListener addClickListener = v -> {
                if (addListener != null) {
                    addListener.onAddClick();
                }
            };
            itemView.setOnClickListener(addClickListener);
            addCard.setOnClickListener(addClickListener);
        }

        void bind(boolean centerInRow) {
            android.widget.FrameLayout.LayoutParams layoutParams =
                    (android.widget.FrameLayout.LayoutParams) addCard.getLayoutParams();
            layoutParams.gravity = centerInRow
                    ? android.view.Gravity.CENTER_HORIZONTAL | android.view.Gravity.TOP
                    : android.view.Gravity.START | android.view.Gravity.TOP;
            addCard.setLayoutParams(layoutParams);
        }
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

            long remainingTime = relaxManager.getAppRemainingTime(app);
            int relaxedCount = relaxManager.getAppRelaxedCloseCount(app);
            int remainingCount = Math.max(0, relaxedLimitCount - relaxedCount);

            if (remainingTime > 0) {
                tvRemainingTime.setVisibility(View.VISIBLE);
                tvRemainingTime.setText("倒计时: " + DateUtils.formatRemainingTime(remainingTime));
                tvRemainingTime.setTextColor(0xFFE91E63);
                tvRelaxedCount.setVisibility(View.GONE);
            } else {
                tvRemainingTime.setVisibility(View.GONE);
                tvRelaxedCount.setVisibility(View.VISIBLE);
                tvRelaxedCount.setText("宽松剩余: " + remainingCount + "次");
            }
        }
    }

}
