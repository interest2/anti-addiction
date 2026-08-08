package com.book.mask.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.book.mask.R;
import com.book.mask.config.ChallengeType;
import com.book.mask.constant.QuestionConst;
import com.book.mask.personalize.RelaxManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 「题目类型」设置子页面。
 * 直接展示答题类型单选列表与答题计时选项，并收纳听力题（豆包语音）、
 * 复述题（腾讯口语评测）两个相关服务的配置入口。
 */
public class ChallengeTypeSettingsNav extends Fragment {

    private SettingsDialogManager settingsDialogManager;
    private LinearLayout typeList;
    private final List<View> rows = new ArrayList<>();
    private final List<ChallengeType> rowTypes = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_challenge_type_settings, container, false);
        settingsDialogManager = new SettingsDialogManager(
                requireContext(), new RelaxManager(requireContext()));

        typeList = view.findViewById(R.id.challenge_type_list);

        view.findViewById(R.id.btn_challenge_type_settings_back)
                .setOnClickListener(v -> getParentFragmentManager().popBackStack());
        view.findViewById(R.id.btn_doubao_tts_settings)
                .setOnClickListener(v -> settingsDialogManager.showDoubaoTtsSettingsDialog());
        view.findViewById(R.id.btn_soe_settings)
                .setOnClickListener(v -> settingsDialogManager.showSoeSettingsDialog());

        settingsDialogManager.bindChallengeTimerOptions(view);
        buildChallengeTypeRows();
        return view;
    }

    private void buildChallengeTypeRows() {
        ChallengeType[] challengeTypes =
                ChallengeType.settingsOptions(QuestionConst.ENGLISH_READING_ENABLED);
        ChallengeType currentType = settingsDialogManager.getChallengeType();

        typeList.removeAllViews();
        rows.clear();
        rowTypes.clear();

        for (final ChallengeType type : challengeTypes) {
            View row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.dialog_challenge_type_item, typeList, false);
            TextView title = row.findViewById(R.id.tv_title);
            TextView subtitle = row.findViewById(R.id.tv_subtitle);
            ImageView radio = row.findViewById(R.id.iv_radio);
            ImageView edit = row.findViewById(R.id.iv_edit);

            title.setText(type.getDisplayName());
            radio.setImageResource(type == currentType
                    ? R.drawable.ic_radio_checked : R.drawable.ic_radio_unchecked);

            if (type == ChallengeType.RETELLING) {
                subtitle.setVisibility(View.VISIBLE);
                subtitle.setText(settingsDialogManager.formatRetellingSummary());
                edit.setVisibility(View.VISIBLE);
                edit.setOnClickListener(v ->
                        settingsDialogManager.showRetellingSettingsDialog(() -> {
                            subtitle.setText(settingsDialogManager.formatRetellingSummary());
                            refreshCheckedRows();
                        }));
            }

            row.setOnClickListener(v -> onTypeSelected(type));
            typeList.addView(row);
            rows.add(row);
            rowTypes.add(type);
        }
    }

    private void onTypeSelected(ChallengeType type) {
        String preflightError =
                settingsDialogManager.applyChallengeTypeSelection(type, this::refreshCheckedRows);
        if (preflightError != null) {
            UiFeedback.showError(requireContext(), preflightError);
        }
    }

    /** 按下标同步选中态：当前题型所在行选中，其余取消。 */
    private void refreshCheckedRows() {
        ChallengeType currentType = settingsDialogManager.getChallengeType();
        for (int i = 0; i < rows.size(); i++) {
            ImageView radio = rows.get(i).findViewById(R.id.iv_radio);
            radio.setImageResource(rowTypes.get(i) == currentType
                    ? R.drawable.ic_radio_checked : R.drawable.ic_radio_unchecked);
        }
    }
}
