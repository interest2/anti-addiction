package com.book.mask.network;

/**
 * lite 版不内置语音 SDK，听力题、复述题是否可用仍交由服务端逐设备授权；听述题始终隐藏
 * （{@code showVoiceChallenge}）控制，与 full 版区分。
 */
final class VoiceChallengeDefaults {

    static final boolean AVAILABLE_BY_DEFAULT = false;
    static final boolean HAS_LISTENING_RETELLING_RUNTIME = false;

    private VoiceChallengeDefaults() {
    }
}
