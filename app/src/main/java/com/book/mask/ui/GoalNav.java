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
import com.book.mask.constant.Const;
import com.book.mask.personalize.RelaxManager;
import com.book.mask.personalize.AppSettingsManager;
import com.book.mask.floating.FloatService;
import com.book.mask.reminder.config.ReminderProviderConfig;
import com.book.mask.reminder.config.ReminderProviderConfigStore;
import com.book.mask.util.DateUtils;

public class GoalNav extends Fragment {

    private RelaxManager relaxManager;
    private AppSettingsManager appSettingsManager;
    private SettingsDialogManager settingsDialogManager;
    private TextView tvGoalCountdown;
    private TextView tvReminderStyleValue;
    private TextView tvReminderProviderValue;
    private TextView tvFloatingStrictReminderValue;
    private TextView tvChallengeTypeValue;
    private Button btnTagSetting, btnTargetDateSetting;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_goal, container, false);
        relaxManager = new RelaxManager(requireContext());
        appSettingsManager = new AppSettingsManager(requireContext());
        settingsDialogManager = new SettingsDialogManager(requireContext(), relaxManager);

        // 初始化控件
        tvGoalCountdown = view.findViewById(R.id.tv_goal_countdown);
        tvReminderStyleValue = view.findViewById(R.id.tv_reminder_style_value);
        tvReminderProviderValue = view.findViewById(R.id.tv_reminder_provider_value);
        tvFloatingStrictReminderValue = view.findViewById(R.id.tv_floating_strict_reminder_value);
        tvChallengeTypeValue = view.findViewById(R.id.tv_challenge_type_value);
        btnTagSetting = view.findViewById(R.id.btn_tag_setting);
        btnTargetDateSetting = view.findViewById(R.id.btn_target_date_setting);

        // 设置按钮点击事件
        btnTagSetting.setOnClickListener(v ->
                settingsDialogManager.showTagSettingDialog(this::onMotivationTagChanged));
        btnTargetDateSetting.setOnClickListener(v -> {
            settingsDialogManager.showTargetDateSettingDialog(this::updateGoalInfo);
        });

        View reminderProviderRow = view.findViewById(R.id.btn_reminder_provider);
        View reminderProviderDivider = view.findViewById(R.id.divider_reminder_provider);
        int reminderProviderVisibility = Const.REMINDER_PROVIDER_SETTINGS_ENABLED
                ? View.VISIBLE
                : View.GONE;
        reminderProviderRow.setVisibility(reminderProviderVisibility);
        reminderProviderDivider.setVisibility(reminderProviderVisibility);
        if (Const.REMINDER_PROVIDER_SETTINGS_ENABLED) {
            reminderProviderRow.setOnClickListener(v -> openReminderProviderSettings());
        }

        // 设置答题类型入口：打开「题目类型」子页面
        View challengeTypeRow = view.findViewById(R.id.btn_challenge_type);
        challengeTypeRow.setOnClickListener(v -> openChallengeTypeSettings());

        View reminderStyleRow = view.findViewById(R.id.btn_reminder_style);
        reminderStyleRow.setOnClickListener(v ->
                settingsDialogManager.showReminderStyleDialog(this::onReminderStyleChanged));

        // 设置悬浮窗额外显示日常提醒按钮
        View floatingStrictReminderRow = view.findViewById(
                R.id.btn_floating_strict_reminder);
        floatingStrictReminderRow.setOnClickListener(v ->
                settingsDialogManager.showFloatingStrictReminderDialog(this::updateReminderValues));

        View leisureTimeRow = view.findViewById(R.id.btn_leisure_time);
        leisureTimeRow.setOnClickListener(v -> {
            settingsDialogManager.showLeisureTimeDialog();
        });

        // 复述题答题记录（可用 Const.ANSWER_RECORDS_ENABLED 控制入口显隐，便于后续恢复）
        View answerRecordsRow = view.findViewById(R.id.btn_answer_records);
        View answerRecordsDivider = view.findViewById(R.id.divider_answer_records);
        int answerRecordsVisibility = Const.ANSWER_RECORDS_ENABLED
                ? View.VISIBLE
                : View.GONE;
        answerRecordsRow.setVisibility(answerRecordsVisibility);
        answerRecordsDivider.setVisibility(answerRecordsVisibility);
        if (Const.ANSWER_RECORDS_ENABLED) {
            answerRecordsRow.setOnClickListener(v -> {
                settingsDialogManager.showAnswerRecordsDialog();
            });
        }

        return view;
    }

    private void openReminderProviderSettings() {
        getParentFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        0,
                        0
                )
                .replace(R.id.fragment_container, new ReminderProviderSettingsNav())
                .addToBackStack(ReminderProviderSettingsNav.class.getSimpleName())
                .commit();
    }

    private void openChallengeTypeSettings() {
        getParentFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left,
                        0,
                        0
                )
                .replace(R.id.fragment_container, new ChallengeTypeSettingsNav())
                .addToBackStack(ChallengeTypeSettingsNav.class.getSimpleName())
                .commit();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateGoalInfo();
    }

    private void onMotivationTagChanged() {
        updateGoalInfo();
        FloatService.notifyReminderContentChanged();
    }

    private void onReminderStyleChanged() {
        updateReminderValues();
        FloatService.notifyReminderContentChanged();
    }

    private void updateReminderValues() {
        String style = appSettingsManager.getReminderStyle();
        tvReminderStyleValue.setText("自定义".equals(style)
                ? appSettingsManager.getReminderCustomStyle()
                : style);

        ReminderProviderConfig provider = new ReminderProviderConfigStore().getActiveConfig();
        tvReminderProviderValue.setText(provider.isOfficial()
                ? "默认"
                : provider.getModel());

        String strictReminder = appSettingsManager.getFloatingStrictReminder();
        tvFloatingStrictReminderValue.setText(strictReminder.isEmpty()
                ? Const.DEFAULT_STRICT_REMINDER
                : strictReminder);
    }

    private void updateGoalInfo() {
        // 目标标签
        String tag = appSettingsManager.getMotivationTag();
        btnTagSetting.setText(tag == null || tag.isEmpty() ? "目标标签设置" : tag);
        // 目标日期
        String date = appSettingsManager.getTargetCompletionDate();
        btnTargetDateSetting.setText((date == null || date.isEmpty() || "待设置".equals(date)) ? "目标日期" : date);
        // 倒计时
        tvGoalCountdown.setText(DateUtils.countdownDate(date));
        // 答题类型
        tvChallengeTypeValue.setText(settingsDialogManager.getChallengeType().getDisplayName());
        updateReminderValues();
    }
}
