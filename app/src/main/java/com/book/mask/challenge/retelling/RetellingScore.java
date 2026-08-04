package com.book.mask.challenge.retelling;

/**
 * 复述题结构化评分结果。各维度满分合计 100：关键事实 50 / 准确程度 25 / 顺序因果 15 / 表达完整 10。
 */
public final class RetellingScore {

    private final int score;
    private final int coverage;
    private final int accuracy;
    private final int order;
    private final int expression;
    private final String feedback;

    public RetellingScore(
            int score,
            int coverage,
            int accuracy,
            int order,
            int expression,
            String feedback) {
        this.score = score;
        this.coverage = coverage;
        this.accuracy = accuracy;
        this.order = order;
        this.expression = expression;
        this.feedback = feedback == null ? "" : feedback;
    }

    public int getScore() {
        return score;
    }

    public int getCoverage() {
        return coverage;
    }

    public int getAccuracy() {
        return accuracy;
    }

    public int getOrder() {
        return order;
    }

    public int getExpression() {
        return expression;
    }

    public String getFeedback() {
        return feedback;
    }
}
