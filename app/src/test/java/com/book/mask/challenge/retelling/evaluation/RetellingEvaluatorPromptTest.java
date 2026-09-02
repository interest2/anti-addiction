package com.book.mask.challenge.retelling.evaluation;

import com.book.mask.challenge.retelling.RetellingStimulusMode;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RetellingEvaluatorPromptTest {

    @Test
    public void retellingPromptUsesThreePartStoryCoverage() {
        String prompt = RetellingEvaluator.buildSystemPrompt(RetellingStimulusMode.TEXT);

        assertTrue(prompt.contains("你是复述题评分器"));
        assertTrue(prompt.contains("「冲突-行动-结局」三大主干"));
        assertTrue(prompt.contains("复述最缺的是「冲突/行动/结局」中的哪一环"));
        assertCommonStructure(prompt);
    }

    @Test
    public void listeningRetellingPromptUsesKeyInformationCoverageOnly() {
        String prompt = RetellingEvaluator.buildSystemPrompt(RetellingStimulusMode.AUDIO);

        assertTrue(prompt.contains("你是听述题评分器"));
        assertTrue(prompt.contains("是否覆盖原文的关键信息"));
        assertTrue(prompt.contains("复述如果有关键遗漏需指出"));
        assertFalse(prompt.contains("「冲突-行动-结局」三大主干"));
        assertFalse(prompt.contains("复述最缺的是「冲突/行动/结局」中的哪一环"));
        assertCommonStructure(prompt);
    }

    private static void assertCommonStructure(String prompt) {
        assertTrue(prompt.contains("[角色]"));
        assertTrue(prompt.contains("[安全边界]"));
        assertTrue(prompt.contains("[语音转写容错]"));
        assertTrue(prompt.contains("原文“李工”被转写成“理工”"));
        assertTrue(prompt.contains("不扣任何评分维度"));
        assertTrue(prompt.contains("不要在 feedback 中指出"));
        assertTrue(prompt.contains("[评分维度]"));
        assertTrue(prompt.contains("[通用判分规则]"));
        assertTrue(prompt.contains("[反馈要求]"));
        assertTrue(prompt.contains("[输出要求]"));
        assertTrue(prompt.contains("\"coverage\":0"));
        assertTrue(prompt.contains("\"feedback\":\"\""));
    }
}
