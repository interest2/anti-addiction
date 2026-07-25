package com.book.mask.personalize;

import android.content.Context;

import com.book.mask.config.ChallengeType;
import com.book.mask.constant.QuestionConst;
import com.tencent.mmkv.MMKV;

/**
 * 管理答题类型、算术题难度和英文阅读题配置。
 */
public class ChallengeSettingsManager {

    static final String KEY_MATH_DIFFICULTY_MODE = "math_difficulty_mode";
    static final String KEY_MATH_ADDITION_DIGITS = "math_addition_digits";
    static final String KEY_MATH_SUBTRACTION_DIGITS = "math_subtraction_digits";
    static final String KEY_MATH_MULTIPLICATION_MULTIPLIER_DIGITS =
            "math_multiplication_multiplier_digits";
    static final String KEY_MATH_MULTIPLICATION_MULTIPLICAND_DIGITS =
            "math_multiplication_multiplicand_digits";
    static final String KEY_MATH_QUESTION_TYPE = "math_question_type";
    static final String KEY_ENGLISH_READING_LENGTH = "english_reading_length";

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

    public ChallengeType getChallengeType() {
        String preferenceValue = mmkv.getString(
                KEY_MATH_QUESTION_TYPE, ChallengeType.MIXED.getPreferenceValue());
        ChallengeType challengeType = ChallengeType.fromPreferenceValue(preferenceValue);
        if (!QuestionConst.ENGLISH_READING_ENABLED
                && challengeType == ChallengeType.ENGLISH_READING) {
            return ChallengeType.MIXED;
        }
        return challengeType;
    }

    public void setChallengeType(ChallengeType challengeType) {
        mmkv.putString(KEY_MATH_QUESTION_TYPE, challengeType.getPreferenceValue()).commit();
    }

    public int getEnglishReadingLength() {
        int length = mmkv.getInt(
                KEY_ENGLISH_READING_LENGTH,
                QuestionConst.ENGLISH_READING_LENGTH_DEFAULT);
        return Math.max(length, QuestionConst.ENGLISH_READING_LENGTH_MIN);
    }

    public void setEnglishReadingLength(int length) {
        int validLength = Math.max(length, QuestionConst.ENGLISH_READING_LENGTH_MIN);
        mmkv.putInt(KEY_ENGLISH_READING_LENGTH, validLength).commit();
    }
}
