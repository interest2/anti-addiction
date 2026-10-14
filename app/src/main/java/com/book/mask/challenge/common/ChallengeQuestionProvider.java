package com.book.mask.challenge.common;

import android.content.Context;

import com.book.mask.constant.QuestionConst;
import com.book.mask.personalize.ChallengeSettingsManager;
import com.book.mask.util.ArithmeticUtils;

/**
 * 负责算术题的生成，难度取自用户设置。
 */
final class ChallengeQuestionProvider {

    private final ChallengeSettingsManager challengeSettingsManager;

    ChallengeQuestionProvider(Context context) {
        this.challengeSettingsManager = new ChallengeSettingsManager(context);
    }

    Question getQuestion() {
        String difficultyMode = challengeSettingsManager.getMathDifficultyMode();
        String question;
        if ("custom".equals(difficultyMode)) {
            question = ArithmeticUtils.customArithmetic(
                    challengeSettingsManager.getMathAdditionDigits(),
                    challengeSettingsManager.getMathSubtractionDigits(),
                    challengeSettingsManager.getMathMultiplicationMultiplierDigits(),
                    challengeSettingsManager.getMathMultiplicationMultiplierTier(),
                    challengeSettingsManager.getMathMultiplicationMultiplicandDigits(),
                    challengeSettingsManager.getMathMultiplicationMultiplicandTier());
        } else {
            question = ArithmeticUtils.customArithmetic(
                    QuestionConst.ADD_LEN_DEFAULT,
                    QuestionConst.SUB_LEN_DEFAULT,
                    QuestionConst.MUL_FIRST_LEN_DEFAULT,
                    ArithmeticUtils.MultiplicationTier.UPPER_HALF,
                    QuestionConst.MUL_SECOND_LEN_DEFAULT,
                    ArithmeticUtils.MultiplicationTier.LOWER_HALF);
        }
        return new Question(question, String.valueOf(ArithmeticUtils.getMathAnswer(question)));
    }

    static final class Question {
        private final String content;
        private final String answer;

        Question(String content, String answer) {
            this.content = content;
            this.answer = answer;
        }

        String getContent() {
            return content;
        }

        String getAnswer() {
            return answer;
        }
    }
}
