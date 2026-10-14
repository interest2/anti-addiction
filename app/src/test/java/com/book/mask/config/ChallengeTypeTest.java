package com.book.mask.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChallengeTypeTest {

    @Test
    public void arithmeticPreferenceValueRoundTrips() {
        assertEquals(
                ChallengeType.ARITHMETIC,
                ChallengeType.fromPreferenceValue(ChallengeType.ARITHMETIC.getPreferenceValue()));
    }

    @Test
    public void removedOrUnknownPreferenceValuesFallBackToArithmetic() {
        // 历史版本 / 备份文件里可能保存过已下线的题型
        for (String legacy : new String[]{
                "mixed", "reasoning", "english_reading",
                "listening", "retelling", "listening_retelling", "unknown", ""}) {
            assertEquals(legacy, ChallengeType.ARITHMETIC, ChallengeType.fromPreferenceValue(legacy));
        }
        assertEquals(ChallengeType.ARITHMETIC, ChallengeType.fromPreferenceValue(null));
    }
}
