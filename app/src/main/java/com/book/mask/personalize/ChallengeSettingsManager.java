package com.book.mask.personalize;

import android.content.Context;

import com.book.mask.config.ChallengeType;
import com.book.mask.constant.QuestionConst;
import com.book.mask.network.AppConfigManager;
import com.book.mask.util.ArithmeticUtils.MultiplicationTier;
import com.google.gson.Gson;
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
    static final String KEY_REASONING_DIFFICULTY_LEVEL = "reasoning_difficulty_level";
    static final String KEY_LISTENING_DIFFICULTY_LEVEL = "listening_difficulty_level";
    static final String KEY_LISTENING_SCENARIO = "listening_scenario";
    static final String KEY_LISTENING_CUSTOM_SCENARIOS = "listening_custom_scenarios";
    static final String KEY_CHALLENGE_TIMER_MODE = "challenge_timer_mode";
    static final String KEY_ENGLISH_READING_LENGTH = "english_reading_length";
    static final String KEY_RETELLING_STORY_LENGTH = "retelling_story_length";
    static final String KEY_RETELLING_DISPLAY_SECONDS = "retelling_display_seconds";
    static final String KEY_RETELLING_PASS_SCORE = "retelling_pass_score";
    static final String KEY_VOICE_CHALLENGE_ENABLED = "voice_challenge_enabled";

    public static final int REASONING_LEVEL_DEFAULT = -1;
    public static final int REASONING_LEVEL_MIN = 0;
    public static final int REASONING_LEVEL_MAX = 2;

    /** 听力题难度：2=基础（默认），3=进阶（对标高考听力）。 */
    public static final int LISTENING_LEVEL_STANDARD = 2;
    public static final int LISTENING_LEVEL_HARD = 3;
    public static final int LISTENING_LEVEL_DEFAULT = LISTENING_LEVEL_STANDARD;

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
        if (!isVoiceChallengeEnabled()
                && (challengeType == ChallengeType.LISTENING
                || challengeType == ChallengeType.RETELLING)) {
            return ChallengeType.MIXED;
        }
        return challengeType;
    }

    public void setChallengeType(ChallengeType challengeType) {
        mmkv.putString(KEY_MATH_QUESTION_TYPE, challengeType.getPreferenceValue()).commit();
    }

    /** 语音题须同时通过服务端设备授权和本地开关。 */
    public boolean isVoiceChallengeEnabled() {
        return AppConfigManager.isVoiceChallengeAvailable()
                && mmkv.getBoolean(KEY_VOICE_CHALLENGE_ENABLED, true);
    }

    public void setVoiceChallengeEnabled(boolean enabled) {
        mmkv.putBoolean(KEY_VOICE_CHALLENGE_ENABLED, enabled).commit();
    }

    public int getReasoningDifficultyLevel() {
        int level = mmkv.getInt(KEY_REASONING_DIFFICULTY_LEVEL, REASONING_LEVEL_DEFAULT);
        return level >= REASONING_LEVEL_MIN && level <= REASONING_LEVEL_MAX
                ? level
                : REASONING_LEVEL_DEFAULT;
    }

    public void setReasoningDifficultyLevel(int level) {
        int validLevel = level >= REASONING_LEVEL_MIN && level <= REASONING_LEVEL_MAX
                ? level
                : REASONING_LEVEL_DEFAULT;
        mmkv.putInt(KEY_REASONING_DIFFICULTY_LEVEL, validLevel).commit();
    }

    public int getListeningDifficultyLevel() {
        return clamp(
                mmkv.getInt(KEY_LISTENING_DIFFICULTY_LEVEL, LISTENING_LEVEL_DEFAULT),
                LISTENING_LEVEL_STANDARD,
                LISTENING_LEVEL_HARD);
    }

    public void setListeningDifficultyLevel(int level) {
        mmkv.putInt(
                KEY_LISTENING_DIFFICULTY_LEVEL,
                clamp(level, LISTENING_LEVEL_STANDARD, LISTENING_LEVEL_HARD)).commit();
    }

    /** 听力题场景（大模型出题时约束原文话题）；空串表示不限。 */
    public String getListeningScenario() {
        return mmkv.getString(KEY_LISTENING_SCENARIO, "");
    }

    public void setListeningScenario(String scenario) {
        mmkv.putString(
                KEY_LISTENING_SCENARIO, scenario == null ? "" : scenario.trim()).commit();
    }

    /** 已保存的自定义听力场景列表（供设置界面展示 + 删除；不含内置预设场景）。 */
    public String[] getCustomListeningScenarios() {
        java.util.List<String> scenarios = new java.util.ArrayList<>();
        try {
            String stored = mmkv.getString(KEY_LISTENING_CUSTOM_SCENARIOS, "[]");
            String[] customScenarios = new Gson().fromJson(stored, String[].class);
            if (customScenarios != null) {
                for (String scenario : customScenarios) {
                    if (scenario != null && !scenario.isEmpty() && !scenarios.contains(scenario)) {
                        scenarios.add(scenario);
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.w("SettingsManager", "解析自定义听力场景列表失败", e);
        }

        String currentScenario = getListeningScenario();
        if (!currentScenario.isEmpty()
                && !java.util.Arrays.asList(QuestionConst.LISTENING_SCENARIOS).contains(currentScenario)
                && !scenarios.contains(currentScenario)) {
            scenarios.add(currentScenario);
        }
        return scenarios.toArray(new String[0]);
    }

    public void addCustomListeningScenario(String scenario) {
        if (scenario == null || scenario.isEmpty()) {
            return;
        }
        java.util.List<String> scenarios = new java.util.ArrayList<>(
                java.util.Arrays.asList(getCustomListeningScenarios()));
        if (!scenarios.contains(scenario)) {
            scenarios.add(scenario);
            mmkv.putString(KEY_LISTENING_CUSTOM_SCENARIOS, new Gson().toJson(scenarios)).commit();
        }
    }

    public void removeCustomListeningScenario(String scenario) {
        java.util.List<String> scenarios = new java.util.ArrayList<>(
                java.util.Arrays.asList(getCustomListeningScenarios()));
        if (scenarios.remove(scenario)) {
            mmkv.putString(KEY_LISTENING_CUSTOM_SCENARIOS, new Gson().toJson(scenarios)).commit();
        }
        if (scenario.equals(getListeningScenario())) {
            setListeningScenario("");
        }
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
