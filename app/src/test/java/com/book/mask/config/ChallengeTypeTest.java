package com.book.mask.config;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ChallengeTypeTest {

    @Test
    public void disabledEnglishOptionsKeepConfiguredOrder() {
        assertArrayEquals(
                new ChallengeType[]{
                        ChallengeType.ARITHMETIC,
                        ChallengeType.REASONING,
                        ChallengeType.MIXED
                },
                ChallengeType.settingsOptions(false));
    }

    @Test
    public void mixedIsDefaultAndUsesRequestTypeOne() {
        assertEquals(ChallengeType.MIXED, ChallengeType.fromPreferenceValue("unknown"));
        assertEquals(1, ChallengeType.MIXED.getRequestType());
    }

    @Test
    public void requestTypesFollowCurrentProtocol() {
        assertEquals(0, ChallengeType.ARITHMETIC.getRequestType());
        assertEquals(1, ChallengeType.MIXED.getRequestType());
        assertEquals(2, ChallengeType.REASONING.getRequestType());
        assertEquals(3, ChallengeType.ENGLISH_READING.getRequestType());
    }
}
