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
import com.book.mask.personalize.RelaxManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 「题目类型」设置子页面。
 * 展示答题类型单选列表（目前仅算术题），点击后进入该题型的难度设置。
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

        buildChallengeTypeRows();
        return view;
    }

    private void buildChallengeTypeRows() {
        ChallengeType currentType = settingsDialogManager.getChallengeType();

        typeList.removeAllViews();
        rows.clear();
        rowTypes.clear();

        for (final ChallengeType type : ChallengeType.values()) {
            View row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.dialog_challenge_type_item, typeList, false);
            TextView title = row.findViewById(R.id.tv_title);
            ImageView radio = row.findViewById(R.id.iv_radio);

            title.setText(type.getDisplayName());
            radio.setImageResource(type == currentType
                    ? R.drawable.ic_radio_checked : R.drawable.ic_radio_unchecked);

            if (type == ChallengeType.ARITHMETIC) {
                updateArithmeticHint(row);
            }

            row.setOnClickListener(v -> onTypeSelected(type));
            typeList.addView(row);
            rows.add(row);
            rowTypes.add(type);
        }
    }

    private void onTypeSelected(ChallengeType type) {
        settingsDialogManager.applyChallengeTypeSelection(type, this::refreshCheckedRows);
    }

    /** 按下标同步选中态，并刷新算术题的生效难度提示。 */
    private void refreshCheckedRows() {
        ChallengeType currentType = settingsDialogManager.getChallengeType();
        for (int i = 0; i < rows.size(); i++) {
            ImageView radio = rows.get(i).findViewById(R.id.iv_radio);
            radio.setImageResource(rowTypes.get(i) == currentType
                    ? R.drawable.ic_radio_checked : R.drawable.ic_radio_unchecked);
            if (rowTypes.get(i) == ChallengeType.ARITHMETIC) {
                updateArithmeticHint(rows.get(i));
            }
        }
    }

    /** 算术题行右侧灰色小字：展示当前生效难度（默认难度 / 自定义难度）。 */
    private void updateArithmeticHint(View row) {
        TextView hint = row.findViewById(R.id.tv_right_hint);
        hint.setText(settingsDialogManager.getMathDifficultyModeLabel());
        hint.setVisibility(View.VISIBLE);
    }
}
