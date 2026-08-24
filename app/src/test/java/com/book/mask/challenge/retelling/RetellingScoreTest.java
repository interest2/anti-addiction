package com.book.mask.challenge.retelling;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RetellingScoreTest {

    @Test
    public void weightedContentScoreUsesFourConfiguredDimensions() {
        RetellingScore score = new RetellingScore(
                12,
                80,
                70,
                60,
                50,
                "feedback");

        assertEquals(69, score.calculateWeightedContentScore());
    }

    @Test
    public void withScoreKeepsContentAndFeedback() {
        RetellingScore score = new RetellingScore(
                20,
                90,
                80,
                70,
                60,
                "需要补全结局");

        RetellingScore replaced = score.withScore(73);

        assertEquals(73, replaced.getScore());
        assertEquals(90, replaced.getCoverage());
        assertEquals(80, replaced.getAccuracy());
        assertEquals(70, replaced.getOrder());
        assertEquals(60, replaced.getExpression());
        assertEquals("需要补全结局", replaced.getFeedback());
        assertFalse(replaced.hasPronunciationScore());
    }
}
