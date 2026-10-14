package com.book.mask.personalize;

import android.content.Context;

import com.book.mask.config.ChallengeType;
import com.book.mask.constant.QuestionConst;
import com.book.mask.util.ArithmeticUtils.MultiplicationTier;
import com.tencent.mmkv.MMKV;

/**
 * 管理答题类型和算术题难度配置。
 */
public class ChallengeSettingsManager {

    static final String KEY_MATH_DIFFICULTY_MODE = "math_difficulty_mode";
    static final String KEY_MATH_ADDITION_DIGITS = "math_addition_digits";
    static final String KEY_MATH_SUBTRACTION_DIGITS = "math_subtraction_digits";
    static final String KEY_MATH_MULTIPLICATION_MULTIPLIER_DIGITS =
            "math_multiplication_multiplier_digits";
    static final String KEY_MATH_MULTIPLICATION_MULTIPLICAND_DIGITS =
            "math_multiplication_multiplicand_digits";
    static final String KEY_MATH_MULTIPLICATION_MULTIPLIER_TIER =
            "math_multiplication_multiplier_tier";
    static final String KEY_MATH_MULTIPLICATION_MULTIPLICAND_TIER =
            "math_multiplication_multiplicand_tier";
    static final String KEY_MATH_QUESTION_TYPE = "math_question_type";

    private final MMKV mmkv;

    public ChallengeSettingsManager(Context context) {
        mmkv = SettingsStorage.open();
    }

    /**
     * @param mode "default" 或 "custom"
     */
    public void setMathDifficultyMode(String mode) {
        android.util.Log.d("SettingsManager", "设置难度模式: " + mode);
        mmkv.putString(KEY_MATH_DIFFICULTY_MODE, mode).commit();
        android.util.Log.d("SettingsManager", "难度模式设置完成");
    }

    public String getMathDifficultyMode() {
        String mode = mmkv.getString(KEY_MATH_DIFFICULTY_MODE, "default");
        android.util.Log.d("SettingsManager", "获取难度模式: " + mode);
        return mode;
    }

    public void setMathAdditionDigits(int digits) {
        mmkv.putInt(KEY_MATH_ADDITION_DIGITS, digits).commit();
    }

    public int getMathAdditionDigits() {
        return mmkv.getInt(KEY_MATH_ADDITION_DIGITS, QuestionConst.ADD_LEN_DEFAULT);
    }

    public void setMathSubtractionDigits(int digits) {
        mmkv.putInt(KEY_MATH_SUBTRACTION_DIGITS, digits).commit();
    }

    public int getMathSubtractionDigits() {
        return mmkv.getInt(KEY_MATH_SUBTRACTION_DIGITS, QuestionConst.SUB_LEN_DEFAULT);
    }

    public void setMathMultiplicationMultiplierDigits(int digits) {
        mmkv.putInt(KEY_MATH_MULTIPLICATION_MULTIPLIER_DIGITS, digits).commit();
    }

    public int getMathMultiplicationMultiplierDigits() {
        return mmkv.getInt(
                KEY_MATH_MULTIPLICATION_MULTIPLIER_DIGITS,
                QuestionConst.MUL_FIRST_LEN_DEFAULT);
    }

    public void setMathMultiplicationMultiplicandDigits(int digits) {
        mmkv.putInt(KEY_MATH_MULTIPLICATION_MULTIPLICAND_DIGITS, digits).commit();
    }

    public int getMathMultiplicationMultiplicandDigits() {
        return mmkv.getInt(
                KEY_MATH_MULTIPLICATION_MULTIPLICAND_DIGITS,
                QuestionConst.MUL_SECOND_LEN_DEFAULT);
    }

    public void setMathMultiplicationMultiplierTier(MultiplicationTier tier) {
        mmkv.putString(
                KEY_MATH_MULTIPLICATION_MULTIPLIER_TIER,
                tier.getPreferenceValue()).commit();
    }

    public MultiplicationTier getMathMultiplicationMultiplierTier() {
        return MultiplicationTier.fromPreferenceValue(mmkv.getString(
                KEY_MATH_MULTIPLICATION_MULTIPLIER_TIER,
                MultiplicationTier.LOWER_HALF.getPreferenceValue()));
    }

    public void setMathMultiplicationMultiplicandTier(MultiplicationTier tier) {
        mmkv.putString(
                KEY_MATH_MULTIPLICATION_MULTIPLICAND_TIER,
                tier.getPreferenceValue()).commit();
    }

    public MultiplicationTier getMathMultiplicationMultiplicandTier() {
        return MultiplicationTier.fromPreferenceValue(mmkv.getString(
                KEY_MATH_MULTIPLICATION_MULTIPLICAND_TIER,
                MultiplicationTier.LOWER_HALF.getPreferenceValue()));
    }

    public ChallengeType getChallengeType() {
        return ChallengeType.fromPreferenceValue(mmkv.getString(
                KEY_MATH_QUESTION_TYPE, ChallengeType.ARITHMETIC.getPreferenceValue()));
    }

    public void setChallengeType(ChallengeType challengeType) {
        mmkv.putString(KEY_MATH_QUESTION_TYPE, challengeType.getPreferenceValue()).commit();
    }
}
