package com.book.mask.personalize;

import android.content.Context;

import com.book.mask.config.ChallengeType;
import com.book.mask.constant.QuestionConst;
import com.book.mask.util.ArithmeticUtils.MultiplicationTier;
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
    static final String KEY_MATH_MULTIPLICATION_MULTIPLIER_TIER =
            "math_multiplication_multiplier_tier";
    static final String KEY_MATH_MULTIPLICATION_MULTIPLICAND_TIER =
            "math_multiplication_multiplicand_tier";
    static final String KEY_MATH_QUESTION_TYPE = "math_question_type";
    static final String KEY_CHALLENGE_TIMER_MODE = "challenge_timer_mode";
    static final String KEY_ENGLISH_READING_LENGTH = "english_reading_length";
    static final String KEY_RETELLING_STORY_LENGTH = "retelling_story_length";
    static final String KEY_RETELLING_DISPLAY_SECONDS = "retelling_display_seconds";
    static final String KEY_RETELLING_PASS_SCORE = "retelling_pass_score";

    /** 答题计时显示模式：不显示 / 仅显示分钟（mm）/ 显示分钟和秒（mm:s）。 */
    public static final int TIMER_MODE_NONE = 0;
    public static final int TIMER_MODE_MINUTES = 1;
    public static final int TIMER_MODE_MINUTES_SECONDS = 2;

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

    public int getChallengeTimerMode() {
        int mode = mmkv.getInt(KEY_CHALLENGE_TIMER_MODE, TIMER_MODE_NONE);
        return clamp(mode, TIMER_MODE_NONE, TIMER_MODE_MINUTES_SECONDS);
    }

    public void setChallengeTimerMode(int mode) {
        mmkv.putInt(KEY_CHALLENGE_TIMER_MODE,
                clamp(mode, TIMER_MODE_NONE, TIMER_MODE_MINUTES_SECONDS)).commit();
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

    public int getRetellingStoryLength() {
        int length = mmkv.getInt(
                KEY_RETELLING_STORY_LENGTH,
                QuestionConst.RETELLING_STORY_LENGTH_DEFAULT);
        return clamp(
                length,
                QuestionConst.RETELLING_STORY_LENGTH_MIN,
                QuestionConst.RETELLING_STORY_LENGTH_MAX);
    }

    public void setRetellingStoryLength(int length) {
        mmkv.putInt(
                KEY_RETELLING_STORY_LENGTH,
                clamp(
                        length,
                        QuestionConst.RETELLING_STORY_LENGTH_MIN,
                        QuestionConst.RETELLING_STORY_LENGTH_MAX))
                .commit();
    }

    public int getRetellingDisplaySeconds() {
        int seconds = mmkv.getInt(
                KEY_RETELLING_DISPLAY_SECONDS,
                QuestionConst.RETELLING_DISPLAY_SECONDS_DEFAULT);
        return clamp(
                seconds,
                QuestionConst.RETELLING_DISPLAY_SECONDS_MIN,
                QuestionConst.RETELLING_DISPLAY_SECONDS_MAX);
    }

    public void setRetellingDisplaySeconds(int seconds) {
        mmkv.putInt(
                KEY_RETELLING_DISPLAY_SECONDS,
                clamp(
                        seconds,
                        QuestionConst.RETELLING_DISPLAY_SECONDS_MIN,
                        QuestionConst.RETELLING_DISPLAY_SECONDS_MAX))
                .commit();
    }

    public int getRetellingPassScore() {
        int score = mmkv.getInt(
                KEY_RETELLING_PASS_SCORE,
                QuestionConst.RETELLING_PASS_SCORE_DEFAULT);
        return clamp(
                score,
                QuestionConst.RETELLING_PASS_SCORE_MIN,
                QuestionConst.RETELLING_PASS_SCORE_MAX);
    }

    public void setRetellingPassScore(int score) {
        mmkv.putInt(
                KEY_RETELLING_PASS_SCORE,
                clamp(
                        score,
                        QuestionConst.RETELLING_PASS_SCORE_MIN,
                        QuestionConst.RETELLING_PASS_SCORE_MAX))
                .commit();
    }

    /** getter/setter 两侧统一做范围限制，防止导入旧备份或异常值绕过界面校验。 */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
